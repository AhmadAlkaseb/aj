"""Real idempotency, duplicate-message, expiry, manual-review and PDF-job checks."""
import concurrent.futures
import hashlib
import json
import subprocess
import time
import urllib.error
import urllib.request
import uuid
from datetime import datetime, timezone
from pathlib import Path
from smoke import wait_for
from test_identity import admin_headers
from check_async import rabbit

ROOT=Path(__file__).resolve().parent.parent
BASE='http://127.0.0.1:3000/api/v1'


def api(path,method='GET',data=None,expected=200,key=None,binary=False):
    headers={'Content-Type':'application/json',**admin_headers()}
    if key:headers['Idempotency-Key']=key
    req=urllib.request.Request(BASE+path,method=method,data=None if data is None else json.dumps(data).encode(),headers=headers)
    try:
        with urllib.request.urlopen(req,timeout=15) as r:status,raw=r.status,r.read()
    except urllib.error.HTTPError as e:status,raw=e.code,e.read()
    assert status in (expected if isinstance(expected,tuple) else (expected,)),(path,status,expected,raw[:100])
    return raw if binary else json.loads(raw) if raw else None


def sql(service,statement):
    return subprocess.check_output(['docker','compose','exec','-T',service+'-db','psql','-X','-v','ON_ERROR_STOP=1','-U','postgres','-d',service,'-Atc',statement],cwd=ROOT).decode().strip()


def publish(topic,payload,event_id=None):
    event_id=event_id or str(uuid.uuid4())
    envelope={'eventId':event_id,'eventType':topic,'schemaVersion':1,
        'occurredAt':datetime.now(timezone.utc).isoformat(),'correlationId':payload['orderGuid'],'payload':payload}
    assert rabbit('exchanges/%2F/aj.events/publish','POST',{'routing_key':topic,'properties':{'message_id':event_id,'delivery_mode':2},'payload_encoding':'string','payload':json.dumps(envelope)})['routed']
    return event_id


def main():
    tag=uuid.uuid4().hex[:10]
    user=api('/users','POST',{'firstName':'Reliability','lastName':tag,'email':tag+'@reliability.test'},201)
    def car():
        result=api('/products','POST',{'brand':'Reliability','model':tag,'price':123456.78,'year':2026},201)
        wait_for(lambda:api('/products/'+result['guid'])['guid']==result['guid']);return result
    product=car();body={'userGuid':user['guid'],'productGuid':product['guid'],'shippingAddress':'Testvej 3'};key=uuid.uuid4().hex
    with concurrent.futures.ThreadPoolExecutor(max_workers=5) as pool:orders=list(pool.map(lambda _:api('/orders','POST',body,201,key),range(5)))
    assert len({o['guid'] for o in orders})==1
    order=orders[0];wait_for(lambda:api('/orders/'+order['guid'])['status']=='RESERVED')
    api('/orders','POST',{**body,'shippingAddress':'Changed'},409,key)
    assert len(api('/users/'+user['guid']+'/orders'))==1
    guid=str(uuid.UUID(order['guid']))
    sql('orders',"update orders_details set expires_at=now()-interval '1 second' where order_id=(select id from orders where guid='"+guid+"')")
    wait_for(lambda:api('/orders/'+guid)['status']=='CANCELLED')
    wait_for(lambda:api('/products/'+product['guid'])['status']=='AVAILABLE')
    # Same delivery identity must commit exactly one handler result even after cancellation.
    payload={'orderGuid':guid,'productGuid':product['guid'],'userGuid':user['guid'],'totalAmount':123456.78,'currency':'DKK'}
    event_id=str(uuid.uuid4())
    for _ in range(3):publish('quantitycheck',payload,event_id)
    wait_for(lambda:sql('products',"select count(*) from event_inbox where id='"+event_id+"'")=='1')
    assert api('/products/'+product['guid'])['status']=='AVAILABLE'
    assert sql('products',"select state from reservation_signals where order_guid='"+guid+"'")=='RELEASED'

    product=car();order=api('/orders','POST',{**body,'productGuid':product['guid']},201)
    wait_for(lambda:api('/orders/'+order['guid'])['status']=='RESERVED')
    with concurrent.futures.ThreadPoolExecutor(max_workers=5) as pool:payments=list(pool.map(lambda _:api('/payments','POST',{'orderGuid':order['guid']},(200,201)),range(5)))
    assert len({p['guid'] for p in payments})==1
    payment=payments[0]
    wait_for(lambda:api('/payments/'+payment['guid'])['status']=='PENDING')
    guid=str(uuid.UUID(order['guid']))
    sql('orders',"update orders_details set expires_at=now()-interval '1 second' where order_id=(select id from orders where guid='"+guid+"')")
    wait_for(lambda:api('/orders/'+guid)['status']=='REVIEW_REQUIRED')
    assert api('/products/'+product['guid'])['status']=='RESERVED'
    api('/payments/'+payment['guid'],'PUT',{'status':'COMPLETED'})
    wait_for(lambda:api('/orders/'+guid)['status']=='COMPLETED')
    wait_for(lambda:api('/products/'+product['guid'])['status']=='SOLD')
    def job():
        subprocess.run(['docker','compose','run','--rm','--no-deps','confirmation-job'],cwd=ROOT,check=True,stdout=subprocess.DEVNULL)
    for _ in range(10):
        job()
        try:pdf=api('/orders/'+guid+'/confirmation',binary=True);break
        except AssertionError:time.sleep(.5)
    else:raise AssertionError('Confirmation job did not store PDF')
    assert pdf.startswith(b'%PDF-');checksum=hashlib.sha256(pdf).hexdigest()
    current=api('/orders/'+guid)
    payload={'orderGuid':guid,'userGuid':user['guid'],'productGuid':product['guid'],'totalAmount':current['totalAmount'],'currency':current['currency'],'paymentGuid':payment['guid']}
    replay=publish('order.completed',payload);publish('order.completed',payload,replay)
    job();job()
    assert hashlib.sha256(api('/orders/'+guid+'/confirmation',binary=True)).hexdigest()==checksum
    assert sql('orders',"select count(*) from order_confirmations where order_guid='"+guid+"'")=='1'
    history=api('/orders/'+guid+'/history');assert any(h['to']=='REVIEW_REQUIRED' for h in history)
    result={'result':'PASS','concurrentCommandRetries':5,'singleOrder':'PASS','changedRetryPayload':'409',
        'duplicateInbox':'PASS','lateReservation':'blocked','expiry':'compensated','ambiguousPayment':'retained for review',
        'latePaymentResult':'completed','pdfJob':'PASS','duplicatePdfWrite':'first artifact preserved'}
    (ROOT/'test-results/reliability-results.json').write_text(json.dumps(result,indent=2),encoding='utf-8');print(json.dumps(result,indent=2))


if __name__=='__main__':main()
