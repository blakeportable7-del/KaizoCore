#!/usr/bin/env python3
"""Sign a KaizoCore release with the release key, so that every installed copy can still take it (rc32 audit P2 #7, #8).

    python tools/sign_release.py --setup                      once, after making the key: link it to the debug key
    python tools/sign_release.py <unsigned.apk> <signed.apk>  each release (tools/release.sh runs it after the build)

Blake runs it, in a console window (Windows Terminal or PowerShell): apksigner asks for the release key's password
itself. Nothing passes the password in, writes it down or keeps it. The debug key's password is AGP's public default.

Every copy of KaizoCore installed before the switch trusts only the debug key in ~/.android/debug.keystore, so a release
carries both keys. The v2 signature is the debug key's: an Android 8 phone reads only that one. The v3 signature is the
release key's, from Android 9 on (--rotation-min-sdk-version 28; without it the release key would reach Android 13 and
later only), with tools/release.lineage, in which the debug key vouches for the release key. A phone on Android 9 or later
takes such a build as an update of the debug key's, and from then on trusts the release key. docs/RELEASING.md, "The
release key", has the whole plan, the backups and what losing either key means. tools/sign_release_test.py proves it with
throwaway keys.
"""
import argparse
import os
import shutil
import subprocess
import sys
import tempfile

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import release_checks as rc  # noqa: E402


def fail(msg):
    print('sign_release: ' + msg)
    sys.exit(1)


def run_apksigner(args, password):
    """apksigner with the terminal, so it asks for the release key's password itself. [password] is for
    sign_release_test.py alone: its throwaway key's password, on apksigner's standard input."""
    cmd = ['java', '-jar', rc.APKSIGNER_JAR] + args
    if password is None:
        return subprocess.run(cmd).returncode
    return subprocess.run(cmd, input=password + '\n', text=True, capture_output=True).returncode


def release_signer(keystore, alias, password):
    """The release keystore as apksigner's signer options: no --ks-pass, so apksigner asks (or reads the test's stdin)."""
    return ['--ks', keystore, '--ks-key-alias', alias] + ([] if password is None else ['--ks-pass', 'stdin'])


def debug_signer(keystore):
    return ['--ks', keystore, '--ks-key-alias', rc.DEBUG_ALIAS, '--ks-pass', 'pass:' + rc.DEBUG_STOREPASS]


def check_keys(players, debug_keystore, release_keystore, password):
    if rc.keystore_cert(debug_keystore) != players:
        fail('%s is missing or is not the key players have: restore it from the backup first' % debug_keystore)
    if not os.path.isfile(release_keystore):
        fail('no release keystore at %s: make it (docs/RELEASING.md, "The release key"), plug in the backup, or point '
             'KAIZOCORE_RELEASE_KEYSTORE at it' % release_keystore)
    if password is None and not rc.has_console():
        fail(rc.NO_CONSOLE)


def setup(certs, lineage, debug_keystore, release_keystore, alias, password=None):
    """Once: the lineage in which the debug key vouches for the release key, and the release key's pin."""
    players, release = rc.pinned_certs(certs)
    if release is not None:
        fail('%s already pins the release key %s: signing with another one would strand every phone that took it' % (certs, release))
    check_keys(players, debug_keystore, release_keystore, password)
    tmp = tempfile.mkdtemp(prefix='kclineage')
    try:
        out = os.path.join(tmp, 'release.lineage')
        print('== linking the release key to the debug key: apksigner asks for the release key\'s password')
        if run_apksigner(['rotate', '--out', out, '--old-signer'] + debug_signer(debug_keystore) +
                         ['--new-signer'] + release_signer(release_keystore, alias, password), password) != 0:
            fail('apksigner could not make the lineage (a wrong password, or no key "%s" in %s)' % (alias, release_keystore))
        chain = rc.read_lineage(out)
        if not chain or len(chain) != 2 or chain[0]['sha'] != players:
            fail('the lineage apksigner made is not the debug key then one more: %s' % chain)
        new = chain[1]['sha']
        if new == players:
            fail('the release keystore holds the debug key itself')
        if not chain[0].get('installed_data') or chain[0].get('rollback'):
            fail('the lineage gives the debug key the wrong capabilities: %s' % chain[0])
        shutil.copyfile(out, lineage)
    finally:
        shutil.rmtree(tmp, ignore_errors=True)
    text = open(certs, encoding='utf-8').read()
    lines = text.splitlines(True)
    at = [i for i, l in enumerate(lines) if l.split('#', 1)[0].split()[:1] == ['release']]
    if len(at) != 1:
        fail('%s has %d release lines, not one' % (certs, len(at)))
    lines[at[0]] = 'release %s\n' % new
    with open(certs, 'w', encoding='utf-8', newline='\n') as f:
        f.write(''.join(lines))
    if rc.pinned_certs(certs) != (players, new):
        fail('%s does not read back as the new pin' % certs)
    print('== the release key is %s' % new)
    print('   written: %s (its pin) and %s (the lineage: two certificates and the debug key\'s signature over the new one, '
          'nothing secret)' % (certs, lineage))
    print('   next: back up the keystore and its password (docs/RELEASING.md), then commit both files. From that commit on, '
          'a release build comes out unsigned and tools/release.sh signs it here.')
    return new


