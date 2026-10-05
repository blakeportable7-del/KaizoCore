package com.ironmonone.tracker

/**
 * Heart & Soul's own Pokemon pictures, out of the ROM (Blake, 2026-10-05: the tracker and the logs "use the game
 * images"). Each species' SpeciesInfo points at its front picture and its palettes; the pictures are compressed with
 * pokeemerald-expansion's "smol" format (tANS-coded copy instructions, [Smol]), the palettes are plain 16-colour
 * BGR555 (an LZ77 one is read too). Every picture a species' entry names is decoded the way the game decodes it.
 */
object HnsPics {
    /** MON_PIC_SIZE: one 64x64 frame, 4 bits a pixel. */
    private const val FRAME = 0x800

    private fun rom(p: Long) = p in 0x08000000L..0x09FFFFFFL

    /** A smol or LZ77 stream at [address], read through [memory]; null where it does not decode. */
    fun decompress(memory: MemoryReader, address: Long): ByteArray? {
        if (!rom(address)) return null
        val head = memory.read(address, 8)
        if (head.size < 8) return null
        val h0 = head.u32(0)
        val mode = (h0 and 0xF).toInt()
        if (mode == 0) {
            if (head.u8(0) != 0x10) return null
            val size = head.u8(1) or (head.u8(2) shl 8) or (head.u8(3) shl 16)
            if (size <= 0 || size > Smol.MAX_SIZE) return null
            // Worst case an LZ77 stream is 9/8 of its output plus the header.
            return Smol.lz77(memory.read(address, 4 + size + size / 8 + 16))
        }
        val size = (((h0 shr 4) and 0x3FFF) * 4).toInt()
        if (size <= 0 || size > Smol.MAX_SIZE) return null
        // A smol stream is never longer than its output plus its tables; read that much and let the decoder bound it.
        return runCatching { Smol.decode(memory.read(address, size * 2 + 64)) }.getOrNull()
    }

    /** Sixteen ARGB colours from a palette at [address] (raw BGR555, or compressed); colour 0 is transparent. */
    fun palette(memory: MemoryReader, address: Long): IntArray? {
        if (!rom(address)) return null
        val raw = memory.read(address, 32).takeIf { it.size == 32 } ?: return null
        val compressed = raw.u8(0) == 0x10 || (raw.u32(0) and 0xF).toInt() in 1..6
        val bytes = if (compressed) decompress(memory, address)?.takeIf { it.size >= 32 } ?: raw else raw
        return IntArray(16) { i -> if (i == 0) 0 else argb555(bytes.u16(i * 2)) }
    }

    /** BGR555 to opaque ARGB. */
    fun argb555(v: Int): Int {
        val r = (v and 0x1F) * 255 / 31; val g = ((v shr 5) and 0x1F) * 255 / 31; val b = ((v shr 10) and 0x1F) * 255 / 31
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    /** 4bpp tiles (8x8, row by row, 8 tiles a row for a 64-wide picture) to ARGB, the first 64x64 frame. */
    fun tilesToArgb(tiles: ByteArray, colors: IntArray, width: Int = 64, height: Int = 64): IntArray {
        val out = IntArray(width * height)
        val tilesPerRow = width / 8
        for (t in 0 until (width / 8) * (height / 8)) {
            val tx = (t % tilesPerRow) * 8; val ty = (t / tilesPerRow) * 8
            for (y in 0 until 8) for (x in 0 until 8) {
                val i = t * 32 + y * 4 + x / 2
                if (i >= tiles.size) continue
                val b = tiles[i].toInt() and 0xFF
                out[(ty + y) * width + tx + x] = colors[if (x and 1 == 1) b shr 4 else b and 0xF]
            }
        }
        return out
    }

    /**
     * [species]' front picture as the game draws it: female where it has its own female picture and [female], with the
     * shiny palette where [shiny]. Null when the entry or its data do not decode.
     */
    fun front(memory: MemoryReader, species: Int, shiny: Boolean = false, female: Boolean = false): Gen3Pictures.Picture? {
        if (species !in 1 until HnsLayout.NUM_SPECIES + 1) return null
        val SI = HnsLayout.SpeciesInfo
        val rec = memory.read(HnsLayout.gSpeciesInfo + species.toLong() * SI.SIZE, SI.SIZE).takeIf { it.size == SI.SIZE } ?: return null
        val femalePic = SI.frontPicFemale.u32(rec).takeIf { female && rom(it) }
        val pic = femalePic ?: SI.frontPic.u32(rec)
        val palAddr = when {
            femalePic != null -> (if (shiny) SI.shinyPaletteFemale else SI.paletteFemale).u32(rec).takeIf(::rom)
            else -> null
        } ?: (if (shiny) SI.shinyPalette else SI.palette).u32(rec)
        val tiles = decompress(memory, pic)?.takeIf { it.size >= FRAME } ?: return null
        val colors = palette(memory, palAddr) ?: return null
        return Gen3Pictures.Picture(64, 64, tilesToArgb(tiles, colors))
    }
}

/**
 * pokeemerald-expansion's "smol" decompression (tools/compresSmol, src/decompress.c, MIT), ported from the tool's own
 * reference decoder (compressAlgo.cpp: readRawDataVecs, decodeNibbles, decodeBytesShort): an 8-byte header (mode,
 * image size, symbol and instruction counts, the tANS start state, the bitstream length), the 16-symbol frequency
 * tables of whichever of the two streams is tANS-coded, the bitstream, then the streams kept plain. The instructions
 * ("lo" bytes) copy runs of earlier u16 words or take new ones from the symbol stream. Any read past the data, or a
 * stream that does not come out at its declared size, throws.
 */
object Smol {
    /** The largest output a header can declare (14 bits of 4-byte units). */
    const val MAX_SIZE = 0x3FFF * 4
    private const val TABLE = 64

