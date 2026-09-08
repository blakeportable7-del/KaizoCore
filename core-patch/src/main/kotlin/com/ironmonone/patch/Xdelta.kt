package com.ironmonone.patch

import java.io.File
import java.io.InputStream
import java.io.RandomAccessFile
import java.util.zip.Adler32

/**
 * xdelta3 patches, which are VCDIFF (RFC 3284) with xdelta3's Adler-32 window
 * check. The Super Kaizo DS patches (HeartGold, Platinum) ship this way, as
 * 10 to 23 MB deltas against a 128 MB dump, so nothing here holds the source
 * or the target in memory: the source is read through a RandomAccessFile a
 * window at a time and the target is streamed to disk.
 *
 * Supported: the default code table, the default address cache (4 near, 3
 * same), source and target windows, application headers, Adler-32 checks.
 * Refused with a plain message: secondary compression (xdelta3 -S) and custom
 * code tables, which neither Super Kaizo patch uses (checked 2026-09-08:
 * both carry hdr_indicator 0x04, app header only).
 *
 * Written from the RFC; verified by the per-window Adler-32 in the patches
 * themselves, which fails on any decoding slip.
 */
object Xdelta {
    private const val VCD_SOURCE = 0x01
    private const val VCD_TARGET = 0x02
    private const val VCD_ADLER32 = 0x04
    private const val HDR_SECONDARY = 0x01
    private const val HDR_CODETABLE = 0x02
    private const val HDR_APPHEADER = 0x04
    private const val NEAR = 4
    private const val SAME = 3

    private const val NOOP = 0
    private const val ADD = 1
    private const val RUN = 2
    private const val COPY = 3

    /** (type, size, mode) pairs per code, the RFC's default table. */
    private val table: Array<IntArray> by lazy {
        val t = ArrayList<IntArray>(256)
        fun e(t1: Int, s1: Int, m1: Int, t2: Int = NOOP, s2: Int = 0, m2: Int = 0) = t.add(intArrayOf(t1, s1, m1, t2, s2, m2))
        e(RUN, 0, 0)
        for (s in 0..17) e(ADD, s, 0)
        for (mode in 0..8) { e(COPY, 0, mode); for (s in 4..18) e(COPY, s, mode) }
        for (mode in 0..5) for (a in 1..4) for (c in 4..6) e(ADD, a, 0, COPY, c, mode)
        for (mode in 6..8) for (a in 1..4) e(ADD, a, 0, COPY, 4, mode)
        for (mode in 0..8) e(COPY, 4, mode, ADD, 1, 0)
        check(t.size == 256) { "code table has ${t.size} entries" }
        t.toTypedArray()
    }

    fun isXdelta(head: ByteArray): Boolean =
        head.size >= 4 && head[0] == 0xD6.toByte() && head[1] == 0xC3.toByte() && head[2] == 0xC4.toByte() && head[3] == 0.toByte()

    /**
     * Applies [patch] to [source], writing [target]. Throws [CorruptPatch] on a
     * malformed or unsupported patch and [OutputMismatch]-style failures as
     * [CorruptPatch] when a window's Adler-32 does not match.
     */
    fun apply(patch: File, source: File, target: File, onProgress: ((Long, Long) -> Unit)? = null) {
        patch.inputStream().buffered(1 shl 16).use { p ->
            RandomAccessFile(source, "r").use { src ->
                target.parentFile?.mkdirs()
                RandomAccessFile(target, "rw").use { out ->
                    out.setLength(0)
                    apply(Reader(p), src, out, patch.length(), onProgress)
                }
            }
        }
    }

    private class Reader(val input: InputStream) {
        var position = 0L
        fun byte(): Int {
            val b = input.read()
            if (b < 0) throw CorruptPatch("the xdelta patch ends early")
            position++
            return b
        }
        fun int(): Long {
            var v = 0L
            while (true) {
                val b = byte()
                v = (v shl 7) or (b and 0x7F).toLong()
                if (b and 0x80 == 0) return v
                if (v > (1L shl 56)) throw CorruptPatch("an xdelta integer is too large")
            }
        }
        fun bytes(n: Int): ByteArray {
            val b = ByteArray(n)
            var got = 0
            while (got < n) {
                val r = input.read(b, got, n - got)
                if (r < 0) throw CorruptPatch("the xdelta patch ends early")
                got += r
            }
            position += n
            return b
        }
        fun skip(n: Long) { var left = n; while (left > 0) { val s = input.skip(left); if (s <= 0) { byte(); left-- } else left -= s }; position += n }
        fun eof(): Boolean { input.mark(1); val b = input.read(); if (b < 0) return true; input.reset(); return false }
    }

