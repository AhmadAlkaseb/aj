"""Resolve version tags to immutable commits when deliberately updating Actions."""
import json
import re
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DIRECTORY = ROOT / '.github/workflows'
PATTERN = re.compile(r'(uses:\s+)([A-Za-z0-9_.-]+/[A-Za-z0-9_./-]+)@([A-Za-z0-9_.-]+)(?:\s+#\s*([^\n]+))?')


def get(url):
    request = urllib.request.Request(url, headers={'Accept': 'application/vnd.github+json', 'User-Agent': 'AJ-workflow-maintenance'})
    with urllib.request.urlopen(request, timeout=30) as response:
        return json.load(response)


def main():
    lock_path = ROOT / '.github/actions-lock.json'
    previous = json.loads(lock_path.read_text(encoding='utf-8')) if lock_path.exists() else {}
    resolved = {}
    for path in sorted(DIRECTORY.glob('*.yaml')):
        source = path.read_text(encoding='utf-8')
        def replace(match):
            prefix, action, ref, comment = match.groups()
            repo = '/'.join(action.split('/')[:2])
            if re.fullmatch('[0-9a-f]{40}', ref):
                resolved[action] = previous.get(action, {'commit': ref, 'version': comment or ref})
                return match[0]
            if action not in resolved:
                version = ref
                try:
                    commit = get('https://api.github.com/repos/' + repo + '/commits/' + urllib.parse.quote(ref, safe=''))['sha']
                except urllib.error.HTTPError as error:
                    if error.code != 404:
                        raise
                    version = get('https://api.github.com/repos/' + repo + '/releases/latest')['tag_name']
                    commit = get('https://api.github.com/repos/' + repo + '/commits/' + urllib.parse.quote(version, safe=''))['sha']
                if not re.fullmatch('[0-9a-f]{40}', commit):
                    raise ValueError('GitHub did not return a full commit for ' + action)
                resolved[action] = {'version': version, 'commit': commit, 'source': 'https://github.com/' + repo}
                print(action + ': ' + version + ' -> ' + commit[:12], flush=True)
            entry = resolved[action]
            return prefix + action + '@' + entry['commit'] + ' # ' + entry['version']
        path.write_text(PATTERN.sub(replace, source), encoding='utf-8')
    lock_path.write_text(json.dumps(resolved, indent=2, sort_keys=True) + '\n', encoding='utf-8')
    version = get('https://pypi.org/pypi/zizmor/json')['info']['version']
    (ROOT / 'scripts/requirements-ci.txt').write_text('PyYAML==6.0.2\nzizmor==' + version + '\n', encoding='utf-8')
    print('Pinned free zizmor CLI: ' + version)


if __name__ == '__main__':
    main()
