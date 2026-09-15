package com.ironmonone.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
 * The move info screen: InfoScreen.drawMoveInfoScreen (InfoScreen.lua:861)'s
 * facts - name, type, category, contact, PP, power, accuracy, priority only
 * when it is not 0, the "Move summary" - plus the type's matchups, which Blake
 * asked for on 2026-09-05. Laid out 2026-09-15 as a compact card (InfoCard):
 * the name with its type and category in the header, the numbers as a strip
 * of large figures, then the matchups and the summary at a readable size.
 *
 * Split from the Dialog wrapper so the render harness can draw the CONTENT:
 * a Dialog is its own window and never appears in a captured root.
 */
@Composable
fun PcMoveInfoContent(d: MoveDetail, onDismiss: () -> Unit) {
    val category = when (d.category) { "PHY" -> "PHYSICAL"; "SPE" -> "SPECIAL"; "STA" -> "STATUS"; else -> null }
    InfoCard(
        title = d.name.uppercase(), onDismiss = onDismiss,
        headerExtra = {
            if (d.typeName != null) {
                InfoTypeTag(d.typeName, d.typeId?.let { pcTypeColor(it) } ?: pcTypeColorByName(d.typeName))
                Spacer(Modifier.width(6.dp))
            }
            if (category != null) InfoTag(category, when (d.category) {
                "PHY" -> Color(0xFFF08030); "SPE" -> Color(0xFF6890F0); else -> Pc.Dim
            })
        },
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            InfoStat("PP", d.ppMax?.let { "${d.pp}/$it" } ?: "${d.pp}")
            InfoStat("POWER", d.power?.takeIf { it > 0 }?.toString() ?: "-")
            InfoStat("ACCURACY", d.acc?.takeIf { it > 0 }?.let { "$it%" } ?: "-")
            InfoStat("CONTACT", when (d.contact) { true -> "Yes"; false -> "No"; null -> "-" })
            // PRIORITY: only takes a place when it is helpful (exists and non-zero).
            d.priority?.takeIf { it != 0 }?.let { InfoStat("PRIORITY", if (it > 0) "+$it" else "$it", Pc.Gold) }
        }
        // The type chart for this move's type, in general. No opponent here.
        d.typeChart?.let { g ->
            Spacer(Modifier.height(10.dp))
            if (g.strongAgainst.isNotEmpty()) Matchup("Strong against", g.strongAgainst, Pc.Positive)
            if (g.resistedBy.isNotEmpty()) Matchup("Resisted by", g.resistedBy, Pc.Gold)
            if (g.noEffectOn.isNotEmpty()) Matchup("No effect on", g.noEffectOn, Pc.Negative)
        }
        Spacer(Modifier.height(10.dp))
        InfoParagraph(
            "Move summary",
            d.summary?.takeIf { it.isNotBlank() } ?: "No description for this one.",
            if (d.summary.isNullOrBlank()) Pc.Dim else Pc.Text,
        )
    }
}

/** "Strong against  Fire, Ground, Rock": the label in its colour, the types after it. */
@Composable
private fun Matchup(label: String, types: List<String>, color: Color) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        PixText(label, 12, color, Modifier.width(118.dp))
        PixText(types.joinToString(", "), 12, Pc.Text, Modifier.weight(1f), wrap = true)
    }
}

@Composable
fun PcMoveInfoDialog(d: MoveDetail, onDismiss: () -> Unit) {
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
    ) {
        androidx.compose.foundation.layout.Box(
            Modifier.fillMaxWidth().padding(16.dp),
            contentAlignment = androidx.compose.ui.Alignment.Center,
        ) { PcMoveInfoContent(d, onDismiss) }
    }
}
