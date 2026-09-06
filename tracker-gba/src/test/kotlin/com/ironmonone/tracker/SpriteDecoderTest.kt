package com.ironmonone.tracker

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The sprite path is the one place a silent failure looks like a design choice
 * ("that mon just has no picture"), so the decoder is pinned by tests rather
 * than by looking at the panel.
 *
 * Note the decoder refuses payloads under 0x10 bytes, so the vectors here are
 * sized like real data rather than minimal.
 */
class SpriteDecoderTest {

    /** Literal bytes only: a flag byte of 0 means eight literals follow. */
    @Test
    fun `decodes literal run`() {
        val expected = ByteArray(16) { (it + 1).toByte() }
        val src = byteArrayOf(0x10, 0x10, 0x00, 0x00) +   // header: 0x10 bytes out
            byteArrayOf(0x00) + expected.copyOfRange(0, 8) +
            byteArrayOf(0x00) + expected.copyOfRange(8, 16)
        assertContentEquals(expected, SpriteDecoder.lz77(src))
    }

    /**
     * A back-reference must copy byte by byte, so a displacement of 0 expands
     * one literal into a run. Copying in blocks would produce garbage here.
     */
    @Test
    fun `back reference expands overlapping run`() {
        val src = byteArrayOf(
            0x10, 0x10, 0x00, 0x00,              // 0x10 bytes out
            0x40,                                 // literal, then a copy
            0x41,                                 // 'A'
            0xC0.toByte(), 0x00,                  // len 12+3=15, disp 0
        )
        assertContentEquals(ByteArray(16) { 0x41 }, SpriteDecoder.lz77(src))
    }

    @Test
    fun `rejects non lz77 and truncated input`() {
        assertNull(SpriteDecoder.lz77(byteArrayOf(0x11, 0x40, 0x00, 0x00)))
        assertNull(SpriteDecoder.lz77(byteArrayOf()))
        // Claims 0x40 bytes but supplies none: must fail, never half-decode.
        assertNull(SpriteDecoder.lz77(byteArrayOf(0x10, 0x40, 0x00, 0x00, 0x00)))
    }

    /**
     * A whole 64x64 sprite through the real path: tile 0 filled with palette
     * index 1, everything else index 0. Proves the tile-to-pixel mapping and
     * that index 0 stays transparent.
     */
    @Test
    fun `renders sprite with transparent background`() {
        val tiles = ByteArray(0x800)
        for (i in 0 until 32) tiles[i] = 0x11          // tile 0: all index 1
        val palette = ByteArray(32).also {
            it[2] = 0x1F; it[3] = 0x00                 // color 1 = red (BGR555)
        }

        val space = FakeRom()
        space.put(0x08001000, store(tiles))
        space.put(0x08002000, store(palette))
        // Table entries {romPtr, size, tag} for species 1 (index 1 = +8 bytes).
        space.put(0x08000008, le32(0x08001000) + byteArrayOf(0, 8, 0, 0))
        space.put(0x08000108, le32(0x08002000) + byteArrayOf(0, 8, 0, 0))

        val map = GameMap(
            name = "test", partyCount = 0, party = 0, enemyParty = 0,
            battleTypeFlags = 0, battleMons = 0, battlersCount = 0, baseStats = 0,
            speciesNames = 0, moveNames = 0,
            frontPics = 0x08000000, palettes = 0x08000100, spriteCount = 10,
        )

        val px = SpriteDecoder.frontSprite(space, map, 1)!!
        assertEquals(64 * 64, px.size)
        assertEquals(0xFFF80000.toInt(), px[0])       // inside tile 0: opaque red
        assertEquals(0, px[63])                        // outside it: transparent
    }

    @Test
    fun `out of range species yields no sprite`() {
        val map = GameMap(
            name = "test", partyCount = 0, party = 0, enemyParty = 0,
            battleTypeFlags = 0, battleMons = 0, battlersCount = 0, baseStats = 0,
            speciesNames = 0, moveNames = 0,
            frontPics = 0x08000000, palettes = 0x08000100, spriteCount = 10,
        )
        assertNull(SpriteDecoder.frontSprite(FakeRom(), map, 99))
        assertNull(SpriteDecoder.frontSprite(FakeRom(), map, 0))
    }

    /** Sparse address space that honours the requested length, like the real
     *  memory bridge (an unmapped read returns empty, never zeros). */
    private class FakeRom : MemoryReader {
        private val regions = HashMap<Long, ByteArray>()
        fun put(addr: Long, bytes: ByteArray) { regions[addr] = bytes }
        fun put(addr: Int, bytes: ByteArray) = put(addr.toLong(), bytes)

        override fun read(address: Long, length: Int): ByteArray {
            for ((base, data) in regions) {
                if (address >= base && address < base + data.size) {
                    val from = (address - base).toInt()
                    val to = minOf(from + length, data.size)
                    return data.copyOfRange(from, to)
                }
            }
            return ByteArray(0)
        }
    }

    private fun le32(v: Int) = byteArrayOf(
        v.toByte(), (v shr 8).toByte(), (v shr 16).toByte(), (v shr 24).toByte())

    /** Wrap raw bytes as LZ77 with literal-only blocks. */
    private fun store(raw: ByteArray): ByteArray {
        val out = ArrayList<Byte>(raw.size + raw.size / 8 + 8)
        out += 0x10
        out += raw.size.toByte(); out += (raw.size shr 8).toByte()
        out += (raw.size shr 16).toByte()
        var i = 0
        while (i < raw.size) {
            out += 0
            repeat(8) { if (i < raw.size) out += raw[i++] }
        }
        return out.toByteArray()
    }
}
