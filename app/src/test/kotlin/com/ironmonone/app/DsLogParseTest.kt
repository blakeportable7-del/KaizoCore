package com.ironmonone.app

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The randomizer log parser on real DS logs (.vendor/logs or IRONMON_LOGS).
 * Black 2 writes names in mixed case and Platinum in capitals, and a DS
 * trainer line has no "@address" after the names, unlike Gen 3's.
 */
class DsLogParseTest {
    private fun log(name: String): RandomizerLog? {
        val dir = System.getenv("IRONMON_LOGS")?.let { File(it) } ?: File("../.vendor/logs")
        return File(dir, name).takeIf { it.isFile }?.let { RandomizerLog.parse(it) }
    }

    @Test
    fun `Black 2's log reads in full`() {
        val log = log("black2.nds.log") ?: return
        // The log also lists alternate forms after the 649 (Basculin, Darmanitan, the Forces of Nature...).
        assertTrue((1..649).all { n -> log.pokemon.any { it.id == n } }, "a national number from 1 to 649 is missing")
        assertTrue(log.trainers.size > 500, "only ${log.trainers.size} trainers")
        assertTrue(log.tms.size >= 95, "only ${log.tms.size} TMs")
        assertTrue(log.routes.size > 100, "only ${log.routes.size} wild sets")
        assertEquals(listOf("Espeon", "Haunter", "Crustle"), log.starters)
        // "#1 (Smasher Elena => Schoolgirl Crystal) - Solrock Lv39"
        val t1 = assertNotNull(log.trainers.firstOrNull { it.number == 1 })
        assertEquals("Smasher" to "Elena", t1.cls to t1.shortName)
        assertEquals("Schoolgirl" to "Crystal", t1.customCls to t1.customShortName)
        assertEquals(listOf("Solrock" to 39), t1.party.map { it.name to it.level })
        // The moveset block for Bulbasaur: HP 68, ATK 59, DEF 44, SPA 26, SPD 61, SPE 60.
        val b = assertNotNull(log.pokemonNamed("Bulbasaur"))
        assertEquals(listOf(68, 59, 44, 26, 61, 60), listOf("HP", "ATK", "DEF", "SPA", "SPD", "SPE").map { LogSearch.statOf(b, it) })
        assertEquals("Quick Guard", b.moves.first().second)
        assertTrue(b.abilities.isNotEmpty())
        for (t in log.trainers) for (m in t.party) assertNotNull(log.pokemonNamed(m.name), "trainer ${t.number}'s ${m.name} is not in the log's Pokemon")
    }

    @Test
    fun `Platinum's log reads in full`() {
        val log = log("platinum.nds.log") ?: return
        // The log also lists alternate forms after the 493 (Deoxys, Rotom, Giratina, Shaymin...).
        assertTrue((1..493).all { n -> log.pokemon.any { it.id == n } }, "a national number from 1 to 493 is missing")
        assertTrue(log.trainers.size > 500, "only ${log.trainers.size} trainers")
        assertEquals(listOf("CROCONAW", "PICHU", "KABUTOPS"), log.starters)
        val t1 = assertNotNull(log.trainers.firstOrNull { it.number == 1 })
        assertEquals("Youngster" to "Tristan", t1.cls to t1.shortName)
        assertEquals(listOf("WAILORD" to 8), t1.party.map { it.name to it.level })
        val b = assertNotNull(log.pokemonNamed("BULBASAUR"))
        assertEquals(listOf(66, 35, 51, 79, 40, 46), listOf("HP", "ATK", "DEF", "SPA", "SPD", "SPE").map { LogSearch.statOf(b, it) })
        assertTrue(log.routes.any { it.name.contains("Old Rod") })
        for (t in log.trainers) for (m in t.party) assertNotNull(log.pokemonNamed(m.name), "trainer ${t.number}'s ${m.name} is not in the log's Pokemon")
    }
}
