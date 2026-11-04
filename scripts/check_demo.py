"""Verify automatic examination fixtures; --restart also checks non-destructive restart.

Run separately from other failure tests: python scripts/check_demo.py --restart
"""
import hashlib
import json
import subprocess
import sys
import time
import urllib.error
import urllib.request
import uuid
from decimal import Decimal
from pathlib import Path
from test_identity import admin_headers

ROOT = Path(__file__).resolve().parent.parent
BASE = 'http://127.0.0.1:3000/api/v1'
DOMAINS = ('users', 'products', 'orders', 'payments')


def guid(domain, index):
    key = 'aj-payment:' + guid('orders', index) if domain == 'payments' else f'aj-demo-v1:{domain}:{index}'
    return str(uuid.UUID(bytes=hashlib.md5(key.encode('utf-8')).digest(), version=3))


def api(path, method='GET', data=None):
    req = urllib.request.Request(BASE + path, method=method,
        data=None if data is None else json.dumps(data).encode('utf-8'),
        headers={'Content-Type': 'application/json', **admin_headers()})
    with urllib.request.urlopen(req, timeout=12) as response:
        return json.load(response)


def wait_for(fn, timeout=120):
    deadline = time.monotonic() + timeout
    last = None
    while time.monotonic() < deadline:
        try:
            result = fn()
            if result:
                return result
        except (AssertionError, OSError) as error:
            last = error
        time.sleep(.5)
    raise AssertionError(f'Demo data did not become ready: {last}')


def snapshot():
    data = {}
    for domain in DOMAINS:
        expected = {guid(domain, index) for index in range(1, 21)}
        rows = [row for row in api('/' + domain) if row['guid'] in expected]
        assert len(rows) == 20, f'{domain}: expected 20 demo records, got {len(rows)}'
        data[domain] = {row['guid']: row for row in rows}
        assert set(data[domain]) == expected, f'{domain}: missing or duplicate identities'
    return data


def check_references(data):
    for index in range(1, 21):
        order = data['orders'][guid('orders', index)]
        payment = data['payments'][guid('payments', index)]
        assert order['userGuid'] == guid('users', index)
        assert order['productGuid'] == guid('products', index)
        assert payment['orderGuid'] == order['guid']
        assert Decimal(str(payment['amount'])) == Decimal(str(order['totalAmount']))
        assert payment['currency'] == order['currency'] == 'DKK'
        assert payment['paidAt'] is not None if payment['status'] == 'COMPLETED' else payment['paidAt'] is None


def main():
    wait_for(lambda: api('/status')['status'] == 'UP')
    data = wait_for(snapshot)
    check_references(data)
    restarted = '--restart' in sys.argv
    if restarted:
        user_guid = guid('users', 20)
        original = data['users'][user_guid].copy()
        edited = {**original, 'firstName': original['firstName'] + ' (restart check)'}
        try:
            api('/users/' + user_guid, 'PUT', edited)
            before = snapshot()
            subprocess.run(['docker', 'compose', 'restart', *DOMAINS], cwd=ROOT, check=True)
            wait_for(lambda: api('/status')['status'] == 'UP')
            after = wait_for(snapshot)
            assert before == after, 'Restart changed existing demonstration records'
            check_references(after)
        finally:
            wait_for(lambda: api('/status')['status'] == 'UP')
            api('/users/' + user_guid, 'PUT', original)
    result = {'result': 'PASS', 'demoRecordsPerAPI': {domain: 20 for domain in DOMAINS},
              'catalogue': '20 products projected into MongoDB',
              'referencesAndAmounts': 'PASS', 'restartPreservesEdits': restarted}
    out = ROOT / 'test-results' / 'demo-data.json'
    out.parent.mkdir(exist_ok=True)
    out.write_text(json.dumps(result, indent=2), encoding='utf-8')
    print(json.dumps(result, indent=2))


if __name__ == '__main__':
    main()
