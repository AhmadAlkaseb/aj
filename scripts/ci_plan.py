"""Explain and select monorepo CI work from changed paths, without external Actions."""
import argparse
import fnmatch
import json
import os
import re
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SERVICES = ['users', 'products', 'orders', 'payments', 'product-view', 'product-synchronizer', 'apigateway', 'frontend', 'confirmation-job']
FLAGS = ['formatting', 'javascript', 'static-analysis', 'contracts', 'deployment-validation', 'api', 'browser', 'cooperation', 'security', 'codeql', 'docs',
         'python-sast', 'python-dependencies', 'npm-dependencies', 'repository-vulnerabilities', 'infrastructure-security']


def make_plan(paths, full=False):
    paths = sorted(set(p.replace('\\', '/').removeprefix('./') for p in paths))
    reasons = {name: [] for name in [*SERVICES, *FLAGS]}

    def select(names, reason):
        for name in names:
            if reason not in reasons[name]:
                reasons[name].append(reason)

    if full:
        select([*SERVICES, *FLAGS], 'Full verification requested (release, merge queue or manual run).')
    for path in paths:
        if path.startswith('system-integration/'):
            continue
        if path.startswith('.github/') or path in ['pom.xml', 'Dockerfile', 'Dockerfile.ci', '.dockerignore'] or path.startswith('service-common/'):
            select([*SERVICES, *FLAGS], 'Shared build, workflow or common library: ' + path)
            continue
        service = next((s for s in SERVICES if path.startswith(s + '/')), None)
        if service and not path.lower().endswith(('.md', '.svg', '.png')):
            select([service, 'security'], 'Service implementation: ' + path)
            if path.endswith(('.java', '.xml')):
                select(['static-analysis', 'codeql'], 'Java implementation/build: ' + path)
            if path.startswith('frontend/src/main/resources/public/'):
                select(['formatting'], 'Frontend formatting: ' + path)
            if service in ['frontend', 'apigateway']:
                select(['browser'], 'Browser boundary: ' + path)
                if path.endswith(('.js', '.mjs', '.cjs')):
                    select(['javascript'], 'JavaScript implementation: ' + path)
            else:
                select(['api'], 'Backend acceptance: ' + path)
            if service in ['users', 'products', 'orders', 'payments']:
                select(['cooperation'], 'Database owner cooperation: ' + path)
            if service in ['apigateway', 'users', 'products', 'orders', 'payments', 'product-view', 'product-synchronizer']:
                select(['contracts'], 'API/broker contract owner: ' + path)
        if 'compose' in path and path.endswith(('.yml', '.yaml')) or path.startswith(('identity/', 'k8s/', 'observability/')):
            select(['deployment-validation', 'api', 'browser', 'security'], 'Runtime configuration: ' + path)
            select(['infrastructure-security'], 'Infrastructure security: ' + path)
        if path.startswith('identity/'):
            select(['cooperation'], 'Shared identity provider: ' + path)
        if path.startswith('scripts/') and not path.lower().endswith('.md'):
            select(['deployment-validation', 'security'], 'Automation implementation: ' + path)
            if path.endswith('.py'):
                select(['python-sast'], 'Python implementation: ' + path)
            if path.startswith('scripts/requirements'):
                select(['python-dependencies'], 'Python dependency declaration: ' + path)
                if '-report' in path:
                    select(['docs'], 'Report dependencies: ' + path)
                if '-validation' in path:
                    select(['contracts'], 'Contract validation dependencies: ' + path)
            elif path.startswith(('scripts/ci_', 'scripts/tests/')) or path in ['scripts/release.py', 'scripts/prepare_admin.py', 'scripts/start_local.py', 'scripts/configure_local.py']:
                select(['api', 'browser', 'cooperation'], 'Shared automation boundary: ' + path)
            elif 'report' in path or 'render_' in path:
                select(['docs'], 'Report tooling: ' + path)
            else:
                select(['api'], 'System verification tooling: ' + path)
            if path in ['scripts/check_gui_hub.py', 'scripts/check_oidc.py', 'scripts/browser_check.py']:
                select(['browser'], 'Browser acceptance tooling: ' + path)
        if path in ['package.json', 'package-lock.json', '.prettierrc.json', '.prettierignore', 'eslint.config.mjs']:
            select(['formatting', 'javascript', 'security', 'deployment-validation'], 'JavaScript/formatting tooling: ' + path)
        if path.startswith('scripts/requirements'):
            select(['python-dependencies'], 'Python dependency declaration: ' + path)
        if path.endswith(('package.json', 'package-lock.json')):
            select(['npm-dependencies', 'repository-vulnerabilities', 'security'], 'Node dependency declaration: ' + path)
        if path.endswith('pom.xml'):
            select(['repository-vulnerabilities'], 'Java dependency declaration: ' + path)
        if path.startswith('.env'):
            select(['deployment-validation', 'security', 'infrastructure-security'], 'Runtime environment template: ' + path)
        if path.endswith('.py') and path.startswith(('k8s/', 'observability/')):
            select(['python-sast'], 'Python implementation: ' + path)
        if path.startswith(('documentation/contracts/', 'tools/contracts/')):
            select(['contracts', 'security'], 'Contract specification/tooling: ' + path)
        if path.startswith('documentation/') or path.lower().endswith('.md'):
            select(['docs'], 'Documentation: ' + path)
    selected = [s for s in SERVICES if reasons[s]]
    flags = {name: bool(reasons[name]) for name in FLAGS}
    flags['containers'] = bool(selected)
    flags['system'] = any(flags[n] for n in ['api', 'browser', 'cooperation'])
    flags['quality'] = any(flags[n] for n in ['formatting', 'javascript', 'static-analysis', 'contracts', 'deployment-validation'])
    scanners = ['secret']
    if flags['repository-vulnerabilities']:
        scanners.append('vuln')
    if flags['infrastructure-security']:
        scanners.append('misconfig')
    return {'version': 1, 'full': full, 'paths': paths, 'services': selected, 'flags': flags, 'security_scanners': scanners, 'reasons': reasons}


