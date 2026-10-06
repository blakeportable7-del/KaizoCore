package com.ironmonone.app

import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The Tracker HUD is one of the FILE bar's views since 2026-10-06 (Blake: "one file bar, semi transparent, the docked
 * tracker, floating tracker, and hud tracker"). It was hidden in rc35.1; the one switch still decides: everything that
 * offers it or turns it on reads [TrackerHud.ENABLED], so turning it off again hides it everywhere.
 */
class TrackerHudHiddenTest {
    private val dir = java.nio.file.Files.createTempDirectory("hudon").toFile()
    private val src = File("src/main/kotlin/com/ironmonone/app")

    @AfterTest fun cleanup() {
        dir.deleteRecursively()
        TrackerOptions.landscapeTracker = LandscapeTracker.DOCKED
        TrackerOptions.trackerHud = false
        TrackerOptions.hudShowMine = true; TrackerOptions.hudShowRest = true
    }

    @Test
    fun `the HUD is switched on`() {
        assertTrue(TrackerHud.ENABLED)
    }

    @Test
    fun `every place that offers the HUD or turns it on reads the switch`() {
        var turnsOn = 0
        val turnOn = Regex("""trackerHud\s*=\s*(true|TrackerHud\.ENABLED)""")
        src.listFiles { f -> f.name.endsWith(".kt") }!!.forEach { f ->
            val lines = f.readLines()
            lines.forEachIndexed { i, raw ->
                val line = raw.substringBefore("//")
                val where = "${f.name}:${i + 1}"
                val above = if (i > 0) lines[i - 1].substringBefore("//").trimEnd() else ""
                val guarded = "TrackerHud.ENABLED" in line || ("TrackerHud.ENABLED" in above && above.endsWith("{"))
                if (turnOn.containsMatchIn(line)) {
                    turnsOn++
                    assertTrue(guarded, "$where turns the HUD on without the switch: ${raw.trim()}")
                }
                if ("TRACKER_HUD_LABEL" in line && "const val" !in line) assertTrue(guarded, "$where offers the HUD without the switch")
            }
        }
        // Tracker Setup's landscape choice, the FILE bar's VIEW (chooseBarView) and a saved file's line.
        assertEquals(3, turnsOn, "the places that turn it on")
        // VIEW lists it only while the switch is on (FileBarMap.views), and the layer is drawn only then.
        assertEquals(listOf(BarView.DOCKED, BarView.FLOATING, BarView.HIDDEN), FileBarMap.views(BarContext(hudEnabled = false)))
        assertTrue("if (TrackerHud.ENABLED && TrackerOptions.trackerHud)" in File(src, "FloatingTracker.kt").readText())
        assertTrue("\"trackerHud\" -> trackerHud = TrackerHud.ENABLED && v == \"true\"" in File(src, "TrackerOptions.kt").readText())
    }

    @Test
    fun `a saved HUD choice loads as the HUD`() {
        val f = File(dir, "tracker-options.txt")
        f.writeText("landscapeTracker=FLOATING" + Char(10) + "trackerHud=true" + Char(10))
        TrackerOptions.load(f)
        assertEquals(LandscapeTracker.FLOATING, TrackerOptions.landscapeTracker)
        assertTrue(TrackerOptions.trackerHud)
        assertEquals(BarView.HUD, currentBarView())
    }
}
