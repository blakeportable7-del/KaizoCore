package com.ironmonone.app.stream

import java.util.zip.CRC32
import java.util.zip.Deflater

/**
 * One game frame as a lossless PNG: 8-bit truecolor, no alpha, no interlace, one
 * IDAT chunk. Hand-written because android.graphics.Bitmap does not exist on the
 * JVM the tests run on, and the exact-pixels round trip is the point of the tests.
 *
 * Game frames are flat areas and hard edges, so each row picks whichever of the four
 * PNG filters that matter (none, sub, up, paeth) leaves the smallest bytes, by the
 * usual sum-of-absolute-values test, and zlib runs in FILTERED mode. A row equal to
 * the one above it costs almost nothing.
 *
 * An instance keeps its buffers and its Deflater between frames, so it is for one
 * thread at a time (the stream's pump). Close it with [release] when done.
 * Added 2026-09-29 for the stream kit.
 */
class PngEncoder(level: Int = DEFAULT_LEVEL) {
    private val deflater = Deflater(level).also { it.setStrategy(Deflater.FILTERED) }
    private val crc = CRC32()
    private var scan = ByteArray(0)
    private var packed = ByteArray(0)
    private var tmpSub = ByteArray(0)
    private var tmpUp = ByteArray(0)
    private var tmpPaeth = ByteArray(0)

    /** [rgb] is width * height * 3 bytes, rows top to bottom, red first. */
    fun encode(rgb: ByteArray, width: Int, height: Int): ByteArray = encode(rgb, width, height, null)

    /** [forceFilter] (0, 1, 2 or 4) is for the tests: it makes every row use that filter. */
    internal fun encode(rgb: ByteArray, width: Int, height: Int, forceFilter: Int?): ByteArray {
        require(width > 0 && height > 0) { "empty image ${width}x$height" }
        require(rgb.size == width * height * 3) { "expected ${width * height * 3} bytes, got ${rgb.size}" }
        require(forceFilter == null || forceFilter in FILTERS) { "filter $forceFilter" }

        val stride = width * 3
        val scanLen = (stride + 1) * height
        if (scan.size < scanLen) scan = ByteArray(scanLen)
        if (tmpSub.size < stride) { tmpSub = ByteArray(stride); tmpUp = ByteArray(stride); tmpPaeth = ByteArray(stride) }
        filterRows(rgb, stride, height, forceFilter)

        deflater.reset()
        deflater.setInput(scan, 0, scanLen)
        deflater.finish()
        var n = 0
        if (packed.size < 4096) packed = ByteArray(4096)
        while (!deflater.finished()) {
            if (n == packed.size) packed = packed.copyOf(packed.size * 2)
            n += deflater.deflate(packed, n, packed.size - n)
        }

        val out = ByteArray(SIGNATURE.size + (12 + 13) + (12 + n) + 12)
        var p = 0
        System.arraycopy(SIGNATURE, 0, out, p, SIGNATURE.size); p += SIGNATURE.size
        val ihdr = ByteArray(13)
        putInt(ihdr, 0, width); putInt(ihdr, 4, height)
        ihdr[8] = 8    // bits per channel
        ihdr[9] = 2    // truecolor
        // compression, filter method and interlace stay 0
        p = chunk(out, p, IHDR, ihdr, 13)
        p = chunk(out, p, IDAT, packed, n)
        p = chunk(out, p, IEND, ihdr, 0)
        check(p == out.size) { "png size $p != ${out.size}" }
        return out
    }

    fun release() = deflater.end()

    private fun filterRows(rgb: ByteArray, stride: Int, height: Int, force: Int?) {
        for (y in 0 until height) {
            val row = y * stride
            val above = if (y == 0) -1 else row - stride
            val at = y * (stride + 1)

            val type = force ?: choose(rgb, row, above, stride)
            scan[at] = type.toByte()
            when (type) {
                FILTER_NONE -> System.arraycopy(rgb, row, scan, at + 1, stride)
                FILTER_SUB -> { sub(rgb, row, stride, scan, at + 1) }
                FILTER_UP -> { up(rgb, row, above, stride, scan, at + 1) }
                else -> { paeth(rgb, row, above, stride, scan, at + 1) }
            }
        }
    }

