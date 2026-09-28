"""Fail closed on missing, skipped, empty or failed backend test reports."""
from pathlib import Path
import sys
import xml.etree.ElementTree as ET

# Floors prevent accidental removal/filtering of existing tests. Raise when adding tests.
REQUIRED = {
    'com.acme.admin.FullStackTest': 13,
    'com.acme.admin.MySqlTest': 17,
    'com.acme.admin.RedisCacheTest': 4,
    'com.acme.admin.DataScopeRulesTest': 4,
    'com.acme.admin.MigrationUpgradeTest': 1,
    'com.acme.admin.generator.CrudGeneratorTest': 1,
}


def check(backend):
    seen = {}
    total = 0
    for path in sorted(backend.glob('*/target/surefire-reports/TEST-*.xml')):
        suite = ET.parse(path).getroot()
        name = suite.attrib['name']
        if name in seen:
            raise ValueError(f'Duplicate suite: {name}')
        cases = suite.findall('testcase')
        count = int(suite.attrib['tests'])
        if count == 0 or count != len(cases):
            raise ValueError(f'Empty or inconsistent suite: {name}')
        if any(int(suite.attrib.get(key, '0')) for key in ('failures', 'errors', 'skipped')):
            raise ValueError(f'Failed or skipped tests: {name}')
        if any(case.find(tag) is not None for case in cases for tag in ('failure', 'error', 'skipped')):
            raise ValueError(f'Failed or skipped testcase: {name}')
        seen[name] = count
        total += count
    for name, minimum in REQUIRED.items():
        if seen.get(name, 0) < minimum:
            raise ValueError(f'Missing/incomplete required suite: {name} (need >= {minimum}, got {seen.get(name, 0)})')
    return total


if __name__ == '__main__':
    try:
        total = check(Path(__file__).resolve().parents[1] / 'backend')
    except (ValueError, KeyError, OSError, ET.ParseError) as error:
        sys.exit(f'Test gate failed: {error}')
    print(f'Test gate passed: {total} tests, no failures/errors/skips; all required suites present.')
