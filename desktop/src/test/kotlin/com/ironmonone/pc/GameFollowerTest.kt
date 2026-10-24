package com.ironmonone.pc

import com.ironmonone.pcui.PcFavorites
import com.ironmonone.pcui.PcPictures
import com.ironmonone.tracker.HnsPics
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Base Aggron on the favorites strip is the game's own follower (Blake, 2026-10-09: Sprite Collab's base Aggron reads
 * as the mega). The typed name reaches Heart & Soul's Aggron, and that follower decodes out of the build KaizoCore PC
 * makes from the official 2.0.6.
 */
class GameFollowerTest {
    @Test
    fun `the favorite Aggron is the species whose follower the strip draws`() {
        val aggron = PcFavorites.icons(PcFavorites.slots("Aggron"), natDex = true).single()
        assertEquals(306, aggron.species)
        assertTrue(aggron.species in PcPictures.GAME_FOLLOWER)
    }

    @Test
    fun `Aggron's follower decodes out of the build, six frames of 32 with its own colours`() {
        val official = TestFiles.official206 ?: return println("GameFollowerTest skipped: no .vendor/roms/hns-206.gba")
        val f = assertNotNull(HnsPics.follower(BridgeReader(PcHns.build(official.readBytes())), 306))
        assertEquals(32 to 32, f.width to f.height)
        assertEquals(6, f.frames.size)
        for ((i, px) in f.frames.withIndex()) {
            val seen = px.filter { it ushr 24 != 0 }
            assertTrue(seen.size > 200, "frame $i has ${seen.size} pixels")
            assertTrue(seen.toSet().size >= 6, "frame $i has ${seen.toSet().size} colours")
        }
    }
}
