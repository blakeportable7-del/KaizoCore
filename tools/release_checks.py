"""The release gate's checks, in one place so they can be tested (tools/release_checks_test.py, sign_release_test.py).

  python tools/release_checks.py pre <version> [--respin]   tree clean, versionCode bumped in HEAD, nothing tagged
                                                            overwritten, dist's notes not the last release's
  python tools/release_checks.py key                        the keys the release will be signed with are here, before
                                                            the tests: the debug key, and the release key once pinned
  python tools/release_checks.py clean                      the tree is still the commit the APK is stamped with
  python tools/release_checks.py tests                      count every JUnit result; refuse a failure or an error, or a
                                                            run the dumps guard did not reach
  python tools/release_checks.py signing <apk>              the APK is signed the way every installed copy can take it
  python tools/release_checks.py mapping <apk> <mapping>    the mapping is the one R8 wrote for that APK

rc32 audit: P2 #6 (a test build carrying the release's versionCode is never offered the release), P3 #104 (a red or
cached suite passed the gate, the stamped tree was not pinned, a tagged APK could be overwritten), P3 #12 (no build's
mapping was kept, so a release stack trace could not be read), P2 #7 and #8 (the release key), P3 #105 (the ROM and
RAM-dump tests returned early and counted as passes).
"""
import glob
import gzip
import os
import re
import subprocess
import sys
import zipfile

ROOT = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), '..'))

# ---------------------------------------------------------------------------------------------------------- signing
# rc32 audit P2 #7, #8. Every installed KaizoCore trusts the debug key in ~/.android/debug.keystore. Until Blake's
# release key is pinned in tools/release-certs.txt, that key signs a release alone, as it always has. From then on a
# release carries both: the v2 signature by the debug key, the only one an Android 8 phone reads, and the v3 signature
# by the release key, which Android 9 and later read, with the lineage in which the debug key vouches for the release
# key. A phone holding the debug key's build takes it as an update and from then on trusts the release key.
# docs/RELEASING.md, "The release key", has the plan, the backups and what losing either key means.
CERTS = os.path.join(ROOT, 'tools', 'release-certs.txt')
LINEAGE = os.path.join(ROOT, 'tools', 'release.lineage')
BUILD_TOOLS = os.environ.get('ANDROID_BUILD_TOOLS', 'C:/Users/bepor/Android/Sdk/build-tools/34.0.0')
APKSIGNER_JAR = os.environ.get('APKSIGNER_JAR') or os.path.join(BUILD_TOOLS, 'lib', 'apksigner.jar')
DEBUG_KEYSTORE = os.path.expanduser('~/.android/debug.keystore')
DEBUG_ALIAS = 'androiddebugkey'
# AGP's own public default for the debug keystore; tools/release.sh has always passed it to keytool.
DEBUG_STOREPASS = 'android'
# Where the release keystore lives: outside every repo, never on the OneDrive desktop. Paths, not secrets.
RELEASE_KEYSTORE = os.environ.get('KAIZOCORE_RELEASE_KEYSTORE', 'C:/Users/bepor/keys/kaizocore-release.p12')
RELEASE_ALIAS = os.environ.get('KAIZOCORE_RELEASE_ALIAS', 'kaizocore')
SHA256 = re.compile(r'[0-9a-f]{64}')


def pinned_certs(path=CERTS):
    """(players, release) from tools/release-certs.txt: the debug key's certificate every installed copy has, and Blake's
    release key's, or None while he has not made it. A release line that is not a SHA-256 is an error, not "no key":
    read as empty, a pasted value with colons would quietly keep the debug key signing alone."""
    pins = {}
    for line in open(path, encoding='utf-8'):
        words = line.split('#', 1)[0].split()
        if words:
            if words[0] in pins:
                raise ValueError('%s has two %s lines' % (path, words[0]))
            pins[words[0]] = words[1] if len(words) > 1 else None
    for name in ('players', 'release'):
        if pins.get(name) is not None and not SHA256.fullmatch(pins[name]):
            raise ValueError('%s: the %s line is not a SHA-256 (64 lowercase hex digits)' % (path, name))
    if not pins.get('players'):
        raise ValueError('%s names no players certificate' % path)
    return pins['players'], pins.get('release')


