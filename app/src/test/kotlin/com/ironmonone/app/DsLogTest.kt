package com.ironmonone.app

import com.ironmonone.tracker.nds.NdsGameMap
import com.ironmonone.tracker.nds.NdsLogData
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The DS log viewer's rules (DsLog) on real logs: Black 2 and Platinum from
 * Blake's runs, HeartGold from HgssLogFixture. Each skips when its log is not
 * in .vendor/logs (gitignored).
 */
class DsLogTest {
    private fun log(name: String): RandomizerLog? {
        val dir = System.getenv("IRONMON_LOGS")?.let { File(it) } ?: File("../.vendor/logs")
        return File(dir, name).takeIf { it.isFile }?.let { RandomizerLog.parse(it) }
    }

    private fun ds(log: RandomizerLog, starter: Int = 1): DsLog {
        val g = assertNotNull(NdsLogData.gameNamed(DsLog.logGameName(log)), "no game named for '${log.game}'")
        return DsLog(log, g, starter)
    }

    private fun sum(rows: List<DsLog.PivotMon>?) = assertNotNull(rows).sumOf { it.percent }

    @Test
    fun `the tables load for every DS game the tracker reads`() {
        assertEquals(9, NdsLogData.games.size)
        for (code in listOf(0x45414441L, 0x45555043L, 0x454B5049L, 0x4F425249L, 0x4F455249L)) {
            val map = assertNotNull(NdsGameMap.forCode(code), "no map for %08X".format(code))
            val g = assertNotNull(NdsLogData.gameFor(map, ""), "no log tables for ${map.name}")
            assertTrue(NdsLogData.groupRows(g.trainerTable).isNotEmpty(), "no trainer groups for ${g.name}")
            assertTrue(NdsLogData.pivotAreas(g.locationTable).isNotEmpty(), "no pivot areas for ${g.name}")
        }
        // A HeartGold map reading a SoulSilver log picks SoulSilver's entry.
        val hgss = assertNotNull(NdsGameMap.forCode(0x454B5049L))
        assertEquals("Pokemon SoulSilver", NdsLogData.gameFor(hgss, "Pokemon SoulSilver")?.name)
    }

    @Test
    fun `rules the reference spells out`() {
        // TeamInfoScreen.estimateStat.
        assertEquals(160, DsLog.estimateStat("HP", 100, 50, 0))
        assertEquals(120, DsLog.estimateStat("ATK", 100, 50, 30))
        // calculateLevelRangeOfMove: kept until the fourth move after it, never ending before it starts.
        val moves = listOf(1 to "A", 5 to "B", 9 to "C", 13 to "D", 17 to "E")
        assertEquals("Lv. 1 - 16", DsLog.levelRange(0, moves))
        assertEquals("Lv. 5 - 100", DsLog.levelRange(1, moves))
        assertEquals("Lv. 1 - 1", DsLog.levelRange(0, List(5) { 1 to "M$it" }))
        assertEquals("Dark Grass", DsLog.formatPivotType("Route 19", "7", "Doubles Grass"))
        assertEquals("Grass", DsLog.formatPivotType("Route 19", "7", "Grass/Cave"))
        assertEquals("Cave", DsLog.formatPivotType("Dark Cave", "365", "Grass/Cave"))
        assertEquals("Ext. Grass", DsLog.formatPivotType("Virbank Complex", "142", "Grass/Cave"))
    }

