# KaizoCore UX audit and player study (rc32, 2026-09-30)

## How it was done

Seven studies ran at once on 30 September 2026:

- **Five players, each walking the app with their own goal:**
  - a first-time IronMON player who knows it from streams;
  - a PC IronMON veteran;
  - a Nuzlocke player (vanilla, then randomized);
  - a casual player with a ROM hack;
  - a streamer with OBS.
- **One expert review:** Nielsen's ten heuristics, tap targets, contrast, screen reader, copy.
- **One desk study** of what players want and where setups fail. Sources: the PC tracker wiki and issues, IronMon Emu, Nuzlocke tools and the Nuzlocke University survey, emulator store reviews, streaming guides.

**Material:**
- 62 captures of every screen, with text dumps, in `screens/`.
- The source, the install page, and the PC tracker source in `ironmon-ref`.

**Limits:**
- Nobody played a real game for this.
- The tracker values in captures 53 to 62 are the staged demo. "Attempt 37" there is `Demo.ATTEMPT`.
- Reddit and Discord could not be read.
- Findings marked **(checked)** were confirmed in the code while writing this. The rest were read from code by the studies and need one look on a phone.

## What players want, ranked

1. **Install to first battle in minutes, on the phone alone.** KaizoCore: partly. It is one app with a short path, but four labels add a file and nothing says what a dump is.
2. **The official rules and settings applied for them.** KaizoCore: well. Official strings, Nat. Dex sets, two-pass levels, wrong patches refused in plain words.
3. **A tracker that reads the game live and matches the PC one.** KaizoCore: well for Gen 1 to 5. Missing: GachaMon, tap-a-trainer, extensions.
4. **Restart in seconds, and keep the count.** Winners needed 1,786, 3,978 and 8,502 attempts. KaizoCore: well, with the next run staged in the background.
5. **Never lose a run or a save.** KaizoCore: partly. Strong inside the app, weak against an uninstall or a new phone.
6. **Controls that fit the device.** KaizoCore: partly. Fast forward, quick save and rewind are unbound on a controller.
7. **Stream with no cable or card, and let chat follow.** KaizoCore: partly. Not yet seen in a real OBS.
8. **A Nuzlocke that follows their rules.** KaizoCore: partly. Presets, dupes on by default, automatic encounters and deaths.
9. **More games, hacks and languages.** 61% of Nuzlocke players play hacks at least sometimes.
10. **Plain-words help, and trust.** KaizoCore: well on trust (free, no ads, no account, GPL), partly on help.

**Facts worth keeping in front of every decision:**
- **Nuzlocke games:** Diamond, Pearl and Platinum are the most played (75%). Emerald is second, Black 2 and White 2 third, FireRed and LeafGreen fourth. Gen 1 and 2 are in no top five.
- **Nuzlocke rules:** 95% use the dupes clause.
- **Nuzlocke behaviour:**
  - 62% do not finish most runs they start, so resume and a cheap restart matter.
  - 44% found Nuzlocking through a streamer.
- **Android:** developer verification starts today in Brazil, Indonesia, Singapore and Thailand, and goes global in 2027. Sideloading gets harder from here.

## Where each player quits

| Player | Quits when |
|---|---|
| First IronMON run | an unusual dump is shelved as a "ROM hack" with no next step; PLAY on the card gives the plain game; leaving the Play tab reboots to the title |
| PC veteran | the next seed opens with the last seed's save; the attempt count does not match the PC one; notes are lost |
| Nuzlocke player | File > Rules shows IronMON rules; the lead faints and an IronMON GAME OVER opens; a used area is not flagged until after the fact |
| Casual and hacks | the Play tab says "No run yet"; the auto-save is overwritten; a .7z is called damaged; FireRed 1.1 does not fit Radical Red and the reason is hidden |
| Streamer | OBS was opened first and shows three error boxes; the phone's address changed; a home swipe froze the stream with no sign |

## What works, and should stay

- **Onboarding copy:**
  - The Welcome screen: plain, honest, one clear button.
  - Checksum identification ("verified"), and plain refusal messages for bad patches, low space and damaged headers.
