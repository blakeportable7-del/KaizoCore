package com.ironmonone.app

import com.ironmonone.app.engine.Randomizers
import com.ironmonone.core.RomKind
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * The player's files that were still rewritten in place, the failure SafeWrite was made to end (rc32 audit P2 #40, #65,
 * P3 #42, #71). A folder where the temp file goes stands in for a phone that refuses the write, as SafeWriteTest does.
 */
class InPlaceWritesTest {
    private val dir: File = Files.createTempDirectory("inplace").toFile()

    @BeforeTest fun settle() { DiskWriter.release(); DiskWriter.drain() }
    @AfterTest fun cleanup() { DiskWriter.drain(); KeptSave.reset(); dir.deleteRecursively() }

    private fun blocked(f: File) = File(f.parentFile, f.name + ".tmp").apply { mkdirs() }

    @Test
    fun `a DS past run that cannot be saved leaves the earlier ones on disk`() {
        val f = File(dir, "pastruns-Pokemon Black 2.tsv")
        val store = PastRunStore(f)
        fun mon(n: String) = PastRun.RunMon(1, n, 10, 300, "Normal", "", "Run Away", listOf("Tackle"))
        store.log(PastRun(1000, 60, mon("PATRAT"), mon("LILLIPUP"), "Route 19", 0, PastRun.NOWHERE))
        DiskWriter.drain()
        val before = f.readText()
        blocked(f)
        store.log(PastRun(2000, 90, mon("TEPIG"), mon("PURRLOIN"), "Route 20", 1, PastRun.PAST_LAB))
        DiskWriter.drain()
        assertEquals(before, f.readText(), "rewritten in place, a full phone emptied the whole log")
        assertEquals(2, store.totalRuns(), "the run is still shown")
    }

    @Test
    fun `tourney scores, tracker settings, Hidden Power, the summary check and the kept-save ids are written whole`() {
        val tourney = File(dir, "tourney.tsv")
        TourneyTracker(tourney).update("seed1", setOf(496))
        DiskWriter.drain()
        val scores = tourney.readText()
        blocked(tourney)
        TourneyTracker(tourney).update("seed1", setOf(496, 290), mapId = 3)
        DiskWriter.drain()
        assertEquals(scores, tourney.readText(), "tourney.tsv")

        val options = File(dir, "tracker-options.txt")
        try {
            TrackerOptions.load(options)
            TrackerOptions.save(); DiskWriter.drain()
            val saved = options.readText()
            blocked(options)
            TrackerOptions.showTimer = !TrackerOptions.showTimer; TrackerOptions.save(); DiskWriter.drain()
            assertEquals(saved, options.readText(), "tracker-options.txt")
            TrackerOptions.showTimer = !TrackerOptions.showTimer
        } finally { TrackerOptions.load(File(dir, "none.txt")) }

        val hp = File(dir, "hidden-power.txt")
        HiddenPowerTypes.load(hp)
        HiddenPowerTypes.next(7L); DiskWriter.drain()
        val types = hp.readText()
        blocked(hp)
        HiddenPowerTypes.next(7L); DiskWriter.drain()
        assertEquals(types, hp.readText(), "hidden-power.txt")

        val checked = File(dir, "summary-checked.txt")
        SummaryChecks.load(checked)
        SummaryChecks.mark(4); DiskWriter.drain()
        blocked(checked)
        SummaryChecks.mark(5); DiskWriter.drain()
        assertEquals("4\n", checked.readText(), "summary-checked.txt")

        assertFalse(KeptSave.seen(dir, "firered", 5, listOf(0x1111L)))
        DiskWriter.drain()
        val ids = KeptSave.file(dir, "firered")
        val kept = ids.readText()
        blocked(ids)
        KeptSave.seen(dir, "firered", 5, listOf(0x2222L)); DiskWriter.drain()
        assertEquals(kept, ids.readText(), "party-ids")
    }

    @Test
    fun `the extra passes' choices, the cloud link and the favourites are written whole`() {
        val choices = File(dir, "extra-passes.txt")
        ExtraPasses.Choices(choices).set("emerald-u", "RSE Kaizo.rnqs", "prePass", true)
        val saved = choices.readText()
        blocked(choices)
        ExtraPasses.Choices(choices).set("emerald-u", "RSE Kaizo.rnqs", "prePass", false)
        assertEquals(saved, choices.readText(), "extra-passes.txt")

        val link = CloudSync.Link("content://x/doc", "Google Drive", 1L, "fp")
        CloudSync.save(dir, link)
        blocked(File(dir, CloudSync.CONFIG))
        CloudSync.save(dir, link.copy(lastSync = 2L))
        assertEquals(link, CloudSync.load(dir), "cloudsync.txt")

        val store = PrepStore(dir)
        store.saveFavorites("emerald-u", "Mudkip")
        blocked(File(dir, "prep/favorites/emerald-u.txt"))
        store.saveFavorites("emerald-u", "Torchic")
        assertEquals("Mudkip", store.favoritesText("emerald-u"), "favorites")
    }

