# Tracker parity findings (audit of 2026-09-28)

Blake: "the tracker on our app needs to do exactly what the pc versions do". A screen-by-screen audit
against both PC trackers, then one audit per game family, found the gaps below. Each line is a
confirmed discrepancy unless marked UNSURE. Status: OPEN, FIXED (with the commit), or WONTFIX (with why).

## Route encounters (all Gen 3, and DS)

- FIXED d07a58d: GBA route info screen was a flat text list; the reference is InfoScreen ROUTE_INFO (one area at a
  time with arrows, Percentages/Levels checkboxes, sprites, "?" slots, lookup, open-book log data).
- FIXED d07a58d: route tables stored national dex ids; Gen 3 memory uses internal ids, so every Hoenn Pokemon on
  R/S/E routes was the wrong species and "seen" never matched it.
- FIXED d07a58d: one route table per group; FireRed/LeafGreen and Ruby/Sapphire/Emerald pick different species.
- FIXED d07a58d: trainer battles were recorded as route sightings; the reference records wild only.
- FIXED d07a58d: sightings not kept per encounter area; the carousel reads "Walking: 1/2 Seen Pokemon".
- FIXED d07a58d: enemy card's RouteDetails pin (wild battle opens route info) missing.
- FIXED dd95193: DS encounter data frame (MainScreen.lua:176-224): pin and seen/total in wild battles,
  tracked and vanilla views; LocationData.encounters converted (tools/trainer-data/convert_nds_encounters.py).
- FIXED 2bc839b, d7681bc (Ruby/Sapphire): the reference looks RouteData up by the raw map id and keys
  routes/caves with "+ offset" but Mossdeep Gym, both Sootopolis Gym floors, the Elite Four rooms (108-115)
  and Route 124 Water (274) without it; we normalise R/S ids (rsMapShift). Trainers and names
  (routeinfo-<version>.tsv) are keyed by our numbering with those maps filed under the map they name, and
  the log's R/S wild sets are converted to it (they were one map off above 108). FIXED 37ed453: the route
  info screen's encounters and title and the carousel's route line use the same numbering (routeKey), so
  Mossdeep Gym reads Mossdeep Gym and Route 124's underwater encounters show (Blake, 2026-09-29: keep correct
  numbers).

## GBA screens and taps (Ironmon-Tracker)

- OPEN: heals box tap opens Heals in Bag (TrackerScreen.lua:326).
- OPEN: enemy card has no way to open its Pokemon info.
- OPEN: carousel Trainers line opens Trainers on Route; Battle Details line opens Battle Details (and its text
  is not the reference summary).
- OPEN: GachaMon (carousel item, heals-box stars, collection, prize card, 6 options) absent.
- OPEN: info screen lookup (Pokemon, move, ability), previous/next Pokemon; Move History "Lookup Pokemon".
- OPEN: info screen note not editable there; no Move History / Type Defenses buttons on it.
- OPEN: Trainer Info lacks the trainer's bag items and icon.
- OPEN: "Can click trainers on screen" option.
- OPEN: "Display play time" (timer, edit, relocate); Stats screen says play time "not kept".
- OPEN: Pokemon icon set cycling (only Walking Pals on/off).
- OPEN: Estimate IVs (Extras).
- OPEN: tracked data manual save/load.
- OPEN: theme preset cycle/save, "Color stat number", "Move type color bar", "Text shadows".
- OPEN: startup screen attempts count display/edit.
- OPEN: shiny shown as " *" instead of the sparkle.
- DECIDED EARLIER: log viewer reachable only from Game Over (no View Log in Extras, no Open Book quick access).
- N/A on a phone: controls tab, quickload, language, updates, extensions, crash recovery, Stream Connect.

## DS screens and taps (NDS-Ironmon-Tracker)

