package com.ironmonone.tracker

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** PokemonRevoData as shipped in revos.tsv: Bulbasaur's single table, Gloom's two options, Mew with nothing. */
class RandomEvosTest {
    private val t = GbaTracker(MemoryReader { _, n -> ByteArray(n) }, GameMap.EMERALD_U)

    @Test
    fun `single and multiple evolutions read as the reference lays them out`() {
        assertTrue(t.hasRandomEvos(1)); assertEquals(emptyList(), t.randomEvoOptions(1))
        val bulba = assertNotNull(t.randomEvos(1))
        assertEquals(33 to 8.03001, bulba.first()); assertEquals(38, bulba.size)
        assertEquals(listOf(45, 182), t.randomEvoOptions(44))
        assertEquals(24, assertNotNull(t.randomEvos(44, 182)).first().first)
        assertEquals(assertNotNull(t.randomEvos(44)), assertNotNull(t.randomEvos(44, 45)))
        assertNull(t.randomEvos(151))
    }
}
