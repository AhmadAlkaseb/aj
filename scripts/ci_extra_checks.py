"""Run account-free containerised linters with immutable tool images and saved reports."""
import argparse
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
IMAGES = {
    'dockerfile-lint': 'hadolint/hadolint@sha256:32dac94127fd60b7b7e3fbfc65e1383b9b5e25c9bfd7b8536de7a539fe68a12d',
    'kubernetes-schema': 'ghcr.io/yannh/kubeconform@sha256:faffaf43f95aa6425306e1ab8d6fcad72acb9049158f38e574c085ea1ec0f64e',
    'history-secrets': 'zricethezav/gitleaks@sha256:c00b6bd0aeb3071cbcb79009cb16a60dd9e0a7c60e2be9ab65d25e6bc8abbb7f',
}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('check', choices=IMAGES)
    args = parser.parse_args()
    out = ROOT / 'test-results/ci-extra'
    out.mkdir(parents=True, exist_ok=True)
    command = ['docker', 'run', '--rm', '-v', str(ROOT) + ':/work:ro', '-v', str(out) + ':/reports', '-w', '/work']
    if args.check == 'dockerfile-lint':
        # Hadolint's image uses CMD rather than ENTRYPOINT.
        command += [IMAGES[args.check], 'hadolint', '--failure-threshold', 'error', '--format', 'json', 'Dockerfile', 'Dockerfile.ci']
    elif args.check == 'kubernetes-schema':
        # Only the two named KEDA CRDs are excluded; unknown built-in schemas fail.
        command += [IMAGES[args.check], '-strict', '-summary', '-output', 'json', '-kubernetes-version', '1.35.0',
                    '-skip', 'ScaledJob,TriggerAuthentication', 'k8s/base.json', 'k8s/deployment-rbac.yaml']
    else:
        command += [IMAGES[args.check], 'git', '/work', '--redact', '--no-banner',
                    '--config', '/work/.gitleaks.toml',
                    '--report-format', 'json', '--report-path', '/reports/history-secrets.json']
    with (out / (args.check + ('.log' if args.check == 'history-secrets' else '.json'))).open('w', encoding='utf-8') as report:
        result = subprocess.run(command, cwd=ROOT, stdout=report, check=False)
    print(args.check + ': ' + ('PASS' if result.returncode == 0 else 'FAILED') + '; reports: ' + str(out))
    raise SystemExit(result.returncode)


if __name__ == '__main__':
    main()
