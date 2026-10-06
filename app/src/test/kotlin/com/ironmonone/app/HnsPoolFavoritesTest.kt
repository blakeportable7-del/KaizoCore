package com.ironmonone.app

import com.ironmonone.app.engine.HnsEngine
import com.ironmonone.core.RomKind
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Heart & Soul's two pools (2026-10-06 audit): a Vanilla run (Gen 1 to 3) is held to Emerald's rules, three favorites
 * and the Gen 3 limits; a Nat. Dex run to the Emerald Nat. Dex v1.2 rules, nine favorites and the 600 BST lines
 * (rulesets/HnS). The favorites, their editor, the tracker's card, the Run screen's count and Your stats' label follow
 * the run's pool; every other game keeps exactly what it had.
 */
class HnsPoolFavoritesTest {
    private val hns = RomKind.HEARTSOUL_KAIZO_206
    private val src = "src/main/kotlin/com/ironmonone/app/"
    private fun read(name: String) = File(src + name).readText().replace("\r\n", "\n")
    private val dirs = ArrayList<File>()
    private fun filesDir(): File = Files.createTempDirectory("hnspool").toFile().also { dirs += it }

    @AfterTest fun clean() { dirs.forEach { it.deleteRecursively() } }

    // ---------------------------------------------------------------------------------------- 1, 2: favorites by pool

    @Test
    fun `a Vanilla run shows and uses three favorites of Gens 1 to 3, a Nat Dex run nine of every Pokemon`() {
        val vanilla = Favorites.scope(hns, hnsNatDex = false)
        assertEquals(Favorites.Scope(hns, 3, 9, 386), vanilla)
        assertTrue(vanilla.hnsVanilla)
        val natDex = Favorites.scope(hns, hnsNatDex = true)
        assertEquals(Favorites.Scope(hns, 9, 9, Int.MAX_VALUE), natDex)
        assertFalse(natDex.hnsVanilla)
        // Offered as typed: Gen 1 to 3 base forms only on Vanilla.
        assertFalse("Lucario" in Favorites.suggest("luca", maxId = vanilla.maxDex, kind = hns), "Gen 4")
        assertFalse(Favorites.suggest("raticate", maxId = vanilla.maxDex, kind = hns).any { "-" in it }, "a regional form")
        assertTrue("Treecko" in Favorites.suggest("treec", maxId = vanilla.maxDex, kind = hns), "Gen 3")
        assertTrue("Lucario" in Favorites.suggest("luca", maxId = natDex.maxDex, kind = hns))
        assertFalse(Favorites.inGame("Lucario", vanilla.maxDex, hns))
        assertTrue(Favorites.inGame("Deoxys", vanilla.maxDex, hns))
        // The run counts the boxes it shows; the others stay as saved.
        val nine = listOf("Pidgey", "Rattata", "Spearow", "Lucario", "", "", "", "", "Pecharunt")
        assertEquals(nine.take(3), vanilla.used(nine))
        assertEquals(nine, natDex.used(nine))
    }

    @Test
    fun `every other game keeps its own boxes and dex whatever the pool`() {
        for (k in RomKind.all.filter { !it.isHns }) for (natDex in listOf(true, false)) {
            assertEquals(Favorites.Scope(k, Favorites.slotCount(k), Favorites.slotCount(k), Favorites.maxDex(k)), Favorites.scope(k, natDex), k.id)
            val dir = filesDir()
            HnsPool.choose(dir, if (natDex) HnsEngine.Pool.NATDEX else HnsEngine.Pool.VANILLA)
            for (next in listOf(true, false))
                assertEquals(Favorites.scope(k), HnsPool.favoritesScope(k, dir, nextRun = next), "${k.id}: the Heart & Soul pool changes nothing")
        }
        assertEquals(Favorites.Scope(null, 3, 3, Int.MAX_VALUE), Favorites.scope(null))
    }

    @Test
    fun `before a run the pool chosen decides, and the choice is seen at once`() {
        val dir = filesDir()
        val before = HnsPool.edits.intValue
        HnsPool.choose(dir, HnsEngine.Pool.VANILLA)
        assertTrue(HnsPool.edits.intValue > before, "the editor reads the choice again")
        assertEquals(3, HnsPool.favoritesScope(hns, dir, nextRun = true).shown)
        // No run in play has a recipe here, so the run's pool is the one chosen (HnsPool.natDexRun).
        assertEquals(3, HnsPool.favoritesScope(hns, dir, nextRun = false).shown)
        HnsPool.choose(dir, HnsEngine.Pool.NATDEX)
        assertEquals(9, HnsPool.favoritesScope(hns, dir, nextRun = true).shown)
    }

