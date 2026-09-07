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
}
