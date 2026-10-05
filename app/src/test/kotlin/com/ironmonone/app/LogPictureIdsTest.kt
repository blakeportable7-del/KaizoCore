package com.ironmonone.app

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Which picture a log page asks for (LogPictureIds): the species the game's own picture is looked up by, the way the
 * tracker's screens number it, on each console the Gen 3 viewer serves (Blake, 2026-10-05: "make sure the log uses the
 * game images as the pc tracker log and our logs"). Play's spriteFor turns that id into the ROM's picture on vanilla
 * and the bundled pack's on Nat. Dex, MaxDex and the Game Boy games; the Walking Pals are found by [LogPictureIds.palDex].
 */
class LogPictureIdsTest {
    private fun log(name: String) = assertNotNull(RandomizerLog.parse(File("src/test/resources/logs/$name").readText(Charsets.UTF_8)))

    @Test fun `a Game Boy log's Pokemon page and trainer page ask for the national number`() {
        val blue = log("blue.log")
        val pikachu = assertNotNull(blue.pokemonNamed("PIKACHU"))
        assertEquals(25, LogPictureIds.species(pikachu, LogNames.PLAIN, gameBoy = true), "the Pokemon page")
        // #1 (YOUNGSTER => Rich Girl) - RHYHORN Lv17, DUGTRIO Lv17: the trainer page's team (this fixture's Pokemon
        // list is cut short and has no Rhyhorn, which then has no icon, as on the page).
        val team = blue.trainers.first { it.number == 1 }.party.map { m -> blue.pokemonNamed(m.name)?.let { LogPictureIds.species(it, LogNames.PLAIN, true) } }
        assertEquals(listOf(null, 51), team)
        // Crystal and Silver too: every one of the 251 is its own number.
        val silver = log("silver.log")
        assertTrue(silver.pokemon.isNotEmpty())
        for (p in silver.pokemon) assertEquals(p.id, LogPictureIds.species(p, LogNames.PLAIN, true), p.name)
        assertEquals(WalkingPals.Dex.NATIONAL, LogPictureIds.palDex(null))
    }

    @Test fun `a Gen 3 log asks by the tracker's id for the log's name, never the log's number`() {
        // MaxDex's log numbers MewtwoX 1039, where the tracker's 1039 is Okidogi (LogNames); the tracker's id is asked for.
        val maxdex = log("maxdex.log")
        val mewtwoX = assertNotNull(maxdex.pokemonNamed("MewtwoX"))
        assertEquals(1039, mewtwoX.id)
        val names = LogNames(mapOf(LogNames.key("Mewtwo-X") to 1266, LogNames.key("Mewtwo") to 150, LogNames.key("SNEASEL") to 215), { "" })
        assertEquals(1266, LogPictureIds.species(mewtwoX, names, gameBoy = false), "the Pokemon page")
        assertEquals(150, LogPictureIds.species(assertNotNull(maxdex.pokemonNamed("Mewtwo")), names, gameBoy = false))
        // A name the tracker does not know asks for nothing rather than the log's number, which is not the game's.
        assertNull(LogPictureIds.species(assertNotNull(maxdex.pokemonNamed("MewtwoY")), names, gameBoy = false))
        // The trainer page: Emerald's #1 (Hiker Sawyer) has a Sneasel, asked for by its name.
        val emerald = log("emerald.log")
        val first = emerald.trainers.first { it.number == 1 }.party.first()
        assertEquals("SNEASEL", first.name)
        assertEquals(215, names.speciesId(first.name))
    }

    @Test fun `the viewer asks every page's picture through it`() {
        val viewer = File("src/main/kotlin/com/ironmonone/app/LogViewer.kt").readText().replace("\r\n", "\n")
        assertTrue("LogPictureIds.species(p, names, gameBoy)?.let(sf)" in viewer, "the stills")
        assertTrue("LogPictureIds.species(p, names, gameBoy)?.let { palIndex.find(it, palDex) }" in viewer, "the Walking Pals")
        assertTrue("val gameBoy = tracker == null" in viewer)
        // The Game Boy pages: the grid, the Pokemon page with its evolutions, the trainers' teams.
        assertTrue("LogMonIcon(logSprite?.invoke(p), logPal?.invoke(p), 32.dp, null)" in viewer)
        assertTrue("PokemonDetail(d, log, onBack = { detail = null }, spriteOf = logSprite, palOf = logPal, onPokemon = { detail = it })" in viewer)
        assertTrue("LogMonIcon(mon?.let { logSprite?.invoke(it) }, mon?.let { logPal?.invoke(it) }, 28.dp, null)" in viewer)
        // A route page's wild Pokemon stand idle like the rest.
        assertTrue("palOf = logPal, portraitOf = portraitOf)" in viewer)
        // And the player's head from the ROM on its Trainers choice, as the Trainers tab has it.
        assertTrue("playerHead = playerHead, palOf = logPal" in viewer)
        val routes = File("src/main/kotlin/com/ironmonone/app/LogRoutes.kt").readText()
        assertTrue("LogMonIcon(w.pokemon?.let { spriteOf?.invoke(it) }, w.pokemon?.let { palOf?.invoke(it) }, 36.dp, shown)" in routes)
    }
}
