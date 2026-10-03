package com.ironmonone.app

import com.ironmonone.app.engine.NatDexEngine
import com.ironmonone.app.engine.Randomizers
import com.ironmonone.core.RomKind
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The Gen 1 two-pass orchestration, with the engine call stubbed: no
 * cartridge exists on this machine, so what is proven is the order, the
 * files each pass sees, the seeds, the cleanup and the refusal when PART 2
 * is missing. The real randomization is owed to a Red/Blue/Yellow dump.
 */
class RandomizersTest {
    private val dir = Files.createTempDirectory("rby").toFile()
    private val rom = File(dir, "yellow.gbc").apply { writeBytes(ByteArray(64) { 1 }) }
    private val part1 = File(dir, "RBY Kaizo.rnqs").apply { writeText("part1") }
    private val part2 = File(dir, Randomizers.GEN1_SECOND_PASS).apply { writeText("part2") }
    private val dest = File(dir, "current.gbc")

    @Test
    fun `PART 1 runs on the ROM, PART 2 on PART 1's output, into the destination`() {
        val calls = ArrayList<List<Any>>()
        val out = Randomizers.twoPass(rom, part1, part2, dest, 42L) { src, st, d, sd ->
            calls += listOf(src.name, st.name, d.name, sd, src.readBytes().size)
            d.writeBytes(src.readBytes() + st.readBytes())   // each pass grows the file, so pass 2 provably read pass 1
            NatDexEngine.Outcome(sd, "log of " + st.name)
        }
        assertEquals(2, calls.size)
        assertEquals(listOf("yellow.gbc", "RBY Kaizo.rnqs", "current.gbc.pass1.tmp", 42L, 64), calls[0])
        assertEquals("current.gbc.pass1.tmp", calls[1][0]); assertEquals("RBY PART 2.rnqs", calls[1][1]); assertEquals("current.gbc", calls[1][2])
        assertEquals(Randomizers.secondSeed(42L), calls[1][3]); assertEquals(64 + 5, calls[1][4], "PART 2's input is PART 1's output")
        assertEquals(64 + 5 + 5, dest.length().toInt())
        assertFalse(File(dir, "current.gbc.pass1.tmp").exists(), "the intermediate is cleaned up")
        assertEquals(42L, out.seed, "the run keeps the seed the player saw")
        assertTrue("== PART 1" in out.logText && "== PART 2" in out.logText && "log of RBY PART 2.rnqs" in out.logText)
        assertTrue(Randomizers.secondSeed(42L) != 42L)
    }

    @Test
    fun `a Gen 1 run without PART 2 is PART 1 alone, into the destination`() {
        val calls = ArrayList<List<Any>>()
        val out = Randomizers.gen1(rom, part1, null, dest, 42L) { src, st, d, sd ->
            calls += listOf(src.name, st.name, d.name, sd)
            d.writeBytes(src.readBytes() + st.readBytes())
            NatDexEngine.Outcome(sd, "log of " + st.name)
        }
        assertEquals(listOf(listOf<Any>("yellow.gbc", "RBY Kaizo.rnqs", "current.gbc", 42L)), calls)
        assertEquals("log of RBY Kaizo.rnqs", out.logText)
        assertFalse(File(dir, "current.gbc.pass1.tmp").exists())
        // With PART 2 it is the two-pass run.
        calls.clear()
        Randomizers.gen1(rom, part1, part2, dest, 42L) { src, st, d, sd -> calls += listOf(src.name, st.name, d.name, sd); d.writeBytes(src.readBytes()); NatDexEngine.Outcome(sd, "") }
        assertEquals(2, calls.size)
    }

    @Test
    fun `a missing PART 2 refuses before touching the ROM, and a failed PART 1 still cleans up`() {
        val e = assertFailsWith<NatDexEngine.EngineException> {
            Randomizers.twoPass(rom, part1, File(dir, "nope.rnqs"), dest, 1L) { _, _, _, _ -> error("must not run") }
        }
        assertTrue(Randomizers.GEN1_SECOND_PASS in e.message!!)
        assertFailsWith<NatDexEngine.EngineException> {
            Randomizers.twoPass(rom, part1, part2, dest, 1L) { _, _, d, _ -> d.writeBytes(ByteArray(0)); NatDexEngine.Outcome(1L, "") }
        }
        assertFalse(File(dir, "current.gbc.pass1.tmp").exists())
    }

