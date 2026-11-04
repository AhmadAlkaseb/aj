"""Repeatable local TLS/Kubernetes demonstration, isolated from Compose volumes.

python scripts/kubernetes.py start|verify|stop
Start stops the root Compose containers to fit the agreed 16 GB workstation budget.
Stop deletes only the named kind demonstration cluster and restores Compose.
"""
import json
import os
import secrets
import shutil
import ssl
import subprocess
import sys
import time
import urllib.request
from pathlib import Path

ROOT=Path(__file__).resolve().parent.parent
RUNTIME=ROOT/'test-results/k8s'
KIND=ROOT/'test-results/tools/kind.exe' if os.name=='nt' else Path(shutil.which('kind') or 'kind')
CONTEXT='kind-aj-exam'


def run(args,data=None,check=True):
    result=subprocess.run([str(x) for x in args],cwd=ROOT,input=data,capture_output=True,check=check)
    if result.stdout: print(result.stdout.decode(errors='replace').strip(),flush=True)
    if result.returncode and result.stderr: print(result.stderr.decode(errors='replace'),file=sys.stderr)
    return result


def kube(*args,data=None,check=True):return run(['kubectl','--context',CONTEXT,*args],data,check)


def start():
    RUNTIME.mkdir(parents=True,exist_ok=True)
    run([sys.executable,'k8s/generate.py'])
    run(['docker','compose','stop'])
    existing=run([KIND,'get','clusters']).stdout.decode().splitlines()
    if 'aj-exam' not in existing:
        config={'kind':'Cluster','apiVersion':'kind.x-k8s.io/v1alpha4','featureGates':{'MutatingAdmissionPolicy':True},
            'networking':{'disableDefaultCNI':True,'podSubnet':'10.244.0.0/16'},
            'nodes':[{'role':'control-plane','extraPortMappings':[{'containerPort':30443,'hostPort':8443,'listenAddress':'127.0.0.1','protocol':'TCP'}]}]}
        config_path=RUNTIME/'kind.json';config_path.write_text(json.dumps(config),encoding='utf-8')
        run([KIND,'create','cluster','--name','aj-exam','--image','kindest/node:v1.35.0','--config',config_path])
    kube('apply','--server-side','-f','https://raw.githubusercontent.com/projectcalico/calico/v3.33.0/manifests/tigera-operator.yaml')
    installation={'apiVersion':'operator.tigera.io/v1','kind':'Installation','metadata':{'name':'default'},'spec':{'kubernetesProvider':'Kind','calicoNetwork':{'ipPools':[{'blockSize':26,'cidr':'10.244.0.0/16','encapsulation':'VXLAN','natOutgoing':'Enabled','nodeSelector':'all()'}]}}}
    kube('apply','-f','-',data=json.dumps(installation).encode())
    kube('wait','--for=condition=Ready','node','--all','--timeout=300s')
    kube('apply','--server-side','-f','https://github.com/kedacore/keda/releases/download/v2.21.0/keda-2.21.0.yaml')
    kube('wait','--for=condition=Established','crd/scaledjobs.keda.sh','--timeout=120s')
    kube('apply','-f','https://github.com/kubernetes-sigs/metrics-server/releases/download/v0.9.0/components.yaml')
    kube('patch','deployment','metrics-server','-n','kube-system','--type=json','-p',json.dumps([{'op':'add','path':'/spec/template/spec/containers/0/args/-','value':'--kubelet-insecure-tls'}]))
    images=['aj-'+s+':local' for s in ['users','products','orders','payments','product-view','product-synchronizer','apigateway','frontend','confirmation-job']]
    run([KIND,'load','docker-image','--name','aj-exam',*images])
    kube('create','namespace','aj',check=False)
    secret_path=RUNTIME/'secrets.json'
    if secret_path.exists():values=json.loads(secret_path.read_text(encoding='utf-8'))
    else:
        values={key:secrets.token_urlsafe(24) for key in ['database-password','rabbit-password','identity-admin-password','projection-token','confirmation-token','test-client-secret']}
        values['rabbit-url']='amqp://aj:'+values['rabbit-password']+'@rabbitmq:5672/%2f'
        secret_path.write_text(json.dumps(values),encoding='utf-8')
    kube('apply','-f','-',data=json.dumps({'apiVersion':'v1','kind':'Secret','metadata':{'name':'aj-secrets','namespace':'aj'},'type':'Opaque','stringData':values}).encode())
    cert=RUNTIME/'localhost.crt';key=RUNTIME/'localhost.key'
    if not cert.exists():run(['openssl','req','-x509','-newkey','rsa:2048','-nodes','-keyout',key,'-out',cert,'-days','365','-subj','/CN=localhost','-addext','subjectAltName=DNS:localhost,IP:127.0.0.1'])
    import base64
    tls={'apiVersion':'v1','kind':'Secret','metadata':{'name':'aj-tls','namespace':'aj'},'type':'kubernetes.io/tls',
        'data':{'tls.crt':base64.b64encode(cert.read_bytes()).decode(),'tls.key':base64.b64encode(key.read_bytes()).decode()}}
    kube('apply','-f','-',data=json.dumps(tls).encode())
    kube('apply','-f','k8s/base.json')
    deployments=json.loads(kube('get','deployments','-n','aj','-o','json').stdout)
    for deployment in deployments['items']:kube('rollout','status','deployment/'+deployment['metadata']['name'],'-n','aj','--timeout=420s')
    configure()
    print('Local cluster ready at https://localhost:8443 (development certificate).',flush=True)


