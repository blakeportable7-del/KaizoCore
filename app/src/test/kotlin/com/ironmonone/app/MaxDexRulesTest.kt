package com.ironmonone.app

import com.ironmonone.core.RomKind
import com.ironmonone.tracker.GbaTracker
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * MaxDex's rules. Trip's own rules page ended as one line, "Same Rules as NatDex", and was deleted the same day
 * (2026-07-03; every version is in tools/upr-settings/community/MaxDex-Ruleset.md). The Nat. Dex page it points to has a
 * v1.2.0+ section and a v1.0.0 to v1.1.3 one, and MaxDex is built on Nat. Dex 1.1.3. Blake, 2026-10-03: "Max dex is
 * allowed a bst 600 pokemon". So MaxDex follows the 1.1.3 section (BstRule, FavoriteBall), Nat. Dex 1.2.1 keeps its own,
 * and MaxDex's RULES box is the Nat. Dex book with the 1.1.3 rules and a MaxDex section (tools/rules/build_rules.py
 * writes FRLG-MaxDex/kaizo.md). Each test that would pass on the 1.2.1 lines says so beside a check that does not.
 */
class MaxDexRulesTest {
    private val k = RomKind.FIRERED_MAXDEX_10
    private fun joined() = JoinedForms(Files.createTempDirectory("joined").resolve("joined.txt").toFile(), attempt = 1)
    /** The tracker's id for a National Dex number: MaxDex's ids are Nat. Dex 1.2.1's up to 1235. */
    private fun id(national: Int): Int = Favorites.fromNational(national)!!
    private val maxDexKaizo = BstRule.lines("kaizo", natDex = true, maxDex = true)
    private val natDexKaizo = BstRule.lines("kaizo", natDex = true)

    // ------------------------------------------------------------------ what stays the Nat. Dex rules

    @Test
    fun `MaxDex plays by the Nat Dex rules for favorites and moves, nine favorites and the Nat Dex move bans`() {
        assertEquals(9, Favorites.slotCount(k))
        val rules = assertNotNull(MoveRule.rules("kaizo", k.family, k.isNatDex, k.baseId ?: k.id))
        // An HM move is the Nat. Dex rule, said on the card with no X.
        assertEquals(MoveRule.When.UNSEEN, rules.banOf("Surf")?.whenBanned)
        // Brock is trainer 414 in MaxDex as in FireRed, so the boss-only bans find him.
        assertTrue(rules.battle(inBattle = true, wild = false, trainerId = 414).boss)
        // The healing, draining and switching moves are marked under the names MaxDex's own move list uses; the one it
        // does not have is Shed Tail.
        val names = File("../tracker-gba/src/main/resources/maxdex/moves.tsv").readLines(Charsets.UTF_8).mapNotNull { it.split('\t').getOrNull(1) }
        val keys = names.map(MoveRule::key).toSet()
        val banned = MoveRule.HP_HEALING + MoveRule.DRAINING + MoveRule.STATUS_HEALING + MoveRule.SWITCHING + listOf("Leech Seed", "Spore", "Assist")
        assertEquals(listOf("Shed Tail"), banned.filter { MoveRule.key(it) !in keys })
        for (n in banned.filter { MoveRule.key(it) in keys }) assertNotNull(rules.banOf(n), n)
        // The moves the first version of Trip's rules page named are in MaxDex's list, and the Kaizo rules mark them.
        for (n in listOf("Draining Kiss", "Roost", "Psycho Shift", "Drain Punch", "Matcha Gotcha", "Bouncy Bubble", "Sappy Seed", "Sparkly Swirl"))
            assertNotNull(rules.banOf(n), n)
    }

    // ------------------------------------------------------------------ the BST X: the 1.1.3 lines