    @Test
    fun `the 60% pre-pass runs on the ROM, the preset on its output with the run's seed, and the log is the preset's`() {
        val emerald = File(dir, "emerald.gba").apply { writeBytes(ByteArray(64) { 2 }) }
        val pre = File(dir, "RSE PRE-PASS.rnqs").apply { writeText("pre") }
        val kaizo = File(dir, "RSE Kaizo.rnqs").apply { writeText("kaizo") }
        val out = File(dir, "current.gba")
        val calls = ArrayList<List<Any>>()
        val o = Randomizers.withPrePass(emerald, pre, kaizo, out, 42L) { src, st, d, sd ->
            calls += listOf(src.name, st.name, d.name, sd, src.readBytes().size)
            d.writeBytes(src.readBytes() + st.readBytes())
            NatDexEngine.Outcome(sd, "Randomizer Version: 4.6.1\nRandom Seed: $sd\nlog of " + st.name)
        }
        assertEquals(listOf("emerald.gba", "RSE PRE-PASS.rnqs", "current.gba.prepass.tmp", Randomizers.preSeed(42L), 64), calls[0])
        assertEquals(listOf("current.gba.prepass.tmp", "RSE Kaizo.rnqs", "current.gba", 42L, 64 + 3), calls[1], "the preset reads the pre-pass's output, with the run's own seed")
        assertEquals(64 + 3 + 5, out.length().toInt())
        assertFalse(File(dir, "current.gba.prepass.tmp").exists(), "the intermediate is cleaned up")
        assertEquals(42L, o.seed)
        assertTrue(o.logText.startsWith("Randomizer Version: 4.6.1\nRandom Seed: 42\nlog of RSE Kaizo.rnqs"), "the log viewer reads the preset's log, not the pre-pass's")
        assertTrue("log of RSE PRE-PASS.rnqs" !in o.logText && "RSE PRE-PASS.rnqs" in o.logText.lines().last { it.isNotBlank() })
        assertTrue(Randomizers.preSeed(42L) != 42L && Randomizers.preSeed(42L) != Randomizers.secondSeed(42L))
    }

    @Test
    fun `a missing pre-pass refuses before touching the ROM, and a failed pre-pass still cleans up`() {
        val emerald = File(dir, "emerald.gba").apply { writeBytes(ByteArray(64) { 2 }) }
        val kaizo = File(dir, "RSE Kaizo.rnqs").apply { writeText("kaizo") }
        val e = assertFailsWith<NatDexEngine.EngineException> {
            Randomizers.withPrePass(emerald, File(dir, "RSE PRE-PASS.rnqs"), kaizo, dest, 1L) { _, _, _, _ -> error("must not run") }
        }
        assertTrue("RSE PRE-PASS.rnqs" in e.message!!)
        val pre = File(dir, "RSE PRE-PASS.rnqs").apply { writeText("pre") }
        assertFailsWith<NatDexEngine.EngineException> {
            Randomizers.withPrePass(emerald, pre, kaizo, dest, 1L) { _, _, d, _ -> d.writeBytes(ByteArray(0)); NatDexEngine.Outcome(1L, "") }
        }
        assertFalse(File(dir, "current.gbc.prepass.tmp").exists())
        assertTrue(RnqsInfo.parse("RSE PRE-PASS.rnqs").prePass && RnqsInfo.parse("GSC PRE-PASS.rnqs").appliedByApp)
        assertFalse(RulesetCatalog.isCompatible(RomKind.EMERALD_U, pre), "never offered as a mode or a settings file")
    }

    @Test
    fun `the Gen 1 kinds are two-pass, everything else is not, and the PART 2 preset is bundled but never offered`() {
        assertEquals(com.ironmonone.core.Generation.GB1, RomKind.YELLOW_U.generation)
        assertEquals("RBY", RomKind.RED_U.family)
        assertTrue(RnqsInfo.parse(Randomizers.GEN1_SECOND_PASS).secondPass)
        assertFalse(RnqsInfo.parse("RBY Kaizo.rnqs").secondPass)
        assertFalse(RulesetCatalog.isCompatible(RomKind.YELLOW_U, File(Randomizers.GEN1_SECOND_PASS)))
        assertTrue(RulesetCatalog.isCompatible(RomKind.YELLOW_U, File("RBY Kaizo.rnqs")))
        assertFalse(RulesetCatalog.isCompatible(RomKind.CRYSTAL_U, File("RBY Kaizo.rnqs")))
        val presets = listOf("src/main/assets/presets", "app/src/main/assets/presets").map(::File).first { it.isDirectory }
        for (n in listOf("RBY Standard.rnqs", "RBY Kaizo.rnqs", "RBY Ultimate.rnqs", "RBY Survival.rnqs", Randomizers.GEN1_SECOND_PASS)) {
            assertTrue(File(presets, n).length() > 0, "$n is bundled")
        }
    }

