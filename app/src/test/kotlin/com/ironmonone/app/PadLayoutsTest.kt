package com.ironmonone.app

import com.ironmonone.core.Platform
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * rc32 audit P3 #48: turning the phone during EDIT LAYOUT dropped the edits, Back asked about changes there were none
 * of, and Cancel put the first orientation's layout on the other one. P3 #41: the editor's My Boy and Original pad
 * chips put dead L and R buttons back on a Game Boy game.
 */
class PadLayoutsTest {
    private val dir: File = Files.createTempDirectory("layouts").toFile()
    private val portrait = PadLayout.key(false, Platform.GBA)
    private val landscape = PadLayout.key(true, Platform.GBA)
    private fun moved(l: PadLayout) = l.with(PadLayout.Element.A, l[PadLayout.Element.A].moved(-0.1f, 0.1f))

    @Test
    fun `a rotation mid-edit keeps each orientation's edits, and Cancel never crosses them`() {
        val store = LayoutStore(dir)
        val pads = PadLayouts(store)
        val p = pads.layout(portrait, false)
        pads.startEdit(portrait, false)
        pads.set(portrait, false, moved(p))
        // The phone turns: the landscape layout shows, from disk, and the portrait edit is kept, not dropped.
        val l = pads.layout(landscape, true)
        assertEquals(PadLayout.defaultFor(landscape), l)
        assertTrue(pads.changed(), "Back asks: the portrait layout was changed")
        pads.finishEdit(keep = false)
        assertEquals(p, pads.layout(portrait, false), "Cancel puts portrait back")
        assertEquals(l, pads.layout(landscape, true), "and never the portrait layout on landscape")
        assertFalse(File(dir, "$portrait.properties").exists(), "nothing reached the disk")
    }

    @Test
    fun `Done saves every orientation the edit reached, a default as no file`() {
        val store = LayoutStore(dir)
        val pads = PadLayouts(store)
        val p = pads.layout(portrait, false)
        pads.startEdit(portrait, false)
        pads.set(portrait, false, moved(p))
        val l = pads.layout(landscape, true)
        pads.set(landscape, true, moved(l))
        // Back in portrait, still editing: its edit is there.
        assertEquals(moved(p), pads.layout(portrait, false))
        pads.finishEdit(keep = true)
        assertEquals(moved(p), LayoutStore(dir).load(portrait, false))
        assertEquals(moved(l), LayoutStore(dir).load(landscape, true))
        // A layout put back to its default is kept as no file.
        pads.startEdit(portrait, false)
        pads.set(portrait, false, PadLayout.defaultFor(portrait))
        pads.finishEdit(keep = true)
        assertFalse(File(dir, "$portrait.properties").exists())
    }

    @Test
    fun `Play holds its layout through the holder`() {
        val play = File("src/main/kotlin/com/ironmonone/app/PlayScreen.kt").readText().replace("\r\n", "\n")
        assertTrue("var padLayout by padLayouts.at(layoutKey, landscape)" in play)
        assertFalse("var padLayout by remember(layoutKey)" in play)
        assertFalse("layoutBefore" in play, "no single before-snapshot that a rotation can carry to the other orientation")
        assertTrue("if (padLayouts.changed() || padSkin != ui.skinBefore) ui.confirmKeepLayout = true" in play)
    }

    @Test
    fun `a Game Boy's My Boy and Original pad presets have no L or R`() {
        for (o in listOf(true, false)) {
            for (l in listOf(PadLayout.myBoyFor(o, gb = true), PadLayout.legacyFor(o, gb = true))) {
                assertFalse(PadLayout.Element.L in l.places, "L, landscape=$o")
                assertFalse(PadLayout.Element.R in l.places, "R, landscape=$o")
            }
            assertEquals(PadLayout.myBoy(o), PadLayout.myBoyFor(o, gb = false))
            assertEquals(PadLayout.legacy(o), PadLayout.legacyFor(o, gb = false))
            assertEquals(PadLayout.default(o, nds = false, gb = true), PadLayout.myBoyFor(o, gb = true), "the same pad as a Game Boy's default")
        }
        val bar = File("src/main/kotlin/com/ironmonone/app/PadFree.kt").readText()
        assertTrue("onEdit(PadLayout.myBoyFor(landscape, gb))" in bar && "onEdit(PadLayout.legacyFor(landscape, gb))" in bar)
        assertTrue("gb = platform == com.ironmonone.core.Platform.GBC," in File("src/main/kotlin/com/ironmonone/app/PlayScreen.kt").readText())
    }
}