def sign(unsigned, signed, certs, lineage, debug_keystore, release_keystore, alias, password=None):
    """Each release: v2 by the debug key, v3 by the release key from Android 9 on, with the lineage; then verified."""
    players, release = rc.pinned_certs(certs)
    if release is None:
        fail('no release key is pinned in %s: until it is, Gradle signs a release with the debug key, and this script has '
             'nothing to do' % certs)
    chain = rc.read_lineage(lineage) if os.path.isfile(lineage) else None
    if not chain or [n['sha'] for n in chain] != [players, release]:
        fail('%s is not the lineage from the debug key to the pinned release key %s' % (lineage, release))
    check_keys(players, debug_keystore, release_keystore, password)
    if os.path.exists(signed):
        os.remove(signed)
    print('== signing %s: apksigner asks for the release key\'s password' % unsigned)
    if run_apksigner(['sign'] + debug_signer(debug_keystore) + ['--next-signer'] + release_signer(release_keystore, alias, password) +
                     ['--lineage', lineage, '--rotation-min-sdk-version', '28', '--out', signed, unsigned], password) != 0:
        # apksigner can leave a part-written file behind, and nothing half signed may sit where an APK is looked for.
        if os.path.exists(signed):
            os.remove(signed)
        fail('apksigner did not sign it (a wrong password, or %s is not the release key in the lineage)' % release_keystore)
    s = rc.read_signing(signed)
    print(rc.describe_signing(s))
    why = rc.signing_problem(s, players, release)
    zipalign = os.path.join(rc.BUILD_TOOLS, 'zipalign.exe' if os.name == 'nt' else 'zipalign')
    if why is None and subprocess.run([zipalign, '-c', '4', signed], capture_output=True).returncode != 0:
        why = 'it is not 4-byte aligned, and Android 11 and later refuse such an APK'
    if why:
        os.remove(signed)
        fail('the signed APK was refused and removed: ' + why)
    print('== %s: the debug key for Android 8, the release key from Android 9 on, and the lineage between them' % signed)


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument('apks', nargs='*', help='<unsigned.apk> <signed.apk>')
    ap.add_argument('--setup', action='store_true', help='once: make the lineage and pin the release key')
    ap.add_argument('--release-keystore', default=rc.RELEASE_KEYSTORE, help='default %(default)s, or KAIZOCORE_RELEASE_KEYSTORE')
    ap.add_argument('--alias', default=rc.RELEASE_ALIAS, help='the key in it (default %(default)s)')
    ap.add_argument('--debug-keystore', default=rc.DEBUG_KEYSTORE, help=argparse.SUPPRESS)
    ap.add_argument('--certs', default=rc.CERTS, help=argparse.SUPPRESS)
    ap.add_argument('--lineage', default=rc.LINEAGE, help=argparse.SUPPRESS)
    a = ap.parse_args(argv)
    if a.setup and not a.apks:
        setup(a.certs, a.lineage, a.debug_keystore, a.release_keystore, a.alias)
    elif not a.setup and len(a.apks) == 2:
        sign(a.apks[0], a.apks[1], a.certs, a.lineage, a.debug_keystore, a.release_keystore, a.alias)
    else:
        ap.print_usage()
        return 2
    return 0


if __name__ == '__main__':
    sys.exit(main())
