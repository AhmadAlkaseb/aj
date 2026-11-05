"""Assemble nine scan-gated image records from one expected source revision."""
import argparse
import json
from pathlib import Path
from release import SERVICES,validate


def assemble(directory,revision):
    images={}
    for path in directory.rglob('image.json'):
        record=json.loads(path.read_text(encoding='utf-8'));service=record['service']
        if record['revision']!=revision or service not in SERVICES or service in images:raise ValueError('Wrong revision, unknown service or duplicate image record')
        images[service]=record['image']
    return validate({'revision':revision,'images':images})


if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('directory',type=Path);parser.add_argument('output',type=Path);parser.add_argument('--revision',required=True)
    args=parser.parse_args();manifest=assemble(args.directory,args.revision)
    args.output.parent.mkdir(parents=True,exist_ok=True);args.output.write_text(json.dumps(manifest,indent=2)+'\n',encoding='utf-8')
    print('Validated nine image digests for revision '+args.revision)
