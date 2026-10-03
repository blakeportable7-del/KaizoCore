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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.launch
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
 * a sure catch is green. Refreshed with every tracker poll. In DialogText,
 * which follows the phone's font size (rc32 audit P2 #19).
 */
@Composable
fun CatchRatesDialog(d: GbaTracker.CatchRates?, hpAdjust: Int, onAdjust: (Int) -> Unit, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose) {
        Column(Modifier.width(300.dp).background(Pc.Page).border(1.dp, Pc.Border).padding(8.dp).verticalScroll(rememberScrollState())) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                DialogText("CATCH RATES", 16, Pc.Text, Modifier.weight(1f), heading = true)
                PcTap("X", 9, Pc.Dim, "Close") { onClose() }
            }
            Spacer(Modifier.height(4.dp))
            @Composable fun line(label: String, value: String, gold: Boolean, trailing: @Composable () -> Unit = {}) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    DialogText(label, 13, Pc.Text, Modifier.width(110.dp)); DialogText(value, 13, if (gold) Pc.Gold else Pc.Text); trailing()
                }
            }
            if (d == null) {
                line("Pokemon:", "---", false); line("Pokemon's HP:", "---", false); line("Status:", "---", false)
                Spacer(Modifier.height(6.dp)); DialogText("Not in a battle.", 13, Pc.Dim)
                return@Column
            }
            line("Pokemon:", d.speciesName, true)
            // Read through rememberUpdatedState so a held key steps from the latest value, not the one it was pressed on.
            val adjustNow by androidx.compose.runtime.rememberUpdatedState(hpAdjust)
            val onAdjustNow by androidx.compose.runtime.rememberUpdatedState(onAdjust)
            fun adjust(d: Int) { val n = (adjustNow + d).coerceIn(-90, 90); if (n != adjustNow) onAdjustNow(n) }
            val rounded = Math.floor(d.hpPercent / 10.0 + 0.5).toInt() * 10
            line("Pokemon's HP:", "$rounded%", true) {
                if (hpAdjust != 0) DialogText((if (hpAdjust > 0) " + " else " -- ") + Math.abs(hpAdjust) + "%", 13, Pc.Text)
                Spacer(Modifier.weight(1f))
                PcHoldTap("-", Pc.Negative, "Lower HP") { adjust(-10) }
                PcHoldTap("+", Pc.Positive, "Raise HP") { adjust(10) }
            }
            line("Status:", d.status.ifEmpty { "---" }, d.status.isNotEmpty())
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth()) {
                DialogText("BALL", 13, Pc.Gold, Modifier.weight(1f)); DialogText("BAG", 13, Pc.Gold, Modifier.widthIn(min = 50.dp), TextAlign.End); DialogText("RATE", 13, Pc.Gold, Modifier.widthIn(min = 60.dp), TextAlign.End)
            }
            d.rows.forEach { r ->
                val dim = r.quantity == 0
                val c = if (dim) Pc.Dim else Pc.Text
                Row(Modifier.fillMaxWidth().border(1.dp, Pc.Border).padding(horizontal = 3.dp, vertical = 2.dp)) {
                    DialogText(r.name, 13, c, Modifier.weight(1f))
                    DialogText("${r.quantity}", 13, c, Modifier.widthIn(min = 50.dp), TextAlign.End)
                    DialogText("${r.rate}%", 13, if (!dim && r.rate >= 100) Pc.Positive else c, Modifier.widthIn(min = 60.dp), TextAlign.End)
                }
            }
        }
    }
}

/**
 * The HP - / + keys: the same glyph in a 48dp target, and holding repeats like
 * the shell's stepper. They were ~26x14dp and one tap per 10%, so -90 to +90
 * was eighteen small taps (2026-09-27, audit).
 */
@Composable
private fun PcHoldTap(glyph: String, color: androidx.compose.ui.graphics.Color, spoken: String, onStep: () -> Unit) {
    val fire by androidx.compose.runtime.rememberUpdatedState(onStep)
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    androidx.compose.foundation.layout.Box(
        Modifier.size(48.dp)
            .semantics { contentDescription = spoken; role = Role.Button; onClick { fire(); true } }
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    fire()
                    val repeat = scope.launch {
                        kotlinx.coroutines.delay(400)
                        while (true) { fire(); kotlinx.coroutines.delay(150) }
                    }
                    tryAwaitRelease()
                    repeat.cancel()
                })
            },
        contentAlignment = Alignment.Center,
    ) { DialogText(glyph, 14, color) }
}
