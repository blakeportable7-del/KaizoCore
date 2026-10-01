package com.ironmonone.app

import com.ironmonone.patch.DsHeader
import com.ironmonone.patch.GbaHeader

/**
 * Small files with headers that check out, for the library tests (2026-09-30, UX audit P0-11): a game of any
 * language or size can be made without a real dump. [salt] makes two of the same game differ, so their checksums do.
 */
internal object LibraryRoms {
    fun ds(title: String, code: String, salt: Int = 0): ByteArray {
        val r = ByteArray(0x1000)
        title.forEachIndexed { i, c -> r[i] = c.code.toByte() }
        code.forEachIndexed { i, c -> r[0x0C + i] = c.code.toByte() }
        val crc = DsHeader.crc16(r, 0, 0x15E)
        r[0x15E] = (crc and 0xFF).toByte(); r[0x15F] = ((crc shr 8) and 0xFF).toByte()
        r[0x800] = salt.toByte()
        return r
    }

    fun gba(title: String, code: String, version: Int = 0, size: Int = 0x400, salt: Int = 0): ByteArray {
        val r = ByteArray(size)
        title.forEachIndexed { i, c -> r[GbaHeader.TITLE + i] = c.code.toByte() }
        code.forEachIndexed { i, c -> r[GbaHeader.CODE + i] = c.code.toByte() }
        "01".forEachIndexed { i, c -> r[GbaHeader.MAKER + i] = c.code.toByte() }
        r[GbaHeader.VERSION] = version.toByte()
        r[GbaHeader.CHECK] = GbaHeader.checksum(r).toByte()
        r[0x300] = (version + 1 + salt).toByte()
        return r
    }

    fun gb(title: String, cgb: Int = 0xC0): ByteArray {
        val r = ByteArray(0x8000)
        intArrayOf(0xCE, 0xED, 0x66, 0x66, 0xCC, 0x0D).forEachIndexed { i, b -> r[0x104 + i] = b.toByte() }
        title.forEachIndexed { i, c -> r[0x134 + i] = c.code.toByte() }
        r[0x143] = cgb.toByte()
        return r
    }

    /** A 7-Zip archive's first bytes, then filler: not a game, and not a zip. */
    fun sevenZip(): ByteArray = byteArrayOf(0x37, 0x7A, 0xBC.toByte(), 0xAF.toByte(), 0x27, 0x1C, 0, 4) + ByteArray(64) { (it * 3).toByte() }

    /** A RAR archive's first bytes, then filler. */
    fun rar(): ByteArray = byteArrayOf(0x52, 0x61, 0x72, 0x21, 0x1A, 0x07, 0, 0) + ByteArray(64) { (it * 5).toByte() }
}
