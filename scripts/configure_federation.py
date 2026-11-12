"""Configure or inspect real GitHub identity brokering without exposing OAuth secrets.

`setup` prompts locally for an owned OAuth app, `check` reports live configuration,
and `verify` checks that Keycloak redirects to GitHub with that app and callback.
No GitHub CLI, repository token, paid service or database reset is required.
"""
import argparse
import getpass
import json
import os
from pathlib import Path
import sys
import urllib.error
import urllib.parse
import urllib.request

ROOT = Path(__file__).resolve().parent.parent
LOCAL = ROOT / 'test-results/identity/github-oauth.json'


def specification(client_id, secret):
    if not client_id.strip() or not secret.strip():
        raise ValueError('Both OAuth application credentials are required; no placeholder provider is enabled.')
    return {
        'alias': 'github', 'displayName': 'GitHub', 'providerId': 'github',
        'enabled': True, 'trustEmail': False, 'storeToken': False,
        'addReadTokenRoleOnCreate': False, 'linkOnly': False,
        'authenticateByDefault': False,
        'firstBrokerLoginFlowAlias': 'first broker login',
        'config': {'clientId': client_id.strip(), 'clientSecret': secret,
                   'defaultScope': 'user:email', 'syncMode': 'IMPORT'},
    }


class Keycloak:
    def __init__(self):
        self.base = os.environ.get('KEYCLOAK_ADMIN_URL', 'http://127.0.0.1:8180').rstrip('/')
        parsed = urllib.parse.urlsplit(self.base)
        if parsed.scheme != 'https' and not (parsed.scheme == 'http' and parsed.hostname in {'localhost', '127.0.0.1', '::1'}):
            raise ValueError('Use HTTPS for remote Keycloak administration.')
        from configure_local import credentials
        user, password = credentials()
        form = urllib.parse.urlencode({
            'client_id': 'admin-cli', 'grant_type': 'password',
            'username': os.environ.get('KEYCLOAK_ADMIN_USER', user),
            'password': os.environ.get('KEYCLOAK_ADMIN_PASSWORD', password),
        }).encode()
        with urllib.request.urlopen(self.base + '/realms/master/protocol/openid-connect/token', form, timeout=20) as response:
            self.token = json.load(response)['access_token']

    def request(self, path, method='GET', value=None):
        request = urllib.request.Request(self.base + '/admin/realms/aj' + path,
            method=method, data=None if value is None else json.dumps(value).encode(),
            headers={'Content-Type': 'application/json', 'Authorization': 'Bearer ' + self.token})
        with urllib.request.urlopen(request, timeout=20) as response:
            data = response.read()
            return json.loads(data) if data else None


def configure(api, client_id, secret):
    desired = specification(client_id, secret)
    providers = api.request('/identity-provider/instances')
    existing = next((p for p in providers if p['alias'] == 'github'), None)
    if existing:
        if existing['providerId'] != 'github':
            raise ValueError('The github alias is already owned by another provider; no changes made.')
        # Retain unrelated provider settings/mappers, replace the security policy explicitly.
        desired = {**existing, **desired, 'config': {**existing.get('config', {}), **desired['config']}}
        api.request('/identity-provider/instances/github', 'PUT', desired)
    else:
        api.request('/identity-provider/instances', 'POST', desired)
    return {'provider': 'github', 'enabled': True, 'externalLoginCompleted': False}


