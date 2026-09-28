#!/usr/bin/env python3
"""Destructive fault tests ONLY in a new, disposable Compose project; no existing DB is used."""
import json
import os
from pathlib import Path
import secrets
import select
import socket
import subprocess
import time
import urllib.error
import urllib.request

ROOT = Path(__file__).resolve().parents[1]


def free_port():
    with socket.socket() as sock:
        sock.bind(('127.0.0.1', 0))
        return sock.getsockname()[1]


def eventually(check, description, timeout=90):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        if check():
            print('PASS ' + description, flush=True)
            return
        time.sleep(1)
    raise AssertionError('Timed out: ' + description)


def main():
    project = 'shop-admin-recovery-' + secrets.token_hex(4)
    env = os.environ.copy()
    # Never reuse a caller's database, credentials, project, volumes or image tag.
    env.update({key: secrets.token_hex(24) for key in (
        'DB_PASSWORD', 'MYSQL_ROOT_PASSWORD', 'REDIS_PASSWORD', 'JWT_SECRET',
        'ADMIN_PASSWORD', 'METRICS_PASSWORD')})
    env.update(COMPOSE_PROJECT_NAME=project, APP_VERSION=project,
               HTTP_PORT=str(free_port()), PROMETHEUS_PORT=str(free_port()))
    compose = ['docker', 'compose', '-p', project, '-f', 'compose.yml', '-f', 'compose.monitoring.yml']

    def run(*args, **kwargs):
        return subprocess.run(compose + list(args), cwd=ROOT, env=env, check=True,
                              text=True, timeout=kwargs.pop('timeout', 300), **kwargs)

    mysql = ['exec', '-T', 'mysql', 'sh', '-c',
             'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot --default-character-set=utf8mb4 --batch --skip-column-names --unbuffered shop_admin']

    def sql(statement):
        return run(*mysql, input=statement + ';\n', capture_output=True).stdout.strip()

    def container_id(service):
        value = run('ps', '-aq', service, capture_output=True).stdout.strip()
        assert value and '\n' not in value, 'Expected exactly one test container'
        return value

    def start_only(service):
        # Compose start also starts dependencies; fault tests must control Redis separately.
        subprocess.run(['docker', 'start', container_id(service)], env=env,
                       check=True, capture_output=True, text=True, timeout=30)

    def http(port, path, method='GET', body=None, token=None):
        headers = {'Content-Type': 'application/json'}
        if token:
            headers['Authorization'] = 'Bearer ' + token
        request = urllib.request.Request('http://127.0.0.1:' + port + path,
            data=json.dumps(body).encode() if body is not None else None,
            headers=headers, method=method)
        with urllib.request.urlopen(request, timeout=20) as response:
            return json.load(response)

    token = None

    def api(path, method='GET', body=None):
        return http(env['HTTP_PORT'], '/api' + path, method, body, token)['data']

    def metric(expression):
        from urllib.parse import urlencode
        try:
            data = http(env['PROMETHEUS_PORT'], '/api/v1/query?' + urlencode({'query': expression}))
            return data['data']['result']
        except (urllib.error.URLError, TimeoutError):
            return []

    def scrape_up():
        rows = metric('up{job="shop-admin"}')
        return len(rows) == 1 and rows[0]['value'][1] == '1'

    def alert_firing():
        return bool(metric('ALERTS{alertname="ShopAdminBackendDown",alertstate="firing"}'))

    def backend_ready():
        try:
            return api('/auth/me')['username'] == 'admin'
        except (urllib.error.URLError, TimeoutError):
            return False

    def diagnose_recovery():
        # Print only routing/startup evidence, never inspect container environments.
        for service in ('backend', 'frontend'):
            output = run('logs', '--no-color', '--tail', '100', service, capture_output=True).stdout
            for line in output.splitlines():
                if any(marker in line for marker in ('connect() failed', 'Started ', 'Tomcat started', 'Application run failed')):
                    print(line, flush=True)
            result = subprocess.run(['docker', 'inspect', '--format',
                '{{.State.Status}} {{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}', container_id(service)],
                env=env, check=True, capture_output=True, text=True, timeout=15)
            print(service + ': ' + result.stdout.strip(), flush=True)

    lock = None
    print('Isolated project: ' + project, flush=True)
    try:
        run('build', '--pull', timeout=900)
        run('up', '-d', '--wait', '--wait-timeout', '240')
        run('exec', '-T', 'prometheus', 'promtool', 'check', 'config', '/etc/prometheus/prometheus.yml')
        token = api('/auth/login', 'POST', {'username': 'admin', 'password': env['ADMIN_PASSWORD']})['token']
        eventually(scrape_up, 'Prometheus scrapes the authenticated backend')
        assert metric('jvm_memory_used_bytes{job="shop-admin"}'), 'JVM metrics missing'

        # Hold the destination table so a real HTTP audit remains durably pending.
        lock = subprocess.Popen(compose + mysql, cwd=ROOT, env=env, text=True,
                                stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        lock.stdin.write("LOCK TABLES op_log WRITE; SELECT 'LOCKED';\n")
        lock.stdin.flush()
        assert select.select([lock.stdout], [], [], 30)[0], 'Audit table lock timed out'
        assert lock.stdout.readline().strip() == 'LOCKED', 'Audit table lock failed'
        body = {'type': 'recovery_check', 'label': 'before', 'value': 'one', 'sort': 0, 'enabled': True}
        api('/dictionaries', 'POST', body)
        eventually(lambda: sql("select count(*) from audit_outbox where action='新增字典'") == '1',
                   'Real HTTP audit is pending before SIGKILL')
        # Observe the blocked transfer, not just an arbitrary idle process kill.
        eventually(lambda: int(sql("select count(*) from information_schema.processlist "
                   "where state='Waiting for table metadata lock' and info like 'insert into op_log%'") or 0) > 0,
                   'Audit drain is blocked while inserting the log')
        run('kill', '-s', 'SIGKILL', 'backend')
        assert sql("select count(*) from audit_outbox where action='新增字典'") == '1'
        lock.stdin.write('UNLOCK TABLES;\n')
        lock.stdin.close()
        lock.wait(timeout=15)
        lock = None
        assert sql("select count(*) from op_log where action='新增字典'") == '0'
        start_only('backend')
        eventually(backend_ready, 'Backend serves persisted session after audit crash')
        eventually(lambda: sql("select count(*) from audit_outbox where action='新增字典'") == '0'
                   and sql("select count(*) from op_log where action='新增字典'") == '1',
                   'Pending audit recovered exactly once')
        run('restart', 'backend')
        eventually(backend_ready, 'Second restart completes')
        assert sql("select count(*) from op_log where action='新增字典'") == '1'
        print('PASS Audit remains single after a second restart', flush=True)

        # Cache a known old value, stop Redis, then commit new DB data via HTTP.
        assert api('/dictionaries/type/recovery_check')[0]['label'] == 'before'
        dictionary = next(x for x in api('/dictionaries') if x['type'] == 'recovery_check')
        run('stop', 'redis')
        body['label'] = 'after'
        api('/dictionaries/' + str(dictionary['id']), 'PUT', body)
        assert sql("select label from sys_dict where type='recovery_check'") == 'after'
        assert int(sql("select count(*) from cache_invalidation where dict_type='recovery_check'")) > 0
        run('kill', '-s', 'SIGKILL', 'backend')
        start_only('backend')
        try:
            eventually(backend_ready, 'Backend restarts while Redis is unavailable')
        except AssertionError:
            diagnose_recovery()
            raise
        redis_state = subprocess.run(['docker', 'inspect', '--format', '{{.State.Status}}', container_id('redis')],
                                     env=env, check=True, capture_output=True, text=True, timeout=15).stdout.strip()
        assert redis_state == 'exited', 'Redis must stay stopped throughout the backend restart'
        assert int(sql("select count(*) from cache_invalidation where dict_type='recovery_check'")) > 0
        start_only('redis')
        eventually(lambda: sql("select count(*) from cache_invalidation where dict_type='recovery_check'") == '0',
                   'Persisted cache invalidation resumes after Redis recovery')
        assert api('/dictionaries/type/recovery_check')[0]['label'] == 'after'
        cached = run('exec', '-T', 'redis', 'sh', '-c',
            'REDISCLI_AUTH="$REDIS_PASSWORD" redis-cli --raw GET dict:type:recovery_check',
            capture_output=True).stdout
        assert json.loads(cached)[0]['label'] == 'after'
        print('PASS Recovered cache contains the committed value', flush=True)

        # Stop long enough to exercise the real 30-second alert, then verify resolution.
        eventually(scrape_up, 'Scrape healthy before alert exercise')
        run('stop', 'backend')
        eventually(alert_firing, 'Backend-down alert is FIRING', timeout=90)
        start_only('backend')
        eventually(backend_ready, 'Backend recovers after alert exercise')
        eventually(lambda: scrape_up() and not alert_firing(), 'Backend-down alert resolves')
        assert sql("select count(*) from op_log where action='新增字典'") == '1'
        print('PASS ALL: audit crash recovery, cache compensation, metrics and alert lifecycle', flush=True)
    finally:
        if lock is not None:
            lock.stdin.close()
            try:
                lock.wait(timeout=15)
            except subprocess.TimeoutExpired:
                lock.kill()
        # This script owns the randomly named project and all its disposable volumes.
        run('down', '-v', '--remove-orphans')
        print('Cleaned up disposable project: ' + project, flush=True)


if __name__ == '__main__':
    main()
