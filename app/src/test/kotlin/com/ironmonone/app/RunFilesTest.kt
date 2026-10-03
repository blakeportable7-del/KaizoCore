package com.ironmonone.app

import com.ironmonone.app.engine.Randomizers
import com.ironmonone.core.RomKind
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

/** The run's big files: the prepared builds, saved attempts, replaced runs and the record of a run left early. */
class RunFilesTest {
    private val dir: File = Files.createTempDirectory("runfiles").toFile()

    @AfterTest fun cleanup() { DiskWriter.drain(); RunProgress.forget(); dir.deleteRecursively() }

    /**
     * rc32 audit P2 #63: a build made from My games was stored without its checksum, so the Kaizo screen's list hashed
     * the whole of it (512 MB for Faster B2W2) on the main thread. The checksum the build already proved is kept, and a
     * store made before it finds it too.
     */
    @Test
    fun `a build stored with its checksum is listed without being read again, by a store made before it`() {
        // The Kaizo screen's store, its cache filled before the build is made.
        File(dir, "prep/prepared/emerald-u.gba").apply { parentFile.mkdirs(); writeBytes(ByteArray(64) { 1 }) }
        val early = PrepStore(dir)
        assertTrue(early.listPrepared().none { it.first == RomKind.EMERALD_U }, "not a real Emerald")
        // Bytes that are not Black 2: only the remembered checksum can say this is it.
        val built = File(dir, "cache-black2").apply { writeBytes(ByteArray(4096) { 3 }) }
        PrepStore(dir).savePrepared(RomKind.BLACK2_U, built, crc = RomKind.BLACK2_U.expectedCrc)
        assertTrue(PrepStore(dir).listPrepared().any { it.first == RomKind.BLACK2_U }, "a new store")
        assertTrue(early.listPrepared().any { it.first == RomKind.BLACK2_U }, "and the store made before it")
        // The callers hand over the checksum they hold.
        fun src(name: String) = File("src/main/kotlin/com/ironmonone/app/$name").readText()
        assertTrue("store.savePrepared(outKind, tmp, built)" in src("PrepRun.kt") && "store.savePrepared(outKind, out, outCrc)" in src("PrepRun.kt"))
        assertTrue("store.savePrepared(kind, file, crc)" in src("PrepRun.kt"))
        assertTrue("PrepRun.run(context, store, file, kind, chosenOption, progress, crc = id.crc)" in src("PrepareScreen.kt"))
        assertTrue("store.savePrepared(out, tmp, crc)" in src("GrowthPatch.kt"))
    }

    /**
     * rc32 audit P2 #64: a pick that had gone (Patched versions, after a second pick failed) was stored with the
     * game's prepared copy deleted first, and trying again could never work.
     */
    @Test
    fun `a pick that has gone never costs the game's prepared copy, and a new copy replaces the old whole`() {
        val store = PrepStore(dir)
        val dest = store.preparedFile(RomKind.EMERALD_U).apply { parentFile.mkdirs(); writeBytes(ByteArray(64) { 5 }) }
        assertFailsWith<java.io.IOException> { store.savePrepared(RomKind.EMERALD_U, File(dir, "prep-gone")) }
        assertContentEquals(ByteArray(64) { 5 }, dest.readBytes(), "the prepared copy stays")
        val pick = File(dir, "prep-123").apply { writeBytes(ByteArray(64) { 6 }) }
        store.savePrepared(RomKind.EMERALD_U, pick)
        assertContentEquals(ByteArray(64) { 6 }, dest.readBytes())
        assertFalse(pick.exists()); assertFalse(File(dest.parentFile, dest.name + ".tmp").exists())
        // A failed pick deletes its own files, never the earlier pick's; Prepare checks the copy is there.
        val prep = File("src/main/kotlin/com/ironmonone/app/PrepareScreen.kt").readText()
        assertFalse("startsWith(\"prep-\")" in prep, "every prep- file in the cache went")
        assertTrue("runCatching { tmp.delete(); java.io.File(tmp.parentFile, tmp.name + \".d\").deleteRecursively() }" in prep)
        assertTrue("if (!file.isFile) { say(" in prep)
    }

