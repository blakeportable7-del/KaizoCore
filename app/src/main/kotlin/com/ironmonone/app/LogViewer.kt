package com.ironmonone.app

import androidx.compose.ui.graphics.asImageBitmap

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * "Inspect the log", full screen: the reference's LogOverlay with its five
 * tabs (Pokemon, Trainers, Routes, TMs, Misc). The Pokemon tab lists every
 * species with types and BST; a tap opens the reference's LogTabPokemonDetails:
 * stats, abilities, level-up moves, evolutions and the TMs it can learn.
 * Trainers, on Gen 3, follow LogTabTrainers: filter by Rival, Gym, Elite 4 or
 * Boss, and a trainer opens LogTabTrainerDetails with each Pokemon's four
 * moves at its level and its held item; other generations keep the plain
 * list of parties with levels and held items. Routes are
 * the wild sets with each encounter's level band (on Gen 3 by map, as
 * LogTabRoutes and LogTabRouteDetails lay them out). TMs are the moves in the
 * machines (on Gen 3 as LogTabTMs: the gym TMs with their leaders, or every
 * TM by number). Misc is the run's version, seed, settings string, starters and
 * static encounters.
 *
 * On a DS game it is NDS-Ironmon-Tracker's LogViewer instead (DsLogViewer):
 * the Pokemon, Trainers, Pivots, Gym TMs, Info and Search tabs.
 *
 * Opened from the game-over screen only (Blake, 2026-09-10: the log is for
 * after a loss), it reads the log
 * the randomizer wrote beside the current run's ROM.
 */
