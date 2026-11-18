"""Run UI tests on the named disposable AVD, then prove process-restart persistence."""
import argparse
import json
import os
from pathlib import Path
import subprocess
import time

parser = argparse.ArgumentParser()
parser.add_argument('--root', required=True)
parser.add_argument('--reset-test-data', action='store_true')
args = parser.parse_args()
root = Path(args.root)
adb = str(root / '.tools' / 'android-sdk' / 'platform-tools' / 'adb.exe')
env = os.environ.copy()
env['ANDROID_USER_HOME'] = str(root / '.tools' / 'android-user')
out = root / 'artifacts' / 'android-runtime'
out.mkdir(parents=True, exist_ok=True)
package = 'app.dayflow.personal'

def run(*command, binary=False, timeout=90):
    r = subprocess.run([adb, '-s', 'emulator-5580', *command], capture_output=True, text=not binary, encoding=None if binary else 'utf-8', env=env, timeout=timeout)
    r.check_returncode()
    return r.stdout

name = run('emu', 'avd', 'name').splitlines()[0].strip()
if name != 'Dayflow_API35':
    raise SystemExit('Refusing to modify an unrecognized or real device: ' + name)
run('install', '-r', str(root / 'composeApp' / 'build' / 'outputs' / 'apk' / 'debug' / 'composeApp-debug.apk'))
run('install', '-r', str(root / 'composeApp' / 'build' / 'outputs' / 'apk' / 'androidTest' / 'debug' / 'composeApp-debug-androidTest.apk'))
if args.reset_test_data:
    run('shell', 'pm', 'clear', package)
log = run('shell', 'am', 'instrument', '-w', '-r', '-e', 'class', 'app.dayflow.AndroidTaskTest', package + '.test/androidx.test.runner.AndroidJUnitRunner', timeout=180)
(out / 'instrumentation.log').write_text(log, encoding='utf-8')
if 'OK (1 test)' not in log or 'FAILURES' in log:
    raise SystemExit(log)
before = json.loads(run('exec-out', 'run-as', package, 'cat', 'files/dayflow.json'))
run('shell', 'am', 'force-stop', package)
run('shell', 'am', 'start', '-n', package + '/app.dayflow.MainActivity')
time.sleep(2)
after = json.loads(run('exec-out', 'run-as', package, 'cat', 'files/dayflow.json'))
assert before == after, 'Process restart changed persisted data'
task = next(t for t in after['tasks'] if t['title'] == '에뮬레이터 한글 저장 검증')
assert task['notes'] == '회전과 재실행 뒤에도 남는 설명'
assert task['priority'] == 3 and task.get('completedAt') is None and task.get('deletedAt') is None
for _ in range(15):
    run('shell', 'uiautomator', 'dump', '/sdcard/dayflow-ui.xml')
    xml = run('exec-out', 'cat', '/sdcard/dayflow-ui.xml')
    if task['title'] in xml:
        (out / 'ui-after-restart.xml').write_text(xml, encoding='utf-8')
        break
    time.sleep(2)
else:
    raise SystemExit('Persisted data exists but the restarted UI did not display it')
(out / 'tasks-phone.png').write_bytes(run('exec-out', 'screencap', '-p', binary=True))
result = {'device': 'Dayflow_API35 / emulator-5580', 'uiTests': 1, 'uiStatus': 'passed', 'processRestartPersistence': 'passed', 'referenceComparison': 'pending', 'realDevice': 'not-run'}
(out / 'result.json').write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding='utf-8')
print(json.dumps(result, ensure_ascii=False))
