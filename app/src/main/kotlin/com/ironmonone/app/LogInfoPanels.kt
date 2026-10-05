package com.ironmonone.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.ironmonone.tracker.GbaTracker
import com.ironmonone.tracker.Gen3Types

/**
 * The PC tracker's side panel while its log is open, for the Gen 3 log viewer. On PC, opening a trainer in the log also
 * puts that trainer's TrainerInfoScreen beside it (LogTabTrainers.lua:209-211, LogTabTMs.lua:171-173,
 * LogTabRouteDetails.lua:338-340), and opening a Pokemon puts its InfoScreen POKEMON_INFO there
 * (LogTabPokemon.lua:138, LogTabTrainerDetails.lua:86). A phone has no room beside the page, so each panel's lines sit
 * at the top of the page they go with (Blake, 2026-10-04, with PC screenshots of Nat. Dex FireRed).
 */
internal object LogInfoPanels {
    /** TrainerInfoScreen.lua's lines for one trainer, as [trainer] builds them. */
    data class Trainer(
        /** "Pokémon:" then "N  (Lv.min -- max)" (TrainerInfoScreen.lua:100-114, 230-250). */
        val team: String,
        /** Red when your lead is below the lowest level (:251-256). */
        val leadBelow: Boolean,
        /** The party's average IV, floor of the mean, 0 at least (:258-263). */
        val avgIvs: Int,
        /** "Smart", "Semi-Smart", "Normal", "Dumb" or "Complex", from the script flags (:271-288). */
        val script: String,
        /** "Usable Items:" (:290-311), null when it has none. */
        val items: String?,
        /** "Double" / "Battle!" under the portrait (:59-62). */
        val doubleBattle: Boolean,
        /** "# id" under the portrait (:26-31). */
        val id: Int,
    )

    /**
     * The label of the script line: the PC tracker's word (Languages/English.lua:472 spells it "A I Script" for its pixel
     * font). The trainer's AI is the game's term for how it battles (Blake, 2026-10-05: "AI Script").
     */
    const val SCRIPT_LABEL = "AI Script"

    /** The label of the items line (Languages/English.lua:473). */
    const val ITEMS_LABEL = "Usable Items"

    fun trainer(t: GbaTracker.TrainerInfo, leadLevel: Int?, itemName: (Int) -> String?): Trainer {
        val lv = if (t.minLevel == t.maxLevel) "${t.minLevel}" else "${t.minLevel} -- ${t.maxLevel}"
        return Trainer(
            team = "${t.party.size}  (Lv.$lv)",
            leadBelow = leadLevel != null && leadLevel < t.minLevel,
            avgIvs = t.avgIvs,
            script = t.aiLabel,
            items = TrainerInfoView.usableItems(t.items, itemName),
            doubleBattle = t.doubleBattle,
            id = t.id,
        )
    }

    /** A 2x or 4x weakness: the type's name and whether it is 4x (drawn with bars on PC, InfoScreen.lua:830-840). */
    data class Weakness(val type: String, val quad: Boolean)

    /**
     * InfoScreen.lua:808-845, "Weak to": the types that hit [types] for 2x or 4x on the game's chart, from the log's own
     * types (InfoScreen.lua:704-710 reads them from the log while it is open). Empty means "Has no weaknesses".
     */
    fun weakTo(types: List<String>, natDex: Boolean): List<Weakness> {
        val ids = types.mapNotNull { Gen3Types.idOf(it) }
        val t1 = ids.firstOrNull() ?: return emptyList()
        val t2 = ids.getOrNull(1) ?: t1
        val d = Gen3Types.defenses(t1, t2, natDex = natDex)
        return d[2.0].orEmpty().map { Weakness(it, false) } + d[4.0].orEmpty().map { Weakness(it, true) }
    }

    /** TypeDefensesScreen's table for the log's types ("Show resistances", InfoScreen.lua:116-129). */
    fun defenses(types: List<String>, natDex: Boolean): Map<Double, List<String>> {
        val ids = types.mapNotNull { Gen3Types.idOf(it) }
        val t1 = ids.firstOrNull() ?: return emptyMap()
        return Gen3Types.defenses(t1, ids.getOrNull(1) ?: t1, natDex = natDex)
    }

    /** InfoScreen.NotepadTracking: the note, or "(Leave a note)" (Languages/English.lua:135). */
    fun noteLine(note: String): String = note.ifBlank { "(Leave a note)" }
}