@Composable
fun LogViewer(
    file: File,
    onClose: () -> Unit,
    /** Gen 3: the tracker that played the run, for the trainer tables, move types and names. */
    tracker: com.ironmonone.tracker.GbaTracker? = null,
    /** "RSE" or "FRLG" picks the trainer rules and the badge art. */
    badgeSet: String? = null,
    spriteFor: ((Int) -> androidx.compose.ui.graphics.ImageBitmap?)? = null,
    /** The run's party, for "Your IVs" and "Your EVs" on a team member's page. */
    party: List<com.ironmonone.tracker.TrackedMon>? = null,
    /** DS: the tracker that played the run, which picks the game's tables; the viewer becomes the DS tracker's. */
    nds: com.ironmonone.tracker.nds.NdsTracker? = null,
    ndsParty: List<com.ironmonone.tracker.nds.NdsTrackedMon>? = null,
) {
    if (nds != null) { DsLogViewer(file, nds, ndsParty, onClose); return }
    var tab by remember { mutableStateOf(LogTab.POKEMON) }
    var query by remember { mutableStateOf("") }
    var detail by remember { mutableStateOf<RandomizerLog.Pokemon?>(null) }
    var trainerDetail by remember { mutableStateOf<RandomizerLog.Trainer?>(null) }
    var routeDetail by remember { mutableStateOf<LogRoute?>(null) }
    var tmFilter by remember { mutableStateOf(LogTmFilter.GYM) }
    // LogSearchScreen: the filter and sort, reset to the tab's defaults when the tab changes.
    var searchSort by remember { mutableStateOf<LogSort?>(null) }
    var searchFilter by remember { mutableStateOf<LogFilter?>(null) }
    var sortPicked by remember { mutableStateOf(false) }
    // By species id: keyed by the tracker's name, MaxDex's "MewtwoX" never found its "Mewtwo-X" (rc34).
    val team = remember(party) {
        party?.associate { tm -> tm.mon.species to (LogSearch.refOrder(tm.mon.ivs) to LogSearch.refOrder(tm.mon.evs)) } ?: emptyMap()
    }
    var trainerFilter by remember { mutableStateOf(LogTrainerFilter.GYM) }
    val rules = remember(tracker, badgeSet) {
        // Heart & Soul's map says "GSC" for its two rows of badges; its rules are its own (LogTrainerRules.hns).
        if (tracker != null && (badgeSet == "RSE" || badgeSet == "FRLG" || tracker.heartSoul)) LogTrainerRules(tracker, badgeSet == "FRLG") else null
    }
    // The badge art: Heart & Soul's sixteen in HeartGold and SoulSilver's (PcAssets.HNS_BADGES), any other game its own.
    val badgeArt = if (tracker?.heartSoul == true) PcAssets.HNS_BADGES else badgeSet
    // The log, its routes, the move types and the species ids, read off the main thread: the first composition
    // parsed the whole log and read the ROM's tables on it, and the screen froze after the tap (rc32 audit P2 #27).
    // Null until it lands, and the dialog says it is reading.
    val loaded by produceState<LogViewData?>(null, file, rules) {
        value = withContext(Dispatchers.Default) { loadLogView(file, tracker, rules) }
    }
    val log = loaded?.log
    val moveTypes = loaded?.moveTypes ?: emptyMap()
    val names = loaded?.names ?: LogNames.PLAIN
    val logRoutes = loaded?.routes ?: emptyList()
    // Program.openLogFromPath (LogOverlay.lua): the log opens on the Pokemon tab with the search and sort reset, then on
    // the lead's page when the run has one, as DsLogViewer does (Blake, 2026-10-04). Back from it is the grid.
    var openedOnLead by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(log) {
        if (log != null && !openedOnLead) {
            openedOnLead = true
            detail = LogOpen.leadPage(log.pokemon, party?.firstOrNull()?.mon?.species, names::speciesId)
        }
    }
    // No Gen 3 tracker here means a Game Boy run (a DS run went to DsLogViewer above): its log numbers Pokemon by
    // national dex, as its tracker does (LogPictureIds).
    val gameBoy = tracker == null
    // Heart & Soul draws the game's own pictures (GbaTracker.frontPicture), the pack's where one does not decode.
    val romPics = remember(tracker) { HashMap<Int, androidx.compose.ui.graphics.ImageBitmap?>() }
    val packSprite: ((RandomizerLog.Pokemon) -> androidx.compose.ui.graphics.ImageBitmap?)? =
        spriteFor?.let { sf -> { p: RandomizerLog.Pokemon -> LogPictureIds.species(p, names, gameBoy)?.let(sf) } }
    val logSprite: ((RandomizerLog.Pokemon) -> androidx.compose.ui.graphics.ImageBitmap?)? =
        if (tracker?.heartSoul == true && packSprite != null) { p: RandomizerLog.Pokemon ->
            names.speciesId(p.name)?.let { id ->
                romPics.getOrPut(id) {
                    tracker.frontPicture(id)?.let { android.graphics.Bitmap.createBitmap(it.argb, it.width, it.height, android.graphics.Bitmap.Config.ARGB_8888).asImageBitmap() }
                }
            } ?: packSprite(p)
        } else packSprite
    // The pictures (LogPictures): Walking Pals standing idle where the PC tracker animates its icons, numbered the tracker's
    // way (none with Animated sprites off), the trainers' portraits and the tabs' small pictures.
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val palIndex = WalkingPals.ready(ctx)
    val palDex = remember(tracker) { LogPictureIds.palDex(tracker) }
    val logPal: ((RandomizerLog.Pokemon) -> WalkingPals.Pal?)? =
        if (palIndex == null || !TrackerOptions.animatedSprites) null
        else { p -> LogPictureIds.species(p, names, gameBoy)?.let { palIndex.find(it, palDex) } }
    val portraitImages = remember(loaded) { HashMap<Int, androidx.compose.ui.graphics.ImageBitmap>() }
    val portraitOf: (RandomizerLog.Trainer) -> androidx.compose.ui.graphics.ImageBitmap? = { t ->
        portraitImages[t.number] ?: loaded?.portraits?.get(t.number)?.let { px -> argbImage(px, 64, 64).also { portraitImages[t.number] = it } }
    }
    val tabPals = remember(palIndex) {
        palIndex?.let { ix -> LogPictures.TAB_POKEMON.shuffled().mapNotNull { ix.find(it, WalkingPals.Dex.GEN3) }.take(LogPictures.TAB_POKEMON_SHOWN) }.orEmpty()
    }
    val playerHead = remember(loaded) { loaded?.playerHead?.let { argbImage(it, 24, 22) } }
    // The PC tracker's side panels (LogInfoPanels): the run's notes and tracked moves are Play's own (RunMarks), so a
    // note left here is the one the tracker shows.
    val runMarks = RunMarks.current
    var noteVersion by remember { mutableStateOf(0) }
    var historyOf by remember { mutableStateOf<Pair<Int, String>?>(null) }
    var resistancesOf by remember { mutableStateOf<Pair<String, Map<Double, List<String>>>?>(null) }
    var noteOf by remember { mutableStateOf<Pair<Int, String>?>(null) }
    // System Back closes the open detail page first, in the order the pages stack
    // (a Pokemon over a trainer over a route), as DsLogViewer does; it used to close
    // the whole viewer from a detail page (2026-09-27, audit).
    fun back() {
        when {
            detail != null -> detail = null
            trainerDetail != null && rules != null -> trainerDetail = null
            routeDetail != null && rules != null -> routeDetail = null
            else -> onClose()
        }
    }
    Dialog(onDismissRequest = { back() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(Pc.Ground).padding(horizontal = 8.dp, vertical = 6.dp)) {
            historyOf?.let { (sp, n) ->
                MoveHistoryDialog(n, 0, runMarks?.movesSeenFor(sp).orEmpty(), tracker?.learnset(sp)?.map { it.first }.orEmpty()) { historyOf = null }
            }
            resistancesOf?.let { (n, b) -> TypeDefensesDialog(n, b) { resistancesOf = null } }
            noteOf?.let { (sp, n) ->
                LogNoteDialog(n, runMarks?.noteFor(sp).orEmpty(), onSave = { text ->
                    runMarks?.setNote(sp, text); noteVersion++; noteOf = null
                }) { noteOf = null }
            }
            // The title, and the X in the top right, which takes the log away: the game-over popup is where you land.
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                DialogText("Randomizer log", 16, Pc.Gold, Modifier.weight(1f), heading = true)
                Box(
                    Modifier.size(PcMin.DIALOG_TOUCH_DP.dp).background(Pc.Page).border(1.dp, Pc.Border).clickable { onClose() }
                        .semantics { contentDescription = "Close the log" },
                    contentAlignment = Alignment.Center,
                ) { DialogText("X", 15, Pc.Text) }
            }
            Spacer(Modifier.height(6.dp))
            // The tabs share the width, each a full touch target, the open one gold and underlined, its small picture
            // above its name as the PC tracker draws one beside it (each LogTab*.lua's TabIcons).
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                LogTab.entries.forEach { t ->
                    val on = t == tab
                    Column(
                        Modifier.weight(1f).heightIn(min = PcMin.DIALOG_TOUCH_DP.dp).background(if (on) Pc.Page else Pc.Ground)
                            .border(if (on) 2.dp else 1.dp, if (on) Pc.Gold else Pc.Border)
                            .clickable { tab = t; detail = null; trainerDetail = null; routeDetail = null; query = ""; searchSort = null; searchFilter = null; sortPicked = false }
                            .padding(vertical = 3.dp),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                    ) {
                        LogTabIcon(t, tabPals, playerHead, if (on) Pc.Gold else Pc.Text)
                        DialogText(t.label, 12, if (on) Pc.Gold else Pc.Text, underline = on)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            if (loaded == null) {
                DialogText("Reading the log.", 13, Pc.Dim)
                return@Column
            }
            if (log == null) {
                DialogText("The log could not be read.", 13, Pc.Negative)
                return@Column
            }
            val d = detail
            if (d != null) {
                if (rules != null) LogPokemonDetail(d, log, badgeSet == "FRLG", logSprite, moveTypes, names.speciesId(d.name)?.let { team[it] }, names,
                    onPokemon = { detail = it }, onBack = { detail = null },
                    // The same PokemonData evolution the info screen reads, in its short words.
                    evoMethodsOf = tracker?.let { t -> { lp -> names.speciesId(lp.name)?.let { com.ironmonone.tracker.EvoText.short(t.evolution(it)) } ?: emptyList() } },
                    palOf = logPal, gymTms = rules.gymTms,
                    infoPanel = {
                        val sp = names.speciesId(d.name)
                        val shown = names.species(d.name)
                        val natDex = tracker?.expandedSpeciesIds == true
                        val weak = remember(d, natDex) { LogInfoPanels.weakTo(d.types, natDex) }
                        val note = noteVersion.let { sp?.let { runMarks?.noteFor(it) }.orEmpty() }
                        LogPokemonInfoPanel(
                            weak, note,
                            onHistory = if (sp != null && runMarks != null) { { historyOf = sp to shown } } else null,
                            onResistances = { resistancesOf = shown to LogInfoPanels.defenses(d.types, natDex) },
                            onNote = if (sp != null && runMarks != null) { { noteOf = sp to shown } } else null,
                        )
                    })
                else PokemonDetail(d, log, onBack = { detail = null }, spriteOf = logSprite, palOf = logPal, onPokemon = { detail = it })
                return@Column
            }
            val td = trainerDetail
            if (td != null && rules != null) {
                LogTrainerDetail(td, log, rules, TrackerOptions.logCustomTrainerNames, badgeArt, logSprite, moveTypes,
                    onPokemon = { detail = it }, onBack = { trainerDetail = null }, names = names,
                    infoPanel = tracker?.let { tr ->
                        @Composable {
                            // TrainerInfoScreen.buildScreen: the game's own gTrainers entry, read from the ROM in play.
                            val info = remember(td.number) { runCatching { tr.trainer(td.number) }.getOrNull() }
                            if (info != null) LogTrainerInfoPanel(
                                LogInfoPanels.trainer(info, party?.firstOrNull()?.mon?.level) { id ->
                                    // Resources.Game.ItemNames has no entry for 0 (TrainerInfoScreen.lua:293).
                                    tr.itemName(id)?.takeIf { id != 0 && it.isNotBlank() && !it.startsWith("#") }
                                },
                                logRoutes.firstOrNull { r -> r.trainers.any { it.number == td.number } }?.name,
                            )
                        }
                    },
                    portrait = portraitOf(td), palOf = logPal)
                return@Column
            }
            val rd = routeDetail
            if (rd != null && rules != null) {
                LogRouteDetail(rd, rules, TrackerOptions.logCustomTrainerNames, logSprite,
                    onTrainer = { trainerDetail = it }, onPokemon = { detail = it }, onBack = { routeDetail = null }, names = names,
                    playerHead = playerHead, palOf = logPal, portraitOf = portraitOf)
                return@Column
            }
            val curSort = searchSort ?: LogSearch.defaultSort(tab)
            val curFilter = searchFilter ?: LogSearch.defaultFilter(tab)
            // What the search suggests while it is typed: Pokemon by name (a tap opens one on the Pokemon tab, and
            // filters by it on the others), or the words the chosen filter searches.
            val suggestFor = curFilter ?: LogFilter.NAME
            val suggestions = remember(log, query, suggestFor, logRoutes, names) {
                when (suggestFor) {
                    LogFilter.NAME -> LogSuggest.pokemon(log, query, names = names).map { LogSuggestion(names.speciesAsWritten(it.name), it, names.species(it.name)) }
                    LogFilter.ABILITY -> LogSuggest.words(log.pokemon.flatMap { it.abilities }, query).map { LogSuggestion(it) }
                    LogFilter.MOVE -> LogSuggest.words(log.pokemon.flatMap { p -> p.moves.map { it.second } + p.evoMoves }, query).map { LogSuggestion(it) }
                    LogFilter.ROUTE -> LogSuggest.words(if (logRoutes.isNotEmpty()) logRoutes.map { it.name } else log.routes.map { it.name }, query).map { LogSuggestion(it) }
                    LogFilter.TRAINER -> LogSuggest.words(log.trainers.map { logTitle(it.originalName) }, query).map { LogSuggestion(it) }
                }
            }
            fun pick(s: LogSuggestion) {
                val p = s.pokemon
                if (p != null && tab == LogTab.POKEMON) detail = p else query = s.label
            }
            if (tab != LogTab.MISC && tab != LogTab.TMS && rules != null && curSort != null && curFilter != null) {
                LogSearchBar(query, { query = it }, LogSearch.sortsFor(tab), curSort, { searchSort = it; sortPicked = true },
                    LogSearch.filtersFor(tab), curFilter, { searchFilter = it }, suggestions, logSprite, ::pick)
                Spacer(Modifier.height(6.dp))
            } else if (tab != LogTab.MISC && tab != LogTab.TMS) {
                LogSearchField(query, { query = it }, LogSearch.hint(if (tab == LogTab.POKEMON) LogFilter.NAME else null).let {
                    if (tab == LogTab.POKEMON) it else "Search"
                }, if (tab == LogTab.POKEMON) suggestions else emptyList(), logSprite, ::pick)
                Spacer(Modifier.height(8.dp))
            }
            val q = query.trim()
            when (tab) {
                // A log with no Pokemon list says why rather than "(No results)" (rc32 audit P2 #71).
                LogTab.POKEMON -> if (log.pokemon.isEmpty() && log.statsUnchanged) {
                    DialogText(NO_POKEMON_IN_LOG, 13, Pc.Dim)
                } else if (rules != null) {
                    val rows = remember(log, q, curFilter, curSort, names) { LogSearch.pokemonRows(log, q, curFilter ?: LogFilter.NAME, curSort ?: LogSort.POKEDEX, names) }
                    LogPokemonTab(rows, logSprite, onPokemon = { detail = it }, names = names, palOf = logPal)
                } else {
                    val rows = log.pokemon.filter { q.isEmpty() || it.name.contains(q, true) || it.types.any { t -> t.contains(q, true) } || it.abilities.any { a -> a.contains(q, true) } }
                    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        items(rows, key = { it.id }) { p ->
                            Row(Modifier.fillMaxWidth().background(Pc.Page).border(1.dp, Pc.Border).clickable { detail = p }.padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                // The PC Gen 1 and 2 trackers' log grid draws each one's icon (LogOverlay.lua:461).
                                LogMonIcon(logSprite?.invoke(p), logPal?.invoke(p), 32.dp, null)
                                Spacer(Modifier.width(6.dp))
                                DialogText("%03d".format(p.id), 12, Pc.Dim, Modifier.width(30.dp))
                                DialogText(p.name, 13, Pc.Text, Modifier.weight(1f))
                                DialogText(p.types.joinToString("/"), 12, Pc.Dim, Modifier.width(96.dp))
                                DialogText("BST ${p.bst}", 12, Pc.Gold)
                            }
                        }
                    }
                }
                LogTab.TRAINERS -> if (rules != null) {
                    LogTrainersTab(log, rules, q, trainerFilter, TrackerOptions.logCustomTrainerNames,
                        onFilter = { trainerFilter = it; query = ""; sortPicked = false }, onTrainer = { trainerDetail = it },
                        searchBy = curFilter ?: LogFilter.TRAINER, sortBy = if (sortPicked) curSort else null, names = names, portraitOf = portraitOf)
                } else {
                    val rows = log.trainers.filter { q.isEmpty() || it.fullName.contains(q, true) || it.originalName.contains(q, true) || it.party.any { m -> m.name.contains(q, true) } }
                    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        items(rows, key = { it.number }) { t ->
                            Column(Modifier.fillMaxWidth().background(Pc.Page).border(1.dp, Pc.Border).padding(6.dp)) {
                                Row(Modifier.fillMaxWidth()) {
                                    DialogText("#${t.number}", 12, Pc.Dim, Modifier.width(34.dp))
                                    DialogText(t.fullName, 13, Pc.Text, Modifier.weight(1f))
                                    if (t.name.isNotBlank()) DialogText(t.originalName, 12, Pc.Dim)
                                }
                                Spacer(Modifier.height(3.dp))
                                t.party.forEach { m ->
                                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                        // The PC Gen 1 and 2 trackers' trainer page draws its team's icons (LogOverlay.lua:1277).
                                        val mon = log.pokemonNamed(m.name)
                                        LogMonIcon(mon?.let { logSprite?.invoke(it) }, mon?.let { logPal?.invoke(it) }, 28.dp, null)
                                        Spacer(Modifier.width(6.dp))
                                        DialogText(m.name, 12, Pc.Text, Modifier.weight(1f))
                                        m.item?.let { DialogText(it, 12, Pc.Gold, Modifier.padding(end = 8.dp)) }
                                        DialogText("Lv${m.level}", 12, Pc.Dim)
                                    }
                                }
                            }
                        }
                    }
                }
                LogTab.ROUTES -> if (rules != null) {
                    val rows = remember(logRoutes, q, curFilter, curSort, names) { LogSearch.routeRows(logRoutes, log, q, curFilter ?: LogFilter.ROUTE, curSort ?: LogSort.WILD, names) }
                    LogRoutesTab(rows, "", onRoute = { routeDetail = it })
                } else {
                    val rows = log.routes.filter { q.isEmpty() || it.name.contains(q, true) || it.encounters.any { e -> e.name.contains(q, true) } }
                    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        items(rows, key = { it.number }) { r ->
                            Column(Modifier.fillMaxWidth().background(Pc.Page).border(1.dp, Pc.Border).padding(6.dp)) {
                                Row(Modifier.fillMaxWidth()) {
                                    DialogText(r.name, 13, Pc.Text, Modifier.weight(1f))
                                    DialogText("rate ${r.rate}", 12, Pc.Dim)
                                }
                                Spacer(Modifier.height(3.dp))
                                // The reference collapses the set to one row per species with its level band.
                                r.encounters.groupBy { it.name }.forEach { (name, es) ->
                                    val lo = es.minOf { it.minLevel }; val hi = es.maxOf { it.maxLevel }
                                    Row(Modifier.fillMaxWidth()) {
                                        DialogText(name, 12, Pc.Text, Modifier.weight(1f))
                                        DialogText(if (lo == hi) "Lv$lo" else "Lv$lo-$hi", 12, Pc.Dim)
                                    }
                                }
                            }
                        }
                    }
                }
                LogTab.TMS -> if (rules != null) {
                    LogTmsTab(log, rules, badgeSet == "FRLG", TrackerOptions.logCustomTrainerNames, badgeArt, tmFilter,
                        onFilter = { tmFilter = it }, onTrainer = { trainerDetail = it }, names = names)
                } else {
                    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        items(log.tms, key = { it.number }) { t ->
                            Row(Modifier.fillMaxWidth().background(Pc.Page).border(1.dp, Pc.Border).padding(6.dp)) {
                                DialogText("TM%02d".format(t.number), 12, Pc.Dim, Modifier.width(44.dp))
                                DialogText(t.move, 13, Pc.Text)
                            }
                        }
                    }
                }
                LogTab.MISC -> LogMiscTab(log, names)
            }
        }
    }
}

