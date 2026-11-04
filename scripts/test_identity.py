"""Short-lived backend test credentials. Tokens/passwords are never logged."""
import json
import os
import time
import urllib.parse
import urllib.request

_token = None
_expires = 0


def admin_headers():
    global _token, _expires
    if time.monotonic() >= _expires:
        form = urllib.parse.urlencode({'grant_type': 'client_credentials', 'client_id': 'aj-tests',
            'client_secret': os.environ.get('AJ_TEST_CLIENT_SECRET', 'aj-tests-local')}).encode()
        url = os.environ.get('KEYCLOAK_TEST_URL', 'http://127.0.0.1:8180')
        with urllib.request.urlopen(url + '/realms/aj/protocol/openid-connect/token', form, timeout=10) as r:
            data = json.load(r)
        _token = data['access_token']; _expires = time.monotonic() + data['expires_in'] - 30
    return {'Authorization': 'Bearer ' + _token}
