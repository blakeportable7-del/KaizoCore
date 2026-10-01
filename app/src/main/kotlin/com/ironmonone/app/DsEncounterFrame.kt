package com.ironmonone.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ironmonone.tracker.nds.NdsEncounterTables

/**
 * The DS tracker's encounter frame (MainScreen.lua:176-224 and 554-567,
 * HoverFrameFactory.createTrackedEncountersHoverFrame / createVanillaEncountersHoverFrame),
 * missing from the app until 2026-09-28 (parity audit). In a wild battle, in an area
 * that has vanilla data, the info box carries a map pin and "seen/total". Touching it
 * (the reference's hover) shows the species met there with their levels, then "?" up
 * to the area's total; touching the frame (the reference's click) switches to the
 * vanilla table, which lists slots by number with levels and percents and no species,
 * so a randomized seed is not spoiled.
 */
@Composable
fun DsEncounterLine(area: NdsEncounterTables.Area, uniqueSeen: Int, onTap: () -> Unit) {
    Row(Modifier.clickable { onTap() }.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        PinIcon()
        Spacer(Modifier.width(3.dp))
        PixText("$uniqueSeen/${area.totalPokemon}", 8, Pc.Text)
    }
}

/** LOCATION_ICON_SMALL_FILLED: a map pin, drawn rather than shipped as art. */
@Composable
private fun PinIcon() {
    val c = Pc.Text
    Canvas(Modifier.size(7.dp, 10.dp)) {
        val w = size.width; val h = size.height
        drawCircle(c, radius = w / 2f, center = Offset(w / 2f, w / 2f))
        drawPath(Path().apply { moveTo(w * 0.1f, w * 0.7f); lineTo(w / 2f, h); lineTo(w * 0.9f, w * 0.7f); close() }, c)
        drawCircle(Pc.Page, radius = w / 5f, center = Offset(w / 2f, w / 2f))
    }
}

/**
 * HoverFrameFactory's sortTrackedEncounters: by the level lists, element by element,
 * a shorter list first when one runs out, and by name when both do.
 */
internal fun sortTrackedEncounters(seen: Map<Int, List<Int>>, nameOf: (Int) -> String): List<Int> =
    seen.keys.sortedWith { a, b -> compareLevelLists(seen.getValue(a), seen.getValue(b)) { nameOf(a).compareTo(nameOf(b)) } }

private fun compareLevelLists(la: List<Int>, lb: List<Int>, byName: () -> Int): Int {
    for (i in 0..maxOf(la.size, lb.size)) {
        val x = la.getOrNull(i); val y = lb.getOrNull(i)
        if (x == null && y == null) return byName()
        if (x == null) return -1
        if (y == null) return 1
        if (x != y) return x.compareTo(y)
    }
    return byName()
}

/** fillTrackedEncounterRow's levels text: "Level 3 - 5" for range areas, else "Lv 3, 5". */
internal fun trackedLevelsText(levels: List<Int>?, usesRange: Boolean): String = when {
    levels.isNullOrEmpty() -> "?"
    usesRange -> "Level ${levels.first()} - ${levels.last()}"
    else -> "Lv " + levels.joinToString(", ")
}

@Composable
fun DsEncounterDialog(
    area: NdsEncounterTables.Area,
    seen: Map<Int, List<Int>>,
    nameOf: (Int) -> String,
    onDismiss: () -> Unit,
) {
    var tracked by remember { mutableStateOf(true) }
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().heightIn(max = 480.dp)
                .background(Pc.Ground).border(1.dp, Pc.Border)
                .verticalScroll(rememberScrollState())
                .clickable { tracked = !tracked }
                .padding(10.dp)
        ) {
            PixText(area.name, 11, Pc.Text, Modifier.fillMaxWidth(), align = TextAlign.Center)
            Spacer(Modifier.padding(top = 6.dp))
            if (tracked) {
                val order = sortTrackedEncounters(seen, nameOf)
                order.forEach { sp -> EncounterRow(nameOf(sp), trackedLevelsText(seen[sp], area.usesRange)) }
                for (i in order.size until area.totalPokemon) EncounterRow("?", "?")
            } else {
                area.slots.forEachIndexed { i, entries ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                        PixText("${i + 1}", 8, Pc.Text, Modifier.width(18.dp))
                        Column { entries.forEach { e -> PixText(e.label(), 8, Pc.Text) } }
                    }
                }
            }
            Spacer(Modifier.padding(top = 8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                PixText(if (tracked) "Tap for the vanilla table" else "Tap for what you met", 7, Pc.Dim)
                PcSmallButton("CLOSE") { onDismiss() }
            }
        }
    }
}

@Composable
private fun EncounterRow(name: String, levels: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        PixText(name, 8, Pc.Text, Modifier.width(96.dp))
        PixText(levels, 8, Pc.Text)
    }
}
