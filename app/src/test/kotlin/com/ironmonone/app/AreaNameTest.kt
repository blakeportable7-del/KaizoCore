package com.ironmonone.app

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The area's name in the tracker's top row scrolls when the attempt, SETUP and the menu leave it too little room (Blake,
 * 2026-10-03: "The area rustboro city up top, can that be scrolling animated? It's cut off"). When to move runs for
 * real; what is drawn is held to the source.
 */
class AreaNameTest {
    private val dir = File("src/main/kotlin/com/ironmonone/app")
    private fun read(name: String) = File(dir, name).readText().replace("\r\n", "\n")

    @Test
    fun `it moves unless the phone's animations are off`() {
        assertTrue(TrackerMotion.animationsOn(1f))
        assertTrue(TrackerMotion.animationsOn(0.5f), "slowed down is still on")
        assertFalse(TrackerMotion.animationsOn(0f), "Remove animations sets the scale to 0")
        assertTrue("Settings.Global.ANIMATOR_DURATION_SCALE, 1f" in read("TrackerLook.kt"), "it reads the system's scale")
    }

    @Test
    fun `the top row's name scrolls on when it does not fit, and is cut with an ellipsis with animations off`() {
        val area = read("TrackerLook.kt").substringAfter("internal fun AreaName(").substringBefore("\n}\n")
        assertTrue("modifier.basicMarquee(iterations = Int.MAX_VALUE)" in area, "round and round while it does not fit")
        assertTrue("if (still) PixText(name, PcRef.FONT, Pc.Dim, modifier, weight = FontWeight.Medium, ellipsis = true)" in area)
        assertTrue("overflow = if (ellipsis) androidx.compose.ui.text.style.TextOverflow.Ellipsis else androidx.compose.ui.text.style.TextOverflow.Clip" in read("PcTracker.kt"))
        // The one place the name heads the tracker: the GBA and Game Boy panel, docked or floating alike.
        val panel = read("TrackerPanel.kt")
        assertTrue("AreaName(routeName.orEmpty(), Modifier.weight(1f))" in panel)
        assertFalse("PixText(routeName.orEmpty()" in panel, "the clipped name is gone")
        assertFalse("routeName" in read("NdsTrackerPanel.kt"), "the DS panel has no area name in its rows")
    }
}
