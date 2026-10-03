package com.ironmonone.app

import com.ironmonone.app.engine.Randomizers
import com.ironmonone.core.RomKind
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * IronMON rules check (2026-09-30): R4, a run's record says what its official file ran without; R7, a run code's seed
 * already in this game's history is said before building, and the run's record says it came from a code.
 */
class RunVariantAndCodeTest {
    private val presets = File("src/main/assets/presets")
    private val bundled: Map<String, ByteArray> = presets.listFiles()!!.filter { it.name.endsWith(".rnqs") }.associate { it.name to it.readBytes() }
    private fun official(name: String) = bundled.getValue(name)

    @Test
    fun `what an official file ran without is named, and nothing is said when it ran as its rules have it`() {
        val emerald = RomKind.EMERALD_U
        assertEquals("50% levels", ExtraPasses.variant(emerald, "RSE Kaizo.rnqs", official("RSE Kaizo.rnqs"), bundled, prePassTaken = false, part2Taken = false))
        assertNull(ExtraPasses.variant(emerald, "RSE Kaizo.rnqs", official("RSE Kaizo.rnqs"), bundled, prePassTaken = true, part2Taken = false))
        assertNull(ExtraPasses.variant(emerald, "RSE Standard.rnqs", official("RSE Standard.rnqs"), bundled, prePassTaken = false, part2Taken = false),
            "Standard calls for no 60% levels")
        assertEquals("50% levels, no Smart AI", ExtraPasses.variant(emerald, "RSE Super Kaizo.rnqs", official("RSE Super Kaizo.rnqs"), bundled, false, false))
        assertNull(ExtraPasses.variant(RomKind.EMERALD_SMARTAI, "RSE Super Kaizo.rnqs", official("RSE Super Kaizo.rnqs"), bundled, true, false))
        assertEquals("+6% levels", ExtraPasses.variant(RomKind.EMERALD_FASTER, "RSE Standard.rnqs", official("RSE Standard.rnqs"), bundled, false, false),
            "Faster Emerald 1.3.2 carries its own 6%")
        assertNull(ExtraPasses.variant(RomKind.EMERALD_FASTER, "RSE Kaizo.rnqs", official("RSE Kaizo.rnqs"), bundled, false, false), "which is the 60% modes' own")
        assertEquals("no PART 2", ExtraPasses.variant(RomKind.RED_U, "RBY Kaizo.rnqs", official("RBY Kaizo.rnqs"), bundled, false, part2Taken = false))
        assertNull(ExtraPasses.variant(RomKind.RED_U, "RBY Kaizo.rnqs", official("RBY Kaizo.rnqs"), bundled, false, part2Taken = true))
        assertNull(ExtraPasses.variant(RomKind.RED_PF, "RBY Kaizo.rnqs", official("RBY Kaizo.rnqs"), bundled, false, false), "the patch is the other official way")
        assertNull(ExtraPasses.variant(emerald, "RSE Kaizo (mine).rnqs", byteArrayOf(1, 2), bundled, false, false), "a custom file says custom instead")
    }

    @Test
    fun `a mode the 60% levels are not for, run with them on, says +6% levels on its record`() {
        // rc32 audit P2 #20: Standard and Ultimate with the switch on read as plain official runs at about x1.59.
        assertEquals("+6% levels", ExtraPasses.variant(RomKind.EMERALD_U, "RSE Standard.rnqs", official("RSE Standard.rnqs"), bundled, prePassTaken = true, part2Taken = false))
        assertEquals("+6% levels", ExtraPasses.variant(RomKind.EMERALD_U, "RSE Ultimate.rnqs", official("RSE Ultimate.rnqs"), bundled, prePassTaken = true, part2Taken = false))
        assertEquals("+6% levels", ExtraPasses.variant(RomKind.CRYSTAL_U, "GSC Ultimate.rnqs", official("GSC Ultimate.rnqs"), bundled, prePassTaken = true, part2Taken = false))
        assertNull(ExtraPasses.variant(RomKind.CRYSTAL_U, "GSC Kaizo.rnqs", official("GSC Kaizo.rnqs"), bundled, prePassTaken = true, part2Taken = false), "Kaizo calls for it")
        assertNull(ExtraPasses.variant(RomKind.EMERALD_U, "RSE Standard.rnqs", official("RSE Standard.rnqs"), bundled, prePassTaken = false, part2Taken = false))
        // The switch's line says what the mode's rules are, not "as the official settings do".
        val standard = ExtraPasses.prePassLine(RomKind.EMERALD_U, "standard", official = true)
        assertTrue("Standard's rules use the mode's +50% only" in standard && "+6% levels" in standard, standard)
        assertFalse("as the official settings do" in standard, standard)
        assertTrue("as the official settings do" in ExtraPasses.prePassLine(RomKind.EMERALD_U, "kaizo", official = true))
        assertTrue("Gold, Silver and Crystal" in ExtraPasses.prePassLine(RomKind.CRYSTAL_U, "survival", official = true))
        val custom = ExtraPasses.prePassLine(RomKind.EMERALD_U, null, official = false)
        assertTrue("custom or edited" in custom && "record" !in custom, custom)
        for (t in listOf(standard, custom, ExtraPasses.prePassLine(RomKind.CRYSTAL_U, "kaizo", true))) assertFalse('—' in t || '–' in t || " - " in t, t)
    }

