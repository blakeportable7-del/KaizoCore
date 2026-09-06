package com.ironmonone.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Everything the move popup shows. Built by the panel from a [PcMove] plus
 * what only the panel knows: the description, and who is across the field.
 */
data class MoveDetail(
    val name: String,
    val typeId: Int?,
    val typeName: String?,
    /** "PHY" | "SPE" | "STA" | null */
    val category: String?,
    val contact: Boolean?,
    val pp: Int,
    val ppMax: Int?,
    val power: Int?,
    val acc: Int?,
    val priority: Int?,
    val summary: String?,
    /**
     * General chart facts about the move's type; nothing about the opponent.
     * Not in the reference InfoScreen; asked for, then narrowed to this, by
     * Blake on 2026-09-05. See MoveMatchup.
     */
    val typeChart: MoveMatchup.General? = null,
)

/**
 * The move info screen: a clone of InfoScreen.drawMoveInfoScreen
 * (InfoScreen.lua:861), in the reference's order - name, type, category,
 * contact, PP, power, accuracy, priority only when it is not 0, then the
 * "Move summary" box - with one line the reference does not have, the matchup
 * against the current opponent, which Blake asked for on 2026-09-05.
 *
 * Split from the Dialog wrapper so the render harness can draw the CONTENT:
 * a Dialog is its own window and never appears in a captured root.
 */
@Composable
fun PcMoveInfoContent(d: MoveDetail, onDismiss: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().heightIn(max = 460.dp)
            .background(Pc.Ground).border(1.dp, Pc.Border)
            .verticalScroll(rememberScrollState())
            .padding(14.dp)
    ) {
        // MOVE NAME, as the reference's header: upper case.
        PixText(d.name.uppercase(), 11, Pc.Gold)
        Spacer(Modifier.height(6.dp))
        // TYPE ICON
        if (d.typeName != null) {
            PcTypeChip(d.typeName, d.typeId?.let { pcTypeColor(it) } ?: pcTypeColorByName(d.typeName))
            Spacer(Modifier.height(6.dp))
        }
        Fact("Category", when (d.category) {
            "PHY" -> "Physical"; "SPE" -> "Special"; "STA" -> "Status"; else -> "-"
        })
        Fact("Contact", when (d.contact) { true -> "Yes"; false -> "No"; null -> "-" })
        Fact("PP", d.ppMax?.let { "${d.pp}/$it" } ?: "${d.pp}")
        Fact("Power", d.power?.takeIf { it > 0 }?.toString() ?: "-")
        Fact("Accuracy", d.acc?.takeIf { it > 0 }?.let { "$it%" } ?: "-")
        // PRIORITY: only takes a line when it is helpful (exists and non-zero).
        d.priority?.takeIf { it != 0 }?.let {
            Fact("Priority", if (it > 0) "+$it" else "$it")
        }
        // The type chart for this move's type, in general. No opponent here.
        d.typeChart?.let { g ->
            Spacer(Modifier.height(6.dp))
            if (g.strongAgainst.isNotEmpty())
                PixText("Strong against: " + g.strongAgainst.joinToString(", "), 8, Pc.Text, wrap = true)
            if (g.resistedBy.isNotEmpty())
                PixText("Resisted by: " + g.resistedBy.joinToString(", "), 8, Pc.Dim, wrap = true)
            if (g.noEffectOn.isNotEmpty())
                PixText("No effect on: " + g.noEffectOn.joinToString(", "), 8, Pc.Dim, wrap = true)
        }
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(Pc.Border))
        Spacer(Modifier.height(6.dp))
        // SUMMARY box
        PixText("Move summary:", 8, Pc.Gold)
        Spacer(Modifier.height(4.dp))
        PixText(
            d.summary?.takeIf { it.isNotBlank() } ?: "No description for this one.",
            8, if (d.summary.isNullOrBlank()) Pc.Dim else Pc.Text, wrap = true,
        )
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            PcSmallButton("CLOSE") { onDismiss() }
        }
    }
}

/** One "Label:   value" line, the reference's two-column layout (offsetColumnX). */
@Composable
private fun Fact(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
        Box(Modifier.width(72.dp)) { PixText("$label:", 8, Pc.Text) }
        PixText(value, 8, Pc.Text)
    }
}

@Composable
fun PcMoveInfoDialog(d: MoveDetail, onDismiss: () -> Unit) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        PcMoveInfoContent(d, onDismiss)
    }
}
