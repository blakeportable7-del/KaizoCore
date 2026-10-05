# rc35 QA (2026-10-04, shipped)

## Shipped (2026-10-04)

GitHub release v1.0.0-rc35, marked Latest (the public repo's tag on its rc35 snapshot
869f8074513e57d01315a19408091ba4f5fd1fda; the private repo's tag on master 34943ea5, built from 152d14e8 on
release/rc35), site deploy 6ac2ac444e7ec9558d75cadf (rollback: 6ac274514a9bad65fc301b8b, the deploy live before it).
APK 155,415,679 bytes, SHA-256 1c11dc48d92f11336c7dc84e32915dd71c46337c069f35316337ecb2caa6c042, versionCode 46.
Blake: "yes", ship rc35 now with what's built.

What it carries: feat/rule-marks (0d742bd6, which holds feat/gachamon), feat/darkus-walking-sprites (69f4fbd0) and
fix/rc35-little-things (24a274f8), merged on release/rc35 as 5293d965, 428b3029 and ef9b7273. Conflicts: the rc35
sections of docs/RELEASE-NOTES.md (now one list) and GameOverHost.kt's imports (little-things dropped `remember`; the
GachaMon prize card still uses it and the `by` delegates, so the imports stayed). NOTICE, Licences.kt and
FUTURE-PROJECTS.md merged on their own with every side kept. The melonDS core (both libraries, patch 0005, PINNED.txt,
the symbols) is byte for byte little-things'.

Play's method after the merge (javap, from a full debug compile): code length 35,270 bytes, 330 locals, the same as
rc34.1. It opened on DS, GBA and Game Boy with no VerifyError.

The app's unit tests on the merged tree before the bump: 2,040, 0 failures. release.sh on 152d14e8: 5,151 tests on the
real dumps, 0 failures, 0 errors, 0 skipped; signed with the debug key alone (no release key pinned yet); every native
library 16 KB aligned and stripped; mapping 31f8a14 and symbols kept in dist. aapt2: versionCode 46,
1.0.0-rc35+152d14e8, arm64-v8a and x86_64, camera not required.

The release check on that exact APK, on the emulator (AVD ironmon, Android 14), installed over rc34.1 with adb install -r:

| Check | Result |
|---|---|
| Installs over 1.0.0-rc34.1+43be1e6a with data kept; versionName 1.0.0-rc35+152d14e8, versionCode 46 | pass |
| GBA: the MaxDex FireRed run (attempt 791) in portrait and landscape, Lorelei's battle, tracker shows TRAINER BATTLE | pass |
| GachaMon: Tracker Setup > GachaMon Collection opens; Captures, Collection, View, GachaDex and Options all open (Options has Add a card from a code and Import a PC tracker collection); Baxcalibur's card is 1 star, 4000 BP, and the star shows by Heals | pass |
| Mark banned items, abilities and moves is on after the upgrade | pass |
| A red X on a banned item or ability on a card | not reached: nothing in run 791 is banned |
| Play as your Pokemon, Always use Iron Boulder: in LeafGreen (Library, new game to the bedroom, nothing saved) the player is DarkusShadow's Iron Boulder, back and front, and walks. Set back to shiny Baxcalibur afterwards | pass |
| About (More > Backup and info): version, credits and licenses, no support button | pass |
| DS: Black 2 (Library) opens in portrait and landscape; Home's Continue resumes it ("Back where you left off") | pass |
| Platinum: resumes into the player's room; File, Restart, then the title's Continue goes into the room, no communication error, no white screen | pass |
| One audio stream opened and started per game load (s#3 to s#6), no audio errors | pass |
| Game Boy: Red in portrait and landscape, resumed into a wild battle (Rattata); tracker shows WILD BATTLE | pass |
| A Library restart (Platinum) left the run page at attempt 791, Start attempt 792 (rc34.1 moved it) | pass |
| The next run is stamped `app=1.0.0-rc35+152d14e8 46` (prep/next/next.meta, prepared right after the install) | pass |
| No FATAL, ANR or VerifyError from KaizoCore in logcat or the dropbox at any step | pass |
| rc34.1 updates itself through More > Backup and info > Check now > Update: "1.0.0-rc35 is out (155 MB)", downloaded from the site, installed with KaizoCore as the installer of record; comes back as 1.0.0-rc35+152d14e8 with attempt 791 and its seed (7a5ee6b03396353f), and Check now says "You have the newest build." | pass |

For the update test rc34.1 was put back with `adb install -r -d dist/KaizoCore-1.0.0-rc34.1.apk` (the downgrade flag
only, data kept; Blake's OK). The first try said "The download stopped" on the emulator's slow network (ping about
350 ms); after `adb shell sync`, `adb emu kill` and a cold boot (`-no-snapshot-load -memory 4096`) the second try went
through without an install prompt.

The site: site_bump.py staged the page, the APK, latest.json and the rc34.1 301 in Saturday/; the page draft
(KaizoCore-site-drafts/tour-rc34/index.html) carries the same link, stamp, size and SHA-256 and a new "What changed in
this build" for rc35 (GachaMon, share codes, rule marks, 19 more walking Pokemon by DarkusShadow, DS forms and the
fixes), installed with install_main.py (its dry run then reported nothing to change). build-deploy.sh and
build_release.py --kaizocore-release passed 19 of 19 gates, but the built folder against the live deploy differed in
about 25 more pages: another session's unshipped Apps page (redesign-prototype/apps.html) and its nav and footer link.
So that build did not ship. The deploy was built from a copy of the live deploy (6ac27451) with only the KaizoCore
page, latest.json, the APK swap and the rc34.1 301 changed, so the Apps work stayed out. After it: the audit tool
answers, latest.json names versionCode 46, the page links the rc35 APK twice, the APK answers 200 at 155,415,679
bytes, the rc34.1 link 301s, and the notes link answers 200. The wiki was pushed (edce042) with the rc35 notes and Home
and IronMON-rules naming 1.0.0-rc35.

## Found on the way

- Saturday/ and redesign-prototype/ hold the Apps work: the next normal build_release.py run ships it with rc35's
  files, which are now the live ones, so only the Apps pages would change.
- The Library restart attempt bug noted in rc34.1 did not happen on rc35 (little-things' run page fix).

## Not yet seen on a device

- A red X on a banned held item or ability, and the X on physical moves with Huge Power or Pure Power (unit tests only).
- A GachaMon share code going to or from the PC tracker, an import of a real FullCollection.gccg, and a Prize card
  after a game over (unit tests only).
- Trainers defeated by area with its sword, GSC Survival's Kanto heals, an enemy Deoxys, and a DS state saved mid-draw.
