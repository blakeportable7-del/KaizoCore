package com.ironmonone.app

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
    val log = remember(file) { RandomizerLog.parse(file) }
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
    val team = remember(party) {
        party?.associate { tm -> tm.speciesName.uppercase() to (LogSearch.refOrder(tm.mon.ivs) to LogSearch.refOrder(tm.mon.evs)) } ?: emptyMap()
    }
    var trainerFilter by remember { mutableStateOf(LogTrainerFilter.GYM) }
    val rules = remember(tracker, badgeSet) {
        if (tracker != null && (badgeSet == "RSE" || badgeSet == "FRLG")) LogTrainerRules(tracker, badgeSet == "FRLG") else null
    }
    // Move types for the same-type colouring, and the game's species ids for the icons: the log
    // numbers Pokemon by National Dex, which parts from the game's own numbering after 251.
    val moveTypes = remember(rules) {
        val t = tracker
        if (rules == null || t == null) emptyMap()
        else (1..354).mapNotNull { id -> t.moveRowFor(id)?.let { r -> r.type?.let { ty -> r.name.uppercase() to ty } } }.toMap()
    }
    val speciesByName = remember(rules) {
        val t = tracker
        if (rules == null || t == null) emptyMap()
        else (1..(if (t.expandedSpeciesIds) 1300 else 411)).associateBy { t.speciesName(it).uppercase() }
    }
    val logRoutes = remember(rules, log) {
        val t = tracker
        if (rules == null || t == null || log == null) emptyList() else LogRoutes.build(log, rules, t)
    }
    val logSprite: ((RandomizerLog.Pokemon) -> androidx.compose.ui.graphics.ImageBitmap?)? =
        spriteFor?.let { sf -> { p: RandomizerLog.Pokemon -> speciesByName[p.name.uppercase()]?.let(sf) } }
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
            // The tabs share the width, each a full touch target, the open one gold and underlined.
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                LogTab.entries.forEach { t ->
                    val on = t == tab
                    Box(
                        Modifier.weight(1f).heightIn(min = PcMin.DIALOG_TOUCH_DP.dp).background(if (on) Pc.Page else Pc.Ground)
                            .border(if (on) 2.dp else 1.dp, if (on) Pc.Gold else Pc.Border)
                            .clickable { tab = t; detail = null; trainerDetail = null; routeDetail = null; query = ""; searchSort = null; searchFilter = null; sortPicked = false },
                        contentAlignment = Alignment.Center,
                    ) { DialogText(t.label, 12, if (on) Pc.Gold else Pc.Text, underline = on) }
                }
            }
            Spacer(Modifier.height(8.dp))
            if (log == null) {
                DialogText("The log could not be read.", 13, Pc.Negative)
                return@Column
            }
            val d = detail
            if (d != null) {
                if (rules != null) LogPokemonDetail(d, log, badgeSet == "FRLG", logSprite, moveTypes, team, onPokemon = { detail = it }, onBack = { detail = null },
                    // The same PokemonData evolution the info screen reads, in its short words.
                    evoMethodsOf = tracker?.let { t -> { lp -> speciesByName[lp.name.uppercase()]?.let { com.ironmonone.tracker.EvoText.short(t.evolution(it)) } ?: emptyList() } })
                else PokemonDetail(d, log, onBack = { detail = null })
                return@Column
            }
            val td = trainerDetail
            if (td != null && rules != null) {
                LogTrainerDetail(td, log, rules, TrackerOptions.logCustomTrainerNames, badgeSet, logSprite, moveTypes,
                    onPokemon = { detail = it }, onBack = { trainerDetail = null })
                return@Column
            }
            val rd = routeDetail
            if (rd != null && rules != null) {
                LogRouteDetail(rd, rules, TrackerOptions.logCustomTrainerNames, logSprite,
                    onTrainer = { trainerDetail = it }, onPokemon = { detail = it }, onBack = { routeDetail = null })
                return@Column
            }
            val curSort = searchSort ?: LogSearch.defaultSort(tab)
            val curFilter = searchFilter ?: LogSearch.defaultFilter(tab)
            // What the search suggests while it is typed: Pokemon by name (a tap opens one on the Pokemon tab, and
            // filters by it on the others), or the words the chosen filter searches.
            val suggestFor = curFilter ?: LogFilter.NAME
            val suggestions = remember(log, query, suggestFor, logRoutes) {
                when (suggestFor) {
                    LogFilter.NAME -> LogSuggest.pokemon(log, query).map { LogSuggestion(it.name, it) }
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
                LogTab.POKEMON -> if (rules != null) {
                    val rows = remember(log, q, curFilter, curSort) { LogSearch.pokemonRows(log, q, curFilter ?: LogFilter.NAME, curSort ?: LogSort.POKEDEX) }
                    LogPokemonTab(rows, logSprite, onPokemon = { detail = it })
                } else {
                    val rows = log.pokemon.filter { q.isEmpty() || it.name.contains(q, true) || it.types.any { t -> t.contains(q, true) } || it.abilities.any { a -> a.contains(q, true) } }
                    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        items(rows, key = { it.id }) { p ->
                            Row(Modifier.fillMaxWidth().background(Pc.Page).border(1.dp, Pc.Border).clickable { detail = p }.padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
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
                        searchBy = curFilter ?: LogFilter.TRAINER, sortBy = if (sortPicked) curSort else null)
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
                                    Row(Modifier.fillMaxWidth()) {
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
                    val rows = remember(logRoutes, q, curFilter, curSort) { LogSearch.routeRows(logRoutes, log, q, curFilter ?: LogFilter.ROUTE, curSort ?: LogSort.WILD) }
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
                    LogTmsTab(log, rules, badgeSet == "FRLG", TrackerOptions.logCustomTrainerNames, badgeSet, tmFilter,
                        onFilter = { tmFilter = it }, onTrainer = { trainerDetail = it })
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
                LogTab.MISC -> LogMiscTab(log)
            }
        }
    }
}

enum class LogTab(val label: String) { POKEMON("POKEMON"), TRAINERS("TRAINERS"), ROUTES("ROUTES"), TMS("TMS"), MISC("MISC") }

/** The reference's LogTabPokemonDetails: one species from the log. */
@Composable
private fun PokemonDetail(p: RandomizerLog.Pokemon, log: RandomizerLog, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(Modifier.fillMaxWidth().background(Pc.Page).border(1.dp, Pc.Border).padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
            LogBack(onBack)
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
                    p.evolutions.forEach { DialogText(it, 12, Pc.Text) }
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
