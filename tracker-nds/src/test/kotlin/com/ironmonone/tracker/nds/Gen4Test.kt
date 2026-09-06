package com.ironmonone.tracker.nds

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The Gen 4 structure is verified by round trip: the test ENCRYPTS a Pokémon the
 * way the game does (shuffle by PID, checksum, two LCG passes) and the decoder
 * must recover it. That pins the shuffle table, both PRNG seeds and the checksum
 * at once, and a mistake in any of them fails rather than quietly producing a
 * plausible-looking mon.
 */
class Gen4Test {

    @Test
    fun `round trips a party pokemon`() {
        val built = buildMon(
            pid = 0x12345678L, species = 392, level = 37,
            curHp = 88, maxHp = 120, moves = listOf(53, 89, 14, 0),
            abilityId = 66, heldItem = 17,
        )
        val mon = assertNotNull(Gen4.decodeParty(built), "should decode")
        assertEquals(392, mon.species)
        assertEquals(37, mon.level)
        assertEquals(88, mon.curHp)
        assertEquals(120, mon.maxHp)
        assertEquals(66, mon.abilityId)
        assertEquals(17, mon.heldItem)
        assertEquals(listOf(53, 89, 14, 0), mon.moves)
    }

    /** Every PID picks a different block order; all 24 must decode. */
    @Test
    fun `handles all 24 block orderings`() {
        for (shift in 0 until 24) {
            val pid = (shift.toLong() shl 13) or 0x1L      // bits 13..17 select order
            val built = buildMon(pid, species = 25, level = 50,
                curHp = 100, maxHp = 100, moves = listOf(85, 0, 0, 0),
                abilityId = 9, heldItem = 0)
            val mon = Gen4.decodeParty(built)
            assertNotNull(mon, "ordering $shift should decode")
            assertEquals(25, mon.species, "ordering $shift species")
            assertEquals(50, mon.level, "ordering $shift level")
        }
    }

    /** The cheap scan filter must agree with the full decode. */
    @Test
    fun `quick species matches full decode`() {
        val built = buildMon(0xABCDEF01L, species = 448, level = 20,
            curHp = 40, maxHp = 60, moves = listOf(1, 2, 3, 4),
            abilityId = 46, heldItem = 0)
        assertEquals(448, Gen4.quickSpecies(built, 0))
        assertEquals(448, Gen4.decodeParty(built)!!.species)
    }

    @Test
    fun `rejects corrupt and empty data`() {
        assertNull(Gen4.decodeParty(ByteArray(Gen4.PARTY_ENTRY_SIZE)))    // all zero
        assertNull(Gen4.decodeParty(ByteArray(10)))                       // too short

        // A real mon with one byte flipped must fail the checksum, not decode
        // into something plausible. This is the property the memory scan rests on.
        val built = buildMon(0x5A5A5A5AL, species = 100, level = 10,
            curHp = 20, maxHp = 25, moves = listOf(10, 0, 0, 0),
            abilityId = 1, heldItem = 0)
        built[20] = (built[20].toInt() xor 0xFF).toByte()
        assertNull(Gen4.decodeParty(built), "corrupted data must be rejected")
    }

    /** Random memory must not look like a Pokémon - the scan depends on it. */
    @Test
    fun `random noise almost never validates`() {
        val rng = java.util.Random(1234)
        var falsePositives = 0
        repeat(20_000) {
            val noise = ByteArray(Gen4.PARTY_ENTRY_SIZE).also(rng::nextBytes)
            if (Gen4.decodeParty(noise) != null) falsePositives++
        }
        assertTrue(falsePositives == 0, "got $falsePositives false positives in 20k")
    }

    /**
     * The shipped encoder must produce EXACTLY the bytes the independent encoder
     * in this test produces. Without this, encodeParty and decodeParty could
     * agree with each other while both being wrong about the game's format.
     */
    @Test
    fun `shipped encoder matches the independent one byte for byte`() {
        for (shift in 0 until 24) {
            val pid = (shift.toLong() shl 13) or 0x2A
            val mine = buildMon(
                pid, species = 392, level = 37, curHp = 88, maxHp = 120,
                moves = listOf(53, 89, 14, 0), abilityId = 66, heldItem = 17)
            val shipped = Gen4.encodeParty(
                pid = pid, species = 392, level = 37, curHp = 88, maxHp = 120,
                moves = listOf(53, 89, 14, 0), abilityId = 66, heldItem = 17)
            assertContentEquals(mine, shipped, "ordering $shift must match")
        }
    }

