"""Real 10,000-car / 20-client GraphQL benchmark in an isolated Mongo database."""
import concurrent.futures
import json
import statistics
import subprocess
import time
import urllib.request
import uuid
from pathlib import Path

ROOT=Path(__file__).resolve().parent.parent


def run(*args,**kwargs):
    return subprocess.run(args,cwd=ROOT,check=True,capture_output=True,**kwargs).stdout


def main():
    suffix=uuid.uuid4().hex[:12];database='performance_test_'+suffix;container='aj-performance-'+suffix
    config=json.loads(run('docker','compose','config','--format','json'))
    network=next(config['networks'][n]['name'] for n in config['services']['product-view-mongo']['networks'])
    assert database.startswith('performance_test_')
    seed='''const target=db.getSiblingDB("'''+database+'''");const batch=[];
    for(let i=0;i<10000;i++)batch.push({guid:"00000000-0000-4000-8000-"+i.toString(16).padStart(12,"0"),brand:"Benchmark",model:"Car "+i,year:2024,price:NumberDecimal("234567.89"),currency:"DKK",status:"AVAILABLE",mileage:i,createdAt:"2026-11-04T10:00:00Z",updatedAt:"2026-11-04T10:00:00Z",version:0,deleted:false});
    target.products.insertMany(batch);target.products.createIndex({status:1,createdAt:-1,guid:1});
    print(target.products.countDocuments({}));'''
    run('docker','compose','exec','-T','product-view-mongo','mongosh','--quiet','--file','/dev/stdin',input=seed.encode())
    try:
        run('docker','run','-d','--rm','--name',container,'--network',network,'-e','PORT=7000',
            '-e','MONGO_URI=mongodb://product-view-mongo:27017','-e','MONGO_DB='+database,'-p','127.0.0.1:17006:7000','aj-product-view:local')
        for _ in range(90):
            try:
                if urllib.request.urlopen('http://127.0.0.1:17006/health',timeout=2).status==200:break
            except OSError:time.sleep(1)
        else:raise AssertionError('Benchmark backend did not start')
        body=json.dumps({'query':'{products(limit:24,filter:{status:"AVAILABLE"}){total hasMore items{guid brand model price year mileage}}}'}).encode()
        def query(_):
            start=time.perf_counter()
            request=urllib.request.Request('http://127.0.0.1:17006/api/v1/graphql',body,headers={'Content-Type':'application/json'})
            with urllib.request.urlopen(request,timeout=30) as response:data=json.load(response)
            assert not data.get('errors') and data['data']['products']['total']==10000
            assert len(data['data']['products']['items'])==24
            return time.perf_counter()-start
        for i in range(5):query(i)
        with concurrent.futures.ThreadPoolExecutor(max_workers=20) as pool:samples=list(pool.map(query,range(200)))
        ordered=sorted(samples);p95=ordered[int(.95*len(ordered))-1]
        result={'cars':10000,'concurrentClients':20,'requests':len(samples),'medianSeconds':statistics.median(samples),
                'p95Seconds':p95,'maxSeconds':max(samples),'targetSeconds':2,'targetMet':p95<2,
                'scope':'Direct real GraphQL backend, isolated Mongo database; no claim about internet latency or long-term availability'}
        (ROOT/'test-results'/'performance.json').write_text(json.dumps(result,indent=2),encoding='utf-8');print(json.dumps(result,indent=2))
    finally:
        subprocess.run(['docker','stop',container],cwd=ROOT,capture_output=True)
        run('docker','compose','exec','-T','product-view-mongo','mongosh','--quiet','--eval','db.getSiblingDB("'+database+'").dropDatabase()')


if __name__=='__main__':main()
