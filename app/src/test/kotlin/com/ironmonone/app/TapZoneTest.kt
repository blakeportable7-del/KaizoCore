package com.ironmonone.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Which taps on the game open the FILE bar (Blake, 2026-10-05: "screen tap, tapped at the upper 33% of the play screen
 * opens the semi transparent bar"): the upper third of the picture does; the lower two thirds, the black bars, the DS
 * touch screen and a tap beside a button never do, nor a tap while a thumb is on the pad or just off it. The rules run
 * for real, on every console, every DS layout and a sweep of phone shapes in both orientations.
 */
class TapZoneTest {
    private fun opens(x: Float, y: Float, p: TapZone.Rect?, controls: List<TapZone.Rect> = emptyList(), held: Boolean = false, since: Long = 10_000L) =
        TapZone.opens(x, y, p, controls, deadPx = TapZone.DEAD_DP, padHeld = held, sincePadUpMs = since)

    @Test
    fun `the upper third of a Game Boy Advance picture opens the bar, the rest is the game's`() {
        // A 20:9 phone in landscape, 900 by 405 dp at 1 px per dp: the 3:2 picture is 607.5 wide, centred.
        val p = assertNotNull(TapZone.picture(TapZone.GBA, 900f, 405f))
        assertEquals(405f, p.h); assertEquals(607.5f, p.w); assertEquals((900f - 607.5f) / 2f, p.l)
        assertTrue(opens(450f, 20f, p), "top centre")
        assertTrue(opens(p.l + 2f, 134f, p), "the zone's left edge, just above a third")
        assertFalse(opens(450f, 136f, p), "just under the third")
        assertFalse(opens(450f, 300f, p), "the lower two thirds are the game's")
        assertFalse(opens(40f, 20f, p), "the black bar beside the picture")
        assertFalse(opens(880f, 20f, p), "the other bar")
        assertEquals(TapZone.SHARE, TapZone.zone(p).h / p.h, 0.0001f)
    }

    @Test
    fun `a Game Boy picture is squarer, and portrait puts it full width at the top`() {
        val land = assertNotNull(TapZone.picture(TapZone.GB, 900f, 405f))
        assertEquals(160f / 144f, land.w / land.h, 0.001f)
        // Portrait: the view is the game's box at the top of the screen, 405 wide.
        val port = assertNotNull(TapZone.picture(TapZone.GBA, 405f, 270f))
        assertEquals(405f, port.w); assertEquals(270f, port.h)
        assertTrue(opens(200f, 40f, port)); assertFalse(opens(200f, 200f, port))
    }

    @Test
    fun `no DS touch screen ever opens the bar, in any layout, at any size`() {
        val layouts = listOf("top-bottom", "bottom-top", "left-right", "right-left", "hybrid-top", "hybrid-bottom", "top", "bottom")
        val views = listOf(900f to 405f, 2340f to 1080f, 405f to 520f, 1280f to 800f, 1812f to 2176f, 904f to 2316f)
        var zones = 0
        for (name in layouts) for (gap in listOf(0, 10)) for (ratio in listOf(2, 3)) for ((w, h) in views) {
            val l = ScreenTap.DsLayout(name, gap, ratio)
            val p = TapZone.picture(TapZone.Console.Ds(l, topOnly = false), w, h)
            val f = ScreenTap.frame(l)
            val whole = TapZone.fit(f.width, f.height, TapZone.Rect(0f, 0f, w, h))
            val s = whole.w / f.width
            val touch = f.touch?.let { t -> TapZone.Rect(whole.l + t.left * s, whole.t + t.top * s, whole.l + t.right * s, whole.t + t.bottom * s) }
            if (name == "bottom") { assertNull(p, "only the touch screen is drawn: no zone"); continue }
            val pic = assertNotNull(p, "$name $w x $h")
            val z = TapZone.zone(pic)
            zones++
            assertTrue(z.l >= whole.l - 0.01f && z.r <= whole.r + 0.01f && z.t >= whole.t - 0.01f && z.b <= whole.b + 0.01f, "$name: inside the picture")
            if (touch != null) assertFalse(z.meets(touch), "$name gap=$gap ratio=$ratio $w x $h: the zone meets the touch screen")
            // A grid of taps over the touch screen: none opens.
            if (touch != null) for (i in 0..10) for (j in 0..10) {
                val x = touch.l + touch.w * i / 10f - (if (i == 10) 0.5f else 0f)
                val y = touch.t + touch.h * j / 10f - (if (j == 10) 0.5f else 0f)
                assertFalse(opens(x, y, pic), "$name: a tap on the touch screen at ($x, $y)")
            }
        }
        assertTrue(zones > 80, "the sweep ran ($zones)")
    }

    @Test
    fun `on a DS the zone is the top screen's upper part`() {
        // Side by side on a 20:9 phone: the left screen is the top screen.
        val side = assertNotNull(TapZone.picture(TapZone.Console.Ds(ScreenTap.DsLayout("left-right"), false), 900f, 405f))
        assertTrue(side.r <= 450f + 0.01f, "the left half")
        assertTrue(opens(side.l + side.w / 2, side.t + 10f, side))
        assertFalse(opens(675f, 30f, side), "the right screen is the touch screen")
        // Stacked in portrait: the top screen's upper part.
        val stacked = assertNotNull(TapZone.picture(TapZone.Console.Ds(ScreenTap.DsLayout("top-bottom"), false), 405f, 607.5f))
        assertEquals(303.75f, stacked.h, 0.01f, "the top screen only")
        assertTrue(opens(200f, 50f, stacked)); assertFalse(opens(200f, 200f, stacked))
        // Hybrid Top: the big screen.
        val hybrid = assertNotNull(TapZone.picture(TapZone.Console.Ds(ScreenTap.DsLayout("hybrid-top"), false), 2340f, 1080f))
        assertTrue(hybrid.w > 1400f, "the big screen")
    }

    @Test
    fun `the DS dock's viewport moves the picture, and one screen is the visible top`() {
        val vp = TapZone.Rect(0.2f, 0.1f, 1f, 0.9f)
        val p = assertNotNull(TapZone.picture(TapZone.Console.Ds(ScreenTap.DsLayout("hybrid-top"), false), 1000f, 500f, vp))
        assertTrue(p.l >= 200f - 0.01f && p.t >= 50f - 0.01f, "inside the viewport: $p")
        // "1 screen": the view is drawn twice the size about its top centre, and the touch arrives in its own pixels,
        // where what is seen is the middle half across and the top half down.
        val one = assertNotNull(TapZone.picture(TapZone.Console.Ds(ScreenTap.DsLayout("top-bottom"), true), 400f, 600f))
        assertTrue(one.l >= 100f - 0.01f && one.r <= 300f + 0.01f && one.b <= 300f + 0.01f, "the part on screen: $one")
    }

    @Test
    fun `a tap beside a button, on a held pad or just after one is a missed press, not the menu`() {
        val p = assertNotNull(TapZone.picture(TapZone.GBA, 900f, 405f))
        val l = TapZone.Rect(200f, 30f, 248f, 60f)
        assertFalse(opens(256f, 45f, p, listOf(l)), "8 dp beside L")
        assertFalse(opens(248f + TapZone.DEAD_DP - 0.5f, 45f, p, listOf(l)), "inside the 16 dp")
        assertTrue(opens(248f + TapZone.DEAD_DP + 1f, 45f, p, listOf(l)), "past it")
        assertFalse(opens(450f, 20f, p, held = true), "a thumb still on the pad")
        assertFalse(opens(450f, 20f, p, since = TapZone.AFTER_PAD_MS - 1), "a quarter second after a button")
        assertTrue(opens(450f, 20f, p, since = TapZone.AFTER_PAD_MS), "and after that, the menu")
        assertFalse(opens(450f, 20f, null), "no picture, no zone")
    }

    @Test
    fun `with every pad preset, the zone's free part opens and nothing near a control does`() {
        for ((w, h) in listOf(851f to 393f, 900f to 405f, 730f to 410f, 1280f to 800f)) for (ds in listOf(false, true)) for (skin in PadSkin.entries) {
            val pad = PadLayout.default(landscape = true, nds = ds)
            val controls = PadGeometry.rects(pad, w, h, landscape = true, skin = skin).values.flatten().map { TapZone.Rect(it.l, it.t, it.r, it.b) }
            val console = if (ds) TapZone.Console.Ds(ScreenTap.DsLayout(NdsScreens.autoLayout(w.toInt(), h.toInt())), false) else TapZone.GBA
            val p = TapZone.picture(console, w, h) ?: continue
            val z = TapZone.zone(p)
            var free = 0
            var y = z.t + 1f
            while (y < z.b) {
                var x = z.l + 1f
                while (x < z.r) {
                    val near = controls.any { it.grown(TapZone.DEAD_DP).contains(x, y) }
                    assertEquals(!near, opens(x, y, p, controls), "($x, $y) $w x $h ds=$ds $skin")
                    if (!near) free++
                    x += 12f
                }
                y += 12f
            }
            assertTrue(free > 0, "$w x $h ds=$ds $skin: some of the zone is free to tap")
        }
    }
}
