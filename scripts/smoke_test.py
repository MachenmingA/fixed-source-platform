#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Read-only smoke test for the Dockerized fixed-source prototype."""
import json
import sys
import urllib.error
import urllib.request

BASE = 'http://localhost:8088/api'
OPEN_BASE = 'http://localhost:8088/open-data/v1'


def call(method, path, token=None, body=None):
    data = json.dumps(body).encode() if body is not None else None
    request = urllib.request.Request(BASE + path, data=data, method=method)
    request.add_header('Content-Type', 'application/json')
    if token:
        request.add_header('X-Token', token)
    try:
        with urllib.request.urlopen(request, timeout=20) as response:
            raw = response.read().decode()
            return response.status, json.loads(raw) if raw else None
    except urllib.error.HTTPError as error:
        return error.code, error.read().decode()


def login(username):
    status, data = call('POST', '/auth/login', body={'username': username, 'password': '123456'})
    assert status == 200, f'login {username} failed: {status} {data}'
    return data['token']


def main():
    checks = []
    status, health = call('GET', '/health')
    checks.append(('health', status == 200 and health.get('status') == 'UP'))

    analyst = login('analyst')
    reviewer = login('reviewer')
    inspector = login('inspector')

    status, dashboard = call('GET', '/dashboard/overview', analyst)
    checks.append(('dashboard', status == 200 and dashboard.get('enterpriseCount', 0) >= 10))
    checks.append(('risk distribution', len(dashboard.get('riskDistribution', [])) > 0))

    status, enterprises = call('GET', '/enterprises', analyst)
    checks.append(('enterprise list', status == 200 and len(enterprises) >= 10))

    status, profile = call('GET', '/enterprises/E007/profile', analyst)
    checks.append(('enterprise profile', status == 200 and profile.get('enterprise', {}).get('id') == 'E007'))

    status, analysis = call('GET', '/monitoring/analysis?enterpriseId=E007&days=7', analyst)
    checks.append(('monitoring hourly/daily charts',
                   status == 200
                   and len(analysis.get('daily', [])) == 7
                   and len(analysis.get('hourly', [])) == 24
                   and len(analysis.get('heatmap', [])) >= 24
                   and analysis.get('stats', {}).get('expectedSamples') == 168))

    status, events = call('GET', '/events', analyst)
    checks.append(('event list', status == 200 and len(events) > 0))
    if events:
        status, detail = call('GET', f"/events/{events[0]['id']}", analyst)
        ai_count = sum(1 for clue in detail.get('clues', []) if clue.get('ai'))
        checks.append(('event detail + AI', status == 200 and ai_count > 0))

    status, tasks = call('GET', '/inspection-tasks', reviewer)
    checks.append(('task list', status == 200 and len(tasks) > 0))

    status, mobile_tasks = call('GET', '/mobile/tasks', inspector)
    checks.append(('mobile task list', status == 200 and isinstance(mobile_tasks, list)))

    status, data_hub = call('GET', '/data-hub/overview', reviewer)
    checks.append(('national data hub overview',
                   status == 200 and len(data_hub.get('catalogs', [])) >= 5
                   and len(data_hub.get('subscribers', [])) >= 5))

    def open_catalog(key):
        request = urllib.request.Request(OPEN_BASE + '/catalog')
        if key:
            request.add_header('X-Open-Data-Key', key)
        try:
            with urllib.request.urlopen(request, timeout=20) as response:
                return response.status, json.loads(response.read().decode())
        except urllib.error.HTTPError as error:
            return error.code, None

    open_status, open_data = open_catalog('national-open-demo-key')
    bad_status, _ = open_catalog('wrong-key')
    checks.append(('open data catalog + key check',
                   open_status == 200 and len(open_data.get('catalogs', [])) >= 5
                   and bad_status == 401))

    print('Fixed-source Docker smoke test')
    print('=' * 42)
    failed = 0
    for name, ok in checks:
        print(f"{'PASS' if ok else 'FAIL'}  {name}")
        if not ok:
            failed += 1
    print('=' * 42)
    print(f'{len(checks) - failed}/{len(checks)} checks passed')
    return 1 if failed else 0


if __name__ == '__main__':
    sys.exit(main())
