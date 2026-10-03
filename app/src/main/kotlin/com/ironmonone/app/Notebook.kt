package com.ironmonone.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
 * NotebookPokemonNoteView.buildScreen's two ability lines (NotebookPokemonNoteView.lua:273-298).
 * Where unknown abilities may show (PokemonData.canShowUnknownAbilities: Open Book, or a game
 * whose abilities were not randomized with "Show data for vanilla game" on), the species' ROM
 * abilities, the first suffixed " /" when there are two. Otherwise only the tracked ones: the
 * first suffixed " /" over "?" until a second is seen, and "---" twice before any. The page
 * used to fall back to the ROM's abilities whenever none were tracked, which on a randomized
 * game named them before the player had seen them.
 */
internal fun notebookAbilityLines(tracked: List<String>, rom: List<String>, canShowUnknown: Boolean): Pair<String, String> {
    val blank = "---"
    if (canShowUnknown) return when {
        rom.size >= 2 -> "${rom[0]} /" to rom[1]
        rom.size == 1 -> rom[0] to blank
        else -> blank to blank
    }
    val first = tracked.firstOrNull() ?: return blank to blank
    return "$first /" to (tracked.getOrNull(1) ?: "?")
}

/**
 * The species the Notebook counts and lists under "Include unseen": Gen 3's 386 on a GBA game (ids 1 to 411 less the
 * 25 empty slots 252 to 276), and the game's own Pokedex on a Game Boy one, 151 (Red, Blue, Yellow) or 251 (Gold,
 * Silver, Crystal). A Game Boy game has no GBA tracker, so the page counted out of 386 and listed #152 to #411 with
 * later games' sprites (rc32 audit P2 #36). [badgeSet] is TrackerState's: "RBY" or "GSC" there.
 */
internal object NotebookSpecies {
    fun ids(tracker: GbaTracker?, badgeSet: String?): List<Int> = when {
        tracker == null && badgeSet == "RBY" -> (1..151).toList()
        tracker == null && badgeSet == "GSC" -> (1..251).toList()
        else -> (1 until 412).filter { it !in 252..276 }
    }
}

