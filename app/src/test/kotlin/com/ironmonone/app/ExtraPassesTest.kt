package com.ironmonone.app

import com.ironmonone.core.RomKind
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Who gets the 60% levels, and that the player decides (Blake, 2026-09-29:
 * "just give the user full control"). Official means byte for byte a bundled
 * preset, read here from the source tree the APK is built from, so an edited
 * copy is custom and runs exactly as saved unless the player switches the
 * pass on; an official Kaizo, Survival or Super Kaizo on Emerald, or Kaizo or
 * Survival on Gold, Silver and Crystal, has it on until the player switches it off.
 */
class ExtraPassesTest {
    private val presets = File("src/main/assets/presets")
    private val bundled = presets.listFiles { f -> f.extension == "rnqs" }!!.associate { it.name to it.readBytes() }
    private val dir = Files.createTempDirectory("passes").toFile()
    private fun choices() = ExtraPasses.Choices(File(dir, "extra-passes.txt"))
    private fun official(name: String) = bundled.getValue(name)

    @Test
    fun `the pre-pass exists for Emerald and Gold, Silver and Crystal builds only`() {
        for (k in listOf(RomKind.EMERALD_U, RomKind.EMERALD_SMARTAI, RomKind.EMERALD_FASTER121))
            assertEquals(ExtraPasses.RSE_PRE_PASS, ExtraPasses.prePassName(k), k.id)
        for (k in listOf(RomKind.GOLD_U, RomKind.SILVER_U, RomKind.CRYSTAL_U, RomKind.CRYSTAL_PF, RomKind.GOLD_PF))
            assertEquals(ExtraPasses.GSC_PRE_PASS, ExtraPasses.prePassName(k), k.id)
        // Nat. Dex's own files are +60 already; Ruby and Sapphire have no such rule;
        // Faster Emerald 1.3.2 carries the 6% in the patch (PrePassLevelsTest).
        for (k in listOf(RomKind.EMERALD_NATDEX_121, RomKind.RUBY_U, RomKind.SAPPHIRE_U, RomKind.EMERALD_FASTER,
                RomKind.FIRERED_U_V11, RomKind.PLATINUM_U, RomKind.HEARTGOLD_U, RomKind.RED_U, RomKind.BLACK2_U))
            assertNull(ExtraPasses.prePassName(k), k.id)
        assertTrue(ExtraPasses.carries60(RomKind.EMERALD_FASTER))
        for (n in listOf(ExtraPasses.RSE_PRE_PASS, ExtraPasses.GSC_PRE_PASS)) assertTrue(n in bundled, "$n is bundled")
    }

    @Test
    fun `an official preset of a mode that calls for it is on by default, every other one is off`() {
        val on = listOf(RomKind.EMERALD_U to "RSE Kaizo.rnqs", RomKind.EMERALD_U to "RSE Survival.rnqs",
            RomKind.EMERALD_SMARTAI to "RSE Super Kaizo.rnqs", RomKind.EMERALD_FASTER121 to "RSE Kaizo.rnqs",
            RomKind.CRYSTAL_PF to "GSC Kaizo.rnqs", RomKind.GOLD_U to "GSC Survival.rnqs",
            // Blake, 2026-10-01: Emerald's Kaizo Doubles, Chaos Kaizo and IronMON Journey take the 60% levels too.
            RomKind.EMERALD_U to "RSE Kaizo Doubles.rnqs", RomKind.EMERALD_U to "RSE Chaos Kaizo.rnqs", RomKind.EMERALD_U to "RSE Ironmon Journey.rnqs")
        for ((k, n) in on) assertTrue(ExtraPasses.prePassByDefault(k, n, official(n), bundled), "${k.id} $n")
        val off = listOf(RomKind.EMERALD_U to "RSE Standard.rnqs", RomKind.EMERALD_U to "RSE Ultimate.rnqs",
            RomKind.CRYSTAL_U to "GSC Standard.rnqs",
            RomKind.CRYSTAL_U to "GSC Ultimate.rnqs", RomKind.EMERALD_FASTER to "RSE Kaizo.rnqs",
            RomKind.RUBY_U to "RSE Kaizo.rnqs", RomKind.EMERALD_NATDEX_121 to "RSE NatDex v1.2 Kaizo.rnqs")
        for ((k, n) in off) assertFalse(ExtraPasses.prePassByDefault(k, n, official(n), bundled), "${k.id} $n")
    }

    @Test
    fun `an edited copy is custom and a renamed identical copy is still official`() {
        val kaizo = official("RSE Kaizo.rnqs")
        val edited = kaizo.copyOf().also { it[it.size - 1] = (it[it.size - 1] + 1).toByte() }
        assertNull(ExtraPasses.officialName("RSE Kaizo.rnqs", edited, bundled), "same name, other bytes: custom")
        assertFalse(ExtraPasses.prePassByDefault(RomKind.EMERALD_U, "RSE Kaizo.rnqs", edited, bundled))
        assertFalse(ExtraPasses.prePassByDefault(RomKind.EMERALD_U, "RSE Kaizo (2).rnqs", edited, bundled))
        assertEquals("RSE Kaizo.rnqs", ExtraPasses.officialName("My run.rnqs", kaizo, bundled))
        assertTrue(ExtraPasses.prePassByDefault(RomKind.EMERALD_U, "My run.rnqs", kaizo, bundled), "identical bytes under another name")
        assertFalse(ExtraPasses.prePassByDefault(RomKind.EMERALD_U, "RSE Kaizo.rnqs", null, bundled), "a missing file is nobody's preset")
    }