    /** The filter whose output has the smallest sum of absolute values, reading bytes as signed. */
    private fun choose(rgb: ByteArray, row: Int, above: Int, stride: Int): Int {
        var best = FILTER_NONE
        var bestSum = 0
        for (i in 0 until stride) { val v = rgb[row + i].toInt(); bestSum += if (v < 0) -v else v }

        up(rgb, row, above, stride, tmpUp, 0)
        val upSum = sumAbs(tmpUp, stride)
        if (upSum < bestSum) { best = FILTER_UP; bestSum = upSum }
        if (upSum == 0) return best               // the row above, exactly: nothing can beat it

        sub(rgb, row, stride, tmpSub, 0)
        val subSum = sumAbs(tmpSub, stride)
        if (subSum < bestSum) { best = FILTER_SUB; bestSum = subSum }

        if (above >= 0) {
            paeth(rgb, row, above, stride, tmpPaeth, 0)
            val paethSum = sumAbs(tmpPaeth, stride)
            if (paethSum < bestSum) { best = FILTER_PAETH }
        }
        return best
    }

    private fun sumAbs(a: ByteArray, n: Int): Int {
        var s = 0
        for (i in 0 until n) { val v = a[i].toInt(); s += if (v < 0) -v else v }
        return s
    }

    private fun sub(src: ByteArray, row: Int, stride: Int, dst: ByteArray, at: Int) {
        for (i in 0 until stride) {
            val left = if (i >= BPP) src[row + i - BPP].toInt() else 0
            dst[at + i] = (src[row + i].toInt() - left).toByte()
        }
    }

    private fun up(src: ByteArray, row: Int, above: Int, stride: Int, dst: ByteArray, at: Int) {
        for (i in 0 until stride) {
            val up = if (above >= 0) src[above + i].toInt() else 0
            dst[at + i] = (src[row + i].toInt() - up).toByte()
        }
    }

    private fun paeth(src: ByteArray, row: Int, above: Int, stride: Int, dst: ByteArray, at: Int) {
        for (i in 0 until stride) {
            val a = if (i >= BPP) src[row + i - BPP].toInt() and 0xFF else 0
            val b = if (above >= 0) src[above + i].toInt() and 0xFF else 0
            val c = if (above >= 0 && i >= BPP) src[above + i - BPP].toInt() and 0xFF else 0
            val p = a + b - c
            val pa = if (p > a) p - a else a - p
            val pb = if (p > b) p - b else b - p
            val pc = if (p > c) p - c else c - p
            val predictor = if (pa <= pb && pa <= pc) a else if (pb <= pc) b else c
            dst[at + i] = ((src[row + i].toInt() and 0xFF) - predictor).toByte()
        }
    }

    private fun chunk(out: ByteArray, at: Int, type: ByteArray, data: ByteArray, len: Int): Int {
        putInt(out, at, len)
        System.arraycopy(type, 0, out, at + 4, 4)
        System.arraycopy(data, 0, out, at + 8, len)
        crc.reset()
        crc.update(out, at + 4, 4 + len)
        putInt(out, at + 8 + len, crc.value.toInt())
        return at + 12 + len
    }

    private fun putInt(b: ByteArray, at: Int, v: Int) {
        b[at] = (v ushr 24).toByte(); b[at + 1] = (v ushr 16).toByte(); b[at + 2] = (v ushr 8).toByte(); b[at + 3] = v.toByte()
    }

    companion object {
        /** zlib level 2: level 1's speed with a smaller file on the flat areas games are made of. */
        const val DEFAULT_LEVEL = 2

        const val FILTER_NONE = 0
        const val FILTER_SUB = 1
        const val FILTER_UP = 2
        const val FILTER_PAETH = 4
        internal val FILTERS = intArrayOf(FILTER_NONE, FILTER_SUB, FILTER_UP, FILTER_PAETH)

        private const val BPP = 3
        private val SIGNATURE = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        private val IHDR = "IHDR".toByteArray(Charsets.US_ASCII)
        private val IDAT = "IDAT".toByteArray(Charsets.US_ASCII)
        private val IEND = "IEND".toByteArray(Charsets.US_ASCII)
    }
}
