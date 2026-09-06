package com.ironmonone.app

import com.ironmonone.core.RomKind
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * No DS dump exists on this machine, so the decoders are proven on files
 * built to the formats the randomizer reads: a Gen 4 sprite encrypted with
 * the game's LCG (both directions), a Gen 5 sprite stored as an LZ10 stream
 * of literals, and palettes laid out as the NCLR files are. The archive
 * paths are checked against the randomizer's own ini files, not retyped.
 */
class RomSpritesTest {

    private fun palFile(colors: IntArray): ByteArray {
        val b = ByteArray(40 + 32)
        for (i in 0 until 16) { val v = colors.getOrElse(i) { 0 }; b[40 + i * 2] = (v and 0xFF).toByte(); b[41 + i * 2] = ((v shr 8) and 0xFF).toByte() }
        return b
    }
    private val red555 = 0x001F; private val green555 = 0x03E0; private val blue555 = 0x7C00
    private val pal = palFile(intArrayOf(0, red555, green555, blue555))

    /** A Gen 4 file whose decrypted words are [plain]; the first word is forced to the key, as the format implies. */
    private fun gen4File(plain: IntArray, backwards: Boolean): ByteArray {
        val enc = IntArray(3200)
        if (!backwards) {
            var key = 0x1234; enc[0] = key
            for (i in 0 until 3200) { enc[i] = if (i == 0) key else plain[i] xor (key and 0xFFFF); key = key * 0x41C64E6D + 0x6073 }
        } else {
            var key = 0x4321; enc[3199] = key
            for (i in 3199 downTo 0) { enc[i] = if (i == 3199) key else plain[i] xor (key and 0xFFFF); key = key * 0x41C64E6D + 0x6073 }
        }
        val b = ByteArray(48 + 6400)
        for (i in 0 until 3200) { b[48 + i * 2] = (enc[i] and 0xFF).toByte(); b[49 + i * 2] = ((enc[i] shr 8) and 0xFF).toByte() }
        return b
    }

    @Test
    fun `a Gen 4 sprite decrypts forwards and backwards and keeps only the left 80 pixels`() {
        val plain = IntArray(3200)
        // Row 5: pixels 0..3 = colours 1,2,3,0 (one word, low nibble first); pixel 79 = colour 3; pixel 80 (right half) = colour 1, must not appear.
        plain[5 * 40 + 0] = 0x0321
        plain[5 * 40 + 19] = 0x3000
        plain[5 * 40 + 20] = 0x0001
        for (back in listOf(false, true)) {
            val d = RomSprites.decodeGen4(gen4File(plain, back), pal, back)
            assertEquals(80, d.width); assertEquals(80, d.height)
            val px = { x: Int, y: Int -> d.argb[y * 80 + x] }
            assertEquals(RomSprites.argb555(red555), px(0, 5), "backwards=$back")
            assertEquals(RomSprites.argb555(green555), px(1, 5))
            assertEquals(RomSprites.argb555(blue555), px(2, 5))
            assertEquals(0, px(3, 5), "palette 0 is transparent")
            assertEquals(RomSprites.argb555(blue555), px(79, 5))
            assertTrue(d.argb.count { it == RomSprites.argb555(red555) } == 1, "the right half of each row is chopped, so pixel 80 never lands")
        }
    }

    /** LZ10 with every byte a literal: 0x10, 24-bit size, then blocks of a zero flag byte and eight bytes. */
    private fun lz10Literal(data: ByteArray): ByteArray {
        val out = ArrayList<Byte>()
        out += 0x10.toByte(); out += (data.size and 0xFF).toByte(); out += ((data.size shr 8) and 0xFF).toByte(); out += ((data.size shr 16) and 0xFF).toByte()
        var i = 0
        while (i < data.size) { out += 0.toByte(); for (k in 0 until 8) { if (i < data.size) out += data[i]; i++ } }
        return out.toByteArray()
    }

