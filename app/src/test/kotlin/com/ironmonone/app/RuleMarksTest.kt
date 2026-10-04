package com.ironmonone.app

import androidx.compose.ui.graphics.Color
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Banned held items and abilities per mode (Blake, 2026-10-04), from the rulesets the app ships; see RuleMarks. Each
 * rule's own words are quoted in RuleMarks beside its list.
 */
class RuleMarksTest {
    private fun run(mode: String, natDex: Boolean = false, family: String = "FRLG") = RuleMarks.Run(mode, natDex, family)
    private fun item(mode: String, name: String, moves: List<String> = emptyList()) = RuleMarks.itemLine(run(mode), name, moves) != null
    private fun ability(mode: String, name: String, bst: Int, evolves: Boolean = false, natDex: Boolean = false) =
        RuleMarks.abilityLine(run(mode, natDex), name, bst, evolves)

    @Test
    fun `Standard bans Lucky Egg, Ultimate adds Leftovers, Soul Dew and Everstone, and both keep every ability`() {
        assertTrue(item("standard", "LUCKY EGG"))
        assertFalse(item("standard", "LEFTOVERS"))
        for (n in listOf("Lucky Egg", "LEFTOVERS", "Soul Dew", "EVERSTONE")) assertTrue(item("ultimate", n), n)
        for (n in listOf("FOCUS BAND", "CHOICE BAND", "QUICK CLAW")) assertFalse(item("ultimate", n), n)
        for (m in listOf("standard", "ultimate")) assertNull(ability(m, "BATTLE ARMOR", 500), m)
    }

    @Test
    fun `Kaizo allows only items used up when they work, evolution items and a Smoke Ball`() {
        for (n in listOf("LEFTOVERS", "CHOICE BAND", "FOCUS BAND", "BRIGHTPOWDER", "QUICK CLAW", "SCOPE LENS", "EXP. SHARE",
            "Focus Sash", "Life Orb", "Choice Scarf", "Bright Powder", "Never-Melt Ice", "PINK BOW"))
            assertTrue(item("kaizo", n), n)
        for (n in listOf("ORAN BERRY", "Sitrus Berry", "WHITE HERB", "MENTAL HERB", "Power Herb", "SMOKE BALL", "KING'S ROCK",
            "METAL COAT", "UP-GRADE", "DEEPSEATOOTH", "Razor Claw", "FIRE STONE", "TM26", "POTION", "-", "", "---"))
            assertFalse(item("kaizo", n), n)
        for (m in listOf("kaizodoubles", "ironmonjourney", "superkaizo", "evokaizo", "survival"))
            assertEquals(item("kaizo", "LEFTOVERS"), item(m, "LEFTOVERS"), m)
        assertEquals("Banned in this run: holding LEFTOVERS. You may hold it in the lab fight.", RuleMarks.itemLine(run("kaizo"), "LEFTOVERS"))
    }

    @Test
    fun `Survival with Cut may hold anything but Lucky Egg and Everstone`() {
        assertTrue(item("survival", "LEFTOVERS"))
        val cut = listOf("TACKLE", "CUT")
        assertFalse(item("survival", "LEFTOVERS", cut))
        assertFalse(item("survival", "CHOICE BAND", cut))
        assertTrue(item("survival", "LUCKY EGG", cut))
        assertTrue(item("survival", "EVERSTONE", cut))
    }

    @Test
    fun `Survival Revival and Chaos Kaizo name their own lists`() {
        for (n in listOf("LUCKY EGG", "LEFTOVERS", "SHELL BELL", "FOCUS BAND", "BRIGHTPOWDER")) assertTrue(item("survivalrevival", n), n)
        for (n in listOf("EVERSTONE", "CHOICE BAND", "QUICK CLAW", "SOUL DEW")) assertFalse(item("survivalrevival", n), n)
        for (n in listOf("LEFTOVERS", "SHELL BELL", "LUCKY EGG", "EVERSTONE", "SOUL DEW")) assertTrue(item("chaoskaizo", n), n)
        for (n in listOf("CHOICE BAND", "FOCUS BAND", "Focus Sash")) assertFalse(item("chaoskaizo", n), n)
    }