    @Test
    fun `MaxDex's BST lines are Nat Dex 1_1_3's, and Nat Dex 1_2_1 keeps its own`() {
        assertEquals(BstRule.Lines(own = 601, wild = 600, legendary = 601), BstRule.lines("kaizo", k.isNatDex, maxDex = k.isMaxDex))
        // Red if MaxDex fell back to the 1.2.1 lines: they are 600 and 600, with no legendary line.
        assertEquals(BstRule.Lines(600, 600), BstRule.lines("kaizo", RomKind.FIRERED_NATDEX_121.isNatDex, maxDex = RomKind.FIRERED_NATDEX_121.isMaxDex))
        assertNotEquals(natDexKaizo, maxDexKaizo)
        // "Kaizo, Survival and Super Kaizo", and the modes built on Kaizo with them.
        for (mode in listOf("survival", "superkaizo", "kaizodoubles", "survivalrevival")) assertEquals(maxDexKaizo, BstRule.lines(mode, true, maxDex = true), mode)
        // "If playing Standard or Ultimate, the BST limit is 640 inclusive, and the Legendary evolution exception does not apply."
        for (mode in listOf("standard", "ultimate")) assertEquals(BstRule.Lines(641, 641), BstRule.lines(mode, true, maxDex = true), mode)
        // The 1.1.3 section names no Evo Kaizo, which keeps the Nat. Dex line; the rest draw none, as everywhere.
        assertEquals(BstRule.Lines(own = 601, wild = 600), BstRule.lines("evokaizo", true, maxDex = true))
        for (mode in listOf("chaoskaizo", "ironmonjourney", null)) assertNull(BstRule.lines(mode, true, maxDex = true), "$mode")
        // Play reads MaxDex from the session's game.
        assertTrue("maxDex = session.kind?.isMaxDex == true)" in File("src/main/kotlin/com/ironmonone/app/BstRule.kt").readText())
    }

    @Test
    fun `a starter of 600 BST has no X on MaxDex, and one of 601 has`() {
        val joined = joined()
        // Metagross (600) from the lab: legal. Red on the 1.2.1 line, where the same Metagross has the X.
        assertFalse(BstRule.ownBreaks(600, maxDexKaizo, joined, pid = 0x376L, species = id(376), name = "Metagross"))
        assertTrue(BstRule.ownBreaks(600, natDexKaizo, joined(), pid = 0x376L, species = id(376), name = "Metagross"), "Nat. Dex 1.2.1 bans 600")
        // "600 for starter Pokemon, including legendaries": Mew, 600.
        assertFalse(BstRule.ownBreaks(600, maxDexKaizo, joined, pid = 0x151L, species = 151, name = "Mew"))
        // 601 and up: the X, a legendary or not.
        assertTrue(BstRule.ownBreaks(601, maxDexKaizo, joined, pid = 0x601L, species = 10, name = "Caterpie"))
        assertTrue(BstRule.ownBreaks(670, maxDexKaizo, joined, pid = 0x289L, species = id(289), name = "Slaking"))
        assertTrue(BstRule.ownBreaks(680, maxDexKaizo, joined, pid = 0x150L, species = 150, name = "Mewtwo"))
    }

    @Test
    fun `a wild Pokemon is legal under 600 on MaxDex`() {
        assertFalse(BstRule.wildBreaks(599, maxDexKaizo))
        assertTrue(BstRule.wildBreaks(600, maxDexKaizo), "the 600 is for a starter only")
        assertTrue(BstRule.wildBreaks(680, maxDexKaizo))
    }

