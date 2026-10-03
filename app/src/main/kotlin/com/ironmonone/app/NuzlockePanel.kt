@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.ironmonone.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ironmonone.tracker.NuzlockeAdapters
import com.ironmonone.tracker.TrackerState
import com.ironmonone.tracker.nds.NdsNuzlocke
import com.ironmonone.tracker.nds.NdsTrackerState
import com.ironmonone.tracker.nuzlocke.AreaKey
import com.ironmonone.tracker.nuzlocke.ClauseGroup
import com.ironmonone.tracker.nuzlocke.NuzlockeAreas
import com.ironmonone.tracker.nuzlocke.NuzlockeFamilies
import com.ironmonone.tracker.nuzlocke.NuzlockeNotes
import com.ironmonone.tracker.nuzlocke.NuzlockePreset
import com.ironmonone.tracker.nuzlocke.NuzlockeRules
import com.ironmonone.tracker.nuzlocke.NuzlockeView
import com.ironmonone.tracker.nuzlocke.NzArea
import com.ironmonone.tracker.nuzlocke.Origin
import com.ironmonone.tracker.nuzlocke.Outcome
import com.ironmonone.tracker.nuzlocke.RosterMon
import com.ironmonone.tracker.nuzlocke.RunStatus
import com.ironmonone.tracker.nuzlocke.SafariRule
import com.ironmonone.tracker.nuzlocke.Snapshot
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The Nuzlocke panel in the tracker (2026-09-29): this area's status, the level cap and whose it is, how many are
 * alive and dead. A tap opens the whole ledger.
 *
 * It draws nothing unless the game being played has a Nuzlocke run and the tracker could read it, so a player who
 * never starts one sees no change at all. It also feeds the run: every state the tracker reports goes to the
 * engine from here, which is why it is one line in TrackerPanel and nothing in PlayScreen. The rules are in
 * tracker-gba (NuzlockeEngine); this file only draws them and passes the player's corrections on.
 *
 * Drawn in the tracker's own parts (PcCard, PixText, the Pc colours), like the rest of the panel.
 */
@Composable
fun NuzlockePanel(state: TrackerState?) {
    if (state?.nuz == null) return
    val context = LocalContext.current
    val live = NuzlockeTracking.current(context.applicationContext.filesDir) ?: return
    // A state that equals the last one is the same poll again and says nothing new, so it is not fed twice.
    val snapshot = remember(state) { NuzlockeAdapters.snapshot(state) }
    NuzlockePanelBody(live, snapshot)
}

/** The same panel on a DS game (2026-09-30): the DS tracker's state, through its own adapter. */
@Composable
fun NuzlockeNdsPanel(state: NdsTrackerState?) {
    if (state == null) return
    val context = LocalContext.current
    val live = NuzlockeTracking.current(context.applicationContext.filesDir) ?: return
    val snapshot = remember(state) { NdsNuzlocke.snapshot(state) }
    NuzlockePanelBody(live, snapshot)
}

@Composable
private fun NuzlockePanelBody(live: NuzlockeTracking.Live, snapshot: Snapshot?) {
    var tick by remember(live) { mutableIntStateOf(0) }
    var open by remember(live) { mutableStateOf(false) }
    LaunchedEffect(snapshot) { if (snapshot != null && live.feed(snapshot)) tick++ }
    val panel = remember(tick, snapshot) { NuzlockeView.panel(live.ledger, snapshot) }
    PcCard {
        Column(Modifier.fillMaxWidth().clickable { open = true }.padding(horizontal = 3.rp, vertical = 2.rp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                PixText(panel.title, PcRef.FONT, Pc.Gold, Modifier.weight(1f))
                PixText(countsOf(panel), PcRef.FONT, if (panel.warnings > 0) Pc.Gold else Pc.Dim)
            }
            for (l in panel.lines) PixText(l.text, PcRef.FONT, toneColor(l.tone), wrap = true)
        }
    }
    if (open) NuzlockeLedgerDialog(live, snapshot, onClose = { open = false }, onChanged = { tick++ })
}