    /**
     * RC35-NOTICED N #19: MainActivity swept the cache's prep- and import- files in every onCreate, and an activity made
     * again in a live process (opened from the launcher while a new run was being made, RunJob outliving the activity)
     * deleted the files the running job was writing. The sweep is a process's first activity's only.
     */
    @Test
    fun `the cache's picks are swept once a process, not under a running job`() {
        val cache = File(dir, "cache").apply { mkdirs() }
        val left = File(cache, "prep-111").apply { writeBytes(ByteArray(8)) }
        val import = File(cache, "import-222").apply { mkdirs(); File(this, "a.gba").writeBytes(ByteArray(8)) }
        val other = File(cache, "keep.txt").apply { writeText("not a pick") }
        val once = java.util.concurrent.atomic.AtomicBoolean(false)
        assertTrue(CacheSweep.once(cache, once), "the process's first activity sweeps")
        assertFalse(left.exists()); assertFalse(import.exists()); assertTrue(other.exists())
        val running = File(cache, "prep-333").apply { writeBytes(ByteArray(8)) }   // a new run being made
        assertFalse(CacheSweep.once(cache, once), "an activity made again in the same process does not")
        assertTrue(running.exists())
        val main = File("src/main/kotlin/com/ironmonone/app/MainActivity.kt").readText()
        assertTrue("CacheSweep.once(cacheDir)" in main)
        assertFalse("cacheDir.listFiles()" in main.substringAfter("override fun onCreate(").substringBefore("override fun "), "onCreate sweeps through CacheSweep only")
    }

    /** rc32 audit P3 #57: a patch that threw part way left its output in the cache, up to 512 MB. */
    @Test
    fun `a patch that throws part way leaves nothing behind`() {
        val tmp = File(dir, "prep-patched-black2-u.nds")
        assertFailsWith<IllegalStateException> { patchInto(tmp) { tmp.writeBytes(ByteArray(4096)); error("the xdelta patch ends early") } }
        assertFalse(tmp.exists())
        assertEquals(7L, patchInto(tmp) { 7L }, "a patch that works hands back its checksum")
        fun src(name: String) = File("src/main/kotlin/com/ironmonone/app/$name").readText()
        assertTrue("patchInto(tmp) { Patcher.applyFiles(patchFile, file, tmp" in src("PrepRun.kt"))
        assertTrue("patchInto(tmp) { Patcher.applyFiles(patch, base, tmp" in src("GrowthPatch.kt"))
    }

    /**
     * rc32 audit P2 #66: a Save this attempt that failed (a DS game on a full phone) left its folder with a part copy,
     * which nothing lists or deletes and every backup carried.
     */
    @Test
    fun `Save this attempt asks for room first, leaves nothing when it cannot finish, and the unfinished go at launch`() {
        val store = PrepStore(dir)
        val kind = RomKind.EMERALD_U
        store.saveLastRun(kind.id, "RSE Kaizo.rnqs"); store.saveLastSeed(0x5L)
        val seed = store.lastSeedText()
        val attempts = File(dir, "attempts")
        // No run on disk: no folder either.
        assertFalse(store.saveAttempt(kind, 1, seed, null))
        assertTrue(attempts.listFiles().orEmpty().isEmpty(), "an empty folder was left")
        // A phone without room for the game: refused before anything is copied.
        store.currentRunFor(kind).apply { parentFile.mkdirs(); writeBytes(ByteArray(64) { 1 }) }
        store.freeBytes = { 100L }
        assertFalse(store.saveAttempt(kind, 1, seed, null))
        assertTrue(attempts.listFiles().orEmpty().isEmpty(), "a part copy was left")
        store.freeBytes = { Long.MAX_VALUE }
        assertTrue(store.saveAttempt(kind, 1, seed, byteArrayOf(1)))
        // A copy a kill cut short has no attempt.txt: swept at launch, and the finished one stays.
        val cut = File(attempts, "emerald-u-attempt2-x").apply { mkdirs(); File(this, "run.gba").writeBytes(ByteArray(10)) }
        store.sweepUnfinishedAttempts()
        assertFalse(cut.exists())
        assertEquals(1, attempts.listFiles().orEmpty().count { File(it, ATTEMPT_DONE).isFile })
    }

