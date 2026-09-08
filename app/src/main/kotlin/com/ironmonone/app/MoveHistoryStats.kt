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

/**
 * MoveHistoryScreen (Ironmon-Tracker screens/MoveHistoryScreen.lua, read
 * 2026-09-08; the Gen 1 and 2 trackers carry the same screen): headed by the
 * Pokemon's name, a table of every move seen on that species with the lowest
 * and highest level it was seen at, sorted by the lowest level, highest
 * first; then "Moves learned", the levels at which the species learns a move,
 * each level in a box, red when the viewed Pokemon is already past it and
 * green when it is still to come. Opened by tapping the Moves header on a
 * card (TrackerScreen.lua:352).
 */
@Composable
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
                PixText(name.uppercase(), 10, Pc.Text, Modifier.weight(1f))
                PixText("X", 9, Pc.Dim, Modifier.clickable { onClose() }.padding(horizontal = 6.dp, vertical = 2.dp))
            }
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth()) {
                PixText("Moves", 8, Pc.Gold, Modifier.weight(1f))
                PixText("Min", 8, Pc.Gold, Modifier.width(34.dp), TextAlign.End)
                PixText("Max", 8, Pc.Gold, Modifier.width(34.dp), TextAlign.End)
            }
            if (rows.isEmpty()) {
                Spacer(Modifier.height(4.dp)); PixText("No tracked moves", 8, Pc.Text)
            }
            rows.forEach { m ->
                Row(Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
                    PixText(m.name, 8, Pc.Text, Modifier.weight(1f))
                    PixText("${m.minLv}", 8, Pc.Text, Modifier.width(34.dp), TextAlign.End)
                    PixText("${m.maxLv}", 8, Pc.Text, Modifier.width(34.dp), TextAlign.End)
                }
            }
            Spacer(Modifier.height(8.dp))
            PixText("Moves learned", 8, Pc.Gold)
            Spacer(Modifier.height(3.dp))
            if (learnLevels.isEmpty()) PixText("No moves learned", 8, Pc.Text)
            learnLevels.chunked(8).forEach { line ->
                Row(Modifier.padding(bottom = 3.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    line.forEach { lv ->
                        Box(Modifier.border(1.dp, Pc.Border).background(Pc.Ground).padding(horizontal = 4.dp, vertical = 2.dp)) {
                            PixText("$lv", 7, if (level <= 0) Pc.Text else if (lv <= level) Pc.Negative else Pc.Positive)
                        }
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
 * says so.
 */
@Composable
fun StatsDialog(rows: List<Pair<String, String>>, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose) {
        Column(Modifier.width(300.dp).background(Pc.Page).border(1.dp, Pc.Border).padding(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                PixText("STATS", 10, Pc.Text, Modifier.weight(1f))
                PixText("X", 9, Pc.Dim, Modifier.clickable { onClose() }.padding(horizontal = 6.dp, vertical = 2.dp))
            }
            Spacer(Modifier.height(6.dp))
            rows.forEach { (label, value) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    PixText(label, 8, Pc.Text, Modifier.weight(1f))
                    PixText(value, 8, Pc.Text, Modifier.width(80.dp), TextAlign.End)
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