private fun countsOf(p: NuzlockeView.Panel): String =
    "${p.alive} alive  ${p.graveyard} dead" + if (p.warnings > 0) "  ${p.warnings} !" else ""

private fun toneColor(t: NuzlockeView.Tone): Color = when (t) {
    NuzlockeView.Tone.GOOD -> Pc.Positive
    NuzlockeView.Tone.BAD -> Pc.Negative
    NuzlockeView.Tone.WARN -> Pc.Gold
    NuzlockeView.Tone.DIM -> Pc.Dim
    NuzlockeView.Tone.NORMAL -> Pc.Text
}

// ---------------------------------------------------------------------------------------------------------------
// The ledger
// ---------------------------------------------------------------------------------------------------------------

private enum class LedgerTab(val label: String) { AREAS("AREAS"), TEAM("TEAM"), GRAVE("GRAVE"), LOG("LOG"), RULES("RULES") }

/**
 * Opens the ledger from outside the panel (2026-09-30, UX audit P0-6): Rules in the File menu and in Tracker Setup
 * show a Nuzlocke's own rules, not the IronMON rulebook. [NuzlockeLedgerRequested] draws it from SideScreenDialogs,
 * which is always there in Play, so it opens with the tracker hidden too.
 */
object NuzlockeLedgerRequest {
    /** The tab asked for, by name ("RULES"), or null when nothing is asked. */
    var tab by mutableStateOf<String?>(null)

    fun openRules() { tab = "RULES" }
}

/** The ledger [NuzlockeLedgerRequest] asked for, over the game. Nothing when the game has no Nuzlocke run. */
@Composable
fun NuzlockeLedgerRequested() {
    val asked = NuzlockeLedgerRequest.tab ?: return
    val context = LocalContext.current
    val live = remember(asked) { NuzlockeTracking.current(context.applicationContext.filesDir) }
    if (live == null) {
        LaunchedEffect(asked) { NuzlockeLedgerRequest.tab = null }
        return
    }
    NuzlockeLedgerDialog(live, live.lastSnapshot, onClose = { NuzlockeLedgerRequest.tab = null }, startTab = asked)
}

private fun whenText(at: Long): String = if (at <= 0) "" else SimpleDateFormat("MMM d, HH:mm", Locale.US).format(Date(at))

/**
 * The whole ledger: every area with its encounter and outcome, the team and the boxed, the graveyard, the warnings
 * and log, and the rules. Everything the tracker decided can be changed here by hand. [snapshot] is what the game
 * shows now, so "this area" and the level cap are the ones the player is standing in; it is null when the ledger is
 * opened from the Nuzlocke screen with no game running.
 *
 * The ledger is plain data that changes under the dialog, so every part takes [rev], which moves after each
 * change and makes the part redraw.
 */
