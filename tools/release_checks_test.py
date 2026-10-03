#!/usr/bin/env python3
"""release_checks.py's refusals (rc32 audit P2 #6, #7, #8, P3 #12, #104, #105), on a throwaway git repo. The signing
rules run here on apksigner's readings as read_signing reports them; tools/sign_release_test.py runs them on real APKs
signed with throwaway keys.

    python tools/release_checks_test.py
"""
import gzip
import os
import shutil
import subprocess
import sys
import tempfile
import zipfile

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import release_checks as rc  # noqa: E402


def run(repo, *args):
    subprocess.run(['git', '-C', repo] + list(args), check=True, capture_output=True)


def write(repo, rel, text):
    p = os.path.join(repo, rel)
    os.makedirs(os.path.dirname(p), exist_ok=True)
    with open(p, 'w', encoding='utf-8', newline='\n') as f:
        f.write(text)


def commit(repo, msg):
    run(repo, 'add', '-A')
    run(repo, '-c', 'user.name=test', '-c', 'user.email=test@example.com', 'commit', '-q', '-m', msg)


def main():
    failures = []

    def expect(name, why, refused):
        if bool(why) != refused:
            failures.append('%s: %s' % (name, why or 'passed'))

    repo = tempfile.mkdtemp(prefix='relchk')
    try:
        run(repo, 'init', '-q')
        write(repo, 'app/build.gradle.kts', 'versionCode = 42\n')
        commit(repo, 'rc33')
        write(repo, 'app/build.gradle.kts', 'versionCode = 43\n')
        commit(repo, 'release rc34')
        expect('the versionCode moved in the release commit', rc.version_code_moved_at_head(repo), refused=False)
        expect('a clean tree', rc.dirty(repo), refused=False)
        write(repo, 'README.md', 'later\n')
        commit(repo, 'a fix after the bump')
        expect('builds after the bump carry its code', rc.version_code_moved_at_head(repo), refused=True)
        write(repo, 'README.md', 'edited\n')
        expect('an uncommitted change', rc.dirty(repo), refused=True)
        run(repo, 'checkout', '-q', '--', 'README.md')
        write(repo, 'stray.txt', 'x')
        expect('an untracked file', rc.dirty(repo), refused=True)
        os.remove(os.path.join(repo, 'stray.txt'))
        write(repo, 'dist/notes.md', 'x')
        expect("dist/ is the release's own output", rc.dirty(repo), refused=False)
        expect('no notes yet', rc.stale_notes('1.0.0-rc34', os.path.join(repo, 'dist', 'RELEASE-NOTES.md')), refused=False)
        write(repo, 'dist/RELEASE-NOTES.md', '# KaizoCore 1.0.0-rc33 (beta)\n\nold\n')
        expect("the last release's notes", rc.stale_notes('1.0.0-rc34', os.path.join(repo, 'dist', 'RELEASE-NOTES.md')), refused=True)
        write(repo, 'dist/RELEASE-NOTES.md', '# KaizoCore 1.0.0-rc34 (beta)\n\nnew\n')
        expect("this release's notes", rc.stale_notes('1.0.0-rc34', os.path.join(repo, 'dist', 'RELEASE-NOTES.md')), refused=False)
        expect('nothing published yet', rc.overwrite(repo, '1.0.0-rc34'), refused=False)
        write(repo, 'dist/KaizoCore-1.0.0-rc34.apk', 'apk')
        expect('an APK already in dist', rc.overwrite(repo, '1.0.0-rc34'), refused=True)
        os.remove(os.path.join(repo, 'dist', 'KaizoCore-1.0.0-rc34.apk'))
        run(repo, 'tag', 'v1.0.0-rc34')
        expect('a tagged release', rc.overwrite(repo, '1.0.0-rc34'), refused=True)

        # The suite is read from its XML, every module's.
        for mod, (t, f, e, s) in {'app': (10, 0, 0, 1), 'tracker-gba': (5, 1, 0, 0)}.items():
            write(repo, '%s/build/test-results/test/TEST-x.xml' % mod,
                  '<?xml version="1.0"?>\n<testsuite name="x" tests="%d" skipped="%d" failures="%d" errors="%d">\n</testsuite>\n' % (t, s, f, e))
        if rc.junit_counts(repo) != (15, 1, 0, 1):
            failures.append('junit counts: %s' % (rc.junit_counts(repo),))

        # The mapping kept beside the APK is the one R8 wrote for it.
        apk = os.path.join(repo, 'x.apk')
        with zipfile.ZipFile(apk, 'w') as z:
            z.writestr('classes.dex', b'dex\n035\0...~~R8{"backend":"dex","pg-map-id":"bdfe229","r8-mode":"full"}')
        good = os.path.join(repo, 'm.txt.gz')
        with gzip.open(good, 'wt', encoding='utf-8') as f:
            f.write('# compiler: R8\n# pg_map_id: bdfe229\n# pg_map_hash: SHA-256 x\nandroid.A -> a:\n')
        bad = os.path.join(repo, 'old.txt')
        write(repo, 'old.txt', '# compiler: R8\n# pg_map_id: 1234567\nandroid.A -> a:\n')
        expect('the matching mapping', rc.mapping_mismatch(apk, good), refused=False)
        expect("another build's mapping", rc.mapping_mismatch(apk, bad), refused=True)

        # The pins (P2 #7, #8): tools/release-certs.txt as committed, and as --setup leaves it.
        players, release = rc.pinned_certs()
        if players != 'c2954523b48a0b59e96e56cdd35cb8048469245f78a50a6e797c0a06a8608b07':
            failures.append('the committed players pin is %s' % players)
        P, R = 'c' * 64, 'e' * 64
        certs = os.path.join(repo, 'certs.txt')
        for text, want in (('# x\nplayers %s\nrelease\n' % P, (P, None)), ('players %s\nrelease %s  # pinned\n' % (P, R), (P, R))):
            write(repo, 'certs.txt', text)
            if rc.pinned_certs(certs) != want:
                failures.append('pins read from %r: %s' % (text, rc.pinned_certs(certs)))
        for text in ('players %s\nrelease E E\n' % P, 'players %s\nrelease %s\n' % (P, 'EE:' * 31 + 'EE'), 'release %s\n' % R,
                     'players %s\nrelease\nrelease %s\n' % (P, R)):
            write(repo, 'certs.txt', text)
            try:
                rc.pinned_certs(certs)
                failures.append('a malformed pins file was read: %r' % text)
            except ValueError:
                pass

        # What each Android reads of a signature, as read_signing reports it.
        def signed(a8, a9=None, lineage=None, v31=False, caps=None, v3=None):
            def view(who, v3):
                return {'verifies': who is not None, 'schemes': {'v2': True, 'v3': v3, 'v3.1': v31}, 'signers': [who] if who else []}
            chain = [dict({'sha': c, 'installed_data': True, 'rollback': False}, **(caps if i == 0 and caps else {}))
                     for i, c in enumerate(lineage or [])]
            return {'android8': view(a8, False), 'android9': view(a8 if a9 is None else a9, bool(lineage) if v3 is None else v3),
                    'lineage': chain or None}
        today, rotated = signed(P), signed(P, R, [P, R])
        expect('today: the debug key alone, nothing pinned', rc.signing_problem(today, P, None), refused=False)
        expect('an unknown key, nothing pinned', rc.signing_problem(signed('0' * 64), P, None), refused=True)
        expect('nothing that verifies', rc.signing_problem(signed(None), P, None), refused=True)
        expect('a rotation, nothing pinned', rc.signing_problem(rotated, P, None), refused=True)
        expect('the rotation to the pinned key', rc.signing_problem(rotated, P, R), refused=False)
        expect('the debug key alone, once pinned', rc.signing_problem(today, P, R), refused=True)
        expect('a rotation to an unknown key', rc.signing_problem(signed(P, '0' * 64, [P, '0' * 64]), P, R), refused=True)
        expect('the release key alone, no lineage', rc.signing_problem(signed(R, R), P, R), refused=True)
        expect('Android 8 reading the release key', rc.signing_problem(signed(R, R, [P, R]), P, R), refused=True)
        expect('the release key in v3.1 only (no --rotation-min-sdk-version 28)',
               rc.signing_problem(signed(P, R, [P, R], v31=True), P, R), refused=True)
        expect('Android 9 reading the release key with no lineage', rc.signing_problem(signed(P, R, v3=True), P, R), refused=True)
        expect('a lineage with a third key in it', rc.signing_problem(signed(P, R, ['0' * 64, P, R]), P, R), refused=True)
        expect('a lineage that drops the data', rc.signing_problem(signed(P, R, [P, R], caps={'installed_data': False}), P, R), refused=True)
        expect('a lineage that lets the debug key back in', rc.signing_problem(signed(P, R, [P, R], caps={'rollback': True}), P, R), refused=True)

        # The dumps guard (P3 #105) reached every guarded module's tests, every variant's.
        def result(path, out):
            write(repo, path, '<?xml version="1.0"?>\n<testsuite name="DumpsTest" tests="3">\n<system-out><![CDATA[%s]]></system-out>\n</testsuite>\n' % out)
        for mod in rc.GUARDED_MODULES:
            result('%s/build/test-results/test/TEST-com.x.DumpsTest.xml' % mod, 'DUMPS_REQUIRED roms=r dumps=d\n')
        if rc.guard_missing(repo):
            failures.append('the guard reached every module: %s' % rc.guard_missing(repo))
        result('app/build/test-results/testReleaseUnitTest/TEST-com.x.DumpsTest.xml', '')
        if rc.guard_missing(repo) != ['app']:
            failures.append('a variant the guard did not reach: %s' % rc.guard_missing(repo))
        os.remove(os.path.join(repo, 'tracker-nds/build/test-results/test/TEST-com.x.DumpsTest.xml'))
        if rc.guard_missing(repo) != ['app', 'tracker-nds']:
            failures.append('a module whose DumpsTest never ran: %s' % rc.guard_missing(repo))
    finally:
        shutil.rmtree(repo, ignore_errors=True)

    for f in failures:
        print('FAIL', f)
    print('release_checks: %d failed' % len(failures))
    return 1 if failures else 0


if __name__ == '__main__':
    sys.exit(main())
