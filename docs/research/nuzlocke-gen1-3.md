# Nuzlocke facts for Generation 1 to 3

Red/Blue, Yellow, Gold/Silver, Crystal, Ruby/Sapphire, Emerald, FireRed/LeafGreen.

Written 2026-09-29 for KaizoCore's Nuzlocke mode and tracker. Research only: no code and no other file was changed. US English releases throughout. Every web page was fetched on 2026-09-29 (GitHub raw files from each repository's default branch that day).

## Not covered yet

All seven game sections (Red/Blue, Yellow, Gold/Silver, Crystal, Ruby/Sapphire, Emerald, FireRed/LeafGreen) have all three parts: level caps, rulings, and where the encounter-area list can come from. No game and no section is missing. What is not covered or not finished:

- Serebii cross-checks: Serebii has no comparable pages for Red/Blue and Gold/Silver, and its Emerald rival page was not read (0.3, 0.3.1).
- PokeAPI's roughly 20,000 wild encounter rows were not audited against the disassemblies; only the gift, prize, trade and static rows were (9.2, section 10).
- No per-area species lists or Gen 1 and Gen 2 grass slot odds are included (they come from PokeAPI or the ROM, 9.7). Gen 3 slot odds and Gen 2 fishing, Rock Smash and headbutt odds are included.
- Community rulings: the r/nuzlocke wiki and the Nuzlocke forums could not be read (0.5). Every ruling is written as a switch and every default is a proposal.
- The window in which the first Route 22 rival fight of Red/Blue and FireRed/LeafGreen can be skipped was not traced (2.1).

## Unconfirmed

Every number and ruling not listed here was checked against at least two sources (see the Src column of each table; the codes are in 0.3). One-source items are marked `unconfirmed` in place:

- Every "Suggested default" and "Suggested handling" column: the report author's proposals, no source.
- Every "Where it falls in a normal run" column: Bulbapedia's walkthrough order only.
- The Game Corner clause in 1.2 (one published FireRed ruleset), and the story-event, "Nature/Special Reserve" and FHH clauses cited from it.
- The four Jessie and James rows of Yellow: the disassembly and the community dataset agree, but there is no Bulbapedia or Serebii entry (marked "no B or S").
- The skip window of the first Route 22 rival fight in Red/Blue and FireRed/LeafGreen (2.1).
- Bulbapedia-only statements: the Crystal headbutt group counts (5.2.4), Crystal's Mt. Mortar layout and the Sneasel and Magmar changes (5.2.4), the Pineco and Heracross headbutt examples (4.2.4), the 16 Johto lines in Emerald's Safari expansions (7.2.4) and FireRed/LeafGreen's changed Safari catch system and Secret House (8.2.4).

## 0. Read this first

### 0.1 What is in this file

| Section | Content |
|---|---|
| Top of file | "Not covered yet" and "Unconfirmed": what is missing and what rests on one source |
| 0 | Method, source key, what could and could not be confirmed |
| 1 | Baseline Nuzlocke terms and where each one is documented |
| 2 to 8 | One self-contained section per game: (1) level caps, (2) rulings for gifts, trades, statics, special areas, tutorials, version exclusives, (3) where that game's list of encounter areas can come from |
| 9 | Data sources for an encounter-area list: URLs, licences, counts, known errors |
| 10 | Open gaps and how to close them |

### 0.2 Conventions

- **Cap** means the level of the highest-level Pokemon on the trainer's team in the first-run fight. Rematches are excluded. This is the definition Bulbapedia gives for the Hardcore "Level Cap" rule (the player may not use Pokemon above the level of the next Gym Leader, Elite Four member or Champion's highest-levelled Pokemon, their "ace") and the one Nuzlocke University uses for its cap list.
- **Versions are never merged.** Yellow, Crystal, Emerald and FireRed/LeafGreen have their own tables. Where two versions turn out identical (Gold, Silver and Crystal caps; Ruby and Sapphire gym caps) the numbers were computed separately for each disassembly and compared by script; the section says so. Red and Blue are one section because the disassembly, Bulbapedia and Nuzlocke University show one set of numbers; the same holds for Gold/Silver, Ruby/Sapphire and FireRed/LeafGreen. Version-only rows are labelled (Ruby:, Sapphire:, Red:, Blue:, Gold:, Silver:, FireRed:, LeafGreen:).
- **ASCII only.** The file uses "Pokemon" without the accent and no dashes beyond the plain hyphen so that any tool can read it.
- **Suggested default** in the ruling tables is the report author's recommendation for the tracker's default switch position, derived from the evidence in section 1. It is not a community standard unless the row says so. Every ruling should stay switchable.

### 0.3 Source key

The Sources column of every table uses these codes.

| Code | Source | URL |
|---|---|---|
| P | pret disassemblies: the game's own data and scripts as compiled into the ROM (every repository read at its default branch on 2026-09-29). Caps: pokered and pokeyellow `data/trainers/parties.asm`; pokegold and pokecrystal `data/trainers/parties.asm`; pokeruby `src/data/trainers_en.h` + `src/data/trainer_parties.h`; pokeemerald and pokefirered `src/data/trainers.h` + `src/data/trainer_parties.h`. Rulings: the gift, prize, trade, static-battle and tutorial scripts (Gen 1: `scripts/*.asm`, `data/events/*.asm`, `data/maps/objects/*.asm`; Gen 2: `maps/*.asm`, `data/events/npc_trades.asm`, `engine/events/*.asm`; Gen 3: `data/maps/*/scripts.inc`, `data/scripts/*.inc`, `src/data/ingame_trades.h`, `src/data/trade.h`, `src/trade.c`, `src/battle_setup.c`) | https://github.com/pret/pokered , https://github.com/pret/pokeyellow , https://github.com/pret/pokegold , https://github.com/pret/pokecrystal , https://github.com/pret/pokeruby , https://github.com/pret/pokeemerald , https://github.com/pret/pokefirered |
| B | Bulbapedia: trainer pages (party blocks), per-game walkthrough pages, in-game event lists, gift list, trade list, Game Corner pages | https://bulbapedia.bulbagarden.net/wiki/Brock (and one page per leader, see each table), https://bulbapedia.bulbagarden.net/wiki/Walkthrough:Pok%C3%A9mon_Red_and_Blue/Part_1 (and Part 2 to 17, the other games' series use the same pattern), https://bulbapedia.bulbagarden.net/wiki/Gift_Pok%C3%A9mon , https://bulbapedia.bulbagarden.net/wiki/List_of_in-game_trades |
| N | Nuzlocke University, "Hardcore Nuzlocke Level Caps by Generation" | https://nuzlockeuniversity.ca/2022/01/18/hardcore-nuzlocke-level-caps-by-generation/ |
| D | Community dataset behind the Nuzlocke Tracker, `leagues/*.txt` | https://github.com/domtronn/nuzlocke.data |
| A | PokeAPI data (CSV in the PokeAPI repository) | https://github.com/PokeAPI/pokeapi/tree/master/data/v2/csv |
| S | Serebii.net per-game database pages, used only to cross-check numbers (nothing is copied). Serebii has gym, Elite Four, rival, gift, trade and legendary pages for Yellow, Crystal, Ruby/Sapphire, Emerald and FireRed/LeafGreen, and none of that kind for Red/Blue or Gold/Silver, so sections 2 and 4 have no S | https://www.serebii.net/ (exact pages in 0.3.1) |

How the cap tables were made: each cap was computed by script from the pret party data (P), then looked up on Bulbapedia (B: the trainer's own page for gyms and Elite Four, the game's walkthrough pages for rival and boss fights), on Nuzlocke University (N: gyms, Elite Four, Champion, plus the Kanto gyms and Red in Gold/Silver/Crystal, Meteor Falls in Emerald), in the community dataset (D) and, for the five games Serebii covers, on Serebii (S: its gym, Elite Four and rival pages). A code appears in a row only when that source shows the same number. A code like `N(says 45)`, `D(says 34)` or `S(says 50)` means the source disagrees; `D(not listed)` means the dataset has no such battle.

#### 0.3.1 Serebii pages read (S)

All under https://www.serebii.net/ and read on 2026-09-29.

| Game | Caps (gyms, Elite Four, rival) | Gifts, trades, legendaries, Game Corner |
|---|---|---|
| Yellow | https://www.serebii.net/yellow/gyms.shtml , https://www.serebii.net/yellow/elitefour.shtml , https://www.serebii.net/yellow/rival.shtml | https://www.serebii.net/yellow/gift.shtml , https://www.serebii.net/yellow/trades.shtml , https://www.serebii.net/yellow/legends.shtml , https://www.serebii.net/yellow/gamecorner.shtml |
| Crystal | https://www.serebii.net/crystal/gyms.shtml , https://www.serebii.net/crystal/elitefour.shtml , https://www.serebii.net/crystal/rival.shtml | https://www.serebii.net/crystal/gift.shtml , https://www.serebii.net/crystal/trades.shtml , https://www.serebii.net/crystal/legends.shtml |
| Ruby and Sapphire | https://www.serebii.net/rubysapphire/gyms.shtml , https://www.serebii.net/rubysapphire/elitefour.shtml , https://www.serebii.net/rubysapphire/rivals.shtml | https://www.serebii.net/rubysapphire/gift.shtml , https://www.serebii.net/rubysapphire/trade.shtml , https://www.serebii.net/rubysapphire/legendary.shtml |
| Emerald | https://www.serebii.net/emerald/gym.shtml , https://www.serebii.net/emerald/elite.shtml | https://www.serebii.net/emerald/gift.shtml , https://www.serebii.net/emerald/trade.shtml , https://www.serebii.net/emerald/legendary.shtml |
| FireRed and LeafGreen | https://www.serebii.net/fireredleafgreen/gyms.shtml , https://www.serebii.net/fireredleafgreen/elitefour.shtml , https://www.serebii.net/fireredleafgreen/rival.shtml | https://www.serebii.net/fireredleafgreen/gift.shtml , https://www.serebii.net/fireredleafgreen/trades.shtml , https://www.serebii.net/fireredleafgreen/legendary.shtml , https://www.serebii.net/fireredleafgreen/gamecorner.shtml |

Serebii's Emerald rival page was not read, so the Emerald rival, Wally and villain rows have no S. Serebii's FireRed/LeafGreen Game Corner page lists no prizes, so the prize rows there rest on P, B and A.

### 0.4 What the cross-check found

All cap numbers in sections 2 to 8 are confirmed by at least two independent sources (P and B). Nuzlocke University (N) agrees on every gym, Elite Four and Champion number except one, and Serebii (S) agrees on every number it covers except two, both of which repeat another source's mistake (Lt. Surge in Crystal is 45 at N and at S; the late Route 22 rival fight in Yellow is 50 at D and at S). The gift, prize, trade and static-battle rows in the ruling tables were checked the same way: the species, level, place and price of each row are in the disassembly (P) and on Bulbapedia (B) and PokeAPI (A), and Serebii (S) confirms them for Yellow, Crystal, Ruby/Sapphire, Emerald and FireRed/LeafGreen. Exceptions and errors found in the sources (do not copy these values):

