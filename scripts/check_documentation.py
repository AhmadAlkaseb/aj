"""Validate report images and SVG XML without installing a browser or TeX in CI."""
import re
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent


def main():
    count = 0
    for path in (ROOT / 'documentation').rglob('*.md'):
        source = path.read_text(encoding='utf-8')
        for target in re.findall(r'!\[[^\]]*\]\(<?([^>\s]+)>?\)', source):
            if re.match(r'^[a-z]+:', target):
                continue
            assert (path.parent / target).exists(), f'{path.relative_to(ROOT)}: missing image {target}'
            count += 1
    diagrams = list((ROOT / 'documentation/report-figures').glob('*.svg'))
    for path in diagrams:
        ET.parse(path)
    print(f'Documentation integrity passed: {count} local image references, {len(diagrams)} report SVGs.')


if __name__ == '__main__':
    main()
