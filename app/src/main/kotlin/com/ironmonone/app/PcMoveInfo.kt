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
    /** The table's power label when the rules replace the ROM number (VAR, >FR, WT...); "0" is none. */
    val powerText: String? = null,
    /**
     * The ROM holds no battle data for this move: a name only. Every move past
     * 354 on the Nat. Dex 1.2.1 build is like that (power 0, PP 0, type ???),
     * the same in the PC tracker's Nat. Dex add-on. Said plainly instead of a
     * row of dashes.
     */
    val noRomData: Boolean = false,
    /** Your own Pokemon's Hidden Power: its personality value, for InfoScreen's type arrows. */
    val hiddenPowerPid: Long? = null,
    /** The row's PP and accuracy as the card draws them: "?" where "Reveal info if randomized" hides them (PcMove). */
    val ppText: String? = null,
    val accText: String? = null,
)

/**
 * The figures the move info card prints, from what the row prints. DataHelper.buildMoveInfoDisplay (DataHelper.lua:499-523)
 * hides a randomized type, PP, power and accuracy there too, for any move your viewed Pokemon does not know, while
 * "Reveal info if randomized" is off; the card used the ROM's numbers and gave them away (2026-09-30, IronMON rules check).
 */
internal object MoveInfoText {
    fun pp(d: MoveDetail): String = d.ppText ?: d.ppMax?.let { "${d.pp}/$it" } ?: "${d.pp}"

    fun power(d: MoveDetail): String = d.powerText?.let { if (it == "0") "-" else it } ?: d.power?.takeIf { it > 0 }?.toString() ?: "-"

    fun accuracy(d: MoveDetail): String = d.accText?.let { t ->
        when {
            t == "0" -> "-"
            t.isNotEmpty() && t.all { it.isDigit() } -> "$t%"
            else -> t
        }
    } ?: d.acc?.takeIf { it > 0 }?.let { "$it%" } ?: "-"
}

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
            // Hidden Power's type and category go on their own row below, so the
            // arrows do not squeeze the move's name onto two lines.
            if (d.hiddenPowerPid == null) {
                if (d.typeName != null) {
                    InfoTypeTag(d.typeName, d.typeId?.let { pcTypeColor(it) } ?: pcTypeColorByName(d.typeName))
                    Spacer(Modifier.width(6.dp))
                }
                if (category != null) InfoTag(category, when (d.category) {
                    "PHY" -> Color(0xFFF08030); "SPE" -> Color(0xFF6890F0); else -> Pc.Dim
                })
            }
        },
    ) {
        if (d.hiddenPowerPid != null) {
            // Read live, so the tag and category follow the arrows as they are tapped.
            val hp = HiddenPowerTypes.of(d.hiddenPowerPid)
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                HiddenPowerPicker(d.hiddenPowerPid)
                Spacer(Modifier.width(8.dp))
                when {
                    hp == null -> {}
                    hp <= 8 -> InfoTag("PHYSICAL", Color(0xFFF08030))
                    else -> InfoTag("SPECIAL", Color(0xFF6890F0))
                }
            }
            Spacer(Modifier.height(10.dp))
        }
        if (d.noRomData) {
            InfoParagraph(
                "No move data in this game",
                "This game has the move's name but no battle data for it: no power, accuracy, PP or type. " +
                    "It cannot be used in battle here, so there is nothing to show.",
                Pc.Dim,
            )
            return@InfoCard
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            InfoStat("PP", MoveInfoText.pp(d))
            InfoStat("POWER", MoveInfoText.power(d))
            InfoStat("ACCURACY", MoveInfoText.accuracy(d))
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
