"""Verify live dashboards, metrics, logs and isolated RabbitMQ message delivery."""
import base64
import argparse
import json
import time
import urllib.parse
import urllib.request
import uuid
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent


def request(url, credentials=None, data=None, method='GET'):
    headers = {'Content-Type': 'application/json'}
    if credentials:
        headers['Authorization'] = 'Basic ' + base64.b64encode(credentials.encode()).decode()
    req = urllib.request.Request(url, headers=headers, method=method,
        data=None if data is None else json.dumps(data).encode())
    with urllib.request.urlopen(req, timeout=15) as response:
        raw = response.read()
        return json.loads(raw) if raw else None


def wait(fn, seconds=120):
    deadline = time.monotonic() + seconds
    last = None
    while time.monotonic() < deadline:
        try:
            last = fn()
            if last:
                return last
        except (OSError, AssertionError) as exc:
            last = exc
        time.sleep(2)
    raise AssertionError(f'Operations check timed out: {last}')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--runtime', choices=['compose', 'kubernetes'], default='compose')
    runtime = parser.parse_args().runtime
    if runtime == 'kubernetes':
        secrets = json.loads((ROOT / 'test-results/k8s/secrets.json').read_text())
        grafana_auth = 'admin:' + secrets['grafana-password']
        broker_auth = 'aj:' + secrets['rabbit-password']
    else:
        from configure_local import credentials
        grafana_auth = broker_auth = ':'.join(credentials())
    grafana = 'http://127.0.0.1:13001'
    broker = 'http://127.0.0.1:15672/api'
    dashboards = ['aj-home', 'aj-platform', 'aj-http', 'aj-messaging', 'aj-logs', 'aj-kubernetes']
    panel_queries = []
    for uid in dashboards:
        item = request(grafana + '/api/dashboards/uid/' + uid, grafana_auth)
        assert item['dashboard']['panels'], uid
        for panel in item['dashboard']['panels']:
            for target in panel.get('targets', []):
                panel_queries.append((panel['datasource']['type'], target['expr']))
    for uid in ['aj-prometheus', 'aj-loki']:
        assert request(grafana + '/api/datasources/uid/' + uid + '/health', grafana_auth)['status'] == 'OK'
    minimum_targets = 10 if runtime == 'kubernetes' else 9
    def healthy_targets():
        rows = request('http://127.0.0.1:9090/api/v1/targets')['data']['activeTargets']
        healthy = [t for t in rows if t['health'] == 'up']
        return healthy if len(healthy) >= minimum_targets else None
    targets = wait(healthy_targets)
    # This queue is private to this test and is always removed; no business queue is consumed.
    queue = 'aj.operations-check-' + uuid.uuid4().hex
    url = broker + '/queues/%2F/' + queue
    try:
        request(url, broker_auth, {'durable': False, 'auto_delete': False, 'arguments': {}}, 'PUT')
        message = {'properties': {'message_id': queue}, 'routing_key': queue,
                   'payload': 'operations-check', 'payload_encoding': 'string'}
        assert request(broker + '/exchanges/%2F/amq.default/publish', broker_auth, message, 'POST')['routed']
        ready = wait(lambda: request(url, broker_auth).get('messages_ready', 0))
        assert ready == 1
        rows = request(url + '/get', broker_auth,
            {'count': 1, 'ackmode': 'ack_requeue_false', 'encoding': 'auto', 'truncate': 100}, 'POST')
        assert rows[0]['payload'] == 'operations-check'
        wait(lambda: request(url, broker_auth).get('messages_ready') == 0)
    finally:
        request(url, broker_auth, method='DELETE')
    expressions = ['rabbitmq_queue_messages_ready', 'aj_outbox_pending',
                 'rabbitmq_global_messages_received_total', 'rabbitmq_global_messages_confirmed_total', 'rabbitmq_global_messages_acknowledged_total']
    for expr in expressions + (['kube_deployment_status_replicas_available{namespace="aj"}'] if runtime == 'kubernetes' else []):
        result = request('http://127.0.0.1:9090/api/v1/query?' + urllib.parse.urlencode({'query': expr}))
        assert result['data']['result'], expr
    for kind, expr in panel_queries:
        endpoint = 'http://127.0.0.1:3100/loki/api/v1/query_range?' if kind == 'loki' else 'http://127.0.0.1:9090/api/v1/query?'
        response = request(endpoint + urllib.parse.urlencode({'query': expr}))
        assert response['status'] == 'success', expr
    query = urllib.parse.urlencode({'query': '{project="aj"}', 'limit': 5})
    logs = wait(lambda: request('http://127.0.0.1:3100/loki/api/v1/query_range?' + query)['data']['result'])
    result = {'result': 'PASS', 'dashboards': dashboards, 'validatedPanelQueries': len(panel_queries), 'healthyMetricsTargets': len(targets),
              'runtime': runtime, 'rabbitPublishReadyConsume': 'PASS', 'kubernetesMetrics': 'PASS' if runtime == 'kubernetes' else 'not applicable', 'centralLogStreams': len(logs)}
    (ROOT / 'test-results/operations-results.json').write_text(json.dumps(result, indent=2) + '\n')
    print(json.dumps(result, indent=2))


if __name__ == '__main__':
    main()
