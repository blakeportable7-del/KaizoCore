package com.ironmonone.app.stream

import java.util.zip.CRC32
import java.util.zip.Inflater
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * The frame encoder must give back the same pixels, byte for byte: the JDK's own
 * PNG decoder is the judge, so a wrong filter, a wrong CRC or a wrong chunk order
 * fails here and not on somebody's stream.
 */
class PngEncoderTest {

    private fun roundTrip(rgb: ByteArray, w: Int, h: Int, filter: Int? = null): ByteArray {
        val png = PngEncoder().encode(rgb, w, h, filter)
        val (dw, dh, decoded) = TestPics.decode(png)
        assertEquals(w to h, dw to dh)
        return decoded.also { assertContentEquals(rgb, it, "pixels changed (${w}x$h, filter $filter)") }
    }

    /** The filter byte of every row, read by inflating the IDAT data. */
    private fun filtersUsed(png: ByteArray, w: Int, h: Int): List<Int> {
        var p = 8
        val idat = java.io.ByteArrayOutputStream()
        while (p < png.size) {
            val len = ((png[p].toInt() and 0xFF) shl 24) or ((png[p + 1].toInt() and 0xFF) shl 16) or ((png[p + 2].toInt() and 0xFF) shl 8) or (png[p + 3].toInt() and 0xFF)
            val type = String(png, p + 4, 4, Charsets.US_ASCII)
            if (type == "IDAT") idat.write(png, p + 8, len)
            p += 12 + len
        }
        val inf = Inflater()
        inf.setInput(idat.toByteArray())
        val raw = ByteArray((w * 3 + 1) * h)
        var got = 0
        while (got < raw.size && !inf.finished()) got += inf.inflate(raw, got, raw.size - got)
        inf.end()
        assertEquals(raw.size, got, "the zlib stream holds every row")
        return (0 until h).map { raw[it * (w * 3 + 1)].toInt() }
    }

    @Test
    fun `random noise comes back exactly`() {
        roundTrip(TestPics.noise(37, 23, 1), 37, 23)
        roundTrip(TestPics.noise(240, 160, 2), 240, 160)
    }

    @Test
    fun `a picture with flat areas, ramps, stripes and noise comes back exactly at game sizes`() {
        for ((w, h) in listOf(240 to 160, 160 to 144, 256 to 192, 256 to 384, 512 to 192)) {
            roundTrip(TestPics.structured(w, h), w, h)
        }
    }

    @Test
    fun `the smallest pictures and odd shapes come back exactly`() {
        roundTrip(byteArrayOf(1, 2, 3), 1, 1)
        roundTrip(TestPics.noise(1, 9, 3), 1, 9)
        roundTrip(TestPics.noise(9, 1, 4), 9, 1)
        roundTrip(TestPics.noise(3, 3, 5), 3, 3)
        roundTrip(ByteArray(64 * 64 * 3), 64, 64)                       // all black
        roundTrip(ByteArray(64 * 64 * 3) { 0xFF.toByte() }, 64, 64)     // all white
    }

    @Test
    fun `every filter decodes to the same pixels`() {
        val w = 61; val h = 40
        val pic = TestPics.structured(w, h, seed = 11)
        for (f in PngEncoder.FILTERS) roundTrip(pic, w, h, f)
        // Values that wrap when the predictor is taken away are where a filter usually breaks.
        val edge = ByteArray(w * h * 3) { if ((it / 3 + it / (w * 3)) % 2 == 0) 0xFF.toByte() else 0 }
        for (f in PngEncoder.FILTERS) roundTrip(edge, w, h, f)
    }

    @Test
    fun `the filter is chosen per row and a repeated row is the up filter`() {
        val w = 48; val h = 64
        val png = PngEncoder().encode(TestPics.structured(w, h), w, h)
        val used = filtersUsed(png, w, h)
        assertTrue(used.toSet().size >= 3, "flat, ramp, stripe and noise rows should not all pick one filter: $used")
        // The stripe band repeats the row above it exactly, so those rows are the up filter.
        val stripes = used.subList(h / 2 + 1, 3 * h / 4)
        assertTrue(stripes.all { it == PngEncoder.FILTER_UP }, "stripe rows: $stripes")
    }

    @Test
    fun `a flat frame is a tiny fraction of its raw size`() {
        val w = 240; val h = 160
        val png = PngEncoder().encode(ByteArray(w * h * 3) { (it % 3 * 40).toByte() }, w, h)
        assertTrue(png.size < w * h * 3 / 50, "flat 240x160 frame took ${png.size} bytes of ${w * h * 3}")
    }

    @Test
    fun `the file is a PNG, with the right header and a good CRC on every chunk`() {
        val w = 30; val h = 20
        val png = PngEncoder().encode(TestPics.structured(w, h), w, h)
        assertContentEquals(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A), png.copyOf(8))

        var p = 8
        val types = ArrayList<String>()
        while (p < png.size) {
            val len = ((png[p].toInt() and 0xFF) shl 24) or ((png[p + 1].toInt() and 0xFF) shl 16) or ((png[p + 2].toInt() and 0xFF) shl 8) or (png[p + 3].toInt() and 0xFF)
            val type = String(png, p + 4, 4, Charsets.US_ASCII)
            types += type
            val crc = CRC32().also { it.update(png, p + 4, 4 + len) }.value
            val stored = ((png[p + 8 + len].toLong() and 0xFF) shl 24) or ((png[p + 9 + len].toLong() and 0xFF) shl 16) or
                ((png[p + 10 + len].toLong() and 0xFF) shl 8) or (png[p + 11 + len].toLong() and 0xFF)
            assertEquals(crc, stored, "CRC of $type")
            if (type == "IHDR") {
                assertEquals(13, len)
                assertEquals(w, ((png[p + 8].toInt() and 0xFF) shl 24) or ((png[p + 9].toInt() and 0xFF) shl 16) or ((png[p + 10].toInt() and 0xFF) shl 8) or (png[p + 11].toInt() and 0xFF))
                assertEquals(h, ((png[p + 12].toInt() and 0xFF) shl 24) or ((png[p + 13].toInt() and 0xFF) shl 16) or ((png[p + 14].toInt() and 0xFF) shl 8) or (png[p + 15].toInt() and 0xFF))
                assertEquals(listOf(8, 2, 0, 0, 0), (16..20).map { png[p + it].toInt() }, "8 bit, truecolor, no interlace")
            }
            p += 12 + len
        }
        assertEquals(listOf("IHDR", "IDAT", "IEND"), types)
        assertEquals(png.size, p)
    }

    @Test
    fun `one encoder serves many frames of different sizes`() {
        val enc = PngEncoder()
        val big = TestPics.structured(256, 384, 3)
        val small = TestPics.noise(40, 30, 4)
        repeat(3) {
            assertContentEquals(big, TestPics.decode(enc.encode(big, 256, 384)).third)
            assertContentEquals(small, TestPics.decode(enc.encode(small, 40, 30)).third)
        }
        enc.release()
    }

    @Test
    fun `a buffer of the wrong size or an empty picture is refused`() {
        val enc = PngEncoder()
        assertFailsWith<IllegalArgumentException> { enc.encode(ByteArray(10), 2, 2) }
        assertFailsWith<IllegalArgumentException> { enc.encode(ByteArray(0), 0, 5) }
        assertFailsWith<IllegalArgumentException> { enc.encode(ByteArray(12), 2, 2, 3) }   // filter 3 is not one this encoder writes
    }
}
