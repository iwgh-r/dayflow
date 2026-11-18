"""Collect built artifacts without starting any desktop app or browser."""
import hashlib
import json
from pathlib import Path
import shutil
import zipfile

root = Path(__file__).resolve().parents[1]
out = root / 'artifacts'
android = out / 'android'
windows = out / 'windows'
android.mkdir(parents=True, exist_ok=True)
windows.mkdir(parents=True, exist_ok=True)
apk = root / 'composeApp/build/outputs/apk/debug/composeApp-debug.apk'
target_apk = android / 'Dayflow-0.1.0-debug.apk'
shutil.copy2(apk, target_apk)
app = root / 'composeApp/build/compose/binaries/main/app/Dayflow'
if not (app / 'Dayflow.exe').is_file() or not (app / 'runtime/release').is_file():
    raise SystemExit('A complete Windows bundle must be built before packaging.')
archive = windows / 'Dayflow-0.1.0-portable.zip'
with zipfile.ZipFile(archive, 'w', compression=zipfile.ZIP_DEFLATED, compresslevel=1) as z:
    for file in sorted(app.rglob('*')):
        if file.is_file():
            z.write(file, str(file.relative_to(app.parent)))
with zipfile.ZipFile(archive) as z:
    assert z.testzip() is None, 'Windows archive CRC check failed'
    assert 'Dayflow/Dayflow.exe' in z.namelist()
manifest = []
for file in [target_apk, archive]:
    with file.open('rb') as stream:
        digest = hashlib.file_digest(stream, 'sha256').hexdigest()
    manifest.append({'file': file.relative_to(out).as_posix(), 'bytes': file.stat().st_size, 'sha256': digest})
(out / 'packages.json').write_text(json.dumps(manifest, indent=2), encoding='utf-8')
print(json.dumps(manifest, indent=2))