@Composable
fun NuzlockeLedgerDialog(
    live: NuzlockeTracking.Live, snapshot: Snapshot?, onClose: () -> Unit, onChanged: () -> Unit = {},
    /** The tab to open on, by name; the areas when null or unknown. */
    startTab: String? = null,
) {
    var tab by remember { mutableStateOf(LedgerTab.entries.firstOrNull { it.name == startTab } ?: LedgerTab.AREAS) }
    var rev by remember { mutableIntStateOf(0) }
    var areaEdit by remember { mutableStateOf<AreaKey?>(null) }
    var monEdit by remember { mutableStateOf<Long?>(null) }
    var addOpen by remember { mutableStateOf(false) }
    var noteOpen by remember { mutableStateOf(false) }
    val ledger = live.ledger
    val rules = ledger.meta.rules
    val here = snapshot?.let { NuzlockeAreas.of(it.area, null, rules, ledger.meta.system) }
    fun changed() { live.edited(); rev++; onChanged() }

    InfoSheet("NUZLOCKE  " + rules.preset.label.uppercase(), onClose) {
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            for (t in LedgerTab.entries) NzChip(t.label, tab == t, Role.Tab) { tab = t }
        }
        Spacer(Modifier.height(8.dp))
        when (tab) {
            LedgerTab.AREAS -> AreasTab(live, here, rev, onEdit = { areaEdit = it })
            LedgerTab.TEAM -> TeamTab(live, rev, onEdit = { monEdit = it }, onAdd = { addOpen = true })
            LedgerTab.GRAVE -> GraveTab(live, rev, onEdit = { monEdit = it })
            LedgerTab.LOG -> LogTab(live, rev, onDismiss = { live.edits.dismiss(it); changed() }, onNote = { noteOpen = true })
            LedgerTab.RULES -> RulesTab(live, snapshot, rev, onChanged = { changed() })
        }
    }
    areaEdit?.let { a -> AreaEditor(live, a, rev, onClose = { areaEdit = null }, onChanged = { changed() }) }
    monEdit?.let { id ->
        val mon = ledger.roster[id]
        if (mon == null) monEdit = null
        else MonEditor(live, mon, here, snapshot?.badges, rev, onClose = { monEdit = null }, onChanged = { changed() })
    }
    if (addOpen) AddMonEditor(live, here, rev, onClose = { addOpen = false }, onChanged = { changed() })
    if (noteOpen) TextPrompt("Add a note", "", onDone = { t -> live.edits.addNote(t, System.currentTimeMillis()); changed(); noteOpen = false }, onDismiss = { noteOpen = false })
}

@Composable
private fun AreasTab(live: NuzlockeTracking.Live, here: AreaKey?, @Suppress("UNUSED_PARAMETER") rev: Int, onEdit: (AreaKey) -> Unit) {
    val ledger = live.ledger
    if (here != null) {
        val rec = ledger.areas[here.key]
        Row(
            Modifier.fillMaxWidth().heightIn(min = PcMin.DIALOG_TOUCH_DP.dp).clickable(role = Role.Button) { onEdit(here) }.background(Pc.Page).border(1.dp, Pc.Border).padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                DialogText("You are in", 12, Pc.Dim)
                DialogText(here.name, 14, Pc.Gold)
            }
            DialogText(rec?.encounter?.let { "${it.speciesName} ${it.outcome.label}" } ?: "open", 12, if (rec?.encounter == null) Pc.Text else Pc.Dim)
        }
        Spacer(Modifier.height(8.dp))
    }
    val areas = ledger.areas.values.toList().asReversed()
    if (areas.isEmpty()) DialogText("No area yet. The first wild battle starts the list.", 12, Pc.Dim)
    for (a in areas) {
        val enc = a.encounter
        Column(Modifier.fillMaxWidth().heightIn(min = PcMin.DIALOG_TOUCH_DP.dp).clickable(role = Role.Button) { onEdit(AreaKey(a.key, a.name)) }.padding(vertical = 5.dp)) {
            Row(Modifier.fillMaxWidth()) {
                DialogText(a.name, 13, Pc.Text, Modifier.weight(1f))
                DialogText(enc?.outcome?.label ?: "open", 12, outcomeColor(enc?.outcome))
            }
            if (enc != null) DialogText("${enc.speciesName} Lv ${enc.level}" + (if (enc.manual) ", set by hand" else ""), 12, Pc.Dim)
            for (x in a.extras) DialogText("${x.speciesName} Lv ${x.level}: ${x.kind.label}, ${x.outcome.label}", 12, Pc.Dim)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Pc.Border.copy(alpha = 0.4f)))
    }
}

private fun outcomeColor(o: Outcome?): Color = when (o) {
    Outcome.CAUGHT -> Pc.Positive
    Outcome.FAINTED, Outcome.LOST -> Pc.Negative
    null -> Pc.Text
    else -> Pc.Dim
}

