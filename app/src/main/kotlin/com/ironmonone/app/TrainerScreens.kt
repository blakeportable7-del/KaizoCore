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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.ironmonone.tracker.GbaTracker

/**
 * TrainersOnRouteScreen.lua: the trainers stationed on the current map, one
 * row each with class and name, party size and a check when defeated, and the
 * "N of M defeated" line; tapping a row opens Trainer Info.
 */
@Composable
fun TrainersOnRouteDialog(routeName: String, trainers: List<GbaTracker.TrainerInfo>, onTrainer: (GbaTracker.TrainerInfo) -> Unit, onClose: () -> Unit) {
    val defeated = trainers.count { it.defeated }
    val monsDefeated = trainers.filter { it.defeated }.sumOf { it.party.size }
    val monsTotal = trainers.sumOf { it.party.size }
    Dialog(onDismissRequest = onClose) {
        Column(Modifier.width(300.dp).background(Pc.Page).border(1.dp, Pc.Border).padding(8.dp).verticalScroll(rememberScrollState())) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                PixText(routeName.uppercase(), 10, Pc.Text, Modifier.weight(1f))
                PixText("X", 9, Pc.Dim, Modifier.clickable { onClose() }.padding(horizontal = 6.dp, vertical = 2.dp))
            }
            Spacer(Modifier.height(4.dp))
            PixText("Trainers: $defeated / ${trainers.size} defeated, Pokemon: $monsDefeated / $monsTotal", 7, Pc.Dim)
            Spacer(Modifier.height(6.dp))
            if (trainers.isEmpty()) PixText("No trainers here.", 8, Pc.Text)
            trainers.forEach { t ->
                Row(Modifier.fillMaxWidth().clickable { onTrainer(t) }.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    PixText(if (t.defeated) "\u2713" else " ", 8, Pc.Positive, Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        PixText((t.className + " " + t.name).trim(), 8, Pc.Text)
                        PixText("${t.party.size} Pokemon, Lv.${t.minLevel}" + (if (t.maxLevel != t.minLevel) "-${t.maxLevel}" else ""), 7, Pc.Dim)
                    }
                }
            }
        }
    }
}

/**
 * TrainerInfoScreen.lua: class and name, route, team size and level range
 * (red when the lead is below the lowest), average IVs, the AI label, and the
 * party one row each: species, level, held item, and custom moves where the
 * trainer has them.
 */
@Composable
fun TrainerInfoDialog(
    t: GbaTracker.TrainerInfo,
    routeName: String?,
    leadLevel: Int?,
    speciesName: (Int) -> String,
    itemName: (Int) -> String,
    moveName: (Int) -> String,
    onClose: () -> Unit,
) {
    Dialog(onDismissRequest = onClose) {
        Column(Modifier.width(300.dp).background(Pc.Page).border(1.dp, Pc.Border).padding(8.dp).verticalScroll(rememberScrollState())) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                PixText((t.className + " " + t.name).trim().uppercase(), 10, Pc.Text, Modifier.weight(1f))
                PixText("X", 9, Pc.Dim, Modifier.clickable { onClose() }.padding(horizontal = 6.dp, vertical = 2.dp))
            }
            Spacer(Modifier.height(4.dp))
            @Composable fun row(label: String, value: String, color: androidx.compose.ui.graphics.Color = Pc.Text) {
                Row(Modifier.fillMaxWidth()) { PixText(label, 8, Pc.Dim, Modifier.width(96.dp)); PixText(value, 8, color) }
            }
            row("Route", routeName ?: "???")
            val lvRange = if (t.minLevel == t.maxLevel) "${t.minLevel}" else "${t.minLevel}-${t.maxLevel}"
            row("Team", "${t.party.size} Pokemon, Lv.$lvRange", if (leadLevel != null && leadLevel < t.minLevel) Pc.Negative else Pc.Gold)
            row("Avg IVs", "${t.avgIvs}", if (t.avgIvs > 0) Pc.Gold else Pc.Text)
            row("AI", t.aiLabel + (if (t.doubleBattle) ", doubles" else ""))
            if (t.defeated) row("Status", "Defeated", Pc.Positive)
            Spacer(Modifier.height(6.dp))
            PixText("Party", 8, Pc.Gold)
            t.party.forEach { m ->
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    PixText(speciesName(m.species), 8, Pc.Text, Modifier.weight(1f))
                    PixText("Lv.${m.level}", 8, Pc.Text, Modifier.width(44.dp), TextAlign.End)
                    PixText(if (m.heldItem != 0) itemName(m.heldItem) else "", 7, Pc.Dim, Modifier.width(90.dp), TextAlign.End)
                }
                if (m.moves.isNotEmpty()) PixText(m.moves.filter { it != 0 }.joinToString(", ") { moveName(it) }, 7, Pc.Dim, Modifier.padding(start = 8.dp), wrap = true)
            }
        }
    }
}
