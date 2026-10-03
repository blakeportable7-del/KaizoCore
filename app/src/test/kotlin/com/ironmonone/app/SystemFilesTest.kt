package com.ironmonone.app

import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * rc32 audit P2 #45: the import read the whole file into memory and copied it out, about 480 MB of heap for the
 * DSi NAND, and wrote it in place with no size check, so a cut-short or wrong file counted as present.
 */
class SystemFilesTest {
    private val dir: File = Files.createTempDirectory("sysfiles").toFile()

    @Test
    fun `a file the wrong size is refused and leaves nothing behind`() {
        val said = SystemFiles.importNow(dir, "dsi_nand.bin") { ByteArrayInputStream(ByteArray(1 shl 20)) }
        assertTrue("Nothing was changed" in said, said)
        assertFalse(File(dir, "dsi_nand.bin").exists())
        assertFalse(File(dir, "dsi_nand.bin.tmp").exists())
        assertFalse(SystemFiles.present(dir, "dsi_nand.bin"))
    }

    @Test
    fun `a good dump lands whole, and a NAND of 240 MB or with its footer is accepted by size`() {
        val bios = ByteArray(16 * 1024) { it.toByte() }
        val said = SystemFiles.importNow(dir, "bios7.bin") { ByteArrayInputStream(bios) }
        assertTrue(said.startsWith("bios7.bin imported (") && said.endsWith(" bytes). Takes effect on the next boot."), said)
        assertContentEquals(bios, File(dir, "bios7.bin").readBytes())
        assertFalse(File(dir, "bios7.bin.tmp").exists())
        assertTrue(SystemFiles.present(dir, "bios7.bin"))
        for (size in listOf(240L shl 20, (240L shl 20) + 64, 0xF580000L)) assertTrue(SystemFiles.sizeOk("dsi_nand.bin", size), "$size")
        for (size in listOf(1L shl 20, (239L shl 20), 0L)) assertFalse(SystemFiles.sizeOk("dsi_nand.bin", size), "$size")
        for (size in listOf(128L, 256L, 512L)) assertTrue(SystemFiles.sizeOk("firmware.bin", size * 1024), "$size KB")
        assertFalse(SystemFiles.sizeOk("firmware.bin", 100L * 1024))
    }

    @Test
    fun `a file cut short keeps the old one, and an old cut-short file is not present`() {
        File(dir, "bios9.bin").writeBytes(ByteArray(4096) { 7 })
        val breaking = object : InputStream() {
            var n = 0
            override fun read(): Int = if (n++ < 1000) 1 else throw java.io.IOException("cable pulled")
        }
        assertEquals("Could not read that file.", SystemFiles.importNow(dir, "bios9.bin") { breaking })
        assertContentEquals(ByteArray(4096) { 7 }, File(dir, "bios9.bin").readBytes(), "the old file is untouched")
        assertFalse(File(dir, "bios9.bin.tmp").exists())
        File(dir, "dsi_firmware.bin").writeBytes(ByteArray(1000))
        assertFalse(SystemFiles.present(dir, "dsi_firmware.bin"), "an import cut short by an older build is not taken as present")
    }

    @Test
    fun `Play streams the import instead of reading the picked file into memory`() {
        val play = File("src/main/kotlin/com/ironmonone/app/PlayScreen.kt").readText().replace("\r\n", "\n")
        assertFalse("openInputStream(uri)!!.use { it.readBytes() }" in play)
        assertTrue("scope.launch { status = SystemFiles.import(context.filesDir, name) { context.contentResolver.openInputStream(uri) } }" in play)
        assertTrue("systemFilePresent = { SystemFiles.present(context.filesDir, it) }" in play)
    }
}
