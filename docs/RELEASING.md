# Releasing KaizoCore

The whole release, in order. Each step has bitten once when it was skipped or done out of order. Publishing (the
GitHub release, the public source, the wiki and the site) happens only on Blake's go, in one sitting.
(rc32 audit P3 #103: the steps used to live in five places.)

## Before

1. **Keep what crash reports need.** `dist/KaizoCore-<version>-mapping.txt.gz` and `dist/KaizoCore-<version>-symbols/`
   exist for the last release (release.sh writes them since rc34; rc32's mapping is kept in the main checkout's dist/;
   rc33's was lost and can only be rebuilt from 4a248501 if its pg_map_id comes out bdfe229).
2. **The keys are backed up off this PC** (Blake's step, RC33-P1 #3; "The release key" below). Every installed KaizoCore
   trusts the debug key in `~/.android/debug.keystore`, and once the release key is made, every phone on Android 9 or
   later that takes a release signed with it trusts that one; a lost key means its players have to uninstall, which
   deletes their games and saves.
3. **One release commit** carries the new `versionCode` and `baseVersion` in app/build.gradle.kts, and nothing after
   it. A build made after the bump carries the release's code and is never offered the release (P2 #6), so the code
   moves only in the commit you release; release.sh refuses otherwise. The tree is clean.
4. **Notes.** Move the last release's `dist/RELEASE-NOTES.md` aside (release.sh refuses to reuse it). Write this
   release's in `docs/RELEASE-NOTES.md` and `docs/wiki/Release-notes.md`, with the wiki's current-download lines.
   Review NOTICE's release conditions.

## Build

5. `bash tools/release.sh` in the worktree that holds the release commit (`--respin` only to ship the same versionCode
   again, on purpose). It takes the shared Gradle lock, runs the full suite uncached and counts the results from the
   JUnit XML (write the counts into QA-RC<n>.md), builds the release APK, checks the signers, checks every native library
   is 16 KB aligned and liblibretrodroid.so stripped (tools/check_apk_libs.py), and keeps the R8 mapping and the
   unstripped libraries beside the APK. About fifteen minutes, more now that the ROM tests run.
   The suite runs on the real dumps (P3 #105): release.sh points IRONMON_ROMS and IRONMON_DUMPS at the main checkout's
   `.vendor/roms` and `.vendor/dumps` (or what they are already set to) and refuses when either is missing or empty, and
   sets IRONMON_REQUIRE_DUMPS=1, under which a ROM or RAM-dump test fails where it used to return when a file it needs is
   missing (the games with no dump yet are listed in each module's test `Dumps.kt`, NOT_YET). It refuses a run whose
   DumpsTest did not see that variable.
   Once the release key is pinned, **Blake runs release.sh himself, in a console window**: after the build, apksigner asks
   for the release key's password ("The release key" below). Until then it signs with the debug key as before.
6. Check the APK: `aapt2 dump badging` gives the new versionCode, `<version>+<HEAD8>`, native code arm64-v8a and
   x86_64, and no required camera feature.

## QA

7. On the emulator (cold boot, `-memory 4096`, one adb driver at a time): install the release APK over the public
   build with data in place; open Play on a Game Boy, a GBA and a DS game; NEW RUN stamps the new version; logcat has
   no FATAL, ANR or VerifyError (PlayScreen sits at the ART verifier's method size limit). The first release signed with
   the release key also takes the device checks under "The release key" below. The in-app update needs the build on the
   site, so it is step 15.
8. `dist/RELEASE-NOTES.md`: the download line first, Changed and Known issues filled in.

## Publish (Blake's go)

9. Merge the branch into master in C:/Users/bepor/IronMonOne with
   `git -c user.name="Blake Porterfield" -c user.email="blake.portable7@gmail.com"` (no identity is set there), tag
   `v<version>`, push both to `dev` (private).
10. Public source: a fresh shallow clone of blakeportable7-del/KaizoCore, its files replaced by
    `git -C C:/Users/bepor/IronMonOne archive master`; check the file counts and that no ROM, BIOS, key or RogueMon file
    is in it; commit "KaizoCore <version>"; push.
11. `gh release create v<version> dist/KaizoCore-<version>.apk dist/KaizoCore-<version>.apk.sha256 --repo
    blakeportable7-del/KaizoCore --title "KaizoCore <version> (beta)" --notes-file dist/RELEASE-NOTES.md --target <the
    snapshot's full 40-character sha> --latest`. Never `--prerelease`. Never attach the mapping or the symbols.
    `curl -sI https://github.com/blakeportable7-del/KaizoCore/releases/latest` must point at the new tag.
12. Wiki: clone KaizoCore.wiki.git, copy the changed `docs/wiki/*.md` in, commit, push.
13. Site, last of all: `python tools/site_bump.py --dry-run`, then `python tools/site_bump.py` (it refuses an APK whose
    versionCode did not go up, unless `--respin`). From the WCG root: `bash "PX PUSH WCG/build-deploy.sh"`, then
    `python redesign-prototype/.release/build_release.py --kaizocore-release`. Diff `wcg-v4-release` against the live
    deploy's files: only the KaizoCore page, the APK in and out, latest.json, netlify.toml's 301 and sitemap dates may
    differ (another session's work in Saturday/ ships with any deploy). Deploy from inside `wcg-v4-release` with
    `--site 11b0442d-95de-4786-9929-71a458425b55` and note the previous deploy id.

## Verify

14. The audit function answers with a score (`curl -s 'https://willowcreek.group/.netlify/functions/audit?business=Studio%2027&town=St.%20Clairsville'`),
    10 functions deployed, latest.json names the new versionCode, the site's APK and the GitHub APK hash to the build,
    the old APK's link 301s to the page, and the notes link answers 200.
15. **A phone takes it the way players will, before the release is announced anywhere** (P3 #86: until rc34 this path
    was only ever run against stand-ins). On a phone or the emulator running the **previous public release**, with a
    run, saves and settings in it: More, Backup and info, Updates, Check now, Update. That is the real path: latest.json
    from the site, the download from the site with its size and SHA-256 checked, then Android's installer. The app must
    come back as the new `<version>+<HEAD8>` with the run, the saves and the settings kept, and Check now must then say
    "You have the newest build." A copy installed with adb has no installer of record, so Android asks to confirm this one
    update; a copy that updated itself before does not ask. If it fails, roll the site back (below) before anyone else is
    offered the build. The first release signed with the release key takes this on Android 9 or later above all: it is
    the first time a phone checks the lineage on a real update.
16. `docs/QA-RC<n>.md`: Shipped, with the test counts, step 15's versionName and what it kept, and what was not checked
    on a phone.

## Rollback

A site rollback (`netlify api restoreSiteDeploy` with the previous deploy id) stops new offers, but a phone already on
the new versionCode stays there: Android refuses a lower versionCode without an uninstall. Fix forward with the next
release.

## The release key

(rc32 audit P2 #7, #8; Blake, 2026-10-02: "A real release key, and a backup of it.") Until the switch, every installed
KaizoCore trusts one key: the debug key in `C:/Users/bepor/.android/debug.keystore`, guarded by AGP's public password.
Whoever copies that file can sign an update that phones take, and if it is lost no phone can take another one. The
switch moves players to a key only Blake can use, without stranding anyone (APK Signature Scheme v3 key rotation):

- **Android 9 and later** take the first release signed with the release key as an ordinary update, because the APK
  carries a lineage in which the debug key vouches for the release key. From then on that phone trusts the release key,
  and the debug key alone can no longer sign an update it takes (the lineage gives the debug key no rollback capability).
- **Android 8** (8.0 and 8.1) reads only the v2 signature, and that stays the debug key's in every release, so those
  phones keep updating on the debug key.
- Nobody uninstalls, and nothing changes on a player's screen.

Until Blake has done step 3 below, nothing changes: Gradle signs a release with the debug key and the gate checks it
exactly as before.

### Making it (Blake, once)

1. **Make the key**, in a console window (Windows Terminal or PowerShell: Git Bash's own window shows a password as you
   type it):

   ```
   mkdir C:\Users\bepor\keys
   keytool -genkeypair -v -storetype PKCS12 -keystore C:/Users/bepor/keys/kaizocore-release.p12 -alias kaizocore -keyalg RSA -keysize 4096 -validity 10000 -dname "CN=KaizoCore"
   ```

   keytool asks for a password twice: pick a long one and put it in the password manager before anything else. The
   keystore never goes in a repo (`*.p12`, `*.jks` and `*.keystore` are ignored, and master is archived into the public
   repo), nor on the OneDrive desktop. Another place for it: set `KAIZOCORE_RELEASE_KEYSTORE` to its path.
2. **Back it up, and the debug key with it** (below), before anything is signed with it.
3. **Link it to the debug key**, in a console window, in the checkout the next release is built from:
   `python tools/sign_release.py --setup`. apksigner asks for the release key's password. It writes `tools/release.lineage`
   (the two certificates and the debug key's signature over the new one; nothing secret, and every APK signed with it
   carries a copy) and the release key's certificate SHA-256 on the `release` line of `tools/release-certs.txt`. Check
   `git diff` and commit both files.

From that commit on:

- A release build comes out unsigned (`app-release-unsigned.apk`), and release.sh signs it after the build with
  `python tools/sign_release.py`, where apksigner asks for the password on the terminal. So **Blake runs release.sh
  himself**, from a Git Bash tab in Windows Terminal. release.sh checks both keys, the lineage and that it has a console
  before the fifteen minutes of tests, and gives the Gradle lock back before it asks.
- release.sh and site_bump.py refuse an APK unless Android 8 reads the debug key, Android 9 and later read the release
  key (in the v3 block, not v3.1) and the lineage is the debug key then the release key
  (`python tools/release_checks.py signing <apk>` prints all three).
- Any other release-type build (a phone build with `-Pironmon.phone=true`, a lite one) comes out unsigned too: sign it
  with `python tools/sign_release.py <unsigned.apk> <signed.apk>` before it goes to a phone. `-Pironmon.debugSigned=true`
  signs one with the debug key instead, for an emulator that never had a release.
- A debug build no longer installs over a release on Android 9 and later: uninstall first (which deletes the data), or
  test on an emulator that never had a release.

What sign_release.py runs, checked against the apksigner 0.9 of build-tools 34.0.0 (`apksigner sign --help`, `rotate --help`):

```
# once (--setup): the lineage
apksigner rotate --out tools/release.lineage --old-signer --ks ~/.android/debug.keystore --ks-key-alias androiddebugkey --ks-pass pass:android --new-signer --ks C:/Users/bepor/keys/kaizocore-release.p12 --ks-key-alias kaizocore
# each release: v2 by the debug key, v3 by the release key from Android 9 (API 28), with the lineage
apksigner sign --ks ~/.android/debug.keystore --ks-key-alias androiddebugkey --ks-pass pass:android --next-signer --ks C:/Users/bepor/keys/kaizocore-release.p12 --ks-key-alias kaizocore --lineage tools/release.lineage --rotation-min-sdk-version 28 --out app-release.apk app-release-unsigned.apk
```

`--rotation-min-sdk-version 28` is the one that matters: without it apksigner puts the release key in a v3.1 block that
only Android 13 and later read, and Android 9 to 12 stay on the debug key. Plain `apksigner verify --print-certs` shows
the v3 signer only; `--max-sdk-version 27` shows what Android 8 reads, and `apksigner lineage --in <apk> --print-certs`
the lineage. tools/sign_release_test.py proves all of it with throwaway keys made at test time.

### The backups (Blake)

| Key | File | Password |
|---|---|---|
| The release key | `C:/Users/bepor/keys/kaizocore-release.p12` | Blake's, in the password manager |
| The debug key | `C:/Users/bepor/.android/debug.keystore` | `android`, AGP's public default: the file itself is the secret |

- Copy both files to **two offline drives** (two USB sticks, kept in two different places, neither of them this PC), and
  keep the release key's password in the **password manager**, with a note of where the drives are. If the password
  manager takes attachments, attach both files to that entry as a third copy.
- Open each copy once, so a bad copy is found now and not on release day:
  `keytool -list -v -keystore E:/kaizocore-release.p12 -alias kaizocore` asks for the password and must show the SHA-256
  on the `release` line of tools/release-certs.txt; `keytool -list -v -keystore E:/debug.keystore -storepass android -alias androiddebugkey`
  must show c2954523...8b07, the `players` line.
- Never in a repo, on the OneDrive desktop, in email or chat, or in a cloud folder in the clear.
- The lineage needs no backup: it is in the repo, and every APK signed with it carries it (apksigner takes such an APK
  where it takes the lineage file: `--lineage dist/KaizoCore-<version>.apk`).

What losing each one means:

- **The release key** (the file, or its password): every phone on Android 9 or later that took a release signed with it
  accepts updates from that key only, so none of them can take another update. Each of those players would have to
  uninstall, which deletes every game, save, run and note not in a KaizoCore backup, and install again. There is no way
  back: moving to another key needs this one to sign the move.
- **The debug key**: Android 8 phones read only its signature, so they can never take another update. Android 9 and
  later can still be served: minSdk 28 and v3 alone, signed by the release key with the lineage, which needs nothing from
  the debug key once it is made. Before the switch, losing it strands every player. Android Studio makes a new
  `debug.keystore` without a word when the file is missing: that is another key, and release.sh refuses it.
- **Both**: every player is stranded.

### Device checks before the first release signed with it

On the emulator (cold boot, `-memory 4096`, one adb driver at a time), once on an **Android 8.0 or 8.1 image** and once
on an **Android 9 or later image**:

1. Install the current public release (signed with the debug key alone) and give it a run, a save and settings.
2. `adb install -r` the new release, signed with the release key: it installs as an update, and the run, the save and
   the settings are still there. That is the Android 8 phone reading the debug key's v2 signature, and the Android 9 one
   following the lineage.
3. On the Android 9 or later image, `adb install -r` the same build signed with the debug key alone (built with
   `-Pironmon.debugSigned=true`, same versionCode): Android refuses it (INSTALL_FAILED_UPDATE_INCOMPATIBLE), which shows
   the phone now trusts the release key. On the Android 8 image the same APK installs: it still reads the debug key.
4. Step 15's real update on both images, once the release is on the site.
