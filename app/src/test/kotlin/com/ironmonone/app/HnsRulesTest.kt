package com.ironmonone.app

import com.ironmonone.core.RomKind
import com.ironmonone.tracker.GbaTracker
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Heart & Soul as a full KaizoCore game (docs/NEW-GAME-CHECKLIST.md, 2026-10-05): the rules page: Heart & Soul's own book and the Mode row's lines.
 */
class HnsRulesTest {
    private val hns = RomKind.HEARTSOUL_KAIZO_206
    private val books = File("src/main/assets/rulesets")



    @Test
    fun `RULES opens Heart and Soul's own book, HeartGold and SoulSilver's game rules with the Nat Dex changes`() {
        assertEquals("HnS", Rules.dirFor(hns.family, hns.isNatDex, hns))
        val dir = File(books, "HnS")
        val presets = File("src/main/assets/presets").listFiles { f -> f.extension == "rnqs" }!!.toList()
        val offered = RulesetCatalog.forRom(hns, presets).map { it.key }.toSet()
        assertTrue("kaizo" in offered && "survival" in offered, "$offered")
        for (mode in offered) {
            val text = File(dir, "$mode.md").readText()
            assertTrue(text.startsWith("# Heart & Soul: "), mode)
            assertTrue("In KaizoCore, Heart & Soul is held to the rules of the game it retells" in text, mode)
            assertTrue("the trash can in Elm's lab" in text, "$mode: the PC item's place (comfort build F0C6236C)")
            assertTrue("## HeartGold and SoulSilver: game-specific rules" in text, mode)
            assertTrue("To win Ironmon you must defeat Red at the top of Mt. Silver." in text, mode)
            assertTrue("## Nat. Dex ruleset changes" in text, mode)
            assertFalse("Ruby, Sapphire and Emerald: game-specific rules" in text, "$mode: Hoenn's rules are not Heart & Soul's")
            assertFalse("\u2014" in text, mode)
        }
        // Kaizo and up: the Johto Kaizo rule; Ultimate and up: the dungeons of the Johto story.
        assertTrue("Ruins of Alph" in File(dir, "kaizo.md").readText())
        assertTrue("Radio Tower" in File(dir, "ultimate.md").readText())
        assertTrue("Ruins of Alph" !in File(dir, "standard.md").readText())
        // Survival: the Kanto heals of a Johto game.
        assertTrue("If you complete the Elite 4 in a Johto game you earn an additional 7 heals for Kanto" in File(dir, "survival.md").readText())
        // Super Kaizo: HeartGold and SoulSilver's own section (the Johto gyms, Kanto, Red in hail), and the smart AI note.
        val sk = File(dir, "superkaizo.md").readText()
        assertTrue("### Super Kaizo, HeartGold and SoulSilver" in sk && "Must fight Red with hail weather active" in sk)
    }

    @Test
    fun `the Mode row's lines follow Heart and Soul's book`() {
        assertEquals(RulesetCatalog.modeLine("ultimate", natDex = true), RulesetCatalog.modeLine("ultimate", false, hns.family),
            "its book has the Nat. Dex Ultimate, HM moves allowed when not taught by the HM item")
        val survival = RulesetCatalog.modeLine("survival", false, hns.family)
        assertTrue("seven more for Kanto" in survival, survival)
        assertEquals(RulesetCatalog.modeLine("survival", false, "HGSS"), survival)
    }
}
