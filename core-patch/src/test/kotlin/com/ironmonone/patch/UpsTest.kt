package com.ironmonone.patch

import com.ironmonone.core.PatchFormat
import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * UPS, added 2026-09-05 for the library patcher. The encoder below is the
 * format's definition written the other way round, so a decode that matches
 * it is a decode of the format, not of an example file.
 */
class UpsTest {

    @Test
    fun `detects and applies a UPS patch, including a size change`() {
        val source = ByteArray(300) { (it * 7 + 3).toByte() }
        val target = source.copyOf(320)
        target[0] = 0x55; target[10] = 0x11; target[11] = 0x22; target[299] = 0x7F
        target[310] = 0x42  // past the source's end: XOR against zero
        val patch = encodeUps(source, target)
        assertEquals(PatchFormat.UPS, Patcher.detect(patch))
        assertTrue(Patcher.apply(patch, source).contentEquals(target))
        assertTrue(Ups.apply(patch, source).contentEquals(target))
    }

    @Test
    fun `refuses the wrong source with the plain-English message`() {
        val source = ByteArray(64) { it.toByte() }
        val target = source.copyOf(); target[5] = 99
        val patch = encodeUps(source, target)
        val wrong = source.copyOf(); wrong[0] = 1
        val e = assertFailsWith<WrongSourceRom> { Ups.apply(patch, wrong, "Emerald") }
        assertContains(e.message!!, "different copy of Emerald")
        assertFailsWith<WrongSourceSize> { Ups.apply(patch, ByteArray(65)) }
    }

    @Test
    fun `refuses a patch whose own checksum is wrong`() {
        val source = ByteArray(16); val target = ByteArray(16) { 1 }
        val patch = encodeUps(source, target)
        patch[8] = (patch[8].toInt() xor 1).toByte()
        val e = assertFailsWith<CorruptPatch> { Ups.apply(patch, source) }
        assertContains(e.message!!, "checksum")
    }

    /** Straight from the spec: header, two sizes, (gap, xor run, 0) records, three CRCs. */
    private fun encodeUps(source: ByteArray, target: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        out.write("UPS1".toByteArray())
        writeVarint(out, source.size.toLong())
        writeVarint(out, target.size.toLong())
        val n = maxOf(source.size, target.size)
        var i = 0
        var last = 0
        while (i < n) {
            val s = if (i < source.size) source[i].toInt() and 0xFF else 0
            val t = if (i < target.size) target[i].toInt() and 0xFF else 0
            if (s == t) { i++; continue }
            writeVarint(out, (i - last).toLong())
            while (i < n) {
                val s2 = if (i < source.size) source[i].toInt() and 0xFF else 0
                val t2 = if (i < target.size) target[i].toInt() and 0xFF else 0
                val x = s2 xor t2
                if (x == 0) break
                out.write(x); i++
            }
            out.write(0); i++
            last = i
        }
        fun le(v: Long) { for (k in 0 until 4) out.write(((v shr (8 * k)) and 0xFF).toInt()) }
        le(Crc32.of(source)); le(Crc32.of(target))
        le(Crc32.of(out.toByteArray()))
        return out.toByteArray()
    }

    private fun writeVarint(out: ByteArrayOutputStream, value: Long) {
        var v = value
        while (true) {
            val x = (v and 0x7f).toInt()
            v = v shr 7
            if (v == 0L) { out.write(0x80 or x); break }
            out.write(x)
            v--
        }
    }
}
