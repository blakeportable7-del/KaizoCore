# New game checklist

Every feature KaizoCore offers a game, built from the code, so a new game is planned in full before the first player
finds the gaps one at a time (Blake, 2026-10-05, after Heart & Soul shipped without level-up moves, X marks,
descriptions and its own pictures: "you should have planned for this, be smart"). Copy the Heart & Soul column for the
next game and fill it in before writing code.

## How to rebuild the list from the code

The list below is complete for the code of 2026-10-05. Before trusting it for a new game, run these and check that
every hit is a row here (a new branch on the game is a new row):

    rg -n "isNatDex|isMaxDex|isHns|fairyTypes|playOnly|patchTag|baseId" app/src/main tracker-gba/src/main core-api/src/main
    rg -n '\.family\b|"(RSE|FRLG|GSC|RBY|DPPt|HGSS|BW|B2W2|HnS)"' app/src/main/kotlin
    rg -n "badgeSet|routeVersion|routeTable|nameSet|expandedSpeciesIds|map\.hns|heartSoul" app/src/main/kotlin tracker-gba/src/main/kotlin
    rg -n "Generation\.(GBA3|NDS4|NDS5|GBC2|GB1)|Platform\.(GBA|NDS|GBC)" app/src/main/kotlin
    rg -n "252\.\.276|1 until 412|\b411\b|\b386\b|\b1283\b|\b1284\b|\b1280\b" app/src/main/kotlin

Then walk the player's screens: Home (every button), Library (My games, Patched versions, ROM hacks), Kaizo IronMON,
Nuzlocke, Play (every tracker tool, the carousel, the File menu, the gear), the game-over popup, GachaMon, More (Backup
and info, Controls, About), the stream page, and the wiki (docs/wiki/, README "What it does").

Three traps Heart & Soul fell into, worth checking first on any game:

- **A game's strings stand for another game.** Heart & Soul's tracker map says `badgeSet = "GSC"` (two rows of eight
  badges), so every `badgeSet == "GSC"` meant for Gold, Silver and Crystal, and every `badgeSet == "RSE"/"FRLG"` gate,
  is a question for it. Its `routeVersion` is empty, so everything keyed on it (GachaMon's game number, prize trainers,
  Sevii) silently did nothing.
- **Species numbering.** Gen 3 ids skip 252 to 276; Nat. Dex 1.2.1 runs to 1283; Heart & Soul to 1572 with Treecko at
  252. Any `filter { it !in 252..276 }`, any `1 until 412`, any table keyed by another game's ids is wrong for it. Map
  through one place (HnsNumbers for Heart & Soul).
- **The chain of a feature.** A screen can be right while the data it needs is missing upstream (the favorite-in-ball
  line is right for Heart & Soul now, and still says nothing until the tracker reads its starters).

Status words: **works** (checked in code or on a device), **fixed** (was wrong or missing, fixed on feat/hns-parity with
a test), **partial** (works with a stated limit), **missing** (not done; owner named), **out of scope** (with the reason),
**queued** (decided, waiting on a batched change).

## Heart & Soul (KaizoCore build, RomKind.HEARTSOUL_KAIZO_206, CRC F0C6236C since the comfort rebuild)

Owners: **parity** = feat/hns-parity (this branch), **assembly** = feat/hns-kaizo (the tracker panel), **comfort** = the
next comfort-patch rebuild (docs/HNS-KAIZO.md), **stream** = the streaming branches.

### Getting the game

| Feature | Where | Game-specific by | Heart & Soul |
|---|---|---|---|
| Its own Home button, add your Emerald, find and apply the patch | HomeNav, HeartSoulScreen, HnsSetup | HomeMode.HEARTSOUL | works |
| Library recognition and shelf (official 2.0.6 under ROM hacks, KaizoCore build under Patched versions) | LibraryStore, RomKind | playOnly, isHns | works |
| The official 2.0.6 plays with no tracker | GameSession.trackerKind | playOnly | works (by design: only our build has symbols) |
| Rename suggestions | LibraryStore.suggestions | isNatDex | fixed: its own name, never "clean" |
| "Stored as" line when a build is added | PrepRun | isNatDex, patchTag | fixed: "Already patched" |
| The empty Library page's list of tracked games | LibraryStore.trackedGames | RomKind.allV1 | out of scope: Heart & Soul is not added under My games; its Home button says how |
| ROM hacks list | HackLinks | RomKind ids | fixed: Emerald lists Heart & Soul and opens the team's GitHub release |

