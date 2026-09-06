package com.ironmonone.tracker

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * The decoder against the expansion's 104-byte Pokemon.
 *
 * Resolving the layout off the ROM is only half the fix; the decoder has to
 * USE it. These build one logical Pokemon twice - once in the vanilla shape,
 * once in the Nat. Dex shape - and require the same answer from both.
 */
class NatDexStructTest {

    private val natdex = PokemonDecoder.Layout(
        size = 104, enc = 0x24, status = 0x54, level = 0x58,
        curHp = 0x5A, maxHp = 0x5C,
    )

    /** Build a party mon: species/level/hp, laid out per [l]. */
    private fun mon(l: PokemonDecoder.Layout): ByteArray {
        val pid = 0x1234_5678L
        val otid = 0x0000_ABCDL
        val b = ByteArray(l.size)
        fun w32(off: Int, v: Long) { for (i in 0..3) b[off + i] = ((v shr (8 * i)) and 0xFF).toByte() }
        fun w16(off: Int, v: Int) { b[off] = (v and 0xFF).toByte(); b[off + 1] = ((v shr 8) and 0xFF).toByte() }
        w32(0x00, pid)
        w32(0x04, otid)
        // Substructure order is PID % 24; pick a PID whose order puts Growth
        // first so the species lands at the start of the encrypted block.
        val sub = ByteArray(48)
        fun s16(off: Int, v: Int) { sub[off] = (v and 0xFF).toByte(); sub[off + 1] = ((v shr 8) and 0xFF).toByte() }
        val slot = PokemonDecoder.SLOT_OF[(pid % 24).toInt()]
        val growth = slot[0] * 12
        s16(growth + 0, 25)        // species: Pikachu
        s16(growth + 2, 0)         // held item
        s16(slot[1] * 12, 84)      // first move: Thunder Shock
        // Encrypt with PID xor OTID, 32 bits at a time.
        val key = pid xor otid
        for (i in sub.indices step 4) {
            var w = 0L
            for (j in 0..3) w = w or ((sub[i + j].toLong() and 0xFF) shl (8 * j))
            w = w xor key
            for (j in 0..3) b[l.enc + i + j] = ((w shr (8 * j)) and 0xFF).toByte()
        }
        b[l.level] = 42
        w16(l.curHp, 97)
        w16(l.maxHp, 130)
        return b
    }

    @Test
    fun `a Nat Dex mon decodes to the same values as the vanilla one`() {
        val v = PokemonDecoder.decode(mon(PokemonDecoder.Layout.VANILLA))
        val n = PokemonDecoder.decode(mon(natdex), natdex)
        assertEquals(v.species, n.species)
        assertEquals(v.level, n.level)
        assertEquals(v.curHp, n.curHp)
        assertEquals(v.maxHp, n.maxHp)
        assertEquals(25, n.species)
        assertEquals(42, n.level)
        assertEquals(97, n.curHp)
        assertEquals(130, n.maxHp)
    }

    @Test
    fun `reading a Nat Dex mon with vanilla offsets gives nonsense`() {
        // This is the bug Blake hit, pinned: same bytes, wrong layout. If this
        // ever starts matching, the test above has stopped proving anything.
        val wrong = PokemonDecoder.decode(mon(natdex))
        assertNotEquals(25, wrong.species, "vanilla offsets must NOT decode a natdex mon")
        assertNotEquals(42, wrong.level)
    }

    @Test
    fun `only Nat Dex uses the expanded species numbering`() {
        // Which is what decides whether the bundled sprite pack may be indexed
        // with a species id. The pack is the expansion's own and follows the
        // expansion's ids, which are NOT national dex numbers.
        assertEquals(false, GameMap.EMERALD_U.expandedSpeciesIds)
        assertEquals(false, GameMap.FIRERED_U_V10.expandedSpeciesIds)
        assertEquals(false, GameMap.FIRERED_U_V11.expandedSpeciesIds)
    }
}
