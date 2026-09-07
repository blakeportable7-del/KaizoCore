package com.ironmonone.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FavoritesTest {
    @Test
    fun `three slots round-trip through the file text and read back as the tracker's set`() {
        assertEquals(listOf("", "", ""), Favorites.slots(""))
        assertNull(Favorites.line(emptyList()))
        val text = Favorites.text(listOf("Scyther", " gengar", ""))
        assertEquals("Scyther,gengar,", text)
        assertEquals(listOf("Scyther", "gengar", ""), Favorites.slots(text))
        // PrepStore.loadFavorites splits on the same separators and lowercases.
        val names = text.split('\n', ',').map { it.trim().lowercase() }.filter { it.isNotEmpty() }
        assertEquals(listOf("scyther", "gengar"), names)
        assertEquals("FAVORITES: SCYTHER / GENGAR", Favorites.line(names))
    }

    @Test
    fun `names resolve through the natdex table, case-insensitively, and nonsense does not`() {
        assertEquals(123, Favorites.idOf("scyther"))
        assertEquals(94, Favorites.idOf("GENGAR"))
        // The table is the Gen 3 build's internal numbering past 251, so Lucario is 473 here, not 448.
        assertEquals(473, Favorites.idOf(" Lucario "))
        assertNull(Favorites.idOf("Blake"))
    }

    /** Typing narrows the list: "s" is many, "sn" fewer, "snorlax" exactly typed is none. */
    @Test
    fun `suggestions start with what was typed, in dex order, and narrow`() {
        val s = Favorites.suggest("s")
        kotlin.test.assertEquals(8, s.size)
        kotlin.test.assertTrue(s.all { it.lowercase().startsWith("s") })
        val sn = Favorites.suggest("sno")
        kotlin.test.assertTrue(sn.size < s.size && sn.all { it.lowercase().startsWith("sno") })
        kotlin.test.assertEquals("Snorlax", Favorites.suggest("snorl").first())
        kotlin.test.assertTrue(Favorites.suggest("snorlax").isEmpty())
        kotlin.test.assertTrue(Favorites.suggest("").isEmpty())
        // Dex order, not alphabetical: Bulbasaur 1, Blastoise 9, Butterfree 12.
        kotlin.test.assertEquals(listOf("Bulbasaur", "Blastoise", "Butterfree"), Favorites.suggest("b").take(3))
    }

    /** Three on the Gen 1 to 3 trackers, four on a Gen 4 DS game, five on Gen 5; the list stops at the game's dex. */
    @Test
    fun `slot count and dex cap follow the game`() {
        val K = com.ironmonone.core.RomKind
        kotlin.test.assertEquals(3, Favorites.slotCount(K.EMERALD_U)); kotlin.test.assertEquals(3, Favorites.slotCount(K.RED_U)); kotlin.test.assertEquals(3, Favorites.slotCount(null))
        kotlin.test.assertEquals(4, Favorites.slotCount(K.PLATINUM_U)); kotlin.test.assertEquals(5, Favorites.slotCount(K.BLACK2_U))
        kotlin.test.assertEquals(151, Favorites.maxDex(K.RED_U)); kotlin.test.assertEquals(251, Favorites.maxDex(K.CRYSTAL_U))
        kotlin.test.assertEquals(386, Favorites.maxDex(K.FIRERED_U_V10)); kotlin.test.assertEquals(493, Favorites.maxDex(K.PLATINUM_U)); kotlin.test.assertEquals(649, Favorites.maxDex(K.WHITE2_U))
        kotlin.test.assertEquals(Int.MAX_VALUE, Favorites.maxDex(K.EMERALD_NATDEX_121))
        kotlin.test.assertEquals(setOf("Mewtwo", "Mew"), Favorites.suggest("mew", maxId = 151).toSet())
        kotlin.test.assertTrue(Favorites.suggest("sni", maxId = 386).isEmpty())
        kotlin.test.assertTrue("Snivy" in Favorites.suggest("sni", maxId = 649))
        kotlin.test.assertEquals(5, Favorites.slots("a,b", 5).size)
    }
}