    class Corrupt(message: String) : IllegalArgumentException(message)

    private fun isLo(mode: Int) = mode == 4 || mode == 5 || mode == 6
    private fun isSym(mode: Int) = mode == 2 || mode == 3 || mode == 5 || mode == 6
    private fun isDelta(mode: Int) = mode == 3 || mode == 6

    /** A decoding table: per state its symbol, y and k. */
    private class Table(val symbol: IntArray, val y: IntArray, val k: IntArray)

    private fun unpack(w0: Long, w1: Long, w2: Long): IntArray {
        val out = IntArray(16)
        var f15 = 0L
        listOf(w0, w1, w2).forEachIndexed { i, w ->
            for (j in 0 until 5) out[i * 5 + j] = ((w shr (6 * j)) and 0x3F).toInt()
            f15 += ((w shr 30) and 3) shl (2 * i)
        }
        out[15] = f15.toInt()
        return out
    }

    private fun table(freqs: IntArray): Table {
        if (freqs.sum() != TABLE) throw Corrupt("frequencies add to ${freqs.sum()}, not $TABLE")
        val s = IntArray(TABLE); val y = IntArray(TABLE); val k = IntArray(TABLE)
        var col = 0
        for (sym in 0 until 16) for (j in 0 until freqs[sym]) {
            s[col] = sym; y[col] = freqs[sym] + j
            var kk = 0
            while ((y[col] shl kk) < TABLE) kk++
            k[col] = kk
            col++
        }
        return Table(s, y, k)
    }