    @Test
    fun `a Vanilla run's editor shows the book without the Nat Dex changes, and says the other favorites are kept`() {
        val book = File("src/main/assets/rulesets/HnS/kaizo.md").readText()
        val vanillaLines = FavoriteRules.lines(FavoriteRules.forPool(book, natDexRules = false))
        val natDexLines = FavoriteRules.lines(FavoriteRules.forPool(book, natDexRules = true))
        assertTrue(natDexLines.any { "up to 9 favorites" in it })
        assertFalse(vanillaLines.any { "up to 9 favorites" in it || "600 BST" in it && "can be up to" in it }, vanillaLines.toString())
        assertTrue(vanillaLines.any { "3 favorite" in it }, vanillaLines.toString())
        // Every other book is read whole, as before.
        val fr = File("src/main/assets/rulesets/FRLG-NatDex/kaizo.md").readText()
        assertEquals(fr, FavoriteRules.forPool(fr, natDexRules = true))

        val vanilla = Favorites.scope(hns, hnsNatDex = false)
        assertNull(FavoritesCopy.keptAside(vanilla, listOf("Pidgey", "", "", "", "", "", "", "", "")))
        assertEquals("Favorites 4 to 9 are kept for a Nat. Dex run. A Vanilla run uses the first 3.",
            FavoritesCopy.keptAside(vanilla, listOf("Pidgey", "", "", "Lucario", "", "", "", "", "")))
        assertNull(FavoritesCopy.keptAside(Favorites.scope(hns, true), listOf("Pidgey", "", "", "Lucario", "", "", "", "", "")))
        assertEquals("A name in red is not a Pokémon this game has.", FavoritesCopy.notInGame(Favorites.scope(RomKind.EMERALD_U)))
        assertTrue("Gens 1 to 3" in FavoritesCopy.notInGame(vanilla))
    }

    @Test
    fun `the editor keeps every saved box and edits only those the run shows`() {
        val editor = read("FavoritesEditor.kt")
        assertTrue("mutableStateOf(Favorites.slots(store, favRomId, scope.stored))" in editor, "reads all nine")
        assertTrue("for (i in 0 until scope.shown)" in editor, "shows the run's")
        assertTrue("Favorites.save(store, favRomId, favSlots)" in editor, "saves all nine back")
        // So a Vanilla edit of box 1 keeps 4 to 9 in the file.
        val dir = filesDir(); val store = PrepStore(dir)
        store.saveFavorites(hns.id, "Pidgey,Rattata,Spearow,Lucario,,,,,Pecharunt")
        val scope = Favorites.scope(hns, hnsNatDex = false)
        val slots = Favorites.slots(store, hns.id, scope.stored).toMutableList().also { it[0] = "Bulbasaur" }
        Favorites.save(store, hns.id, slots)
        assertEquals("Bulbasaur,Rattata,Spearow,Lucario,,,,,Pecharunt", store.favoritesText(hns.id))
    }

    // ---------------------------------------------------------------------------------------- 3: card and count

    @Test
    fun `the tracker's card lists a Vanilla run's first three favorites, a Nat Dex run's nine`() {
        val dir = filesDir(); val store = PrepStore(dir)
        store.saveFavorites(hns.id, "Pidgey,Rattata,Spearow,Lucario,,,,,Pecharunt")
        store.saveLastRun(hns.id, "RSE NatDex v1.2 Kaizo.rnqs")
        val session = GameSession.forRun(File(dir, "run.gba"), hns)
        HnsPool.choose(dir, HnsEngine.Pool.VANILLA)
        val vanilla = FavoriteBall.shown(store, session, null, dir)
        assertEquals("FAVORITES: PIDGEY / RATTATA / SPEAROW", vanilla.list)
        assertEquals(listOf("Pidgey", "Rattata", "Spearow"), vanilla.icons.map { it.name })
        HnsPool.choose(dir, HnsEngine.Pool.NATDEX)
        val natDex = FavoriteBall.shown(store, session, null, dir)
        assertEquals("FAVORITES: PIDGEY / RATTATA / SPEAROW / LUCARIO / PECHARUNT", natDex.list)
        // The Run screen's "N set." counts the boxes the next run uses.
        val run = read("RunScreen.kt")
        assertTrue("val scope = HnsPool.favoritesScope(selectedRom?.first, context.filesDir, nextRun = true)" in run)
        assertTrue("scope.used(Favorites.slots(store, selectedRom?.first?.id, scope.stored)).count { s -> s.isNotBlank() }" in run)
        assertFalse("Favorites.slotCount(selectedRom?.first)" in run)
        // And a Vanilla run's card draws no Gen 4 favorite even if one is typed in the first three.
        assertEquals(listOf(null), FavoriteIcons.of(listOf("Lucario"), hns, Favorites.scope(hns, false).maxDex).map { it.species })
    }

