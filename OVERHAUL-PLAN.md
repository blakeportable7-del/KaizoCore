# KaizoCore - Overhaul Plan

Requested 2026-09-01. This is the plan, not the work. Nothing below is built.

Every item carries one of three tags, and the tag is the point:

- **CAUSE KNOWN** - I can name the defect in the code today and cite it.
- **HYPOTHESIS** - I have a specific suspect but have not proven it. First
  step is a measurement that can come back "no".
- **RESEARCH** - it needs a reference we do not have yet. Under the standing
  rule (the tracker is a clone, not a design surface) these cannot start until
  the reference is in `~/ironmon-ref/`.

Effort is in honest sizes: S = an afternoon, M = a few days, L = a week or
more, XL = a milestone on its own.

---

## 0. The architecture the rest of this needs

**Progress 2026-09-01.** 0.1 and 0.3 have a first slice in; 0.2 is not started.

- 0.1: `Platform` (core, ROM extension, turbo cap) and `Engine` live in
  `core-api`; `Generation` carries its platform; `RomKind` gains `platform`,
  `engine`, `family` and a `byId` registry. PlayScreen's core file, turbo cap
  and engine choice, and RunScreen's engine choice, all read the descriptor -
  the two copies of "if Nat. Dex then the fork" are one `Randomizers` call.
  Correction to the estimate below: `isNds` was in 28 places, not ~40; 24
  remain and every one of them is a rendering branch (which panel, which pad),
  which is 0.2's job.
- 0.3, differently than planned: no catalogue file. Modes are DERIVED from the
  preset files, because every preset name already carries game tag + Nat. Dex
  + ruleset (RnqsInfo, validated against 72 real files). `RulesetCatalog`
  offers, for the selected ROM, one mode per ruleset that has a compatible
  preset; the RUN tab shows them as a MODE row above Settings and picking one
  selects its preset. Import a Survival preset and Survival appears. Nothing
  is ever offered that the pairing guard would refuse. 196 tests green.
- 0.2, done the same day: `RunView` (in tracker-gba, which both trackers
  see) answers the screen's four questions - in battle, wild, opponent id,
  outcome - and both `TrackerState` and `NdsTrackerState` implement it.
  PlayScreen reads one `view`; the flee gate, encounter counting, the battle-
  end lockout and the wild/trainer decision all lost their console branch.
  Save ownership became a platform fact (`coreOwnsSaves`). `isNds` no longer
  exists in PlayScreen. What remains is `dsScreens`, ten uses, every one of
  them the DS's second screen or touch layer - which panel, which pad, the
  layout chip - and the comment on it says that is all it may ever gate. A
  Gen 1 or Gen 5 tracker joins by implementing RunView; the screen does not
  change. 201 tests green, verified on the emulator.
- Still to do here: the rules text per mode (2.4), which hangs off `Ruleset`.

Today the app is one Gen 3 tracker with a Gen 4 sidecar bolted on, one game
kind per engine, and five hardcoded Kaizo presets. Item 12 (every IronMON game
and every ruleset) does not fit in that shape, and neither do a floating
tracker or a game-over screen that needs the final team. So the first
milestone is structural, and everything after it gets cheaper.

### 0.1 A `Game` descriptor instead of `isNds` branches

```
Game(
  id,                // "frlg-natdex-121", "hgss", "crystal"
  platform,          // GB, GBC, GBA, NDS
  generation,        // 1..5
  core,              // gambatte / mgba / melonds (libretro core name)
  engine,            // which randomizer fork owns it
  memoryMap,         // resolved per ROM, as today
  tracker,           // Gen1Tracker / Gen3Tracker / Gen4Tracker / Gen5Tracker
  rulesets           // the IronMON modes that exist for it
)
```

`PlayScreen` currently branches on `isNds` in roughly forty places. Each of
those is a place a third platform would need a third branch. The descriptor
replaces the branches with one lookup, and the tracker column renders whatever
`tracker` produces through one `TrackerState` interface.

Effort: **L**. It touches everything, which is why it goes first, not last.

### 0.2 One `TrackerState`, many producers

`GbaTracker` and `NdsTracker` already emit similar shapes. Formalise it:
party, enemy, inBattle/isWild, enemyTeam, route, badges, heals, gameOver,
statusCondition. Gen 1/2/5 producers plug in without the panel changing.

### 0.3 Rulesets as data, not code

A ruleset is: a display name, the game(s) it applies to, a randomizer preset
file (`.rnqs`), and tracker flags (e.g. Survival's healing rules). Ship them as
`assets/rulesets/<game>/<mode>.json` + the `.rnqs`. The run flow gains one
step: choose game -> choose mode -> randomize. Effort **M** once 0.1 exists.

Redistribution note: the official mode presets are community-published
settings files. Confirm each one's source and terms before bundling, the same
way NOTICE item 7 already covers the Nat. Dex assets.

---

## 1. Bugs

### 1.1 `[SLP]` next to a freshly met opponent - **FIXED 2026-09-01**

Outcome: the cause was close to the guess but not the guess. Neither
reference reads status out of `gBattleMons` at all - the PC tracker takes it
from the enemy PARTY struct, whose status offset the ROM publishes and we
already decode with. The `0x4C` read was this app's own invention. Status now
comes from the matching enemy-party slot (species + level, HP as tiebreak),
and no match renders blank rather than a guess. `BattleBugFixesTest` pins it,
and with the old read put back all three status tests fail.

Original analysis, kept for the record:

Only ever SLP, only on first sight. Sleep is the low three bits of `status1`,
so any garbage in bits 0-2 renders as SLP - it is the one condition random
bytes fake.

We just found `sizeofBattlePokemon` is 92 on Nat. Dex, not 88, and read the
opponent four bytes early for weeks. The status byte is read at a fixed
`0x4C` inside the same struct. If the extra four bytes sit before it, the
"status" we render is part of some other field. The ROM publishes the
struct's geometry (`offsetBattlePokemonStatStages` at `0x080003f6`, types at
`0x080003f8`, and the comment at `GbaTracker.kt:157` already mentions a
published `offsetBattleMonsStatus2`), so status1's true offset is either
published or derivable from status2.

Fix: read the status offset from the slot table like the stride, with the
vanilla `0x4C` as the fallback. Test: pin the published value against the
shipped ROM (same shape as `save block offsets are read from the ROM`).
Effort **S**.

### 1.2 Route encounter list appears 1/3, 2/3, 3/3 of the time - **HYPOTHESIS, instrumented + debounced 2026-09-01**

Shipped: the map id must now be seen on two consecutive polls before it is
adopted, so a mid-transition blip can no longer reset the panel (tested). And
a **ROUTE LOG** button in the FILE menu shares the adopted transitions, so if
it still happens the log names the cause instead of us guessing. If suspect 1
was right, the debounce fixed it; if it recurs, send the log.

Original analysis:

The panel keys on `mapId`, read from `mapHeader + 0x12` through an EWRAM
pointer every poll (`GbaTracker.kt:1029`). Suspects, in order:

1. The pointer is mid-transition during a map load and reads a different (or
   zero) map for one poll, and the panel resets on every mapId change - so it
   flickers rather than holds.
2. `routes[mapId]` has gaps for some maps, so it is right that nothing shows
   for those, and "sometimes" is really "on some routes".
3. The seen-count is right but the LIST is being clipped by the column (we
   have seen the tracker column clip before).

Step one is a log, not a fix: record `(poll, mapId, routeName, listSize)` for
five minutes of walking and look at it. Fix follows the log. Effort **S-M**.

### 1.3 B-mashing walks the player after a battle - **FIXED 2026-09-01**

Shipped exactly as below: `GbaTracker.isChoosingActionInWild()` reads
`gBattleMainFunc` live at each tap and only passes on
`HandleTurnActionSelectionState` in a wild battle with the outcome still 0,
plus a 400 ms lockout after any battle ends. Tested, including "the menu
closes and the very next call is false, no poll required".

Original analysis:

Already narrowed once: `flee()` re-checks "still a wild battle" before every
synthetic tap. It is not enough, because that check reads `trackerState`,
which lags reality by one poll (250-700ms). Mash B in that window and the
RIGHT/DOWN/A burst still lands in the overworld.

Proper gate: the reference's own action-selection state. Fire only when
`gBattleMainFunc == HandleTurnActionSelectionState`, read LIVE from memory at
the moment of the tap, not from the cached state. That is "the battle menu is
open right now", which is the only moment the sequence makes sense. Add a
300ms lockout after any battle ends so a queued press cannot slip through.
Effort **S**.

---

## 2. Play screen

### 2.1 Controls: match the most-downloaded emulators - **DONE 2026-09-07 (DS portrait waits on a screenshot)**

Ranking read off the Play Store pages on 2026-09-07 (install band, last
update): GBA: My Boy! Lite com.fastemulator.gbafree 50M+ (2019) and My Boy!
com.fastemulator.gba 1M+ (Aug 2026), then John GBAC, Pizza Boy, My Retro
Gameboy and 90s Retro Games, all 1M+. DS: DraStic is gone from the store
(404); SuperNDS com.supernds.free 5M+ (Aug 2026), melonDS 1M+ (May 2026).

