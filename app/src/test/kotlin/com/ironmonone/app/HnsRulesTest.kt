package com.ironmonone.app

import com.ironmonone.core.RomKind
import java.io.File
import java.io.FileInputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Heart & Soul as a full KaizoCore game (docs/NEW-GAME-CHECKLIST.md, 2026-10-05): the rules page: Heart & Soul's own book and the Mode row's lines.
 */
class HnsRulesTest {
    private val hns = RomKind.HEARTSOUL_KAIZO_206
    private val books = File("src/main/assets/rulesets")
    private val dir = File(books, "HnS")
    private val presets = File("src/main/assets/presets").listFiles { f -> f.extension == "rnqs" }!!.toList()
    private val offered = RulesetCatalog.forRom(hns, presets)

    @Test
    fun `RULES opens Heart and Soul's own book, HeartGold and SoulSilver's game rules with the Nat Dex changes`() {
        assertEquals("HnS", Rules.dirFor(hns.family, hns.isNatDex, hns))
        val keys = offered.map { it.key }.toSet()
        assertTrue("kaizo" in keys && "survival" in keys, "$keys")
        for (mode in keys) {
            val text = File(dir, "$mode.md").readText()
            assertTrue(text.startsWith("# Heart & Soul: "), mode)
            assertTrue("In KaizoCore, Heart & Soul is held to the rules of the game it retells" in text, mode)
            assertTrue("the trash can in Elm's lab" in text, "$mode: the PC item's place (comfort build F0C6236C)")
            assertTrue("## HeartGold and SoulSilver: game-specific rules" in text, mode)
            assertTrue("To win Ironmon you must defeat Red at the top of Mt. Silver." in text, mode)
            assertTrue("## Nat. Dex ruleset changes" in text, mode)
            assertTrue("this section is for a run with the Nat. Dex pool only; a Vanilla pool run skips it" in text, mode)
            assertFalse("Ruby, Sapphire and Emerald: game-specific rules" in text, "$mode: Hoenn's rules are not Heart & Soul's")
            assertFalse("\u2014" in text, mode)
        }
        // Kaizo and up: the Johto Kaizo rule; Ultimate and up: the dungeons of the Johto story.
        assertTrue("Ruins of Alph" in File(dir, "kaizo.md").readText())
        assertTrue("Radio Tower" in File(dir, "ultimate.md").readText())
        assertTrue("Ruins of Alph" !in File(dir, "standard.md").readText())
        // Survival: the Kanto heals of a Johto game.
        assertTrue("If you complete the Elite 4 in a Johto game you earn an additional 7 heals for Kanto" in File(dir, "survival.md").readText())
        // Super Kaizo: HeartGold and SoulSilver's own section (the Johto gyms, Kanto, Red in hail), and the smart trainers note.
        val sk = File(dir, "superkaizo.md").readText()
        assertTrue("### Super Kaizo, HeartGold and SoulSilver" in sk && "Must fight Red with hail weather active" in sk)
        assertTrue("Heart & Soul's Super Kaizo settings make every trainer pick its moves the smart way" in sk)
    }

    /**
     * The levels (2026-10-06): Standard's rule says 50%, but Heart & Soul runs the Emerald Nat. Dex v1.2 settings files,
     * which raise trainer and wild levels 60% for Kaizo and every mode built on it. Each page is checked against the
     * very file its mode runs, so a page and its file cannot drift apart.
     */
    @Test
    fun `each page says 60 percent levels exactly where its own settings file raises them 60 percent`() {
        val line = "In KaizoCore, Heart & Soul's settings files for Kaizo and every mode built on it raise trainer and wild Pokémon levels by 60%, not 50%."
        assertTrue(offered.size >= 10, "${offered.map { it.key }}")
        for (m in offered) {
            val s = FileInputStream(m.preset).use { com.dabomstew.pkrandom.Settings.read(it) }
            val levels = listOf(s.isTrainersLevelModified, s.trainersLevelModifier, s.isWildLevelsModified, s.wildLevelModifier)
            val text = File(dir, "${m.key}.md").readText()
            when (m.key) {
                "standard", "ultimate" -> {
                    assertEquals(listOf<Any>(true, 50, true, 50), levels, m.preset.name)
                    assertFalse(line in text, m.key)
                }
                else -> {
                    assertEquals(listOf<Any>(true, 60, true, 60), levels, m.preset.name)
                    assertEquals(1, text.split(line).size - 1, "${m.key}: the 60% line, once")
                    // Under Standard's own "increased by 50%" rule, where a player reads it.
                    val rule = text.indexOf("- Randomize the game:")
                    assertTrue(rule >= 0 && text.indexOf(line) > rule && text.indexOf(line) < text.indexOf("- Catch or Kill 1 per Route"), m.key)
                }
            }
        }
    }

