# PokeBot, Kaizo IronMON fork (Yellow)

PokeBot (`../pokebot`, MIT) plays Red and Yellow for speed. This folder holds only the files that make it play a
randomized Kaizo IronMON run of Yellow instead; everything else is loaded from `../pokebot`, which this folder is
searched before (`BizHawk(port, [pokebot-ironmon, pokebot])`). `run_ironmon_gen1.py` drives it.

## What changes

- `main.lua`: PokeBot's, with `require "ironmon"` after its modules.
- `ironmon.lua`: the rules, in the words of the app's `rulesets/RBY/kaizo.md`:
  - never fights or catches a wild Pokémon (runs from every wild battle);
  - items only in battle: Battle.automate drinks a potion when the next hit would kill, and the route has no
    potion stops outside battle;
  - never picks a banned move: healing and draining moves, Spore, Wrap, Bind, Fire Spin, Clamp, and the HM moves.
    The starter's banned move is allowed in the lab fight, as the rules say. Self-Destruct and Explosion never (a run
    of one Pokémon is over when it faints). With only a banned move left the run ends; with no PP at all the game's
    Struggle is used;
  - an evolution is never stopped: B is held back while one plays;
  - nothing resets the console. The lead fainting ends the run (`KCBOT_RUN_OVER death`) and the app's game-over popup
    starts the next seed; anything PokeBot would reset for ends it too, with its reason.
- `data/yellow/paths.lua`: the speedrun's route with the same coordinates (the maps are not randomized), without its
  catches, shopping, stat checks and potions; it talks to Mom, heals at the Viridian and Pewter Pokémon Centers,
  fights every trainer the speedrun fights, and ends after Brock (`KCBOT_ROUTE_END`).

## Not done (yet)

- The route stops at Brock. Past him the speedrun's route is built around its Nidoking.
- A new move with four already known is answered the way PokeBot answers any text, A and B in turn, so it is
  sometimes learned (over the first move) and sometimes skipped. It never uses a TM.
- The Nemesis and Favorites clauses do not come up: Yellow has one starter, and the bot never catches.
