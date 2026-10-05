# rc35.1 QA (2026-10-04, shipped)

## Shipped (2026-10-04)

GitHub release v1.0.0-rc35.1, marked Latest (the public repo's tag on its rc35.1 snapshot
72b07b1a3b1a91240a39a571258bbaf91b74d0b1; the private repo's tag on master 6330babb, built from 227852c7 on
release/rc35.1), site deploy 6ac2e9ce8a947ff768bea1c2 (rollback: 6ac2ad1bbaa0d74ad95e70c3, the deploy live before it).
APK 155,436,803 bytes, SHA-256 42771faa2976f0e9d48683625325c719a46080d5d42c1063122fe15c3d66e159, versionCode 47.
Blake: "lets go! thanks, lets ship".

What it carries: feat/transparent-tracker (93668b95: the see-through slider, the wide view, the one-row window top,
and the Tracker HUD) and feat/favorites-in-play (3bf787f6: Edit favorites in Tracker Setup), merged on release/rc35.1
as 5ffce286 and d613681e. The only conflict was the rc35.1 section of docs/RELEASE-NOTES.md (both lists kept).
TrackerGearDialog.kt merged on its own.

**The HUD ships hidden** (Blake: keep it hidden until its second pass), bb6d2809: `TrackerHud.ENABLED = false`. The
tracker menu (from the dock and from the window) and Tracker Setup no longer offer it, the HUD layer is never drawn,
and a saved `trackerHud=true` loads as the floating window. The code stays, behind that one constant.
TrackerHudHiddenTest proves it: the switch is off, every line that offers or turns on the HUD sits behind it (red with
the menu and load guards removed: 2 of 3 failed), and a saved HUD choice loads as floating. The HUD's notes line came
out.

**Play's method:** the merged tree came out 35,272 bytes on a full compile, 2 over rc35's 35,270. The cause was
transparent-tracker's new `swipe` parameter on TrackerScroll: Play's two calls pushed a placeholder for it (found by
diffing Play's bytecode against rc35's: the instructions match except one extra `iconst_0`). 26aebf2b gives
TrackerScroll back its rc35 signature and puts the swipe choice in an overload only the floating window calls. After
it (class file read after `compileDebugKotlin --rerun`): code length 35,270 bytes, 330 locals, the same as rc35. An
incremental compile reads lower (34,829), so only a full compile's number counts.

The app's unit tests on the merged tree before the bump: 2,064, 0 failures. release.sh on 227852c7: 5,199 tests on
the real dumps, 0 failures, 0 errors, 0 skipped; signed with the debug key alone (no release key pinned yet); every
native library 16 KB aligned and stripped; mapping e8fd98e and symbols kept in dist. aapt2: versionCode 47,
1.0.0-rc35.1+227852c7, arm64-v8a and x86_64, camera not required.

The release check on that exact APK, on the emulator (AVD ironmon, Android 14, cold boot, `-memory 4096`), installed
over rc35 with `adb install -r`:

| Check | Result |
|---|---|
| Installs over 1.0.0-rc35+152d14e8 with data kept; versionName 1.0.0-rc35.1+227852c7, versionCode 47 | pass |
| DS: Platinum (Library) resumes from its auto-save in portrait and landscape | pass |
| GBA: the MaxDex FireRed run (attempt 791) in portrait and landscape, in Lorelei's battle | pass |
| Game Boy: Red (Library) in portrait and landscape, in a wild battle (Rattata) | pass |
| Floating window: one row on top; the line scrolls (ATTEMPT 791, then TRAINER BATTLE) | pass |
| Swap icon: a tap flips to your Pokemon; a held press shows SEE FOE above it | pass |
| Tapping the battle line opens the trainer's card (team, items, route) | pass |
| Window see-through at 30%: the game shows through, words and buttons stay solid | pass |
| Locked and see-through, a tap and a held press on blank window space over the d-pad's up arrow reach the game (the battle cursor moves); unlocked, the window keeps the same touch | pass |
| Wide view: a wide window lays out in columns, your Pokemon, stats and moves, the foe on the right | pass |
| Edit favorites from the gear in the Kaizo run: saved Eevee in Favorite 1, reopened, still there; then cleared back to blank | pass |
| No HUD choice: the tracker menu from the dock and from the window, and Tracker Setup's landscape choices (Docked, Floating, Hidden) | pass |
| The next run is stamped `app=1.0.0-rc35.1+227852c7 47` (prep/next/next.meta) | pass |
| No FATAL, ANR or VerifyError from KaizoCore in logcat or the dropbox at any step | pass |
| rc35 updates itself through More > Backup and info > Check now > Update: "1.0.0-rc35.1 is out (155 MB)", downloaded from the site, installed with KaizoCore as the installer of record and no prompt; comes back as 1.0.0-rc35.1+227852c7 with attempt 791 and its seed (7a5ee6b03396353f), and Check now says "You have the newest build." | pass |

For the update test rc35 was put back with `adb install -r -d dist/KaizoCore-1.0.0-rc35.apk` (data kept). The
emulator's network answered at about 200 ms; the download went through on the first try.

The window settings changed for the checks (Float, 30% see-through, locked, the wide size) were put back afterwards:
docked, 100% solid, unlocked. The battle cursor ended where the run had it (Pyro Ball). Favorites are blank again.

The site: site_bump.py staged the page, the APK, latest.json and the rc35 301 in Saturday/; the page draft
(KaizoCore-site-drafts/tour-rc34/index.html) carries the same link, stamp, size and SHA-256, the second button's
label, and one line in "What changed in this build" for rc35.1, installed with install_main.py (its dry run then
reported nothing to change). No normal build ran. The deploy was a copy of the live deploy (6ac2ad1b, matched byte for
byte against listSiteFiles, netlify.toml through the CLI's own serialization) with only kaizocore.html,
kaizocore/latest.json, the APK swap and the rc35 301 changed; the diff against live was exactly those paths. After it:
the audit tool answers with a business, 10 functions deployed, latest.json names versionCode 47, the page links the
rc35.1 APK twice, the site's APK and GitHub's hash to the build, the rc35 link 301s to /kaizocore, and the notes answer
200. The wiki was pushed (a8f09c8) with the rc35.1 notes and Home and IronMON-rules naming 1.0.0-rc35.1.

## Found on the way

- **/apps answers 200, and did before this deploy.** The Apps page went live in 6ac2ad1b (a CLI deploy at 19:46 UTC,
  three and a half minutes after rc35's 6ac2ac44), so the live copy this deploy was built from already held apps.html,
  and it shipped unchanged. Nothing in this release added it.
- On a GBA game in landscape, the d-pad's right arrow sits under the game picture and does not take a tap there.
  Not new in rc35.1; not looked into.
- The public snapshot still carries docs/research/roguemon.md (the research note) and the melonDS firmware source
  patch, both public since rc35. No RogueMon mode file, ROM, BIOS or key is in it.

## Not yet seen on a device

- The tracker's favorites row itself after an edit: it draws only before your first Pokemon, and run 791 has a party.
  Starting a new attempt to see it would have ended Blake's run. The save and the read back were checked.
- The stream's favorite pictures after an edit (unit tests only).
- The wide view and the see-through window on a DS game in a Kaizo run, and a double battle's side in the bar.