- **Starting a run:**
  - The randomize progress copy ("Twenty to forty seconds is normal").
  - Play opening by itself.
  - The next run made in the background.
- **Death and resume:**
  - The death card: team, killer, integrity line, announcer quote.
  - The Home Continue card.
  - Save states with thumbnails, Lock and Undo.
  - Crash resume.
- **Nuzlocke:**
  - The rules copy: every switch has one plain line.
  - Level caps read from the game's own trainers.
  - Real save detection.
- **Everywhere:**
  - Armed "SURE?" deletes.
  - Disabled buttons that say why.
  - Sticky footers.
  - No em dashes anywhere.

## Findings

Severity:
- **P0:** the player loses a run, a save or a record, cannot read or hit a control, or cannot get past the first run.
- **P1:** clear friction for most players.
- **P2:** polish.

### P0: before rc32 ships

**Runs and saves**

- **P0-1. The IronMON game-over popup, its "New game" tile and A+B+Start work in any tracked game.** (checked)
  - In a Nuzlocke, the lead fainting opens GAME OVER with "Retry the battle", which undoes a death.
  - "New game" rolls an IronMON seed and replaces the Kaizo run.
  - In plain play it pops GAME OVER at the first faint.
  - Code: `GameOverHost` has no session gate, `NewRunCombo.onFire` is armed for every session, and the loss latch is ungated.
  - Fix: the popup, the latch, the combo and NEW RUN act only in a Kaizo IronMON run. A Nuzlocke ends by its own rules.
- **P0-2. A new run keeps the last seed's in-game save.** (checked)
  - The run's battery save is one file per game (`saves/<game>.srm`, DS `current.sav`), and the confirm sells it: "Your in-game save is kept, so Continue starts straight into the new seed."
  - A save made after the starter puts the last seed's team, items and badges into the next attempt. The PC tracker's FAQ lists this as a fault.
  - Fix: a new seed starts from a clean save. The old one is kept aside as a copy, never deleted.
    - A save with no Pokémon in it (the intro skip) is kept, because it cannot carry a team.
    - "Keep my in-game save" stays as a choice in the confirm.
- **P0-3. Stat marks and notes are rewritten in place.** Eight files (`marks.txt`, `notes.txt`, routes, moves, abilities, encounters, safari, DS encounters) are truncated first, so a crash mid-write empties the run's notebook. Fix: `SafeWrite` for all eight.
- **P0-4. The auto-save is never offered back, and the next visit overwrites it.**
  - After Back or a tab switch the game boots to the title. A toast for 3 seconds names File > States > Resume.
  - The next pause, or the 3 minute timer, replaces the auto-save with the title screen.
  - Fix:
    - When a game starts and an auto-save exists, ask: "Pick up where you left off?" with Pick up and Start from the title screen.
    - Do not overwrite the auto-save from a fresh boot until the player has chosen.
- **P0-5. "Save this attempt" writes where nothing can open it, and the backup skips it.** `filesDir/attempts/` is not in `Backup.PREFIXES`. Fix: back it up. Listing and sharing saved attempts is P1.

**Nuzlocke**

- **P0-6. File > Rules and Tracker Setup > Rules open the IronMON rulebook in a Nuzlocke.** Fix: in a Nuzlocke game they open the ledger on its Rules tab.
- **P0-7. The Randomizer preset starts Kaizo, with levels up about 60%, and calls it Standard.** Fix: a "Nuzlocke fair" default:
  - wild Pokémon and trainers random but similar in strength;
  - levels as the game has them;
  - no wild legendaries.
  - The IronMON modes stay as a choice, each with its own line.
- **P0-8. NEW RUN in a randomized Nuzlocke drops the ledger.** Only the Nuzlocke screen makes a ledger, so the new seed has none and the panel vanishes. Fix: a new run of a Nuzlocke is a new Nuzlocke with the same rules.
- **P0-9. Wally's tutorial battle (Emerald) and the Old Man's (FireRed, LeafGreen) may count as the route's encounter.** They read as wild battles, and Wally's ends in a catch. Every Emerald run passes Route 102. Fix: tutorial battles are scripted, never an encounter.

