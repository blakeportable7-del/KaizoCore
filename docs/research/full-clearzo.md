# Full Clearzo IronMON for KaizoCore (research, 2026-10-02)

Read only. Nothing in KaizoCore was changed. Downloads (text only) are in
`scratchpad/research-fullclearzo/`: the two repo files, the Sevii guide as text, a sparse pret checkout (maps, scripts,
constants, a few C files; the three multiboot `.gba` files in `data/` were never fetched), the parsers and their
outputs (`tracker_routes_frlg.json`, `pret_maps.json`, `dungeons_frlg.json`, `compact_all.md`).

Pinned sources used throughout:

- FullClearzo: https://github.com/ratcityretro/FullClearzo at HEAD `1089734059f74cf9bc029554c661d9c222395367`
  (2026-05-01). The rules (README.md) last changed in `c473c94827` on 2026-02-25.
- pret/pokefirered at `037335f4c725d7c9aecdac87066f2002b4bd7e14` (2026-09-26). Links below are
  `https://github.com/pret/pokefirered/blob/037335f4c725d7c9aecdac87066f2002b4bd7e14/<path>#L<n>`; written here as
  `pret:<path>:<line>`.
- Ironmon-Tracker (Gen 3 PC tracker): the local copy `C:/Users/bepor/ironmon-ref/Ironmon-Tracker` at `c450ecae`
  (2026-04-12). Upstream HEAD `41e671124f` (2026-09-10) has the same RouteData.lua, TrainerData.lua and EventData.lua
  (checked, line endings aside); its Program.lua differs only in how trainer IVs are read. Written as `IT:<path>:<line>`.
- KaizoCore: `C:/Users/bepor/IronMonOne-wt-rc33` at `76a9a44d` (fix/rc34-ds-audio). Written as `path:line`.

---

## 1. What it is

**Name.** Full Clearzo IronMON ("Clearzo"), a Kaizo IronMON variant for Pokemon FireRed and LeafGreen.

**Who.** Blake's note and FUTURE-PROJECTS credit Typo, Puffsun and ratcityretro; the README lists the same three as
Season 1's players, with typo the winner. The repository and all 62 commits are ratcityretro's (GitHub display name
"The Mayor"). Discussion lives in Typo's Discord (https://discord.gg/Q8zYpznW, channel #clearzo-gc), which I did not
join. No GitHub account for Typo or Puffsun was found.

**Links.**
- Rules: https://github.com/ratcityretro/FullClearzo/blob/1089734059f74cf9bc029554c661d9c222395367/README.md
- Season 2 table: https://github.com/ratcityretro/FullClearzo/blob/1089734059f74cf9bc029554c661d9c222395367/LeaderboardFullClearzoS2.md
- Twitch: twitch.tv/typo, twitch.tv/puffsun, twitch.tv/ratcityretro (from the README and table).

**Base game and revision.** FireRed or LeafGreen, any revision the Kaizo settings accept; the README names none. It
names one build as safe: DrMaple's Faster FireRed (a patch for FireRed Rev 1, also called 1.1), shown not to
interfere with the late game. It warns that a game given the National Dex from the start breaks the run at Sevii
(section 2.8).

**How it is distributed.** As two Markdown files and nothing else. There is no patch, no randomizer or fork, no
settings string or `.rnqs`, and no tracker extension. GitHub-wide searches for "clearzo" and "Full Clearzo" in repos
and code (2026-10-02) found only this repository; ratcityretro's other public repo, NameThatPokemon-IronmonExtension,
is a chat-naming add-on, unrelated. The mode runs on the standard Kaizo IronMON rules and settings plus the stock Gen 3
PC tracker, whose Stream Connect command `!progress sevii` is the scoring tool.

**Licence.** The repository has no licence file (GitHub API `license: null`).

**Version and date.** No version numbers. The commit messages mark the rules final on 2026-02-25 (commits
`13c1066117`, `379d7d3ed8`, `c473c94827`); the repository's 62 commits run 2026-01-24 to 2026-05-01, most of them
leaderboard updates. History of the rules: Sevii was optional until 2026-02-11 and required from then
(`64d6d4d6c3`); the 26-trainer count, the item allowances and the trainer estimate for part 2 came the same day; the
spa-water clause on 2026-02-19 (`a9e004d5c8`) and the HM friend clauses on 2026-02-25 (`13c1066117`).

**The rules, restated in full** (my wording, every requirement kept; the README is the authority):

- Base: everything in the Kaizo IronMON ruleset (the valiant-code gist the README links), on FireRed or LeafGreen.
- The added rule: before you leave any dungeon, every trainer in it must be beaten.
- Rule 0. Each dungeon is cleared completely on the visit where you enter it.
- Rule 1. Pokemon Tower: on your first entry, clear it up to the Marowak ghost.
- Rule 2. Victory Road is a dungeon and must be cleared.
- Rule 3. The Sevii Islands are allowed, and once unlocked they must be visited at some point before the Elite Four.
  - One, Two and Three Island together are one continuous dungeon.
  - All 26 trainers there must be beaten, with no Pokemon Center or NPC healing during the clear. Two of them are double
    battles, which need the HM friend in the party.
  - The Move Relearner may be used once on this trip if you can pay for it, for as many moves as you can afford.
  - The heal ban covers the Ember Spa's healing water on Kindle Road too.
  - If the HM friend faints in a double battle it may be revived only with a revive item or by depositing it in the PC;
    the PC on Three Island is not available until the Lostelle quest is done and the Iapapa Berry received.
  - You may take the Full Restore given after the four bikers, and keep the Iapapa Berry after the Hypno fight. No other
    items may be taken.
- Rule 4. After beating the Elite Four, the leaderboard place comes from the "Victory Lap" on Four, Five, Six and Seven
  Island.
  - These islands are one continuous dungeon (the README puts it at 75 or more trainers).
  - One Move Relearner visit on this trip; the same HM friend clause; a second HM friend may be caught for Rock Smash
    and Waterfall; no Center or NPC healing (the Kindle Road spa water included); no other items.
  - It is a death march: play until your Pokemon faints, and the score is the number of trainers beaten at that moment,
    as the tracker's `!progress sevii` reports it.
- Rule 5. Legendaries with a BST of exactly 580 are allowed as starters. If one is taken from the lab it must be used
  (unless another rule forbids it) and you may not pivot away from it, Diglett's Cave pivots included. If every lab
  choice is over 580 BST, Kaizo's usual rule applies: pivot at once or throw the seed away.
- Technical disclaimer: a ROM given the National Dex from the start (usually a randomizer setting) breaks the run at
  Sevii, because the game's Elite Four rematch mechanics switch on at the first visit to the islands and cannot be
  undone. Faster FireRed does not interfere with the late game.
- Resources the README lists: Zucchinipuff's Sevii guide, part I (Google Doc); Faster FireRed (DrMaple); Kelsey Young's
  interactive FRLG map (no Sevii); iAmSlammer's FRLG vision maps (part of Sevii); UTDZac's Stream Connect guide, needed
  for `!progress sevii`.

**Seasons.** Season 1 (2024): Puffsun, typo, ratcityretro; typo won. Season 2 ran 2026-02-01 to 2026-04-30 as a bet
(25 gifted Twitch subs, an emote slot for six months, or a charity opt-in of about $100), entries by 2026-02-28, up to
1,500 seeds each, scored by the furthest valid clear as `!progress sevii` reports it out of 447. Leaders: ecofroggy
376/447 (84.1%, the season record, emote bet) and puffsun 375/447 (83.9%, gift-sub bet); the table lists twelve
players and their clips.

## 2. How it works

### 2.1 No ROM or randomizer changes

