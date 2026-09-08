package com.ironmonone.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Blake, 2026-09-08: "the buttons for the controls should never overlap each
 * other." Every shipped layout, on every skin, on the phone widths that
 * matter, at the portrait band's full height and at its 55% floor: no two
 * controls' rectangles touch. The d-pad counts as its cross, so a shoulder
 * pill in its empty corner is fine.
 */
class PadGeometryTest {
    private val portraitAreas = listOf(360f, 393f, 411f, 480f).map { it to PortraitBudget.PAD_NATURAL_DP }
    private val landscapeAreas = listOf(500f to 300f, 550f to 360f, 640f to 400f, 800f to 420f, 1000f to 600f)

    /** Every failing combination at once, so one run shows the whole picture. */
    private fun check(name: String, l: PadLayout, landscape: Boolean): List<String> {
        val out = ArrayList<String>()
        for (skin in PadSkin.entries) {
            val areas = if (landscape) landscapeAreas else portraitAreas
            for ((w, h) in areas) for (scale in if (landscape) listOf(1f) else listOf(1f, PortraitBudget.PAD_FLOOR)) {
                val hh = if (landscape) h else h * scale
                val bad = PadGeometry.overlaps(l, w, hh, landscape, skin, scale)
                if (bad.isNotEmpty()) out += "$name ($skin, ${w}x$hh, scale $scale): $bad"
            }
        }
        return out
    }

    @Test
    fun `the defaults never overlap`() {
        val bad = check("My Boy portrait", PadLayout.default(false, nds = false), false) +
            check("My Boy landscape", PadLayout.default(true, nds = false), true) +
            check("SuperNDS portrait", PadLayout.default(false, nds = true), false) +
            check("SuperNDS landscape", PadLayout.default(true, nds = true), true)
        assertTrue(bad.isEmpty(), bad.joinToString(System.lineSeparator()))
    }

    @Test
    fun `the original pads never overlap either`() {
        val bad = check("original portrait", PadLayout.legacy(false), false) +
            check("original landscape", PadLayout.legacy(true), true)
        assertTrue(bad.isEmpty(), bad.joinToString(System.lineSeparator()))
    }

    /** The DS diamond is one button wide whatever the screen: on a 1000dp column and a 360dp phone alike. */
    @Test
    fun `the DS diamond keeps its dp spacing on any screen and moves as one`() {
        for ((w, h, landscape) in listOf(Triple(1480f, 1509f, true), Triple(550f, 360f, true), Triple(360f, 192f, false), Triple(480f, 192f, false))) {
            val l = PadLayout.default(landscape, nds = true)
            assertTrue(l.abxyDiamond)
            val a = PadGeometry.centre(l, PadLayout.Element.A, w, h, landscape, PadSkin.OUTLINE)
            val y = PadGeometry.centre(l, PadLayout.Element.Y, w, h, landscape, PadSkin.OUTLINE)
            val x = PadGeometry.centre(l, PadLayout.Element.X, w, h, landscape, PadSkin.OUTLINE)
            val b = PadGeometry.centre(l, PadLayout.Element.B, w, h, landscape, PadSkin.OUTLINE)
            val d = (PadGeometry.BUTTON + 2 * PadGeometry.PAD) * l[PadLayout.Element.A].scale
            assertEquals(2 * d, a.first - y.first, 0.01f, "A to Y at ${w}x$h")
            assertEquals(2 * d, b.second - x.second, 0.01f, "X to B at ${w}x$h")
            assertEquals(a.second, y.second, 0.01f); assertEquals(x.first, b.first, 0.01f)
        }
        // Dragging Y moves A's place (the centre), so the four stay a diamond.
        val l = PadLayout.SUPERNDS_LANDSCAPE
        assertTrue(l.inDiamond(PadLayout.Element.Y)); assertTrue(!l.inDiamond(PadLayout.Element.L))
        assertTrue(!PadLayout.MYBOY_PORTRAIT.abxyDiamond)
    }

    @Test
    fun `the emulator layouts and the outline skin are what ships`() {
        assertEquals(PadLayout.MYBOY_PORTRAIT, PadLayout.default(false, nds = false))
        assertEquals(PadLayout.SUPERNDS_LANDSCAPE, PadLayout.default(true, nds = true))
        assertEquals(PadSkin.OUTLINE, PadSkin.parse(null))
        assertEquals(PadSkin.CLASSIC, PadSkin.parse("classic"), "a chosen skin still loads")
    }

    @Test
    fun `an overlap is reported, not hidden by the clamp`() {
        val l = PadLayout(mapOf(PadLayout.Element.A to PadLayout.Place(0.5f, 0.5f), PadLayout.Element.B to PadLayout.Place(0.52f, 0.5f)))
        assertEquals(listOf(PadLayout.Element.A to PadLayout.Element.B), PadGeometry.overlaps(l, 411f, 192f, false, PadSkin.OUTLINE))
        // Two controls pushed into the same corner by the clamp overlap too.
        val c = PadLayout(mapOf(PadLayout.Element.X to PadLayout.Place(0.02f, 0.02f), PadLayout.Element.L to PadLayout.Place(0.02f, 0.02f)))
        assertTrue(PadGeometry.overlaps(c, 411f, 192f, false, PadSkin.OUTLINE).isNotEmpty())
    }
}
