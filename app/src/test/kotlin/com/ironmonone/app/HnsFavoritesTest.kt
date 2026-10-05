package com.ironmonone.app

import com.ironmonone.core.RomKind
import com.ironmonone.tracker.GbaTracker
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Heart & Soul as a full KaizoCore game (docs/NEW-GAME-CHECKLIST.md, 2026-10-05): Favorites: nine boxes, icons and the favorite in a ball by Heart & Soul's ids.
 */
class HnsFavoritesTest {
    private val hns = RomKind.HEARTSOUL_KAIZO_206
    private val books = File("src/main/assets/rulesets")



    @Test
    fun `Heart and Soul offers nine favorites, any Pokemon through Gen 9`() {
        assertEquals(Favorites.NAT_DEX_SLOTS, Favorites.slotCount(hns))
        assertEquals(Int.MAX_VALUE, Favorites.maxDex(hns))
        assertTrue(Favorites.inGame("Mamoswine", Favorites.maxDex(hns), hns))
        assertTrue(Favorites.inGame("Pecharunt", Favorites.maxDex(hns), hns))
    }

    @Test
    fun `a favorite's icon is drawn by Heart and Soul's own species id`() {
        // Treecko is 277 in the name table and 252 in Heart & Soul; Bulbasaur is 1 in both.
        assertEquals(277, Favorites.idOf("Treecko", hns))
        assertEquals(252, HnsNumbers.fromPack(277))
        assertEquals(277, HnsNumbers.toPack(252))
        val icons = FavoriteIcons.of(listOf("Treecko", "Bulbasaur", "Not a Pokemon"), hns)
        assertEquals(listOf(252, 1, null), icons.map { it.species })
        assertEquals(listOf("Treecko", "Bulbasaur", "Not a Pokemon"), icons.map { it.name })
        // Every Pokemon of the name table that Heart & Soul has comes back to itself.
        var round = 0
        for ((id, _) in Favorites.namesInOrder(hns)) {
            val h = HnsNumbers.fromPack(id) ?: continue
            assertEquals(id, HnsNumbers.toPack(h), "pack $id -> hns $h")
            round++
        }
        assertTrue(round > 1000, "$round names reach a Heart & Soul species")
        assertEquals(StreamFavoritePictures.From.HNS_PACK, StreamFavoritePictures.from(com.ironmonone.core.Platform.GBA, hns))
    }

    @Test
    fun `the favorite in a ball is matched by Heart and Soul's species, three with the Vanilla pool and nine with Nat Dex`() {
        val favorites = listOf("Pidgey", "Rattata", "Spearow", "Treecko")
        val balls = listOf(GbaTracker.BallOption("LEFT", 252, ""), GbaTracker.BallOption("MIDDLE", 16, ""))
        val bst = mapOf(252 to 310, 16 to 251)
        val nat = FavoriteBall.hnsLines(favorites, "kaizo", natDexPool = true, balls = balls) { bst[it] }
        assertEquals(listOf("FAVORITE! TREECKO IN THE LEFT BALL", "FAVORITE! PIDGEY IN THE MIDDLE BALL"), nat)
        val vanilla = FavoriteBall.hnsLines(favorites, "kaizo", natDexPool = false, balls = balls) { bst[it] }
        assertEquals(listOf("FAVORITE! PIDGEY IN THE MIDDLE BALL"), vanilla, "a Vanilla pool run counts the first three favorites")
        // Read by the Nat. Dex table's id, as Emerald Nat. Dex does, 252 was one of Gen 3's empty slots and matched nothing.
        assertTrue(FavoriteBall.lines(listOf("Treecko"), "kaizo", true, balls.take(1)) { bst[it] }.isEmpty())
    }
}
