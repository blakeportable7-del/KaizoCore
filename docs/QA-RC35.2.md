# rc35.2 QA (2026-10-04, shipped)

## Shipped (2026-10-04)

GitHub release v1.0.0-rc35.2, marked Latest (the public repo's tag on its snapshot
7278294d018d3e7702060b88da61967cd6f999b8; the private repo's tag on master 89e10d3b, built from f1829af2 on
fix/log-opens-lead), site deploy 6ac3085884bb2abf14f8b6d8 (rollback: 6ac2e9ce8a947ff768bea1c2, the rc35.1 deploy live
before it). APK 155,440,699 bytes, SHA-256 65f313df9e62c2f4feba74dceb003ba61d122b386047ece69f496d978329458a,
versionCode 48. Blake: "I can't play with that stupid arrow", then "Fix it" for the log.

What it carries, on fix/log-opens-lead from master ba72b20e:

- 3f84cab4, the floating window. Locked and see-through, the activity tells a swipe from a tap before Compose sees the
  touch (WindowSwipe.kt, MainActivity.dispatchTouchEvent). A move past the touch slop scrolls the window and never
  reaches the game. A tap goes on as a press and a lift at least 50 ms apart. A press held still goes on after 250 ms.
  The two round up and down arrows are gone. Unlocked, solid, docked and portrait are unchanged, including rc35's one
  down arrow there. The window's top is one row at every width, drawn 36 dp tall, each button keeping a 44 dp box that
  reaches past the row. Narrow, the grip dots go first, then the gear moves into the menu as "Tracker Setup"; with
  under 64 dp for the text, its trainer tap is in the menu too. The swap always stays in the row.
- 5beca2fd, the Gen 3 log opens on the lead's page, as the PC tracker's Program.openLogFromPath does
  (LogOverlay.lua 590-604). Back from it is the Pokemon grid with the search and sort reset. The DS log viewer already
  did this (DsLogViewer.kt, same reference), so it is unchanged.
- 1589c696 notes, wiki lines and the Seen (Trainer) note in FUTURE-PROJECTS.md; f1829af2 the bump.

A first rc35.2 build (4c46989f, without the log fix) was merged as 7dd08489, tagged and snapshotted publicly
(1637eb05) before the log fix was asked for. It was never released: master took the new branch on top of it (89e10d3b,
whose tree is f1829af2's), the dev tag was moved to 89e10d3b, the public snapshot 7278294d went on top, and its APK
(ec0a429f...) was moved out of dist.

**Play's method:** 35,270 bytes and 330 locals on a full compile (`compileDebugKotlin --rerun`, class file read), the
same as rc35.1. PlayScreen.kt is unchanged.

Tests: the app's unit tests on f1829af2's code, uncached after cleanTest: 2,074 debug and 2,074 release, 0 failures.
New: WindowSwipeTest (7), LogOpenTest (3), and WindowBarTest's one-row test, which walks every width from the least up
to 1200 dp with and without the swap, the gear and the lock. Red once each: with the swipe guard passing the press on,
the drag test failed ("nothing went on: [down]"); with leadPage returning null, the lead test failed. release.sh on
f1829af2: 5,219 tests on the real dumps, 0 failures, 0 errors, 0 skipped; signed with the debug key alone; every native
library 16 KB aligned and stripped; mapping 8a120e5 kept in dist.

The release check on that exact APK, on the emulator (AVD ironmon, Android 14), installed over rc35.1 with
`adb install -r`:

| Check | Result |
|---|---|
| Installs over 1.0.0-rc35.1+227852c7 with data kept; 1.0.0-rc35.2+f1829af2, versionCode 48; attempt 791 resumes in Lorelei's battle | pass |
| Locked, 60% see-through, window over the d-pad: a swipe that starts on the up arrow scrolls the window, and the battle cursor stays on Zen Headbutt | pass |
| A tap on empty window space over the up arrow moves the cursor to Pyro Ball | pass |
| No arrow in the locked window, at the top or the bottom | pass |
| The swap shows your Pokemon; the gear opens Tracker Setup | pass |
| Narrow window: one row (lock, scrolling text, swap, menu); the menu holds Tracker Setup, which opens it, and "Open the trainer's info" (debug build of the same code) | pass |
| The bar's lock and menu answer in 44 dp boxes (132 px at the emulator's density) on a 36 dp row | pass |
| No FATAL, ANR or VerifyError from KaizoCore in logcat or the dropbox | pass |
| rc35.1 updates itself through More > Backup and info > Check now > Update: "1.0.0-rc35.2 is out (155 MB)", downloaded from the site, installed with KaizoCore as the installer of record and no prompt; comes back as 1.0.0-rc35.2+f1829af2 with attempt 791, and Check now says "You have the newest build." | pass |

Screenshots: C:/Users/bepor/KaizoCore-shots/window-swipe/ (before and after the narrow top, the swipe, the tap, and
the release build's swipe then tap).

The window was put back afterwards: docked, 100% solid, unlocked; auto-rotate on. The battle cursor is on Pyro Ball.

The site: site_bump.py staged the page, the APK, latest.json and the rc35.1 301 in Saturday/. The page draft
(KaizoCore-site-drafts/tour-rc34/index.html, old copy index.before-rc352.html) carries the same link, stamp and
SHA-256; install_main.py's dry run reports nothing to change. No normal build ran. The deploy was a copy of rc35.1's
deploy folder, matched against listSiteFiles first (217 files, netlify.toml equal under the CLI's serialization),
with only kaizocore.html, kaizocore/latest.json, the APK swap and the rc35.1 301 changed; the diff against live was
exactly those paths. After it: the deploy is ready with 10 functions, the audit tool answers with a business,
latest.json names versionCode 48, the page links the rc35.2 APK twice, the site's APK and GitHub's hash to the build,
the rc35.1 link 301s to /kaizocore, and the release notes answer 200. The wiki was pushed (be1437a).

## Not yet seen on a device

- The Gen 3 log opening on the lead. The viewer opens only from the game-over screen, and reaching it on the emulator
  would end Blake's run 791 (no save states). Unit tests and the source checks only.

## Found on the way

- **Seen (Trainer) goes up by one on each app restart:** Leafeon's line read 17, 18, 19, 20 and 22 over the session's
  app starts, with no new battle. Noted in FUTURE-PROJECTS.md, not fixed.
