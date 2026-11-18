"""Install portable build tools inside this checkout; never change system settings."""
import concurrent.futures
import hashlib
import json
from pathlib import Path
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parents[1]
TOOLS = ROOT / '.tools'
TOOLS.mkdir(exist_ok=True)

def get(url):
    with urllib.request.urlopen(url, timeout=90) as response:
        return response.read()

def download(url, path, expected=None):
    if not path.exists():
        print('Downloading ' + path.name, flush=True)
        with urllib.request.urlopen(url, timeout=120) as response, path.open('wb') as out:
            while chunk := response.read(1024 * 1024):
                out.write(chunk)
    if expected and hashlib.file_digest(path.open('rb'), 'sha256').hexdigest() != expected.lower().strip():
        raise RuntimeError('Checksum mismatch: ' + str(path))

def jdk():
    if (TOOLS / 'jdk' / 'bin' / 'java.exe').exists():
        return
    release = json.loads(get('https://api.github.com/repos/adoptium/temurin17-binaries/releases/latest'))
    package = next(a for a in release['assets'] if a['name'].startswith('OpenJDK17U-jdk_x64_windows_hotspot_') and a['name'].endswith('.zip'))
    info = {'link': package['browser_download_url'], 'checksum': get(package['browser_download_url'] + '.sha256.txt').decode().split()[0]}
    archive = TOOLS / 'jdk.zip'
    download(info['link'], archive, info['checksum'])
    with zipfile.ZipFile(archive) as z:
        top = z.namelist()[0].split('/')[0]
        z.extractall(TOOLS)
    (TOOLS / top).rename(TOOLS / 'jdk')
    print('JDK ready', flush=True)

def gradle():
    if (TOOLS / 'gradle-8.11.1' / 'bin' / 'gradle.bat').exists():
        return
    url = 'https://services.gradle.org/distributions/gradle-8.11.1-bin.zip'
    checksum = get(url + '.sha256').decode().strip()
    archive = TOOLS / 'gradle.zip'
    download(url, archive, checksum)
    with zipfile.ZipFile(archive) as z:
        z.extractall(TOOLS)
    print('Gradle ready', flush=True)

if __name__ == '__main__':
    with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
        list(pool.map(lambda f: f(), [jdk, gradle]))
    print('Portable tools ready. No Android licenses accepted.', flush=True)
