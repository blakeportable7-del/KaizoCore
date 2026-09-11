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
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(Pc.Ground).padding(6.dp)) {
            // Header: tabs, then CLOSE at the end.
            Row(Modifier.fillMaxWidth().horizontalScrollIfNeeded(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                LogTab.entries.forEach { t ->
                    val on = t == tab
                    Box(
                        Modifier.background(if (on) Pc.Page else Pc.Ground).border(1.dp, if (on) Pc.Gold else Pc.Border)
                            .clickable { tab = t; detail = null; trainerDetail = null; routeDetail = null; query = ""; searchSort = null; searchFilter = null; sortPicked = false }.padding(horizontal = 7.dp, vertical = 6.dp),
                    ) { PixText(t.label, 7, if (on) Pc.Gold else Pc.Text) }
                }
                Spacer(Modifier.weight(1f))
                // The X in the top right takes the log away and the game-over popup is where you land.
                Box(Modifier.background(Pc.Page).border(1.dp, Pc.Border).clickable { onClose() }.padding(horizontal = 10.dp, vertical = 6.dp)) {
                    PixText("X", 9, Pc.Text)
                }
            }
            Spacer(Modifier.height(6.dp))
            if (log == null) {
                PixText("The log could not be read.", 8, Pc.Negative, wrap = true)
                return@Column
            }
            val d = detail
            if (d != null) {
                if (rules != null) LogPokemonDetail(d, log, badgeSet == "FRLG", logSprite, moveTypes, team, onPokemon = { detail = it }, onBack = { detail = null })
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
            if (tab != LogTab.MISC && tab != LogTab.TMS && rules != null && curSort != null && curFilter != null) {
                LogSearchBar(query, { query = it }, LogSearch.sortsFor(tab), curSort, { searchSort = it; sortPicked = true },
                    LogSearch.filtersFor(tab), curFilter, { searchFilter = it })
                Spacer(Modifier.height(6.dp))
            } else if (tab != LogTab.MISC && tab != LogTab.TMS) {
                Row(Modifier.fillMaxWidth().background(Pc.Page).border(1.dp, Pc.Border).padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    PixText("FIND", 7, Pc.Dim, Modifier.width(34.dp))
                    BasicTextField(
                        value = query, onValueChange = { query = it }, singleLine = true,
                        textStyle = TextStyle(color = Pc.Text, fontSize = 12.sp),
                        cursorBrush = SolidColor(Pc.Gold),
                        modifier = Modifier.weight(1f),
                    )
                    if (query.isNotEmpty()) PixText("X", 7, Pc.Dim, Modifier.clickable { query = "" }.padding(4.dp))
                }
                Spacer(Modifier.height(6.dp))
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
                                PixText("%03d".format(p.id), 7, Pc.Dim, Modifier.width(30.dp))
                                PixText(p.name, 8, Pc.Text, Modifier.weight(1f))
                                PixText(p.types.joinToString("/"), 7, Pc.Dim, Modifier.width(96.dp))
                                PixText("BST ${p.bst}", 7, Pc.Gold)
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
                                    PixText("#${t.number}", 7, Pc.Dim, Modifier.width(34.dp))
                                    PixText(t.fullName, 8, Pc.Text, Modifier.weight(1f))
                                    if (t.name.isNotBlank()) PixText(t.originalName, 7, Pc.Dim)
                                }
                                Spacer(Modifier.height(3.dp))
                                t.party.forEach { m ->
                                    Row(Modifier.fillMaxWidth()) {
                                        PixText(m.name, 7, Pc.Text, Modifier.weight(1f))
                                        m.item?.let { PixText(it, 7, Pc.Gold, Modifier.padding(end = 8.dp)) }
                                        PixText("Lv${m.level}", 7, Pc.Dim)
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
                                    PixText(r.name, 8, Pc.Text, Modifier.weight(1f))
                                    PixText("rate ${r.rate}", 7, Pc.Dim)
                                }
                                Spacer(Modifier.height(3.dp))
                                // The reference collapses the set to one row per species with its level band.
                                r.encounters.groupBy { it.name }.forEach { (name, es) ->
                                    val lo = es.minOf { it.minLevel }; val hi = es.maxOf { it.maxLevel }
                                    Row(Modifier.fillMaxWidth()) {
                                        PixText(name, 7, Pc.Text, Modifier.weight(1f))
                                        PixText(if (lo == hi) "Lv$lo" else "Lv$lo-$hi", 7, Pc.Dim)
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
                                PixText("TM%02d".format(t.number), 7, Pc.Dim, Modifier.width(44.dp))
                                PixText(t.move, 8, Pc.Text)
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
            PixText("< BACK", 7, Pc.Dim, Modifier.clickable { onBack() }.padding(end = 10.dp))
            PixText(p.name, 9, Pc.Gold, Modifier.weight(1f))
            PixText(p.types.joinToString("/"), 7, Pc.Text)
        }
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Column(Modifier.weight(1f).background(Pc.Page).border(1.dp, Pc.Border).padding(6.dp)) {
                p.statNames.forEachIndexed { i, n ->
                    Row(Modifier.fillMaxWidth()) {
                        PixText(n, 7, Pc.Dim, Modifier.width(34.dp))
                        PixText("${p.stats[i]}", 7, Pc.Text)
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 3.dp)) {
                    PixText("BST", 7, Pc.Gold, Modifier.width(34.dp))
                    PixText("${p.bst}", 7, Pc.Gold)
                }
            }
            Column(Modifier.weight(1f).background(Pc.Page).border(1.dp, Pc.Border).padding(6.dp)) {
                if (p.abilities.isNotEmpty()) {
                    PixText("Abilities", 7, Pc.Dim)
                    p.abilities.forEach { PixText(it, 7, Pc.Text, wrap = true) }
                }
                if (p.item.isNotBlank()) { PixText("Item", 7, Pc.Dim); PixText(p.item, 7, Pc.Text, wrap = true) }
                if (p.evolutions.isNotEmpty()) {
                    PixText("Evolves into", 7, Pc.Dim)
                    p.evolutions.forEach { PixText(it, 7, Pc.Text, wrap = true) }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Column(Modifier.fillMaxWidth().background(Pc.Page).border(1.dp, Pc.Border).padding(6.dp)) {
            PixText("Level-up moves", 7, Pc.Dim)
            p.moves.forEach { (lv, mv) ->
                Row(Modifier.fillMaxWidth()) { PixText("Lv$lv", 7, Pc.Dim, Modifier.width(40.dp)); PixText(mv, 7, Pc.Text) }
            }
            if (p.evoMoves.isNotEmpty()) {
                Spacer(Modifier.height(3.dp)); PixText("On evolution", 7, Pc.Dim)
                p.evoMoves.forEach { PixText(it, 7, Pc.Text) }
            }
            if (p.tmsLearnable.isNotEmpty()) {
                Spacer(Modifier.height(3.dp)); PixText("TMs", 7, Pc.Dim)
                val byNum = log.tms.associateBy { it.number }
                PixText(p.tmsLearnable.joinToString(", ") { n -> "TM%02d %s".format(n, byNum[n]?.move ?: "") }, 7, Pc.Text, wrap = true)
            }
        }
    }
}

/** Tab rows fit a phone; nothing to do at the widths this ships at. Kept as one place to change. */
private fun Modifier.horizontalScrollIfNeeded(): Modifier = this
