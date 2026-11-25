"""Publish separate JaCoCo track coverage without a third-party coverage account."""
import argparse
import os
from pathlib import Path
import xml.etree.ElementTree as E

ROOT = Path(__file__).resolve().parent.parent


def coverage_rows(root=ROOT):
    rows = []
    for report in sorted(root.glob('*/target/site/jacoco/jacoco.xml')):
        counters = {c.get('type'): (int(c.get('covered')), int(c.get('missed'))) for c in E.parse(report).getroot().findall('counter')}
        values = []
        for kind in ['LINE', 'BRANCH']:
            covered, missed = counters.get(kind, (0, 0))
            values.append(f'{100 * covered / (covered + missed):.1f}% ({covered}/{covered + missed})' if covered + missed else 'n/a')
        rows.append((report.relative_to(root).parts[0], *values))
    return rows


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--title', default='Java coverage')
    args = parser.parse_args()
    rows = coverage_rows()
    lines = ['## ' + args.title, '', '| Module | Lines | Branches |', '| --- | ---: | ---: |']
    lines += ['| ' + ' | '.join(row) + ' |' for row in rows]
    if not rows:
        lines += ['', 'No JaCoCo report produced for this track; this is not 100% coverage.']
    lines += ['', 'Unit and integration tracks are measured separately. Download the HTML report for class details.',
              'Coverage is reported, not enforced as an arbitrary percentage threshold.', '']
    text = '\n'.join(lines)
    print(text)
    if os.environ.get('GITHUB_STEP_SUMMARY'):
        with Path(os.environ['GITHUB_STEP_SUMMARY']).open('a', encoding='utf-8') as output:
            output.write(text)


if __name__ == '__main__':
    main()