    // ---------------------------------------------------------------------------------------- 4: Your stats

    @Test
    fun `Your stats labels a Heart & Soul run by its pool`() {
        val file = "RSE NatDex v1.2 Kaizo.rnqs"
        assertEquals("Kaizo (Vanilla)", CareerStats.modeLabel(file, "VANILLA"))
        assertEquals("Kaizo (Nat. Dex)", CareerStats.modeLabel(file, "NATDEX"))
        assertEquals("Kaizo (Nat. Dex)", CareerStats.modeLabel(file), "no pool recorded: as before")
        assertEquals("Kaizo", CareerStats.modeLabel("RSE Kaizo.rnqs"), "every other game as before")
        val r = RunRecord(attempt = 3, seed = "00000000000000aa", ruleset = file, started = 1L, ended = 2L, playSeconds = 0,
            outcome = RunRecord.Outcome.LOST, badges = 1, lead = null, killer = null, trainer = "", location = "", hnsPool = "VANILLA")
        assertEquals("VANILLA", RunRecord.decode(r.encode())!!.hnsPool, "kept in the history file")
        val older = r.encode().split('\t').take(20).joinToString("\t")
        assertEquals("", RunRecord.decode(older)!!.hnsPool, "a line written before the pool was")
        assertEquals(r.copy(hnsPool = ""), RunRecord.decode(older))
        // Both places a run is filed record the pool of the run in play.
        assertTrue("hnsPool = HnsPool.ofRun(store)?.name.orEmpty()," in read("RunHistory.kt"))
        assertTrue("hnsPool = HnsPool.ofRun(this)?.name.orEmpty()," in read("PrepStore.kt"))
        assertTrue("CustomRuns.label(modeLabel(r.ruleset, r.hnsPool), r.custom)" in read("CareerStats.kt"))
    }

    // ---------------------------------------------------------------------------------------- 5: the BST lines

