package com.ironmonone.app

import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BackupTest {

    private fun put(root: File, rel: String, text: String) {
        val f = File(root, rel); f.parentFile.mkdirs(); f.writeText(text)
    }

    @Test
    fun `takes saves and settings, leaves ROMs and patches behind`() {
        val d = Files.createTempDirectory("files").toFile()
        put(d, "saves/state1.bin", "s1"); put(d, "saves/state1.png", "png"); put(d, "saves/lib/lib-1/state2.bin", "s2")
        put(d, "saves/firered-u-v11.srm", "srm"); put(d, "prep/marks.txt", "m"); put(d, "prep/games/run.properties", "speed=2")
        put(d, "prep/runs/current.gba", "run"); put(d, "prep/settings/FRLG Kaizo.rnqs", "preset")
        put(d, "prep/library/Emerald.gba", "ROM"); put(d, "prep/library/Emerald.gba.meta", "meta")
        put(d, "prep/library/patches/hack.bps", "patch"); put(d, "prep/prepared/firered.gba", "base")
        put(d, "prep/patches/natdex.bps", "big"); put(d, "prep/tmp/x", "junk"); put(d, "saves/state3.bin.tmp", "half")
        val got = Backup.collect(d)
        assertTrue("saves/state1.bin" in got && "saves/lib/lib-1/state2.bin" in got && "prep/marks.txt" in got)
        assertTrue("prep/games/run.properties" in got && "prep/runs/current.gba" in got && "prep/settings/FRLG Kaizo.rnqs" in got)
        assertFalse(got.any { it.contains("library/Emerald") || it.contains("library/patches") || it.contains("prepared") || it.contains("prep/patches") })
        assertFalse(got.any { it.endsWith(".tmp") || it.contains("tmp/") })
    }

    @Test
    fun `round-trips through a zip and restores only admitted paths`() {
        val src = Files.createTempDirectory("src").toFile()
        put(src, "saves/state1.bin", "hello"); put(src, "prep/keys.txt", "A=52"); put(src, "prep/library/rom.gba", "ROM")
        val bytes = ByteArrayOutputStream().also { Backup.write(src, it) }.toByteArray()
        val dst = Files.createTempDirectory("dst").toFile()
        assertEquals(2, Backup.read(dst, bytes.inputStream()))
        assertEquals("hello", File(dst, "saves/state1.bin").readText())
        assertEquals("A=52", File(dst, "prep/keys.txt").readText())
        assertFalse(File(dst, "prep/library/rom.gba").exists())
        // A zip that is not ours is refused, and a path that escapes is dropped.
        val foreign = ByteArrayOutputStream().also { b ->
            ZipOutputStream(b).use { z -> z.putNextEntry(ZipEntry("saves/x.bin")); z.write(1); z.closeEntry() }
        }.toByteArray()
        assertEquals(-1, Backup.read(dst, foreign.inputStream()))
        assertFalse(Backup.admits("../etc/passwd")); assertFalse(Backup.admits("saves/../../x")); assertFalse(Backup.admits("/saves/x"))
        assertTrue(Backup.suggestedName().startsWith("KaizoCore-backup-") && Backup.suggestedName().endsWith(".zip"))
    }

    @Test
    fun `zip import pulls out ROMs and patches and ignores the rest`() {
        val bytes = ByteArrayOutputStream().also { b ->
            ZipOutputStream(b).use { z ->
                for ((n, c) in listOf("README.txt" to "hi", "game/POKEMON.gba" to "GBA!", "hack.ips" to "PATCH", "art.png" to "png", "__MACOSX/._hack.ips" to "junk")) {
                    z.putNextEntry(ZipEntry(n)); z.write(c.toByteArray()); z.closeEntry()
                }
            }
        }.toByteArray()
        assertTrue(ZipImport.isZip("stuff.zip", bytes)); assertTrue(ZipImport.isZip("stuff.bin", bytes))
        assertFalse(ZipImport.isZip("rom.gba", "not a zip".toByteArray()))
        val got = ZipImport.extract(bytes)
        assertEquals(listOf("POKEMON.gba", "hack.ips"), got.map { it.first })
        assertEquals("GBA!", String(got[0].second))
    }
}