**First run**

- **P0-10. Library opens on "Set up a game", with Nat. Dex preselected for FireRed 1.1 and Emerald.**
  - What is made there is listed only on the Kaizo screen.
  - Fix:
    - Open Library on the games list.
    - Preselect Standard ("the game as it is").
    - Rename the tabs "My games" and "Patched versions".
- **P0-11. A file the app cannot use is "Added", shelved as a "ROM hack", and Kaizo IronMON then says "No games yet".**
  - Fix: say which case it is (another language, another revision, changed), what does work, and that it still plays without a tracker.
  - A real game KaizoCore does not recognize goes on an "Other versions" shelf.
- **P0-12. The mode row opens on Standard on a screen named Kaizo IronMON.**
  - Ten chips carry no words, and Ultimate comes after Super Kaizo although the rules build Standard, Ultimate, Kaizo.
  - Fix:
    - Default to Kaizo.
    - Order the chips as the rules build.
    - Put one plain line under the chosen mode.
    - Add a "Read the rules" link.
- **P0-13. Empty states name the next place and give no button, and old names remain** ("the ROMs tab", "the Library tab"). Fix: each empty state gets the button it names.

**Controls and reading**

- **P0-14. RUN (31x17dp) sits 3dp from SEE MINE and flees the wild battle at once.** (checked) A mis-tap costs the encounter in a Nuzlocke. Fix: targets at least 44dp, apart.
- **P0-15. Tracker Setup and the Rules dialog use 6 to 8dp text that ignores the phone's font size.** Fix: 12sp minimum for labels.

**Streaming**

- **P0-16. OBS opened before the phone leaves three dead sources.** The scene writes `restart_when_active: false`. Fix: true, plus a first line under "If nothing shows".
- **P0-17. The stream tracker prints the last Kaizo run's attempt in any tracked game.** Fix: print the attempt only in a run.
- **P0-18. The phone's address changes, and the guide's cure (import again) wipes the layout.** Fix for rc32: the guide says to paste the new address into each source. The app noticing the change is P1.
- **P0-19. A home swipe or the power button freezes the stream with no sign.** Fix for rc32:
  - The guide says KaizoCore has to stay in front.
  - A notification while the stream is on brings the player back.
  - A heartbeat and a status page are P1.

### P1: next

**Runs**
- **Game Over card:** "Start attempt N" as the filled tile, with "Keep playing this run" secondary. When no run is staged, start making it as the popup opens.
- **One verb per action:** Start for new, Continue for in progress, Play for a file. Today ten labels mean "go".
- **Attempts:**
  - Counted per game and settings file.
  - Editable, so a PC count carries over.
  - An abandoned run is filed as "Ended by a new run".
  - The last attempt's notes and log are kept until the next one ends.
- **The Kaizo screen says what the next attempt is:** "Attempt 15 · Pokémon Emerald (U) · RSE Kaizo, official · 60% levels on · ends when your lead faints". An edited settings file shows in the danger colour with "Use the official file".
- **Imported settings files** are selected and named ("Your file: RSE Kaizo (2)").
- **The mode and attempt show in Play's top bar.** In portrait they show nowhere today.

**Plain play and saves**
- **Library play is plain.**
  - The tracker, Rules, the attempt number and the rewind and cheat limits belong to runs and Nuzlockes.
  - A switch turns the tracker on for a recognized game.
- **The Play tab with nothing chosen** shows recent games and Open Library.
- **Back in Play asks** before leaving the game.
- **Saves:**
  - A Home card until the first backup: "Your saves are only on this phone."
  - Export and import of a game's .sav.
  - A safety copy before Restore.
  - Sync saves only by default.
- **A DS game's in-game save follows its file name**, so a rename or a re-add under another name loses it. Verify, then key it to the game, not the file name.
- **Speed and sound:**
  - Fast forward one tap away, and a visible "4x" and mute state.
  - Each game starts at 1x with sound unless the player asks to remember.
