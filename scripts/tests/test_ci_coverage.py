"""Exercise report math, missing counters and absence of coverage data."""
from pathlib import Path
import sys
import tempfile
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from ci_coverage import coverage_rows


class CoverageTests(unittest.TestCase):
    def test_line_branch_totals_and_no_branch_data(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            target = root / 'users/target/site/jacoco/jacoco.xml'
            target.parent.mkdir(parents=True)
            target.write_text('<report><counter type="LINE" missed="3" covered="7"/></report>')
            self.assertEqual(coverage_rows(root), [('users', '70.0% (7/10)', 'n/a')])

    def test_missing_report_does_not_invent_coverage(self):
        with tempfile.TemporaryDirectory() as directory:
            self.assertEqual(coverage_rows(Path(directory)), [])


if __name__ == '__main__':
    unittest.main()
