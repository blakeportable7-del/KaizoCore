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
}
