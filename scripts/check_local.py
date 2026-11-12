"""Verify the unified project, fixed container names and shared database/UI logins."""
import json
from configure_local import config, credentials, docker


def main():
    configuration = config()
    username, password = credentials(configuration)
    rows = json.loads(docker('inspect', *[service['container_name'] for service in configuration['services'].values()]))
    for row in rows:
        assert row['Config']['Labels']['com.docker.compose.project'] == 'aj', row['Name']
        assert row['State']['Running'], row['Name']
        assert row['State'].get('Health', {}).get('Status', 'healthy') == 'healthy', row['Name']
    databases = ['users-db', 'products-db', 'orders-db', 'payments-db', 'keycloak-db']
    for name in databases:
        database = configuration['services'][name]['environment']['POSTGRES_DB']
        user = docker('compose', 'exec', '-T', '-e', 'PGPASSWORD=' + password, name,
            'psql', '-h', name, '-U', username, '-d', database, '-tAc', 'select current_user')
        assert user == username, name
    output = docker('compose', 'exec', '-T', 'product-view-mongo', 'mongosh', '--quiet',
        '--username', username, '--password', password, '--authenticationDatabase', 'admin',
        '--eval', 'JSON.stringify(db.runCommand({connectionStatus:1}).authInfo.authenticatedUsers)')
    assert any(row['user'] == username for row in json.loads(output))
    print(json.dumps({'result': 'PASS', 'project': 'aj', 'runningContainers': len(rows),
        'fixedNames': 'PASS', 'sharedPostgresLogins': len(databases), 'sharedMongoLogin': 'PASS'}, indent=2))


if __name__ == '__main__':
    main()