    @Test
    fun `a new run whose name or seed cannot be saved fails with a reason and the run before keeps its name`() {
        val store = PrepStore(dir)
        val kind = RomKind.EMERALD_U
        val prepared = File(dir, "prep/prepared/emerald-u.gba").apply { parentFile.mkdirs(); writeBytes(ByteArray(64) { 7 }) }
        val settings = File(dir, "prep/settings/RSE Kaizo.rnqs").apply { parentFile.mkdirs(); writeBytes(byteArrayOf(1)) }
        store.currentRunFor(kind).apply { parentFile.mkdirs(); writeBytes(ByteArray(64) { 1 }) }
        store.saveLastRun(kind.id, "Old.rnqs"); store.saveLastSeed(0x0dL)
        blocked(File(dir, "prep/lastrun.txt"))
        val e = assertFailsWith<RunSetupProblem> {
            RunStart.start(store, kind, prepared, settings, seed = 0x42L, app = "t", prePass = null, secondPass = null,
                take = { fail("a seed was chosen") }, stop = {}, randomize = { d, s -> d.writeBytes(ByteArray(64) { 2 }); Randomizers.logFor(d).writeText("log $s") })
        }
        assertEquals(RUN_NOT_SAVED, e.message)
        assertEquals(kind.id to "Old.rnqs", store.loadLastRun(), "lastrun.txt is as it was, never cut short")
        assertFalse(PrepStore.stampKnown(store.runIdentity()), "and no save state of the run before loads on what is in place")
        // Worded for both new-run buttons.
        assertEquals(RUN_NOT_SAVED, newRunFailureCopy(e))
        val job = File("src/main/kotlin/com/ironmonone/app/RunJob.kt").readText()
        assertTrue("e is RunSetupProblem && m.isNotBlank() -> m" in job)
    }

    /**
     * Every write of a file under prep/ or saves/ goes through SafeWrite or the background writer, or is on this list
     * with its reason (rc32 audit P2 #65, #96): a new in-place write of a player's file fails here.
     */
    @Test
    fun `no file the player keeps is rewritten in place`() {
        val allowed = mapOf(
            "PrepStore.kt" to listOf(
                "padForceFile.writeText(\"1\")",           // markers: the file's being there is the setting
                "nextRunOffFile.writeText(\"1\")",
                "speedFile.writeText(\"\$v\")",            // one number, rebuilt from the default
                "muteFile.writeText(\"1\")",
                "runErrorFile.writeText(message)",         // a diagnostic
                "tmp.writeBytes(bytes)",                    // a scratch copy, deleted at once
                "target.writeBytes(bytes)",                 // a new settings file, never over one already there
                "File(dir, \"state.bin\").writeBytes(it)",   // a saved attempt's files, in a folder that is new
                "File(dir, ATTEMPT_DONE).writeText(",
                "crcMemo.writeText(",                       // a cache, rebuilt from the files
            ),
            "StatMarks.kt" to listOf("writeText(\"\")"),   // clear(), owned by another change (P2 #91): empties, nothing to lose
            // A marker. The sign-in and importPatch's patch are written whole since RC35-NOTICED N #15.
            "RetroAchievements.kt" to listOf("f.writeText(\"1\")"),
            "StateSlots.kt" to listOf("lockFile.writeText(\"1\")"),   // a marker
        )
        val scanned = listOf("PrepStore.kt", "StatMarks.kt", "RunClock.kt", "TrackerOptions.kt", "PastRuns.kt", "DsExtras.kt",
            "ExtraPasses.kt", "CloudSync.kt", "KeptSave.kt", "HiddenPower.kt", "SummaryChecks.kt", "Theme.kt", "ThemePresets.kt",
            "TrackerBackground.kt", "CheatStore.kt", "RetroAchievements.kt", "StateSlots.kt", "PcHeals.kt", "RunHistory.kt")
        val write = Regex("""\.(writeText|writeBytes)\(""")
        for (name in scanned) {
            val lines = File("src/main/kotlin/com/ironmonone/app/$name").readLines()
            for (line in lines) {
                if (!write.containsMatchIn(line) || line.trimStart().startsWith("*") || line.trimStart().startsWith("//")) continue
                assertTrue(allowed[name].orEmpty().any { it in line }, "$name writes in place: ${line.trim()}")
            }
        }
        // And the ones the audit named, by what they call.
        fun src(name: String) = File("src/main/kotlin/com/ironmonone/app/$name").readText()
        assertTrue("SafeWrite.text(lastRunFile" in src("PrepStore.kt") && "SafeWrite.text(lastSeedFile" in src("PrepStore.kt"))
        assertTrue("DiskWriter.write(f, text())" in src("TrackerOptions.kt"))
        assertTrue("DiskWriter.write(f, list.joinToString" in src("PastRuns.kt"))
        assertTrue("DiskWriter.write(file, scores.joinToString" in src("DsExtras.kt"))
        assertTrue("SafeWrite.text(file, \"\$user\\n\$token\\n\")" in src("RetroAchievements.kt"), "the RetroAchievements sign-in")
        assertTrue("if (!SafeWrite.bytes(f, bytes)) return Result.failure(" in src("PrepStore.kt"), "importPatch")
    }

