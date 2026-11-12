"""Keep a loopback operational UI forward alive across Kubernetes pod replacement."""
import os
import socket
import subprocess
import sys
import time


def main():
    service, local, remote = sys.argv[1:]
    options = {'creationflags': subprocess.CREATE_NO_WINDOW} if os.name == 'nt' else {}
    failures = 0
    while failures < 20:
        # Yield the port if another runtime has taken over (for example Compose).
        with socket.socket() as probe:
            if probe.connect_ex(('127.0.0.1', int(local))) == 0:
                return
        started = time.monotonic()
        subprocess.run(['kubectl', '--context', 'kind-aj-exam', '-n', 'aj',
            'port-forward', '--address=127.0.0.1', 'service/' + service, local + ':' + remote], **options)
        failures = 0 if time.monotonic() - started > 30 else failures + 1
        time.sleep(3)
    raise SystemExit('Cluster unavailable; run python scripts/kubernetes.py access when it is ready.')


if __name__ == '__main__':
    main()
