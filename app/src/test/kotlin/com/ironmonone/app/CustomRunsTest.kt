package com.ironmonone.app

import com.ironmonone.core.RomKind
import com.ironmonone.app.engine.Randomizers
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A custom settings file is a custom game, not the mode its name contains (2026-09-30, IronMON rules check R2): a
 * Build your own game, an edited copy or "My Kaizo run" was shown and counted as Kaizo.
 */
class CustomRunsTest {
    private val presets = File("src/main/assets/presets")
    private val bundled: Map<String, ByteArray> = presets.listFiles()!!.filter { it.name.endsWith(".rnqs") }.associate { it.name to it.readBytes() }

    @Test
    fun `only the bytes of a file KaizoCore comes with are that mode`() {
        val kaizo = bundled.getValue("FRLG Kaizo.rnqs")
        assertFalse(CustomRuns.isCustom("FRLG Kaizo.rnqs", kaizo, bundled))
        assertFalse(CustomRuns.isCustom("FRLG Kaizo copy.rnqs", kaizo, bundled), "a renamed copy is still those settings")
        assertTrue(CustomRuns.isCustom("FRLG Kaizo.rnqs", kaizo.copyOf().also { it[it.size / 2] = (it[it.size / 2] + 1).toByte() }, bundled),
            "an edited file under the bundled name")
        assertTrue(CustomRuns.isCustom("FRLG Kaizo (my build).rnqs", byteArrayOf(1, 2, 3), bundled))
        assertEquals("Kaizo (custom)", CustomRuns.label("Kaizo", true))
        assertEquals("Kaizo", CustomRuns.label("Kaizo", false))
        val line = CustomRuns.line("Kaizo")
        assertTrue("custom game, not an official Kaizo run" in line)
        assertFalse('\u2014' in line || '\u2013' in line)
    }

    @Test
    fun `a run from a custom file says so on its record, the Home card, the shared line and in Your stats`() {
        val filesDir = Files.createTempDirectory("custom").toFile()
        val store = PrepStore(filesDir)
        val emerald = RomKind.EMERALD_U
        val prepared = File(filesDir, "prep/prepared/emerald-u.gba").apply { parentFile.mkdirs(); writeBytes(ByteArray(256) { 7 }) }
        val stub: (File, Long) -> Unit = { dest, seed -> dest.writeBytes(ByteArray(64) { 2 }); Randomizers.logFor(dest).writeText("log $seed") }
        val saved = CustomRuns.bundled
        try {
            CustomRuns.bundled = { bundled }
            fun roll(settings: File) = RunStart.start(store, emerald, prepared, settings, seed = 0x21L, app = "t", prePass = null, secondPass = null,
                take = { kotlin.test.fail("a seed was chosen") }, stop = {}, randomize = stub)
            val official = File(filesDir, "prep/settings/RSE Kaizo.rnqs").apply { parentFile.mkdirs(); writeBytes(bundled.getValue("RSE Kaizo.rnqs")) }
            roll(official)
            assertFalse(store.lastRunCustom())
            assertEquals(emerald.id to "RSE Kaizo.rnqs", store.loadLastRun(), "the two lines every reader takes")
            val mine = File(filesDir, "prep/settings/RSE Kaizo (my build).rnqs").apply { writeBytes(byteArrayOf(9, 9, 9)) }
            roll(mine)
            assertTrue(store.lastRunCustom())
            assertEquals(emerald.id to "RSE Kaizo (my build).rnqs", store.loadLastRun())
        } finally {
            CustomRuns.bundled = saved
        }
        val r = RunRecord(attempt = 3, seed = "aa", ruleset = "RSE Kaizo (my build).rnqs", started = 0, ended = 0, playSeconds = 0,
            outcome = RunRecord.Outcome.LOST, badges = 1, lead = null, killer = null, trainer = "", location = "", custom = true)
        assertTrue(RunRecord.decode(r.encode())!!.custom, "kept in the history file")
        assertFalse(RunRecord.decode(r.copy(custom = false).encode())!!.custom)
        assertTrue("(RSE Kaizo (my build), custom)" in runSummaryLine(r, "Pok\u00e9mon Emerald"))
        val stats = CareerStats.compute(CareerStats.Inputs(
            records = mapOf(RomKind.EMERALD_U.id to listOf(r, r.copy(attempt = 4, seed = "bb", ruleset = "RSE Kaizo.rnqs", custom = false))),
            attempts = mapOf(RomKind.EMERALD_U.id to 4), clock = emptyMap(), pastRuns = emptyList(), nuzlocke = emptyList()))
        assertEquals(setOf("Kaizo", "Kaizo (custom)"), stats.bests.map { it.mode }.toSet(), "a custom game is never the mode's best")
    }

    @Test
    fun `a run started before rc32 has no custom line, and is judged from its file`() {
        val filesDir = Files.createTempDirectory("oldrun").toFile()
        val store = PrepStore(filesDir)
        File(filesDir, "prep/settings/RSE Kaizo (my build).rnqs").apply { parentFile.mkdirs(); writeBytes(byteArrayOf(4, 5, 6)) }
        File(filesDir, "prep/settings/RSE Kaizo.rnqs").writeBytes(bundled.getValue("RSE Kaizo.rnqs"))
        val saved = CustomRuns.bundled
        try {
            CustomRuns.bundled = { bundled }
            store.saveLastRun(RomKind.EMERALD_U.id, "RSE Kaizo (my build).rnqs")        // as rc31 wrote it: two lines
            assertTrue(store.lastRunCustom())
            store.saveLastRun(RomKind.EMERALD_U.id, "RSE Kaizo.rnqs")
            assertFalse(store.lastRunCustom())
            store.saveLastRun(RomKind.EMERALD_U.id, "RSE Kaizo.rnqs", custom = true)   // a line that says so wins
            assertTrue(store.lastRunCustom())
            CustomRuns.bundled = null
            store.saveLastRun(RomKind.EMERALD_U.id, "RSE Kaizo (my build).rnqs")
            assertFalse(store.lastRunCustom(), "nothing to judge by: not called custom")
        } finally {
            CustomRuns.bundled = saved
        }
    }

    @Test
    fun `the screens that name the mode ask the file`() {
        fun src(n: String) = File("src/main/kotlin/com/ironmonone/app/$n").readText().replace("\r\n", "\n")
        val run = src("RunScreen.kt")
        assertTrue("CustomRuns.isCustom(context, it)" in run)
        assertTrue("if (customPicked) Text(CustomRuns.line(mode.label)" in run)
        assertTrue("CustomRuns.label(RunCopy.modeName(" in run, "the new-run question")
        assertTrue("CustomRuns.label(HomeCopy.ironmonDetail(RnqsInfo.rulesetLabel(it)), store.lastRunCustom())" in src("HomeNav.kt"), "the Home card")
        assertTrue("CustomRuns.bundled = { ExtraPasses.bundled(appContext) }" in src("MainActivity.kt"))
    }
}
