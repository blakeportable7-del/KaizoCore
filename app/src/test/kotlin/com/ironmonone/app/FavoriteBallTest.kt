package com.ironmonone.app

import com.ironmonone.core.RomKind
import com.ironmonone.tracker.GameMap
import com.ironmonone.tracker.GbaTracker
import com.ironmonone.tracker.MemoryReader
import com.ironmonone.tracker.nuzlocke.NuzlockePreset
import com.ironmonone.tracker.nuzlocke.NuzlockeRules
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The favorite-in-ball line (Blake, 2026-10-01: "As long as it fits in the rules, I would say do it for the modes that
 * allow favorites"; asked whether it should follow the official rules mode by mode: "Yes").
 */
class FavoriteBallTest {
    private val books = File("src/main/assets/rulesets")
    private val gen3Books: List<File> = listOf("RSE", "FRLG", "RSE-NatDex", "FRLG-NatDex", "FRLG-MaxDex").flatMap { d ->
        File(books, d).listFiles()!!.filter { it.name.endsWith(".md") }
    }

    /** The Nuzlocke case binds a ledger to the run in play; left set, a stream test run after it read every run as a Nuzlocke. */
    @kotlin.test.AfterTest fun clean() { NuzlockeTracking.reset() }

    private fun ball(place: String, species: Int) = GbaTracker.BallOption(place, species, "")

    /** Whether [mode] takes a favorite of [bst]: [legendary] and [strong] as the rules count them, [national] for Cosmoem. */
    private fun ok(
        mode: String?, bst: Int, natDex: Boolean = false, legendary: Boolean = false, strong: Boolean = false,
        national: Int? = null, legendaries: Int = if (legendary) 1 else 0, maxDex: Boolean = false,
    ) = FavoriteBall.takeable(mode, natDex, FavoriteBall.Candidate(bst, legendary, strong, national), legendaries, maxDex)

    @Test
    fun `each mode takes the favorites its official rules allow`() {
        // Standard and Ultimate: no BST limit, and a legendary only as the list's one.
        for (mode in listOf("standard", "ultimate")) {
            assertTrue(ok(mode, 600), "$mode: Dragonite")
            assertTrue(ok(mode, 680, legendary = true, strong = true), "$mode: Mewtwo as the one legendary")
            assertFalse(ok(mode, 580, legendary = true, legendaries = 2), "$mode: two legendaries")
        }
        // Kaizo and the modes on it: no 599+ BST, so under 600, a legendary too.
        for (mode in listOf("kaizo", "kaizodoubles", "chaoskaizo", "survivalrevival")) {
            assertTrue(ok(mode, 599), mode)
            assertFalse(ok(mode, 600), "$mode: Dragonite, Metagross and Mew are 600")
            assertTrue(ok(mode, 580, legendary = true), "$mode: Articuno, the list's one legendary")
            assertFalse(ok(mode, 580, legendary = true, legendaries = 2), mode)
        }
        // Evo Kaizo: 600 and lower are legal in the lab; a legendary favorite stays under 600.
        assertTrue(ok("evokaizo", 600))
        assertFalse(ok("evokaizo", 601))
        assertFalse(ok("evokaizo", 600, legendary = true))
        // Super Kaizo: Kaizo's limit and no legendary. Survival: no legendary, and under 580.
        assertTrue(ok("superkaizo", 599))
        assertFalse(ok("superkaizo", 600))
        assertFalse(ok("superkaizo", 580, legendary = true))
        assertTrue(ok("survival", 579))
        assertFalse(ok("survival", 580))
        assertFalse(ok("survival", 500, legendary = true))
        // Journey takes any starter, a build of your own names no mode, and stats the game did not give are no stats.
        assertFalse(ok(FavoriteBall.JOURNEY, 300))
        assertFalse(ok(null, 300))
        assertFalse(ok("kaizo", 0))
    }

    @Test
    fun `a Nat Dex run takes favourites up to 600 BST, and more in Standard and Ultimate`() {
        for (mode in listOf("kaizo", "kaizodoubles", "chaoskaizo", "survivalrevival", "superkaizo", "survival", "evokaizo")) {
            assertTrue(ok(mode, 600, natDex = true), "$mode: 600 itself")
            assertFalse(ok(mode, 601, natDex = true), mode)
        }
        // Standard and Ultimate: above 600 too, unless Strong Legendary or Mythical.
        for (mode in listOf("standard", "ultimate")) {
            assertTrue(ok(mode, 670, natDex = true), "$mode: Slaking")
            assertTrue(ok(mode, 670, natDex = true, legendary = true), "$mode: Regigigas, a sub-legendary")
            assertFalse(ok(mode, 680, natDex = true, legendary = true, strong = true), "$mode: Mewtwo")
            assertTrue(ok(mode, 600, natDex = true, legendary = true, strong = true), "$mode: Mew, 600")
        }
        // Evo Kaizo takes no Strong Legendary or Mythical starter at all, and only Standard a Cosmoem.
        assertFalse(ok("evokaizo", 600, natDex = true, legendary = true, strong = true))
        assertTrue(ok("kaizo", 600, natDex = true, legendary = true, strong = true), "Kaizo: Mew, 600")
        assertTrue(ok("standard", 400, natDex = true, legendary = true, strong = true, national = FavoriteRules.COSMOEM))
        for (mode in listOf("ultimate", "kaizo", "survivalrevival"))
            assertFalse(ok(mode, 400, natDex = true, legendary = true, strong = true, national = FavoriteRules.COSMOEM), mode)
        // Super Kaizo and Survival still take no legendary.
        assertFalse(ok("survival", 580, natDex = true, legendary = true))
        assertFalse(ok("superkaizo", 580, natDex = true, legendary = true))
    }

    @Test
    fun `the limits are the ones the Gen 3 books write`() {
        for (f in gen3Books) {
            val text = f.readText()
            val mode = f.nameWithoutExtension
            // MaxDex's book is the Nat. Dex rules for 1.1.3, and the app takes MaxDex as a Nat. Dex build with those lines.
            val maxDex = f.parentFile.name.endsWith("-MaxDex")
            val natDex = f.parentFile.name.endsWith("-NatDex") || maxDex
            if (maxDex) assertTrue(com.ironmonone.core.RomKind.FIRERED_MAXDEX_10.isNatDex && com.ironmonone.core.RomKind.FIRERED_MAXDEX_10.isMaxDex)
            fun takes(bst: Int) = ok(mode, bst, natDex, maxDex = maxDex)
            // Every book has the Favorites Clause; Journey's Rule #1 makes it moot.
            assertTrue("You can skip choosing the starter pick at Random ONLY if you find one of your" in text, f.path)
            if ("You can choose any of the 3 starters" in text) {
                assertFalse(takes(300), f.path)
                continue
            }
            when {
                maxDex -> {
                    assertTrue("Your favorites can be up to 600 BST in Kaizo, Survival and Super Kaizo, or 640 BST in Standard and Ultimate." in text, f.path)
                    assertFalse("Your favorites can be up to 600 BST, and if you are playing Standard or Ultimate" in text, "${f.path}: the v1.2.0+ line")
                    val plain = mode == "standard" || mode == "ultimate"
                    assertTrue(if (plain) takes(640) && !takes(641) else takes(600) && !takes(601), f.path)
                }
                natDex -> {
                    assertTrue("Your favorites can be up to 600 BST, and if you are playing Standard or Ultimate" in text, f.path)
                    assertTrue(takes(600) && !takes(601) || mode == "standard" || mode == "ultimate", f.path)
                    assertEquals(mode == "standard" || mode == "ultimate", takes(700), f.path)
                }
                "No Legendaries or 580+ BST Pokemon as favorites" in text -> assertTrue(takes(579) && !takes(580), f.path)
                "600 BST and lower Mons ARE LEGAL in LAB" in text -> assertTrue(takes(600) && !takes(601), f.path)
                "No using 599+ BST Pokémon" in text -> assertTrue(takes(599) && !takes(600), f.path)
                else -> assertTrue(takes(700), "${f.path}: no BST limit")
            }
        }
    }

    @Test
    fun `only a ball holding a favorite is named, never what the others hold`() {
        val bst = mapOf(25 to 320, 94 to 500, 151 to 600, 149 to 600, 144 to 580, 59 to 555, 1139 to 555)
        val balls = listOf(ball("LEFT", 25), ball("MIDDLE", 94), ball("RIGHT", 151))
        fun lines(favorites: List<String>, mode: String, b: List<GbaTracker.BallOption> = balls, natDex: Boolean = false) =
            FavoriteBall.lines(favorites, mode, natDex, b) { bst[it] }
        val kaizo = lines(listOf("Gengar", "Scyther"), "kaizo")
        assertEquals(listOf("FAVORITE! GENGAR IN THE MIDDLE BALL"), kaizo)
        assertTrue(kaizo.none { "PIKACHU" in it || "MEW" in it })
        // Two favorites in two balls: both, left to right.
        assertEquals(listOf("FAVORITE! PIKACHU IN THE LEFT BALL", "FAVORITE! GENGAR IN THE MIDDLE BALL"), lines(listOf("gengar", "Pikachu"), "kaizo"))
        // Mew, 600 BST: Standard has no limit; in Kaizo the ball call stands.
        assertEquals(listOf("FAVORITE! MEW IN THE RIGHT BALL"), lines(listOf("Mew"), "standard"))
        assertTrue(lines(listOf("Mew"), "kaizo").isEmpty())
        assertTrue(lines(listOf("Articuno"), "survival", listOf(ball("LEFT", 144))).isEmpty())
        assertEquals(1, lines(listOf("Articuno"), "kaizo", listOf(ball("LEFT", 144))).size)
        // A favourite counts for its own form only, as the Nat. Dex books say.
        assertTrue(lines(listOf("Arcanine"), "kaizo", listOf(ball("LEFT", 1139)), natDex = true).isEmpty())
        assertEquals(listOf("FAVORITE! ARCANINE-H IN THE LEFT BALL"), lines(listOf("Arcanine-H"), "kaizo", listOf(ball("LEFT", 1139)), natDex = true))
        // No stats, no line.
        assertTrue(FavoriteBall.lines(listOf("Gengar"), "kaizo", false, balls) { null }.isEmpty())
        for (l in kaizo) assertFalse(Char(0x2014) in l || Char(0x2013) in l, l)
    }

    // ------------------------------------------------------------- the whole way, on the real FireRed

    private val fireRed = File("C:/Users/bepor/IronMonOne/.vendor/roms/firered-u-v10.gba")

    private fun tracker(): GbaTracker? {
        if (!fireRed.exists()) return null
        val bytes = fireRed.readBytes()
        val mem = MemoryReader { address, length ->
            val off = (address - 0x08000000L).toInt()
            if (address >= 0x08000000L && off >= 0 && off + length <= bytes.size) bytes.copyOfRange(off, off + length)
            else ByteArray(0)
        }
        return GbaTracker(mem, GameMap.resolve(mem))
    }

    private fun runOn(settings: String, favorites: String): Triple<File, PrepStore, GameSession> {
        val filesDir = Files.createTempDirectory("favball").toFile()
        val store = PrepStore(filesDir)
        store.saveFavorites(RomKind.FIRERED_U_V10.id, favorites)
        store.saveLastRun(RomKind.FIRERED_U_V10.id, settings)
        return Triple(filesDir, store, GameSession.forRun(File(filesDir, "run.gba"), RomKind.FIRERED_U_V10))
    }

    @Test
    fun `a Kaizo run on FireRed names Squirtle's ball, the middle one`() {
        val t = tracker() ?: run { println("SKIP: FireRed v1.0 missing"); return }
        NuzlockeTracking.reset()
        val (dir, store, session) = runOn("FRLG Kaizo.rnqs", "Squirtle,Gengar,Dragonite")
        val shown = FavoriteBall.shown(store, session, t, dir)
        assertEquals("FAVORITES: SQUIRTLE / GENGAR / DRAGONITE", shown.list)
        assertEquals(listOf("FAVORITE! SQUIRTLE IN THE MIDDLE BALL"), shown.balls)
        // All three, in the order they stand.
        val (dir2, store2, session2) = runOn("FRLG Kaizo.rnqs", "Charmander,Squirtle,Bulbasaur")
        assertEquals(
            listOf("FAVORITE! BULBASAUR IN THE LEFT BALL", "FAVORITE! SQUIRTLE IN THE MIDDLE BALL", "FAVORITE! CHARMANDER IN THE RIGHT BALL"),
            FavoriteBall.shown(store2, session2, t, dir2).balls,
        )
        // Before the tracker is up: the list alone, and its icons.
        val early = FavoriteBall.shown(store, session, null, dir)
        assertEquals("FAVORITES: SQUIRTLE / GENGAR / DRAGONITE", early.list)
        assertTrue(early.balls.isEmpty())
        assertEquals(listOf("Squirtle", "Gengar", "Dragonite"), early.icons.map { it.name })
    }

    @Test
    fun `Journey, a build of your own, a library game and a Nuzlocke get no ball line`() {
        val t = tracker() ?: run { println("SKIP: FireRed v1.0 missing"); return }
        NuzlockeTracking.reset()
        runOn("FRLG IronMON Journey.rnqs", "Squirtle").let { (d, s, g) -> assertTrue(FavoriteBall.shown(s, g, t, d).balls.isEmpty(), "Journey") }
        runOn("My build.rnqs", "Squirtle").let { (d, s, g) -> assertTrue(FavoriteBall.shown(s, g, t, d).balls.isEmpty(), "no mode") }
        runOn("FRLG Kaizo.rnqs", "Squirtle").let { (d, s, _) ->
            val library = GameSession(File(d, "FireRed.gba"), RomKind.FIRERED_U_V10.platform, RomKind.FIRERED_U_V10, "FireRed", "lib", isRun = false)
            assertTrue(FavoriteBall.shown(s, library, t, d).balls.isEmpty(), "a library game")
            assertEquals("FAVORITES: SQUIRTLE", FavoriteBall.shown(s, library, t, d).list)
        }
        // A randomized Nuzlocke: the run in play, with a ledger bound to it.
        val (d, s, g) = runOn("FRLG Kaizo.rnqs", "Squirtle")
        File(d, "prep/lastseed.txt").writeText("12345")
        val bind = NuzlockeStore.bindOf(s.session(), s.runIdentity())!!
        NuzlockeStore(d).start(bind, "FireRed, randomized", NuzlockeRules.forPreset(NuzlockePreset.STANDARD), 1_800_000_000_000L)
        assertEquals(PlayRules.Kind.NUZLOCKE, PlayRules.kind(g, d))
        assertTrue(FavoriteBall.shown(s, g, t, d).balls.isEmpty(), "a Nuzlocke")
    }

    @Test
    fun `Play and the panel show the line in the lab, under the favorites`() {
        val play = File("src/main/kotlin/com/ironmonone/app/PlayScreen.kt").readText()
        assertTrue("val favoriteLine = remember(session.id, trackerRef) { FavoriteBall.shown(store, session, trackerRef, context.filesDir) }" in play)
        val panel = File("src/main/kotlin/com/ironmonone/app/TrackerPanel.kt").readText()
        val noParty = panel.indexOf("state.partyCount == 0 -> PcCard {")
        val line = panel.indexOf("if (state.inLab) favoriteLine?.balls?.forEach {")
        assertTrue(noParty in 0 until line && line < panel.indexOf("state.gameOver != null && ironmonOver"), "on the no-party card")
        // Under the favorites, drawn as their icons since 2026-10-02 (FavoriteIconsTest).
        assertTrue(panel.indexOf("FavoriteIconRow(it, { sp -> spriteFor(sp) })") in noParty until line, "under the favorites")
        assertTrue("DsBallAndFavorites(randomBall, hgss = state.badgeSet == \"HGSS\", favoriteLine?.icons.orEmpty())" in File("src/main/kotlin/com/ironmonone/app/NdsTrackerPanel.kt").readText())
        // The Kaizo IronMON screen says so on a Game Boy Advance game, in any mode but Journey.
        val run = File("src/main/kotlin/com/ironmonone/app/RunScreen.kt").readText()
        assertTrue("val favBall = selectedRom?.first?.platform == com.ironmonone.core.Platform.GBA && favMode != null && favMode != FavoriteBall.JOURNEY" in run)
        assertTrue("if (favBall) \" \" + RunCopy.FAVORITE_BALL else \"\"" in run)
        assertFalse(Char(0x2014) in RunCopy.FAVORITE_BALL || Char(0x2013) in RunCopy.FAVORITE_BALL)
    }
}
