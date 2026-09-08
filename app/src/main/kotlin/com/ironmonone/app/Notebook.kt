package com.ironmonone.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.ironmonone.tracker.GbaTracker
import com.ironmonone.tracker.Gen3Types

/**
 * The Notebook, four screens from the reference in one dialog:
 * NotebookIndexScreen (Pokemon seen and trainers fought, each a row that
 * opens its list), NotebookPokemonSeen (every species with a tracked note,
 * marks, moves, ability or encounter this run, alphabetical, with the
 * reference's "include unseen" checkbox), NotebookTrainersByArea (areas
 * with usable trainers and the beaten count, with its "show completed" and
 * FRLG "Sevii" checkboxes) and NotebookPokemonNoteView (one species: types,
 * BST, last level, abilities, encounters, marks, moves seen and the note).
 */
@Composable
fun NotebookDialog(
    tracker: GbaTracker?,
    marks: StatMarks,
    encountersOf: (Int) -> Int,
    seenSpecies: Set<Int>,
    lastLevelOf: (Int) -> Int?,
    lastSeenSpecies: Int?,
    speciesName: (Int) -> String,
    spriteFor: (Int) -> ImageBitmap?,
    onClose: () -> Unit,
) {
    var page by remember { mutableStateOf("index") }
    var viewed by remember { mutableStateOf<Int?>(null) }
    var includeUnseen by remember { mutableStateOf(false) }
    var showCompleted by remember { mutableStateOf(false) }
    var includeSevii by remember { mutableStateOf(false) }
    val frlg = tracker?.let { !it.isRse } ?: false
    val tracked: Set<Int> = remember(marks, seenSpecies) {
        (marks.markedSpecies() + marks.notedSpecies() + marks.movesSeenSpecies() + marks.abilitySeenSpecies() + seenSpecies).filter { it > 0 }.toSet()
    }
    Dialog(onDismissRequest = onClose) {
        Column(Modifier.width(300.dp).background(Pc.Page).border(1.dp, Pc.Border).padding(8.dp).verticalScroll(rememberScrollState())) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                val title = when (page) { "seen" -> "POKEMON SEEN"; "areas" -> "TRAINERS BY AREA"; "note" -> viewed?.let { speciesName(it).uppercase() } ?: "NOTE"; else -> "NOTEBOOK" }
                PixText(title, 10, Pc.Text, Modifier.weight(1f))
                if (page != "index") PixText("BACK", 8, Pc.Dim, Modifier.clickable { page = if (page == "note") "seen" else "index" }.padding(horizontal = 6.dp, vertical = 2.dp))
                PixText("X", 9, Pc.Dim, Modifier.clickable { onClose() }.padding(horizontal = 6.dp, vertical = 2.dp))
            }
            Spacer(Modifier.height(6.dp))
            when (page) {
                "index" -> {
                    PixText("Review your notes on:", 8, Pc.Text); Spacer(Modifier.height(6.dp))
                    val total = tracker?.notebookSpeciesTotal() ?: 386
                    Row(Modifier.fillMaxWidth().border(1.dp, Pc.Border).clickable { page = "seen" }.padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        PcSprite(spriteFor(lastSeenSpecies ?: tracked.firstOrNull() ?: 1))
                        Column(Modifier.padding(start = 8.dp)) { PixText("Pokemon Seen", 8, Pc.Gold); PixText("${tracked.size} / $total", 8, Pc.Text) }
                    }
                    Spacer(Modifier.height(8.dp))
                    val totals = tracker?.notebookTrainerTotals(includeSevii || !frlg)
                    Row(Modifier.fillMaxWidth().border(1.dp, Pc.Border).clickable { page = "areas" }.padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.padding(start = 8.dp)) { PixText("Trainers Fought", 8, Pc.Gold); PixText(if (totals != null) "${totals.first} / ${totals.second}" else "--- / ---", 8, Pc.Text) }
                    }
                }
                "seen" -> {
                    GearToggle("Include unseen", includeUnseen) { includeUnseen = it }
                    Spacer(Modifier.height(4.dp))
                    val ids = if (includeUnseen) (1 until 412).filter { it !in 252..276 } else tracked.toList()
                    val rows = ids.map { it to speciesName(it) }.sortedBy { it.second }
                    if (rows.isEmpty()) PixText("Nothing tracked yet this run.", 8, Pc.Dim)
                    rows.forEach { (id, name) ->
                        val m = marks.of(id)
                        val summary = StatMarks.STAT_NAMES.indices.mapNotNull { i -> when (m.getOrElse(i) { 0 }) { 1 -> StatMarks.STAT_NAMES[i] + "+"; 2 -> StatMarks.STAT_NAMES[i] + "--"; 3 -> StatMarks.STAT_NAMES[i] + "="; else -> null } }.joinToString(" ")
                        Row(Modifier.fillMaxWidth().border(1.dp, Pc.Border).clickable { viewed = id; page = "note" }.padding(3.dp), verticalAlignment = Alignment.CenterVertically) {
                            PcSprite(spriteFor(id))
                            Column(Modifier.weight(1f).padding(start = 6.dp)) {
                                PixText(name, 8, if (id in tracked) Pc.Text else Pc.Dim)
                                if (summary.isNotEmpty()) PixText(summary, 7, Pc.Gold)
                                val note = marks.noteFor(id); if (note.isNotEmpty()) PixText(note, 7, Pc.Dim, wrap = true)
                            }
                            val seen = encountersOf(id); if (seen > 0) PixText("Seen: $seen", 7, Pc.Dim)
                        }
                    }
                }
                "areas" -> {
                    GearToggle("Show completed areas", showCompleted) { showCompleted = it }
                    if (frlg) GearToggle("Include Sevii Islands", includeSevii) { includeSevii = it }
                    Spacer(Modifier.height(4.dp))
                    val rows = tracker?.notebookAreas(includeSevii || !frlg, showCompleted) ?: emptyList()
                    if (rows.isEmpty()) PixText(if (tracker == null) "No trainer data for this game." else "Every area is done.", 8, Pc.Dim)
                    rows.forEach { r ->
                        Row(Modifier.fillMaxWidth().border(1.dp, Pc.Border).padding(4.dp)) {
                            PixText(r.name, 8, Pc.Text, Modifier.weight(1f))
                            PixText("${r.defeated} / ${r.total}", 8, if (r.defeated == r.total) Pc.Positive else Pc.Text, Modifier.width(60.dp), TextAlign.End)
                        }
                    }
                }
                "note" -> viewed?.let { id ->
                    val base = tracker?.baseStats(id)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PcSprite(spriteFor(id))
                        Column(Modifier.padding(start = 8.dp)) {
                            if (base != null) {
                                PcTypeChip(Gen3Types.name(base.type1), pcTypeColor(base.type1))
                                if (base.type2 != base.type1) PcTypeChip(Gen3Types.name(base.type2), pcTypeColor(base.type2))
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        val m = marks.of(id)
                        Column {
                            StatMarks.STAT_NAMES.forEachIndexed { i, n ->
                                val st = m.getOrElse(i) { 0 }
                                if (st != 0) PixText(n + " " + StatMarks.symbol(st), 7, Pc.Gold)
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    @Composable fun line(k: String, v: String) { Row { PixText(k, 8, Pc.Dim, Modifier.width(96.dp)); PixText(v, 8, Pc.Text, wrap = true) } }
                    line("BST", base?.bst?.toString() ?: "---")
                    line("Last level", lastLevelOf(id)?.toString() ?: "---")
                    line("Abilities", (marks.abilityFor(id)?.let { listOf(it) } ?: tracker?.possibleAbilities(id) ?: emptyList()).ifEmpty { listOf("---") }.joinToString(" / "))
                    line("Encounters", encountersOf(id).toString())
                    Spacer(Modifier.height(4.dp))
                    PixText("Moves seen", 8, Pc.Gold)
                    val moves = marks.movesSeenFor(id)
                    if (moves.isEmpty()) PixText("---", 8, Pc.Dim)
                    moves.forEach { mv -> line(mv.name, if (mv.minLv == mv.maxLv) "Lv.${mv.minLv}" else "Lv.${mv.minLv}-${mv.maxLv}") }
                    Spacer(Modifier.height(4.dp))
                    PixText("Note", 8, Pc.Gold)
                    PixText(marks.noteFor(id).ifEmpty { "---" }, 8, Pc.Text, wrap = true)
                }
            }
        }
    }
}
