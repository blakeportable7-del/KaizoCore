package com.ironmonone.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import java.io.File

/**
 * "Track PC Heals" (off by default in the reference): how many Pokemon Center
 * heals this attempt has left, or has used.
 *
 * Counting down it starts at 10, up at 0; either way 0 to 99. The +/- buttons
 * change it by hand. With the heart on (auto-tracking; off each session, as the
 * reference's toggle starts), a new Pokemon Center heal or rest at home - game
 * statistics 15 and 16 - moves it by one (Program.lua:1203). Kept per attempt,
 * so a new run starts fresh.
 */
object PcHeals {
    private val counts = mutableStateMapOf<Int, Int>()
    private val baseline = HashMap<Int, Int>()
    var autoTracking by mutableStateOf(false)
    private var file: File? = null

    fun count(attempt: Int): Int = counts[attempt] ?: if (TrackerOptions.pcHealsCountDownward) 10 else 0

    fun add(attempt: Int, delta: Int) {
        counts[attempt] = (count(attempt) + delta).coerceIn(0, 99)
        save()
    }

    /**
     * The game's heal statistics, polled. The first reading this session is the
     * baseline, so reopening the app never counts old heals; a zero after a real
     * value is an unreadable save, not a reset, and is ignored.
     */
    fun observe(attempt: Int, stat: Int) {
        val prev = baseline[attempt]
        if (prev != null && prev > 0 && stat == 0) return
        baseline[attempt] = stat
        if (prev == null || stat <= prev) return
        if (TrackerOptions.trackPcHeals && autoTracking) {
            add(attempt, if (TrackerOptions.pcHealsCountDownward) -1 else +1)
        }
    }

    /** Utils.getCenterHealColor. */
    fun color(n: Int): Color =
        if (TrackerOptions.pcHealsCountDownward) when { n < 1 -> Pc.Negative; n < 6 -> Pc.Gold; else -> Pc.Text }
        else when { n < 5 -> Pc.Text; n < 10 -> Pc.Gold; else -> Pc.Negative }

    fun load(f: File) {
        file = f
        counts.clear()
        runCatching {
            if (f.exists()) f.forEachLine { line ->
                val parts = line.split('=')
                if (parts.size == 2) {
                    val a = parts[0].trim().toIntOrNull(); val n = parts[1].trim().toIntOrNull()
                    if (a != null && n != null) counts[a] = n.coerceIn(0, 99)
                }
            }
        }
    }

    private fun save() {
        val f = file ?: return
        runCatching { f.parentFile?.mkdirs(); f.writeText(counts.entries.joinToString("") { "${it.key}=${it.value}\n" }) }
    }
}

/** Constants.PixelImages.HEART: 1 outline, 2 fill, 3 shine. */
private val HEART = listOf(
    "00110001100", "01221012210", "12332122221", "12322222221", "12222222221", "01222222210",
    "00122222100", "00012221000", "00001210000", "00000100000", "00000000000",
)

/**
 * The PC heal counter in the heals box (TrackerScreen.lua:1274): the heart
 * toggle on top - an outline off, red on - and the count right-aligned, with a
 * small green + and red - beside it.
 */
@Composable
internal fun PcHealCounter(attempt: Int) {
    val n = PcHeals.count(attempt)
    Column(horizontalAlignment = Alignment.End) {
        val on = PcHeals.autoTracking
        PcPixelImageColors(
            HEART,
            if (on) mapOf('1' to Color(0xFFF04037), '2' to Color(0xFFFF0000), '3' to Color(0xFFFFFFFF))
            else mapOf('1' to Pc.Text),
            Modifier.clickable { PcHeals.autoTracking = !PcHeals.autoTracking },
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column {
                PixText("+", PcRef.FONT - 3, Pc.Positive, Modifier.clickable { PcHeals.add(attempt, +1) })
                PixText("-", PcRef.FONT - 3, Pc.Negative, Modifier.clickable { PcHeals.add(attempt, -1) })
            }
            Spacer(Modifier.width(2.rp))
            PixText("$n", PcRef.FONT, PcHeals.color(n))
        }
    }
}