    @Test
    fun `evolving to 600 or more is fine on MaxDex, unless into a legendary of 601 or more`() {
        val joined = joined()
        // Dratini grew into Dragonite (600) and Slakoth into Slaking (670): neither is a legendary, so no X.
        assertFalse(BstRule.ownBreaks(300, maxDexKaizo, joined, 0x147L, 147, "Dratini"))
        assertFalse(BstRule.ownBreaks(600, maxDexKaizo, joined, 0x147L, 149, "Dragonite"))
        assertFalse(BstRule.ownBreaks(280, maxDexKaizo, joined, 0x287L, id(287), "Slakoth"))
        assertFalse(BstRule.ownBreaks(670, maxDexKaizo, joined, 0x287L, id(289), "Slaking"))
        // Cosmoem (400) into Solgaleo (680), a legendary past 601: the X, and saying it evolved does not clear it.
        assertFalse(BstRule.ownBreaks(400, maxDexKaizo, joined, 0x790L, id(790), "Cosmoem"))
        assertTrue(BstRule.ownBreaks(680, maxDexKaizo, joined, 0x790L, id(791), "Solgaleo"))
        joined.markEvolved(0x790L)
        assertTrue(BstRule.ownBreaks(680, maxDexKaizo, joined, 0x790L, id(791), "Solgaleo"), "never a legendary of 601 or more")
        // A form counts as its species, MaxDex's Z-A megas among them.
        assertTrue(BstRule.legendaryBreaks(780, maxDexKaizo, "Mewtwo-X"))
        assertTrue(BstRule.legendaryBreaks(700, maxDexKaizo, "Darkrai-M"))
        assertFalse(BstRule.legendaryBreaks(700, maxDexKaizo, "Dragonite-M"), "not a legendary")
        // Meltan into Melmetal, a legendary of 600: fine, the exception is for 601 and up.
        assertFalse(BstRule.ownBreaks(300, maxDexKaizo, joined, 0x808L, id(808), "Meltan"))
        assertFalse(BstRule.ownBreaks(600, maxDexKaizo, joined, 0x808L, id(809), "Melmetal"))
        // Nat. Dex 1.2.1 draws no legendary line: there an evolved Solgaleo keeps no X.
        val other = joined()
        assertFalse(BstRule.ownBreaks(400, natDexKaizo, other, 0x790L, id(790), "Cosmoem"))
        assertFalse(BstRule.ownBreaks(680, natDexKaizo, other, 0x790L, id(791), "Solgaleo"), "1.2.1 has no such exception")
        // Standard and Ultimate on MaxDex: 640 inclusive, and anything may come by evolving.
        val standard = BstRule.lines("standard", natDex = true, maxDex = true)
        val s = joined()
        assertFalse(BstRule.ownBreaks(640, standard, s, 0x640L, 11, "Metapod"))
        assertTrue(BstRule.ownBreaks(641, standard, s, 0x641L, 12, "Butterfree"))
        assertFalse(BstRule.ownBreaks(405, standard, s, 0x288L, id(288), "Vigoroth"))
        assertFalse(BstRule.ownBreaks(720, standard, s, 0x288L, id(493), "Arceus"), "\"acquire a legal Arceus by evolving a Vigoroth\"")
    }

    @Test
    fun `the rule sheet says the legendary line, and offers no it-evolved button for a legendary past it`() {
        assertEquals("A legendary at BST 601 or above is not allowed even then.", BstRule.legendarySays(601))
        val bst = File("src/main/kotlin/com/ironmonone/app/BstRule.kt").readText().replace("\r\n", "\n")
        val sheet = bst.substringAfter("internal fun BstRuleSheet(").substringBefore("\n}\n")
        assertTrue("(legendary?.let { \" \" + BstRule.legendarySays(it) } ?: \"\")" in sheet)
        val panel = File("src/main/kotlin/com/ironmonone/app/TrackerPanel.kt").readText().replace("\r\n", "\n")
        assertTrue("legendary = bstLines?.legendary) { bstSheet = null }" in panel)
        assertTrue("BstRule.keyOf(p.mon, generation).takeUnless { BstRule.legendaryBreaks(p.base?.bst, bstLines, p.speciesName) }" in panel)
        for (s in listOf(BstRule.legendarySays(601))) assertFalse(s.contains(0x2014.toChar()) || s.contains(0x2013.toChar()), s)
    }

    // ------------------------------------------------------------------ favorites: the 1.1.3 limits

    private fun takes(mode: String, bst: Int, legendary: Boolean = false, strong: Boolean = false, national: Int? = null, maxDex: Boolean = true) =
        FavoriteBall.takeable(mode, natDex = true, FavoriteBall.Candidate(bst, legendary, strong, national), if (legendary) 1 else 0, maxDex)

