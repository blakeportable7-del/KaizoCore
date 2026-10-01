# kcbot: test bots for KaizoCore

Bots that play games in KaizoCore on an emulator or a phone, for weeks if need be: to find crashes, freezes and
memory creep, to check the tracker and the rules against what the game really did, and to take the gameplay
screenshots for the wiki from the middle and late game.

Nothing here ships. The bots drive a **debug build** of the app through its test port (`app/src/debug`,
`bot/BotPort.kt`), which a players' build does not have (`BotPortStaysInDebugTest` holds that line).

## How it fits together

- **The test port** (on the device, 127.0.0.1:8650, reached with `adb forward`): reads and writes the game's
  memory, holds buttons for an exact number of the game's frames, takes the bot's own states, resets the console.
  In **lockstep** the render loop runs no frame of its own and the bot runs every frame itself, one call per frame,
  as an emulator's Lua console does. It starts only when the PC arms it (`files/bot/enabled` and a token).
- **`kcbot/port.py`**: the client for the port.
- **`kcbot/device.py`**: adb, arming the port, opening a library game by name, and what a long run watches: the
  crash buffer, ANRs, the app's memory, screenshots.
- **`kcbot/bizhawk.py`**: the slice of BizHawk's Lua API a BizHawk bot script uses, over the port. With it,
  existing bots run as they are.
- **`pokebot/`**: PokeBot (MIT), the Red and Yellow speedrun bot, which plays from the title screen to the Hall of
  Fame. See `pokebot/KCBOT.md`.
- **`pokebot-story/`**: the files that make PokeBot play the story for screenshots instead of for speed. A death,
  or the bot getting stuck, reloads the checkpoint `run_pokebot.py` took when the player last arrived on a map (three
  deaths there go back one more), and the reloaded game finds its place on the route with `Walk.init`, which
  PokeBot never called.
- **`pokebot-ironmon/`**: the files that make PokeBot play a randomized Kaizo IronMON run of Yellow by the rules:
  one Pokémon, no wild fights, items only in battle, no banned moves, talks to Mom, up to Brock. See its `KCBOT.md`.
  `run_ironmon_gen1.py` plays one seed after another and checks the app's game-over popup each time.

## Running

Python 3.12 with `lupa` (a Lua runtime) and `pillow`, in `C:\Users\bepor\kcbot-venv`:

```bash
cd tools/kcbot
C:/Users/bepor/kcbot-venv/Scripts/python.exe run_pokebot.py red --shots shots
```

One bot per device. Nothing else may drive the same emulator or phone while a bot runs.

## Rules

- **No ROMs travel.** The games are the owner's own dumps, already on the device. A bot never copies one off it.
- **No reference code with firmware.** `3583Bytes/PokeBot` bundles a GBA BIOS in its emulator folder; only its
  `Scripts/` were taken.
- **Plain play for screenshots.** The wiki's gameplay pictures come from bots, from the middle and late game.

## What the bots are for (Blake, 2026-09-30: free only)

1. **Hammering the app for weeks**: crashes, freezes and memory creep. No smart play needed.
2. **The IronMON soak**: start a run, lose, NEW RUN, and check the game-over popup, the attempt number and what it
   says beat the player, again and again. On Yellow PokeBot plays the runs (`run_ironmon_gen1.py --start`); on the
   Gen 3 games simple play is enough (`run_ironmon_soak.py`), and it needs each game's intro-skip save first.
3. **Gen 1 screenshots from the middle and late game**: PokeBot plays Red and Yellow to the Hall of Fame.

Not planned: bots that play the Gen 3 or DS stories. No free bot does it (the trained Red agents reach about
Cerulean; the general players run on paid APIs), and a hand-written one tripped on its first staircase. Those
games' story screenshots come from real saves instead.

A supervisor (`supervisor.py`) runs jobs in turn for as long as it is left running, checks the device every minute
and writes a daily report.