- OPEN: one-Pokemon view with Start swap and auto swap; we stack the enemy and all six party cards.
- OPEN: move effectiveness, variable damage, actual enemy PP: DS panel ignores the options.
- OPEN: enemy locking, doubles mode, Platinum first-fight stats.
- OPEN: Hidden Power arrows and move info on tap.
- OPEN: Pokemon info on tap.
- OPEN: heals hover (bag heals and status items); Pokecenter heals +/- not passed through on DS.
- OPEN: bookmark (mark as tracked).
- OPEN: level/evo label opens Evo Data (gear only today, lead only).
- OPEN: move header shows move levels (ours opens Move History).
- OPEN: tourney points on the main screen.
- OPEN: exp bar, right-justified numbers, nicknames, accuracy/evasion stages: DS cards ignore the options.
- OPEN: blind mode, auto Pokemon themes, badge alignment/spacer, icon sets (Stadium, brows, faster
  animations, direction), colour settings (move names by type, shadows, move type icons, transparency),
  named colour schemes, timer reset/position/transparency, Open a Log, Load Tracker Data, Run Over
  "View Tourney Scores", title screen attempts and "Did you know" stats.

## FireRed / LeafGreen / Nat. Dex FireRed

- FIXED bffc510 (Nat. Dex, reported by Blake): enemy abilities never revealed. The reveal check read the
  battle struct's byte 0x20, which is not the ability in Nat. Dex (u16 abilities, types at 0x21). Now
  species ability by the party Pokemon's slot via gBattlerPartyIndexes, as the reference. Needs a live
  confirmation in a real battle.

- FIXED 2bc839b (all FRLG): trainerroutes-frlg.tsv built from FRLGTrainerRouteData.lua (tile clicks), not
  RouteData.Info[id].trainers; 11 maps missing (Oak's Lab 5, Route 22 110, Elite Four 213-217, ...),
  177/178 swapped, extra 257, order differs on ~20 maps. Now routeinfo-firered/leafgreen.tsv, run from
  RouteData.lua by tools/trainer-data/convert_route_info.py.
- FIXED cf5a259 (all FRLG): trainer 666 is both Beauty and in BirdKeeper's range in TrainerData.lua; the
  reference's pairs() order picks one. The converter keeps Beauty (the FireRed ROM's own class).
- FIXED 4d4f587 (all FRLG): ghost battles (flag bit 15 on, 13 off): the reference's "Ghost" stand-in (GhostId
  413, Nat. Dex 1285), no move/ability/encounter recorded, no effectiveness on either card, "Spoooky!" note.
  Known difference: the reference also records the real species' last-seen level at battle end.
- FIXED 19aae7d (all Gen 3): Safari Zone battles start (flag 0x800 FRLG, 0x860+0x2C RSE, enemy lead present);
  Tracker.TrackSafariEncounter ported, but nothing shows it (the reference's only reader is Stream Connect).
- FIXED 35a7ee0 (all Gen 3): repel bar hidden in the Hall of Fame (FRLG 218, Emerald 431, R/S 299).
- FIXED de7c955 (Nat. Dex FR + Em): trainer tables and the 44-byte layout from the ROM's slots; the 1.1.3
  addresses only for ROMs at 1.1.3 or older (version bytes at 0x0800048C). FR 414 reads Brock.
- FIXED 813fbe1 (Nat. Dex FR + Em): flags 0x1090/0x1458, vars 0x11B0/0x1584, repel vars+0x40/+0x52, rival name
  0x3829, trainer flag start, all from the slot table.
- FIXED be1853f (Nat. Dex FR + Em): starter preview (both), rod/Rock Smash vars, Poke Balls pocket (Catch Rates
  on), exp tables and growth rate, gTakenDmg and the 0x41 turn byte, Battle Details addresses and offsets,
  friendship threshold, summary screen, dex owned bits; Catch Rates takes the enemy status from its party slot.
- UNSURE: starters() ball labels (no callers yet); the 1.1.3 gate is untested on a real 1.1.3 ROM (none to
  hand); the notebook's species total still stops at 411 on Nat. Dex; combined areas in the notebook.

## Diamond / Pearl / Platinum / HeartGold / SoulSilver

- FIXED 1f157f6: level-up move table is Platinum's for all Gen 4 (Program.lua:327 picks by version group): 51 species
  wrong on D/P, 13 on HGSS (HG Cyndaquil shows 4 moves, reference 6). Tables from convert_nds_movelevels.py (lupa).
- FIXED a9db519 (HGSS): both badge rows from the start by default; swap to Kanto after the League (leagueBeaten).
- FIXED ad1f91f: past runs stored per badge set, so D/P/Pt share and HG/SS share; reference keys by game name.
  An older per-family file is read alongside the game's own (its runs cannot be attributed).
- FIXED a8504a6 (HGSS): items 468-536 (Apricorns, Apricorn Balls, Sport/Park Ball) show "#id". Held items now use
  GEN_5_ITEMS on every DS game as MainScreen.lua:860 does, so D/P/Pt get its spellings (and its Gen 5 mail names).
- FIXED 2390627 (HGSS): Bug Catching area not renamed by weekday (Program.lua:601-611).
- OPEN (Gen 4): gen4/moves.tsv holds ROM numbers; 37 moves differ from the reference's Gen 4 values (run through
  lupa, 2026-09-28): 34 fixed or variable powers read 1 where the reference prints --- or WT/<HP/VAR..., Nightmare
  and Memento accuracy, Curse and Hidden Power type (UNKNOWN there).
