package com.ironmonone.pcui

import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The gear's "Use my own pictures": a picture named after a Pokemon stands in for it on the panel, for every run. */
class PcPicturesTest {
    private val home = kotlin.io.path.createTempDirectory("kc-pictures").toFile()

    private fun state(): com.ironmonone.tracker.TrackerState {
        // Heart & Soul's Delcatty (its own id 301, the pack's 316), as a real run reads it: its tracker names the badges
        // "GSC" (Hns.kt). The panel took that for another game until 2026-10-07 and missed the player's picture.
        val p = Samples.party
        val mon = p.party[0]
        return p.copy(badgeSet = "GSC", party = listOf(mon.copy(mon = mon.mon.copy(species = 301), speciesName = "Delcatty")))
    }

    private fun iconPixels(png: ByteArray): IntArray {
        val img = ImageIO.read(png.inputStream())
        return IntArray(32 * 32).also { img.getRGB(5, 5, 32, 32, it, 0, 32) }
    }

    @Test
    fun `a picture named after the lead stands in for it on the panel, and goes when the option is off`() {
        try {
            val pics = File(home, "pictures").apply { mkdirs() }
            val own = BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB)
            for (y in 0 until 32) for (x in 0 until 32) own.setRGB(x, y, 0xFF12AB34.toInt())
            ImageIO.write(own, "png", File(pics, "Delcatty.png"))
            File(home, "pictures.txt").writeText("set=gba\nown=false\n")
            PcPictures.load(File(home, "pictures.txt"), pics)
            val input = PcPanel.Input(state(), 3)
            PcPanel.Renderer(File(home, "files"), input).use { r ->
                val plain = iconPixels(r.render(input))
                PcPictures.own = true; PcPictures.save()
                val mine = iconPixels(r.render(input.copy(attempt = 4)))
                assertTrue(mine.count { it == 0xFF12AB34.toInt() } > 500, "the own picture is drawn")
                assertTrue(plain.none { it == 0xFF12AB34.toInt() }, "the GBA picture before")
                assertEquals("own=true", File(home, "pictures.txt").readLines().first { it.startsWith("own") })
            }
        } finally { PcPictures.own = false; home.deleteRecursively() }
    }
}
