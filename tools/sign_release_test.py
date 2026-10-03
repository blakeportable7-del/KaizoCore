#!/usr/bin/env python3
"""tools/sign_release.py and the release gate's signing checks on real APKs signed with THROWAWAY keys (rc32 audit P2 #7,
#8). Needs the JDK's keytool, and aapt2, apksigner and zipalign from the SDK's build-tools.

    python tools/sign_release_test.py

Makes three keystores in a temp folder (one stands in for ~/.android/debug.keystore, one for Blake's release key, one for
a stranger's), a minimal APK with aapt2, and signs it as a release is signed before the switch and after it. Everything is
deleted at the end. No real key is used: the real pins in tools/release-certs.txt are only compared against, to show that
the gate refuses an APK these keys signed.
"""
import glob
import os
import secrets
import shutil
import subprocess
import sys
import tempfile

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import release_checks as rc  # noqa: E402
import sign_release as sr  # noqa: E402
import site_bump as sb  # noqa: E402

TOOLS = os.path.dirname(os.path.abspath(__file__))
SDK = os.environ.get('ANDROID_HOME') or 'C:/Users/bepor/Android/Sdk'
MANIFEST = '''<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="com.example.kcsigtest" android:versionCode="1" android:versionName="1">
  <uses-sdk android:minSdkVersion="26" android:targetSdkVersion="34"/>
  <application android:label="t" android:hasCode="false"/>
</manifest>
'''


def keystore(path, alias, password, cn):
    subprocess.run(['keytool', '-genkeypair', '-keystore', path, '-storetype', 'PKCS12', '-storepass', password, '-alias', alias,
                    '-keyalg', 'RSA', '-keysize', '2048', '-validity', '10000', '-dname', 'CN=' + cn], check=True, capture_output=True)
    return rc.keystore_cert(path, alias, password)


def minimal_apk(tmp):
    manifest = os.path.join(tmp, 'AndroidManifest.xml')
    with open(manifest, 'w', encoding='utf-8') as f:
        f.write(MANIFEST)
    out = os.path.join(tmp, 'unsigned.apk')
    aapt2 = os.path.join(rc.BUILD_TOOLS, 'aapt2.exe' if os.name == 'nt' else 'aapt2')
    android_jar = sorted(glob.glob(os.path.join(SDK, 'platforms', 'android-*', 'android.jar')))[-1]
    subprocess.run([aapt2, 'link', '-o', out, '--manifest', manifest, '-I', android_jar], check=True, capture_output=True)
    return out


def refused(fn):
    """Whether [fn] stopped with sign_release's fail()."""
    try:
        fn()
        return False
    except SystemExit as e:
        return e.code == 1