- **Controller:**
  - Defaults for fast forward (R2) and the menu.
  - A Controller section above Keyboard.

**Play screen**
- **File menu:**
  - Sixteen same-weight buttons, grouped as Save states, Game and Tools.
  - Renames: "Save state" and "Load state", "Reset game".
  - Close it after an action.
- **Tracker Setup:**
  - Basics first, each with one line: this run ends when, ball picker, move effectiveness, catch rate, landscape position.
  - The rest folded under "More options".
- **Portrait tracker:** hide the bottom bar on Play to give the tracker about 45% more room.
- **Stat marks:**
  - Stat rows are 15dp tall.
  - A long press opens big buttons.
  - A one-time hint says the boxes cycle + - =.
- **Landscape:**
  - The floating tracker covers the chip strip.
  - The Nuzlocke line collides ("Standa1 alive").
  - The chips are 2.1:1 over bright scenes.

**Nuzlocke**
- **A verdict line in every wild battle,** for example:
  - "Counts. First encounter on Route 111."
  - "Skip. Dupe."
  - "Used. Zubat already used up Route 111."
- **Level caps:** warn on the overworld before the gym ("Marshtomp is Lv 17, over the cap of 15. Box it before the gym.").
- **Warnings:** in words ("2 warnings"), with the newest in the strip.
- **Reloads:**
  - Log every save-state load, restore point, Retry and Restart.
  - A "Save states" rule: Allowed, Logged, Crash resume only.
- **The ledger:**
  - Remove a Pokémon that never was, with one-step Undo.
  - A searchable species picker.
  - 44dp targets.
  - A "Tap a line to fix it" hint.
  - A LEDGER chip on the strip.
- **The rules on the start screen:**
  - A rule card on the start screen instead of a fold.
  - Clause names as the community says them ("Gifts use up an area", "Set battles use up an area", "Rules begin at your first Poké Ball").
  - Presets add their switches instead of resetting the player's.
- **Starting and ending:**
  - An existing save is a decision: "Start from this save" or "Back up, then start fresh".
  - Whiteout and Champion get real cards with Share, Open the ledger and Start a new Nuzlocke.
- **Randomized Nuzlocke:**
  - A property of the game ("The game as it is" or "A randomized copy") for every preset.
  - "How random": Nuzlocke fair, Your builds, IronMON modes.
  - Build your own opens from Nuzlocke with "Catching" options.
- **Genlocke says honestly** that survivors are a list the player carries by hand.

**Files and hacks**
- **Library as a shelf:**
  - Game names as titles, with the file name only in Rename.
  - A tile picture from the player's own last screen.
  - Recent first.
  - Patch, Rename and Delete behind a menu.
- **Words:**
  - "Original game", "Patched game", "Tracker works", "No tracker".
  - A .7z or .rar gets "KaizoCore opens .zip only. Unpack it first."
  - The empty state lists .gb.
- **ROM Hacks starts from the patch:**
  - It says which base version the hack needs and whether you have it.
  - "Radical Red needs FireRed 1.0 (US). Your FireRed is 1.1, so it will not fit."
- **"Which dumps does the tracker know?"** on the games page, listing name, release and checksum. It names nothing to download.

**Streaming**
- **A Stream sheet that stays open:**
  - The address with Send to my PC and Copy.
  - A live "OBS connected" dot.
  - "Your phone's address changed" when it did.
  - A confirm before stopping while OBS watches.
  - Plug-in and 5 GHz notes.
- **The guide:**
  - Two paths, one for people who already have scenes and one for new OBS users.
  - A test link that opens the picture in the browser.
  - The file name that downloads.
  - "What you should see".
- **The scene and sound:**
  - A 720p scene, and `&int=1` on the game source.
  - The echo advice is about the mic hearing the phone, not about hearing it twice.
  - The stream sends silence when the phone's own sound is off (fast forward).
- **A Nuzlocke source for OBS:** alive, dead, area, team, graveyard.
- **Labels:** "Cam (phone screen only)" and "Clean view (hides the buttons)", with a first-use confirm.

