#!/usr/bin/env python3
"""Put a built release on willowcreek.group/kaizocore (2026-09-29).

    python tools/site_bump.py              # the version in app/build.gradle.kts, APK from dist/
    python tools/site_bump.py --dry-run    # say what would change, change nothing
    python tools/site_bump.py --respin     # publish a rebuilt APK under the versionCode the site already names

One release touches five things on the site, and they used to be edited by hand from a one-off script each time:
the page's two download links (with their ?v= stamp), its size and SHA-256 lines, the APK itself, a 301 for the
build it replaces, and kaizocore/latest.json, the file the app reads once a day to learn the newest build. If
latest.json lags the page, every phone keeps being offered the old build; if it leads the page, phones are sent to
an APK that is not there. So all five move here, together.

It edits the site's SOURCE (Saturday/ in the WCG repo) and nothing else, and deploys nothing. After it, build the
site (bash "PX PUSH WCG/build-deploy.sh", then python redesign-prototype/.release/build_release.py --kaizocore-release):
the release build's kaizocore-latest gate fails if the page, the APK and latest.json disagree. Deploying is Blake's go.

Run it LAST, right before that deploy: after the build's QA, with its GitHub release published. Saturday/ is the source
of every site deploy, so a release staged here early shipped on another session's deploy (2026-09-29), and since rc31
latest.json prompts every phone. The release build's kaizocore-release gate now refuses a latest.json newer than the
live one unless it is run with --kaizocore-release (rc33 audit P1 #66).

What it publishes is the APK's own word, not the Gradle file's (rc32 audit P2 #127): aapt2 reads the APK's versionCode
and versionName, and it is refused unless the code is the Gradle file's, the name carries the commit release.sh built
(HEAD or one before it), it is signed the way every installed copy can take it (the debug key alone until Blake's release
key is pinned in tools/release-certs.txt, then the debug key for Android 8 and the release key with the lineage from
Android 9 on: rc32 audit P2 #7, #8), and the code is above the one the site names now.
A release published under an unchanged code is offered to nobody. tools/site_bump_test.py tests the checks.
"""
import argparse
import datetime
import hashlib
import json
import os
import re
import shutil
import subprocess
import sys

ROOT = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), '..'))
SITE_DEFAULT = 'C:/Users/bepor/willowcreek-v2-deploy.zip/Saturday'
ORIGIN = 'https://willowcreek.group'
RELEASES = 'https://github.com/blakeportable7-del/KaizoCore/releases/tag/v'
APK_LINK = re.compile(r'kaizocore/KaizoCore-([0-9A-Za-z.+-]+)\.apk\?v=([0-9a-f]{8})')
BUILD_TOOLS = os.environ.get('ANDROID_BUILD_TOOLS', 'C:/Users/bepor/Android/Sdk/build-tools/34.0.0')
PACKAGE = 'com.ironmonone.app'
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import release_checks as rc  # noqa: E402

# The certificate every installed KaizoCore trusts (rc33 audit P1 #3), from tools/release-certs.txt, the one place
# release.sh, the build and this script read it.
PLAYERS_CERT = rc.pinned_certs()[0]


def fail(msg):
    print('site_bump: ' + msg)
    sys.exit(1)


def replace_once(text, old, new, what):
    n = text.count(old)
    if n != 1:
        fail('%s: expected exactly one %r on the page, found %d' % (what, old, n))
    return text.replace(old, new)


def badging(apk):
    """The APK's package, versionCode and versionName, as aapt2 reads them."""
    aapt2 = os.path.join(BUILD_TOOLS, 'aapt2.exe' if os.name == 'nt' else 'aapt2')
    if not os.path.isfile(aapt2):
        fail('aapt2 is not at %s: set ANDROID_BUILD_TOOLS to the SDK build-tools folder' % aapt2)
    out = subprocess.run([aapt2, 'dump', 'badging', apk], capture_output=True, text=True).stdout
    m = re.search(r"package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'", out)
    if not m:
        fail('aapt2 could not read the package line of %s' % apk)
    return m.group(1), int(m.group(2)), m.group(3)


def is_ancestor(commit):
    """The commit release.sh built is HEAD or one before it (the notes and the merge come after the build)."""
    return subprocess.run(['git', '-C', ROOT, 'merge-base', '--is-ancestor', commit, 'HEAD'], capture_output=True).returncode == 0


def check_apk(package, apk_code, apk_name, signing, gradle_code, version, site_code, site_sha, sha, respin, ancestor, pins=None):
    """Why this APK must not be published, or None. The arguments are what the APK, the Gradle file and the site's
    current latest.json say; [signing] is release_checks.read_signing's reading of the APK and [pins] the certificates
    in tools/release-certs.txt (read from it when None); [ancestor] answers whether a commit is HEAD or before it."""
    if package != PACKAGE:
        return 'the APK is %s, not %s' % (package, PACKAGE)
    if apk_code != gradle_code:
        return 'the APK says versionCode %d and app/build.gradle.kts says %d: build it again with tools/release.sh' % (apk_code, gradle_code)
    if not apk_name.startswith(version + '+'):
        return 'the APK is %s, not a build of %s' % (apk_name, version)
    commit = apk_name[len(version) + 1:]
    if not re.fullmatch(r'[0-9a-f]{7,40}', commit):
        return 'the APK is %s, a local build: only tools/release.sh stamps the commit a release is built from' % apk_name
    if not ancestor(commit):
        return 'the APK was built from %s, which is not this branch\'s HEAD or before it' % commit
    why = rc.signing_problem(signing, *(pins or rc.pinned_certs()))
    if why:
        return 'the APK is not signed the way every installed copy can take it: ' + why
    if site_code is not None:
        if apk_code < site_code:
            return 'the site already names versionCode %d, above this APK\'s %d' % (site_code, apk_code)
        if apk_code == site_code and site_sha != sha and not respin:
            return ('the site already names versionCode %d with another APK: a phone on that build would never be offered this '
                    'one. Bump versionCode, or pass --respin to publish it anyway' % apk_code)
    return None


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument('--site', default=SITE_DEFAULT, help='the site source folder (Saturday/)')
    ap.add_argument('--dry-run', action='store_true', help='print the plan, write nothing')
    ap.add_argument('--respin', action='store_true', help='allow the versionCode the site already names, for a rebuilt APK')
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
    package, apk_code, apk_name = badging(apk)
    try:
        current = json.load(open(os.path.join(a.site, 'kaizocore', 'latest.json'), encoding='utf-8'))
        site_code, site_sha = int(current['versionCode']), current.get('sha256')
    except (OSError, ValueError, KeyError, TypeError):
        site_code, site_sha = None, None
    why = check_apk(package, apk_code, apk_name, rc.read_signing(apk), code, version, site_code, site_sha, sha, a.respin, is_ancestor)
    if why:
        fail(why)

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

    print('KaizoCore %s (versionCode %d, build %s): %d MB, sha256 %s' % (version, code, apk_name, mb, sha))
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
    print('written. Next, in the same sitting: bash "PX PUSH WCG/build-deploy.sh" && '
          'python redesign-prototype/.release/build_release.py --kaizocore-release (from the WCG repo), then Blake\'s deploy')


if __name__ == '__main__':
    main()
