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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.ironmonone.tracker.GbaTracker

/**
 * BattleDetailsScreen.lua: terrain, weather and the turn at the top, then
 * the effects in play. The reference shows one group at a time behind a
 * picker of team balls; the phone has the room, so every group with
 * anything in it is listed under its own underlined heading, in the
 * reference's order and words. Refreshed with every tracker poll.
 */
@Composable
fun BattleDetailsDialog(d: GbaTracker.BattleDetails?, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose) {
        Column(Modifier.width(300.dp).background(Pc.Page).border(1.dp, Pc.Border).padding(8.dp).verticalScroll(rememberScrollState())) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                PixText("BATTLE DETAILS", 10, Pc.Text, Modifier.weight(1f))
                PixText("X", 9, Pc.Dim, Modifier.clickable { onClose() }.padding(horizontal = 6.dp, vertical = 2.dp))
            }
            Spacer(Modifier.height(4.dp))
            if (d == null) {
                PixText("Terrain: ---", 8, Pc.Text); PixText("Weather: ---", 8, Pc.Text); PixText("Turn: ---", 8, Pc.Text)
                Spacer(Modifier.height(6.dp)); PixText("Not in a battle.", 8, Pc.Dim)
                return@Column
            }
            PixText("Terrain: ${d.terrain}", 8, Pc.Text)
            PixText("Weather: ${d.weather}", 8, Pc.Text)
            PixText("Turn: ${d.turn}", 8, Pc.Text)
            Spacer(Modifier.height(6.dp))
            @Composable fun group(title: String, lines: List<GbaTracker.BattleDetail>) {
                if (lines.isEmpty()) return
                PixText(title, 8, Pc.Gold); Spacer(Modifier.height(2.dp))
                lines.forEach { PixText(it.text, 8, Pc.Text, Modifier.padding(start = 6.dp), wrap = true) }
                Spacer(Modifier.height(6.dp))
            }
            group("Allied Pokemon", d.mons[0])
            group("Enemy Pokemon", d.mons[1])
            if (d.battlers > 2) { group("Allied Pokemon (2)", d.mons[2]); group("Enemy Pokemon (2)", d.mons[3]) }
            group("Allied Team", d.sides[0])
            group("Enemy Team", d.sides[1])
            group("Field Effects", d.field)
            if (d.field.isEmpty() && d.sides.all { it.isEmpty() } && d.mons.all { it.isEmpty() }) PixText("No effects in play.", 8, Pc.Dim)
        }
    }
}