    @Test
    fun `a Gen 5 sprite is decompressed, tiled and unscrambled onto 96x96 the way the reference draws it`() {
        // 64x144 at 4bpp = 4608 bytes of tiles after a 48-byte header. Tile t covers strip x=(t%8)*8, y=(t/8)*8.
        val tiles = ByteArray(48 + 64 * 144 / 2)
        fun setPixel(x: Int, y: Int, v: Int) {
            val t = (y / 8) * 8 + x / 8; val i = 48 + t * 32 + (y % 8) * 4 + (x % 8) / 2
            val shift = ((x % 8) % 2) * 4
            tiles[i] = ((tiles[i].toInt() and (if (shift == 0) 0xF0 else 0x0F)) or (v shl shift)).toByte()
        }
        setPixel(3, 3, 1)      // top-left block: identity
        setPixel(0, 64, 2)     // strip (0,64) -> canvas (64,0)
        setPixel(32, 72, 3)    // strip (32,72) -> canvas (64,24)
        setPixel(5, 100, 1)    // strip (5,100) -> canvas (5,68)
        setPixel(33, 136, 2)   // strip (33,136) -> canvas (65,88)
        val d = RomSprites.decodeGen5(lz10Literal(tiles), pal)
        assertEquals(96, d.width); assertEquals(96, d.height)
        val px = { x: Int, y: Int -> d.argb[y * 96 + x] }
        assertEquals(RomSprites.argb555(red555), px(3, 3))
        assertEquals(RomSprites.argb555(green555), px(64, 0))
        assertEquals(RomSprites.argb555(blue555), px(64, 24))
        assertEquals(RomSprites.argb555(red555), px(5, 68))
        assertEquals(RomSprites.argb555(green555), px(65, 88))
        assertEquals(5, d.argb.count { it != 0 }, "nothing else lit")
    }

    @Test
    fun `archive paths are the randomizer's own, read from its ini files`() {
        val gen4 = File("../engine-zx/src/com/dabomstew/pkrandomzx/config/gen4_offsets.ini").takeIf { it.exists() }
            ?: File("engine-zx/src/com/dabomstew/pkrandomzx/config/gen4_offsets.ini")
        val gen5 = File(gen4.path.replace("gen4_offsets", "gen5_offsets"))
        fun graphics(ini: File, section: String): String {
            val lines = ini.readLines(); val start = lines.indexOf("[$section]"); require(start >= 0) { "no [$section]" }
            val body = lines.drop(start + 1).takeWhile { !it.startsWith("[") }
            val line = body.first { it.startsWith("File<PokemonGraphics>=") }
            return line.substringAfter("<", "").substringAfter("<").substringBefore(",").trim()
        }
        assertEquals(graphics(gen4, "Platinum (U)"), RomSprites.narcPath(RomKind.PLATINUM_U))
        assertEquals(graphics(gen4, "HeartGold (U)"), RomSprites.narcPath(RomKind.HEARTGOLD_U))
        assertEquals(graphics(gen5, "Black (U)"), RomSprites.narcPath(RomKind.BLACK_U))
        assertEquals(graphics(gen5, "Black 2 (U)"), RomSprites.narcPath(RomKind.BLACK2_U))
        assertNull(RomSprites.narcPath(RomKind.EMERALD_U), "GBA decodes its own way")
        assertTrue(RomSprites.isGen5(RomKind.WHITE2_U)); assertTrue(!RomSprites.isGen5(RomKind.SOULSILVER_U))
    }

    @Test
    fun `the palette reader takes sixteen BGR555 words at 40 and leaves entry 0 clear`() {
        val p = RomSprites.palette(pal)
        assertEquals(0, p[0]); assertEquals(RomSprites.argb555(red555), p[1])
        assertEquals(RomSprites.argb555(green555), p[2]); assertEquals(RomSprites.argb555(blue555), p[3])
        assertEquals((31 * 8.25).toInt(), (RomSprites.argb555(red555) shr 16) and 0xFF)
    }
}
