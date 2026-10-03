package com.ironmonone.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
 * MoveHistoryScreen (Ironmon-Tracker screens/MoveHistoryScreen.lua, read
 * 2026-09-08; the Gen 1 and 2 trackers carry the same screen): headed by the
 * Pokemon's name, a table of every move seen on that species with the lowest
 * and highest level it was seen at, sorted by the lowest level, highest
 * first; then "Moves learned", the levels at which the species learns a move,
 * each level in a box, red when the viewed Pokemon is already past it and
 * green when it is still to come. Opened by tapping the Moves header on a
 * card (TrackerScreen.lua:352). Its words are DialogText, which follows the
 * phone's font size, and it closes with a 48dp X that says Close (rc32 audit
 * P2 #19, #102): fixed 7 to 10dp text and a 17 by 13dp X that read "X".
 */
@Composable
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
fun MoveHistoryDialog(
    name: String,
    level: Int,
    seen: List<StatMarks.SeenMove>,
    learnLevels: List<Int>,
    onClose: () -> Unit,
) {
    val rows = seen.sortedByDescending { it.minLv }
    Dialog(onDismissRequest = onClose) {
        Column(Modifier.width(300.dp).background(Pc.Page).border(1.dp, Pc.Border).padding(8.dp).verticalScroll(rememberScrollState())) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                DialogText(name.uppercase(), 16, Pc.Text, Modifier.weight(1f), heading = true)
                PcTap("X", 9, Pc.Dim, "Close") { onClose() }
            }
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth()) {
                DialogText("Moves", 12, Pc.Gold, Modifier.weight(1f))
                DialogText("Min", 12, Pc.Gold, Modifier.widthIn(min = 40.dp), TextAlign.End)
                DialogText("Max", 12, Pc.Gold, Modifier.widthIn(min = 40.dp), TextAlign.End)
            }
            if (rows.isEmpty()) {
                Spacer(Modifier.height(4.dp)); DialogText("No tracked moves", 13, Pc.Text)
            }
            rows.forEach { m ->
                Row(Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
                    DialogText(m.name, 13, Pc.Text, Modifier.weight(1f))
                    DialogText("${m.minLv}", 13, Pc.Text, Modifier.widthIn(min = 40.dp), TextAlign.End)
                    DialogText("${m.maxLv}", 13, Pc.Text, Modifier.widthIn(min = 40.dp), TextAlign.End)
                }
            }
            Spacer(Modifier.height(8.dp))
            DialogText("Moves learned", 12, Pc.Gold)
            Spacer(Modifier.height(3.dp))
            if (learnLevels.isEmpty()) DialogText("No moves learned", 13, Pc.Text)
            // A flowing row, so a bigger font wraps the boxes onto the next line instead of past the dialog's edge.
            FlowRow(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                learnLevels.forEach { lv ->
                    Box(Modifier.border(1.dp, Pc.Border).background(Pc.Ground).padding(horizontal = 4.dp, vertical = 2.dp)) {
                        DialogText("$lv", 12, if (level <= 0) Pc.Text else if (lv <= level) Pc.Negative else Pc.Positive)
                    }
                }
            }
        }
    }
}

/**
 * StatsScreen (Ironmon-Tracker screens/StatsScreen.lua, read 2026-09-08):
 * ten rows, most read from the game's own statistics block
 * (Constants.GAME_STATS indices). The Gen 1 tracker's copy of this screen
 * shows the attempt count and zeros for the rest, and this does the same
 * for a Game Boy game. There is no play timer in this app yet, so that row
 * says so. In DialogText, with a 48dp Close, and it scrolls now that a big
 * font makes its ten rows taller (rc32 audit P2 #19, #102).
 */
@Composable
fun StatsDialog(rows: List<Pair<String, String>>, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose) {
        Column(Modifier.width(300.dp).background(Pc.Page).border(1.dp, Pc.Border).padding(8.dp).verticalScroll(rememberScrollState())) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                DialogText("STATS", 16, Pc.Text, Modifier.weight(1f), heading = true)
                PcTap("X", 9, Pc.Dim, "Close") { onClose() }
            }
            Spacer(Modifier.height(6.dp))
            rows.forEach { (label, value) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    DialogText(label, 13, Pc.Text, Modifier.weight(1f))
                    DialogText(value, 13, Pc.Text, Modifier.padding(start = 8.dp), TextAlign.End)
                }
            }
        }
    }
}

object StatsRows {
    /** Constants.GAME_STATS, pokefirered's game_stat.h. */
    const val SAVED_GAME = 0; const val STEPS = 5; const val WILD_BATTLES = 8; const val TRAINER_BATTLES = 9
    const val POKEMON_CAPTURES = 11; const val USED_POKECENTER = 15; const val RESTED_AT_HOME = 16; const val USED_STRUGGLE = 27; const val SHOPPED = 38

    /** The reference's ten rows, in its order, from a stat reader (null when the game has no statistics block). */
    fun build(attempts: Int, stat: ((Int) -> Int)?): List<Pair<String, String>> {
        fun g(i: Int) = stat?.invoke(i) ?: 0
        return listOf(
            "Play Time" to "not kept",
            "Total Attempts" to "$attempts",
            "PCs Used" to "${g(USED_POKECENTER) + g(RESTED_AT_HOME)}",
            "Trainer Battles" to "${g(TRAINER_BATTLES)}",
            "Wild Encounters" to "${g(WILD_BATTLES)}",
            "Pokemon Caught" to "${g(POKEMON_CAPTURES)}",
            "Shop Purchases" to "${g(SHOPPED)}",
            "Game Saves" to "${g(SAVED_GAME)}",
            "Total Steps" to "${g(STEPS)}",
            "Struggles Used" to "${g(USED_STRUGGLE)}",
        )
    }
}
