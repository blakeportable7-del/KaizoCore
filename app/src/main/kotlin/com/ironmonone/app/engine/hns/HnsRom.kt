package com.ironmonone.app.engine.hns

/**
 * A Heart & Soul ROM read and written through a [HnsLayout]: GBA addresses, struct fields (bitfields and signed fields
 * as layout.py describes them), text through the build's charmap, and new data placed in the 0xFF tail of the 32 MB
 * cartridge. tools/hns/layout_check.py is the Python reader this follows.
 */
class HnsRom(val bytes: ByteArray, val layout: HnsLayout) {
    private val base = layout.romBase
    /** 0x53 and 0x54 draw the PK and MN glyphs in the game's font, whatever charmap.txt lists first for them. */
    private val decode: Map<Int, String> = layout.charmap + mapOf(0x53 to "[PK]", 0x54 to "[MN]")
    private val encode: Map<Char, Int> = layout.charmap.entries
        .filter { it.value.length == 1 && it.key != 0xFF && it.key != 0x53 && it.key != 0x54 }
        .associate { it.value[0] to it.key }

    fun off(addr: Int): Int {
        val o = addr - base
        require(o >= 0 && o < bytes.size) { "address 0x%08X outside the ROM".format(addr) }
        return o
    }

    fun isRomPtr(p: Int): Boolean = p != 0 && p - base >= 0 && p - base < bytes.size

    fun u8(a: Int): Int = bytes[off(a)].toInt() and 0xFF
    fun u16(a: Int): Int = u8(a) or (u8(a + 1) shl 8)
    fun u32(a: Int): Int = u16(a) or (u16(a + 2) shl 16)
    fun read(a: Int, size: Int): Long {
        var v = 0L
        for (k in 0 until size) v = v or ((u8(a + k).toLong()) shl (8 * k))
        return v
    }

    fun w8(a: Int, v: Int) { bytes[off(a)] = v.toByte() }
    fun w16(a: Int, v: Int) { w8(a, v); w8(a + 1, v ushr 8) }
    fun w32(a: Int, v: Int) { w16(a, v); w16(a + 2, v ushr 16) }
    fun write(a: Int, size: Int, v: Long) { for (k in 0 until size) w8(a + k, (v ushr (8 * k)).toInt()) }

    /** Field [name] of [struct] at [rec], as the C compiler laid it out. */
    fun get(struct: String, rec: Int, name: String): Int = get(layout.struct(struct).f(name), rec)

    fun get(f: HnsLayout.Field, rec: Int): Int {
        var v = read(rec + f.offset, f.size)
        if (f.isBits) {
            v = (v ushr f.shift) and ((1L shl f.width) - 1)
            if (f.signed && (v and (1L shl (f.width - 1))) != 0L) v -= 1L shl f.width
        } else if (f.signed && (v and (1L shl (8 * f.size - 1))) != 0L) {
            v -= 1L shl (8 * f.size)
        }
        return v.toInt()
    }

    fun set(struct: String, rec: Int, name: String, value: Int) = set(layout.struct(struct).f(name), rec, value)

    fun set(f: HnsLayout.Field, rec: Int, value: Int) {
        if (f.isBits) {
            val mask = ((1L shl f.width) - 1) shl f.shift
            val old = read(rec + f.offset, f.size)
            val v = (old and mask.inv()) or ((value.toLong() shl f.shift) and mask)
            write(rec + f.offset, f.size, v)
        } else {
            write(rec + f.offset, f.size, value.toLong())
        }
    }

    /** Element [i] of array field [name]. */
    fun elem(struct: String, rec: Int, name: String, i: Int): Int {
        val f = layout.struct(struct).f(name)
        val es = f.size / f.count
        return read(rec + f.offset + i * es, es).toInt()
    }

    fun setElem(struct: String, rec: Int, name: String, i: Int, v: Int) {
        val f = layout.struct(struct).f(name)
        val es = f.size / f.count
        write(rec + f.offset + i * es, es, v.toLong())
    }

    fun text(a: Int, max: Int): String {
        val sb = StringBuilder()
        for (k in 0 until max) {
            val b = u8(a + k)
            if (b == 0xFF) break
            sb.append(decode[b] ?: "?")
        }
        return sb.toString()
    }

    fun inlineText(struct: String, rec: Int, name: String): String {
        val f = layout.struct(struct).f(name)
        return text(rec + f.offset, f.size)
    }

    /** [s] in the game's charmap, or null when a character has no code. */
    fun encodeText(s: String): ByteArray? {
        val out = ByteArray(s.length)
        for ((k, c) in s.withIndex()) out[k] = (encode[c] ?: return null).toByte()
        return out
    }

    /** Writes [s] into a fixed text field of [size] bytes: the text, its terminator, then zeros as the compiler pads. */
    fun writeFixedText(a: Int, size: Int, s: String): Boolean {
        val enc = encodeText(s) ?: return false
        if (enc.size + 1 > size) return false
        for (k in 0 until size) w8(a + k, when {
            k < enc.size -> enc[k].toInt()
            k == enc.size -> 0xFF
            else -> 0
        })
        return true
    }

    // ---- free space ----

    /** The first free byte of the 0xFF tail, word aligned, with a margin after the build's last byte. */
    private var freeNext: Int = run {
        var o = bytes.size - 1
        while (o >= 0 && bytes[o] == 0xFF.toByte()) o--
        val start = ((o + 1 + 0x100 + 15) / 16) * 16
        base + start
    }
    val freeStart: Int = freeNext

    /** Reserves [size] bytes of the tail, aligned to [align], all 0xFF before the write; returns the GBA address. */
    fun alloc(size: Int, align: Int = 4): Int {
        val a = ((freeNext + align - 1) / align) * align
        val o = a - base
        require(o + size <= bytes.size) { "the ROM has no room left for $size bytes" }
        for (k in 0 until size) require(bytes[o + k] == 0xFF.toByte()) { "free space at 0x%08X is not empty".format(a + k) }
        freeNext = a + size
        return a
    }

    fun freeUsed(): Int = freeNext - freeStart
}