    @Test
    fun `MaxDex favorites go up to 600 BST in Kaizo and 640 in Standard and Ultimate, with no Cosmoem rule`() {
        // "Your favourites can be up to 600 BST in Kaizo, Survival and Super Kaizo, or 640 BST in Standard and Ultimate."
        for (mode in listOf("kaizo", "survival", "superkaizo", "kaizodoubles", "survivalrevival", "evokaizo")) {
            assertTrue(takes(mode, 600), mode)
            assertFalse(takes(mode, 601), mode)
        }
        assertTrue(takes("kaizo", 600, legendary = true, strong = true), "Mew, 600")
        for (mode in listOf("standard", "ultimate")) {
            assertTrue(takes(mode, 640), mode)
            assertFalse(takes(mode, 641), mode)
            // Red on the 1.2.1 lines, which take a 670 Slaking and refuse a 620 Strong Legendary in these modes.
            assertFalse(takes(mode, 670), "$mode: Slaking is over 640")
            assertTrue(takes(mode, 670, maxDex = false), "$mode on Nat. Dex 1.2.1")
            assertTrue(takes(mode, 620, legendary = true, strong = true), "$mode: under 640")
            assertFalse(takes(mode, 620, legendary = true, strong = true, maxDex = false), "$mode on Nat. Dex 1.2.1")
        }
        // Cosmoem: 1.2.1 lets no one start with it outside Standard; the 1.1.3 section has no such rule.
        assertTrue(takes("kaizo", 400, legendary = true, strong = true, national = FavoriteRules.COSMOEM))
        assertFalse(takes("kaizo", 400, legendary = true, strong = true, national = FavoriteRules.COSMOEM, maxDex = false), "Nat. Dex 1.2.1")
        // The rules every book has still hold: one legendary at most, and none in Super Kaizo and Survival.
        assertFalse(takes("superkaizo", 580, legendary = true))
        assertFalse(takes("survival", 580, legendary = true))
        assertFalse(FavoriteBall.takeable("kaizo", true, FavoriteBall.Candidate(580, true, false, null), 2, maxDex = true))
    }

    @Test
    fun `MaxDex's lab names a Cosmoem favorite and a 600 BST one`() {
        val balls = listOf(GbaTracker.BallOption("LEFT", id(790), ""), GbaTracker.BallOption("RIGHT", id(376), ""))
        val bst = mapOf(id(790) to 400, id(376) to 600)
        assertEquals(listOf("FAVORITE! COSMOEM IN THE LEFT BALL", "FAVORITE! METAGROSS IN THE RIGHT BALL"),
            FavoriteBall.lines(listOf("Cosmoem", "Metagross"), "kaizo", true, balls, maxDex = true) { bst[it] })
        assertEquals(listOf("FAVORITE! METAGROSS IN THE RIGHT BALL"),
            FavoriteBall.lines(listOf("Cosmoem", "Metagross"), "kaizo", true, balls) { bst[it] }, "Nat. Dex 1.2.1 skips the Cosmoem")
        // Play asks with the session's game.
        assertTrue("maxDex = session.kind?.isMaxDex == true) { tracker.baseStats(it)?.bst }" in File("src/main/kotlin/com/ironmonone/app/FavoriteBall.kt").readText())
    }

    // ------------------------------------------------------------------ the RULES box

    private val books = File("src/main/assets/rulesets")
    private fun lines(path: String) = File(books, path).readText().replace("\r\n", "\n").lines()

