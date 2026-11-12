"""Exercise the real Grafana home, each database GUI and the Kubernetes proxy."""
import base64
import argparse
import json
import os
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / 'test-results/tools/python-deps'))
from playwright.sync_api import sync_playwright, expect
from configure_local import credentials, http


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--without-cluster', action='store_true', help='CI runner has no local kind cluster; still verify the GUI shell.')
    without_cluster = parser.parse_args().without_cluster
    username, password = credentials()
    auth = {'Authorization': 'Basic ' + base64.b64encode((username + ':' + password).encode()).decode()}
    dashboard = http('http://127.0.0.1:13001/api/dashboards/uid/aj-home', headers=auth)['dashboard']
    assert dashboard['title'] == 'AJ · Control Center'
    destination = ROOT / 'test-results/gui-hub'
    destination.mkdir(parents=True, exist_ok=True)
    with sync_playwright() as p:
        candidates = sorted((Path(os.environ.get('LOCALAPPDATA', '')) / 'ms-playwright').glob('chromium-*/chrome-win*/chrome.exe'), reverse=True)
        browser = p.chromium.launch(headless=True, **({'executable_path': str(candidates[0])} if candidates else {}))
        context = browser.new_context(viewport={'width': 1600, 'height': 1080}, http_credentials={'username': username, 'password': password})
        page = context.new_page()
        page.goto('http://localhost:13001/login')
        page.locator('input[name="user"]').fill(username)
        page.locator('input[name="password"]').fill(password)
        page.get_by_role('button', name='Log in', exact=True).click()
        expect(page.get_by_role('heading', name='AJ Control Center', exact=True)).to_be_visible(timeout=45000)
        for label in ['Users ↗', 'Products ↗', 'Orders ↗', 'Payments ↗', 'Keycloak DB ↗', 'Mongo Express ↗', 'Kubernetes GUI ↗', 'RabbitMQ ↗', 'Keycloak ↗']:
            expect(page.get_by_role('link', name=label, exact=True)).to_be_attached()
        page.screenshot(path=str(destination / 'grafana-home.png'), full_page=True)
        databases = []
        for server, database in [('users-db', 'users'), ('products-db', 'products'), ('orders-db', 'orders'), ('payments-db', 'payments'), ('keycloak-db', 'keycloak')]:
            tab = context.new_page()
            tab.goto(f'http://localhost:18080/?pgsql={server}&username={username}&db={database}')
            expect(tab.locator('input[name="auth[password]"]')).to_be_visible()
            tab.locator('input[name="auth[password]"]').fill(password)
            tab.locator('input[type="submit"]').first.click()
            expect(tab.locator('input[name="auth[password]"]')).to_have_count(0, timeout=20000)
            try:
                expect(tab.locator('a[href*="&sql="]')).to_be_visible(timeout=20000)
            except Exception:
                tab.screenshot(path=str(destination / (server + '-error.png')), full_page=True)
                print(server + ': ' + tab.inner_text('body')[:600])
                raise
            databases.append(database)
            tab.close()
        mongo = context.new_page()
        response = mongo.goto('http://localhost:18081/')
        assert response.status == 200
        expect(mongo.get_by_role('link', name='product_view', exact=True)).to_be_visible(timeout=20000)
        mongo.screenshot(path=str(destination / 'mongo-gui.png'), full_page=True)
        kube = context.new_page()
        response = kube.goto('http://localhost:14466/')
        assert response.status == 200
        kube.wait_for_timeout(2000)
        kube.screenshot(path=str(destination / 'kubernetes-gui.png'), full_page=True)
        if not without_cluster:
            cluster_response = context.request.get('http://localhost:14466/config')
            assert cluster_response.ok, 'Headlamp cluster discovery failed'
            clusters = cluster_response.json()
            assert 'kind-aj-exam' in json.dumps(clusters), 'Headlamp has no configured local cluster'
            nodes = context.request.get('http://localhost:14466/clusters/kind-aj-exam/api/v1/nodes')
            assert nodes.ok, 'Headlamp cannot read cluster nodes'
            assert nodes.json()['items'], 'No Kubernetes nodes returned'
            secrets = context.request.get('http://localhost:14466/clusters/kind-aj-exam/api/v1/namespaces/aj/secrets')
            assert secrets.status == 403, 'GUI identity must not be able to read Secrets'
        browser.close()
    result = {'result': 'PASS', 'grafanaHome': 'PASS', 'postgresGuiLogins': databases,
              'mongoGuiLogin': 'PASS', 'kubernetesGui': 'PASS',
              'kubernetesNodes': 'not selected on ephemeral CI runner' if without_cluster else 'PASS',
              'kubernetesReadOnly': 'not selected on ephemeral CI runner' if without_cluster else 'PASS'}
    (destination / 'results.json').write_text(json.dumps(result, indent=2), encoding='utf-8')
    print(json.dumps(result, indent=2))


if __name__ == '__main__':
    main()
