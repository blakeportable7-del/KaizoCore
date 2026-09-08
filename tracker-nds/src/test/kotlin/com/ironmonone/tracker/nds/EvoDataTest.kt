package com.ironmonone.tracker.nds

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** EvoDataGen5.lua as shipped in gen5/evos.tsv: Bulbasaur into Ivysaur leads with Nidorino at 6.75%. */
class EvoDataTest {
    @Test
    fun `the gen 5 table loads for the Black 2 map`() {
        val t = NdsTracker(NdsMemoryReader { _, _ -> ByteArray(0) }, null, NdsGameMap.B2W2)
        val bulba = t.evoData(1)
        assertEquals(listOf(2), bulba.keys.toList())
        assertEquals(33 to 6.75, bulba[2]!!.first())
        assertTrue(t.evoData(151).isEmpty())
        assertEquals(setOf(161, 162, 163), NdsGameMap.B2W2.labTrainerIds); assertEquals(341, NdsGameMap.B2W2.finalTrainerId)
    }
}
