# rc32: the major upgrade

Blake, 2026-09-29: "no rc32 should be a major upgrade". Everything here ships together as rc32. Status as of
2026-09-29, late evening.

## In rc32

| Feature | What it is | Status |
|---|---|---|
| Tracker themes | The PC tracker's 15 presets (the DS tracker's own 15 on DS games), save your own, your own image behind the tracker with dim, see-through boxes and Fill/Fit | Done, merged. Emulator: GBA and DS presets, the floating window and a second display all checked. The second display is view only, so FILE gained TRACKER SETUP as the way back (fixed on the emulator) |
| FireRed/LeafGreen pictures | Bill Greenwald's (doctrDNA, IronMon Emu, used with permission) 11 dungeon guide maps and hidden-item pictures; a pin after the place's name opens a zoomable viewer | Done, merged, tested on the emulator (demo `gba-frlg`) |
| Home screen and main menu | Home, Play, Library, More; "Welcome to KaizoCore" on top (Blake); Continue card; Play any game, Kaizo IronMON, Nuzlocke, ROM Hacks; one-time welcome; crash-resume still reopens the game | Done, merged. Emulator at 411dp and at 360dp with font 1.3; every "Run tab"/"Hacks tab" line now names the Home buttons |
| Nuzlocke, Gen 3 | Presets (Standard, Hardcore, Randomizer, Monotype, Wedlocke, Genlocke-ready) with every clause a switch; first encounter, catch, death, whiteout detection; level caps at boss battles; graveyard and an editable ledger; a tracker panel | Done, merged, on the Home button. The ledger follows every tracker poll. Emulator: the screen, a start, and a scripted run (demo gba-nuz: encounter, catch, new route, death) through the panel and every ledger tab. Fixed there: "Map 0" on the title screen, names printed twice. Not yet seen: a real encounter in a played game |
| Build your game | Any starter from the game's dex, plain-words randomizer choices, saved as a settings file, All settings for experts | Done, merged ("Build your own" on the Kaizo IronMON screen). Emulator: Emerald from Kaizo with Pikachu, Mewtwo and Random saved and started as a run |
| Stream kit | The game's own picture (native resolution, lossless) and sound to OBS over Wi-Fi as browser sources, an importable OBS scene with game, tracker and attempts, a setup guide page; every mode | Done, merged. Emulator over adb forward: the guide, /game at about 54 pictures a second with sound, the tracker page, the OBS scene file, the token. Fixed there: the page showed ROM move powers (Return 1) instead of the phone's (102). Not yet seen in a real OBS |
| Play as your lead Pokemon, or your own sprite | SpriteIsMe (UTDZac, MIT) behaviour, drawn into the game picture natively so OBS gets it too; the trainer hidden through the game's own OAM buffer each frame; your own picture or sheets; one switch for every mode (Blake) | Helper building (feat/sprite-is-me) |
| Nuzlocke, Game Boy and DS | The same rules engine on the Gen 1, Gen 2 and DS trackers | Wave 2 |
| Smaller items | Custom game-over lines (UTDZac Death Quotes, MIT); the Black 2/White 2 tracker update from NDS tracker 6.3.11 (pointer at 0x24); career stats | Wave 2 |

## Researched for later (2026-09-29)

- RogueMon: yes with limits, XL (docs/research/roguemon.md). Phase 1 (pin the build, the config-block resolver, the
  randomizer spike, a state model) is being built switched off on feat/roguemon; it is not an rc32 feature.
- Kaizo modes on other ROM hacks: docs/research/kaizo-on-rom-hacks.md. First step is a generic "plays as the base
  game" gate for in-place patches (M); Radical Red and Unbound share one CFRU decoder (L, then M).

## Known issues still owed (from rc30 and rc31)

DS double battles are not tracked; Johto +7 Survival heals are counted by hand; Black 2/White 2 uses about 1 GB;
the DS core leaks a little memory on each load.

## Process

Each feature is built by a helper in its own worktree, reviewed and merged by the lead, then tested on the emulator
(cold boot, `-memory 4096`). The full suite runs uncached before the release build. Research is in docs/research/.