    @Test
    fun `the RULES box opens MaxDex's own book, the Nat Dex 1_1_3 rules with MaxDex's section`() {
        assertEquals("FRLG-MaxDex", Rules.dirFor("FRLG", true, RomKind.FIRERED_MAXDEX_10))
        assertEquals("FRLG-NatDex", Rules.dirFor("FRLG", true, RomKind.FIRERED_NATDEX_121))
        assertEquals("FRLG", Rules.dirFor("FRLG", false, RomKind.FIRERED_U_V11))
        assertEquals("FRLG-NatDex", Rules.dirFor("FRLG", true, null), "no game: as before")
        val dialog = File("src/main/kotlin/com/ironmonone/app/RulesDialog.kt").readText().replace("\r\n", "\n")
        assertTrue("val dir = Rules.dirFor(family, natDex, kind)" in dialog)
        assertEquals(listOf("kaizo.md"), File(books, "FRLG-MaxDex").list()!!.toList(), "one mode, one book")
        val maxDex = lines("FRLG-MaxDex/kaizo.md")
        val natDex = lines("FRLG-NatDex/kaizo.md")
        assertEquals("# FireRed and LeafGreen, MaxDex: Kaizo", maxDex.first())
        // Everything before the Nat. Dex changes is the Nat. Dex book's, in its order: the two are generated together.
        fun before(l: List<String>) = l.drop(1).takeWhile { it != "## Nat. Dex ruleset changes" }
        assertTrue(before(maxDex).size > 100)
        assertEquals(before(natDex), before(maxDex))

        // MaxDex's Nat. Dex changes: the v1.0.0 to v1.1.3 rules, as the page words them, and none of the v1.2.0+ ones.
        val changes = maxDex.dropWhile { it != "## Nat. Dex ruleset changes" }.takeWhile { it != "## MaxDex" }
        assertTrue(changes.any {
            it.trim() == "- BST limit for Kaizo, Survival and Super Kaizo is 599, or 600 for starter Pokémon, including legendaries. " +
                "600+ BST mons may be obtained through evolution, except for 601+ BST legendaries. For example, a Cosmoem could evolve into " +
                "a Mega Gallade, but not a Mewtwo. (The randomizer will prevent this from happening anyway)"
        }, "the 1.1.3 BST line")
        assertTrue(changes.any { it.trim().startsWith("- If playing Standard or Ultimate, the BST limit is 640 inclusive, and the Legendary evolution exception does not apply.") })
        assertTrue(changes.any { it.trim() == "- (v1.0.0 to v1.1.3 only) Your favorites can be up to 600 BST in Kaizo, Survival and Super Kaizo, or 640 BST in Standard and Ultimate." })
        assertTrue(changes.any { it.startsWith("List of banned Pokémon in Kaizo, Survival and Super Kaizo (v1.0.0 to v1.1.3 only)") })
        val ruleLines = changes.filterNot { it.startsWith("(Rules the page marks") }
        assertTrue(ruleLines.none { "v1.2.0+" in it }, "no v1.2.0+ rule: ${ruleLines.filter { "v1.2.0+" in it }}")
        assertTrue(ruleLines.none { "except Cosmoem" in it || "Strong Legendary" in it }, "nor its words")
        assertTrue(maxDex.any { it.startsWith("(Rules the page marks \"v1.2.0+ only\" are left out: MaxDex is built on Nat. Dex 1.1.3") })
        // Nat. Dex 1.2.1 keeps its own: the v1.2.0+ rules, and none of the 1.1.3 ones.
        val natChanges = natDex.dropWhile { it != "## Nat. Dex ruleset changes" }.filterNot { it.startsWith("(Rules the page marks") }
        assertTrue(natChanges.any { "Your favorites can be up to 600 BST, and if you are playing Standard or Ultimate" in it })
        assertTrue(natChanges.none { "v1.0.0 to v1.1.3 only" in it || "BST limit for Kaizo, Survival and Super Kaizo is 599" in it })

        // MaxDex's own section: Trip's page, every version, and the rules KaizoCore holds a run to.
        val section = maxDex.dropWhile { it != "## MaxDex" }.takeWhile { it != "## Sources" }
        assertTrue("Same Rules as NatDex (https://github.com/CyanSMP64/NatDexExtension/wiki/Nat.-Dex-Ruleset-Changes)" in section)
        assertTrue("- 2026-06-30: V-Create came off Extra Banned Moves." in section)
        assertTrue("- 2026-07-03: the page was deleted." in section)
        val held = section.single { it.startsWith("In KaizoCore, MaxDex is held to the Nat. Dex rules above for v1.0.0 to v1.1.3") }
        for (said in listOf("a starter may have up to 600 BST", "any other Pokémon must be under 600",
            "may evolve to 600 or more unless it becomes a legendary of 601 or more", "up to 9 favorites may have up to 600 BST"))
            assertTrue(said in held, "$said: $held")
        assertTrue(maxDex.any { "https://github.com/Tripc423/Maxdex.wiki.git" in it })
        for (l in maxDex) assertTrue('—' !in l && '–' !in l, l)
    }

    @Test
    fun `the Kaizo IronMON screen shows MaxDex's own favorite lines`() {
        val shown = FavoriteRules.lines(File(books, "FRLG-MaxDex/kaizo.md").readText())
        assertTrue(shown.any { "Your favorites can be up to 600 BST in Kaizo, Survival and Super Kaizo, or 640 BST in Standard and Ultimate." in it }, shown.toString())
        assertTrue(shown.none { "any Pokémon above 600 BST that is not categorized as Strong Legendary or Mythical" in it }, "not the 1.2.1 line")
        assertTrue(shown.any { "up to 9 favorites" in it })
    }
}
