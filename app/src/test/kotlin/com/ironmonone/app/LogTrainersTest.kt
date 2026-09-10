package com.ironmonone.app

import com.ironmonone.tracker.GameMap
import com.ironmonone.tracker.GbaTracker
import com.ironmonone.tracker.MemoryReader
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The log viewer's Gen 3 trainer rules against the reference's (TrainerData.lua,
 * LogTabTrainers.lua, RandomizerLog.lua), on real randomizer logs. The logs sit in
 * .vendor/logs (gitignored, like the dumps) or IRONMON_LOGS; without them the
 * log-backed tests skip.
 */
class LogTrainersTest {
    private fun log(name: String): RandomizerLog? {
        val dir = System.getenv("IRONMON_LOGS")?.let { File(it) } ?: File("../.vendor/logs")
        return File(dir, name).takeIf { it.isFile }?.let { RandomizerLog.parse(it) }
    }
    private fun tracker(map: GameMap) = GbaTracker(MemoryReader { _, len -> ByteArray(len) }, map)

    @Test
    fun `class and name split the way the reference splits them`() {
        assertEquals("LEADER" to "ROXANNE", RandomizerLog.splitClassAndName("LEADER ROXANNE"))
        assertEquals("LEADER" to "LT. SURGE", RandomizerLog.splitClassAndName("LEADER LT. SURGE"))
        assertEquals("YOUNG COUPLE" to "GIA & JES", RandomizerLog.splitClassAndName("YOUNG COUPLE GIA & JES"))
        assertEquals("" to "PRESCHOOLER", RandomizerLog.splitClassAndName("Preschooler".uppercase()))
        assertEquals("Chief" to "Kate", RandomizerLog.splitClassAndName("Chief Kate"))
        assertEquals("Leader Lt. Surge", logTitle("LEADER LT. SURGE"))
    }

    @Test
    fun `the exclusion lists expand the reference's ranges`() {
        val rse = LogTrainerRules.ranges(LogTrainerRules.RSE_EXCLUDED)
        assertTrue(117 in rse && 40 in rse && 43 in rse && 855 in rse && 801 in rse)
        assertFalse(44 in rse || 265 in rse)
        val frlg = LogTrainerRules.ranges(LogTrainerRules.FRLG_EXCLUDED)
        assertTrue(1 in frlg && 88 in frlg && 530 in frlg && 741 in frlg)
        assertFalse(89 in frlg || 414 in frlg)
    }

    @Test
    fun `every trainer mon knows the last four moves it learned at or below its level`() {
        val log = log("emerald.gba.log") ?: return
        var checked = 0
        for (t in log.trainers) for (m in t.party) {
            val p = log.pokemonNamed(m.name) ?: continue
            val expected = p.moves.filter { it.first <= m.level }.takeLast(4).map { it.second }
            assertEquals(expected, log.movesAt(p, m.level), "${t.originalName}'s ${m.name} Lv${m.level}")
            checked++
        }
        assertTrue(checked > 500, "only $checked party Pokemon were checked")
    }

    @Test
    fun `Emerald's gym filter lists the eight leaders in badge order`() {
        val log = log("emerald.gba.log") ?: return
        val rules = LogTrainerRules(tracker(GameMap.EMERALD_U), frlg = false)
        val gym = rules.rows(log, LogTrainerFilter.GYM, "", custom = false)
        assertEquals((1..8).toList(), gym.map { rules.gymNumber(it.number) })
        assertEquals("Roxanne", logTitle(gym.first().shortName))
        assertEquals("Leader", logTitle(gym.first().cls))
        // The custom names are the randomizer's: "LEADER ROXANNE => Chief Kate".
        assertEquals("Kate", rules.displayName(gym.first(), custom = true))
        assertEquals(6, gym.first().party.size)
        assertEquals(23, gym.first().maxLevel)
        // A search looks past the filter.
        assertTrue(rules.rows(log, LogTrainerFilter.GYM, "wattson", custom = false).any { it.number == 267 })
    }

    @Test
    fun `FireRed drops its excluded trainers and shows its rivals by class`() {
        val log = log("firered.gba.log") ?: return
        val rules = LogTrainerRules(tracker(GameMap.FIRERED_U_V10), frlg = true)
        val all = rules.rows(log, LogTrainerFilter.ALL, "", custom = false)
        assertFalse(all.any { it.number in 1..88 }, "the reference excludes 1 to 88 on FireRed")
        val gym = rules.rows(log, LogTrainerFilter.GYM, "", custom = false)
        assertEquals(8, gym.size)
        assertEquals(414, gym.first().number, "Brock leads the gym list")
        val rival = assertNotNull(log.trainers.firstOrNull { it.number == 326 })
        assertEquals("Rival", rules.displayName(rival, custom = false))
        assertTrue(rules.isGiovanni(348))
    }
}
