package com.ironmonone.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import com.ironmonone.tracker.GameMap
import com.ironmonone.tracker.GbaTracker
import com.ironmonone.tracker.MemoryReader
import java.io.File

/**
 * Debug builds only: the randomizer log viewer on its own, for looking at its design without a lost run. The log is
 * the external files folder's demo.log (or the "log" extra); "rom" names a GBA ROM under files/ whose tables the
 * Gen 3 tracker reads, as it would in a run.
 *
 *   adb shell am start -n com.ironmonone.app/.LogViewerPreviewActivity --es rom prep/library/emerald-u.gba
 */
class LogViewerPreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val log = intent.getStringExtra("log")?.let { File(it) } ?: File(getExternalFilesDir(null), "demo.log")
        val tracker = intent.getStringExtra("rom")?.let { path ->
            runCatching {
                val rom = File(filesDir, path).readBytes()
                val memory = MemoryReader { address, length ->
                    val off = (address - 0x08000000L).toInt()
                    if (address >= 0x08000000L && off >= 0 && off + length <= rom.size) rom.copyOfRange(off, off + length) else ByteArray(0)
                }
                GbaTracker(memory, GameMap.resolveOrNull(memory) ?: GameMap.EMERALD_U)
            }.getOrNull()
        }
        val badgeSet = intent.getStringExtra("badges") ?: "RSE"
        setContent {
            MaterialTheme(colorScheme = com.ironmonone.app.gen3.Gen3.Scheme) {
                LogViewer(log, onClose = { finish() }, tracker = tracker, badgeSet = badgeSet,
                    spriteFor = { PcAssets.gbaSprite(this, it) })
            }
        }
    }
}