GBA: EDIT LAYOUT has a MY BOY LAYOUT chip that applies PadLayout.myBoy for
the orientation and the new OUTLINE skin (outlined circles, pills and a
square-arm cross on a clear ground, this app's own drawing). Positions were
measured off My Boy's store screenshots; PadLayoutTest holds them inside
the area and apart. Seen on the emulator in both orientations.

DS: Blake sent a landscape screenshot of SuperNDS on 2026-09-07. EDIT
LAYOUT on a DS game in landscape has a SUPERNDS LAYOUT chip that applies
PadLayout.SUPERNDS_LANDSCAPE (screens side by side, cross low left,
X Y A B diamond right, L and R pills in the top corners, START and SELECT
along the bottom) with the OUTLINE skin. The pad now has X and Y elements:
only DS layouts place them, so GBA and GB never draw them, and the DS
defaults carry them in the console's diamond. Portrait keeps the DS default
until a portrait screenshot arrives.

The 0xB70 chain was seen resolving on Blake's Diamond dump on the emulator:
"party base 0x0226D558 - reading live memory" before the first Pokemon. A
battle read still waits on DS touch (below).

**Open, found the same day: DS stylus touch is dead in both orientations**
on the current build. The Compose layer that maps touches to the core's
pointer never fires (a diagnostic log line in it printed nothing), so no DS
game can get past a "touch the screen" prompt. Diamond's intro needs one.
This blocks any DS run for a player and is the next thing to fix.

Found on the way: in DS landscape every chip in the strip and every pad
button over the bottom screen was dead. The stylus layer was composed on
top of the controls and Compose hands a touch to the topmost target only,
so nothing ever "fell through" to them. It now sits under the pad and the
strip. Seen on the emulator (Black 2): the strip opens the editor, the
chip applies the layout. 424 tests green.

Original note:

Target, per your call: the layout and look of the most-downloaded GBA
emulator on Google Play for GBA titles, and the most-downloaded DS emulator
for DS titles. Step one is to identify both from current Play Store install
counts on the day the work starts - I am not naming them from memory,
rankings move. Step two is to match their LAYOUT (button placement, sizes,
D-pad shape, where the DS touch screen sits) with our own drawing, using the
openly licensed overlay sets already vetted (`libretro/common-overlays`,
CC-BY-4.0). Their actual artwork is proprietary and is not copied. This is
skin, not logic: the pad's hit-testing stays, only the drawing changes.
Attribution goes in NOTICE. Effort **M**, mostly asset work.

### 2.2 Floating, movable, resizable tracker - **DONE 2026-09-06**

The tracker gear's "Landscape tracker" choice: docked beside the game
(as before), a floating window over the game, or hidden with the edge tab
to bring it back. FloatingTracker.kt is one Box: the title strip drags it,
the corner grip resizes it, a double tap on the strip puts it back where
the dock would be, DOCK on the strip returns to the dock, and it never
leaves the window. The frame is kept per game beside speed, mute and the
dock fraction (GameSettings floatFrame). The docked column's children were
lifted into one trackerContent so both layouts draw the same thing.
GameSettingsTest and TrackerOptionsTest cover the persistence.

Original note:

Landscape options: docked right (today), floating window, or hidden with the
game full-screen. The floating window is one draggable `Box` with a resize
grip, its position and size persisted the way `playspeed.txt` is. The
existing drag handle already resizes the dock; this generalises it. The
double-tap reset added this week carries over.

**Portrait tracker missing - FIXED 2026-09-05.** Your screenshot showed it:
the pad's bottom row was clipped off the screen too, so the column was
overflowing, and the tracker (which took "whatever was left") got nothing.
The emulator is taller relative to its width than your phone, which is why I
never saw it; reproduced at 1080x1700. Fix is `PortraitBudget`, a pure,
tested function: the pad scales down to a 55% floor first, and only if the
tracker still cannot have 140dp does the game shrink, centred, to a floor of
half the width. Verified at three shapes - short (1080x1700), a Fold-style
cover screen (904x2316, unchanged: small game, big tracker, full pad) and
normal (1080x2340, pad loses ~4%). The pad's labels scale with it, or SELECT
read SELEC at the floor. 211 tests green.

**Then rejected, rightly:** shrinking every button was the wrong answer when
the band was four rows tall with an empty middle. The pad is now ONE band,
three buttons tall (192dp, was 262): L and R in the D-pad's unused top
corners, SELECT/START stacked in the middle, A/B on the console's diagonal;
HIDE PAD moved into the FILE menu. Result: a normal screen shrinks nothing at
all, a Fold cover screen scales only to its width (0.93), and a test built
from your phone's play area keeps buttons above 85%. 213 tests green.

Seen and not fixed: the black bars INSIDE the game box on the emulator are
the core's own aspect handling and predate this; your phone shows the same
thing as a band on the right. Separate item.

### 2.3 Game-over popup - **DONE 2026-09-06, REBUILT 2026-09-07 AS THE PC SCREEN**

Blake, 2026-09-07, with a shot of the PC tracker's box: "the pc tracker for
every game will be integrated into the popup", a New game button that rolls a
new seed, and "Inspect the log" opening the log full screen. Researched per
title first:

| Tracker | Screen | Actions |
|---|---|---|
| Gen 3 (Ironmon-Tracker GameOverScreen.lua) | "G a m e O v e r", Attempt, team icon that cycles and re-rolls one of 21 announcer quotes, CONGRATULATIONS!! on a win | Continue playing, Retry the battle ("Are you sure?" first), Save this attempt, Grade my notes, Inspect the log |
| Gen 1 and Gen 2 (gen-tracker, gen-2-tracker) | one older copy of the same file, byte-identical between the two | the same four without Grade my notes |
| DS (NDS-Ironmon-Tracker RunOverScreen.lua) | no game-over screen: a run-over box whose line is picked by CAUSE (won, Shedinja, Imposter, an enemy 100+ BST below you, else the twelve standard lines) | Dismiss, Open log |

GameOverDialog takes a GameOverFamily (GEN12, GEN3, DS) and shows that
tracker's title and quote source with one row of actions for all:
Continue playing; Retry the battle, from a save state PlayScreen takes when
a battle begins (the reference's createTempSaveState), hidden when none was
taken or the run was won; Save this attempt (PrepStore.saveAttempt: the
run's ROM, its log, a state and the per-run notes into
files/attempts/<game>-attempt<N>-<seed>/, never overwriting); Inspect the
log ("Open the log" on DS); New game (new seed), the existing NEW RUN path.
Also 2026-09-07, Blake: "find a way for the user to give me bug information
within the app. email blake@willowcreek.group". The INFO tab's feedback box
is REPORT A BUG now: EMAIL BLAKE composes a mail to that address
(Feedback.emailIntent, ACTION_SENDTO mailto) with the device, versions,
game family, the last crash report if CrashLog kept one, and the log tail;
SHARE INSTEAD is the old share-sheet path for a phone with no mail app.
The site's download: a direct APK link in the hero and a DOWNLOAD THE BETA
section with size, SHA-256 and the three install steps; the APK is served
from the site itself (Saturday/kaizocore/) so it works before the GitHub
release is published.

Grade my notes is not ported yet. Blake, later the same day: an X in the top
right of the popup and of the full-screen log; the X on the log drops back to
the popup, and the X on the popup is Continue playing. Seen on the emulator
in the staged Emerald mode (which reads a demo.log from the app's external
files dir and offers Retry so every action shows): popup, log, a species
opened from it, X back to the popup.

The log itself did not exist before: both randomize paths discarded the
engine's log text. Randomizers.randomize now writes it beside the run ROM
(current.<ext>.log, rotated with previous), RandomizerLog.kt parses it the
way the reference's data/RandomizerLog.lua does (checked against a log of
each family from the bundled engine: Gen 1 has five stats, Gen 2 no
abilities, Gen 5 three), and LogViewer.kt is the full-screen viewer with
the reference's five tabs: Pokemon (tap one for stats, abilities, moves,
evolutions, TMs), Trainers, Routes, TMs, Misc. 112 app tests green, six of
them on the parser fixtures in app/src/test/resources/logs/.

Earlier note (2026-09-06):

GameOverDialog.kt: the reference's GameOverScreen as a dialog over the
game. Top box: the team icon (tap cycles the team and rolls a new announcer
quote), GAME OVER, Attempt, the quote or CONGRATULATIONS!!. Bottom box:
the final team, then CONTINUE PLAYING (the run stays as it is) and NEW RUN
(the existing confirmation). Shown once per outcome, on every generation
through RunView.outcome. BizHawk-only retry, save and notes-grading
buttons not ported. Seen on Emerald and Black 2 through the staged modes.

Original note:

`readGameOver()` already returns WON / LOST (`GbaTracker.kt:1351`). Nothing
listens to it. Add a centred dialog: attempt number, the final team as six
sprites with names and levels, and two buttons - NEW RUN (the existing path)
and CONTINUE. Reference: `screens/GameOverScreen.lua`, which is exactly this
and even carries the announcer quotes.

### 2.4 The RULES button - **DONE 2026-09-06**

RULES in the FILE strip and in the tracker gear opens RulesDialog: the
rules for the game family being played, the run's own mode preselected
when it is a run, tabs for the other modes. The text is generated, not
written: tools/rules/build_rules.py composes assets/rulesets/<family>/<mode>.md
from three sources kept in tools/upr-settings/ (the IronMON rules gist,
valiant-code, gist updated 2026-08-31; the game-specific rules gist,
UTDZac, saved 2026-09-05; PyroMikeGit's Super Kaizo README, pushed
2026-03-20). Each mode's file carries every ruleset it builds on in order,
then the game's own updates for that mode, then the sources and dates.
Nothing paraphrased: table rows become bullets. 41 files, one per preset's
game and mode; RulesAssetsTest holds that and the em-dash rule. When a
gist changes, refresh the copy and rerun the script.

Original note:

Every game and every mode has its own rules, and you want them readable
mid-run: a RULES button that opens a box with the rules for the game and
mode you are currently playing, which you read and close without leaving the
game.

Data first: `assets/rulesets/<game>/<mode>/rules.md`, one file per game and
mode, alongside the preset from 0.3. The button reads the current run's
descriptor (game + mode, from 0.1) and opens that file - so it is always the
right rules for where you are, with no picker. Lives in the FILE menu and the
tracker gear, as a scrollable Gen 3 box with CLOSE.

The research is the real work: collect the rules for EVERY game and mode
(Standard, Kaizo, Survival, Super Kaizo, Ultimate, and the per-game variants
for Gen 1-5). Sources: the UTDZac settings gist you shared, and the official
IronMON rules pages per mode. Each file cites its source and date, because
these rulesets get revised, and a rules box that is a version behind is worse
than none. Effort **M**, nearly all of it gathering and checking text.

---

## 3. Tracker

Every item in this section is a clone. Reference file named per item.

### 3.1 Move popup: show the move's type and its matchups - **DONE 2026-09-05**

Cloned from `InfoScreen.drawMoveInfoScreen` (InfoScreen.lua:861) in the
reference's order: name, type chip, Category, Contact, PP, Power, Accuracy,
Priority only when non-zero, then the "Move summary" box. Contact and
priority are read LIVE out of gBattleMoves (priority is the signed byte at
+7, contact is bit 0 of the flags at +8 - pinned against the shipped Nat. Dex
ROM), the same way power and type already were, so a randomized move is
described as it is, not as a table remembers it. The opponent's moves are
tappable too, as in the reference.

