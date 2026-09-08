package com.ironmonone.patch

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * The VCDIFF decoder against the real Super Kaizo HeartGold patch and Blake's
 * dump, when both are on this machine (IRONMON_XDELTA_PATCH and
 * IRONMON_XDELTA_SOURCE). Every window carries xdelta3's Adler-32, so a clean
 * run is a proof of the decoding, not just of "it did not crash". Prints the
 * result CRC so the patched kind can be pinned.
 */
class XdeltaTest {
    @Test
    fun `refuses what is not an xdelta`() {
        val dir = File(System.getProperty("java.io.tmpdir"))
        val p = File(dir, "notx.xdelta").apply { writeBytes("BPS1....".toByteArray()) }
        val s = File(dir, "src.bin").apply { writeBytes(ByteArray(16)) }
        assertFailsWith<CorruptPatch> { Xdelta.apply(p, s, File(dir, "out.bin")) }
    }

    @Test
    fun `the HeartGold Super Kaizo patch decodes with every window checksum passing`() {
        val patch = System.getenv("IRONMON_XDELTA_PATCH")?.let(::File)?.takeIf { it.isFile } ?: return
        val source = System.getenv("IRONMON_XDELTA_SOURCE")?.let(::File)?.takeIf { it.isFile } ?: return
        val out = File(System.getProperty("java.io.tmpdir"), "xdelta-out.nds")
        var last = 0L
        Xdelta.apply(patch, source, out) { done, total -> last = done; assertTrue(done <= total) }
        assertEquals(patch.length(), last)
        val crc = java.util.zip.CRC32()
        out.inputStream().buffered(1 shl 20).use { input -> val b = ByteArray(1 shl 20); while (true) { val n = input.read(b); if (n < 0) break; crc.update(b, 0, n) } }
        println("XDELTA_RESULT size=${out.length()} crc=%08x".format(crc.value))
        assertTrue(out.length() > 0)
    }
}