def apksigner(*args, **kw):
    return subprocess.run(['java', '-jar', APKSIGNER_JAR] + list(args), capture_output=True, text=True, **kw)


def read_lineage(src):
    """The signing lineage in [src] (a lineage file, or an APK signed with one), oldest first, as
    [{'sha': ..., 'installed_data': bool, 'rollback': bool}], or None when it has none."""
    r = apksigner('lineage', '--in', src, '--print-certs')
    if r.returncode != 0:
        return None
    signers = []
    for line in r.stdout.replace('\r', '').splitlines():
        m = re.match(r'Signer #\d+ in lineage certificate SHA-256 digest: ([0-9a-f]{64})$', line)
        if m:
            signers.append({'sha': m.group(1)})
        m = re.match(r'Has (installed data|rollback) capability *: (true|false)$', line)
        if m and signers:
            signers[-1][m.group(1).replace(' ', '_')] = m.group(2) == 'true'
    return signers or None


def _view(apk, *sdk):
    r = apksigner('verify', '-v', '--print-certs', *sdk, apk)
    out = r.stdout.replace('\r', '')
    return {'verifies': r.returncode == 0 and out.startswith('Verifies'),
            'schemes': {m.group(1): m.group(2) == 'true' for m in re.finditer(r'^Verified using (v[0-9.]+) scheme[^:\n]*: (true|false)$', out, re.M)},
            'signers': re.findall(r'^Signer[^\n]*? certificate SHA-256 digest: ([0-9a-f]{64})$', out, re.M)}


def read_signing(apk):
    """What each Android reads of [apk]'s signature, as apksigner verifies it: 'android8' (API 26 and 27, which read v2
    and know nothing of v3), 'android9' (API 28 on) and the lineage the APK carries."""
    return {'android8': _view(apk, '--max-sdk-version', '27'), 'android9': _view(apk, '--min-sdk-version', '28'),
            'lineage': read_lineage(apk)}


def signing_problem(s, players, release):
    """Why [s] (read_signing's answer) is not how a release must be signed, or None. [release] is None until Blake's
    release key is pinned: then the debug key signs alone. Once it is pinned every release must carry the rotation, or a
    phone on Android 9 or later that already took one would refuse the next."""
    a8, a9, lin = s['android8'], s['android9'], s['lineage']
    if not a8['verifies']:
        return 'it does not verify on Android 8'
    if not a9['verifies']:
        return 'it does not verify on Android 9 and later'
    if a8['signers'] != [players]:
        return 'Android 8 reads its signer as %s, and every installed copy has %s' % (', '.join(a8['signers']) or 'nothing', players)
    if release is None:
        if a9['signers'] != [players] or lin:
            return ('Android 9 and later read its signer as %s%s, and no release key is pinned in tools/release-certs.txt: '
                    'until one is, the debug key %s signs alone' % (', '.join(a9['signers']) or 'nothing',
                                                                    ' with a lineage' if lin else '', players))
        return None
    if a9['schemes'].get('v3.1') or not a9['schemes'].get('v3'):
        return ('its release key is not in the v3 block Android 9 to 12 read: sign with --rotation-min-sdk-version 28 '
                '(tools/sign_release.py does)')
    if a9['signers'] != [release]:
        return 'Android 9 and later read its signer as %s, not the release key %s' % (', '.join(a9['signers']) or 'nothing', release)
    if not lin or [n['sha'] for n in lin] != [players, release]:
        return ('its lineage is %s, not the debug key %s then the release key %s'
                % (' -> '.join(n['sha'] for n in lin) if lin else 'missing', players, release))
    if not lin[0].get('installed_data'):
        return "its lineage does not let a phone holding the debug key's build keep its data (installed-data capability off)"
    if lin[0].get('rollback'):
        return 'its lineage lets the debug key sign updates again (rollback capability on)'
    return None