    @Test
    fun `the record keeps it, and the shared line, the card and Your stats say it`() {
        val r = RunRecord(attempt = 7, seed = "aa", ruleset = "RSE Kaizo.rnqs", started = 0, ended = 1, playSeconds = 0,
            outcome = RunRecord.Outcome.LOST, badges = 2, lead = null, killer = null, trainer = "", location = "",
            variant = "50% levels", fromCode = true)
        val back = RunRecord.decode(r.encode())!!
        assertEquals("50% levels", back.variant); assertTrue(back.fromCode)
        val old = RunRecord.decode(r.encode().split('\t').take(17).joinToString("\t"))!!
        assertEquals("", old.variant); assertFalse(old.fromCode, "a line from before reads as neither")
        assertTrue("Attempt 7, Emerald (RSE Kaizo, 50% levels, from a run code): lost." in runSummaryLine(r, "Emerald"), runSummaryLine(r, "Emerald"))
        assertTrue(DeathCard("Emerald", r, null, false).statsText().endsWith(", 50% levels, from a run code"))
        val stats = CareerStats.compute(CareerStats.Inputs(mapOf(RomKind.EMERALD_U.id to listOf(r, r.copy(attempt = 8, seed = "bb", variant = ""))),
            emptyMap(), emptyMap(), emptyList(), emptyList()))
        assertEquals(setOf("Kaizo", "Kaizo (50% levels)"), stats.bests.map { it.mode }.toSet())
    }

    @Test
    fun `a run started with the 60% levels switched off says so on its record`() {
        val filesDir = Files.createTempDirectory("variant").toFile()
        val store = PrepStore(filesDir)
        val prepared = File(filesDir, "prep/prepared/emerald-u.gba").apply { parentFile.mkdirs(); writeBytes(ByteArray(256) { 7 }) }
        val kaizo = File(filesDir, "prep/settings/RSE Kaizo.rnqs").apply { parentFile.mkdirs(); writeBytes(official("RSE Kaizo.rnqs")) }
        val saved = CustomRuns.bundled
        try {
            CustomRuns.bundled = { bundled }
            fun roll(prePass: File?, code: Boolean = false) = RunStart.start(store, RomKind.EMERALD_U, prepared, kaizo, seed = 0x77L, app = "t",
                prePass = prePass, secondPass = null, take = { fail("a seed was chosen") }, stop = {},
                randomize = { dest, s -> dest.writeBytes(ByteArray(64) { 2 }); Randomizers.logFor(dest).writeText("log $s") }, fromCode = code)
            roll(prePass = null)
            assertEquals("50% levels", store.lastRunVariant())
            roll(prePass = File(filesDir, "prep/settings/RSE PRE-PASS.rnqs").apply { writeBytes(official("RSE PRE-PASS.rnqs")) })
            assertNull(store.lastRunVariant(), "with the pre-pass it ran as its rules have it")
            assertEquals(emptyList(), RunEvents(File(filesDir, "prep/integrity.txt")).entries())
            // Built from a code, and this seed is already in the history (the first roll was filed as ended).
            roll(prePass = null, code = true)
            val log = RunEvents(File(filesDir, "prep/integrity.txt")).entries()
            assertEquals(RunEvents.Kind.CODE, log.single().kind)
            assertTrue("seed played before as attempt" in log.single().detail, log.single().detail)
        } finally {
            CustomRuns.bundled = saved
        }
    }

    @Test
    fun `a code's seed already played is said before building, with how that run ended`() {
        val dir = Files.createTempDirectory("codes").toFile()
        val history = RunHistory(File(dir, "runhistory-emerald-u.tsv"))
        history.record(RunRecord(attempt = 7, seed = "%016x".format(0x55L), ruleset = "RSE Kaizo.rnqs", started = 0, ended = 5, playSeconds = 0,
            outcome = RunRecord.Outcome.LOST, badges = 1, lead = null, killer = null, trainer = "Leader Roxanne", location = ""))
        val before = RunCodeHistory.playedBefore(history, "%016x".format(0x55L), "RSE Kaizo.rnqs")!!
        assertEquals(7, before.attempt)
        assertNull(RunCodeHistory.playedBefore(history, "%016x".format(0x55L), "RSE Standard.rnqs"), "another file's run is another game")
        assertNull(RunCodeHistory.playedBefore(history, "%016x".format(0x56L), "RSE Kaizo.rnqs"))
        val line = RunCodeHistory.playedLine(before)
        assertEquals("You played this seed as attempt 7, lost to Leader Roxanne. Built again, it is a new attempt, and its record says it came from a code.", line)
        assertFalse('—' in line || '–' in line)
        // The build asks first when the seed was played, and the code's passes never touch the player's switches.
        val ui = File("src/main/kotlin/com/ironmonone/app/RunCodeUi.kt").readText()
        assertTrue("RunCodeHistory.playedBefore(store, plan) != null) confirm = plan else build(plan)" in ui)
        assertFalse("choosePrePass" in ui || "choosePart2" in ui, "a code's passes are for its one build")
        assertTrue("prePassOn = plan.code.prePass" in ui && "part2On = plan.code.part2" in ui)
        // And the job hands them to the passes for that one build (the mutation check found this unguarded).
        val job = File("src/main/kotlin/com/ironmonone/app/RunJob.kt").readText()
        assertTrue("ExtraPasses.prePassFor(app, store, rom.first, settings, prePassOn)" in job)
        assertTrue("ExtraPasses.secondPassFor(app, store, rom.first, settings, part2On)" in job)
        val passes = File("src/main/kotlin/com/ironmonone/app/ExtraPasses.kt").readText()
        assertTrue("if (!(on ?: prePassOn(context, kind, settings))) return null" in passes, "the code's choice wins for its build")
        assertTrue("if (!(on ?: part2On(context, kind, settings))) return null" in passes)
    }
}