**Help and access**
- **"How it works"** covers every mode, the tracker, save states and backup, and shows on Home until the first game is played.
- **A "Coming from the PC tracker" card:** A+B+Start, settings file import, attempts, game over rule, colours, notes.
- **Accessibility:**
  - Repeated buttons carry their game's name for the screen reader.
  - Radios and checkboxes expose their state.
  - The ledger X reads "Close".
  - Toasts are announced.
- **Tracker themes:** 13 of 15 have a text colour under 4.5:1. Offer an opt-in "Readable colors" lift, and leave the PC presets as they are.
- **Theme import** accepts the PC tracker's eleven-colour code.
- **A run timer** for GBA and Game Boy runs, not only DS.

### P2: polish

- **Home:**
  - The Continue title is a file name.
  - The links row repeats the bottom bar.
  - Put Kaizo IronMON first.
- **Spelling:**
  - "Pokemon" and "Poke Ball" without accents in Home and the ledger.
  - "colour" and "color" mixed.
- **Wording:**
  - "Ended the most runs" on Your stats reads as if the player did.
  - "(playing)" in Library means "open in Play". Make it a badge.
- **Two red primaries on the Kaizo screen:** only the one that continues should be red.
- **Build your own:**
  - The top-bar arrow drops five steps with no question.
  - It should start from the mode that was chosen.
- **Tracker Setup tools** close Setup instead of returning to it.
- **Emulator settings:** "Play as your Pokémon" leads a list of jargon. Put picture options on top and the rest under Advanced.
- **"Blake" in the crash copy** needs "who makes KaizoCore".
- **Layout:**
  - Margins differ between tabs (16dp against 10dp).
  - Four text-field styles.
  - Red does three jobs.
- **Haptics** on every press, with no setting.

## How a player should set up

**A first Kaizo IronMON run**
1. Install, open, "Add your games", pick the dump. The app says what it found and whether the tracker reads it (P0-11).
2. Home, Kaizo IronMON. The game is selected, Kaizo is the default, one line says what Kaizo means, and "Read the rules" is one tap away (P0-12).
3. Start. Wait 20 to 40 seconds. Play opens.
4. New game on the title screen. Pick a ball when the tracker says so. Rotate the phone for a bigger tracker.
5. When the run ends:
   - see what killed you;
   - start the next attempt in one tap;
   - the next seed starts from a clean save unless you kept a save made before the starter (P0-2).

**A Nuzlocke**
1. Home, Nuzlocke, a preset (Standard if unsure), the game. Read the rules card.
2. A game with a save: choose "Start from this save" or start fresh.
3. For Hardcore, set Battle Style to Set in the game's own options. The ledger warns if it is Shift.
4. Tap the strip for the ledger. File > Rules shows the Nuzlocke rules (P0-6).
5. Randomized: pick "A randomized copy". Nuzlocke fair is the default, and NEW RUN keeps the rules (P0-7, P0-8).

**Just play, or a ROM hack**
1. Add games, tap the game, play. Save in the game as usual. KaizoCore writes it on pause and exit.
2. Come back: "Pick up where you left off?" (P0-4).
3. A hack: Home, ROM Hacks, add the patch. It says the base version it needs. Patch it, then Play.
4. Back up: More, Backup, Google Drive. Do it before a new phone or an uninstall.

**Stream to OBS**

Once, at your desk:
1. Phone and PC on the same Wi-Fi: not a guest network, no VPN. Plug the phone in and turn on Do Not Disturb.
2. On the phone: a game, File, Stream. Open the address on the PC. Press "Open the picture in this tab" to test.
3. New to OBS: import the scene. Already have scenes: add the three addresses as browser sources.
4. Wear headphones on the phone or mute it, so the mic cannot hear the game.

Every time:
1. Phone first, then OBS.
2. A source that shows an error box: switch scenes and back.
3. Keep KaizoCore in front the whole stream.

## Words

