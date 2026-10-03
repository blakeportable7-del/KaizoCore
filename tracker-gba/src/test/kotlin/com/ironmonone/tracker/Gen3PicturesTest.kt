package com.ironmonone.tracker

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Gen3Pictures on a cartridge built here to pret's layout, so each rule is pinned without a dump: the shiny palette is
 * the one painted, Unown's letter comes from the personality, Deoxys's form is its picture's last frame, and a table
 * whose tags are not the expected ones is refused. Gen3PicturesRomTest proves the same reads on the real games.
 */
class Gen3PicturesTest {
    private val red = 0x001F; private val green = 0x03E0; private val blue = 0x7C00
    private fun argb(bgr555: Int) = (0xFF shl 24) or ((bgr555 and 31) shl 19) or (((bgr555 shr 5) and 31) shl 11) or (((bgr555 shr 10) and 31) shl 3)

    /** A 256 KB cartridge: the header (or none), the three tables, and LZ77 data after them. */
    private class Cart(header: Boolean, val palettes: Long = 0x08002000) {
        val rom = ByteArray(0x40000)
        val front = 0x08001000L
        val shiny = if (header) 0x08003000L else palettes + Gen3Pictures.TABLE_ENTRIES * 8L
        private var next = 0x08008000L
        init {
            if (header) {
                rom.putU32(0x100, 4); rom.putU32(0x104, 2)
                "pokemon red version".toByteArray(Charsets.ISO_8859_1).copyInto(rom, 0x108)
                rom.putU32(0x128, front); rom.putU32(0x12C, front); rom.putU32(0x130, palettes); rom.putU32(0x134, shiny)
            }
        }
        init {
            // Species 1's palettes, plain and shiny: the entry tables() checks the shiny table's tags by.
            colors(1, 0x001F, 0x03E0)
        }
        private fun at(addr: Long) = (addr - 0x08000000L).toInt()
        /** [raw] stored as LZ77 of literals, returning where. */
        fun lz(raw: ByteArray): Long {
            val out = ArrayList<Byte>()
            out += 0x10; out += raw.size.toByte(); out += (raw.size shr 8).toByte(); out += (raw.size shr 16).toByte()
            var i = 0
            while (i < raw.size) { out += 0; repeat(8) { if (i < raw.size) out += raw[i++] } }
            val where = next
            out.toByteArray().copyInto(rom, at(where)); next += (out.size + 3) / 4 * 4
            return where
        }
        fun picture(slot: Int, frames: Array<ByteArray>, tag: Int = slot) {
            val data = lz(frames.reduce { a, b -> a + b })
            rom.putU32(at(front + slot * 8), data); rom[at(front + slot * 8) + 4] = 0; rom[at(front + slot * 8) + 5] = 8
            rom[at(front + slot * 8) + 6] = tag.toByte(); rom[at(front + slot * 8) + 7] = (tag shr 8).toByte()
        }
        fun palette(table: Long, slot: Int, color1: Int, tag: Int) {
            val raw = ByteArray(32).also { it[2] = color1.toByte(); it[3] = (color1 shr 8).toByte() }
            val e = at(table + slot * 8)
            rom.putU32(e, lz(raw)); rom[e + 4] = tag.toByte(); rom[e + 5] = (tag shr 8).toByte()
        }
        fun colors(slot: Int, plain: Int, shinyColor: Int) {
            palette(palettes, slot, plain, slot)
            palette(shiny, slot, shinyColor, slot + Gen3Pictures.SHINY_TAG)
        }
        val reader = MemoryReader { address, length ->
            val o = (address - 0x08000000L).toInt()
            if (address < 0x08000000L || o + length > rom.size) ByteArray(0) else rom.copyOfRange(o, o + length)
        }
        val map = GameMap(
            name = "test", partyCount = 0, party = 0, enemyParty = 0, battleTypeFlags = 0, battleMons = 0, battlersCount = 0,
            baseStats = 0, speciesNames = 0, moveNames = 0, frontPics = front, palettes = palettes,
        )
    }

