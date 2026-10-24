package com.ironmonone.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

/**
 * The moves header's screen, as KaizoCore PC has it (PcScreens.MovesLearned, 2026-10-23 to the phone): each level-up move
 * and its level, the ones learned at the Pokemon's level in the theme's Negative colour and the ones to come in Positive,
 * then History (MoveHistoryDialog). A move's name only where the rules let it show (Blake, 2026-10-11: "you can only see
 * enemy wild or trainer pokemon past/current moves of that particular encounter, and same for your pokemon, you can only
 * see the levels"): open-book play or an unrandomized game shows them all (InfoRules.canShowMoves); otherwise your own
 * Pokemon's moves at or below its level, and the moves the foe in this battle was seen to use. The level alone else.
 */
@Composable
fun MovesLearnedDialog(
    name: String,
    level: Int?,
    /** (level, move id), in the game's order. */
    learnset: List<Pair<Int, Int>>,
    moveName: (Int) -> String,
    /** Every move name shows (InfoRules.canShowMoves). */
    open: Boolean,
    /** Your own Pokemon (else a foe's). */
    own: Boolean,
    /** The foe's moves seen in this battle, when it is a foe's. */
    seenIds: Set<Int>,
    onHistory: () -> Unit,
    onClose: () -> Unit,
) {
    Dialog(onDismissRequest = onClose) {
        Column(Modifier.width(300.dp).background(Pc.Page).border(1.dp, Pc.Border).padding(8.dp).verticalScroll(rememberScrollState())) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                DialogText(name.uppercase(), 16, Pc.Text, Modifier.weight(1f), heading = true)
                PcTap("X", 9, Pc.Dim, "Close") { onClose() }
            }
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth()) {
                DialogText(MovesLearnedCopy.HEADER, 12, Pc.Gold, Modifier.weight(1f))
                level?.let { DialogText("Lv.$it", 12, Pc.Gold, align = TextAlign.End) }
            }
            if (learnset.isEmpty()) DialogText(MovesLearnedCopy.NONE, 13, Pc.Text)
            learnset.forEach { (lv, id) ->
                val color = when { level == null -> Pc.Text; lv <= level -> Pc.Negative; else -> Pc.Positive }
                val known = open || id in seenIds || (own && level != null && lv <= level)
                Row(Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
                    DialogText("$lv", 13, color, Modifier.widthIn(min = 32.dp), TextAlign.End)
                    DialogText(if (known) moveName(id) else "?", 13, if (known) Pc.Text else Pc.Dim, Modifier.weight(1f).padding(start = 10.dp))
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth()) {
                PcTap(MovesLearnedCopy.HISTORY, 12, Pc.Gold, MovesLearnedCopy.HISTORY_SPOKEN) { onHistory() }
            }
        }
    }
}

/** The words of the moves header's screen. */
internal object MovesLearnedCopy {
    const val HEADER = "Level-up moves"
    const val NONE = "Does not learn any moves"
    const val HISTORY = "History"
    const val HISTORY_SPOKEN = "Move history"
}
