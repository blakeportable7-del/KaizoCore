package com.ironmonone.app

import com.ironmonone.core.RomKind
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Favorites follow the run's own rulebook (Blake, 2026-10-01: "make sure all kaizo runs have the right rules, and base
 * it on whatever game is doing a run because different kaizo's have different rules, and different kaizo modes have
 * different rules"). Each test reads every bundled book, so a book that changes its clause fails here.
 */
class FavoriteRulesTest {
    private val books = File("src/main/assets/rulesets")
    private fun allBooks(): List<File> = books.listFiles()!!.filter { it.isDirectory }.flatMap { d -> d.listFiles()!!.filter { it.name.endsWith(".md") } }

    private val kindOf = mapOf(
        "RBY" to RomKind.RED_U, "GSC" to RomKind.CRYSTAL_U, "FRLG" to RomKind.FIRERED_U_V11, "RSE" to RomKind.EMERALD_U,
        "DPPt" to RomKind.PLATINUM_U, "HGSS" to RomKind.HEARTGOLD_U, "BW" to RomKind.BLACK_U, "B2W2" to RomKind.BLACK2_U,
        "FRLG-NatDex" to RomKind.FIRERED_NATDEX_121, "RSE-NatDex" to RomKind.EMERALD_NATDEX_121,
        "FRLG-MaxDex" to RomKind.FIRERED_MAXDEX_10,
    )
    private val generation = mapOf("RBY" to 1, "GSC" to 2, "FRLG" to 3, "RSE" to 3, "DPPt" to 4, "HGSS" to 4, "BW" to 5, "B2W2" to 5)

    @Test
    fun `every book's favorites count is the one the app gives its game`() {
        assertEquals(72, allBooks().size, "the books bundled today")
        for (f in allBooks()) {
            val text = f.readText()
            val family = f.parentFile.name
            assertTrue("Favorites Clause" in text, f.path)
            // Three, one more each generation after the third; nine on a Nat. Dex build.
            val want = if ("up to 9 favorites" in text) 9 else 3 + maxOf(0, generation.getValue(family.substringBefore('-')) - 3)
            assertEquals(want, Favorites.slotCount(kindOf.getValue(family)), f.path)
        }
    }

    @Test
    fun `the modes that allow no legendary favorite are exactly the books that say so`() {
        for (f in allBooks()) {
            // Super Kaizo's "No Legendary favorites", Survival's "No Legendaries or 580+ BST Pokemon as favorites"; not
            // the clause every book has, "No Legendaries ... with the exception that you may select up to one".
            val says = f.readText().lines().any { "No Legendar" in it && "favorites" in it && "exception" !in it }
            assertEquals(says, f.nameWithoutExtension in FavoriteRules.NO_LEGENDARY_MODES, f.path)
        }
    }

    @Test
    fun `the legendary list is the Nat Dex engine's own, base species only`() {
        val pokemon = File("../engine-natdex/src/com/dabomstew/pkrandom/pokemon/Pokemon.java").readText()
        val species = File("../engine-natdex/src/com/dabomstew/pkrandom/constants/Species.java").readText()
        // The top-level constants: the nested Formes classes reuse names with other numbers.
        val top = species.substring(0, Regex("static final class [A-Za-z0-9]+").find(species)!!.range.first)
        val value = Regex("public static final int ([A-Za-z0-9]+) = ([0-9]+);").findAll(top).associate { it.groupValues[1] to it.groupValues[2].toInt() }
        val block = Regex("List<Integer> legendaries = Arrays[.]asList[(](.*?)[)];", RegexOption.DOT_MATCHES_ALL).find(pokemon)!!.groupValues[1]
        val engine = Regex("Species[.]([A-Za-z0-9]+)").findAll(block).map { value.getValue(it.groupValues[1]) }.filter { it <= 1025 }.toSet()
        assertEquals(engine, FavoriteRules.LEGENDARY)
    }

