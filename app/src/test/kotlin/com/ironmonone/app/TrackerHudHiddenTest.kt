package com.ironmonone.app

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The Tracker HUD ships hidden in rc35.1 (Blake, 2026-10-04: keep it hidden until its second pass): no menu or Tracker
 * Setup line offers it, and a HUD choice saved by a test build loads as the floating window.
 */
class TrackerHudHiddenTest {
    private val dir = Files.createTempDirectory("hudoff").toFile()
    private val src = File("src/main/kotlin/com/ironmonone/app")

    @AfterTest fun cleanup() {
        dir.deleteRecursively()
        TrackerOptions.landscapeTracker = LandscapeTracker.DOCKED
        TrackerOptions.trackerHud = false
        TrackerOptions.hudShowMine = true; TrackerOptions.hudShowRest = true
    }

    @Test
    fun `the HUD is switched off`() {
        assertFalse(TrackerHud.ENABLED, "rc35.1 ships with the HUD hidden")
    }

    @Test
    fun `no menu or Tracker Setup line offers the HUD while it is off`() {
        var offers = 0
        var turnsOn = 0
        val turnOn = Regex("""trackerHud\s*=\s*true""")
        src.listFiles { f -> f.name.endsWith(".kt") }!!.forEach { f ->
            val lines = f.readLines()
            lines.forEachIndexed { i, raw ->
                val line = raw.substringBefore("//")
                val where = "${f.name}:${i + 1}"
                // The switch on this line, or on the line that opens this line's block.
                val above = if (i > 0) lines[i - 1].substringBefore("//").trimEnd() else ""
                val guarded = "TrackerHud.ENABLED" in line || ("TrackerHud.ENABLED" in above && above.endsWith("{"))
                if (Regex("MENU_HUD|TRACKER_HUD_LABEL").containsMatchIn(line) && "const val" !in line) {
                    offers++
                    assertTrue(guarded, "$where offers the HUD without the switch: ${raw.trim()}")
                }
                if (turnOn.containsMatchIn(line)) {
                    turnsOn++
                    assertTrue(guarded, "$where turns the HUD on without the switch: ${raw.trim()}")
                }
            }
        }
        // The tracker menu (from the dock and from the window) and Tracker Setup's landscape choices.
        assertEquals(3, offers, "the places that offer the HUD")
        assertEquals(3, turnsOn, "the places that turn it on")
        // The HUD's own layer is never drawn while it is off, whatever is saved.
        assertTrue("if (TrackerHud.ENABLED && TrackerOptions.trackerHud)" in File(src, "FloatingTracker.kt").readText())
    }

    @Test
    fun `a saved HUD choice loads as the floating window`() {
        val f = File(dir, "tracker-options.txt")
        f.writeText("landscapeTracker=FLOATING" + "\n" + "trackerHud=true" + "\n")
        TrackerOptions.load(f)
        assertEquals(LandscapeTracker.FLOATING, TrackerOptions.landscapeTracker, "still floating")
        assertFalse(TrackerOptions.trackerHud, "the window, not the HUD")
    }
}
