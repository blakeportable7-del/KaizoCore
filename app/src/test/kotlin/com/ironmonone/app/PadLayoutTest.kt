package com.ironmonone.app

import com.ironmonone.core.Platform
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PadLayoutTest {

    @Test
    fun `defaults place every element inside the pad area`() {
        for (l in listOf(PadLayout.LANDSCAPE, PadLayout.PORTRAIT)) {
            for (e in l.places.keys) {
                val p = l[e]
                assertTrue(p.x in 0.04f..0.96f && p.y in 0.04f..0.96f, "$e at $p")
                assertEquals(1f, p.scale)
            }
        }
        assertEquals("hybrid-top", PadLayout.LANDSCAPE.dsLayout)
        assertEquals("top-bottom", PadLayout.PORTRAIT.dsLayout)
    }

    @Test
    fun `moves and scales are clamped`() {
        val p = PadLayout.Place(0.9f, 0.9f).moved(0.5f, -2f)
        assertEquals(PadLayout.Place(0.96f, 0.04f), p)
        assertEquals(1.8f, PadLayout.Place(0.5f, 0.5f, 1.7f).scaled(2f).scale)
        assertEquals(0.6f, PadLayout.Place(0.5f, 0.5f, 0.7f).scaled(0.1f).scale)
    }

    @Test
    fun `round-trips per key, and reset returns the default`() {
        val s = LayoutStore(Files.createTempDirectory("layouts").toFile())
        val key = PadLayout.key(true, Platform.NDS)
        assertEquals("landscape-nds", key)
        // A DS key carries X and Y (2.1); its default is the DS layout.
        val edited = PadLayout.LANDSCAPE_DS.with(PadLayout.Element.A, PadLayout.Place(0.5f, 0.5f, 1.3f))
            .copy(opacity = 0.8f, dsLayout = "left-right", dsGap = 32)
        s.save(key, edited)
        assertEquals(edited, s.load(key, landscape = true))
        assertEquals(PadLayout.PORTRAIT_DS, s.load(PadLayout.key(false, Platform.NDS), landscape = false), "other key untouched")
        s.reset(key)
        assertEquals(PadLayout.LANDSCAPE_DS, s.load(key, landscape = true))
        // A GBA key never picks up X or Y, even from a file that names them.
        val gba = PadLayout.key(true, Platform.GBA)
        s.save(gba, PadLayout.LANDSCAPE_DS)
        assertEquals(PadLayout.LANDSCAPE, s.load(gba, landscape = true))
    }

    @Test
    fun `a corrupt file falls back to the default, field by field`() {
        val dir = Files.createTempDirectory("layouts").toFile()
        File(dir, "portrait-gba.properties").writeText("A.x=7\nA.y=0.5\nopacity=9\ndsLayout=sideways\ndsGap=13\nB.x=0.2\nB.y=0.3\nB.scale=1.1")
        val l = LayoutStore(dir).load("portrait-gba", landscape = false)
        assertEquals(PadLayout.PORTRAIT[PadLayout.Element.A], l[PadLayout.Element.A], "out-of-range A is the default")
        assertEquals(PadLayout.Place(0.2f, 0.3f, 1.1f), l[PadLayout.Element.B], "valid B is kept")
        assertEquals(1f, l.opacity); assertEquals("top-bottom", l.dsLayout); assertEquals(0, l.dsGap)
    }

    /** 2.1: the My Boy presets place every control inside the area and keep the buttons apart. */
    @Test
    fun `the My Boy presets are complete, inside the area, and no two buttons sit on each other`() {
        for (landscape in listOf(true, false)) {
            val l = PadLayout.myBoy(landscape)
            for (e in l.places.keys) {
                val p = l[e]
                kotlin.test.assertTrue(p.x in 0.04f..0.96f && p.y in 0.04f..0.96f, "$e at ${p.x},${p.y}")
            }
            val buttons = l.places.keys.filter { it != PadLayout.Element.DPAD }
            for (i in buttons.indices) for (j in i + 1 until buttons.size) {
                val a = l[buttons[i]]; val b = l[buttons[j]]
                val d = kotlin.math.hypot((a.x - b.x).toDouble(), (a.y - b.y).toDouble())
                kotlin.test.assertTrue(d > 0.07, "${buttons[i]} and ${buttons[j]} overlap at $d (landscape=$landscape)")
            }
        }
        kotlin.test.assertEquals(PadSkin.OUTLINE, PadSkin.parse("outline"))
    }

    /** 2.1, DS: X and Y exist only where a layout places them; the SuperNDS preset has all nine, apart. */
    @Test
    fun `GBA layouts never draw X or Y, DS layouts and the SuperNDS preset do`() {
        kotlin.test.assertFalse(PadLayout.Element.X in PadLayout.default(true).places)
        kotlin.test.assertFalse(PadLayout.Element.Y in PadLayout.myBoy(false).places)
        for (l in listOf(PadLayout.default(true, nds = true), PadLayout.default(false, nds = true), PadLayout.SUPERNDS_LANDSCAPE)) {
            kotlin.test.assertTrue(PadLayout.Element.X in l.places && PadLayout.Element.Y in l.places)
            val buttons = l.places.keys.filter { it != PadLayout.Element.DPAD }
            for (i in buttons.indices) for (j in i + 1 until buttons.size) {
                val a = l[buttons[i]]; val b = l[buttons[j]]
                val d = kotlin.math.hypot((a.x - b.x).toDouble(), (a.y - b.y).toDouble())
                kotlin.test.assertTrue(d > 0.06, "${buttons[i]} and ${buttons[j]} at $d")
            }
        }
        kotlin.test.assertEquals("left-right", PadLayout.SUPERNDS_LANDSCAPE.dsLayout)
    }
}
