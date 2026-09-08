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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

/**
 * TimeMachineScreen.lua: restore points, a save state in memory made every
 * four minutes while the game is on a map and out of battle (the
 * reference's timeToWaitPerRP), at most ten kept, newest first. Restoring
 * one first saves where you were as "Return back to the future", as the
 * reference does, so a wrong jump can be undone. DS states run tens of
 * megabytes, so the set is also capped by size, which the reference has no
 * need for.
 */
class TimeMachine {
    class RestorePoint(val id: Int, val label: String, val timestamp: Long, val bytes: ByteArray)

    val points = mutableStateListOf<RestorePoint>()
    private var count = 0
    private var nextId = 1
    var lastCreated = 0L
        private set
    /** While the list is on screen nothing is trimmed, as the reference holds off. */
    var viewing = false

    companion object {
        const val MAX = 10
        const val WAIT_MS = 4 * 60 * 1000L
        const val MAX_BYTES = 96L * 1024 * 1024
        const val RETURN_LABEL = ">>  Return back to the future"
    }

    /** TimeMachineScreen.checkCreatingRestorePoint, called on a slow tick. */
    fun tick(now: Long, enabled: Boolean, inBattle: Boolean, mapKnown: Boolean, mapName: String?, snapshot: () -> ByteArray?) {
        if (!enabled || !mapKnown || inBattle) return
        if (now - lastCreated >= WAIT_MS) create(null, mapName, now, snapshot)
    }

    fun create(label: String?, mapName: String?, now: Long, snapshot: () -> ByteArray?): RestorePoint? {
        val bytes = snapshot()?.takeIf { it.isNotEmpty() } ?: return null
        lastCreated = now
        count++
        val rp = RestorePoint(nextId++, label ?: "# $count - ${mapName ?: "Unknown Area"}", now, bytes)
        points.add(0, rp)
        cleanup()
        return rp
    }

    /** TimeMachineScreen.backupCurrentPointInTime: keep one "return" point, always the newest. */
    fun backupCurrent(now: Long, snapshot: () -> ByteArray?) {
        if (points.isEmpty()) return
        points.sortByDescending { it.timestamp }
        if (points[0].label == RETURN_LABEL) return
        points.removeAll { it.label == RETURN_LABEL }
        create(RETURN_LABEL, null, now, snapshot)
    }

    fun cleanup(forceRemoveAll: Boolean = false) {
        if (forceRemoveAll) { points.clear(); return }
        if (viewing) return
        points.sortByDescending { it.timestamp }
        while (points.size > MAX) points.removeAt(points.size - 1)
        while (points.size > 1 && points.sumOf { it.bytes.size.toLong() } > MAX_BYTES) points.removeAt(points.size - 1)
    }

    fun clear() = cleanup(true)
}

@Composable
fun TimeMachineDialog(tm: TimeMachine, enabled: Boolean, onEnable: (Boolean) -> Unit, onCreate: () -> Unit, onRestore: (TimeMachine.RestorePoint) -> Unit, onClose: () -> Unit) {
    var confirmId by remember { mutableStateOf<Int?>(null) }
    val now = System.currentTimeMillis()
    Dialog(onDismissRequest = onClose) {
        Column(Modifier.width(300.dp).background(Pc.Page).border(1.dp, Pc.Border).padding(8.dp).verticalScroll(rememberScrollState())) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                PixText("TIME MACHINE", 10, Pc.Text, Modifier.weight(1f))
                PixText("X", 9, Pc.Dim, Modifier.clickable { onClose() }.padding(horizontal = 6.dp, vertical = 2.dp))
            }
            Spacer(Modifier.height(4.dp))
            GearToggle("Enable restore points", enabled) { onEnable(it) }
            Spacer(Modifier.height(4.dp))
            PixText("Select a restore point below to go back to that point in time.", 8, Pc.Text, wrap = true)
            Spacer(Modifier.height(6.dp))
            if (tm.points.isEmpty()) PixText("No restore points are available; one is created every 4 minutes.", 8, Pc.Dim, wrap = true)
            tm.points.sortedByDescending { it.timestamp }.forEach { rp ->
                val confirming = confirmId == rp.id
                val minutes = Math.ceil((now - rp.timestamp) / 60000.0).toInt()
                Column(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                    PixText(if (confirming) "Confirm restore?" else rp.label, 8, if (confirming) Pc.Negative else Pc.Text,
                        Modifier.fillMaxWidth().border(1.dp, Pc.Border).clickable { if (confirming) { onRestore(rp); confirmId = null } else confirmId = rp.id }.padding(4.dp))
                    PixText(if (minutes <= 1) "created 1 minute ago" else "created $minutes minutes ago", 7, Pc.Dim, Modifier.fillMaxWidth(), TextAlign.End)
                }
            }
            Spacer(Modifier.height(6.dp))
            com.ironmonone.app.gen3.Gen3Button("CREATE") { onCreate() }
        }
    }
}