    /** A personality whose Unown letter is [letter]: its two-bit groups in the low bits of the three low bytes. */
    private fun personalityOf(letter: Int): Long = (letter and 3).toLong() or ((letter shr 2 and 3).toLong() shl 8) or ((letter shr 4 and 3).toLong() shl 16)

    /** One 64x64 frame with tile [tile] all color 1 and every other pixel clear. */
    private fun frameLit(tile: Int) = ByteArray(Gen3Pictures.FRAME).also { for (i in 0 until 32) it[tile * 32 + i] = 0x11 }
    /** The pixel at the top left of tile [tile]. */
    private fun tilePixel(p: Gen3Pictures.Picture, tile: Int) = p.argb[(tile / 8) * 8 * 64 + (tile % 8) * 8]

    @Test
    fun `a shiny Pokemon is painted in its shiny palette, a plain one is left to the card`() {
        val c = Cart(header = true)
        c.picture(1, arrayOf(frameLit(0)))
        c.colors(1, plain = red, shinyColor = green)
        val t = assertNotNull(Gen3Pictures.tables(c.reader, c.map))
        assertEquals(c.shiny, t.shinyPalettes, "the header's shiny table")
        val shiny = assertNotNull(Gen3Pictures.picture(c.reader, t, 1, shiny = true, personality = 0, gameForm = true))
        assertEquals(argb(green), tilePixel(shiny, 0), "the shiny palette's color, not the normal one's")
        assertEquals(0, tilePixel(shiny, 1), "color 0 stays clear")
        assertNull(Gen3Pictures.picture(c.reader, t, 1, shiny = false, personality = 0, gameForm = true), "the species' plain picture is the card's own")
        // The plain colors are the ones SpriteDecoder paints: the shiny picture is the same picture, recolored.
        val plain = assertNotNull(SpriteDecoder.frontSprite(c.reader, c.map, 1))
        assertEquals(argb(red), plain[0])
        assertEquals(plain.map { it != 0 }, shiny.argb.map { it != 0 })
    }

    @Test
    fun `without a header the shiny table is found right after the normal one, by its tags`() {
        val c = Cart(header = false)
        c.picture(1, arrayOf(frameLit(3)))
        c.colors(1, plain = red, shinyColor = blue)
        val t = assertNotNull(Gen3Pictures.tables(c.reader, c.map))
        assertEquals(c.palettes + 440 * 8, t.shinyPalettes)
        assertEquals(c.front, t.ownFront, "no header: the map's table is the game's own")
        assertEquals(argb(blue), tilePixel(assertNotNull(Gen3Pictures.picture(c.reader, t, 1, true, 0, true)), 3))
        // Shiny tags missing there: no table is guessed at.
        c.palette(c.shiny, 1, blue, tag = 1)
        assertNull(Gen3Pictures.tables(c.reader, c.map))
    }

    @Test
    fun `a palette whose tag is not its slot's is not painted`() {
        val c = Cart(header = true)
        c.picture(1, arrayOf(frameLit(0)))
        c.palette(c.palettes, 1, red, 1)
        c.palette(c.shiny, 1, green, 1 + Gen3Pictures.SHINY_TAG)
        c.picture(2, arrayOf(frameLit(0)))
        c.palette(c.palettes, 2, red, 2)
        c.palette(c.shiny, 2, green, 3 + Gen3Pictures.SHINY_TAG)   // another slot's
        val t = assertNotNull(Gen3Pictures.tables(c.reader, c.map))
        assertNotNull(Gen3Pictures.picture(c.reader, t, 1, true, 0, true))
        assertNull(Gen3Pictures.picture(c.reader, t, 2, true, 0, true))
        // A header naming other palettes than the map's: neither is trusted.
        assertNull(Gen3Pictures.tables(c.reader, c.map.copy(palettes = 0x08004000)))
    }