    @Test
    fun `a mode with no item or ability rules marks nothing`() {
        for (m in listOf("nuzlocke", "", "roguemon")) {
            assertFalse(item(m, "LEFTOVERS"), m)
            assertNull(ability(m, "BATTLE ARMOR", 600), m)
        }
        assertNull(RuleMarks.itemLine(null, "LEFTOVERS"))
        assertNull(RuleMarks.abilityLine(null, "HUGE POWER", 600, false))
    }

    private fun mv(cat: String, blank: Boolean = false) =
        PcMove(name = "Fire Punch", pp = 15, ppMax = 15, power = 75, acc = 100, color = Color.White, category = cat, blank = blank)
    private fun physicalX(mode: String, ability: String, bst: Int?, evolves: Boolean, cat: String, natDex: Boolean = false) =
        MoveRule.physical(mv(cat), RuleMarks.physicalBan(run(mode, natDex), ability, bst, evolves)) != null

    @Test
    fun `Huge Power and Pure Power put the X on physical moves, every branch of Kaizo's exceptions`() {
        // "Banned abilities don't apply to: Pokemon with BST 399 or lower; Pokemon with BST 400-410 (inclusive) that will eventually evolve"
        assertFalse(physicalX("kaizo", "HUGE POWER", 399, evolves = false, "PHY"), "399")
        assertFalse(physicalX("kaizo", "HUGE POWER", 400, evolves = true, "PHY"), "400 evolving")
        assertTrue(physicalX("kaizo", "HUGE POWER", 400, evolves = false, "PHY"), "400 not evolving")
        assertFalse(physicalX("kaizo", "HUGE POWER", 410, evolves = true, "PHY"), "410 evolving")
        assertTrue(physicalX("kaizo", "HUGE POWER", 410, evolves = false, "PHY"), "410 not evolving")
        assertTrue(physicalX("kaizo", "HUGE POWER", 411, evolves = true, "PHY"), "411 evolving")
        assertTrue(physicalX("kaizo", "Pure Power", 411, evolves = false, "PHY"), "Pure Power too")
        assertFalse(physicalX("kaizo", "HUGE POWER", null, false, "PHY"), "an unknown BST")
        // Special and status moves never.
        assertFalse(physicalX("kaizo", "HUGE POWER", 500, false, "SPE"))
        assertFalse(physicalX("kaizo", "HUGE POWER", 500, false, "STA"))
        assertNull(MoveRule.physical(mv("PHY", blank = true), RuleMarks.physicalBan(run("kaizo"), "HUGE POWER", 500, false)), "a blank slot")
        // Nat. Dex and MaxDex: "increased to 420 inclusive".
        assertFalse(physicalX("kaizo", "HUGE POWER", 420, false, "PHY", natDex = true))
        assertTrue(physicalX("kaizo", "HUGE POWER", 421, true, "PHY", natDex = true))
        // Every mode with Kaizo's Banned Abilities rule; not Evo Kaizo ("All Abilities are LEGAL"), Chaos Kaizo, Survival
        // (its own list bans the ability itself), Standard, Ultimate or Nuzlocke.
        for (m in listOf("kaizo", "kaizodoubles", "ironmonjourney", "superkaizo", "survivalrevival")) assertTrue(physicalX(m, "HUGE POWER", 500, false, "PHY"), m)
        for (m in listOf("evokaizo", "chaoskaizo", "survival", "standard", "ultimate", "nuzlocke")) assertFalse(physicalX(m, "HUGE POWER", 500, false, "PHY"), m)
        assertFalse(physicalX("kaizo", "SWIFT SWIM", 500, false, "PHY"))
        assertEquals("Banned in this run: a physical move with HUGE POWER. Banned abilities don't apply to a Pokemon with BST 399 or lower, or BST 400 to 410 that will evolve.",
            MoveRule.physical(mv("PHY"), RuleMarks.physicalBan(run("kaizo"), "HUGE POWER", 500, false))?.line)
    }

