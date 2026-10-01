# Kaizo IronMON

IronMON is a Pokémon challenge: a randomized game, one Pokémon, and a loss means starting over with a new seed.
KaizoCore does the whole setup on the phone. It randomizes the game with the ruleset's own settings file, boots it,
tracks it, and makes your next seed while you play.

## Modes

The mode row lists every mode your game has a settings file for, in the order the rules build: Standard is the
base, Ultimate adds rules to it, Kaizo adds more, and every other mode starts from Kaizo. **Read all the rules** has
the full text of each.

| Game | Modes |
|---|---|
| Red, Blue, Yellow | Standard, Ultimate, Kaizo, Survival |
| Gold, Silver, Crystal | Standard, Ultimate, Kaizo, Survival |
| Ruby, Sapphire | Standard, Ultimate, Kaizo, Survival, Kaizo Doubles, Chaos Kaizo, IronMON Journey |
| Emerald | the Ruby and Sapphire modes, plus Super Kaizo |
| FireRed, LeafGreen | all ten: Standard, Ultimate, Kaizo, Super Kaizo, Survival, Survival Revival, Kaizo Doubles, Chaos Kaizo, Evo Kaizo, IronMON Journey |
| Emerald and FireRed, Nat. Dex builds | all ten |
| Diamond, Pearl | Standard, Ultimate, Kaizo, Survival, Kaizo Doubles, IronMON Journey |
| Platinum | the Diamond and Pearl modes, plus Super Kaizo |
| HeartGold, SoulSilver | Standard, Ultimate, Kaizo, Super Kaizo, Survival, Kaizo Doubles, IronMON Journey |
| White | Standard, Ultimate, Kaizo, Survival, IronMON Journey |
| Black 2, White 2 | Standard, Ultimate, Kaizo, Survival, Kaizo Doubles, IronMON Journey |

Super Kaizo is played on its patched build of the game (see [ROM hacks and patches](ROM-hacks-and-patches)). On a
clean copy the app warns you, and lets you play anyway.

## What the rules add, done for you

- **Official 60% levels** (Emerald Kaizo and every Emerald mode built on it; Gold, Silver and Crystal Kaizo and
  Survival):
  trainer and wild levels go up 6% first, then the mode's 50%, as the official settings do.
- **Second pass (PART 2)** (Red, Blue and Yellow): the second randomizer pass the Gen 1 rules add, which sets every
  Pokémon to Slow growth so nothing evolves into a legendary.
- **The growth patch** (Gold, Silver and Crystal): the pseudo-fluctuating patch the rules ask for is the default when
  you prepare one of these games. If you pick a copy without it, the Kaizo IronMON screen says so, and **Make the
  patched copy** makes it in one tap.

The two switches are on for the bundled settings files. Turn one off and the run's record says that run went
without it.

## Favorites

**Startup favorites**, on the Kaizo IronMON screen, are the Pokémon you may take from the lab instead of the
random ball: 3 on Gen 1 to 3, 4 on Gen 4, 5 on Gen 5 and 9 on a Nat. Dex build, as the rules say. Under the boxes are
your mode's own lines about favorites, and a warning for a legendary your mode does not allow. Every mode allows one
legendary at most in your list.

The tracker lists them until you have your first Pokémon. In a Kaizo IronMON run on a Game Boy Advance game it
also names the ball that holds one, "FAVORITE! GENGAR IN THE LEFT BALL", when your mode's official rules let you
take it:

| Mode | A favorite you may take |
|---|---|
| Standard, Ultimate | any BST |
| Kaizo, Kaizo Doubles, Chaos Kaizo, Survival Revival | under 600 BST |
| Evo Kaizo | 600 BST and lower (a legendary under 600) |
| Super Kaizo | under 600 BST, and no legendary |
| Survival | under 580 BST, and no legendary |
| Nat. Dex builds | up to 600 BST, and in Standard and Ultimate above 600 too unless it is a Strong Legendary or Mythical |

It never says what the other balls hold, so with no favorite there the pick stays blind. IronMON Journey has no such
line: you may take any starter there.

## Starting, continuing, and NEW RUN

- **Start attempt N** makes a new run. **Continue attempt N** goes back to the one in progress.
- **NEW RUN** is in the File menu while you play. So is **New game (new seed)** on the game-over popup, the NEW chip in
  landscape, and holding A, B and Start for about a second. Every one of them asks first.
