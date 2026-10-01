package com.ironmonone.app

import com.ironmonone.tracker.GameMap
import com.ironmonone.tracker.GbaTracker
import com.ironmonone.tracker.MemoryReader
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The log viewer's Gen 3 routes against RandomizerLog.parseRoutes, on real
 * randomizer logs (.vendor/logs or IRONMON_LOGS; skipped without them).
 */
class LogRoutesTest {
    private fun log(name: String): RandomizerLog? {
        val dir = System.getenv("IRONMON_LOGS")?.let { File(it) } ?: File("../.vendor/logs")
        return File(dir, name).takeIf { it.isFile }?.let { RandomizerLog.parse(it) }
    }
    private fun tracker(map: GameMap) = GbaTracker(MemoryReader { _, len -> ByteArray(len) }, map)

    @Test
    fun `log text and table names are capitalised the reference's two ways`() {
        assertEquals("Mud-Slap", logTitle("MUD-SLAP"))
        assertEquals("Tate&liza", logTitle("TATE&LIZA"))
        assertEquals("Lt. Surge", logTitle("LT. SURGE"))
        assertEquals("Victory Road 1F", refUpperEachWord("Victory Road 1F"))
        assertEquals("S.S. Anne", refUpperEachWord("S.S. anne"))
    }

    @Test
    fun `the set tables load per game`() {
        val e = tracker(GameMap.EMERALD_U).logRouteSets()
        assertEquals(17, e[1], "Emerald's set 1 is Route 101")
        val rs = tracker(GameMap.RUBY_U).logRouteSets()
        assertEquals(17, rs[85], "Ruby's set 85 is Route 101")
        assertTrue(tracker(GameMap.FIRERED_U_V10).logRouteSets().size > 200)
    }

    @Test
    fun `Emerald's routes carry their wild areas at the reference's slot rates`() {
        val log = log("emerald.gba.log") ?: return
        val t = tracker(GameMap.EMERALD_U)
        val routes = LogRoutes.build(log, LogTrainerRules(t, frlg = false), t)
        assertTrue(routes.size > 50, "only ${routes.size} routes")
        val r101 = routes.first { it.mapId == 17 }
        assertEquals("Route 101", r101.name)
        assertEquals(1.0, r101.areas.getValue(LogEncType.GRASS).sumOf { it.rate }, 1e-9)
        for (r in routes) for ((type, ws) in r.areas) {
            if (ws.isEmpty()) continue
            assertTrue(ws.sumOf { it.rate } <= 1.0 + 1e-9, "${r.name} ${type.label} adds past 100%")
            assertTrue(ws.all { it.levelMin <= it.levelMax && it.pokemon != null })
        }
        // The rods split the one fishing table, and each rod's slots make a whole table.
        val fish = routes.first { it.areas.containsKey(LogEncType.OLDROD) }
        for (rod in listOf(LogEncType.OLDROD, LogEncType.GOODROD, LogEncType.SUPERROD))
            assertEquals(1.0, fish.areas.getValue(rod).sumOf { it.rate }, 1e-9, "${fish.name} ${rod.label}")
        // The default order: the highest wild level, lowest first, routes with no wilds last.
        val keys = routes.map { it.maxWildLv ?: 999 }
        assertEquals(keys.sorted(), keys)
    }

    @Test
    fun `trainers are placed from the tracker's route tables`() {
        val log = log("emerald.gba.log") ?: return
        val t = tracker(GameMap.EMERALD_U)
        val rules = LogTrainerRules(t, frlg = false)
        val routes = LogRoutes.build(log, rules, t)
        val withTrainers = routes.filter { it.trainers.isNotEmpty() }
        assertTrue(withTrainers.size > 20)
        for (r in withTrainers) {
            assertTrue(r.trainers.all { it.number in t.trainersOnRoute(r.mapId) && rules.use(it.number) })
            assertTrue((r.avgTrainerLv ?: 0.0) > 0 && r.minTrainerLv!! <= r.maxTrainerLv!!)
        }
        // Mauville Gym holds Wattson.
        assertTrue(routes.first { it.mapId == 89 }.trainers.any { it.number == 267 })
    }

    @Test
    fun `Ruby's wild sets and trainers land on one route with its name`() {
        // A minimal Ruby log: Victory Road 1F's first trainer (RouteData.Info[163 + offset]) and its
        // grass set, #61, which RouteSetNumToIdMap puts on 163 + offset: the game's raw 164. The
        // tracker keys trainers by its own ids (163), so a raw set id split the route in two, the
        // wild half filed under the tracker's 164, Shoal Cave Lo-1.
        val log = RandomizerLog.parse(listOf(
            "Randomizer Version: 4.6.1", "Random Seed: 1", "Settings String: x",
            "--Pokemon Base Stats & Types--",
            "NUM|NAME|TYPE|HP|ATK|DEF|SATK|SDEF|SPD|ABILITY1|ABILITY2|ITEM",
            "  1|GOLBAT|POISON/FLYING|75|80|70|65|75|90|INNER FOCUS|INNER FOCUS|",
            "  2|HARIYAMA|FIGHTING|144|120|60|40|60|50|THICK FAT|GUTS|",
            "--Trainers Pokemon--",
            "#80 (COOLTRAINER EDGAR) - HARIYAMA Lv44",
            "--Wild Pokemon--",
            "Set #61 - VICTORY ROAD 1F Grass/Cave (rate=10)",
            "GOLBAT Lv38",
            "--End--",
        ).joinToString("\n"))
        val t = tracker(GameMap.RUBY_U)
        val routes = LogRoutes.build(log, LogTrainerRules(t, frlg = false), t)
        val vr = routes.single { it.name == "Victory Road 1F" }
        assertEquals(163, vr.mapId)
        assertEquals(listOf(80), vr.trainers.map { it.number })
        assertEquals(listOf("GOLBAT"), vr.areas.getValue(LogEncType.GRASS).map { it.name })
        assertEquals(1, routes.size, routes.joinToString { "${it.mapId} ${it.name}" })
    }

    @Test
    fun `FireRed maps its sets through its own table`() {
        val log = log("firered.gba.log") ?: return
        val t = tracker(GameMap.FIRERED_U_V10)
        val routes = LogRoutes.build(log, LogTrainerRules(t, frlg = true), t)
        assertTrue(routes.size > 50)
        assertTrue(routes.any { it.name == "Route 1" && it.areas.containsKey(LogEncType.GRASS) })
    }
}