def verify_redirect(base, client_id):
    sys.path.insert(0, str(ROOT / 'test-results/tools/python-deps'))
    from playwright.sync_api import sync_playwright
    with sync_playwright() as p:
        candidates = sorted((Path(os.environ.get('LOCALAPPDATA', '')) / 'ms-playwright').glob('chromium-*/chrome-win*/chrome.exe'), reverse=True)
        browser = p.chromium.launch(**({'executable_path': str(candidates[0])} if candidates else {}))
        context = browser.new_context()
        verifier = 'a' * 64
        import base64
        import hashlib
        challenge = base64.urlsafe_b64encode(hashlib.sha256(verifier.encode()).digest()).rstrip(b'=').decode()
        page = context.new_page()
        query = urllib.parse.urlencode({'client_id': 'aj-frontend', 'response_type': 'code',
            'redirect_uri': 'http://localhost:3000/', 'scope': 'openid profile email',
            'state': 'federation-configuration-probe', 'code_challenge': challenge,
            'code_challenge_method': 'S256', 'kc_idp_hint': 'github'})
        # Observe the genuine broker redirect without logging into another person's account.
        observed = []
        def capture(route):
            observed.append(route.request.url)
            route.fulfill(status=200, content_type='text/plain', body='GitHub OAuth authorization redirect verified.')
        page.route('https://github.com/login/oauth/authorize**', capture)
        page.goto(base.rstrip('/') + '/realms/aj/protocol/openid-connect/auth?' + query, wait_until='domcontentloaded')
        assert observed, 'Keycloak did not initiate a GitHub authorization request'
        params = urllib.parse.parse_qs(urllib.parse.urlsplit(observed[0]).query)
        assert params.get('client_id') == [client_id], 'Wrong OAuth app selected'
        callback = params.get('redirect_uri', [''])[0]
        assert callback.endswith('/realms/aj/broker/github/endpoint'), 'Wrong broker callback'
        assert params.get('state', [''])[0], 'Missing broker OAuth state'
        assert 'repo' not in params.get('scope', [''])[0].split(), 'Login must not request repository access'
        browser.close()
    return {'brokerAuthorizationRedirect': 'PASS', 'externalAccountLogin': 'not executed by this configuration probe', 'callback': callback}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('command', choices=['setup', 'apply', 'check', 'verify'])
    args = parser.parse_args()
    if args.command == 'setup':
        print('Register an owned GitHub OAuth app: https://github.com/settings/applications/new')
        print('Homepage: http://localhost:3000/  Callback: http://localhost:8180/realms/aj/broker/github/endpoint')
        client_id = input('Client ID: ').strip()
        secret = getpass.getpass('Client secret (hidden): ')
        # Validate before saving or contacting the running system.
        specification(client_id, secret)
        result = configure(Keycloak(), client_id, secret)
        LOCAL.parent.mkdir(parents=True, exist_ok=True)
        LOCAL.write_text(json.dumps({'clientId': client_id, 'clientSecret': secret}), encoding='utf-8')
        print('Provider configured. Credentials retained only in ignored test-results/identity.')
    elif args.command == 'apply':
        stored = json.loads(LOCAL.read_text(encoding='utf-8')) if LOCAL.exists() else {}
        client_id = os.environ.get('GITHUB_OAUTH_CLIENT_ID', stored.get('clientId', ''))
        secret = os.environ.get('GITHUB_OAUTH_CLIENT_SECRET', stored.get('clientSecret', ''))
        result = configure(Keycloak(), client_id, secret)
    else:
        api = Keycloak()
        providers = api.request('/identity-provider/instances')
        provider = next((p for p in providers if p['alias'] == 'github' and p['providerId'] == 'github'), None)
        result = {'provider': 'github', 'configured': bool(provider and provider.get('enabled')),
                  'externalLoginCompleted': 'requires real user browser verification'}
        if args.command == 'verify':
            if not result['configured']:
                raise ValueError('Register an owned OAuth app and run setup first; local Keycloak is not external federation.')
            result.update(verify_redirect('http://localhost:8180', provider['config']['clientId']))
    print(json.dumps(result, indent=2))


if __name__ == '__main__':
    try:
        main()
    except urllib.error.HTTPError as error:
        raise SystemExit(f'Identity administration failed with HTTP {error.code}; response/credentials withheld.') from None
    except (ValueError, OSError) as error:
        # Do not print request bodies or imported OAuth credentials.
        raise SystemExit(str(error)) from None
