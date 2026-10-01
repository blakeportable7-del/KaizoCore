# Release notes

Newest first. Each build's GitHub release has the same list, with the APK and its checksum.

## 1.0.0-rc32

**New**
- **A new app icon**: the cracked Poké Ball.
- **Home screen**: four modes, Play any game, Kaizo IronMON, Nuzlocke and ROM Hacks, with links to Library, More and Your stats. The **Continue** card takes you back to the game in progress and names its mode: Kaizo IronMON and the attempt, or the Nuzlocke and how it ended.
- **Nuzlocke mode** on every game the tracker reads, Red to White 2.
  - 6 presets: Standard, Hardcore, Randomizer, Monotype, Wedlocke and Genlocke. Each is a starting point, and every rule is a switch.
  - 19 rules: first encounter per area, a fainted Pokémon is dead, a whiteout ends the run, slow start, the dupes clause (and dupes as only lines you still own), the shiny clause, gifts count, static Pokémon count, cave floors as one area, water as its own area, the escape clause, a nickname reminder, level caps, rivals and team leaders as bosses, no items in battle, Set battle style, Wedlocke pairs and Genlocke. The Safari Zone can be one area or each zone its own.
  - The tracker keeps the ledger by itself: encounters, catches, gifts, deaths and level caps, in Areas, Grave and Log tabs. It warns when a rule is broken and never stops the game.
  - A randomized Nuzlocke starts from "Nuzlocke fair": random wild Pokémon and trainers close in strength, with levels as the game has them.
- **Build your own**: a custom randomized game in five steps: start from the game or any of its modes; starters (the game's own, random, or any Pokémon you pick); the world; the Pokémon; save and play. **Randomizer settings (advanced)** opens all of the randomizer's settings, about 140 in eight sections, with search, undo on each row, Save as, Copy and Paste. A custom game says "(custom)" wherever its mode is named.
- **Stream to OBS over Wi-Fi**: the game's own picture and sound (lossless, at the game's own resolution), the tracker and your attempt counter, as browser sources. A setup page tests the picture, a ready-made OBS scene comes at 1920 x 1080 (and a top-screen-only scene for DS), and the address carries a code so nobody else on the network can guess it. While the stream is on, a notification brings you back if KaizoCore goes to the background. Works for any game, in a run or not.
- **Play as your Pokémon** (Game Boy Advance games): your trainer becomes your lead Pokémon as you walk around. 980 of the 1,025 Pokémon have a sprite: all 386 from Gen 1 to 3 and 594 of the 639 from Gen 4 to 9, plus 164 forms (42 Megas, 18 Alolan, 18 Galarian, 16 Hisuian, 4 Paldean, and more such as Primal, Origin and Therian forms). It walks the way you face, stands idle, dozes off after about a minute without input, and faints at 0 HP. Your lead decides, or **Always use** picks any of them, with names that start with your letters listed first. It is drawn into the game's own picture, so the stream shows it too. After UTDZac's Sprite Is Me.
- **Your own sprite**: play as your own picture, fitted to 32 x 32, or your own sprite sheet set (idle, walk, sleep and faint sheets, with your own frame size and frame lengths). Backups keep it.
- **Animated Pokémon on every tracker**: Gen 4 to 9 now animate beside Gen 1 to 3, on Game Boy Advance games, Nat. Dex builds and DS games: the same 980 Pokémon and 164 forms. They stand idle, walk while you walk (a switch), fall asleep after 55 seconds without input, and faint at 0 HP. A Pokémon nobody has drawn yet keeps its still picture. Sprites by the Walking Pals collab's artists.
- **Tracker themes**: **EDIT COLOR THEME** has the PC tracker's 15 presets on Gen 1 to 3 and the DS tracker's 16 on DS games. Save your own presets under your own names, edit any colour by its code, and export or import a theme. Auto Pokémon Themes keeps the preset you picked when it hands back.
- **Image behind the tracker**: your own photo, with Dim, see-through boxes and Fill or Fit.
- **Game over lines**: up to 100 lines of your own, 120 characters each, for the game-over screen, with or without the built-in ones. After UTDZac's Death Quotes.
- **Your stats** (Home): runs started and won (a win after state loads, retries or restarts is counted apart), time played in runs, the mode that ended the most runs, your longest winning streak of clean wins, your best run in each game and mode, and your Nuzlockes.
- **Favorites, by the rules**: 3 on Gen 1 to 3, 4 on Gen 4, 5 on Gen 5 and 9 on Nat. Dex builds, with your mode's own lines under the boxes and a warning for a legendary your mode does not allow. Ralts to Rayquaza, and the later Pokémon of every generation, can be picked now. In a Kaizo IronMON run on a Game Boy Advance game, the tracker names the starter ball that holds one of your favorites when your mode's official rules let you take it (Standard and Ultimate: any BST; Kaizo and the modes built on it: under 600; Evo Kaizo: 600 and lower; Survival: under 580 and no legendary; Super Kaizo: no legendary; Nat. Dex: up to 600, and above that in Standard and Ultimate unless Strong Legendary or Mythical). It never says what the other balls hold.
- **FireRed and LeafGreen dungeon maps**: Mt. Moon, S.S. Anne, Rock Tunnel, Power Plant, Pokémon Mansion, Seafoam Islands and Victory Road, 28 floors in all, by Bill Greenwald (doctrDNA), used with permission. They show routes and item spots, and stay off until you switch them on in Tracker Setup.
- **Type matchups in move info**: what a damaging move is strong against, what resists it and what it has no effect on. A switch in Tracker Setup, off by default.
- **Patched versions from the games you already added**: PATCH lists what KaizoCore can make of a game, FireRed 1.1 and Emerald have a NAT. DEX button that says what Nat. Dex adds, and the Kaizo IronMON screen offers to make it under the game. **ADD A PATCH FILE** puts a patch of your own in the library. Your own copy is never changed.
- **Dual-screen handhelds**: with the tracker on the second screen, the phone's FILE menu opens Tracker Setup, and moving the app to the other screen keeps the game playing.

