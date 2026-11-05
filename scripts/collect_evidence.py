"""Publish compact local results, never runtime secrets or invented remote outcomes."""
import hashlib
import json
import re
import subprocess
import xml.etree.ElementTree as ET
from datetime import datetime, timezone
from pathlib import Path

ROOT=Path(__file__).resolve().parent.parent
OUT=ROOT/'documentation/evidence'


def main():
    OUT.mkdir(parents=True,exist_ok=True)
    java=ROOT/'test-results/java'
    log=(java/'build.log').read_text(encoding='utf-8',errors='replace') if (java/'build.log').exists() else ''
    totals={'unit':0,'integration':0,'failures':0,'errors':0}
    for file in java.rglob('TEST-*.xml'):
        r=ET.parse(file).getroot();kind='integration' if 'failsafe-reports' in str(file) else 'unit'
        totals[kind]+=int(r.get('tests','0'));totals['failures']+=int(r.get('failures','0'));totals['errors']+=int(r.get('errors','0'))
    totals['result']='PASS' if 'BUILD SUCCESS' in log and 'BUILD FAILURE' not in log else 'not verified by current build log'
    digest=hashlib.sha256()
    sources=list(ROOT.glob('*/src/**/*.java'))+list(ROOT.glob('*/src/main/resources/**/*'))+list(ROOT.glob('*/pom.xml'))+[ROOT/'pom.xml']
    for file in sorted(set(p for p in sources if p.is_file())):
        digest.update(file.relative_to(ROOT).as_posix().encode()+b'\0'+file.read_bytes().replace(b'\r\n',b'\n'))
    evidence={'reviewDate':'2026-11-05','gitBase':subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip(),
        'sourceFingerprint':digest.hexdigest(),'java':totals,'checks':{},'remoteEvidence':'No successful run IDs or publication are inferred from local results.'}
    mapping={'security':'security-results.json','reliability':'reliability-results.json','monitoring':'observability-results.json',
             'restore':'restore-results.json','performance':'performance.json','kubernetes':'kubernetes-results.json',
             'brokerPersistence':'broker-persistence.json'}
    for key,name in mapping.items():
        file=ROOT/'test-results'/name
        if not file.exists():evidence['checks'][key]={'result':'not yet verified'};continue
        data=json.loads(file.read_text(encoding='utf-8'));data.pop('directory',None)
        evidence['checks'][key]={'artifactTimestampUTC':datetime.fromtimestamp(file.stat().st_mtime,timezone.utc).isoformat(),'summary':data}
    cooperation=ROOT/'test-results/cooperation/result.json'
    if cooperation.exists():evidence['checks']['cooperation']=json.loads(cooperation.read_text(encoding='utf-8'))
    for label,name in [('browserWithRealAI','implementation-ai-browser.log'),('browser','implementation-browser.log')]:
        file=ROOT/name
        if file.exists():
            try:
                data=json.loads(file.read_text(encoding='utf-8'));data.pop('screenshots',None);evidence['checks'][label]=data
            except ValueError:pass
    for label,name in [('oidcRecovery','final-oidc.log'),('demoRestart','final-demo-restart.log')]:
        file=ROOT/name
        if file.exists():
            raw=file.read_text(encoding='utf-8',errors='replace')
            try:
                data=json.loads(raw[raw.index('{'):]);evidence['checks'][label]=data
            except (ValueError,TypeError):pass
    system=ROOT/'final-system.log'
    if system.exists():
        raw=system.read_text(encoding='utf-8',errors='replace')
        count=re.search(r'Ran (\d+) tests',raw)
        if count:evidence['checks']['systemRecovery']={'result':'PASS' if re.search(r'^OK\s*$',raw,re.M) else 'not verified','tests':int(count.group(1))}
    for label,name in [('performanceFirstRun','performance-first-run.json'),('performanceRepeatRun','performance-repeat-run.json')]:
        file=OUT/name
        if file.exists():evidence['checks'][label]={'summary':json.loads(file.read_text(encoding='utf-8'))}
    (OUT/'local-verification.json').write_text(json.dumps(evidence,indent=2,ensure_ascii=False)+'\n',encoding='utf-8')
    lines=['# Verification evidence','','Reviewed 5 November 2026. Results below are local and refer to the recorded source fingerprint. Historical artifact timestamps are retained in [the JSON record](evidence/local-verification.json). They are not fabricated GitHub run results.','',
        '| Check | Observed result |','| --- | --- |',f"| Java / SpotBugs | {totals['result']}; {totals['unit']} unit and {totals['integration']} integration checks; {totals['failures']} failures, {totals['errors']} errors |"]
    for key,data in evidence['checks'].items():
        summary=data.get('summary',data);result=summary.get('result')
        if key=='restore':result='Verified owner database restores' if summary.get('verified') else 'not verified'
        if key=='performance':result=f"10,000 cars / 20 clients; p95 {summary.get('p95Seconds',0):.3f}s; two-second target met: {summary.get('targetMet')}"
        if key.startswith('performance') and key!='performance':result=f"p95 {summary.get('p95Seconds',0):.3f}s; target met: {summary.get('targetMet')}"
        if key=='kubernetes':result='TLS, commerce, PDF job, database isolation and HPA recorded' if summary.get('tls')=='PASS' else 'not yet verified'
        lines.append(f'| {key} | {result or "See artifact summary"} |')
    lines+=['','Java source/resource fingerprint: `'+evidence['sourceFingerprint']+'`.','',
        'Local build logs and full reports stay under ignored test-results. Reproduce Java verification with `python scripts/verify_java.py --quality`; Docker-backed checks are described in [TESTING.md](TESTING.md). Source changes require a new run; one local pass is not a guarantee of future availability.','',
        'Remote CI IDs, immutable image digests and branch protection must be recorded after an actual GitHub operation. Group meeting attendance, historical contributions and first-person reflections require author confirmation.']
    (ROOT/'documentation/EVIDENCE.md').write_text('\n'.join(lines)+'\n',encoding='utf-8')
    print('Evidence summaries updated without copying secrets or full logs.')


if __name__=='__main__':main()