enum class LogTab(val label: String) { POKEMON("POKEMON"), TRAINERS("TRAINERS"), ROUTES("ROUTES"), TMS("TMS"), MISC("MISC") }

/** What the Pokemon tab says for a log that lists no Pokemon because the randomizer did not change them. */
internal const val NO_POKEMON_IN_LOG = "The randomizer left the Pokémon as the game has them, so this log does not list them."

/** What the Gen 3 log viewer draws from, read off the main thread (rc32 audit P2 #27). */
internal class LogViewData(
    val log: RandomizerLog?,
    val moveTypes: Map<String, Int>,
    /** The game's names and species ids for the log's names (LogNames); LogNames.PLAIN with no tracker. */
    val names: LogNames,
    val routes: List<LogRoute>,
    /** Each trainer's portrait by its number, 64x64 ARGB from the ROM (GbaTracker.trainerPicture); none where the build has none. */
    val portraits: Map<Int, IntArray> = emptyMap(),
    /** The Trainers tab's head, cut from the player's own picture (TrainerPictures.head), 24x22 ARGB; null where there is none. */
    val playerHead: IntArray? = null,
)

/**
 * The log (shared with Play's Open Book through RandomizerLog's cache), and on a Gen 3 run with its tracker the move
 * types for the same-type coloring, the game's names and species ids for the log's names (the log numbers Pokemon by
 * National Dex, which parts from the game's own numbering after 251, and MaxDex's log cuts names to ten letters) and
 * the routes.
 */
