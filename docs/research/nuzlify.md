# Nuzlify: what it does, and what KaizoCore's Nuzlocke modes took from it

Research of 2026-10-06, one pass, read-only: Nuzlify's public pages, its public JavaScript bundles and its public
read-only data API (`nuzlify.com/api/trainers/<game>`, `/api/routes/...`, `/api/versions/...`), its public changelog
repo (github.com/chris-tela/nuzlocke-tracker-public, v1.1 to v2.2), and Bulbapedia's and Nuzlocke University's rules
pages. No account was made, nothing was signed in, submitted or uploaded, so anything behind a login (the live route
screens, save import) is described from the bundle code, not from use. The source repo the site links to answers 404.

## 1. What Nuzlify is

A React web app with a server behind it. The homepage still says "Gen 1-5, 19 games"; its FAQ now lists 36 games, Gen
1 to 8 plus hacks (Unbound, Heart & Soul, Emerald Kaizo, Renegade Platinum, the Redux hacks). It is a **tracker with good
data, not a rules engine**: no Nuzlocke rule is enforced or warned about, and the run's rules are a free-text note.

## 2. Each feature, how it works, and whether to copy it

| Feature | How Nuzlify does it | Best practice to copy? |
|---|---|---|
| Save import | A .sav/.dsv (Gen 1 to 6) parsed on the server: party, boxes, badges, dex; per Pokemon species, nickname, level, moves, nature, ability, IVs and EVs. "Update from save" previews adds and removals. Does not read met location, faint status or shininess. | **No.** KaizoCore reads the live game every poll, which is better: it sees encounters, outcomes and deaths as they happen, which a save cannot. |
| Trainer scouting | Every trainer in the game (Emerald 519, Platinum 630), tagged gym leader, rival, Elite Four, champion, villain, plus an automatic "level spike" flag. Per Pokemon: level, types, ability, held item, nature, IVs, all four moves, battle stats. Sorted by story order, "after gym N" filter hides those passed. Rival teams listed for all three starters with "YOUR FIGHT" on the player's. Vanilla and published-hack data only: randomizers are "coming soon". | **Yes, for bosses, and only on an unrandomized game.** Built (section 4). We read the teams from the loaded ROM instead of a database, and the player's rival team comes first. |
| Route encounters | Ordered route list, each with its encounter table (species, levels, rate per method). "Log encounter" or "Mark as seen"; no failed or fled state. Gifts and statics logged by hand. Dupes: a purple "Owned" tag only. No shiny clause. | We already do more: the first encounter per area, its outcome, dupes, shiny, gifts and statics are read and judged automatically. Encounter tables per route already exist on the tracker for Gen 3 and the early DS areas. |
| Team type coverage | Defensive only: a grid of attacking type by party member, a score per row (+1 weak, -1 resist or immune), and a "top 3 threats" callout. Party only. Uses the modern chart for every game, so Gen 1 (Ghost on Psychic, Bug and Poison) and Steel's old resistances are wrong. | **Yes.** Built, with the game's own chart (Gen 1 rules kept), boxed Pokemon on request, and the party's move coverage, which Nuzlify does not have. |
| Damage calc | Smogon's damage calculator bundled in: 16 rolls, min-max % of HP, OHKO/2HKO labels, who is faster, a full team-against-team grid, crits, stages, weather, screens. **No survival % is ever shown**: "survival chances" in its marketing means the % range and the nHKO label. Accuracy not counted. | **Yes, and further.** Built: the actual chance to live through one hit, every roll and the critical hit counted, in plain words. |
| IV/DV calculator | Stats from the summary screen plus known EVs give a min-max range per stat, rated Great/Decent/OK/Low. Gen 1-2 DVs with HP derived. | Worth having; **not built this round** (Blake's note: only if quick). Our own Pokemon's IVs are the player's, so it is allowed. |
| Badges | A badge timeline, "next gyms" with leader and type. No level-cap numbers. | We already read badges and show the level cap of the next boss. |
| Graveyard and runs | Status Party/Stored/Fainted/Unknown, a fainted list with revive and delete, no cause or date of death. Several runs, guest mode, offline mode. No Soul Link, no export, no run statistics. | We already keep cause, place, badges and time of every death, and an append-only log. |

## 3. The conventions those features serve (Bulbapedia, Nuzlocke University)

- The two core rules: only the first wild encounter in each area may be caught; a fainted Pokemon is dead (released or
  boxed for good). A whiteout ends the run even with Pokemon in the box.
- Near universal: nicknames; check the "met in" place when unsure if an area is new; gifts are often apart from the
  area's wild encounter; no soft resets, no cheats, no outside trades.
- Dupes clause (Bulbapedia names it with the species clause as one rule): an encounter of a line already caught does
  not count. Whether dead or boxed lines count varies by ruleset (our "Dupes: only lines you still own" switch).
- Shiny clause: a shiny may always be caught.
- Optional: Safari Zone sectors as areas, story-split areas (Pinwheel Forest), set mode, no items in battle.
- Hardcore: no items in battle, never above the next boss's ace level, set mode.
- Soul Link: two players' catches paired by area; one dies, both die.

## 4. Gap analysis against KaizoCore (all games: Game Boy, GBA with Heart & Soul, DS)

| Area | KaizoCore before | Nuzlify | Now (this branch) |
|---|---|---|---|
| Encounters by area | Automatic first encounter, outcome, extras (shiny, dupe, static); editable | By hand | Unchanged |
| Dupes, shiny, gifts, statics, slow start, Safari | Automatic, switchable | Owned tag only | Unchanged |
| Deaths and graveyard | Automatic at 0 HP, cause, place, badges, time | By hand, no cause | Unchanged |
| Level caps | Next boss's ace, read from the ROM where it can be | None | Unchanged |
| Rules | 19 switches and presets, warnings, log | Free-text note | Unchanged |
| Data source | Live memory, every poll | Save file, by hand | Unchanged (better) |
| Boss scouting | Trainer Info shows a route trainer's team only on an unrandomized Gen 3 game, one at a time | Every trainer, vanilla only | **BOSSES tab**: every gym leader, Elite Four, Champion and table fight still to come, with levels, types, held items and moves, read from the loaded ROM. Gen 3 and Heart & Soul. Not yet on Game Boy or DS (no team reader there) |
| Fence for hidden data | Tracker rule: never what the randomizer log knows | Does not support randomizers | **In code**: closed for any randomized run, the Randomizer preset, a game whose first two leaders do not field their own aces, or one the tracker cannot judge; the ROM is not even read then (NuzlockeScoutTest) |
| Survival chances | Calc Atk works out the enemy's Attack from the damage it did | % range and nHKO, no survival % | **Chance to survive one hit** for each boss move against each party member (BOSSES, tap a Pokemon), and in Calc Atk for the same hit again at your HP now, from the Attack it worked out (seen information only) |
| Team types | The tracker's weaknesses for one Pokemon, IronMON's coverage screen for one Pokemon's moves | Party grid, defensive, modern chart | **TYPES tab**: each type against the party (or party and box), the types to watch, and the types none of the party's moves hits hard; the game's own chart |
| IV / DV estimate | None shown | Range from stats | Not built (Blake's call to skip unless quick) |
| Save import | Not needed | Yes | Skipped on purpose |

What we do better: live reads (no save to keep in step), automatic rules and warnings, deaths with causes, caps read
from the ROM, the hidden-information fence, the game's own type chart, box-aware coverage, and an actual survival
number. What Nuzlify still has that we do not: every trainer rather than bosses only, the Game Boy and DS teams, IV
ranges, abilities and natures of trainer Pokemon, and who is faster.
