package com.ironmonone.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.unit.dp
import com.ironmonone.tracker.LossCondition

/**
 * The tracker's gear (the reference's SettingsGear, which opens NavigationMenu).
 * Ported: the Setup and Gameplay options this panel honours, the Notebook
 * (every species marked or noted this run), and Manage Data's clear. Not
 * ported: theme, language, updates, extensions, streaming and quickload
 * screens, which belong to BizHawk or to this app's own settings.
 */
@Composable
fun TrackerGearDialog(
    speciesName: (Int) -> String,
    marks: StatMarks,
    onCleared: () -> Unit,
    onRules: () -> Unit = {},
    onCoverage: () -> Unit = {},
    onStats: (() -> Unit)? = null,
    onTrainers: (() -> Unit)? = null,
    onBattleDetails: (() -> Unit)? = null,
    onCatchRates: (() -> Unit)? = null,
    onNotebook: (() -> Unit)? = null,
    onHeals: (() -> Unit)? = null,
    onTimeMachine: (() -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    var confirmClear by remember { mutableStateOf(false) }
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.width(320.dp).heightIn(max = 560.dp).background(Pc.Ground).border(1.dp, Pc.Border)
                .verticalScroll(rememberScrollState()).padding(10.dp),
        ) {
            PixText("TRACKER SETUP", 10, Pc.Gold)
            Spacer(Modifier.height(8.dp))
            com.ironmonone.app.gen3.Gen3Button("RULES FOR THIS RUN") { onRules() }
            com.ironmonone.app.gen3.Gen3Button("COVERAGE CALC") { onCoverage() }
            onStats?.let { com.ironmonone.app.gen3.Gen3Button("STATS") { it() } }
            onTrainers?.let { com.ironmonone.app.gen3.Gen3Button("TRAINERS ON ROUTE") { it() } }
            onBattleDetails?.let { com.ironmonone.app.gen3.Gen3Button("BATTLE DETAILS") { it() } }
            onCatchRates?.let { com.ironmonone.app.gen3.Gen3Button("CATCH RATES") { it() } }
            onHeals?.let { com.ironmonone.app.gen3.Gen3Button("HEALS IN BAG") { it() } }
            onTimeMachine?.let { com.ironmonone.app.gen3.Gen3Button("TIME MACHINE") { it() } }
            Spacer(Modifier.height(8.dp))

            PixText("Options", 8, Pc.Text)
            Spacer(Modifier.height(4.dp))
            GearToggle("Show random ball picker", TrackerOptions.showBallPicker) { TrackerOptions.showBallPicker = it; TrackerOptions.save() }
            GearToggle("Show physical special icons", TrackerOptions.showCategoryIcons) { TrackerOptions.showCategoryIcons = it; TrackerOptions.save() }
            GearToggle("Show heals as whole number", TrackerOptions.healsWhole) { TrackerOptions.healsWhole = it; TrackerOptions.save() }
            GearToggle("Show team view", TrackerOptions.showTeamView) { TrackerOptions.showTeamView = it; TrackerOptions.save() }
            Spacer(Modifier.height(8.dp))

            PixText("Landscape tracker", 8, Pc.Text)
            Spacer(Modifier.height(4.dp))
            LandscapeTracker.entries.forEach { m ->
                GearToggle(m.label, TrackerOptions.landscapeTracker == m, radio = true) {
                    if (it) { TrackerOptions.landscapeTracker = m; TrackerOptions.save() }
                }
            }
            Spacer(Modifier.height(8.dp))

            PixText("Game is considered over when", 8, Pc.Text)
            Spacer(Modifier.height(4.dp))
            LossCondition.entries.forEach { c ->
                GearToggle(c.label, TrackerOptions.lossCondition == c, radio = true) {
                    if (it) { TrackerOptions.lossCondition = c; TrackerOptions.save() }
                }
            }
            Spacer(Modifier.height(8.dp))

            PixText("Notebook", 8, Pc.Text)
            Spacer(Modifier.height(4.dp))
            onNotebook?.let { com.ironmonone.app.gen3.Gen3Button("OPEN NOTEBOOK") { it() }; Spacer(Modifier.height(4.dp)) }
            val noted = remember(marks) { (marks.markedSpecies() + marks.notedSpecies()).sorted() }
            if (noted.isEmpty()) PixText("Nothing marked or noted this run.", 7, Pc.Dim)
            noted.forEach { sp ->
                val m = marks.of(sp)
                val summary = StatMarks.STAT_NAMES.indices.mapNotNull { i ->
                    when (m.getOrElse(i) { 0 }) { 1 -> "${StatMarks.STAT_NAMES[i]}+"; 2 -> "${StatMarks.STAT_NAMES[i]}--"; 3 -> "${StatMarks.STAT_NAMES[i]}="; else -> null }
                }.joinToString(" ")
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    PixText(speciesName(sp).uppercase(), 7, Pc.Text, Modifier.width(96.dp))
                    Column(Modifier.weight(1f)) {
                        if (summary.isNotEmpty()) PixText(summary, 7, Pc.Gold)
                        val note = marks.noteFor(sp)
                        if (note.isNotEmpty()) PixText(note, 7, Pc.Dim)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))

            PixText("Manage data", 8, Pc.Text)
            Spacer(Modifier.height(4.dp))
            if (!confirmClear) {
                com.ironmonone.app.gen3.Gen3Button("CLEAR TRACKED DATA") { confirmClear = true }
            } else {
                PixText("Marks, notes, routes, moves and abilities for this run. Sure?", 7, Pc.Negative)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    com.ironmonone.app.gen3.Gen3Button("YES, CLEAR", accent = true) { marks.clear(); confirmClear = false; onCleared() }
                    com.ironmonone.app.gen3.Gen3Button("CANCEL") { confirmClear = false }
                }
            }
            Spacer(Modifier.height(12.dp))
            com.ironmonone.app.gen3.Gen3Button("CLOSE") { onDismiss() }
        }
    }
}

/** A reference-style checkbox row: an 8px box with the label beside it. */
@Composable
internal fun GearToggle(label: String, on: Boolean, radio: Boolean = false, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(if (radio) true else !on) }.padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(12.dp).border(1.dp, Pc.Border), contentAlignment = Alignment.Center) {
            if (on) Box(Modifier.size(6.dp).background(Pc.Gold))
        }
        Spacer(Modifier.width(8.dp))
        PixText(label, 7, Pc.Text)
    }
}