internal fun loadLogView(file: File, tracker: com.ironmonone.tracker.GbaTracker?, rules: LogTrainerRules?): LogViewData {
    val log = RandomizerLog.parse(file)
    // Heart & Soul has no trainer rules here (they are Emerald's and FireRed's), but its names, sprites and move types
    // are the tracker's like any Gen 3 game's: without them its log showed every Pokemon by name only.
    // Its trainers' portraits are the game's own pictures (rc38.1, Blake: "No image of trainer"), read through
    // gTrainerSprites (HnsPics.trainer).
    if (rules == null && tracker != null && tracker.heartSoul) return LogViewData(log, logMoveTypes(tracker), LogNames.of(tracker), emptyList(),
        portraits = log?.trainers.orEmpty().mapNotNull { t -> tracker.trainerPicture(t.number)?.let { t.number to it } }.toMap())
    if (rules == null || tracker == null) return LogViewData(log, emptyMap(), LogNames.PLAIN, emptyList())
    return LogViewData(
        log, logMoveTypes(tracker),
        // MaxDex's log names Pokemon as its ROM does, cut to ten letters: LogNames finds those through GbaTracker.logSpeciesIds.
        LogNames.of(tracker),
        if (log == null) emptyList() else LogRoutes.build(log, rules, tracker),
        // The portraits of the trainers in play, each picture decoded once (the tracker keeps them).
        portraits = log?.trainers.orEmpty().filter { rules.use(it.number) }
            .mapNotNull { t -> tracker.trainerPicture(t.number)?.let { t.number to it } }.toMap(),
        playerHead = log?.let { tracker.playerPicture(LogPictures.girlFor(it.seed)) }?.let { com.ironmonone.tracker.TrainerPictures.head(it) },
    )
}

