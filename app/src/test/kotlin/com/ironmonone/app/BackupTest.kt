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

    /** Blake, 2026-09-30: the live run's randomizer log stays out of a backup until the run is over. */
    @Test
    fun `the live run's log stays out of the backup until the run has ended`() {
        val src = Files.createTempDirectory("live").toFile()
        put(src, "prep/lastrun.txt", "emerald-u\nRSE Kaizo.rnqs\n"); put(src, "prep/lastseed.txt", "00000000000000aa")
        put(src, "prep/runs/current.gba", "GAME"); put(src, "prep/runs/current.gba.log", "THE SEED'S ANSWERS")
        put(src, "prep/runs/current.species.tsv", "species"); put(src, "prep/runs/previous.gba.log", "the last run's")
        val live = Backup.collect(src)
        assertFalse("prep/runs/current.gba.log" in live, "the seed's answers, while the run is live")
        assertTrue("prep/runs/current.gba" in live && "prep/runs/current.species.tsv" in live, "the game and its species file stay")
        assertTrue("prep/runs/previous.gba.log" in live, "a replaced run is over")
        // The run ended: its record, with the game file's own time as its start.
        val started = File(src, "prep/runs/current.gba").lastModified()
        RunHistory(File(src, "prep/runhistory-emerald-u.tsv")).record(RunRecord(attempt = 1, seed = "00000000000000aa", ruleset = "RSE Kaizo.rnqs",
            started = started, ended = started + 1, playSeconds = 0, outcome = RunRecord.Outcome.LOST, badges = 1, lead = null, killer = null,
            trainer = "", location = ""))
        assertTrue("prep/runs/current.gba.log" in Backup.collect(src), "over, the log goes in")
        // A record of the same seed from another run of it (a run code) is not this run's end.
        File(src, "prep/runs/current.gba").setLastModified(started + 60_000)
        assertFalse("prep/runs/current.gba.log" in Backup.collect(src))
        // A randomized Nuzlocke's stays out.
        put(src, "prep/lastrun.txt", "emerald-u\nNuzlocke fair.rnqs\nnuzlocke=true\n")
        assertFalse("prep/runs/current.gba.log" in Backup.collect(src))
        // And what collect leaves out is what write leaves out.
        val names = java.util.zip.ZipInputStream(ByteArrayOutputStream().also { Backup.write(src, it) }.toByteArray().inputStream()).use { z ->
            generateSequence { z.nextEntry?.name }.toList()
        }
        assertFalse("prep/runs/current.gba.log" in names)
    }

    /** IronMON rules check R1 (2026-09-30): a restore puts a run back in time, so the run's record says so. */
    @Test
    fun `a restore that holds a run is a restore on its record, and nothing opens at the backup's moment by itself`() {
        val src = Files.createTempDirectory("src").toFile()
        put(src, "prep/lastseed.txt", "00000000000000aa"); put(src, "saves/autosave.bin", "state"); put(src, "saves/autosave.left", "1")
        put(src, "saves/lib/abc/autosave.left", "1")
        val bytes = ByteArrayOutputStream().also { Backup.write(src, it) }.toByteArray()
        val dst = Files.createTempDirectory("dst").toFile()
        put(dst, "saves/autosave.left", "1")
        assertTrue(Backup.read(dst, bytes.inputStream()) > 0)
        val log = RunEvents(File(dst, "prep/integrity.txt")).entries()
        assertEquals(1, log.size); assertEquals(RunEvents.Kind.RESTORE, log[0].kind); assertEquals("backup", log[0].slot)
        assertFalse(File(dst, "saves/autosave.left").exists(), "the next open offers the auto-save instead of dropping into it")
        assertFalse(File(dst, "saves/lib/abc/autosave.left").exists())
        assertTrue(File(dst, "saves/autosave.bin").isFile, "the state itself is restored")
        // No run in the backup, nothing to write on a record.
        val plain = Files.createTempDirectory("plain").toFile()
        put(plain, "prep/keys.txt", "A=52")
        val dst2 = Files.createTempDirectory("dst2").toFile()
        Backup.read(dst2, ByteArrayOutputStream().also { Backup.write(plain, it) }.toByteArray().inputStream())
        assertFalse(File(dst2, "prep/integrity.txt").exists())
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
