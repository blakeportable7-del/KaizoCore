package com.ironmonone.tracker

/**
 * Decodes a species' front battle sprite straight out of the loaded ROM: the
 * pic table entry points at GBA BIOS LZ77 data (64x64 4bpp tiles), the palette
 * table at a compressed 16-color BGR555 palette. Nothing is bundled with the
 * app, so randomized ROMs and every game version supply their own art.
 *
 * Table addresses were located empirically (tools/find_sprites.py: longest
 * 8-byte-entry runs whose pointers land on 0x800-byte LZ77 headers, then a
 * human eye confirmed Arcanine's front sprite in correct colors per game).
 * FireRed's table lands exactly on pret's gMonFrontPicTable.
 */
object SpriteDecoder {

    /** GBA BIOS LZ77 (type 0x10). Returns null on anything malformed. */
    fun lz77(src: ByteArray): ByteArray? {
        if (src.size < 4 || src[0].toInt() != 0x10) return null
        val size = (src.u8(1)) or (src.u8(2) shl 8) or (src.u8(3) shl 16)
        if (size !in 0x10..0x4000) return null
        val out = ByteArray(size)
        var o = 0
        var i = 4
        try {
            while (o < size) {
                val flags = src.u8(i); i++
                var bit = 0
                while (bit < 8 && o < size) {
                    if (flags and (0x80 shr bit) != 0) {
                        val b1 = src.u8(i); val b2 = src.u8(i + 1); i += 2
                        val len = (b1 shr 4) + 3
                        val disp = ((b1 and 0xF) shl 8) or b2
                        repeat(len) {
                            if (o < size) { out[o] = out[o - disp - 1]; o++ }
                        }
                    } else {
                        out[o] = src[i]; o++; i++
                    }
                    bit++
                }
            }
        } catch (_: IndexOutOfBoundsException) {
            return null
        }
        return out
    }

    /**
     * 64x64 ARGB pixels for [species], or null when the tables can't serve it.
     * Color 0 is transparent, everything else opaque - the classic sprite look.
     */
    fun frontSprite(memory: MemoryReader, map: GameMap, species: Int): IntArray? {
        if (map.frontPics == 0L || map.palettes == 0L) return null
        if (species !in 1 until map.spriteCount) return null

        fun tablePtr(table: Long): Long {
            val e = memory.read(table + species.toLong() * 8, 4)
            return if (e.size == 4) e.u32(0) else 0L
        }
        val picPtr = tablePtr(map.frontPics)
        val palPtr = tablePtr(map.palettes)
        if (picPtr !in 0x08000000L..0x09FFFFFFL || palPtr !in 0x08000000L..0x09FFFFFFL)
            return null

        // Compressed size is not stored in the entry we use, so read a window
        // big enough for the worst case. Measured across all 1283 Nat. Dex
        // species: the largest pic compresses to 0xAB0 and the largest palette
        // to 0x89, so 0xA00/0x80 would have silently dropped the biggest
        // sprites. These windows carry real slack over the measured maxima.
        val pic = lz77(memory.read(picPtr, 0xC00)) ?: return null
        val palRaw = lz77(memory.read(palPtr, 0x100)) ?: return null
        if (pic.size < 0x800 || palRaw.size < 32) return null

        val pal = IntArray(16)
        for (c in 0 until 16) {
            val v = palRaw.u16(c * 2)
            val r = (v and 31) shl 3
            val g = ((v shr 5) and 31) shl 3
            val b = ((v shr 10) and 31) shl 3
            pal[c] = if (c == 0) 0
            else (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }

        val px = IntArray(64 * 64)
        for (t in 0 until 64) {
            val tx = (t % 8) * 8
            val ty = (t / 8) * 8
            for (i in 0 until 32) {
                val b = pic.u8(t * 32 + i)
                val x = (i % 4) * 2
                val y = i / 4
                px[(ty + y) * 64 + tx + x] = pal[b and 0xF]
                px[(ty + y) * 64 + tx + x + 1] = pal[b shr 4]
            }
        }
        return px
    }
}
