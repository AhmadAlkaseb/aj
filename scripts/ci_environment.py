"""Create short-lived runner credential files and verify a release artifact's origin."""
import argparse
import base64
import json
import os
import re
import shutil
import urllib.parse
import urllib.request
from pathlib import Path
from release import validate


def https_url(value):
    parsed=urllib.parse.urlsplit(value)
    if parsed.scheme!='https' or not parsed.hostname or parsed.username or parsed.password or parsed.query or parsed.fragment:
        raise ValueError('Deployment endpoints must be explicit HTTPS URLs without credentials or query strings')
    return value.rstrip('/')


def setup():
    required=['KUBE_CONFIG_B64','KUBE_CONTEXT','PUBLIC_BASE_URL','SMOKE_CLIENT_SECRET']
    missing=[key for key in required if not os.environ.get(key)]
    if missing:raise ValueError('Missing deployment environment configuration: '+', '.join(missing))
    namespace=os.environ.get('KUBE_NAMESPACE') or 'aj'
    if not re.fullmatch('[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?',namespace):raise ValueError('Invalid target namespace')
    base=https_url(os.environ['PUBLIC_BASE_URL'])
    identity=https_url(os.environ.get('IDENTITY_BASE_URL') or base+'/identity')
    runtime=Path(os.environ['RUNNER_TEMP'])/'aj-deployment'
    runtime.mkdir(parents=True,exist_ok=True)
    config=runtime/'kubeconfig';config.write_bytes(base64.b64decode(os.environ['KUBE_CONFIG_B64'],validate=True));config.chmod(0o600)
    values={'KUBECONFIG':str(config),'KUBE_NAMESPACE':namespace,'PUBLIC_BASE_URL':base,'IDENTITY_BASE_URL':identity,'DEPLOY_CA_FILE':''}
    if os.environ.get('TLS_CA_B64'):
        ca=runtime/'ca.crt';ca.write_bytes(base64.b64decode(os.environ['TLS_CA_B64'],validate=True));values['DEPLOY_CA_FILE']=str(ca)
    with Path(os.environ['GITHUB_ENV']).open('a',encoding='utf-8') as out:
        for key,value in values.items():
            if '\n' in value or '\r' in value:raise ValueError('Invalid multiline configuration')
            out.write(key+'='+value+'\n')
    print('Target configuration validated; temporary credential files created.')


def verify_artifact(file,run_id,rollback=False):
    if not run_id.isdecimal():raise ValueError('Artifact run ID must be numeric')
    repo=os.environ['GITHUB_REPOSITORY']
    request=urllib.request.Request('https://api.github.com/repos/'+repo+'/actions/runs/'+run_id,
        headers={'Authorization':'Bearer '+os.environ['GH_TOKEN'],'Accept':'application/vnd.github+json','User-Agent':'AJ-release-verification'})
    with urllib.request.urlopen(request,timeout=30) as response:run=json.load(response)
    if run['path']!='.github/workflows/release.yaml' or run['head_branch']!='main':raise ValueError('Artifact must originate from the trusted main-branch release workflow')
    if run_id!=os.environ['GITHUB_RUN_ID'] and run.get('conclusion')!='success':raise ValueError('An earlier release must have completed successfully')
    manifest=validate(json.loads(file.read_text(encoding='utf-8')))
    if manifest['revision']!=run['head_sha']:raise ValueError('Artifact revision differs from its workflow revision')
    if rollback:manifest['rollback']=True
    file.write_text(json.dumps(manifest,indent=2)+'\n',encoding='utf-8')
    print('Release artifact source and revision verified.')


if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('mode',choices=['setup','verify-artifact','cleanup']);parser.add_argument('--manifest',type=Path);parser.add_argument('--run-id');parser.add_argument('--rollback',action='store_true');args=parser.parse_args()
    if args.mode=='setup':setup()
    elif args.mode=='verify-artifact':verify_artifact(args.manifest,args.run_id,args.rollback)
    else:
        target=(Path(os.environ['RUNNER_TEMP'])/'aj-deployment').resolve()
        if target.parent!=Path(os.environ['RUNNER_TEMP']).resolve() or target.name!='aj-deployment':raise ValueError('Unexpected credential directory')
        if target.exists():shutil.rmtree(target)
