package com.ironmonone.app

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The log's search suggestions (Blake, 2026-10-02: "an actual search bar that suggests pokemon as you type the
 * name"), on the Emerald log the tests already read.
 */
class LogSuggestTest {
    private val log = RandomizerLog.parse(File("src/test/resources/logs/emerald.log").readText(Charsets.UTF_8))
    private fun names(q: String) = LogSuggest.pokemon(log, q).map { it.name.uppercase() }

    @Test
    fun `typing the start of a name suggests it, Pokedex order among equals`() {
        assertEquals(listOf("CHARMANDER", "CHARMELEON", "CHARIZARD"), names("char").take(3))
        assertEquals("PIKACHU", names("pika").first())
        assertTrue(names("").isEmpty(), "nothing typed, nothing suggested")
    }

    @Test
    fun `the whole name comes first, then names that start with it, then names that hold it`() {
        assertEquals(listOf("PIDGEOT", "PIDGEOTTO"), names("pidgeot").take(2), "the whole name before a longer one, though later in the Pokedex")
        val pid = names("pid")
        assertTrue(pid.indexOf("PIDGEY") < pid.indexOf("PIDGEOTTO"), "both start with it: Pokedex order")
        val saur = names("saur")
        assertTrue(saur.containsAll(listOf("BULBASAUR", "IVYSAUR", "VENUSAUR")), "a name that holds what was typed")
    }

    @Test
    fun `case, accents, spaces and punctuation do not matter, and the gender signs read as f and m`() {
        assertEquals(LogSuggest.normalize("Mr. Mime"), LogSuggest.normalize("mr mime"))
        assertEquals("pokemon", LogSuggest.normalize("Pokémon"))
        assertEquals("NIDORAN♀", names("nidoran f").first())
        assertEquals("NIDORAN♂", names("nidoranm").first())
    }

    @Test
    fun `one slip still finds it`() {
        assertEquals("CHARIZARD", names("charizrd").first(), "a letter missing")
        assertEquals("CHARIZARD", names("chraizard").first(), "two letters swapped")
        assertTrue("BULBASAUR" in names("bulbz"), "a wrong letter, early in the name")
        assertNull(LogSuggest.rank("Charizard", "zz"), "two letters are too few to guess at")
    }

    @Test
    fun `no more than asked for, and words are offered once each`() {
        assertTrue(LogSuggest.pokemon(log, "a").size <= 6)
        val abilities = LogSuggest.words(log.pokemon.flatMap { it.abilities }, "sand")
        assertEquals(abilities.distinct(), abilities)
        assertTrue(abilities.isNotEmpty() && abilities.all { it.lowercase().contains("sand") })
    }

    @Test
    fun `one edit, and only one`() {
        assertTrue(LogSuggest.withinOneEdit("charizard", "charizrd"))
        assertTrue(LogSuggest.withinOneEdit("abc", "acb"))
        assertTrue(!LogSuggest.withinOneEdit("abcd", "badc"))
        assertTrue(!LogSuggest.withinOneEdit("abc", "abcde"))
    }
}
