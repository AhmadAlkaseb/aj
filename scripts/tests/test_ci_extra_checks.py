"""Keep account-free tool pins and the JDBC false-positive exception narrow."""
from pathlib import Path
import re
import sys
import tomllib
import unittest

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'scripts'))
from ci_extra_checks import IMAGES


class ExtraChecksTests(unittest.TestCase):
    def test_container_tools_use_immutable_digests(self):
        for image in IMAGES.values():
            self.assertRegex(image, r'@sha256:[0-9a-f]{64}$')

    def test_jdbc_exception_does_not_ignore_files_or_real_credentials(self):
        config = tomllib.loads((ROOT / '.gitleaks.toml').read_text())
        self.assertTrue(config['extend']['useDefault'])
        self.assertEqual(len(config['allowlists']), 1)
        allowlist = config['allowlists'][0]
        self.assertNotIn('paths', allowlist)
        self.assertNotIn('commits', allowlist)
        regex, = allowlist['regexes']
        self.assertIsNotNone(re.fullmatch(regex, 'keycloak-db:5432/keycloak"'))
        self.assertIsNone(re.fullmatch(regex, 'keycloak-db:my-secret-password"'))


if __name__ == '__main__':
    unittest.main()
