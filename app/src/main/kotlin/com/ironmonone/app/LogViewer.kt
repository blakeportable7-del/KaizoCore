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
 * Trainers list their randomized party with levels and held items. Routes are
 * the wild sets with each encounter's level band. TMs are the moves in the
 * machines. Misc is the run's version, seed, settings string, starters and
 * static encounters.
 *
 * Opened from the game-over screen (and nowhere else yet), it reads the log
 * the randomizer wrote beside the current run's ROM.
 */
@Composable
fun LogViewer(file: File, onClose: () -> Unit) {
    val log = remember(file) { RandomizerLog.parse(file) }
    var tab by remember { mutableStateOf(LogTab.POKEMON) }
    var query by remember { mutableStateOf("") }
    var detail by remember { mutableStateOf<RandomizerLog.Pokemon?>(null) }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(Pc.Ground).padding(6.dp)) {
            // Header: tabs, then CLOSE at the end.
            Row(Modifier.fillMaxWidth().horizontalScrollIfNeeded(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                LogTab.entries.forEach { t ->
                    val on = t == tab
                    Box(
                        Modifier.background(if (on) Pc.Page else Pc.Ground).border(1.dp, if (on) Pc.Gold else Pc.Border)
                            .clickable { tab = t; detail = null }.padding(horizontal = 7.dp, vertical = 6.dp),
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
                PokemonDetail(d, log, onBack = { detail = null })
                return@Column
            }
            if (tab != LogTab.MISC && tab != LogTab.TMS) {
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
                LogTab.POKEMON -> {
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
                LogTab.TRAINERS -> {
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
                LogTab.ROUTES -> {
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
                LogTab.TMS -> {
                    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        items(log.tms, key = { it.number }) { t ->
                            Row(Modifier.fillMaxWidth().background(Pc.Page).border(1.dp, Pc.Border).padding(6.dp)) {
                                PixText("TM%02d".format(t.number), 7, Pc.Dim, Modifier.width(44.dp))
                                PixText(t.move, 8, Pc.Text)
                            }
                        }
                    }
                }
                LogTab.MISC -> {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).background(Pc.Page).border(1.dp, Pc.Border).padding(8.dp)) {
                        PixText("Game: ${log.game}", 8, Pc.Text, wrap = true)
                        PixText("Randomizer ${log.version}", 7, Pc.Dim)
                        PixText("Seed: ${log.seed}", 7, Pc.Dim)
                        Spacer(Modifier.height(6.dp))
                        PixText("Starters", 8, Pc.Gold)
                        log.starters.forEach { PixText(it, 7, Pc.Text) }
                        Spacer(Modifier.height(6.dp))
                        PixText("Static encounters", 8, Pc.Gold)
                        log.statics.forEach { (a, b) -> PixText("$a  ->  $b", 7, Pc.Text) }
                        if (log.pickup.isNotEmpty()) {
                            Spacer(Modifier.height(6.dp))
                            PixText("Pickup items", 8, Pc.Gold)
                            log.pickup.forEach { PixText(it, 7, Pc.Text, wrap = true) }
                        }
                        Spacer(Modifier.height(6.dp))
                        PixText("Settings string", 8, Pc.Gold)
                        PixText(log.settingsString, 6, Pc.Dim, wrap = true)
                    }
                }
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