- UNSURE: D/P use Platinum's location table in the reference too; playerBattleBase not used on Gen 4;
  enemy species guard in singles.

## Gen 1 / Gen 2 (Ironmon-gen-tracker v1.2.2 = RBY, Ironmon-gen-2-tracker v0.4 = Crystal only)

Dead in the references too, so NOT gaps: route encounter screen, game stats, pedometer, repel, seen
counter, last-attack damage.

- OPEN (biggest): GB panel runs with trackerRef = null (PlayScreen.kt:755-781), so move descriptions, learn
  levels, "Moves x/y (next)", weight, evolution, weak-to and names are empty on every GB game.
- OPEN: Gen 1 badges draw as numbers ("RBY" set, no art); reference uses the FRLG Kanto art we bundle.
- OPEN (Crystal): Kanto badge row missing (GSC_K art bundled); badges 5/6 bit swap (Chuck/Jasmine) missing.
- OPEN: evolution level after "Lv." (GB TrackedMon.evo never set).
- OPEN: stat stage arrows and ACC/EVA in battle (GB never reads stage bytes).
- OPEN: last-move carousel strip (GB never sets lastAttackMove).
- OPEN: trainer "Team:" pokeballs (enemyTeam empty on GB).
- OPEN: enemy icon tap does nothing; info screen has no search / Next / Prev / History / resistances / notepad.
- OPEN: Time Machine auto restore points never fire on GB (needs a map id).
- OPEN: GB log viewer is plain lists (no sprites, gym pages, search, sort); View Log from Extras missing.
- OPEN (Crystal): held item shows "#id"; move tracking records any byte, reference only known moves.
- OPEN: "Disable mainscreen carousel", "Animated Pokemon popout", icon sets, controller shortcuts,
  tracked data save/load, theme presets and import compatibility, streamer attempts/welcome message.

## Ruby / Sapphire / Emerald

