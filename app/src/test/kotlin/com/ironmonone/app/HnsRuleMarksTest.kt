package com.ironmonone.app

import androidx.compose.ui.graphics.Color
import com.ironmonone.core.RomKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The rules' X on Heart & Soul (2026-10-05): a Nat. Dex pool run plays the Emerald Nat. Dex rules, a Vanilla pool run
 * Emerald's, and every move, ability and item is matched by its name (Heart & Soul's ids are neither Emerald's nor the
 * Nat. Dex build's). Its HMs are its own eight.
 */
class HnsRuleMarksTest {
    private val family = RomKind.HEARTSOUL_KAIZO_206.family
    private fun move(name: String, category: String = "PHY") =
        PcMove(name = name, pp = 10, ppMax = 10, power = 80, acc = 100, color = Color.White, category = category)

    @Test
    fun `a banned move on a Heart and Soul Pokemon gets its X, by its name in the game's own capitals`() {
        val natDex = assertNotNull(MoveRule.rules("kaizo", family, natDex = true, RomKind.HEARTSOUL_KAIZO_206.id))
        val recover = assertNotNull(natDex.banOf("RECOVER"))
        assertTrue(MoveRule.mark(move("RECOVER", "STA"), recover, MoveRule.Battle(), natDex.note).banned)
        assertNotNull(natDex.banOf("SOFT-BOILED"), "Soft-Boiled, however the game spells it")
        assertNull(natDex.banOf("TACKLE"))
        // Nat. Dex: an HM move is allowed unless the HM item taught it, which the tracker cannot see: a line, no X.
        assertEquals(MoveRule.When.UNSEEN, natDex.banOf("WHIRLPOOL")!!.whenBanned)
        // Vanilla pool: Emerald's rules, the HM moves banned outright, and they are Heart & Soul's own HMs.
        val vanilla = assertNotNull(MoveRule.rules("kaizo", family, natDex = false, RomKind.HEARTSOUL_KAIZO_206.id))
        for (hm in listOf("CUT", "FLY", "SURF", "STRENGTH", "FLASH", "ROCK SMASH", "WATERFALL", "WHIRLPOOL"))
            assertEquals(MoveRule.When.ALWAYS, vanilla.banOf(hm)?.whenBanned, hm)
        assertNull(vanilla.banOf("DIVE"), "Dive is Emerald's HM, not Heart & Soul's")
    }

    @Test
    fun `Super Kaizo's boss-only bans know Heart and Soul's gym leaders by its own trainer ids`() {
        val rules = assertNotNull(MoveRule.rules("superkaizo", family, natDex = true, RomKind.HEARTSOUL_KAIZO_206.id))
        val falkner = rules.battle(inBattle = true, wild = false, trainerId = 402)
        assertTrue(falkner.boss, "TRAINER_FALKNER_1_HNS is a gym leader")
        assertTrue(rules.battle(inBattle = true, wild = false, trainerId = 441).elite, "TRAINER_LANCE_1_HNS is the champion")
        assertFalse(rules.battle(inBattle = true, wild = false, trainerId = 100).boss)
    }

    @Test
    fun `Huge Power bans a physical move, and the BST exception holds`() {
        val natDexRun = RuleMarks.Run("kaizo", natDex = true, family)
        assertNotNull(RuleMarks.physicalBan(natDexRun, "HUGE POWER", bst = 450, canEvolve = false))
        assertNull(RuleMarks.physicalBan(natDexRun, "HUGE POWER", bst = 420, canEvolve = false), "Nat. Dex: 420 or lower is exempt")
        val reason = RuleMarks.physicalBan(natDexRun, "PURE POWER", bst = 500, canEvolve = true)!!
        assertTrue(MoveRule.mark(move("TACKLE"), MoveRule.physical(move("TACKLE"), reason), MoveRule.Battle(), null).banned)
        assertFalse(MoveRule.mark(move("SURF", "SPE"), MoveRule.physical(move("SURF", "SPE"), reason), MoveRule.Battle(), null).banned, "special moves are fine")
        // Vanilla pool, Emerald's exception: 399 or lower, or 400 to 410 that will evolve.
        val vanillaRun = RuleMarks.Run("kaizo", natDex = false, family)
        assertNull(RuleMarks.physicalBan(vanillaRun, "HUGE POWER", bst = 405, canEvolve = true))
        assertNotNull(RuleMarks.physicalBan(vanillaRun, "HUGE POWER", bst = 405, canEvolve = false))
        // Held items by name.
        assertNotNull(RuleMarks.itemLine(natDexRun, "LEFTOVERS"))
        assertNotNull(RuleMarks.itemLine(natDexRun, "CHOICE SCARF"))
        assertNull(RuleMarks.itemLine(natDexRun, "ORAN BERRY"))
    }

    @Test
    fun `the run in play's rules follow its pool`() {
        val d = java.nio.file.Files.createTempDirectory("hns-rules").toFile()
        HnsPool.choose(d, com.ironmonone.app.engine.HnsEngine.Pool.VANILLA)
        assertFalse(HnsPool.rulesNatDex(RomKind.HEARTSOUL_KAIZO_206, d))
        HnsPool.choose(d, com.ironmonone.app.engine.HnsEngine.Pool.NATDEX)
        assertTrue(HnsPool.rulesNatDex(RomKind.HEARTSOUL_KAIZO_206, d))
        assertTrue(HnsPool.rulesNatDex(RomKind.EMERALD_NATDEX_121, d))
        assertFalse(HnsPool.rulesNatDex(RomKind.EMERALD_U, d))
        d.deleteRecursively()
    }
}
