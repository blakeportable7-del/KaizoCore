# Nuzlocke trackers: what exists, what KaizoCore already has, what to build

Research report, 2026-09-29. Question from Blake: "see if a tracker can be adapted for nuzlock".
Read-only: no code was changed, this is the only file written. Star and commit figures come from
the GitHub API on 2026-09-29 and will drift. Everything about KaizoCore comes from reading the
sources in this repo; nothing was run on a device or an emulator.

## 1. Answer

**No existing tracker is worth adapting as code. Extend KaizoCore's own tracker, and borrow data
and rule design from the BSD/MIT web trackers.**

- **IronMON side.** I found no Nuzlocke tracker in that family. The two repos with "Nuzlocke" in
  the name are a renamed mirror with 0 commits ahead of besteon/Ironmon-Tracker
  (`jakobskr/Nuzlocke-Tracker`) and a renamed DS fork whose only rules change is an
  "on whiteout" faint mode (`ElusiveFluffy/NDS-Nuzlocke-Tracker`). KaizoCore already has that
  mode (`LossCondition.ENTIRE_PARTY`, `NdsTracker.runHasEnded`). The PC tracker has no Nuzlocke
  feature: issue 546 (a level cap in the carousel) is open with no reply, and issue 495 (skip to
  a boss) was declined because the tracker does not modify the game. The extension list on the
  wiki (21 entries, checked online and against the local copy, they match) has none; the wider
  extension search found a Soul Link extension and a rules-automation one (both unlicensed).
- **Memory-reading Nuzlocke trackers exist, but none I found is licensed.** Diving-Fish (mGBA,
  party and PC boxes, deaths, area limits), PokeStreamer-Tools (BizHawk/DeSmuME, graveyard, Soul
  Link), Run & Bun Tracker (BizHawk, encounters by route), nuzbridge (an Android app reading
  RetroArch, the closest match to our setup), an IronMON Soul Link extension, Lockeweb and
  others. Each one that tracks Nuzlocke rules has no licence file, so none can be copied into a
  GPL-3.0 app, and they are hobby scale (0 to 9 stars) in Lua/Node/web anyway.
- **The licensed ones are manual-entry web apps or plain readers.** Worth taking: data and rule
  design, not code. nuzlocke.app (BSD-3) ships ordered area lists for 65 games and hacks and 32
  boss-team sets. diballesteros/nuzlocke (BSD-3) ships a clause/rule model. NuzLike
  (GPL-3.0-or-later) ships level-cap presets.
- **One different thing: NuzLike enforces Nuzlocke inside the ROM** (Red, Blue, Yellow, Crystal,
  Emerald, FireRed, LeafGreen; clean backups only; alpha 7, 1 star). It is not a tracker. Park it
  as an optional later "enforced" build, not the first move.
- **KaizoCore is closer than it looks on Gen 3.** The map id, wild/trainer flag, encounter
  method, enemy species/level/PID, party HP and PID, badges, loss conditions, per-run files, an
  integrity ledger and a hardcore-style "no state loads" gate all exist. What is missing is the
  rules layer (areas, first-encounter, outcome, deaths ledger, dupes, caps) and a handful of
  reads that need no new addresses on Gen 3 (section 4.3).
- **The best Gen 3 shortcut:** `gBattleResults`, already mapped in `GameMap.battleResults`,
  holds the faint counter, healing-item and revive counters, the shiny-wild flag and the caught
  species, and `gBattleOutcome` (already mapped) says 7 for "caught". Verified in pret.

| Console | Tier 1: auto areas and auto deaths, outcome and dupes by hand | Tier 2: auto outcome, catches, dupes, caps, violations | Main risk |
|---|---|---|---|
| Gen 3 (FR/LG/R/S/E, NatDex), includes the shared engine, file and screen | 6-8 days | +7-10 days | What counts as "an area", and edge encounters (gifts, statics, roamers) |
| Game Boy (Gen 1 and 2) | +3-4 days | +4-6 days | No route-encounter path today, no unique mon id, new WRAM addresses |
| DS (Gen 4 and 5) | +5-7 days | +10-15 days | No catch, dex or battle-result addresses exist for five game families |

Rough engineer-days for one person who knows the repo, tests included, device waiting time
excluded. Gen 3 alone at Tier 2 is 13-18 days; everything at Tier 2 is 35-50. A useful first
ship is Gen 3 Tier 1.

**Main risk, all consoles:** the rules are player-defined and the edge cases are many. A tracker
that wrongly says "you broke the rules" is worse than one that says nothing. Build it as an
advisory ledger: append-only events, every automatic record editable, "unknown" allowed, nothing
blocked unless the player turns on a strict mode.

**Scope fact worth knowing:** KaizoCore only tracks games it identifies by CRC
(`GameSession.trackerKind`), so Radical Red, Run & Bun, Unbound and the other hacks that most
Nuzlocke players use are not tracked and would need their own address maps. The strong fit is
vanilla, randomized vanilla ("randolocke") and NatDex, which is what the app builds.

## 2. What the rules need, in code terms

Core rules and the usual clauses (nuzlockeguide.com, Wikipedia): only the first wild encounter
in each area may be caught, a fainted Pokemon is permanently out, and every catch gets a
nickname. Common extras: dupes clause, shiny clause, level caps by gym, set mode, no items in
battle, a rule for gifts and static encounters, and a rule for whether a whiteout ends the run.

| Rule | What the code must know | From memory? |
|---|---|---|
| First encounter per area | area key, wild battle start (species, level, method), outcome | yes, outcome is the weak part on DS |
| Permanent death | per-mon faint event, stable mon identity | yes, party only (boxed dead mons are never in the party) |
| Whiteout ends the run | every party mon dead; boxes if the house rule counts them | party yes, boxes not read today |
| Dupes clause | evolution family of the encounter, owned or dead set | family table is missing everywhere |
| Shiny clause | shiny flag of the encounter before capture | yes (Gen 3 has it in `gBattleResults`) |
| Nicknames | nickname of caught mon | visible (Gen 3 decodes it), not enforceable |
| Level caps | party levels, next boss level | levels yes, boss table yes on Gen 3 (ROM), from the log on DS |
| Set mode | the game's battle-style option | Gen 1 to 3 yes (one bit), DS unknown |
| No items in battle | item use during a battle | Gen 3 counters, otherwise bag diffs |
| Gifts, statics, roamers | battle-type flags, or a gift outside battle | Gen 3 flags yes, others by hand |
| Soul Link | two players and a shared server | out of scope |

