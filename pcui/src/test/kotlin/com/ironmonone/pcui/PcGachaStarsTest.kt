package com.ironmonone.pcui

import com.ironmonone.app.TrackerOptions
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** GachaMon's stars beside the heals on your Pokemon's card (TrackerScreen.Buttons.GachaMonStars), or the PC heals. */
class PcGachaStarsTest {
    private val files = File(System.getProperty("java.io.tmpdir"), "pcui-gacha-${System.nanoTime()}")

    @Test
    fun `the run's ruleset is read from its settings name, as autoDetermineIronmonRuleset reads it`() {
        assertEquals("Kaizo", PcGachaStars.ruleset("RSE NatDex v1.2 Kaizo", natDex = true))
        assertEquals("SuperKaizo", PcGachaStars.ruleset("HnS Super Kaizo", natDex = true))
        assertEquals("Survival", PcGachaStars.ruleset("HnS Survival", natDex = true))
        assertEquals("Standard", PcGachaStars.ruleset("My build", natDex = true))
    }

    @Test
    fun `the heals box shows GachaMon's stars, or the PC heal counter when Track PC Heals is on`() {
        val was = TrackerOptions.trackPcHeals
        try {
            PcPanel.Renderer(files, PcPanel.Input(null, 0)).use { r ->
                // Star yellow (0xFCED86 / 0xEACA1D) in the stars' box: x 61 to 99, y 58 to 79.
                fun starPixels(input: PcPanel.Input): Int {
                    val img = ImageIO.read(r.render(input).inputStream())
                    return (58 until 80).sumOf { y -> (61 until 100).count { x -> img.getRGB(x, y) and 0xFFFFFF in setOf(0xFCED86, 0xEACA1D) } }
                }
                TrackerOptions.trackPcHeals = false
                assertTrue(starPixels(PcPanel.Input(Samples.party, 3, gachaStars = 3 to 3)) > 20, "three stars drawn")
                assertEquals(0, starPixels(PcPanel.Input(Samples.party, 3)), "no card, no stars")
                TrackerOptions.trackPcHeals = true
                assertEquals(0, starPixels(PcPanel.Input(Samples.party, 3, gachaStars = 3 to 3)), "the PC heals take the place")
            }
        } finally {
            TrackerOptions.trackPcHeals = was
            files.deleteRecursively()
        }
    }
}
