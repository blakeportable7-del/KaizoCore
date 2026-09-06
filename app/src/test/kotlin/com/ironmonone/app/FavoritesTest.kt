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
}
