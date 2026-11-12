"""Idempotently configure the existing local Keycloak realm, without deleting identities.

Run after Keycloak is healthy. Development credentials come from the local Compose setup.
The test client is confidential, has no browser redirects, and must never be put in frontend code.
"""
import json
import os
import urllib.request
import urllib.parse

BASE = os.environ.get('KEYCLOAK_ADMIN_URL', 'http://127.0.0.1:8180')


def configure():
    form = urllib.parse.urlencode({'client_id': 'admin-cli', 'grant_type': 'password',
        'username': os.environ.get('KEYCLOAK_ADMIN_USER', os.environ.get('AJ_ADMIN_USER', 'admin')),
        'password': os.environ.get('KEYCLOAK_ADMIN_PASSWORD', os.environ.get('AJ_ADMIN_PASSWORD', 'Admin123!'))}).encode()
    with urllib.request.urlopen(BASE + '/realms/master/protocol/openid-connect/token', form, timeout=15) as r:
        token = json.load(r)['access_token']

    def admin(path, method='GET', value=None):
        req = urllib.request.Request(BASE + '/admin/realms/aj' + path, method=method,
            data=None if value is None else json.dumps(value).encode(),
            headers={'Content-Type': 'application/json', 'Authorization': 'Bearer ' + token})
        with urllib.request.urlopen(req, timeout=15) as r:
            raw = r.read()
            return json.loads(raw) if raw else None

    realm = admin('')
    admin('', 'PUT', {'registrationAllowed': True, 'registrationEmailAsUsername': False})
    role = admin('/roles/customer')
    default = realm['defaultRole']['name']
    admin('/roles/' + default + '/composites', 'POST', [role])
    profile = admin('/users/profile')
    attrs = [a for a in profile['attributes'] if a['name'] != 'customer_guid']
    attrs.append({'name': 'customer_guid', 'displayName': 'Customer reference',
                  'permissions': {'view': ['admin'], 'edit': ['admin']},
                  'multivalued': False})
    profile['attributes'] = attrs
    admin('/users/profile', 'PUT', profile)
    source = admin('/clients?clientId=aj-frontend')[0]
    clients = admin('/clients?clientId=aj-tests')
    spec = {'clientId': 'aj-tests', 'name': 'Local backend verification only',
            'enabled': True, 'publicClient': False, 'serviceAccountsEnabled': True,
            'standardFlowEnabled': False, 'directAccessGrantsEnabled': False,
            'secret': os.environ.get('AJ_TEST_CLIENT_SECRET', 'aj-tests-local'),
            'protocol': 'openid-connect', 'protocolMappers': source['protocolMappers']}
    # Server-generated mapper IDs must not be copied between clients.
    for mapper in spec['protocolMappers']:
        mapper.pop('id', None)
    if clients:
        client_id = clients[0]['id']; admin('/clients/' + client_id, 'PUT', spec)
    else:
        admin('/clients', 'POST', spec); client_id = admin('/clients?clientId=aj-tests')[0]['id']
    service = admin('/clients/' + client_id + '/service-account-user')
    admin('/users/' + service['id'] + '/role-mappings/realm', 'POST', [admin('/roles/admin')])
    print('Local registration, protected customer mapping and confidential test client configured.')


if __name__ == '__main__':
    configure()