**Easier to use**
- Library opens on My games, and a file the tracker cannot read says why and still plays.
- Kaizo IronMON opens on Kaizo, lists the modes in the order the rules build, gives each one line from its rules, and links to all of them. The rules box shows only the modes your game can run.
- Gold, Silver and Crystal: the growth patch the rules ask for is the default, and the Kaizo IronMON screen makes the patched copy in one tap.
- Bigger tracker buttons (44dp), RUN asks twice, and Tracker Setup and the rules follow your phone's text size.
- Tracker Setup has flat, full-width buttons, round pick-one choices and square on/off switches, and shows only what works in the game and mode you are playing: FireRed's maps only on FireRed and LeafGreen, the run-over rule and game over lines only in a Kaizo IronMON run, Play as your Pokémon only on Game Boy Advance games, and no Gen 3 switches on Game Boy or DS games.
- Windows close with an X at the top right. The tracker's own windows keep the PC tracker's look.
- In portrait, A and B sit on the GBA's diagonal, B lower left of A. A layout you made with Edit layout is kept.
- Play as your Pokémon asks only what it needs: as your lead, party slot 1 decides, and you are the trainer until you have a Pokémon. Tapping Always use or Your own sprite again goes back to your lead.
- Pickers list names that start with your letters first, then any name holding them.
- The first launch says KaizoCore is a beta, asks for bugs and changes, and offers the crash report switch.
- The random ball picker is off in a Nuzlocke unless you turn it on.
- Coming back to a game opens it where you left off, once.
- Rewind and cheats work in plain play. They stay off in Kaizo IronMON runs and Nuzlockes.

**IronMON rules**
- The game-over screen, Retry and New game appear only in Kaizo IronMON runs.
- The tracker and the stream show no more than the PC tracker does, and the randomizer data opens only when the run is over. One addition is the favorite-ball line above.
- Attempts count per settings file, as the PC tracker counts per profile. Your number carried over, and a Nuzlocke counts none.
- A run's record says what went back in time (state loads, retries, restores, restarts, a kept save), when its file ran without what its rules add, when it came from a run code, and when it ran under another game-over rule. Run codes say first when you already played that seed.
- Each Kaizo run follows its own game and mode: the favorites count and limits, the game-over rule, Survival heals and Journey, even from a settings file you saved yourself.
- DS game over follows the mode. Red, Blue and Yellow Survival allow the HM friend. HeartGold and SoulSilver Survival add the 7 Kanto heals when the Johto League is beaten.
- Emerald Kaizo Doubles, Chaos Kaizo and IronMON Journey get the official 60% levels, as Emerald Kaizo does.
- The Nat. Dex rulebooks say Super Kaizo's smart AI is built in, and that Nat. Dex Evo Kaizo bans pivots.
- Backups leave out the live run's randomizer log until the run is over.
- The full check: [How KaizoCore follows the IronMON rules](IronMON-rules).

**Fixed**
- The app no longer freezes ("isn't responding") when the game's picture stalls, as on a dual-screen handheld when the app moves to the other screen. A freeze report now shows where it was stuck.
- Loading a save state never changes your in-game save, on Game Boy and DS too.
- A new run keeps your in-game save in every game: Continue opens it, New Game starts fresh.
- Catching tutorials (Wally's, and the Old Man's) no longer count as your encounter.
- Stat marks and notes survive a crash while they save, and saved attempts are in backups.
- The backup screen says what a backup holds: never your library games, but the current run's randomized game, so a restored run comes back whole.
- Black 2 and White 2 find their data with NDS-Ironmon-Tracker 6.3.11's pointer.

## 1.0.0-rc31

- The app says when a new build is out, and Update installs it over the old one.
- Crash reports can be sent straight to the developer. Nothing about your games or files is in them.

## 1.0.0-rc30

- NEW RUN is ready in seconds: the next run is made while you play.
- The game saves itself every 3 minutes, and after a crash it opens where you were.
- The game-over popup says what beat you, your badges and time, and your best run.