**Vetoed the same day:** the first cut measured the move against the
CURRENT OPPONENT's live types ("vs Geodude: Super effective (x4)"). You
called it too much of an advantage, and it is - the opponent's typing is
the player's to work out. The opponent-aware code was deleted, not
disabled. What stays is general chart knowledge about the move's own type
("Strong against: Fire, Ground, Rock / Resisted by: ... / No effect on:
..."), which any player may know by heart, and the harness now asserts no
"vs" line can appear. DS moves show type by name only.

Original note:

`InfoScreen.lua`. The popup already opens; it omits the type and the
super-effective / neutral / resisted / immune breakdown against the current
opponent. Both come from `TypeChart` which already exists (`CoverageTest.kt`
proves it). Effort **S**.

### 3.2 The gear menu - **DONE 2026-09-06**

SETUP at the top of both tracker panels (the reference's SettingsGear;
SETUP is its NavigationMenu.ButtonSetup) opens TrackerGearDialog: the
Setup and Gameplay options this panel honours, each wired where it takes
effect (Show random ball picker, Show physical special icons, Show heals
as whole number, and Game is considered over when: lead faints, highest
level faints, entire party faints, which every tracker reads through
LossCondition); the Notebook, every species marked or noted this run; and
Manage Data's clear, with a confirmation. Kept in prep/tracker-options.txt.
Theme, language, update, extension, streaming and quickload screens are
not ported. Tests: LossConditionTest, TrackerOptionsTest.

Original note:

`NavigationMenu.lua` is the gear. Port the subset that has meaning on a
phone: options, favourites, stat-marking mode, tracked data reset. Skip the
streamer/stream-connect screens.

### 3.3 Favourites that follow you across attempts - **DONE 2026-09-06**

Three name fields under "Startup favorites" on the RUN screen (Favorites.kt),
saved to the favorites file that `PrepStore.loadFavorites()` already read.
Names validate against the tracker's species table and a wrong one turns
red. The tracker's no-party card shows "FAVORITES: A / B / C" on Gen 1, 2
and 3, which is where the reference trackers show them (StreamerScreen.lua
favorites, drawn on StartupScreen.lua; the Gen 1 and Gen 2 forks carry the
same). The NDS tracker has no favorites screen, only a stream chat command
that reads a file, so the DS panel shows nothing. The Gen 3 ball call is
unchanged and still names only a match. Not a rule of any ruleset; a
tracker display feature. Two tests in FavoritesTest.

Blake asked why this sat undone for weeks when the port was supposed to
mirror the tracker: no reason beyond always taking the next large item
before the small ones. Clear the small tracker items before starting
anything new.

### 3.4 Die icon under the pokeballs - **DONE 2026-09-06**

The reroll is the reference's 13x14 `Constants.PixelImages.DICE`, drawn
in Default text beside "Pick this ball:" with a 24dp hit area
(PcDiceButton). It was a REROLL text chip, which the reference never shows.
Seen on the emulator through the `gba-lab` screenshot mode.

### 3.5 Move memory across encounters - **DONE 2026-09-06 (it was not working)**

The run-wide list was recorded and passed to the enemy card, and the card
never read it: every encounter started blind. Now StatMarks keeps the
reference's `{id, minLv, maxLv}` per move (Tracker.TrackMove: Struggle
skipped, new move to the front, a repeat widens the range and re-fronts a
move that slipped past four), and the enemy card draws the whole run's
list, most recent first, with a row from the ROM's move table for a move
used in an earlier battle. Gen 1, 2 and 3 through `moveRowFor` on each
tracker. DS: the enemy card had no moves table at all and the tracker
exposed every move in the opponent's slots; it now keeps only moves whose
PP is below base (BattleHandlerBase.lua:265, `NdsTracker.usedOnly`) and
draws the same run-wide memory. Tests: StatMarksTest (two new),
NdsUsedMovesTest, NdsGen5MapTest updated to the used-only rule. Old
names-only moves files still read.

Original note:

`StatMarks.movesSeenFor(species)` already records moves per species for the
run and `movesSeenRunWide` is already passed into the enemy card. So the
feature you describe may already be working and merely untested - or the
recording side may not fire. Step one: a test that records a move on
encounter one and asserts it is listed on encounter two, including the
"learned a new move at a higher level" case. Fix whatever that test finds.
Reference: `MoveHistoryScreen.lua` and `Tracker.TrackMove`.

---

## 4. Every IronMON game and every ruleset - **XL; research DONE 2026-09-05**

### 4.0 Inventory - what each platform needs, and where it now is

Gathered on Blake's instruction. Nothing ROM-shaped was fetched; ROMs stay
his own dumps, always.

| need | GB / GBC (Gen 1-2) | new DS (HGSS, BW, B2W2) |
|---|---|---|
| emulator core | **fetched**: Gambatte from Swordfish90/LemuroidCores master, arm64 4,739,808 B + x86_64 4,810,064 B, sizes match the API byte for byte. Same pinned family as the app's mGBA (which is LemuroidCores' 3,176,776 B exactly). Staged in `.vendor/cores/gambatte/`, NOT yet in jniLibs | already have: melonDS |
| randomizer | already have: ZX 4.6.1 has Gen1/Gen2RomHandler + gen1/gen2 offsets | already have: Gen4/Gen5RomHandler |
| tracker reference | **fetched**: `~/ironmon-ref/Ironmon-gen-tracker` (mollo010; Red/Blue/Yellow US/EU; MIT, (c) Besteon; last push 2024-03) and `~/ironmon-ref/Ironmon-gen-2-tracker` (seadogstingray fork; Crystal US only; MIT; last push 2023-11, WIP). Both carry GameSettings.lua (~470 addresses), the data tables, 414 Pokemon images | already have: `~/ironmon-ref/NDS-Ironmon-Tracker` covers all NINE DS games with per-game address blocks (HG 26, SS 25, B 28, W 28, B2 27, W2 101 offsets), Gen 4 AND Gen 5 battle handlers, icon sets to #650. Upstream note: "also test B/W" - treat Gen 5 as less proven |
| presets, every mode | **DONE 2026-09-05**: `tools/upr-settings/parse_gist.py` turns the official page (kept as `official-settings.md`) into `strings.tsv`, 42 rows; `editor` test `PresetGenerator` (env-gated) writes each through the vanilla engine's own reader and reads it back. 39 bundled: GSC 4, FRLG 6, RSE 6, HGSS 6, DPPt 6, BW 4, B2W2 5, plus the 5 that were there. RBY's 5 were sidelined in `tools/upr-settings/generated/` until the two-pass randomization existed; bundled 2026-09-06, see 4.26. Your three existing files were kept, not overwritten: FRLG and RSE Kaizo match the page; **your DPPt Kaizo differs from the page in two options** (Randomize starters held items, Ban bad random starter held items) - yours to decide. Found and fixed on the way: the app's settings-string IMPORT handed the whole string to `fromString`, which wants bare base64 - every real string carries a 3-digit version prefix, so the feature had never worked. Both engines now read a string the way the desktop GUI does (prefix, SettingsUpdater for older, refuse newer); tested against real page strings | same |
| rules text (2.4) | same gist + each mode's rules page; needs writing into `assets/rulesets` | same |
| sprites | in the references (414 Gen 1 images) or decoded from the ROM as Gen 3 does; decide at port time | Gen 4: have 986. Gen 5: the DS reference's icon set to #650, or ROM decode |
| game ids | new `RomKind`s for RBY (GB) and GSC (GBC) with CRCs from Blake's dumps; `Platform.GB/GBC` | new `RomKind`s per DS game with header codes from the reference's `GameInfo.VERSION_NUMBER` |

**Caveats stated plainly:** the Gen 2 reference is Crystal-only and a WIP
fork; Gold/Silver would need its addresses added. The Gen 1 reference is
US/EU only. Neither has been run against this app yet. The Gambatte core is
staged, not wired: wiring it is `Platform.GB` plus a boot test, and it stays
out of the APK until then so nothing ships untested.

### 4.1 Order

HGSS first (Gen 4 structs already ported; it is new addresses), then B2W2
and BW (Gen 5 handler port), then Crystal, then RBY. Each one: RomKind +
CRC, memory map from the reference, RunView producer, a fixture test, a
boot on the emulator, before the next.

**HGSS - landed 2026-09-05, awaiting a dump to boot.** `NdsGameMap` turns
the DS tracker's Platinum-only private vals into per-game data copied from
the reference's `MemoryAddresses.lua` (HeartGold and SoulSilver share one
block); the tracker takes the map as a parameter and `detect()` picks it
from the cartridge header's game code in RAM - an unknown game gets NO
tracker rather than Platinum's offsets. `RomKind.HEARTGOLD_U` /
`SOULSILVER_U` (family HGSS, CRC_UNKNOWN until read off your dumps). The
badge set follows the map, and the Kanto row (art was already bundled)
appears once the first Kanto badge is earned. Tests: the map values are
pinned to the reference; the same RAM image read through the HGSS map finds
its bag and badges while the Platinum map finds nothing, which fails if any
offset is still hardcoded; the unlocated state still names HGSS's badge art.
Not yet: a boot, which needs your HeartGold or SoulSilver dump (for the CRC
too), and HGSS presets, which are settings strings on the gist waiting to be
imported.

**BW / B2W2 - landed 2026-09-05, awaiting dumps to boot.** The real port,
not just addresses:
- `Generation.NDS5` and `RomKind.BLACK_U / WHITE_U / BLACK2_U / WHITE2_U`
  (families BW and B2W2, CRC_UNKNOWN until read off your dumps).
- The engine picks its ROM handler by GENERATION now. It used to pick Gen 4
  for any `.nds`, which would have handed a Black 2 cartridge to the Gen 4
  handler. The extension is only the fallback for a caller with no kind.
- `NdsGameMap.BW` and `.B2W2` from the reference's GLOBAL blocks, marked
  `absolute`: Gen 5 has no pointer chain, and the tracker's four "version
  pointer is 0" guards now know that.
- The decoder reads the 220-byte Gen 5 entry (84-byte party area, 649
  species, nature as its own byte at block B + 0x19) - a round-trip test
  through the encoder, plus proofs that a Gen 5 entry is refused as Gen 4
  and Victini is refused as a Gen 4 species.