### Kaizo IronMON

| Feature | Where | Game-specific by | Heart & Soul |
|---|---|---|---|
| NEW RUN, seeds, attempts, staged next run | RunScreen, RunStart, RunJob, NextRunJob | engine | works (HnsEngine) |
| Modes offered (every RSE Nat. Dex v1.2 file, files tagged HnS) | RulesetCatalog.isCompatible, listedFor | isHns | works |
| The pool, Vanilla (Gen 1-3) or Nat. Dex (Gen 1-9) | HnsPool, RunScreen pass rows | isHns | works |
| Mode lines under the Mode row | RulesetCatalog.modeLine | natDex, family | fixed: Ultimate reads as Nat. Dex's, Survival names the seven Kanto heals |
| Super Kaizo warning | RulesetCatalog.superKaizoWarning | isHns | works: HnsEngine applies the smart AI |
| The 60% pre-pass and PART 2 | ExtraPasses | family | works: none needed, the Nat. Dex files carry +60% |
| Build your own | RunScreen, BuildYourGame | isHns | out of scope: its starter list and choices are Emerald's; hidden for Heart & Soul |
| Run codes | RunCode, RunCodes, RunCodeUi | passes | fixed: the code carries the pool (it changes the game); a missing build points to the Heart & Soul button |
| The new seed's save check ("your save holds a team") | RunSaves.plan, SaveCheck | family | fixed: its party count, 4 bytes past Emerald's |
| Kept save, auto-save, SRAM guard, crash resume | KeptSave, AutoSave, SramGuard, CrashResume | platform | works |
| Time Machine, rewind, save states | TimeMachine, RewindBuffer, StateSlots | platform | works |
| Randomized items: the PC item, the starter's held item, hidden items, Pickup, item pools by the run's pool | HnsEngine | engine | works (assembly, 58258f77) |
| The win | tracker FINAL_TRAINERS (gen_tracker.py) | game | **missing, assembly/Blake**: the tracker ends the run at Lance; HeartGold and SoulSilver's rules (now its rules page) say Red on Mt. Silver |

### Game over and records

| Feature | Where | Game-specific by | Heart & Soul |
|---|---|---|---|
| The popup: continue, retry the battle, save the attempt, grade notes, inspect the log, new seed | GameOverLatch, GameOverHost, GameOverDialog | platform | works |
| Death card and quotes | DeathQuotes, RunHistory | no | works |
| Attempt count, run history, Your stats | RunHistory, RunProgress, CareerStats | kind id | works (16 badges count) |
| Hide stats until the summary | SummaryChecks | generation | works |

### GachaMon

| Feature | Where | Game-specific by | Heart & Soul |
|---|---|---|---|
| A card for each lead | GachaMon.capture, make | routeVersion | fixed: its own game number (6, "Heart & Soul") and numbering ("hns") |
| Badges on the party's cards | GachaMon.watchBadges | badgeSet, map ids | fixed for Johto; partial: a card holds eight badges (the PC tracker's record), so Kanto's are not kept |
| Prize card at the game over | GachaMonPrize.commonTrainers | game number | fixed: rivals, the 16 leaders, Elite Four, Lance, Red, the Rocket executives (hns/commontrainers.tsv) |
| Card picture, badge art, game name, filter | GachaMonCards, GachaMonScreen | dex, gameVersion | fixed (HGSS badge art) |
| GachaDex | GachaMonScreen | species range | fixed: 1 to 1572, Treecko at 252 |
| Share codes | GachaMonCodec, GachaMon.import | gameVersion | fixed: a Heart & Soul code keeps its numbering; the PC tracker shows its game as "?" |
| Ratings for abilities past Gen 3 | GachaMonRatings | ability table | out of scope: the PC tracker's own table, the same on Nat. Dex builds |

