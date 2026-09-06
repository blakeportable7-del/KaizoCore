package com.ironmonone.tracker.nds

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * The Gen 5 party entry: 220 bytes, 84-byte party area, nature as its own
 * byte at block B + 0x19, species up to 649. Everything else is the Gen 4
 * struct - same shuffle, same checksum, same two LCG passes.
 */
class Gen5DecodeTest {

    @Test
    fun `a Gen 5 entry round-trips through the encoder`() {
        val entry = Gen4.encodeParty(
            pid = 0x12345678L, species = 649, level = 42, curHp = 120, maxHp = 150,
            moves = listOf(558, 559, 1, 0), pp = listOf(5, 5, 35, 0),
            gen5 = true, nature = 17,
        )
        assertEquals(220, entry.size)
        val m = assertNotNull(Gen4.decodeParty(entry, gen5 = true))
        assertEquals(649, m.species)
        assertEquals(42, m.level)
        assertEquals(120 to 150, m.curHp to m.maxHp)
        assertEquals(listOf(558, 559, 1, 0), m.moves)
        assertEquals(17, m.nature, "Gen 5 nature is the byte, not PID mod 25")
    }

    @Test
    fun `a Gen 5 entry is not mistaken for a Gen 4 one`() {
        val entry = Gen4.encodeParty(pid = 0x1L, species = 25, level = 5, curHp = 20, maxHp = 20,
            moves = listOf(1, 0, 0, 0), gen5 = true)
        // 220 bytes is shorter than a Gen 4 entry: the Gen 4 decoder refuses it
        // rather than reading past the end.
        assertNull(Gen4.decodeParty(entry, gen5 = false))
    }

    @Test
    fun `species above 493 are only valid in Gen 5`() {
        val entry = Gen4.encodeParty(pid = 0x2L, species = 494, level = 5, curHp = 20, maxHp = 20,
            moves = listOf(1, 0, 0, 0))
        assertNull(Gen4.decodeParty(entry, gen5 = false), "Victini is not a Gen 4 species")
    }
}