- **Your next run is made while you play.** Randomizing from scratch takes the phone half a minute or more, so
  KaizoCore makes the next seed in the background, with the same game and settings, and NEW RUN only has to swap it
  in. It skips this when the phone is short on space or memory, and for a run built from a run code. The switch is
  **Get the next run ready in the background**, under New runs in Tracker Setup, and it is on by default.
- A new run keeps your in-game save, in every game. Continue on the title screen opens it, so a save made in front
  of the starters skips the intro on every attempt, and New Game is the clean start.

## The game-over popup

In a Kaizo IronMON run, a loss brings up the PC tracker's popup: **GAME OVER** (or **YOU WON**), the attempt, your
team, what beat you (the level and species, with the trainer on Gen 3 games and the place on the others), your
badges and time, and **NEW BEST** or your best so far on that settings file.

- **Continue playing**
- **Retry the battle**: back to the start of the battle you lost. It asks first.
- **Save this attempt**
- **Grade my notes** (Gen 1 to 3): how close your stat marks were.
- **Inspect the log**: the randomizer's log, full screen and searchable. It opens here, once the run is over, and
  nowhere else.
- **New game (new seed)**
- **Share**: one line about the run to paste anywhere.

## When a run is lost

The rule follows the mode:

- Standard and Ultimate: the entire party faints.
- Kaizo Doubles: either of your first two faints.
- Red, Blue and Yellow Survival: the highest level Pokémon faints.
- Every other mode: the lead Pokémon faints.

You can change it at the top of Tracker Setup ("Game is considered over when"). The choice is kept per settings file,
and a change in the middle of a run goes on its record.

**Survival heals.** The counter starts at 10 (5 on Survival Revival) and counts down. The 8th badge adds one. On
HeartGold and SoulSilver, beating the Johto League adds the 7 Kanto heals; on Gold, Silver and Crystal, add those by
hand. Count heals with the + and - buttons (the arrows on DS). On Gen 3 games the heart counts them for you while it
is on. Reaching 0 changes the counter's color and does not end the run.

## Build your own

**Build your own**, on the Kaizo IronMON screen, makes your own settings file in five steps:

1. **Start from**: the game as it is, or any of its modes.
2. **Starters**: the game's own, random, or your picks from the game's own Pokémon.
3. **The world**: wild Pokémon and trainers' Pokémon (unchanged, random, or random at a similar strength), wild and
   trainer levels (up to +50%), and items on the ground.
4. **The Pokémon**: types, abilities, evolutions, the moves they learn (Metronome only is one choice), the moves
   themselves, TMs and move tutors.
5. **Save and play**: **Start this game**, **Save**, or **All settings** for the full editor.

A settings file that differs from the bundled ones is custom, and the app says "(custom)" wherever it names the
mode: the start confirm, the Continue card, Your stats and shared lines.

**Randomizer settings (advanced)** opens every setting of Universal Pokémon Randomizer ZX 4.6.1: about 140 options
in eight sections, with search, undo on each row, **Save as**, **Copy** and **Paste**.

## Run codes

Under **Run codes** on the Kaizo IronMON screen, **Share this run** gives you one line starting with `KC1-`. A friend
pastes it into **Run code** and taps **Build this run**. Their app builds the same seed from their own game and
settings file, then checks the result: "Same game as theirs: the checksum matches." No game file changes hands.
If you already played that seed, the app tells you first, and the run's record says it came from a code.

## Attempts and Your stats

Attempts count per settings file, the way the PC tracker counts per profile. The number is at the top of the
tracker, on the start button, the Continue card and the game-over popup. A Nuzlocke counts no attempt, and a run you
replace before it ends is filed as ended.

Home, **Your stats**: runs started, runs won (wins after state loads, retries or restarts are counted apart), time
played in runs, the mode that ended the most runs, your longest winning streak (clean wins on one settings file),
your best run in each game and mode, and your Nuzlockes.

## What a run's record keeps

Save states, Time Machine restores and Retry are allowed in a run, and every use goes on the run's record: state
loads, restores, retries, coming back after a crash, File > Restart, a kept in-game save and a backup restore. The
game-over card and the shared line say so. Rewind and cheats are off in runs. More in
[How KaizoCore follows the IronMON rules](IronMON-rules).
