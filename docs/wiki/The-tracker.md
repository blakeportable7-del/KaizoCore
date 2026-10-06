# The tracker

KaizoCore's tracker is its own copy of the IronMON trackers: [Ironmon-Tracker](https://github.com/besteon/Ironmon-Tracker)
by Besteon for Gen 3, its Gen 1 and Gen 2 forks by Sannji
([Ironmon-gen-tracker](https://github.com/mollo010/Ironmon-gen-tracker)) and seadogstingray
([Ironmon-gen-2-tracker](https://github.com/seadogstingray/Ironmon-gen-2-tracker)), and the [NDS-Ironmon-Tracker](https://github.com/Brian0255/NDS-Ironmon-Tracker) for Gen 4
and 5. It reads the game's memory while you play, and it keeps their options, their names and their defaults. Since
rc33 it has a look of its own: rounded cards, type and status pills, and move names in their type's color with the DS
tracker's type symbols. Your color theme sets every color.

It reads the US versions of Red, Blue, Yellow, Gold, Silver, Crystal, Ruby, Sapphire, Emerald, FireRed, LeafGreen,
Diamond, Pearl, Platinum, HeartGold, SoulSilver, White, Black 2 and White 2. Any other game plays without it. It
works in every mode: a Kaizo IronMON run, a Nuzlocke, a ROM hack or a game from your library.

## Where it sits

- **Portrait**: under the game, with the moves beside the stats so the whole card fits on the screen.
- **Landscape**: pick in **VIEW** on the File bar, or under Landscape tracker in Tracker Setup.
  - **Docked** beside the game, with no bar between them: drag the tracker's left edge to make it wider or narrower.
  - **Floating**, a window over the game. It stays locked in place; hold it for half a second to unlock it, then drag
    it or pull its corner button to resize it. Hold it again to lock it. It locks itself when the File bar opens and
    after six seconds untouched.
  - **HUD**: the tracker in panels round the game, which stays its full size.
  - **Hidden**: the game alone.

The tracker has no buttons of its own: Tracker Setup and its screens are under **TRACKER** on the File bar.

## What it shows

- **Before your first Pokémon**: your favorites, the random ball picker in the lab, and in a Kaizo IronMON run on a
  Game Boy Advance game the ball that holds a favorite your mode lets you take (see
  [Kaizo IronMON](Kaizo-IronMON#favorites)).
- **Your Pokémon**: one card for your lead, or the one in battle. HP, the level with the level it evolves at beside
  it ("Lv.25 (30)"), stats, ability and item. **Show team view** adds your whole team as a strip (Gen 1 to 3).
- **The enemy**: its HP as a bar, the abilities and moves you have seen it use, and six stat marking boxes.
- **Moves**: each name in its type's color, with its type's symbol, then PP, power and accuracy. Tap the Moves header for every move seen, with the levels it was seen at, and the
  moves the species learns.
- **Stat marks**: tap a box to cycle through blank, +, -- and =. Your marks and notes on a species follow it through
  the run.
- **Notes**: a note for every species. **OPEN NOTEBOOK** (Gen 1 to 3) shows them all.
- **Abilities** (Gen 3 to 5): recorded as the game reveals them, up to two per species. Tap one to read it.
- **Weather** (Game Boy Advance games): rain, sun, a sandstorm or hail, under the battle banner while it lasts.
- **Badges, heals and friendship**: the badges carousel (Gold, Silver and Crystal show all 16), the heals box
  ("Heals: 62% HP (4)"), and friendship readiness (a FRIEND bar that reads READY on DS).
- **Tap a move** to read it: PP, power, accuracy, contact and priority on Gen 1 to 3, and the description on DS.
  **Type matchups in move info** (off by default, Gen 1 to 3) adds what a damaging move is strong against, what
  resists it, and what it has no effect on.

## Tools

- **Time Machine**: a restore point every four minutes on the map, out of battle (every five on Game Boy games), with
  the last ten kept. Restoring one in a run goes on the run's record.
- **Coverage calc** (Gen 3 and DS): pick up to six move types and see how every Pokémon takes them, from immune to 4x.
- **Calc Atk** (Gen 3): tap the last attack line in a battle to estimate the enemy's Attack from the damage it did.
  Wild battles only, unless you change it.
- **Grade my notes** (Gen 1 to 3): at game over, a score sheet grades your stat marks against the real numbers.

## Make it yours

- **Colors**: **EDIT COLOR THEME** has the PC tracker's 15 presets on Gen 1 to 3 and the DS tracker's 15 and a Default on DS games.
  Save your own, edit any color by its code, and export or import a theme.
- **Image behind the tracker**: your own picture, with Dim, See-through boxes, and Fill or Fit.
- **GAME OVER LINES**: up to 100 lines of your own, 120 characters each, for the game-over screen to throw at you,
  with or without the built-in lines. After UTDZac's Death Quotes.
- **Play as your Pokemon** (Game Boy Advance games): your trainer becomes your lead Pokémon, or your own sprite (a
  picture, fitted to 32 x 32, or a sprite sheet set). It dozes off after about a minute without input. It is drawn
  into the game's own picture, so a stream shows it too. After UTDZac's Sprite Is Me.
- **FireRed and LeafGreen dungeon maps**: Mt. Moon, S.S. Anne, Rock Tunnel, Power Plant, Pokémon Mansion, Seafoam
  Islands and Victory Road, 28 floors in all, by Bill Greenwald (doctrDNA), used with permission. They show routes
  and item spots, and they are off until you switch them on in Tracker Setup.

## Tracker Setup

Tap **SETUP** on the tracker. The options keep the PC tracker's names: **Reveal info if randomized**, **Show data for
vanilla game**, **Hide stats until summary shown**, **Open Book Play Mode**, **Track PC Heals**, **Determine
friendship readiness** and the rest. Some of them are Gen 1 to 3 only. The top of the page sets when a run is
considered over (see [Kaizo IronMON](Kaizo-IronMON)).

## DS games

The DS tracker shows one Pokémon at a time, with SEE FOE and SEE MINE to swap. It keeps PAST RUNS and STATISTICS for
each game, and it counts Pokécenter heals. Double battles are not tracked yet.
