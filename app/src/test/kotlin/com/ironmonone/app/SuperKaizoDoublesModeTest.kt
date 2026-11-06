package com.ironmonone.app

import com.ironmonone.core.RomKind
import com.ironmonone.tracker.LossCondition
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * KaizoCore's Super Kaizo Doubles (Blake, 2026-11-06: "give the player the options"): Super Kaizo played with the Kaizo
 * Doubles rules, a KaizoCore preset and never an official IronMON ruleset. The editor's SuperKaizoDoublesPresetsTest
 * holds the files to Super Kaizo plus Kaizo Doubles' four changes; this holds what the app does with the mode.
 */
class SuperKaizoDoublesModeTest {
    private val presets = File("src/main/assets/presets")
    private val tags = listOf("FRLG", "RSE", "DPPt", "HGSS", "FRLG NatDex v1.2", "RSE NatDex v1.2")

    @Test
    fun `the file names the mode, and every label says it is a KaizoCore preset`() {
        for (tag in tags) {
            val f = File(presets, "$tag Super Kaizo Doubles.rnqs")
            assertTrue(f.isFile, f.name)
            val i = RnqsInfo.of(f)
            assertEquals(RnqsInfo.SUPER_KAIZO_DOUBLES, i.ruleset, f.name)
            assertTrue("Super Kaizo Doubles (KaizoCore preset)" in i.label, i.label)
            // The run record's mode name, which the name-keyed rules read: doubles' game over, and the label.
            val name = RunModeName.of(f, null)
            assertTrue("(KaizoCore preset)" in name, name)
            assertEquals(LossCondition.EITHER_OF_FIRST_TWO, LossCondition.forSettingsName(name))
            // The official modes keep their own keys.
            assertEquals("superkaizo", RnqsInfo.of(File(presets, "$tag Super Kaizo.rnqs")).ruleset)
            assertEquals("kaizodoubles", RnqsInfo.of(File(presets, "$tag Kaizo Doubles.rnqs")).ruleset)
        }
        assertEquals("Super Kaizo Doubles (KaizoCore preset)", RnqsInfo.rulesetLabel(RnqsInfo.SUPER_KAIZO_DOUBLES))
        assertTrue("not an official IronMON ruleset" in RulesetCatalog.modeLine(RnqsInfo.SUPER_KAIZO_DOUBLES))
        // After Kaizo Doubles in the Mode row.
        assertEquals(RulesetCatalog.rank("kaizodoubles") + 1, RulesetCatalog.rank(RnqsInfo.SUPER_KAIZO_DOUBLES))
    }

    @Test
    fun `it takes Super Kaizo's rules and Kaizo Doubles' together`() {
        val r = assertNotNull(MoveRule.rules(RnqsInfo.SUPER_KAIZO_DOUBLES, "FRLG", natDex = false))
        val sk = assertNotNull(MoveRule.rules("superkaizo", "FRLG", natDex = false))
        val kd = assertNotNull(MoveRule.rules("kaizodoubles", "FRLG", natDex = false))
        // Super Kaizo's: draining moves wild-only, setup moves against bosses, No Guard's OHKO ban.
        for (m in listOf("GIGA DRAIN", "SWORDS DANCE", "RECOVER")) assertEquals(sk.banOf(m), r.banOf(m), m)
        assertNotNull(r.banOf("FISSURE", ability = "No Guard"))
        // Kaizo Doubles': Dark Void, and a move both your Pokemon know.
        assertEquals(kd.banOf("DARK VOID"), r.banOf("DARK VOID"))
        assertNotNull(r.banOf("TACKLE", shared = true))
        assertNull(r.banOf("TACKLE"))
        // Smart AI: the warning fires on a build without it, as for Super Kaizo.
        assertNotNull(RulesetCatalog.superKaizoWarning(RomKind.FIRERED_U_V11, RnqsInfo.SUPER_KAIZO_DOUBLES))
        assertNull(RulesetCatalog.superKaizoWarning(RomKind.FIRERED_V11_FASTER_SK, RnqsInfo.SUPER_KAIZO_DOUBLES))
        assertTrue(RnqsInfo.SUPER_KAIZO_DOUBLES in FavoriteRules.NO_LEGENDARY_MODES)
        // Not on the games Super Kaizo's rules leave out.
        for (k in listOf(RomKind.RUBY_U, RomKind.PEARL_U)) assertTrue(!RulesetCatalog.defined(k, RnqsInfo.SUPER_KAIZO_DOUBLES), k.id)
    }

    @Test
    fun `each game that has the mode has its rules page, which says what it combines`() {
        for (dir in listOf("FRLG", "RSE", "DPPt", "HGSS", "FRLG-NatDex", "RSE-NatDex", "HnS")) {
            val page = File("src/main/assets/rulesets/$dir/superkaizodoubles.md").readText()
            assertTrue(page.lines().first().endsWith("Super Kaizo Doubles (KaizoCore preset)"), dir)
            assertTrue("is a KaizoCore preset, not an official IronMON ruleset" in page, dir)
            assertTrue("## Super Kaizo IronMON" in page && "## Kaizo Doubles" in page, dir)
            assertTrue(page.indexOf("## Super Kaizo IronMON") < page.indexOf("## Kaizo Doubles"), dir)
            assertTrue('—' !in page, dir)
        }
    }
}
