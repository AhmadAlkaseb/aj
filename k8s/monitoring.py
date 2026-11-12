"""Monitoring resources for the local aj namespace; shared Grafana dashboards."""
import hashlib
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent


def resources():
    items = []
    def add(kind, name, spec=None, api='v1', **extra):
        item = {'apiVersion': api, 'kind': kind, 'metadata': {'name': name, 'namespace': 'aj'}, **extra}
        if spec is not None:
            item['spec'] = spec
        items.append(item)
        return item

    def config(name, data):
        add('ConfigMap', name, data=data)
        return {'name': name, 'configMap': {'name': name}}

    def rbac(name, rules):
        add('ServiceAccount', name)
        add('Role', name, api='rbac.authorization.k8s.io/v1', rules=rules)
        add('RoleBinding', name, api='rbac.authorization.k8s.io/v1',
            roleRef={'apiGroup': 'rbac.authorization.k8s.io', 'kind': 'Role', 'name': name},
            subjects=[{'kind': 'ServiceAccount', 'name': name, 'namespace': 'aj'}])

    def deploy(name, image, port, args=None, env=None, mounts=None, volumes=None, account=None, path=None, data_path=None):
        container = {'name': name, 'image': image, 'ports': [{'containerPort': port}],
            'resources': {'requests': {'cpu': '25m', 'memory': '64Mi'}, 'limits': {'cpu': '500m', 'memory': '512Mi'}}}
        if args:
            container['args'] = args
        if env:
            container['env'] = env
        mounts = list(mounts or [])
        volumes = list(volumes or [])
        if data_path:
            add('PersistentVolumeClaim', name + '-data', {'accessModes': ['ReadWriteOnce'], 'resources': {'requests': {'storage': '2Gi'}}})
            volumes.append({'name': 'data', 'persistentVolumeClaim': {'claimName': name + '-data'}})
            mounts.append({'name': 'data', 'mountPath': data_path})
        if mounts:
            container['volumeMounts'] = mounts
        if path:
            container['readinessProbe'] = {'httpGet': {'path': path, 'port': port}, 'periodSeconds': 5}
            container['startupProbe'] = {'httpGet': {'path': path, 'port': port}, 'periodSeconds': 5, 'failureThreshold': 60}
            container['livenessProbe'] = {'httpGet': {'path': path, 'port': port}, 'periodSeconds': 15, 'failureThreshold': 5}
        pod = {'containers': [container], 'volumes': volumes, 'securityContext': {'fsGroup': 472 if name == 'grafana' else 10001}}
        if account:
            pod['serviceAccountName'] = account
        config_names = {v['configMap']['name'] for v in volumes if 'configMap' in v}
        checksum = hashlib.sha256(json.dumps([i for i in items if i['kind'] == 'ConfigMap' and i['metadata']['name'] in config_names], sort_keys=True).encode()).hexdigest()
        add('Deployment', name, {'replicas': 1, 'strategy': {'type': 'Recreate'}, 'selector': {'matchLabels': {'app': name}},
            'template': {'metadata': {'labels': {'app': name, 'tier': 'monitoring'}, 'annotations': {'config-checksum': checksum}}, 'spec': pod}}, 'apps/v1')
        add('Service', name, {'selector': {'app': name}, 'ports': [{'port': port, 'targetPort': port}]})

    def mount(name, path):
        return {'name': name, 'mountPath': path, 'readOnly': True}

    obs = ROOT / 'observability'
    prometheus = json.loads((obs / 'prometheus.yml').read_text())
    # Pod discovery keeps every replica visible when HPA scales a deployment.
    prometheus['scrape_configs'][0] = {'job_name': 'aj-applications',
        'kubernetes_sd_configs': [{'role': 'pod', 'namespaces': {'names': ['aj']}}],
        'relabel_configs': [
            {'source_labels': ['__meta_kubernetes_pod_label_tier'], 'regex': 'application', 'action': 'keep'},
            {'source_labels': ['__meta_kubernetes_pod_container_port_number'], 'regex': '7000', 'action': 'keep'},
            {'source_labels': ['__meta_kubernetes_pod_label_app'], 'target_label': 'service'},
            {'source_labels': ['__meta_kubernetes_pod_name'], 'target_label': 'pod'}]}
    prometheus['scrape_configs'].append({'job_name': 'kube-state-metrics', 'static_configs': [{'targets': ['kube-state-metrics:8080']}]})
    pv = config('prometheus-config', {'prometheus.yml': json.dumps(prometheus), 'alerts.yml': (obs / 'alerts.yml').read_text()})
    rbac('prometheus', [{'apiGroups': [''], 'resources': ['pods'], 'verbs': ['get', 'list', 'watch']}])
    deploy('prometheus', 'prom/prometheus:v3.2.1', 9090,
        ['--config.file=/etc/prometheus/prometheus.yml', '--storage.tsdb.path=/prometheus', '--storage.tsdb.retention.time=7d'],
        mounts=[mount('prometheus-config', '/etc/prometheus')], volumes=[pv], account='prometheus', path='/-/ready', data_path='/prometheus')
    lv = config('loki-config', {'loki.yml': (obs / 'loki.yml').read_text()})
    deploy('loki', 'grafana/loki:3.4.2', 3100, ['-config.file=/etc/loki/loki.yml'],
        mounts=[mount('loki-config', '/etc/loki')], volumes=[lv], path='/ready', data_path='/loki')
    gv = config('grafana-datasources', {'aj.yml': (obs / 'grafana/datasources/aj.yml').read_text()})
    gp = config('grafana-provider', {'aj.yml': (obs / 'grafana/dashboards/aj.yml').read_text()})
    gd = config('grafana-dashboards', {'aj.json': (obs / 'dashboard.json').read_text(),
        **{p.name: p.read_text(encoding='utf-8') for p in (obs / 'dashboards').glob('*.json')}})
    deploy('grafana', 'grafana/grafana:11.5.2', 3000,
        env=[{'name': 'GF_SECURITY_ADMIN_USER', 'value': 'admin'},
             {'name': 'GF_DASHBOARDS_DEFAULT_HOME_DASHBOARD_PATH', 'value': '/var/lib/grafana/dashboards/aj-home.json'},
             {'name': 'GF_SECURITY_ADMIN_PASSWORD', 'valueFrom': {'secretKeyRef': {'name': 'aj-secrets', 'key': 'grafana-password'}}}],
        mounts=[mount('grafana-datasources', '/etc/grafana/provisioning/datasources'),
                mount('grafana-provider', '/etc/grafana/provisioning/dashboards'), mount('grafana-dashboards', '/var/lib/grafana/dashboards')],
        volumes=[gv, gp, gd], path='/api/health', data_path='/var/lib/grafana')
    av = config('alloy-config', {'config.alloy': (obs / 'kubernetes.alloy').read_text()})
    rbac('alloy', [{'apiGroups': [''], 'resources': ['pods', 'pods/log'], 'verbs': ['get', 'list', 'watch']}])
    deploy('alloy', 'grafana/alloy:v1.8.0', 12345,
        ['run', '--server.http.listen-addr=0.0.0.0:12345', '--storage.path=/var/lib/alloy/data', '/etc/alloy/config.alloy'],
        mounts=[mount('alloy-config', '/etc/alloy')], volumes=[av], account='alloy', path='/-/ready', data_path='/var/lib/alloy/data')
    rbac('kube-state-metrics', [
        {'apiGroups': [''], 'resources': ['pods', 'persistentvolumeclaims'], 'verbs': ['list', 'watch']},
        {'apiGroups': ['apps'], 'resources': ['deployments', 'replicasets'], 'verbs': ['list', 'watch']},
        {'apiGroups': ['autoscaling'], 'resources': ['horizontalpodautoscalers'], 'verbs': ['list', 'watch']},
        {'apiGroups': ['batch'], 'resources': ['jobs'], 'verbs': ['list', 'watch']}])
    deploy('kube-state-metrics', 'registry.k8s.io/kube-state-metrics/kube-state-metrics:v2.18.0', 8080,
        ['--namespaces=aj', '--resources=pods,persistentvolumeclaims,deployments,replicasets,horizontalpodautoscalers,jobs'], account='kube-state-metrics', path='/healthz')
    # Monitoring can scrape apps, but receives no access to private database ports.
    add('NetworkPolicy', 'monitoring-scrapes', {'podSelector': {'matchLabels': {'tier': 'application'}}, 'policyTypes': ['Ingress'],
        'ingress': [{'from': [{'podSelector': {'matchLabels': {'app': 'prometheus'}}}], 'ports': [{'port': 7000}]}]}, 'networking.k8s.io/v1')
    add('NetworkPolicy', 'monitoring-broker', {'podSelector': {'matchLabels': {'app': 'rabbitmq'}}, 'policyTypes': ['Ingress'],
        'ingress': [{'from': [{'podSelector': {'matchLabels': {'app': 'prometheus'}}}], 'ports': [{'port': 15692}]}]}, 'networking.k8s.io/v1')
    add('NetworkPolicy', 'monitoring-internal', {'podSelector': {'matchLabels': {'tier': 'monitoring'}}, 'policyTypes': ['Ingress'],
        'ingress': [{'from': [{'podSelector': {'matchLabels': {'tier': 'monitoring'}}}]}]}, 'networking.k8s.io/v1')
    return items
