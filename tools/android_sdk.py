"""Portable Android SDK setup. Run --accept-license only with user's consent."""
import argparse
from pathlib import Path
import subprocess
import os
import zipfile
from bootstrap import ROOT, TOOLS, download

parser = argparse.ArgumentParser()
parser.add_argument('--accept-license', action='store_true')
args = parser.parse_args()
if not args.accept_license:
    raise SystemExit('Explicit SDK license consent is required.')

sdk = TOOLS / 'android-sdk'
manager = sdk / 'cmdline-tools' / 'latest' / 'bin' / 'sdkmanager.bat'
if not manager.exists():
    archive = TOOLS / 'commandlinetools.zip'
    download('https://dl.google.com/android/repository/commandlinetools-win-15859902_latest.zip', archive, '90ae805d20434428bffcb699c290860f19bb5f66a67e6b330067e3de801fb04a')
    stage = TOOLS / 'sdk-stage'
    with zipfile.ZipFile(archive) as z:
        z.extractall(stage)
    manager.parent.parent.parent.mkdir(parents=True, exist_ok=True)
    (stage / 'cmdline-tools').rename(sdk / 'cmdline-tools' / 'latest')
env = os.environ.copy()
env['JAVA_HOME'] = str(TOOLS / 'jdk')
env['ANDROID_USER_HOME'] = str(TOOLS / 'android-user')
subprocess.run([str(manager), '--sdk_root=' + str(sdk), '--licenses'], input='y\n'*100, text=True, env=env, check=True)
subprocess.run([str(manager), '--sdk_root=' + str(sdk), 'platform-tools', 'platforms;android-35', 'build-tools;35.0.0'], env=env, check=True)
(ROOT / 'local.properties').write_text('sdk.dir=' + sdk.as_posix() + '\n', encoding='utf-8')
print('Android SDK ready')