@Composable
private fun TeamTab(live: NuzlockeTracking.Live, @Suppress("UNUSED_PARAMETER") rev: Int, onEdit: (Long) -> Unit, onAdd: () -> Unit) {
    val ledger = live.ledger
    NzSection("In the party")
    val party = ledger.party
    if (party.isEmpty()) DialogText("Nobody yet.", 12, Pc.Dim)
    for (m in party) MonRow(m) { onEdit(m.id) }
    NzSection("In a box")
    val boxed = ledger.boxed
    if (boxed.isEmpty()) DialogText("Nobody. The tracker cannot see inside a box, so a Pokemon that is not in the party is listed here.", 12, Pc.Dim)
    for (m in boxed) MonRow(m) { onEdit(m.id) }
    Spacer(Modifier.height(10.dp))
    GearButton("ADD A POKEMON") { onAdd() }
    DialogText("For one the tracker never saw in the party.", 12, Pc.Dim)
}

@Composable
private fun GraveTab(live: NuzlockeTracking.Live, @Suppress("UNUSED_PARAMETER") rev: Int, onEdit: (Long) -> Unit) {
    val dead = live.ledger.graveyard
    if (dead.isEmpty()) DialogText("Nobody has died.", 12, Pc.Dim)
    for (m in dead) {
        val d = m.death
        Column(Modifier.fillMaxWidth().heightIn(min = PcMin.DIALOG_TOUCH_DP.dp).clickable(role = Role.Button) { onEdit(m.id) }.padding(vertical = 5.dp)) {
            DialogText(m.label(d?.level ?: m.level), 13, Pc.Negative)
            if (d != null) {
                DialogText("${d.areaName}. ${d.cause}." + (if (d.manual) " Marked by hand." else ""), 12, Pc.Dim)
                val badges = if (d.badges != 0) "${Integer.bitCount(d.badges)} badges" else "no badges"
                DialogText(listOf(badges, whenText(d.at)).filter { it.isNotEmpty() }.joinToString(", "), 12, Pc.Dim)
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Pc.Border.copy(alpha = 0.4f)))
    }
}

@Composable
private fun MonRow(m: RosterMon, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().heightIn(min = PcMin.DIALOG_TOUCH_DP.dp).clickable(role = Role.Button) { onClick() }.padding(vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth()) {
            DialogText((if (m.violation) "! " else "") + m.shownName, 13, if (m.violation) Pc.Gold else Pc.Text, Modifier.weight(1f))
            DialogText("Lv ${m.level}", 12, Pc.Dim)
        }
        val gender = m.gender?.glyph?.let { "  $it" } ?: ""
        DialogText("${m.speciesName}$gender  ${m.origin.label}, ${m.areaName}", 12, Pc.Dim)
    }
}

@Composable
private fun LogTab(live: NuzlockeTracking.Live, @Suppress("UNUSED_PARAMETER") rev: Int, onDismiss: (String) -> Unit, onNote: () -> Unit) {
    val ledger = live.ledger
    NzSection("Warnings")
    val open = ledger.openWarnings
    if (open.isEmpty()) DialogText("None.", 12, Pc.Dim)
    for (w in open.asReversed()) {
        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                DialogText(w.kind.label + "  " + whenText(w.at), 12, Pc.Gold)
                DialogText(w.text, 12, Pc.Text)
            }
            Spacer(Modifier.width(6.dp))
            GearButton("OK", Modifier) { onDismiss(w.id) }
        }
    }
    NzSection("Notes")
    for (n in ledger.notes) DialogText(n, 12, Pc.Text)
    Spacer(Modifier.height(4.dp))
    GearButton("ADD A NOTE") { onNote() }
    NzSection("What happened")
    for (e in ledger.events.takeLast(80).asReversed()) {
        DialogText((if (e.manual) "* " else "") + whenText(e.at) + "  " + e.text, 12, if (e.manual) Pc.Gold else Pc.Dim, Modifier.padding(vertical = 1.dp))
    }
}

