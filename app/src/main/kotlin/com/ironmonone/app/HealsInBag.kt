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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.ironmonone.tracker.GbaTracker

/**
 * HealsInBagScreen.lua: the bag by tab (HP, PP, Status, Battle, All), each
 * item with its count, green when it would help the lead right now (or when
 * there are 69 of it, as the reference has it). Refreshed with every poll.
 */
@Composable
fun HealsInBagDialog(rows: List<GbaTracker.BagRow>, onClose: () -> Unit) {
    var tab by remember { mutableStateOf("HP") }
    val tabs = listOf("HP", "PP", "Status", "Battle", "All")
    Dialog(onDismissRequest = onClose) {
        Column(Modifier.width(300.dp).background(Pc.Page).border(1.dp, Pc.Border).padding(8.dp).verticalScroll(rememberScrollState())) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                PixText("HEALS IN BAG", 10, Pc.Text, Modifier.weight(1f))
                PixText("X", 9, Pc.Dim, Modifier.clickable { onClose() }.padding(horizontal = 6.dp, vertical = 2.dp))
            }
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth()) {
                tabs.forEach { t ->
                    PixText(t.uppercase(), 8, if (t == tab) Pc.Gold else Pc.Dim,
                        Modifier.weight(1f).border(1.dp, if (t == tab) Pc.Gold else Pc.Border).clickable { tab = t }.padding(vertical = 4.dp), TextAlign.Center)
                }
            }
            Spacer(Modifier.height(6.dp))
            val shown = if (tab == "All") rows else rows.filter { it.category == tab }
            if (shown.isEmpty()) PixText("Nothing in the bag for this.", 8, Pc.Dim)
            shown.forEach { r ->
                val c = if (r.helpful || r.quantity == 69) Pc.Positive else Pc.Text
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    PixText(r.name, 8, c, Modifier.weight(1f))
                    PixText("x${r.quantity}", 8, c, Modifier.width(50.dp), TextAlign.End)
                }
            }
        }
    }
}