    private class Section(val bytes: ByteArray) {
        var pos = 0
        fun byte(): Int { if (pos >= bytes.size) throw CorruptPatch("an xdelta section ends early"); return bytes[pos++].toInt() and 0xFF }
        fun int(): Long {
            var v = 0L
            while (true) {
                val b = byte()
                v = (v shl 7) or (b and 0x7F).toLong()
                if (b and 0x80 == 0) return v
            }
        }
    }

    private fun apply(r: Reader, src: RandomAccessFile, out: RandomAccessFile, patchLen: Long, onProgress: ((Long, Long) -> Unit)?) {
        val magic = r.bytes(4)
        if (!isXdelta(magic)) throw CorruptPatch("it is not an xdelta (VCDIFF) patch")
        val hdr = r.byte()
        if (hdr and HDR_SECONDARY != 0) throw CorruptPatch("this xdelta patch uses secondary compression, which this app does not read")
        if (hdr and HDR_CODETABLE != 0) throw CorruptPatch("this xdelta patch uses a custom code table, which this app does not read")
        if (hdr and HDR_APPHEADER != 0) r.skip(r.int())

        var written = 0L
        while (!r.eof()) {
            val win = r.byte()
            var segLen = 0L; var segPos = 0L
            if (win and (VCD_SOURCE or VCD_TARGET) != 0) { segLen = r.int(); segPos = r.int() }
            r.int() // length of the delta encoding
            val targetLen = r.int().toInt()
            val deltaIndicator = r.byte()
            if (deltaIndicator != 0) throw CorruptPatch("this xdelta patch compresses its sections, which this app does not read")
            val dataLen = r.int().toInt(); val instLen = r.int().toInt(); val addrLen = r.int().toInt()
            var adler = -1L
            if (win and VCD_ADLER32 != 0) {
                val a = r.bytes(4)
                adler = ((a[0].toLong() and 0xFF) shl 24) or ((a[1].toLong() and 0xFF) shl 16) or ((a[2].toLong() and 0xFF) shl 8) or (a[3].toLong() and 0xFF)
            }
            val data = Section(r.bytes(dataLen)); val inst = Section(r.bytes(instLen)); val addr = Section(r.bytes(addrLen))

            val seg = when {
                win and VCD_SOURCE != 0 -> ByteArray(segLen.toInt()).also { src.seek(segPos); src.readFully(it) }
                win and VCD_TARGET != 0 -> ByteArray(segLen.toInt()).also { out.seek(segPos); out.readFully(it); out.seek(out.length()) }
                else -> ByteArray(0)
            }
            val target = ByteArray(targetLen)
            var tp = 0
            val near = LongArray(NEAR); var nearNext = 0
            val same = LongArray(SAME * 256)

            fun decodeAddr(here: Long, mode: Int): Long {
                val a = when {
                    mode == 0 -> addr.int()
                    mode == 1 -> here - addr.int()
                    mode < 2 + NEAR -> near[mode - 2] + addr.int()
                    else -> same[(mode - 2 - NEAR) * 256 + addr.byte()]
                }
                near[nearNext] = a; nearNext = (nearNext + 1) % NEAR
                same[(a % (SAME * 256)).toInt()] = a
                return a
            }
            fun run(type: Int, size0: Int, mode: Int) {
                if (type == NOOP) return
                val size = if (size0 == 0) inst.int().toInt() else size0
                if (tp + size > targetLen) throw CorruptPatch("an xdelta window overruns its target")
                when (type) {
                    ADD -> { repeat(size) { target[tp++] = data.byte().toByte() } }
                    RUN -> { val b = data.byte().toByte(); repeat(size) { target[tp++] = b } }
                    COPY -> {
                        var a = decodeAddr(segLen + tp, mode)
                        repeat(size) {
                            val v = if (a < segLen) seg[a.toInt()] else {
                                val t = (a - segLen).toInt()
                                if (t >= tp) throw CorruptPatch("an xdelta copy reads ahead of itself")
                                target[t]
                            }
                            target[tp++] = v
                            a++
                        }
                    }
                }
            }
            while (inst.pos < inst.bytes.size) {
                val code = table[inst.byte()]
                run(code[0], code[1], code[2]); run(code[3], code[4], code[5])
            }
            if (tp != targetLen) throw CorruptPatch("an xdelta window decoded ${tp} of $targetLen bytes")
            if (adler >= 0) {
                val check = Adler32().apply { update(target, 0, targetLen) }.value
                if (check != adler) throw CorruptPatch("an xdelta window failed its checksum (the source dump may be the wrong revision)")
            }
            out.write(target, 0, targetLen)
            written += targetLen
            onProgress?.invoke(r.position, patchLen)
        }
    }
}
