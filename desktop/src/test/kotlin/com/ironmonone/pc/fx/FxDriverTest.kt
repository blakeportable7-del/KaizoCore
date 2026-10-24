package com.ironmonone.pc.fx

import com.ironmonone.pc.BridgeReader
import com.ironmonone.pc.PcHns
import com.ironmonone.pc.TestFiles
import com.ironmonone.pcui.PcPanel
import com.ironmonone.pcui.Samples
import com.ironmonone.tracker.GameMap
import com.ironmonone.tracker.GbaTracker
import java.awt.image.BufferedImage
import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The effects on the real tracker window (developer mode, Blake 2026-10-10: "I want to run it tonight with animations in
 * dev mode"): the game's changes play over the card, and a randomized ROM keeps the game's own art.
 */
class FxDriverTest {
    private fun build(): ByteArray? = TestFiles.official206?.let { PcHns.build(it.readBytes()) }

    @Test
    fun `a randomized ROM keeps the game's art, and a ROM whose art differs does not`() {
        val rom = build() ?: return println("FxDriverTest skipped: no .vendor/roms/hns-206.gba")
        assertTrue(FxRomArt.forBuild(rom).ok, "the build itself")
        // A seed changes the game's data, never its art: one byte of the species table (far from the battle art) changed.
        val seed = rom.copyOf().also { it[0x00400000] = (it[0x00400000] + 1).toByte() }
        assertTrue(FxRomArt.forBuild(seed).ok, "a randomized ROM is the same build's art (its CRC is new, its artCrc is not)")
        // One byte of the anger mark's own tiles changed: not this build's art, so the code's art draws instead.
        val json = PcHns.resource("hns/fx-art-kaizo.json").toString(Charsets.UTF_8)
        val anger = Regex("\"anger\":\\s*\\{[^}]*\"gfx\":\\s*\"0x([0-9A-F]+)\"").find(json)!!.groupValues[1].toLong(16)
        val other = rom.copyOf().also { val at = (anger - 0x08000000L).toInt() + 4; it[at] = (it[at] + 1).toByte() }
        assertFalse(FxRomArt.forBuild(other).ok, "a ROM whose art is not the build's")
    }

    @Test
    fun `the game's changes play over the card, and a still card stops the clock`() {
        val rom = build() ?: return println("FxDriverTest skipped: no .vendor/roms/hns-206.gba")
        val reader = BridgeReader(rom)
        val tracker = GbaTracker(reader, GameMap.resolve(reader))
        val driver = FxDriver(com.ironmonone.pc.TrackerWindow.cardFont(), FxRomArt.forBuild(rom), tracker)
        try {
            assertTrue(driver.romArt, "the build's own art")
            val before = Samples.party
            val lead = before.party[0]
            // The lead is burned and loses an Attack stage, as a read in battle would show it.
            val after = before.copy(party = listOf(lead.copy(statusCondition = "BRN", statStages = lead.statStages + ("ATK" to 5))) + before.party.drop(1))
            val files = File(System.getProperty("java.io.tmpdir"), "kc-fxdriver").apply { mkdirs() }
            PcPanel.Renderer(files, PcPanel.Input(null, 0), 3f).use { r ->
                val card = r.renderImage(PcPanel.Input(after, 1))
                driver.update(before, true, null, true)
                driver.update(after, true, null, true)
                assertTrue(driver.active, "the burn and the stat drop are playing")
                fun frame(): IntArray {
                    val img = BufferedImage(card.width, card.height, BufferedImage.TYPE_INT_RGB)
                    val g = img.createGraphics(); driver.paint(g, card, 3.0); g.dispose()
                    return img.getRGB(0, 0, img.width, img.height, null, 0, img.width)
                }
                val a = frame(); Thread.sleep(250); val b = frame()
                val plain = card.getRGB(0, 0, card.width, card.height, null, 0, card.width)
                assertTrue(a.indices.count { a[it] != (plain[it] or 0xFF000000.toInt()) } > 200, "the effects drew over the card")
                assertTrue(a.indices.count { a[it] != b[it] } > 50, "they move from one frame to the next")
                // Covered by a screen: nothing of the effects shows.
                driver.update(after, true, null, false)
                val covered = frame()
                assertTrue(covered.indices.count { covered[it] != (plain[it] or 0xFF000000.toInt()) } < 50, "nothing draws over a covered card")
            }
        } finally {
            com.ironmonone.pcui.PcPictures.cardDrawsWalking = true
        }
    }
}
