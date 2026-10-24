# APK follow-ups

Blake, 2026-10-10: "we are only working on kaizo core pc right now, the apk can be dealt with later, keep a tab on what to
update on future apk work". Every change made for KaizoCore PC that the Android app will also need goes here, with why.
Strike a line through (or delete it) once the APK has it.

## Status in apk/rc39 (2026-10-23)

Blake, 2026-10-23: "i need the apk version to have all the fixes of the pc", "the apk shouldn't contain the easter eggs
that are only for qwert". Branch apk/rc39 (from feat/pc-tracker-anim + feat/pc-tracker + master's docs). Each item below:

- **Heart & Soul build: done, split.** One source, two builds (patch 0061, KAIZOCORE_CLIENT_EGGS): the app ships
  F5F37A8E (no client easter eggs) from app/src/main/assets, KaizoCore PC keeps F4E28E3D from desktop/assets. Shared
  tracker reads both (HnsLayout.pcBuild). docs/HNS-KAIZO.md "One source, two builds".
- **CRC and layout: done.** RomKind expectedCrc F5F37A8E, F4E28E3D superseded for the app (Move this run proved on the
  emulator: an attempt-11 run moved onto F5F37A8E and tracked).
- **Move categories by type: done** (TrackerPanel, both cards).
- **Challenge menu rows: done.** The HnS rules pages say the rows are fixed; no app screen explains them otherwise.
  EV caps: no app text or tracker math assumes 252 (checked), not applicable.
- **Weather marks: done** (PcWeatherGlyph beside Pow on the phone's move rows).
- **Hit chance and crit odds: not applicable.** The phone draws no hit chance; HitChance is shared and follows the
  build's scalars if it ever does.
- **Animations: not applicable.** The phone's TrackerGearDialog does not show the two toggles (PC only).
- **Per-battler ability reveals:** shared code; the APK's reveals still go through RetroTriggerTap. Not seen in a
  battle on the emulator this round (no battle reached); check on a device in QA.
- **Stat marks: done** (theme colors in Notebook and the DS extras; one minus sign was already shared).
- **PC-only screens: done for the ones the phone lacked:** level-up moves (MovesLearnedDialog), held item info,
  resistances with type icons, evolution odds sorted by Name/BST/Percent with BST and Heart & Soul odds (1,000 seeds a
  pool). The rest of PcScreens already had phone equivalents.

Player feedback:

- **Continue per game: done** (PrepStore parkRun/continueRun; proved both ways on the emulator, FireRed and Heart &
  Soul). Nuzlocke runs are not set aside (their ledger is its own store): unchanged behavior.
- **Stream: done in code and JVM tests** (nature marks on the card; logOpen drives the run-over view). Not proved on the
  emulator: the stream server did not answer over adb forward in this session.
- **D-pad slide: done** (DpadSlide; proved on the emulator, one touch from Down to Left walked down then left).
- **Fast forward in emulator settings: done** (proved on the emulator).
- **DS on two screens (AYN Thor): not built.** Needs native work in libretrodroid: the core draws both screens into one
  frame on one GL surface, so the bottom screen needs a second EGL window surface (a Presentation on the second display
  holding a SurfaceView) drawn from the same frame, and its touches mapped to the DS touch screen. SecondScreen.kt
  already puts the tracker on a second display; that is the hook. Needs a two-screen device to prove (an emulator
  overlay display can show the picture, not the Thor's touch).
- **Black 2 / White 2 Challenge Mode: done** (Blake, 2026-10-23: official, not custom). A switch per game and settings
  file on the Kaizo IronMON screen (ExtraPasses.takesChallenge); on, the run is made from a copy of the file under its own
  name with engine-zx's FORCE_CHALLENGE_MODE set (prep/challenge/), the recipe moves with it, and the record's variant
  says Challenge Mode. ChallengeModeTest.
- **Season change: not built.** The game takes the season from the DS clock's month; melonDS's libretro options in
  CoreOptions have no clock setting, so it needs an RTC offset option in the core.
- **Platinum patched ROM:** no fault found in the patch path (xdelta streams; BPS/UPS/IPS are applied in memory with
  largeHeap). Ask VILLAIN: which patch (name and version) and its file type, his Platinum's revision or CRC (USA rev 0 is
  9253921D), and what happens (an error when patching and its words, the Library calling it another version, or the
  game failing to boot).
- **RogueMon:** still tabled, nothing done.

## Heart & Soul on Nat. Dex Emerald's Gen 3 battle rules (patch 0047, 2026-10-10)

The client asked for Heart & Soul Nat. Dex Kaizo to play Nat. Dex Emerald's Gen 3 mechanics (moves, abilities, battle
rules) with Heart & Soul's larger Pokemon pool.

- **The patch file is shared.** `app/src/main/assets/patches/hns-kaizo-heartsoul-206.bps` is the patch both the PC jar
  and the APK apply. Rebuilding it for the Gen 3 build changes the APK's Heart & Soul too the next time the APK is built
  from this branch. Ship the APK with the matching tracker (the items below) or it will track a ROM whose rules it does
  not know.
- **The build's CRC moved to F4E28E3D** (was AA092306, then 1C824440, 2026-10-11) in shared code: `Generation.kt`
  (HEARTSOUL_KAIZO_206.expectedCrc, 1C824440, CDC719C2 and 09405F53 now in supersededCrcs), `HnsEngine.BUILDS`, and the app's own tests. An APK built from this branch recognizes only the new
  build, so it must ship with the new BPS and layout together. Patches 0049-0051 (debug out, the Kaizo compliance
  fixes, Gen 3 move behavior) change what the APK's players get too.
