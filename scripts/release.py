"""Deploy digest-pinned applications to an existing cluster, preserving target configuration."""
import argparse
import copy
import json
import re
import subprocess
import time
from pathlib import Path

ROOT=Path(__file__).resolve().parent.parent
SERVICES={'users','products','orders','payments','product-view','product-synchronizer','apigateway','frontend','confirmation-job'}
OWNERS={'users','products','orders','payments'}
DIGEST=re.compile(r'[a-z0-9./_-]+@sha256:[0-9a-f]{64}')


def validate(manifest):
    if not re.fullmatch('[0-9a-f]{40}',manifest.get('revision','')):raise ValueError('A full source commit SHA is required')
    images=manifest.get('images',{})
    if set(images)!=SERVICES:raise ValueError('Exactly nine application digests are required')
    if any(not isinstance(v,str) or not DIGEST.fullmatch(v) for v in images.values()):raise ValueError('Images must use immutable sha256 digests, not tags')
    return manifest


def workload_name(item):
    name=item['metadata']['name']
    return 'confirmation-job' if item['kind']=='ScaledJob' and name=='confirmation' else name


def pod_template(item):
    return item['spec']['jobTargetRef']['template'] if item['kind']=='ScaledJob' else item['spec']['template']


def prepare(manifest,base=None):
    validate(manifest)
    if base is None:base=json.loads((ROOT/'k8s/base.json').read_text(encoding='utf-8'))
    items=[]
    for original in base['items']:
        name=workload_name(original)
        if not (original['kind']=='Deployment' and name in SERVICES or original['kind']=='ScaledJob' and name=='confirmation-job'):continue
        item=copy.deepcopy(original);item.pop('status',None)
        for field in ['uid','resourceVersion','creationTimestamp','generation','managedFields']:item['metadata'].pop(field,None)
        item['metadata'].get('annotations',{}).pop('kubectl.kubernetes.io/last-applied-configuration',None)
        pod=pod_template(item)
        pod.setdefault('metadata',{}).setdefault('annotations',{})['aj/source-revision']=manifest['revision']
        containers=pod['spec']['containers']
        if len(containers)!=1:raise ValueError('Expected one application container: '+name)
        containers[0]['image']=manifest['images'][name];containers[0]['imagePullPolicy']='IfNotPresent'
        for init in pod['spec'].get('initContainers',[]):
            if name in OWNERS:init['image']=manifest['images'][name];init['imagePullPolicy']='IfNotPresent'
        if name in OWNERS or name=='product-synchronizer':
            if item['spec'].get('replicas',1)!=1 or item['spec'].get('strategy',{}).get('type')!='Recreate':
                raise ValueError('Event workers require one replica and Recreate: '+name)
        items.append(item)
    if len(items)!=9 or {workload_name(i) for i in items}!=SERVICES:raise ValueError('Target must contain all nine application workloads')
    return {'apiVersion':'v1','kind':'List','items':items}


def previous_release(base):
    images={};revisions=set()
    for item in base['items']:
        name=workload_name(item)
        if name not in SERVICES:continue
        pod=pod_template(item);images[name]=pod['spec']['containers'][0]['image']
        revisions.add(pod.get('metadata',{}).get('annotations',{}).get('aj/source-revision',''))
    if len(revisions)!=1:return None
    manifest={'revision':revisions.pop(),'images':images,'rollback':True}
    try:return validate(manifest)
    except ValueError:return None


def migration_jobs(plan,namespace,run):
    jobs=[]
    for item in plan['items']:
        owner=workload_name(item)
        if owner not in OWNERS:continue
        template=copy.deepcopy(pod_template(item));spec=template['spec']
        spec.pop('initContainers',None);spec['restartPolicy']='Never';spec['automountServiceAccountToken']=False
        container=spec['containers'][0]
        for field in ['readinessProbe','livenessProbe','startupProbe','ports','args']:container.pop(field,None)
        container['command']=['java','-cp','/app/app.jar','com.aj.common.Migrate',owner]
        template['metadata']={'labels':{'app':owner,'aj/migration':run}}
        jobs.append({'apiVersion':'batch/v1','kind':'Job','metadata':{'name':f'migrate-{owner}-{run}','namespace':namespace},
            'spec':{'backoffLimit':0,'activeDeadlineSeconds':240,'ttlSecondsAfterFinished':86400,'template':template}})
    return jobs