@Composable
private fun RulesTab(live: NuzlockeTracking.Live, snapshot: Snapshot?, @Suppress("UNUSED_PARAMETER") rev: Int, onChanged: () -> Unit) {
    val meta = live.ledger.meta
    val rules = meta.rules
    DialogText(meta.game, 13, Pc.Gold)
    DialogText("Started " + whenText(meta.startedAt) + ". " + meta.status.label + (if (meta.endReason.isNotBlank()) ": ${meta.endReason}" else "") + ".", 12, Pc.Dim)
    if (meta.genlockeId.isNotEmpty()) {
        DialogText("Genlocke, game ${meta.leg}. Carried in: " + meta.heirsIn.joinToString(", ") { it.speciesName }.ifEmpty { "nobody" } + ".", 12, Pc.Dim)
    }
    // Every finished run fills heirsOut; only a Genlocke has a next game to save them for (rc32 audit P2 #39).
    if (meta.heirsOut.isNotEmpty() && (meta.genlockeId.isNotEmpty() || rules.preset == NuzlockePreset.GENLOCKE))
        DialogText("Survivors saved for the next game: " + meta.heirsOut.joinToString(", ") { it.speciesName } + ".", 12, Pc.Positive)
    if (!meta.started) DialogText(if (meta.system.readsBalls) "The rules have not begun: they wait for the first Poke Ball." else "The rules have not begun: they wait for your first Pokemon.", 12, Pc.Dim)
    // What this family of games lets the tracker see, and what stays by hand (Game Boy and DS; Gen 3 has no notes).
    val notes = NuzlockeNotes.forGame(meta.system, meta.gameKey)
    if (!notes.isEmpty) {
        NzSection("What the tracker reads")
        for (t in notes.automatic) DialogText(t, 12, Pc.Text, Modifier.padding(vertical = 2.dp))
        NzSection("By hand in this game")
        for (t in notes.byHand) DialogText(t, 12, Pc.Gold, Modifier.padding(vertical = 2.dp))
    }
    Spacer(Modifier.height(6.dp))
    rules.monotypeLabel?.let { DialogText("Only $it Pokémon.", 12, Pc.Text) }
    DialogText("Tap a rule to change it. It applies from now on, and the log says it was changed.", 12, Pc.Dim)
    for (g in ClauseGroup.entries) {
        // Not the Genlocke switch: only a start reads it, so turning it here changed nothing (rc32 audit P2 #39).
        val cs = NuzlockeRules.CLAUSES.filter { it.group == g && it.key != "genlocke" }
        if (cs.isEmpty()) continue
        NzSection(g.title)
        for (c in cs) {
            val on = c.get(rules)
            Row(Modifier.fillMaxWidth().heightIn(min = PcMin.DIALOG_TOUCH_DP.dp).toggleable(value = on, role = Role.Switch) { live.edits.setClause(c.key, !on, System.currentTimeMillis()); onChanged() }.padding(vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically) {
                DialogText(c.title, 12, if (on) Pc.Text else Pc.Dim, Modifier.weight(1f))
                DialogText(if (on) "ON" else "off", 12, if (on) Pc.Positive else Pc.Dim)
            }
        }
    }
    NzSection("Areas")
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for (r in SafariRule.entries) NzChip(r.label, rules.safari == r) { live.edits.setSafari(r, System.currentTimeMillis()); onChanged() }
    }
    val caps = snapshot?.caps
    if (rules.levelCaps && caps != null) {
        val where = when {
            caps.fromRom -> "Level caps come from the boss teams in this game."
            caps.mixed -> "Some level caps come from the game, the rest from the standard table."
            else -> "Level caps come from the standard table for this game. If the game is changed or randomized they can be wrong."
        }
        DialogText(where, 12, Pc.Dim)
    }
    NzSection("The run")
    if (meta.status == RunStatus.ACTIVE) GearButton("END THE RUN") { live.edits.endRun("Ended by hand", System.currentTimeMillis()); onChanged() }
    if (meta.status == RunStatus.OVER || meta.status == RunStatus.COMPLETE) GearButton("OPEN IT AGAIN") { live.edits.reopen(System.currentTimeMillis()); onChanged() }
}

