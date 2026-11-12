"""Apply the shared local administrator to existing services without resetting their data."""
import base64
import json
import os
import subprocess
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent


def docker(*args, data=None):
    result = subprocess.run(['docker', *args], cwd=ROOT, input=data, capture_output=True, timeout=120)
    if result.returncode:
        raise RuntimeError('Local configuration command failed: ' + ' '.join(args[:3]))
    return result.stdout.decode().strip()


def config():
    return json.loads(docker('compose', 'config', '--format', 'json'))


def credentials(configuration=None):
    env = (configuration or config())['services']['grafana']['environment']
    return env['GF_SECURITY_ADMIN_USER'], env['GF_SECURITY_ADMIN_PASSWORD']


def http(url, data=None, method='GET', headers=None):
    request = urllib.request.Request(url, method=method, headers={'Content-Type': 'application/json', **(headers or {})},
        data=None if data is None else json.dumps(data).encode())
    with urllib.request.urlopen(request, timeout=20) as response:
        raw = response.read()
        return json.loads(raw) if raw else None


def identity(username, password):
    import configure_identity
    base = os.environ.get('KEYCLOAK_ADMIN_URL', 'http://127.0.0.1:8180')
    token = None
    for user, secret in [(username, password), ('keycloak-admin', 'KeycloakAdmin123!')]:
        form = urllib.parse.urlencode({'client_id': 'admin-cli', 'grant_type': 'password', 'username': user, 'password': secret}).encode()
        try:
            with urllib.request.urlopen(base + '/realms/master/protocol/openid-connect/token', form, timeout=20) as response:
                token = json.load(response)['access_token']
            break
        except urllib.error.HTTPError as error:
            if error.code not in (400, 401):
                raise
    if token is None:
        raise RuntimeError('Could not authenticate the existing Keycloak administrator.')
    headers = {'Authorization': 'Bearer ' + token}

    def admin(realm, path, data=None, method='GET'):
        return http(base + '/admin/realms/' + realm + path, data, method, headers)

    for realm in ['master', 'aj']:
        users = admin(realm, '/users?' + urllib.parse.urlencode({'username': username, 'exact': 'true'}))
        if not users:
            admin(realm, '/users', {'username': username, 'enabled': True, 'emailVerified': True,
                  'firstName': 'Local', 'lastName': 'Administrator', 'email': username + '@aj.local'}, 'POST')
            users = admin(realm, '/users?' + urllib.parse.urlencode({'username': username, 'exact': 'true'}))
        user_id = users[0]['id']
        admin(realm, '/users/' + user_id + '/reset-password', {'type': 'password', 'value': password, 'temporary': False}, 'PUT')
        role = admin(realm, '/roles/admin')
        admin(realm, '/users/' + user_id + '/role-mappings/realm', [role], 'POST')
    os.environ.update({'KEYCLOAK_ADMIN_USER': username, 'KEYCLOAK_ADMIN_PASSWORD': password})
    configure_identity.BASE = base
    configure_identity.configure()
    print('AJ Motors and Keycloak: shared administrator configured.', flush=True)


def main():
    configuration = config()
    username, password = credentials(configuration)
    # Existing SQL service users and application connection strings remain valid.
    identifier = '"' + username.replace('"', '""') + '"'
    literal_user = "'" + username.replace("'", "''") + "'"
    literal_password = "'" + password.replace("'", "''") + "'"
    for service in ['users-db', 'products-db', 'orders-db', 'payments-db', 'keycloak-db']:
        env = configuration['services'][service]['environment']
        sql = ('DO $$ BEGIN IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname=' + literal_user + ') THEN CREATE ROLE ' + identifier + '; END IF; END $$; '
               'ALTER ROLE ' + identifier + ' WITH LOGIN SUPERUSER PASSWORD ' + literal_password + ';')
        docker('compose', 'exec', '-T', service, 'psql', '-U', env['POSTGRES_USER'], '-d', env['POSTGRES_DB'], '-v', 'ON_ERROR_STOP=1', data=sql.encode())
        print(service + ': shared administrator configured.', flush=True)
    users = docker('compose', 'exec', '-T', 'rabbitmq', 'rabbitmqctl', 'list_users', '--quiet')
    command = 'change_password' if any(line.split()[0] == username for line in users.splitlines() if line.split()) else 'add_user'
    docker('compose', 'exec', '-T', 'rabbitmq', 'rabbitmqctl', command, username, password)
    docker('compose', 'exec', '-T', 'rabbitmq', 'rabbitmqctl', 'set_user_tags', username, 'administrator')
    docker('compose', 'exec', '-T', 'rabbitmq', 'rabbitmqctl', 'set_permissions', '-p', '/', username, '.*', '.*', '.*')
    print('RabbitMQ: shared administrator configured.', flush=True)
    mongo = ('const database=db.getSiblingDB("admin"); const user=' + json.dumps(username) + '; const password=' + json.dumps(password) + '; '
             'if(database.getUser(user)){database.updateUser(user,{pwd:password,roles:["root"]});}'
             'else{database.createUser({user:user,pwd:password,roles:["root"]});}')
    docker('compose', 'exec', '-T', 'product-view-mongo', 'mongosh', '--quiet', '--eval', mongo)
    print('MongoDB: shared administrator configured.', flush=True)
    docker('compose', 'exec', '-T', 'grafana', 'grafana', 'cli', '--homepath', '/usr/share/grafana',
           'admin', 'reset-admin-password', '--password-from-stdin', data=(password + '\n').encode())
    for login in dict.fromkeys([username, 'admin']):
        auth = {'Authorization': 'Basic ' + base64.b64encode((login + ':' + password).encode()).decode()}
        try:
            profile = http('http://127.0.0.1:13001/api/user', headers=auth)
            if profile['login'] != username:
                http('http://127.0.0.1:13001/api/user', {'login': username, 'email': username + '@aj.local', 'name': 'Local administrator'}, 'PUT', auth)
            break
        except urllib.error.HTTPError as error:
            if error.code != 401:
                raise
    else:
        raise RuntimeError('Could not verify the shared Grafana login.')
    print('Grafana: shared administrator configured.', flush=True)
    deadline = time.monotonic() + 120
    while True:
        try:
            identity(username, password)
            break
        except urllib.error.HTTPError as error:
            if error.code not in (502, 503, 504) or time.monotonic() > deadline:
                raise
            time.sleep(3)
    print('Shared local login: ' + username + ' (password from AJ_ADMIN_PASSWORD).', flush=True)


if __name__ == '__main__':
    main()