### Favorites

| Feature | Where | Game-specific by | Heart & Soul |
|---|---|---|---|
| Edit favorites: boxes and suggestions | Favorites.slotCount, maxDex | isNatDex, generation | fixed: nine boxes, any Pokemon through Gen 9 |
| Icons on the no-Pokemon card | FavoriteIcons.of | numbering | fixed: drawn by Heart & Soul's own ids |
| The stream's favorite pictures | StreamFavoritePictures | kind | fixed: the pack by Heart & Soul's ids |
| "FAVORITE! X IN THE LEFT BALL" with the ball rules per mode | FavoriteBall | isNatDex | fixed in the app (its ids, nine with the Nat. Dex pool, three with Vanilla); **missing, assembly**: the tracker reads no starters for it (map.startersBase, labMapIds) |
| The rules text in the editor | FavoritesEditor | Rules.dirFor | fixed (its own book) |

### Play as your Pokemon, Walking Pals, themes

| Feature | Where | Game-specific by | Heart & Soul |
|---|---|---|---|
| Play as your Pokemon (the lead walks the map) | Overworld.resolve, SpriteIsMe, sprite_core.h | map, scan | fixed: its overworld table from the build's symbols (layout.py); the sprite structs are checked against sprite_core.h |
| Walking Pals in the tracker and the log | WalkingPals, Dex.HNS | numbering | works |
| Castform's forms on the overworld | PalForms.overworld | numbering | fixed |
| Auto Pokemon themes | AutoTheme | species ids | fixed: looked up by the same Pokemon's Gen 3 id |

### The rules page