// ---------------------------------------------------------------------------------------------------------------
// Corrections by hand
// ---------------------------------------------------------------------------------------------------------------

@Composable
private fun AreaEditor(live: NuzlockeTracking.Live, area: AreaKey, @Suppress("UNUSED_PARAMETER") rev: Int, onClose: () -> Unit, onChanged: () -> Unit) {
    val ledger = live.ledger
    var species by remember(area.key) { mutableStateOf("") }
    var level by remember(area.key) { mutableStateOf("") }
    var outcome by remember(area.key) { mutableStateOf(Outcome.CAUGHT) }
    val enc = ledger.areas[area.key]?.encounter
    val now = System.currentTimeMillis()
    InfoSheet(area.name, onClose) {
        if (enc != null) {
            DialogText("${enc.speciesName} Lv ${enc.level}, ${enc.outcome.label}" + (if (enc.manual) ". Set by hand." else "."), 13, Pc.Text)
            Spacer(Modifier.height(6.dp))
            DialogText("How it ended", 12, Pc.Dim)
            OutcomeChips(enc.outcome) { o -> live.edits.setEncounter(area, enc.species, enc.speciesName, enc.level, o, now); onChanged() }
            Spacer(Modifier.height(8.dp))
            GearButton("THIS DOES NOT COUNT") { live.edits.clearEncounter(area.key, now); onChanged(); onClose() }
            DialogText("The area is open again for the next Pokemon.", 12, Pc.Dim)
        } else {
            DialogText("Nothing counts here yet. The next wild Pokemon will.", 12, Pc.Dim)
        }
        NzSection(if (enc == null) "Say what happened here" else "Change it")
        NzField("Pokemon", species) { species = it }
        NzField("Level", level, numbers = true) { level = it.filter { c -> c.isDigit() }.take(3) }
        OutcomeChips(outcome) { outcome = it }
        Spacer(Modifier.height(6.dp))
        val lv = level.toIntOrNull()?.takeIf { it in 1..100 }
        GearButton(if (species.isBlank() || lv == null) "TYPE A NAME AND A LEVEL" else "SAVE") {
            if (species.isNotBlank() && lv != null) {
                live.edits.setEncounter(area, NuzlockeFamilies.speciesId(species, ledger.meta.system) ?: 0, species, lv, outcome, now)
                species = ""; level = ""; onChanged()
            }
        }
        val mons = ledger.roster.values.toList()
        if (mons.isNotEmpty()) {
            NzSection("Or count one of yours here")
            DialogText("A gift or a static the tracker did not count.", 12, Pc.Dim)
            for (m in mons) {
                Row(Modifier.fillMaxWidth().heightIn(min = PcMin.DIALOG_TOUCH_DP.dp).clickable(role = Role.Button) { live.edits.countAsEncounter(m.id, area, now); onChanged(); onClose() }.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    DialogText(m.shownName, 12, if (m.alive) Pc.Text else Pc.Dim, Modifier.weight(1f))
                    DialogText("${m.speciesName} Lv ${m.level}, ${m.origin.label}", 12, Pc.Dim)
                }
            }
        }
    }
}

@Composable
private fun OutcomeChips(current: Outcome, onPick: (Outcome) -> Unit) {
    FlowRow(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for (o in listOf(Outcome.CAUGHT, Outcome.FAINTED, Outcome.FLED, Outcome.RAN, Outcome.LOST)) NzChip(o.label, current == o) { onPick(o) }
    }
}

