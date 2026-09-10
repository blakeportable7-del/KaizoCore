package com.ironmonone.app

import com.ironmonone.tracker.GameMap
import com.ironmonone.tracker.GbaTracker
import com.ironmonone.tracker.MemoryReader
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * LogSearchScreen's filters and sorts, the Pokemon page's TM list and stats,
 * against the reference, on a real Emerald log (.vendor/logs or IRONMON_LOGS).
 */
class LogSearchTest {
    private fun log(name: String): RandomizerLog? {
        val dir = System.getenv("IRONMON_LOGS")?.let { File(it) } ?: File("../.vendor/logs")
        return File(dir, name).takeIf { it.isFile }?.let { RandomizerLog.parse(it) }
    }
    private fun tracker(map: GameMap) = GbaTracker(MemoryReader { _, len -> ByteArray(len) }, map)

    @Test
    fun `IVs and EVs follow the reference's stat order`() {
        assertEquals(listOf(1, 2, 3, 5, 6, 4), LogSearch.refOrder(listOf(1, 2, 3, 4, 5, 6)))
    }

    @Test
    fun `each tab offers the reference's sorts and filters, defaults first`() {
        assertEquals(LogSort.POKEDEX, LogSearch.defaultSort(LogTab.POKEMON))
        assertEquals(LogSort.WILD, LogSearch.defaultSort(LogTab.ROUTES))
        assertEquals(LogFilter.TRAINER, LogSearch.defaultFilter(LogTab.TRAINERS))
        assertTrue(LogSort.BST in LogSearch.sortsFor(LogTab.POKEMON) && LogSort.BST !in LogSearch.sortsFor(LogTab.TRAINERS))
        assertEquals(5, LogSearch.filtersFor(LogTab.ROUTES).size)
    }

    @Test
    fun `base stats are read by name, as the moveset block repeats them`() {
        val log = log("emerald.gba.log") ?: return
        val b = assertNotNull(log.pokemonNamed("BULBASAUR"))
        // The log's moveset block for this seed: HP 56, ATK 22, DEF 107, SPA 31, SPD 84, SPE 17.
        assertEquals(listOf(56, 22, 107, 31, 84, 17), listOf("HP", "ATK", "DEF", "SPA", "SPD", "SPE").map { LogSearch.statOf(b, it) })
    }

    @Test
    fun `the Pokemon tab filters and sorts as the search screen does`() {
        val log = log("emerald.gba.log") ?: return
        val byBst = LogSearch.pokemonRows(log, "", LogFilter.NAME, LogSort.BST).map { it.bst }
        assertEquals(byBst.sortedDescending(), byBst)
        val bySpeed = LogSearch.pokemonRows(log, "", LogFilter.NAME, LogSort.SPE).map { LogSearch.statOf(it, "SPE") }
        assertEquals(bySpeed.sortedDescending(), bySpeed)
        assertTrue(LogSearch.pokemonRows(log, "bulba", LogFilter.NAME, LogSort.POKEDEX).any { it.name == "BULBASAUR" })
        val b = log.pokemonNamed("BULBASAUR")!!
        val ability = b.abilities.first()
        assertTrue(LogSearch.pokemonRows(log, ability, LogFilter.ABILITY, LogSort.POKEDEX).all { p -> p.abilities.any { it.contains(ability, true) } })
        val move = b.moves.last().second
        assertTrue(LogSearch.pokemonRows(log, move, LogFilter.MOVE, LogSort.POKEDEX).any { it.name == "BULBASAUR" })
    }

    @Test
    fun `the TM list puts the gym TMs first and adds the ones it cannot learn`() {
        val log = log("emerald.gba.log") ?: return
        val p = log.pokemon.first { it.tmsLearnable.isNotEmpty() }
        val rows = LogSearch.tmRows(p, log, frlg = false, showUnlearnable = true)
        assertEquals("Gym TMs", rows.first().label)
        val other = rows.indexOfFirst { it.label == "Other TMs" }
        val gym = rows.subList(1, other)
        assertEquals(8, gym.size, "with the unlearnable ones every gym TM appears once")
        assertEquals(gym.sortedBy { it.gym }.map { it.number }, gym.map { it.number })
        val gymNumbers = LogTms.gymTmNumbers(false)
        assertEquals(gymNumbers.filter { it !in p.tmsLearnable }.toSet(), gym.filter { it.unlearnable }.map { it.number }.toSet())
        val plain = LogSearch.tmRows(p, log, frlg = false, showUnlearnable = false)
        assertTrue(plain.none { it.unlearnable })
        assertEquals(p.tmsLearnable.size + 2, plain.size)
    }

    @Test
    fun `pre-evolutions are the log's evolutions read backwards`() {
        val log = log("emerald.gba.log") ?: return
        val e = log.pokemon.first { it.evolutions.isNotEmpty() }
        val target = assertNotNull(log.pokemonNamed(e.evolutions.first()))
        assertTrue(e in LogSearch.preEvolutions(log, target))
    }

    @Test
    fun `trainers and routes search by a party Pokemon and by a trainer`() {
        val log = log("emerald.gba.log") ?: return
        val t = tracker(GameMap.EMERALD_U)
        val rules = LogTrainerRules(t, frlg = false)
        // Roxanne's team holds a Caterpie in this seed.
        assertTrue(rules.rows(log, LogTrainerFilter.GYM, "caterpie", false, LogFilter.NAME).any { it.number == 265 })
        val routes = LogRoutes.build(log, rules, t)
        assertTrue(LogSearch.routeRows(routes, log, "wattson", LogFilter.TRAINER, LogSort.WILD).any { it.mapId == 89 })
        val alpha = LogSearch.routeRows(routes, log, "", LogFilter.ROUTE, LogSort.ALPHA).map { it.name }
        assertEquals(alpha.sorted(), alpha)
    }

    @Test
    fun `share text is the reference's four lines`() {
        val log = log("emerald.gba.log") ?: return
        val lines = logShareText(log).lines()
        assertTrue(lines[0].startsWith("Pokémon Game: ") && lines[2].startsWith("Random Seed: ") && lines[4].startsWith("Settings String: "))
        assertEquals("", lines[3])
    }
}
