package com.ironmonone.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
 *
 * [waitMs] is the reference's wait: four minutes in the Gen 3 tracker
 * (TimeMachineScreen.lua:10), five in both Game Boy trackers (:19, "one is
 * created every 5 minutes" at :5), [GB_WAIT_MS].
 *
 * The wait runs on [clock], which only goes forward, and the points are kept newest first by the order they were made
 * (their id). Both used the phone's wall clock: set back (players move it for the game's own clock), no point was made
 * until it caught up again, and the newest points sorted last and were the first trimmed (rc32 audit P2 #10). The wall
 * clock's time stays on each point for "created N minutes ago".
 */
class TimeMachine(
    val waitMs: Long = WAIT_MS,
    val maxBytes: Long = MAX_BYTES,
    /** Milliseconds that only go forward: elapsedRealtime on the phone, a test's own clock in the tests. */
    private val clock: () -> Long = { android.os.SystemClock.elapsedRealtime() },
) {
    class RestorePoint(val id: Int, val label: String, val timestamp: Long, val bytes: ByteArray)

    val points = mutableStateListOf<RestorePoint>()
    private var count = 0
    private var nextId = 1
    /** When the last point was made, on [clock]; null before the first. */
    private var lastCreated: Long? = null
    /** While the list is on screen nothing is trimmed, as the reference holds off. */
    var viewing = false

    companion object {
        const val MAX = 10
        const val WAIT_MS = 4 * 60 * 1000L
        const val GB_WAIT_MS = 5 * 60 * 1000L
        const val MAX_BYTES = 96L * 1024 * 1024
        /** A DS game's cap (rc32 audit P2 #52): its states run to megabytes, on the Java heap beside rewind's. */
        const val DS_MAX_BYTES = 48L * 1024 * 1024
        const val RETURN_LABEL = ">>  Return back to the future"

        /** Play's time machine for a console: five minutes between points on a Game Boy game, four on the others. */
        fun forPlatform(p: com.ironmonone.core.Platform, clock: () -> Long = { android.os.SystemClock.elapsedRealtime() }) = when (p) {
            com.ironmonone.core.Platform.GBC -> TimeMachine(GB_WAIT_MS, clock = clock)
            com.ironmonone.core.Platform.NDS -> TimeMachine(WAIT_MS, DS_MAX_BYTES, clock)
            else -> TimeMachine(WAIT_MS, clock = clock)
        }
    }

    /**
     * Whether a slow tick now makes a point (TimeMachineScreen.checkCreatingRestorePoint): on a map, out of battle, and
     * the wait over on [clock].
     */
    fun due(enabled: Boolean, inBattle: Boolean, mapKnown: Boolean): Boolean {
        if (!enabled || !mapKnown || inBattle) return false
        val last = lastCreated
        return last == null || clock() - last >= waitMs
    }

    /** TimeMachineScreen.checkCreatingRestorePoint, called on a slow tick. [now] is the wall clock's, for the point's label. */
    fun tick(now: Long, enabled: Boolean, inBattle: Boolean, mapKnown: Boolean, mapName: String?, snapshot: () -> ByteArray?) {
        if (due(enabled, inBattle, mapKnown)) create(null, mapName, now, snapshot)
    }

    /**
     * Play's tick: the state is taken between frames on the core's own thread from a background one, and the point
     * added back on the caller's (rc32 audit P2 #52). The main thread used to wait on the core for a whole DS state.
     * [now] is the wall clock's, for the point's label.
     */
    suspend fun tick(now: Long, enabled: Boolean, inBattle: Boolean, mapKnown: Boolean, mapName: String?, view: com.swordfish.libretrodroid.GLRetroView?) {
        if (view == null || !due(enabled, inBattle, mapKnown)) return
        val bytes = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { AutoSave.snapshot(view, quiet = true) } ?: return
        create(null, mapName, now) { bytes }
    }

    fun create(label: String?, mapName: String?, now: Long, snapshot: () -> ByteArray?): RestorePoint? {
        val bytes = snapshot()?.takeIf { it.isNotEmpty() } ?: return null
        lastCreated = clock()
        count++
        val rp = RestorePoint(nextId++, label ?: "# $count - ${mapName ?: "Unknown Area"}", now, bytes)
        points.add(0, rp)
        cleanup()
        return rp
    }

    /** TimeMachineScreen.backupCurrentPointInTime: keep one "return" point, always the newest. */
    fun backupCurrent(now: Long, snapshot: () -> ByteArray?) {
        if (points.isEmpty()) return
        points.sortByDescending { it.id }
        if (points[0].label == RETURN_LABEL) return
        points.removeAll { it.label == RETURN_LABEL }
        create(RETURN_LABEL, null, now, snapshot)
    }

    fun cleanup(forceRemoveAll: Boolean = false) {
        if (forceRemoveAll) { points.clear(); return }
        if (viewing) return
        points.sortByDescending { it.id }
        while (points.size > MAX) points.removeAt(points.size - 1)
        while (points.size > 1 && points.sumOf { it.bytes.size.toLong() } > maxBytes) points.removeAt(points.size - 1)
    }

    fun clear() = cleanup(true)
}

/** The restore points, in DialogText, which follows the phone's font size, each a 48dp target (rc32 audit P2 #19). */
@Composable
fun TimeMachineDialog(tm: TimeMachine, enabled: Boolean, onEnable: (Boolean) -> Unit, onCreate: () -> Unit, onRestore: (TimeMachine.RestorePoint) -> Unit, onClose: () -> Unit) {
    var confirmId by remember { mutableStateOf<Int?>(null) }
    val now = System.currentTimeMillis()
    Dialog(onDismissRequest = onClose) {
        Column(Modifier.width(300.dp).background(Pc.Page).border(1.dp, Pc.Border).padding(8.dp).verticalScroll(rememberScrollState())) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                DialogText("TIME MACHINE", 16, Pc.Text, Modifier.weight(1f), heading = true)
                PcTap("X", 9, Pc.Dim, "Close") { onClose() }
            }
            Spacer(Modifier.height(4.dp))
            GearToggle("Enable restore points", enabled) { onEnable(it) }
            Spacer(Modifier.height(4.dp))
            DialogText("Select a restore point below to go back to that point in time.", 13, Pc.Text)
            Spacer(Modifier.height(6.dp))
            if (tm.points.isEmpty()) DialogText("No restore points are available; one is created every ${tm.waitMs / 60_000} minutes.", 13, Pc.Dim)
            tm.points.sortedByDescending { it.id }.forEach { rp ->
                val confirming = confirmId == rp.id
                val minutes = Math.ceil((now - rp.timestamp) / 60000.0).toInt()
                Column(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                    DialogText(if (confirming) "Confirm restore?" else rp.label, 13, if (confirming) Pc.Negative else Pc.Text,
                        Modifier.fillMaxWidth().heightIn(min = PcMin.DIALOG_TOUCH_DP.dp).border(1.dp, Pc.Border).clickable { if (confirming) { onRestore(rp); confirmId = null } else confirmId = rp.id }.padding(horizontal = 6.dp, vertical = 12.dp))
                    DialogText(if (minutes <= 1) "created 1 minute ago" else "created $minutes minutes ago", 12, Pc.Dim, Modifier.fillMaxWidth(), TextAlign.End)
                }
            }
            Spacer(Modifier.height(6.dp))
            com.ironmonone.app.gen3.Gen3Button("CREATE") { onCreate() }
        }
    }
}
