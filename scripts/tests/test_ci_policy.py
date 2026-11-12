"""Exercise change selection and fail-closed workflow dependency boundaries."""
import copy
import json
import sys
import unittest
from pathlib import Path
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'scripts'))
from ci_gate import evaluate
from ci_plan import SERVICES, changed_paths, make_plan


class PlanningTests(unittest.TestCase):
    def test_docs_only_never_build_or_scan_application_images(self):
        plan = make_plan(['documentation/Report.md', 'documentation/report-figures/architecture.svg'])
        self.assertEqual(plan['services'], [])
        self.assertTrue(plan['flags']['docs'])
        self.assertFalse(any(plan['flags'][key] for key in ['containers', 'system', 'quality', 'security', 'codeql']))

    def test_css_change_only_rebuilds_frontend_and_runs_browser(self):
        plan = make_plan(['frontend/src/main/resources/public/style.css'])
        self.assertEqual(plan['services'], ['frontend'])
        for key in ['formatting', 'containers', 'browser']:
            self.assertTrue(plan['flags'][key], key)
        for key in ['javascript', 'static-analysis', 'codeql', 'api', 'cooperation']:
            self.assertFalse(plan['flags'][key], key)

    def test_javascript_change_selects_eslint(self):
        plan = make_plan(['frontend/src/main/resources/public/app.js'])
        self.assertTrue(plan['flags']['javascript'])
        self.assertFalse(plan['flags']['static-analysis'])

    def test_css_skips_unchanged_dependency_and_python_audits(self):
        plan = make_plan(['frontend/src/main/resources/public/style.css'])
        self.assertEqual(plan['security_scanners'], ['secret'])
        for flag in ['python-sast', 'python-dependencies', 'npm-dependencies']:
            self.assertFalse(plan['flags'][flag])

    def test_ci_python_dependencies_do_not_start_business_system(self):
        plan = make_plan(['scripts/requirements-ci.txt'])
        self.assertTrue(plan['flags']['python-dependencies'])
        self.assertFalse(plan['flags']['system'])
        self.assertEqual(plan['services'], [])

    def test_one_backend_selects_owner_cooperation_and_contracts(self):
        plan = make_plan(['users/src/main/java/com/aj/users/Application.java'])
        self.assertEqual(plan['services'], ['users'])
        for key in ['api', 'cooperation', 'contracts', 'static-analysis', 'codeql']:
            self.assertTrue(plan['flags'][key], key)
        self.assertFalse(plan['flags']['browser'])

    def test_shared_library_checks_all_services(self):
        plan = make_plan(['service-common/src/main/java/com/aj/common/Bus.java'])
        self.assertEqual(plan['services'], SERVICES)
        self.assertTrue(all(plan['flags'].values()))

    def test_observability_runs_runtime_acceptance_without_java_build_matrix(self):
        plan = make_plan(['observability/alloy.alloy'])
        self.assertEqual(plan['services'], [])
        self.assertTrue(plan['flags']['deployment-validation'])
        self.assertTrue(plan['flags']['system'])
        self.assertFalse(plan['flags']['containers'])

    def test_workflow_changes_get_full_verification(self):
        self.assertEqual(make_plan(['.github/workflows/ci.yaml'])['services'], SERVICES)

    def test_windows_paths_and_duplicates_are_normalized(self):
        self.assertEqual(make_plan(['users\\src\\main\\Application.java', './users/src/main/Application.java'])['services'], ['users'])

    def test_deleted_paths_still_select_their_owner(self):
        self.assertEqual(make_plan(['orders/src/main/java/Removed.java'])['services'], ['orders'])

    def test_course_exercise_does_not_trigger_application_ci(self):
        self.assertFalse(any(make_plan(['system-integration/assignment-1/rest/pom.xml'])['flags'].values()))

    def test_full_release_cannot_select_only_changed_services(self):
        self.assertEqual(make_plan(['frontend/README.md'], full=True)['services'], SERVICES)

    def test_missing_diff_base_fails_safe_to_full_plan(self):
        self.assertTrue(changed_paths({'before': '0' * 40}, 'push', 'a' * 40)[1])

    def test_unavailable_git_base_fails_safe(self):
        result = type('Result', (), {'returncode': 128})()
        with patch('ci_plan.subprocess.run', return_value=result):
            self.assertTrue(changed_paths({'before': 'b' * 40}, 'push', 'a' * 40)[1])

    def test_rename_diff_accounts_for_both_old_and_new_owner(self):
        result = type('Result', (), {'returncode': 0, 'stdout': b'users/old.java\0orders/new.java\0'})()
        with patch('ci_plan.subprocess.run', return_value=result) as command:
            paths, full = changed_paths({'before': 'b' * 40}, 'push', 'a' * 40)
        self.assertIn('--no-renames', command.call_args.args[0])
        self.assertEqual(make_plan(paths)['services'], ['users', 'orders'])
        self.assertFalse(full)


