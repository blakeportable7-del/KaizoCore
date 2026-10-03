package com.ironmonone.patch

import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.zip.Adler32
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The VCDIFF decoder against patches built by hand from RFC 3284, on synthetic bytes only (rc32 audit P3 #85: the only
 * always-on decode was one COPY and one ADD). Every instruction's address is worked out below from the RFC's own rules
 * (5.1 to 5.3: near cache of 4, same cache of 3 x 256, both reset at each window, "here" is the segment length plus
 * the position in the target), and each expected byte is a slice of the source or of what came before, so the test
 * does not lean on the decoder to say what is right. The codes are the RFC's default table (5.6):
 * RUN 0; ADD size s at 1 + s; COPY size s in mode m at 19 + 16m + (s - 3), size 0 at 19 + 16m; ADD a + COPY c in mode
 * m at 163 + 12m + 3(a - 1) + (c - 4) for m up to 5, ADD a + COPY 4 in mode m at 235 + 4(m - 6) + (a - 1) for m from
 * 6; COPY 4 in mode m + ADD 1 at 247 + m.
 */
class XdeltaVectorsTest {
    private val dir = Files.createTempDirectory("xd").toFile()
    @AfterTest fun clean() { dir.deleteRecursively() }

    private fun varint(v: Long): ByteArray {
        val out = ArrayList<Byte>()
        var x = v
        out.add((x and 0x7F).toByte()); x = x ushr 7
        while (x > 0) { out.add(0, ((x and 0x7F) or 0x80).toByte()); x = x ushr 7 }
        return out.toByteArray()
    }
    private fun varint(v: Int) = varint(v.toLong())
    private fun bytes(vararg b: Int) = ByteArray(b.size) { b[it].toByte() }

    /** One window: its indicator, the segment, and its three sections; the Adler-32 of [target] when [adler] (xdelta3's extension). */
    private fun window(indicator: Int, segment: Pair<Int, Int>?, target: ByteArray, data: ByteArray, inst: ByteArray, addr: ByteArray, adler: Long? = null): ByteArray {
        val delta = ByteArrayOutputStream().apply {
            write(varint(target.size)); write(0)
            write(varint(data.size)); write(varint(inst.size)); write(varint(addr.size))
            val a = adler ?: if (indicator and 0x04 != 0) Adler32().apply { update(target) }.value else null
            if (a != null) write(bytes(((a shr 24) and 0xFF).toInt(), ((a shr 16) and 0xFF).toInt(), ((a shr 8) and 0xFF).toInt(), (a and 0xFF).toInt()))
            write(data); write(inst); write(addr)
        }.toByteArray()
        return ByteArrayOutputStream().apply {
            write(indicator)
            segment?.let { (len, pos) -> write(varint(len)); write(varint(pos)) }
            write(varint(delta.size)); write(delta)
        }.toByteArray()
    }

    private fun patch(vararg windows: ByteArray) = ByteArrayOutputStream().apply {
        write(bytes(0xD6, 0xC3, 0xC4, 0)); write(0)
        windows.forEach { write(it) }
    }.toByteArray()

    private fun run(patch: ByteArray, source: ByteArray): ByteArray {
        val p = File(dir, "p.xdelta").apply { writeBytes(patch) }
        val s = File(dir, "s.bin").apply { writeBytes(source) }
        val t = File(dir, "t.bin")
        Xdelta.apply(p, s, t)
        return t.readBytes()
    }

    private val src = ByteArray(600) { ((it * 7 + 3) % 251).toByte() }
    private fun s(from: Int, len: Int) = src.copyOfRange(from, from + len)

