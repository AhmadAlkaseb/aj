"""Real Keycloak Authorization Code + PKCE browser test.

Requires Python Playwright and Chromium: python scripts/check_oidc.py [--failures]
No password grant, external account, or raw token output is used.
"""
from pathlib import Path
import base64
import hashlib
import json
import os
import subprocess
import sys
import time
import urllib.parse
import urllib.request
import uuid
from playwright.sync_api import sync_playwright, expect

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / 'test-results'
OUT.mkdir(exist_ok=True)


def compose(*args):
    subprocess.run(['docker', 'compose', *args], cwd=ROOT, check=True, stdout=subprocess.DEVNULL)


def main():
    discovery = json.load(urllib.request.urlopen('http://127.0.0.1:8180/realms/aj/.well-known/openid-configuration', timeout=10))
    assert discovery['issuer'] == 'http://localhost:8180/realms/aj'
    results = []
    with sync_playwright() as p:
        candidates = sorted((Path(os.environ.get('LOCALAPPDATA', '/tmp')) / 'ms-playwright').glob('chromium-*/chrome-win64/chrome.exe'), reverse=True)
        browser = p.chromium.launch(headless=True, **({'executable_path': str(candidates[0])} if candidates else {}))
        for username, password, role in [('kunde01', 'Demo123!', 'customer'), ('eksamen-admin', 'Admin123!', 'admin')]:
            context = browser.new_context(viewport={'width': 1440, 'height': 1000})
            page = context.new_page()
            errors = []
            page.on('pageerror', lambda e: errors.append(str(e)))
            page.goto('http://localhost:3000')
            expect(page.locator('#login-button')).to_be_enabled(timeout=30000)
            missing = context.request.get('http://localhost:3000/api/v1/auth/session')
            assert missing.status == 401
            page.locator('#login-button').click()
            expect(page.locator('#username')).to_be_visible(timeout=20000)
            query = urllib.parse.parse_qs(urllib.parse.urlparse(page.url).query)
            assert query['response_type'] == ['code']
            assert query['code_challenge_method'] == ['S256']
            assert query.get('state') and query.get('nonce') and query.get('code_challenge')
            page.locator('#username').fill(username)
            page.locator('#password').fill(password)
            page.locator('#kc-login').click()
            expect(page.locator('#identity-label')).to_have_text(username, timeout=30000)
            expect(page.locator('#logout-button')).to_be_visible()
            headers = page.evaluate('async () => await window.ajAuth.headers()')
            session = context.request.get('http://localhost:3000/api/v1/auth/session', headers=headers)
            assert session.status == 200, session.status
            identity = session.json()
            assert identity['username'] == username and role in identity['roles']
            if role == 'customer':
                expected_guid = str(uuid.UUID(bytes=hashlib.md5(b'aj-demo-v1:users:1', usedforsecurity=False).digest(), version=3))
                assert identity['customerGuid'] == expected_guid
            # An edited payload cannot grant a role or identity without a provider signature.
            parts = headers['Authorization'].removeprefix('Bearer ').split('.')
            payload = json.loads(base64.urlsafe_b64decode(parts[1] + '=' * (-len(parts[1]) % 4)))
            payload['preferred_username'] = 'forged-admin'
            parts[1] = base64.urlsafe_b64encode(json.dumps(payload).encode()).decode().rstrip('=')
            forged = context.request.get('http://localhost:3000/api/v1/auth/session', headers={'Authorization':'Bearer ' + '.'.join(parts)})
            assert forged.status == 401
            page.reload()
            expect(page.locator('#identity-label')).to_have_text(username, timeout=30000)
            page.screenshot(path=str(OUT / f'oidc-{role}.png'))
            page.locator('#logout-button').click()
            expect(page.locator('#identity-label')).to_have_text('Gæst', timeout=30000)
            expect(page.locator('#login-button')).to_be_visible()
            expect(page.locator('#login-button')).to_be_enabled(timeout=30000)
            assert page.evaluate('async () => await window.ajAuth.headers()') == {}
            assert context.request.get('http://localhost:3000/api/v1/auth/session').status == 401
            assert not errors, errors
            results.append({'username':username, 'role':role, 'pkce':'S256', 'login':'PASS', 'reload':'PASS', 'logout':'PASS', 'forgedToken':'401'})
            context.close()
        if '--failures' in sys.argv:
            context = browser.new_context()
            page = context.new_page()
            try:
                compose('stop', 'keycloak')
                page.goto('http://localhost:3000')
                expect(page.locator('#auth-notice')).to_be_visible(timeout=30000)
                expect(page.locator('#cars .car-card').first).to_be_visible(timeout=30000)
                expect(page.locator('#outage-message')).to_contain_text('Keycloak', timeout=20000)
                assert context.request.get('http://localhost:3000/').status == 200
            finally:
                compose('start', 'keycloak')
                deadline = time.monotonic() + 90
                while time.monotonic() < deadline:
                    try:
                        status = json.load(urllib.request.urlopen('http://127.0.0.1:3000/api/v1/status', timeout=8))
                        if status['status'] == 'UP': break
                    except OSError:
                        pass
                    time.sleep(1)
                else:
                    raise AssertionError('Identity provider did not recover')
            context.close()
        browser.close()
    result = {'result':'PASS', 'provider':'local Keycloak', 'cases':results, 'outageChecked':'--failures' in sys.argv}
    (OUT / 'oidc-results.json').write_text(json.dumps(result, indent=2), encoding='utf-8')
    print(json.dumps(result, indent=2))


if __name__ == '__main__':
    main()