## 3. Part 1: what exists

### 3.1 How I searched, and the limits

- GitHub repository search (`gh search repos`) for nuzlocke, nuzlocke tracker, soul link, soullink
  tracker, nuzlocke overlay, plus topic pages (nuzlocke, nuzlocke-tracker, soullink, ironmon):
  about 200 distinct repos. The API caps each query at 100 results, so that is a floor.
  Most are personal manual-entry web apps and ROM hacks.
- GitHub code search for "nuzlocke" in Lua (29 repos, most of them mods for other games), and
  for gEnemyParty / gBattleOutcome in Lua (the Gen 3 memory-reading scripts).
- Repo searches for "pokemon mgba lua", "pokemon bizhawk lua tracker", "pokemon desmume lua",
  "IronmonExtension" (37 repos, extensions plus a few data repos, most not on the wiki).
- The IronMON wiki extension table: local copy
  `C:/Users/bepor/ironmon-ref/Ironmon-Tracker.wiki/Tracker-Add-ons.md` and the live page. Same 21
  entries. Also the Custom Code Extension issues (5) and the three Ironmon-Tracker issues that
  mention Nuzlocke (348, 495, 546).
- Web search for BizHawk, mGBA, DeSmuME, melonDS, RetroArch and Android angles. melonDS has no
  scripting in a released build (a Lua pull request exists); the DS trackers run on BizHawk's
  melonDS or DeSmuME cores.
- Not searched: GitLab, Codeberg, SourceForge, files shared only on Discord or Reddit, and store
  apps other than a look at Pokelink's site.

### 3.2 IronMON family

