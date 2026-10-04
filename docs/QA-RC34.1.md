# rc34.1 QA (2026-10-04, shipped)

## Shipped (2026-10-04)

GitHub release v1.0.0-rc34.1, marked Latest (the public repo's tag on its rc34.1 snapshot 31e31040; the private repo's
tag on master cad4ce70, built from 43be1e6a on release/rc34.1), site deploy 6ac201aed393563645fcd7b2 (rollback:
6ac1ebf655a894cf8900a5d1, the deploy live before it, with rc34's page). APK 155,250,369 bytes, SHA-256
7e6a0272315f4a2eb46e4d087c935ca13c219e66bce79ac0be599ae9f9e336d6, versionCode 45. Blake: "Small update", ship it once
ready.

What it carries: fix/floating-tracker-tight (ca12f5a8), fix/natdex-move-descriptions (157cd3e4), fix/ability-reveal
(ecfa7913) and fix/platinum-comm-error (5ab2eec5), merged on master 342d49a8. The only conflicts were the four "rc34.1
(in testing)" sections of docs/RELEASE-NOTES.md, now one list; NOTICE, Licences.kt and PlayScreen.kt merged on their own
with every side kept.

Play's method after the merge (javap, from a full debug compile): code length 35,270 bytes, 330 locals (rc34 was
35,298 and 330; floating-tracker-tight alone 34,821). The ability branch's one-line RetroTriggerTap.attach in
PlayScreen accounts for the rest. It opened on the device on every system below with no VerifyError.

release.sh on 43be1e6a: 5,052 tests on the real dumps, 0 failures, 0 errors, 0 skipped; signed with the debug key alone
(no release key pinned yet); every native library 16 KB aligned and stripped; mapping 5eb05a7 and symbols kept in dist.

The release check on that exact APK, on the emulator (AVD ironmon, Android 14), installed over the public rc34:

| Check | Result |
|---|---|
| Installs over the public rc34 (1.0.0-rc34+74c24d0d) with data kept; versionName 1.0.0-rc34.1+43be1e6a, versionCode 45 | pass |
| DS: Black 2 opens in Play in portrait and landscape; resuming the Black 2 run (attempt 789) and the library copy, no crash | pass |
| One audio start per game load (AAudio s#1 LeafGreen, s#2 Black 2), no audio errors | pass |
| Landscape, DS tracker docked: the File strip stops at the tracker's edge, scrolls round (Menu then States, slot 1 again), MENU tapped and opens the app's menu | pass |
| The layout editor's bar stops at the tracker and comes round to Cancel; Done stays put | pass |
| File red in the tracker's menu and on the app bar; NEW red in the strip during a run | pass |
| Floating tracker: edge on the boxes, lock, grip, ATTEMPT 789 and the menu together at the left; unlocked, the left edge drags it wider and the height refits; docks again | pass |
| Platinum: File, Restart, then the title's Continue goes into the player's room, no "communication error" and no white screen | pass |
| GBA: the MaxDex FireRed run (attempt 791) in portrait and landscape; Lorelei's battle at 8x plays normally, tracker shows TRAINER BATTLE, SEE MINE and the gear | pass |
| Game Boy: Red in portrait and landscape, resumed into a wild battle (Rattata); the tracker shows WILD BATTLE, SEE FOE and the gear, no RUN | pass |
| The next run is stamped `app=1.0.0-rc34.1+43be1e6a 45` (prep/runs/current.recipe) | pass |
| No FATAL, ANR or VerifyError in logcat at any step | pass |
| rc34 updates itself through More > Backup and info > Check now > Update (latest.json, the site's APK, the installer): comes back as 1.0.0-rc34.1+43be1e6a with attempt 791 and its seed, and Check now says "You have the newest build." | pass |

The site, before the deploy: every gate of build_release.py --kaizocore-release passed (19 of 19), and the built folder
against the live deploy's files differed in exactly the release's own files (the page, latest.json, netlify.toml's 301
for the rc34 APK, the rc34.1 APK in and rc34's out); four more paths differed only in case with identical bytes;
sitemap.xml was unchanged. After it: the audit tool answers with a business (Studio 27 Hair Salon), 10 functions
deployed, latest.json names versionCode 45, the page links the rc34.1 APK twice, the APK from the site and from GitHub
both hash to 7e6a0272, the rc34 link 301s to /kaizocore, and the notes link answers 200. The wiki was pushed with the
rc34.1 notes and Home and IronMON-rules naming 1.0.0-rc34.1. The KaizoCore page's draft
(KaizoCore-site-drafts/tour-rc34/index.html) carries the same link, stamp, size and SHA-256; install_main.py --dry-run
reports it already installed.

## Found on the way

- site_bump.py refused the release: its "the page still names the old version" check was a plain substring test, and
  1.0.0-rc34.1 contains 1.0.0-rc34. Fixed on master (92e2e845, names_old()), with tests that fail against the old check.
- The release build could not clear wcg-v4-release while a shell's working directory was inside it: a session that
  starts there holds it with every Bash or PowerShell call. Run the build from a process started in the repo root.
- Killing the emulator right after an adb install lost the install's code folder: on the cold boot the package had its
  data (2.3 GB, uid 10211) but no APK and would not open. `adb install -r` of the same APK brought it back with the same
  uid and all data. Run `adb shell sync` before `adb emu kill`.
- A Restart of Platinum played from the Library moved the run page's attempt number from 789 to 790 with no new run,
  and the next run became 791. Not looked into; check whether a Library restart should count as an attempt.

## Not yet seen on a device

- An enemy weather ability caught at 8x: no battle with one could be reached without a save state.
- The RUN button's absence in a GBA wild battle (seen in a Game Boy wild battle and a GBA trainer battle; the tests
  cover every tracker).
- Area names scrolling in a narrow tracker, the narrow doubles banner and a Nat. Dex move description tap, on a device
  (unit and look tests only).
- The one-time Gen 4 clock lock on an old save: Platinum continued, the daily events were not checked.
