"""Backend cooperation and broker contracts: python scripts/check_async.py [--failures].

Uses service APIs directly and RabbitMQ's management API, without a browser/gateway.
Outage scenarios restore every stopped service; demonstration volumes are retained.
"""
import base64
import json
import sys
import time
import unittest
import urllib.request
import uuid
from datetime import datetime
FAILURES = '--failures' in sys.argv
if FAILURES:
    sys.argv.remove('--failures')
from smoke import api, compose, wait_for
URLS = {'users': 'http://127.0.0.1:7002/api/v1', 'products': 'http://127.0.0.1:7003/api/v1',
        'orders': 'http://127.0.0.1:7004/api/v1', 'payments': 'http://127.0.0.1:7005/api/v1'}


def call(service, path, method='GET', data=None, expected=200):
    return api(path, method, data, expected, base=URLS[service])


def rabbit(path, method='GET', value=None):
    req = urllib.request.Request('http://127.0.0.1:15672/api/' + path, method=method,
        data=None if value is None else json.dumps(value).encode(),
        headers={'Content-Type': 'application/json',
                 'Authorization': 'Basic ' + base64.b64encode(b'aj:aj-local').decode()})
    with urllib.request.urlopen(req, timeout=10) as res:
        raw = res.read()
        return json.loads(raw) if raw else None


def state(service, resource, guid, status):
    return wait_for(lambda: call(service, '/' + resource + '/' + guid)['status'] == status, 90)


class AsyncTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.user = call('users', '/users', 'POST', {'firstName': 'Async', 'lastName': 'Test',
                        'email': uuid.uuid4().hex + '@async.test'}, 201)

    def car(self):
        return call('products', '/products', 'POST', {'brand': 'Async test', 'model': uuid.uuid4().hex[:8],
                    'price': 234567.89, 'year': 2026, 'currency': 'DKK'}, 201)

    def order(self, car, user=None):
        return call('orders', '/orders', 'POST', {'userGuid': user or self.user['guid'],
                    'productGuid': car['guid'], 'shippingAddress': 'Testvej 2',
                    'totalAmount': 1, 'currency': 'EUR'}, 201)

    def test_01_real_broker_metadata_and_authoritative_sale(self):
        queue = 'aj.test.audit.' + uuid.uuid4().hex
        rabbit('queues/%2F/' + queue, 'PUT', {'durable': False, 'auto_delete': False, 'arguments': {}})
        rabbit('bindings/%2F/e/aj.events/q/' + queue, 'POST', {'routing_key': '#', 'arguments': {}})
        try:
            car = self.car(); order = self.order(car)
            self.assertEqual(order['status'], 'PENDING'); self.assertIsNone(order['totalAmount'])
            state('orders', 'orders', order['guid'], 'RESERVED')
            confirmed = call('orders', '/orders/' + order['guid'])
            self.assertEqual((confirmed['totalAmount'], confirmed['currency']), (234567.89, 'DKK'))
            payment = call('payments', '/payments', 'POST', {'orderGuid': order['guid']}, 201)
            self.assertEqual(payment['status'], 'REQUESTED'); self.assertIsNone(payment['amount'])
            state('payments', 'payments', payment['guid'], 'PENDING')
            invoice = call('payments', '/payments/' + payment['guid'])
            self.assertEqual((invoice['amount'], invoice['currency']), (234567.89, 'DKK'))
            call('payments', '/payments/' + payment['guid'], 'PUT', {'status': 'COMPLETED'})
            state('orders', 'orders', order['guid'], 'COMPLETED')
            state('products', 'products', car['guid'], 'SOLD')
            seen = set()
            def collected():
                for msg in rabbit('queues/%2F/' + queue + '/get', 'POST',
                                  {'count': 500, 'ackmode': 'ack_requeue_false', 'encoding': 'auto'}):
                    body = json.loads(msg['payload'])
                    if body.get('correlationId') != order['guid']:
                        continue
                    self.assertEqual(body['eventId'], msg['properties']['message_id'])
                    self.assertEqual(body['eventType'], msg['routing_key'])
                    self.assertEqual(body['schemaVersion'], 1)
                    datetime.fromisoformat(body['occurredAt'].replace('Z', '+00:00'))
                    seen.add(body['eventType'])
                return {'customer.check', 'customer.valid', 'quantitycheck', 'quantityreserved',
                        'product.reserved', 'payment.requested', 'payment.authorized', 'payment.completed'} <= seen
            wait_for(collected)
        finally:
            rabbit('queues/%2F/' + queue, 'DELETE')

    def test_02_unknown_references_are_asynchronous_business_rejections(self):
        car = self.car(); order = self.order(car, str(uuid.uuid4()))
        state('orders', 'orders', order['guid'], 'REJECTED')
        self.assertEqual(call('products', '/products/' + car['guid'])['status'], 'AVAILABLE')
        missing = self.order({'guid': str(uuid.uuid4())})
        state('orders', 'orders', missing['guid'], 'REJECTED')
        payment = call('payments', '/payments', 'POST', {'orderGuid': str(uuid.uuid4())}, 201)
        state('payments', 'payments', payment['guid'], 'REJECTED')
        call('payments', '/payments/' + payment['guid'], 'PUT', {'status': 'COMPLETED'}, 409)
        self.assertEqual(call('orders', '/users/' + str(uuid.uuid4()) + '/orders'), [])
        self.assertEqual(call('payments', '/orders/' + str(uuid.uuid4()) + '/payments'), [])

    def test_03_unsupported_schema_goes_to_dead_letter_queue(self):
        queue = 'aj.test.dead.' + uuid.uuid4().hex
        rabbit('queues/%2F/' + queue, 'PUT', {'durable': False, 'auto_delete': False, 'arguments': {}})
        rabbit('bindings/%2F/e/aj.dead/q/' + queue, 'POST', {'routing_key': '', 'arguments': {}})
        event_id = str(uuid.uuid4())
        try:
            result = rabbit('exchanges/%2F/aj.events/publish', 'POST', {
                'routing_key': 'customer.check', 'properties': {'message_id': event_id, 'delivery_mode': 2},
                'payload_encoding': 'string', 'payload': json.dumps({'schemaVersion': 999, 'payload': {}})})
            self.assertTrue(result['routed'])
            def rejected():
                messages = rabbit('queues/%2F/' + queue + '/get', 'POST',
                    {'count': 50, 'ackmode': 'ack_requeue_false', 'encoding': 'auto'})
                return any(m['properties'].get('message_id') == event_id for m in messages)
            wait_for(rejected)
        finally:
            rabbit('queues/%2F/' + queue, 'DELETE')

    @unittest.skipUnless(FAILURES, 'Use --failures for consumer outage checks')
    def test_04_requests_recover_without_client_retries(self):
        car = self.car()
        try:
            compose('stop', 'users')
            order = self.order(car)
            time.sleep(2)
            self.assertEqual(call('orders', '/orders/' + order['guid'])['status'], 'PENDING')
        finally:
            compose('start', 'users')
        state('orders', 'orders', order['guid'], 'RESERVED')
        try:
            compose('stop', 'orders')
            payment = call('payments', '/payments', 'POST', {'orderGuid': order['guid']}, 201)
            time.sleep(2)
            self.assertEqual(call('payments', '/payments/' + payment['guid'])['status'], 'REQUESTED')
            call('payments', '/payments/' + payment['guid'], 'PUT', {'status': 'COMPLETED'}, 409)
            self.assertEqual(call('payments', '/payments', 'POST', {'orderGuid': order['guid']})['guid'], payment['guid'])
            compose('stop', 'payments')
            compose('start', 'orders')
            state('orders', 'orders', order['guid'], 'PAYMENT_PENDING')
        finally:
            compose('start', 'orders', 'payments')
        state('payments', 'payments', payment['guid'], 'PENDING')
        call('payments', '/payments/' + payment['guid'], 'PUT', {'status': 'FAILED'})
        state('orders', 'orders', order['guid'], 'FAILED')
        state('products', 'products', car['guid'], 'AVAILABLE')

    @unittest.skipUnless(FAILURES, 'Use --failures for late reservation checks')
    def test_05_deleted_order_releases_late_reservation(self):
        car = self.car()
        try:
            compose('stop', 'products')
            order = self.order(car)
            state('orders', 'orders', order['guid'], 'RESERVING')
            call('orders', '/orders/' + order['guid'], 'DELETE', expected=204)
        finally:
            compose('start', 'products')
        # Wait until Products actually consumed the queued check and its compensation.
        def released():
            p = call('products', '/products/' + car['guid'])
            return p['version'] >= 2 and p['status'] == 'AVAILABLE'
        wait_for(released, 90)


if __name__ == '__main__':
    unittest.main(verbosity=2)