    @Test
    fun `the engines run in the root locale and the phone's comes back after`() {
        // rc32 audit P2 #118: a Turkish phone lower-cased FIRE to a dotless "fıre"; an Arabic-digit phone logged its digits.
        val saved = java.util.Locale.getDefault()
        try {
            java.util.Locale.setDefault(java.util.Locale.forLanguageTag("tr-TR"))
            assertEquals("Fire", Randomizers.inEngineLocale { com.dabomstew.pkrandomzx.RomFunctions.camelCase("FIRE") })
            assertFalse(com.dabomstew.pkrandomzx.RomFunctions.camelCase("FIRE") == "Fire", "outside it the phone's own rules apply, which is the bug")
            assertEquals("tr-TR", java.util.Locale.getDefault().toLanguageTag(), "the phone's locale comes back")
            java.util.Locale.setDefault(java.util.Locale.forLanguageTag("ar-EG-u-nu-arab"))
            assertEquals("001", Randomizers.inEngineLocale { String.format("%03d", 1) })
            assertFalse(String.format("%03d", 1) == "001")
            assertFailsWith<IllegalStateException> { Randomizers.inEngineLocale { error("the engine threw") } }
            assertEquals("ar-EG-u-nu-arab", java.util.Locale.getDefault().toLanguageTag(), "and comes back when the engine throws")
        } finally {
            java.util.Locale.setDefault(saved)
        }
    }

    @Test
    fun `a Turkish or Arabic phone makes the same game and the same log for a seed as any other`() {
        val roms = File(System.getenv("IRONMON_ROMS") ?: "C:/Users/bepor/IronMonOne/.vendor/roms")
        val src = Dumps.file(roms, "emerald-u.gba") ?: return println("SKIP: no emerald-u.gba")
        val presets = listOf("src/main/assets/presets", "app/src/main/assets/presets").map(::File).first { it.isDirectory }
        // Kaizo with the lower-case names tweak, which runs every Pokemon's name through RomFunctions.camelCase.
        val tweaked = File(dir, "RSE Kaizo lower.rnqs")
        val s = java.io.FileInputStream(File(presets, "RSE Kaizo.rnqs")).use { com.dabomstew.pkrandomzx.Settings.read(it) }
        s.currentMiscTweaks = s.currentMiscTweaks or com.dabomstew.pkrandomzx.MiscTweak.LOWER_CASE_POKEMON_NAMES.value
        java.io.FileOutputStream(tweaked).use { s.write(it) }
        val saved = java.util.Locale.getDefault()
        fun make(tag: String): Pair<ByteArray, String> {
            java.util.Locale.setDefault(java.util.Locale.forLanguageTag(tag))
            try {
                val dest = File(dir, "out-$tag.gba")
                Randomizers.randomize(RomKind.EMERALD_U, src, tweaked, dest, 0x1234567L)
                return dest.readBytes() to Randomizers.logFor(dest).readText().lines().filterNot { it.startsWith("Time elapsed:") }.joinToString("\n")
            } finally {
                java.util.Locale.setDefault(saved)
            }
        }
        val us = make("en-US")
        val tr = make("tr-TR")
        val ar = make("ar-EG-u-nu-arab")
        assertTrue(us.first.contentEquals(tr.first), "the same seed makes the same ROM on a Turkish phone")
        assertEquals(us.second, ar.second, "and the same log on a phone with Arabic digits")
        assertTrue("TM01" in ar.second)
    }

    private fun halfEngine(): (File, File, File, Long) -> NatDexEngine.Outcome = { src, _, d, sd ->
        d.writeBytes(src.readBytes().copyOf(src.length().toInt() / 2)); NatDexEngine.Outcome(sd, "log")
    }