| Concept | Use | Retire |
|---|---|---|
| a thing you play | game (the file is a game file) | ROM in prose, dump, build for a game |
| unmodified copy | original game | clean ROM, clean dump, verified dump, vanilla |
| changed by a patch | patched game | "Patched" and "ROM hacks" as two shelves |
| the tracker reads it | Tracker works / No tracker | verified, tracked, plays untracked |
| bringing a file in | Add | Choose ROM, Import, Replace |
| going | Start (new), Continue (in progress), Play (a file) | Randomize, Start this game, Resume, NEW RUN |
| difficulty | Mode (Kaizo IronMON), Preset (Nuzlocke) | ruleset, Randomizer mode, settings file |
| saving | Save state, Game save, Auto-save, Backup | States slot 1, Quick save, battery saves |
| menus | Menu (the game menu), Tabs (landscape) | File, More, MENU |

## Fixed in rc32

Each P0 with the commit that fixed it and what proves it. "Emulator" means walked through on the rc32 build; the
tests are in `app/src/test`. The IronMON rules check that followed this audit is in
[../IRONMON-RULES-CHECK.md](../IRONMON-RULES-CHECK.md).

| Item | Fix | Commit | Evidence |
|---|---|---|---|
| P0-1 popup, New game, A+B+Start in any game | Only in Kaizo IronMON runs (PlayRules) | 7dd5d5a | PlayRulesAndSavesTest; emulator: no popup in a Nuzlocke |
| P0-2 new run keeps the old save | Clean save; the intro skip kept; a team's save never kept | 7dd5d5a, 688a72f | PlayRulesAndSavesTest; emulator: the confirm's save line |
| P0-3 marks and notes rewritten in place | Every file through SafeWrite | 7dd5d5a | StatMarksTest |
| P0-4 auto-save never offered back | Coming back opens the moment you left (its left mark), used once; a crash resume is logged | 7dd5d5a, 688a72f | CrashResumeTest; emulator walk pending |
| P0-5 saved attempts not backed up | `attempts/` in the backup | 7dd5d5a | BackupCoverageTest |
| P0-6 Rules opens the IronMON rulebook in a Nuzlocke | Opens the ledger's Rules tab | 7dd5d5a | NuzlockeWiringTest; emulator |
| P0-7 Randomizer preset is Kaizo | "Nuzlocke fair" default | 8673d40 | NuzlockeFairTest |
| P0-8 NEW RUN drops the ledger | Same rules, new ledger | 7dd5d5a | PlayRulesAndSavesTest |
| P0-9 tutorial battles count | Not read, as the PC tracker does | 7dd5d5a | GbaTrackerNuzlockeTest, AddressAuditTest |
| P0-10 Library opens on Set up | Opens on My games; Standard preselected (the growth patch for Gold, Silver, Crystal) | b2db34b, 688a72f | LibraryWiringTest, PrepOptionsTest; emulator |
| P0-11 unusable file shelved as a hack | Says which case and still plays | b2db34b | LibraryWiringTest, LibraryShelvesTest |
| P0-12 mode row opens on Standard | Kaizo first, rules order, a line per mode, rules link | 6fd92e8 | RunScreenKaizoTest; emulator |
| P0-13 empty states without buttons | Each has its button | 88d5b40, 2e44f95 | HomeWiringTest, PlayNothingTest |
| P0-14 RUN too small and too near | 44dp, asks twice | 31604c3 | TrackerTouchTest |
| P0-15 tiny text in Tracker Setup | 12sp minimum, follows font size | 31604c3 | TrackerTouchTest; emulator |
| P0-16 dead OBS sources | restart_when_active | 1cb2e6a | ObsSceneTest |
| P0-17 attempt shown in any game | Only in a run | 1cb2e6a | StreamAttemptsTest |
| P0-18 changed address wipes the layout | The guide: paste the new address into each source | 1cb2e6a | StreamGuideTest |
| P0-19 stream freezes with no sign | The guide says to keep KaizoCore in front, and a notification brings the player back | 1cb2e6a, 13f6410 | StreamGuideTest, StreamReminderTest |
