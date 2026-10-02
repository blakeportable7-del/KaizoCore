# KaizoCore release notes

Short and plain, one list per release. Newest first.

## rc33 (2 October 2026)

**New**

- **The tracker has KaizoCore's own look:** rounded cards, type and status pills, an HP bar and pill buttons. Your color theme still sets every color.
- **Move names in their type's color, with a type symbol** before each one, on every game. The symbols are the DS tracker's.
- **No wasted space:** on a phone in portrait the whole card fits on the screen, with the moves beside the stats. SETUP no longer takes a row of its own.
- **Weather on Game Boy Advance games:** rain, sun, a sandstorm or hail shows under the battle banner while it lasts.
- **Search the randomizer log:** it suggests Pokémon as you type, and one typo is forgiven. Every page of the log is easier to read.

**Fixed**

- **B to run** only runs from the battle menu (Fight, Bag, Pokémon, Run). B in the Bag, the party screen or the move list goes back again, on Game Boy Advance and Game Boy games. On DS games B is only B: the tracker's RUN button still runs.
- **Crackling sound** on Game Boy Advance games.
- **Black 2 and White 2 crashed at every launch** when the last auto-save was taken at the wrong moment. They open where you left off now.
- **After a switch, the tracker shows the Pokémon on the field**, not your first party slot. Heals, Calc Atk and the move matchups follow it.
- **Saves:**
  - Two quick saves to one slot could delete it.
  - A save that fails on a full phone says so.
  - Each DS game keeps its own save: a HeartGold run no longer opens on the last Platinum run's save.
  - UNDO brings a slot back with its own run.
  - Save states no longer use more memory the longer you play.
  - Time Machine no longer keeps every restore point after a restore, which could run the phone out of memory.
- **Runs:**
  - NEW RUN starts one run, however fast you tap.
  - A run made while Play is open boots on its own, instead of being written into the game you were playing.
  - Turning off "Get the next run ready" no longer deletes the run being installed.
  - A randomized Nuzlocke asks before ending a run in another game.
  - A blackout in Red, Blue or Yellow ends the run.
  - On DS games an egg no longer keeps an "entire party faints" run alive.
- **Backups and cloud sync:** a restore is all or nothing and restarts the app at once; cloud sync never leaves a half-written copy; a backup picked for restoring is linked only once the restore goes through.
- **Patches** work on Android 8 to 12, and a patch copied out of the app only part way is copied again.
- **32-bit phones:** the app no longer installs where no game could play.
- **Cheats** are no longer lost after a Nuzlocke, and hardcore mode turns them off.
- **RetroAchievements:** achievements no longer all show as unsupported.
- **Your key bindings** work from the moment the app starts.
- **The docked facecam** lets go of the camera when you turn it off.
- **A game the emulator refuses** says so, instead of a black screen.
- **Dual-screen handhelds:** the starter's info no longer opens on the second screen, where it crashed.
- **A big DS game loading** no longer freezes the app's tabs.
- **Streaming** no longer risks a crash while it reads your stat marks.

**Known issues**

- DS double battles are not tracked.
- Gold, Silver and Crystal Survival: the 7 Kanto heals are added by hand.
- Black 2 and White 2 use about 1 GB of memory.
- The DS core leaks a little memory on each load.

## rc32 (1 October 2026)

**New**

- **A new app icon:** the cracked Poké Ball.
- **Home screen:** Play any game, Kaizo IronMON, Nuzlocke and ROM Hacks, with a Continue card for where you left off.
- **Nuzlocke mode** on every game the tracker reads (Red to White 2). Pick a preset, or change any rule.
  - The tracker keeps the ledger: encounters, catches, deaths and level caps.
  - It warns when a rule is broken and never stops the game.
  - A randomized Nuzlocke starts from "Nuzlocke fair": wild Pokémon and trainers random but close in strength, levels as the game has them. IronMON modes are still there if you want them.
