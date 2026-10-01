# rc32 QA (2026-09-29 to 2026-10-01, shipped)

## Shipped (2026-10-01)

GitHub pre-release v1.0.0-rc32 (tag on build commit 87fad45c), site deploy 6abe19c825a42024412adca1 (rollback:
6abc3bdbb03f758db35c6a2a, rc31's). APK 131,274,148 bytes, SHA-256
4d50c3e50cfaf82053385d941c49f5e48b9f2a7c41e0e8d106cbeb700ebc3c4a. Blake: "Push it live, update everything and the
wiki."

The release check on that exact APK, on the emulator (AVD ironmon, Android 14):

| Check | Result |
|---|---|
| Installs over the earlier rc32 test build, data kept | pass |
| The launcher shows the new icon, the cracked ball on white | pass |
| Game Boy: the Yellow run opens in Play | pass |
| Game Boy Advance: Emerald from the library opens in Play | pass |
| DS: HeartGold from the library opens in Play | pass |
| NEW RUN on the Yellow run: seed b567374a149bf613, recipe stamped 1.0.0-rc32+87fad45c, attempt 3 | pass |
| No crash, ANR or VerifyError in logcat at any step (PlayScreen sits at the verifier's limit) | pass |
| Check now against the live latest.json | "You have the newest build." |

Before the deploy the built site was compared file by file with the live one: six differences, all the release's
(the rc32 APK in and rc31's out, kaizocore.html, latest.json, netlify.toml's 301 for rc31, sitemap.xml's dates). The
audit check passed against the live site first. After the deploy: the audit tool answers 80, C, 18 checks (as before),
10 functions deployed, latest.json names rc32 (versionCode 41), the downloaded APK hashes to 4d50c3e5, the rc31 link
301s to the page, home and /audit answer 200, and the release notes link answers 200. The GitHub wiki was pushed
(b81f544): rc32 is the current download, and its release notes list everything new with the counts.

Not shipped yet. This records what has been checked so far; the release section is filled in when it ships.

## What rc32 adds

- **Home and a main menu:** Home, Play, Library and More tabs. "Welcome to KaizoCore" sits on top, with a
  Continue card and four modes: Play any game, Kaizo IronMON, Nuzlocke and ROM Hacks. The welcome screen shows
  once.
- **Nuzlocke for Gen 3:** presets with every clause as a switch. A ledger the tracker keeps, with a panel and
  Areas, Team, Graveyard, Log and Rules tabs, all editable by hand.
- **Build your own:** any starter, plus plain-word randomizer choices, saved as a settings file.
- **Stream kit:** the game's own picture and sound to OBS over Wi-Fi, an OBS scene, and a setup guide. It works
  in every mode.
- **Tracker themes:** presets (the PC tracker's, and the DS tracker's on DS games), your own image behind the
  tracker, see-through boxes.
- **FireRed and LeafGreen:** dungeon maps and hidden-item pictures (Bill Greenwald / doctrDNA, with permission).
- Still being built: play as your Pokemon or your own sprite, Nuzlocke for Game Boy and DS, death quotes,
  the Black 2/White 2 address update, career stats.

## Automated

- App module after the Home, Build your own, Nuzlocke and Stream kit merges: 903 tests, 0 failures, uncached.
  tracker-gba: 430, 0 failures.
- Full uncached suite across all modules: see below once it finishes.

## On the emulator (AVD ironmon, Android 14, release builds)

| Check | Result |
|---|---|
| Welcome once; Look around lands on Home; flag written on answer | pass |
| Home at 411dp, and at 360dp with font scale 1.3: title on one line, everything reachable | pass |
| "Welcome to KaizoCore" on top of the menu (Blake) | pass |
| Each mode button and Back: Kaizo IronMON, ROM Hacks, Nuzlocke (top-bar arrow and system Back), Play any game to All files | pass |
| Continue card opens the game it names (heartgold-u) | pass |
| Build your own: Start from Kaizo, starters Pikachu (by name), Mewtwo (by number 150), Random; world and Pokemon pages; saved as "RSE Kaizo (my build)"; Start this game asked first, then made the run (lastrun names the file) | pass |
| Nuzlocke: screen, presets, Rules in detail, the existing-save warning, Start on emerald-u, panel on Play | pass |
| Nuzlocke staged run (demo gba-nuz): Route 101 open; Zigzagoon caught; Route 102; Treecko dies to a wild Wurmple; battle won; counts 1 alive 1 dead; nickname warning; Areas, Grave and Log tabs | pass |
| Stream kit over adb forward: setup guide; /game at about 54 pictures a second with sound 70 ms ahead; tracker page; OBS scene file (three sources, game audio to OBS); /attempts empty outside a run; a wrong or missing token gets 403 | pass |
| Tracker themes on a DS game (HeartGold): the DS tracker's 15 presets with previews; Chalkboard applied | pass |
| Floating tracker window in landscape takes the theme (Fire Red) | pass |
| Second display (overlay 1280x720): the tracker moves there | pass, after the fix below |

## Found and fixed during QA

- **The second screen had no way back:** it is view only, so with the tracker there, the phone had no way into
  the tracker's setup, not even to turn the second screen off. FILE now has TRACKER SETUP.
- **Empty tracker line cut off:** the GBA tracker's "No Pokemon yet" line did not wrap and was cut off on the
  second display.
- **Build your own copy:** the builder still named "the Run tab" in three places, and its My picks hint promised
  Random slots that start as the game's own.
- **Nuzlocke panel said "Map 0":** it showed "Map 0: first encounter open" on the title screen.
- **Nuzlocke names printed twice:** the ledger printed "Treecko (Treecko Lv 7)". On a real game the default
  nickname is the species name in capitals, so it would have read "ZIGZAGOON (Zigzagoon Lv 3)".
- **Stream page move power:** the stream tracker page showed each move's ROM power (Return 1, Weather Ball 50 in
  the rain) instead of the phone's (102, 100 Water). It now also hides a randomized opponent's move facts the
  way the phone does.

## After the UX audit and the IronMON rules check (2026-09-30)

Debug builds from the day's commits on the emulator, driven by script; the last walk was on 572a1cd.

| Check | Result |
|---|---|
| No crash and no VerifyError opening a run in Play (PlayScreen sits at the verifier's limit) | pass |
| Library opens on My games; Kaizo IronMON opens on Kaizo with a line and "Read all the rules" | pass |
| Tracker Setup opens on "Game is considered over when"; the guide maps switch is there | pass |
| An unpatched Crystal says it needs the growth patch; "Make the patched copy" makes it, picks it and keeps the mode | pass |
| Nuzlocke, Randomizer, a game picked: "How random" opens on Nuzlocke fair | pass |
| Leaving a game writes the left mark (a library game's under saves/lib/); coming back says "Back where you left off", and the mark is used once | pass |
| No GAME OVER popup in a Nuzlocke (the panel's card is covered by tests, not yet seen on the emulator) | pass |
| A library game with no Nuzlocke (Red) offers Rewind and Cheats; the Nuzlocke game (Emerald) has no Rewind and "Cheats off" | pass |

Each rules-check guard was also broken on purpose, one at a time, and its test went red: 20 of 20.

## The AYN Thor freeze (2026-09-30)

A player's report: ANR on an AYN Thor (two screens, Android 13), rc31, a Ruby/Sapphire/Emerald game, "Input
dispatching timed out (Application does not have a focused window)". The report kept only the dump thread.

Proved with the debug build's test port (tools/kcbot/freeze_proof.py): hold the emulation thread for 8 seconds,
tap File > Save state straight after, tap the screen again while it waits.

| Build | Result |
|---|---|
| Fixed (bounded waits) | no ANR; the app gave up on the emulation thread twice; the game ran on afterwards |
| Control (only the old unbounded wait put back) | `ANR in com.ironmonone.app`, and the "KaizoCore isn't responding" dialog |

Also: `density` in configChanges (moving the app to the Thor's other screen no longer rebuilds it mid-game), and an
ANR's report now keeps the main and emulation threads' stacks (FreezeFixesTest, from a trace in the real format,
where the main thread follows "DALVIK THREADS" with no blank line).

## Not yet seen

- A real encounter, catch and death in a played game. The Nuzlocke engine's 160 unit tests and the scripted demo
  cover the logic.
- A real OBS on a PC over Wi-Fi.
- Sound through OBS.
