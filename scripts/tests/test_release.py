"""Deployment failure boundaries without external accounts or a running cluster."""
import copy
import json
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
import release
from ci_manifest import assemble
from ci_environment import https_url,verify_artifact


def manifest(revision='a',digest='b'):
    return {'revision':revision*40,'images':{s:'ghcr.io/test/aj-'+s+'@sha256:'+digest*64 for s in release.SERVICES}}


class FakeCluster:
    context='test';namespace='aj'
    def __init__(self,fail_migration=False):
        self.base=release.prepare(manifest('c','d'));self.calls=[];self.fail_migration=fail_migration
    def state(self):return copy.deepcopy(self.base)
    def run(self,*args,**kwargs):self.calls.append('preflight');return b''
    def migrate(self,plan):
        self.calls.append('migrate')
        if self.fail_migration:raise RuntimeError('injected migration failure')
        return ['four-owner-jobs']
    def rollout(self,plan):self.calls.append('rollout');self.base=copy.deepcopy(plan)


class ReleaseTest(unittest.TestCase):
    def test_preserves_target_configuration_and_pins_owner_migrations(self):
        base=release.prepare(manifest('c','d'))
        owner=next(i for i in base['items'] if release.workload_name(i)=='users')
        owner['spec']['template']['spec']['containers'][0]['env'].append({'name':'TARGET_SPECIFIC','value':'keep-me'})
        original=copy.deepcopy(base);plan=release.prepare(manifest(),base)
        self.assertEqual(base,original)
        current=next(i for i in plan['items'] if release.workload_name(i)=='users')
        self.assertIn({'name':'TARGET_SPECIFIC','value':'keep-me'},current['spec']['template']['spec']['containers'][0]['env'])
        self.assertEqual(current['spec']['template']['spec']['initContainers'][0]['image'],manifest()['images']['users'])
        self.assertEqual({release.workload_name(i) for i in plan['items']},release.SERVICES)

    def test_rejects_mutable_missing_and_duplicate_workloads(self):
        bad=manifest();bad['images']['users']='ghcr.io/test/users:latest'
        with self.assertRaises(ValueError):release.prepare(bad)
        bad=manifest();del bad['images']['payments']
        with self.assertRaises(ValueError):release.prepare(bad)
        base=release.prepare(manifest());base['items'][-1]=copy.deepcopy(base['items'][0])
        with self.assertRaises(ValueError):release.prepare(manifest(),base)

    def test_rejects_unverified_worker_scaling(self):
        base=release.prepare(manifest())
        next(i for i in base['items'] if release.workload_name(i)=='orders')['spec']['replicas']=2
        with self.assertRaises(ValueError):release.prepare(manifest(),base)

    def test_migration_jobs_use_owner_network_identity_and_no_consumers(self):
        jobs=release.migration_jobs(release.prepare(manifest()),'aj-stage','test')
        self.assertEqual(len(jobs),4)
        for job in jobs:
            spec=job['spec']['template']['spec'];owner=job['spec']['template']['metadata']['labels']['app']
            self.assertEqual(job['metadata']['namespace'],'aj-stage')
            self.assertEqual(spec['containers'][0]['command'][-2:],['com.aj.common.Migrate',owner])
            self.assertNotIn('initContainers',spec)
            self.assertFalse(spec['automountServiceAccountToken'])
            self.assertEqual(job['spec']['backoffLimit'],0)

    def test_failed_migration_never_changes_apps(self):
        cluster=FakeCluster(True)
        with tempfile.TemporaryDirectory() as directory:
            with self.assertRaises(RuntimeError):release.execute(cluster,manifest(),Path(directory))
        self.assertEqual(cluster.calls,['preflight','migrate'])

    def test_successful_migrations_precede_rollout_and_capture_previous(self):
        cluster=FakeCluster()
        with tempfile.TemporaryDirectory() as directory:
            out=Path(directory);release.execute(cluster,manifest(),out)
            previous=json.loads((out/'previous-release.json').read_text())
            self.assertEqual(previous['images'],manifest('c','d')['images'])
            self.assertTrue(previous['rollback'])
        self.assertEqual(cluster.calls,['preflight','migrate','rollout'])

    def test_migration_proof_is_bound_to_revision_target_and_unchanged_images(self):
        cluster=FakeCluster()
        with tempfile.TemporaryDirectory() as directory:
            out=Path(directory);release.execute(cluster,manifest(),out,migrate_only=True)
            proof=out/'migration-results.json'
            with self.assertRaises(ValueError):release.execute(cluster,manifest('e'),out,proof=proof)
            cluster.base['items'][0]['spec']['template']['spec']['containers'][0]['image']=manifest()['images']['users']
            with self.assertRaises(ValueError):release.execute(cluster,manifest(),out,proof=proof)
        self.assertNotIn('rollout',cluster.calls)

    def test_gate_and_apply_run_migrations_once(self):
        cluster=FakeCluster()
        with tempfile.TemporaryDirectory() as directory:
            out=Path(directory);release.execute(cluster,manifest(),out,migrate_only=True)
            release.execute(cluster,manifest(),out,proof=out/'migration-results.json')
        self.assertEqual(cluster.calls,['preflight','migrate','preflight','rollout'])

    def test_manifest_assembly_rejects_mixed_revisions_and_duplicate_services(self):
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory)
            for service,image in manifest()['images'].items():
                path=root/service;path.mkdir();(path/'image.json').write_text(json.dumps({'service':service,'image':image,'revision':'a'*40}))
            self.assertEqual(assemble(root,'a'*40),manifest())
            with self.assertRaises(ValueError):assemble(root,'c'*40)
            duplicate=root/'duplicate';duplicate.mkdir();(duplicate/'image.json').write_bytes((root/'users/image.json').read_bytes())
            with self.assertRaises(ValueError):assemble(root,'a'*40)

    def test_only_complete_digest_release_can_be_a_rollback_snapshot(self):
        base=release.prepare(manifest())
        self.assertIsNotNone(release.previous_release(base))
        base['items'][0]['spec']['template']['spec']['containers'][0]['image']='aj-users:local'
        self.assertIsNone(release.previous_release(base))

    def test_deployment_urls_require_tls_and_no_embedded_credentials(self):
        self.assertEqual(https_url('https://localhost:8443/'),'https://localhost:8443')
        for url in ['http://localhost:3000','https://secret:password@example.test','https://example.test/?token=x']:
            with self.assertRaises(ValueError):https_url(url)

    def test_remote_artifact_source_and_revision_are_verified(self):
        import io
        with tempfile.TemporaryDirectory() as directory:
            file=Path(directory)/'release.json';file.write_text(json.dumps(manifest()))
            env={'GITHUB_REPOSITORY':'test/aj','GH_TOKEN':'not-logged','GITHUB_RUN_ID':'2'}
            record={'path':'.github/workflows/release.yaml','head_branch':'main','conclusion':'success','head_sha':'a'*40}
            with patch.dict('os.environ',env),patch('urllib.request.urlopen',return_value=io.BytesIO(json.dumps(record).encode())):
                verify_artifact(file,'1',True)
            self.assertTrue(json.loads(file.read_text())['rollback'])
            record['head_branch']='feature'
            with patch.dict('os.environ',env),patch('urllib.request.urlopen',return_value=io.BytesIO(json.dumps(record).encode())):
                with self.assertRaises(ValueError):verify_artifact(file,'1')


if __name__=='__main__':unittest.main()
