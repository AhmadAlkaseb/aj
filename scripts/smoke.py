"""Run against a running Docker stack: python scripts/smoke.py [--failures].

Creates its own labelled records, and retains the resulting sales/audit history.
--failures temporarily stops only this project's services and always restarts them.
"""
import concurrent.futures
import json
import subprocess
import sys
import time
import unittest
import urllib.error
import urllib.request
import uuid
from pathlib import Path
from test_identity import admin_headers

ROOT = Path(__file__).resolve().parent.parent
BASE = 'http://127.0.0.1:3000/api/v1'
FAILURES = '--failures' in sys.argv
if FAILURES:
    sys.argv.remove('--failures')


def api(path, method='GET', data=None, expected=200, base=BASE):
    body = None if data is None else json.dumps(data).encode()
    req = urllib.request.Request(base + path, data=body, method=method,
                                 headers={'Content-Type': 'application/json', **admin_headers()})
    try:
        with urllib.request.urlopen(req, timeout=12) as res:
            status, raw = res.status, res.read()
    except urllib.error.HTTPError as e:
        status, raw = e.code, e.read()
    result = json.loads(raw) if raw else None
    if status != expected:
        raise AssertionError(f'{method} {path}: expected {expected}, got {status}: {result}')
    return result


def wait_for(fn, timeout=35):
    deadline = time.monotonic() + timeout
    last = None
    while time.monotonic() < deadline:
        try:
            last = fn()
            if last:
                return last
        except (AssertionError, OSError) as e:
            last = e
        time.sleep(.4)
    raise AssertionError(f'Timed out: {last}')


def compose(*args):
    subprocess.run(['docker', 'compose', *args], cwd=ROOT, check=True,
                   stdout=subprocess.DEVNULL)


class PlatformTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        wait_for(lambda: api('/status')['status'] == 'UP', 120)
        cls.tag = uuid.uuid4().hex[:10]
        cls.user = api('/users', 'POST', {'firstName': 'Integrationstest',
                       'lastName': cls.tag, 'email': f'{cls.tag}@example.test',
                       'address': 'Testvej 1', 'city': 'Koebenhavn', 'postalCode': '2100'}, 201)

    def car(self):
        result = api('/products', 'POST', {'brand': 'Testbil', 'model': self.tag,
                     'year': 2026, 'price': 219995.50, 'currency': 'DKK',
                     'mileage': 15000, 'fuelType': 'El', 'transmission': 'Automatisk',
                     'engine': {'type': 'El', 'powerHp': 220, 'capacityLiters': 0},
                     'equipment': ['Bakkamera', 'Saedevarme'], 'images': []}, 201)
        wait_for(lambda: api('/products/' + result['guid'])['guid'] == result['guid'])
        return result

    def order(self, car):
        return api('/orders', 'POST', {'userGuid': self.user['guid'],
                   'productGuid': car['guid'], 'shippingAddress': 'Testvej 1',
                   'totalAmount': 1, 'currency': 'EUR'}, 201)

    def order_state(self, order, status):
        return wait_for(lambda: api('/orders/' + order['guid'])['status'] == status)

    def product_state(self, car, status):
        return wait_for(lambda: api('/products/' + car['guid'])['status'] == status)

    def test_01_contracts_and_validation(self):
        api('/users/not-a-guid', expected=400)
        api('/users/' + str(uuid.uuid4()), expected=404)
        api('/users', 'POST', {'firstName': 'X', 'lastName': 'Y', 'email': 'invalid'}, 400)
        api('/users', 'POST', {'firstName': 'X', 'lastName': 'Y', 'email': self.user['email']}, 409)
        api('/products', 'POST', {'brand': 'X', 'model': 'Y', 'price': -1}, 400)
        api('/products?status=BOGUS', expected=400)
        api('/internal/orders/anything/payment', 'POST', {}, 404)
        self.assertNotIn('id', api('/users/' + self.user['guid']))

    def test_02_complete_sale_and_idempotent_payment(self):
        car = self.car()
        projected = api('/products/' + car['guid'])
        self.assertEqual(projected['price'], 219995.50)
        self.assertEqual(projected['engine']['powerHp'], 220)
        self.assertEqual(projected['equipment'], ['Bakkamera', 'Saedevarme'])
        order = self.order(car)
        self.order_state(order, 'RESERVED')
        order = api('/orders/' + order['guid'])
        self.assertEqual(order['totalAmount'], 219995.50)
        self.assertEqual(order['currency'], 'DKK')
        self.order_state(order, 'RESERVED')
        api('/orders/' + order['guid'], 'PUT', {'status': 'COMPLETED'}, 400)
        payment = api('/payments', 'POST', {'orderGuid': order['guid'], 'paymentMethod': 'MANUAL'}, 201)
        retry = api('/payments', 'POST', {'orderGuid': order['guid'], 'paymentMethod': 'MANUAL'})
        self.assertEqual(payment['guid'], retry['guid'])
        wait_for(lambda: api('/payments/' + payment['guid'])['status'] == 'PENDING')
        api('/orders/' + order['guid'], 'DELETE', expected=409)
        api('/payments/' + payment['guid'], 'PUT', {'status': 'COMPLETED'})
        api('/payments/' + payment['guid'], 'PUT', {'status': 'COMPLETED'})
        api('/payments/' + payment['guid'], 'PUT', {'status': 'FAILED'}, 409)
        self.order_state(order, 'COMPLETED')
        self.product_state(car, 'SOLD')
        api('/products/' + car['guid'], 'DELETE', expected=409)
        self.assertTrue(any(o['guid'] == order['guid'] for o in api('/users/' + self.user['guid'] + '/orders')))
        self.assertEqual(len(api('/orders/' + order['guid'] + '/payments')), 1)

    def test_03_failed_payment_releases_car(self):
        car = self.car(); order = self.order(car); self.order_state(order, 'RESERVED')
        payment = api('/payments', 'POST', {'orderGuid': order['guid']}, 201)
        wait_for(lambda: api('/payments/' + payment['guid'])['status'] == 'PENDING')
        api('/payments/' + payment['guid'], 'PUT', {'status': 'FAILED'})
        self.order_state(order, 'FAILED'); self.product_state(car, 'AVAILABLE')

    def test_04_only_one_buyer_can_reserve(self):
        car = self.car()
        def attempt(_):
            try:
                return self.order(car)
            except AssertionError as e:
                if 'got 409' in str(e):
                    return None
                raise
        with concurrent.futures.ThreadPoolExecutor(max_workers=5) as pool:
            orders = [o for o in pool.map(attempt, range(5)) if o]
        wait_for(lambda: all(api('/orders/' + o['guid'])['status'] in ['RESERVED', 'REJECTED'] for o in orders))
        winners = [o for o in orders if api('/orders/' + o['guid'])['status'] == 'RESERVED']
        self.assertEqual(len(winners), 1)
        api('/orders/' + winners[0]['guid'], 'PUT', {'status': 'CANCELLED'})
        self.product_state(car, 'AVAILABLE')

    def test_05_soft_delete_and_projection(self):
        car = self.car()
        api('/products/' + car['guid'], 'PUT', {'brand': 'Updated', 'model': self.tag,
            'price': 299000, 'year': 2026, 'status': 'UNAVAILABLE'})
        wait_for(lambda: api('/products/' + car['guid'])['brand'] == 'Updated')
        api('/products/' + car['guid'], 'DELETE', expected=204)
        def removed():
            api('/products/' + car['guid'], expected=404)
            return True
        wait_for(removed)

    @unittest.skipUnless(FAILURES, 'Use --failures to stop and restart containers')
    def test_06_outages_and_recovery(self):
        try:
            compose('stop', 'product-synchronizer')
            wait_for(lambda: api('/status')['status'] == 'DEGRADED')
            car = api('/products', 'POST', {'brand': 'Recovery', 'model': self.tag,
                      'price': 12345, 'year': 2026}, 201)
        finally:
            compose('start', 'product-synchronizer')
        wait_for(lambda: api('/products/' + car['guid'])['brand'] == 'Recovery')
        try:
            compose('stop', 'rabbitmq')
            wait_for(lambda: api('/status')['status'] == 'DEGRADED')
            queued = api('/products', 'POST', {'brand': 'Outbox', 'model': self.tag,
                         'price': 54321, 'year': 2026}, 201)
        finally:
            compose('start', 'rabbitmq')
        wait_for(lambda: api('/products/' + queued['guid'])['brand'] == 'Outbox', 90)
        try:
            compose('stop', 'users-db')
            wait_for(lambda: any(s['service'] == 'users' and s['status'] == 'DOWN' for s in api('/status')['services']))
        finally:
            compose('start', 'users-db')
        wait_for(lambda: api('/users/' + self.user['guid'])['guid'] == self.user['guid'], 60)
        try:
            compose('stop', 'apigateway')
            error = api('/status', expected=503)
            self.assertIn('message', error)
            with urllib.request.urlopen('http://localhost:3000', timeout=5) as res:
                self.assertEqual(res.status, 200)
        finally:
            compose('start', 'apigateway')
        wait_for(lambda: api('/status')['status'] == 'UP', 90)


if __name__ == '__main__':
    unittest.main(verbosity=2)
