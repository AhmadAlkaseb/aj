"""HTTPS/authenticated deployment acceptance; optional labelled business compensation flow."""
import argparse
import json
import os
import ssl
import time
import urllib.parse
import urllib.request
import uuid
from pathlib import Path
from ci_environment import https_url


def run(base,identity,ca=None,business=False):
    base=https_url(base);identity=https_url(identity)
    context=ssl.create_default_context(cafile=ca or None)
    opener=urllib.request.build_opener(urllib.request.HTTPSHandler(context=context))
    def request(url,method='GET',data=None,token=None,binary=False):
        headers={'Content-Type':'application/json'}
        if token:headers['Authorization']='Bearer '+token
        req=urllib.request.Request(url,method=method,data=None if data is None else json.dumps(data).encode(),headers=headers)
        with opener.open(req,timeout=30) as response:
            raw=response.read()
            return raw if binary else json.loads(raw) if raw else None
    def api(path,method='GET',data=None):return request(base+'/api/v1'+path,method,data,token)
    def wait(fn):
        end=time.monotonic()+120
        while time.monotonic()<end:
            if fn():return
            time.sleep(2)
        raise AssertionError('Business smoke timed out')
    assert b'<html' in request(base+'/',binary=True).lower(),'Frontend HTML missing'
    assert request(base+'/api/v1/status')['status']=='UP','Dependency status is degraded'
    catalogue=request(base+'/api/v1/graphql','POST',{'query':'{products(limit:1){total items{guid}}}'})
    assert not catalogue.get('errors') and 'products' in catalogue.get('data',{}),'GraphQL query failed'
    form=urllib.parse.urlencode({'grant_type':'client_credentials','client_id':os.environ.get('SMOKE_CLIENT_ID') or 'aj-tests',
        'client_secret':os.environ['SMOKE_CLIENT_SECRET']}).encode()
    with opener.open(identity+'/realms/aj/protocol/openid-connect/token',form,timeout=30) as response:token=json.load(response)['access_token']
    session=api('/auth/session');assert session.get('subject'),'Authenticated session missing'
    result={'result':'PASS','https':True,'frontend':True,'graphql':True,'authentication':True,'businessSmoke':False}
    if business:
        marker=uuid.uuid4().hex;user=None;car=None;order=None
        try:
            user=api('/users','POST',{'firstName':'Deployment','lastName':'Smoke','email':marker+'@example.invalid'})
            car=api('/products','POST',{'brand':'Deployment smoke','model':marker[:12],'year':2026,'price':123456})
            order=api('/orders','POST',{'userGuid':user['guid'],'productGuid':car['guid'],'shippingAddress':'Synthetic deployment fixture'})
            wait(lambda:api('/orders/'+order['guid'])['status']=='RESERVED')
            payment=api('/payments','POST',{'orderGuid':order['guid']})
            wait(lambda:api('/payments/'+payment['guid'])['status']=='PENDING')
            api('/payments/'+payment['guid'],'PUT',{'status':'FAILED'})
            wait(lambda:api('/orders/'+order['guid'])['status']=='FAILED' and api('/products/'+car['guid'])['status']=='AVAILABLE')
            result['businessSmoke']=True
        finally:
            if order and api('/orders/'+order['guid'])['status'] in ['PENDING','RESERVING','RESERVED']:
                api('/orders/'+order['guid'],'PUT',{'status':'CANCELLED'})
            if car and api('/products/'+car['guid'])['status']=='AVAILABLE':api('/products/'+car['guid'],'DELETE')
            if user:api('/users/'+user['guid'],'DELETE')
    return result


if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('--base-url',required=True);parser.add_argument('--identity-url',required=True);parser.add_argument('--ca');parser.add_argument('--business',action='store_true');parser.add_argument('--output',type=Path,default=Path('test-results/deploy/smoke.json'));args=parser.parse_args()
    result=run(args.base_url.rstrip('/'),args.identity_url.rstrip('/'),args.ca,args.business)
    args.output.parent.mkdir(parents=True,exist_ok=True);args.output.write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8');print(json.dumps(result))
