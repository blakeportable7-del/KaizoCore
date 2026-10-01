# PokeBot in kcbot

These Lua scripts are PokeBot, the Pokémon Red and Yellow speedrun bot by Kyle Coburn and Michael Jondahl (MIT, see
LICENSE), from the 3583Bytes/PokeBot fork (version 2.5.4), the one with both Red and Yellow routes. Only the
`Scripts/` folder was taken; that repository also carries an emulator folder with firmware, which never came here.

kcbot runs them as they are, with two changes:

- `main.lua`: `RUNS_FILE` reads `KCBOT_RUNS` from the environment instead of a path on the author's PC.
- `util/utils.lua`: `Utils.splitCheck` returns at once unless `RESET_FOR_TIME` is on. It restarts a run behind
  speedrun pace using the Red route's split names, and on Yellow it failed on a split Yellow does not have.

BizHawk's Lua API is served by `kcbot/bizhawk.py` over the app's test port (plain Lua 5.1, as BizHawk 1.x ran).
