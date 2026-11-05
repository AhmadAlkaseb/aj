"""Self-starting backend cooperation fixture: no frontend or gateway.

Starts Users, Orders, Products, their private PostgreSQL stores, RabbitMQ and a local
identity provider under a unique Compose project, then removes only its own volumes.
"""
import copy
import json
import os
import subprocess
import sys
import time
import urllib.request
import uuid
from pathlib import Path

ROOT=Path(__file__).resolve().parent.parent
OUT=ROOT/'test-results/cooperation'


def main():
    OUT.mkdir(parents=True,exist_ok=True)
    project='aj-cooperation-'+uuid.uuid4().hex[:8]
    source=json.loads(subprocess.check_output(['docker','compose','config','--format','json'],cwd=ROOT))
    names=['users','products','orders','payments','users-db','products-db','orders-db','payments-db','rabbitmq','keycloak','keycloak-db']
    config={'name':project,'services':{n:copy.deepcopy(source['services'][n]) for n in names},'networks':{},'volumes':{}}
    for name,service in config['services'].items():
        service.pop('build',None);service.pop('restart',None)
        if name in ['users','products','orders','payments']:service['environment']['JAVA_TOOL_OPTIONS']='-Xms64m -Xmx256m'
        if name=='keycloak':service['environment']['JAVA_OPTS_KC_HEAP']='-Xms128m -Xmx384m'
        if name in ['users','products','orders','payments']:service['depends_on']['rabbitmq']={'condition':'service_healthy'}
        if name in ['keycloak','rabbitmq']:service['healthcheck'].update({'start_period':'120s','retries':30})
        service['ports']=[{'target':8180 if name=='keycloak' else 15672 if name=='rabbitmq' else 7000,'published':'0','host_ip':'127.0.0.1'}] if name in ['users','products','orders','payments','keycloak','rabbitmq'] else []
        for network in service.get('networks',{}):config['networks'][network]={}
        for volume in service.get('volumes',[]):
            if volume['type']=='volume':config['volumes'][volume['source']]={}
    path=OUT/'compose.json';path.write_text(json.dumps(config,indent=2),encoding='utf-8')
    command=['docker','compose','-p',project,'-f',str(path)]
    def compose(*args):return subprocess.run([*command,*args],cwd=ROOT,check=True,stdout=subprocess.PIPE).stdout
    def url(service,port):return 'http://'+compose('port',service,str(port)).decode().strip()
    exclusive='--exclusive' in sys.argv
    if exclusive:subprocess.run(['docker','compose','stop'],cwd=ROOT,check=True,stdout=subprocess.DEVNULL)
    try:
        compose('up','-d','--wait','--wait-timeout','1200')
        base=url('keycloak',8180)
        os.environ['KEYCLOAK_TEST_URL']=base
        import configure_identity
        configure_identity.BASE=base;configure_identity.configure()
        from test_identity import admin_headers
        def api(service,path,method='GET',value=None):
            request=urllib.request.Request(url(service,7000)+'/api/v1'+path,method=method,
                data=None if value is None else json.dumps(value).encode(),headers={'Content-Type':'application/json',**admin_headers()})
            with urllib.request.urlopen(request,timeout=15) as response:return json.load(response)
        user=api('users','/users')[0]
        car=api('products','/products','POST',{'brand':'Cooperation','model':project,'price':98765.43,'year':2026})
        order=api('orders','/orders','POST',{'userGuid':user['guid'],'productGuid':car['guid'],'shippingAddress':'Isolated backend fixture','totalAmount':1})
        end=time.monotonic()+60
        while time.monotonic()<end:
            confirmed=api('orders','/orders/'+order['guid'])
            if confirmed['status']=='RESERVED':break
            time.sleep(.5)
        else:raise AssertionError('Reservation cooperation did not finish')
        assert confirmed['totalAmount']==98765.43
        assert api('products','/products/'+car['guid'])['status']=='RESERVED'
        payment=api('payments','/payments','POST',{'orderGuid':order['guid']})
        end=time.monotonic()+30
        while api('payments','/payments/'+payment['guid'])['status']!='PENDING':
            assert time.monotonic()<end;time.sleep(.5)
        api('payments','/payments/'+payment['guid'],'PUT',{'status':'FAILED'})
        end=time.monotonic()+30
        while api('products','/products/'+car['guid'])['status']!='AVAILABLE':
            assert time.monotonic()<end;time.sleep(.5)
        assert api('orders','/orders/'+order['guid'])['status']=='FAILED'
        versions={}
        for owner in ['users','products','orders','payments']:
            observed=compose('exec','-T',owner+'-db','psql','-U','postgres','-d',owner,'-Atc',"select max(version::int) from flyway_schema_history where success").decode().strip()
            versions[owner]=observed
            processed=int(compose('exec','-T',owner+'-db','psql','-U','postgres','-d',owner,'-Atc','select count(*) from event_inbox').decode().strip())
            assert processed>0,(owner,'No processed broker messages')
        result={'result':'PASS','project':project,'backends':['users','products','orders','payments'],'freshDatabaseVersions':versions,
            'reservation':'RabbitMQ request/reply','authoritativePrice':'PASS','compensation':'PASS','frontendOrGateway':False}
        (OUT/'result.json').write_text(json.dumps(result,indent=2),encoding='utf-8');print(json.dumps(result,indent=2))
    finally:
        try:(OUT/'containers.log').write_bytes(compose('logs','--no-color'))
        finally:
            assert project.startswith('aj-cooperation-')
            compose('down','--volumes','--remove-orphans')
            if exclusive:subprocess.run(['docker','compose','up','-d','--wait','--wait-timeout','240'],cwd=ROOT,check=True,stdout=subprocess.DEVNULL)


if __name__=='__main__':main()
