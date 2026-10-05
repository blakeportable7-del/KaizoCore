package com.ironmonone.tracker

import java.io.File
import java.util.zip.CRC32
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Heart & Soul's own pictures (HnsPics, Smol) out of the comfort build, hns-kaizo.gba, where it lies (as HnsTrackerTest
 * finds it). Each picture's pixels are pinned by a CRC-32 of its ARGB, taken from this decoder and checked by eye against
 * the game (2026-10-05): Bulbasaur, Pikachu, Sprigatito (Gen 9), Alolan Rattata and Unown C (forms).
 */
class HnsPicsTest {
    private val rom: ByteArray? by lazy {
        Dumps.file(HnsTrackerTest.romDir(), "hns-kaizo.gba")?.readBytes()
    }

    private class Rom(val rom: ByteArray) : MemoryReader {
        override fun read(address: Long, length: Int): ByteArray {
            if (address !in 0x08000000L..0x09FFFFFFL) return ByteArray(0)
            val o = (address - 0x08000000L).toInt()
            if (o >= rom.size) return ByteArray(0)
            return rom.copyOfRange(o, minOf(rom.size, o + length))
        }
    }

    private fun crc(argb: IntArray): Long = CRC32().also { c -> argb.forEach { v -> for (i in 0 until 4) c.update((v ushr (8 * i)) and 0xFF) } }.value

    @Test
    fun `known species decode to 64 by 64 pictures with their own palettes`() {
        val r = rom ?: return
        val m = Rom(r)
        val pins = mapOf(1 to "BULBASAUR", 25 to "PIKACHU", 1289 to "SPRIGATITO", 956 to "RATTATA_ALOLA", 1025 to "UNOWN_C")
        val got = pins.keys.associateWith { sp -> assertNotNull(HnsPics.front(m, sp), pins[sp]) }
        for ((sp, p) in got) {
            assertEquals(64, p.width); assertEquals(64, p.height); assertEquals(64 * 64, p.argb.size)
            assertEquals(0, p.argb[0] ushr 24, "${pins[sp]}: the corner is the transparent colour 0")
            val opaque = p.argb.count { it ushr 24 == 0xFF }
            assertTrue(opaque in 300..4000, "${pins[sp]}: $opaque opaque pixels")
        }
        // Pinned from this decoder on 2026-10-05, the pictures checked by eye against the game's.
        val crcs = mapOf(1 to 0xB5BCF3CCL, 25 to 0x81046CEEL, 1289 to 0x727A7E3CL, 956 to 0xE989999FL, 1025 to 0x7761E265L)
        for ((sp, want) in crcs) assertEquals(want, crc(got.getValue(sp).argb), "${pins[sp]}'s pixels")
        // Bulbasaur's palette entry 1 and Pikachu's yellow body: their own colours, not each other's.
        assertTrue(got.getValue(25).argb.any { (it shr 16 and 0xFF) > 200 && (it shr 8 and 0xFF) > 180 && (it and 0xFF) < 100 }, "Pikachu has yellow")
        assertTrue(got.getValue(1).argb.none { (it shr 16 and 0xFF) > 200 && (it shr 8 and 0xFF) > 180 && (it and 0xFF) < 100 && it ushr 24 == 0xFF } ||
            got.getValue(1).argb.count { (it shr 8 and 0xFF) > (it shr 16 and 0xFF) } > 300, "Bulbasaur is mostly green")
        // A shiny palette is another picture of the same shape.
        val shiny = assertNotNull(HnsPics.front(m, 25, shiny = true))
        assertTrue(!shiny.argb.contentEquals(got.getValue(25).argb), "the shiny palette differs")
        assertEquals(got.getValue(25).argb.map { it ushr 24 == 0 }, shiny.argb.map { it ushr 24 == 0 }, "same pixels, other colours")
    }

    @Test
    fun `nearly every species decodes`() {
        val r = rom ?: return
        val m = Rom(r)
        var ok = 0; var none = 0
        val failed = ArrayList<Int>()
        for (sp in 1..HnsSpecies.TOTAL) {
            if (HnsPics.front(m, sp) != null) ok++ else { none++; if (failed.size < 40) failed += sp }
        }
        println("HnsPicsTest: $ok of ${HnsSpecies.TOTAL} species decode; $none do not (first: $failed)")
        // Every species the game uses has a picture: the 145 that do not are the build's disabled entries (Megas,
        // Gigantamax and the other battle-only forms, enabled false in species-kaizo.json), which have none.
        assertEquals(1427, ok, "$ok of ${HnsSpecies.TOTAL} decode")
    }

    @Test
    fun `a corrupt stream fails cleanly`() {
        val r = rom ?: return
        val m = Rom(r)
        val SI = HnsLayout.SpeciesInfo
        val rec = m.read(HnsLayout.gSpeciesInfo + 25L * SI.SIZE, SI.SIZE)
        val pic = SI.frontPic.u32(rec)
        val stream = m.read(pic, 4096)
        // Truncated, a broken header, and noise after the header: each is refused, none loops or reads out of bounds.
        assertFailsWith<Smol.Corrupt> { Smol.decode(stream.copyOf(40)) }
        assertFailsWith<Smol.Corrupt> { Smol.decode(stream.copyOf().also { it[0] = 0x0F }) }
        val noisy = stream.copyOf().also { for (i in 8 until 200) it[i] = (it[i].toInt() xor 0x5A).toByte() }
        runCatching { Smol.decode(noisy) }.onSuccess { out ->
            // A stream noise happens to keep decodable still has to come out at its declared size.
            assertEquals(2048, out.size)
        }
        assertNull(HnsPics.decompress(m, 0x07000000L), "not ROM")
        assertNull(Smol.lz77(byteArrayOf(0x10, 0x40, 0, 0, 0x01)), "LZ77 that ends early")
    }
}