    /** Window 1: a source window that walks every address mode, RUN, both kinds of paired code and copies inside the target. */
    private fun window1(): Pair<ByteArray, ByteArray> {
        val t = ByteArrayOutputStream()
        fun out() = t.toByteArray()
        // 1. COPY 4, mode 0 (self) at 300.                          near [300 0 0 0], same[300] = 300
        t.write(s(300, 4))
        // 2. COPY 5, mode 1 (here): here = 600 + 4, read 594 -> 10.   near [300 10 0 0], same[10] = 10
        t.write(s(10, 5))
        // 3. COPY 4, mode 2 (near[0] = 300) + 20 -> 320.            near [300 10 320 0], same[320] = 320
        t.write(s(320, 4))
        // 4. RUN 3 of 0x7E, its size in the instruction stream.
        t.write(bytes(0x7E, 0x7E, 0x7E))
        // 5. COPY 4, mode 7 (same block 1), byte 44: same[256 + 44] = 300.   near [300 10 320 300]
        t.write(s(300, 4))
        // 6. COPY 4, mode 6 (same block 0), byte 10: same[10] = 10.          near [10 10 320 300]
        t.write(s(10, 4))
        // 7. ADD 2 then COPY 6, mode 4 (near[2] = 320) + 5 -> 325.            near [10 325 320 300]
        t.write(bytes(0xA1, 0xA2)); t.write(s(325, 6))
        // 8. COPY 6, mode 0 at 614: inside this window's target, from position 14.   near [10 325 614 300]
        t.write(out().copyOfRange(14, 20))
        // 9. COPY 6, mode 1: here = 600 + 38, read 2 -> 636, position 36: overlaps itself, a two-byte repeat.  near [10 325 614 636]
        repeat(3) { val o = out(); t.write(o.copyOfRange(o.size - 2, o.size)) }
        // 10. COPY 4, mode 8 (same block 2), byte 102: same[512 + 102] = 614.   near [614 325 614 636]
        t.write(out().copyOfRange(14, 18))
        // 11. COPY 4, mode 3 (near[1] = 325) + 100 -> 425, then ADD 1.
        t.write(s(425, 4)); t.write(bytes(0xB7))
        // 12. ADD with its size (3) in the instruction stream.
        t.write(bytes(1, 2, 3))
        val target = out()
        val data = bytes(0x7E, 0xA1, 0xA2, 0xB7, 1, 2, 3)
        val inst = byteArrayOf(20) + byteArrayOf(37) + byteArrayOf(52) + byteArrayOf(0) + varint(3) + byteArrayOf(132.toByte()) +
            byteArrayOf(116) + byteArrayOf((163 + 12 * 4 + 3 * 1 + 2).toByte()) + byteArrayOf(22) + byteArrayOf(38) +
            byteArrayOf(148.toByte()) + byteArrayOf((247 + 3).toByte()) + byteArrayOf(1) + varint(3)
        val addr = varint(300) + varint(594) + varint(20) + bytes(44) + bytes(10) + varint(5) + varint(614) + varint(2) +
            bytes(102) + varint(100)
        return window(0x05, 600 to 0, target, data, inst, addr) to target
    }

    @Test
    fun `three windows (source, target and none) decode to the exact bytes, every address mode and code kind included`() {
        val (w1, t1) = window1()
        assertTrue(t1.size == 56, "the plan above makes 56 bytes, not ${t1.size}")
        // Window 2: a VCD_TARGET window over the first 20 bytes already written. COPY 5 mode 0 at 3, ADD 1, then
        // COPY 4 mode 1: here = 20 + 6, read 4 -> 22, position 2 of this window.
        val t2 = ByteArrayOutputStream().apply {
            write(t1.copyOfRange(3, 8)); write(bytes(0xC3))
            val sofar = toByteArray(); write(sofar.copyOfRange(2, 6))
        }.toByteArray()
        val w2 = window(0x06, 20 to 0, t2, bytes(0xC3), byteArrayOf(21, 2, 36), varint(3) + varint(4))
        // Window 3: no segment. RUN 5 of 0, ADD 2, COPY 4 mode 0 at 1 (inside this window).
        val t3 = bytes(0, 0, 0, 0, 0, 0xD1, 0xD2, 0, 0, 0, 0)
        val w3 = window(0x04, null, t3, bytes(0, 0xD1, 0xD2), byteArrayOf(0) + varint(5) + byteArrayOf(3, 20), varint(1))
        assertContentEquals(t1 + t2 + t3, run(patch(w1, w2, w3), src))
    }

    @Test
    fun `a copy that reads ahead of what the window has written is refused`() {
        val e = assertFailsWith<CorruptPatch> { run(patch(window(0, null, ByteArray(4), ByteArray(0), byteArrayOf(20), varint(0))), src) }
        assertTrue("reads ahead" in e.message!!, e.message)
    }

    @Test
    fun `a window that copied from the game and fails its checksum is the wrong game, and leaves no file`() {
        val (w1, t1) = window1()
        val other = src.copyOf().also { it[301] = (it[301] + 1).toByte() }
        assertFailsWith<SourceMismatch> { run(patch(w1), other) }
        assertFalse(File(dir, "t.bin").exists(), "a failed apply leaves no half-written game")
        // The same patch on a game too short for its window is the wrong game too, not a write error.
        assertFailsWith<SourceMismatch> { run(patch(w1), src.copyOf(100)) }
        assertContentEquals(t1, run(patch(w1), src))
    }

    @Test
    fun `a window that read nothing from the game and fails its checksum is a damaged patch`() {
        val t3 = bytes(0, 0, 0, 0, 0, 0xD1, 0xD2)
        val good = Adler32().apply { update(t3) }.value
        val w = window(0x04, null, t3, bytes(0, 0xD1, 0xD2), byteArrayOf(0) + varint(5) + byteArrayOf(3), ByteArray(0), adler = good xor 1L)
        val e = assertFailsWith<CorruptPatch> { run(patch(w), src) }
        assertTrue("checksum" in e.message!! && "revision" !in e.message!! && "dump" !in e.message!!, e.message)
    }
}