def changed_paths(event, name, head):
    if name in ['workflow_dispatch', 'merge_group', 'schedule']:
        return [], True
    base = event.get('pull_request', {}).get('base', {}).get('sha') if name == 'pull_request' else event.get('before')
    if not base or not re.fullmatch(r'[0-9a-f]{40}', base) or set(base) == {'0'}:
        return [], True
    if not re.fullmatch(r'[0-9a-f]{40}', head):
        raise ValueError('Expected a full Git source revision.')
    result = subprocess.run(['git', 'diff', '--name-only', '--no-renames', '-z', base, head, '--'], cwd=ROOT, capture_output=True)
    if result.returncode:
        # An unavailable base must never make required validation disappear.
        return [], True
    return [p.decode('utf-8', errors='replace') for p in result.stdout.split(b'\0') if p], False


def summary(plan):
    rows = ['## 01 · Change plan', '', f'**Affected services:** {", ".join(plan["services"]) or "none"}', '',
            '| Track | Decision | Reason |', '| --- | --- | --- |']
    for track, selected in plan['flags'].items():
        why = '; '.join(plan['reasons'].get(track, [])[:3]) or ('Selected dependencies' if selected else 'No matching changes')
        rows.append(f'| {track} | {"RUN" if selected else "SKIP"} | {why.replace("|", "/")} |')
    rows += ['', '```mermaid', 'flowchart LR', '  Plan[01 Changes] --> Quality[02 Quality]',
             '  Quality --> Services[03 Service tests]', '  Services --> Images[04 Container scans]',
             '  Images --> System[05 System acceptance]', '  Plan --> Security[06 Security]',
             '  Plan --> Docs[07 Documentation]', '  System --> Gate[Ready to merge]',
             '  Security --> Gate', '  Docs --> Gate', '```', '']
    return '\n'.join(rows)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('paths', nargs='*')
    parser.add_argument('--full', action='store_true')
    parser.add_argument('--event', type=Path)
    parser.add_argument('--output', type=Path, default=ROOT / 'test-results/ci/plan.json')
    args = parser.parse_args()
    paths, full = args.paths, args.full
    event_path = args.event or (Path(os.environ['GITHUB_EVENT_PATH']) if os.environ.get('GITHUB_EVENT_PATH') else None)
    if event_path:
        paths, automatic_full = changed_paths(json.loads(event_path.read_text()), os.environ.get('GITHUB_EVENT_NAME', ''), os.environ.get('GITHUB_SHA', ''))
        full = full or automatic_full
    plan = make_plan(paths, full)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(plan, indent=2) + '\n', encoding='utf-8')
    if os.environ.get('GITHUB_OUTPUT'):
        with Path(os.environ['GITHUB_OUTPUT']).open('a', encoding='utf-8') as out:
            out.write('services=' + json.dumps(plan['services'], separators=(',', ':')) + '\n')
            out.write('plan=' + json.dumps(plan, separators=(',', ':')) + '\n')
    if os.environ.get('GITHUB_STEP_SUMMARY'):
        with Path(os.environ['GITHUB_STEP_SUMMARY']).open('a', encoding='utf-8') as out:
            out.write(summary(plan))
    print(json.dumps({'services': plan['services'], 'flags': plan['flags']}, indent=2))


if __name__ == '__main__':
    main()