@Composable
private fun MonEditor(
    live: NuzlockeTracking.Live, mon: RosterMon, here: AreaKey?,
    /** The badges the game shows now, with [here]; null with no game running. */
    badges: Int?,
    @Suppress("UNUSED_PARAMETER") rev: Int, onClose: () -> Unit, onChanged: () -> Unit,
) {
    var cause by remember(mon.id) { mutableStateOf("") }
    var newCause by remember(mon.id) { mutableStateOf(mon.death?.cause ?: "") }
    var removing by remember(mon.id) { mutableStateOf(false) }
    val now = System.currentTimeMillis()
    InfoSheet(mon.shownName, onClose) {
        DialogText("${mon.speciesName} Lv ${mon.level}" + (mon.gender?.let { "  ${it.glyph}" } ?: ""), 13, Pc.Text)
        DialogText("${mon.origin.label} at ${mon.areaName}", 12, Pc.Dim)
        if (mon.violation) DialogText("The rules did not allow this catch. See the log.", 12, Pc.Gold)
        val d = mon.death
        if (d != null) DialogText("Died at ${d.areaName}: ${d.cause}" + (if (d.manual) " (by hand)" else "") + ".", 12, Pc.Negative)
        Spacer(Modifier.height(8.dp))
        if (mon.alive) {
            NzField("How it died", cause) { cause = it }
            // Where the player is and the badges held, as a death the tracker sees records them (rc32 audit P3 #115).
            GearButton("MARK DEAD") { live.edits.markDead(mon.id, cause.trim(), now, here?.name, badges); onChanged(); onClose() }
        } else {
            // The tracker names what was on the field when it fainted, which is not always what did it.
            NzField("What killed it", newCause) { newCause = it }
            GearButton("SAVE THE CAUSE") { if (live.edits.setCause(mon.id, newCause, now)) onChanged() }
            Spacer(Modifier.height(8.dp))
            GearButton("THE DEATH DOES NOT COUNT") { live.edits.markAlive(mon.id, now); onChanged(); onClose() }
            DialogText("It stays alive here even while the game shows it at 0 HP, until it has been healthy once.", 12, Pc.Dim)
        }
        NzSection("What it is")
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            for (o in listOf(Origin.CAUGHT, Origin.GIFT, Origin.STATIC, Origin.EXTRA)) {
                NzChip(o.label, mon.origin == o) { live.edits.setOrigin(mon.id, o, now); onChanged(); onClose() }
            }
        }
        if (here != null) {
            NzSection("Its area")
            GearButton("Count it as the encounter of " + here.name) { live.edits.countAsEncounter(mon.id, here, now); onChanged(); onClose() }
        }
        // A Pokemon added by hand or made for an area that never was: off the roster, with a second tap to be sure
        // (rc32 audit P2 #139). One the tracker saw is in the game and has no such button.
        if (mon.id < 0 && !mon.inParty) {
            NzSection("You never had it")
            if (!removing) {
                GearButton("TAKE IT OFF THE ROSTER") { removing = true }
                DialogText("For a Pokemon added by hand, or made when an area was set to caught, that you never had.", 12, Pc.Dim)
            } else {
                DialogText("${mon.shownName} goes off the roster for good.", 12, Pc.Gold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    GearButton("TAKE IT OFF", Modifier.weight(1f)) { live.edits.removeMon(mon.id, now); onChanged(); onClose() }
                    GearButton("KEEP IT", Modifier.weight(1f)) { removing = false }
                }
            }
        }
    }
}

/**
 * A Pokemon the tracker never saw in the party: a catch made while the app was closed, or on another save. Where
 * it came from is typed the way the game names the place; it goes through the same grouping the tracker's own
 * areas do, so a Route 3 typed here is the Route 3 the tracker counts.
 */