| Feature | Where | Game-specific by | Heart & Soul |
|---|---|---|---|
| RULES (in Play, on Kaizo IronMON, in the favorites editor) | RulesDialog, rulesets/ | family | fixed: rulesets/HnS, made by build_rules.py: each mode's chain, HeartGold and SoulSilver's game rules (Johto and Kanto, Red to win, the Lighthouse, Radio Tower, Seafoam, the Ruins of Alph, the Kanto heals), the Nat. Dex changes, and what the pool changes |
| The IronMON PC-item rule | comfort patch, HnsEngine, rulesets/HnS | game | works (assembly, comfort build F0C6236C: the PC item is in the trash can in Elm's lab, random like the other Kaizo modes); fixed: the HnS book says so in an "In KaizoCore" line |

### Nuzlocke

| Feature | Where | Game-specific by | Heart & Soul |
|---|---|---|---|
| Start one, plain or randomized, the pool, Monotype Fairy | NuzlockeScreen, NuzlockeStarts | isHns, fairyTypes | works |
| Areas | Gen3 tracker, NuzlockeAreas | map sections | works (each region map name; a cave's floors are one area) |
| Dupes | Gen3Nuzlocke.engineSpecies, families-gen3.tsv | numbering | partial, as on Emerald Nat. Dex (the same table): lines of Gen 1-3 and their later evolutions; a Gen 4+ line counts each Pokemon alone (said in its notes) |
| Level caps | levelcaps-gen3.tsv "hns" | game | works (Johto, Elite Four, Lance, Kanto, Red; levels read from your copy) |
| Its notes page | NuzlockeNotes | gameKey | fixed: Heart & Soul's own notes |
| Set battles (statics) and gifts | GbaTracker (gMain.savedCallback), the Nuzlocke engine | game | works (assembly, 09141819: a battle a script started is a static; a given Pokemon is a gift of its town); its notes say so |
| The hack's own Nuzlocke option | challenge menu | game | out of scope: not read; its notes say so, and the hardcore setting's Cherrygrove wipe |
| First Poke Ball starts the rules | GbaTracker | ball ids | partial, assembly: Apricorn and later balls are not seen |

### The tracker (assembly worker's, recorded here)

| Feature | Heart & Soul |
|---|---|
| Level-up moves, the red X rule marks (by the run's pool), move/ability/item descriptions, the ROM's own pictures | works (assembly: a0a926a9, 18a9466e) |
| Starter balls and the lab's "starter offered" line | missing, assembly: `startersBase` (sStarterMon), `labMapIds` (Elm's lab) |
| Safari Zone | missing, assembly: `safariModeFlag`, `safariMapIds` |
| Battle Details | works (assembly, 6c2859ec) |
| The last-attack line | works (assembly, 6c2859ec: battler 0's HP lost while the foe attacks) |
| Calc Atk, coverage, type defenses | works (the Fairy chart) |
| Catch rates | partial, assembly: Gen 3's formula, the twelve Gen 3 balls |
| Badge rows | partial, assembly: drawn as Crystal's Game Boy badges; HeartGold and SoulSilver's art is bundled (HGSS, HGSS_K), and Heart & Soul's flags put Sabrina at 13 and Janine at 14 |
| Trainer teams before a fight | partial, assembly: refGame 0 hides them outside Open Book |
| Kanto heals counted in Survival | partial, assembly: `leagueBeaten` is never set on GBA |
| Kanto gyms in any order for the caps | partial, assembly: checked in order from Brock |
| Giovanni's Master Balls on trainer info | fixed: never on Heart & Soul |
| Notebook | fixed: its own species (one per Pokemon or form), no Sevii switch |
| Species total on the panel (1572) | works (TrackerPanel, hnsInPlay) |

### The log viewer

| Feature | Where | Game-specific by | Heart & Soul |
|---|---|---|---|
| Names, pictures, move types, Walking Pals | LogNames, LogViewer | heartSoul | works |
| The full tabs: trainer groups, gym filter, badges, Pokemon detail with evolutions and gym TMs, gym TMs | LogTrainerRules, LogTms, LogSearch | badgeSet | fixed: its sixteen gyms (from the cap rows), sixteen gym TMs, post-game rematches left out, HeartGold and SoulSilver badge art |
| Routes tab and Open Book route icons | LogRoutes, OpenBookRoutes | tracker routes | partial, assembly: needs the tracker's route map ids and log sets for Heart & Soul |
| The new info panels (fix/seen-and-log) | that branch | | needs: Heart & Soul's ids through HnsNumbers, its sixteen gyms (LogTrainerRules.gymTms) |

### Backup, sync, reports, updates, RetroAchievements

| Feature | Where | Game-specific by | Heart & Soul |
|---|---|---|---|
| Backup and cloud sync | Backup, CloudSync | file lists | works: runs, prep/hns-pool.txt, settings, attempts; Library games are left out for every game, so a new phone makes the build again with the Heart & Soul button |
| Crash reports and bug reports | CrashReport, Feedback | family | works ("HnS") |
| The update check | UpdateCheck | no | works |
| RetroAchievements | RetroAchievements | hash | works: our build has no RetroAchievements set, and a run never loads one |

### Streaming (the streaming branches; what Heart & Soul needs from them)

| Feature | Heart & Soul needs |
|---|---|
| Tracker page | badgeSet "GSC" stands for 16 badges here; draw them with the HGSS art (PcAssets.HNS_BADGES maps 1 to 16) |
| Randomized data (/dex.json) | species to 1572 (GbaTracker.speciesIdCount), not 1284 |
| Attempts, game-over card | works |
| Timer and splits | sixteen badges, then Red |
| History | works |
| Twitch commands | species and moves by Heart & Soul's own ids (HnsNumbers for names and pictures) |
| Favorites pictures | fixed on this branch |

### About and credits

| Feature | Where | Heart & Soul |
|---|---|---|
| About | AboutScreen | fixed: Lil Dill and the Heart & Soul team, RHH's pokeemerald-expansion, their GitHub and Hackdex |
| Licenses | Licences | out of scope: the Heart & Soul source has no license file; credited in About and NOTICE |
| NOTICE | NOTICE | works |

### Counts (2026-10-05, feat/hns-parity after merging feat/hns-kaizo at 09141819)

82 rows: 34 works, 27 fixed on this branch, 8 partial (each with its limit), 3 missing (the tracker's: starter balls
and the lab line, the Safari Zone, and the win at Red, which is for the assembly worker and Blake), 5 out of scope
(with the reason), and 5 rows of what the streaming branches need. Recount from the tables when a row changes.
