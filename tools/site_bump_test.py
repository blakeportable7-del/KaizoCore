#!/usr/bin/env python3
"""site_bump.py's refusals (rc32 audit P2 #127, and P2 #7 and #8 for the signers): what the APK says decides what is
published. The signers are stubbed here as release_checks.read_signing reads them; tools/sign_release_test.py reads
real APKs signed with throwaway keys.

    python tools/site_bump_test.py
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import site_bump as sb  # noqa: E402

OK_CERT = sb.PLAYERS_CERT
RELEASE = 'e' * 64
HEAD = lambda c: c == '4a248501'


def signed(android8, android9=None, lineage=None, v31=False):
    """What apksigner reads: the signer Android 8 sees (v2), the one Android 9 and later see, and the lineage."""
    def view(who, v3):
        return {'verifies': who is not None, 'schemes': {'v2': True, 'v3': v3, 'v3.1': v31}, 'signers': [who] if who else []}
    return {'android8': view(android8, False), 'android9': view(android8 if android9 is None else android9, lineage is not None),
            'lineage': [{'sha': c, 'installed_data': True, 'rollback': False} for c in lineage] if lineage else None}


TODAY = signed(OK_CERT)
ROTATED = signed(OK_CERT, RELEASE, [OK_CERT, RELEASE])


def check(**kw):
    args = dict(package='com.ironmonone.app', apk_code=43, apk_name='1.0.0-rc34+4a248501', signing=TODAY, gradle_code=43,
                version='1.0.0-rc34', site_code=42, site_sha='a' * 64, sha='b' * 64, respin=False, ancestor=HEAD,
                pins=(OK_CERT, None))
    args.update(kw)
    return sb.check_apk(**args)


def main():
    failures = []

    def expect(name, why, refused):
        if bool(why) != refused:
            failures.append('%s: %s' % (name, why or 'passed'))

    expect('a proper release', check(), refused=False)
    expect('an unchanged versionCode is offered to nobody', check(apk_code=42, gradle_code=42, site_code=42), refused=True)
    expect('...unless it is a respin', check(apk_code=42, gradle_code=42, site_code=42, respin=True), refused=False)
    expect('the same APK again changes nothing', check(apk_code=42, gradle_code=42, site_code=42, site_sha='b' * 64), refused=False)
    expect('a code below the site\'s', check(apk_code=41, gradle_code=41), refused=True)
    expect('the Gradle file edited after the build', check(gradle_code=44), refused=True)
    expect('a local build', check(apk_name='1.0.0-rc34+local'), refused=True)
    expect('another version\'s APK', check(apk_name='1.0.0-rc33+4a248501'), refused=True)
    expect('a commit not on this branch', check(apk_name='1.0.0-rc34+deadbeef'), refused=True)
    expect('another signer', check(signing=signed('0' * 64)), refused=True)
    expect('no signer at all', check(signing=signed(None)), refused=True)
    # The release key (P2 #7, #8): the rotated APK once it is pinned, and nothing else.
    expect('the rotated APK, the release key pinned', check(signing=ROTATED, pins=(OK_CERT, RELEASE)), refused=False)
    expect('the rotated APK, no release key pinned', check(signing=ROTATED), refused=True)
    expect('the debug key alone, once the release key is pinned', check(pins=(OK_CERT, RELEASE)), refused=True)
    expect('rotated to a key that is not the pinned one', check(signing=signed(OK_CERT, '0' * 64, [OK_CERT, '0' * 64]),
                                                                  pins=(OK_CERT, RELEASE)), refused=True)
    expect('the release key in a v3.1 block only', check(signing=signed(OK_CERT, RELEASE, [OK_CERT, RELEASE], v31=True),
                                                         pins=(OK_CERT, RELEASE)), refused=True)
    expect('Android 8 reading the release key', check(signing=signed(RELEASE, RELEASE, [OK_CERT, RELEASE]), pins=(OK_CERT, RELEASE)),
           refused=True)
    expect('the pins come from tools/release-certs.txt by default', check(pins=None), refused=sb.rc.pinned_certs()[1] is not None)
    expect('another package', check(package='com.example'), refused=True)
    expect('a site with no latest.json yet', check(site_code=None, site_sha=None), refused=False)
    # The page check after the edit (rc34.1): a point release's own name holds its parent's.
    for name, page, old, new, refused in (('a point release', 'Download KaizoCore 1.0.0-rc34.1 for Android', '1.0.0-rc34', '1.0.0-rc34.1', False),
                                          ('a point release with the old name left', 'KaizoCore 1.0.0-rc34.1, was 1.0.0-rc34', '1.0.0-rc34', '1.0.0-rc34.1', True),
                                          ('a release with the old name left', 'rc35, was 1.0.0-rc34', '1.0.0-rc34', '1.0.0-rc35', True),
                                          ('a respin', 'Download KaizoCore 1.0.0-rc34', '1.0.0-rc34', '1.0.0-rc34', False)):
        expect(name, 'names the old version' if sb.names_old(page, old, new) else None, refused=refused)
    for f in failures:
        print('FAIL', f)
    print('site_bump checks: %d failed' % len(failures))
    return 1 if failures else 0


if __name__ == '__main__':
    sys.exit(main())
