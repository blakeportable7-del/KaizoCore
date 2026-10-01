# rc30 QA gate

Blake, 2026-09-29: "this needs to be perfect, or at least playable". Nothing ships until every
line below is checked on the emulator (AVD `ironmon`) for GB, GBA and DS. A feature that fails and
cannot be fixed the same day is switched off or held, not shipped.

Rules for running it: one adb driver at a time (no agent on the emulator while this runs). Staged
demo modes are fine; playing a real game past its title screen needs Blake's go first. Record each
result here with the commit it was run on.

## 0. Before the device

- [ ] Full unit suite green, counted from the JUnit XML (not the exit code alone).
- [ ] `./gradlew :app:assembleRelease`, then install the release APK over the last build. It must be
  an install over an existing one, so upgrade paths run (options, notes, attempts, heal counts kept).

## 1. Every platform: GB (Red, Crystal), GBA (Emerald, FireRed 1.0, Emerald Nat. Dex), DS (Platinum, Black 2)

- [ ] Prepare the dump, randomize with Kaizo, and open Play. The play screen must open. A VerifyError
  here means PlayScreen passed ART's limit; see memory note "PlayScreen verifier limit".
- [ ] The game boots to its title; the tracker panel attaches with no error line.
- [ ] Portrait and landscape both lay out, with no clipped tracker and no controls over the picture.
- [ ] Save a state, load it, undo the load. Then the auto slot: leave for 3 minutes, check it moved.
- [ ] Crash resume: `adb shell am force-stop com.ironmonone.app` mid-game, relaunch, and it returns to
  the run where it was.
- [ ] NEW RUN from Play: it takes about a second (the staged next run), the attempt goes up by one,
  the run's notes, encounters and Safari record clear, and the run history keeps the last run.

## 2. The tracker, from the staged states

`adb shell am start -n com.ironmonone.app/.MainActivity --es demo <mode>` with each mode:
gba-battle, gba-wild, gba-over, gb-battle, gb-over, nds-battle, nds-wild, nds-over.

- [ ] Battle cards: your Pokemon and the enemy, move rows, the carousel, notes, heals.
- [ ] Calc Atk opens from the last attack line on GBA only, never on GB or DS.
- [ ] DS: one Pokemon at a time; SEE FOE / SEE MINE swaps; the lock icon locks and the banner shows
  after the battle; the enemy card shows no HP; move effectiveness appears after the pause.
- [ ] Game over (the *-over modes): the popup covers the picture, the text is readable, and the death
  card reads right: LOST TO, badges and time, Best or NEW BEST. Share opens the chooser, and each
  button does what it says.
- [ ] The gear on each platform: Game Boy has no Calc Atk and no hidden-stats switch. DS shows its own
  run-over setting (four choices), Pokecenter heals, Experience bar and ACC/EVA. Each toggle takes
  effect and survives a restart.

## 3. Modes and settings

- [ ] Every mode listed on the Run tab for each game opens its rules text, and a mode with no rules
  text says so.
- [ ] Emerald and Crystal Kaizo: the 60% pre-pass is shown and switchable, and a seed's log shows
  trainer levels about x1.59 with it on.
- [ ] A Survival run switches the heal counter on at 10 (GBA heart, DS Pokecenter counter); Revival
  starts at 5.
- [ ] A Doubles settings file sets "Either of your first two faints"; the player can change it.

## 4. Logs

- [ ] `adb logcat -d | grep -E "FATAL EXCEPTION|ANR in|VerifyError|AndroidRuntime"` is empty for the
  whole session.
- [ ] No "Could not" status lines were seen during sections 1 to 3.

## 5. Credits and copy

- [ ] About names every author whose work ships: trackers (besteon and contributors, UTDZac, Sannji,
  seadogstingray, Brian0255), Calc Atk (UTDZac), Auto Pokemon Themes (Fellshadow), the randomizer
  (Ajarmar, Dabomstew), mode patches and settings (per NOTICE), cores and LibretroDroid (Swordfish90),
  the PMD Sprite Collab, and the Press Start 2P font.
- [ ] NOTICE lists the same, with licences.
- [ ] No em dashes in new user-facing strings.

## Results

| Section | Commit | Result | Notes |
|---|---|---|---|
| 1. Play screen opens | release builds of 9745981 and later | PASS | No VerifyError: DS (White 2 run) and GBA (Emerald) sessions composed on the release build |
| 2. Death card, staged | cec2a27 | PASS after 2 fixes | DS and GBA popups fit the picture at full size; Share opens the chooser with the run line. Fixed: the GBA stage showed the Game Boy sample; Share had no name for a screen reader |
| Second screen | 73561d3 | PASS | Emulator overlay display 720x1280/320: the tracker column on it at full width, the phone drops it and the pad moves down |
| Back to the run | e73c8a6 | FAIL, FIXED | Once a Library game was opened, nothing led back to the run but a new run (rc29 too). The Run tab now offers "Back to attempt N"; checked it opens the run and then goes away |
| DS core teardowns | b091dfb | PASS | Master: 0 crashes in 20 teardowns (2.5 GB guest). With the audio fix: 10 of 10 clean on a 4 GB guest, no ANR; the 11th met an emulator audioserver abort (TimeCheck on createTrack), a system service |
| DS memory | 9745981 | KNOWN ISSUE | White 2 Faster run (287 MB ROM): native heap 844 MB, PSS 984 MB. Clean White 2 (512 MB ROM) reached 1.45 GB and was OOM-killed on a 2.5 GB guest at its third load. Likely two copies of the ROM (LibretroDroid's and melonDS's) |
| Run codes | 4225792 | PASS after 2 fixes | Share made KC1-emerald-u-645c402a-6d79a408dc89d32a-1-575dd91a-RSE_Kaizo; pasting it rebuilt the run: "Same game as theirs: the checksum matches". An unknown game is refused by name. Fixed: the build question was off screen (now a dialog); a run from before run codes said nothing |
| Modes on the Run tab | 1a647350 build | PASS | Emerald: eight mode chips; the 60% switch off for Standard, on by default for the official Kaizo preset; "Using RSE Kaizo" |
| Emulator ANRs | several | WATCH | One 5 s Compose frame under host load (loop with a build running). An older build waited on GLSurfaceView's GL thread during a resize; a layout change while a DS ROM loads can freeze the UI |

Still to run: section 1's randomize, NEW RUN, save and load, crash resume and rotation per platform (Game Boy needs a dump added on the device); section 3; section 4 over a whole session; section 5 is done in code (CreditsTest).