/** TrainerInfoScreen's lines at the top of a trainer's log page. */
@Composable
internal fun LogTrainerInfoPanel(p: LogInfoPanels.Trainer, routeName: String?) {
    Column(Modifier.fillMaxWidth().background(Pc.Page).border(1.dp, Pc.Border).padding(10.dp)) {
        @Composable fun line(label: String, value: String, color: androidx.compose.ui.graphics.Color) {
            Row(Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
                DialogText("$label:", 13, Pc.Text, Modifier.width(110.dp))
                DialogText(value, 13, color, Modifier.weight(1f))
            }
        }
        Row(Modifier.fillMaxWidth()) {
            DialogText(routeName ?: "???", 13, Pc.Text, Modifier.weight(1f))
            DialogText("# ${p.id}", 13, Pc.Text)
        }
        if (p.doubleBattle) DialogText("Double Battle!", 13, Pc.Gold)
        Spacer(Modifier.height(4.dp))
        line("Pokémon", p.team, if (p.leadBelow) Pc.Negative else Pc.Gold)
        line("Avg. IVs", "${p.avgIvs}", if (p.avgIvs > 0) Pc.Gold else Pc.Text)
        // Every label but Normal is drawn in the highlight colour (TrainerInfoScreen.lua:273-288).
        line(LogInfoPanels.SCRIPT_LABEL, p.script, if (p.script == "Normal") Pc.Text else Pc.Gold)
        p.items?.let {
            DialogText("${LogInfoPanels.ITEMS_LABEL}:", 13, Pc.Text, Modifier.padding(top = 2.dp))
            DialogText(it, 13, Pc.Positive, Modifier.padding(start = 8.dp))
        }
    }
}

/** InfoScreen POKEMON_INFO's History, Weak to, Show resistances and note, on a Pokemon's log page. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LogPokemonInfoPanel(
    weak: List<LogInfoPanels.Weakness>,
    note: String,
    onHistory: (() -> Unit)?,
    onResistances: () -> Unit,
    onNote: (() -> Unit)?,
) {
    Column(Modifier.fillMaxWidth().background(Pc.Page).border(1.dp, Pc.Border).padding(horizontal = 10.dp, vertical = 4.dp)) {
        @Composable fun link(label: String, onClick: () -> Unit) {
            Box(Modifier.heightIn(min = PcMin.DIALOG_TOUCH_DP.dp).clickable(role = Role.Button) { onClick() }.padding(end = 18.dp),
                contentAlignment = androidx.compose.ui.Alignment.CenterStart) {
                DialogText(label, 13, Pc.Text, underline = true)
            }
        }
        DialogText("Weak to:", 13, Pc.Text, Modifier.padding(top = 6.dp))
        Spacer(Modifier.height(4.dp))
        if (weak.isEmpty()) DialogText(InfoScreenLines.NO_WEAKNESSES, 13, Pc.Text, Modifier.padding(start = 8.dp))
        else FlowRow(Modifier.padding(start = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            weak.forEach { w ->
                // PC marks a 4x weakness with white bars over its type badge; here a frame in the same white.
                Box(if (w.quad) Modifier.border(2.dp, androidx.compose.ui.graphics.Color.White).padding(2.dp) else Modifier.padding(2.dp)) {
                    PcTypeChip(w.type.uppercase(), pcTypeColorByName(w.type))
                }
            }
        }
        Row {
            onHistory?.let { link("History", it) }
            link("Show resistances", onResistances)
        }
        val shown = LogInfoPanels.noteLine(note)
        Box(
            Modifier.fillMaxWidth().heightIn(min = PcMin.DIALOG_TOUCH_DP.dp)
                .then(if (onNote != null) Modifier.clickable(role = Role.Button) { onNote() } else Modifier),
            contentAlignment = androidx.compose.ui.Alignment.CenterStart,
        ) { DialogText(shown, 13, if (note.isBlank()) Pc.Dim else Pc.Text) }
    }
}

/** TrackerScreen.openNotePadWindow from the log: the species' note for this run, one line, saved into the run's notes. */
@Composable
internal fun LogNoteDialog(name: String, note: String, onSave: (String) -> Unit, onClose: () -> Unit) {
    var draft by remember(name) { mutableStateOf(note) }
    androidx.compose.ui.window.Dialog(onDismissRequest = onClose) {
        Column(Modifier.background(Pc.Ground).border(1.dp, Pc.Border).padding(12.dp)) {
            DialogText("Note for $name", 14, Pc.Gold, heading = true)
            Spacer(Modifier.height(8.dp))
            androidx.compose.material3.OutlinedTextField(
                value = draft, onValueChange = { draft = it }, singleLine = false,
                textStyle = androidx.compose.ui.text.TextStyle(color = Pc.Text), modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LogButton("Save", Pc.Gold) { onSave(draft) }
                LogButton("Cancel", Pc.Text) { onClose() }
            }
        }
    }
}