- **The layout is shared.** `tracker-gba/.../HnsLayout.kt` (generated from `tools/hns/layout.py` and
  `gen_tracker.py`) carries the new build's CRC, addresses and the battle-rule scalars added for this build
  (`B_PHYSICAL_SPECIAL_SPLIT`, `B_CRIT_CHANCE`, `B_CRIT_MULTIPLIER`, `B_BLIZZARD_HAIL`, `B_TOXIC_NEVER_MISS`,
  `B_HIDDEN_POWER_DMG`, `B_UPDATED_MOVE_DATA`, `I_TYPE_BOOST_POWER`, `I_SITRUS_BERRY_HEAL`, `KAIZOCORE_GEN3_BATTLE`,
  `B_ATE_MULTIPLIER`). BattleMoveTypes.ateBoosted now follows `B_ATE_MULTIPLIER` (x1.3 and no Normalize boost on this build).
- **Move categories by type.** `HnsRules.categoryByType` (Hns.kt) and `GbaTracker.categoryOf` now give Gen 3's
  physical/special rule on this build. The PC cards pass `ownCategories = hns && !HnsRules.categoryByType`
  (PcBesteon.kt, PcSnapshot.kt) so Hidden Power and Weather Ball follow their type. **APK:** `TrackerPanel.kt` (two
  `.copy(ownCategories = hnsGame, ...)` calls, around lines 909 and 948) still passes `hnsGame`; give it the same
  `&& !HnsRules.categoryByType`.
- **Challenge menu rows greyed.** Synchronize, Sturdy, New Citrus, Split and Gen 1 recharge are pinned to their Gen 3
  values in the ROM, and Survive Poison is pinned ON (overworld poison stops at 1 HP) (Blake, 2026-10-10: "default to the Kaizo Emerald National Dex rules when my client
  starts the game and plays it on BizHawk"). Any APK screen or help text that explains those options for Heart & Soul
  should say they are fixed. Vitamins stop at 100 EVs, a stat holds 255, and legendaries get no guaranteed perfect IVs:
  any APK text or tracker math that assumes 252 needs the same.

## Tracker card work prototyped for PC (wt-pc-anim copy, not merged yet)

- **Weather marks on the move column** (MoveDecor.kt `PcWeatherGlyph`, `MoveRules.weatherMark`). MoveDecor is shared, so
  the APK's card will need the glyph drawn in its own layout.
- **Hit chance and crit odds** (HitChance.kt): on the Gen 3 build these follow the build's scalars (1/16 base crit, x2
  damage, Blizzard can miss in hail, Toxic can miss). The APK shows the same numbers once it draws them.
- **Battle and level-up animations** (desktop `fx/`): PC only today. The two Settings toggles ("Battle animations",
  "Level-up and item animations") live in `TrackerOptions`, which the APK shares; the APK needs its own drawing or the
  toggles hidden there.
- **Per-battler ability reveals** (GbaTracker `battleShown`): shared tracker code, so the APK gets the fence when it is
  built from this branch. Only the PC session calls `notePopup` (the BizHawk Lua's pop-up tap, now with `pid=`); check
  the APK's own reveals still reach `battleShown` on a device before shipping.

## Player feedback for the APK (Discord and the site's idea form, 2026-10-09 to 2026-10-20)

Collected 2026-10-23 from Blake's paste. None of it is built yet; the APK waits ([[pc-only-track-apk]]).

- **Evolution odds screen** (VILLAIN, 10/10): "what different evos a mon can get" and how likely, as besteon's PC
  tracker shows it. Blake said it would come to the APK with the resistances card. Port the PC's EvoData screen.
- **Resistances card and the other PC-only screens** (Blake, 10/10): bring the PC tracker's missing screens to the APK.
- **Stream to PC: the log viewer is not shown on stream** (VILLAIN, 10/10): after a game over only the cause and place
  showed, not the log. He may not have opened it ("Randomized data"); check that opening the log is sent to OBS.
- **Stream to PC: the nature is missing** on the streamed tracker (VILLAIN, 10/10: his -SpA +SpD was not shown).
- **Continue a run in another game** (VILLAIN, 10/12, called it "major"): Continue attempt only offers the last game
  played. A FireRed run saved mid-game cannot be continued after playing HeartGold. Each game needs its own Continue.
- **Drag to move on the d-pad** (The Other Toby, 10/10): slide a finger from one direction to the next without lifting,
  instead of a separate tap and hold for each.
- **Fast forward is hard to find** (VILLAIN, 10/10): it is under File > Speed; he looked in the emulator settings
  first. Add it there too, or point to it.
- **Platinum patched ROM does not work** (VILLAIN, 10/20): no details yet; ask which patch and what happens.
- **DS on two screens on an AYN Thor** (el borracho, 10/20): only one screen could be set up. Dual-screen devices need
  the bottom screen on the second display.
- **Black 2 / White 2: force Challenge Mode** as a randomizer setting, and **a way to change the season** (site idea
  form, 2026-10-20, "Force Challenge Mode and Change Season"; the sender left an email for an answer, kept in the form
  inbox, not here).
- **RogueMon on Android** (The Other Toby, 10/9): still tabled until its creators give permission
  ([[roguemon-permission-rule]]); Blake told him so 10/10.
