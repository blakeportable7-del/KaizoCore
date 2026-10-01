package com.ironmonone.app

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The Play as your Pokemon picker's search (Blake, 2026-09-30: "type a name should suggest names of all the pokemon
 * with the letters you type"): every name holding the letters, those that start with them first.
 */
class SpriteIsMeSearchTest {
    private val names = listOf(
        10 to "Caterpie", 16 to "Pidgey", 17 to "Pidgeotto", 18 to "Pidgeot", 25 to "Pikachu", 37 to "Vulpix",
        70 to "Weepinbell", 122 to "Mr. Mime", 127 to "Pinsir", 172 to "Pichu", 439 to "Mime Jr.", 669 to "Flabébé",
    )

    private fun search(q: String) = SpriteIsMeSearch.matches(names, q).map { it.second }

    @Test
    fun `names that start with the letters come first, then every other name holding them`() {
        assertEquals(
            listOf("Pidgey", "Pidgeotto", "Pidgeot", "Pikachu", "Pinsir", "Pichu", "Caterpie", "Vulpix", "Weepinbell"),
            search("pi"),
        )
    }

    @Test
    fun `case, spaces and dots do not count`() {
        assertEquals(search("pi"), search(" PI "))
        assertEquals(listOf("Mr. Mime"), search("mrmime"))
        assertEquals(listOf("Mr. Mime"), search("Mr. Mime"))
    }

    @Test
    fun `a later word that starts with the letters comes before a match inside a word`() {
        // Mime Jr. starts with the letters; Mr. Mime, earlier in the Pokedex, has them at the start of a later word.
        assertEquals(listOf("Mime Jr.", "Mr. Mime"), search("mime"))
        assertEquals(listOf("Pikachu", "Pichu"), search("chu"))
    }

    @Test
    fun `accents do not count`() {
        assertEquals(listOf("Flabébé"), search("flabebe"))
    }

    @Test
    fun `an empty search lists everything in order`() {
        assertEquals(names.map { it.second }, search(""))
        assertEquals(names.map { it.second }, search("   "))
    }

    @Test
    fun `no match is an empty list`() {
        assertEquals(emptyList(), search("zzz"))
    }
}
