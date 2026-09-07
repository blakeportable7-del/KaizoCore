package com.ironmonone.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The parser against trimmed real logs: each fixture is the bundled ZX
 * engine's own output for a Kaizo preset on Blake's dumps (2026-09-07), cut
 * to the first rows of every section. Five families, five shapes of the base
 * stats table.
 */
class RandomizerLogTest {
    private fun load(name: String): RandomizerLog =
        RandomizerLog.parse(javaClass.getResource("/logs/$name.log")!!.readText(Charsets.UTF_8))

    @Test
    fun `header, game and settings are read from the first lines and the closing line`() {
        val log = load("emerald")
        assertEquals("4.6.1", log.version)
        assertEquals("186610104527268", log.seed)
        assertTrue(log.settingsString.startsWith("322WRIEE"))
        assertEquals("Emerald (U)", log.game)
    }

    @Test
    fun `Gen 3 base stats carry six stats, two abilities and a held item`() {
        val b = load("emerald").pokemonNamed("BULBASAUR")
        assertNotNull(b)
        assertEquals(listOf("GRASS", "POISON"), b.types)
        assertEquals(listOf("HP", "ATK", "DEF", "SPA", "SPD", "SPE"), b.statNames)
        assertEquals(listOf(56, 22, 107, 31, 84, 17), b.stats)
        assertEquals(317, b.bst)
        assertEquals(listOf("HUGE POWER"), b.abilities)
        assertEquals("TM36 (rare)", b.item)
        assertEquals(listOf("NIDORINA"), b.evolutions)
    }

    @Test
    fun `Gen 1 has five stats and no abilities, Gen 5 has three abilities`() {
        val g1 = load("blue").pokemonNamed("BULBASAUR")!!
        assertEquals(listOf("HP", "ATK", "DEF", "SPE", "SPC"), g1.statNames)
        assertEquals(listOf(38, 26, 72, 43, 74), g1.stats)
        assertTrue(g1.abilities.isEmpty())
        val g5 = load("black2").pokemonNamed("Bulbasaur")!!
        assertEquals(listOf("Heatproof", "Swarm", "Cute Charm"), g5.abilities)
        val g2 = load("silver").pokemonNamed("BULBASAUR")!!
        assertEquals(6, g2.stats.size); assertTrue(g2.abilities.isEmpty())
    }

    @Test
    fun `movesets give level-up moves in order and egg moves separately`() {
        val b = load("emerald").pokemonNamed("BULBASAUR")!!
        assertEquals(1 to "DREAM EATER", b.moves.first())
        assertEquals(46 to "SHADOW BALL", b.moves.last())
        assertTrue(b.moves.size >= 14)
        assertEquals("MAGIC COAT", b.eggMoves.first())
    }

    @Test
    fun `TMs, TM compatibility, trainers with held items, wild sets and starters`() {
        val log = load("emerald")
        assertEquals("COTTON SPORE", log.tms.first { it.number == 1 }.move)
        assertEquals(listOf(2, 5, 6), load("emerald").pokemonNamed("BULBASAUR")!!.tmsLearnable.take(3))
        val t1 = log.trainers.first()
        assertEquals(1, t1.number); assertEquals("HIKER SAWYER", t1.originalName); assertEquals("Designer Jill", t1.name)
        assertEquals("SNEASEL", t1.party[0].name); assertEquals(32, t1.party[0].level)
        // A held item rides on the species with "@", as the engine writes it for a Gen 5 Elite Four member.
        val withItem = RandomizerLog.parse(listOf("v", "s", "x", "--Trainers Pokemon--", "#38 (Elite Four Shauntal => Biker Noelle) - Armaldo Lv84, Terrakion@Sitrus Berry Lv87").joinToString(System.lineSeparator())).trainers.single()
        assertEquals("Sitrus Berry", withItem.party[1].item); assertEquals("Terrakion", withItem.party[1].name); assertEquals(87, withItem.party[1].level)
        assertEquals(null, withItem.party[0].item)
        val r1 = log.routes.first()
        assertEquals("ROUTE 101 Grass/Cave", r1.name); assertEquals(20, r1.rate)
        assertEquals("CASTFORM", r1.encounters[0].name); assertEquals(3, r1.encounters[0].minLevel)
        val ds = load("black2").routes.first().encounters[0]
        assertEquals(68, ds.minLevel); assertEquals(90, ds.maxLevel)
        assertEquals(listOf("SKIPLOOM", "EXEGGUTOR", "FURRET"), log.starters)
        assertEquals("LILEEP" to "SANDSHREW", log.statics.first())
    }

    @Test
    fun `a Gen 1 trainer line with an address suffix and a Gen 4 line without one both parse`() {
        val g1 = load("blue").trainers.first()
        assertEquals("YOUNGSTER", g1.originalName); assertEquals("Rich Girl", g1.name); assertEquals(2, g1.party.size)
        val g4 = load("platinum").trainers.first()
        assertEquals("Youngster Tristan", g4.originalName); assertEquals("WAILORD", g4.party[0].name)
    }
}
