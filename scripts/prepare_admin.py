"""Prepare a read-only Kubernetes GUI while Compose serves the application."""
import base64
import json
import os
import subprocess
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
RUNTIME = ROOT / 'test-results/admin'
CONTEXT = 'kind-aj-exam'


def run(*args, data=None, check=True):
    result = subprocess.run(list(args), cwd=ROOT, input=data, capture_output=True, timeout=360)
    if check and result.returncode:
        raise RuntimeError('Administration setup failed: ' + ' '.join(args[:5]) + '\n' + result.stderr.decode(errors='replace')[:600])
    return result


def kube(*args, data=None, check=True):
    return run('kubectl', '--context', CONTEXT, *args, data=data, check=check)


def prepare(standby=True):
    RUNTIME.mkdir(parents=True, exist_ok=True)
    path = RUNTIME / 'kubeconfig.json'
    empty = {'apiVersion': 'v1', 'kind': 'Config', 'clusters': [], 'contexts': [], 'users': [], 'current-context': ''}
    node = run('docker', 'inspect', 'aj-exam-control-plane', check=False)
    if os.environ.get('CI') == 'true' or node.returncode:
        path.write_text(json.dumps(empty), encoding='utf-8')
        print('Kubernetes GUI prepared; no local kind cluster is available.', flush=True)
        return
    print('Preparing Kubernetes read-only GUI; Compose remains the application runtime.', flush=True)
    run('docker', 'start', 'aj-exam-control-plane')
    kube('wait', '--for=condition=Ready', 'node', '--all', '--timeout=300s')
    if standby and kube('get', 'namespace', 'aj', check=False).returncode == 0:
        deployments = json.loads(kube('get', 'deployments', '-n', 'aj', '-o', 'json').stdout)['items']
        snapshot = {d['metadata']['name']: d['spec'].get('replicas', 1) for d in deployments}
        if any(snapshot.values()):
            (RUNTIME / 'standby-replicas.json').write_text(json.dumps(snapshot, indent=2), encoding='utf-8')
        kube('annotate', 'scaledjob', 'confirmation', '-n', 'aj', 'autoscaling.keda.sh/paused=true', '--overwrite', check=False)
        kube('scale', 'deployment', '--all', '-n', 'aj', '--replicas=0')
        print('Kubernetes application workloads are in standby; definitions and PVCs are retained.', flush=True)
    account = 'aj-gui-viewer'
    role = {'apiVersion': 'rbac.authorization.k8s.io/v1', 'kind': 'ClusterRole', 'metadata': {'name': account}, 'rules': [
        {'apiGroups': [''], 'resources': ['namespaces', 'nodes', 'pods', 'pods/log', 'services', 'endpoints', 'persistentvolumeclaims', 'persistentvolumes', 'events'], 'verbs': ['get', 'list', 'watch']},
        {'apiGroups': ['apps'], 'resources': ['deployments', 'replicasets', 'statefulsets', 'daemonsets'], 'verbs': ['get', 'list', 'watch']},
        {'apiGroups': ['batch'], 'resources': ['jobs', 'cronjobs'], 'verbs': ['get', 'list', 'watch']},
        {'apiGroups': ['autoscaling'], 'resources': ['horizontalpodautoscalers'], 'verbs': ['get', 'list', 'watch']},
        {'apiGroups': ['networking.k8s.io'], 'resources': ['networkpolicies', 'ingresses', 'ingressclasses'], 'verbs': ['get', 'list', 'watch']},
        {'apiGroups': ['metrics.k8s.io'], 'resources': ['nodes', 'pods'], 'verbs': ['get', 'list', 'watch']},
        {'apiGroups': ['keda.sh'], 'resources': ['scaledjobs', 'scaledobjects'], 'verbs': ['get', 'list', 'watch']},
        {'apiGroups': ['apiextensions.k8s.io'], 'resources': ['customresourcedefinitions'], 'verbs': ['get', 'list', 'watch']}]}
    objects = [
        {'apiVersion': 'v1', 'kind': 'ServiceAccount', 'metadata': {'name': account, 'namespace': 'kube-system'}},
        role,
        {'apiVersion': 'rbac.authorization.k8s.io/v1', 'kind': 'ClusterRoleBinding', 'metadata': {'name': account},
         'roleRef': {'apiGroup': 'rbac.authorization.k8s.io', 'kind': 'ClusterRole', 'name': account},
         'subjects': [{'kind': 'ServiceAccount', 'name': account, 'namespace': 'kube-system'}]},
        {'apiVersion': 'v1', 'kind': 'Secret', 'metadata': {'name': account, 'namespace': 'kube-system',
         'annotations': {'kubernetes.io/service-account.name': account}}, 'type': 'kubernetes.io/service-account-token'}]
    kube('apply', '-f', '-', data=json.dumps({'apiVersion': 'v1', 'kind': 'List', 'items': objects}).encode())
    secret = None
    for _ in range(30):
        secret = json.loads(kube('get', 'secret', account, '-n', 'kube-system', '-o', 'json').stdout)
        if secret.get('data', {}).get('token'):
            break
        time.sleep(1)
    if not secret.get('data', {}).get('token'):
        raise RuntimeError('Kubernetes did not issue the read-only GUI token.')
    current = json.loads(kube('config', 'view', '--minify', '--raw', '--flatten', '-o', 'json').stdout)
    ca = current['clusters'][0]['cluster']['certificate-authority-data']
    configuration = {'apiVersion': 'v1', 'kind': 'Config',
        'clusters': [{'name': 'aj', 'cluster': {'server': 'https://aj-exam-control-plane:6443', 'tls-server-name': 'kubernetes', 'certificate-authority-data': ca}}],
        'contexts': [{'name': CONTEXT, 'context': {'cluster': 'aj', 'user': account, 'namespace': 'aj'}}],
        'current-context': CONTEXT,
        'users': [{'name': account, 'user': {'token': base64.b64decode(secret['data']['token']).decode()}}]}
    path.write_text(json.dumps(configuration), encoding='utf-8')
    print('Kubernetes GUI configured with read-only access and verified cluster TLS.', flush=True)


def connect():
    if run('docker', 'network', 'inspect', 'kind', check=False).returncode:
        return
    container = run('docker', 'inspect', 'kubernetes-gui', check=False)
    if not container.returncode and 'kind' not in json.loads(container.stdout)[0]['NetworkSettings']['Networks']:
        run('docker', 'network', 'connect', 'kind', 'kubernetes-gui')


if __name__ == '__main__':
    prepare()
