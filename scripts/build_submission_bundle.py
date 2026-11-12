"""Package reviewable deliverables and source code without local credentials or build caches."""
import hashlib
import json
from pathlib import Path
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / 'documentation/submission'
SKIP_PARTS = {'.git', '.codex', '.agents', '.aws', '.ssh', '.vscode', 'node_modules', 'target', 'test-results', 'backups', '__pycache__'}


def main():
    # Include both committed and new implementation files; never copy ignored runtime secrets.
    listed = subprocess.check_output(['git', 'ls-files', '--cached', '--others', '--exclude-standard', '-z'], cwd=ROOT)
    candidates = sorted(set(p.decode('utf-8') for p in listed.split(b'\0') if p))
    source_zip = OUT / 'aj-motors-source.zip'
    members = []
    with zipfile.ZipFile(source_zip, 'w', compression=zipfile.ZIP_DEFLATED) as archive:
        for relative in candidates:
            path = ROOT / relative
            parts = Path(relative).parts
            if any(part in SKIP_PARTS for part in parts) or path.is_symlink() or not path.is_file():
                continue
            if path.name.startswith('.env') and not path.name.endswith('.example'):
                continue
            if path.suffix.lower() in {'.zip', '.jar', '.log', '.pyc'}:
                continue
            if relative == 'documentation/Report.pdf':
                continue
            if relative.startswith('documentation/submission/') and path.name != 'README.md':
                continue
            archive.write(path, 'aj/' + relative)
            members.append(relative)
    with zipfile.ZipFile(source_zip) as archive:
        assert archive.testzip() is None
        assert 'aj/scripts/configure_federation.py' in archive.namelist()
        assert 'aj/.github/workflows/ci.yaml' in archive.namelist()
        assert not any('/test-results/' in name or name.endswith('/.env') for name in archive.namelist())
    delivery_zip = OUT / 'aj-motors-delivery.zip'
    files = {
        'Report.pdf': ROOT / 'documentation/Report.pdf',
        'INSTALLATION.md': ROOT / 'documentation/LOCAL_SETUP.md',
        'KUBERNETES.md': ROOT / 'documentation/KUBERNETES.md',
        'EXTERNAL-LOGIN.md': ROOT / 'documentation/EXTERNAL-LOGIN.md',
        source_zip.name: source_zip,
    }
    for slug in ['mandatory-assignment-1', 'mandatory-assignment-2']:
        for extension in ['pptx', 'pdf']:
            files[slug + '.' + extension] = OUT / (slug + '.' + extension)
    with zipfile.ZipFile(delivery_zip, 'w', compression=zipfile.ZIP_DEFLATED) as archive:
        for name, path in files.items():
            archive.write(path, name)
        archive.writestr('REPOSITORY.txt', 'https://github.com/AhmadAlkaseb/aj\nSource ZIP is a local handoff, not proof that remote main contains these changes.\n')
        archive.writestr('README.txt',
            'AJ MOTORS - DELIVERY PACKAGE\n\n'
            'Report.pdf includes appendices A-E.\n'
            'Assignment 1 and 2: upload Report.pdf, the matching .pptx and repository link to itsLearning.\n'
            'Final project: upload Report.pdf, repository link and installation procedure to WISEflow.\n'
            'The .pdf slide previews are optional reading copies; .pptx files are editable.\n'
            'aj-motors-source.zip contains current local source, excluding ignored runtime secrets/caches.\n\n'
            'Remaining account steps: register owned GitHub OAuth app and run the setup in EXTERNAL-LOGIN.md;\n'
            'complete a real external login, publish source and retain actual CI run links,\n'
            'confirm individual reflection/contribution evidence and upload receipts.\n'
            'Local verification does not assert GitHub publication, cloud deployment or completed external login.\n')
    with zipfile.ZipFile(delivery_zip) as archive:
        assert archive.testzip() is None
    result = {'sourceFiles': len(members), 'sourceArchive': source_zip.name,
              'deliveryArchive': delivery_zip.name, 'remotePublication': False,
              'sourceSha256': hashlib.sha256(source_zip.read_bytes()).hexdigest(),
              'deliverySha256': hashlib.sha256(delivery_zip.read_bytes()).hexdigest()}
    (OUT / 'bundle.json').write_text(json.dumps(result, indent=2) + '\n', encoding='utf-8')
    print(json.dumps(result, indent=2))


if __name__ == '__main__':
    main()