def describe_signing(s):
    """What Blake reads after a build: each Android's signer and the lineage."""
    a8, a9 = s['android8'], s['android9']
    schemes = '+'.join(k for k in ('v2', 'v3', 'v3.1') if a9['schemes'].get(k))
    lines = ['   Android 8 (v2):         %s' % (', '.join(a8['signers']) or 'nothing'),
             '   Android 9 and later (%s): %s' % (schemes or 'none', ', '.join(a9['signers']) or 'nothing')]
    if s['lineage']:
        lines.append('   lineage:                %s' % ' -> '.join(n['sha'] for n in s['lineage']))
    return '\n'.join(lines)


def keystore_cert(path, alias=DEBUG_ALIAS, storepass=DEBUG_STOREPASS):
    """The SHA-256 of [alias]'s certificate in the keystore at [path], read with keytool, or None. Only for the debug
    keystore, whose password is AGP's public default: the release keystore's is Blake's and only apksigner asks for it."""
    if not os.path.isfile(path):
        return None
    r = subprocess.run(['keytool', '-list', '-v', '-keystore', path, '-storepass', storepass, '-alias', alias],
                       capture_output=True, text=True)
    m = re.search(r'SHA256:\s*([0-9A-Fa-f:]{95})', r.stdout)
    return m.group(1).replace(':', '').lower() if r.returncode == 0 and m else None


def has_console():
    """Whether apksigner can ask for a password here without showing it: standard input and output are a console window
    (Windows Terminal, PowerShell, cmd). Git Bash's own window (mintty) hands a program pipes, where Java finds no
    console and reads the password as typed, on screen; so does a run with no terminal at all."""
    if os.name != 'nt':
        return sys.stdin.isatty() and sys.stdout.isatty()
    import ctypes
    import msvcrt
    mode = ctypes.c_uint32()
    for stream in (sys.stdin, sys.stdout):
        try:
            handle = msvcrt.get_osfhandle(stream.fileno())
        except (OSError, ValueError, AttributeError):
            return False
        if not ctypes.windll.kernel32.GetConsoleMode(ctypes.c_void_p(handle), ctypes.byref(mode)):
            return False
    return True


NO_CONSOLE = ('the release key\'s password is asked for on this terminal, and this is not a console window (Git Bash\'s own '
              'window would show the password as you type it, and a script has no keyboard): run it from Windows Terminal '
              'or PowerShell, or prefix it with winpty in Git Bash')


def key_problem(players, release, debug_keystore=DEBUG_KEYSTORE, release_keystore=RELEASE_KEYSTORE, lineage=LINEAGE,
                console=None):
    """Why the release could not be signed, checked before fifteen minutes of tests, or None."""
    if keystore_cert(debug_keystore) != players:
        return '%s is missing or is not the key players have. Restore it from the backup first.' % debug_keystore
    if release is None:
        return None
    if not os.path.isfile(release_keystore):
        return ('the release key is pinned and its keystore is not at %s: plug in the backup, or point '
                'KAIZOCORE_RELEASE_KEYSTORE at it' % release_keystore)
    chain = read_lineage(lineage) if os.path.isfile(lineage) else None
    if not chain or [n['sha'] for n in chain] != [players, release]:
        return '%s is not the lineage from the debug key to the pinned release key' % lineage
    if not (has_console() if console is None else console):
        return NO_CONSOLE
    return None


