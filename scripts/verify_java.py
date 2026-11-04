"""Run Java checks in an isolated source copy, away from editor output directories."""
import os
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT=Path(__file__).resolve().parent.parent
OUT=ROOT/'test-results/java'
SKIP={'.git','target','node_modules','test-results','backups','.vscode','system-integration','documentation'}


def main():
    work=Path(tempfile.mkdtemp(prefix='aj-verify-')).resolve()
    assert work.parent==Path(tempfile.gettempdir()).resolve() and work.name.startswith('aj-verify-')
    OUT.mkdir(parents=True,exist_ok=True)
    try:
        for parent,dirs,files in os.walk(ROOT):
            dirs[:]=[name for name in dirs if name not in SKIP]
            relative=Path(parent).relative_to(ROOT)
            for name in files:
                if name.endswith('.log') or name.startswith('.env'):continue
                target=work/relative/name;target.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(Path(parent)/name,target)
        command=['mvn.cmd' if os.name=='nt' else 'mvn','-B','-ntp']
        if '--quality' in sys.argv:command.append('-Pquality')
        command.append('verify')
        print('Verifying isolated source copy; log: '+str(OUT/'build.log'),flush=True)
        with (OUT/'build.log').open('w',encoding='utf-8') as log:result=subprocess.run(command,cwd=work,stdout=log,stderr=subprocess.STDOUT)
        for module in work.iterdir():
            if not module.is_dir():continue
            for name in ['surefire-reports','failsafe-reports']:
                reports=module/'target'/name
                if reports.exists():shutil.copytree(reports,OUT/module.name/name,dirs_exist_ok=True)
            report=module/'target/spotbugsXml.xml'
            if report.exists():shutil.copy2(report,OUT/(module.name+'-spotbugs.xml'))
        print('Java verification exit status: '+str(result.returncode),flush=True)
        return result.returncode
    finally:
        assert work.parent==Path(tempfile.gettempdir()).resolve() and work.name.startswith('aj-verify-')
        shutil.rmtree(work)


if __name__=='__main__':sys.exit(main())
