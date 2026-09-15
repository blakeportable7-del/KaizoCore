package com.ironmonone.app

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** The Walking Pals table as shipped, and the frame and facing rules SpriteData and Input use. */
class WalkingPalsTest {
    private val table = WalkingPals.parse(File("src/main/assets/walkingpals/walkingpals.tsv").readLines().asSequence())

    @Test
    fun `the shipped table covers Gen 1 to 3 with every sheet on disk`() {
        assertTrue(table.size >= 380, "only ${table.size} species")
        assertTrue((1..251).all { it in table }, "a Gen 1 or 2 species is missing")
        // Gengar (94): SpriteData.WalkingPals[94].
        val g = assertNotNull(table[94])
        val idle = assertNotNull(g[WalkingPals.Anim.IDLE])
        assertEquals(listOf(40, 4, 3, 3, 3, 3, 3, 4), idle.durations.toList())
        assertEquals(32 to 40, idle.w to idle.h)
        for ((id, anims) in table) for (a in anims.keys) {
            assertTrue(File("src/main/assets/walkingpals/${a.key}/$id.png").isFile, "no sheet ${a.key}/$id")
        }
    }

    @Test
    fun `frames follow their durations, and faint stops on its last`() {
        val s = WalkingPals.Sheet(32, 40, 0, 0, intArrayOf(40, 6, 6))
        assertEquals(0, s.frameAt(0, loop = true))
        assertEquals(0, s.frameAt(39, loop = true))
        assertEquals(1, s.frameAt(40, loop = true))
        assertEquals(2, s.frameAt(51, loop = true))
        assertEquals(0, s.frameAt(52, loop = true))
        assertEquals(2, s.frameAt(500, loop = false))
    }

    @Test
    fun `facing follows Input getSpriteFacingDirection`() {
        assertEquals(0, WalkingPals.facingRow(up = false, down = false, left = false, right = false))
        assertEquals(0, WalkingPals.facingRow(up = false, down = true, left = false, right = false))
        assertEquals(1, WalkingPals.facingRow(up = false, down = true, left = false, right = true))
        assertEquals(2, WalkingPals.facingRow(up = false, down = false, left = false, right = true))
        assertEquals(4, WalkingPals.facingRow(up = true, down = false, left = false, right = false))
        assertEquals(6, WalkingPals.facingRow(up = false, down = false, left = true, right = false))
        assertEquals(5, WalkingPals.facingRow(up = true, down = false, left = true, right = false))
    }
}