- The battle read is BattleHandlerGen5 in Kotlin: the fetch guard (battle
  PID equals the lead's, enemy side has one), battler records 0x1C apart
  from mainBattleDataPtr, pointer to battle data, pointer to the struct,
  Illusion pointer at +4, live HP/status/moves/PP/stages at the
  BATTLE_STAT_OFFSETS. Tested against a fake Black laid out exactly that
  way: Genesect comes through with live HP 77 (not the struct's 150), Fusion
  Flare/Bolt with PP, ATK at +2. The same RAM read with Platinum's map finds
  nothing.
- Data: `tools/extract_gen5_data.py` scripts species (649), moves (559),
  abilities (164) and per-version-group move levels out of the reference's
  constants; items from the reference's GEN_5_ITEMS (see 4.24). Sprites 494-649 with shinies (312 files, 128px) from the
  reference's icon set, id-keyed so the loader needed no change.
- Not yet: a boot (needs your dumps, for the CRCs too); the
  Gen 5 bag layout is ASSUMED to match Gen 4's - the reference's own author
  flags B/W as less tested. 249 tests green.

**Crystal - landed 2026-09-05, awaiting a dump to boot.** A new console:
- `Platform.GBC` (Gambatte, the LemuroidCores build now in jniLibs;
  SRAM persisted by the app like mGBA; a 160:144 game box, which the Play
  screen now takes from the platform instead of assuming 3:2),
  `Generation.GBC2`, `RomKind.CRYSTAL_U` (family GSC, title "PM_CRYSTAL",
  CRC_UNKNOWN). The engine dispatches `.gbc` to ZX's Gen 2 handler.
- `GbcTracker` produces the same `TrackerState` the Gen 3 panel draws, so
  no panel work. WRAM comes through LibretroDroid's SYSTEM_RAM fallback
  (`0x02000000 + offset`); the reference's "0x0200xxxx" numbers turn out to
  be exactly those offsets, bank 1 of pokecrystal's WRAM. Two places the
  WIP reference disagrees with the decomp were taken from the decomp and
  guarded: its party count is bank 0 (impossible for Crystal), so the count
  is validated against the species list and the structs; and it steps the
  party by 44 bytes, Gen 1's size, where Crystal's is 48.
- Types, base stats, move power/type/accuracy/PP come from the RANDOMIZED
  ROM file (BaseData 0x51424, Moves 0x41AFB - the reference's addresses
  with the domain prefix dropped). Only names are bundled, scripted out of
  the reference (251 species, 251 moves). Sprites: Gen 2's ids are Gen 3's
  first 251, so the existing pack serves with nothing added.
- The opponent shows only the moves it has USED (wCurEnemyMove), the
  reference's rule. Heals use the reference's own Gen 2 item table.
- Tested through a fake WRAM and ROM: party typed from the ROM, PP Ups,
  status, a wild battle with the opponent and its used-move list, trainer
  vs wild, a dead lead as a loss, and a lying count byte inventing nothing.
- Found and fixed on the way: `RomIdentity` matched by CRC only, so EVERY
  kind added with CRC_UNKNOWN - HGSS, BW, B2W2, Crystal - would have been
  rejected at Prepare as "Not a GBA ROM". It now falls back to the header
  (DS title + code, Game Boy title + logo), exact-match on the kind's own
  platform so "POKEMON B" cannot claim "POKEMON B2", and says which console
  an unknown game is. The GBA parser now requires its checksum, which is
  what keeps the three from claiming each other's files. 257 tests green.
- Not yet: a boot (your Crystal dump, and its CRC); item names (shown as
  #id); Gen 2 has no abilities, so that line reads "-". Gold and Silver
  landed 2026-09-06, see 4.25.

### 4.2 The earlier note, superseded

This is the one that needs 0.1-0.3 first. Then per platform:

| Platform | Games | Core | Tracker reference | Status |
|---|---|---|---|---|
| GBA Gen 3 | FRLG, RSE, Nat. Dex | mGBA | besteon Ironmon-Tracker | **have it** |
| NDS Gen 4 | DPPt, HGSS | melonDS | (whatever `tracker-nds` was ported from) | DPPt built; HGSS is new memory maps |
| NDS Gen 5 | BW, B2W2 | melonDS | **need a reference** | RESEARCH |
| GB/GBC Gen 1-2 | RBY, GSC | Gambatte | **need a reference** | RESEARCH |

The besteon tracker is Gen 3 only. Gen 1/2 and Gen 5 IronMON exist as
community rulesets, but I do not currently have a tracker to clone for them.
Under the clone rule I will not invent one: the first task for those rows is
finding the community tracker each uses, vendoring it under `~/ironmon-ref/`,
and only then porting. If none exists for a platform, that platform's tracker
is a design decision, and it is yours to make, not mine to assume.

Rulesets (Standard, Kaizo, Survival, Super Kaizo, Ultimate, and the per-game
variants) are the 0.3 data files plus the mode picker before RUN. The
randomizer side is already there: every mode is a preset. The tracker side is
per-mode flags, of which Survival's are the only substantial ones.

---

## 4.5 Beyond IronMON: a general emulator, a patcher, and a new name

Added by Blake 2026-09-05: the app should also be a full Game Boy, GBA and
DS emulator that plays any ROM you own (tracker or not), a patcher (your own
.rnqs, any BPS/IPS hack patch applied to a game file, then patch-and-play),
and it needs a new name.

**What already exists in pieces:** three cores (Gambatte, mGBA, melonDS)
with the same speed/save-state/SRAM/controller plumbing; a BPS patcher and
patch import (today keyed to the Nat. Dex path); `.rnqs` import and the
settings-string import; header-based identification that already names an
unknown DS or Game Boy game as such.

**What the general emulator needs (M-L):**
- The Play screen is built around "the current run". It becomes "a session":
  any file from a Library, platform from the header/extension, tracker only
  when the game is one we have a map for. That is mostly untying, and 0.1's
  `Platform.fromExtension` is the hook.
- Standard-emulator parity per console: save states (have, three slots),
  fast-forward (have), SRAM (have), controller mapping (have), plus what a
  general player expects that IronMON never asked for - a library list,
  per-game settings, cheats, screen filters/scaling options, DS screen
  layouts beyond the two we draw, GB palettes. Each is a small item; the
  list is long. Do them after the session refactor, not before.

**What the patcher needs (S-M):** the Prepare tab grows a plain "PATCH A
ROM" path: pick a file, pick a .bps/.ips/.ups, output stored in the Library
under the patch's name. `Patcher` handles BPS today; IPS and UPS are small
formats. An unknown result (a hack) plays without a tracker - it is not an
error.

**The name: KaizoCore**, chosen by Blake 2026-09-05. Launcher label, INFO
screen, crash/route-log share subjects and the APK file names carry it. The
package id stays `com.ironmonone.app`, because changing it is a NEW app to
Android and would orphan every save on the phone; the Kotlin package and the
repo folder keep the old name for the same reason (no user ever sees them).

**Phase 1 done 2026-09-05: sessions and a real library.** `GameSession`
(file, platform, kind, id, isRun) is what Play opens; `PrepStore.session()`
resolves it from `prep/library/session.txt` ("run" or "lib<TAB>name"), and
the run's save paths are pinned byte-for-byte to the pre-session paths
(GameSessionTest), so nothing on an existing phone moved. `LibraryStore`
copies a picked file into `prep/library/` with a `.meta` sidecar (crc, kind,
platform, summary) so listing never re-hashes; PLAY selects it and jumps to
the Play tab; DELETE is two taps. A library file is tracked only on a CRC
match (`GameSession.trackerKind`): a one-byte-modified FireRed identifies
"FireRed, but not a revision this app knows", plays, and shows no tracker,
no NEW RUN, no attempts. The Run tab re-selects the run on every seed.

Found while verifying it, and older than the session work: `GLRetroView`
registered its render observer on the ACTIVITY lifecycle and never removed
it, so after leaving Play any activity pause (the ROM picker, HOME, screen
off) called native pause() on a destroyed core: SIGSEGV in Audio::stop().
Fixed both sides (observer removed in onDestroy; pause/resume/aspect guard
null). Reproduced before the fix, clean after, same sequence.

**Phase 3 done 2026-09-05: the patcher.** Every library card has PATCH:
pick a .bps, .ips or .ups and `LibraryStore.patch` applies it to that file
off the main thread and stores the output as a NEW entry named after the
patch with the base's extension; the base is untouched. UPS is new in
`core-patch` (`Ups.kt`, spec-encoder round-trip in UpsTest, source size,
source CRC and the patch's own CRC all gated, output CRC checked). The
output is identified like an import: Emerald_Clean + natdex-emerald.bps on
the emulator gave "Pokémon Emerald + Nat. Dex 1.2.1 · tracked" and
booted; a hack that matches nothing plays untracked. Failures are the
PatchException messages, shown in the status line, and store nothing.

**Phase 4.1 done 2026-09-05: per-game settings.** `GameSettings` keeps
speed, mute, DS one-screen mode and the landscape pane width per session
id in `prep/games/<id>.properties`. Speed and mute also update a global
"last used" file that a never-seen game inherits, so the pre-session
behaviour (NEW RUN keeps turbo and mute) holds exactly: the run is one id.
The old playspeed.txt/playmute.txt seed that global once. One debounced
writer in PlayScreen persists all four, so no toggle can forget to.

Still to do here: DS layouts beyond the two we set, Game Boy palettes,
screen filters and integer scaling, cheats; and CRC pinning for
HGSS/BW/B2W2/Crystal so clean dumps of those track from the library too.

**Rules that still hold:** no ROM is ever bundled, fetched or hosted - every
game is the player's own file; the tracker stays a clone with a reference;
the IronMON path is not to regress while the general one is built.

## 4.29 The Gen 1 tracker (2026-09-06)

**One Special stat (2026-09-06, Blake).** The card showed SPA and SPD for a
game that has one Special, and a six-stat BST that counted it twice.
BaseStats now carries `singleSpecial`; the panel prints one SPA row (the
label the Gen 1 reference tracker draws, `statKey:upper()` over its
hp/atk/def/spa/spe list) and the BST is the five-stat sum. Note the
reference's own data file lists Nidoking at 495, the modern six-stat
figure, which is a table copied from a later generation; the ROM's five
base stats are what the game uses, so the app sums those. Move category
icons stay: Gen 1 already splits physical and special, by type, and the
reference draws the same icons.

`Gen1Tracker` in tracker-gba: Red, Blue and Yellow through Gambatte's WRAM,
producing the same TrackerState the Gen 3 panel draws, so no panel work.
Three sources and nothing else: the Gen 1 reference tracker's table (six
44-byte party slots, one 29-byte battle struct for the opponent, the enemy's
move byte recorded only when it is a move the opponent knows, the in-battle
byte 0/1/2, one badge byte, a counted bag of (id, qty) pairs); pokered and
pokeyellow through tools/wram_layout.py, which gives the same numbers with
their names and confirms the reference's "Yellow is one less" rule for the
D block (wPartyCount D163/D162, wPartyMons D16B/D16A, wEnemyMon CFE5/CFE4,
wIsInBattle D057/D056, wEnemyMoveNum CFCC/CFCB, wObtainedBadges D356/D355,
wNumBagItems D31D/D31C, wBagItems D31E/D31D); and the randomizer's
gen1_offsets.ini for the ROM tables it rewrites (base stats 28 bytes per dex
number, Mew apart in Red, moves 6 bytes, and the PokedexOrder table).

The Gen 1 fact that shapes the code: RAM holds INTERNAL species ids, not
dex numbers. The ROM's own order table (Red 0x41024, Yellow 0x410B1, 190
entries) turns one into the other, and names, base stats and sprites key on
the dex number. The fixtures map internal ids to dex numbers deliberately
oddly, so a tracker that skipped the table would name the wrong Pokemon.
Healing item ids come from pokered's item_constants.asm (Potion 0x14,
Super 0x13, Hyper 0x12, Max 0x11, Full Restore 0x10, the three drinks
0x3C-0x3E). Gen 1 has no held items or abilities; those lines read "-".
The Play screen picks Gen 2 or Gen 1 from the header. Four tests: Red read
and typed through the ROM, a wild battle with the used-move rule and the
trainer flag, Yellow's shifted layout reading its own RAM while Red's map
finds nothing, and the count-byte, no-party and unknown-title rules. Red and
Yellow boot on the emulator as tracked library games. Suite green.

## 4.28 Art from the player's own ROM, the app's own icons, real dumps (2026-09-06)

The bundled Nintendo art was the gate on a public build (BETA-PLAN section 0).
Three things closed most of it in one day:

- `RomSprites`: Gen 4 and Gen 5 Pokemon sprites decoded out of the player's
  own DS ROM, the randomizer's getMascotImage read ported to int arrays and
  run for every species into a per-kind PNG cache the tracker card reads
  before the bundled sheet. Archive paths are checked against the
  randomizer's own ini files by test. Proven on synthetic files first, then
  on REAL dumps the same day: Black 2 decodes 649 of 649, HeartGold 493 of
  493 (RomSpritesRealDumpTest, env-gated on the dump paths; montages were
  looked at, not just counted). On the emulator the Black 2 cache holds 1298
  files after one Play visit. The bundled gen4sprites folder is now a
  fallback and goes once the card has been seen drawing from the cache with
  a party on screen.
- `tools/draw_icons.py`: the 19 type, 6 status and 112 badge icons are drawn
  by the project from primitives and a 3x5 pixel alphabet, same names and
  sizes, so nothing in the app changed. NOTICE says so.
- Real dumps arrived and were read, never copied anywhere but the scratchpad
  and the emulator: Black 2 (CRC D4427FD1), HeartGold (C180A0E9), Crystal
  (EE6F5188, the 1.0 revision), Red (9F7FDD53) and Yellow (7D527D62), the
  last two matching the randomizer's ini. Black 2, HeartGold and Crystal are
  pinned now; SoulSilver's copy of HeartGold's kind had to be given
  CRC_UNKNOWN explicitly or it would have inherited HeartGold's CRC.

Found on the way and fixed: importing a 512 MB DS dump threw
OutOfMemoryError, because the library read every picked file into a byte
array. RomIdentity.identify(File) streams the CRC and keeps 64 KB for the
headers; LibraryStore.importFile moves the file in; ZipImport.extractToFiles
unpacks a zip entry straight to disk (cap 600 MB); the picker streams each
URI to a temp file first. Only patches, which are small, are read into
memory. Black 2 imports as "verified, tracked" and boots on the emulator's
melonDS (title screen, tracker waiting for a party). Suite green.

## 4.27 Beta phase 1: feedback, page, release mechanics (2026-09-06)

See BETA-PLAN.md for the whole plan and its gate (the licence and the
bundled art). Phase 1 is the part that is safe before that decision:

- INFO tab, BETA FEEDBACK: a text box and SEND FEEDBACK. `Feedback.compose`
  builds the report (app version, device and Android version, the game
  family of the last run and never a file name, the tester's words, the
  app's own logcat tail) and hands it to the share sheet, so a tester picks
  Discord, email or anything else and no server exists. Verified on the
  emulator: the chooser opened with the composed report in it. BUG FORM,
  LATEST BUILD and SUPPORT THE PROJECT buttons exist but render only when
  `Feedback.Links` has a real https URL; all three are blank on purpose
  until the site, the repository and the Ko-fi page exist, and a test
  refuses anything that is neither blank nor https.
- `site/`: one static page (what it is, video and screenshot slots, the
  before-you-install warning that it contains no ROMs, install steps, a
  Netlify bug form with no uploads, support, credits) plus thanks.html.
  Placeholders (`RELEASES_URL`, `SUPPORT_URL`, `SOURCE_URL`, `DISCORD_URL`,
  `LICENSE_LINE`) are listed in site/README.md. Not deployed: deploying is
  Blake's act.
- `.github/ISSUE_TEMPLATE/bug_report.yml` (device, version, family, what,
  steps, the pasted report; no uploads) and a config that sends ideas to
  Discussions.
- `tools/release.sh`: full suite, release build, `dist/KaizoCore-<ver>.apk`
  with a .sha256 beside it and a release-notes stub. Tags, pushes and
  uploads nothing.
- `site/img/`: the first screenshot set from the emulator (library,
  states, settings, achievements are usable; the play and OBS shots show an
  empty tracker because the emulator's Emerald sits at the intro with no
  party, and the OBS source says "No Pokémon yet"). tools/screenshots.sh
  re-takes the set from any device adb sees; the real set comes from Blake's
  phone with a run in progress.

## 4.26 Red, Blue, Yellow: the two-pass randomization (2026-09-06)

The official settings page: Gen 1 has no fluctuating exp curve option, so a
proper Ironmon game takes two randomizations, PART 1 (the ruleset's string)
then PART 2 (one string shared by every ruleset) over PART 1's output, to
stop evolutions landing on legendaries. The page offers a pseudo-fluctuating
growth patch as the alternative; that patch is a third-party download the app
does not bundle, and the two-pass route needs nothing but the dump.

`Generation.GB1` (Gambatte, like Gen 2) and three kinds, RED_U, BLUE_U,
YELLOW_U: titles from gen1_offsets.ini's `Game=` lines, CRCs from its (U)
entries, Yellow as .gbc since it is the CGB-enhanced cartridge. ZX's Gen 1
handler accepts its own output as input (checkRomEntry matches the (U)
entries on title, version and region; their CRCInHeader is unset), which is
what the desktop workflow relies on too. `Randomizers.twoPass` runs the
chosen preset into a temp file, then the bundled "RBY PART 2.rnqs" from it
into the destination with a seed derived from the run's (so one seed
reproduces both), joins the two logs under PART headings and deletes the
intermediate on every exit. The four sidelined RBY presets and PART 2 are
bundled now; RnqsInfo.secondPass keeps PART 2 out of the picker
(RulesetCatalog.isCompatible refuses it) while both randomize call sites
hand it in through PrepStore.secondPassSettings.

Proof without a cartridge: RandomizersTest stubs the engine and checks the
order, that PART 2's input is PART 1's output, the seeds, the cleanup, the
joined log, the refusal when PART 2 is missing, and that a failed PART 1
still cleans up; RomIdentityTest identifies the three headers and pins the
CRCs. Owed: a real two-pass run on a Red/Blue/Yellow dump. The Gen 1 tracker
landed the same day, see 4.29. Suite green.

## 4.25 Gold and Silver (2026-09-06)

Two new kinds, GOLD_U and SILVER_U (family GSC, Gambatte, the same Gen 2
tracker and ZX handler as Crystal). Header titles "POKEMON_GLD" and
"POKEMON_SLV" with the manufacturer code right behind them, from pokegold's
rgbfix flags; CRC-32s 6BDE3C3E and 8AD48636 from the randomizer's own
gen2_offsets.ini, the whole-file CRC ZX checks before it will randomize a
dump, so a dump ZX accepts is a dump this pins. Crystal stays CRC_UNKNOWN
because two US revisions exist (ZX lists both) and Blake's dump decides.

The tracker now takes a `Gen2Map`, picked from the header: CRYSTAL is the
reference tracker's set as before; GS is new and came from nowhere the
trackers had. It was computed from the pokegold disassembly by
tools/wram_layout.py, which walks ram/wram.asm and layout.link the way
rgbasm and rgblink do (constants, struct macros, for/endr, if/endc,
UNION, section order, align). The tool was believed only after `--check`
reproduced every Crystal address the tracker already uses from pokecrystal,
10 of 10 exact; the same run on pokegold, -D_GOLD and -D_SILVER, gives one
layout for both: wPartyCount DA22, wPartySpecies DA23, wPartyMons DA2A,
wEnemyMon D0EF, wBattleMode D116, wEnemyMoveStruct CAE8, wJohtoBadges D57C,
wKantoBadges D57D, wNumItems D5B7, wItems D5B8. ROM tables from the
randomizer's Gold (U) entry (base stats 0x51B0B, moves 0x41AFE), which
Silver (U) copies; pokegold's base_stats files have Crystal's shape.

Found on the way: the reference's "eMove" 0xC608 is wEnemyMoveStruct, whose
first byte is the move id, not wCurEnemyMove (0xC6E4). The tracker was
reading the right byte under the wrong name; the comment is fixed.

Proof: GbcTrackerTest lays a Gold header, a Gold party and a Gold battle at
the GS addresses and reads them back, and the same RAM through Crystal's map
finds nothing; a Game Boy title the tracker does not know gets no map and
reads nothing rather than guessing. RomIdentityTest identifies both headers
and pins the two CRCs. A boot is owed to a Gold or Silver dump. Suite green.

## 4.24 Gen 5 item names (2026-09-06)

gen5/items.tsv was a copy of the Gen 4 table, so the 158 items that only
exist in Gen 5 (the Drives, Gems, Casteliacone, Balm Mushroom, the Grams)
showed as "#id" on the panel and 42 Gen 4 names carried Gen 4 spellings.
tools/extract_gen5_data.py now reads ItemData.GEN_5_ITEMS out of the
reference, key by key since the table is sparse (607 entries, ids to
625), and writes 606 rows; the names are the reference's verbatim
("Paralyze Heal", "Poke Ball"), not edited. Ids the reference leaves out
(113-115, 120-125 and other holes) are gone from the table too, and the
panel prints "#id" for those, which is the truth. The healing table
already carried RageCandyBar (504), the one Gen 5 id in the reference's
HEALING_ITEMS. Gen5ItemsTest pins six of the reference's names and that no
row is "???" or blank. Suite green.

## 4.23 BW and B2W2 badge art (2026-09-06)

The reference's ironmon_tracker/images/icons carries BW_badge1..8 and
BW2_badge1..8 with _OFF twins, 16x16 like the DPPT set already bundled
(which is byte-identical to the reference's). All 32 are copied into
app/src/main/assets/badges under the names PcBadgeRow already builds from
the map's badgePrefix ("BW", "BW2"), so no code moved: the row that fell
back to numbers now finds art. BadgeArtTest reads the prefixes from
NdsGameMap.ALL plus the GBA sets and asserts all sixteen files per set;
it was run red (with the cached test result cleared, since assets are not a test input and Gradle would otherwise replay the green) with one BW2 file removed ("BW2 is missing
[BW2_badge5_OFF.png]") before going green. A live row still needs a Black
or White dump. Suite green.

## 4.22 Gen 5 ability reveals, and White was reading Black's addresses (2026-09-06)

Ported from BattleHandlerGen5._checkBattlerAbilityTriggered. Each battler
slot has a u16 at abilityTriggerStart (player +0, opponent +4 in singles,
the two _readBattleDataPtr calls in _tryToFetchBattleData) that the game
writes with the ability that just activated. The tracker remembers the
last word per slot, as the reference's lastAbilityValue, and acts only on
a change to a non-zero word: equal to that battler's own ability, it is
revealed for that species; on the player's side with Trace (36), a
different word is the traced opponent's ability, revealed for the
opponent when it carries it. The opponent's slot is read first and a
player reveal in the same tick waits for the next one, so none is lost.
Two reference behaviours kept on purpose: a zero in between does not make
the same word new, and there is no Speed Boost rule (that is Gen 4's
subscript path). The slot memory clears when the battle ends.

Found on the way, and the more important fix: MemoryAddresses.lua gives
WHITE as every BLACK address `+ 0x20` and WHITE2 as every BLACK2 address
`+ 0x80`, and the maps here had White on Black's addresses and White 2 on
Black 2's. On a White cartridge every read would have been 0x20 short:
no party, or worse, confident garbage. `NdsGameMap.shifted` builds WHITE
and WHITE2 from their siblings so the numbers exist once; forCode returns
the shifted map for the White codes.

Proof is in NdsGen5MapTest against the fake Black: the opponent's word
reveals Download for Genesect once and not again, a foreign word reveals
nothing, Trace on the player's side reveals the opponent, the memory
clears between battles, and the same RAM moved up by 0x20 and stamped
White reads the party and the opponent through the WHITE map while
Black's map finds nothing there. A real Black or White boot is still owed
to a dump. Suite green.

## 4.21 Cloud sync (2026-09-06), last item of the audit order

The backup zip (4.13: saves, states, runs, notes, settings, never a ROM)
kept up to date in one document the player picked through the system
picker. `CloudSync`: LINK A CLOUD FILE on the INFO tab runs
CreateDocument, the player chooses Google Drive (or Dropbox, OneDrive, a
folder) in the picker, and the app takes a persistable write permission
on the document it made. Whenever the Play screen pauses or closes, after
the SRAM and auto-save are on disk, a background thread rewrites the zip
into that document ("wt", truncating) if the fingerprint of the backed-up
files moved and two minutes have passed; the Drive app carries it up.
SYNC NOW forces it, RESTORE FROM CLOUD asks and then overwrites, UNLINK
releases the permission and leaves the file. The link (prep/cloudsync.txt)
is not admitted by the backup, so a restore on another phone does not
carry this phone's link.

Why not the Drive API: it needs an OAuth client registered against the
signing key in a Google Cloud project, a consent screen and the Play
Services auth dependency, for one file. The document provider is the
contract every cloud app already ships and needs nothing registered.

Verified on the emulator, which has no Google account, against the
Downloads provider (the same contract): link wrote 90 files (11.3 MB);
playing Emerald, pressing START and leaving the tab rewrote the file
(mtime 01:49 to 01:52); RESTORE FROM CLOUD confirmed and restored 90
files. Drive itself, on the phone, is owed. Three tests pin the link file
round trip and its exclusion from the backup, the fingerprint (a ROM
change does not trigger a sync, a save change does), and provider names.

## 4.20 RetroAchievements (2026-09-06), ninth item of the audit order

The client is rcheevos' rc_client (MIT, vendored at
libretrodroid/src/main/cpp/rcheevos, built into the native library with
RC_CLIENT_SUPPORTS_HASH). It lives in native code (cheevos.cpp) so it can
read the core's memory once per emulated frame from LibretroDroid::step
with no JNI hop; the memory map is rc_libretro's, built from the core's
memory descriptors plus retro_get_memory_data for the console id (GBA 5,
GBC 6, DS 18). What the client cannot do alone is queued and drained by
the JNI step into GLRetroView.cheevosListener: server calls, which
RetroAchievements.kt performs on one HTTP thread against the URL and body
rc_client built and answers through cheevosServerResponse; and events
(unlock, mastery, server error, offline/online, plus login-done and
game-loaded), which the Play screen turns into the status line.

The FILE row has ACHIEVEMENTS: sign in (username and password, sent once;
what is kept is the session token the server returns, in
files/ra/session.txt), the game's progress, the set grouped as rcheevos
groups it, and the HARDCORE switch. A saved token signs in silently when
the core comes up, and the game is identified by hash once the login
lands. Hardcore is the player's choice, stored as a marker file, and the
app enforces its half: cheats reset and refused, rewind refused, slow
motion skipped in the speed cycle, state loads refused. Softcore is the
default. Nothing is loaded on an IronMON run (session.isRun): a randomized
ROM has no set anyway, and the run stays unquestionable. Library games,
tracked or not, do load a set.

Verified on the emulator with a bogus account: the client's request went
to retroachievements.org and came back in 1.3 s, rcheevos logged "Login
failed: Invalid user/password combination", and the dialog shows that
sentence in red under the form. A real login, a hashed game and an unlock
are owed to Blake's account and phone; no RA account was on hand here.
Three tests pin the JSON the native side emits, the token store (never a
password) and the console ids. Suite 355 green.

## 4.19 DSi mode (2026-09-06), eighth item of the audit order

melonDS DS runs a DSi given the player's own dumps in the system folder
under the names it scans for: bios7.bin, bios9.bin, firmware.bin,
dsi_bios7.bin, dsi_bios9.bin, dsi_firmware.bin and a NAND image (imported
as dsi_nand.bin). `DsiMode` is the bundle: the seven files, the missing
list, and the option sets ON (console dsi, sysfile native, boot direct,
the three path options, virtual SD on) and OFF. The DS settings page has
a DSI MODE section: the checklist, ENABLE DSI MODE (only when all seven
are present) and BACK TO DS; both take effect on the next boot. The path
options live in a hidden DSi group and are sent to the core only while
console mode is dsi, so a plain DS is never told about files it lacks.
The core has no save states in DSi mode (its own message), so the
auto-save, rewind and slots do nothing there.

Found while trying to open the DS page with a header-only stub: a DS file
the core cannot load left the core with no game, and the rewind recorder's
next serializeState faulted inside melonDS (0x18) and took the app down.
Two guards now: every native serialize/unserialize returns empty unless a
game is loaded, and RomIdentity checks the DS header's own CRC-16 (0x15E),
so a damaged DS file is shelved as not playable ("would crash the core")
and never header-matched to a tracked kind. The three test stubs carry a
valid checksum now.

Library sidecars are versioned now (`v2` first line). A sidecar written
under an older identification rule is ignored and the file re-identified
on the next list(), so a verdict that changed (a DS file now known to be
damaged) cannot survive an update in the cache (LibraryOrganizeTest).

The DSi section is unit-tested (DsiModeTest: the switch values are real
catalogue values, the file gate) and shares the dialog path the link
section was verified through, but it has not been opened on the emulator:
with the checksum guard, no DS file on hand is loadable, and the real
dumps are Blake's. A real DSi boot is owed; there must be no DSi dumps
here, they come from his console.

## 4.18 Game Boy link over Wi-Fi (2026-09-06), seventh item of the audit order

Gambatte carries its own network Game Link: `gambatte_gb_link_mode` (Not
Connected / Network Server / Network Client), `gambatte_gb_link_network_port`
(56400..56420) and the server address as TWELVE digit options
(`..._server_ip_1..12`, three zero-padded digits per octet), all gated by
`gambatte_show_gb_link_settings`. The Game Boy SETTINGS page has a LINK
CABLE (WI-FI) group: the phone's own address, mode, port, and one address
field that `LinkIp` splits into the twelve digits (LinkIpTest). Both phones
run KaizoCore with the same game on the same Wi-Fi; one hosts, the other
joins with the host's address.

Only the page is verified (the emulator is one phone). A two-phone trade is
the proof still owed, and mGBA's libretro build has no link at all, so this
is Game Boy only.

## 4.17 Sensors and rumble (2026-09-06), sixth item of the audit order

Rumble: LibretroDroid already surfaced the core's two motor strengths as
events; the app now collects them into the phone's motor (`PhoneHardware`:
the louder motor's strength as amplitude, a long one-shot re-armed on every
change, zero cancels; VIBRATE permission added). Sources: mGBA's Game Boy
Player rumble (`mgba_force_gbp`) and rumble carts, Gambatte's rumble carts
(`gambatte_rumble_level`), melonDS's Rumble Pak (`melonds_slot2_device`,
now a SETTINGS row).

Sensors: LibretroDroid had no sensor interface, so one was added (KaizoCore
patch in environment.cpp): RETRO_ENVIRONMENT_GET_SENSOR_INTERFACE hands the
core set_sensor_state / get_sensor_input; enable requests set a mask (1
accel, 2 gyro, 4 illuminance) the app reads every half second and
registers only the listeners the core asked for. Values go in as libretro
expects: accelerometer in g (Android's m/s^2 / 9.80665), gyro in rad/s,
light in lux. mGBA uses them for tilt and gyro carts (Yoshi, WarioWare
Twisted) and for solar when `mgba_solar_sensor_level` is "sensor"; melonDS
for `melonds_solar_sensor_host_sensor`.

Both have on/off rows in SETTINGS under Hardware (app-side keys, never sent
to the core as variables). Verified on the emulator only for stability and
the rows: the emulator has no motor and its virtual sensors are static, so
a tilt game on a real phone is the proof still owed.

## 4.16 Zip import and saves backup (2026-09-05), fifth item of the audit order

`ZipImport`: ADD FILES on the ROMs tab now takes a .zip and imports every
.gba/.gbc/.gb/.nds/.bps/.ips/.ups inside as if picked on its own (readmes
and art ignored, entries capped at 256 MB, macOS `._` shadows skipped).

`Backup`: INFO has a BACKUP card with BACK UP (system "save as", suggested
name `KaizoCore-backup-<date>.zip`) and RESTORE (system picker). The zip
holds saves/ (states, thumbnails, locks, undo backups, .srm, melonDS .sav,
auto-save), the run's notes and favourites, lastrun/lastseed, attempts,
presets, key bindings, skin, layouts, cheats, core options, per-game
settings, library notes and the current/previous run. Never ROMs,
patches, prepared bases or the Nat. Dex patches. Restore admits only
allowlisted relative paths, refuses escapes, overwrites in place, and
refuses a zip without the KAIZOCORE-BACKUP marker (BackupTest).

## 4.15 Rewind and slow motion (2026-09-05), fourth item of the audit order

Slow motion is native: `LibretroDroid::setSlowMotion(divisor)` (KaizoCore
patch, JNI + `GLRetroView.slowMotion`) runs the core on one vsync in
`divisor` while every vsync still renders, so the picture never freezes and
input stays live. The speed cycle is 1x -> 2x ... -> top turbo -> 1/2x ->
1/4x -> 1x; audio is off in turbo, slow motion and rewind alike. Slow
motion is never persisted.

Rewind is `RewindBuffer`: a ring of serialized states pushed every 500 ms
(GBA/GB, 60 deep = 30 s, ~30 MB) or every 2 s (DS, 6 deep, tens of MB each)
while the game runs at 1x. REWIND is a HOLD (FILE row, landscape chip,
and the REWIND emulator action for a pad button): down pops states back
into the core every interval/4 until released or the history is empty.
Serialize/unserialize go direct (no emulation-thread hop), the same rule as
the SRAM flush and the auto-save. Refused on a tracked game, the cheats
rule: a rewound IronMON run is the thing a viewer would call out.

## 4.14 Emulator settings page (2026-09-05), third item of the audit order

FILE > SETTINGS opens `EmulatorSettingsDialog` for the current console.
`CoreOptions` is the catalogue: per platform, the core options worth a row
(key, label, accepted values, default, group, restart flag, hint), taken
from the bundled cores' own option tables and the libretro docs. GBA: video
filter, colour correction, frame blending, frameskip + interval, idle loop
removal, audio low-pass + range, BIOS use/skip, solar sensor, GB Player
rumble, opposing directions. GB/GBC: filter, colourisation, 47 internal
palettes, GBC colour correction + mode, dark filter, frame blending,
resampler, hardware mode, boot logo, rumble, opposing directions. DS:
filter, renderer, OpenGL filtering, threaded renderer, hybrid ratio and
small screens, audio interpolation and bit depth, mic input (blow/noise/
silence; the phone mic is deliberately not used, it needs a permission),
solar sensor, console (ds/dsi), boot, system files mode, firmware
language, clock.

The video filter row is LibretroDroid's shader (Default/Sharp/LCD/CRT/
Upscale=CUT), not a core option. Tapping a row cycles the value; it goes to
the running core (`updateVariables`, or `shader`) and to disk
(`CoreOptionStore`, changed values only, `prep/coreopts/<platform>.properties`);
restart-marked rows say so and land on the next boot, since every stored
option is passed as `variables` at view creation. SYSTEM FILES lists the
console's BIOS/firmware names with present/missing and an IMPORT that copies
the player's own dump into filesDir, which is the cores' system directory.

## 4.13 Controller skins (2026-09-05), second item of the audit order

`PadSkin` (CLASSIC, MODERN), one choice for the app in `prep/skin.txt`,
switched with the SKIN chip in the layout editor. Positions and sizes are
the layout's; the skin only changes the paint in `PadButton`:
- CLASSIC: the framed pixel look, unchanged.
- MODERN: what the store emulators draw. A and B are circles, Select and
  Start pills, L and R capsules, all translucent white with a hairline edge
  and a brighter fill while pressed (a `pressed` state set by the same
  press handlers that send the key). The d-pad is one disc with a cross
  drawn on a Canvas under the four arrow buttons, which keep their hit
  areas and draw only their glyphs. Labels are a bold sans, not the pixel
  font.

## 4.12 States polish (2026-09-05), first item of the audit order

- All eight slots on one screen: a two-column grid of cells (screenshot,
  time, SAVE/LOAD, LOCK, UNDO), title "Save states · 8 slots".
- Auto-save: slot 0 (`autosave.bin`, its own name so it can never be
  confused with a numbered slot), written on ON_PAUSE and on leaving Play,
  silent, load-only through RESUME at the top of the dialog. On boot, a
  status line says an auto-save exists and where RESUME is.
- Locked slots: a `.lock` marker; SAVE is refused with a message, the
  cell says LOCKED.
- Backups: every overwrite of a numbered slot keeps the previous state
  (+ thumbnail) as `.bak`; UNDO swaps it back and keeps the replaced one
  as the new backup (StatesPolishTest).
- Quick actions: KEYS has an EMULATOR ACTIONS list (quick save, quick
  load, fast forward as a hold, open save states). A key or pad button
  bound to an action is taken in dispatchKeyEvent before the core sees it,
  and loses any game button it drove; binding a button takes it back
  (KeyBindings actions, QuickActions handlers set by Play).

## 4.11 Emulator parity audit (2026-09-05)

Blake: "a real audit, because you have only 4 save states, and you have no
idea what the other emulators do." Sources read: My Boy! and My OldBoy!
(Play Store descriptions via mirrors), Pizza Boy A Pro, DraStic (guide +
store copy), Delta (README + FAQ), Lemuroid (README), melonDS Android
(README, releases, Android Authority). Cores checked by dumping their
option strings: melonDS 72 options, mGBA 17, Gambatte 33; LibretroDroid
exposes frameSpeed (int), shaders (Default/CRT/LCD/Sharp/CUT), rumble,
microphone, variables, cheats, memory read/write.

Status key: HAVE / PARTIAL / MISSING (effort S/M/L) / WON'T (reason).

| Feature (who has it) | KaizoCore |
|---|---|
| Save states with screenshot, many slots (all) | HAVE: 8 with thumbnails. Dialog scrolls; only 4 visible at once, which read as "only 4" - make all 8 visible (S) |
| Auto-save on exit + resume (Lemuroid, Pizza Boy) | MISSING (S) |
| Locked slots, auto backup (Delta) | MISSING (S) |
| Quick save/load on a controller button (Delta, Pizza Boy) | MISSING (S) - KEYS maps game buttons only |
| Fast forward (all) | HAVE 16x GBA/GB, 4x DS (melonDS crashes above) |
| Slow motion (My Boy, Pizza Boy) | MISSING (M) - frameSpeed is an integer; needs a fractional path |
| Rewind (Pizza Boy, melonDS Android) | MISSING (M) - ring buffer of states, ~30MB for 60 GBA states |
| Cheats, multi-line, toggle live (all) | HAVE; never on tracked games |
| Bundled cheat database (DraStic) | WON'T bundle; import of .cht files possible (S) |
| Controller support + remap (all) | PARTIAL: game buttons yes, emulator actions no |
| Layout editor: position/size/opacity per orientation (all) | HAVE (opacity landscape only); per-game layouts (DraStic) MISSING (S) |
| Controller skins (Delta, Pizza Boy) | MISSING (M) - next slice |
| Video filters/shaders LCD/CRT (My Boy, Pizza Boy, Lemuroid) | MISSING in UI (S) - LibretroDroid already ships CRT/LCD/Sharp/CUT |
| DS 3D upscaling 2x/3x (DraStic, melonDS) | UNKNOWN: our melonDS build has render_mode/opengl_filtering but no resolution option in its strings; needs a core check (M) |
| GB palettes, SGB palettes, colour correction (My OldBoy) | MISSING in UI (S) - gambatte_gb_internal_palette, gb_colorization, gbc_color_correction; mGBA gb_colors |
| Link cable GB over Wi-Fi (My OldBoy) | MISSING (M) - Gambatte has gb_link_mode network server/client |
| Link cable GBA (My Boy, Pizza Boy) | WON'T with mGBA libretro (no link); would need a different core build |
| DS local multiplayer (melonDS Android missing too) | WON'T for now - melonds_network_mode exists but needs slirp plumbing |
| Sensors: tilt/gyro/solar, rumble (My Boy, Pizza Boy, Delta) | PARTIAL: rumble plumbing exists; solar via mgba_solar_sensor_level (S); tilt/gyro need the libretro sensor interface in LibretroDroid (M) |
| Game Boy Camera / Printer (My OldBoy) | WON'T - Gambatte has neither |
| IPS/UPS patching (My Boy, My OldBoy) | HAVE, plus BPS, matched by CRC |
| Zip / 7z ROMs (Pizza Boy, Lemuroid) | MISSING: zip (S), 7z (M, needs a library) |
| Google Drive sync (My Boy, Pizza Boy, DraStic, Delta) | HAVE (4.21): one linked document via the picker, rewritten on leaving a game, restore with confirmation |
| Custom BIOS (Pizza Boy) / HLE BIOS (My Boy) | PARTIAL: mGBA HLE works; gba_bios.bin import + mgba_use_bios MISSING (S); DS runs built-in firmware, native firmware import MISSING (S) |
| RetroAchievements (Pizza Boy, melonDS Android) | HAVE (4.20): rcheevos rc_client, login with token kept, hardcore, per-frame memory read; real unlock owed to a real account |
| DSi mode (melonDS) | MISSING (M) - melonds_console_mode dsi + NAND/firmware from the player's own console |
| DS microphone (Delta, melonDS) | MISSING in UI (S) - enableMicrophone + melonds_mic_input exist |
| Frameskip / battery options (DraStic, mGBA) | MISSING (S) - mgba_frameskip, threaded renderer |
| Hold button / turbo buttons (Delta) | MISSING (S-M) |
| Screenshot to gallery | MISSING (S) - PixelCopy is in |
| Audio options: low-pass, interpolation, bit depth | MISSING (S) - core options exist |
| Real-time clock control (melonDS) | MISSING (S) - start_time options exist |
| Library with box art / shortcuts (Delta, Lemuroid, My Boy) | PARTIAL: library yes; art from save screenshots (S) |
| In-game quick menu (DraStic) | HAVE (FILE row / chip strip) |
| Android TV (DraStic) | WON'T |

Order proposed: states polish (visible 8, auto-save/resume, lock, quick
save mapping) -> skins -> a per-console Emulator Settings page (filters,
palettes, BIOS, mic, frameskip, audio, RTC, solar) -> rewind + slow motion
-> zip import + backup export -> sensors/rumble -> GB link over Wi-Fi ->
DSi -> RetroAchievements -> Drive sync.

## 4.10 Save states with screenshots (2026-09-05)

Eight slots per game (`StateSlots.COUNT`), slots 1..3 on the paths the app
always used. FILE > STATES (and the STATES chip in landscape) opens
`SaveStatesDialog`: each row has the screenshot taken at save time, the
save time and size, SAVE and LOAD; tapping a row makes it the quick slot.
The thumbnail is a PixelCopy of the game SurfaceView at save (240 wide,
PNG beside the state, `stateN.png`); a failed copy leaves no thumbnail and
the row shows a dash. The stamp gate (a state loads only against the run
or library id it was taken from) is unchanged and applies to every slot.

## 4.9 Layout editor (2026-09-05)

`PadLayout` is where the controls sit: per orientation and console
(`prep/layouts/<landscape|portrait>-<gba|gbc|nds>.properties`), each element
(D-pad cluster, A, B, L, R, Select, Start) as a centre in fractions of the
pad area plus its own scale; the pad's opacity (landscape); and the melonDS
screen layout for that orientation. `FreePad` renders from it in both
orientations, replacing the fixed OverlayPad and Pad (both still in the file,
unused). The defaults reproduce the shipped arrangements.

FILE > EDIT LAYOUT (or the LAYOUT chip in landscape) enters edit mode: a
handle over every element takes the drag (move) and the tap (select, gold
frame); the toolbar offers SMALLER / BIGGER for the selection, FADE (landscape
opacity, cycles), SCREENS (DS: cycles melonDS's layouts), RESET and DONE.
Buttons send no keys while editing. Back also saves and leaves.

DS: the stylus mapping now follows the chosen layout through a small
geometry table (aspect + touch-screen rectangle). hybrid-top keeps the
tuned mapping; top-bottom, bottom-top, left-right, right-left and bottom are
the core's plain arrangements; top and the rotations have no touch screen.
The screen gap is in the model but not exposed, because it shifts the
touch rectangle and there is no DS dump on the emulator to measure it.
Unverified on a DS game for the same reason.

## 4.8 Cheat engine (2026-09-05)

`CheatStore` keeps a per-game list (`prep/cheats/<session id>.tsv`: enabled,
name, code). FILE > CHEATS opens `CheatsDialog`: toggle, add (name + code,
multi-line), remove. Every change saves and re-sends the whole list to the
core (`resetCheat` then `setCheat` per enabled code), and the same happens
once the core is up (after FrameRendered, beside speed and audio), so disk
and core never disagree. Codes are normalised (`CheatStore.normalise`):
trimmed, blank lines dropped, hex uppercased, lines joined with '+', which
is how mGBA (GameShark/CodeBreaker/AR), Gambatte (GameShark/Game Genie) and
melonDS (Action Replay) all read a multi-line code through libretro.

**Never on a tracked game.** `CheatStore.allowed(session)` is `!tracked`;
the button reads CHEATS OFF and explains. The IronMON run is tracked, so a
stream can never be questioned; hacks, other games and unverified dumps can
cheat freely. No code database is bundled: the player's codes are theirs.

LibretroDroid already carried `setCheat`/`resetCheat` natives; the
GLRetroView wrapper for reset was missing (added) and both native calls now
guard a destroyed core like the serialize paths do.

## 4.7 The library, organised (2026-09-05)

Blake: "a really nice UI and file explorer for the users roms, patches, rom
hacks etc... clean roms, patched... easy to rename and naming suggestions...
they won't be able to accidentally put a patch on a game that doesn't match."

`LibraryStore` now shelves every ROM by identity (Category): CLEAN (CRC
verified, or header-matched with the CRC not pinned yet, labelled
unverified), PATCHED (a verified Nat. Dex build), ROM HACKS (a Pokémon game
that is not a clean dump, or anything APPLY produced that is not a known
build), OTHER (a game the app does not know). Patches live in
`library/patches/` with a sidecar naming the format, the CRC they are FOR
and the CRC they MAKE. BPS and UPS say so themselves; an IPS cannot, so the
import asks which clean ROM it is for and pins it to that CRC. PATCH on a
ROM lists only patches whose CRC matches; APPLY on a patch lists only ROMs
it fits; `apply()` refuses a mismatch even if the UI is bypassed
(LibraryOrganizeTest). Provenance (base + patch) is kept on the result and
shown as its subtitle. RENAME keeps the extension, moves the sidecar,
follows the selection, and offers names built from what the file is
("Pokémon Emerald (U) clean", the Nat. Dex build name, "<patch> on <base>",
the header line). One ADD FILES button takes ROMs and patches together.

Verified on the emulator: three shelves populated correctly from the
existing files, the Nat. Dex BPS imported as "For: Pokémon Emerald (U)"
with APPLY (1), PATCH (1) on the clean Emerald listing it and PATCH on the
Nat. Dex build listing nothing, rename suggestions as designed.

## 4.6 Streaming (2026-09-05)

Blake: the app must be something people live stream from, each screen its
own OBS source, like the PC stack. Research (besteon wiki "Streamer Setup
Tips", Stream Connect guide, IronMonVS setup): on PC it is two cropped
captures of the BizHawk window, an attempts text file, and files that
Streamer.bot polls. OBS cannot see a phone, so KaizoCore does it the other
way round: the phone SERVES the sources over Wi-Fi and scrcpy carries the
game over USB.

**Built:**
- `app/.../stream/StreamServer.kt`: hand-written HTTP/1.1 GET server with
  Server-Sent Events. Routes `/`, `/tracker`, `/state.json`, `/events`,
  `/attempts`, `/dex.json`; every route needs `?k=<token>`. Port 8642.
  Started and stopped from the FILE menu (STREAM); lives in `StreamHub`
  (process-wide) so tab switches do not drop OBS's connection. INTERNET
  permission added for this alone; the app still makes no outbound call.
- `StreamSnapshot.kt`: one JSON object per tick from TrackerState or
  NdsTrackerState plus the run's notes. Obeys the information rule: the
  enemy carries marks, moves SEEN and the ability guess, never real stats
  (StreamSnapshotTest asserts the real attack value is absent from the JSON).
  `dex()` is the post-game browser's data: every species as randomized
  (types, stats, abilities, learnset, evolution, weight) from the GBA
  tracker's ROM tables; DS gives names and move levels only for now.
- `assets/stream/tracker.html`: the stream tracker, ONE box always (Blake's
  requirement, unlike the app's swap/stack panel). The enemy's card overlays
  the player's page in battle (red frame, WILD/TRAINER tag, team balls,
  marks, seen moves, "vs <lead>"), and drops away after. After game over
  the box becomes RUN OVER/WON with two tabs: final party, and the
  randomizer data browser (search any species, see its randomized data;
  the PC log viewer's job). `?w=` sets the width; `?demo=1|battle|over`
  renders a fixed sample for layout work only (the server has no fake path).
- CLEAN VIEW (FILE menu / CLEAN chip): nothing on screen but the game. Pad,
  chips, tracker pane, facecam and status all hidden; Back leaves it. DS
  keeps the core's own layout (hybrid in landscape, stacked in portrait),
  so both screens are in the capture; 1 SCREEN still applies.

**Not built, and why:** in-app RTMP (route B) - the phone streaming apps do
it; Streamer.bot command parity - next, the same command names over HTTP;
IronMonVS posting - needs WaffleSmacker; DS dex with full stats - needs the
NDS tracker to expose base stats per species. Verified on the emulator only
with a run at the intro (no party) plus the demo layouts; a party on screen
and the enemy overlay live are still to be seen on Blake's phone.

## 5. Order of work

1. **Bugs first** (1.1, 1.3, then 1.2's log). Small, and 1.1 is the same
   family as a defect we just fixed - leaving it would be negligent.
2. **Architecture** (0.1-0.3). Nothing in section 4 is possible without it and
   sections 2-3 get cheaper after it.
3. **Tracker clones** (3.1-3.5). Each is small and each has a named reference.
4. **Play screen** (2.3 game over, then 2.2 floating tracker, then 2.1 skins).
5. **Rulesets and the mode picker** (0.3 + the picker) - unlocks value early
   because it works for the Gen 3 games we already support.
6. **New platforms** (section 4), one at a time, reference first, HGSS before
   Gen 5 before Gen 1/2 because it reuses the most.

## 6. Rules that do not change

- No ROMs bundled, hosted, or downloaded. Ever.
- Never mix engines across ROM kinds.
- The tracker is a clone. Interior changes cite a file in `~/ironmon-ref/`.
- No change lands without a rendered screenshot and green tests.
- A check that cannot fail is not a check (three of those bit me this week).
