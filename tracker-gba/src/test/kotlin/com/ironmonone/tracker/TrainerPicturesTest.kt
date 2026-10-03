package com.ironmonone.tracker

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The log viewer's trainer portraits (TrainerPictures), on a ROM made here: two pictures, their palettes, one trainer. */
class TrainerPicturesTest {
    /** GBA BIOS LZ77 that keeps every byte as it is: a flag byte of zeros before each eight. */
    private fun literal(data: ByteArray): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        out.write(0x10); out.write(data.size and 0xFF); out.write((data.size shr 8) and 0xFF); out.write((data.size shr 16) and 0xFF)
        data.toList().chunked(8).forEach { group -> out.write(0); group.forEach { out.write(it.toInt()) } }
        return out.toByteArray()
    }

    private val pics = 0x08001000L
    private val pals = pics + 2 * 8
    private val trainers = 0x08002000L

    /**
     * Slot 0: every tile's pixels alternate color 1 and color 2, but the second tile (x 8 to 15 of the top row) is
     * clear. Slot 1: a 10 x 6 block of color 3 whose top row is row 20, at x 30 to 39. Trainer 1 is drawn with slot 1.
     */
    private fun rom(tag: (Int) -> Int = { it }): MemoryReader {
        val bytes = ByteArray(0x10000)
        fun put(at: Long, b: ByteArray) = b.copyInto(bytes, (at - 0x08000000L).toInt())
        fun word(at: Long, v: Long) = put(at, ByteArray(4) { ((v shr (8 * it)) and 0xFF).toByte() })
        fun half(at: Long, v: Int) = put(at, byteArrayOf((v and 0xFF).toByte(), (v shr 8).toByte()))
        val pic0 = ByteArray(0x800) { i -> (if (i / 32 == 1) 0 else 0x21).toByte() }
        val pic1 = ByteArray(0x800)
        for (y in 20 until 26) for (x in 30 until 40) {
            val t = (y / 8) * 8 + x / 8; val i = t * 32 + (y % 8) * 4 + (x % 8) / 2
            pic1[i] = (pic1[i].toInt() or (3 shl if (x % 2 == 0) 0 else 4)).toByte()
        }
        // Color 1 red (0x001F), 2 blue (0x7C00), 3 green (0x03E0).
        val palette = ByteArray(32).also { p -> listOf(0 to 0x7FFF, 1 to 0x001F, 2 to 0x7C00, 3 to 0x03E0).forEach { (c, v) -> p[c * 2] = (v and 0xFF).toByte(); p[c * 2 + 1] = (v shr 8).toByte() } }
        var data = 0x08004000L
        for (slot in 0..1) {
            val p = literal(if (slot == 0) pic0 else pic1); put(data, p)
            word(pics + slot * 8, data); half(pics + slot * 8 + 4, 0x800); half(pics + slot * 8 + 6, tag(slot))
            data += p.size + 4
            val c = literal(palette); put(data, c)
            word(pals + slot * 8, data); half(pals + slot * 8 + 4, tag(slot))
            data += c.size + 4
        }
        bytes[(trainers + 1 * 40 + 3 - 0x08000000L).toInt()] = 1
        return MemoryReader { address, length ->
            val off = (address - 0x08000000L).toInt()
            if (address >= 0x08000000L && off + length <= bytes.size) bytes.copyOfRange(off, off + length) else ByteArray(length)
        }
    }

    private val map = GameMap.FIRERED_U_V10.copy(trainerPics = pics, trainerPicPalettes = pals, trainerPicCount = 2, gTrainers = trainers, playerPic = 0)

    @Test
    fun `a picture is its tiles in its own colors, color 0 clear`() {
        val px = assertNotNull(TrainerPictures.picture(rom(), map, 0))
        assertEquals(64 * 64, px.size)
        assertEquals(0xFFF80000.toInt(), px[0], "color 1, the low four bits, first")
        assertEquals(0xFF0000F8.toInt(), px[1], "color 2")
        assertEquals(0, px[8], "the clear second tile")
        assertEquals(0xFFF80000.toInt(), px[16], "the third tile")
        assertEquals(0xFF0000F8.toInt(), px[63 * 64 + 63], "the last tile's last pixel")
    }

    @Test
    fun `a slot past the table, an entry tagged for another slot, or a build with no tables gives none`() {
        assertNull(TrainerPictures.picture(rom(), map, 2))
        assertNull(TrainerPictures.picture(rom(), map, -1))
        assertNull(TrainerPictures.picture(rom { it + 1 }, map, 0), "a table read in the wrong place")
        assertNull(TrainerPictures.picture(rom(), map.copy(trainerPics = 0), 0))
        assertNull(TrainerPictures.picture(rom(), map.copy(trainerPics = pics + 8), 0), "one entry off")
    }

    @Test
    fun `a trainer is drawn with the slot its entry names, and the player with the build's own`() {
        val t = GbaTracker(rom(), map)
        assertContentEquals(TrainerPictures.picture(rom(), map, 1), t.trainerPicture(1))
        assertContentEquals(TrainerPictures.picture(rom(), map, 0), t.playerPicture(girl = false))
        assertContentEquals(TrainerPictures.picture(rom(), map, 1), t.playerPicture(girl = true))
        assertNull(t.trainerPicture(0))
        assertNull(GbaTracker(rom(), map.copy(playerPic = -1)).playerPicture(false))
        assertNull(GbaTracker(rom(), map.copy(trainerPics = 0, trainerPicPalettes = 0)).trainerPicture(1), "no tables on the map")
    }

    @Test
    fun `the head is cut from the first colored row, centered on it`() {
        val px = assertNotNull(TrainerPictures.picture(rom(), map, 1))
        val head = TrainerPictures.head(px)
        assertEquals(24 * 22, head.size)
        // The block's 10 columns centered in 24: 7 clear, 10 green, 7 clear; its 6 rows, then clear.
        val green = 0xFF00F800.toInt()
        assertEquals(List(7) { 0 } + List(10) { green } + List(7) { 0 }, head.slice(0 until 24))
        assertEquals(green, head[5 * 24 + 7])
        assertEquals(0, head[6 * 24 + 7])
        assertTrue(TrainerPictures.head(IntArray(64 * 64)).all { it == 0 }, "nothing to cut")
    }
}
