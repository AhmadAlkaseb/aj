"""Generate provisioned dashboards shared by Compose and Kubernetes."""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent


def dashboard(uid, title, definitions):
    panels = []
    for index, (name, expr, kind, description) in enumerate(definitions):
        loki = kind == 'logs'
        panels.append({'id': index + 1, 'title': name, 'description': description,
            'type': kind, 'datasource': {'type': 'loki' if loki else 'prometheus',
                                       'uid': 'aj-loki' if loki else 'aj-prometheus'},
            'gridPos': {'x': (index % 2) * 12, 'y': (index // 2) * 8, 'w': 12, 'h': 8},
            'targets': [{'expr': expr, 'refId': 'A', **({'queryType': 'range'} if loki else {'legendFormat': '{{service}}{{queue}}{{pod}}{{deployment}}'})}],
            'fieldConfig': {'defaults': {'min': 0}, 'overrides': []},
            'options': {'showTime': True, 'wrapLogMessage': True} if loki else {}})
    return {'uid': uid, 'title': title, 'schemaVersion': 39, 'version': 1,
            'tags': ['AJ Motors'], 'refresh': '5s', 'time': {'from': 'now-30m', 'to': 'now'},
            'links': [{'title': 'AJ dashboards', 'type': 'dashboards', 'tags': ['AJ Motors']}], 'panels': panels}


def main():
    dashboards = [
        dashboard('aj-http', 'AJ · HTTP og services', [
            ('Services online', 'up{job="aj-applications"}', 'stat', '1 = reachable; 0 = scrape failed.'),
            ('Requests / sekund', 'sum by(service)(rate(aj_http_requests_total[5m]))', 'timeseries', 'Includes health checks.'),
            ('HTTP 5xx / sekund', 'sum by(service)(rate(aj_http_requests_total{status=~"5.."}[5m]))', 'timeseries', 'Server errors.'),
            ('P95 svartid (sekunder)', 'histogram_quantile(0.95,sum by(le,service)(rate(aj_http_duration_seconds_bucket[5m])))', 'timeseries', '95th percentile response time.')]),
        dashboard('aj-messaging', 'AJ · RabbitMQ og beskedlevering', [
            ('Venter på afsendelse (outbox)', 'aj_outbox_pending', 'timeseries', 'Committed events still in application database; RabbitMQ cannot see these yet.'),
            ('Ældste ventende afsendelse (sekunder)', 'aj_outbox_oldest_seconds', 'timeseries', 'Age of oldest outbox event.'),
            ('Venter i RabbitMQ (Ready)', 'rabbitmq_queue_messages_ready', 'timeseries', 'Accepted by broker, waiting to be fetched.'),
            ('Under behandling (Unacked)', 'rabbitmq_queue_messages_unacked', 'timeseries', 'Delivered, awaiting consumer acknowledgement. Fast processing may occur between scrapes.'),
            ('Sendt til broker / sekund', 'sum(rate(rabbitmq_global_messages_received_total[5m]))', 'timeseries', 'Publish rate, not HTTP requests.'),
            ('Kvitteret af broker / sekund', 'sum(rate(rabbitmq_global_messages_confirmed_total[5m]))', 'timeseries', 'Publisher confirms; does not mean business processing is complete.'),
            ('Behandlet og kvitteret / sekund', 'sum(rate(rabbitmq_global_messages_acknowledged_total[5m]))', 'timeseries', 'Consumer acknowledgements across queues.'),
            ('Modtagne beskeder siden broker-start', 'sum(rabbitmq_global_messages_received_total)', 'stat', 'Broker counter resets when RabbitMQ restarts; historical samples remain in Prometheus.'),
            ('Kvitterede beskeder siden broker-start', 'sum(rabbitmq_global_messages_acknowledged_total)', 'stat', 'Consumer acknowledgements; one event can be delivered to multiple queues.'),
            ('Afviste beskeder (dead letters)', 'rabbitmq_queue_messages{queue="aj.dead"}', 'timeseries', 'Requires operator review; no automatic purge.'),
            ('Beskedhistorik', '{project="aj"} | json | event=~"broker.*"', 'logs', 'Search eventId or correlationId to follow a business event.')]),
        dashboard('aj-logs', 'AJ · Logs og fejlsøgning', [
            ('Applikationslogs', '{project="aj"}', 'logs', 'Use Explore to filter service or correlationId.'),
            ('HTTP-fejl', '{project="aj"} | json | status >= 400', 'logs', 'Structured request failures.'),
            ('Beskeder behandlet', '{project="aj"} | json | event="broker.processed"', 'logs', 'Successful message handling.'),
            ('Fejl og genforsøg', '{project="aj"} |~ "(?i)error|exception|retry|rejected"', 'logs', 'Operational errors and retries.')]),
        dashboard('aj-kubernetes', 'AJ · Kubernetes drift', [
            ('Ønskede replikaer', 'kube_deployment_spec_replicas{namespace="aj"}', 'timeseries', 'Desired number of application pods.'),
            ('Tilgængelige replikaer', 'kube_deployment_status_replicas_available{namespace="aj"}', 'timeseries', 'Ready and available pods.'),
            ('Container-genstarter', 'kube_pod_container_status_restarts_total{namespace="aj"}', 'timeseries', 'Investigate increasing restarts.'),
            ('Pods klar', 'kube_pod_status_ready{namespace="aj",condition="true"}', 'timeseries', 'Readiness determines traffic routing.'),
            ('HPA ønskede replikaer', 'kube_horizontalpodautoscaler_status_desired_replicas{namespace="aj"}', 'timeseries', 'CPU autoscaling target.'),
            ('Vedvarende lagring', 'kube_persistentvolumeclaim_status_phase{namespace="aj",phase="Bound"}', 'stat', '1 means the data volume is bound.')])]
    destination = ROOT / 'dashboards'
    destination.mkdir(exist_ok=True)
    for item in dashboards:
        (destination / (item['uid'] + '.json')).write_text(json.dumps(item, indent=2) + '\n', encoding='utf-8')
    from gui_hub import main as generate_hub
    generate_hub()


if __name__ == '__main__':
    main()