| Project | Licence (GPL-3.0 OK?) | Activity | What it is | Nuzlocke content | Reuse |
|---|---|---|---|---|---|
| [besteon/Ironmon-Tracker](https://github.com/besteon/Ironmon-Tracker) | MIT (yes) | 190 stars, last commit 2026-09-10 | Gen 3 tracker, BizHawk and mGBA, extension API | None built in. The v7.4.0 changelog says its new team view suits Nuzlocke players too | Our parity reference; RouteData and TrainerData already ported |
| [Brian0255/NDS-Ironmon-Tracker](https://github.com/Brian0255/NDS-Ironmon-Tracker) | GPL-3.0 (yes) | 61 stars, 2026-09-27 | DS tracker (Gen 4 and 5) | None | Already ported (`tracker-nds`) |
| [ElusiveFluffy/NDS-Nuzlocke-Tracker](https://github.com/ElusiveFluffy/NDS-Nuzlocke-Tracker) | GPL-3.0 (yes) | 0 stars, 12 commits on the fork, last 2024-02-28 | Renamed fork of the DS tracker | One change: an `ON_WHITEOUT` faint mode (scans the battle party copy for any mon with HP > 0), made the default, and the heals line hidden | Nothing. `LossCondition.ENTIRE_PARTY` and `NdsTracker.runHasEnded` already do it |
| [jakobskr/Nuzlocke-Tracker](https://github.com/jakobskr/Nuzlocke-Tracker) | MIT (yes) | 1 star, 0 commits ahead, 653 behind | Renamed mirror of besteon's tracker | None | None |
| [stableneo/Soullink-Tracker-IronmonExtension](https://github.com/stableneo/Soullink-Tracker-IronmonExtension) | none (no) | 0 stars, 2025-08-22 | Soul Link extension, FireRed and LeafGreen | Link table, ban list of routes and mons, level cap by badge count and by beaten E4 trainer ids, marker when the current route is banned or linked | Idea only. Its gym caps (14,21,24,29,43,43,47,50) are the same as NuzLike's FireRed gym caps |
| [something-smart/Roguemon-IronmonExtension](https://github.com/something-smart/Roguemon-IronmonExtension), [drumstix576/roguemon-releases](https://github.com/drumstix576/roguemon-releases) | none (no) | 4 and 1 stars, 2025-10-16 and 2026-05-20 | Automates the Roguemon ruleset (FireRed NatDex) | HP and status caps, prizes, "segments" (stretches of the game with trainer counts), curses | Idea only; proves rules automation fits the tracker model |
| [jwunderl/EncounterDetails-IronmonExtension](https://github.com/jwunderl/EncounterDetails-IronmonExtension) | MIT (yes) | 0 stars, 2026-09-09, 112 KB Lua | Per-species encounter history and a per-battle action timeline | Encounter log, not rules | Data model ideas; MIT if we ever want code |
| [jciii91/ironmon-encounter-automation](https://github.com/jciii91/ironmon-encounter-automation) | GPL-3.0 (yes) | 0 stars, 2026-01-28 | Automates pivot hunting | Not Nuzlocke | None |
| [WaffleSmacker/SmackerTracker](https://github.com/WaffleSmacker/SmackerTracker-IronmonExtension) | MIT (yes) | 3 stars, 2025-03-20 | Logs seed data to CSV when HP hits 0 | A death log | None |
| [Yascob99/run-and-bun-tracker](https://github.com/Yascob99/run-and-bun-tracker) | none (no) | 2 stars, 2026-07-22 | Run & Bun (Emerald hack) tracker, built on MKDasher's PokemonBizhawkLua | Encounters by route to CSV, dupes pool from evolution families, wild tables read from the ROM (gWildMonHeaders, 20-byte entries), rebuilds past catches from party and boxes but cannot recover missed encounters | Concepts only. The author says the RAM locations may be reused, which is not a licence |
| [Desco1/RnBFragTracker](https://github.com/Desco1/RnBFragTracker) | none (no) | 0 stars, 2025-08-22 | mGBA "frag" and battle log for Run & Bun | KO log; misses passive-damage KOs | None |
| [CyanSMP64/NatDexExtension](https://github.com/CyanSMP64/NatDexExtension) | none, written permission on file (see `NOTICE`) | 13 stars, 2026-06-29 | Nat. Dex support | The hack's wiki names Nuzlocke and Soul Link players as an audience and adds a debug menu for them | Already used |

### 3.3 Emulator-memory trackers outside IronMON

| Project | Licence (GPL-3.0 OK?) | Activity | Games | What it tracks | How | Reuse |
|---|---|---|---|---|---|---|
| [dfoverdx/PokeStreamer-Tools](https://github.com/dfoverdx/PokeStreamer-Tools) (fork of [EverOddish's](https://github.com/EverOddish/PokeStreamer-Tools), archived 2022-01) | none (no) | fork 5 stars, last 2021-05-18; original 35 stars | Gen 3 to 5 (Soul Link: HeartGold and SoulSilver only) | Party slots, graveyard, death sounds, Soul Link links, static encounters by location | Lua in the emulator posts to a Node server that feeds a web page and OBS | Idea only. Its docs admit an area's missed catch cannot be seen from a catch-only reader |
| [Diving-Fish/pokemon-nuzlocke-tracker](https://github.com/Diving-Fish/pokemon-nuzlocke-tracker) | none (no) | 9 stars, 2026-06-29 | Radical Red and FireRed hacks, Run & Bun | Party and PC boxes, death when HP reaches 0 (right-click to override), per-area catch count from each mon's met-location with a default limit of 1, dead-box overlay | mGBA Lua, TCP to Node, web and OBS | Idea: the met-location field (section 4.3) |
| [RadicalDreamer-Code/soullink-tracker](https://github.com/RadicalDreamer-Code/soullink-tracker) | none (no) | 0 stars, 2026-08-24 | FireRed (German) | Catches by route, linked across two players, CSV ledger | BizHawk Lua, Node | None |
| [vngnzz/Lockeweb-Companion](https://github.com/vngnzz/Lockeweb-Companion) | none (no) | 0 stars, 2026-07-05 | Gen 3 (R/S/E/FR/LG) | Team, bag, graveyard, checklist | mGBA Lua and a local server | None |
| [mcollins189/nuzbridge](https://github.com/mcollins189/nuzbridge) | none (no) | 0 stars, 2026-08-22 | Vanilla and hacks, identified by CRC32 | Party, HP, route, battle, PC boxes | Android app reading RetroArch network commands (port 55355), WebSocket to a web tracker | Same setting as KaizoCore, nothing licensed |
| [Vargontoc/pokemon-nuzlocker](https://github.com/Vargontoc/pokemon-nuzlocker) | none (no) | 0 stars, created 2026-09-28 | FireRed via mGBA | Java backend with an mGBA READ/PRESS bridge, level-cap and rule-violation use cases | mGBA Lua socket | None; one day old |
| [Pokelink](https://pokelink.app/) | closed source app (no) | v0.7.1 on its site | Game Boy to Switch, plus Radical Red | Party, levels, items, "Nuzlocke death counter", deaths and encounters | Real-time memory reading | None; no source repo found (its org holds translations and overlay sources) |
| [atendev/pokemon-memory-reader](https://github.com/atendev/pokemon-memory-reader) | GPL-3.0 (yes) | 20 stars, 2026-03-04 | Gen 1 to 3 and Radical Red | Party, player, bag over HTTP; no battle or encounter tracking | BizHawk Lua | Radical Red addresses, if that ever matters |
| [paulthemagno/pokemon-emulator-tracker](https://github.com/paulthemagno/pokemon-emulator-tracker) | MIT (yes) | 1 star, 2026-08-03 | Gen 1 to 3 | Party, PC boxes, dex, badges, map from saves or live mGBA; not rules | Save files and mGBA Lua | PC box decoding, as a reference |
| [hzla/Desmume-Pokemon-Nuzlockers-Edition](https://github.com/hzla/Desmume-Pokemon-Nuzlockers-Edition) | GPL-2.0 (no, and it is a separate emulator) | 3 stars, 2026-08-18 | DS games and hacks (list not stated) | Trainer-battle log ("fragsheet"), party and box sync over HTTP, in-emulator editor | C++ inside DeSmuME | None |
| [EmuLnk](https://github.com/EmuLnk/emulnk) | PolyForm Noncommercial (no) | 267 stars, 2026-04-29 | Many, via emulator forks | Themed dashboards from live memory; the theme schema mentions a hidden `nuzlocke-data` setting, so a Nuzlocke theme probably exists (not inspected) | Android app, UDP to emulator forks | Prior art only |
| [agent0816/PokemonGen1to7OBSTracker](https://github.com/agent0816/PokemonGen1to7OBSTracker) | none (no) | 0 stars, dev branch 2026-09-29 | Gen 1 to 7 | Team overlay over OBS-WebSocket | Python and Lua | None |
| [kodiakbermun2/nuzlocke-layout](https://github.com/kodiakbermun2/nuzlocke-layout) | none (no) | 0 stars, 2026-05-29 | FireRed, Radical Red | Overlay driven by the save file | Save parser | None |
| [Fracture17/NuzlockeSolver](https://github.com/Fracture17/NuzlockeSolver) | MIT (yes) | 0 stars, 2026-09-17 | Emerald | Autopilot that plays battles; reads party and battle state from an embedded mGBA | Python, cffi | Not a tracker; its Gen 3 struct code is a cross-check only |

### 3.4 Web and desktop trackers (manual entry)

| Project | Licence (GPL-3.0 OK?) | Activity | Scope | Notes | Reuse |
|---|---|---|---|---|---|
| [domtronn/nuzlocke.app](https://github.com/domtronn/nuzlocke.app) | BSD-3 (yes; LICENSE names Diego Ballesteros) | 164 stars; last commit to main 2023-05-27 | 65 game and hack keys, SvelteKit | Encounter log, graveyard, box, boss teams and level caps, patches for hacks | **Data:** `src/lib/data/routes.json` (1.7 MB) and `league.json` (2.4 MB) |
| [domtronn/nuzlocke.data](https://github.com/domtronn/nuzlocke.data) | no licence (no) | 10 stars, 2023-05-27 | The same data as text files | | Ask the author, or use the copy in the app repo |
| [diballesteros/nuzlocke](https://github.com/diballesteros/nuzlocke) | BSD-3 (yes) | 26 stars, 2025-07-29 | Gen 1 to 8 and custom games | Detailed encounters, level caps for base games, "smart rules" (allow only these types or generations, maximum level, custom text) that alert when broken, dupes alerts, export and import, damage calc, Soul Link | **Rule model** |
| [Ashenfactory/nuzlocke-tracker](https://github.com/Ashenfactory/nuzlocke-tracker) | MIT (yes) | 24 stars, last commit 2023-01-10 | Up to Scarlet and Violet | Ordered route tracker | None |
| [jynnie/soullocke](https://github.com/jynnie/soullocke) | MIT (yes) | 16 stars, 2026-08-20 | Soul Link | Two-player linking | None |
| [emzinnia/nuzlocke-generator](https://github.com/emzinnia/nuzlocke-generator) | none (no) | 31 stars, 2026-09-10 | Run report images | | None |
| [TeamLumi/LumiPlat_NuzlockeTracker](https://github.com/TeamLumi/LumiPlat_NuzlockeTracker) | BSD-3 (yes) | 1 star, 2025-07-11 | One hack | | None |
| [joos-too/Soullink-Tracker](https://github.com/joos-too/Soullink-Tracker), [atillatheboss/soullinktracker](https://github.com/atillatheboss/soullinktracker) | MIT (yes) | 3 and 1 stars, 2026 | Soul Link, 1 to 3 players | | None |
| [LarsUphoff/nuzlocke-tracker](https://github.com/LarsUphoff/nuzlocke-tracker) | MIT (yes) | 2 stars, 2025-08-16 | HeartGold and SoulSilver, Django web app | | None |

Desktop, mobile and overlay apps (all manual entry unless noted):

| Project | Licence (GPL-3.0 OK?) | Activity | Scope | Notes |
|---|---|---|---|---|
| [T1nyTim/NuzlockeTool](https://github.com/T1nyTim/NuzlockeTool) | AGPL-3.0 (usable; the network clause travels with copied code) | 1 star, last commit 2025-03-17 | PyQt6 desktop app | Party, box and dead lists, encounter locations with duplicate prevention, a journal, a decision roller, a best-move calculator |
| [pokejgameryt-ship-it/nuzlocke-overlay](https://github.com/pokejgameryt-ship-it/nuzlocke-overlay) | MIT (yes) | 1 star, 2026-09-23, v1.1.0-beta.1 | Windows app, Gen 1 to 9 via PKHeX | OBS team overlay. Reads the save file, so it updates when the player saves, not live |
| [namboy94/nuztrack](https://github.com/namboy94/nuztrack) | GPL-3.0 (yes) | 0 stars, last commit 2022-01-16 | Python desktop app | Plain tracker |
| [WoollyAndWooden/NuzlockePlanner](https://github.com/WoollyAndWooden/NuzlockePlanner) | GPL-3.0 (yes) | 1 star, 2026-02-24 | C# | No README |
| [VBS1998/Pokemon-Sword-Shield-Nuzlocke](https://github.com/VBS1998/Pokemon-Sword-Shield-Nuzlocke) | Apache-2.0 (yes) | 2 stars, 2020-07-14 | iPhone and macOS, Sword and Shield | Progress tracker |
| [JacenBoy/Nuzlocke-Stream-Dashboard](https://github.com/JacenBoy/Nuzlocke-Stream-Dashboard) | MIT (yes) | 1 star, 2018-05-17 | C# stream layout dashboard | Abandoned; sprites only to Gen 6 |

None of these that I looked at reads emulator memory live (the stream dashboard does not say how
it gets its data).

The nuzlocke.app area lists are ordered by progression and mix in boss rows. Counts per key,
areas / boss rows: Emerald 70 / 29, FireRed and LeafGreen 52 / 22, Ruby and Sapphire 67 / 25,
Crystal 84 / 35, Red and Blue 45 / 22, Yellow 45 / 26, Diamond and Pearl 58 / 26, Platinum 57 / 28,
HeartGold and SoulSilver 94 / 38, Black and White 47 / 38, Black 2 and White 2 63 / 29. The boss
rows carry a group tag (gym leader, elite four, rival, evil team) and link to a team with levels,
moves and abilities in `league.json`. Encounter species in these files are the vanilla ones.

### 3.5 Enforcement inside the ROM

| Project | Licence (GPL-3.0 OK?) | Activity | Games | What it does |
|---|---|---|---|---|
| [msmfai/nuzlike](https://github.com/msmfai/nuzlike) | GPL-3.0-or-later (yes) | 1 star, 82 commits, alpha 7 on 2026-09-18, created 2026-08-02 | Red, Blue, Yellow, Crystal, Emerald, FireRed, LeafGreen; exact clean English backups only (a modified ROM, even one already patched, is rejected) | One wild encounter per area with uncaught species prioritised, no wild EXP, boss level caps with capped-EXP sharing, fainted mons retired to a PC Memorial box, a full-party wipe deletes the run save. Delivered as JSON "recipes" (guarded byte writes with SHA-1 and fingerprint checks, or XOR deltas) applied by a Python, Rust or browser patcher. Randomized runs only through its own FVX bridge. Rules other than caps, EXP share percentage and debug switches are fixed |
| [Almamu/pokeruby_nuzlocke](https://github.com/Almamu/pokeruby_nuzlocke) | none (no) | 4 stars, last commit 2019 | Ruby (decomp) | Nuzlocke rules in C inside the game |
| Decomp hacks with a `src/nuzlocke.c` (Elite Redux, emerald-path, nuzlocke-invitational-2 and others) | none (no) | various | Gen 3 hacks | Study only |
| tustin2121/trihard-emerald, colawsol/pokerednuzlocke, bryanthaboi/nuzlocke | none (no) | small | Emerald (TPP), Red, Gen 1 recomp | Study only |

NuzLike's level-cap research is the most reusable part: `configs/presets/level_caps.json` (Easy,
Medium, Hard per game) and `configs/LEVEL_CAP_SOURCES.md` (community "hardcore" caps, cross-checked
against Nuzlocke University, r/nuzlocke and Nuzlocke Tracker guides). Its Medium caps, gyms | Elite
Four | champion: Emerald 15,19,24,29,31,33,42,46 | 49,51,53,55 | 58; FireRed and LeafGreen
14,21,24,29,43,43,47,50 | 54,56,58,60 | 63; Crystal 9,16,20,25,30,31,35,40 | 42,44,46,47 | 50.
The file itself says these are community conventions, not rules.

### 3.6 Licence verdicts

- **Can be copied into a GPL-3.0 app, with the notice kept:** MIT, BSD-3, GPL-3.0, GPL-3.0-or-later,
  Apache-2.0, LGPL. Attribution goes in `NOTICE`.
- **Cannot:** no licence file (all rights reserved), GPL-2.0-only, PolyForm Noncommercial, closed
  source.
- **Two things to check before copying data:** nuzlocke.app's LICENSE names Diego Ballesteros
  while most commits are domtronn's, and where the data files came from (probably public wikis)
  is not stated. The data is facts, but confirm before bulk-copying, or regenerate from the ROM
  where we can (Gen 3 boss teams are readable from the ROM).
- **If code from an unlicensed project were ever wanted,** the fix is a written grant from the
  author, as was done for NatDexExtension (recorded in `NOTICE`). Not needed for the plan below.

### 3.7 What the survey teaches

1. The memory-reading Nuzlocke trackers I looked at are passive ledgers: they read the party,
   latch a death when HP hits 0 and show the result. None of them blocks play. (Two other
   projects send button presses, Fracture17's autopilot and Vargontoc's bridge; neither is a
   rule tracker.)
2. Areas are data, not code: a list per game (nuzlocke.app), the game's own map section
   (Diving-Fish via each mon's met-location), or the route table (IronMON tools).
3. Level caps in the projects I read are static tables keyed on badges or trainer ids. Reading
   the next boss's actual party from a randomized ROM is something KaizoCore can do and they do
   not (`GbaTracker.trainer`).
4. Detection has known holes that the authors admit: missed encounters while the tool was not
   running (Run & Bun tracker), a partner's uncaught encounter in Soul Link (PokeStreamer-Tools),
   KOs from passive damage (RnBFragTracker). Battle-event detection plus game counters (section
   4.3) narrows these.
5. The PC tracker's maintainer, replying on issue 495, says it does not modify the game and only
   tracks what the player can see. That fits an advisory ledger and argues against enforcement
   in the tracker.

## 4. Part 2: KaizoCore today

### 4.1 The plumbing

- `tracker-gba`: `GbaTracker` (Gen 3), `GbcTracker` (Gen 2), `Gen1Tracker` (Gen 1). Each `read()`
  returns a `TrackerState` (also implements `RunView`). `tracker-nds`: `NdsTracker.read()` returns
  `NdsTrackerState`. All read memory through a reader lambda over the libretro core
  (`retro.readMemory`).
- `app/.../PlayScreen.kt` polls: 250 ms in battle, 700 ms otherwise, paused when the app is not
  visible (Gen 3 and Game Boy loop at lines 867-947, DS loop 814-865). New per-run facts are fed
  from `LaunchedEffect(trackerState)` and `LaunchedEffect(ndsState)` (lines 521-529) through
  `EncounterBook` into `StatMarks`, which writes plain text files beside the run.
- Per-run files are listed in `PrepStore.saveAttempt` (line 562) and `PrepStore.clearRunNotes`
  (line 584); a Nuzlocke ledger file has to be added to both lists.
- Game-over: `GameOverLatch` and `RunHistoryHook.recordRunEnd` (`RunHistory.kt`) file the run with
  the fainted mon, the killer, the trainer and the location: already a death card.
- Integrity: `RunEvents` logs state loads, Time Machine restores, battle retries and app resumes
  per run. RetroAchievements hardcore already switches off state loads and rewind
  (`raHardcore`, PlayScreen.kt lines 336, 1200, 1311; today only for Library games,
  `!session.isRun`): a template for a strict mode.
- Rulesets: `RulesetCatalog`, `RnqsInfo.RULESETS` (the keys), rules text under
  `app/src/main/assets/rulesets/<family>/`. `PcHeals.Limit` is a worked example of a mode that
  adds counters and options.
- Stream: `stream/StreamSnapshot.kt` builds the JSON the stream page shows; a Nuzlocke block
  would go there.
- The PC tracker has the same route-encounter store we ported (`Tracker.Data.encounterTable`,
  `Battle.incrementEnemyEncounter`), and no Nuzlocke logic.

### 4.2 Capability matrix

Status: HAVE, PARTIAL, MISSING. Line numbers are as of this commit (5c0d540).

#### Gen 3 (FireRed, LeafGreen, Ruby, Sapphire, Emerald, NatDex Emerald and FireRed)

| Need | Status | Where | What is missing, and how to close it |
|---|---|---|---|
| Current map or route id | HAVE, coarse | `GbaTracker.read()` GbaTracker.kt:1813-1832 reads `gMapHeader + 0x12` (`mapLayoutId`), adopted after two equal polls; `TrackerState.mapId`, `routeName` from `gen3/routeinfo-*.tsv` | A Nuzlocke area is the game's map section, `gMapHeader + 0x14` (`regionMapSectionId`, verified in the Emerald and FireRed decomps, the same value the game stores in every new mon). Not read anywhere. Add one u8 read and a `mapsec-<game>.tsv` name table. Check Ruby, Sapphire (field exists in pokeruby, offset presumed) and NatDex |
| First wild encounter per area | PARTIAL | PlayScreen.kt:747-779 calls `StatMarks.seeOnRoute` and `seeOnRouteArea(mapId, area, species)` (StatMarks.kt:547, 599); the area comes from `battleEncounterArea()` (GbaTracker.kt:3356, `encounterAreaByTerrain` at :1266); `EncounterBook.onGba` counts species per battle | Records every new species per layout id and method, not "the first"; the per-method record skips methods the vanilla RouteData lacks for that map (the per-map record does not); stores species only (no level, PID or outcome); no gift or static classification. Static, roamer, legendary and Regi battles are already distinguishable from `gBattleTypeFlags` (`encounterAreaByTerrain` labels them "Static"); Emerald's bits are roamer 10, legendary 13, Regi 14 (verified in pret), and FireRed reuses some bits, for example its legendary flag is bit 18 |
| Catches | MISSING as an event | `GameMap.battleOutcome` is mapped for every game (GbaTracker.kt:316, NatDex from ROM pointer 0x08000260) but `updateBattleStatus()` (:3424) and `readGameOver()` (:3049) only test 0 and 1. `dexOwned()` (:2565) exists | See 4.3: outcome 7, `gBattleResults.caughtMonSpecies`, capture stat 11, enemy PID matched against party PIDs. Boxes are not read anywhere |
| Party faints | PARTIAL | `TrackerState.party[i].mon.curHp` and `PokemonDecoder.Mon.pid`; `LossCondition.lostMons` feeds only the run-over check, and only while in battle (`readGameOver`) | No per-mon death latch, no graveyard, no revived-mon check, whiteout counts the party only. Latch on state (HP is 0 now), not on the transition, so a missed poll or a state load cannot lose a death |
| Levels | HAVE | `PokemonDecoder.Mon.level` | Cap table: `trainerGroup()` (:2204), `trainer(id).party` (:2290, live from the ROM) and `trainerDefeated()` (:2846) give the next boss's real levels on a randomized seed. "Next boss" ordering is not written (gym ids are consecutive in the trainer tables, for example 265-272 in RSE) |
| Badges | HAVE | `readBadges()` GbaTracker.kt:3238 | None |
| Items used in battle | MISSING as an event | `readBag()` (:2402) reads Items, Berries and Balls | Diff the bag between battle start and end, or read `gBattleResults` counters (4.3) |
| Battle style | MISSING | `saveBlock2()` (:3086) already resolves SaveBlock2 | `optionsBattleStyle` is bit 9 of the u16 at SaveBlock2 + 0x14 in the Emerald and FireRed decomps. Ruby, Sapphire not checked here |

#### Game Boy (Red, Blue, Yellow, Gold, Silver, Crystal)

| Need | Status | Where | What is missing |
|---|---|---|---|
| Current map or route id | PARTIAL | Crystal: `GbcTracker.read()` (GbcTracker.kt:388) sets `mapId` from `Gen2Map.curLandmark` (wCurLandmark) and names it from `gen2/landmarks.tsv` (95 landmarks). Gold and Silver: `mapGroup` only. Gen 1: `Gen1Map.curMap` (wCurMap), no name (comment at Gen1Tracker.kt:341-345) | Gen 1 map-to-area table (pokered `constants/map_constants.asm` is in `C:/Users/bepor/ironmon-ref/pokered`); Gold and Silver group and number to area |
| First wild encounter per area | MISSING | PlayScreen.kt:761-763 records route encounters only when `trackerRef != null`, which is Gen 3 only; the Game Boy references record none | A Game Boy path through `gbRef`. `EncounterBook.gbaUpdate` only counts species per battle, keyed on a synthetic id |
| Catches | MISSING | Party count only | Symbols exist in the local decomps: Gen 1 `wCapturedMonSpecies`, `wBattleResult`, `wPokedexOwned`, `wBoxCount` (pokered `ram/wram.asm`); Gen 2 `wBattleResult`, `wPokedexCaught` (pokecrystal `ram/wram.asm`). Addresses are not in `Gen1Map` or `Gen2Map`; compute with `tools/wram_layout.py` |
| Party faints | PARTIAL | `readParty()` (GbcTracker.kt:301, Gen1Tracker.kt:234) has HP; `GbGameOver.lost` fires only once the battle byte is 0 | Identity: the Game Boy `pid` is synthetic, `(species << 16) or OT id` (GbcTracker.kt:216), so two mons of one species collide. Add the DVs (already in `Mon.ivs`) to the key |
| Levels | HAVE | `partyMon()` | No boss table read from a Game Boy ROM |
| Badges | HAVE | `GbcTracker.read()` (Johto and Kanto), `Gen1Map.badges` | None |
| Items used in battle | MISSING | `readBag()` in both trackers | Bag diff |
| Battle style | MISSING | None | `wOptions` bit 6 in both decomps (`BIT_BATTLE_SHIFT`, `BATTLE_SHIFT`); address to compute |

#### DS (Diamond, Pearl, Platinum, HeartGold, SoulSilver, Black, White, Black 2, White 2)

| Need | Status | Where | What is missing |
|---|---|---|---|
| Current map or route id | HAVE | `NdsTracker.updateLocation()` (NdsTracker.kt:474) gives `NdsTrackerState.mapId` (child map header) and `areaName` from `gen4/locations-*.tsv` and `gen5/locations-*.tsv`; an unknown header reads "Mystery Zone" and keeps the last area | Nothing for area keys. Vanilla encounter tables exist for only 5, 12, 7 and 4 areas (`NdsEncounterTables`), so there is no full checklist |
| First wild encounter per area | PARTIAL | PlayScreen.kt:757-759 calls `StatMarks.seeDsEncounter(areaName, species, level)` for a wild battle (`enemyTrainerId == 0`); `EncounterBook.onDs` | Same gaps as Gen 3: not first-only, no outcome, no classification |
| Catches | MISSING | `partyCount` only. The reference address table has `totalMonsParty` and no dex, box or battle-result address | New RAM research for five families (DP, Pt, HGSS, BW, B2W2), or infer: enemy HP 0 means KO, party count +1 means caught, party full means unknown |
| Party faints | PARTIAL | `NdsTrackerState.party` (HP, PID). `runHasEnded()` (:622) reads the battle's own party copy (`battleParty()` :657, Gen 5 `gen5PartyPointers()`) | Per-slot battle HP exists internally and is not exposed |
| Levels | HAVE | `Gen4.Mon.level` | Boss teams only from the randomizer log (`DsLog.team`, `NdsLogData` groups) |
| Badges | HAVE | `readBadges()` (:815), HGSS Johto and Kanto combined | None |
| Items used in battle | PARTIAL | `readBag(inBattle)` (:715) returns healing and status items only | Full bag, or accept heals-only |
| Battle style | MISSING | None | Address unknown |

### 4.3 Cheap signals not used yet (Gen 3, all verified in pret)

Sources: pret/pokeemerald and pret/pokefirered headers and source (links in section 7).

| Signal | Address | Value |
|---|---|---|
| `gBattleOutcome` | `GameMap.battleOutcome`, already mapped | 1 won, 2 lost, 3 drew, 4 ran, 5 teleported, 6 wild fled, 7 caught, 8 no Safari balls, 9 forfeited, 10 mon teleported |
| `gBattleResults` (zeroed at every battle start, `BattleStartClearSetData`) | `GameMap.battleResults`, already mapped (Emerald 0x03005D10, FireRed 0x03004F90, Ruby 0x030042E0) | +0x00 player faint counter, +0x01 opponent faints, +0x03 healing items used, +0x04 revives used, +0x05 bit 6 shiny wild mon (Emerald layout), +0x28 caught species (u16), +0x2A caught nickname, +0x13 turn counter (already used) |
| Game statistics via `readGameStat(i)` (GbaTracker.kt:3095, already decrypts) | SaveBlock1 | 8 = wild battles (standard, roamer, scripted, legendary, Regi, first battle; not Safari), 11 = Pokemon captures (incremented in `BattleScript_SuccessBallThrow`, skipped for a Safari Ball), 13 hatched eggs, 14 evolved. The PC tracker's constants list 8 and 11 for FireRed too |
| Enemy PID | `EnemyInfo.pid` (battler struct 0x48), already read | After a catch, the caught mon's PID equals it: scan the party for it |
| Met location | Byte +1 of the M substructure (the decoder's `m` offset). `PokemonDecoder.decode` reads M only at +4 (the IV word) | `CreateBoxMon` stores `GetCurrentRegionMapSectionId()` in every mon it creates (pokemon.c), so the area a mon came from is in the mon itself. Diving-Fish uses this |
| Map section | `gMapHeader + 0x14` | Same value as the met location |
| Battle style | SaveBlock2 + 0x14, bit 9 | 0 shift, 1 set |

Two consequences. First, a missed encounter (a battle that starts and ends between polls at
turbo speed, or while the app was closed) can be detected by comparing stat 8 with the number of
encounters logged, and `gEnemyParty` keeps the last enemy in memory to recover the species.
(Recovery from `gEnemyParty` is an idea, not tested.) Second, the outcome does not need a new
address on Gen 3.

NatDex caution: the app's NatDex map overrides the turn-counter offset to 0x41
(`battleResultsTurnOffset`, from ROM address 0x0800040A), so `BattleResults` is not the vanilla
layout there. The first bytes probably hold (they precede the name arrays); the later ones moved.
Verify on a dump before trusting NatDex.

### 4.4 Gaps that need new data or new addresses

1. **Evolution families for the dupes clause, on every console.** `gen4/evos.tsv` and
   `gen5/evos.tsv` are random-evolution probability tables, not evolution chains; Gen 3
   `species-extra.tsv` holds evolution text. A vanilla family table is small; randomized
   evolutions need the ROM's evolution table or the randomizer log.
2. **Game Boy identity and addresses** (section 4.2).
3. **DS outcome and catch detection** (section 4.2). The largest single item.
4. **Area tables:** Gen 3 map-section names, Gen 1 map names, Gold and Silver map names.
5. **Boxes:** nothing reads PC storage on any console. Needed only for "dead mons in the box"
   checks and box-side catch confirmation; the party-only design works without it.
6. **Hacks:** Radical Red, Run & Bun and Unbound are refused by the CRC gate. Out of scope.

### 4.5 Data the repo already ships that a Nuzlocke feature reuses

`gen3/routeinfo-*.tsv` (map, name, trainers), `gen3/routeenc-*.tsv` (vanilla encounters with rates
and levels), `gen3/trainers-*.tsv` (gym, Elite Four, boss and rival ids), `gen2/landmarks.tsv`,
`nds/locations-*.tsv` style tables (`gen4/locations-*.tsv`, `gen5/locations-*.tsv`),
`nds/trainer-groups.tsv` (rivals, gym leaders, Elite Four with badge numbers), `nds/pivot-areas.tsv`.

## 5. Recommendation

### 5.1 Options compared

| Option | Verdict | Why |
|---|---|---|
| Adapt an IronMON-family tracker | No | The only Nuzlocke-named ones add nothing we lack. The extensions are unlicensed Lua for a host KaizoCore deliberately does not embed (`TRACKER-PARITY.md`: custom extensions do not apply on a phone; `docs/TRACKER_SPEC.md`: Lua host rejected) |
| Adapt an emulator-memory Nuzlocke tracker | No | Unlicensed, Lua/Node/web, and they read the same party and HP we already read |
| Adapt a web tracker | No as code, yes as data and rules | BSD-3 and MIT TypeScript and Svelte are not portable to Compose; their area lists, boss data and rule model are |
| **Extend our tracker** | **Yes** | Most of the signal path exists (4.1). The remaining work is a rules engine, a file, a screen and a few reads |
| NuzLike patch path | Later, optional | GPL-3.0-or-later and it enforces, but alpha, seven games, clean ROMs only, fixed rules, and its recipes refuse anything but a clean backup or its own randomizer bridge, so composing it with our NatDex, smart-AI and randomizer stages is unproven. A 2-day spike would settle it |

### 5.2 Shape of the build

- **A platform-neutral engine** (a new package in `tracker-gba`, which `tracker-nds` already
  depends on, for example for `LossCondition`). Input: a per-poll snapshot (area key and name,
  in battle, wild, method, enemy species/level/PID/shiny, party with id/level/HP/egg/nickname,
  badges, counters). Output: an append-only event log and a ledger: areas (first encounter,
  outcome, dupe or shiny skip), roster (caught, died where, by what), violations. Level-triggered
  where it can be, so a missed poll or a state load reconciles instead of losing facts.
- **Per-console adapters** in the shape of `EncounterBook.gbaUpdate` and `dsUpdate`, which already
  turn `TrackerState` and `NdsTrackerState` into small records. The adapters add the reads in 4.3.
- **One ledger file** per run (like `routes.txt`), added to the two `PrepStore` lists, plus the
  ruleset entry (`RnqsInfo.RULESETS` key and a rules text) so it shows up as a mode.
- **Rules as data**: dupes clause, shiny clause, gifts and statics counted or not, area key
  (map section or layout), cap source (ROM, log, table), strict mode (reuse the RetroAchievements
  hardcore gate to disable state loads, rewind, retries and cheats, and log the rest in
  `RunEvents`). Defaults from NuzLike's Medium caps and the common clauses.
- **Passive by default.** Nothing blocks; violations are flags the player can dismiss.
- **UI:** one screen (areas, roster and graveyard, cap banner), a line on the tracker panel, a
  block in `StreamSnapshot`, and the existing death card extended with the Nuzlocke facts.

### 5.3 Phases

1. Decisions with Blake (5.5). Tier 1 on Gen 3: area from map section, first wild encounter
   per area, deaths from party HP, roster and graveyard, manual outcome tap.
2. Gen 3 Tier 2: outcome and catch from `gBattleOutcome` and `gBattleResults`, PID link, dupes
   with a vanilla family table, caps from ROM trainers, shiny clause, item and battle-style
   flags, missed-encounter check from stat 8. Verify on a NatDex dump.
3. Game Boy: compute WRAM addresses, area tables, DV-based identity, then Tier 2 where the
   decomps give a battle result and dex flags.
4. DS: Tier 1 first (area and deaths are already there); Tier 2 needs RAM research per game
   family.
5. Optional: the NuzLike spike; a stream overlay; run export.

### 5.4 Risks

- **Adjudication.** Gifts, statics, roamers, legendaries, Safari Zone, Game Corner prizes,
  fossils, eggs, trades, multi-map areas (cave floors), "Mystery Zone" on DS, fishing versus
  surfing sharing an area. Mitigation: flags on Gen 3 (4.2), per-run rule toggles, manual
  override on every automatic record, an "unclassified" state.
- **Missed events** at turbo speed or across state loads. Mitigation: level-triggered latches and
  the stat 8 check on Gen 3; `RunEvents` marks the loads.
- **DS outcome detection** has no reference to copy from and five games to find it in. Ship DS
  semi-automatic first.
- **Vanilla-based tables on randomized seeds.** Area names and boss ids are places and people,
  not species, so they survive randomization; species and boss levels must come from the ROM or
  the log, not from a bundled table.
- **Spoilers.** The log and route tables can reveal the seed. A Nuzlocke mode should keep Open Book
  off unless the player turns it on.
- **Timing.** The tracker runs only while the Play screen is visible and the core is running
  (`PlayScreen.kt` loops); the ledger must not depend on the Compose effect staying alive
  (`EncounterBook` is kept per session for that reason).

### 5.5 Decisions needed from Blake

1. Scope of the first ship: Gen 3 only, or all consoles?
2. Advisory only, or a strict toggle reusing the hardcore gate?
3. Default rule set (dupes and shiny clauses on? gifts and statics counted? area = map section?).
4. Where it lives: IronMON runs only, or Library games that match a known CRC as well.
5. Whether the ledger may show the randomizer log's contents (Open Book) at all.
6. Whether a 2-day NuzLike spike is worth doing now.

## 6. Not verified

- The NatDex `BattleResults` layout past byte 4, and the map-header field offset on NatDex, Ruby
  and Sapphire.
- Whether FireRed, LeafGreen, Ruby and Sapphire increment game stats 8 and 11 exactly as Emerald
  (verified in Emerald source; the PC tracker's constants cite pokefirered's `game_stat.h`).
- The Gen 1 and Gen 2 addresses (symbols confirmed in the local decomps, addresses not computed).
- Anything on DS beyond what the app and the reference tracker already read.
- NuzLike's behaviour on a KaizoCore-randomized or NatDex ROM.
- Provenance of nuzlocke.app's data files.
- Timing at turbo speeds; recovery of a missed encounter from `gEnemyParty`.
- Effort figures are estimates, not measurements. I did not run the app, the tests or any emulator.

## 7. Sources

Trackers and repos are linked in the tables above. Other sources:

- Nuzlocke rules: <https://nuzlockeguide.com/>, <https://en.wikipedia.org/wiki/Nuzlocke>
- IronMON wiki, extensions: <https://github.com/besteon/Ironmon-Tracker/wiki/Tracker-Add-ons>;
  local copy `C:/Users/bepor/ironmon-ref/Ironmon-Tracker.wiki/Tracker-Add-ons.md`
- Ironmon-Tracker issues: <https://github.com/besteon/Ironmon-Tracker/issues/546>,
  <https://github.com/besteon/Ironmon-Tracker/issues/495>,
  <https://github.com/besteon/Ironmon-Tracker/issues/348>
- ElusiveFluffy fork change: commit `abfc4261` ("Edit battle handler to have a option to only count
  fail on whiteout")
- pret, Gen 3 facts:
  [battle outcomes](https://github.com/pret/pokeemerald/blob/master/include/constants/battle.h),
  [BattleResults](https://github.com/pret/pokeemerald/blob/master/include/battle.h),
  [BattleResults in FireRed](https://github.com/pret/pokefirered/blob/master/include/battle.h),
  [MapHeader](https://github.com/pret/pokeemerald/blob/master/include/global.fieldmap.h),
  [MapHeader in FireRed](https://github.com/pret/pokefirered/blob/master/include/global.fieldmap.h),
  [SaveBlock2 options](https://github.com/pret/pokeemerald/blob/master/include/global.h),
  [game statistics](https://github.com/pret/pokeemerald/blob/master/include/constants/game_stat.h),
  [wild battle counters](https://github.com/pret/pokeemerald/blob/master/src/battle_setup.c),
  [capture counter](https://github.com/pret/pokeemerald/blob/master/data/battle_scripts_2.s),
  [met location set at creation](https://github.com/pret/pokeemerald/blob/master/src/pokemon.c),
  [results cleared at battle start](https://github.com/pret/pokeemerald/blob/master/src/battle_main.c),
  [faint counter](https://github.com/pret/pokeemerald/blob/master/src/battle_script_commands.c)
- Local decomps used for Game Boy symbols: `C:/Users/bepor/ironmon-ref/pokered/ram/wram.asm`,
  `C:/Users/bepor/ironmon-ref/pokecrystal/ram/wram.asm`, `constants/ram_constants.asm` in both
- KaizoCore files read: `tracker-gba/.../GbaTracker.kt`, `GbcTracker.kt`, `Gen1Tracker.kt`,
  `Gen3.kt`, `LossCondition.kt`, `GbGameOver.kt`; `tracker-nds/.../NdsTracker.kt`, `NdsGameMap.kt`,
  `NdsEncounterTables.kt`, `NdsLogData.kt`; `app/.../StatMarks.kt`, `EncounterBook.kt`,
  `PlayScreen.kt`, `LogRoutes.kt`, `RunHistory.kt`, `RunEvents.kt`, `PcHeals.kt`,
  `GameOverLatch.kt`, `RulesetCatalog.kt`, `RnqsInfo.kt`, `PrepStore.kt`, `GameSession.kt`,
  `stream/StreamSnapshot.kt`; `TRACKER-PARITY.md`, `docs/TRACKER_SPEC.md`;
  PC tracker `Battle.lua`, `Tracker.lua`, `Constants.lua`