/**
 * The Notebook, four screens from the reference in one dialog:
 * NotebookIndexScreen (Pokemon seen and trainers fought, each a row that
 * opens its list), NotebookPokemonSeen (every species with a tracked note,
 * marks, moves, ability or encounter this run, alphabetical, with the
 * reference's "include unseen" checkbox), NotebookTrainersByArea (areas
 * with usable trainers and the beaten count, with its "show completed" and
 * FRLG "Sevii" checkboxes) and NotebookPokemonNoteView (one species: types,
 * BST, last level, abilities, encounters, marks, moves seen and the note).
 * Its words are DialogText, which follows the phone's font size, and its rows
 * are 48dp targets (rc32 audit P2 #19): it was fixed 7 to 10dp text.
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
    /** Every species of this game (NotebookSpecies): the count's total and the list with unseen included. */
    speciesIds: List<Int>,
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
    // System Back walks the pages back to the index before it closes, as the BACK
    // glyph does; it used to close the whole notebook from a species page (2026-09-27, audit).
    fun back() { when (page) { "index" -> onClose(); "note" -> page = "seen"; else -> page = "index" } }
    var filter by remember { mutableStateOf("") }
    Dialog(onDismissRequest = { back() }) {
        Column(Modifier.width(300.dp).background(Pc.Page).border(1.dp, Pc.Border).padding(8.dp).verticalScroll(rememberScrollState())) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                val title = when (page) { "seen" -> "POKEMON SEEN"; "areas" -> "TRAINERS BY AREA"; "note" -> viewed?.let { speciesName(it).uppercase() } ?: "NOTE"; else -> "NOTEBOOK" }
                DialogText(title, 16, Pc.Text, Modifier.weight(1f), heading = true)
                if (page != "index") PcTap("BACK", 8, Pc.Dim, "Back") { back() }
                PcTap("X", 9, Pc.Dim, "Close") { onClose() }
            }
            Spacer(Modifier.height(6.dp))
            when (page) {
                "index" -> {
                    DialogText("Review your notes on:", 13, Pc.Text); Spacer(Modifier.height(6.dp))
                    val total = speciesIds.size
                    Row(Modifier.fillMaxWidth().heightIn(min = PcMin.DIALOG_TOUCH_DP.dp).border(1.dp, Pc.Border).clickable { page = "seen" }.padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        PcSprite(spriteFor(lastSeenSpecies ?: tracked.firstOrNull() ?: 1))
                        Column(Modifier.padding(start = 8.dp)) { DialogText("Pokemon Seen", 13, Pc.Gold); DialogText("${tracked.size} / $total", 13, Pc.Text) }
                    }
                    Spacer(Modifier.height(8.dp))
                    val totals = tracker?.notebookTrainerTotals(includeSevii || !frlg)
                    Row(Modifier.fillMaxWidth().heightIn(min = PcMin.DIALOG_TOUCH_DP.dp).border(1.dp, Pc.Border).clickable { page = "areas" }.padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.padding(start = 8.dp)) { DialogText("Trainers Fought", 13, Pc.Gold); DialogText(if (totals != null) "${totals.first} / ${totals.second}" else "--- / ---", 13, Pc.Text) }
                    }
                }
                "seen" -> {
                    GearToggle("Include unseen", includeUnseen) { includeUnseen = it }
                    Spacer(Modifier.height(4.dp))
                    // A name filter: with unseen included the list is 386 species long (2026-09-27, audit).
                    Row(Modifier.fillMaxWidth().heightIn(min = 40.dp).border(1.dp, Pc.Border).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        DialogText("FIND", 12, Pc.Dim, Modifier.widthIn(min = 34.dp).padding(end = 4.dp))
                        BasicTextField(
                            value = filter, onValueChange = { filter = it }, singleLine = true,
                            textStyle = TextStyle(color = Pc.Text, fontSize = 12.sp),
                            cursorBrush = SolidColor(Pc.Gold),
                            modifier = Modifier.weight(1f),
                        )
                        if (filter.isNotEmpty()) PcTap("X", 7, Pc.Dim, "Clear") { filter = "" }
                    }
                    Spacer(Modifier.height(4.dp))
                    val ids = if (includeUnseen) speciesIds else tracked.toList()
                    val q = filter.trim()
                    val rows = ids.map { it to speciesName(it) }.filter { q.isEmpty() || it.second.contains(q, ignoreCase = true) }.sortedBy { it.second }
                    if (rows.isEmpty()) DialogText(if (q.isEmpty()) "Nothing tracked yet this run." else "No Pokemon match.", 13, Pc.Dim)
                    rows.forEach { (id, name) ->
                        val m = marks.of(id)
                        val summary = StatMarks.STAT_NAMES.indices.mapNotNull { i -> when (m.getOrElse(i) { 0 }) { 1 -> StatMarks.STAT_NAMES[i] + "+"; 2 -> StatMarks.STAT_NAMES[i] + "--"; 3 -> StatMarks.STAT_NAMES[i] + "="; else -> null } }.joinToString(" ")
                        Row(Modifier.fillMaxWidth().heightIn(min = PcMin.DIALOG_TOUCH_DP.dp).border(1.dp, Pc.Border).clickable { viewed = id; page = "note" }.padding(3.dp), verticalAlignment = Alignment.CenterVertically) {
                            PcSprite(spriteFor(id))
                            Column(Modifier.weight(1f).padding(start = 6.dp)) {
                                DialogText(name, 13, if (id in tracked) Pc.Text else Pc.Dim)
                                if (summary.isNotEmpty()) DialogText(summary, 12, Pc.Gold)
                                val note = marks.noteFor(id); if (note.isNotEmpty()) DialogText(note, 12, Pc.Dim)
                            }
                            val seen = encountersOf(id); if (seen > 0) DialogText("Seen: $seen", 12, Pc.Dim)
                        }
                    }
                }
                "areas" -> {
                    GearToggle("Show completed areas", showCompleted) { showCompleted = it }
                    if (frlg) GearToggle("Include Sevii Islands", includeSevii) { includeSevii = it }
                    Spacer(Modifier.height(4.dp))
                    val rows = tracker?.notebookAreas(includeSevii || !frlg, showCompleted) ?: emptyList()
                    if (rows.isEmpty()) DialogText(if (tracker == null) "No trainer data for this game." else "Every area is done.", 13, Pc.Dim)
                    rows.forEach { r ->
                        Row(Modifier.fillMaxWidth().border(1.dp, Pc.Border).padding(4.dp)) {
                            DialogText(r.name, 13, Pc.Text, Modifier.weight(1f))
                            DialogText("${r.defeated} / ${r.total}", 13, if (r.defeated == r.total) Pc.Positive else Pc.Text, Modifier.widthIn(min = 60.dp), TextAlign.End)
                        }
                    }
                }
                "note" -> viewed?.let { id ->
                    val base = tracker?.baseStats(id)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PcSprite(spriteFor(id))
                        Column(Modifier.padding(start = 8.dp)) {
                            // NotebookPokemonNoteView.lua:260: "Reveal info if randomized" off hides randomized types as "?".
                            if (base != null) InfoRules.typeIcons(
                                listOf(Gen3Types.name(base.type1) to base.type1) +
                                    (if (base.type2 != base.type1) listOf(Gen3Types.name(base.type2) to base.type2) else emptyList()),
                                InfoRules.hidesRandomizedTypes(tracker?.randomized()),
                            ).forEach { (name, type) -> PcTypeChip(name, pcTypeColor(type)) }
                        }
                        Spacer(Modifier.weight(1f))
                        val m = marks.of(id)
                        Column {
                            StatMarks.STAT_NAMES.forEachIndexed { i, n ->
                                val st = m.getOrElse(i) { 0 }
                                if (st != 0) DialogText(n + " " + StatMarks.symbol(st), 12, Pc.Gold)
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    @Composable fun line(k: String, v: String) { Row { DialogText(k, 13, Pc.Dim, Modifier.width(96.dp)); DialogText(v, 13, Pc.Text) } }
                    line("BST", base?.bst?.toString() ?: "---")
                    line("Last level", lastLevelOf(id)?.toString() ?: "---")
                    val (ability1, ability2) = notebookAbilityLines(
                        marks.abilitiesFor(id), tracker?.possibleAbilities(id) ?: emptyList(),
                        InfoRules.canShowAbilities(tracker?.randomized()),
                    )
                    line("Abilities", ability1)
                    line("", ability2)
                    line("Encounters", encountersOf(id).toString())
                    Spacer(Modifier.height(4.dp))
                    DialogText("Moves seen", 13, Pc.Gold)
                    val moves = marks.movesSeenFor(id)
                    if (moves.isEmpty()) DialogText("---", 13, Pc.Dim)
                    moves.forEach { mv -> line(mv.name, if (mv.minLv == mv.maxLv) "Lv.${mv.minLv}" else "Lv.${mv.minLv}-${mv.maxLv}") }
                    Spacer(Modifier.height(4.dp))
                    DialogText("Note", 13, Pc.Gold)
                    DialogText(marks.noteFor(id).ifEmpty { "---" }, 13, Pc.Text)
                }
            }
        }
    }
}
