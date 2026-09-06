package com.ironmonone.tracker.nds

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The Platinum level-up levels, extracted from the DS reference tracker's
 * PokemonData.POKEMON_MASTER_LIST (version group 2).
 *
 * This is a bundled table rather than a live read, which is what the reference
 * does and is correct for a reason worth restating: a randomizer changes WHICH
 * move sits at each slot but leaves the LEVELS alone, so the levels stay true
 * across every seed. Gen 4 learnsets live in the ROM's narc archives, not in
 * RAM, so there is nothing to read live anyway.
 *
 * The extraction had two real bugs before this test existed - an off-by-one
 * that shifted every species by one, and a parser that merged "43" and "7"
 * into "437" - so the spot checks below are against known Platinum learnsets
 * rather than against a row count that both bugs would still satisfy.
 */
class MoveLevelsTest {

    private val t = NdsTracker(NdsMemoryReader { _, _ -> ByteArray(0) })

    @Test
    fun `known Platinum learnsets read back exactly`() {
        assertEquals(
            listOf(3, 7, 9, 13, 13, 15, 19, 21, 25, 27, 31, 33, 37),
            t.moveLevelsOf(1), "Bulbasaur",
        )
        // Arceus' famously regular ten-move ladder - a good canary for the
        // far end of the dex, where an off-by-one would land on Victini.
        assertEquals(
            listOf(10, 20, 30, 40, 50, 60, 70, 80, 90, 100),
            t.moveLevelsOf(493), "Arceus",
        )
        // Turtwig is 387; the previous parse had it at 386.
        assertEquals(
            listOf(5, 9, 13, 17, 21, 25, 29, 33, 37, 41, 45),
            t.moveLevelsOf(387), "Turtwig",
        )
    }

    @Test
    fun `every level is a legal level and the list is ascending`() {
        var checked = 0
        for (id in 1..493) {
            val lv = t.moveLevelsOf(id)
            if (lv.isEmpty()) continue
            checked++
            assertTrue(lv.all { it in 1..100 }, "species $id has an illegal level: $lv")
            assertTrue(
                lv.zipWithNext().all { (a, b) -> a <= b },
                "species $id is not ascending: $lv",
            )
        }
        // A merged number like 437 would have tripped the range check above,
        // but only if the table actually loaded - so assert it is populated.
        assertTrue(checked > 400, "expected most of the dex to have levels, got $checked")
    }

    @Test
    fun `the header counts what has been learned and points at the next`() {
        // Bulbasaur at level 20: 3,7,9,13,13,15,19 are learned = 7 of 13, and
        // the next arrives at 21.
        val lv = t.moveLevelsOf(1)
        assertEquals(7, lv.count { it <= 20 })
        assertEquals(21, lv.first { it > 20 })
        // At level 100 nothing is left, so there is no "next" to show.
        assertEquals(13, lv.count { it <= 100 })
        assertEquals(null, lv.firstOrNull { it > 100 })
    }
}