    @Test
    fun `Black 2`() {
        val log = log("black2.nds.log") ?: return
        assertEquals("Pokemon Black 2", DsLog.logGameName(log))
        // "Set starter 2 to Haunter": a run that began with Haunter fights Hugh's second team.
        val haunter = assertNotNull(log.pokemonNamed("Haunter")).id
        assertEquals(2, DsLog.starterNumber(log, listOf(haunter)))
        assertEquals(1, DsLog.starterNumber(log, emptyList()))
        val ds = ds(log, 2)
        assertEquals(listOf("Hugh", "Gym Leaders", "Elite 4", "Team Plasma"), ds.groups.map { it.name })
        val hugh = ds.groups[0]
        assertEquals(162, hugh.battles[0].id)
        assertEquals("Hugh 1", hugh.battleName(hugh.battles[0]))
        assertEquals("Lab", hugh.battles[0].location)
        for (g in ds.groups) for (b in g.battles) assertTrue(ds.team(b).isNotEmpty(), "${g.battleName(b)} has no team")
        assertEquals(8, ds.gymTms.size)
        assertEquals(83, ds.gymTms[0].tm)
        assertEquals("Cheren", ds.gymTms[0].leader?.name)
        assertEquals("BW2", ds.gymTms[0].badgeSet)
        // Virbank Complex's two Grass sets are 142 and 148.
        val virbank = assertNotNull(ds.pivots["Virbank Complex"])
        assertTrue("Ext. Grass" in virbank && "Int. Grass" in virbank, "Virbank Complex has ${virbank.keys}")
        for ((area, types) in ds.pivots) types["Grass"]?.let { assertEquals(100, sum(it), "$area Grass") }
        assertNotNull(ds.pivots[ds.pivotAreas[0]])
        assertEquals(listOf(10, 10, 10, 10, 10, 10), ds.statistics.map { it.top.size })
        val bulb = assertNotNull(log.pokemonNamed("Bulbasaur"))
        assertEquals("Level 16", ds.evoText(bulb, 0))
        assertEquals(listOf("1. Heatproof", "2. Swarm", "3. Cute Charm (HA)"), ds.abilityLines(bulb))
        // Genesect's second slot is empty and keeps its place.
        assertEquals("2. ---", ds.abilityLines(assertNotNull(log.pokemonNamed("Genesect")))[1])
        val quickGuard = ds.pokemonWithMove("Quick Guard")
        assertTrue(quickGuard.any { it.pokemon.id == bulb.id && it.label.startsWith("Lv. ") })
        assertEquals("1 of 3 abilities", ds.pokemonWithAbility("Heatproof").first { it.pokemon.id == bulb.id }.label)
        // A move one of Hugh's Pokemon knows at its level finds him.
        val (g, b) = ds.sortedTrainers.first { it.first.isRival }
        val m = ds.team(b).first()
        val known = log.movesAt(assertNotNull(log.pokemonNamed(m.name)), m.level).first()
        assertTrue(ds.trainersWith(known, move = true).any { it.battle === b && it.group === g })
    }

    @Test
    fun `Platinum`() {
        val log = log("platinum.nds.log") ?: return
        assertEquals("Pokemon Platinum", DsLog.logGameName(log))
        val ds = ds(log)
        assertEquals(listOf("Barry", "Gym Leaders", "Elite 4"), ds.groups.map { it.name })
        assertEquals(6, ds.groups[0].battles.size)
        assertEquals(8, ds.gymTms.size)
        assertEquals("Roark", ds.gymTms[0].leader?.name)
        // Route 204's Grass and Old Rod are sets 383 and 386; Route 218's Grass is left out.
        val r204 = assertNotNull(ds.pivots["Route 204"])
        assertEquals(100, sum(r204["Grass"]))
        assertEquals(100, sum(r204["Old Rod"]))
        assertNull(ds.pivots["Route 218"]?.get("Grass"))
        assertNotNull(ds.pivots["Lake Verity"]?.get("Grass"))
        val bulb = assertNotNull(log.pokemonNamed("BULBASAUR"))
        assertEquals("Bulbasaur", DsLog(log, ds.game, 1) { if (it == 1) "Bulbasaur" else null }.nameOf(bulb))
        assertEquals(2, ds.abilityLines(bulb).size)
        assertEquals("---", ds.evoText(assertNotNull(log.pokemonNamed("VENUSAUR")), 0))
    }

    @Test
    fun `HeartGold`() {
        val log = log("heartgold.nds.log") ?: return
        val ds = ds(log)
        assertEquals(listOf("Silver", "Johto Gyms", "Kanto Gyms", "Elite 4 / Bosses"), ds.groups.map { it.name })
        // The two Sprout Tower Grass sets are its two floors.
        assertNotNull(ds.pivots["Sprout Tower 1F"]?.get("Grass"))
        assertNotNull(ds.pivots["Sprout Tower 2F"]?.get("Grass"))
        val r29 = assertNotNull(ds.pivots["Route 29"])
        assertEquals(100, sum(r29["Headbutt(C)"]))
        assertEquals(100, sum(r29["Headbutt(R)"]))
        val bug = assertNotNull(ds.pivots["Bug Catching"])
        assertEquals(setOf("Tuesday", "Thursday", "Saturday"), bug.keys)
        for ((day, rows) in bug) assertEquals(100, sum(rows), day)
        assertNotNull(ds.pivots["Dark Cave"]?.get("Cave"))
        // Gym TMs: Johto, the spacer, then Kanto with its own badges and leaders.
        assertEquals(17, ds.gymTms.size)
        assertEquals(-1, ds.gymTms[8].tm)
        assertEquals("HGSS_K", ds.gymTms[9].badgeSet)
        assertEquals(1, ds.gymTms[9].badge)
        assertEquals("Brock", ds.gymTms[9].leader?.name)
        assertEquals("Falkner", ds.gymTms[0].leader?.name)
    }
}