    /** Decodes the smol stream that starts at [data][0] (its header included). */
    fun decode(data: ByteArray): ByteArray {
        fun word(o: Int): Long { if (o + 4 > data.size) throw Corrupt("data ends at $o"); return data.u32(o) }
        val h0 = word(0); val h1 = word(4)
        val mode = (h0 and 0xF).toInt()
        val size = (((h0 shr 4) and 0x3FFF) * 4).toInt()
        val symSize = ((h0 shr 18) and 0x3FFF).toInt()
        var state = (h1 and 0x3F).toInt()
        val bitsSize = ((h1 shr 6) and 0x1FFF).toInt()
        val loSize = ((h1 shr 19) and 0x1FFF).toInt()
        if (mode !in 1..6) throw Corrupt("mode $mode is not a smol picture")
        var p = 8
        if (mode == 1) {
            val syms = IntArray(symSize) { i -> if (p + 2 * i + 2 > data.size) throw Corrupt("symbols end early") else data.u16(p + 2 * i) }
            val lo = ByteArray(loSize) { i -> data.getOrNull(p + symSize * 2 + i) ?: throw Corrupt("instructions end early") }
            return instructions(lo, syms, size)
        }
        val lo = isLo(mode); val sym = isSym(mode)
        val loTable = if (lo) table(unpack(word(p), word(p + 4), word(p + 8))).also { p += 12 } else null
        val symTable = if (sym) table(unpack(word(p), word(p + 4), word(p + 8))).also { p += 12 } else null
        val bitsStart = p
        p += 4 * bitsSize
        if (p > data.size) throw Corrupt("bitstream ends early")
        var bit = 0
        fun bits(n: Int): Int {
            var v = 0
            for (j in 0 until n) {
                if (bit >= bitsSize * 32) throw Corrupt("bitstream overrun")
                val w = data.u32(bitsStart + (bit ushr 5) * 4)
                v = v or ((((w ushr (bit and 31)) and 1L).toInt()) shl j)
                bit++
            }
            return v
        }
        fun nibbles(t: Table, n: Int, last: Boolean): IntArray {
            val out = IntArray(n)
            for (i in 0 until n) {
                if (state !in 0 until TABLE) throw Corrupt("tANS state $state")
                out[i] = t.symbol[state]
                if (i + 1 == n && last) break
                state = (t.y[state] shl t.k[state]) + bits(t.k[state]) - TABLE
            }
            return out
        }
        var loVec = ByteArray(loSize)
        var symVec = IntArray(symSize)
        if (lo) {
            val nb = nibbles(loTable!!, loSize * 2, !sym)
            loVec = ByteArray(loSize) { i -> (nb[2 * i] or (nb[2 * i + 1] shl 4)).toByte() }
        }
        if (sym) {
            val nb = nibbles(symTable!!, symSize * 4, true)
            if (isDelta(mode)) { var prev = 0; for (i in nb.indices) { nb[i] = (nb[i] + prev) and 0xF; prev = nb[i] } }
            symVec = IntArray(symSize) { i -> nb[4 * i] or (nb[4 * i + 1] shl 4) or (nb[4 * i + 2] shl 8) or (nb[4 * i + 3] shl 12) }
        } else {
            symVec = IntArray(symSize) { i -> if (p + 2 * i + 2 > data.size) throw Corrupt("symbols end early") else data.u16(p + 2 * i) }
            p += symSize * 2
        }
        if (!lo) loVec = ByteArray(loSize) { i -> data.getOrNull(p + i) ?: throw Corrupt("instructions end early") }
        return instructions(loVec, symVec, size)
    }

    /** compressAlgo.cpp decodeBytesShort: each instruction copies a run of earlier words or takes new ones. */
    private fun instructions(lo: ByteArray, syms: IntArray, size: Int): ByteArray {
        val out = IntArray(size / 2)
        var n = 0; var li = 0; var si = 0
        fun put(v: Int) { if (n >= out.size) throw Corrupt("output past its size $size"); out[n++] = v }
        fun next(): Int = (lo.getOrNull(li++) ?: throw Corrupt("instructions end early")).toInt() and 0xFF
        while (li < lo.size) {
            var b = next(); var length = b and 0x7F
            if (b and 0x80 != 0) length += next() shl 7
            b = next(); var offset = b and 0x7F
            if (b and 0x80 != 0) offset += next() shl 7
            if (length != 0) {
                put(syms.getOrNull(si++) ?: throw Corrupt("symbols run out"))
                if (offset <= 0 || offset > n) throw Corrupt("copy from $offset back at $n")
                repeat(length) { put(out[n - offset]) }
            } else {
                repeat(offset) { put(syms.getOrNull(si++) ?: throw Corrupt("symbols run out")) }
            }
        }
        if (n * 2 != size) throw Corrupt("decoded ${n * 2} bytes, the header says $size")
        val bytes = ByteArray(size)
        for (i in 0 until n) { bytes[2 * i] = out[i].toByte(); bytes[2 * i + 1] = (out[i] shr 8).toByte() }
        return bytes
    }

    /** GBA BIOS LZ77 (type 0x10), the data starting at its 4-byte header. */
    fun lz77(src: ByteArray): ByteArray? {
        if (src.size < 4 || src.u8(0) != 0x10) return null
        val size = src.u8(1) or (src.u8(2) shl 8) or (src.u8(3) shl 16)
        val out = ByteArray(size)
        var o = 0; var p = 4
        while (o < size) {
            if (p >= src.size) return null
            val flags = src.u8(p++)
            for (b in 0 until 8) {
                if (o >= size) break
                if (flags and (0x80 shr b) != 0) {
                    if (p + 1 >= src.size) return null
                    val v = (src.u8(p) shl 8) or src.u8(p + 1); p += 2
                    val n = (v shr 12) + 3; val d = (v and 0xFFF) + 1
                    if (d > o) return null
                    repeat(n) { if (o < size) { out[o] = out[o - d]; o++ } }
                } else {
                    if (p >= src.size) return null
                    out[o++] = src[p++]
                }
            }
        }
        return out
    }
}
