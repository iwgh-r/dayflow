"""Create an isolated test AVD and start it headlessly, with bounded readiness checks."""
import argparse
import os
from pathlib import Path
import subprocess
import time

p = argparse.ArgumentParser()
p.add_argument('action', choices=['start', 'status', 'stop'])
p.add_argument('--root', required=True)
args = p.parse_args()
root = Path(args.root)
sdk = root / '.tools' / 'android-sdk'
env = os.environ.copy()
env.update(JAVA_HOME=str(root / '.tools' / 'jdk'), ANDROID_HOME=str(sdk), ANDROID_USER_HOME=str(root / '.tools' / 'android-user'), ANDROID_AVD_HOME=str(root / '.tools' / 'avd'))
avds = Path(env['ANDROID_AVD_HOME'])
avds.mkdir(parents=True, exist_ok=True)
adb = str(sdk / 'platform-tools' / 'adb.exe')
serial = 'emulator-5580'

def run(command, **kwargs):
    return subprocess.run(command, env=env, capture_output=True, text=True, timeout=kwargs.pop('timeout', 60), **kwargs)

if args.action == 'stop':
    r = run([adb, '-s', serial, 'emu', 'kill'])
    print(r.stdout, r.stderr)
elif args.action == 'status':
    print(run([adb, 'devices']).stdout)
    print(run([adb, '-s', serial, 'shell', 'getprop', 'sys.boot_completed']).stdout)
else:
    if not (avds / 'Dayflow_API35.ini').exists():
        result = run([str(sdk / 'cmdline-tools' / 'latest' / 'bin' / 'avdmanager.bat'), 'create', 'avd', '--name', 'Dayflow_API35', '--package', 'system-images;android-35;default;x86_64', '--path', str(avds / 'Dayflow_API35.avd'), '--device', 'pixel_5'], input='no\n', timeout=120)
        print(result.stdout[-2000:], result.stderr[-1000:], flush=True)
        result.check_returncode()
    config = avds / 'Dayflow_API35.avd' / 'config.ini'
    s = config.read_text()
    if 'hw.keyboard=yes' not in s:
        s += '\nhw.keyboard=yes\n'
        config.write_text(s)
    log = root / 'artifacts' / 'emulator-runtime.log'
    log.parent.mkdir(exist_ok=True)
    if serial not in run([adb, 'devices']).stdout:
        with log.open('w') as stream:
            process = subprocess.Popen([str(sdk / 'emulator' / 'emulator.exe'), '-avd', 'Dayflow_API35', '-port', '5580', '-no-window', '-no-audio', '-no-snapshot', '-gpu', 'swiftshader', '-no-boot-anim', '-memory', '2048', '-cores', '2'], env=env, stdout=stream, stderr=subprocess.STDOUT, creationflags=subprocess.CREATE_NO_WINDOW)
            print('Emulator PID', process.pid, flush=True)
    for _ in range(120):
        if run([adb, '-s', serial, 'shell', 'getprop', 'sys.boot_completed'], timeout=10).stdout.strip() == '1':
            run([adb, '-s', serial, 'shell', 'input', 'keyevent', '82'])
            print('Emulator booted:', serial, flush=True)
            break
        time.sleep(2)
    else:
        raise SystemExit('Emulator did not boot. See ' + str(log))
