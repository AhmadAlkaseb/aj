"""Verify the delivered report and presentations match their current editable sources."""
import hashlib
import json
from pathlib import Path
import sys
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / 'test-results/tools/python-deps'))
from pypdf import PdfReader


def main():
    docs = ROOT / 'documentation'
    report = (docs / 'Report.md').read_text(encoding='utf-8')
    evidence = json.loads((docs / 'evidence/report-export.json').read_text(encoding='utf-8'))
    assert evidence['sourceSha256'] == hashlib.sha256(report.encode()).hexdigest(), 'Report PDF must be regenerated after source changes'
    assert evidence['styleSha256'] == hashlib.sha256((docs / 'report-style.tex').read_bytes()).hexdigest(), 'Report style changed after export'
    pdf = docs / 'Report.pdf'
    assert evidence['sha256'] == hashlib.sha256(pdf.read_bytes()).hexdigest(), 'Report PDF differs from export evidence'
    reader = PdfReader(pdf)
    assert len(reader.pages) <= 30
    assert len(report) <= 72000
    text = '\n'.join(page.extract_text() or '' for page in reader.pages)
    for appendix in 'ABCDE':
        label = 'Appendix ' + appendix + '.'
        assert '## ' + label in report, 'Appendix must be included, not just linked: ' + label
        assert label in text, 'Appendix absent from delivered PDF: ' + label
    assert 'twenty-one' not in report, 'Obsolete Compose count'
    assert 'does not currently issue credentials, authenticate users or validate roles' not in report
    assert 'Ready to merge' in text and 'Control Center' in text
    slide_results = {}
    for slug in ['mandatory-assignment-1', 'mandatory-assignment-2']:
        path = docs / 'submission' / (slug + '.pptx')
        with zipfile.ZipFile(path) as archive:
            assert archive.testzip() is None
            slides = [n for n in archive.namelist() if n.startswith('ppt/slides/slide') and n.endswith('.xml')]
            notes = [n for n in archive.namelist() if n.startswith('ppt/notesSlides/notesSlide') and n.endswith('.xml')]
            assert len(slides) == 9 and len(notes) == 9
            for name in slides + notes:
                ET.fromstring(archive.read(name))
        preview = PdfReader(docs / 'submission' / (slug + '.pdf'))
        assert len(preview.pages) == 9, 'Presentation PDF pagination differs from slide count'
        slide_results[slug] = len(slides)
    result = {'result': 'PASS', 'reportPages': len(reader.pages), 'reportSourceCharacters': len(report),
              'embeddedAppendices': list('ABCDE'), 'editablePresentations': slide_results,
              'externalGitHubLogin': 'requires owned OAuth app and actual user login',
              'remoteCi': 'not inferred from local verification'}
    destination = docs / 'evidence/submission-verification.json'
    destination.write_text(json.dumps(result, indent=2) + '\n', encoding='utf-8')
    print(json.dumps(result, indent=2))


if __name__ == '__main__':
    main()
