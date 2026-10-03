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

    /**
     * rc32 audit P3 #19: after Retry the battle, the loss stays filed with the run's seed and start while the run goes
     * on, and the next backup took that as the run's end and carried the seed's answers.
     */
    @Test
    fun `after Retry the battle the live run's log stays out until the run ends again`() {
        val src = Files.createTempDirectory("retry").toFile()
        put(src, "prep/lastrun.txt", "emerald-u\nRSE Kaizo.rnqs\n"); put(src, "prep/lastseed.txt", "00000000000000aa")
        put(src, "prep/runs/current.gba", "GAME"); put(src, "prep/runs/current.gba.log", "THE SEED'S ANSWERS")
        val started = File(src, "prep/runs/current.gba").lastModified()
        val history = RunHistory(File(src, "prep/runhistory-emerald-u.tsv"))
        fun lost(ended: Long) = RunRecord(attempt = 1, seed = "00000000000000aa", ruleset = "RSE Kaizo.rnqs", started = started, ended = ended,
            playSeconds = 0, outcome = RunRecord.Outcome.LOST, badges = 1, lead = null, killer = null, trainer = "", location = "")
        history.record(lost(started + 1))
        assertTrue("prep/runs/current.gba.log" in Backup.collect(src), "over, the log goes in")
        RunEvents(File(src, "prep/integrity.txt")).add(RunEvents.Kind.RETRY, "battle start", at = started + 2)
        assertFalse("prep/runs/current.gba.log" in Backup.collect(src), "Retry reopened the run: it is live again")
        history.record(lost(started + 3))
        assertTrue("prep/runs/current.gba.log" in Backup.collect(src), "lost again after the retry: over")
    }

    /**
     * rc32 audit P2 #12: a restore only added and overwrote, so a backup of another run kept the phone's run's log beside
     * the restored game, its record of loads, and its notes the backup's run had not written.
     */
    @Test
    fun `a backup of another run takes the phone's run's log, notes and loads away, and one of the same run keeps them`() {
        val backup = Files.createTempDirectory("runA").toFile()
        put(backup, "prep/lastrun.txt", "emerald-u\nRSE Kaizo.rnqs\n"); put(backup, "prep/lastseed.txt", "000000000000000a")
        put(backup, "prep/runs/current.gba", "A GAME")
        val zip = ByteArrayOutputStream().also { Backup.write(backup, it) }.toByteArray()
        val phone = Files.createTempDirectory("runB").toFile()
        put(phone, "prep/lastrun.txt", "emerald-u\nRSE Kaizo.rnqs\n"); put(phone, "prep/lastseed.txt", "000000000000000b")
        put(phone, "prep/runs/current.gba", "B GAME"); put(phone, "prep/runs/current.gba.log", "B's answers")
        put(phone, "prep/marks.txt", "25:1,0,0,0,0,0"); put(phone, "prep/keys.txt", "A=52")
        RunEvents(File(phone, "prep/integrity.txt")).add(RunEvents.Kind.LOAD, "slot 1")
        assertTrue(Backup.read(phone, zip.inputStream()) > 0)
        assertEquals("A GAME", File(phone, "prep/runs/current.gba").readText())
        assertFalse(File(phone, "prep/runs/current.gba.log").exists(), "B's randomizer log beside A's game")
        assertFalse(File(phone, "prep/marks.txt").exists(), "B's notes")
        assertEquals(listOf(RunEvents.Kind.RESTORE), RunEvents(File(phone, "prep/integrity.txt")).entries().map { it.kind }, "B's loads on A's record")
        assertEquals("A=52", File(phone, "prep/keys.txt").readText(), "nothing that is not the run's goes")
        // The same run, restored: its log and notes are its own and stay.
        val same = Files.createTempDirectory("runA2").toFile()
        put(same, "prep/lastrun.txt", "emerald-u\nRSE Kaizo.rnqs\n"); put(same, "prep/lastseed.txt", "000000000000000a")
        put(same, "prep/runs/current.gba", "A GAME"); put(same, "prep/runs/current.gba.log", "A's answers"); put(same, "prep/notes.txt", "25:fast")
        assertTrue(Backup.read(same, zip.inputStream()) > 0)
        assertEquals("A's answers", File(same, "prep/runs/current.gba.log").readText())
        assertEquals("25:fast", File(same, "prep/notes.txt").readText())
    }

    /** rc32 audit P2 #2 and #66: what a backup carries of whole games, and what it must not. */
    @Test
    fun `the replaced run's game and an attempt copy left unfinished stay out of the backup`() {
        val d = Files.createTempDirectory("games").toFile()
        put(d, "prep/runs/current.nds", "RUN"); put(d, "prep/runs/previous.nds", "OLD RUN"); put(d, "prep/runs/previous.nds.log", "old log")
        put(d, "attempts/black2-u-attempt3-ab/run.nds", "COPY"); put(d, "attempts/black2-u-attempt3-ab/attempt.txt", "game=black2-u")
        put(d, "attempts/black2-u-attempt4-cd/run.nds", "HALF A COP")
        val got = Backup.collect(d)
        assertTrue("prep/runs/current.nds" in got && "prep/runs/previous.nds.log" in got)
        assertFalse("prep/runs/previous.nds" in got, "nothing reads the replaced run's game")
        assertFalse(Backup.admits("prep/runs/previous.gba"), "nor is it restored from an older backup")
        assertTrue("attempts/black2-u-attempt3-ab/run.nds" in got, "a saved attempt keeps its game")
        assertFalse(got.any { it.startsWith("attempts/black2-u-attempt4-cd/") }, "a copy with no attempt.txt never finished")
    }

    /**
     * rc32 audit P2 #2, #94: the Backup and Cloud sync cards said a backup held only the current run's game, and nothing
     * said the pictures go up with it. Both pickers say it too.
     */
    @Test
    fun `the cards and the pickers say what a backup and the cloud copy hold`() {
        assertTrue("each saved attempt" in BackupCopy.CARD && "512 MB" in BackupCopy.CARD, "the attempts' games and their size")
        assertTrue("pictures you chose for the tracker and for Play as your Pokemon" in BackupCopy.CARD)
        assertTrue("saved attempts" in CloudSyncCopy.WHAT_GOES_UP && "pictures" in CloudSyncCopy.WHAT_GOES_UP)
        assertTrue("Backups and cloud sync include" in TrackerBackground.IN_BACKUPS)
        assertTrue("Backups and cloud sync include" in SpriteIsMeCopy.IN_BACKUPS)
        for (line in listOf(BackupCopy.CARD, BackupCopy.NO_ROOM, CloudSyncCopy.WHAT_GOES_UP, TrackerBackground.IN_BACKUPS, SpriteIsMeCopy.IN_BACKUPS,
                RestoreGate.NEW_RUN, RestoreGate.SYNCING, RestoreGate.WAITING, CloudSync.RESTORING, CheatStore.SAVE_FAILED, StateSlots.LOCK_FAILED, RetroAchievements.HARDCORE_FAILED,
                SaveTrouble.NOTES_FAILED, ATTEMPT_NO_ROOM, RUN_NOT_SAVED, SpriteIsMeCopy.SHEETS_NOT_SAVED))
            assertFalse('\u2014' in line || '\u2013' in line || " - " in line, "no dashes: $line")
        fun src(name: String) = File("src/main/kotlin/com/ironmonone/app/$name").readText().replace("\r\n", "\n")
        assertTrue("Text(BackupCopy.CARD," in src("AboutScreen.kt"))
        assertTrue("CloudSyncCopy.WHAT_GOES_UP" in src("AboutScreen.kt"))
        assertTrue("TrackerBackground.IN_BACKUPS" in src("ThemePicker.kt"))
        assertTrue("Note(pix, SpriteIsMeCopy.IN_BACKUPS)" in src("SpriteIsMeUi.kt"))
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
