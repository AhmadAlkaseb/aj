"""Provider configuration must preserve authentication boundaries and be idempotent."""
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from configure_federation import configure, specification


class FakeKeycloak:
    def __init__(self, providers=None):
        self.providers = providers or []
        self.writes = []

    def request(self, path, method='GET', value=None):
        if method == 'GET':
            return self.providers
        self.writes.append((path, method, value))


class FederationTest(unittest.TestCase):
    def test_no_placeholder_credentials(self):
        for client, secret in [('', ''), ('id', ''), (' ', 'secret')]:
            with self.assertRaises(ValueError):
                specification(client, secret)

    def test_login_has_no_repository_access_or_email_autolinking(self):
        provider = specification('owned-app', 'local-secret')
        self.assertEqual(provider['config']['defaultScope'], 'user:email')
        self.assertFalse(provider['trustEmail'])
        self.assertFalse(provider['storeToken'])
        self.assertFalse(provider['addReadTokenRoleOnCreate'])
        self.assertEqual(provider['firstBrokerLoginFlowAlias'], 'first broker login')

    def test_create_only_provider_no_realm_reset(self):
        api = FakeKeycloak()
        result = configure(api, 'owned-app', 'local-secret')
        self.assertEqual([(p, m) for p, m, _ in api.writes], [('/identity-provider/instances', 'POST')])
        self.assertNotIn('clientSecret', result)
        self.assertFalse(result['externalLoginCompleted'])

    def test_update_preserves_unrelated_config(self):
        api = FakeKeycloak([{'alias': 'github', 'providerId': 'github', 'internalId': 'keep',
                             'config': {'customSetting': 'keep', 'clientSecret': 'old'}}])
        configure(api, 'owned-app', 'new-secret')
        path, method, provider = api.writes[0]
        self.assertEqual((path, method), ('/identity-provider/instances/github', 'PUT'))
        self.assertEqual(provider['internalId'], 'keep')
        self.assertEqual(provider['config']['customSetting'], 'keep')
        self.assertEqual(provider['config']['clientSecret'], 'new-secret')

    def test_alias_collision_does_not_overwrite_other_provider(self):
        api = FakeKeycloak([{'alias': 'github', 'providerId': 'oidc'}])
        with self.assertRaises(ValueError):
            configure(api, 'owned-app', 'secret')
        self.assertFalse(api.writes)


if __name__ == '__main__':
    unittest.main()
