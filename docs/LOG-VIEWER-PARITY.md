# Gen 3 log viewer vs the PC tracker's LogOverlay

Checked 2026-10-05 for the "Log viewer parity check" (Blake, 2026-10-04, PC screenshots of Nat. Dex FireRed). PC source:
besteon/Ironmon-Tracker at c450ecae (local copy `C:/Users/bepor/ironmon-ref/Ironmon-Tracker`), `ironmon_tracker/screens/`.
Ours: `LogViewer.kt`, `LogPokemon.kt`, `LogTrainers.kt`, `LogRoutes.kt`, `LogTms.kt`, `LogMisc.kt`, `LogSearch*.kt`,
`LogInfoPanels.kt`. The DS viewer (`DsLogViewer.kt`) follows NDS-Ironmon-Tracker and is not part of this check.

On PC the log fills the game screen and the tracker's own panel stays beside it: opening a trainer or a Pokemon in the
log also changes that panel. A phone has no room beside the page, so those panels' lines now sit at the top of the page
they go with.

| PC page or panel | PC source | Ours | Status |
|---|---|---|---|
| Five tabs with their pictures (Pokemon, Trainers, Routes, TMs, Misc) | LogOverlay.lua:319-376 | `LogViewer` tab row, `LogTabIcon` | have |
| Search, filter (name, ability, move, route, trainer) and sort | LogSearchScreen.lua | `LogSearchBar`, `LogSearch` | have |
| Back through the pages opened | LogOverlay.lua:22, 158 | `back()` in `LogViewer` | have |
| Opens on the lead's page | LogOverlay.lua:602 | `LogOpen.leadPage` | have |
| Pages with arrows | LogOverlay.Windower | one scrolling list | have (scroll instead of pages) |
| Pokemon grid | LogTabPokemon.lua | `LogPokemonTab` | have |
| Pokemon page: abilities, evolutions with methods, pre-evolutions option, stat graph with Your IVs / Your EVs, Levelup Moves and TM Moves tabs, gym TMs, unlearnable gym TMs option | LogTabPokemonDetails.lua | `LogPokemonDetail` | have |
| Tap an ability or a move for its info | LogTabPokemonDetails.lua:155, 627, 736; LogTabTrainerDetails.lua:175; LogTabTMs.lua:124 | none | skipped: the log has no move or ability info screen yet (also open in parity/gaps.tsv, "No move or ability look-up"); worth doing next |
| Trainers grid, filters All / Rival / Gym / Elite 4 / Boss | LogTabTrainers.lua | `LogTrainersTab` | have |
| Trainer page: badge, class and name (custom names option, grunt number), team with level, four moves (same type green), held item | LogTabTrainerDetails.lua | `LogTrainerDetail` | have |
| Trainer Info panel: route, "Pokémon: N (Lv.a -- b)" red when your lead is lower, Avg. IVs, AI Script, Usable Items, # id, Double Battle | TrainerInfoScreen.lua:22-311 | `LogTrainerInfoPanel` (`LogInfoPanels.trainer`) at the top of the trainer page, from the ROM's own trainer entry (`GbaTracker.trainer`) | **added** |
| Trainer Info panel: the team as Poke Balls, shown once beaten | TrainerInfoScreen.lua:313-385 | the trainer page shows the whole team | skipped: the page under it already shows every Pokemon |
| Trainer Info panel: tap the route for its trainers; tap the portrait for a random trainer | TrainerInfoScreen.lua:35-46, 89-96 | none | skipped: in-game screens, not log data |
| Routes list: location, trainers and wild Pokemon with level ranges | LogTabRoutes.lua | `LogRoutesTab` | have |
| Route page: trainers, encounters by area with level range and rate | LogTabRouteDetails.lua | `LogRouteDetail` | have |
| Route info panel (the game's own table, Show percentages / levels) | LogTabRoutes.lua:241 | none in the log | skipped: the route page shows the log's rates and levels; the in-game route info screen has the game's own |
| TMs: Gym TMs with leader and badge, or by TM number; leader opens the trainer | LogTabTMs.lua | `LogTmsTab` | have (the trainer page now carries its info panel too) |
| Misc: three options, Share Seed, game, version, seed, settings string | LogTabMisc.lua | `LogMiscTab` | have (ours also lists starters, statics and pickup items, marked as not on PC) |
| Pokemon info panel: name, types from the log, BST | InfoScreen.lua:678-718 | the page's header and stat graph | have |
| Pokemon info panel: evolution and learn levels | InfoScreen.lua:732-806 | the page's evolution row and Levelup Moves tab | have |
| Pokemon info panel: Weak to (2x and 4x, from the log's types) | InfoScreen.lua:808-845 | `LogPokemonInfoPanel` | **added** |
| Pokemon info panel: History (the run's tracked moves and learn levels) | InfoScreen.lua:98-114, MoveHistoryScreen.lua:88 | `LogPokemonInfoPanel` opens `MoveHistoryDialog` with the run's tracked moves | **added** |
| Pokemon info panel: Show resistances | InfoScreen.lua:116-129 | `LogPokemonInfoPanel` opens `TypeDefensesDialog` for the log's types | **added** |
| Pokemon info panel: note, "(Leave a note)", tap to write one | InfoScreen.lua:279-295, 1127-1147 | `LogPokemonInfoPanel` and `LogNoteDialog`, saved into the run's notes Play uses (`RunMarks`) | **added** |
| Pokemon info panel: EXP yield, weight, View Evos, previous / next, look-up | InfoScreen.lua:61-96, 712-725 | none in the log | skipped: EXP yield is not read anywhere in the app yet (parity/gaps.tsv); the others are the in-game info screen's |

Wording notes: "Weak to:", "Has no weaknesses", "History", "Show resistances", "(Leave a note)", "Avg. IVs", "Usable
Items" and the level range "Lv.51 -- 54" are the PC tracker's English (Languages/English.lua:135, 419-428, 471-475;
TrainerInfoScreen.lua:244). The script line reads "AI Script", the PC's word (its English spells it "A I Script" for
its pixel font; Blake, 2026-10-05).

## Images

Blake, 2026-10-05: "make sure the log uses the game images as the pc tracker log and our logs". Every place the PC log
draws a picture, ours draws the game's own, got the way the app's other screens get it:

- **Pokemon stills** go through Play's `spriteFor`: read from the player's ROM on vanilla FireRed, LeafGreen, Ruby,
  Sapphire and Emerald (`GbaTracker.sprite`), the bundled pack on Nat. Dex and MaxDex (`gbasprites`,
  `gbasprites-maxdex`) and on Red to Crystal (Gen 3's first 251), as the tracker card does. The species asked for is
  `LogPictureIds.species`: the tracker's id for the log's name (`LogNames`) on Gen 3, never the log's number (MaxDex's
  log numbers MewtwoX 1039, the tracker's Okidogi), and the log's own national number on a Game Boy run.
- **Animated icons**: the PC tracker's icons, Walking Pals, standing idle where the PC draws `SpriteData.Types.Idle`,
  numbered by `LogPictureIds.palDex` (Gen 3, Nat. Dex, MaxDex or national); the still when Animated sprites is off.
- **Trainers and the player**: portraits and the player's head read from the ROM (`GbaTracker.trainerPicture`,
  `TrainerPictures.head`), badges from the bundled badge art (`PcAssets.badge`).
- **DS** (`DsLogViewer`): every Pokemon picture is the ROM's own (`RomFormSprites`, then the gen4 pack, `DsPictures`).

| PC picture | PC source | GBA (vanilla, Nat. Dex, MaxDex) | Game Boy | DS |
|---|---|---|---|---|
| Tab pictures | LogTabPokemon.lua:8, LogTabTrainers.lua:9-65, LogTabRoutes.lua:9, LogTabTMs.lua:9, LogTabMisc.lua:9 | have (`LogTabIcon`: two idle Pokemon, the player's head from the ROM, drawn map, TM and PC) | have (the head needs a Gen 3 ROM, so a blank there) | n/a: the NDS log's tabs are words |
| Pokemon grid icons | LogTabPokemon.lua:97-135; Gen 1/2 LogOverlay.lua:461 | have | **added** (`LogMonIcon` on each row) | have (overview, stats) |
| Pokemon page icon | LogTabPokemonDetails.lua:266-270, InfoScreen.lua:61-82 (the new info panel's icon is the page's) | have | **added** | have |
| Evolution and pre-evolution icons | LogTabPokemonDetails.lua:225-231, 303-309; Gen 1/2 LogOverlay.lua:972 | have | **added**, a tap opens it | have |
| Trainer portraits on the Trainers tab and route pages | LogTabTrainers.lua (drawTrainerPortraitInfo), LogTabRouteDetails.lua:271-274 | have | n/a: the PC Gen 1/2 log draws none (its trainer icon is commented out, LogOverlay.lua:1749) | skipped: the NDS tracker's are its own art (`images/trainers/*_vs.png`); no DS screen of ours draws trainers and the app reads no DS trainer pictures from the ROM |
| Trainer page portrait and badge (and the new Trainer Info panel's portrait, TrainerInfoScreen.lua:23-63) | LogTabTrainerDetails.lua:259-267 | have (the panel sits under the page's portrait) | badge skipped: our Game Boy log has no gym grouping to hang it on | badges have (`DsTrainerGroups`, `DsGymTms`) |
| Trainer team icons | LogTabTrainerDetails.lua:98-129; Gen 1/2 LogOverlay.lua:1277 | have | **added** | have (`DsTeamMon`) |
| Route page wild Pokemon | LogTabRouteDetails.lua:367-412 (walking) | **added** the idle icon (it was the still only); ours stands idle, the PC's walks | n/a: no routes tab on the PC Gen 1/2 log | n/a: the NDS Pivots page draws none |
| Route page Trainers choice: the player's head | LogTabRouteDetails.lua:136-140 | **added** | n/a | n/a |
| Route page area pictures (grass, surf, rods), route list signs and column heads | LogTabRouteDetails.lua:142-190, LogTabRoutes.lua:77-89, 136, 264 | skipped: the PC tracker's own drawn signs, not game pictures; ours name the area in words | n/a | n/a |
| TM badges | LogTabTMs.lua:165; Gen 1/2 LogOverlay.lua:1349 | have | skipped (no gym TMs on our Game Boy log) | have |
| Search suggestions' icons | LogSearchScreen.lua | have | n/a (no Gen 3 search there) | have |
| Type pictures (page, info panel's Weak to) | InfoScreen.lua:719-731, 820-845 | KaizoCore's type pills, as on every tracker screen (Blake, 2026-10-02) | same | same |