def main():
    failures = []

    def expect(name, ok, detail=''):
        if not ok:
            failures.append('%s %s' % (name, detail))

    tmp = tempfile.mkdtemp(prefix='kcsign')
    try:
        p = lambda name: os.path.join(tmp, name).replace('\\', '/')
        pw = secrets.token_urlsafe(18)
        debug_ks, release_ks, stranger_ks = p('debug.keystore'), p('release.p12'), p('stranger.p12')
        OLD = keystore(debug_ks, rc.DEBUG_ALIAS, rc.DEBUG_STOREPASS, 'Throwaway Debug')
        NEW = keystore(release_ks, 'kaizocore', pw, 'Throwaway Release')
        X = keystore(stranger_ks, 'kaizocore', pw, 'Throwaway Stranger')
        expect('three throwaway keys', OLD and NEW and X and len({OLD, NEW, X}) == 3, (OLD, NEW, X))
        unsigned = minimal_apk(tmp)
        certs, lineage = p('release-certs.txt'), p('release.lineage')
        with open(certs, 'w', encoding='utf-8') as f:
            f.write('players %s\nrelease\n' % OLD)

        # Before the switch: the debug key alone, as Gradle signs a release today.
        today = p('today.apk')
        subprocess.run(['java', '-jar', rc.APKSIGNER_JAR, 'sign'] + sr.debug_signer(debug_ks) + ['--out', today, unsigned],
                       check=True, capture_output=True)
        s = rc.read_signing(today)
        expect('today: Android 8 and 9 both read the debug key', s['android8']['signers'] == [OLD] and s['android9']['signers'] == [OLD], s)
        expect('today: no lineage', s['lineage'] is None, s['lineage'])
        expect('today passes while no release key is pinned', rc.signing_problem(s, OLD, None) is None, rc.signing_problem(s, OLD, None))
        expect('today is refused once one is pinned', rc.signing_problem(s, OLD, NEW) is not None)
        expect('sign refuses while nothing is pinned', refused(lambda: sr.sign(unsigned, p('x.apk'), certs, lineage, debug_ks, release_ks, 'kaizocore', pw)))

        # The preflight before the tests.
        expect('preflight: the debug key, nothing pinned', rc.key_problem(OLD, None, debug_ks) is None, rc.key_problem(OLD, None, debug_ks))
        expect('preflight: a debug keystore holding another key', rc.key_problem(OLD, None, stranger_ks) is not None)
        expect('preflight: no debug keystore', rc.key_problem(OLD, None, p('missing.keystore')) is not None)

        # Setup: the lineage, and the release key pinned.
        expect('setup refuses a debug keystore that is not the players\' key',
               refused(lambda: sr.setup(certs, lineage, release_ks, release_ks, 'kaizocore', pw)))
        expect('setup with a wrong password', refused(lambda: sr.setup(certs, lineage, debug_ks, release_ks, 'kaizocore', pw + 'x')))
        expect('...writes nothing', rc.pinned_certs(certs) == (OLD, None) and not os.path.exists(lineage))
        pinned = sr.setup(certs, lineage, debug_ks, release_ks, 'kaizocore', pw)
        expect('setup pins the release key', pinned == NEW and rc.pinned_certs(certs) == (OLD, NEW), rc.pinned_certs(certs))
        chain = rc.read_lineage(lineage)
        expect('the lineage is the debug key, then the release key', [n['sha'] for n in chain or []] == [OLD, NEW], chain)
        expect('the debug key keeps the data and cannot sign updates back', chain and chain[0]['installed_data'] and not chain[0]['rollback'], chain)
        expect('setup refuses a second key', refused(lambda: sr.setup(certs, lineage, debug_ks, stranger_ks, 'kaizocore', pw)))
        expect('preflight: both keys and the lineage', rc.key_problem(OLD, NEW, debug_ks, release_ks, lineage, console=True) is None,
               rc.key_problem(OLD, NEW, debug_ks, release_ks, lineage, console=True))
        expect('preflight: no console for the password', rc.key_problem(OLD, NEW, debug_ks, release_ks, lineage, console=False) == rc.NO_CONSOLE)
        expect('preflight: no release keystore', rc.key_problem(OLD, NEW, debug_ks, p('missing.p12'), lineage, console=True) is not None)
        expect('preflight: no lineage', rc.key_problem(OLD, NEW, debug_ks, release_ks, p('missing.lineage'), console=True) is not None)

        # Each release after the switch: v2 by the debug key, v3 by the release key from Android 9, with the lineage.
        rotated = p('rotated.apk')
        sr.sign(unsigned, rotated, certs, lineage, debug_ks, release_ks, 'kaizocore', pw)
        s = rc.read_signing(rotated)
        expect('Android 8 reads the debug key (v2)', s['android8']['signers'] == [OLD] and s['android8']['schemes'].get('v2'), s['android8'])
        expect('Android 9 and later read the release key (v3, not v3.1)', s['android9']['signers'] == [NEW] and s['android9']['schemes'].get('v3')
               and not s['android9']['schemes'].get('v3.1'), s['android9'])
        expect('the APK carries the lineage', [n['sha'] for n in s['lineage'] or []] == [OLD, NEW], s['lineage'])
        expect('the rotated APK passes with its keys pinned', rc.signing_problem(s, OLD, NEW) is None, rc.signing_problem(s, OLD, NEW))
        expect('...and is refused while no release key is pinned', rc.signing_problem(s, OLD, None) is not None)
        expect('...and by the real pins, which are not these keys', rc.signing_problem(s, *rc.pinned_certs()) is not None)
        r = subprocess.run([sys.executable, os.path.join(TOOLS, 'release_checks.py'), 'signing', rotated], capture_output=True, text=True)
        expect('release_checks.py signing refuses it against the real pins', r.returncode == 1 and 'REFUSED' in r.stdout, r.stdout)
        site = dict(package='com.ironmonone.app', apk_code=43, apk_name='1.0.0-rc34+4a248501', signing=s, gradle_code=43,
                    version='1.0.0-rc34', site_code=42, site_sha='a' * 64, sha='b' * 64, respin=False, ancestor=lambda c: True)
        expect('site_bump takes the rotated APK with its keys pinned', sb.check_apk(pins=(OLD, NEW), **site) is None, sb.check_apk(pins=(OLD, NEW), **site))
        expect('site_bump refuses it against the real pins', sb.check_apk(**site) is not None)

        # What must not pass.
        stranger = p('stranger.apk')
        subprocess.run(['java', '-jar', rc.APKSIGNER_JAR, 'sign', '--ks', stranger_ks, '--ks-key-alias', 'kaizocore', '--ks-pass', 'stdin',
                        '--out', stranger, unsigned], input=pw + '\n', text=True, check=True, capture_output=True)
        s = rc.read_signing(stranger)
        expect('a stranger\'s key alone, nothing pinned', rc.signing_problem(s, OLD, None) is not None)
        expect('a stranger\'s key alone, pinned', rc.signing_problem(s, OLD, NEW) is not None)
        expect('sign with a stranger\'s keystore', refused(lambda: sr.sign(unsigned, p('s2.apk'), certs, lineage, debug_ks, stranger_ks, 'kaizocore', pw))
               and not os.path.exists(p('s2.apk')))
        expect('sign with a wrong password', refused(lambda: sr.sign(unsigned, p('s3.apk'), certs, lineage, debug_ks, release_ks, 'kaizocore', pw + 'x'))
               and not os.path.exists(p('s3.apk')))
        other_lineage = p('stranger.lineage')
        subprocess.run(['java', '-jar', rc.APKSIGNER_JAR, 'rotate', '--out', other_lineage, '--old-signer'] + sr.debug_signer(debug_ks) +
                       ['--new-signer', '--ks', stranger_ks, '--ks-key-alias', 'kaizocore', '--ks-pass', 'stdin'], input=pw + '\n', text=True,
                       check=True, capture_output=True)
        expect('sign with a lineage to another key', refused(lambda: sr.sign(unsigned, p('s4.apk'), certs, other_lineage, debug_ks, stranger_ks, 'kaizocore', pw)))
        rot_x = p('rotated-stranger.apk')
        subprocess.run(['java', '-jar', rc.APKSIGNER_JAR, 'sign'] + sr.debug_signer(debug_ks) + ['--next-signer', '--ks', stranger_ks,
                       '--ks-key-alias', 'kaizocore', '--ks-pass', 'stdin', '--lineage', other_lineage, '--rotation-min-sdk-version', '28',
                       '--out', rot_x, unsigned], input=pw + '\n', text=True, check=True, capture_output=True)
        expect('a rotation from the debug key to a stranger\'s', rc.signing_problem(rc.read_signing(rot_x), OLD, NEW) is not None)
        v31 = p('rotated-v31.apk')
        subprocess.run(['java', '-jar', rc.APKSIGNER_JAR, 'sign'] + sr.debug_signer(debug_ks) + ['--next-signer', '--ks', release_ks,
                       '--ks-key-alias', 'kaizocore', '--ks-pass', 'stdin', '--lineage', lineage, '--out', v31, unsigned],
                       input=pw + '\n', text=True, check=True, capture_output=True)
        expect('the release key for Android 13 and later only (no --rotation-min-sdk-version 28)',
               rc.signing_problem(rc.read_signing(v31), OLD, NEW) is not None)
        expect('an unsigned APK', rc.signing_problem(rc.read_signing(unsigned), OLD, NEW) is not None)

        # Run as a command with no console, as from a script, it refuses before apksigner could ask for the password.
        r = subprocess.run([sys.executable, os.path.join(TOOLS, 'sign_release.py'), unsigned, p('s5.apk'), '--certs', certs,
                            '--lineage', lineage, '--debug-keystore', debug_ks, '--release-keystore', release_ks],
                           stdin=subprocess.PIPE, capture_output=True, text=True, timeout=120)
        expect('the command refuses to ask for a password with no console', r.returncode == 1 and 'console' in r.stdout
               and not os.path.exists(p('s5.apk')), r.stdout + r.stderr)
    finally:
        shutil.rmtree(tmp, ignore_errors=True)

    for f in failures:
        print('FAIL', f)
    print('sign_release: %d failed' % len(failures))
    return 1 if failures else 0


if __name__ == '__main__':
    sys.exit(main())
