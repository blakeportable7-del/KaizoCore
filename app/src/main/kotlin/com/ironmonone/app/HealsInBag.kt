package com.ironmonone.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
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
 * HealsInBagScreen.lua's tabs. All comes first and the screen opens on it (HealsInBagScreen.Tabs,
 * lua:8-34; initialize, :105; the tracker's Heals tap, TrackerScreen.lua:332); it opened on HP.
 * All lists every item in the bag with no helpful colouring, since it builds its rows without
 * one (:266-279); only 69 of anything turns green there (:303).
 */
internal object HealsTabs {
    val ORDER = listOf("All", "HP", "PP", "Status", "Battle")

    fun rows(rows: List<GbaTracker.BagRow>, tab: String): List<GbaTracker.BagRow> =
        if (tab == "All") rows else rows.filter { it.category == tab }

    fun green(row: GbaTracker.BagRow, tab: String): Boolean = (row.helpful && tab != "All") || row.quantity == 69
}

/**
 * HealsInBagScreen.lua: the bag by tab (All, HP, PP, Status, Battle), each
 * item with its count, green when it would help the lead right now (or when
 * there are 69 of it, as the reference has it). Refreshed with every poll.
 * The words are DialogText, which follows the phone's font size, the close is
 * a 48dp X that says Close and the tabs are 48dp tall and selectable (rc32
 * audit P2 #19, #42, #102): they were fixed 8 to 10dp text, a 17 by 13dp X and
 * 16dp tabs.
 */
@Composable
fun HealsInBagDialog(rows: List<GbaTracker.BagRow>, onClose: () -> Unit) {
    var tab by remember { mutableStateOf(HealsTabs.ORDER.first()) }
    val tabs = HealsTabs.ORDER
    Dialog(onDismissRequest = onClose) {
        Column(Modifier.width(300.dp).background(Pc.Page).border(1.dp, Pc.Border).padding(8.dp).verticalScroll(rememberScrollState())) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                DialogText("HEALS IN BAG", 16, Pc.Text, Modifier.weight(1f), heading = true)
                PcTap("X", 9, Pc.Dim, "Close") { onClose() }
            }
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth()) {
                tabs.forEach { t ->
                    val on = t == tab
                    Box(
                        Modifier.weight(1f).heightIn(min = PcMin.DIALOG_TOUCH_DP.dp)
                            .border(if (on) 2.dp else 1.dp, if (on) Pc.Gold else Pc.Border)
                            .selectable(selected = on, role = Role.Tab) { tab = t },
                        contentAlignment = Alignment.Center,
                    ) {
                        DialogText(t.uppercase(), 12, if (on) Pc.Gold else Pc.Dim, align = TextAlign.Center, underline = on)
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            val shown = HealsTabs.rows(rows, tab)
            if (shown.isEmpty()) DialogText("Nothing in the bag for this.", 13, Pc.Dim)
            shown.forEach { r ->
                val c = if (HealsTabs.green(r, tab)) Pc.Positive else Pc.Text
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    DialogText(r.name, 13, c, Modifier.weight(1f))
                    DialogText("x${r.quantity}", 13, c, Modifier.padding(start = 8.dp), TextAlign.End)
                }
            }
        }
    }
}