    @Test
    fun `the Strong Legendary and Mythical list is the Nat Dex engine's own, base species only`() {
        val pokemon = File("../engine-natdex/src/com/dabomstew/pkrandom/pokemon/Pokemon.java").readText()
        val species = File("../engine-natdex/src/com/dabomstew/pkrandom/constants/Species.java").readText()
        val top = species.substring(0, Regex("static final class [A-Za-z0-9]+").find(species)!!.range.first)
        val value = Regex("public static final int ([A-Za-z0-9]+) = ([0-9]+);").findAll(top).associate { it.groupValues[1] to it.groupValues[2].toInt() }
        val block = Regex("List<Integer> strongLegendaries = Arrays[.]asList[(](.*?)[)];", RegexOption.DOT_MATCHES_ALL).find(pokemon)!!.groupValues[1]
        val engine = Regex("Species[.]([A-Za-z0-9]+)").findAll(block).map { value.getValue(it.groupValues[1]) }.filter { it <= 1025 }.toSet()
        assertEquals(engine, FavoriteRules.STRONG_OR_MYTHICAL)
        assertEquals(value.getValue("cosmoem"), FavoriteRules.COSMOEM)
        for (n in listOf("Mewtwo", "Kyogre-P", "Mew", "Pecharunt", "Cosmoem")) assertTrue(FavoriteRules.isStrongOrMythical(n), n)
        for (n in listOf("Articuno", "Regigigas", "Latias", "Latias-M", "Dragonite")) assertFalse(FavoriteRules.isStrongOrMythical(n), n)
    }

    @Test
    fun `the Kaizo IronMON screen shows the mode's own lines and flags what breaks them`() {
        val kaizo = FavoriteRules.lines(File(books, "FRLG/kaizo.md").readText())
        assertTrue(kaizo.any { it.startsWith("Favorites Clause:") }, kaizo.toString())
        assertTrue(kaizo.any { "under 600 BST" in it })
        assertTrue(kaizo.none { "https://" in it || it.startsWith("-") })
        assertTrue(FavoriteRules.lines(File(books, "RSE-NatDex/kaizo.md").readText()).any { "up to 9 favorites" in it })
        assertTrue(FavoriteRules.lines(File(books, "FRLG/survival.md").readText()).any { "580+ BST" in it })
        // Legendaries, forms by their species.
        for (n in listOf("Rayquaza", "Kyogre", "Articuno", "Celebi", "Zacian", "Pecharunt")) assertTrue(FavoriteRules.isLegendary(n), n)
        for (n in listOf("Salamence", "Metagross", "Pikachu", "Garchomp")) assertFalse(FavoriteRules.isLegendary(n), n)
        // Kaizo: one legendary is allowed, two are not.
        assertTrue(FavoriteRules.problems(listOf("Articuno", "Pikachu", ""), "kaizo").isEmpty())
        assertEquals(1, FavoriteRules.problems(listOf("Mewtwo", "Lugia", "Pikachu"), "kaizo").size)
        // Super Kaizo and Survival: none at all.
        assertEquals(1, FavoriteRules.problems(listOf("Articuno", "Pikachu"), "superkaizo").size)
        assertEquals(1, FavoriteRules.problems(listOf("Raikou"), "survival").size)
        // The app's own words follow the copy rules.
        val own = listOf(FavoriteRules.HEAD) + FavoriteRules.problems(listOf("Mewtwo", "Lugia"), "kaizo") + FavoriteRules.problems(listOf("Mew"), "survival")
        for (s in own) assertFalse(Char(0x2014) in s || Char(0x2013) in s, s)
    }

    @Test
    fun `the screen reads the book of the game and mode picked`() {
        val run = File("src/main/kotlin/com/ironmonone/app/RunScreen.kt").readText().replace("\r\n", "\n")
        // With the game itself, so MaxDex reads its own book (FRLG-MaxDex, the Nat. Dex 1.1.3 lines), not Nat. Dex 1.2.1's.
        assertTrue("Rules.text(context, Rules.dirFor(k.family, k.isNatDex, k), favMode)" in run)
        assertTrue("FavoriteRulesBlock(favBook, favMode, favSlots)" in run)
        assertTrue("val favMode = RulesetCatalog.modeOf(modes, selectedSettings)?.key" in run)
    }
}