    @Test
    fun `Unown's letter is read from two bits of each personality byte, as the game does`() {
        assertEquals(0, Gen3Pictures.unownLetter(0))
        // (3 << 6 | 3 << 4 | 3 << 2 | 3) = 255, and 255 % 28 = 3: D.
        assertEquals(3, Gen3Pictures.unownLetter(0x03030303))
        // Bits above the low two of each byte do not count.
        assertEquals(0, Gen3Pictures.unownLetter(0xFCFCFCFCL))
        assertEquals(27, Gen3Pictures.unownLetter(0x00010203L))   // 0b00011011 = 27, the question mark
        assertEquals(1, Gen3Pictures.unownLetter(0x00000001L))
        assertEquals(0, Gen3Pictures.unownLetter(0x00000300L + 0x10000L), "(1 << 4 | 3 << 2) = 28 wraps to A")

        val c = Cart(header = true)
        c.picture(Gen3Pictures.UNOWN, arrayOf(frameLit(0)))
        c.colors(Gen3Pictures.UNOWN, red, green)
        for (letter in 1..27) {
            val slot = Gen3Pictures.UNOWN_B + letter - 1
            c.picture(slot, arrayOf(frameLit(letter)))
            c.colors(slot, red, green)
        }
        val t = assertNotNull(Gen3Pictures.tables(c.reader, c.map))
        assertNull(Gen3Pictures.picture(c.reader, t, Gen3Pictures.UNOWN, false, 0, true), "A is Unown's own picture")
        for (letter in 1..27) {
            val p = assertNotNull(Gen3Pictures.picture(c.reader, t, Gen3Pictures.UNOWN, false, personalityOf(letter), true))
            assertEquals(argb(red), tilePixel(p, letter), "letter $letter draws its own slot")
        }
        assertEquals(argb(green), tilePixel(assertNotNull(Gen3Pictures.picture(c.reader, t, Gen3Pictures.UNOWN, true, 1, true)), 1), "a shiny letter")
    }

    @Test
    fun `Deoxys takes its picture's last frame from the game's own table, an opponent its first`() {
        val c = Cart(header = true)
        c.picture(Gen3Pictures.DEOXYS, arrayOf(frameLit(0), frameLit(9)))
        c.colors(Gen3Pictures.DEOXYS, red, green)
        val t = assertNotNull(Gen3Pictures.tables(c.reader, c.map))
        val form = assertNotNull(Gen3Pictures.picture(c.reader, t, Gen3Pictures.DEOXYS, false, 0, gameForm = true))
        assertEquals(argb(red), tilePixel(form, 9)); assertEquals(0, tilePixel(form, 0))
        assertNull(Gen3Pictures.picture(c.reader, t, Gen3Pictures.DEOXYS, false, 0, gameForm = false), "an opponent's is its Normal form, the plain picture")
        val shinyFoe = assertNotNull(Gen3Pictures.picture(c.reader, t, Gen3Pictures.DEOXYS, true, 0, gameForm = false))
        assertEquals(argb(green), tilePixel(shinyFoe, 0)); assertEquals(0, tilePixel(shinyFoe, 9))
        // One frame only (Ruby and Sapphire): the form is the plain picture.
        val one = Cart(header = true)
        one.picture(Gen3Pictures.DEOXYS, arrayOf(frameLit(0)))
        one.colors(Gen3Pictures.DEOXYS, red, green)
        assertNull(Gen3Pictures.picture(one.reader, assertNotNull(Gen3Pictures.tables(one.reader, one.map)), Gen3Pictures.DEOXYS, false, 0, true))
    }

    @Test
    fun `a ROM with nothing at the header reads no header`() {
        assertNull(Gen3Pictures.header(MemoryReader { _, n -> ByteArray(n) }))
        assertNull(Gen3Pictures.header(MemoryReader { _, _ -> ByteArray(0) }))
        assertNull(Gen3Pictures.tables(MemoryReader { _, n -> ByteArray(n) }, GameMap.FIRERED_U_V10), "zeros: no shiny tags")
    }
}
