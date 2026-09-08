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
 * CatchRatesScreen.lua: the enemy's name, its HP to the nearest ten percent
 * with the screen's +/- adjusters, its status, then BALL / BAG / RATE rows,
 * balls in the bag first and best rate first. Balls not carried are dimmed;
 * a sure catch is green. Refreshed with every tracker poll.
 */
@Composable
fun CatchRatesDialog(d: GbaTracker.CatchRates?, hpAdjust: Int, onAdjust: (Int) -> Unit, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose) {
        Column(Modifier.width(300.dp).background(Pc.Page).border(1.dp, Pc.Border).padding(8.dp).verticalScroll(rememberScrollState())) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                PixText("CATCH RATES", 10, Pc.Text, Modifier.weight(1f))
                PixText("X", 9, Pc.Dim, Modifier.clickable { onClose() }.padding(horizontal = 6.dp, vertical = 2.dp))
            }
            Spacer(Modifier.height(4.dp))
            @Composable fun line(label: String, value: String, gold: Boolean, trailing: @Composable () -> Unit = {}) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    PixText(label, 8, Pc.Text, Modifier.width(110.dp)); PixText(value, 8, if (gold) Pc.Gold else Pc.Text); trailing()
                }
            }
            if (d == null) {
                line("Pokemon:", "---", false); line("Pokemon's HP:", "---", false); line("Status:", "---", false)
                Spacer(Modifier.height(6.dp)); PixText("Not in a battle.", 8, Pc.Dim)
                return@Column
            }
            line("Pokemon:", d.speciesName, true)
            val rounded = Math.floor(d.hpPercent / 10.0 + 0.5).toInt() * 10
            line("Pokemon's HP:", "$rounded%", true) {
                if (hpAdjust != 0) PixText((if (hpAdjust > 0) " + " else " -- ") + Math.abs(hpAdjust) + "%", 8, Pc.Text)
                Spacer(Modifier.weight(1f))
                PixText("-", 9, Pc.Negative, Modifier.clickable { onAdjust(maxOf(hpAdjust - 10, -90)) }.padding(horizontal = 8.dp))
                PixText("+", 9, Pc.Positive, Modifier.clickable { onAdjust(minOf(hpAdjust + 10, 90)) }.padding(horizontal = 8.dp))
            }
            line("Status:", d.status.ifEmpty { "---" }, d.status.isNotEmpty())
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth()) {
                PixText("BALL", 8, Pc.Gold, Modifier.weight(1f)); PixText("BAG", 8, Pc.Gold, Modifier.width(50.dp), TextAlign.End); PixText("RATE", 8, Pc.Gold, Modifier.width(60.dp), TextAlign.End)
            }
            d.rows.forEach { r ->
                val dim = r.quantity == 0
                val c = if (dim) Pc.Dim else Pc.Text
                Row(Modifier.fillMaxWidth().border(1.dp, Pc.Border).padding(horizontal = 3.dp, vertical = 2.dp)) {
                    PixText(r.name, 8, c, Modifier.weight(1f))
                    PixText("${r.quantity}", 8, c, Modifier.width(50.dp), TextAlign.End)
                    PixText("${r.rate}%", 8, if (!dim && r.rate >= 100) Pc.Positive else c, Modifier.width(60.dp), TextAlign.End)
                }
            }
        }
    }
}