All RAM/ROM addresses match the reference JSONs (two deliberate differences noted in code). Our R/S map id
shift (above 108 minus one) is RIGHT: the reference keys R/S gyms (108-110), Elite Four (111-115), Cave of
Origin (160-163) and 274 with no offset, which its own generated RSTrainerRouteData contradicts. DECISION FOR
BLAKE: copy that reference bug or keep the game-correct numbering. Correction (2026-09-29): Cave of Origin
160-163 are right; they sit in the R/S-only branch and are written in R/S numbering (Ruby's gWildMonHeaders
put B1F-B3F on layouts 160-162). The off-by-one keys are 108-115 and 274 (Ruby's Route 124 underwater is
layout 275, Emerald's 274). DECIDED (Blake, 2026-09-29): "keep correct numbers". Trainers, names, the log
(2bc839b, d7681bc) and the route info screen (37ed453) all use the game-correct numbering.

- FIXED cf5a259 (R/S): trainers-rse.tsv and rivals-rse.tsv are Emerald's; R/S has its own table
  (setupTrainersAsRubySapphire): 229 of 692 ids differ (Archie 1/34/35, Maxie 566/601/602...), Courtney 599
  flagged as rival and sets the run's rival, Boss counts and log filters wrong.
- FIXED 2bc839b (R/S): trainerroutes-rse.tsv is Emerald's; R/S gym lists, Rustboro, Victory Road, no Space Center,
  and Ruby's Aqua/Magma swap (swapRubySapphireTeamTrainers) all missing.
- FIXED d7681bc (R/S): log viewer route tab mixes Emerald-keyed trainers with raw-keyed wild sets above 108; route
  debug log names the wrong map above 108.
- FIXED 2bc839b (Ruby): hideout named "Aqua Hideout" instead of "Magma Hideout 1F/B1F/B2F".
- FIXED 2bc839b (Emerald): route names missing for 162, 275, 276, 431; 31 maps' trainer lists differ from
  RouteData.Info trainers (Rustboro rivals, Lilycove, Route 103/110 rivals, Wallace 115, Magma Hideout,
  Victory Road split, Space Center). The names already came through routeenc-emerald.tsv since d07a58d;
  they are now in the log's route list too.
- FIXED 8b1c6ff (Emerald, Nat. Dex Emerald): Toxic catch bonus should be 1x (game 1 or 2), we give 1.5x.
- FIXED 0114ee2 (R/S): Emerald ability note appended unlabelled; reference labels it "Emerald:". The label is
  "In Emerald:" (LabelEmeraldAbility plus ":"), and InfoScreen shows it in every game, FireRed included.
- FIXED 55d5af0 (R/S): notebook totals summed per-map rows; R/S RouteData lists 12 gym trainers on two floors,
  counted twice. Totals now count each trainer once, as NotebookIndexScreen does.
- FIXED de7c955, be1853f (Nat. Dex Emerald): trainer addresses from the slots past 1.1.3; starter preview
  (gTasks + confirm task), battle details, weather, takenDmg, balls pocket now read.
- UNSURE: PC heals counted anywhere (reference: CanPCHeal maps only).

## Black / White / Black 2 / White 2

- FIXED d07a58d (White 2, CRITICAL): battleStatus shifted +0x80 with the rest (0x1B51B8); reference 0x1B5178 (+0x40).
  White 2 never detects a battle: no enemy card, reveals, progress, or enemy-caused loss.
- FIXED b9721aa (Gen 5): gen5/moves.tsv has 70 wrong moves (extract_gen5_data.py field() regex can't read the
  per-generation tables): Tackle/Thrash power 0, Bite/Gust/Curse no type, 21 moves PP 0 (and PP 0 moves
  leak onto the enemy card before use via usedOnly). All 559 now match initMoveData(GEN = 5) run through lupa;
  text powers (WT, <HP, VAR...) print as the reference prints them. usedOnly unchanged: the leak was the data.
- DECIDED AND FIXED 1f593a2 (Blake, 2026-09-29: "read properly"): one condition word per id from + 0x20,
  ids checked against the ROM's move data (Thunder Wave 1 ... Toxic 5, Odor Sleuth 17 at + 0x60 in the dump).
  The finding as it was: battle status. The reference's _readBattleStats (BattleHandlerGen5.lua:160-166)
  reads the SAME word, battle data + 0x20, seven times and sets status to the loop index, so any non-zero word
  gives 7, which STATUS_TO_IMG_NAME has no icon for; with the word zero it keeps the Pokemon struct's own status
  byte, and the struct lags the battle (the Black 2 dump: 19/19 HP in the struct, 8/19 in battle). Copied
  literally, a status inflicted in a Gen 5 battle most likely never shows. The loop and the table (1 PAR, 2 SLP,
  3 FRZ, 4 BRN, 5 PSN, 6 "") read as a scan of one word per condition from + 0x20 (the Black 2 dump has Odor
  Sleuth's foresight word on the enemy at + 0x60, which fits 4-byte condition words with paralysis at + 0x20),
  so + 0x20 alone is paralysis, not an enum; ours decodes it with the Gen 4 bitfield. Choose: copy the
  reference, or scan + 0x20 + 4 * (i - 1) for i = 1..7 as its loop evidently meant. A Gen 5 RAM dump with a
  burned or poisoned Pokemon in battle would confirm the layout first.
- FIXED 1961a18 (Gen 5): stat stages lack ACC/EVA and the validity reset to 6.
- FIXED aad5b79 (Black 2 heap shift): bag and location addresses not shifted with `live`; area name never updates.
- OPEN (Gen 5): doubles/triples/multi battles read only the first enemy.
- OPEN (Gen 5): in-battle player data from the party lead, not the active battler (stages/reveals after a switch).
- OPEN (Gen 5): faint/run-over detection differs (no battle-side HP, no HP-bar wait).
- FIXED 63e90bc (B/W): Statistics says "Past Lab", reference "Past N".
- UNSURE: party HP write-back in battle; Gen 5 party status format; Faster B2W2 header code / addresses
  (needs a dump); W2 pivot types; tourney tracker scope; starter number walk; ability slot seen when null;
  heal % truncation.

## Gen 1 / Gen 2 per game

- OPEN (RBY, LIKELY CRITICAL, verify on device): Gen1Map addresses are raw GB addresses on 0x02000000
  (party 0x0200D16B); LibretroDroid readMemory treats 0x02xxxxxx as a WRAM offset (0x2000/0x8000 bytes), so
  every read is out of range. Reference and our own GbcTracker use 0xDxxx -> 0x1xxx. Unit test's fake memory
  is 64 KB so it passes either way.
- OPEN (RBY): reference writes Repel (0x1E) into the Viridian Mart list at startup (ROM 0x2445 R/B, 0x233E Y).
- OPEN (GSC): badges 5/6 bit swap (Chuck/Jasmine) missing; Kanto 8 never drawn (GSC_K art bundled).
- OPEN (RBY): badge set "RBY" has no art; reference uses FRLG art.
- OPEN (GSC): enemy moves recorded from any byte; reference only known moves, one per turn.
- OPEN (GSC): Low Kick "WT"; Gen 2 reference power 50 acc 90.
- OPEN (RBY): Gen 1 type chart only on Type Defenses, not on move rows / move info matchups.
- DECIDED (Blake, 2026-09-29: "correct values"): Yellow gBattleTypeFlags 0xD056 (the reference's 0xD057 is a
  bug), Crystal party count 0xDCD7 (reference 0xCCD7), Gen 1 learn levels from the ROM (the reference's table
  disagrees with it on 24 species). Still open for Blake: Gen 2 move data from the randomized ROM (reference:
  its vanilla table), Low Kick by weight, Yellow's Repel replacing the Potion.
- UNSURE: extra title codes accepted by the reference; reference loss rule (battle end, trainer battles only).

## Auto Pokemon Themes (GBA and Game Boy DONE 2026-09-29; DS part waits for the DS data branch)

MIT (Fellshadow); palettes are the NDS tracker's own (GPL-3.0). One job covers the GBA extension (party
slot 1, egg skipped, user theme restored for species without one) and the NDS built-in option (active
battler in battle, forms, alternate positive/negative, type icons). Design: tools/trainer-data/
convert_autothemes.py (lupa lua54), Pc override colours, AutoTheme.kt, ThemeStore user colours kept apart,
gear toggles, PlayScreen hooks, isEgg (Gen 3), form (Gen 4), DS active battler; tests incl. wiring proof.

## Add-ons (wiki review, 2026-09-28)

Port first: Calc Atk (MIT), Faster Battle Intro (clean-room), Smacker Tracker run history for Gen 3 (MIT),
Auto Pokemon Themes (MIT, design in progress), FRLG tourney points (MIT). Small: Auto-Pedometer, editable
death quotes, I'm Attached, favorites on the overlay. Roguemon: ask permission first (no licence, ships a
patch and a randomizer jar). Never bundle ripped art (pop-out models, HGSS walking sprites, Stadium brows,
Infinite Fusion pack).

## Survival heal limits and Kaizo Doubles (2026-09-29)

Blake: "an official mode adds what its rules need, and the player keeps control". A Survival or Survival
Revival run switches its heal counter on at the rules' limit (10, or 5 after Revival's badge-1 heal) the first
time the run is read, once per run: the heart on Gen 1 to 3 (PcHeals.arm), the DS tracker's own "Show
Pokecenter heals" counter on DS (PcHeals.observeDsSurvival). The 8th badge adds the bonus heal once. A run the
app first meets mid-way (an update) keeps the count the player has and gets no second bonus. Counts are kept
per game: a new run clears whatever another game left under the same attempt number (PrepStore.bumpAttempt).
Counted by hand, as in the references: heals before the first trainer who is not the rival (free under the
rules) and the Johto +7 after the Elite Four (no Elite Four flag verified for GSC or HGSS).

Kaizo Doubles (the official settings gist): the run ends when either of your first two Pokemon faints
(LossCondition.EITHER_OF_FIRST_TWO). No PC tracker has it; the app offers it on every game, and a settings
file named "Doubles" chooses it.