- **Build your own:** a custom randomized game with any starter you pick. It is a custom game, not an official IronMON mode, and the app says "(custom)" wherever it names the mode.
- **Stream to OBS over Wi-Fi:** the game's picture and sound, the tracker and the attempt counter as browser sources, with a step-by-step setup page.
- **Tracker themes:** the PC tracker's color presets, your own, and an image behind the tracker.
- **Play as your lead Pokémon** or your own sprite, Gen 1 to 9. Optional, off by default.
- **Animated Pokémon on every tracker:** the Walking Pals icons now cover Gen 4 to 9 and the Nat. Dex forms, on Nat. Dex builds and on the DS tracker as well as on Gen 1 to 3. The same two Tracker Setup switches turn them on and let them walk. A Pokémon nobody has drawn yet keeps its still picture.
- **FireRed and LeafGreen dungeon maps** for seven dungeons, by Bill Greenwald (doctrDNA), used with permission.
  - Off by default: turn them on in Tracker Setup.
  - They show routes and item spots, the same in every game, and are not part of the PC tracker.
- **Smaller additions:**
  - Custom game-over lines (UTDZac's Death Quotes).
  - Career stats.
  - The Black 2 and White 2 tracker update from NDS tracker 6.3.11.

**Easier to use**

- **Library:**
  - It opens on your games.
  - A file the tracker cannot read says why (another language, another version, a changed game) and still plays.
- **Kaizo IronMON screen:**
  - It opens on Kaizo, and the modes are in the order the rules build.
  - Each mode has a one-line summary taken from the official rules, with a link to all of them.
- **Buttons that lead somewhere:** empty screens have a button to the next step, and start buttons name the attempt they start.
- **Windows close with an X** at the top right, as the game-over popup does. The tracker's own windows keep the PC tracker's look.
- **A and B in portrait** sit on the GBA's diagonal, B lower left of A, as on other GBA emulators. If you moved your buttons with Edit layout, your layout is kept.
- **Play as your Pokémon** asks only what it needs. With your lead Pokémon there is nothing to pick: party slot 1 decides, and you are the trainer until you have a Pokémon. The Pokémon list shows only under Always use, where it lists the names that start with what you type first, and the picture and sheet buttons only under Your own sprite. The walking sprites cover Gen 1 to 9 and the Nat. Dex forms, and Always use lists every Pokémon that has one. A few later ones have none yet: with one of those in the lead you stay the trainer, as the line under the switch says. Who you play as is one of three, drawn round, and tapping Always use or Your own sprite again goes back to your lead.
- **Patched versions from the games you already added:** PATCH on a game in My games lists what KaizoCore can make of it (Nat. Dex, the growth patch, Faster, Smart AI, Super Kaizo), FireRed 1.1 and Emerald have a NAT. DEX button that says what the Nat. Dex version adds, Kaizo IronMON offers to make it under the game, and Patched versions lists your games before the phone's files. PATCH also has an ADD A PATCH FILE button. Your own copy is never changed.
- **Tracker Setup** offers only switches that work on the game you are playing: five Gen 1 to 3 switches that did nothing on DS games, and COVERAGE CALC on Game Boy games, are gone there.
- **Gold, Silver and Crystal:** Prepare picks the growth patch the IronMON rules ask for. If a copy on the Kaizo IronMON screen lacks it, the screen says so and makes the patched copy in one tap.
- **Tracker controls:**
  - RUN asks for a second tap, so a mis-tap does not throw away an encounter.
  - Tracker buttons are easier to hit.
  - Tracker Setup and the rules follow your phone's text size. The PC tracker's option names are kept.
- **Stream kit:**
  - OBS picks the sources back up after being opened before the phone.
  - The setup page covers people who already have scenes.
  - Nothing is written over the game picture on air.

**Fixed**

- **The app no longer stops responding when the game's picture stalls.** A player's AYN Thor reported "KaizoCore
  isn't responding" (a freeze, not a crash). The app now gives up waiting on the emulator after 2 seconds instead
  of freezing, and moving it to the other screen of a dual-screen handheld no longer rebuilds it mid-game. A
  freeze report now shows where the app was stuck.
- **Save states and the in-game save:** loading a save state never changes your in-game save on Game Boy and DS. GBA already worked this way.
- **A new run keeps your in-game save, in every game.** Continue on the title screen opens it, so a save in front of the starters skips the intro on every attempt, and New Game is the clean start. The new-run window says which, and it reads the save you just made.
- **The first launch says KaizoCore is a beta** and asks for every bug and change, with the crash report switch from More right there.
- **Each Kaizo run follows its own game and mode:** favorites (three, four on Gen 4, five on Gen 5, nine on Nat. Dex) with the mode's own favorites rules under the boxes and a warning for a legendary the mode does not allow; the game-over rule, Survival's heal limit and Journey's starter read from the run's settings even when only its saved details name the mode; the rules box lists only the modes a game can run; Survival's line on Black and White and on the Johto games says what their rules say.
- **Tracker Setup shows only what works in the game and mode you are playing:** FireRed's maps only on FireRed and LeafGreen, the run-over rule and game over lines only in a Kaizo IronMON run, Play as your Pokemon only on Game Boy Advance games, and no Gen 3 switches on Game Boy or DS games.
- **Where you left off names the mode:** Standard Nuzlocke (with how the run ended, once it has) or Kaizo IronMON with the attempt.
- **The Game Over popup and A+B+Start only act in a Kaizo IronMON run.**
  - A Nuzlocke ends by its own rules.
  - A game played from your library has no run to end.
- **Nuzlocke:**
  - Rules shows your Nuzlocke rules, not the IronMON rulebook.
  - New run in a randomized Nuzlocke keeps the same rules.
  - The random ball picker is off in a Nuzlocke unless you turn it on in Tracker Setup. Kaizo IronMON keeps it on.
- **Catching tutorials:** Wally's (Emerald) and the Old Man's (FireRed and LeafGreen) no longer count as your encounter. The tracker does not read the borrowed Pokémon during them, as the PC tracker does.
- **Coming back to a game** after a tab switch, Back or closing the app opens it where you left off, not at the title screen. It is the moment you left, so nothing is rewound.
- **Stat marks and notes** can no longer be emptied by a crash while they save.
- **Saved attempts** are included in backups.
- **The backup screen says what a backup holds:** never your library games, but the current run's randomized game, so a restored run comes back whole.

**IronMON rules check** (details in [IRONMON-RULES-CHECK.md](IRONMON-RULES-CHECK.md))

- **Your favorite in a starter ball:** in a Kaizo IronMON run on a Game Boy Advance game, the tracker names the ball that holds one of your favorites, when the mode lets you take it (its official limits: one legendary at most; no BST limit in Standard and Ultimate; under 600 BST in Kaizo and the modes built on it, 600 and lower in Evo Kaizo; Survival under 580 with no legendary; Super Kaizo no legendary; Nat. Dex up to 600, and above that in Standard and Ultimate unless Strong Legendary or Mythical). It never says what the other balls hold, and IronMON Journey, where any starter is allowed, has no such line. The PC tracker does not have this line.
- **The stream shows no more than the phone.** An opponent's abilities, types and HP appear as the phone's tracker shows them, and the randomized data waits until the run is over.
- **Move info** hides the randomized numbers its move row hides.
- **DS game over follows the mode:** Standard and Ultimate end when the whole party is down, Kaizo Doubles when either of the first two faints.
- **Red, Blue and Yellow Survival** end when the highest level Pokémon faints, so the HM friend may lead a gym as that mode allows.
- **The run's record** also counts File > Restart, a kept in-game save and a backup restore.
- **Mode lines** now match the rules: when Survival's heal count starts, Journey's emergency swaps (and no ball picker in Journey), and the favorites' limits per mode.
- **Attempts count per settings file**, as the PC tracker counts per profile. Each file starts from your number as it stood; a Nuzlocke counts no attempt.
- **An attempt left before it ended** (a re-roll, a bail) goes into the run history as ended.
- **Your stats** say when a win came after state loads, retries or restarts, and a winning streak counts clean wins only.
- **A run's record** says when its official file ran without what the rules add ("50% levels", "no PART 2", "no Smart AI"), when it came from a run code, and when it was played under another game-over rule.
- **Run codes** say first when you already played that seed, and a code's passes no longer change your switches.
- **HeartGold and SoulSilver Survival** add the 7 Kanto heals when the Johto League is beaten.
- **The DS panel** no longer shows coverage counts on the main screen; they are under COVERAGE CALC, as on the DS tracker.
- **Type matchups in move info** are a switch in Tracker Setup, off by default.
- **Backups** leave out the live run's randomizer log until the run is over.
- **Streaming:** a notification brings you back if KaizoCore goes to the background while the stream is on.

**Known issues**

- DS double battles are not tracked.
- Gold, Silver and Crystal Survival: the 7 Kanto heals are added by hand.
- Black 2 and White 2 use about 1 GB of memory.
- The DS core leaks a little memory on each load.