    @Test
    fun `physical is the split the card shows, by type in a Gen 3 game and by move where the build splits them`() {
        // Fire Punch is Fire: special by type in vanilla Gen 3 (gen3Category, GbaTracker), physical by move on Nat. Dex,
        // MaxDex and the DS games (their per-move byte). The X follows the row's category.
        assertFalse(physicalX("kaizo", "HUGE POWER", 500, false, "SPE"), "vanilla Gen 3: Fire is special")
        assertTrue(physicalX("kaizo", "HUGE POWER", 500, false, "PHY", natDex = true), "Nat. Dex: Fire Punch is physical")
        val rows = File("../tracker-gba/src/main/kotlin/com/ironmonone/tracker/GbaTracker.kt").readText()
        assertTrue("} else gen3Category(d[1], d[0])" in rows, "the row's category: the ROM's byte where the build has one, else by type")
    }

    @Test
    fun `Huge Power and Pure Power themselves are never marked, only their moves`() {
        for (m in listOf("kaizo", "kaizodoubles", "ironmonjourney", "superkaizo", "survivalrevival", "evokaizo", "chaoskaizo"))
            for (a in listOf("HUGE POWER", "Pure Power")) assertNull(ability(m, a, 600), "$m $a")
    }

    private val EVOS_LOG: String get() = javaClass.getResource("/logs/emerald.log")!!.readText(Charsets.UTF_8)

    @Test
    fun `this seed's evolutions decide the 400 to 410 exception, the game's own where the seed kept them`() {
        val randomized = RandomizerLog.parse(EVOS_LOG)
        val evolves = RuleMarks.seedEvolutions(randomized)
        assertNotNull(evolves)
        val r = run("kaizo").copy(evolves = evolves)
        assertTrue(r.canEvolve("CHARMANDER", tracker = false), "the seed: Charmander -> Quilava")
        assertFalse(r.canEvolve("ARBOK", tracker = true), "in the log, with no evolution in this seed")
        assertTrue(r.canEvolve("NOT IN THE LOG", tracker = true), "a name the log lacks: the tracker's answer")
        assertFalse(r.canEvolve("NOT IN THE LOG", tracker = false))
        assertTrue(RandomizerLog.parse(EVOS_LOG.substringBefore("--Randomized Evolutions--") + "--Pokemon Base Stats & Types--" + EVOS_LOG.substringAfter("--Pokemon Base Stats & Types--")).pokemonNamed("ARBOK") != null)
        assertNull(RuleMarks.seedEvolutions(RandomizerLog.parse(EVOS_LOG.substringBefore("--Randomized Evolutions--") + "--Pokemon Base Stats & Types--" + EVOS_LOG.substringAfter("--Pokemon Base Stats & Types--"))),
            "no Randomized Evolutions: the game's own, which the tracker reads")
        assertTrue(run("kaizo").canEvolve("ARBOK", tracker = true), "no log read: the tracker's answer")
        // 405 BST with Huge Power: exempt only while this seed lets it evolve.
        assertFalse(physicalX("kaizo", "HUGE POWER", 405, r.canEvolve("CHARMANDER", false), "PHY"))
        assertTrue(physicalX("kaizo", "HUGE POWER", 405, r.canEvolve("ARBOK", true), "PHY"))
    }

    @Test
    fun `Super Kaizo adds Battle Armor, Shell Armor and Magic Guard with Kaizo's exceptions, not Pickup, No Guard or Poison Heal`() {
        val except = "Banned abilities don't apply to a Pokemon with BST 399 or lower, or BST 400 to 410 that will evolve."
        for (n in listOf("BATTLE ARMOR", "SHELL ARMOR", "Magic Guard"))
            assertEquals("Banned in this run: $n. $except You may use it in the lab fight.", ability("superkaizo", n, 450), n)
        for (n in listOf("PICKUP", "NO GUARD", "Poison Heal")) assertNull(ability("superkaizo", n, 450), n)
        assertNull(ability("superkaizo", "BATTLE ARMOR", 399))
        assertNull(ability("superkaizo", "BATTLE ARMOR", 400, evolves = true))
        assertNotNull(ability("superkaizo", "BATTLE ARMOR", 400, evolves = false))
        assertNull(ability("superkaizo", "BATTLE ARMOR", 410, evolves = true))
        assertNotNull(ability("superkaizo", "BATTLE ARMOR", 411, evolves = true))
        assertNull(ability("superkaizo", "BATTLE ARMOR", 420, natDex = true))
        assertTrue(ability("superkaizo", "BATTLE ARMOR", 421, natDex = true)!!.contains("BST 420 or lower"))
        assertNull(ability("kaizo", "BATTLE ARMOR", 500), "Kaizo itself bans only the two")
        assertNull(ability("survivalrevival", "BATTLE ARMOR", 500), "Revival keeps Kaizo's abilities only")
    }

