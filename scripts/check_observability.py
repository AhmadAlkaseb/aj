"""Verify real metrics, central logs, cross-service correlation and an outage alert."""
import base64
import json
import subprocess
import sys
import time
import urllib.parse
import urllib.request
from pathlib import Path
FAILURES='--failures' in sys.argv
if FAILURES:sys.argv.remove('--failures')
from smoke import api,wait_for,compose

ROOT=Path(__file__).resolve().parent.parent


def get(url):
    with urllib.request.urlopen(url,timeout=10) as r:return json.load(r)


def main():
    subprocess.run(['docker','compose','-f','compose.observability.yml','up','-d'],cwd=ROOT,check=True,stdout=subprocess.DEVNULL)
    wait_for(lambda:len([t for t in get('http://127.0.0.1:9090/api/v1/targets')['data']['activeTargets'] if t['health']=='up'])>=9,120)
    credentials=base64.b64encode(b'admin:aj-monitoring-local').decode()
    request=urllib.request.Request('http://127.0.0.1:13001/api/dashboards/uid/aj-platform',headers={'Authorization':'Basic '+credentials})
    with urllib.request.urlopen(request,timeout=15) as r:assert json.load(r)['dashboard']['title']=='AJ Motors operations'
    user=api('/users')[0]
    car=api('/products','POST',{'brand':'Observability','model':'Correlated trace','price':12345,'year':2026},201)
    order=api('/orders','POST',{'userGuid':user['guid'],'productGuid':car['guid'],'shippingAddress':'Monitoring test'},201)
    wait_for(lambda:api('/orders/'+order['guid'])['status']=='RESERVED')
    def trace():
        query='{project="aj"} |= "'+order['guid']+'"'
        url='http://127.0.0.1:3100/loki/api/v1/query_range?'+urllib.parse.urlencode({'query':query,'limit':100,'start':str(time.time_ns()-300_000_000_000)})
        rows=get(url)['data']['result'];services=set()
        for stream in rows:
            for timestamp,line in stream['values']:
                try:
                    data=json.loads(line);service=data.get('service') or data.get('queue','').removeprefix('aj.')
                    if data.get('correlationId')==order['guid']:services.add(service)
                except ValueError:pass
        return sorted(services) if {'users','products','orders'}<=services else None
    services=wait_for(trace,120)
    api('/orders/'+order['guid'],'PUT',{'status':'CANCELLED'})
    alerts=[]
    if FAILURES:
        try:
            compose('stop','rabbitmq')
            api('/products','POST',{'brand':'Monitoring','model':'Outbox outage','price':9876,'year':2026},201)
            def firing():
                return [a['labels']['alertname'] for a in get('http://127.0.0.1:9090/api/v1/alerts')['data']['alerts'] if a['state']=='firing' and a['labels']['alertname'] in ['AJDependencyDown','AJOutboxDelayed']]
            alerts=wait_for(firing,90)
        finally:compose('start','rabbitmq')
        wait_for(lambda:api('/status')['status']=='UP',120)
    result={'result':'PASS','metricsTargets':9,'grafanaDashboard':'PASS','centralLogs':'PASS','correlatedServices':services,'outageAlerts':alerts}
    (ROOT/'test-results/observability-results.json').write_text(json.dumps(result,indent=2),encoding='utf-8');print(json.dumps(result,indent=2))


if __name__=='__main__':main()
