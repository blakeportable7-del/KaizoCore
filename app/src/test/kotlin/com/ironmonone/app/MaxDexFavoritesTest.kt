package com.ironmonone.app

import com.ironmonone.app.stream.StreamFavorites
import com.ironmonone.core.RomKind
import com.ironmonone.tracker.GbaTracker
import java.io.File
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * MaxDex's favorites by MaxDex's own species ids (found 2026-10-03). MaxDex 1.0's ids are Nat. Dex 1.2.1's up to 1235
 * and part ways after: its 45 Legends Z-A Megas are 1236 to 1280 in another order, and Greninja-B, Squawkabilly-W and
 * Meowstic-F-M are not in it. Favorites were read from the Nat. Dex table on every game, so on MaxDex a Dragonite-M
 * favorite drew Starmie-M's icon (Nat. Dex's 1241 is MaxDex's Starmie-M), matched a Starmie-M in a ball, and a Greninja-B
 * favorite drew Dragonite-M (Nat. Dex's 1236). The card, the lab line, the stream's pictures and the Kaizo IronMON
 * screen's list now read MaxDex's own table (tracker-gba maxdex/species.tsv); every other game is as it was.
 */
class MaxDexFavoritesTest {
    private val maxDex = RomKind.FIRERED_MAXDEX_10
    private val natDex = RomKind.FIRERED_NATDEX_121

    /** MaxDex's own table, read here as the tracker ships it. */
    private val maxDexRows: List<Pair<Int, String>> = File("../tracker-gba/src/main/resources/maxdex/species.tsv").readLines(Charsets.UTF_8)
        .mapNotNull { l -> l.split('\t').takeIf { it.size >= 2 }?.let { (id, name) -> id.trim().toIntOrNull()?.let { it to name.trim() } } }
    private val zaMegas = maxDexRows.filter { it.first in 1236..1280 }

    @Test
    fun `every Z-A Mega favorite draws MaxDex's own id on MaxDex, and Nat Dex's on Nat Dex`() {
        assertEquals(45, zaMegas.size, "the 45 Legends Z-A Megas")
        for ((id, name) in zaMegas) {
            assertEquals(FavoriteIcon(name, id), FavoriteIcons.of(listOf(name), maxDex).single(), name)
            assertEquals(id, Favorites.idOf(name, maxDex), name)
        }
        // Dragonite-M: MaxDex's 1236, Nat. Dex 1.2.1's 1241, which is MaxDex's Starmie-M.
        assertEquals(FavoriteIcon("Dragonite-M", 1236), FavoriteIcons.of(listOf("dragonite-m"), maxDex).single())
        assertEquals(FavoriteIcon("Dragonite-M", 1241), FavoriteIcons.of(listOf("Dragonite-M"), natDex).single(), "Nat. Dex as it was")
        assertEquals("Starmie-M", Favorites.nameOf(1241, maxDex))
        // What MaxDex lacks draws nothing there, and is still in Nat. Dex 1.2.1.
        for (name in listOf("Greninja-B", "Squawkabilly-W", "Meowstic-F-M")) {
            assertEquals(FavoriteIcon(name, null), FavoriteIcons.of(listOf(name), maxDex).single(), "$name on MaxDex")
            assertTrue(FavoriteIcons.of(listOf(name), natDex).single().species != null, "$name on Nat. Dex")
        }
        // Up to 1235 the two tables agree, so nothing else moved.
        for (name in listOf("Gengar", "Treecko", "Garchomp", "Charizard-X", "Terapagos-S"))
            assertEquals(FavoriteIcons.of(listOf(name), natDex), FavoriteIcons.of(listOf(name), maxDex), name)
    }

    @Test
    fun `the lab names a MaxDex favorite by MaxDex's id, and never another Pokemon in its place`() {
        // BSTs the Kaizo limit takes (the ROM's own come from the tracker): the test is which favorite a ball holds.
        val bst = mapOf(1236 to 600, 1241 to 600, 94 to 500)
        fun lines(favorites: List<String>, vararg ball: Pair<String, Int>) =
            FavoriteBall.lines(favorites, "kaizo", true, ball.map { (place, sp) -> GbaTracker.BallOption(place, sp, "") }, maxDex = true) { bst[it] }
        // MaxDex's Dragonite-M (1236) in the left ball: named.
        assertEquals(listOf("FAVORITE! DRAGONITE-M IN THE LEFT BALL"), lines(listOf("Dragonite-M"), "LEFT" to 1236))
        // MaxDex's Starmie-M (1241, Nat. Dex's Dragonite-M) holds no favorite of the player's.
        assertTrue(lines(listOf("Dragonite-M"), "LEFT" to 1241).isEmpty(), "Starmie-M is not Dragonite-M")
        // Greninja-B is Nat. Dex's 1236: on MaxDex that ball is Dragonite-M's, and no Greninja-B is in MaxDex.
        assertTrue(lines(listOf("Greninja-B"), "LEFT" to 1236).isEmpty())
        assertEquals(listOf("FAVORITE! GENGAR IN THE RIGHT BALL"), lines(listOf("Gengar"), "RIGHT" to 94))
    }

    @Test
    fun `the Kaizo IronMON screen offers MaxDex's names when MaxDex is the game`() {
        val greninja = Favorites.suggest("gren", kind = maxDex)
        assertTrue("Greninja-M" in greninja && "Greninja" in greninja, greninja.toString())
        assertFalse("Greninja-B" in greninja, "MaxDex has no Greninja-B")
        assertFalse("Squawkabilly-W" in Favorites.suggest("squawk", kind = maxDex))
        assertFalse(Favorites.suggest("non", kind = maxDex).any { it.equals("none", ignoreCase = true) }, "the Gen 3 gap rows are no Pokemon")
        // The red name: a name MaxDex lacks is not in the game; one it has is.
        val max = Favorites.maxDex(maxDex)
        assertFalse(Favorites.inGame("Greninja-B", max, maxDex))
        assertTrue(Favorites.inGame("Baxcalibur-M", max, maxDex))
        // Every other game is as it was: Nat. Dex 1.2.1 offers Greninja-B, and a game-less call reads the Nat. Dex table.
        assertTrue("Greninja-B" in Favorites.suggest("gren", kind = natDex))
        assertEquals(Favorites.suggest("gren"), Favorites.suggest("gren", kind = natDex))
        assertTrue(Favorites.inGame("Greninja-B", Favorites.maxDex(natDex), natDex))
        assertEquals(Favorites.idOf("Dragonite-M"), Favorites.idOf("Dragonite-M", natDex))
        // The screen asks with the game picked: the Run screen hands its game to the shared editor (FavoritesEditor.kt).
        assertTrue("FavoritesEditor(store, selectedRom?.first, " in File("src/main/kotlin/com/ironmonone/app/RunScreen.kt").readText())
        val run = File("src/main/kotlin/com/ironmonone/app/FavoritesEditor.kt").readText().replace("\r\n", "\n")
        assertTrue("Favorites.suggest(favSlots[favActive], maxId = favMax, kind = kind)" in run)
        assertEquals(2, Regex("""Favorites\.inGame\((v|it), favMax, kind\)""").findAll(run).count(), "both red-name checks")
        assertFalse(Regex("""Favorites\.inGame\((v|it), favMax\)""").containsMatchIn(run))
    }

    @Test
    fun `the stream's picture for a MaxDex favorite is MaxDex's own icon`() {
        // Play's spriteFor on MaxDex draws ids 412 to 1280 from MaxDex's own pack (PcAssets.gbaSprite with nameSet "maxdex").
        fun card(sp: Int): ByteArray? =
            File(if (sp in 412..1280) "src/main/assets/gbasprites-maxdex" else "src/main/assets/gbasprites", "$sp.png").takeIf { it.isFile }?.readBytes()
        val pictures = StreamFavorites.pictures(StreamFavorites.icons(listOf("Dragonite-M", "", "Greninja-B", "Gengar"), maxDex), ::card)
        assertContentEquals(File("src/main/assets/gbasprites-maxdex/1236.png").readBytes(), pictures[0], "Dragonite-M's own icon")
        assertNull(pictures[1], "an empty box")
        assertNull(pictures[2], "MaxDex has no Greninja-B: an empty picture, not Dragonite-M's")
        assertContentEquals(File("src/main/assets/gbasprites/94.png").readBytes(), pictures[3])
        val pc = File("src/main/kotlin/com/ironmonone/app/PcTracker.kt").readText()
        assertTrue("if (nameSet == \"maxdex\" && species in 412..1280) load(context, \"gbasprites-maxdex/\$species.png\") else gbaSprite(context, species)" in pc,
            "the pack rule this test's card follows")
    }
}