Full Clearzo changes nothing in the ROM and nothing in the randomizer. Everything it checks is state the unmodified
game already keeps: one flag per trainer, the player's map, and a few story flags. The PC tracker reads those flags;
the community scores with its `!progress sevii` command.

### 2.2 Trainer flags

- Trainer N's "beaten" flag is event flag `0x500 + N` (`TRAINER_FLAGS_START`, pret:include/constants/flags.h:1319;
  `NUM_TRAINERS` 743 and `MAX_TRAINERS_COUNT` 768, pret:include/constants/opponents.h:754-755). Flags live in SaveBlock1
  at `0xEE0` on FireRed and LeafGreen, bit `(0x500+N) % 8` of byte `(0x500+N) / 8` (IT:ironmon_tracker/Program.lua:45
  and 1529-1539; `gameFlagsOffset: "EE0"` in IT:ironmon_tracker/GameAddresses/Pokemon FireRed v1.1.json:107 and
  Pokemon LeafGreen v1.0.json:31).
- The flag is set when you win (pret:src/battle_setup.c:906-951, `CB2_EndTrainerBattle`), and for the lab rival also
  when you lose (the early-rival branch at :908-933 heals and sets it either way).
- **A gym leader sets every trainer of his gym.** Each leader's victory script runs `set_gym_trainers N`
  (pret:data/maps/PewterCity_Gym/scripts.inc:19; Cerulean :17, Vermilion :220, Celadon :18, Fuchsia :17, Saffron :18,
  Cinnabar :63, Viridian :22 of their own scripts.inc), which `settrainerflag`s all the gym's trainers
  (pret:data/scripts/set_gym_trainers.inc). A skipped gym trainer therefore reads as beaten afterwards, in the game, the
  PC tracker and KaizoCore alike.
- **The Hall of Fame clears the Champion's flag.** `EventScript_ResetEliteFour` (pret:data/scripts/hall_of_fame.inc:24-37,
  called at pret:data/maps/PokemonLeague_HallOfFame/scripts.inc:37) clears trainers 438-440 and 739-741. Lorelei,
  Bruno, Agatha and Lance (410-413) keep theirs.
- A double-battle trainer will not fight a player without two usable Pokemon; it says its line and the flag stays clear
  (pret:data/scripts/trainer_battle.inc:22-39).
- LeafGreen uses the same ids as FireRed: one `opponents.h`, one set of map scripts (the only FR/LG branch in any map
  script is the Game Corner prize room), and KaizoCore's `gen3/routeinfo-firered.tsv` and `routeinfo-leafgreen.tsv` are
  identical.

### 2.3 The score: `!progress sevii`

`EventData.getProgress` (IT:ironmon_tracker/data/EventData.lua:1199-1247) sums, over every map in the tracker's FRLG
`RouteData.Info` that lists trainers, the trainers that `TrainerData.shouldUseTrainer` keeps (the player's rival
only, once known: IT:ironmon_tracker/data/TrainerData.lua:304-317) and whose flag is set. Without the `sevii` word it
stops at map id 230 (the first Sevii layout). The Stream Connect guide documents it, and `!dungeon` and
`!unfought dungeon sevii` beside it (IT wiki Stream-Connect-Guide.md:82-84, 95).

Counted from the tracker's own data (parsed by `parse_routes.py`): 96 maps, 463 entries, all distinct, none in the
excluded list; eight rival battles of three variants each leave **447** = **352 Kanto + 95 Sevii**. Consequences:

- Zucchinipuff's checkpoints (361 before Victory Road, 373 before the Elite Four) are exactly every Kanto trainer
  outside the League (347) plus Sevii part 1 (26), minus Victory Road's 12 for the first. The guide assumes route
  trainers are fought too; the rules only require dungeons, but the score counts everything.