@Composable
private fun AddMonEditor(live: NuzlockeTracking.Live, here: AreaKey?, @Suppress("UNUSED_PARAMETER") rev: Int, onClose: () -> Unit, onChanged: () -> Unit) {
    var species by remember { mutableStateOf("") }
    var level by remember { mutableStateOf("") }
    var place by remember { mutableStateOf(here?.name ?: "") }
    var origin by remember { mutableStateOf(Origin.CAUGHT) }
    val id = NuzlockeFamilies.speciesId(species, live.ledger.meta.system)
    val lv = level.toIntOrNull()?.takeIf { it in 1..100 }
    InfoSheet("Add a Pokemon", onClose) {
        DialogText("It goes on the roster, out of the party. To make it an area's encounter, open it after and count it there.", 12, Pc.Dim)
        NzField("Pokemon", species) { species = it }
        if (species.isNotBlank() && id == null) DialogText("No Pokemon has that name, so it is added with no species and the dupes clause cannot see it.", 12, Pc.Dim)
        NzField("Level", level, numbers = true) { level = it.filter { c -> c.isDigit() }.take(3) }
        NzField("Where it came from", place) { place = it }
        Spacer(Modifier.height(6.dp))
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            for (o in listOf(Origin.CAUGHT, Origin.GIFT, Origin.STATIC, Origin.EXTRA)) NzChip(o.label, origin == o) { origin = o }
        }
        Spacer(Modifier.height(8.dp))
        GearButton(if (species.isBlank() || lv == null) "TYPE A NAME AND A LEVEL" else "ADD IT") {
            if (species.isNotBlank() && lv != null) {
                val area = place.trim().takeIf { it.isNotEmpty() }
                    ?.let { NuzlockeAreas.of(NzArea(it, null), null, live.ledger.meta.rules, live.ledger.meta.system) }
                    ?: AreaKey("unknown", "Unknown place")
                live.edits.addMon(species, id ?: 0, lv, origin, area, System.currentTimeMillis())
                onChanged(); onClose()
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------
// Small parts, in the tracker's look
// ---------------------------------------------------------------------------------------------------------------

@Composable
private fun NzSection(text: String) {
    Spacer(Modifier.height(10.dp))
    DialogText(text.uppercase(), 13, Pc.Gold, heading = true)
    Box(Modifier.fillMaxWidth().padding(vertical = 3.dp).height(1.dp).background(Pc.Border.copy(alpha = 0.5f)))
}

/**
 * One of a set of choices (the ledger's tabs, an outcome, an origin, the Safari rule): 48dp tall, selectable, the chosen
 * one marked by its fill and an underline, its label in sp (rc32 audit P2 #19, rc35 follow-up N #29).
 */
@Composable
private fun NzChip(label: String, on: Boolean, role: Role = Role.RadioButton, onClick: () -> Unit) {
    Box(
        Modifier.heightIn(min = PcMin.DIALOG_TOUCH_DP.dp).background(if (on) Pc.Border else Color(0xFF303030))
            .selectable(selected = on, role = role) { onClick() }.padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) { DialogText(label, 12, if (on) Pc.Page else Pc.Text, underline = on) }
}

@Composable
private fun NzField(label: String, value: String, numbers: Boolean = false, onChange: (String) -> Unit) {
    Spacer(Modifier.height(4.dp))
    androidx.compose.material3.OutlinedTextField(
        value = value, onValueChange = onChange, singleLine = true,
        label = { DialogText(label, 12, Pc.Dim) },
        textStyle = androidx.compose.ui.text.TextStyle(color = Pc.Text),
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
            keyboardType = if (numbers) androidx.compose.ui.text.input.KeyboardType.Number else androidx.compose.ui.text.input.KeyboardType.Text,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** A one-field question in the tracker's look. */
@Composable
private fun TextPrompt(title: String, initial: String, onDone: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(initial) }
    InfoSheet(title, onDismiss) {
        NzField("", text) { text = it }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            GearButton("SAVE", Modifier.weight(1f)) { onDone(text) }
            GearButton("CANCEL", Modifier.weight(1f)) { onDismiss() }
        }
    }
}