    /**
     * What the game itself does in every IronMON mode (tools/hns/patches 0004 to 0034, HnsEngine), said once at the top of
     * each page, and where Heart & Soul differs from HeartGold and SoulSilver's own rules, said under that rule.
     */
    @Test
    fun `each page says what Heart and Soul does in KaizoCore once, and where its places differ from HeartGold and SoulSilver`() {
        val game = listOf(
            "You cannot run from a trainer battle or forfeit it",
            "Catching a Pokémon gives no EXP.",
            "Wild Pokémon come in turn",
            "No Pokémon learns an HM move by level or knows one when you meet it",
            "The lab fight these rules name is your first rival battle, outside Cherrygrove City.",
            "A held item that gets used up in battle stays used up.",
            "The Day Care on Route 34 takes no Pokémon.",
            "The Exp. Share that gives EXP to the whole party is off",
            "Only a Pokémon that was sent out in the battle can find an item with Pickup.",
            "A TM breaks after one use",
            "Shiny odds are Gen 3's 1 in 8,192",
            "A hidden item is never a TM.",
            "The start menu has an HM entry.",
            "Every Pokémon Center has a Name Rater: the Meowth",
        )
        for (m in offered) {
            val text = File(dir, "${m.key}.md").readText()
            val top = text.substringBefore("## Standard IronMON")
            assertTrue("### What Heart & Soul does in KaizoCore" in top, m.key)
            for (g in game) assertEquals(1, text.split(g).size - 1, "${m.key}: \"$g\" once, at the top")
            for (g in game) assertTrue(g in top, "${m.key}: \"$g\" at the top")
            // The HeartGold and SoulSilver lines that are wrong for Heart & Soul carry its own fact right under them.
            assertTrue("the top of Mt. Silver always snows, whatever the date or time" in text, m.key)
            assertTrue("Heart & Soul's red Gyarados never comes back" in text, m.key)
            assertTrue("no Heart & Soul event waits for a day of the week" in text, m.key)
            assertTrue("a call from Oak that opens Mt. Silver" in text, m.key)
            assertEquals(m.key != "standard", "the Cianwood pharmacist sends the Secret Potion himself" in text, m.key)
            assertEquals(m.key != "standard", "Heart & Soul's Olivine Pokémon Center has no trainer in it." in text, m.key)
            assertEquals(m.key != "standard" && m.key != "ultimate", "Heart & Soul's Ruins of Alph are not dark" in text, m.key)
        }
        // Free heals: outside a dungeon each one counts against Survival's limits; inside one they are banned (Kaizo and up).
        for (mode in listOf("survival", "survivalrevival")) {
            val text = File(dir, "$mode.md").readText()
            assertTrue("every free heal of the whole party outside a dungeon counts as one of these" in text, mode)
            assertTrue("the heal machine in Elm's lab, the teacher in National Park, and any other person who heals your party" in text, mode)
        }
        for (m in offered) {
            val text = File(dir, "${m.key}.md").readText()
            val note = "a person or bed that heals you inside a Heart & Soul dungeon is off limits"
            assertEquals(if (m.key == "standard" || m.key == "ultimate") 0 else 1, text.split(note).size - 1, m.key)
            if (note in text) assertTrue(text.indexOf(note) > text.indexOf("- No Healing Stations in Dungeons:"), m.key)
        }
        val sk = File(dir, "superkaizo.md").readText()
        assertTrue("Heart & Soul's Bug-Catching Contest is open every day, once a day, so no time change and no Celebi is needed." in sk)
        assertTrue("Heart & Soul's newer form of hail, so Red is always fought in it" in sk)
        // Evo Kaizo: no evo loops in either pool (Blake, 2026-10-06), as the Nat. Dex rules say.
        val evo = File(dir, "evokaizo.md").readText()
        assertTrue("In KaizoCore, Heart & Soul's Evo Kaizo has no evo loops, in either pool" in evo)
        assertFalse("evo loops happen" in evo)
        // No loops on either pool, so rules 10 and 11 (break a loop, pivot to checkpoints) apply on neither, and rule 4's
        // checkpoint pivots go with them (Blake, 2026-10-06: no loops "as the Nat. Dex rules say").
        assertTrue("pivots are banned, no exceptions, in place of rule 4" in evo)
        assertFalse("rules 4 and 11 stand as written" in evo)
        assertFalse("On a Nat. Dex build, the Evo Kaizo part" in evo, "Heart & Soul's own line, by pool")
    }

    @Test
    fun `the Mode row's lines follow Heart and Soul's book, which holds both pools`() {
        val ultimate = RulesetCatalog.modeLine("ultimate", false, hns.family)
        // A Vanilla pool run keeps Ultimate's ban on HM moves in battle; the Nat. Dex pool takes the Nat. Dex change.
        assertNotEquals(RulesetCatalog.modeLine("ultimate", natDex = true), ultimate)
        assertNotEquals(RulesetCatalog.modeLine("ultimate"), ultimate)
        assertTrue("no HM moves in battle" in ultimate && "with the Nat. Dex pool, none taught by an HM item" in ultimate, ultimate)
        val book = File(dir, "ultimate.md").readText()
        assertTrue("- No HM Moves: No HM Moves in Battle." in book)
        assertTrue("Ultimate and harder: HM moves are allowed to be used in battle, as long as the moves are not taught with the HM items." in book)
        val survival = RulesetCatalog.modeLine("survival", false, hns.family)
        assertTrue("seven more for Kanto" in survival, survival)
        assertEquals(RulesetCatalog.modeLine("survival", false, "HGSS"), survival)
    }
}
