"""Verify a durable private probe survives container recreation, retaining live volumes."""
import json
import subprocess
import sys
import time
import uuid
from pathlib import Path
from check_async import rabbit

ROOT=Path(__file__).resolve().parent.parent


def main():
    queue='aj.test.persistence.'+uuid.uuid4().hex
    marker=uuid.uuid4().hex
    before=rabbit('nodes')[0]['name']
    rabbit('queues/%2F/'+queue,'PUT',{'durable':True,'auto_delete':False,'arguments':{}})
    try:
        published=rabbit('exchanges/%2F/amq.default/publish','POST',{'routing_key':queue,'payload':marker,'payload_encoding':'string','properties':{'delivery_mode':2}})
        assert published['routed']
        subprocess.run(['docker','compose','up','-d','--no-deps','--force-recreate','--wait','--wait-timeout','180','rabbitmq'],cwd=ROOT,check=True)
        end=time.monotonic()+60
        while True:
            try:
                after=rabbit('nodes')[0]['name']
                messages=rabbit('queues/%2F/'+queue+'/get','POST',{'count':1,'ackmode':'ack_requeue_false','encoding':'auto'})
                break
            except OSError:
                if time.monotonic()>end:raise
                time.sleep(2)
        assert before==after,'Broker node identity changed'
        assert len(messages)==1 and messages[0]['payload']==marker,'Durable probe did not survive recreation'
        result={'result':'PASS','nodeIdentityPreserved':True,'durableMessagePreserved':True,'volumesDeleted':False}
        (ROOT/'test-results/broker-persistence.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8')
        print(json.dumps(result))
    finally:
        rabbit('queues/%2F/'+queue,'DELETE')


if __name__=='__main__':main()