class GateTests(unittest.TestCase):
    def test_new_dependencies_require_explicit_gate_policy(self):
        self.assertTrue(evaluate({}, {'new-stage': {'result': 'success'}})[1])

    def test_planned_skips_are_accepted(self):
        self.assertFalse(evaluate({'tests': False}, {'tests': {'result': 'skipped'}})[1])

    def test_failed_required_stage_blocks(self):
        self.assertTrue(evaluate({'tests': True}, {'tests': {'result': 'failure'}})[1])

    def test_unexpected_skip_blocks(self):
        self.assertTrue(evaluate({'tests': True}, {'tests': {'result': 'skipped'}})[1])

    def test_missing_required_stage_blocks(self):
        self.assertTrue(evaluate({'tests': True}, {})[1])

    def test_cancelled_optional_stage_still_blocks(self):
        self.assertTrue(evaluate({'tests': False}, {'tests': {'result': 'cancelled'}})[1])

    def test_optional_failure_is_not_hidden(self):
        self.assertTrue(evaluate({'tests': False}, {'tests': {'result': 'failure'}})[1])

    def test_empty_documentation_only_runtime_tracks_can_pass(self):
        expected = {'plan': True, 'docs': True, 'tests': False}
        results = {'plan': {'result': 'success'}, 'docs': {'result': 'success'}, 'tests': {'result': 'skipped'}}
        self.assertFalse(evaluate(expected, results)[1])


class WorkflowTopologyTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        import yaml
        cls.workflows = {path.name: yaml.safe_load(path.read_text(encoding='utf-8')) for path in (ROOT / '.github/workflows').glob('*.yaml')}

    def test_build_scan_system_chain_is_explicit(self):
        jobs = self.workflows['ci.yaml']['jobs']
        self.assertIn('quality', jobs['services']['needs'])
        self.assertIn('services', jobs['containers']['needs'])
        self.assertIn('containers', jobs['system']['needs'])
        self.assertEqual(jobs['ready']['if'], 'always()')

    def test_stable_ci_has_no_path_trigger_that_leaves_required_check_pending(self):
        data = self.workflows['ci.yaml']
        triggers = data.get('on', data.get(True))
        self.assertIn('pull_request', triggers)
        self.assertIn('merge_group', triggers)
        self.assertIsNone(triggers['pull_request'])

    def test_publication_requires_release_gate(self):
        jobs = self.workflows['release.yaml']['jobs']
        self.assertEqual(jobs['images']['needs'], ['verify'])
        self.assertEqual(jobs['staging']['needs'], 'images')
        self.assertEqual(jobs['production']['needs'], 'staging')

    def test_package_waits_for_both_test_tracks(self):
        self.assertEqual(set(self.workflows['service-ci.yaml']['jobs']['package']['needs']), {'unit', 'integration'})

    def test_no_privileged_pr_target_workflow(self):
        for data in self.workflows.values():
            self.assertNotIn('pull_request_target', data.get('on', data.get(True, {})))

    def test_remote_actions_are_immutable_and_checkout_does_not_persist_credentials(self):
        import re
        for data in self.workflows.values():
            for job in data['jobs'].values():
                for step in job.get('steps', []):
                    action = step.get('uses', '')
                    if action and not action.startswith(('./', 'docker://')):
                        self.assertRegex(action, r'@[0-9a-f]{40}$')
                    if action.startswith('actions/checkout@'):
                        self.assertIs(step['with']['persist-credentials'], False)


if __name__ == '__main__':
    unittest.main()