/**
 * Every move of the game by upper-case name to its type: up to the game's own last move, which on the Nat. Dex builds
 * is 847 (rc32 audit P2 #28: it stopped at 354, so Roost or Dragon Pulse were never drawn as same-type).
 */
internal fun logMoveTypes(t: com.ironmonone.tracker.GbaTracker): Map<String, Int> =
    (1..t.lastMoveId).mapNotNull { id -> t.moveRowFor(id)?.let { r -> r.type?.let { ty -> r.name.uppercase() to ty } } }.toMap()

/**
 * The reference's LogTabPokemonDetails: one species from the log, on a Game Boy run. Its icon and its evolutions' icons
 * as the PC Gen 1 and 2 trackers draw them (LogOverlay.lua:948, 972), a tap on an evolution opening its page.
 */
@Composable
private fun PokemonDetail(
    p: RandomizerLog.Pokemon, log: RandomizerLog, onBack: () -> Unit,
    spriteOf: ((RandomizerLog.Pokemon) -> androidx.compose.ui.graphics.ImageBitmap?)? = null,
    palOf: ((RandomizerLog.Pokemon) -> WalkingPals.Pal?)? = null,
    onPokemon: (RandomizerLog.Pokemon) -> Unit = {},
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(Modifier.fillMaxWidth().background(Pc.Page).border(1.dp, Pc.Border).padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
            LogBack(onBack)
            LogMonIcon(spriteOf?.invoke(p), palOf?.invoke(p), 40.dp, null)
            Spacer(Modifier.width(6.dp))
            DialogText(p.name, 15, Pc.Gold, Modifier.weight(1f))
            DialogText(p.types.joinToString("/"), 12, Pc.Text)
        }
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Column(Modifier.weight(1f).background(Pc.Page).border(1.dp, Pc.Border).padding(6.dp)) {
                p.statNames.forEachIndexed { i, n ->
                    Row(Modifier.fillMaxWidth()) {
                        DialogText(n, 12, Pc.Dim, Modifier.width(34.dp))
                        DialogText("${p.stats[i]}", 12, Pc.Text)
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 3.dp)) {
                    DialogText("BST", 12, Pc.Gold, Modifier.width(34.dp))
                    DialogText("${p.bst}", 12, Pc.Gold)
                }
            }
            Column(Modifier.weight(1f).background(Pc.Page).border(1.dp, Pc.Border).padding(6.dp)) {
                if (p.abilities.isNotEmpty()) {
                    DialogText("Abilities", 12, Pc.Dim)
                    p.abilities.forEach { DialogText(it, 12, Pc.Text) }
                }
                if (p.item.isNotBlank()) { DialogText("Item", 12, Pc.Dim); DialogText(p.item, 12, Pc.Text) }
                if (p.evolutions.isNotEmpty()) {
                    DialogText("Evolves into", 12, Pc.Dim)
                    p.evolutions.forEach { e ->
                        val evo = log.pokemonNamed(e)
                        Row(
                            Modifier.heightIn(min = PcMin.DIALOG_TOUCH_DP.dp).then(if (evo != null) Modifier.clickable { onPokemon(evo) } else Modifier),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            LogMonIcon(evo?.let { spriteOf?.invoke(it) }, evo?.let { palOf?.invoke(it) }, 32.dp, null)
                            Spacer(Modifier.width(4.dp))
                            DialogText(e, 12, Pc.Text)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Column(Modifier.fillMaxWidth().background(Pc.Page).border(1.dp, Pc.Border).padding(6.dp)) {
            DialogText("Level-up moves", 12, Pc.Dim)
            p.moves.forEach { (lv, mv) ->
                Row(Modifier.fillMaxWidth()) { DialogText("Lv$lv", 12, Pc.Dim, Modifier.width(40.dp)); DialogText(mv, 12, Pc.Text) }
            }
            if (p.evoMoves.isNotEmpty()) {
                Spacer(Modifier.height(3.dp)); DialogText("On evolution", 12, Pc.Dim)
                p.evoMoves.forEach { DialogText(it, 12, Pc.Text) }
            }
            if (p.tmsLearnable.isNotEmpty()) {
                Spacer(Modifier.height(3.dp)); DialogText("TMs", 12, Pc.Dim)
                val byNum = log.tms.associateBy { it.number }
                DialogText(p.tmsLearnable.joinToString(", ") { n -> "TM%02d %s".format(n, byNum[n]?.move ?: "") }, 12, Pc.Text)
            }
        }
    }
}

/** Tab rows fit a phone; nothing to do at the widths this ships at. Kept as one place to change. */
private fun Modifier.horizontalScrollIfNeeded(): Modifier = this

/** Where the Gen 3 log opens (Program.openLogFromPath): the lead's page, or null for the grid. */
internal object LogOpen {
    fun leadPage(pokemon: List<RandomizerLog.Pokemon>, lead: Int?, speciesId: (String) -> Int?): RandomizerLog.Pokemon? =
        lead?.takeIf { it > 0 }?.let { id -> pokemon.firstOrNull { speciesId(it.name) == id } }
}