| Source | Where | Says | Correct (P and B) |
|---|---|---|---|
| Nuzlocke University | Gold/Silver and Crystal, Vermilion Gym (Lt. Surge) | 45 | 46 (Electabuzz L46) |
| Community dataset (D) | Yellow, Route 22 rival before Victory Road | 50 | 53 (evolved Eevee L53; Kadabra is 50) |
| Community dataset (D) | Gold/Silver/Crystal, Silver at Goldenrod Tunnel | 34 (Meganium) | 32 |
| Community dataset (D) | Crystal, Eusine at Cianwood City | 27 | 25 (27 is the HeartGold/SoulSilver value) |
| Community dataset (D) | Ruby/Sapphire, Drake | 54 | 55 (Salamence L55) |
| Community dataset (D) | Ruby/Sapphire, Wally at Mauville City | 17 | 16 |
| Community dataset (D) | Emerald, Matt at the Aqua Hideout | 37 | 34 |
| Community dataset (D) | Emerald, Maxie at Mt. Chimney (L25) | missing | 25 |
| Bulbapedia "Mass outbreak" page | Crystal swarm table | lists Marill and Snubbull swarms for Crystal | Crystal has swarms for Yanma, Dunsparce and Qwilfish only (pret, PokeAPI and Bulbapedia's own Crystal page agree) |
| PokeAPI | Red, Blue, Yellow, FireRed, LeafGreen | Magikarp gift at both the Route 3 and Route 4 Pokemon Centers | Route 4 only (Bulbapedia; pret has one salesman, in the Mt. Moon Pokemon Center at the Route 4 end of the cave in Red, Blue and Yellow and in `Route4_PokemonCenter_1F` in FireRed/LeafGreen) |
| PokeAPI | Ruby, Sapphire, Emerald | New Mauville shows 6 Voltorb + 2 Electrode; Emerald also shows Electrode in the Magma Hideout | New Mauville has 3 Voltorb (L25) in all three games (pret, Bulbapedia); the two fake-item Electrode are in the Team Magma Hideout (Ruby), Team Aqua Hideout (Sapphire, Emerald) |
| Serebii | Crystal, Vermilion Gym (Lt. Surge) | 45 (the same number Nuzlocke University gives) | 46 (Electabuzz L46) |
| Serebii | Yellow, Route 22 rival before Victory Road | 50 (the community dataset makes the same mistake) | 53 (evolved Eevee L53; Kadabra is 50) |
| Serebii | Crystal, who gives the Rainbow Wing | Eusine, after the three beasts are caught and shown to him in Celadon City | The three sages on Tin Tower 1F, once you are Champion and own all three beasts (pret `maps/TinTower1F.asm`, and Bulbapedia); Eusine in Celadon only mentions the rumour (pret `maps/CeladonPokecenter1F.asm`) |
| Serebii | Emerald, when Rayquaza can be caught | after you are Champion (the Ruby/Sapphire text repeated) | as soon as the Sootopolis events are done (Bulbapedia; pret opens the Sky Pillar through the Wallace scene in `data/maps/SkyPillar_Outside/scripts.inc`, while Ruby/Sapphire's `Route131` script sets the Sky Pillar layout only after the Hall of Fame) |
| Serebii | FireRed/LeafGreen, Togepi Egg | only needs a free party slot | two conditions in pret: the lead Pokemon must be at the top friendship tier and the party must have room (`data/maps/FiveIsland_WaterLabyrinth/scripts.inc`) |
| Serebii | Emerald, in-game trades | three (Fortree, Pacifidlog, Battle Frontier) | four: Rustboro City (Ralts for Seedot) is in pokeemerald `src/data/trade.h` and `data/maps/RustboroCity_House1/scripts.inc`, on Bulbapedia and in PokeAPI |

Things that rest on fewer than two independent sources are flagged where they occur. The main ones: the "typical place in a run" column of the optional cap tables (Bulbapedia walkthrough order only), the suggested-default columns (the report author's synthesis), the four Jessie and James rows of Yellow (the disassembly and the community dataset agree; Bulbapedia's party blocks and Serebii do not list them), the window in which the first Route 22 rival fight of Red/Blue and FireRed/LeafGreen can be skipped (not traced in the disassembly), and everything about community rulings, which by nature has no authoritative source (see 1.3). Section 10 lists what each gap needs.

### 0.5 Sources that could not be read

The r/nuzlocke wiki (login wall), nuzlockeforums.com (HTTP 403), Serebii's forum (403), the Fandom wikis (403), nuzlocke.app (HTTP 402 to any client) and Digital Trends (405) were not fetchable, so community rulings rest on the pages that were: Bulbapedia's Nuzlocke Challenge page, Nuzlocke University (rules, optional rules, level caps, Emerald guide), one published example ruleset, the Pokemon Reborn community guidelines and the Nuzlocke Tracker's own data. Serebii's per-game database pages were readable and are used as S.

## 1. Baseline terms and where they are documented

The Nuzlocke Challenge is player-imposed and "subject to variation" (Bulbapedia). There is no authority that rules on Route 3 gift Pokemon, so this section records what the readable sources actually say. Sections 2 to 8 apply these terms game by game.

### 1.1 The rules every source agrees on

| Term | What the sources say | Source |
|---|---|---|
| Limited encounters | Only the first wild Pokemon met in each area may be caught; if it faints or flees there is no second chance | Bulbapedia Nuzlocke Challenge; Nuzlocke University (NU) Nuzlocke Rules |
| Death | A Pokemon that faints is dead and released or permanently boxed; no revives; a whiteout is game over | same |
| Nicknames | "Near-universal" (Bulbapedia); universally accepted but not technically required (NU) | same |
| "Met in" check | When unsure whether a location is a new area (for example several floors of a cave), look at where the Pokemon says it was met | Bulbapedia (Near-universal rules) |
| Level Cap (Hardcore) | Do not use a Pokemon above the level of the next Gym Leader / Elite Four member / Champion's highest-levelled Pokemon. "Hardcore Nuzlocke" is the general name for variants that limit item use and over-levelling | Bulbapedia (Leveling restrictions, Variant rulesets) |
| Species (Dupes) Clause | If the first encounter in an area is a species (line) you already own, it does not count and you keep going | Bulbapedia (Decreased difficulty); NU Optional Rules |
| Shiny Clause | A shiny can be caught even if it is not the first encounter | same |
| Slow Start | The rules start only once the player has Poke Balls. Bulbapedia's example: the Poochyena (Ruby/Sapphire/ORAS) or Zigzagoon (Emerald) that attacks Professor Birch is not counted | Bulbapedia (Decreased difficulty) |
| Trading | "No Outside Trading": only Pokemon from in-game methods are allowed, so in-game trades are allowed; Trade Evolution Clause has "no firm consensus" | Bulbapedia (Near-universal rules) |

Sources: https://bulbapedia.bulbagarden.net/wiki/Nuzlocke_Challenge , https://nuzlockeuniversity.ca/nuzlocke-rules/ , https://nuzlockeuniversity.ca/optional-rules/

### 1.2 The rules that vary, with the evidence for each option

| Question | Options found | Evidence |
|---|---|---|
| Do NPC gift Pokemon use the area's encounter? | (a) Free, "Gift Clause": treat gifts as free encounters that do not count for the area. (b) Separate: some players count a gift as its own encounter beside the area's wild one (Bulbapedia's example is the Eevee in the Celadon Condominiums, called the Celadon Mansion roof elsewhere in this file). (c) Ban all gifts. (d) Giftlocke: only gifts and the starter are allowed | NU Optional Rules lists (a) under "easier" and (c) under "harder"; Bulbapedia Nuzlocke Challenge lists (b) and (d) |
| Do starters count? | No. "Starter Clause": the starter does not trigger the first-encounter rule. Optionally the starter is released after the first catch ("Caught Only") | One example ruleset (DA below); Bulbapedia (Caught Only); NU (Ban the use of Starter Pokemon) |
| Do static encounters (Snorlax, legendaries, fake-item Voltorb...) use the area's encounter? | (a) No: "traditional Nuzlockes allow any number of shinies and static encounters"; (b) a popular rule allows only one static and one shiny per area; (c) one static "story event" Pokemon per run (example ruleset); (d) legendaries banned ("Ban List") | Pokemon Reborn community guidelines (RE); example ruleset (DA); Bulbapedia (Ban List) |
| Safari Zone | (a) one catch per sector of the Zone ("On Safari": used because each sector has its own encounter pool and the Zone holds many Pokemon found nowhere else); (b) the whole Zone is one area (the "more restrictive interpretation"); (c) one Pokemon for the whole Zone, ignoring the first-encounter rule (example ruleset, "Nature/Special Reserve Clause") | Bulbapedia (On Safari); DA |
| Game Corner prizes | Example ruleset: not a first encounter, but only one may be bought ("Game Corner Clause"). No other source rules on it | DA (unconfirmed, one source) |
| Cave floors and buildings | Bulbapedia: use "Met in"; areas that share a name but are split by story progress may be two areas (its example is Unova's Pinwheel Forest). Example ruleset: multiple floors are not separate areas. The Nuzlocke Tracker's data merges floors into one area | Bulbapedia; DA; Nuzlocke Tracker data (section 9.4) |
| Fishing, headbutt (and rock smash) | Example ruleset ("FHH Clause"): one extra first encounter from fishing or a headbutt tree per area. NU's Emerald guide: Magikarp fished from a town "is a free encounter that doesn't take away from another encounter on a route", i.e. a town with water is its own area | DA; NU Emerald guide |
| Repel and other manipulation | "No Repels" is an optional hardening rule | Bulbapedia |

Sources: https://bulbapedia.bulbagarden.net/wiki/Nuzlocke_Challenge , https://nuzlockeuniversity.ca/optional-rules/ , https://nuzlockeuniversity.ca/2021/02/03/pokemon-emerald-nuzlocke-guide-and-tips/ , https://www.rebornevo.com/pr/nuzlocke/ (RE, Pokemon Reborn community guidelines: generic clause on statics and shinies) , https://www.deviantart.com/theseleneseipher/art/SP-1-Pokemon-Fire-Red-Nuzlocke-Rules-Settings-973567048 (DA, a single player's published FireRed ruleset; it is the only source found that rules on Game Corner prizes, story-event Pokemon and Safari Games, so it is cited as an example of one workable ruleset, not as consensus).

### 1.3 What that means for the tracker

- Ship the rulings as switches, not as fixed rules. The switches the evidence supports: gifts (free / count / banned), statics (free / one per area / one per run / banned), Safari (per sector / whole Zone), floors (merged / per floor), fishing and headbutt (shared with the area / extra), Game Corner prizes (free / one / banned), dupes clause and shiny clause on or off.
- Record every special acquisition with a kind (starter, gift, egg, fossil, purchase, prize, in-game trade, static, roaming, swarm, wild) so a switch can act on it. PokeAPI already carries most of these kinds as encounter methods (see section 9).
- The defaults suggested in each game's tables are: starters, gifts, eggs, fossils, purchases and trades free; statics free with an optional "one per area" limit; legendaries flagged for the ban option; Safari per sector; caves merged into one area with a per-floor option. They are the author's synthesis from the sources above.

## 2. Pokemon Red and Pokemon Blue (Generation I)

### 2.1 Level caps (Hardcore Nuzlocke)

Gym Leaders, Elite Four and Champion, first run. Red and Blue share every number below (pokered's trainer table is one table for both).

| # | Battle | Location | Cap (highest level) | Ace / top Pokemon | Sources |
|---|---|---|---|---|---|
| 1 | Brock | Pewter City (Pewter Gym) | 14 | Onix | P B N D |
| 2 | Misty | Cerulean City (Cerulean Gym) | 21 | Starmie | P B N D |
| 3 | Lt. Surge | Vermilion City (Vermilion Gym) | 24 | Raichu | P B N D |
| 4 | Erika | Celadon City (Celadon Gym) | 29 | Victreebel / Vileplume | P B N D |
| 5 | Koga | Fuchsia City (Fuchsia Gym) | 43 | Weezing | P B N D |
| 6 | Sabrina | Saffron City (Saffron Gym) | 43 | Alakazam | P B N D |
| 7 | Blaine | Cinnabar Island (Cinnabar Gym) | 47 | Arcanine | P B N D |
| 8 | Giovanni | Viridian City (Viridian Gym) | 50 | Rhydon | P B N D |
| E4-1 | Lorelei | Indigo Plateau | 56 | Jynx / Lapras | P B N D |
| E4-2 | Bruno | Indigo Plateau | 58 | Machamp | P B N D |
| E4-3 | Agatha | Indigo Plateau | 60 | Gengar | P B N D |
| E4-4 | Lance | Indigo Plateau | 62 | Dragonite | P B N D |
| C | Champion (Blue, your rival) | Indigo Plateau | 65 | Blastoise / Venusaur / Charizard (his starter's final form) | P B N D |

Sources: P https://raw.githubusercontent.com/pret/pokered/master/data/trainers/parties.asm ; B one page per trainer, for example https://bulbapedia.bulbagarden.net/wiki/Brock (Pewter Gym block, game code RGB), https://bulbapedia.bulbagarden.net/wiki/Lorelei , https://bulbapedia.bulbagarden.net/wiki/Blue_(game) (Champion), and the walkthrough pages https://bulbapedia.bulbagarden.net/wiki/Walkthrough:Pok%C3%A9mon_Red_and_Blue/Part_16 ; N https://nuzlockeuniversity.ca/2022/01/18/hardcore-nuzlocke-level-caps-by-generation/ (section "Pokemon Red/Blue") ; D https://github.com/domtronn/nuzlocke.data/blob/main/leagues/rb.txt

Other cap points that players and the Nuzlocke Tracker treat as checkpoints (the Tracker lists the seven rival fights and the two Giovanni fights as "boss" markers in Red's and Blue's route order). NU's cap list does not include them, so they are optional caps:

| Battle | Location | Cap | Where it falls in a normal run (unconfirmed, Bulbapedia order only) | Sources |
|---|---|---|---|---|
| Rival, Professor Oak's Lab | Pallet Town | 5 | Start of game (Slow Start / Starter clauses normally skip it) | P B D |
| Rival, Route 22 (first) | Route 22 | 9 | Right after Viridian City, before Viridian Forest and Gym 1 (walk-on trigger, see the note above) | P B D |
| Rival, Cerulean City | Cerulean City | 18 | Between Gym 1 and Gym 3 (Bulbapedia walkthrough places it with Misty) | P B D |
| Rival, S.S. Anne | S.S. Anne 2F | 20 | Just before Gym 3 (Lt. Surge) | P B D |
| Giovanni, Rocket Hideout | Rocket Hideout B4F (Celadon City) | 29 | Around Gym 4 (Erika) | P B D |
| Rival, Pokemon Tower | Pokemon Tower 2F (Lavender Town) | 25 | Around Gym 4-5, after the Rocket Hideout | P B D |
| Rival, Silph Co. | Silph Co. 7F (Saffron City) | 40 | Around Gym 6 (Sabrina) | P B D |
| Giovanni, Silph Co. | Silph Co. 11F (Saffron City) | 41 | Around Gym 6 (Sabrina) | P B D |
| Rival, Route 22 (last before the League) | Route 22 (below Victory Road) | 53 | After all 8 badges, before Victory Road | P B D |

Sources: P pokered `data/trainers/parties.asm` (Rival1Data, Rival2Data, Rival3Data, GiovanniData: three entries per battle, one per starter, all with the same top level); B https://bulbapedia.bulbagarden.net/wiki/Walkthrough:Pok%C3%A9mon_Red_and_Blue/Part_1 to Part_16 (each rival and Giovanni fight is listed with its location); D `leagues/rb.txt` (blocks r1 to r7, g1, g2); the "typical place" column is read from which walkthrough part the fight sits in (Bulbapedia only).

Order and flexibility:

- The table shows the usual order. Bulbapedia's Gym Badge page (Trivia) says the Thunder, Rainbow, Soul, Marsh and Volcano Badges (gyms 3, 4, 5, 6, 7) can be won in almost any order, with one stipulation: the Soul Badge (Fuchsia) must come before the Volcano Badge (Cinnabar), because Surf is needed to reach Cinnabar. https://bulbapedia.bulbagarden.net/wiki/Gym_Badge
- The Earth Badge (Viridian, Giovanni) is always last: pokered `scripts/ViridianCity.asm` (ViridianCityCheckGymOpenScript) only opens the gym when the badge byte holds every badge except the Earth Badge. https://raw.githubusercontent.com/pret/pokered/master/scripts/ViridianCity.asm
- Koga and Sabrina both cap at 43, so their order never changes the cap. Bulbapedia's walkthrough visits Saffron before Fuchsia; NU and the Nuzlocke Tracker list Koga as gym 5. Blaine (47) can be fought before Koga and Sabrina once Surf is in hand. In practice: the cap is the top level of the leader you are about to fight, and for gyms 3 to 7 there is no fixed sequence to encode.
- The first Route 22 rival fight (9) is a walk-on trigger west of Viridian City. Bulbapedia calls the equivalent fight optional in Yellow; pokered `scripts/Route22.asm` starts it only while EVENT_ROUTE22_RIVAL_WANTS_BATTLE is set and the player steps on the trigger tiles, which fits "skip it by not going there", but the exact window in Red and Blue was not traced (unconfirmed). The second Route 22 fight (53) is mandatory before Victory Road.

### 2.2 Rulings

#### 2.2.1 Gift, purchased and prize Pokemon (Red and Blue)

| Pokemon | Where and how | Level | Suggested default (unconfirmed proposal) | Src |
|---|---|---|---|---|
| Bulbasaur, Charmander or Squirtle (choose one) | Professor Oak's Laboratory, Pallet Town | 5 | Free (starter) | P B A |
| Eevee | Celadon Mansion roof: a Poke Ball on the table in the roof room | 25 | Free (gift) | P B A |
| Hitmonlee or Hitmonchan (choose one) | Fighting Dojo, Saffron City, after beating the Karate Master | 30 | Free (gift) | P B A |
| Lapras | Silph Co. 7F, from a Silph employee after beating Blue there | 15 | Free (gift) | P B A |
| Omanyte (Helix Fossil) or Kabuto (Dome Fossil), choose one | Fossils from the Super Nerd at the end of Mt. Moon; revived at the Pokemon Lab, Cinnabar Island | 30 | Free (gift) | P B A |
| Aerodactyl (Old Amber) | Old Amber from a scientist in the back of the Pewter Museum of Science (needs Cut); revived at Cinnabar Lab. This is a second fossil on top of the Mt. Moon one | 30 | Free (gift) | P B A |
| Magikarp | Salesman in the Pokemon Center at the Route 4 end of Mt. Moon (pret map `MtMoonPokecenter`), $500 | 5 | Free (purchase) | P B A |
| Red: Abra, Clefairy, Nidorina, Dratini, Scyther, Porygon | Celadon City Game Corner prize exchange: Abra L9 for 180 coins, Clefairy L8 for 500, Nidorina L17 for 1200, Dratini L18 for 2800, Scyther L25 for 5500, Porygon L26 for 9999 | see row | Free (prize); switch for "one prize only" | P B A |
| Blue: Abra, Clefairy, Nidorino, Pinsir, Dratini, Porygon | Same counter: Abra L6 for 120 coins, Clefairy L12 for 750, Nidorino L17 for 1200, Pinsir L20 for 2500, Dratini L24 for 4600, Porygon L18 for 6500 | see row | Free (prize); switch for "one prize only" | P B A |

Sources: https://bulbapedia.bulbagarden.net/wiki/Gift_Pok%C3%A9mon (Generation I), https://bulbapedia.bulbagarden.net/wiki/List_of_in-game_event_Pok%C3%A9mon_in_Generation_I , https://bulbapedia.bulbagarden.net/wiki/Celadon_Game_Corner (Prize corner, Generation I) ; A `encounters.csv` + `encounter_slots.csv` rows with method gift and condition `coins-*` for version_id 1 (Red) and 2 (Blue). The Nidorina/Scyther prizes are Red-only and the Nidorino/Pinsir prizes Blue-only, matching the version-exclusive lists in 2.2.6. P (https://raw.githubusercontent.com/pret/pokered/master/): `scripts/OaksLab.asm` (starter at level 5), `scripts/CeladonMansionRoofHouse.asm` (Eevee L25), `scripts/FightingDojo.asm` (Hitmonlee or Hitmonchan L30), `scripts/SilphCo7F.asm` (Lapras L15), `scripts/CinnabarLabFossilRoom.asm` (revived fossil Pokemon, Old Amber included, L30), `scripts/MtMoonPokecenter.asm` (Magikarp L5), `data/events/prizes.asm` and `data/events/prize_mon_levels.asm` (every prize, price and level in the two Game Corner rows). Serebii has no Red/Blue pages, so there is no S.

#### 2.2.2 In-game trades (Red and Blue, identical)

| Where | You give | You get | Src |
|---|---|---|---|
| Route 2 (gatehouse) | Abra | Mr. Mime | P B A |
| Underground Path (Routes 5-6) | Nidoran (male) | Nidoran (female) | P B A |
| Route 11 | Nidorino | Nidorina | P B A |
| Route 18 | Slowbro | Lickitung | P B A |
| Cerulean City | Poliwhirl | Jynx | P B A |
| Vermilion City | Spearow | Farfetch'd | P B A |
| Cinnabar Lab | Raichu | Electrode | P B A |
| Cinnabar Lab | Venonat | Tangela | P B A |
| Cinnabar Lab | Ponyta | Seel | P B A |

Suggested default: free, like gifts (Bulbapedia's "No Outside Trading" rule allows in-game trades; the received Pokemon has a different Original Trainer). Sources: https://bulbapedia.bulbagarden.net/wiki/List_of_in-game_trades (Generation I, "Red and Green (Japan), Red and Blue (Western)") ; A rows with method npc-trade. P pokered `data/events/trades.asm` holds these nine trades and an unused tenth entry (Butterfree for Beedrill) that no map uses.

#### 2.2.3 Static, one-off and item-ball encounters (Red and Blue)

| Encounter | Where | Level | Notes | Suggested default (unconfirmed proposal) | Src |
|---|---|---|---|---|---|
| Snorlax (two set encounters) | Route 12 and Route 16 | 30 | Woken with the Poke Flute; catching or defeating at least one is needed to reach Fuchsia | Free (static) | P B A |
| Voltorb x6 and Electrode x2 | Power Plant (disguised as item balls) | 40 and 43 | Each ball is its own battle | Free (static); watch the "one per area" switch | P B A |
| Zapdos | Power Plant | 50 | Legendary | Free, flag for the legendary ban | P B A |
| Articuno | Seafoam Islands B4F | 50 | Legendary | Free, flag for the legendary ban | P B A |
| Moltres | Victory Road 2F | 50 | Legendary (in FireRed/LeafGreen it moved to Mt. Ember) | Free, flag for the legendary ban | P B A |
| Mewtwo | Cerulean Cave B1F | 70 | Only after entering the Hall of Fame | Post-game; flag for the legendary ban | P B A |

Sources: https://bulbapedia.bulbagarden.net/wiki/List_of_in-game_event_Pok%C3%A9mon_in_Generation_I ; A rows with method static and pokeflute; P https://raw.githubusercontent.com/pret/pokered/master/data/maps/objects/PowerPlant.asm (six VOLTORB L40 objects, two ELECTRODE L43, one ZAPDOS L50). Also P `data/maps/objects/SeafoamIslandsB4F.asm` (Articuno L50), `data/maps/objects/VictoryRoad2F.asm` (Moltres L50), `data/maps/objects/CeruleanCaveB1F.asm` (Mewtwo L70), `scripts/Route12.asm` and `scripts/Route16.asm` (Snorlax L30).

#### 2.2.4 Special areas and handling (Red and Blue)

| Area | Fact | Suggested handling (unconfirmed proposal) |
|---|---|---|
| Safari Zone | Four sectors with their own walk tables and fishing (PokeAPI names them Safari Zone middle, Area 1 east, Area 2 north, Area 3 west); entry $500 for 30 Safari Balls, no battling. Bulbapedia notes that in Generation I "Center Area" names both the entrance hub and the area east of it. Kangaskhan and Tauros are wild only here (PokeAPI and Bulbapedia's Safari Zone page) | Switch: per sector (Bulbapedia's "On Safari") or the whole Zone as one area |
| Mt. Moon (1F, B1F, B2F), Rock Tunnel (2 floors), Seafoam Islands (1F to B4F), Cerulean Cave (1F, 2F, B1F), Victory Road (1F to 3F), Pokemon Tower (3F to 7F have wild encounters), Pokemon Mansion (1F to B1F), Power Plant, Diglett's Cave | PokeAPI splits every one into floor areas (74 areas over 45 locations for Red and Blue); the Nuzlocke Tracker's list keeps one area per location (45) | One area per location by default (floors merged), per-floor switch |
| Towns with water (Pallet, Viridian, Cerulean, Vermilion including the S.S. Anne dock, Celadon, Fuchsia, Cinnabar) | These have fishing tables of their own and no grass. NU (Emerald guide) treats a town's fished Pokemon as separate from the route encounters | Town water is its own area |
| Sea Routes 19, 20, 21 | Surf and fishing only; Route 21 also has grass | Own areas |
| Old Rod, Good Rod, Super Rod | Old Rod always gives Magikarp (L5) and Good Rod gives Goldeen or Poliwag (L10, half each), anywhere. Super Rod has its own table for each of 33 maps in pokered (10 fishing groups of 2 to 4 species, for example Pallet Town and Viridian City share Tentacool and Poliwag), including town water, the Cerulean Gym pool, Vermilion Dock and each Safari Zone sector | Fishing shares the area's slot unless "fishing is an extra encounter" (the FHH clause in the example ruleset) is on; only the Super Rod adds variety |
| Pokemon Tower (ghosts) | Until the Silph Scope is in your bag, every wild battle on 3F to 7F (1F and 2F have no wild table) is an unidentifiable Ghost that cannot be caught; with the Scope the same grass gives ordinary Gastly, Haunter and Cubone. The ghost Marowak on 6F cannot be caught even with the Scope. pret: `IsGhostBattle` (`engine/battle/core.asm`) is true on Pokemon Tower maps while the Silph Scope is missing, and `ItemUseBall` (`engine/items/item_effects.asm`) makes the ball fail for a ghost and for the Marowak | The tower's encounter is only usable after you own the Scope (picked up on Rocket Hideout B4F, under the Celadon Game Corner). The ghost fights are not encounters |
| Route 22 and Route 23 | Route 22 is reachable from the start | Normal areas |
| Cerulean Cave | Post-game only | Exclude from first-run lists, show after the Hall of Fame |
| Rocket Hideout, Silph Co., Pokemon Tower 1F-2F, Underground Paths | No wild encounters | Not areas |

Sources: https://bulbapedia.bulbagarden.net/wiki/Kanto_Safari_Zone ; A `location_areas.csv` + `encounters.csv` for version_id 1 and 2; https://bulbapedia.bulbagarden.net/wiki/Nuzlocke_Challenge ; Nuzlocke Tracker `routes.json` keys `red` and `blue`. P pokered `engine/battle/core.asm` (IsGhostBattle), `engine/items/item_effects.asm` (ItemUseBall), `data/wild/maps/PokemonTower*.asm` (encounter rate 0 on 1F and 2F), `data/wild/good_rod.asm`, `data/wild/super_rod.asm`, `scripts/RocketHideoutB4F.asm` (Silph Scope). Safari Zone entry: `scripts/SafariZoneGate.asm` (pokered: $500 fee, 30 Safari Balls).

#### 2.2.5 Scripted encounters that must not count (Red and Blue)

- **Old Man, Viridian City:** after Oak's Parcel he demonstrates catching a Weedle (L5). In Red and Blue the tutorial can be skipped by not talking to him. This Weedle is never yours and is not an encounter. Bulbapedia https://bulbapedia.bulbagarden.net/wiki/Old_man_(Kanto) ; pret `scripts/ViridianCity.asm` sets BATTLE_TYPE_OLD_MAN with WEEDLE.
- **Rival battle in Oak's Lab** and the rival's starter: not an encounter (Starter clause).

#### 2.2.6 Version-exclusive Pokemon (Red versus Blue)

Not obtainable in the other game without trading (Bulbapedia): Red has Ekans, Arbok, Oddish, Gloom, Vileplume, Mankey, Primeape, Growlithe, Arcanine, Scyther and Electabuzz. Blue has Sandshrew, Sandslash, Vulpix, Ninetales, Meowth, Persian, Bellsprout, Weepinbell, Victreebel, Magmar and Pinsir. Wild-table differences in the PokeAPI data agree (Red: Ekans, Arbok, Oddish, Gloom, Mankey, Growlithe, Scyther, Electabuzz; Blue: Sandshrew, Sandslash, Vulpix, Meowth, Bellsprout, Weepinbell, Magmar, Pinsir). Prize differences: Red has Nidorina and Scyther at the Game Corner, Blue has Nidorino and Pinsir. The in-game trades are the same in both. Ship separate encounter tables for Red and Blue. Source: https://bulbapedia.bulbagarden.net/wiki/Pok%C3%A9mon_Red_and_Blue_Versions (Version-exclusive Pokemon) ; A encounters for version_id 1 versus 2.

### 2.3 Where the encounter-area list can come from (Red and Blue)

| Source | What it gives for Red/Blue | Licence |
|---|---|---|
| PokeAPI | 74 location areas with encounters across 45 locations; methods walk, surf, old-rod, good-rod, super-rod, gift, static, pokeflute, npc-trade; prize prices and trade partners as conditions | BSD-3-Clause |
| Nuzlocke Tracker data (`routes.json`, keys `red`, `blue`) | 45 Nuzlocke-style areas in story order with 22 boss markers (8 gyms, 7 rival fights, 2 Giovanni fights, 4 Elite Four, Champion) | BSD-3-Clause in the app repository; the separate data repository has no licence |
| pret pokered | `data/wild/maps/*.asm` (59 files), `data/wild/grass_water.asm`, `good_rod.asm`, `super_rod.asm`, and `data/maps/names.asm` (53 town and route names) | No licence file (not shippable) |
| Bulbapedia | Route and location pages plus https://bulbapedia.bulbagarden.net/wiki/List_of_locations_by_index_number_(Generation_I) | CC BY-NC-SA 2.5 (not shippable in a GPL-3.0 app) |

Details and caveats: section 9.

## 3. Pokemon Yellow (Generation I)

Yellow has its own Gym Leader teams, its own rival (Eevee line), its own trades and its own encounter tables. Nothing below is shared with Red/Blue except where a row says the number is equal.

### 3.1 Level caps (Hardcore Nuzlocke)

| # | Battle | Location | Cap (highest level) | Ace / top Pokemon | Sources |
|---|---|---|---|---|---|
| 1 | Brock | Pewter City (Pewter Gym) | 12 | Onix | P B N D S |
| 2 | Misty | Cerulean City (Cerulean Gym) | 21 | Starmie | P B N D S |
| 3 | Lt. Surge | Vermilion City (Vermilion Gym) | 28 | Raichu | P B N D S |
| 4 | Erika | Celadon City (Celadon Gym) | 32 | Weepinbell / Gloom | P B N D S |
| 5 | Koga | Fuchsia City (Fuchsia Gym) | 50 | Venomoth | P B N D S |
| 6 | Sabrina | Saffron City (Saffron Gym) | 50 | Abra / Kadabra / Alakazam | P B N D S |
| 7 | Blaine | Cinnabar Island (Cinnabar Gym) | 54 | Arcanine | P B N D S |
| 8 | Giovanni | Viridian City (Viridian Gym) | 55 | Nidoking / Rhydon | P B N D S |
| E4-1 | Lorelei | Indigo Plateau | 56 | Jynx / Lapras | P B N D S |
| E4-2 | Bruno | Indigo Plateau | 58 | Machamp | P B N D S |
| E4-3 | Agatha | Indigo Plateau | 60 | Gengar | P B N D S |
| E4-4 | Lance | Indigo Plateau | 62 | Dragonite | P B N D S |
| C | Champion (Blue, your rival) | Indigo Plateau | 65 | Jolteon / Flareon / Vaporeon (his Eevee's final form) | P B N D S |

Sources: P https://raw.githubusercontent.com/pret/pokeyellow/master/data/trainers/parties.asm ; B one page per trainer, for example https://bulbapedia.bulbagarden.net/wiki/Lt._Surge (Vermilion Gym block, game code Y), https://bulbapedia.bulbagarden.net/wiki/Sabrina , https://bulbapedia.bulbagarden.net/wiki/Blue_(game) , walkthrough https://bulbapedia.bulbagarden.net/wiki/Walkthrough:Pok%C3%A9mon_Yellow/Part_16 ; N https://nuzlockeuniversity.ca/2022/01/18/hardcore-nuzlocke-level-caps-by-generation/ (section "Pokemon Yellow") ; D https://github.com/domtronn/nuzlocke.data/blob/main/leagues/yel.txt ; S https://www.serebii.net/yellow/gyms.shtml , https://www.serebii.net/yellow/elitefour.shtml (same numbers as P and B)

The Yellow Gym Leaders differ from Red/Blue (Brock 12, Surge 28 with a single Raichu, Erika 32, Koga 50, Sabrina 50, Blaine 54, Giovanni 55) while the Elite Four and Champion match Red/Blue (56, 58, 60, 62, 65).

Other cap points (optional caps; not in NU's list):

| Battle | Location | Cap | Where it falls in a normal run (unconfirmed, Bulbapedia order only) | Sources |
|---|---|---|---|---|
| Rival, Professor Oak's Lab | Pallet Town | 5 | Start of game (Eevee L5) | P B D S |
| Rival, Route 22 (first) | Route 22 | 9 | Right after Viridian City, before Viridian Forest and Gym 1 (walk-on trigger; Bulbapedia calls the Yellow fight optional) | P B D S |
| Rival, Cerulean City | Cerulean City | 18 | Between Gym 1 and Gym 3 | P B D S |
| Jessie and James, Mt. Moon | Mt. Moon B2F | 14 | Right after Mt. Moon, before Gym 2 | P D (no B or S) |
| Rival, S.S. Anne | S.S. Anne 2F | 20 | Just before Gym 3 (Lt. Surge) | P B D S |
| Jessie and James, Rocket Hideout | Rocket Hideout B4F | 25 | Around Gym 4 (Erika) | P D (no B or S) |
| Giovanni, Rocket Hideout | Rocket Hideout B4F (Celadon City) | 29 | Around Gym 4 (Erika) | P B D |
| Rival, Pokemon Tower | Pokemon Tower 2F (Lavender Town) | 25 | Around Gym 4-5, after the Rocket Hideout | P B D S |
| Jessie and James, Pokemon Tower | Pokemon Tower 7F | 27 | Around Gym 4-5 | P D (no B or S) |
| Rival, Silph Co. | Silph Co. 7F (Saffron City) | 40 | Around Gym 6 (Sabrina) | P B D S |
| Jessie and James, Silph Co. | Silph Co. 11F | 31 | Around Gym 6 (Sabrina) | P D (no B or S) |
| Giovanni, Silph Co. | Silph Co. 11F (Saffron City) | 41 | Around Gym 6 (Sabrina) | P B D |
| Rival, Route 22 (last before the League) | Route 22 (below Victory Road) | 53 | After all 8 badges, before Victory Road | P B D(says 50) S(says 50) |

Sources: P pokeyellow `data/trainers/parties.asm` (Rival1Data, Rival2Data, Rival3Data, GiovanniData, and the four Jessie and James entries marked in RocketData); B https://bulbapedia.bulbagarden.net/wiki/Walkthrough:Pok%C3%A9mon_Yellow/Part_1 to Part_16 (rival and Giovanni fights; Jessie and James are not in Bulbapedia's party blocks, so those four rows rest on P and D only, which agree); D `leagues/yel.txt` (r1 to r7, jj1 to jj4, g1, g2). S https://www.serebii.net/yellow/rival.shtml (its late Route 22 fight reads 50; P and B say 53, see 0.4).

Yellow's rival evolves his Eevee according to how you did in the first two fights (Bulbapedia, Yellow page, Gameplay changes): win both the Oak's Lab and Route 22 fights and it becomes Jolteon; win the lab and lose or skip Route 22 and it becomes Flareon; lose at the lab and it becomes Vaporeon. The levels are identical in all three cases, so the cap does not change. https://bulbapedia.bulbagarden.net/wiki/Pok%C3%A9mon_Yellow_Version

Order and flexibility: same as Red/Blue. Bulbapedia's Gym Badge trivia covers "Generation I" and lets gyms 3 to 7 be won in almost any order (Fuchsia before Cinnabar); pokeyellow `scripts/ViridianCity.asm` has the same rule that Viridian Gym opens only when every badge except the Earth Badge is held. The caps make Koga (50) and Sabrina (50) interchangeable; Blaine (54) can precede them once Surf is available. The Route 22 fight is skippable in Yellow (Bulbapedia, "the second being optional").

### 3.2 Rulings

#### 3.2.1 Gift, purchased and prize Pokemon (Yellow)

| Pokemon | Where and how | Level | Suggested default (unconfirmed proposal) | Src |
|---|---|---|---|---|
| Pikachu | Professor Oak, Pallet Town; follows you and cannot evolve. It can be boxed but never released (pret `engine/pokemon/bills_pc.asm` refuses the release), so a dead Pikachu has to stay in the box | 5 | Free (starter) | P B A S |
| Bulbasaur | A girl in Cerulean City, only if Pikachu's friendship is high enough (missable) | 10 | Free (gift) | P B A S |
| Charmander | A boy at the north end of Route 24 | 10 | Free (gift) | P B A S |
| Squirtle | Officer Jenny, Vermilion City, after the Thunder Badge | 10 | Free (gift) | P B A S |
| Eevee | Celadon Mansion roof, Poke Ball on the table | 25 | Free (gift) | P B A S |
| Hitmonlee or Hitmonchan (choose one) | Fighting Dojo, Saffron City | 30 | Free (gift) | P B A S |
| Lapras | Silph Co. 7F, after beating Blue there | 15 | Free (gift) | P B A S |
| Omanyte (Helix) or Kabuto (Dome), choose one | Mt. Moon fossil, revived at Cinnabar Lab | 30 | Free (gift) | P B A S |
| Aerodactyl (Old Amber) | Pewter Museum back room (needs Cut), revived at Cinnabar Lab | 30 | Free (gift) | P B A S |
| Magikarp | Route 4 Pokemon Center salesman, $500 | 5 | Free (purchase) | P B A |
| Abra L15 (230 coins), Vulpix L18 (1000), Wigglytuff L22 (2680), Scyther L30 (6500), Pinsir L30 (6500), Porygon L26 (9999) | Celadon City Game Corner prize exchange | see row | Free (prize); switch for "one prize only" | P B A S |

Sources: https://bulbapedia.bulbagarden.net/wiki/Gift_Pok%C3%A9mon (Generation I), https://bulbapedia.bulbagarden.net/wiki/List_of_in-game_event_Pok%C3%A9mon_in_Generation_I (Pokemon Yellow), https://bulbapedia.bulbagarden.net/wiki/Celadon_Game_Corner ; A method gift rows for version_id 3. P (https://raw.githubusercontent.com/pret/pokeyellow/master/): `scripts/CeruleanMelaniesHouse.asm` (Bulbasaur L10), `scripts/Route24.asm` (Charmander L10), `scripts/VermilionCity_2.asm` (Squirtle L10), `scripts/CeladonMansionRoofHouse.asm` (Eevee L25), `scripts/FightingDojo.asm` (Hitmon L30), `scripts/SilphCo7F.asm` (Lapras L15), `scripts/CinnabarLabFossilRoom.asm` (fossil Pokemon L30), `scripts/MtMoonPokecenter_2.asm` (Magikarp L5), `data/events/prizes.asm` and `data/events/prize_mon_levels.asm` (Abra L15 for 230 coins, Vulpix L18 for 1000, Wigglytuff L22 for 2680, Scyther L30 and Pinsir L30 for 6500 each, Porygon L26 for 9999). S https://www.serebii.net/yellow/gift.shtml and https://www.serebii.net/yellow/gamecorner.shtml (same species, levels, places and coin prices).

#### 3.2.2 In-game trades (Yellow: seven, not nine)

| Where | You give | You get | Src |
|---|---|---|---|
| Route 2 (gatehouse) | Clefairy | Mr. Mime | P B A S |
| Underground Path (Routes 5-6) | Cubone | Machoke | P B A S |
| Route 11 | Lickitung | Dugtrio | P B A S |
| Route 18 | Tangela | Parasect | P B A S |
| Cinnabar Lab | Golduck | Rhydon | P B A S |
| Cinnabar Lab | Growlithe | Dewgong | P B A S |
| Cinnabar Lab | Kangaskhan | Muk | P B A S |

Bulbapedia's Yellow page: "In-game trades are changed to different Pokemon, and two trades are removed from the game", and Farfetch'd and Lickitung, previously trade-only, are now wild. Suggested default: free. Sources: https://bulbapedia.bulbagarden.net/wiki/List_of_in-game_trades (Generation I, Yellow) ; A rows with method npc-trade for version_id 3. P pokeyellow `data/events/trades.asm` (the seven used entries above, plus three unused ones: Butterfree for Beedrill, Mew for Mew, Pidgeot for Pidgeot). S https://www.serebii.net/yellow/trades.shtml (the same seven).

#### 3.2.3 Static, one-off and item-ball encounters (Yellow)

| Encounter | Where | Level | Notes | Suggested default (unconfirmed proposal) | Src |
|---|---|---|---|---|---|
| Snorlax (two) | Route 12 and Route 16 | 30 | Poke Flute; one must be caught or beaten to reach Fuchsia | Free (static) | P B A |
| Voltorb x6 and Electrode x2 | Power Plant (item-ball lookalikes) | 40 and 43 | Separate battles | Free (static) | P B A |
| Zapdos | Power Plant | 50 | Legendary | Free, flag for the legendary ban | P B A S |
| Articuno | Seafoam Islands B4F | 50 | Legendary | Free, flag for the legendary ban | P B A S |
| Moltres | Victory Road 2F | 50 | Legendary | Free, flag for the legendary ban | P B A S |
| Mewtwo | Cerulean Cave B1F | 70 | Post-game (Hall of Fame); the cave has a different layout in Yellow | Post-game; flag for the legendary ban | P B A S |

Sources: as 2.2.3 with version_id 3; P https://raw.githubusercontent.com/pret/pokeyellow/master/data/maps/objects/PowerPlant.asm (identical to pokered). Also P `data/maps/objects/SeafoamIslandsB4F.asm`, `VictoryRoad2F.asm`, `CeruleanCaveB1F.asm` (Articuno, Moltres, Mewtwo) and `scripts/Route12.asm`, `Route16.asm` (Snorlax L30). S https://www.serebii.net/yellow/legends.shtml (Articuno L50 Seafoam Islands, Zapdos L50 Power Plant, Moltres L50 Victory Road, Mewtwo L70 Cerulean Cave).

#### 3.2.4 Special areas and handling (Yellow)

| Area | Fact | Suggested handling (unconfirmed proposal) |
|---|---|---|
| Safari Zone | Four sectors as in Red/Blue (middle, Area 1 east, Area 2 north, Area 3 west) | Per sector or whole Zone (switch) |
| Multi-floor caves and towers | Same set as Red/Blue: 74 PokeAPI areas over 45 locations | One area per location, per-floor switch |
| Surfing encounters | Yellow adds wild Pokemon while surfing on the Seafoam Islands and on Routes 6, 12 and 13 (Bulbapedia) | Water on those routes is part of the route's encounters or a separate encounter type depending on the fishing/surf switch |
| Wild changes that break a Red/Blue table | Pikachu and Raichu are not wild; only Caterpie (no Weedle line) is wild; Pidgey and Pidgeotto were added to Viridian Forest; Farfetch'd and Lickitung are wild; Abra moved to the four routes around Saffron; Ekans, Koffing and Meowth lines are not wild (Team Rocket's Pokemon) | Do not reuse Red/Blue tables |
| Old Rod, Good Rod, Super Rod | The Old Rod and Good Rod are as in Red/Blue (pokeyellow `data/wild/good_rod.asm` is the same). The Super Rod has an explicit four-species table for each of 31 maps (`data/wild/super_rod.asm`), including town water, the four Safari Zone sectors, Vermilion Dock and Cerulean Cave 1F and B1F | As Red/Blue |
| Pokemon Tower (ghosts) | Same as Red/Blue: without the Silph Scope every wild battle on 3F to 7F is an uncatchable Ghost, and the ghost Marowak on 6F cannot be caught even with the Scope (pokeyellow `IsGhostBattle` in `engine/battle/core.asm`, `ItemUseBall` in `engine/items/item_effects.asm`) | The tower's encounter is only usable after you own the Scope (Rocket Hideout B4F). The ghost fights are not encounters |

Sources: https://bulbapedia.bulbagarden.net/wiki/Pok%C3%A9mon_Yellow_Version (Pokemon availability changes) ; A `encounters.csv` version_id 3. P pokeyellow `data/wild/super_rod.asm`, `data/wild/good_rod.asm`. Safari Zone entry is the same as in Red/Blue (pokeyellow `scripts/SafariZoneGate.asm`).

#### 3.2.5 Scripted encounters that must not count (Yellow)

- **Old Man, Viridian City:** unskippable in Yellow. He owns one Poke Ball, fails to catch a Rattata, goes to the Mart for more, then catches the Rattata (L5) after you enter and leave the Mart (Bulbapedia; pret `scripts/ViridianCity.asm` sets RATTATA). Not an encounter. https://bulbapedia.bulbagarden.net/wiki/Old_man_(Kanto)
- **Rival fight in Oak's Lab** (his Eevee is L5): not an encounter.

#### 3.2.6 Version notes (Yellow)

Yellow is one game with its own tables. Differences from Red (PokeAPI wild-method diff, consistent with Bulbapedia's list above): Red-only wild species: Ekans, Arbok, Weedle, Kakuna, Koffing, Weezing, Electabuzz, Electrode, Hypno, Pikachu, Raichu, Wigglytuff. Yellow-only wild species: Bellsprout, Weepinbell, Dragonair, Farfetch'd, Gyarados, Lickitung, Pinsir, Primeape, Sandshrew, Sandslash, Tentacruel. Prize and trade lists differ too (3.2.1, 3.2.2). Source: A encounters for version_id 1 versus 3.

### 3.3 Where the encounter-area list can come from (Yellow)

| Source | What it gives for Yellow | Licence |
|---|---|---|
| PokeAPI | 74 location areas with encounters across 45 locations (version_id 3), including its own gift, static and trade rows | BSD-3-Clause |
| Nuzlocke Tracker data (`routes.json`, key `yel`) | 45 areas in story order with 26 boss markers (adds four Jessie and James fights to Red's 22) | BSD-3-Clause in the app repository; data repository has no licence |
| pret pokeyellow | `data/wild/maps/*.asm` (60 files), `grass_water.asm`, `good_rod.asm`, `super_rod.asm`, `data/maps/names.asm` | No licence file (not shippable) |
| Bulbapedia | https://bulbapedia.bulbagarden.net/wiki/List_of_locations_by_index_number_(Generation_I) and route pages | CC BY-NC-SA 2.5 (not shippable) |

Details and caveats: section 9.

## 4. Pokemon Gold and Pokemon Silver (Generation II)

### 4.1 Level caps (Hardcore Nuzlocke)

Gen 2 structure: eight Johto Gym Leaders, then the Elite Four and Lance as Champion (this is the "first run"), then Kanto with eight more gyms and Red on Mt. Silver as post-game. The disassemblies for Gold/Silver (pokegold) and Crystal (pokecrystal) were compared trainer by trainer: every Gym Leader, Elite Four member, Champion, Kanto leader, Blue, Red, the rival and the Rocket executives is identical between the two; only ordinary route trainers differ. So the tables in sections 4 and 5 hold the same numbers. Gold and Silver share one set.

Johto gyms, Elite Four, Champion:

| # | Battle | Location | Cap (highest level) | Ace / top Pokemon | Sources |
|---|---|---|---|---|---|
| 1 | Falkner | Violet City (Violet Gym) | 9 | Pidgeotto | P B N D |
| 2 | Bugsy | Azalea Town (Azalea Gym) | 16 | Scyther | P B N D |
| 3 | Whitney | Goldenrod City (Goldenrod Gym) | 20 | Miltank | P B N D |
| 4 | Morty | Ecruteak City (Ecruteak Gym) | 25 | Gengar | P B N D |
| 5 | Chuck | Cianwood City (Cianwood Gym) | 30 | Poliwrath | P B N D |
| 6 | Jasmine | Olivine City (Olivine Gym) | 35 | Steelix | P B N D |
| 7 | Pryce | Mahogany Town (Mahogany Gym) | 31 | Piloswine | P B N D |
| 8 | Clair | Blackthorn City (Blackthorn Gym) | 40 | Kingdra | P B N D |
| E4-1 | Will | Indigo Plateau | 42 | Xatu | P B N D |
| E4-2 | Koga | Indigo Plateau | 44 | Crobat | P B N D |
| E4-3 | Bruno | Indigo Plateau | 46 | Machamp | P B N D |
| E4-4 | Karen | Indigo Plateau | 47 | Houndoom | P B N D |
| C | Champion Lance | Indigo Plateau | 50 | Dragonite | P B N D |

Sources: P https://raw.githubusercontent.com/pret/pokegold/master/data/trainers/parties.asm (groups FalknerGroup ... ChampionGroup) ; B one page per trainer, for example https://bulbapedia.bulbagarden.net/wiki/Falkner (Violet Gym, game code GSC), https://bulbapedia.bulbagarden.net/wiki/Pryce , https://bulbapedia.bulbagarden.net/wiki/Lance ; N https://nuzlockeuniversity.ca/2022/01/18/hardcore-nuzlocke-level-caps-by-generation/ (section "Pokemon Gold/Silver") ; D https://github.com/domtronn/nuzlocke.data/blob/main/leagues/gsc.txt ; no S: Serebii has no Gold/Silver gym or Elite Four page, so every cap in this section rests on P, B, N and D.

Note the dip: gym 7 (Pryce, 31) is lower than gym 6 (Jasmine, 35); NU flags this too. The Elite Four fight in the order Will, Koga, Bruno, Karen, then Lance.

Kanto gyms, Blue and Red (post-game):

| K | Battle | Location | Cap (highest level) | Ace / top Pokemon | Sources |
|---|---|---|---|---|---|
| K1 | Brock | Pewter City (Pewter Gym) | 44 | Onix | P B N D |
| K2 | Misty | Cerulean City (Cerulean Gym) | 47 | Starmie | P B N D |
| K3 | Lt. Surge | Vermilion City (Vermilion Gym) | 46 | Electabuzz | P B N(says 45) D |
| K4 | Erika | Celadon City (Celadon Gym) | 46 | Victreebel / Bellossom | P B N D |
| K5 | Janine | Fuchsia City (Fuchsia Gym) | 39 | Venomoth | P B N D |
| K6 | Sabrina | Saffron City (Saffron Gym) | 48 | Alakazam | P B N D |
| K7 | Blaine | Seafoam Islands (Seafoam Gym, Blaine's Gen 2 gym) | 50 | Rapidash | P B N D |
| K8 | Blue | Viridian City (Viridian Gym) | 58 | Gyarados / Exeggutor / Arcanine | P B N D |
| K9 | Red | Mt. Silver (summit) | 81 | Pikachu | P B N D |

Sources: P pokegold `data/trainers/parties.asm` (BrockGroup, MistyGroup, LtSurgeGroup, ErikaGroup, JanineGroup, SabrinaGroup, BlaineGroup, BlueGroup, RedGroup) ; B one page per trainer (Kanto Gym block, game code GSC) and https://bulbapedia.bulbagarden.net/wiki/Blue_(game) , https://bulbapedia.bulbagarden.net/wiki/Red_(game) ; N section "Pokemon Gold/Silver" (Pewter City Gym ... Viridian City Gym, Red; N reads Lt. Surge as 45, which is wrong) ; D `leagues/gsc.txt` (blocks k1 to k8, red).

Blaine's Gen 2 gym is the Seafoam Gym (pokegold and pokecrystal `maps/SeafoamGym.asm`); Bulbapedia files his block under "Cinnabar Gym" and NU calls it "Seafoam Island Gym". Kanto order is flexible: Bulbapedia's Gym Badge trivia says that in Generation II "the Kanto badges can be obtained in virtually any order". The row order above follows NU's listing (Pewter, Cerulean, Vermilion, Celadon, Fuchsia, Saffron, Seafoam, Viridian). Janine (39) is the easy first pick; nothing forces Blue last in Gen 2. The cap is the leader you are about to fight. https://bulbapedia.bulbagarden.net/wiki/Gym_Badge

Other cap points (optional caps; NU lists none of these):

| Battle | Location | Cap | Where it falls in a normal run (unconfirmed, Bulbapedia order only) | Sources |
|---|---|---|---|---|
| Rival Silver, Cherrygrove City | Cherrygrove City | 5 | Right after the starter (Slow Start / Starter clauses normally skip it) | P B D |
| Rival Silver, Azalea Town | Azalea Town | 16 | With Gym 2 (Bugsy) | P B D |
| Rival Silver, Burned Tower | Burned Tower (Ecruteak City) | 22 | With Gym 4 (Morty) | P B D |
| Executive Petrel, Rocket HQ | Team Rocket HQ (Mahogany Town) | 24 | Between Gym 6 and Gym 7 (Mahogany) | P B D |
| Executive Ariana, Rocket HQ | Team Rocket HQ (Mahogany Town) | 25 | Between Gym 6 and Gym 7 (Mahogany) | P B D |
| Executive Petrel, Radio Tower | Goldenrod Radio Tower | 32 | After Gym 7 (Pryce), before Gym 8 | P B D |
| Executive Ariana, Radio Tower | Goldenrod Radio Tower | 32 | After Gym 7 (Pryce), before Gym 8 | P B D |
| Rival Silver, Goldenrod Tunnel | Goldenrod Tunnel | 32 | After Gym 7 (Pryce), before Gym 8 | P B D(says 34) |
| Executive Archer, Radio Tower | Goldenrod Radio Tower | 35 | After Gym 7 (Pryce), before Gym 8 | P B D |
| Executive Proton, Radio Tower | Goldenrod Radio Tower | 36 | After Gym 7 (Pryce), before Gym 8 | P B D |
| Rival Silver, Victory Road (last before the League) | Victory Road (Kanto) | 38 | After Gym 8 (Clair), before the Elite Four | P B D |
| Rival Silver, Mt. Moon (post-game) | Mt. Moon (Kanto) | 45 | Post-game, Kanto | P B D |
| Rival Silver, Indigo Plateau (post-game) | Indigo Plateau (Kanto) | 50 | Post-game, Kanto, before Mt. Silver | P B D(not listed) |

Sources: P pokegold `data/trainers/parties.asm` (RIVAL1 entries 1 to 15 in five groups of three starters, RIVAL2 entries 1 to 6 in two groups, EXECUTIVEM and EXECUTIVEF entries) ; B https://bulbapedia.bulbagarden.net/wiki/Silver_(game) (party blocks with locations) and https://bulbapedia.bulbagarden.net/wiki/Walkthrough:Pok%C3%A9mon_Gold_and_Silver/Part_2 to Part_24 ; D `leagues/gsc.txt` (r1 to r6, petrel1/2, ariana1/2, archer, proton). The "typical place" column comes from the walkthrough part each fight sits in (Bulbapedia only). Eusine exists only in Crystal.

### 4.2 Rulings

#### 4.2.1 Gift, purchased and prize Pokemon (Gold and Silver)

| Pokemon | Where and how | Level | Suggested default (unconfirmed proposal) | Src |
|---|---|---|---|---|
| Chikorita, Cyndaquil or Totodile (choose one) | Professor Elm, New Bark Town | 5 | Free (starter) | P B A |
| Togepi (Mystery Egg) | Elm's aide at the Violet City Pokemon Center after beating Falkner; hatches from the Egg you carry | 5 at hatching | Free (gift egg) | P B A |
| Spearow ("Kenya") | The Route 35 gatehouse on the Goldenrod side; holds a Mail meant for a man on Route 31 (reward TM50) | 10 | Free (gift) | P B A |
| Eevee | Bill, at his house in Goldenrod City, after you meet him in the Ecruteak City Pokemon Center | 20 | Free (gift) | P B A |
| Shuckle | Mania, Cianwood City (he wants it back) | 15 | Free (gift) | P B A |
| Tyrogue | Kiyo, Mt. Mortar B1F, after beating him | 10 | Free (gift) | P B A |
| Gold: Abra L10 (200 coins), Ekans L10 (700), Dratini L10 (2100) | Goldenrod City Game Corner prize counter | see row | Free (prize); "one prize only" switch | P B A |
| Silver: Abra L10 (200), Sandshrew L10 (700), Dratini L10 (2100) | same counter | see row | Free (prize); "one prize only" switch | P B A |
| Gold and Silver: Mr. Mime L15 (3333 coins), Eevee L15 (6666), Porygon L20 (9999) | Celadon City Game Corner (Kanto, post-game) | see row | Free (prize), post-game | P B A |

Sources: https://bulbapedia.bulbagarden.net/wiki/Gift_Pok%C3%A9mon (Generation II) , https://bulbapedia.bulbagarden.net/wiki/List_of_in-game_event_Pok%C3%A9mon_in_Generation_II , https://bulbapedia.bulbagarden.net/wiki/Goldenrod_Game_Corner , https://bulbapedia.bulbagarden.net/wiki/Celadon_Game_Corner ; A method gift and gift-egg rows for version_id 4 (Gold) and 5 (Silver). PokeAPI places the Kenya gift in "Goldenrod City (north gate)"; Bulbapedia says the Route 35 south gate. They describe the same gatehouse between Goldenrod City and Route 35. P (https://raw.githubusercontent.com/pret/pokegold/master/): `maps/ElmsLab.asm` (starters L5), `maps/VioletPokecenter1F.asm` (Togepi Egg), `maps/Route35GoldenrodGate.asm` (Spearow L10), `maps/BillsFamilysHouse.asm` (Eevee L20), `engine/events/shuckle.asm` (Shuckle L15), `maps/MountMortarB1F.asm` (Tyrogue L10), `maps/GoldenrodGameCorner.asm` and `maps/CeladonGameCornerPrizeRoom.asm` (every prize, price and level in the three prize rows; the Goldenrod file holds a Gold and a Silver block). pokegold has no Dragon Shrine map, which is why the Dragon's Den Dratini is not a gift in Gold and Silver. Serebii has no Gold/Silver gift pages.

#### 4.2.2 In-game trades (Gold and Silver: six)

| Where | You give | You get | Src |
|---|---|---|---|
| Violet City (southwest house) | Bellsprout | Onix | P B A |
| Goldenrod City Department Store 5F | Drowzee | Machop | P B A |
| Olivine City | Krabby | Voltorb | P B A |
| Blackthorn City | Dragonair (must be female) | Rhydon | P B A |
| Route 14 | Chansey | Aerodactyl | P B A |
| Pewter City | Gloom | Rapidash | P B A |

Suggested default: free. Sources: https://bulbapedia.bulbagarden.net/wiki/List_of_in-game_trades (Generation II) ; A rows with method npc-trade for version_id 4 and 5. P pokegold `data/events/npc_trades.asm` (six entries, NPC_TRADE_MIKE to NPC_TRADE_KIM; the Dragonair request is gender-locked to female). The Route 14 trade is shared with Crystal (5.2.2).

#### 4.2.3 Static, roaming and one-off encounters (Gold and Silver)

| Encounter | Where | Level | Notes | Suggested default (unconfirmed proposal) | Src |
|---|---|---|---|---|---|
| Sudowoodo | Route 36 | 20 | Needs the SquirtBottle from the Goldenrod City flower shop (after Whitney); it blocks the way to Ecruteak | Free (static) | P B A |
| Red Gyarados | Lake of Rage | 30 | Forced encounter during the Lake of Rage event | Free (static) | P B A |
| Snorlax | Vermilion City (path to Diglett's Cave) | 50 | Kanto, Poke Flute; only one in Gen 2 | Free (static), post-game | P B A |
| Lapras | Union Cave B2F | 20 | Fridays only | Free (static, weekday) | P B A |
| Geodude x7 and Koffing x7 (L21), Voltorb x8 (L23) | Team Rocket HQ B1F, Mahogany Town: floor-tile traps | 21 and 23 | Each trap is a forced battle you cannot run from (pret BATTLETYPE_TRAP), so 22 separate battles | Free (static); watch "one per area" | P B A |
| Electrode x3 | Team Rocket HQ B2F, generator room, during Lance's scene | 23 | Three visible Electrode, each its own battle | Free (static) | P B A |
| Raikou, Entei and Suicune | Roam Johto after the Burned Tower event; all three roam in Gold and Silver | 40 | Roaming from Route 42 (Raikou), Route 37 (Entei) and Route 38 (Suicune) at L40; hard to pin down | Free (static); flag for the legendary ban | P B A |
| Lugia | Whirl Islands B2F | Gold 70, Silver 40 | Needs the Silver Wing: Gold from a man in Pewter City after the Hall of Fame, Silver from the Radio Tower director (pret `maps/RadioTower5F.asm` and `maps/PewterCity.asm`) | Gold: post-game. Silver: mid-game, right after the Radio Tower takeover. Flag for the legendary ban | P B A |
| Ho-Oh | Tin Tower roof | Gold 40, Silver 70 | Needs the Rainbow Wing: Gold from the Radio Tower director, Silver from the man in Pewter City | Gold: mid-game, right after the Radio Tower takeover. Silver: post-game. Flag for the legendary ban | P B A |

Sources: https://bulbapedia.bulbagarden.net/wiki/List_of_in-game_event_Pok%C3%A9mon_in_Generation_II ; A rows with method static, squirt-bottle, pokeflute and roaming-grass for version_id 4 and 5 (PokeAPI labels the Tin Tower "Bell Tower"). The Trainer Red on Mt. Silver (L81) is a battle, not an encounter. P pokegold `maps/Route36.asm` (Sudowoodo L20), `maps/LakeOfRage.asm` (Red Gyarados L30), `maps/VermilionCity.asm` (Snorlax L50), `maps/UnionCaveB2F.asm` (Lapras L20), `maps/TeamRocketBaseB1F.asm` and `maps/TeamRocketBaseB2F.asm` (the traps and the Electrode), `engine/overworld/wildmons.asm` (InitRoamMons: Raikou, Entei and Suicune, all L40), `maps/WhirlIslandLugiaChamber.asm` and `maps/TinTowerRoof.asm` (a `checkver` branch: Gold Lugia 70 and Ho-Oh 40, Silver Lugia 40 and Ho-Oh 70). `maps/BurnedTowerB1F.asm` also holds an unreferenced Entei battle script that the game never calls.

#### 4.2.4 Special areas and handling (Gold and Silver)

| Area or mechanic | Fact | Suggested handling (unconfirmed proposal) |
|---|---|---|
| Time of day | Every grass and cave table has morning, day and night versions (PokeAPI splits Gold's encounters into 441 morning, 441 day and 441 night rows, and Silver's into 413 of each) | The first encounter is whatever appears; do not key areas by time |
| Swarms | Gold and Silver: Marill (Mt. Mortar, grass and water), Yanma (Route 35), Dunsparce (Dark Cave), Snubbull (Route 38), plus Qwilfish (Route 32 fishing) and Remoraid (Route 44 fishing). Only one swarm per day, announced by a phone call; it lasts until midnight | Swarm Pokemon are ordinary wild encounters of that area on that day (they appear at a raised rate). Offer a switch to make a swarm an extra encounter |
| Headbutt trees | Special trees in Johto routes; a tree gives an encounter at 10%, 50% or 80% depending on the tree's coordinates and your Trainer ID digit; Pineco and Heracross are headbutt-only (Bulbapedia's examples; unconfirmed, one source) | Extra encounter type (the "FHH" clause) or shared with the route, switch |
| Rock Smash | Four maps give a wild Pokemon when you smash a rock: Cianwood City, Route 40, Dark Cave (Violet City entrance) and Slowpoke Well B1F. 40% per smashed rock; Krabby 90% and Shuckle 10%, both L15. PokeAPI lists three of the four (no Slowpoke Well row) | Part of the map's encounters or an extra type under the fishing/headbutt switch; Shuckle is also a gift, so the dupes clause matters |
| Fishing | Every water map belongs to one of 13 fishing groups (shore, ocean, lake, pond, Dratini, Gyarados, Whirl Islands, and Qwilfish or Remoraid variants used by swarms). The Old Rod gives Magikarp 85% and one group species 15% (Krabby at the shore, Tentacool at the ocean, Goldeen at lakes, Poliwag at ponds); the Good and Super Rods add more species and time-of-day slots. Gold, Silver and Crystal have identical fishing tables in pret | Fishing shares the area or is an extra encounter under the FHH switch |
| Bug-Catching Contest | National Park, Tuesday, Thursday and Saturday: one entry a day, 20 Park Balls, 20 minutes, only one Pokemon of yours, and only one caught Pokemon is kept. Other days the Park has an ordinary grass table. pret keeps the two apart: the contest has its own species table (`data/wild/bug_contest_mons.asm`: Caterpie, Weedle, Metapod, Kakuna, Butterfree, Beedrill, Venonat, Paras, Scyther and Pinsir, with Venomoth as a fallback entry) beside the Park's ordinary grass table in `johto_grass.asm`; the 20 balls and 20 minutes are in `constants/script_constants.asm` and the three days in the two National Park gate maps (checked in pokecrystal) | One area (the Park) with the contest as a special mode; the example ruleset gives a contest one catch for the whole area |
| Ruins of Alph | Outside area plus four interior chambers with Unown (PokeAPI: 5 areas) | One area; per-chamber switch |
| Union Cave 1F/B1F/B2F, Slowpoke Well, Ilex Forest, Mt. Mortar, Ice Path, Whirl Islands, Dark Cave (Violet entrance and Blackthorn entrance), Tohjo Falls, Victory Road, Mt. Silver | Multi-floor or multi-room; 124 PokeAPI areas over 84 locations for Gold and for Silver | One area per location by default, per-floor switch |
| Roaming beasts | Raikou, Entei, Suicune move from route to route | Static, not an area |
| Kanto | Separate post-game world with its own routes and caves | Separate section of the area list, shown after the Champion |

Sources: https://bulbapedia.bulbagarden.net/wiki/Mass_outbreak (Generation II; note its Crystal column is wrong, see 5.2.4) ; https://bulbapedia.bulbagarden.net/wiki/Headbutt_tree ; https://bulbapedia.bulbagarden.net/wiki/Bug-Catching_Contest ; P pokegold `data/wild/swarm_grass.asm`, `swarm_water.asm`, `fish.asm`, `treemon_maps.asm`, `bug_contest_mons.asm`, `johto_grass.asm` (NATIONAL_PARK block) ; A `encounter_condition_value_map.csv` (swarm-yes 49 rows in Gold, 41 in Silver). P pokegold `engine/events/treemons.asm` (GetTreeMon: 10%, 50% or 80% encounter chance by tree score, the numbers above; RockMonEncounter), `data/wild/treemon_maps.asm` (RockMonMaps), `data/wild/treemons.asm` (TreeMonSet_Rock), `data/wild/fish.asm` (identical in pokecrystal); A rock-smash rows.

#### 4.2.5 Scripted encounters that must not count (Gold and Silver)

- **The Dude on Route 29** shows how to catch a Rattata (L5) once you have your starter and have talked to the policeman and Elm. The Rattata is his. pokegold `maps/Route29.asm` calls `loadwildmon RATTATA, 5` for the tutorial. https://bulbapedia.bulbagarden.net/wiki/Dude
- **Silver's first fight in Cherrygrove City** uses the starter he stole: not an encounter.

#### 4.2.6 Version-exclusive Pokemon (Gold versus Silver)

Bulbapedia's international list (not obtainable in the other game without trading): Gold has Mankey, Primeape, Growlithe, Arcanine, Spinarak, Ariados, Gligar, Teddiursa, Ursaring, Mantine. Silver has Vulpix, Ninetales, Meowth, Persian, Ledyba, Ledian, Delibird, Skarmory, Phanpy, Donphan. Phanpy/Donphan and Teddiursa/Ursaring are swapped between the Japanese and the international releases (Japanese Gold and international Silver have Phanpy). Prize differences: the Goldenrod counter offers Ekans in Gold and Sandshrew in Silver. Lugia and Ho-Oh levels are swapped (4.2.3). PokeAPI's wild tables also differ on Caterpie, Metapod, Butterfree and Sandshrew, Sandslash (Gold) versus Weedle, Kakuna, Beedrill and Ekans, Arbok (Silver), which the exclusive list does not show (presumably because the other version can still get those species in the Bug-Catching Contest or as a prize; that reason is an inference); ship separate tables for Gold and Silver. Sources: https://bulbapedia.bulbagarden.net/wiki/Pok%C3%A9mon_Gold_and_Silver_Versions (Version-exclusive Pokemon) ; A version_id 4 versus 5.

### 4.3 Where the encounter-area list can come from (Gold and Silver)

| Source | What it gives for Gold/Silver | Licence |
|---|---|---|
| PokeAPI | 124 location areas with encounters across 84 locations for each of Gold and Silver; methods include walk, surf, old-rod, good-rod, super-rod, rock-smash, headbutt-low/normal/high, gift, gift-egg, static, roaming-grass, pokeflute, squirt-bottle, npc-trade; conditions for swarm, time of day, weekday, coin price and trade partner | BSD-3-Clause |
| Nuzlocke Tracker data (`routes.json`, keys `gold`, `silv`) | 84 areas in story order with 35 boss markers (8 Johto gyms, rival, Rocket executives, Elite Four, Champion, Kanto gyms, Red) | BSD-3-Clause in the app repository; data repository has no licence |
| pret pokegold | `data/wild/johto_grass.asm` (61 map entries), `johto_water.asm` (38), `kanto_grass.asm` (30), `kanto_water.asm` (24), `swarm_grass.asm`, `swarm_water.asm`, `fish.asm`, `treemons.asm` + `treemon_maps.asm`, `bug_contest_mons.asm`, `roammon_maps.asm`; `data/maps/landmarks.asm` (95 landmark entries in pokegold, 96 in pokecrystal, entry 0 being a placeholder; these are the names the game shows when you enter an area) | No licence file (not shippable) |
| Gen 2 IronMON tracker fork (MIT) | `RouteData.lua`: 95 location names for Johto and Kanto (names only, no encounter data) | MIT (see section 9.5) |
| Bulbapedia | https://bulbapedia.bulbagarden.net/wiki/List_of_locations_by_index_number_(Generation_II) and route pages | CC BY-NC-SA 2.5 (not shippable) |

Details and caveats: section 9.

## 5. Pokemon Crystal (Generation II)

Crystal has its own disassembly (pokecrystal). The Gym Leader, Elite Four, Champion, Kanto and rival numbers came out identical to Gold/Silver when compared trainer by trainer, so the cap tables below equal section 4's; the gifts, trades, swarms, statics and encounter tables are Crystal's own.

### 5.1 Level caps (Hardcore Nuzlocke)

Johto gyms, Elite Four, Champion:

| # | Battle | Location | Cap (highest level) | Ace / top Pokemon | Sources |
|---|---|---|---|---|---|
| 1 | Falkner | Violet City (Violet Gym) | 9 | Pidgeotto | P B N D S |
| 2 | Bugsy | Azalea Town (Azalea Gym) | 16 | Scyther | P B N D S |
| 3 | Whitney | Goldenrod City (Goldenrod Gym) | 20 | Miltank | P B N D S |
| 4 | Morty | Ecruteak City (Ecruteak Gym) | 25 | Gengar | P B N D S |
| 5 | Chuck | Cianwood City (Cianwood Gym) | 30 | Poliwrath | P B N D S |
| 6 | Jasmine | Olivine City (Olivine Gym) | 35 | Steelix | P B N D S |
| 7 | Pryce | Mahogany Town (Mahogany Gym) | 31 | Piloswine | P B N D S |
| 8 | Clair | Blackthorn City (Blackthorn Gym) | 40 | Kingdra | P B N D S |
| E4-1 | Will | Indigo Plateau | 42 | Xatu | P B N D S |
| E4-2 | Koga | Indigo Plateau | 44 | Crobat | P B N D S |
| E4-3 | Bruno | Indigo Plateau | 46 | Machamp | P B N D S |
| E4-4 | Karen | Indigo Plateau | 47 | Houndoom | P B N D S |
| C | Champion Lance | Indigo Plateau | 50 | Dragonite | P B N D S |

Sources: P https://raw.githubusercontent.com/pret/pokecrystal/master/data/trainers/parties.asm ; B one page per trainer (game code GSC covers Gold, Silver and Crystal on Bulbapedia; Crystal-only trainers carry code C), for example https://bulbapedia.bulbagarden.net/wiki/Clair , https://bulbapedia.bulbagarden.net/wiki/Karen , https://bulbapedia.bulbagarden.net/wiki/Lance ; N https://nuzlockeuniversity.ca/2022/01/18/hardcore-nuzlocke-level-caps-by-generation/ (section "Pokemon Crystal") ; D https://github.com/domtronn/nuzlocke.data/blob/main/leagues/gsc.txt ; S https://www.serebii.net/crystal/gyms.shtml , https://www.serebii.net/crystal/elitefour.shtml (same numbers as P and B for every Johto gym, Elite Four member and the Champion)

Same Gen 2 structure as Gold/Silver: eight Johto gyms with the Pryce dip (31 after Jasmine's 35), the Elite Four in the order Will, Koga, Bruno, Karen, Lance as Champion, then Kanto.

Kanto gyms, Blue and Red (post-game):

| K | Battle | Location | Cap (highest level) | Ace / top Pokemon | Sources |
|---|---|---|---|---|---|
| K1 | Brock | Pewter City (Pewter Gym) | 44 | Onix | P B N D S |
| K2 | Misty | Cerulean City (Cerulean Gym) | 47 | Starmie | P B N D S |
| K3 | Lt. Surge | Vermilion City (Vermilion Gym) | 46 | Electabuzz | P B N(says 45) D S(says 45) |
| K4 | Erika | Celadon City (Celadon Gym) | 46 | Victreebel / Bellossom | P B N D S |
| K5 | Janine | Fuchsia City (Fuchsia Gym) | 39 | Venomoth | P B N D S |
| K6 | Sabrina | Saffron City (Saffron Gym) | 48 | Alakazam | P B N D S |
| K7 | Blaine | Seafoam Islands (Seafoam Gym, Blaine's Gen 2 gym) | 50 | Rapidash | P B N D S |
| K8 | Blue | Viridian City (Viridian Gym) | 58 | Gyarados / Exeggutor / Arcanine | P B N D S |
| K9 | Red | Mt. Silver (summit) | 81 | Pikachu | P B N D |

Sources: P pokecrystal `data/trainers/parties.asm` ; B as in 4.1 ; N section "Pokemon Crystal" (Vermillion City Gym is given as 45, which is wrong) ; D `leagues/gsc.txt`. S https://www.serebii.net/crystal/gyms.shtml (also gives Lt. Surge as 45, which is wrong: pokecrystal's Lt. Surge has Electabuzz L46 and Bulbapedia agrees).

Kanto order is flexible (Bulbapedia, Gym Badge trivia: in Generation II the Kanto badges can be won in "virtually any order"). Blaine's gym is the Seafoam Gym. https://bulbapedia.bulbagarden.net/wiki/Gym_Badge

Other cap points (optional caps; NU lists none of these). Crystal adds Eusine:

| Battle | Location | Cap | Where it falls in a normal run (unconfirmed, Bulbapedia order only) | Sources |
|---|---|---|---|---|
| Rival Silver, Cherrygrove City | Cherrygrove City | 5 | Right after the starter (Slow Start / Starter clauses normally skip it) | P B D S |
| Rival Silver, Azalea Town | Azalea Town | 16 | With Gym 2 (Bugsy) | P B D S |
| Rival Silver, Burned Tower | Burned Tower (Ecruteak City) | 22 | With Gym 4 (Morty) | P B D S |
| Eusine (Crystal only) | Cianwood City | 25 | With Gym 5-6 (Cianwood / Olivine) | P B D(says 27) |
| Executive Petrel, Rocket HQ | Team Rocket HQ (Mahogany Town) | 24 | Between Gym 6 and Gym 7 (Mahogany) | P B D |
| Executive Ariana, Rocket HQ | Team Rocket HQ (Mahogany Town) | 25 | Between Gym 6 and Gym 7 (Mahogany) | P B D |
| Executive Petrel, Radio Tower | Goldenrod Radio Tower | 32 | After Gym 7 (Pryce), before Gym 8 | P B D |
| Executive Ariana, Radio Tower | Goldenrod Radio Tower | 32 | After Gym 7 (Pryce), before Gym 8 | P B D |
| Rival Silver, Goldenrod Tunnel | Goldenrod Tunnel | 32 | After Gym 7 (Pryce), before Gym 8 | P B D(says 34) S |
| Executive Archer, Radio Tower | Goldenrod Radio Tower | 35 | After Gym 7 (Pryce), before Gym 8 | P B D |
| Executive Proton, Radio Tower | Goldenrod Radio Tower | 36 | After Gym 7 (Pryce), before Gym 8 | P B D |
| Rival Silver, Victory Road (last before the League) | Victory Road (Kanto) | 38 | After Gym 8 (Clair), before the Elite Four | P B D S |
| Rival Silver, Mt. Moon (post-game) | Mt. Moon (Kanto) | 45 | Post-game, Kanto | P B D S |
| Rival Silver, Indigo Plateau (post-game) | Indigo Plateau (Kanto) | 50 | Post-game, Kanto, before Mt. Silver | P B D(not listed) S |

Sources: P pokecrystal `data/trainers/parties.asm` (RIVAL1, RIVAL2, EXECUTIVEM, EXECUTIVEF, MYSTICALMAN = Eusine) ; B https://bulbapedia.bulbagarden.net/wiki/Silver_(game) , https://bulbapedia.bulbagarden.net/wiki/Eusine (Crystal block, Cianwood City, L25; the L27 in the HeartGold/SoulSilver block is the source of the dataset's mistake), https://bulbapedia.bulbagarden.net/wiki/Walkthrough:Pok%C3%A9mon_Crystal/Part_2 to Part_24 ; D `leagues/gsc.txt`. S https://www.serebii.net/crystal/rival.shtml (same rival numbers).

### 5.2 Rulings

#### 5.2.1 Gift, purchased and prize Pokemon (Crystal)

| Pokemon | Where and how | Level | Suggested default (unconfirmed proposal) | Src |
|---|---|---|---|---|
| Chikorita, Cyndaquil or Totodile (choose one) | Professor Elm, New Bark Town | 5 | Free (starter) | P B A S |
| Togepi (Mystery Egg) | Elm's aide at the Violet City Pokemon Center after beating Falkner | 5 at hatching | Free (gift egg) | P B A S |
| Odd Egg: Pichu, Cleffa, Igglybuff, Tyrogue, Smoochum, Elekid or Magby (one of seven, all knowing Dizzy Punch) | The Day-Care Man, Route 34 Day Care (Crystal only). Not a choice: the game picks the species at random when you take the egg, with these odds (pret `data/events/odd_eggs.asm`): Pichu 9%, Cleffa 19%, Igglybuff 19%, Smoochum 16%, Magby 12%, Elekid 14%, Tyrogue 11%, and 14% of all eggs are shiny (Serebii gives the same 14%) | 5 at hatching | Free (gift egg) | P B A S |
| Spearow ("Kenya") | Route 35 gatehouse (Goldenrod side); Mail to deliver on Route 31 | 10 | Free (gift) | P B A S |
| Eevee | Bill, his house in Goldenrod City, after meeting him in the Ecruteak City Pokemon Center | 20 | Free (gift) | P B A S |
| Shuckle | Mania, Cianwood City | 15 | Free (gift) | P B A S |
| Tyrogue | Kiyo, Mt. Mortar B1F | 10 | Free (gift) | P B A S |
| Dratini | The Dragon Master in the Dragon's Den, after his quiz (ExtremeSpeed if every answer is right, Leer otherwise) | 15 | Free (gift) | P B A S |
| Abra L5 (100 coins), Cubone L15 (800), Wobbuffet L15 (1500) | Goldenrod City Game Corner prize counter | see row | Free (prize); "one prize only" switch | P B A |
| Pikachu L25 (2222 coins), Porygon L15 (5555), Larvitar L40 (8888) | Celadon City Game Corner (Kanto, post-game) | see row | Free (prize), post-game | P B A |

Sources: https://bulbapedia.bulbagarden.net/wiki/Gift_Pok%C3%A9mon (Generation II) , https://bulbapedia.bulbagarden.net/wiki/List_of_in-game_event_Pok%C3%A9mon_in_Generation_II (Odd Egg, Dratini) , https://bulbapedia.bulbagarden.net/wiki/Goldenrod_Game_Corner , https://bulbapedia.bulbagarden.net/wiki/Celadon_Game_Corner ; A rows with method gift and gift-egg for version_id 6 (Crystal: 14 gift rows, 8 gift-egg rows). P (https://raw.githubusercontent.com/pret/pokecrystal/master/): `maps/ElmsLab.asm` (starters L5), `maps/VioletPokecenter1F.asm` (Togepi Egg), `engine/events/odd_egg.asm` and `data/events/odd_eggs.asm` (Odd Egg), `maps/Route35GoldenrodGate.asm` (Spearow L10), `maps/BillsFamilysHouse.asm` (Eevee L20), `engine/events/shuckle.asm` (Shuckle L15), `maps/MountMortarB1F.asm` (Tyrogue L10), `maps/DragonShrine.asm` (Dratini L15), `maps/GoldenrodGameCorner.asm` (Abra L5 for 100 coins, Cubone L15 for 800, Wobbuffet L15 for 1500) and `maps/CeladonGameCornerPrizeRoom.asm` (Pikachu L25 for 2222, Porygon L15 for 5555, Larvitar L40 for 8888). S https://www.serebii.net/crystal/gift.shtml (the gifts above; it has no Crystal Game Corner page).

#### 5.2.2 In-game trades (Crystal: seven)

| Where | You give | You get | Src |
|---|---|---|---|
| Violet City (southwest house) | Bellsprout | Onix | P B A S |
| Goldenrod City Department Store 5F | Abra | Machop | P B A S |
| Olivine City | Krabby | Voltorb | P B A S |
| Blackthorn City | Dragonair (must be female) | Dodrio | P B A S |
| Route 14 | Chansey | Aerodactyl | P B A S |
| Pewter City | Haunter | Xatu | P B A S |
| Kanto Power Plant | Dugtrio | Magneton | P B A S |

Crystal keeps the Route 14 trade that Gold and Silver have and changes three others: Abra for Machop (Gold/Silver: Drowzee), female Dragonair for Dodrio (Gold/Silver: Rhydon) and Haunter for Xatu (Gold/Silver: Gloom for Rapidash). The Kanto Power Plant trade (Dugtrio for Magneton) exists only in Crystal. Suggested default: free. Sources: https://bulbapedia.bulbagarden.net/wiki/List_of_in-game_trades (Generation II, the combined "Pokemon Gold, Silver, and Crystal" table: the Route 14 row is one row shared by all three games) ; A rows with method npc-trade for version_id 6 (seven rows); P pokecrystal `data/events/npc_trades.asm` (seven entries, NPC_TRADE_MIKE to NPC_TRADE_FOREST) and `maps/Route14.asm` (`trade NPC_TRADE_KIM`); S https://www.serebii.net/crystal/trades.shtml (the same seven).

#### 5.2.3 Static, roaming and one-off encounters (Crystal)

| Encounter | Where | Level | Notes | Suggested default (unconfirmed proposal) | Src |
|---|---|---|---|---|---|
| Sudowoodo | Route 36 | 20 | SquirtBottle from the Goldenrod City flower shop | Free (static) | P B A |
| Red Gyarados | Lake of Rage | 30 | Forced encounter | Free (static) | P B A |
| Snorlax | Vermilion City (path to Diglett's Cave) | 50 | Poke Flute, post-game Kanto | Free (static), post-game | P B A |
| Lapras | Union Cave B2F | 20 | Fridays only | Free (static, weekday) | P B A |
| Geodude x7 and Koffing x7 (L21), Voltorb x8 (L23) | Team Rocket HQ B1F, Mahogany Town: floor-tile traps | 21 and 23 | Each trap is a forced battle you cannot run from (pret BATTLETYPE_TRAP), so 22 separate battles | Free (static); watch "one per area" | P B A |
| Electrode x3 | Team Rocket HQ B2F, generator room, during Lance's scene | 23 | Three visible Electrode, each its own battle | Free (static) | P B A |
| Suicune | Tin Tower 1F (PokeAPI: "Bell Tower 1F") | 40 | Needs the Clear Bell from the Radio Tower director; you meet it around Johto first and it settles at Tin Tower. It does not roam in Crystal and is a single forced battle that cannot be run from (pret `maps/TinTower1F.asm`, `InitRoamMons` holds only Raikou and Entei) | Free; flag for the legendary ban | P B A S |
| Raikou and Entei | Roam Johto after the Burned Tower event | 40 | Two roamers, not three; they start on Route 42 (Raikou) and Route 37 (Entei) at L40 (pret `InitRoamMons`) | Free (static); flag for the legendary ban | P B A S |
| Lugia | Whirl Islands B2F | 60 | Silver Wing from a man in Pewter City after the Hall of Fame | Post-game; flag for the legendary ban | P B A S |
| Ho-Oh | Tin Tower roof | 60 | Rainbow Wing from the three sages on Tin Tower 1F once you are Champion and own Raikou, Entei and Suicune. pret `BeastsCheck` (`engine/pokemon/search_owned.asm`) needs all three in your party or PC with your own OT and ID, so a beast that died and was released makes Ho-Oh impossible; one kept boxed still counts. Serebii says Eusine hands over the wing in Celadon City; pret shows he only mentions the rumour there (0.4) | Post-game; flag for the legendary ban | P B A S |
| Celebi | Ilex Forest shrine | 30 | Only through the Japanese Mobile System GS Ball event or the Virtual Console versions; not obtainable in US cartridges | Exclude | P B A S |

Sources: https://bulbapedia.bulbagarden.net/wiki/List_of_in-game_event_Pok%C3%A9mon_in_Generation_II ; A rows with method static, squirt-bottle, pokeflute and roaming-grass for version_id 6 (the Celebi row carries the condition other-virtual-console). P pokecrystal `maps/Route36.asm` (Sudowoodo L20), `maps/LakeOfRage.asm` (Red Gyarados L30), `maps/VermilionCity.asm` (Snorlax L50), `maps/UnionCaveB2F.asm` (Lapras L20), `maps/IlexForest.asm` (Celebi L30), `maps/TeamRocketBaseB1F.asm` and `maps/TeamRocketBaseB2F.asm`, `maps/TinTower1F.asm` (Suicune L40), `maps/TinTowerRoof.asm` (Ho-Oh L60), `maps/WhirlIslandLugiaChamber.asm` (Lugia L60), `engine/overworld/wildmons.asm` (InitRoamMons), `maps/TinTower1F.asm` and `maps/CeladonPokecenter1F.asm` (who gives the Rainbow Wing). S https://www.serebii.net/crystal/legends.shtml (Raikou and Entei L40 roaming, Suicune L40 at Tin Tower, Lugia and Ho-Oh L60, Celebi L30).

#### 5.2.4 Special areas and handling (Crystal)

| Area or mechanic | Fact | Suggested handling (unconfirmed proposal) |
|---|---|---|
| Time of day | Morning, day and night tables as in Gold/Silver (PokeAPI: 623 day, 623 morning, 622 night rows) | Do not key areas by time |
| Swarms | Crystal has fewer: Yanma (Route 35), Dunsparce (Dark Cave) and Qwilfish (Route 32 fishing). Marill, Snubbull and Tauros are ordinary wild Pokemon in Crystal (Snubbull on other routes) and Remoraid is unavailable. Bulbapedia's Crystal page, pret's `swarm_grass.asm` (two swarms) and `swarm_water.asm` ("No swarms encountered while surfing in Crystal") and PokeAPI (Dunsparce, Yanma, Qwilfish only) agree; Bulbapedia's separate "Mass outbreak" table wrongly lists all six for Crystal | Same handling as Gold/Silver but with this shorter list |
| Headbutt trees | Same mechanic as Gold/Silver (10, 50 or 80% by tree index and Trainer ID digit) but Crystal uses six tree groups (mountain, town, route, border, lake, forest) where Gold/Silver use three (forest, mountain, city), and adds asleep variants (group counts unconfirmed, Bulbapedia only) | Extra encounter type or shared with the route (switch) |
| Rock Smash | Same four maps and odds as Gold/Silver: Cianwood City, Route 40, Dark Cave (Violet City entrance) and Slowpoke Well B1F, 40% per smashed rock, Krabby 90% and Shuckle 10% (both L15). PokeAPI lists three of the four | Part of the map's encounters or an extra type under the fishing/headbutt switch; Shuckle is also a gift |
| Fishing | Identical to Gold/Silver in pret (`data/wild/fish.asm`): 13 fishing groups; the Old Rod gives Magikarp 85% and one group species 15% (Krabby, Tentacool, Goldeen or Poliwag by water type) | Fishing shares the area or is an extra encounter under the FHH switch |
| Bug-Catching Contest | Same as Gold/Silver (Tuesday, Thursday, Saturday; National Park; Park Balls) | One area with a contest mode |
| Growlithe on Route 36 | Crystal adds grass on the east side of Route 36 with wild Growlithe (an early Fire-type) | Normal wild encounter |
| Sneasel, Magmar | Sneasel is wild in Ice Path (Gold/Silver: only Mt. Silver); Magmar moved from the Burned Tower to Mt. Silver (unconfirmed, Bulbapedia only) | Use Crystal's table, never Gold/Silver's |
| Mt. Mortar layout | Rebuilt, no Flash needed (unconfirmed, Bulbapedia only) | As a normal cave |
| Multi-floor areas | Same list as Gold/Silver; PokeAPI has 126 areas over 85 locations for Crystal | One area per location, per-floor switch |
| Kanto | Post-game; Kanto wild tables were altered in Crystal | Separate section of the list |

Sources: https://bulbapedia.bulbagarden.net/wiki/Pok%C3%A9mon_Crystal_Version (Pokemon availability changes) ; https://bulbapedia.bulbagarden.net/wiki/Mass_outbreak ; https://bulbapedia.bulbagarden.net/wiki/Headbutt_tree ; P pokecrystal `data/wild/swarm_grass.asm`, `swarm_water.asm`, `treemons.asm`, `treemons_asleep.asm` ; A `encounter_condition_value_map.csv` (swarm-yes rows for Crystal: Dark Cave, Route 35, Route 32 rods). P pokecrystal `engine/events/treemons.asm` (same 10%, 50% and 80% rule), `data/wild/treemon_maps.asm`, `data/wild/treemons.asm`, `data/wild/fish.asm`.

#### 5.2.5 Scripted encounters that must not count (Crystal)

- **The Dude on Route 29** catches a Rattata (L5) as the tutorial (pokecrystal `maps/Route29.asm`: `loadwildmon RATTATA, 5`). Not an encounter. https://bulbapedia.bulbagarden.net/wiki/Dude
- **Silver's first fight in Cherrygrove City:** not an encounter. **Eusine** at Cianwood City is a trainer battle (L25), not an encounter.

#### 5.2.6 Version notes (Crystal versus Gold/Silver)

Crystal is a separate game with its own tables, so it has no exclusives of its own, but its wild tables differ from Gold's. PokeAPI wild-method differences: only in Gold: Mareep, Flaaffy, Girafarig, Mankey, Primeape, Remoraid and a roaming Suicune. Only in Crystal: Ekans, Arbok, Weedle, Kakuna, Beedrill, Parasect, Weezing, Rhydon, Pupitar, Granbull, Meowth, Persian, Ledyba, Ledian, Phanpy, Donphan, Delibird, Skarmory. Bulbapedia's Crystal page agrees on the direction (Silver/Gold exclusives such as Gligar and Skarmory are wild in Crystal; the Mareep line is missing). Gifts, prizes and trades also differ (5.2.1, 5.2.2). Sources: https://bulbapedia.bulbagarden.net/wiki/Pok%C3%A9mon_Crystal_Version ; A version_id 4 versus 6.

### 5.3 Where the encounter-area list can come from (Crystal)

| Source | What it gives for Crystal | Licence |
|---|---|---|
| PokeAPI | 126 location areas with encounters across 85 locations; same method and condition families as Gold/Silver, plus gift-egg rows for the Odd Egg | BSD-3-Clause |
| Nuzlocke Tracker data (`routes.json`, key `crys`) | 84 areas in story order with 35 boss markers (includes Eusine) | BSD-3-Clause in the app repository; data repository has no licence |
| pret pokecrystal | `data/wild/johto_grass.asm` (61 map entries), `johto_water.asm` (38), `kanto_grass.asm` (30), `kanto_water.asm` (24), swarm, fish, tree and roaming files, `data/maps/landmarks.asm` (96 entries, entry 0 a placeholder) | No licence file (not shippable) |
| Gen 2 IronMON tracker fork (MIT) | `RouteData.lua`: 95 location names (names only; the NOTICE file already lists this fork as a Crystal reference) | MIT (section 9.5) |
| Bulbapedia | https://bulbapedia.bulbagarden.net/wiki/List_of_locations_by_index_number_(Generation_II) | CC BY-NC-SA 2.5 (not shippable) |

Details and caveats: section 9.

## 6. Pokemon Ruby and Pokemon Sapphire (Generation III)

The pokeruby disassembly builds both games from one trainer table, and Bulbapedia's Ruby/Sapphire blocks use one code (RS) for the Gym Leaders, Elite Four and Champion, so those numbers are shared. The villain team differs by version (Team Magma in Ruby, Team Aqua in Sapphire), and that shows in the optional caps.

### 6.1 Level caps (Hardcore Nuzlocke)

| # | Battle | Location | Cap (highest level) | Ace / top Pokemon | Sources |
|---|---|---|---|---|---|
| 1 | Roxanne | Rustboro City (Rustboro Gym) | 15 | Nosepass | P B N D S |
| 2 | Brawly | Dewford Town (Dewford Gym) | 18 | Makuhita | P B N D S |
| 3 | Wattson | Mauville City (Mauville Gym) | 23 | Magneton | P B N D S |
| 4 | Flannery | Lavaridge Town (Lavaridge Gym) | 28 | Torkoal | P B N D S |
| 5 | Norman | Petalburg City (Petalburg Gym) | 31 | Slaking | P B N D S |
| 6 | Winona | Fortree City (Fortree Gym) | 33 | Altaria | P B N D S |
| 7 | Tate and Liza | Mossdeep City (Mossdeep Gym, double battle) | 42 | Lunatone / Solrock | P B N D S |
| 8 | Wallace | Sootopolis City (Sootopolis Gym) | 43 | Milotic | P B N D S |
| E4-1 | Sidney | Ever Grande City (Pokemon League) | 49 | Absol | P B N D S |
| E4-2 | Phoebe | Ever Grande City (Pokemon League) | 51 | Dusclops | P B N D S |
| E4-3 | Glacia | Ever Grande City (Pokemon League) | 53 | Walrein | P B N D S |
| E4-4 | Drake | Ever Grande City (Pokemon League) | 55 | Salamence | P B N D(says 54) S |
| C | Champion Steven | Ever Grande City (Pokemon League) | 58 | Metagross | P B N D S |

Sources: P https://raw.githubusercontent.com/pret/pokeruby/master/src/data/trainers_en.h and https://raw.githubusercontent.com/pret/pokeruby/master/src/data/trainer_parties.h (TRAINER_ROXANNE ... TRAINER_STEVEN) ; B one page per trainer, for example https://bulbapedia.bulbagarden.net/wiki/Roxanne (Rustboro Gym, game code RS), https://bulbapedia.bulbagarden.net/wiki/Tate_and_Liza , https://bulbapedia.bulbagarden.net/wiki/Wallace , https://bulbapedia.bulbagarden.net/wiki/Steven_Stone ; N https://nuzlockeuniversity.ca/2022/01/18/hardcore-nuzlocke-level-caps-by-generation/ (section "Pokemon Ruby/Sapphire") ; D https://github.com/domtronn/nuzlocke.data/blob/main/leagues/rs.txt (D reads Drake as 54, which is wrong) ; S https://www.serebii.net/rubysapphire/gyms.shtml , https://www.serebii.net/rubysapphire/elitefour.shtml (same numbers as P and B)

Structure note: in Ruby and Sapphire the eighth Gym Leader is Wallace (Sootopolis, cap 43) and the Champion is Steven. In Emerald the eighth is Juan and the Champion is Wallace (section 7). Tate and Liza (gym 7) is a double battle; the cap is the higher of their two Pokemon (Lunatone and Solrock, 42).

Order and flexibility: Bulbapedia's Gym Badge page (Trivia) says that in Ruby, Sapphire and Emerald "the Knuckle Badge [gym 2, Brawly] can be postponed until after the Heat Badge [gym 4, Flannery], but must be obtained in order to challenge the Petalburg Gym. The Feather Badge [gym 6, Winona] is not required until challenging the Elite Four." So the standard 1 to 8 order is not forced: gym 2 can move behind gym 4, and gym 6 can move behind gyms 7 and 8. Caps follow the leader you are about to fight. https://bulbapedia.bulbagarden.net/wiki/Gym_Badge

Other cap points (optional caps; NU lists none of these). Ruby's villains are Team Magma (Maxie, Tabitha, Courtney), Sapphire's are Team Aqua (Archie, Matt, Shelly); the rows say which:

| Battle | Location | Cap | Where it falls in a normal run (unconfirmed, Bulbapedia order only) | Sources |
|---|---|---|---|---|
| Rival, Route 103 | Route 103 | 5 | Start of game (Slow Start / Starter clauses normally skip it) | P B D S |
| Wally, Mauville City | Mauville City | 16 | With Gym 3 (Wattson) | P B D(says 17) |
| Rival, Route 110 | Route 110 | 20 | Between Gym 2 and Gym 3 | P B D S |
| Ruby: Tabitha, Mt. Chimney | Mt. Chimney | 20 | With Gym 4 (Flannery) | P B D |
| Sapphire: Matt, Mt. Chimney | Mt. Chimney | 20 | With Gym 4 (Flannery) | P B D |
| Ruby: Maxie, Mt. Chimney | Mt. Chimney | 25 | With Gym 4 (Flannery) | P B D |
| Sapphire: Archie, Mt. Chimney | Mt. Chimney | 25 | With Gym 4 (Flannery) | P B D |
| Ruby: Courtney, Weather Institute | Weather Institute (Route 119) | 28 | With Gym 6 (Winona) | P B D |
| Sapphire: Shelly, Weather Institute | Weather Institute (Route 119) | 28 | With Gym 6 (Winona) | P B D |
| Rival, Route 119 | Route 119 | 31 | Between Gym 5 and Gym 6 | P B D S |
| Ruby: Tabitha, Magma Hideout | Team Magma Hideout (island cave east of Lilycove City) | 32 | Between Gym 6 and Gym 7 | P B D |
| Sapphire: Matt, Aqua Hideout | Team Aqua Hideout (Lilycove City) | 32 | Between Gym 6 and Gym 7 | P B D |
| Rival, Lilycove City (last before the League) | Lilycove City | 34 | Between Gym 6 and Gym 7 | P B D S |
| Ruby: Courtney, Seafloor Cavern | Seafloor Cavern | 38 | Between Gym 7 and Gym 8 | P B D |
| Sapphire: Shelly, Seafloor Cavern | Seafloor Cavern | 38 | Between Gym 7 and Gym 8 | P B D |
| Ruby: Maxie, Seafloor Cavern | Seafloor Cavern | 43 | Between Gym 7 and Gym 8 | P B D |
| Sapphire: Archie, Seafloor Cavern | Seafloor Cavern | 43 | Between Gym 7 and Gym 8 | P B D |
| Wally, Victory Road (last before the League) | Victory Road | 45 | After Gym 8, before the Elite Four | P B D |

Sources: P pokeruby trainer table (TRAINER_BRENDAN_1 to 9 and TRAINER_BRENDAN_LILYCOVE_*, TRAINER_WALLY_1 and 2, TRAINER_MAXIE_2 and 3, TRAINER_ARCHIE_2 and 3, TRAINER_TABITHA_1 and 2, TRAINER_MATT_1 and 2, TRAINER_COURTNEY_1 and 2, TRAINER_SHELLY_1 and 2; Brendan and May share the same levels) ; B https://bulbapedia.bulbagarden.net/wiki/Brendan (RS blocks), https://bulbapedia.bulbagarden.net/wiki/Walkthrough:Pok%C3%A9mon_Ruby_and_Sapphire/Part_1 to Part_22 (game codes Ru and Sa, with locations) ; D `leagues/rs.txt` (r1 to r4, w1, w2, ruby1 to 4, saph1 to 4, max1/2, arc1/2). pokeruby also holds an L17 Maxie and Archie entry that neither Bulbapedia nor the walkthrough shows as a fight; it is left out. The "typical place" column comes from the walkthrough part each fight sits in (Bulbapedia only). S https://www.serebii.net/rubysapphire/rivals.shtml (rival and villain fights).

### 6.2 Rulings

#### 6.2.1 Gift and purchased Pokemon (Ruby and Sapphire)

| Pokemon | Where and how | Level | Suggested default (unconfirmed proposal) | Src |
|---|---|---|---|---|
| Treecko, Torchic or Mudkip (choose one) | Route 101, from Professor Birch's bag while a wild Poochyena attacks him | 5 | Free (starter) | P B A S |
| Wynaut (Egg) | The old woman by the sand baths, Lavaridge Town | 5 at hatching | Free (gift egg) | P B A S |
| Lileep (Root Fossil) or Anorith (Claw Fossil), choose one | Fossil picked on Route 111 (the other sinks and is lost); revived at the Devon Corporation, Rustboro City | 20 | Free (gift) | P B A S |
| Castform | Weather Institute, Route 119, for saving it from Team Magma (Ruby) or Team Aqua (Sapphire) | 25 | Free (gift) | P B A S |
| Beldum | Steven's house, Mossdeep City, after the Hall of Fame | 5 | Free (gift), post-game | P B A S |

Ruby and Sapphire have no Game Corner prize Pokemon (PokeAPI shows no coin-priced gifts for Hoenn). Sources: https://bulbapedia.bulbagarden.net/wiki/Gift_Pok%C3%A9mon (Generation III, Ruby, Sapphire and Emerald table) , https://bulbapedia.bulbagarden.net/wiki/List_of_in-game_event_Pok%C3%A9mon_in_Pok%C3%A9mon_Ruby_and_Sapphire ; A rows with method gift and gift-egg for version_id 7 (Ruby) and 8 (Sapphire). PokeAPI also lists three "colosseum-bonus-disc" rows (Jirachi, Celebi, Pikachu) that are not obtainable in normal play; ignore them. P (https://raw.githubusercontent.com/pret/pokeruby/master/): `src/battle_setup.c` (CB2_GiveStarter: the starter is given at L5 and the Poochyena battle starts right after), `data/maps/LavaridgeTown/scripts.inc` (Wynaut Egg), `data/maps/RustboroCity_DevonCorp_2F/scripts.inc` (Lileep and Anorith L20), `data/maps/Route119_WeatherInstitute_2F/scripts.inc` (Castform L25, holding a Mystic Water), `data/maps/MossdeepCity_StevensHouse/scripts.inc` (Beldum L5). S https://www.serebii.net/rubysapphire/gift.shtml (the same five gifts).

#### 6.2.2 In-game trades (Ruby and Sapphire: three)

| Where | You give | You get | Src |
|---|---|---|---|
| Rustboro City | Slakoth | Makuhita | P B A S |
| Fortree City | Pikachu | Skitty | P B A S |
| Pacifidlog Town | Bellossom | Corsola | P B A S |

Suggested default: free. Sources: https://bulbapedia.bulbagarden.net/wiki/List_of_in-game_trades (Generation III, Ruby and Sapphire) ; A rows with method npc-trade. P pokeruby `src/trade.c` (gIngameTrades: Makuhita for Slakoth, Skitty for Pikachu, Corsola for Bellossom). S https://www.serebii.net/rubysapphire/trade.shtml (the same three).

#### 6.2.3 Static, roaming and one-off encounters (Ruby and Sapphire)

| Encounter | Where | Level | Notes | Suggested default (unconfirmed proposal) | Src |
|---|---|---|---|---|---|
| Kecleon x8 | Route 119 (2) and Route 120 (6), invisible until the Devon Scope | 30 | Eight separate set encounters (pret map data: 2 objects on Route 119, 6 on Route 120, all running `setwildbattle SPECIES_KECLEON, 30`) | Free (static); the example ruleset allows one such story Pokemon per run | P B A |
| Voltorb x3 | New Mauville (fake item balls) | 25 | Also three in Emerald (pret) | Free (static); watch "one per area" | P B |
| Electrode x2 | Team Magma Hideout (Ruby) or Team Aqua Hideout (Sapphire), B1F, fake item balls | 30 | Two per hideout; pret runs the same two scripts (`Hideout_B1F_EventScript_Electrode1` and `2`) for both hideouts | Free (static) | P B A |
| Regirock, Regice, Registeel | Desert Ruins, Island Cave, Ancient Tomb | 40 | Braille puzzle; chambers open only with Relicanth in the lead and Wailord in the last slot (the reverse of Emerald) | Free; flag for the legendary ban | P B A S |
| Groudon (Ruby) or Kyogre (Sapphire) | Cave of Origin, Sootopolis City, during the climax | 45 | Version exclusive | Free; flag for the legendary ban | P B A S |
| Rayquaza | Sky Pillar apex | 70 | Reachable only after the Hall of Fame in Ruby and Sapphire | Post-game; flag for the legendary ban | P B A S |
| Latios (Ruby) or Latias (Sapphire) | Roams Hoenn after the Hall of Fame | 40 | The other one is on Southern Island (L50) but that needs the Eon Ticket, an event item not obtainable in US retail games | Post-game roamer; flag for the legendary ban | P B A S |
| Feebas | Route 119, fishing on 6 water tiles chosen at random (pret `CheckFeebas` in `src/wild_encounter.c`, level range 20 to 25 in `gWildFeebasRoute119Data`) | 20 to 25 | A wild encounter, but only on those 6 tiles | Wild; count under the fishing switch | P A |

Sources: https://bulbapedia.bulbagarden.net/wiki/List_of_in-game_event_Pok%C3%A9mon_in_Pok%C3%A9mon_Ruby_and_Sapphire ; A rows with method static, devon-scope, roaming-grass, roaming-water and feebas-tile-fishing for version_id 7 and 8; P pokeruby `data/maps/NewMauville_Inside/scripts.inc` (three `setwildbattle SPECIES_VOLTORB, 25`). PokeAPI's Ruby rows overcount the New Mauville Voltorb/Electrode (see 0.4); use the Bulbapedia and pret counts. Also P `data/scripts/static_pokemon.inc` (Kecleon scripts and the two hideout Electrode, L30), `data/maps/Route119/map.json` and `Route120/map.json` (the Kecleon objects), `data/maps/DesertRuins`, `IslandCave` and `AncientTomb` scripts (Regirock, Regice, Registeel L40), `data/maps/CaveOfOrigin_B4F/scripts.inc` (Groudon or Kyogre L45), `data/maps/SkyPillar_Top/scripts.inc` (Rayquaza L70), `data/maps/SouthernIsland_Interior/scripts.inc` (Latias or Latios L50, Soul Dew), `data/maps/Route131/scripts.inc` (the Sky Pillar layout appears only after the Hall of Fame). S https://www.serebii.net/rubysapphire/legendary.shtml (Kyogre or Groudon, the three Regis at L40 with Relicanth first and Wailord sixth, Rayquaza after the Champion, roaming Latios or Latias after the League).

#### 6.2.4 Special areas and handling (Ruby and Sapphire)

| Area or mechanic | Fact | Suggested handling (unconfirmed proposal) |
|---|---|---|
| Safari Zone (Route 121) | Four areas (PokeAPI: NW Mach Bike, NE Acro Bike, SW, SE); entry $500 for 30 Safari Balls, and you must own a Pokeblock Case. Two more eastern areas exist only in Emerald | Switch: per area (Bulbapedia "On Safari") or the whole Zone as one area |
| Rock Smash | Rock Smash encounters exist in Granite Cave B2F, Victory Road B1F, Route 111, Route 114 and the Safari Zone NE area (PokeAPI: 5 areas, 25 rows) | Part of the area's encounters, or an extra type under the fishing/headbutt switch |
| Fishing and slot odds | Every water area has its own rod table. The Old Rod has 2 slots (70% / 30%), the Good Rod 3 slots (60% / 20% / 20%) and the Super Rod 5 slots (40% / 40% / 15% / 4% / 1%). Surf and Rock Smash tables have 5 slots (60 / 30 / 5 / 4 / 1) and grass has 12 slots (20 / 20 / 10 / 10 / 10 / 10 / 5 / 5 / 4 / 4 / 1 / 1). Identical in pokeruby, pokeemerald and pokefirered | Fishing shares the area or is an extra encounter under the FHH switch |
| Underwater (Dive) | Seaweed encounters in Route 124 and Route 126 underwater areas | Own encounter type; Route 124 and 126 water areas |
| Towns and cities with water | Petalburg City, Slateport City, Lilycove City, Mossdeep City, Sootopolis City, Ever Grande City, Dewford Town and Pacifidlog Town have surf and fishing tables of their own | Own areas (NU's Emerald guide treats town fishing as separate from the route encounters) |
| Route 119 | Feebas tiles, invisible Kecleon, and the Weather Institute (gift) all sit in this route | One area with special encounters attached |
| Route 111 | Desert with Rock Smash and the fossil choice; PokeAPI lists it as one area | One area |
| Multi-room caves and buildings | Granite Cave, Meteor Falls, Mt. Pyre (1F to 6F, outside, summit), Victory Road (1F, B1F, B2F), Shoal Cave (tide-dependent rooms), Cave of Origin, Seafloor Cavern, Sky Pillar (floors): PokeAPI has 103 areas over 68 locations for Ruby, 104 over 69 for Sapphire | One area per location, per-floor switch |
| Mirage Island | A rare alternate map of Route 130 that appears when the day's random number matches a party Pokemon's personality value (odds around 1 in 10,900 for a full party); wild Wynaut L5 to 50; Pokemon caught there show Route 130 as their origin (Bulbapedia) | Part of Route 130 (the game records it that way); may never appear |
| Abandoned Ship, Route 105 to 110, 122 to 134 | Water routes: surf and fishing only | Own areas |

Sources: https://bulbapedia.bulbagarden.net/wiki/Hoenn_Safari_Zone , https://bulbapedia.bulbagarden.net/wiki/Mirage_Island_(Generation_III) ; A `location_areas.csv` and `encounters.csv` for version_id 7 and 8 ; https://nuzlockeuniversity.ca/2021/02/03/pokemon-emerald-nuzlocke-guide-and-tips/ . P pokeruby `src/data/wild_encounters.json` (encounter_rates and the fishing_mons groups). P pokeruby `src/safari_zone.c` (30 Safari Balls) and `src/time_events.c` (IsMirageIslandPresent: the island shows when the day's 16-bit random value equals the low half of a party Pokemon's personality value, so 6 party slots out of 65,536 values is about 1 in 10,900).

#### 6.2.5 Scripted encounters that must not count (Ruby and Sapphire)

- **The Poochyena that attacks Professor Birch on Route 101.** Bulbapedia's Slow Start rule names exactly this Pokemon as not counted (pokeruby `src/battle_controllers.c` creates it at L2). The starter you pick is fought with, not caught.
- **Wally's Ralts on Route 102.** Norman lends Wally a Zigzagoon, and Wally catches a wild Ralts (L5) himself. You get no Pokemon and the Route 102 encounter is still yours. The tutorial battle is created in pokeruby `src/battle_setup.c` (a Ralts at L5) and the scene is in pokeemerald `data/maps/Route102/scripts.inc`. https://bulbapedia.bulbagarden.net/wiki/Wally
- **Wally's Ralts battle in Mauville City** (L16) and later fights are battles, not encounters (see the optional caps).

#### 6.2.6 Version-exclusive Pokemon (Ruby versus Sapphire)

Bulbapedia's list (not obtainable in the other game without trading): Ruby has Seedot, Nuzleaf, Shiftry, Mawile, Zangoose, Solrock, Latios and Groudon. Sapphire has Lotad, Lombre, Ludicolo, Sableye, Seviper, Lunatone, Latias and Kyogre. The villain team is also version-specific (Team Magma in Ruby, Team Aqua in Sapphire). PokeAPI's wild-table diff agrees (Ruby: Seedot, Nuzleaf, Mawile, Solrock, Zangoose, Dusclops; Sapphire: Lotad, Lombre, Sableye, Lunatone, Seviper, Banette; plus the roaming Eon Pokemon). Ship separate tables for Ruby and Sapphire. Sources: https://bulbapedia.bulbagarden.net/wiki/Pok%C3%A9mon_Ruby_and_Sapphire_Versions (Version-exclusive Pokemon) ; A version_id 7 versus 8.

### 6.3 Where the encounter-area list can come from (Ruby and Sapphire)

| Source | What it gives for Ruby/Sapphire | Licence |
|---|---|---|
| PokeAPI | Ruby 103 areas over 68 locations, Sapphire 104 over 69; methods walk, surf, old/good/super-rod, rock-smash, seaweed, gift, gift-egg, static, devon-scope, roaming, feebas-tile-fishing, npc-trade | BSD-3-Clause |
| Nuzlocke Tracker data (`routes.json`, keys `ruby`, `saph`) | 67 areas in story order with 25 boss markers each | BSD-3-Clause in the app repository; data repository has no licence |
| pret pokeruby | `src/data/wild_encounters.json`: 97 distinct maps (194 entries, one per Ruby or Sapphire variant); `src/data/region_map/region_map_sections.json` (the map-section names the game shows) | No licence file (not shippable) |
| besteon/Ironmon-Tracker | `ironmon_tracker/data/RouteData.lua`: route and area names keyed by map layout id, with encounter areas and merged multi-floor dungeons, for Ruby, Sapphire, Emerald and FireRed/LeafGreen | MIT (section 9.5) |
| Bulbapedia | https://bulbapedia.bulbagarden.net/wiki/List_of_locations_by_index_number_(Generation_III) and route pages | CC BY-NC-SA 2.5 (not shippable) |

Details and caveats: section 9.

## 7. Pokemon Emerald (Generation III)

Emerald has its own disassembly (pokeemerald), its own Gym Leader teams (Brawly 19, Wattson 24 and Flannery 29 differ from Ruby/Sapphire's 18, 23 and 28), its own eighth leader (Juan) and its own Champion (Wallace). Both villain teams appear in one game.

### 7.1 Level caps (Hardcore Nuzlocke)

| # | Battle | Location | Cap (highest level) | Ace / top Pokemon | Sources |
|---|---|---|---|---|---|
| 1 | Roxanne | Rustboro City (Rustboro Gym) | 15 | Nosepass | P B N D S |
| 2 | Brawly | Dewford Town (Dewford Gym) | 19 | Makuhita | P B N D S |
| 3 | Wattson | Mauville City (Mauville Gym) | 24 | Manectric | P B N D S |
| 4 | Flannery | Lavaridge Town (Lavaridge Gym) | 29 | Torkoal | P B N D S |
| 5 | Norman | Petalburg City (Petalburg Gym) | 31 | Slaking | P B N D S |
| 6 | Winona | Fortree City (Fortree Gym) | 33 | Altaria | P B N D S |
| 7 | Tate and Liza | Mossdeep City (Mossdeep Gym, double battle) | 42 | Lunatone / Solrock | P B N D S |
| 8 | Juan | Sootopolis City (Sootopolis Gym) | 46 | Kingdra | P B N D S |
| E4-1 | Sidney | Ever Grande City (Pokemon League) | 49 | Absol | P B N D S |
| E4-2 | Phoebe | Ever Grande City (Pokemon League) | 51 | Dusclops | P B N D S |
| E4-3 | Glacia | Ever Grande City (Pokemon League) | 53 | Walrein | P B N D S |
| E4-4 | Drake | Ever Grande City (Pokemon League) | 55 | Salamence | P B N D S |
| C | Champion Wallace | Ever Grande City (Pokemon League) | 58 | Milotic | P B N D S |
| post | Steven Stone (post-game) | Meteor Falls | 78 | Metagross | P B N D |

Sources: P https://raw.githubusercontent.com/pret/pokeemerald/master/src/data/trainers.h and https://raw.githubusercontent.com/pret/pokeemerald/master/src/data/trainer_parties.h (TRAINER_ROXANNE_1 ... TRAINER_WALLACE, TRAINER_STEVEN; the _2 to _5 entries are rematches and are excluded) ; B one page per trainer, for example https://bulbapedia.bulbagarden.net/wiki/Roxanne (first block, Rustboro Gym, game code E), https://bulbapedia.bulbagarden.net/wiki/Juan , https://bulbapedia.bulbagarden.net/wiki/Wallace (Pokemon League block) , https://bulbapedia.bulbagarden.net/wiki/Steven_Stone (Meteor Falls block) ; N https://nuzlockeuniversity.ca/2022/01/18/hardcore-nuzlocke-level-caps-by-generation/ (section "Pokemon Emerald", including "Meteor Falls: Lv 78") ; D https://github.com/domtronn/nuzlocke.data/blob/main/leagues/em.txt ; S https://www.serebii.net/emerald/gym.shtml , https://www.serebii.net/emerald/elite.shtml (same numbers as P and B for all eight gyms, the Elite Four and Wallace)

Order and flexibility: same rule as Ruby and Sapphire (Bulbapedia, Gym Badge trivia): the Knuckle Badge (gym 2, cap 19) can be postponed until after the Heat Badge (gym 4, cap 29) but is needed for the Petalburg Gym, and the Feather Badge (gym 6, cap 33) is not required until the Elite Four. Steven's post-game battle at Meteor Falls (78) is a very high cap that most runs treat as outside the first run. https://bulbapedia.bulbagarden.net/wiki/Gym_Badge

Other cap points (optional caps; NU lists none except Meteor Falls above). In Emerald both villain teams show up; the rival is Brendan or May with the same levels:

| Battle | Location | Cap | Where it falls in a normal run (unconfirmed, Bulbapedia order only) | Sources |
|---|---|---|---|---|
| Rival, Route 103 | Route 103 | 5 | Start of game (Slow Start / Starter clauses normally skip it) | P B D |
| Rival, Rustboro City | Rustboro City | 15 | After Gym 1 (Roxanne) | P B D |
| Rival, Route 110 | Route 110 | 20 | Between Gym 2 and Gym 3 | P B D |
| Wally, Mauville City | Mauville City | 16 | With Gym 3 (Wattson) | P B D |
| Tabitha, Mt. Chimney | Mt. Chimney | 22 | With Gym 4 (Flannery) | P B D |
| Maxie, Mt. Chimney | Mt. Chimney | 25 | With Gym 4 (Flannery) | P B D(not listed) |
| Shelly, Weather Institute | Weather Institute (Route 119) | 28 | Between Gym 5 and Gym 6 | P B D |
| Rival, Route 119 | Route 119 | 31 | Between Gym 5 and Gym 6 | P B D |
| Tabitha, Magma Hideout | Magma Hideout (inside Mt. Chimney, entrance on Jagged Pass) | 33 | After Gym 6 (Winona) | P B D |
| Maxie, Magma Hideout | Magma Hideout (inside Mt. Chimney, entrance on Jagged Pass) | 39 | After Gym 6 (Winona) | P B D |
| Matt, Aqua Hideout | Team Aqua Hideout (Lilycove City) | 34 | After Gym 6 (Winona) | P B D(says 37) |
| Rival, Lilycove City (last before the League) | Lilycove City | 34 | After Gym 6 (Winona) | P B D |
| Maxie and Tabitha (double battle), Mossdeep Space Center | Mossdeep Space Center (Steven is your partner) | 44 | With Gym 7 (Tate and Liza) | P B D |
| Shelly, Seafloor Cavern | Seafloor Cavern | 37 | Between Gym 7 and Gym 8 | P B D |
| Archie, Seafloor Cavern | Seafloor Cavern | 43 | Between Gym 7 and Gym 8 | P B D |
| Wally, Victory Road (last before the League) | Victory Road | 45 | After Gym 8, before the Elite Four | P B D |

Sources: P pokeemerald trainer table (TRAINER_BRENDAN_* and TRAINER_MAY_* in five location groups, TRAINER_WALLY_MAUVILLE and TRAINER_WALLY_VR_1, TRAINER_TABITHA_MT_CHIMNEY, TRAINER_MAXIE_MT_CHIMNEY, TRAINER_SHELLY_WEATHER_INSTITUTE, TRAINER_TABITHA_MAGMA_HIDEOUT, TRAINER_MAXIE_MAGMA_HIDEOUT, TRAINER_MATT, TRAINER_MAXIE_MOSSDEEP plus TRAINER_TABITHA_MOSSDEEP, TRAINER_SHELLY_SEAFLOOR_CAVERN, TRAINER_ARCHIE) ; B https://bulbapedia.bulbagarden.net/wiki/Brendan (Emerald blocks), https://bulbapedia.bulbagarden.net/wiki/Walkthrough:Pok%C3%A9mon_Emerald/Part_1 to Part_20 (locations and order) ; D `leagues/em.txt` (r1 to r5, w1, w2, mt1, mt2, mtmax, mtmaxt, as1, as2, am1, aqarc). The "typical place" column comes from the walkthrough part each fight sits in (Bulbapedia only). The Mossdeep Space Center battle is a double battle against Maxie and Tabitha with Steven as your partner; the cap is the top level across all six Pokemon (44). Serebii's Emerald rival page was not read, so these rows have no S.

### 7.2 Rulings

#### 7.2.1 Gift and purchased Pokemon (Emerald)

| Pokemon | Where and how | Level | Suggested default (unconfirmed proposal) | Src |
|---|---|---|---|---|
| Treecko, Torchic or Mudkip (choose one) | Route 101, from Professor Birch's bag while a wild Zigzagoon attacks him | 5 | Free (starter) | P B A |
| Wynaut (Egg) | The old woman by the sand baths, Lavaridge Town | 5 at hatching | Free (gift egg) | P B A S |
| Lileep (Root Fossil) or Anorith (Claw Fossil), choose one | Mirage Tower on Route 111 (it sinks once you pick); revived at the Devon Corporation, Rustboro City | 20 | Free (gift) | P B A S |
| The other fossil | Desert Underpass, after the Hall of Fame | 20 | Free (gift), post-game | P B A S |
| Castform | Weather Institute, Route 119, for saving it from Team Aqua | 25 | Free (gift) | P B A S |
| Beldum | Steven's house, Mossdeep City, after the Hall of Fame | 5 | Free (gift), post-game | P B A S |
| Chikorita, Cyndaquil or Totodile (choose one) | Professor Birch, Littleroot Town, after completing the Hoenn Pokedex (Emerald only) | 5 | Free (gift), post-game | P B A S |
| Pichu (Egg) | Mystery Gift event script only (pret `data/scripts/gift_pichu.inc`, `MysteryGiftScript_SurfPichu`); no in-game way to get it | 5 at hatching | Exclude | P |

Emerald has no Game Corner prize Pokemon. Sources: https://bulbapedia.bulbagarden.net/wiki/Gift_Pok%C3%A9mon (Generation III) , https://bulbapedia.bulbagarden.net/wiki/List_of_in-game_event_Pok%C3%A9mon_in_Pok%C3%A9mon_Emerald ; A rows with method gift and gift-egg for version_id 9 (PokeAPI files the Johto starters under "Littleroot Town" with no condition; the condition is the Hoenn Pokedex). PokeAPI's "colosseum-bonus-disc" Celebi and Pikachu rows are not obtainable; ignore them. P (https://raw.githubusercontent.com/pret/pokeemerald/master/): `src/battle_setup.c` (starter given at L5 by ScriptGiveMon), `data/maps/LavaridgeTown/scripts.inc` (Wynaut Egg), `data/maps/RustboroCity_DevonCorp_2F/scripts.inc` (Lileep and Anorith L20), `data/maps/Route119_WeatherInstitute_2F/scripts.inc` (Castform L25, holding a Mystic Water), `data/maps/MossdeepCity_StevensHouse/scripts.inc` (Beldum L5), `data/maps/LittlerootTown_ProfessorBirchsLab/scripts.inc` (Chikorita, Cyndaquil, Totodile L5). S https://www.serebii.net/emerald/gift.shtml (the same gifts, including the Desert Underpass fossil).

#### 7.2.2 In-game trades (Emerald: four)

| Where | You give | You get | Src |
|---|---|---|---|
| Rustboro City | Ralts | Seedot | P B A |
| Fortree City | Volbeat | Plusle | P B A |
| Pacifidlog Town | Bagon | Horsea | P B A |
| Battle Frontier | Skitty | Meowth | P B A |

Suggested default: free. The Battle Frontier trade is post-game. Sources: https://bulbapedia.bulbagarden.net/wiki/List_of_in-game_trades (Generation III, Emerald) ; A rows with method npc-trade for version_id 9. P pokeemerald `src/data/trade.h` (four entries: Seedot for Ralts, Plusle for Volbeat, Horsea for Bagon, Meowth for Skitty) and the four maps that call them (`RustboroCity_House1`, `FortreeCity_House1`, `PacifidlogTown_House3`, `BattleFrontier_Lounge6`). S https://www.serebii.net/emerald/trade.shtml lists only the last three (0.4).

#### 7.2.3 Static, roaming and one-off encounters (Emerald)

| Encounter | Where | Level | Notes | Suggested default (unconfirmed proposal) | Src |
|---|---|---|---|---|---|
| Kecleon x8 | Route 119 (2) and Route 120 (6), invisible until the Devon Scope | 30 | Eight separate set encounters (pret map data: 2 objects on Route 119, 6 on Route 120, all running `setwildbattle SPECIES_KECLEON, 30` in `data/scripts/kecleon.inc`) | Free (static); the example ruleset allows one such story Pokemon per run | P B A |
| Voltorb x3 | New Mauville (fake item balls) | 25 | pret and Bulbapedia agree on three | Free (static); watch "one per area" | P B |
| Electrode x2 | Team Aqua Hideout (fake item balls) | 30 | Emerald has no Electrode in the Magma Hideout (PokeAPI wrongly shows some) | Free (static) | P B |
| Regirock, Regice, Registeel | Desert Ruins, Island Cave, Ancient Tomb | 40 | Braille puzzle; chambers open with Wailord in the lead and Relicanth last (the reverse of Ruby/Sapphire) | Free; flag for the legendary ban | P B A S |
| Rayquaza | Sky Pillar apex | 70 | Available as soon as the Sootopolis events are resolved, before the Hall of Fame (Bulbapedia; pret opens the Sky Pillar through the Wallace scene in `SkyPillar_Outside`; Serebii's "after the Champion" is the Ruby/Sapphire text repeated, see 0.4) | Flag for the legendary ban | P B A |
| Groudon (Terra Cave) and Kyogre (Marine Cave) | After the Hall of Fame a Weather Institute researcher reports harsh sunlight on Route 114, 115, 116 or 118 (Terra Cave) or torrential rain on Route 105, 125, 127 or 129 (Marine Cave, reached by Diving); the location changes over time | 70 | Both are in Emerald and both are catchable (pret `TerraCave_End` and `MarineCave_End` run `setwildbattle` for Groudon and Kyogre at L70) | Post-game; flag for the legendary ban | P B A S |
| Latias or Latios | Roams Hoenn after the Hall of Fame; which one depends on the colour your Mom hears (Red = Latias, Blue = Latios) | 40 | The other is on Southern Island (L50) but needs the Eon Ticket, not obtainable in US retail games | Post-game roamer; flag for the legendary ban | B A S |
| Sudowoodo | Battle Frontier, southeast section | 40 | Wailmer Pail; the only wild Sudowoodo in Gen 3 handhelds | Post-game | P B A |
| Mew, Lugia, Ho-Oh, Deoxys | Faraway Island, Navel Rock, Birth Island | 30 to 70 | Need the Old Sea Map, MysticTicket and AuroraTicket, which were event-only | Exclude | B A S |
| Feebas | Route 119, fishing on 6 water tiles chosen at random out of 447 fishable tiles (pret `CheckFeebas` in `src/wild_encounter.c`, `sWildFeebas` L20 to 25) | 20 to 25 | Wild, but only on those 6 tiles | Wild; count under the fishing switch | P A |

Sources: https://bulbapedia.bulbagarden.net/wiki/List_of_in-game_event_Pok%C3%A9mon_in_Pok%C3%A9mon_Emerald ; A rows with method static, devon-scope, roaming-*, wailmer-pail and feebas-tile-fishing for version_id 9; P pokeemerald `data/maps/NewMauville_Inside/scripts.inc` (three `setwildbattle SPECIES_VOLTORB, 25`) and `data/maps/AquaHideout_B1F/scripts.inc` (two `setwildbattle SPECIES_ELECTRODE, 30`). Also P `data/scripts/kecleon.inc`, `data/maps/Route119/map.json` and `Route120/map.json` (Kecleon objects), `data/maps/SkyPillar_Top/scripts.inc` (Rayquaza L70), `TerraCave_End` and `MarineCave_End` scripts (L70), `BattleFrontier_OutsideEast` (Sudowoodo L40), `data/maps/SkyPillar_Outside/scripts.inc` and `Route131/scripts.inc` (Sky Pillar access). S https://www.serebii.net/emerald/legendary.shtml (Kyogre and Groudon L70 on the routes above, Regis L40 with Wailord first and Relicanth sixth, roaming Latias or Latios L40, Mew at Faraway Island).

#### 7.2.4 Special areas and handling (Emerald)

| Area or mechanic | Fact | Suggested handling (unconfirmed proposal) |
|---|---|---|
| Safari Zone (Route 121) | Six areas in Emerald (PokeAPI: NW, NE, SW, SE plus expansion south and north). The two expansions open after the National Pokedex and add 16 Johto evolutionary lines, most found nowhere else on GBA (unconfirmed, Bulbapedia only). Entry $500 for 30 Safari Balls, Pokeblock Case required | Switch: per area (Bulbapedia "On Safari") or the whole Zone |
| Rock Smash | Granite Cave B2F, Victory Road B1F, Route 111, Route 114, Safari NE and more (PokeAPI: 30 rows in Emerald) | Part of the area, or an extra type under the fishing/headbutt switch |
| Fishing and slot odds | Same as Ruby/Sapphire (identical in pokeruby, pokeemerald and pokefirered): the Old Rod has 2 slots (70% / 30%), the Good Rod 3 slots (60% / 20% / 20%), the Super Rod 5 slots (40% / 40% / 15% / 4% / 1%); Surf and Rock Smash 5 slots (60 / 30 / 5 / 4 / 1); grass 12 slots (20 / 20 / 10 / 10 / 10 / 10 / 5 / 5 / 4 / 4 / 1 / 1) | Fishing shares the area or is an extra encounter under the FHH switch |
| Underwater (Dive) | Seaweed encounters in Route 124 and Route 126 underwater areas | Own encounter type |
| Towns and cities with water | As in Ruby/Sapphire (Petalburg City, Slateport City, Lilycove City, Mossdeep City, Sootopolis City, Ever Grande City, Dewford Town, Pacifidlog Town) | Own areas |
| Route 119 and Route 120 | Feebas tiles, invisible Kecleon, the Weather Institute gift | One area each with special encounters attached |
| Route 111, Mirage Tower, Desert Underpass | Desert with Rock Smash; Mirage Tower holds the fossil choice (Sandshrew and Trapinch inside); the Desert Underpass (wild Ditto, other fossil) opens after the Hall of Fame | Separate areas; Underpass is post-game |
| Multi-room caves and buildings | Granite Cave, Meteor Falls, Mt. Pyre (1F to 6F, outside, summit), Victory Road (1F, B1F, B2F), Shoal Cave (tides), Cave of Origin, Seafloor Cavern, Sky Pillar: PokeAPI has 117 areas over 81 locations for Emerald | One area per location, per-floor switch |
| Altering Cave | Route 103, opens after the Hall of Fame; only Zubat in normal play (the other tables were never distributed) | Post-game area |
| Battle Frontier and Artisan Cave | Post-game; the only wild Smeargle (Artisan Cave) and a Sudowoodo | Post-game areas |
| Mirage Island | A rare alternate map of Route 130 (daily personality-value match, roughly 1 in 10,900 for a full party); wild Wynaut L5 to 50; Pokemon caught there show Route 130 as their origin | Part of Route 130; may never appear |
| Magma Hideout | In Emerald Team Magma's hideout is inside Mt. Chimney with its entrance on Jagged Pass, and Groudon is first met there (story only; pret's `data/maps/JaggedPass/map.json` has the warp into `MAP_MAGMA_HIDEOUT_1F`). In Ruby the Team Magma Hideout is an island cave east of Lilycove City instead (Bulbapedia) | Not a wild area |

Sources: https://bulbapedia.bulbagarden.net/wiki/Hoenn_Safari_Zone , https://bulbapedia.bulbagarden.net/wiki/Pok%C3%A9mon_Emerald_Version (differences from Ruby and Sapphire) , https://bulbapedia.bulbagarden.net/wiki/Altering_Cave , https://bulbapedia.bulbagarden.net/wiki/Mirage_Island_(Generation_III) ; A `location_areas.csv` and `encounters.csv` for version_id 9 ; https://nuzlockeuniversity.ca/2021/02/03/pokemon-emerald-nuzlocke-guide-and-tips/ (Whismur is a guaranteed Rusturf Tunnel encounter, so it collides with a Route 116 Whismur under the dupes clause; town fishing is a free extra). P pokeemerald `src/data/wild_encounters.json` (encounter_rates and the fishing_mons groups). P pokeemerald `data/maps/Route121_SafariZoneEntrance/scripts.inc` ($500 and the Pokeblock Case check), `src/safari_zone.c` (30 Safari Balls) and `src/time_events.c` (IsMirageIslandPresent, same rule as in Ruby and Sapphire).

#### 7.2.5 Scripted encounters that must not count (Emerald)

- **The Zigzagoon that attacks Professor Birch on Route 101.** Bulbapedia's Slow Start rule names this Pokemon (Zigzagoon in Emerald). pokeemerald `data/maps/Route101/scripts.inc` scripts the chase and `src/battle_controllers.c` creates the Zigzagoon at L2. The starter you pick is fought with, not caught.
- **Wally's Ralts on Route 102** (L5, caught by Wally with Norman's Zigzagoon): not your encounter, and Route 102 still gives you its first wild encounter. pokeemerald `data/maps/Route102/scripts.inc` contains the scene and `src/battle_setup.c` creates the Ralts at L5. https://bulbapedia.bulbagarden.net/wiki/Wally
- **Wally's Ralts battle in Mauville City** (L16) and the later Victory Road battle (L45) are battles, not encounters.

#### 7.2.6 Version notes (Emerald versus Ruby and Sapphire)

Emerald is a separate game with no exclusives of its own, but its wild tables differ from Ruby and Sapphire's, and it adds Johto Pokemon (Aipom, Ditto, Gligar, Hoothoot, Houndour, Ledyba, Mareep, Miltank, Octillery, Pineco, Quagsire, Remoraid, Shuckle, Smeargle, Snubbull, Spinarak, Stantler, Sunkern, Teddiursa, Wooper and more). PokeAPI wild-method differences against Ruby: only in Ruby: Dusclops, Medicham, Meditite, Roselia, Surskit, Zangoose; only in Emerald: 27 species including the Johto ones above plus Banette, Lombre, Lotad, Mightyena, Sableye, Seviper and the roaming Latias. Gifts and trades differ (7.2.1, 7.2.2). Bulbapedia also notes Brendan and May use different Pokemon than in Ruby and Sapphire. Sources: https://bulbapedia.bulbagarden.net/wiki/Pok%C3%A9mon_Emerald_Version ; A version_id 9 versus 7.

### 7.3 Where the encounter-area list can come from (Emerald)

| Source | What it gives for Emerald | Licence |
|---|---|---|
| PokeAPI | 117 location areas with encounters across 81 locations; same method families as Ruby/Sapphire plus wailmer-pail | BSD-3-Clause |
| Nuzlocke Tracker data (`routes.json`, key `em`) | 70 areas in story order with 29 boss markers | BSD-3-Clause in the app repository; data repository has no licence |
| pret pokeemerald | `src/data/wild_encounters.json`: 124 header entries over 116 distinct maps (the Battle Pyramid and Battle Pike groups are separate); `src/data/region_map/region_map_sections.json` (213 map-section entries, the names the game shows) | No licence file (not shippable) |
| besteon/Ironmon-Tracker | `ironmon_tracker/data/RouteData.lua` (Emerald branch, keyed by map layout id, with merged multi-floor areas) | MIT (section 9.5) |
| Bulbapedia | https://bulbapedia.bulbagarden.net/wiki/List_of_locations_by_index_number_(Generation_III) and route pages | CC BY-NC-SA 2.5 (not shippable) |

Details and caveats: section 9.

## 8. Pokemon FireRed and Pokemon LeafGreen (Generation III)

FireRed/LeafGreen reuse Kanto but not the Red/Blue numbers for the Elite Four and Champion, and the gifts, prizes, trades and encounter tables are different. Nothing in section 2 may be reused here except where a row says the number is equal.

### 8.1 Level caps (Hardcore Nuzlocke)

The eight gyms have the same levels as Red/Blue; the Elite Four (54, 56, 58, 60) and Champion (63) are lower than Red/Blue's, and Giovanni's ace is a Rhyhorn at L50 (Red, Blue and Yellow have a Rhydon).

| # | Battle | Location | Cap (highest level) | Ace / top Pokemon | Sources |
|---|---|---|---|---|---|
| 1 | Brock | Pewter City (Pewter Gym) | 14 | Onix | P B N D S |
| 2 | Misty | Cerulean City (Cerulean Gym) | 21 | Starmie | P B N D S |
| 3 | Lt. Surge | Vermilion City (Vermilion Gym) | 24 | Raichu | P B N D S |
| 4 | Erika | Celadon City (Celadon Gym) | 29 | Victreebel / Vileplume | P B N D S |
| 5 | Koga | Fuchsia City (Fuchsia Gym) | 43 | Weezing | P B N D S |
| 6 | Sabrina | Saffron City (Saffron Gym) | 43 | Alakazam | P B N D S |
| 7 | Blaine | Cinnabar Island (Cinnabar Gym) | 47 | Arcanine | P B N D S |
| 8 | Giovanni | Viridian City (Viridian Gym) | 50 | Rhyhorn | P B N D S |
| E4-1 | Lorelei | Indigo Plateau | 54 | Jynx / Lapras | P B N D S |
| E4-2 | Bruno | Indigo Plateau | 56 | Machamp | P B N D S |
| E4-3 | Agatha | Indigo Plateau | 58 | Gengar | P B N D S |
| E4-4 | Lance | Indigo Plateau | 60 | Dragonite | P B N D S |
| C | Champion (Blue, your rival) | Indigo Plateau | 63 | Blastoise / Venusaur / Charizard (his starter's final form) | P B N D S |

Sources: P https://raw.githubusercontent.com/pret/pokefirered/master/src/data/trainers.h and https://raw.githubusercontent.com/pret/pokefirered/master/src/data/trainer_parties.h (TRAINER_LEADER_*, TRAINER_ELITE_FOUR_* first entries, TRAINER_CHAMPION_FIRST_*) ; B one page per trainer (FRLG block, first-run entry listed first), for example https://bulbapedia.bulbagarden.net/wiki/Giovanni , https://bulbapedia.bulbagarden.net/wiki/Agatha , https://bulbapedia.bulbagarden.net/wiki/Blue_(game) ; N https://nuzlockeuniversity.ca/2022/01/18/hardcore-nuzlocke-level-caps-by-generation/ (section "Pokemon FireRed/LeafGreen") ; D https://github.com/domtronn/nuzlocke.data/blob/main/leagues/frlg.txt ; S https://www.serebii.net/fireredleafgreen/gyms.shtml , https://www.serebii.net/fireredleafgreen/elitefour.shtml (same numbers as P and B)

Rematches are excluded: after the Sevii Islands mission the Elite Four return at 66, 68, 70, 72 (TRAINER_ELITE_FOUR_*_2) and Blue at 75 (TRAINER_CHAMPION_REMATCH_*).

Other cap points (optional caps; NU lists none of these):

| Battle | Location | Cap | Where it falls in a normal run (unconfirmed, Bulbapedia order only) | Sources |
|---|---|---|---|---|
| Rival, Professor Oak's Lab | Pallet Town | 5 | Start of game (Slow Start / Starter clauses normally skip it) | P B D S |
| Rival, Route 22 (first) | Route 22 | 9 | Right after Viridian City, before Viridian Forest and Gym 1 (walk-on trigger; Bulbapedia calls the Yellow fight optional) | P B D S |
| Rival, Cerulean City | Cerulean City | 18 | Between Gym 1 and Gym 3 | P B D S |
| Rival, S.S. Anne | S.S. Anne 2F | 20 | Just before Gym 3 (Lt. Surge) | P B D S |
| Giovanni, Rocket Hideout | Rocket Hideout B4F (Celadon City) | 29 | Around Gym 4 (Erika) | P B D |
| Rival, Pokemon Tower | Pokemon Tower 2F (Lavender Town) | 25 | Around Gym 4-5, after the Rocket Hideout | P B D S |
| Rival, Silph Co. | Silph Co. 7F (Saffron City) | 40 | Around Gym 6 (Sabrina) | P B D S |
| Giovanni, Silph Co. | Silph Co. 11F (Saffron City) | 41 | Around Gym 6 (Sabrina) | P B D |
| Rival, Route 22 (last before the League) | Route 22 (below Victory Road) | 53 | After all 8 badges, before Victory Road | P B D S |

Sources: P pokefirered trainer table (TRAINER_RIVAL_OAKS_LAB_*, _ROUTE22_EARLY_*, _CERULEAN_*, _SS_ANNE_*, _POKEMON_TOWER_*, _SILPH_*, _ROUTE22_LATE_*, TRAINER_BOSS_GIOVANNI, TRAINER_BOSS_GIOVANNI_2; three starter variants each with the same top level) ; B https://bulbapedia.bulbagarden.net/wiki/Walkthrough:Pok%C3%A9mon_FireRed_and_LeafGreen/Part_1 to Part_20 (each fight with its location; FireRed and LeafGreen share these numbers) ; D `leagues/frlg.txt` (r1 to r7, g1, g2). Differences from Red/Blue: at Cerulean the cap is 18 in both, but in Red/Blue the top Pokemon is Pidgeotto (18, with Abra 15) while in FireRed/LeafGreen it is his starter (18, with Pidgeotto 17 and Abra 16); the Route 22 late fight has Alakazam 47 (Red/Blue 50) and the same 53 cap; the Champion is 63, not 65. S https://www.serebii.net/fireredleafgreen/rival.shtml (same rival numbers).

Order and flexibility: Bulbapedia's Gym Badge page (Trivia): "In Generation I and III, the Thunder Badge, Rainbow Badge, Soul Badge, Marsh Badge, and Volcano Badge can be obtained in almost any order ... with the stipulation that the Soul Badge must be obtained before the Volcano Badge", because Surf is needed to reach Cinnabar. So FireRed/LeafGreen gyms 3 to 7 (24, 29, 43, 43, 47) have no fixed sequence. https://bulbapedia.bulbagarden.net/wiki/Gym_Badge

### 8.2 Rulings

#### 8.2.1 Gift, purchased and prize Pokemon (FireRed and LeafGreen)

| Pokemon | Where and how | Level | Suggested default (unconfirmed proposal) | Src |
|---|---|---|---|---|
| Bulbasaur, Charmander or Squirtle (choose one) | Professor Oak's Laboratory, Pallet Town | 5 | Free (starter) | P B A S |
| Eevee | Celadon Mansion roof, Poke Ball beside an unnamed trainer | 25 | Free (gift) | P B A S |
| Hitmonlee or Hitmonchan (choose one) | Fighting Dojo, Saffron City, from Koichi after beating him | 25 | Free (gift) | P B A S |
| Lapras | Silph Co. 7F, after beating Blue there | 25 | Free (gift) | P B A S |
| Omanyte (Helix Fossil) or Kabuto (Dome Fossil), choose one | Super Nerd Miguel at the end of Mt. Moon; revived at Cinnabar Lab | 5 | Free (gift) | P B A S |
| Aerodactyl (Old Amber) | Pewter Museum of Science back room (needs Cut); revived at Cinnabar Lab | 5 | Free (gift) | P B A S |
| Magikarp | Salesman in the Route 4 Pokemon Center, $500 | 5 | Free (purchase) | P B A |
| Togepi (Egg) | A Gentleman at the end of the Water Labyrinth (Five Island, Sevii Islands). Two conditions in pret: the first Pokemon in your party must be at the top friendship tier and the party must have a free slot (Serebii lists only the slot) | 5 at hatching | Free (gift egg), post-game | P B A S |
| FireRed: Abra L9 (180 coins), Clefairy L8 (500), Dratini L18 (2800), Scyther L25 (5500), Porygon L26 (9999) | Celadon City Game Corner prize counter | see row | Free (prize); "one prize only" switch | P B A |
| LeafGreen: Abra L7 (120), Clefairy L12 (750), Pinsir L18 (2500), Dratini L24 (4600), Porygon L18 (6500) | same counter | see row | Free (prize); "one prize only" switch | P B A |

Sources: https://bulbapedia.bulbagarden.net/wiki/Gift_Pok%C3%A9mon (Generation III, FireRed and LeafGreen) , https://bulbapedia.bulbagarden.net/wiki/List_of_in-game_event_Pok%C3%A9mon_in_Pok%C3%A9mon_FireRed_and_LeafGreen , https://bulbapedia.bulbagarden.net/wiki/Celadon_Game_Corner (Generation III) ; A rows with method gift and gift-egg for version_id 10 (FireRed) and 11 (LeafGreen). Levels for Hitmon, Lapras and the fossils differ from Red/Blue (25, 25, 5 instead of 30, 15, 30). P (https://raw.githubusercontent.com/pret/pokefirered/master/): `data/maps/PalletTown_ProfessorOaksLab/scripts.inc` (starter L5), `data/maps/CeladonCity_Condominiums_RoofRoom/scripts.inc` (Eevee L25), `data/maps/SaffronCity_Dojo/scripts.inc` (Hitmon L25), `data/maps/SilphCo_7F/scripts.inc` (Lapras L25), `data/maps/CinnabarIsland_PokemonLab_ExperimentRoom/scripts.inc` (fossil Pokemon L5, Old Amber included), `data/maps/Route4_PokemonCenter_1F/scripts.inc` (Magikarp L5, `MAGIKARP_PRICE` 500), `data/maps/FiveIsland_WaterLabyrinth/scripts.inc` (Togepi Egg), `data/maps/CeladonCity_GameCorner_PrizeRoom/scripts.inc` (every prize, price and level in the two prize rows). S https://www.serebii.net/fireredleafgreen/gift.shtml (all gifts above); Serebii's FireRed/LeafGreen Game Corner page lists no prizes.

#### 8.2.2 In-game trades (FireRed and LeafGreen: nine)

| Where | FireRed: give / get | LeafGreen: give / get | Src |
|---|---|---|---|
| Route 2 (gatehouse) | Abra / Mr. Mime | Abra / Mr. Mime | P B A S |
| Underground Path (Routes 5-6) | Nidoran (male) / Nidoran (female) | Nidoran (female) / Nidoran (male) | P B A S |
| Route 11 | Nidorino / Nidorina | Nidorina / Nidorino | P B A S |
| Route 18 | Golduck / Lickitung | Slowbro / Lickitung | P B A S |
| Cerulean City | Poliwhirl / Jynx | Poliwhirl / Jynx | P B A S |
| Vermilion City | Spearow / Farfetch'd | Spearow / Farfetch'd | P B A S |
| Cinnabar Lab | Raichu / Electrode | Raichu / Electrode | P B A S |
| Cinnabar Lab | Venonat / Tangela | Venonat / Tangela | P B A S |
| Cinnabar Lab | Ponyta / Seel | Ponyta / Seel | P B A S |

Suggested default: free. Sources: https://bulbapedia.bulbagarden.net/wiki/List_of_in-game_trades (Generation III, FireRed and LeafGreen) ; A rows with method npc-trade for version_id 10 and 11; https://bulbapedia.bulbagarden.net/wiki/Pok%C3%A9mon_FireRed_and_LeafGreen_Versions (version differences: the Nidoran and Lickitung trades). P pokefirered `src/data/ingame_trades.h` (all nine, with the `#if defined(FIRERED)` and `LEAFGREEN` branches for the Nidoran, Nidorino/Nidorina and Lickitung trades). S https://www.serebii.net/fireredleafgreen/trades.shtml (the same nine).

#### 8.2.3 Static, roaming and one-off encounters (FireRed and LeafGreen)

| Encounter | Where | Level | Notes | Suggested default (unconfirmed proposal) | Src |
|---|---|---|---|---|---|
| Snorlax (two) | Route 12 and Route 16 | 30 | Poke Flute; one must be caught or beaten to reach Fuchsia | Free (static) | P B A |
| Zapdos | Power Plant | 50 | Legendary | Free; flag for the legendary ban | P B A S |
| Electrode x2 | Power Plant (item-ball lookalikes; Red/Blue have eight Voltorb and Electrode here) | 34 | pokefirered `data/maps/PowerPlant/scripts.inc` has two `setwildbattle SPECIES_ELECTRODE, 34` and one Zapdos | Free (static) | P B A |
| Articuno | Seafoam Islands B4F | 50 | Legendary | Free; flag for the legendary ban | P B A S |
| Moltres | Mt. Ember summit (One Island, Sevii Islands) | 50 | Moved from Victory Road; One Island opens right after Blaine (Tri-Pass) | Optional mid-game static; flag for the legendary ban | P B A S |
| Hypno | Berry Forest (Three Island, Sevii Islands) | 30 | Attacks Lostelle; catch or defeat it; Three Island opens right after Blaine (Tri-Pass) | Optional mid-game static | P B A |
| Ghost Marowak | Pokemon Tower 6F | 30 | Fought only with the Silph Scope; it can never be caught (pret `StartMarowakBattle` in `src/battle_setup.c` and the `BATTLE_TYPE_GHOST` check in `Cmd_handleballthrow`) | Not an encounter | P |
| Mewtwo | Cerulean Cave B1F | 70 | Needs Surf, Strength and Rock Smash inside. The cave opens at the end of a long chain: the National Pokedex, then Celio asks for the Ruby (Mt. Ember) and gives the Rainbow Pass, the Sapphire comes from the Five Island Rocket Warehouse, and once Celio finishes the Network Machine the Cerulean City guard leaves (pret `OneIsland_PokemonCenter_1F/scripts.inc`) | Post-game; flag for the legendary ban | P B A S |
| Entei, Suicune or Raikou | Roams Kanto once the Network Machine is finished (the Sapphire from the Five Island Rocket Warehouse handed to Celio; pret starts the roamer with `special InitRoamer` in that same script): Entei if you chose Bulbasaur, Suicune for Charmander, Raikou for Squirtle | 50 | Roaming Roar bug in the original cartridges makes Raikou and Entei vanish if they Roar away (Serebii says the same) | Post-game roamer; flag for the legendary ban | P B A S |
| Lugia and Ho-Oh, Deoxys | Navel Rock, Birth Island | 70, 30 | Need MysticTicket and AuroraTicket, event-only on GBA cartridges (the 2026 Nintendo Switch release hands out both after the Hall of Fame, per Bulbapedia) | Exclude | B A S |

Sources: https://bulbapedia.bulbagarden.net/wiki/List_of_in-game_event_Pok%C3%A9mon_in_Pok%C3%A9mon_FireRed_and_LeafGreen ; A rows with method static, pokeflute and roaming-grass for version_id 10 and 11. Also P pokefirered `data/maps/Route12/scripts.inc` and `Route16/scripts.inc` (Snorlax L30), `PowerPlant/scripts.inc` (Zapdos L50, two Electrode L34), `SeafoamIslands_B4F`, `MtEmber_Summit` (Moltres L50), `ThreeIsland_BerryForest` (Hypno L30), `CeruleanCave_B1F` (Mewtwo L70) and `PokemonTower_6F` (Marowak L30). S https://www.serebii.net/fireredleafgreen/legendary.shtml (the three birds at L50 with Moltres on Mt. Ember, Mewtwo L70, Lugia and Ho-Oh L70 on Navel Rock, Deoxys L30 on Birth Island, and the starter-dependent roaming dog at L50).

#### 8.2.4 Special areas and handling (FireRed and LeafGreen)

| Area | Fact | Suggested handling (unconfirmed proposal) |
|---|---|---|
| Safari Zone | Same four sectors as Red/Blue (PokeAPI: middle, Area 1 east, Area 2 north, Area 3 west); different tables and a changed catch system; the Secret House still gives Surf (unconfirmed, Bulbapedia only) | Switch: per sector (Bulbapedia "On Safari") or the whole Zone |
| Multi-floor caves and towers | Mt. Moon (3 floors), Rock Tunnel, Seafoam Islands (5), Victory Road (3), Pokemon Tower, Pokemon Mansion (4), Diglett's Cave, Cerulean Cave; PokeAPI has 139 areas over 83 locations because the Sevii Islands are included | One area per location, per-floor switch |
| Sevii Islands (One to Seven Island) | One, Two and Three Island open right after Blaine (a Tri-Pass from Celio); Four to Seven need the Rainbow Pass, which Celio swaps for the Tri-Pass once the National Pokedex is unlocked and you bring him the Ruby (pret `OneIsland_PokemonCenter_1F/scripts.inc`; Bulbapedia says after the Hall of Fame). They hold Moltres (One Island), Hypno (Three Island), the Togepi Egg (Five Island), Altering Cave (Outcast Island) and many extra areas | Separate section of the list: islands 1 to 3 optional mid-game, islands 4 to 7 post-game |
| Rock Smash | Rock Smash encounters exist in Cerulean Cave (three floors), Kindle Road, Mt. Ember (several floors; Slugma and Magcargo on B3F, Geodude and Graveler elsewhere), Rock Tunnel B1F and Sevault Canyon (PokeAPI: 65 rows) | Part of the area or extra type |
| Towns and routes with water | Same town-water areas as Red/Blue | Own areas |
| Old Rod, Good Rod, Super Rod | Old Rod: Magikarp in both of its slots. Good Rod: Magikarp, Horsea, Goldeen, Poliwag or Krabby across 3 slots (60% / 20% / 20%). Super Rod: 5 slots (40% / 40% / 15% / 4% / 1%). Every water area has its own table (pret `src/data/wild_encounters.json`; PokeAPI for the species) | As Red/Blue |
| Pokemon Tower (ghosts) | As in Red/Blue: without the Silph Scope every wild battle in the tower is an uncatchable Ghost (pret `CheckSilphScopeInPokemonTower` and `DoGhostBattle` in `src/battle_setup.c`); the ghost Marowak cannot be caught even with the Scope | The tower's encounter is only usable after you own the Scope (Rocket Hideout B4F, `giveitem ITEM_SILPH_SCOPE`). The ghost fights are not encounters |
| Mankey, Meowth | Available in both games in FRLG (they were Red-only and Blue-only in Gen 1); Psyduck, Shellder, Slowpoke and Staryu lines became version exclusives | Do not reuse Red/Blue tables |

Sources: https://bulbapedia.bulbagarden.net/wiki/Kanto_Safari_Zone ; https://bulbapedia.bulbagarden.net/wiki/Pok%C3%A9mon_FireRed_and_LeafGreen_Versions ; A `location_areas.csv` and `encounters.csv` for version_id 10 and 11 ; the Nuzlocke Tracker's `fr` and `lg` data lists 52 areas (Kanto plus one entry per Sevii island). P pokefirered `src/battle_setup.c`, `src/battle_script_commands.c` (Cmd_handleballthrow), `src/data/wild_encounters.json`, `data/maps/RocketHideout_B4F/scripts.inc`. Safari Zone: pokefirered `src/safari_zone.c` (30 Safari Balls).

#### 8.2.5 Scripted encounters that must not count (FireRed and LeafGreen)

- **Old Man, Viridian City:** unskippable in FireRed and LeafGreen. He has one Poke Ball and catches a Weedle (L5); he then gives you the Teachy TV. Bulbapedia https://bulbapedia.bulbagarden.net/wiki/Old_man_(Kanto) ; pokefirered `src/battle_setup.c` (`StartOldManTutorialBattle` creates the Weedle L5). Not an encounter.
- **Rival battle in Oak's Lab:** not an encounter.

#### 8.2.6 Version-exclusive Pokemon (FireRed versus LeafGreen)

Bulbapedia's list (not obtainable in the other game without trading): FireRed has Ekans, Arbok, Oddish, Gloom, Vileplume, Psyduck, Golduck, Growlithe, Arcanine, Shellder, Cloyster, Scyther, Electabuzz, Bellossom, Wooper, Quagsire, Murkrow, Qwilfish, Scizor, Delibird, Skarmory, Elekid and Deoxys (Attack). LeafGreen has Sandshrew, Sandslash, Vulpix, Ninetales, Bellsprout, Weepinbell, Victreebel, Slowpoke, Slowbro, Staryu, Starmie, Magmar, Pinsir, Marill, Azumarill, Slowking, Misdreavus, Sneasel, Remoraid, Octillery, Mantine, Magby, Azurill and Deoxys (Defense). Prize and trade differences are in 8.2.1 and 8.2.2. PokeAPI's wild-table diff agrees in direction (FireRed: Ekans, Arbok, Oddish, Gloom, Growlithe, Scyther, Electabuzz, Psyduck, Golduck, Shellder, Wooper, Murkrow, Qwilfish, Seadra, Delibird, Skarmory, Weezing; LeafGreen: Bellsprout, Weepinbell, Sandshrew, Sandslash, Vulpix, Magmar, Pinsir, Slowpoke, Slowbro, Staryu, Kingler, Marill, Misdreavus, Sneasel, Remoraid, Mantine, Muk). Ship separate tables. Sources: https://bulbapedia.bulbagarden.net/wiki/Pok%C3%A9mon_FireRed_and_LeafGreen_Versions (Version-exclusive Pokemon) ; A version_id 10 versus 11.

### 8.3 Where the encounter-area list can come from (FireRed and LeafGreen)

| Source | What it gives for FireRed/LeafGreen | Licence |
|---|---|---|
| PokeAPI | 139 location areas with encounters across 83 locations for each version (Kanto and Sevii Islands); methods walk, surf, rods, rock-smash, gift, gift-egg, static, pokeflute, roaming-grass, npc-trade | BSD-3-Clause |
| Nuzlocke Tracker data (`routes.json`, keys `fr`, `lg`) | 52 areas in story order with 22 boss markers | BSD-3-Clause in the app repository; data repository has no licence |
| pret pokefirered | `src/data/wild_encounters.json`: 124 distinct maps (264 header entries, one per FireRed or LeafGreen variant); `src/data/region_map/region_map_sections.json` | No licence file (not shippable) |
| besteon/Ironmon-Tracker | `ironmon_tracker/data/RouteData.lua` (FireRed/LeafGreen branch, `setupRouteInfoAsFRLG`, with merged multi-floor areas such as Mt. Moon, Victory Road and the Rocket Hideout) | MIT (section 9.5) |
| Bulbapedia | https://bulbapedia.bulbagarden.net/wiki/List_of_locations_by_index_number_(Generation_III) and route pages | CC BY-NC-SA 2.5 (not shippable) |

Details and caveats: section 9.

## 9. Data sources for an encounter-area list

Everything here was checked on 2026-09-29. Licences were read from the repositories (GitHub reports the SPDX id from each LICENSE file) and from each site's own copyright statement. GPL-compatibility statements come from the GNU project's licence list, https://www.gnu.org/licenses/license-list.html . This is an engineering reading of those pages, not legal advice.

### 9.1 Summary

| Source | What it holds for the eleven games | Licence | Can a GPL-3.0 app ship it? |
|---|---|---|---|
| PokeAPI, CSV files in https://github.com/PokeAPI/pokeapi/tree/master/data/v2/csv | Locations, location areas (one per floor or room), every encounter row with method, level range, rate and conditions, for all eleven games | BSD-3-Clause | Yes, with the licence text and copyright notice kept. The GNU list calls the modified BSD licence "compatible with the GNU GPL" |
| Nuzlocke Tracker app repository, https://github.com/domtronn/nuzlocke.app , files `src/lib/data/routes.json` and `src/lib/data/league.json` | A Nuzlocke-ordered area list per game with boss markers and per-area species lists; boss teams | BSD-3-Clause (LICENSE names Diego Ballesteros, 2021) | Yes with the notice, but the Gen 2 Kanto species are wrong (9.4) and the separate data repository is unlicensed and the origin of the species lists is not stated (section 10) |
| Nuzlocke Tracker data repository, https://github.com/domtronn/nuzlocke.data (`routes/*.txt`, `leagues/*.txt`) | Area names in order, boss teams | None (GitHub shows no licence) | No |
| pret disassemblies (seven repositories, 9.3) | The games' own wild tables, map names, trainer parties, gift and trade scripts | None (no LICENSE or COPYING file and no licence text in any README) | Not as copied files. Reading the numbers to verify a derived table, or reading the tables from the player's own ROM at run time, needs no licence |
| besteon/Ironmon-Tracker `ironmon_tracker/data/RouteData.lua` | Gen 3 only: named routes keyed by map layout id with encounter rates and levels for Ruby, Sapphire, Emerald, FireRed, LeafGreen | MIT (the GNU list's Expat, also GPL-compatible) | Yes with the notice; the IronMonOne NOTICE already credits this repository |
| IronMON Gen 1 and Gen 2 forks | Gen 2: 95 location names only. Gen 1: no route data | MIT | Names only; nothing worth shipping |
| Bulbapedia | Location lists and route pages for every game | CC BY-NC-SA 2.5 (Bulbapedia:Copyrights) | No. The GNU list says CC NonCommercial licences "do not qualify as free" because of the limits on charging for copies, and ShareAlike cannot be met by GPL code |
| Serebii.net | Per-game database pages | "All Content is (c) Copyright of Serebii.net" (every page footer) | No |
| Nuzlocke University | Level cap page, rules pages | No licence statement seen | No; the caps in this report are computed from the disassemblies and only cross-checked against it |

The recommended pipeline is in 9.7.

### 9.2 PokeAPI

- **Where.** The CSV files live under https://github.com/PokeAPI/pokeapi/tree/master/data/v2/csv ; raw files are at https://raw.githubusercontent.com/PokeAPI/pokeapi/master/data/v2/csv/<name>.csv . API documentation and fair-use policy: https://pokeapi.co/docs/v2 ("locally cache resources whenever you request them"; static hosting since 2018, no rate limit). Bundling the CSV files avoids the API altogether.
- **Licence.** BSD-3-Clause (GitHub API `license.spdx_id`, checked 2026-09-29; the repository was pushed to that day). The docs add that Pokemon names are trademarks of Nintendo.
- **Files and what they hold.** `versions.csv` (ids: red 1, blue 2, yellow 3, gold 4, silver 5, crystal 6, ruby 7, sapphire 8, emerald 9, firered 10, leafgreen 11), `locations.csv` (with region), `location_names.csv` (use language 9 for English), `location_areas.csv` (one row per floor or room, with `location_id`), `location_area_prose.csv` (area labels), `encounters.csv` (3.2 MB, about 117,000 rows for all generations; each row has version, area, slot, Pokemon, min and max level), `encounter_slots.csv` (method, slot, rarity), `encounter_methods.csv` (67 methods), `encounter_condition_values.csv` and `encounter_condition_value_map.csv` (conditions per row), `pokemon.csv` (identifiers).
- **Size per game** (rows, areas that have encounters, locations that have encounters):

| Game | Rows | Areas | Locations |
|---|---|---|---|
| Red and Blue (each) | 891 | 74 | 45 |
| Yellow | 877 | 74 | 45 |
| Gold and Silver (each) | 2820 | 124 | 84 |
| Crystal | 3183 | 126 | 85 |
| Ruby | 1530 | 103 | 68 |
| Sapphire | 1527 | 104 | 69 |
| Emerald | 1647 | 117 | 81 |
| FireRed and LeafGreen (each) | 2117 | 139 | 83 |

- **Methods relevant to a Nuzlocke rules engine.** Wild: `walk`, `surf`, `old-rod`, `good-rod`, `super-rod`, `rock-smash`, `seaweed`, `headbutt-low`, `headbutt-normal`, `headbutt-high`, `feebas-tile-fishing`. Special: `gift`, `gift-egg`, `static`, `npc-trade`, `pokeflute`, `squirt-bottle`, `wailmer-pail`, `devon-scope`, `roaming-grass`, `roaming-water`. Event-only, to filter out: `colosseum-bonus-disc-us`, `colosseum-bonus-disc-jpn`, `pokemon-channel-pal`, and rows carrying the condition `other-virtual-console`.
- **Conditions in these eleven games.** `swarm-yes` and `swarm-no`; `time-morning`, `time-day`, `time-night`; `weekday-friday` (Union Cave Lapras); `coins-<price>` (25 values, the Game Corner prizes); `trade-<species>` (33 values, the species the NPC wants); `item-<fossil>` (five values); `starter-bulbasaur`, `starter-charmander`, `starter-squirtle` (the FireRed/LeafGreen roaming dog); `tv-option-red` and `tv-option-blue` (Emerald's Latias or Latios); `first-party-pokemon-high-friendship` (FireRed/LeafGreen Togepi Egg); `story-progress-awakened-beasts`, `story-progress-hall-of-fame` and `story-progress-beat-elite-four-round-two`.
- **What it does not have.** No story order, no boss battles, no Nuzlocke area concept. Floors are separate areas, so group `location_areas.csv` by `location_id` to get one area per location (that reproduces the counts above: 45 for Red, 84 for Gold). Gen 2 tables carry three rows for every grass slot (morning, day, night). The English area labels in `location_area_prose.csv` read "Road 111" where the location name reads "Route 111", so take display names from `location_names.csv`.
- **Errors found against the disassemblies** (also in 0.4): Red, Blue, Yellow, FireRed and LeafGreen list the Magikarp salesman at both the Route 3 and Route 4 Pokemon Centers (only Route 4 exists); Ruby, Sapphire and Emerald overcount the New Mauville Voltorb and Electrode, and Emerald lists Electrode in the Magma Hideout; the Gen 2 Rock Smash rows omit Slowpoke Well B1F; PokeAPI calls the Tin Tower "Bell Tower" and files the Kenya gift under "Goldenrod City (north gate)"; Emerald files the Johto starters under Littleroot Town without the Hoenn Pokedex condition. Nothing else was found, but no complete audit of the 117,000 rows was done (section 10).

### 9.3 pret disassemblies

All seven repositories were read at their default branch on 2026-09-29. **None has a LICENSE or COPYING file and no README mentions a licence**, so nothing in them can be copied into a GPL-3.0 app on the strength of a licence.

| Game | Repository | Wild data | Area names | Size |
|---|---|---|---|---|
| Red, Blue | https://github.com/pret/pokered | `data/wild/maps/*.asm`, `data/wild/grass_water.asm`, `good_rod.asm`, `super_rod.asm` | `data/maps/names.asm` | 59 wild map files |
| Yellow | https://github.com/pret/pokeyellow | same paths | same | 60 wild map files |
| Gold, Silver | https://github.com/pret/pokegold | `data/wild/johto_grass.asm`, `johto_water.asm`, `kanto_grass.asm`, `kanto_water.asm`, `swarm_grass.asm`, `swarm_water.asm`, `fish.asm`, `treemons.asm`, `treemon_maps.asm`, `bug_contest_mons.asm`, `roammon_maps.asm` | `data/maps/landmarks.asm` | 61, 38, 30 and 24 map entries; 95 landmarks |
| Crystal | https://github.com/pret/pokecrystal | same files (plus `treemons_asleep.asm`) | `data/maps/landmarks.asm` | same entry counts; 96 landmarks |
| Ruby, Sapphire | https://github.com/pret/pokeruby | `src/data/wild_encounters.json` | `src/data/region_map/region_map_sections.json` | 194 entries over 97 maps (one entry per Ruby or Sapphire variant) |
| Emerald | https://github.com/pret/pokeemerald | `src/data/wild_encounters.json` | `src/data/region_map/region_map_sections.json` | 124 entries over 116 maps, plus 7 Battle Pyramid and 4 Battle Pike headers |
| FireRed, LeafGreen | https://github.com/pret/pokefirered | `src/data/wild_encounters.json` | `src/data/region_map/region_map_sections.json` | 264 entries over 124 maps (one per version) |

Three ways to use them without shipping them:

1. **Read the tables from the ROM the player loads.** The game data are in the ROM the app already emulates. The IronMON Gen 1 fork does exactly that (`RouteData.readWildPokemonInfoFromMemory` in its `RouteData.lua`), so no wild table has to be bundled. Area names are the text the game itself displays (Gen 1 `names.asm`, Gen 2 landmarks, Gen 3 region map sections).
2. **Use them as a test oracle.** A build-time test can parse the disassemblies and compare them with a shipped PokeAPI-derived table. This report did that comparison for the gift, trade, static and prize rows and found the errors in 9.2. Nothing from pret is distributed.
3. **Ship a compact derived table** (species, levels, rates per map). The numbers are facts about the games rather than pret's text, but the repositories offer no licence for it, so this is the option to clear with a lawyer or skip.

The scripts used for this report were not saved in the repository; each is a short Python parser over the files above.

### 9.4 Nuzlocke Tracker data (domtronn)

- **Repositories.** The web app https://github.com/domtronn/nuzlocke.app (BSD-3-Clause, LICENSE names Diego Ballesteros, 2021; last push 2024-07-24) and the data repository https://github.com/domtronn/nuzlocke.data (no licence, last push 2024-05-12). The app repository holds the compiled files: `src/lib/data/routes.json` (1.78 MB, 65 game keys), `src/lib/data/league.json` (2.4 MB, boss teams), `src/lib/data/games.json` (50 game entries) and `src/lib/data/trainers.json`. The data repository holds the hand-written sources: `routes/<game>.txt` (area names in order, with `--Gym battle|N|leader` marker lines) and `leagues/<game>.txt` (boss teams).
- **Format of `routes.json`.** One key per game, each an ordered list of two kinds of entry: `{"type":"route","name":"Route 1","encounters":["pidgey","rattata"]}` and `{"type":"gym","name":"Pewter City Gym","value":"1","group":"gym-leader","boss":"Brock"}`. The `group` values are `gym-leader`, `rival`, `elite-four` and `evil-team`. Keys for the eleven games: `red`, `blue`, `yel`, `gold`, `silv`, `crys`, `ruby`, `saph`, `em`, `fr`, `lg` (plus the shared `rb`, `gsc`, `frlg`). The first entry of every game is a `Starter` area. The list is in the order a typical run meets the areas, which is the one thing PokeAPI does not have.
- **Size per game** (areas, boss markers):

| Key | Areas | Boss markers | PokeAPI locations with encounters |
|---|---|---|---|
| red, blue | 45 | 22 | 45 |
| yel | 45 | 26 | 45 |
| gold, silv | 84 | 35 | 84 |
| crys | 84 | 35 | 85 |
| ruby | 67 | 25 | 68 |
| saph | 67 | 25 | 69 |
| em | 70 | 29 | 81 |
| fr, lg | 52 | 22 | 83 |

- **Quality check.** Each area was matched by name to the PokeAPI location of the same version and its species set compared (all methods, event-only methods removed; a scripted comparison, so a name that did not match counts as "no match"):

| Key | Areas | Same species set | Tracker lacks species | Tracker has extra species | Both differ | No name match |
|---|---|---|---|---|---|---|
| red | 45 | 29 | 11 | 1 | 0 | 4 |
| blue | 45 | 29 | 11 | 1 | 0 | 4 |
| yel | 45 | 28 | 12 | 1 | 0 | 4 |
| gold | 84 | 31 | 11 | 9 | 30 | 3 |
| silv | 84 | 30 | 12 | 9 | 30 | 3 |
| crys | 84 | 26 | 13 | 11 | 31 | 3 |
| ruby | 67 | 38 | 16 | 9 | 3 | 1 |
| saph | 67 | 39 | 16 | 8 | 3 | 1 |
| em | 70 | 56 | 3 | 10 | 0 | 1 |
| fr | 52 | 33 | 14 | 1 | 0 | 4 |
| lg | 52 | 33 | 14 | 1 | 0 | 4 |

  What the differences are. In Gen 1 and FireRed/LeafGreen the tracker is curated: it attaches gift, prize and trade species to towns (Game Corner prizes on Celadon City, the Nidoran trade on Route 5) and leaves out rod species in some towns and several statics (Moltres on Victory Road in Red). In Ruby, Sapphire and Emerald it adds the Regis and Kecleon to their routes and the starters to Route 101. **In Gold, Silver and Crystal the Kanto species lists are wrong**: in each of the three, 24 of the 36 Kanto areas repeat a Johto area's list exactly (the tracker's Route 6 has Route 36's list with Sudowoodo and Stantler, where pret's `data/wild/kanto_grass.asm` gives Rattata, Snubbull, Magnemite, Raticate, Jigglypuff and Granbull by day; Routes 5 and 25 have Route 45's list; Route 1 has Route 31's; Mt. Moon has Mt. Mortar's). Seven or eight Johto areas also differ from PokeAPI (for example Ruins of Alph, Union Cave, National Park, Olivine City, Route 41). The area names and their order are usable; the species lists are usable for Gen 1, Gen 3 and Johto only after a check.
- **Use.** The order and the boss markers are the reusable part. Red's 22 markers are the 8 gyms, 7 rival fights, 2 Giovanni fights, 4 Elite Four members and the Champion, the same battles as section 2; the marker lists of the other games were counted but not diffed against the cap tables. Take species and levels from PokeAPI instead.

### 9.5 IronMON tracker route data (besteon and forks)

- **Gen 3.** https://github.com/besteon/Ironmon-Tracker (MIT; last push 2026-09-10), file `ironmon_tracker/data/RouteData.lua` (about 5,900 lines, 228 KB). `RouteData.Info[mapLayoutId] = { name, icon, [encounter area] = { { pokemonID, rate, minLv, maxLv }, ... } }`, where the encounter areas are Walking, Surfing, Underwater, Static, RockSmash, Super Rod, Good Rod, Old Rod and Trainer. `setupRouteInfoAsFRLG` holds about 190 named entries and `setupRouteInfoAsRSE` about 200 (Ruby and Sapphire are offset by one map id above 107, Emerald is not). It also flags Pokemon Center healing spots and badge gyms (`RouteData.Locations.CanPCHeal`, `CanObtainBadge`) and merges multi-floor dungeons (`combineRouteAreas`). There is no story order.
- **Gen 1.** https://github.com/mollo010/Ironmon-gen-tracker (MIT). Its `RouteData.lua` still only builds the FireRed/LeafGreen and Ruby/Sapphire/Emerald tables; it reads the wild tables of the loaded Gen 1 ROM from memory at run time.
- **Gen 2.** https://github.com/seadogstingray/Ironmon-gen-2-tracker (MIT). `setupRouteInfoAsGSC` lists 95 location names for Johto and Kanto and no encounter data.
- The IronMonOne `NOTICE` file already lists all three repositories.

### 9.6 Bulbapedia and Serebii

Both are good for reading and cross-checking (this report cites them throughout) and neither can be shipped: Bulbapedia is CC BY-NC-SA (https://bulbapedia.bulbagarden.net/wiki/Bulbapedia:Copyrights , version 2.5 for articles edited after 15 April 2007) and Serebii states copyright on all content. Useful Bulbapedia pages for area lists: https://bulbapedia.bulbagarden.net/wiki/List_of_locations_by_index_number_(Generation_I) , https://bulbapedia.bulbagarden.net/wiki/List_of_locations_by_index_number_(Generation_II) , https://bulbapedia.bulbagarden.net/wiki/List_of_locations_by_index_number_(Generation_III) , https://bulbapedia.bulbagarden.net/wiki/Kanto_Safari_Zone , https://bulbapedia.bulbagarden.net/wiki/Hoenn_Safari_Zone .

### 9.7 Recommended pipeline

1. **Areas.** One Nuzlocke area per PokeAPI location (group `location_areas.csv` by `location_id`), named from `location_names.csv`, with floors as sub-areas behind a per-floor switch. Add the few areas PokeAPI has no encounters for but a run needs (the starter, town water that has only fishing, the Game Corner).
2. **Order and boss markers.** Author a small JSON per game by hand. The tables in sections 2 to 8 give the boss rows (with their caps and sources); the tracker's `routes.json` order (BSD-3-Clause) can seed the area order. Do not copy its Gen 2 Kanto species.
3. **Species, levels and rates.** PokeAPI `encounters.csv` filtered by version, with the methods and conditions in 9.2, and the corrections in 0.4 and 9.2 applied as a patch file. Where the emulated game is running, prefer reading its own wild table from the ROM (9.3, option 1).
4. **Special acquisitions.** Tag every gift, prize, trade, static, roamer and egg from the PokeAPI method or condition and let the ruling switches of section 1.3 act on the tag. The ruling tables in sections 2 to 8 list every case with its level and its source.
5. **Verification.** A build-time test that compares the shipped table with the disassemblies (9.3, option 2) would have caught every PokeAPI error listed here.
6. **Do not ship** pret files, Bulbapedia or Serebii text, or the unlicensed data repository.

## 10. Open gaps and how to close them

Nothing here blocks building the tracker; each item is either a policy call or a check that was not done. Everything else in this file rests on at least two sources.

| # | Gap | Why it is open | How to close it |
|---|---|---|---|
| 1 | Community rulings: do gifts, statics, Safari catches, Game Corner prizes and fished Pokemon count as encounters | No authority rules on them. The r/nuzlocke wiki (login wall), nuzlockeforums.com (403), the Fandom wikis (403) and nuzlocke.app (402) could not be read; only Bulbapedia's Nuzlocke Challenge page, Nuzlocke University, one published FireRed ruleset and the Pokemon Reborn guidelines were (section 1) | Ship every ruling as a switch (1.3) and let Blake pick the defaults; if a consensus source matters, read the r/nuzlocke rules in a logged-in browser |
| 2 | The "suggested default" columns | They are the report author's synthesis from section 1, not a standard | Blake's decision |
| 3 | The "typical place in a run" column of the optional cap tables | Bulbapedia walkthrough order only, one source | Check against the tracker's route order (9.4) or a played run; it does not affect any cap number |
| 4 | Whether the first Route 22 rival fight of Red, Blue, FireRed and LeafGreen can be skipped, and for how long | Bulbapedia calls it optional in Yellow; the Red/Blue window was not traced (2.1) | Read pokered `scripts/Route22.asm` (EVENT_ROUTE22_RIVAL_WANTS_BATTLE) and pokefirered `data/maps/Route22/scripts.inc` |
| 5 | The four Jessie and James rows of Yellow | The disassembly and the community dataset agree; Bulbapedia's party blocks and Serebii do not list them | Bulbapedia's Team Rocket trainer pages or a play-through; low risk because pokeyellow is the game's own data |
| 6 | No Serebii cross-check for the Red/Blue and Gold/Silver caps, and none for the Emerald rival, Wally and villain rows | Serebii has no comparable pages for those games or fights | Optional third source; the caps there already agree between P, B, N and D |
| 7 | Licence of the Nuzlocke Tracker's `routes.json` | The app repository is BSD-3-Clause, the separate data repository has no licence, and the origin of the per-area species lists is not stated | Ask the maintainer in a GitHub issue, or author the area order by hand and take species from PokeAPI (9.7) |
| 8 | Completeness of PokeAPI's wild tables | Only the gift, prize, trade and static rows were audited against the disassemblies (9.2); the roughly 20,000 encounter rows for these eleven games were not | Build the parse-and-compare test described in 9.3 (option 2), which needs the seven disassemblies and one short parser per file layout (Gen 1 assembly, Gen 2 assembly, Gen 3 JSON) |

What was not a gap after checking: the Old Man and Dude tutorial species (all five confirmed in the disassemblies), the Crystal trade list (seven, confirmed by pret, PokeAPI, Bulbapedia and Serebii), every gift, prize and trade row (P plus B plus A, and S where Serebii has the page), the static battles of all seven games (P), the Pokemon Tower ghosts (P), and the Kecleon, Feebas and Mirage Island numbers (P).
