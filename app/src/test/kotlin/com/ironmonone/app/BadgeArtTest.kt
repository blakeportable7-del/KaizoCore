package com.ironmonone.app

import com.ironmonone.tracker.nds.NdsGameMap
import java.io.File
import kotlin.test.Test
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
}