def configure():
    values=json.loads((RUNTIME/'secrets.json').read_text(encoding='utf-8'))
    context=ssl.create_default_context(cafile=str(RUNTIME/'localhost.crt'))
    urllib.request.install_opener(urllib.request.build_opener(urllib.request.HTTPSHandler(context=context)))
    os.environ.update({'KEYCLOAK_ADMIN_URL':'https://localhost:8443/identity','KEYCLOAK_ADMIN_PASSWORD':values['identity-admin-password'],
        'KEYCLOAK_TEST_URL':'https://localhost:8443/identity','AJ_TEST_CLIENT_SECRET':values['test-client-secret']})
    import configure_identity
    configure_identity.BASE=os.environ['KEYCLOAK_ADMIN_URL'];configure_identity.configure()


def verify():
    configure()
    from test_identity import admin_headers
    def api(path,method='GET',data=None,binary=False):
        request=urllib.request.Request('https://localhost:8443/api/v1'+path,method=method,
            data=None if data is None else json.dumps(data).encode(),headers={'Content-Type':'application/json',**admin_headers()})
        with urllib.request.urlopen(request,timeout=20) as response:return response.read() if binary else json.load(response)
    def wait(fn,seconds=120):
        end=time.monotonic()+seconds
        while time.monotonic()<end:
            try:
                result=fn()
                if result:return result
            except (OSError,AssertionError):pass
            time.sleep(2)
        raise AssertionError('Kubernetes verification timed out')
    wait(lambda:api('/status')['status']=='UP',180)
    user=api('/users')[0]
    car=api('/products','POST',{'brand':'Kubernetes','model':'TLS and KEDA test','price':123456,'year':2026})
    wait(lambda:api('/products/'+car['guid'])['guid']==car['guid'])
    order=api('/orders','POST',{'userGuid':user['guid'],'productGuid':car['guid'],'shippingAddress':'Local cluster test'})
    wait(lambda:api('/orders/'+order['guid'])['status']=='RESERVED')
    payment=api('/payments','POST',{'orderGuid':order['guid']})
    wait(lambda:api('/payments/'+payment['guid'])['status']=='PENDING')
    api('/payments/'+payment['guid'],'PUT',{'status':'COMPLETED'})
    wait(lambda:api('/orders/'+order['guid'])['status']=='COMPLETED')
    pdf=wait(lambda:api('/orders/'+order['guid']+'/confirmation',binary=True))
    assert pdf.startswith(b'%PDF-');(RUNTIME/'confirmation.pdf').write_bytes(pdf)
    jobs=json.loads(kube('get','jobs','-n','aj','-o','json').stdout)
    assert any(j.get('status',{}).get('succeeded',0)>0 for j in jobs['items'])
    # Negative CNI test: unrelated pods cannot connect to another service's database.
    probe=kube('run','ownership-probe','-n','aj','--image=busybox:1.37','--restart=Never','--rm','-i','--attach=true','--command','--','sh','-c','nc -z -w 3 users-db 5432',check=False)
    assert probe.returncode!=0,'Database isolation policy was not enforced'
    metrics=json.loads(kube('get','hpa','frontend','-n','aj','-o','json').stdout)
    result={'tls':'PASS','commerce':'PASS','kedaJob':'PASS','pdfBytes':len(pdf),'databaseIsolation':'PASS','hpaStatus':metrics.get('status',{})}
    (ROOT/'test-results/kubernetes-results.json').write_text(json.dumps(result,indent=2),encoding='utf-8');print(json.dumps(result,indent=2))


def stop():
    run([KIND,'delete','cluster','--name','aj-exam'])
    run(['docker','compose','up','-d','--wait','--wait-timeout','180'])


if __name__=='__main__':
    {'start':start,'verify':verify,'stop':stop}[sys.argv[1]]()