    @Test
    fun `Gen 1's PART 2 is on for an official preset without the patch, off with it, off for a custom file`() {
        val c = choices()
        val kaizo = official("RBY Kaizo.rnqs")
        val edited = kaizo.copyOf().also { it[it.size - 1] = (it[it.size - 1] + 1).toByte() }
        for (k in listOf(RomKind.RED_U, RomKind.BLUE_U, RomKind.YELLOW_U)) {
            val pf = RomKind.allPatched.first { it.baseId == k.id && it.patchTag == "pseudofluct" }
            for (n in listOf("RBY Standard.rnqs", "RBY Kaizo.rnqs", "RBY Ultimate.rnqs", "RBY Survival.rnqs")) {
                assertTrue(ExtraPasses.part2On(k, n, official(n), bundled, c), "${k.id} $n: the second official way")
                assertFalse(ExtraPasses.part2On(pf, n, official(n), bundled, c), "${pf.id} $n: the patch is the first way")
            }
            assertFalse(ExtraPasses.part2On(k, "RBY mine.rnqs", edited, bundled, c), "custom runs as saved")
        }
        for (k in listOf(RomKind.CRYSTAL_U, RomKind.EMERALD_U)) assertFalse(ExtraPasses.part2On(k, "RBY Kaizo.rnqs", kaizo, bundled, c))
        // The player decides, per game and file, like the 60% levels.
        c.set(RomKind.RED_PF.id, "RBY Kaizo.rnqs", ExtraPasses.PART_2, true)
        c.set(RomKind.RED_U.id, "RBY mine.rnqs", ExtraPasses.PART_2, true)
        assertTrue(ExtraPasses.part2On(RomKind.RED_PF, "RBY Kaizo.rnqs", kaizo, bundled, c))
        assertTrue(ExtraPasses.part2On(RomKind.RED_U, "RBY mine.rnqs", edited, bundled, c))
        assertFalse(ExtraPasses.part2On(RomKind.BLUE_PF, "RBY Kaizo.rnqs", kaizo, bundled, c))
    }

    @Test
    fun `the player's choice wins either way, per game and file, and survives a restart`() {
        val c = choices()
        val kaizo = official("RSE Kaizo.rnqs")
        val edited = kaizo.copyOf().also { it[0] = (it[0] + 1).toByte() }
        assertTrue(ExtraPasses.prePassOn(RomKind.EMERALD_U, "RSE Kaizo.rnqs", kaizo, bundled, c))
        c.set(RomKind.EMERALD_U.id, "RSE Kaizo.rnqs", ExtraPasses.PRE_60, false)
        assertFalse(ExtraPasses.prePassOn(RomKind.EMERALD_U, "RSE Kaizo.rnqs", kaizo, bundled, c), "switched off for the official preset")
        assertTrue(ExtraPasses.prePassOn(RomKind.EMERALD_SMARTAI, "RSE Kaizo.rnqs", kaizo, bundled, c), "another game keeps its own default")
        assertFalse(ExtraPasses.prePassOn(RomKind.EMERALD_U, "RSE custom.rnqs", edited, bundled, c))
        c.set(RomKind.EMERALD_U.id, "RSE custom.rnqs", ExtraPasses.PRE_60, true)
        assertTrue(ExtraPasses.prePassOn(RomKind.EMERALD_U, "RSE custom.rnqs", edited, bundled, c), "switched on for a custom file")
        val again = choices()   // a new process reads the same file
        assertFalse(ExtraPasses.prePassOn(RomKind.EMERALD_U, "RSE Kaizo.rnqs", kaizo, bundled, again))
        assertTrue(ExtraPasses.prePassOn(RomKind.EMERALD_U, "RSE custom.rnqs", edited, bundled, again))
        again.set(RomKind.EMERALD_U.id, "RSE Kaizo.rnqs", ExtraPasses.PRE_60, true)
        assertTrue(ExtraPasses.prePassOn(RomKind.EMERALD_U, "RSE Kaizo.rnqs", kaizo, bundled, choices()))
        assertEquals(2, File(dir, "extra-passes.txt").readLines().size, "one line per game, file and pass")
        // A switch on a game with no pre-pass changes nothing.
        c.set(RomKind.RUBY_U.id, "RSE Kaizo.rnqs", ExtraPasses.PRE_60, true)
        assertFalse(ExtraPasses.prePassOn(RomKind.RUBY_U, "RSE Kaizo.rnqs", kaizo, bundled, c))
    }
}