    @Test
    fun `the Nat Dex lines are the books' own, and Heart & Soul's randomizer draws the line it shipped with`() {
        for (book in listOf("FRLG-NatDex", "RSE-NatDex")) for (mode in listOf("kaizo", "survival", "superkaizo", "kaizodoubles", "survivalrevival")) {
            val text = File("src/main/assets/rulesets/$book/$mode.md").readText()
            assertTrue("The only banned Pokémon are any Pokémon 600 BST or higher (unless obtained via an evolution)" in text, "$book/$mode")
            assertTrue("Any Legendary/Mythical Pokémon 600 BST or below, except Cosmoem, may be chosen as the starter" in text, "$book/$mode")
            assertTrue("600 BST starter Pokémon are now completely legal" in text, "$book/$mode")
            val l = BstRule.lines(mode, natDex = true)!!
            // Blake, 2026-10-03: Nat. Dex 1.2.1 keeps its own 600+ ban, the v1.2 "Kaizo and harder" line; only MaxDex
            // (the 1.1.3 rules) lets a 600 starter in.
            assertTrue(BstRule.breaks(600, l.own, null, 0), "$book/$mode: a 600 of yours is banned")
            assertFalse(BstRule.breaks(599, l.own, null, 0), "$book/$mode: 599 is not")
            assertTrue(BstRule.wildBreaks(600, l), "$book/$mode: a wild 600 is banned")
            assertFalse(BstRule.wildBreaks(599, l))
        }
        // Heart & Soul through its pool: Nat. Dex takes the lines above, Vanilla Emerald's 599.
        assertEquals(BstRule.Lines(own = 600, wild = 600), BstRule.lines("kaizo", natDex = true))
        assertEquals(BstRule.Lines(599, 599), BstRule.lines("kaizo", natDex = false))
        // The randomizer's line for each preset and pool is what rc36.1 drew, so a seed makes the same game.
        val before = mapOf(
            ("kaizo" to HnsEngine.Pool.NATDEX) to 600, ("kaizo" to HnsEngine.Pool.VANILLA) to 599,
            ("survival" to HnsEngine.Pool.NATDEX) to 600, ("super kaizo" to HnsEngine.Pool.NATDEX) to 600,
            ("kaizo doubles" to HnsEngine.Pool.NATDEX) to 600, ("survival revival" to HnsEngine.Pool.VANILLA) to 599,
            ("evo kaizo" to HnsEngine.Pool.NATDEX) to 601, ("evo kaizo" to HnsEngine.Pool.VANILLA) to 601,
            ("standard" to HnsEngine.Pool.NATDEX) to null, ("ultimate" to HnsEngine.Pool.VANILLA) to null,
            ("chaos kaizo" to HnsEngine.Pool.NATDEX) to null,
        )
        for ((key, line) in before) {
            val name = "RSE NatDex v1.2 " + key.first.split(' ').joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }
            val o = com.ironmonone.app.engine.hns.HnsOptions.from(com.dabomstew.pkrandom.Settings(), name)
            assertEquals(line, HnsEngine.bstLine(o, key.second), "$name, ${key.second}")
        }
    }

    // ---------------------------------------------------------------------------------------- the drop-down

    @Test
    fun `typing ranks names as the log's search does`() {
        assertEquals("Mr. Mime", Favorites.suggest("mr mime").first())
        assertEquals("Ho-Oh", Favorites.suggest("hooh").first())
        assertEquals("Farfetch'd", Favorites.suggest("farfetchd").first())
        assertEquals("Nidoran F", Favorites.suggest("nidoranf").first())
        assertEquals("Pikachu", Favorites.suggest("pikachi").first(), "one letter wrong")
        assertEquals("Charizard", Favorites.suggest("charzard").first(), "one letter missing")
        assertEquals("Gengar", Favorites.suggest("gegnar").first(), "two letters swapped")
        // National Dex order within a rank: Bulbasaur 1, Blastoise 9, Butterfree 12; Treecko (252) before Turtwig (387).
        assertEquals(listOf("Bulbasaur", "Blastoise", "Butterfree"), Favorites.suggest("b").take(3))
        val t = Favorites.suggest("t", limit = 400)
        assertTrue(t.indexOf("Treecko") < t.indexOf("Turtwig"))
        // A name that starts with what is typed comes before one that only contains it.
        val mew = Favorites.suggest("mew", maxId = 151)
        assertEquals(listOf("Mew", "Mewtwo"), mew.take(2))
        // The pool's limit holds in every rank: no Gen 4 on a Vanilla run even one slip away.
        assertTrue(Favorites.suggest("lucari", maxId = 386, kind = hns).none { Favorites.nationalOf(Favorites.idOf(it)!!)?.let { n -> n > 386 } ?: true })
        // A name typed loosely is still the Pokemon: the card, the ball line and the red mark agree.
        assertEquals(Favorites.idOf("Mr. Mime"), Favorites.idOf("mr mime"))
        assertEquals(Favorites.idOf("Farfetch'd"), Favorites.idOf("farfetchd"))
        assertTrue(Favorites.inGame("ho oh", 251))
        assertTrue(Favorites.suggest("Snorlax").isEmpty(), "the one name left is the one typed")
        assertEquals(listOf("Charizard"), Favorites.suggest("charzard"), "a slip still offers the name as written")
        // Each line's picture: the game's own table id (MaxDex's own past 411 on MaxDex 1.0).
        assertEquals(Favorites.idOf("Mr. Mime"), Favorites.suggestIds("mr mime").first().first)
    }

    @Test
    fun `both places favorites are entered use the one editor and its drop-down`() {
        val editor = read("FavoritesEditor.kt")
        assertTrue("FavoriteNameField(favSlots[i], i, scope)" in editor)
        assertTrue("Favorites.suggestIds(value, limit = FavoriteNames.LIMIT, maxId = scope.maxDex, kind = scope.kind)" in editor)
        assertTrue("keyboardActions = KeyboardActions(onDone = { hits.firstOrNull()?.let { pick(it.second) }" in editor, "Done takes the top name")
        assertTrue("FavoritesEditor(store, kind, mode, nextRun = false)" in editor, "Tracker Setup during a run")
        assertTrue("FavoritesEditor(store, selectedRom?.first, RulesetCatalog.modeOf(modes, selectedSettings)?.key, nextRun = true)" in read("RunScreen.kt"), "the Run screen")
        // Nowhere else builds favorites boxes of its own.
        val others = File(src).walkTopDown().filter { it.isFile && it.extension == "kt" && it.name != "FavoritesEditor.kt" }
            .filter { f -> f.readText().let { "FavoriteNameField(" in it || "Favorites.suggest" in it } }.map { it.name }.toList()
        assertEquals(emptyList(), others)
        // No randomized data in the list: a picture and a name, nothing from the game's tables.
        val field = editor.substringAfter("internal fun FavoriteNameField(").substringBefore("/** The favorite box's list. */")
        assertFalse("bst" in field.lowercase() || "types" in field)
    }
}