    @Test
    fun `a Game Boy or GBA game written short is refused and deleted, in every pass`() {
        // rc32 audit P2 #119: the engines swallow a failed write and only an empty file was refused.
        val gba = File(dir, "emerald.gba").apply { writeBytes(ByteArray(64) { 3 }) }
        val out = File(dir, "short.gba")
        val e = assertFailsWith<NatDexEngine.EngineException> { Randomizers.whole(RomKind.EMERALD_U, halfEngine())(gba, part1, out, 1L) }
        assertTrue("stopped partway" in e.message!!, e.message)
        assertFalse(out.exists(), "the cut file is gone")
        // With less room than the missing part, it is the phone's space, said so on Play and RUN alike.
        val full = assertFailsWith<RunSetupProblem> { Randomizers.whole(RomKind.EMERALD_U, halfEngine(), free = { 0L })(gba, part1, out, 1L) }
        assertEquals(Randomizers.NO_ROOM, full.message)
        assertEquals(Randomizers.NO_ROOM, newRunFailureCopy(full))
        assertEquals(Randomizers.NO_ROOM, RunJob.randomizeFailure(full))
        // The checked engine is what each pass runs: PART 1, and the pre-pass.
        assertFailsWith<NatDexEngine.EngineException> { Randomizers.twoPass(rom, part1, part2, dest, 1L, Randomizers.whole(RomKind.RED_U, halfEngine())) }
        assertFalse(File(dir, "current.gbc.pass1.tmp").exists() || dest.exists())
        val pre = File(dir, "RSE PRE-PASS.rnqs").apply { writeText("pre") }
        assertFailsWith<NatDexEngine.EngineException> { Randomizers.withPrePass(gba, pre, part1, out, 1L, Randomizers.whole(RomKind.EMERALD_U, halfEngine())) }
        // A whole one passes, and a DS game, whose handler throws on a failed write itself, is not measured.
        val ok = Randomizers.whole(RomKind.EMERALD_U, { src, _, d, sd -> d.writeBytes(src.readBytes()); NatDexEngine.Outcome(sd, "log") })(gba, part1, out, 1L)
        assertEquals(1L, ok.seed)
        val nds = File(dir, "platinum.nds").apply { writeBytes(ByteArray(64)) }
        Randomizers.whole(RomKind.PLATINUM_U, halfEngine())(nds, part1, File(dir, "out.nds"), 1L)
        // A full disk that the DS engine reports is said as a full disk too.
        val enospc = NatDexEngine.EngineException("Randomization failed: x", java.io.IOException("write failed: ENOSPC (No space left on device)"))
        assertEquals(Randomizers.NO_ROOM, RunJob.randomizeFailure(enospc))
        assertEquals("The randomizer stopped partway through. Try again, or pick another settings file.", RunJob.randomizeFailure(NatDexEngine.EngineException("Randomization failed: null")))
    }

    @Test
    fun `a Limit Pokemon the engine turns off for a changed Gen 3 game is said in the log`() {
        // rc32 audit P3 #90: the 60% levels' pre-pass output is not the clean dump, so the engine dropped the limit unsaid.
        val roms = File(System.getenv("IRONMON_ROMS") ?: "C:/Users/bepor/IronMonOne/.vendor/roms")
        val src = Dumps.file(roms, "emerald-u.gba") ?: return println("SKIP: no emerald-u.gba")
        val presets = listOf("src/main/assets/presets", "app/src/main/assets/presets").map(::File).first { it.isDirectory }
        val limited = File(dir, "RSE Kaizo limited.rnqs")
        val s = java.io.FileInputStream(File(presets, "RSE Kaizo.rnqs")).use { com.dabomstew.pkrandomzx.Settings.read(it) }
        s.currentRestrictions = com.dabomstew.pkrandomzx.pokemon.GenRestrictions(1 or 2 or 4)
        s.isLimitPokemon = true
        java.io.FileOutputStream(limited).use { s.write(it) }
        val out = File(dir, "limited.gba")
        Randomizers.randomize(RomKind.EMERALD_U, src, limited, out, 0x77L, prePass = File(presets, "RSE PRE-PASS.rnqs"))
        assertEquals(listOf(com.ironmonone.app.engine.ZxEngine.LIMIT_DROPPED), RandomizerLog.notesOf(Randomizers.logFor(out)))
        // On the clean dump the engine keeps the limit, and the log says nothing of it.
        Randomizers.randomize(RomKind.EMERALD_U, src, limited, out, 0x77L)
        assertEquals(emptyList(), RandomizerLog.notesOf(Randomizers.logFor(out)))
    }
}
