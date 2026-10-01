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
    fun `each Gen 4 version group reads its own levels`() {
        // Program.lua:327 keeps movelvls[gameInfo.VERSION_GROUP]: 1 Diamond and Pearl, 2 Platinum,
        // 3 HeartGold and SoulSilver. Every Gen 4 map used to load Platinum's: 51 species wrong on
        // Diamond and Pearl, 13 on HeartGold and SoulSilver (parity audit, 2026-09-28).
        val none = NdsMemoryReader { _, _ -> ByteArray(0) }
        fun lv(map: NdsGameMap, species: Int) = NdsTracker(none, null, map).moveLevelsOf(species)
        assertEquals(listOf(4, 7, 10, 13, 16, 19, 22, 25, 28, 31, 34, 37), lv(NdsGameMap.DP, 7), "Diamond/Pearl Squirtle")
        assertEquals(listOf(4, 7, 10, 13, 16, 19, 22, 25, 28, 31, 34, 37, 40), lv(NdsGameMap.PLATINUM, 7), "Platinum Squirtle")
        assertEquals(emptyList(), lv(NdsGameMap.DP, 10), "Caterpie has no level-up move after 1 before Platinum")
        assertEquals(listOf(15), lv(NdsGameMap.PLATINUM, 10), "Platinum Caterpie")
        assertEquals(listOf(6, 10, 13, 19, 22, 28, 31, 37, 40, 46, 49), lv(NdsGameMap.HGSS, 155), "HeartGold Cyndaquil")
        assertEquals(listOf(4, 10, 13, 19, 22, 28, 31, 37, 40, 46, 49), lv(NdsGameMap.PLATINUM, 155), "Platinum Cyndaquil")
        assertEquals(listOf(6, 10, 15, 19, 24, 28, 33, 37, 42, 46), lv(NdsGameMap.HGSS, 483), "HeartGold Dialga")
        // The header's "next" for a Lv. 5 Cyndaquil: 6 on HeartGold, 10 on Platinum.
        assertEquals(6, lv(NdsGameMap.HGSS, 155).first { it > 5 })
        for (map in listOf(NdsGameMap.DP, NdsGameMap.HGSS)) {
            val t = NdsTracker(none, null, map)
            assertTrue((1..493).count { t.moveLevelsOf(it).isNotEmpty() } > 400, "${map.name} table loaded")
        }
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