    /**
     * rc32 audit P2 #67: every replaced run stayed as previous.<ext>, read by nothing, and a run of the other console
     * stayed as current.<ext>: up to 512 MB each on DS, in every backup.
     */
    @Test
    fun `a new run leaves no replaced run, and no run of the other console`() {
        val store = PrepStore(dir)
        fun roll(kind: RomKind, seed: Long) {
            val prepared = File(dir, "prep/prepared/${kind.id}.${kind.fileExtension}").apply { parentFile.mkdirs(); writeBytes(ByteArray(64) { 7 }) }
            val settings = File(dir, "prep/settings/Kaizo.rnqs").apply { parentFile.mkdirs(); writeBytes(byteArrayOf(1)) }
            RunStart.start(store, kind, prepared, settings, seed = seed, app = "t", prePass = null, secondPass = null,
                take = { fail("a seed was chosen") }, stop = {}, randomize = { d, s -> d.writeBytes(ByteArray(64) { s.toByte() }); Randomizers.logFor(d).writeText("log $s") })
        }
        roll(RomKind.PLATINUM_U, 1); roll(RomKind.PLATINUM_U, 2)
        val runs = File(dir, "prep/runs")
        assertFalse(store.previousRunFor(RomKind.PLATINUM_U).exists(), "the replaced run's game")
        assertFalse(Randomizers.logFor(store.previousRunFor(RomKind.PLATINUM_U)).exists(), "and its log")
        roll(RomKind.EMERALD_U, 3)
        assertFalse(File(runs, "current.nds").exists(), "the DS run, after a GBA one")
        assertFalse(File(runs, "current.nds.log").exists())
        assertTrue(store.currentRunFor(RomKind.EMERALD_U).isFile && Randomizers.logFor(store.currentRunFor(RomKind.EMERALD_U)).isFile)
        assertEquals(emptyList(), PrepStore.staleRunFiles(runs, "gba"))
    }

    /**
     * rc32 audit P3 #58: a run left by a new one before it ended was filed with 0 badges and no lead, whatever it had
     * reached, so Your stats' best run and the death card's Best never saw it.
     */
    @Test
    fun `a run replaced before it ended is filed with how far Play saw it get`() {
        val store = PrepStore(dir)
        val kind = RomKind.EMERALD_U
        val prepared = File(dir, "prep/prepared/emerald-u.gba").apply { parentFile.mkdirs(); writeBytes(ByteArray(64) { 7 }) }
        val settings = File(dir, "prep/settings/RSE Kaizo.rnqs").apply { parentFile.mkdirs(); writeBytes(byteArrayOf(1)) }
        fun roll(seed: Long) = RunStart.start(store, kind, prepared, settings, seed = seed, app = "t", prePass = null, secondPass = null,
            take = { fail("a seed was chosen") }, stop = {}, randomize = { d, s -> d.writeBytes(ByteArray(64) { 2 }); Randomizers.logFor(d).writeText("log $s") })
        roll(0x10L)
        val session = GameSession.forRun(store.currentRunFor(kind), kind)
        val lead = RunRecord.Mon(258, "MUDKIP", 31)
        RunProgress.note(store, session, 5, lead, "Route 119")
        RunProgress.note(store, session, 3, lead, "Route 119")   // a state from before two gyms: the most stays
        roll(0x11L)
        val filed = RunHistory(store.runHistoryFile(kind)).all().single()
        assertEquals(RunRecord.Outcome.ENDED, filed.outcome)
        assertEquals(5, filed.badges)
        assertEquals(lead, filed.lead)
        assertEquals("Route 119", filed.location)
        assertFalse(RunProgress.file(dir).exists(), "the new run starts with none")
        // Every poll loop feeds it, through the call each already makes.
        val kept = File("src/main/kotlin/com/ironmonone/app/KeptSave.kt").readText()
        assertEquals(2, Regex(Regex.escape("RunProgress.observe(store, session, s)")).findAll(kept).count())
    }
}
