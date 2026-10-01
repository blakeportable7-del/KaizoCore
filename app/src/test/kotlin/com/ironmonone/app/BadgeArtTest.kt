package com.ironmonone.app

import com.ironmonone.tracker.nds.NdsGameMap
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Every badge set the panel can be asked for has all sixteen files, so the
 * numbers fallback in PcBadgeRow never quietly stands in for art. BW sat
 * on numbers for a week because nothing checked this.
 */
class BadgeArtTest {
    private val dir = listOf("src/main/assets/badges", "app/src/main/assets/badges").map(::File).first { it.isDirectory }

    @Test
    fun `every badge set has eight lit and eight unlit icons`() {
        val sets = listOf("FRLG", "RSE", "HGSS_K") + NdsGameMap.ALL.map { it.badgePrefix }.distinct()
        assertTrue("BW" in sets && "BW2" in sets, "the Gen 5 prefixes are read from the maps, not typed here")
        for (set in sets) {
            val missing = (1..8).flatMap { i -> listOf("${set}_badge$i.png", "${set}_badge${i}_OFF.png") }
                .filter { !File(dir, it).let { f -> f.isFile && f.length() > 0 } }
            assertTrue(missing.isEmpty(), "$set is missing $missing")
        }
    }

    /**
     * The Game Boy sets draw art too. Red, Blue and Yellow ("RBY") had none
     * and drew numbers; the Gen 1 reference draws them with FireRed's
     * (BADGE_PREFIX = "FRLG"). Crystal drew Johto's eight only; the Gen 2
     * reference draws all sixteen, Kanto in the GSC_K art.
     */
    @Test
    fun `Game Boy badge sets draw the references' art, sixteen for Gold, Silver and Crystal`() {
        assertEquals("badges/FRLG_badge3.png", PcAssets.badgePath("RBY", 3, true))
        assertEquals(listOf("GSC_J" to 0x21, "GSC_K" to 0x81), gscBadgeRows(0x8121))
        for (set in listOf("RBY") + gscBadgeRows(0).map { it.first }) {
            val missing = (1..8).flatMap { i -> listOf(PcAssets.badgePath(set, i, true), PcAssets.badgePath(set, i, false)) }
                .filter { !File(dir.parentFile, it).let { f -> f.isFile && f.length() > 0 } }
            assertTrue(missing.isEmpty(), "$set draws from missing files $missing")
        }
    }
}
