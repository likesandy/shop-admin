import importlib.util
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest
import xml.etree.ElementTree as ET

SCRIPTS = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('reports', SCRIPTS / 'check-test-reports.py')
reports = importlib.util.module_from_spec(spec)
spec.loader.exec_module(reports)


class ReportGateTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.backend = Path(self.temp.name) / 'backend'
        self.directory = self.backend / 'example/target/surefire-reports'
        self.directory.mkdir(parents=True)
        for name, count in reports.REQUIRED.items():
            suite = ET.Element('testsuite', name=name, tests=str(count), failures='0', errors='0', skipped='0')
            for i in range(count):
                ET.SubElement(suite, 'testcase', name=f'test{i}', classname=name)
            ET.ElementTree(suite).write(self.directory / f'TEST-{name}.xml')

    def test_accepts_complete_reports(self):
        self.assertEqual(sum(reports.REQUIRED.values()), reports.check(self.backend))

    def test_rejects_missing_mysql_suite(self):
        (self.directory / 'TEST-com.acme.admin.MySqlTest.xml').unlink()
        with self.assertRaisesRegex(ValueError, 'Missing/incomplete'):
            reports.check(self.backend)

    def test_rejects_skipped_redis_test_even_with_green_summary(self):
        path = self.directory / 'TEST-com.acme.admin.RedisCacheTest.xml'
        tree = ET.parse(path)
        ET.SubElement(tree.getroot().find('testcase'), 'skipped')
        tree.write(path)
        with self.assertRaisesRegex(ValueError, 'skipped'):
            reports.check(self.backend)

    def test_rejects_failed_migration(self):
        path = self.directory / 'TEST-com.acme.admin.MigrationUpgradeTest.xml'
        tree = ET.parse(path)
        tree.getroot().set('failures', '1')
        tree.write(path)
        with self.assertRaisesRegex(ValueError, 'Failed'):
            reports.check(self.backend)

    def test_rejects_filtered_test_methods(self):
        path = self.directory / 'TEST-com.acme.admin.MySqlTest.xml'
        tree = ET.parse(path)
        tree.getroot().remove(tree.getroot().find('testcase'))
        tree.getroot().set('tests', str(reports.REQUIRED['com.acme.admin.MySqlTest'] - 1))
        tree.write(path)
        with self.assertRaisesRegex(ValueError, 'Missing/incomplete'):
            reports.check(self.backend)

    def test_coverage_script_rejects_zero_coverage(self):
        # Execute the real checker against isolated synthetic data, never edit actual reports.
        root = Path(self.temp.name)
        (root / 'scripts').mkdir()
        shutil.copy(SCRIPTS / 'check-coverage.py', root / 'scripts/check-coverage.py')
        report = self.backend / 'admin-starter/target/site/jacoco-aggregate/jacoco.xml'
        report.parent.mkdir(parents=True)
        report.write_text('<report><package><class name="com/acme/admin/auth/AuthService">'
                          '<counter type="LINE" missed="10" covered="0"/></class></package></report>')
        result = subprocess.run(['python3', str(root / 'scripts/check-coverage.py')], capture_output=True, text=True)
        self.assertNotEqual(0, result.returncode)
        self.assertIn('Coverage gate failed', result.stderr)


if __name__ == '__main__':
    unittest.main()