    @Test
    fun `Survival bans its list outright from 400 BST, with no evolving exception`() {
        for (n in listOf("HUGE POWER", "PURE POWER", "BATTLE ARMOR", "SHELL ARMOR", "Parental Bond", "Protean", "Fur Coat", "Magic Guard"))
            assertEquals("Banned in this run: $n. This run bans it for a Pokemon with BST 400 or more. You may use it in the lab fight.", ability("survival", n, 400), n)
        assertNull(ability("survival", "HUGE POWER", 399))
        assertNotNull(ability("survival", "HUGE POWER", 405, evolves = true))
        assertNull(ability("survival", "Protean", 420, natDex = true))
        assertNotNull(ability("survival", "Protean", 421, natDex = true))
        assertNull(RuleMarks.physicalBan(run("survival"), "HUGE POWER", 500, false), "Survival bans the ability, not the moves")
    }

    @Test
    fun `Black and White allow them in the first three battles`() {
        assertEquals("Banned in this run: holding Leftovers. You may hold it in the first three battles, N's included.",
            RuleMarks.itemLine(run("kaizo", family = "BW"), "Leftovers"))
    }

    @Test
    fun `the item names up to Gen 5 are the games' own`() {
        fun key(s: String) = MoveRule.key(s)
        val known = listOf("../tracker-gba/src/main/resources/gen2/items.tsv", "../tracker-nds/src/main/resources/gen5/items.tsv",
            "../tracker-nds/src/main/resources/nds/item-desc.tsv").flatMap { p ->
            File(p).readLines(Charsets.UTF_8).flatMap { it.split('\t') }.map(::key)
        }.toSet()
        assertTrue(known.size > 500, "the tables were read")
        // Gen 6 on, in Nat. Dex and MaxDex only, which name their items in the ROM.
        val later = setOf("Assault Vest", "Safety Goggles", "Protective Pads", "Heavy-Duty Boots", "Utility Umbrella",
            "Terrain Extender", "Loaded Dice", "Covert Cloak", "Clear Amulet", "Punching Glove", "Ability Shield", "Fairy Feather",
            "Pixie Plate")
        for (n in RuleMarks.KEPT_WHILE_HELD - later) assertTrue(key(n) in known, "$n is no Gen 2 to 5 item")
    }

    @Test
    fun `both panels mark your item and ability behind the switch, which starts on`() {
        fun read(name: String) = File("src/main/kotlin/com/ironmonone/app/$name").readText().replace("\r\n", "\n")
        assertTrue("var ruleMarks by mutableStateOf(true)" in read("TrackerOptions.kt"), "on by default (Blake, 2026-10-04)")
        val gba = read("TrackerPanel.kt")
        assertTrue("itemBan = RuleMarks.itemLine(ruleRun.takeIf { TrackerOptions.ruleMarks }, p.itemName, p.moveNames)," in gba)
        assertTrue("abilityBan = RuleMarks.abilityLine(ruleRun.takeIf { TrackerOptions.ruleMarks }, p.abilityName, p.base?.bst,\n                            ruleRun?.canEvolve(p.speciesName, p.evo != null) == true))" in gba)
        val ds = read("NdsTrackerPanel.kt")
        assertTrue("itemBan = RuleMarks.itemLine(ruleRun.takeIf { TrackerOptions.ruleMarks }," in ds)
        assertTrue("abilityBan = RuleMarks.abilityLine(ruleRun.takeIf { TrackerOptions.ruleMarks }," in ds)
        assertTrue("if (rules == null || !on) return { it }" in read("MoveRule.kt"), "the move X follows the switch too")
        assertTrue("GearToggle(\"Mark banned items, abilities and moves\", TrackerOptions.ruleMarks)" in read("TrackerGearDialog.kt"))
        val head = read("PcTracker.kt")
        assertTrue("RuleMarkedLine(itemLine, itemBan, \"Held item\", onItemTap)" in head)
        assertTrue("RuleMarkedLine(abilityLine, abilityBan, \"Ability\", onAbilityTap)" in head)
    }
}
