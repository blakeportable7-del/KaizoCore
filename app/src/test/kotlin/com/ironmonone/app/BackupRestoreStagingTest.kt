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
import kotlin.test.assertTrue

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

    /**
     * rc32 audit P2 #13: ZipInputStream ends with no error where a cut leaves the next entry's header short, so a backup
     * cut there put in the run's name and seed without its game and saves, and called it restored. The zip's end record
     * is checked now, with the entries counted.
     */
    @Test
    fun `a backup cut between two entries changes nothing`() {
        val out = ByteArrayOutputStream()
        var cut = 0
        ZipOutputStream(out).use { z ->
            z.putNextEntry(ZipEntry("KAIZOCORE-BACKUP.txt")); z.write("x".toByteArray()); z.closeEntry()
            z.putNextEntry(ZipEntry("prep/lastrun.txt")); z.write("emerald-u\nRSE Kaizo.rnqs\n".toByteArray()); z.closeEntry()
            z.putNextEntry(ZipEntry("prep/lastseed.txt")); z.write("00000000000000aa".toByteArray()); z.closeEntry()
            z.flush(); cut = out.size()
            z.putNextEntry(ZipEntry("saves/emerald-u.srm")); z.write(ByteArray(40_000) { it.toByte() }); z.closeEntry()
        }
        for (at in listOf(cut, cut + 12, cut + 29)) {
            val dir = Files.createTempDirectory("restore").toFile()
            val name = File(dir, "prep/lastrun.txt").apply { parentFile.mkdirs(); writeText("firered-u-v11\nFRLG Kaizo.rnqs\n") }
            assertFailsWith<Exception>("cut at $at") { Backup.read(dir, ByteArrayInputStream(out.toByteArray().copyOf(at))) }
            assertEquals("firered-u-v11\nFRLG Kaizo.rnqs\n", name.readText(), "cut at $at")
            assertFalse(File(dir, "prep/lastseed.txt").exists())
            assertFalse(File(dir, Backup.STAGING).exists())
        }
        // Whole, the same zip goes in: the end record is what made the difference.
        val dir = Files.createTempDirectory("restore").toFile()
        assertEquals(3, Backup.read(dir, ByteArrayInputStream(out.toByteArray())))
    }

    /** rc32 audit P3 #20: an entry inflated with no limit, so a damaged or crafted one filled the phone before it failed. */
    @Test
    fun `an entry bigger than any file a backup holds is refused before it fills the phone`() {
        val dir = Files.createTempDirectory("restore").toFile()
        val save = File(dir, "saves/x.bin").apply { parentFile.mkdirs(); writeText("mine") }
        val big = zip(listOf("saves/x.bin" to "z".repeat(2 * 1024 * 1024)), marker = true)
        assertFailsWith<Exception> { Backup.read(dir, ByteArrayInputStream(big), entryCap = 1024 * 1024) }
        assertEquals("mine", save.readText())
        assertFalse(File(dir, Backup.STAGING).exists())
        // The floor: a phone already short of space is told so, and nothing changes.
        assertEquals(Backup.NO_ROOM, Backup.read(dir, ByteArrayInputStream(zip(listOf("saves/x.bin" to "new"), marker = true)), freeFloor = Long.MAX_VALUE))
        assertEquals("mine", save.readText())
        assertTrue(Backup.ENTRY_CAP >= 512L * 1024 * 1024, "a DS game fits")
        // A staging folder a kill left is swept at launch.
        File(dir, Backup.STAGING + "/saves/y.bin").apply { parentFile.mkdirs(); writeText("left") }
        Backup.sweepStaging(dir)
        assertFalse(File(dir, Backup.STAGING).exists())
        // At launch, beside a saved attempt a kill cut short (rc32 audit P2 #66).
        val main = File("src/main/kotlin/com/ironmonone/app/MainActivity.kt").readText()
        assertTrue("PrepStore(filesDir).sweepUnfinishedAttempts()" in main && "Backup.sweepStaging(filesDir)" in main)
    }

    /**
     * rc32 audit P2 #9: the restore and its restart ended whatever else was writing: a background cloud sync over the
     * only cloud copy, a new run half installed. Both restores open the gate first and let it go when nothing changed.
     */
    @Test
    fun `a restore waits for a cloud sync and a new run, and holds both off until the restart`() {
        val about = File("src/main/kotlin/com/ironmonone/app/AboutScreen.kt").readText().replace("\r\n", "\n")
        assertEquals(2, Regex(Regex.escape("RestoreGate.open()?.let { why ->")).findAll(about).count(), "both Restore buttons")
        assertEquals(2, Regex(Regex.escape("kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { RestoreGate.settle() }")).findAll(about).count())
        assertEquals(2, Regex(Regex.escape("if (CloudSync.syncing) {")).findAll(about).count(), "a wait for a sync says why")
        val backup = File("src/main/kotlin/com/ironmonone/app/Backup.kt").readText().replace("\r\n", "\n")
        assertTrue("DiskWriter.hold()" in backup.substringAfter("fun settle("), "the background writer is held")
        assertTrue("DiskWriter.drain()" in backup.substringAfter("fun collect(filesDir: File)").substringBefore("fun liveRunLogs"),
            "and a backup waits for it")
        val file = about.substringAfter("RestoreGate.open()?.let { why -> backupStatus")
        assertTrue(file.indexOf("RestoreGate.settle()") < file.indexOf("Backup.read(context.filesDir, it)"), "settled before the backup is read")
        assertTrue(file.indexOf("Backup.read(context.filesDir, it)") < file.indexOf("restartApp(context)"))
        val cloud = about.substringAfter("RestoreGate.open()?.let { why -> cloudStatus")
        assertTrue(cloud.indexOf("RestoreGate.settle()") < cloud.indexOf("CloudSync.restore(context)"))
        // A new run being made refuses the restore; the gate holds new runs off until it is let go.
        assertTrue(NewRunGuard.claim())
        try { assertEquals(RestoreGate.NEW_RUN, RestoreGate.open()) } finally { NewRunGuard.release() }
        assertEquals(null, RestoreGate.open())
        try {
            assertFalse(NewRunGuard.claim(), "no new run starts while the files go back")
            assertEquals(null, RestoreGate.settle(syncWaitMs = 1000))
            // Held: a save handed to the background writer now is not written over what the restore puts back.
            val dir = Files.createTempDirectory("gate").toFile()
            val f = File(dir, "theme.txt")
            DiskWriter.write(f, "from before the restore")
            assertFalse(DiskWriter.drain(300))
            assertFalse(f.exists())
        } finally { RestoreGate.close() }
        assertTrue(NewRunGuard.claim(), "let go, new runs start again"); NewRunGuard.release()
        assertTrue(DiskWriter.drain(), "and the held save is written")
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
