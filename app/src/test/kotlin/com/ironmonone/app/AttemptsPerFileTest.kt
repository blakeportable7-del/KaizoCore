package com.ironmonone.app

import com.ironmonone.app.engine.Randomizers
import com.ironmonone.core.RomKind
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Attempts per game and settings file (2026-09-30, IronMON rules check R8, Blake's call), an attempt left before it
 * ended filed as ended (R13), and a randomized Nuzlocke counting no attempt.
 */
class AttemptsPerFileTest {
    private val filesDir: File = Files.createTempDirectory("attempts").toFile()
    private val store = PrepStore(filesDir)
    private val emerald = RomKind.EMERALD_U
    private val prepared = File(filesDir, "prep/prepared/emerald-u.gba").apply { parentFile.mkdirs(); writeBytes(ByteArray(256) { 7 }) }
    private fun settings(name: String) = File(filesDir, "prep/settings/$name").apply { parentFile.mkdirs(); writeBytes(name.toByteArray()) }
    private var seed = 0x100L
    private fun roll(settings: File, nuzlocke: Boolean = false) =
        RunStart.start(store, emerald, prepared, settings, seed = seed++, app = "t", prePass = null, secondPass = null,
            take = { fail("a seed was chosen") }, stop = {}, randomize = { dest, s -> dest.writeBytes(ByteArray(64) { 2 }); Randomizers.logFor(dest).writeText("log $s") },
            countAttempt = !nuzlocke)

    private fun history() = RunHistory(store.runHistoryFile(emerald)).all()

    @Test
    fun `each settings file counts its own attempts, and the game counts them all`() {
        val kaizo = settings("RSE Kaizo.rnqs"); val standard = settings("RSE Standard.rnqs")
        roll(kaizo); roll(kaizo); roll(kaizo)
        assertEquals(3, store.attempt(emerald.id), "the run in play is Kaizo's third")
        roll(standard)
        assertEquals(1, store.attempt(emerald.id), "Standard's first, not the game's fourth")
        assertEquals(3, store.attemptOf(emerald.id, kaizo.name))
        assertEquals(4, store.gameAttempts(emerald.id), "every run started on the game, for Your stats")
        roll(kaizo)
        assertEquals(4, store.attempt(emerald.id), "Kaizo carries on from its own count")
        assertEquals(0, store.attemptOf(RomKind.FIRERED_U_V11.id, "FRLG Kaizo.rnqs"), "a game first played now starts at 0")
    }

    @Test
    fun `a file with no count yet starts from the game's count as it stood when this came in`() {
        // An install from before: one count for the whole game, 57.
        File(filesDir, "prep/attempts/emerald-u.txt").apply { parentFile.mkdirs(); writeText("57") }
        store.saveLastRun(emerald.id, "RSE Kaizo.rnqs")
        assertEquals(57, store.attempt(emerald.id), "the run in play keeps its number")
        assertEquals(57, store.attemptOf(emerald.id, "RSE Standard.rnqs"), "every file starts from the current number")
        roll(settings("RSE Kaizo.rnqs"))
        assertEquals(58, store.attempt(emerald.id))
        roll(settings("RSE Standard.rnqs"))
        assertEquals(58, store.attempt(emerald.id), "Standard's own next, from the number as it stood")
        assertEquals(59, store.gameAttempts(emerald.id))
        assertEquals(57, store.attemptOf(emerald.id, "RSE Survival.rnqs"), "fixed when it came in, not floating with the game's count")
    }

    @Test
    fun `a randomized Nuzlocke counts no attempt and is no IronMON run`() {
        val kaizo = settings("RSE Kaizo.rnqs"); val fair = settings("Emerald Nuzlocke fair.rnqs")
        roll(kaizo); roll(kaizo)
        roll(fair, nuzlocke = true)
        assertTrue(store.lastRunNuzlocke())
        assertEquals(2, store.gameAttempts(emerald.id), "not counted")
        assertEquals(2, store.attemptOf(emerald.id, kaizo.name))
        roll(fair, nuzlocke = true)
        assertEquals(listOf(1, 2), history().map { it.attempt }, "the two Kaizo runs are filed as ended, the Nuzlockes are not")
        roll(kaizo)
        assertFalse(store.lastRunNuzlocke())
        assertEquals(3, store.attempt(emerald.id))
    }

    @Test
    fun `an attempt a new run replaces before it ended is filed as ended, once, and a filed one keeps its record`() {
        val kaizo = settings("RSE Kaizo.rnqs")
        roll(kaizo)
        val firstSeed = store.lastSeedText()
        assertTrue(history().isEmpty())
        roll(kaizo)
        val filed = history().single()
        assertEquals(1, filed.attempt); assertEquals(firstSeed, filed.seed); assertEquals("RSE Kaizo.rnqs", filed.ruleset)
        assertEquals(RunRecord.Outcome.ENDED, filed.outcome)
        // The second run ended the ordinary way: its record stays as the popup filed it.
        val lost = RunRecord(attempt = 2, seed = store.lastSeedText(), ruleset = kaizo.name, started = 0, ended = 1, playSeconds = 0,
            outcome = RunRecord.Outcome.LOST, badges = 3, lead = null, killer = null, trainer = "", location = "Route 110")
        RunHistory(store.runHistoryFile(emerald)).record(lost)
        roll(kaizo)
        assertEquals(listOf(RunRecord.Outcome.ENDED, RunRecord.Outcome.LOST), history().map { it.outcome })
        assertEquals(3, history()[1].badges)
    }

    @Test
    fun `what was kept under a number starts over for the run that takes it`() {
        val kaizo = settings("RSE Kaizo.rnqs"); val standard = settings("RSE Standard.rnqs")
        roll(kaizo)
        // Kaizo's attempt 1 played 90 seconds, used heals and checked a summary.
        val clock = File(filesDir, "prep/run-clock.txt")
        clock.writeText("${RunClock.key(emerald.id, 1)}=90\n")
        RunClock.load(clock)
        SummaryChecks.mark(1)
        PcHeals.add(1, -3)
        roll(standard)
        assertEquals(1, store.attempt(emerald.id), "Standard's attempt 1")
        assertEquals(0, RunClock.of(RunClock.key(emerald.id, 1)), "Standard's clock starts at 0")
        assertEquals(90L, RunClock.readSeconds(clock).values.sum(), "Kaizo's 90 seconds still count in Your stats")
        assertFalse(SummaryChecks.checked(1))
        assertEquals(if (TrackerOptions.pcHealsCountDownward) 10 else 0, PcHeals.count(1))
    }

    @Test
    fun `the Kaizo screen counts the file picked, and a Nuzlocke in play says so`() {
        val run = File("src/main/kotlin/com/ironmonone/app/RunScreen.kt").readText()
        assertTrue("store.attemptOf(it, selectedSettings?.name)" in run, "Start attempt N is the picked file's next")
        assertTrue("\"Attempt \${store.attemptOf(rom.id, selectedSettings?.name)}\"" in run)
        assertEquals("Continue the Nuzlocke", RunCopy.CONTINUE_NUZLOCKE)
        assertEquals("End the Nuzlocke on Emerald and start attempt 5 on Emerald, Kaizo?",
            RunCopy.confirmNewAttempt(RunCopy.EndingRun(0, "Emerald", nuzlocke = true), 5, "Emerald", "Kaizo"))
        val play = File("src/main/kotlin/com/ironmonone/app/PlayScreen.kt").readText()
        assertTrue("secondPassFor(context, store, k, settings), countAttempt = nuzlocke == null)" in play, "Play's NEW RUN in a Nuzlocke counts none")
        assertTrue("RunJob.randomize(context, prepared, settingsFile, seed, nuzlocke = true)" in File("src/main/kotlin/com/ironmonone/app/NuzlockeScreen.kt").readText())
    }

    @Test
    fun `the stream prints no attempt for a Nuzlocke`() {
        val snap = com.ironmonone.app.stream.StreamSnapshot.build(
            com.ironmonone.app.stream.StreamSnapshot.Run("Emerald", "GBA", 7, true, "abc", nuzlocke = true), null, null,
            com.ironmonone.app.stream.StreamSnapshot.Notes())
        assertEquals(false, snap["run"])
    }
}
