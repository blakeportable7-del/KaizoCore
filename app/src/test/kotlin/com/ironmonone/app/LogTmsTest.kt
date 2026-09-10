package com.ironmonone.app

import com.ironmonone.tracker.GameMap
import com.ironmonone.tracker.GbaTracker
import com.ironmonone.tracker.MemoryReader
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/** LogTabTMs' Gym TMs rows against TrainerData.GymTMs, on real logs (.vendor/logs or IRONMON_LOGS). */
class LogTmsTest {
    private fun log(name: String): RandomizerLog? {
        val dir = System.getenv("IRONMON_LOGS")?.let { File(it) } ?: File("../.vendor/logs")
        return File(dir, name).takeIf { it.isFile }?.let { RandomizerLog.parse(it) }
    }
    private fun tracker(map: GameMap) = GbaTracker(MemoryReader { _, len -> ByteArray(len) }, map)

    @Test
    fun `Emerald's gym TMs go to its leaders in order`() {
        val log = log("emerald.gba.log") ?: return
        val rows = LogTms.gymRows(log, LogTrainerRules(tracker(GameMap.EMERALD_U), frlg = false), frlg = false)
        assertEquals(listOf(39, 8, 34, 50, 42, 40, 4, 3), rows.map { it.number })
        assertEquals((265..272).toList(), rows.map { it.leader?.number })
        assertEquals("Roxanne", logTitle(rows.first().leader!!.shortName))
        assertEquals(log.tms.first { it.number == 39 }.move, rows.first().move)
    }

    @Test
    fun `FireRed's gym TMs are its own`() {
        val log = log("firered.gba.log") ?: return
        val rows = LogTms.gymRows(log, LogTrainerRules(tracker(GameMap.FIRERED_U_V10), frlg = true), frlg = true)
        assertEquals(listOf(39, 3, 34, 19, 6, 4, 38, 26), rows.map { it.number })
        assertEquals(414, rows.first().leader?.number, "Brock gives TM39")
        assertEquals(8, rows.count { it.leader != null })
    }
}
