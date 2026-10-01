#!/usr/bin/env python3
"""Put a built release on willowcreek.group/kaizocore (2026-09-29).

    python tools/site_bump.py              # the version in app/build.gradle.kts, APK from dist/
    python tools/site_bump.py --dry-run    # say what would change, change nothing

One release touches five things on the site, and they used to be edited by hand from a one-off script each time:
the page's two download links (with their ?v= stamp), its size and SHA-256 lines, the APK itself, a 301 for the
build it replaces, and kaizocore/latest.json, the file the app reads once a day to learn the newest build. If
latest.json lags the page, every phone keeps being offered the old build; if it leads the page, phones are sent to
an APK that is not there. So all five move here, together.

It edits the site's SOURCE (Saturday/ in the WCG repo) and nothing else, and deploys nothing. After it, build the
site (bash "PX PUSH WCG/build-deploy.sh", then python redesign-prototype/.release/build_release.py): the release
build's kaizocore-latest gate fails if the page, the APK and latest.json disagree. Deploying is Blake's go.
"""
import argparse
import datetime
import hashlib
import json
import os
import re
import shutil
import sys

ROOT = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), '..'))
SITE_DEFAULT = 'C:/Users/bepor/willowcreek-v2-deploy.zip/Saturday'
ORIGIN = 'https://willowcreek.group'
RELEASES = 'https://github.com/blakeportable7-del/KaizoCore/releases/tag/v'
APK_LINK = re.compile(r'kaizocore/KaizoCore-([0-9A-Za-z.+-]+)\.apk\?v=([0-9a-f]{8})')


def fail(msg):
    print('site_bump: ' + msg)
    sys.exit(1)


def replace_once(text, old, new, what):
    n = text.count(old)
    if n != 1:
        fail('%s: expected exactly one %r on the page, found %d' % (what, old, n))
    return text.replace(old, new)


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument('--site', default=SITE_DEFAULT, help='the site source folder (Saturday/)')
    ap.add_argument('--dry-run', action='store_true', help='print the plan, write nothing')
    a = ap.parse_args()

    gradle = open(os.path.join(ROOT, 'app', 'build.gradle.kts'), encoding='utf-8').read()
    code = int(re.search(r'versionCode = (\d+)', gradle).group(1))
    version = re.search(r'baseVersion = "([^"]+)"', gradle).group(1)
    apk = os.path.join(ROOT, 'dist', 'KaizoCore-%s.apk' % version)
    if not os.path.isfile(apk):
        fail('%s is not built: run tools/release.sh first' % apk)
    data = open(apk, 'rb').read()
    sha = hashlib.sha256(data).hexdigest()
    listed = open(apk + '.sha256', encoding='utf-8').read().split()[0] if os.path.isfile(apk + '.sha256') else None
    if listed != sha:
        fail('%s.sha256 says %s, the APK hashes to %s' % (os.path.basename(apk), listed, sha))
    mb = round(len(data) / 1e6)

    page_path = os.path.join(a.site, 'kaizocore.html')
    page = open(page_path, 'rb').read().decode('utf-8')
    links = APK_LINK.findall(page)
    if len(links) != 2 or len(set(links)) != 1:
        fail('expected the page to link one APK twice, found %s' % links)
    old_version, old_stamp = links[0]
    old_sha = re.search(r'<span class="sha">([0-9a-f]{64})</span>', page)
    if not old_sha:
        fail('no <span class="sha"> on the page')

    page2 = page.replace('kaizocore/KaizoCore-%s.apk?v=%s' % (old_version, old_stamp), 'kaizocore/KaizoCore-%s.apk?v=%s' % (version, sha[:8]))
    page2 = replace_once(page2, 'Download KaizoCore %s for Android' % old_version, 'Download KaizoCore %s for Android' % version, 'version')
    page2 = replace_once(page2, '<span class="sha">%s</span>' % old_sha.group(1), '<span class="sha">%s</span>' % sha, 'checksum')
    page2, n1 = re.subn(r'Download the beta \(APK, \d+ MB\)', 'Download the beta (APK, %d MB)' % mb, page2)
    page2, n2 = re.subn(r'APK &middot; \d+ MB &middot;', 'APK &middot; %d MB &middot;' % mb, page2)
    if (n1, n2) != (1, 1):
        fail('expected one of each size line on the page, found %d and %d' % (n1, n2))
    if old_version != version and old_version in page2:
        fail('the page still names %s somewhere after the edit: update that line by hand first' % old_version)

    manifest = {
        'app': 'KaizoCore',
        'versionCode': code,
        'version': version,
        'published': datetime.date.today().isoformat(),
        'apk': '%s/kaizocore/KaizoCore-%s.apk?v=%s' % (ORIGIN, version, sha[:8]),
        'bytes': len(data),
        'sha256': sha,
        'notes': RELEASES + version,
        'page': ORIGIN + '/kaizocore',
    }

    toml_path = os.path.join(a.site, 'netlify.toml')
    toml = open(toml_path, 'rb').read().decode('utf-8')
    nl = '\r\n' if '\r\n' in toml else '\n'
    old_from = 'from = "/kaizocore/KaizoCore-%s.apk"' % old_version
    add_301 = old_version != version and old_from not in toml
    if add_301:
        anchor = toml.find('[[redirects]]' + nl + '  from = "/kaizocore/KaizoCore-')
        if anchor < 0:
            fail('netlify.toml has no retired-APK redirect to put the new one beside')
        block = nl.join(['[[redirects]]', '  ' + old_from, '  to = "/kaizocore"', '  status = 301', '  force = true', '', ''])
        toml = toml[:anchor] + block + toml[anchor:]

    print('KaizoCore %s (versionCode %d): %d MB, sha256 %s' % (version, code, mb, sha))
    print('  page       %s -> %s' % (old_version, version) if old_version != version else '  page       same version, checksum and size refreshed')
    print('  APK        copy to kaizocore/%s%s' % (os.path.basename(apk), ', remove KaizoCore-%s.apk' % old_version if old_version != version else ''))
    print('  redirect   %s' % ('301 /kaizocore/KaizoCore-%s.apk -> /kaizocore' % old_version if add_301 else 'none needed'))
    print('  manifest   kaizocore/latest.json -> versionCode %d' % code)
    if a.dry_run:
        print('dry run: nothing written')
        return

    open(page_path, 'wb').write(page2.encode('utf-8'))
    dest = os.path.join(a.site, 'kaizocore', os.path.basename(apk))
    shutil.copyfile(apk, dest)
    if hashlib.sha256(open(dest, 'rb').read()).hexdigest() != sha:
        fail('the copied APK does not hash to %s' % sha)
    old_apk = os.path.join(a.site, 'kaizocore', 'KaizoCore-%s.apk' % old_version)
    if old_version != version and os.path.exists(old_apk):
        os.remove(old_apk)
    if add_301:
        open(toml_path, 'wb').write(toml.encode('utf-8'))
    open(os.path.join(a.site, 'kaizocore', 'latest.json'), 'w', encoding='utf-8', newline='\n').write(json.dumps(manifest, indent=2) + '\n')
    print('written. Next: bash "PX PUSH WCG/build-deploy.sh" && python redesign-prototype/.release/build_release.py (from the WCG repo)')


if __name__ == '__main__':
    main()
