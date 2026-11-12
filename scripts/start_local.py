"""Start the complete aj Compose project, preserving previous monitoring and cluster data."""
import os
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
MONITORING = ['prometheus', 'loki', 'grafana', 'alloy']


def run(*args, capture=False):
    result = subprocess.run(list(args), cwd=ROOT, check=True, capture_output=capture, text=True)
    return result.stdout.strip() if capture else None


def pause_cluster():
    # The GUI keeps the control plane reachable; prepare_admin suspends app workloads.
    if os.name == 'nt':
        script_path = str(ROOT / 'scripts/forward_operations.py').replace("'", "''")
        command = ("Get-CimInstance Win32_Process | Where-Object { "
            "($_.Name -like 'python*' -and $_.CommandLine -like '*" + script_path + "*') -or "
            "($_.Name -eq 'kubectl.exe' -and $_.CommandLine -match '^kubectl --context kind-aj-exam -n aj port-forward --address=127\\.0\\.0\\.1 service/(grafana|rabbitmq|prometheus|loki) ') "
            "} | ForEach-Object { Stop-Process -Id $_.ProcessId -ErrorAction SilentlyContinue }")
        run('powershell', '-NoProfile', '-Command', command)


def migrate_monitoring():
    old = run('docker', 'ps', '-aq', '--filter', 'label=com.docker.compose.project=aj-monitoring', capture=True).split()
    if old:
        run('docker', 'stop', *old)
        run('docker', 'rm', *old)
    volumes = set(run('docker', 'volume', 'ls', '-q', capture=True).splitlines())
    for name in MONITORING:
        source = 'aj-monitoring_' + name + '-data'
        target = 'aj_' + name + '-data'
        run('docker', 'volume', 'create', '--label', 'com.docker.compose.project=aj',
            '--label', 'com.docker.compose.volume=' + name + '-data', target)
        if source not in volumes:
            continue
        print('Preserving ' + name + ' data in ' + target, flush=True)
        # Both mount paths are fixed; the original volume is mounted read-only and retained.
        run('docker', 'run', '--rm', '--name', 'aj-volume-migration', '--label', 'com.docker.compose.project=aj',
            '--entrypoint', 'sh', '-v', source + ':/from:ro', '-v', target + ':/to', 'postgres:16-alpine',
            '-c', 'if [ -z "$(ls -A /to)" ]; then cp -a /from/. /to/; else echo "Existing target data retained"; fi')


def main():
    pause_cluster()
    import prepare_admin
    prepare_admin.prepare()
    migrate_monitoring()
    run('docker', 'compose', 'build')
    infrastructure = ['rabbitmq', 'users-db', 'products-db', 'orders-db', 'payments-db', 'keycloak-db',
                      'product-view-mongo', 'keycloak', *MONITORING]
    run('docker', 'compose', 'up', '-d', '--wait', '--wait-timeout', '300', *infrastructure)
    import configure_local
    configure_local.main()
    run('docker', 'compose', 'up', '-d', '--wait', '--wait-timeout', '300')
    prepare_admin.connect()
    print('All aj services are running. Frontend: http://localhost:3000; Grafana: http://localhost:13001; RabbitMQ: http://localhost:15672', flush=True)


if __name__ == '__main__':
    main()
