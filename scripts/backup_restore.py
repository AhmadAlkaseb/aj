"""Back up owner databases and verify restore into fresh, isolated test databases.

python scripts/backup_restore.py [--verify]
Never restores over a running application's database. Binary data bypasses shell pipes.
"""
import hashlib
import json
import subprocess
import sys
import uuid
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent


def command(args, data=None):
    return subprocess.run(['docker','compose',*args],cwd=ROOT,input=data,capture_output=True,check=True).stdout


def sql(service, database, statement):
    return command(['exec','-T',service+'-db','psql','-X','-v','ON_ERROR_STOP=1','-U','postgres','-d',database,'-Atc',statement]).decode().strip()


def main():
    target=ROOT/'backups'/datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%SZ')
    target.mkdir(parents=True,exist_ok=False)
    result={'directory':str(target),'postgres':{},'mongo':{},'verified':'--verify' in sys.argv}
    for service in ['users','products','orders','payments']:
        payload=command(['exec','-T',service+'-db','pg_dump','-U','postgres','-d',service,'--format=custom','--no-owner','--no-privileges'])
        (target/(service+'.dump')).write_bytes(payload)
        details={'sha256':hashlib.sha256(payload).hexdigest(),'bytes':len(payload)}
        if '--verify' in sys.argv:
            database='restore_test_'+uuid.uuid4().hex
            assert database.startswith('restore_test_') and database.isidentifier()
            sql(service,'postgres','CREATE DATABASE '+database)
            try:
                command(['exec','-T',service+'-db','pg_restore','-U','postgres','-d',database,'--no-owner','--no-privileges','--exit-on-error'],payload)
                source=sql(service,service,'SELECT count(*) FROM '+service)
                restored=sql(service,database,'SELECT count(*) FROM '+service)
                assert source==restored,(service,source,restored)
                assert sql(service,database,'SELECT count(*) FROM flyway_schema_history')==sql(service,service,'SELECT count(*) FROM flyway_schema_history')
                details['restoredRows']=int(restored)
            finally:
                sql(service,'postgres','DROP DATABASE '+database+' WITH (FORCE)')
        result['postgres'][service]=details
    archive=command(['exec','-T','product-view-mongo','mongodump','--db','product_view','--archive','--gzip'])
    (target/'product_view.archive.gz').write_bytes(archive)
    result['mongo']={'sha256':hashlib.sha256(archive).hexdigest(),'bytes':len(archive)}
    if '--verify' in sys.argv:
        database='restore_test_'+uuid.uuid4().hex
        try:
            command(['exec','-T','product-view-mongo','mongorestore','--archive','--gzip','--nsFrom=product_view.*','--nsTo='+database+'.*'],archive)
            counts=command(['exec','-T','product-view-mongo','mongosh','--quiet','--eval',
                'JSON.stringify([db.getSiblingDB("product_view").products.countDocuments({}),db.getSiblingDB("'+database+'").products.countDocuments({})])'])
            a,b=json.loads(counts);assert a==b
            result['mongo']['restoredRows']=b
        finally:
            assert database.startswith('restore_test_')
            command(['exec','-T','product-view-mongo','mongosh','--quiet','--eval','db.getSiblingDB("'+database+'").dropDatabase()'])
    (target/'manifest.json').write_text(json.dumps(result,indent=2),encoding='utf-8')
    (ROOT/'test-results'/'restore-results.json').write_text(json.dumps(result,indent=2),encoding='utf-8')
    print(json.dumps(result,indent=2))


if __name__=='__main__':main()