# ----------------------------------------------------------------------------------------------------- dumps guard
# rc32 audit P3 #105: tools/release.sh points IRONMON_ROMS and IRONMON_DUMPS at the dumps and sets IRONMON_REQUIRE_DUMPS,
# under which a ROM or RAM-dump test fails where it used to return. Each module's DumpsTest prints DUMPS_REQUIRED when
# the variable reached its test JVM; if Gradle ever stopped passing it on, the guard would be off with no sign of it.
GUARDED_MODULES = ('app', 'tracker-gba', 'tracker-nds')


def guard_missing(root):
    """The modules whose tests ran without the dumps guard, read from DumpsTest's results (every variant's)."""
    missing = []
    for mod in GUARDED_MODULES:
        found = glob.glob(os.path.join(root, mod, 'build', 'test-results', '**', 'TEST-*.DumpsTest.xml'), recursive=True)
        if not found or not all('DUMPS_REQUIRED' in open(p, encoding='utf-8', errors='replace').read() for p in found):
            missing.append(mod)
    return missing


def git(repo, *args):
    return subprocess.run(['git', '-C', repo] + list(args), capture_output=True, text=True)


def dirty(repo):
    """Why the tree is not clean, or None. Untracked files under dist/ are the release's own output."""
    if git(repo, 'diff', '--quiet').returncode != 0 or git(repo, 'diff', '--cached', '--quiet').returncode != 0:
        return 'the tree has uncommitted changes: commit or stash them, so the APK is the commit it is stamped with'
    untracked = [l for l in git(repo, 'ls-files', '--others', '--exclude-standard').stdout.splitlines() if not l.startswith('dist/')]
    if untracked:
        return 'untracked files would go into the build: %s' % ', '.join(untracked[:5])
    return None


def version_code_moved_at_head(repo, gradle='app/build.gradle.kts'):
    """Why the versionCode must move in the release commit, or None (P2 #6)."""
    last = git(repo, 'log', '-1', '--format=%H', '-G', r'versionCode = ', '--', gradle).stdout.strip()
    head = git(repo, 'rev-parse', 'HEAD').stdout.strip()
    if not last:
        return 'no commit ever set the versionCode in %s' % gradle
    if last != head:
        code = re.search(r'versionCode = (\d+)', open(os.path.join(repo, gradle), encoding='utf-8').read())
        return ('builds made since %s carry versionCode %s and would never be offered this release: bump it in the '
                'commit you release (or pass --respin to ship this code again)' % (last[:8], code.group(1) if code else '?'))
    return None


def overwrite(repo, version, dist='dist'):
    """Why the release would replace a published one, or None."""
    if git(repo, 'rev-parse', '-q', '--verify', 'refs/tags/v' + version).returncode == 0:
        return 'tag v%s exists: that release is published, so its APK is not rebuilt over' % version
    if os.path.exists(os.path.join(repo, dist, 'KaizoCore-%s.apk' % version)):
        return '%s/KaizoCore-%s.apk exists: move it aside first' % (dist, version)
    return None


def stale_notes(version, path):
    """Why dist's notes are the last release's, or None (P2 #116: rc33's notes started from rc32's file)."""
    if not os.path.exists(path):
        return None
    first = open(path, encoding='utf-8').readline().strip()
    if first != '# KaizoCore %s (beta)' % version:
        return '%s is still the notes for "%s": write this release\'s' % (path, first.lstrip('# '))
    return None


def junit_counts(root):
    """Tests, failures, errors and skipped over every JUnit XML under root's build/test-results."""
    t = f = e = s = 0
    for p in glob.glob(os.path.join(root, '**', 'build', 'test-results', '**', 'TEST-*.xml'), recursive=True):
        head = open(p, encoding='utf-8', errors='replace').read(4000)
        m = re.search(r'<testsuite [^>]*>', head)
        if not m:
            continue
        tag = m.group(0)
        def num(attr):
            a = re.search(r'\b%s="(\d+)"' % attr, tag)
            return int(a.group(1)) if a else 0
        t += num('tests'); f += num('failures'); e += num('errors'); s += num('skipped')
    return t, f, e, s