class Cluster:
    def __init__(self,context,namespace):self.context=context;self.namespace=namespace
    def run(self,*args,data=None):
        result=subprocess.run(['kubectl','--context',self.context,'-n',self.namespace,*args],input=data,capture_output=True,timeout=600)
        if result.returncode:raise RuntimeError('kubectl operation failed: '+result.stderr.decode(errors='replace')[:1500])
        return result.stdout
    def state(self):
        deployments=json.loads(self.run('get','deployments','-o','json'))['items']
        selected=[x for x in deployments if x['metadata']['name'] in SERVICES]
        selected.append(json.loads(self.run('get','scaledjob','confirmation','-o','json')))
        return {'items':selected}
    def apply(self,plan):self.run('apply','-f','-',data=json.dumps(plan).encode())
    def migrate(self,plan):
        run=str(time.time_ns())[-14:];results=[]
        for job in migration_jobs(plan,self.namespace,run):
            self.apply(job);name=job['metadata']['name'];deadline=time.monotonic()+270
            while True:
                state=json.loads(self.run('get','job',name,'-o','json')).get('status',{})
                if state.get('succeeded',0)>0:break
                if state.get('failed',0)>0:raise RuntimeError('Migration job failed: '+name)
                if time.monotonic()>deadline:raise RuntimeError('Migration job timed out: '+name)
                time.sleep(2)
            results.append(name)
        return results
    def rollout(self,plan):
        self.apply(plan)
        for item in plan['items']:
            if item['kind']=='Deployment':self.run('rollout','status','deployment/'+item['metadata']['name'],'--timeout=420s')


def execute(cluster,manifest,out,migrate_only=False,proof=None):
    base=cluster.state();plan=prepare(manifest,base)
    cluster.run('apply','--dry-run=server','-f','-',data=json.dumps(plan).encode())
    previous=previous_release(base)
    if proof:
        record=json.loads(proof.read_text(encoding='utf-8'))
        expected={'revision':manifest['revision'],'images':manifest['images'],'context':cluster.context,'namespace':cluster.namespace,'result':'PASS'}
        if any(record.get(k)!=v for k,v in expected.items()):raise ValueError('Migration proof does not match this release and target')
        before={workload_name(i):pod_template(i)['spec']['containers'][0]['image'] for i in base['items']}
        if before!=record.get('beforeImages'):raise ValueError('Target images changed after the migration gate')
    else:
        if previous:write(out/'previous-release.json',previous)
        else:(out/'previous-release.json').unlink(missing_ok=True)
        jobs=cluster.migrate(plan)
        write(out/'migration-results.json',{'result':'PASS','revision':manifest['revision'],'images':manifest['images'],
            'context':cluster.context,'namespace':cluster.namespace,'jobs':jobs,
            'beforeImages':{workload_name(i):pod_template(i)['spec']['containers'][0]['image'] for i in base['items']}})
    if migrate_only:return
    cluster.rollout(plan)
    write(out/'rollout-state.json',{'result':'PASS','revision':manifest['revision'],'images':manifest['images'],'migrationReadinessGate':True})


def write(path,data):path.write_text(json.dumps(data,indent=2)+'\n',encoding='utf-8')


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('manifest',type=Path)
    parser.add_argument('--context',default='kind-aj-exam');parser.add_argument('--namespace',default='aj')
    mode=parser.add_mutually_exclusive_group();mode.add_argument('--apply',action='store_true');mode.add_argument('--migrate-only',action='store_true')
    parser.add_argument('--rollback-compatible',action='store_true');parser.add_argument('--migration-proof',type=Path)
    parser.add_argument('--output-dir',type=Path,default=ROOT/'test-results/deploy')
    args=parser.parse_args();manifest=validate(json.loads(args.manifest.read_text(encoding='utf-8')))
    if manifest.get('rollback') and not args.rollback_compatible:parser.error('Rollback requires explicit compatibility with the current forward-migrated schema')
    args.output_dir.mkdir(parents=True,exist_ok=True)
    if args.apply or args.migrate_only:
        execute(Cluster(args.context,args.namespace),manifest,args.output_dir,args.migrate_only,args.migration_proof)
        print('Migration gate passed.' if args.migrate_only else 'All application rollouts ready; smoke verification is required before promotion.')
    else:
        write(args.output_dir/'application-release.json',prepare(manifest))
        print('Validated nine digest-pinned workloads. Planning made no cluster changes.')


if __name__=='__main__':main()
