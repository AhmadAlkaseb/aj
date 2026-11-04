"""Verify Product View ownership, its internal API, and queued recovery.

python scripts/check_projection.py [--failures]
The optional outage cases stop/restart only Product View and its MongoDB container.
Run separately from other failure-test suites.
"""
import base64
import concurrent.futures
import json
import subprocess
import sys
import time
import urllib.error
import urllib.request
import uuid
import zipfile
from pathlib import Path
from test_identity import admin_headers

ROOT = Path(__file__).resolve().parent.parent
OWNER = 'http://127.0.0.1:7006'
PRODUCTS = 'http://127.0.0.1:7003'
GATEWAY = 'http://127.0.0.1:8080'


def compose(*args):
    subprocess.run(['docker', 'compose', *args], cwd=ROOT, check=True, stdout=subprocess.DEVNULL)


def request(url, method='GET', data=None, expected=200, headers=None):
    req = urllib.request.Request(url, method=method, data=None if data is None else json.dumps(data).encode(),
        headers={'Content-Type': 'application/json', **(admin_headers() if '/api/v1/products' in url else {}), **(headers or {})})
    try:
        with urllib.request.urlopen(req, timeout=12) as response:
            status, raw = response.status, response.read()
    except urllib.error.HTTPError as error:
        status, raw = error.code, error.read()
    assert status == expected, f'{method} {url}: expected {expected}, received {status}'
    return json.loads(raw) if raw else None


def wait_for(fn, timeout=90):
    deadline = time.monotonic() + timeout
    last = None
    while time.monotonic() < deadline:
        try:
            value = fn()
            if value:
                return value
        except (AssertionError, OSError) as error:
            last = error
        time.sleep(.4)
    raise AssertionError(f'Projection did not recover: {last}')


def main():
    config = json.loads(subprocess.check_output(['docker', 'compose', 'config', '--format', 'json'], cwd=ROOT))
    sync = config['services']['product-synchronizer']
    owner = config['services']['product-view']
    mongo = config['services']['product-view-mongo']
    assert not any(key.startswith('MONGO') for key in sync['environment'])
    assert not set(sync['networks']).intersection(mongo['networks'])
    assert set(owner['networks']).intersection(mongo['networks'])
    assert not mongo.get('ports'), 'Normal startup must not expose the private database port'
    jar = ROOT / 'product-synchronizer/target/app.jar'
    if jar.exists():
        with zipfile.ZipFile(jar) as archive:
            assert not any(name.startswith(('com/mongodb/', 'org/bson/')) for name in archive.namelist())
    token = owner['environment']['PROJECTION_API_TOKEN']
    credentials = {'X-Projection-Token': token}
    wait_for(lambda: request(OWNER + '/health')['status'] == 'UP')
    wait_for(lambda: request(GATEWAY + '/api/v1/status')['status'] == 'UP')
    tag = uuid.uuid4().hex[:8]
    created = []
    direct_snapshots = []

    def create(label):
        car = request(PRODUCTS + '/api/v1/products', 'POST',
            {'brand': 'Projection check', 'model': label + '-' + tag, 'price': 123450, 'year': 2026}, 201)
        created.append(car['guid'])
        return car

    def visible(guid):
        return request(OWNER + '/api/v1/products/' + guid)

    def apply(guid, version, topic='product.updated', headers=credentials, expected=204):
        snapshot = {**template, 'guid': guid, 'version': version, 'model': f'Version {version}'}
        event = {'eventId': str(uuid.uuid4()), 'topic': topic, 'snapshot': snapshot}
        request(OWNER + '/internal/projections/products/' + guid, 'PUT', event, expected, headers)

    def queued():
        rabbit = config['services']['rabbitmq']['environment']
        auth = base64.b64encode((rabbit['RABBITMQ_DEFAULT_USER'] + ':' + rabbit['RABBITMQ_DEFAULT_PASS']).encode()).decode()
        queue = request('http://127.0.0.1:15672/api/queues/%2F/aj.product-sync', headers={'Authorization': 'Basic ' + auth})
        return queue.get('messages_ready', 0) + queue.get('messages_unacknowledged', 0) > 0

    try:
        template = create('normal-flow')
        wait_for(lambda: visible(template['guid']))
        # Neither anonymous callers nor the public gateway can invoke the internal writer.
        apply(template['guid'], 100, headers={}, expected=401)
        apply(template['guid'], 100, headers={'X-Projection-Token': 'wrong'}, expected=401)
        request(GATEWAY + '/internal/projections/products/' + template['guid'], 'PUT', {}, 404, credentials)
        assert visible(template['guid'])['version'] == template['version']

        ordered = str(uuid.uuid4())
        direct_snapshots.append(ordered)
        for version in [1, 3, 2, 3]:
            apply(ordered, version)
        assert visible(ordered)['version'] == 3
        apply(ordered, 4, 'product.removed')
        apply(ordered, 3)
        request(OWNER + '/api/v1/products/' + ordered, expected=404)

        parallel_guid = str(uuid.uuid4())
        direct_snapshots.append(parallel_guid)
        with concurrent.futures.ThreadPoolExecutor(max_workers=6) as pool:
            list(pool.map(lambda version: apply(parallel_guid, version), [9, 2, 7, 11, 5, 12, 3, 6, 10, 8, 4, 1]))
        assert visible(parallel_guid)['version'] == 12

        if '--failures' in sys.argv:
            for service in ['product-view', 'product-view-mongo']:
                try:
                    compose('stop', service)
                    pending = create('queued-' + service)
                    wait_for(queued)
                finally:
                    compose('start', service)
                wait_for(lambda: visible(pending['guid'])['model'] == pending['model'])
            wait_for(lambda: request(GATEWAY + '/api/v1/status')['status'] == 'UP')
    finally:
        for guid in direct_snapshots:
            apply(guid, 1000, 'product.removed')
        for guid in created:
            request(PRODUCTS + '/api/v1/products/' + guid, 'DELETE', expected=204)
            def removed():
                request(OWNER + '/api/v1/products/' + guid, expected=404)
                return True
            wait_for(removed)

    result = {'result': 'PASS', 'databaseOwner': 'product-view', 'synchronizerMongoAccess': False,
        'internalAuthentication': 'PASS', 'versionsAndTombstones': 'PASS', 'concurrentSnapshots': 'PASS',
        'queuedRecovery': '--failures' in sys.argv}
    out = ROOT / 'test-results' / 'projection-results.json'
    out.parent.mkdir(exist_ok=True)
    out.write_text(json.dumps(result, indent=2), encoding='utf-8')
    print(json.dumps(result, indent=2))


if __name__ == '__main__':
    main()