    /**
     * RC35-NOTICED N #4: the per-game settings and the pad layouts were written to a temp file and renamed, and when that
     * rename failed the file was deleted and renamed again: a kill in between left no file at all. Both go through
     * SafeWrite now, as the cheats already did.
     */
    @Test
    fun `game settings and pad layouts are written whole, and nothing deletes before a second rename`() {
        val games = File(dir, "games")
        val settings = GameSettings(games)
        settings.save("run", GameSettings.Values(speed = 4, muted = true))
        val f = File(games, "run.properties")
        blocked(f)
        settings.save("run", GameSettings.Values(speed = 8, muted = false))
        assertEquals(4, GameSettings(games).load("run").speed, "a write the phone refused leaves the settings as they were")
        assertEquals(true, GameSettings(games).load("run").muted)

        val layouts = LayoutStore(File(dir, "layouts"))
        val key = "gba-portrait"
        val moved = PadLayout.default(landscape = false, nds = false).copy(opacity = 0.5f)
        layouts.save(key, moved)
        blocked(File(dir, "layouts/$key.properties"))
        layouts.save(key, moved.copy(opacity = 0.9f))
        assertEquals(0.5f, LayoutStore(File(dir, "layouts")).load(key, false).opacity, "the layout as it was")

        val again = Regex("""\.delete\(\);\s*\w+\.renameTo\(""")
        for (name in listOf("GameSettings.kt", "PadLayout.kt", "CheatStore.kt")) {
            val text = File("src/main/kotlin/com/ironmonone/app/$name").readText()
            assertFalse(again.containsMatchIn(text), "$name deletes the file before a second rename")
        }
        assertTrue("SafeWrite.bytes(f, bytes)" in File("src/main/kotlin/com/ironmonone/app/GameSettings.kt").readText())
        assertTrue("SafeWrite.bytes(file(key), bytes)" in File("src/main/kotlin/com/ironmonone/app/PadLayout.kt").readText())
    }

    /** RC35-NOTICED N #15: the RetroAchievements sign-in and a Nat. Dex patch taken on Prepare were written in place. */
    @Test
    fun `the RetroAchievements sign-in and a patch taken on Prepare are written whole`() {
        val ra = File(dir, "prep/ra-login.txt")
        val store = RetroAchievements.Store(ra)
        store.save("blake", "token-1")
        blocked(ra)
        store.save("blake", "token-2")
        assertEquals("blake" to "token-1", RetroAchievements.Store(ra).load(), "a refused write keeps the sign-in that was there")

        // A BPS header for Emerald (U) with no actions: Bps.info reads its source checksum and its own.
        fun patch(fill: Int): ByteArray = java.io.ByteArrayOutputStream().apply {
            write("BPS1".toByteArray())
            fun v(x: Long) { var n = x; while (true) { val c = (n and 0x7f).toInt(); n = n shr 7; if (n == 0L) { write(0x80 or c); break }; write(c); n-- } }
            v(0x1000); v(0x1000); v(1); write(fill)
            fun le(x: Long) { for (k in 0 until 4) write(((x shr (8 * k)) and 0xFF).toInt()) }
            le(RomKind.EMERALD_U.expectedCrc); le(0)
            le(com.ironmonone.patch.Crc32.of(toByteArray()))
        }.toByteArray()
        val prep = PrepStore(dir)
        val first = prep.importPatch(patch(1)).getOrThrow()
        val kept = first.readBytes()
        blocked(first)
        val refused = prep.importPatch(patch(2))
        assertEquals(PATCH_NOT_SAVED, refused.exceptionOrNull()?.message, "said, as the import's other refusals are")
        assertTrue(kept.contentEquals(first.readBytes()), "the patch that was there is whole")
    }
}