def dex_map_id(apk):
    """The pg-map-id R8 stamps into the APK's dex marker, or None."""
    with zipfile.ZipFile(apk) as z:
        for n in z.namelist():
            if re.fullmatch(r'classes\d*\.dex', n):
                m = re.search(rb'"pg-map-id":"([0-9a-f]+)"', z.read(n))
                if m:
                    return m.group(1).decode()
    return None


def mapping_map_id(mapping):
    opener = gzip.open if mapping.endswith('.gz') else open
    with opener(mapping, 'rt', encoding='utf-8', errors='replace') as f:
        for line in f:
            if not line.startswith('#'):
                break
            m = re.match(r'# pg_map_id: ([0-9a-f]+)', line)
            if m:
                return m.group(1)
    return None


def mapping_mismatch(apk, mapping):
    """Why [mapping] is not the one R8 wrote for [apk], or None (P3 #12)."""
    a, m = dex_map_id(apk), mapping_map_id(mapping)
    if a is None:
        return '%s carries no pg-map-id' % apk
    if m is None:
        return '%s carries no pg_map_id' % mapping
    if not a.startswith(m) and not m.startswith(a):
        return 'the mapping is %s, the APK was built with %s' % (m, a)
    return None


def main(argv):
    cmd = argv[1] if len(argv) > 1 else ''
    if cmd == 'pre':
        version, respin = argv[2], '--respin' in argv
        for why in (dirty(ROOT), None if respin else version_code_moved_at_head(ROOT), None if respin else overwrite(ROOT, version),
                    stale_notes(version, os.path.join(ROOT, 'dist', 'RELEASE-NOTES.md'))):
            if why:
                print('REFUSED: ' + why)
                return 1
        if respin:
            print('WARNING: --respin: phones on an earlier build of this versionCode are not offered this one')
        return 0
    if cmd == 'key':
        players, release = pinned_certs()
        why = key_problem(players, release)
        if why:
            print('REFUSED: ' + why)
            return 1
        print('== signing: ' + ('the debug key, alone (no release key pinned in tools/release-certs.txt)' if release is None else
                                'the debug key for Android 8 and the release key %s from Android 9 on; apksigner asks for '
                                'its password after the build' % release))
        return 0
    if cmd == 'clean':
        why = dirty(ROOT)
        if why:
            print('REFUSED: ' + why)
            return 1
        return 0
    if cmd == 'tests':
        t, f, e, s = junit_counts(ROOT)
        print('== tests: %d run, %d failures, %d errors, %d skipped (for the QA record)' % (t, f, e, s))
        if t == 0 or f or e:
            print('REFUSED: the suite is not green')
            return 1
        if os.environ.get('IRONMON_REQUIRE_DUMPS') == '1':
            missing = guard_missing(ROOT)
            if missing:
                print('REFUSED: IRONMON_REQUIRE_DUMPS did not reach the tests of %s, so their ROM and dump tests may have '
                      'returned early again (DumpsTest printed no DUMPS_REQUIRED)' % ', '.join(missing))
                return 1
        return 0
    if cmd == 'signing':
        players, release = pinned_certs()
        s = read_signing(argv[2])
        print(describe_signing(s))
        why = signing_problem(s, players, release)
        if why:
            print('REFUSED: %s is not signed the way every installed copy can take it: %s' % (argv[2], why))
            return 1
        print('== signed as every installed copy can take it (%s)' % ('debug key alone' if release is None else
                                                                      'debug key for Android 8, release key from Android 9'))
        return 0
    if cmd == 'mapping':
        why = mapping_mismatch(argv[2], argv[3])
        if why:
            print('REFUSED: ' + why)
            return 1
        print('== mapping %s matches the APK' % mapping_map_id(argv[3]))
        return 0
    print(__doc__)
    return 2


if __name__ == '__main__':
    sys.exit(main(sys.argv))
