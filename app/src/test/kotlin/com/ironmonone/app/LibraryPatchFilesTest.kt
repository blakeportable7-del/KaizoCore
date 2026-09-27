package com.ironmonone.app

import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Patching goes file to file, so an xdelta (the format DS hacks ship in) works
 * from the library and the HACK tab. It used to go through the in-memory
 * Patcher.apply, which refuses xdelta outright, and read the whole ROM into
 * the heap (audit, 2026-09-27).
 */
class LibraryPatchFilesTest {

    private fun dsRom(): ByteArray {
        val r = ByteArray(0x1000)
        "POKEMON HG".forEachIndexed { i, c -> r[i] = c.code.toByte() }
        "IPKE".forEachIndexed { i, c -> r[0x0C + i] = c.code.toByte() }
        val crc = com.ironmonone.patch.DsHeader.crc16(r, 0, 0x15E)
        r[0x15E] = (crc and 0xFF).toByte(); r[0x15F] = ((crc shr 8) and 0xFF).toByte()
        for (i in 0x200 until r.size) r[i] = (i * 7).toByte()
        return r
    }

    private fun varint(v: Int): ByteArray {
        val out = ArrayList<Byte>()
        var x = v
        out.add((x and 0x7F).toByte()); x = x ushr 7
        while (x > 0) { out.add(0, ((x and 0x7F) or 0x80).toByte()); x = x ushr 7 }
        return out.toByteArray()
    }

    /** A real VCDIFF: copy the whole source, then add "ABC". Built from RFC 3284's default code table. */
    private fun xdelta(sourceLen: Int): ByteArray {
        val data = "ABC".toByteArray()
        val inst = byteArrayOf(19) + varint(sourceLen) + byteArrayOf(1) + varint(data.size) // COPY size-follows mode 0, ADD size-follows
        val addr = varint(0)                                                                 // copy from source offset 0
        val body = ByteArrayOutputStream().apply {
            write(varint(sourceLen + data.size))                                             // target window length
            write(0)                                                                          // delta indicator: nothing compressed
            write(varint(data.size)); write(varint(inst.size)); write(varint(addr.size))
            write(data); write(inst); write(addr)
        }.toByteArray()
        return ByteArrayOutputStream().apply {
            write(byteArrayOf(0xD6.toByte(), 0xC3.toByte(), 0xC4.toByte(), 0)); write(0)     // magic, no header extras
            write(1); write(varint(sourceLen)); write(varint(0))                              // VCD_SOURCE, segment length, position
            write(varint(body.size)); write(body)
        }.toByteArray()
    }

    @Test
    fun `an xdelta patch applies from the library and the result is a new game`() {
        val dir = Files.createTempDirectory("libx").toFile()
        val lib = LibraryStore(dir)
        val base = lib.import("heartgold-test.nds", dsRom())
        val patchFile = File(dir.parentFile, "hack-" + System.nanoTime() + ".xdelta").apply { writeBytes(xdelta(base.sizeBytes.toInt())) }
        val p = lib.importPatchFile("My Hack.xdelta", patchFile, declaredFor = base)
        assertTrue(p.matches(base), "a patch saved for this game is offered for it")

        var lastDone = -1L
        val made = lib.apply(base, p) { done, _ -> lastDone = done }

        assertContentEquals(dsRom() + "ABC".toByteArray(), made.file.readBytes())
        assertEquals("My Hack.nds", made.name)
        assertEquals(base.name, made.baseName)
        assertEquals(p.name, made.patchName)
        assertTrue(lastDone > 0, "progress was reported")
        assertContentEquals(dsRom(), base.file.readBytes(), "the original is untouched")
        assertTrue(dir.listFiles()!!.none { it.name.startsWith(".patching-") }, "no temp file left behind")
    }

    @Test
    fun `xdelta and vcdiff are recognised as patches`() {
        assertTrue(LibraryStore.looksLikePatch("Blaze Black 2.xdelta"))
        assertTrue(LibraryStore.looksLikePatch("hack.VCDIFF"))
        assertTrue(LibraryStore.looksLikePatch("hack.bps"))
    }
}
