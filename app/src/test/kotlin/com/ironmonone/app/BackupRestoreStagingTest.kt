package com.ironmonone.app

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

/**
 * rc33 audit P1: a restore wrote each file as it read it, so a zip that was not a KaizoCore backup, or one that broke
 * part way, left the phone half restored while the message said nothing had changed. It now restores all or nothing.
 */
class BackupRestoreStagingTest {
    private fun zip(entries: List<Pair<String, String>>, marker: Boolean, cutAt: Int? = null): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z ->
            if (marker) { z.putNextEntry(ZipEntry("KAIZOCORE-BACKUP.txt")); z.write("x".toByteArray()); z.closeEntry() }
            for ((name, text) in entries) { z.putNextEntry(ZipEntry(name)); z.write(text.toByteArray()); z.closeEntry() }
        }
        val b = out.toByteArray()
        return if (cutAt != null) b.copyOf(cutAt) else b
    }

    @Test
    fun `a zip that is not a KaizoCore backup changes nothing`() {
        val dir = Files.createTempDirectory("restore").toFile()
        val save = File(dir, "saves/emerald.sav").apply { parentFile.mkdirs(); writeText("mine") }
        assertEquals(-1, Backup.read(dir, ByteArrayInputStream(zip(listOf("saves/emerald.sav" to "someone else's"), marker = false))))
        assertEquals("mine", save.readText())
        assertFalse(File(dir, Backup.STAGING).exists())
    }

    @Test
    fun `a backup that breaks part way changes nothing either`() {
        val dir = Files.createTempDirectory("restore").toFile()
        val save = File(dir, "saves/emerald.sav").apply { parentFile.mkdirs(); writeText("mine") }
        val big = "z".repeat(50_000)
        val whole = zip(listOf("saves/emerald.sav" to "restored", "saves/firered.srm" to big), marker = true)
        assertFailsWith<Exception> { Backup.read(dir, ByteArrayInputStream(whole.copyOf(whole.size / 2))) }
        assertEquals("mine", save.readText())
        assertFalse(File(dir, Backup.STAGING).exists())
    }

    /** rc33 audit P1: the app's copies in memory were written back over a restore before the restart the prompt asked for. */
    @Test
    fun `both restores restart the app at once`() {
        val about = File("src/main/kotlin/com/ironmonone/app/AboutScreen.kt").readText().replace("\r\n", "\n")
        kotlin.test.assertTrue("if (n != null && n >= 0) { needsRestart = true; restartApp(context) }" in about, "from a file")
        kotlin.test.assertTrue("linkedToRestore = false; restartApp(context) }" in about, "from the cloud")
    }

    @Test
    fun `a whole backup is put in place`() {
        val dir = Files.createTempDirectory("restore").toFile()
        File(dir, "saves/emerald.sav").apply { parentFile.mkdirs(); writeText("mine") }
        assertEquals(1, Backup.read(dir, ByteArrayInputStream(zip(listOf("saves/emerald.sav" to "restored"), marker = true))))
        assertEquals("restored", File(dir, "saves/emerald.sav").readText())
        assertFalse(File(dir, Backup.STAGING).exists())
        assertFalse(Backup.admits(Backup.STAGING + "/saves/emerald.sav"), "the staging folder is never backed up")
    }
}