    @Test
    fun `encoded mon decodes back`() {
        val bytes = Gen4.encodeParty(
            pid = 0x0BADF00DL, species = 448, level = 42, curHp = 77, maxHp = 130,
            moves = listOf(9, 85, 0, 0), abilityId = 46, heldItem = 13)
        val mon = assertNotNull(Gen4.decodeParty(bytes))
        assertEquals(448, mon.species)
        assertEquals(42, mon.level)
        assertEquals(77, mon.curHp)
        assertEquals(130, mon.maxHp)
        assertEquals(46, mon.abilityId)
        assertEquals(13, mon.heldItem)
    }

    @Test
    fun `level out of range is rejected`() {
        val built = buildMon(0x11112222L, species = 1, level = 200,
            curHp = 10, maxHp = 10, moves = listOf(1, 0, 0, 0),
            abilityId = 1, heldItem = 0)
        assertNull(Gen4.decodeParty(built))
    }

    // ---------------------------------------------------------------- helpers

    /** Encrypts a Pokémon exactly as the game stores it. */
    private fun buildMon(
        pid: Long, species: Int, level: Int, curHp: Int, maxHp: Int,
        moves: List<Int>, abilityId: Int, heldItem: Int,
    ): ByteArray {
        val blocks = ByteArray(Gen4.BLOCK_AREA)
        val order = orderFor(pid)
        val a = order[0] * 32
        val b = order[1] * 32

        blocks.putU16(a + 0x00, species)
        blocks.putU16(a + 0x02, heldItem)
        blocks.putU16(a + 0x04, 0x1234)          // OT id
        blocks.putU16(a + 0x06, 0x5678)          // OT secret id
        blocks[a + 0x0D] = abilityId.toByte()
        moves.forEachIndexed { i, m -> blocks.putU16(b + i * 2, m) }
        repeat(4) { blocks[b + 0x08 + it] = 10 }
        blocks.putU32(b + 0x10, 0x3FFFFFFFL and 0x7FFFFFFFL)   // IVs, not an egg

        var checksum = 0
        for (i in 0 until Gen4.BLOCK_AREA step 2) {
            checksum = (checksum + blocks.u16(i)) and 0xFFFF
        }

        val party = ByteArray(100)
        party[0x04] = level.toByte()
        party.putU16(0x06, curHp)
        party.putU16(0x08, maxHp)
        party.putU16(0x0A, 50); party.putU16(0x0C, 51)
        party.putU16(0x0E, 52); party.putU16(0x10, 53); party.putU16(0x12, 54)

        val out = ByteArray(Gen4.PARTY_ENTRY_SIZE)
        out.putU32(0, pid)
        out.putU16(6, checksum)
        encryptInto(out, 8, blocks, checksum)
        encryptInto(out, 8 + Gen4.BLOCK_AREA, party, pid.toInt())
        return out
    }

    /** Same LCG the game uses; encryption and decryption are the same XOR. */
    private fun encryptInto(dest: ByteArray, at: Int, plain: ByteArray, seed0: Int) {
        var seed = seed0
        var i = 0
        while (i < plain.size) {
            seed = seed * 0x41C64E6D + 0x6073
            val key = (seed ushr 16) and 0xFFFF
            val v = plain.u16(i) xor key
            dest[at + i] = (v and 0xFF).toByte()
            dest[at + i + 1] = ((v shr 8) and 0xFF).toByte()
            i += 2
        }
    }

    /** Position of blocks A,B,C,D for a PID — mirrors the decoder's own table. */
    private fun orderFor(pid: Long): IntArray {
        val rows = ArrayList<IntArray>(24)
        for (x in 0..3) for (y in 0..3) {
            if (y == x) continue
            for (z in 0..3) {
                if (z == x || z == y) continue
                val w = 6 - x - y - z
                val pos = IntArray(4)
                intArrayOf(x, y, z, w).forEachIndexed { slot, block -> pos[block] = slot }
                rows += pos
            }
        }
        return rows[(((pid and 0x3E000L) shr 13) % 24).toInt()]
    }

    private fun ByteArray.putU16(o: Int, v: Int) {
        this[o] = (v and 0xFF).toByte(); this[o + 1] = ((v shr 8) and 0xFF).toByte()
    }

    private fun ByteArray.putU32(o: Int, v: Long) {
        this[o] = v.toByte(); this[o + 1] = (v ushr 8).toByte()
        this[o + 2] = (v ushr 16).toByte(); this[o + 3] = (v ushr 24).toByte()
    }
}
