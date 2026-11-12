"""Render JUnit results as a native GitHub job summary; no privileged reporting action."""
import argparse
import glob
import os
import xml.etree.ElementTree as ET
from pathlib import Path


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('pattern')
    parser.add_argument('--title', default='Test results')
    args = parser.parse_args()
    rows = []
    for filename in sorted(glob.glob(args.pattern, recursive=True)):
        root = ET.parse(filename).getroot()
        suites = [root] if root.tag == 'testsuite' else root.findall('testsuite')
        for suite in suites:
            rows.append([suite.get('name', Path(filename).stem).replace('|', '/'), *[int(suite.get(k, 0)) for k in ['tests', 'failures', 'errors', 'skipped']]])
    lines = ['## ' + args.title, '', '| Suite | Tests | Failures | Errors | Skipped |', '| --- | ---: | ---: | ---: | ---: |']
    lines += ['| ' + ' | '.join(map(str, row)) + ' |' for row in rows]
    if not rows:
        lines.append('| No JUnit suites produced for this stage | 0 | 0 | 0 | 0 |')
    text = '\n'.join(lines) + '\n'
    print(text)
    if os.environ.get('GITHUB_STEP_SUMMARY'):
        with Path(os.environ['GITHUB_STEP_SUMMARY']).open('a', encoding='utf-8') as out:
            out.write(text)


if __name__ == '__main__':
    main()