- After the Hall of Fame the Champion's flag is cleared, so during the Victory Lap the count tops out at 446.
- Gym trainers skipped before a leader still count (2.2).
- Before the first rival battle every rival variant is in the total (463).
- Four Sevii trainers are missing from the tracker's data: Tanoby Ruins' 602 Ruin Maniac Brandon, 603 Ruin Maniac
  Benjamin, 604 Painter Edna, 605 Gentleman Clifford (all on pret:data/maps/SevenIsland_TanobyRuins/map.json, scripts
  at pret:data/scripts/trainers.inc:2762-2798). They never count toward 447. Every other trainer the tracker lists
  matches pret exactly by layout id (the only other difference: the tracker files S.S. Anne 2F room trainers 127, 223,
  482, 483 under layout 177 where pret's maps are layout 178; the S.S. Anne total is unaffected).

### 2.4 What counts as a dungeon

The Kaizo ruleset (inherited) defines one under "One Shot Dungeons": any hideout, any cave, any building with
trainers; forests are not (`app/src/main/assets/rulesets/FRLG/kaizo.md:42-47`). Its "No Way Out Gyms" rule already
makes gyms and the Fighting Dojo full clears (`kaizo.md:41`). Full Clearzo adds its two Sevii dungeons. The Gen 3 PC
tracker marks the same places `dungeon = true` (its `CombinedAreas` for multi-floor ones plus single maps: Oak's Lab,
the eight gyms, the Game Corner, the Dojo, the Rocket Warehouse; IT:ironmon_tracker/data/RouteData.lua:465-484 and the
`Info` entries). Walking every pret map with trainers (`pret_maps.json`) gives this list; there are no others in Kanto
(Viridian Forest is a forest; Cerulean City's rival and grunt are in a town; Seafoam, Cerulean Cave, Diglett's Cave
and the Power Plant have no trainers).

Counts are with one rival of three. Layout ids are what both trackers read as the map id (`gMapHeader + 0x12`).

| Dungeon | Map set (layout ids) | Trainers | Notes |
|---|---|---|---|
| Oak's Lab | 5 | 1 | rival; flag set win or lose |
| Pewter Gym | 28 | 2 | gym rule: leader flags the rest |
| Mt. Moon | 114, 115, 116 | 12 | |
| Cerulean Gym | 12 | 3 | |
| S.S. Anne | 118-123, 170, 171, 177, 178 | 17 | Kaizo FRLG rule: rival first; ship leaves after Cut |
| Vermilion Gym | 25 | 4 | |
| Rock Tunnel | 154, 155 | 15 | |
| Pokemon Tower | 161-167 | 17 | 14 up to Marowak, 3 after |
| Celadon Gym | 15 | 8 | |
| Celadon Game Corner | 27 | 1 | the poster grunt |
| Rocket Hideout | 128-131, 225 | 12 | Giovanni (348) included |
| Fuchsia Gym | 20 | 7 | |
| Silph Co. | 132-142, 229 | 32 | Giovanni hides the rest |
| Fighting Dojo | 228 | 5 | |
| Saffron Gym | 34 | 8 | |
| Pokemon Mansion | 143-146 | 7 | |
| Cinnabar Gym | 36 | 8 | |
| Viridian Gym | 37 | 9 | |
| Victory Road | 125, 126, 127 | 12 | one double |
| Pokemon League | 213-218 | 5 | fought in sequence |
| Sevii, part 1 | map sections, see 2.7 | 26 | two doubles |
| Sevii, part 2 (Victory Lap) | map sections, see 2.7 | 69 (73 with Tanoby) | four doubles; one boss hides five |

Kanto dungeons hold 185 trainers (124 outside gyms, the Dojo, the League, the Lab and the Game Corner). The PC
tracker's `CombinedAreas` miss a few trainer-less maps of these dungeons: S.S. Anne kitchen (170) and captain's
office (171), the Rocket Hideout elevator (225), the Silph elevator (229), the Hall of Fame (218). A "you are in this
dungeon" test must include them, or the elevator reads as leaving.

### 2.5 The dungeons, map by map

From pret (`trainerbattle_*` commands reachable from each map's objects and scripts, rematches excluded). Flag =
`0x500 + id`. "Rival" lines are one of three by starter (ids ordered Squirtle, Bulbasaur, Charmander teams). Gym
leaders are marked by name. Full per-trainer tables with flags, source lines and hide flags: `research-fullclearzo/tables.md`.

**Oak's Lab:** PalletTown_ProfessorOaksLab (5): 326/327/328 Rival.

**Pewter Gym:** PewterCity_Gym (28): 142 Camper Liam; 414 Leader Brock.

**Mt. Moon:**

| Map (layout) | Trainers |
|---|---|
| MtMoon_1F (114) | 91 Youngster Josh; 108 Bug Catcher Kent; 109 Bug Catcher Robby; 120 Lass Miriam; 121 Lass Iris; 169 Super Nerd Jovan; 181 Hiker Marcos |
| MtMoon_B2F (116) | 170 Super Nerd Miguel; 351, 352, 353, 354 Rocket Grunts |

**Cerulean Gym:** CeruleanCity_Gym (12): 150 Picnicker Diana; 234 Swimmer Luis; 415 Leader Misty.

**S.S. Anne:**

| Map (layout) | Trainers |
|---|---|
| SSAnne_2F_Corridor (120) | 426/427/428 Rival |
| SSAnne_1F_Room2 (177) | 96 Youngster Tyler; 126 Lass Ann |
| SSAnne_1F_Room5 (177) | 422 Gentleman Arthur |
| SSAnne_1F_Room7 (177) | 421 Gentleman Thomas |
| SSAnne_2F_Room2 (178) | 223 Fisherman Dale; 482 Gentleman Brooks |
| SSAnne_2F_Room4 (178) | 127 Lass Dawn; 483 Gentleman Lamar |
| SSAnne_B1F_Room1 (178) | 140 Sailor Phillip; 224 Fisherman Barny |
| SSAnne_B1F_Room2 (178) | 138 Sailor Huey |
| SSAnne_B1F_Room3 (178) | 139 Sailor Dylan |
| SSAnne_B1F_Room4 (178) | 136 Sailor Leonard; 137 Sailor Duncan |
| SSAnne_Deck (123) | 134 Sailor Edmond; 135 Sailor Trevor |

**Vermilion Gym:** VermilionCity_Gym (25): 141 Sailor Dwayne; 220 Engineer Baily; 423 Gentleman Tucker; 416 Leader Lt. Surge.

**Rock Tunnel:**

| Map (layout) | Trainers |
|---|---|
| RockTunnel_1F (154) | 168 Pokemaniac Ashton; 192 Hiker Lenny; 193 Hiker Oliver; 194 Hiker Lucas; 474 Picnicker Dana; 475 Picnicker Ariana; 476 Picnicker Leah |
| RockTunnel_B1F (155) | 158 Picnicker Sofia; 159 Picnicker Martha; 164 Pokemaniac Cooper; 165 Pokemaniac Steve; 166 Pokemaniac Winston; 189 Hiker Dudley; 190 Hiker Allen; 191 Hiker Eric |

**Pokemon Tower:**

| Map (layout) | Trainers |
|---|---|
| PokemonTower_2F (162) | 429/430/431 Rival |
| PokemonTower_3F (163) | 441 Channeler Patricia; 442 Channeler Carly; 443 Channeler Hope |
| PokemonTower_4F (164) | 444 Channeler Paula; 445 Channeler Laurel; 446 Channeler Jody |
| PokemonTower_5F (165) | 447 Channeler Tammy; 448 Channeler Ruth; 449 Channeler Karina; 450 Channeler Janae |
| PokemonTower_6F (166) | 451 Channeler Angelica; 452 Channeler Emilia; 453 Channeler Jennifer |
| PokemonTower_7F (167), after Marowak | 369, 370, 371 Rocket Grunts |

**Celadon Gym:** CeladonCity_Gym (15): 132 Lass Kay; 133 Lass Lisa; 160 Picnicker Tina; 265 Beauty Bridget; 266 Beauty
Tamia; 267 Beauty Lori; 402 Cooltrainer Mary; 417 Leader Erika.

**Celadon Game Corner:** CeladonCity_GameCorner (27): 357 Rocket Grunt.

**Rocket Hideout:**

| Map (layout) | Trainers |
|---|---|
| RocketHideout_B1F (128) | 358, 359, 360, 361, 362 Rocket Grunts |
| RocketHideout_B2F (129) | 363 Rocket Grunt |
| RocketHideout_B3F (130) | 364, 365 Rocket Grunts |
| RocketHideout_B4F (131) | 348 Giovanni; 366, 367, 368 Rocket Grunts |

**Fuchsia Gym:** FuchsiaCity_Gym (20): 288 Juggler Kirk; 289 Juggler Shawn; 292 Juggler Kayden; 293 Juggler Nate; 294
Tamer Phil; 295 Tamer Edgar; 418 Leader Koga.

**Silph Co.:**

| Map (layout) | Trainers |
|---|---|
| SilphCo_2F (133) | 336 Scientist Connor; 337 Scientist Jerry; 373, 374 Rocket Grunts |
| SilphCo_3F (134) | 338 Scientist Jose; 375 Rocket Grunt |
| SilphCo_4F (135) | 339 Scientist Rodney; 376, 377 Rocket Grunts |
| SilphCo_5F (136) | 286 Juggler Dalton; 340 Scientist Beau; 378, 379 Rocket Grunts |
| SilphCo_6F (137) | 341 Scientist Taylor; 380, 381 Rocket Grunts |
| SilphCo_7F (138) | 432/433/434 Rival; 342 Scientist Joshua; 383, 384, 385 Rocket Grunts |
| SilphCo_8F (139) | 343 Scientist Parker; 382, 386 Rocket Grunts |
| SilphCo_9F (140) | 344 Scientist Ed; 387, 388 Rocket Grunts |
| SilphCo_10F (141) | 345 Scientist Travis; 389 Rocket Grunt |
| SilphCo_11F (142) | 349 Giovanni; 390, 391 Rocket Grunts |

**Fighting Dojo:** SaffronCity_Dojo (228): 317 Black Belt Koichi (the Karate Master); 318 Mike; 319 Hideki; 320 Aaron;
321 Hitoshi (all Black Belts).

**Saffron Gym:** SaffronCity_Gym (34): 280 Psychic Johan; 281 Psychic Tyron; 282 Psychic Cameron; 283 Psychic Preston;
462 Channeler Amanda; 463 Channeler Stacy; 464 Channeler Tasha; 420 Leader Sabrina.

**Pokemon Mansion:**

| Map (layout) | Trainers |
|---|---|
| PokemonMansion_1F (143) | 335 Scientist Ted; 534 Youngster Johnson |
| PokemonMansion_2F (144) | 216 Burglar Arnie |
| PokemonMansion_3F (145) | 218 Burglar Simon; 346 Scientist Braydon |
| PokemonMansion_B1F (146) | 219 Burglar Lewis; 347 Scientist Ivan |

**Cinnabar Gym:** CinnabarIsland_Gym (36): 177 Super Nerd Erik; 178 Super Nerd Avery; 179 Super Nerd Derek; 180 Super
Nerd Zac; 213 Burglar Quinn; 214 Burglar Ramon; 215 Burglar Dusty; 419 Leader Blaine. (A right quiz answer opens a
door without a battle; the trainer stays fightable until Blaine falls.)

**Viridian Gym:** ViridianCity_Gym (37): 296 Tamer Jason; 297 Tamer Cole; 322 Black Belt Atsushi; 323 Black Belt Kiyo;
324 Black Belt Takashi; 392 Cooltrainer Samuel; 400 Cooltrainer Yuji; 401 Cooltrainer Warren; 350 Leader Giovanni.

**Victory Road:**

| Map (layout) | Trainers |
|---|---|
| VictoryRoad_1F (125) | 396 Cooltrainer Rolando; 406 Cooltrainer Naomi |
| VictoryRoad_2F (126) | 167 Pokemaniac Dawson; 287 Juggler Nelson; 290 Juggler Gregory; 298 Tamer Vincent; 325 Black Belt Daisuke |
| VictoryRoad_3F (127) | 393 Cooltrainer George; 394 Cooltrainer Colby; 403 Cooltrainer Caroline; 404 Cooltrainer Alexa; 485 Cool Couple Ray & Tyra (double) |

**Pokemon League:** 410 Lorelei (213); 411 Bruno (214); 412 Agatha (215); 413 Lance (216); 438/439/440 Champion (217).

**Sevii, part 1 (One, Two and Three Island; 26):**

| Map (layout) | Trainers |
|---|---|
| OneIsland_KindleRoad (237) | 518 Crush Girl Sharon; 547 Swimmer Maria; 548 Swimmer Abigail; 549 Swimmer Finn; 550 Swimmer Garrett; 551 Fisherman Tommy; 552 Crush Girl Tanya; 553 Black Belt Shea; 554 Black Belt Hugh; 555 Camper Bryce; 556 Picnicker Claire; 557 Crush Kin Mik & Kia (double) |
| OneIsland_TreasureBeach (238) | 546 Swimmer Amara |
| MtEmber_Exterior (280) | 592 Crush Girl Jocelyn; 595 Pkmn Ranger Logan; 597 Pkmn Ranger Beth |
| ThreeIsland (232) | 527, 528, 529 Biker Goons; 742 Cue Ball Paxton (a scripted run of four battles) |
| ThreeIsland_BondBridge (240) | 519 Tuber Amira; 523 Aroma Lady Nikki; 558 Aroma Lady Violet; 559 Tuber Alexis; 560 Twins Joy & Meg (double); 561 Swimmer Tisha |

Two Island, Cape Brink, Berry Forest and the rest of Mt. Ember hold no flagged trainers (the Hypno is a scripted wild
battle). The total, 12 + 1 + 3 + 4 + 6 = 26 with two doubles, matches the README and Zucchinipuff's guide.

**Sevii, part 2 (the Victory Lap; 69 in the PC tracker, 73 in the game):**

| Map (layout) | Trainers |
|---|---|
| MtEmber_Exterior (280) | 537, 538 Rocket Grunts (fight only after the Elite Four) |
| FourIsland_IcefallCave_Back (296) | 539 Rocket Grunt (scripted, with Lorelei) |
| FiveIsland_ResortGorgeous (246) | 525 Lady Jacki; 526 Painter Daisy; 562 Painter Celina; 563 Painter Rayna; 564 Lady Gillian; 565 Youngster Destin; 566 Swimmer Toby |
| FiveIsland_WaterLabyrinth (247) | 520 Pkmn Breeder Alize |
| FiveIsland_Meadow (248) | 567, 568, 569 Rocket Grunts |
| FiveIsland_MemorialPillar (249) | 570 Bird Keeper Milo; 571 Bird Keeper Chaz; 572 Bird Keeper Harold |
| FiveIsland_RocketWarehouse (292) | 516 Rocket Grunt; 541, 542 Rocket Grunts; 543 Rocket Admin; 544 Rocket Admin 2; 545 Scientist Gideon |
| FiveIsland_LostCave Room 1 (321), Room 4 (324), Room 10 (330) | 607 Ruin Maniac Lawson; 608 Psychic Laura; 606 Lady Selphy (scripted) |
| SixIsland_OutcastIsland (250) | 540 Rocket Grunt; 573 Fisherman Tylor; 574 Swimmer Mymo; 575 Swimmer Nicole; 576 Sis and Bro Ava & Geb (double) |
| SixIsland_GreenPath (251) | 517 Psychic Jaclyn |
| SixIsland_WaterPath (252) | 291 Juggler Edward; 577 Aroma Lady Rose; 578 Swimmer Samir; 579 Swimmer Denise; 580 Twins Miu & Mia (double); 581 Hiker Earl |
| SixIsland_RuinValley (253) | 524 Ruin Maniac Stanly; 582 Ruin Maniac Foster; 583 Ruin Maniac Larry; 584 Hiker Daryl; 585 Pokemaniac Hector |
| SixIsland_PatternBush (317) | 609 Pkmn Breeder Bethany; 610 Pkmn Breeder Allison; 611 Bug Catcher Garret; 612 Bug Catcher Jonah; 613 Bug Catcher Vance; 614 Youngster Nash; 615 Youngster Cordell; 616 Lass Dalia; 617 Lass Joana; 618 Camper Riley; 619 Picnicker Marcy; 620 Ruin Maniac Layton |
| SevenIsland_TrainerTower (254) | 586 Psychic Dario; 587 Psychic Rodette (outside the tower; the tower's own challengers use no trainer flags) |
| SevenIsland_SevaultCanyon_Entrance (255) | 521 Pkmn Ranger Nicolas; 522 Pkmn Ranger Madeline; 588 Aroma Lady Miah; 589 Young Couple Eve & Jon (double); 590 Juggler Mason |
| SevenIsland_SevaultCanyon (256) | 591 Crush Girl Cyndy; 593 Tamer Evan; 596 Pkmn Ranger Jackson; 598 Pkmn Ranger Katelyn; 599 Cooltrainer Leroy; 600 Cooltrainer Michelle; 601 Cool Couple Lex & Nya (double) |
| SevenIsland_TanobyRuins (257) | 602 Ruin Maniac Brandon; 603 Ruin Maniac Benjamin; 604 Painter Edna; 605 Gentleman Clifford (not in the PC tracker's data) |

Zucchinipuff's guide counts part 2 as 65 trainers plus 4 doubles, which is the tracker's 69 without Tanoby. The
README's estimate of 75 or more matches neither (unconfirmed which list the authors mean).

### 2.6 The edge cases, settled from the game's scripts

- **Pokemon Tower, "up to Marowak on first entry."** The Marowak ghost is a scripted wild battle on two tiles beside
  the 7F stairs, (11,15) and (12,16) on 6F (pret:data/maps/PokemonTower_6F/map.json, scripts.inc:4-28, beaten when
  `VAR_MAP_SCENE_POKEMON_TOWER_6F` 0x4059 becomes 1). All three 6F channelers stand before it. So the first-entry set is
  the 2F rival and the twelve channelers on 3F-6F, 14 trainers; the three 7F grunts (369-371) come on the story revisit
  with the Silph Scope and are in the way of Mr. Fuji anyway. Kaizo's FRLG rule "fight the rival to leave Lavender on
  the first visit" (`kaizo.md:62`, :122) also applies.
- **Silph Co.** 32 trainers. Beating Giovanni (349) runs `removeobject` on the two 11F grunts
  (pret:data/maps/SilphCo_11F/scripts.inc:68-74). `removeobject` sets the object's hide flag
  (pret:src/event_object_movement.c:1520-1528), and every grunt, scientist and Juggler Dalton in the building shares
  that flag, `FLAG_HIDE_SILPH_ROCKETS` (0x053, pret:include/constants/flags.h:99). So anything left when Giovanni falls
  disappears for good: under Full Clearzo the other 31 must come first. The 7F rival is a two-tile trigger
  (pret:data/maps/SilphCo_7F/map.json coord events at (2,4) and (2,5); the 11F warp pad is at (5,8)); whether a player
  can walk past it is unconfirmed, so the checklist should count him either way.
- **Rock Tunnel.** 15, all ordinary single battles (7 on 1F, 8 on B1F). No hide flags.
- **Victory Road.** 12, including 485 Cool Couple Ray & Tyra on 3F, a double that will not fight a player with one
  usable Pokemon. The README addresses doubles only for Sevii (open question 4).
- **Rocket Hideout.** 12. Its grunts share `FLAG_HIDE_MISC_KANTO_ROCKETS`, set only by the Viridian Gym
  (pret:data/maps/ViridianCity_Gym/scripts.inc:18), so nothing there vanishes early; Giovanni (348) leaves after his
  battle.
- **Mt. Moon.** 12; Miguel (170) is a scripted battle on the path. Same Rocket hide flag as the hideout.
- **S.S. Anne.** 17. The ship sails when the player steps off it after the captain's Cut
  (`DoSSAnneDepartureCutscene` once `VAR_MAP_SCENE_VERMILION_CITY` is 1, pret:data/maps/SSAnne_Exterior/scripts.inc:10-28),
  so it is a one-visit dungeon by the game's own design. Kaizo's FRLG rule puts the rival first (`kaizo.md:61`, :121).
- **Gyms.** Each leader flags his whole gym (2.2), so "beaten" after the leader cannot be told from "skipped" by flags
  alone. The order has to be watched: whoever is still unbeaten when the leader battle starts was skipped. This already
  matters for Kaizo's "No Way Out Gyms".
- **Sevii, part 1.** Unlocked after Blaine (Bill in the Cinnabar Pokemon Center; Faster FireRed removes his prompt but
  keeps him there). The 26 above. Meeting Celio disables the PC (`setflag FLAG_SYS_PC_STORAGE_DISABLED`,
  pret:data/maps/OneIsland_PokemonCenter_1F/scripts.inc:116); it is cleared at pret:data/maps/ThreeIsland_Port/scripts.inc:6,
  which fits the README's clause about the PC and the Lostelle quest. The two Mt. Ember grunts (537, 538) only talk until Celio
  asks for the Ruby (`VAR_MAP_SCENE_ONE_ISLAND_POKEMON_CENTER_1F` 0x4076 = 4, pret:data/maps/MtEmber_Exterior/scripts.inc:27
  and :62), so they belong to part 2 even though they stand on One Island; the guide says the same. The guide also notes
  that only one Bond Bridge trainer is needed before Berry Forest, so the rest may wait until after the Lostelle quest
  (allowed: the part is one dungeon).
- **Sevii, part 2.** Reached after the Elite Four (Celio's Rainbow Pass). Beating Rocket Admin 2 (544) removes the
  warehouse grunts and both admins and sets `FLAG_HIDE_FIVE_ISLAND_ROCKETS` (0x088)
  (pret:data/maps/FiveIsland_RocketWarehouse/scripts.inc:75 and 85-89). That flag also hides grunt 516 in the warehouse,
  the three Five Isle Meadow grunts (567-569) and the Outcast Island grunt (540). Those five must be beaten before Admin 2
  or they are gone. Gideon (545) stays.
- **Double battles.** The game has 14. Kanto dungeons: one (485, Victory Road). Sevii part 1: two (557, 560). Part 2:
  four (576, 580, 589, 601). The other seven are on Routes 8, 12, 14, 15, 16, 19 and 21 (484, 486-491), outside dungeons.
  Each double is one trainer id and one flag.
- **Trainers reachable only later.** Pokemon Tower 7F (Silph Scope), the Mt. Ember grunts (post-League), all of part 2
  (Rainbow Pass), and, outside dungeons, the late Route 22 rival.
- **Trainers that can be lost.** Silph Co. after Giovanni; the five Five Island Rockets after Admin 2; any S.S. Anne
  trainer once the ship sails; any gym trainer once the leader falls (flagged, so it looks beaten).
- **LeafGreen.** Same ids and flags as FireRed (2.2).

### 2.7 Telling where the player is

- Kanto dungeons: by layout id (`gMapHeader + 0x12`, which KaizoCore already reads), using the full map sets in 2.4.
  None of those layouts is shared with a map outside its dungeon (checked over all 425 pret maps).
- Sevii: layout ids do not work, because Sevii's Pokemon Centers, marts, houses and harbors share Kanto's layouts (8,
  9, 10, 11, 315). The region map section does: `gMapHeader + 0x14` is `regionMapSectionId`
  (pret:include/global.fieldmap.h:191-202). Values from pret:src/data/region_map/region_map_sections.json:
  - Part 1: ONE_ISLAND 0x8F, TWO_ISLAND 0x90, THREE_ISLAND 0x91, KINDLE_ROAD 0x96, TREASURE_BEACH 0x97, CAPE_BRINK
    0x98, BOND_BRIDGE 0x99, THREE_ISLE_PORT 0x9A, MT_EMBER 0xAF, BERRY_FOREST 0xB0, THREE_ISLE_PATH 0xB9, EMBER_SPA 0xC3.
  - Part 2: FOUR_ISLAND 0x92, FIVE_ISLAND 0x93, SEVEN_ISLAND 0x94, SIX_ISLAND 0x95, 0x9F-0xAA (Resort Gorgeous to Tanoby
    Ruins), ICEFALL_CAVE 0xB1, ROCKET_WAREHOUSE 0xB2, TRAINER_TOWER_2 0xB3 (the tower's interior), DOTTED_HOLE 0xB4,
    LOST_CAVE 0xB5, PATTERN_BUSH 0xB6, ALTERING_CAVE 0xB7, TANOBY_KEY 0xBA, the chambers 0xBC-0xC2.
  - Not part of either: NAVEL_ROCK 0xAE, BIRTH_ISLAND 0xBB (event islands), SPECIAL_AREA 0xC4 (link rooms: entering the
    Union Room from a Sevii center must not count as leaving). Kanto is 0x58-0x8E.
  - Before or after the League: `FLAG_SYS_GAME_CLEAR` (0x82C, pret:include/constants/flags.h:1378), set by
    `EnterHallOfFame` (pret:src/post_battle_event_funcs.c:12-26). Simplest rule: a Sevii map section before the flag is
    part 1, any Sevii map section after it is part 2 (which also puts the Mt. Ember grunts where they belong).
- The rival from the save: `VAR_STARTER_MON` (0x4031; 0 Bulbasaur, 1 Squirtle, 2 Charmander ball,
  pret:include/constants/vars.h:98) picks every rival battle (for example pret:data/maps/PokemonTower_2F/scripts.inc:31-33).
  Player 0 means the rival's "Right" variants (328, 331, ...), 1 "Left" (327, ...), 2 "Middle" (326, ...), matching the
  tracker's `whichRival` marks (IT:ironmon_tracker/data/TrainerData.lua:903-926).
- Vars live at SaveBlock1 + 0x1000 + (var - 0x4000) * 2 on FRLG (KaizoCore's `gameVarsOffset`).

### 2.8 The National Dex disclaimer, checked against the code

- Every official FRLG settings string turns "National Dex at start" on: `currentMiscTweaks` 334008 = 0x518B8 includes
  bit 0x80, `NATIONAL_DEX_AT_START` (engine-zx/src/com/dabomstew/pkrandomzx/MiscTweak.java:49; the dump in
  `ironmon-ref/modes-scratch/dump/dump-zx.tsv` shows 334008 for the gist's and the app's FRLG Standard, Ultimate,
  Kaizo, Super Kaizo, Survival and Doubles). So KaizoCore's `FRLG Kaizo.rnqs` is exactly the kind of game the
  disclaimer warns about.
- What that tweak does in the vendored ZX (engine-zx/src/com/dabomstew/pkrandomzx/romhandlers/Gen3RomHandler.java:2977-3056):
  after Oak's Pokedex it also calls `EnableNationalPokedex` (special 0x16F), and it rewrites every script check of the
  form "IsNationalPokedexEnabled equals 1" into "flag 0x82C (game clear) is set". Celio's Ruby request
  (pret:data/maps/OneIsland_PokemonCenter_1F/scripts.inc:207) and the Indigo Plateau door guard have that form, so they
  stay post-League.
- The Elite Four use their rematch teams when `FLAG_SYS_CAN_LINK_WITH_RS` (0x844) is set
  (pret:data/maps/PokemonLeague_LoreleisRoom/scripts.inc:57-62, the same in each room), and in pret only Celio sets it,
  after he is given the Sapphire (OneIsland_PokemonCenter_1F/scripts.inc:276).
- So the failure the README describes is not visible in pret plus the vendored patch. It may come from a different
  National Dex patch, or a script the byte pattern misses. Unconfirmed; it needs a device check (section 5). Turning the
  tweak off is not free either: ZX patches the level-up and stone evolution checks anyway
  (`attemptObedienceEvolutionPatches`, Gen3RomHandler.java:2922-2975), but pret also blocks non-Kanto evolutions in
  `src/evolution_scene.c` and `src/party_menu.c`, and whether ZX's patch covers those is unconfirmed.

### 2.9 The other resources

- **Zucchinipuff's Sevii guide** (https://docs.google.com/document/d/1S3WkgSmRDZDncyl_Am54WbPlOVK9o3bxn7nS3CEFOlc,
  exported as text 2026-10-02): part 1 in full (Bill at Cinnabar; the Two Island Game Corner starts the Lostelle quest;
  the PC is locked until it ends; one Move Relearner check, paid in mushrooms; Three Island's four bikers and the Full
  Restore; Bond Bridge; Berry Forest and the Iapapa Berry; Treasure Beach and Kindle Road by Surf, not entering Ember Spa
  yet; Mt. Ember's three trainers need Strength only; the two Rockets will not fight yet; back to Bill). Totals: 26
  trainers, two doubles, 62 Pokemon; checkpoints 361/447 and 373/447. Part 2: 65 trainers and 4 doubles (167 Pokemon),
  Rock Smash from the old man at Ember Spa, stay out of the healing water, a second HM friend; the document stops after
  Mt. Ember. No licence stated.
- **Kelsey Young's interactive map** (https://github.com/kelseyyoung/FRLGIronmonMap, MIT, HEAD `1282f84d70`,
  2026-08-26): `src/data/trainers.ts` has each Kanto trainer's name, party size, levels and pixel position; no Sevii. Its
  `src/assets/mapFiles/FRLGFiles.zip` (9 MB archive) was not downloaded.
- **iAmSlammer's FRLG Kaizo vision maps** (https://imgur.com/a/frlg-kaizo-vision-maps-A7m2Vsx): an image album
  (trainers' sight lines); the page answers, the images were not read.
- **Faster FireRed** (https://github.com/DrMaple/Faster-FireRed, no licence file, release 1.3.2 of 2024-03-19): QoL patch
  for FireRed 1.1; among its changes, Bill no longer prompts the Sevii trip after Blaine (he stays in the Cinnabar
  center), a Nurse in Viridian Forest, guaranteed step items in Sevii. KaizoCore already bundles it
  (`app/src/main/assets/patches/faster-firered-u-v11.ips`).
- **Stream Connect guide** (UTDZac, besteon wiki): documents `!progress sevii`, `!dungeon`, `!unfought`.
- **Typo's Discord**: not joined. Rulings made there (for example on doubles outside Sevii, Tanoby, settings) are
  unconfirmed here.

## 3. What KaizoCore already has

**Trainer flags and route data (tracker-gba).**
- `GbaTracker.trainerDefeated` reads flag `0x500 + id` from SaveBlock1 + `gameFlagsOffset`
  (`tracker-gba/src/main/kotlin/com/ironmonone/tracker/GbaTracker.kt:3010-3017`; offsets at :377-379, FRLG values at
  :549-551; the Nat. Dex builds read them from the ROM's slot table at :1105-1111).
- `gen3/routeinfo-firered.tsv` and `routeinfo-leafgreen.tsv` are the PC tracker's FRLG RouteData, converted by
  `tools/trainer-data/convert_route_info.py` (it runs the Lua with lupa). Checked: identical keys and lists, in the same
  order, to `IT:ironmon_tracker/data/RouteData.lua` (96 maps). Loaded by `routeInfoTable` (GbaTracker.kt:2316) and
  `routeTrainerIds`/`trainersOnRoute` (:2333-2337).
- `trainersForRoute` drops the other two rivals once known (:2413-2414); `TrainerInfo` has `doubleBattle` and `defeated`
  (:2424-2432); `rivalOf` and `whichRival` come from `gen3/rivals-frlg.tsv` (:2376-2390).
- `trainerCounts` = the PC's excluded list plus the rival filter (:2645, :2652-2656). `notebookAreas` and
  `notebookTrainerTotals(includeSevii)` (:2667-2690) give, with Sevii included, the same number as `!progress sevii`
  (all 463 entries are distinct and none is excluded, so per-map sums and distinct counts agree): 447 with the rival
  known.
- The state carries `routeTrainers` and `routeTrainersDefeated` for the carousel (:1563-1566, filled at :2071-2072);
  `mapHeader` + 0x12 is read at :1972-1975, adopted after two equal polls (:1984-1991); `gameVarsOffset` exists (:388).
- `readGameOver` returns WON on a win over trainer 438, 439 or 440 (:3220-3233).

**App.**
- Notebook: "Trainers Fought" X / Y with an "Include Sevii Islands" switch, and "Trainers by Area"
  (`app/src/main/kotlin/com/ironmonone/app/Notebook.kt:80`, :107, :147-149).
- `TrainersOnRouteDialog`: class and name, party size, level range, a check when beaten, tap for Trainer Info
  (`TrainerScreens.kt:35-68`), opened from the carousel's Trainers line (`SideScreens.kt:184-195`,
  `PlayScreen.kt:1762`); Trainer Info hides teams the way the PC does (`TrainerScreens.kt:81-103`).
- The carousel's Trainers line (`PcTracker.kt:1376-1377`, :1395).
- Modes are data: a preset in `app/src/main/assets/presets/`, its key from the file name (`RnqsInfo.kt:41-60`), the row
  and its one line (`RulesetCatalog.kt:45-48`, :103-116, :179-199), the rules page generated by
  `tools/rules/build_rules.py` (sources :40-52, chains :65-85, community rules :294-313 and :359-364, writer :370-418)
  into `app/src/main/assets/rulesets/<family>/<key>.md`, shown by `RulesDialog.kt:35-60`. Mode-keyed sets:
  `BstRule.kt:28`, `FavoriteRules.kt:30`, `CareerStats.kt:107-113`.
- "An official mode adds what its rules need, and the player keeps control": `PcHeals.arm` switches the Survival counter
  on once per run (`PcHeals.kt:47-52`, :74-85; `docs/PARITY-FINDINGS.md:228-235`). `GearScope` knows game and mode for
  Tracker Setup (`GearScope.kt:11-35`).
- A model for "cleared and left": the HGSS TourneyTracker's dungeon milestones, which need every listed trainer beaten
  and the player out of the map set (`DsExtras.kt:150-156`, sets at :161-166, "Full Cleared" milestones at :175 and
  :185, `FULL_CLEAR_IDS` :207).
- Run records: the game-over latch re-arms at each battle on Gen 1-3 (`GameOverLatch.kt:82-84`), and the first end of an
  attempt is its record (`RunHistory.kt:303-314`, :331-336).
- Credits: `AboutScreen.kt:483-498` and `NOTICE:443-475` (mode sources, commits, preset SHA-256s).
- The FRLG place pictures (`FrlgPictures.kt:24-58`) already show beside the Trainers on Route title when the player has
  turned them on (off by default).

**Gaps found on the way (they matter here, and some are parity bugs today).**
1. The carousel's "Trainers defeated" counts the current map only and every rival variant
   (GbaTracker.kt:2071-2072; `PlayScreen.kt:1738` and :2372 pass `routeTrainers.size`). The PC counts the combined area
   with `shouldUseTrainer` (IT:ironmon_tracker/screens/TrackerScreen.lua:867-905). On Silph Co. 7F the PC says x/32,
   KaizoCore x/7.
2. Trainers on Route lists the current map only and has no "Double!" mark; the PC lists the combined area
   (IT:ironmon_tracker/screens/TrainersOnRouteScreen.lua:147-162) and marks doubles (:313-327).
3. The notebook's areas are per map (GbaTracker.kt:2664-2665 says so; `docs/PARITY-FINDINGS.md:94`).
4. `rivalChoice` is learned only during a rival battle and not kept (GbaTracker.kt:2073, :2409-2410). After an app
   restart the totals read 463 until the next rival battle. `VAR_STARTER_MON` gives it from the save (2.7).
5. No Stream Connect, so no `!progress` (`docs/PARITY-FINDINGS.md:47`). The notebook total is the same number.
6. A Champion win files the run as won and a later loss shows that filing, so a Victory Lap would not be recorded.

## 4. How to add it

### 4.1 What the player sees, and whether it reveals anything

- **Rules page:** Full Clearzo's rules after Kaizo's, from the README (verbatim, as for the other community modes).
- **While in a dungeon** (Full Clearzo runs): a line under the route name, "Silph Co.: 20 of 32 left", that opens the
  dungeon's checklist: each trainer's class and name, party size, level range, "Double", and beaten / gone / skipped.
- **Sevii and the score:** "Sevii, part 1: x / 26" (then part 2), and "Trainers, Sevii included: X / 447 (Y%)", the
  leaderboard number, on the notebook index, the death card and the shared line.

Does any of that show what the PC tracker or the game hides? No, if it stays within the PC's own screens:
- Counts per combined area: the PC shows them on the carousel after a trainer battle, on `!dungeon` and in the notebook.
- Per-area lists with class, name, party size, level range, a defeated mark and "Double!": the PC's Trainers on Route.
- Grouping the Sevii routes into two parts adds no information: the PC shows each Sevii route's count with "sevii".
- "Gone" and "skipped" come from flags the game sets after the fact; the player saw it happen.

Three things would go past the PC and need Blake's call:
- **A warning before it happens** ("Giovanni removes the rest", "the leader flags his gym") is advice on how to play;
  leave it out by default (open question 6).
- **Trainer positions** (Kelsey's map has them): the PC shows none; leave them out.
- **Tanoby Ruins**: the PC does not count them. For the score keep 447 exactly as the PC; list Tanoby only if the
  authors say it belongs (open question 3).

Teams stay hidden by the existing `TrainerInfoView` rules.

### 4.2 First version

| # | Step | Files | Size |
|---|---|---|---|
| 1 | PC parity: combined areas and the rival | converter, tsv, GbaTracker, PlayScreen, TrainerScreens, Notebook | S to M |
| 2 | The mode: preset, key, line, rules page, credits | presets, RnqsInfo, RulesetCatalog, build_rules.py, NOTICE, AboutScreen | S |
| 3 | Dungeon data from pret | a converter and `gen3/fullclearzo-frlg.tsv` | S |
| 4 | Dungeon state in the tracker | GbaTracker (+ a small `FullClear.kt` in tracker-gba) | M |
| 5 | The screens | TrackerPanel/PcTracker, a checklist dialog, Notebook | M |
| 6 | The run's score and record | RunHistory, death card, share line | S to M |
| 7 | Tests | tracker-gba and app tests | M |

**Step 1, parity groundwork (helps every FRLG mode).**
- `tools/trainer-data/convert_route_info.py`: also write each `RouteData.Info` entry's area key and the area's name and
  `dungeon` flag (a 5th and 6th column, or a new `gen3/areas-<version>.tsv`). Re-run for all five Gen 3 versions.
- `GbaTracker`: `areaOf(mapId)` and `trainersInArea(mapId)` (the area's maps, else the map; filtered by `trainerCounts`).
  Use them for `routeTrainers`/`routeTrainersDefeated` (:2071-2072), for `trainersForRoute` (:2413) and for
  `notebookAreas` (one row per area, as `NotebookTrainersByArea` does).
- `GbaTracker`: when `rivalChoice` is null on FRLG, read `VAR_STARTER_MON` from the save and set it (0 Right, 1 Left,
  2 Middle).
- `TrainersOnRouteDialog`: add the "Double!" line from `TrainerInfo.doubleBattle`.
- Record it in `TRACKER-PARITY.md` and `docs/PARITY-FINDINGS.md`.

**Step 2, the mode.**
- Save the README at `1089734059` as `tools/upr-settings/community/FullClearzo-README.md`.
- `tools/rules/build_rules.py`: `SOURCES["fullclearzo"]` (repo URL, "README last changed 2026-02-25"),
  `MODE_LABEL["fullclearzo"] = "Full Clearzo"`, `CHAIN["fullclearzo"] = KAIZO + ["fullclearzo"]`, add the key to
  `MODE_KEYS`, and a `fullclearzo_rules()` that renders "General Rules for Clearzo" and "Technical DISCLAIMER" (and the
  Resources list). One change of order for this mode only: write "FireRed and LeafGreen: game-specific rules" before
  the Full Clearzo section, so that rule 3 (Sevii allowed and required) is the last word after Kaizo's FRLG rule
  against Sevii before the Elite Four (`kaizo.md:63` and :123). The generator's no-em-dash assertion
  (`build_rules.py:418`) still holds: the README has none in the sections rendered.
- Preset: `app/src/main/assets/presets/FRLG Full Clearzo.rnqs`, byte for byte `FRLG Kaizo.rnqs` (the README publishes no
  string of its own), unless the authors say to drop "National Dex at start" (open question 1). FRLG only; no Nat. Dex
  file (the README's disclaimer), so `RulesetCatalog.forRom` offers it on FireRed 1.0 and 1.1, LeafGreen, and their
  Smart AI and Faster FireRed builds (`core-api/src/main/kotlin/com/ironmonone/core/Generation.kt:327-350`), never on
  `FIRERED_NATDEX_121`.
- `RnqsInfo.kt:41-60`: add `"fullclearzo"` (it shares no substring with the other keys) and the label "Full Clearzo".
- `RulesetCatalog.kt`: add to `ORDER` (after `kaizodoubles`), and a line in `LINES` written from the rules file, for
  example "Kaizo, plus every trainer in a dungeon beaten before you leave it. The Sevii Islands come before the Elite
  Four, then a Victory Lap until your Pokemon faints."
- `BstRule.kt:28`: add `"fullclearzo"` to `FROM_KAIZO` (Kaizo's 599 line; rule 5 is about legendaries, not BST).
- Credits: `AboutScreen.kt` mode paragraph and links; `NOTICE` (source, commit, the preset's SHA-256).

**Step 3, the dungeon data.**
- New `tools/trainer-data/convert_fullclearzo.py`, reading a pinned pret checkout and the PC's RouteData (the same method
  as `research-fullclearzo/dungeons.py`), writing `tracker-gba/src/main/resources/gen3/fullclearzo-frlg.tsv`, one row
  per dungeon:
  `key  name  where  trainers  before  hides  firstEntry  notes`, where `where` is `layout:114,115,116` or
  `mapsec:0x8F,...` plus `+gameclear`/`-gameclear` for the Sevii parts; `trainers` in the PC's order; `before` is the
  trainer whose defeat ends the rest (each gym leader, 349 at Silph, 544 at the warehouse); `hides` the hide flag to
  read (0x053, 0x088); `firstEntry` the Tower's 14 and the Marowak var 0x4059.
- It asserts the totals in 2.4 and that every id is in RouteData except Tanoby's four (written with a "not counted by the
  PC tracker" mark, off by default).

**Step 4, dungeon state.**
- In `GbaTracker`: read the map section (`mapHeader + 0x14`), flag 0x82C, and any flag or var by number (the code in
  `trainerDefeated` and the safari read already does this for flags).
- `fullClearHere()`: the dungeon for the current map (stable layout id, then map section and game-clear for Sevii).
- `fullClearStatus(key)`: total, beaten, remaining ids, gone ids (hide flag set and trainer flag clear), skipped ids
  (gym trainers still unbeaten when the leader battle began: snapshot the gym's flags when `opponentTrainerId` is the
  leader).
- Per run, kept beside the run's marks the way `JoinedForms` keeps `joined.txt` (`BstRule.kt:65-119`): dungeons entered,
  dungeons left with trainers remaining, the gym snapshots. A restart keeps them.

**Step 5, the screens (Full Clearzo runs only; one Tracker Setup switch, on by itself the first time, as PcHeals does).**
- Inside a dungeon: the "N of M left" line under the route name; tap opens the checklist (Step 1's Trainers on Route
  over the dungeon, plus "gone" and "skipped", and the FrlgPictures map mark).
- Notebook index: the Sevii part line and "Trainers, Sevii included: X / 447 (Y%)".
- Leaving a dungeon with trainers left: one neutral line in the run's events ("Left Rock Tunnel with 2 of 15 unbeaten").
  No popup and no automatic end; the player decides (open question 5).

**Step 6, the score and the record.**
- For a Full Clearzo run, the Champion win does not file the run: the WON card says the Victory Lap starts, and the run is
  filed when it ends.
- Add the trainers count (beaten, total) to `RunRecord` for FRLG runs; show "X / 447 (Y%)" on the death card and in the
  shared line.

**Step 7, tests** (fake memory, the way `NotebookTest.kt` sets flags).
- The data: Mt. Moon 12, S.S. Anne 17, Rock Tunnel 15, Tower 17 (14 first entry), Silph 32, Victory Road 12, Sevii part
  1 26, part 2 69; every Kanto layout in exactly one dungeon; FireRed and LeafGreen tables equal.
- Parity: totals 447 with the rival known; Silph 7F carousel x/32; rival from `VAR_STARTER_MON` after a "restart".
- Edges: Silph with 0x053 set and a grunt's flag clear shows "gone"; 0x088 hides 516, 540, 567-569 but not Gideon; gym
  flags set at the leader with one trainer unbeaten at the snapshot shows "skipped" while the score still counts it (as
  the PC does); Champion flag cleared after the Hall of Fame drops the total by one; a Sevii center (layout 8, map
  section 0x8F) is part 1 before 0x82C and part 2 after; the Mt. Ember grunts count in part 2 only; the Union Room
  (0xC4) is not "leaving".
- Mode: `RnqsInfo` parses "FRLG Full Clearzo.rnqs"; `BundledPresetsTest.expected()` (:53-62) adds it to FRLG only;
  `RunScreenKaizoTest` expects 10 modes at :121 (make it 11) and needs a `Claim` for the new line (:136-190), which
  :192-204 reads back from the rules file; `RulesAssetsTest`'s heading map (:80-86) gets "Full Clearzo IronMON".

### 4.3 Later

- Kaizo's "One Shot Dungeons" and "No Way Out Gyms" for every Kaizo mode on FRLG, from Step 4's entered and skipped
  records (S).
- A line when a Pokemon Center heal happens during a Sevii part (game stats 15 and 16 rising while in a Sevii map
  section, as `PcHeals` counts them) (S).
- The dungeon line and the X / 447 score on the stream page (`app/src/main/assets/stream/tracker.html`) (S).
- Tanoby Ruins, if the authors count them (S).

### 4.4 Size

First version: **M** (steps 1-7; most of it in `GbaTracker` and two app screens; data is generated, not hand-kept).
With the later steps: M to L. This agrees with the FUTURE-PROJECTS estimate.

## 5. Open questions for Blake, and what to check on a device

**Questions** (several are for ratcityretro, Typo and Puffsun; Discord only):
1. Settings: ship the official FRLG Kaizo string unchanged, or the same with "National Dex at start" off as the
   disclaimer implies? The authors publish no string.
2. Which places the checklist covers: all of 2.4, or only caves, hideouts and buildings proper (leaving out Oak's Lab,
   the Game Corner and the League, and gyms and the Dojo, which Kaizo already covers)?
3. Tanoby Ruins' four trainers: part of the Victory Lap list or not? The PC count and the guide leave them out; the
   README's estimate (75 or more) fits neither count.
4. Victory Road's Cool Couple (485), a double: does the Sevii HM friend clause apply in Kanto too?
5. Leaving a dungeon with trainers left: a line on the run's record only, or something the player sees at once?
6. Should the checklist say before it happens that Giovanni (Silph), Admin 2 (warehouse) or a gym leader ends the
   chance at the rest? That is advice, which the dev team asked the app not to give.
7. Full Clearzo runs: file the run at the Victory Lap death with its X / 447, not at the Champion?
8. Nat. Dex builds: leave Full Clearzo off them, as the disclaimer suggests?
9. Credits: how Typo and Puffsun want to be named and linked (no GitHub accounts found).
10. Step 1's parity fixes change what every FRLG mode shows (combined areas): ship them separately first?

**Device checks once built:**
- Same save, same moment: KaizoCore's "X / 447" equals the PC tracker's `!progress sevii` (at Silph entry, before
  Victory Road, after the Hall of Fame).
- Silph Co.: the carousel after a battle reads x/32 and the 7F rival counts once; beat Giovanni with one grunt left, go
  down a floor and back: "gone".
- A gym: skip a trainer, beat the leader: "skipped" in the checklist, still counted in the score.
- Restart the app mid-run: totals stay 447 (rival from the save).
- Pokemon Tower first entry: "to Marowak" shows 14; after Marowak and the Silph Scope, 7F's three.
- Sevii part 1: in One Island's center and in the Union Room the panel stays "part 1"; Mt. Ember's grunts are not in
  part 1's 26.
- After the Hall of Fame: the score drops by one (the Champion's flag), exactly as on the PC.
- The disclaimer: with the official Kaizo preset, after Bill's first trip to One Island read flag 0x844 and see whether
  Lorelei opens with her rematch line; then the same with "National Dex at start" off, and check that a randomized
  evolution into a non-Kanto species still happens.
- Faster FireRed build, FireRed 1.0, LeafGreen 1.0: same counts.
- A double in Sevii with the HM friend: one flag, counted once.

## 6. Credits to show in the app

| Who | What | Licence (as published) |
|---|---|---|
| Typo, Puffsun, ratcityretro | Full Clearzo IronMON, its rules and seasons (repo by ratcityretro): https://github.com/ratcityretro/FullClearzo | none |
| Zucchinipuff | Full Clearzo Sevii Islands guide (Google Doc) | none stated |
| iAmSlammer | FRLG Kaizo vision maps (imgur) | none stated |
| Kelsey Young | FRLG IronMON interactive map | MIT |
| DrMaple | Faster FireRed (already credited in the app) | none |
| UTDZac and besteon | Ironmon-Tracker and Stream Connect, whose `!progress sevii` is the score; RouteData's trainer lists | MIT |
| valiant-code (rules by iateyourpie), UTDZac | the Kaizo IronMON rules and FRLG settings the mode builds on (already credited) | gists, no licence |
| pret | pokefirered, the map and trainer data the dungeon table is generated from | no licence file |

Blake's standing ruling is that KaizoCore has the licensing it needs; these are recorded for the credits.
