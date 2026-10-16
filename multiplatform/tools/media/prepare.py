"""Fetch pinned, checksum-verified upstream media helpers for the current host.
Run before packaging. No global installation; binaries stay in desktopApp/build.
"""
import gzip, hashlib, io, json, os, platform, shutil, urllib.request, zipfile
from pathlib import Path
root = Path(__file__).resolve().parents[2]
versions = json.loads((Path(__file__).parent / 'versions.json').read_text())
system, arch = platform.system(), platform.machine().lower()
arm = arch in ('arm64', 'aarch64')
if system == 'Darwin':
    extractor, runtime = 'yt-dlp_macos', f'deno-{"aarch64" if arm else "x86_64"}-apple-darwin.zip'
elif system == 'Windows':
    if arm: raise SystemExit('Use an x64 Windows builder for the current JavaFX distribution.')
    extractor, runtime = 'yt-dlp.exe', 'deno-x86_64-pc-windows-msvc.zip'
else:
    extractor, runtime = ('yt-dlp_linux_aarch64' if arm else 'yt-dlp_linux'), f'deno-{"aarch64" if arm else "x86_64"}-unknown-linux-gnu.zip'
ffplatform = ('darwin' if system == 'Darwin' else 'win32' if system == 'Windows' else 'linux') + ('-arm64' if arm else '-x64')
out = root / 'desktopApp/build/media-resources/common/media-tools'
cache = root / 'desktopApp/build/media-cache'
out.mkdir(parents=True, exist_ok=True); cache.mkdir(parents=True, exist_ok=True)
def download(url):
    for attempt in range(3):
        try:
            with urllib.request.urlopen(urllib.request.Request(url, headers={'User-Agent': 'Fetch-build'}), timeout=45) as response:
                return response.read()
        except OSError:
            if attempt == 2: raise
            print('Retrying interrupted download', flush=True)
for project, asset, name in [('yt-dlp', extractor, 'yt-dlp'), ('deno', runtime, 'deno'), ('ffmpeg', f'ffmpeg-{ffplatform}.gz', 'ffmpeg')]:
    entry = versions[project]['assets'][asset]
    archive = cache / asset
    if not archive.exists() or hashlib.sha256(archive.read_bytes()).hexdigest() != entry['sha256']:
        print('Downloading', asset, flush=True)
        data = download(entry['url'])
        if hashlib.sha256(data).hexdigest() != entry['sha256']: raise SystemExit('Checksum mismatch: ' + asset)
        archive.write_bytes(data)
    dest = out / (name + ('.exe' if system == 'Windows' else ''))
    if asset.endswith('.zip'):
        with zipfile.ZipFile(archive) as z:
            dest.write_bytes(z.read(dest.name))
    elif asset.endswith('.gz'): dest.write_bytes(gzip.decompress(archive.read_bytes()))
    else: shutil.copyfile(archive, dest)
    dest.chmod(0o755)
    print('Prepared', project, versions[project]['version'], flush=True)
for suffix in ['LICENSE', 'README']:
    entry = versions['ffmpeg']['assets'][f'{ffplatform}.{suffix}']
    dest = out / f'ffmpeg-{suffix}.txt'
    if not dest.exists() or hashlib.sha256(dest.read_bytes()).hexdigest() != entry['sha256']:
        data = download(entry['url'])
        if hashlib.sha256(data).hexdigest() != entry['sha256']: raise SystemExit('Checksum mismatch for FFmpeg notice')
        dest.write_bytes(data)
licenses = {
 'yt-dlp-LICENSE.txt': f"https://raw.githubusercontent.com/yt-dlp/yt-dlp/{versions['yt-dlp']['version']}/LICENSE",
 'yt-dlp-THIRD-PARTY.txt': f"https://raw.githubusercontent.com/yt-dlp/yt-dlp/{versions['yt-dlp']['version']}/THIRD_PARTY_LICENSES.txt",
 'ffmpeg-COPYING.GPLv3.txt': 'https://raw.githubusercontent.com/FFmpeg/FFmpeg/n6.1.1/COPYING.GPLv3',
 'ffmpeg-COPYING.LGPLv2.1.txt': 'https://raw.githubusercontent.com/FFmpeg/FFmpeg/n6.1.1/COPYING.LGPLv2.1',
 'deno-LICENSE.txt': f"https://raw.githubusercontent.com/denoland/deno/{versions['deno']['version']}/LICENSE.md",
}
for name, url in licenses.items():
    dest = out / name
    if not dest.exists(): dest.write_bytes(download(url))
projects = {'yt-dlp': 'yt-dlp/yt-dlp', 'deno': 'denoland/deno', 'ffmpeg': 'eugeneware/ffmpeg-static'}
(out / 'SOURCES.txt').write_text('Separately invoked, unmodified upstream command-line tools.\n' + '\n'.join(f"{p} {v['version']}: https://github.com/{projects[p]}/tree/{v['version']}" for p,v in versions.items()) + '\nSource archives and build instructions are available at the URLs above. For FFmpeg, see the accompanying build README for exact source references.\n')
print(out)
