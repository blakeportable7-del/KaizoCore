package com.ironmonone.app

import com.ironmonone.tracker.GameMap
import com.ironmonone.tracker.GbaTracker
import com.ironmonone.tracker.MemoryReader
import java.io.File
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** What the Gen 3 log viewer reads before it draws, off the main thread (rc32 audit P2 #27 and #28). */
class LogViewerLoadTest {
    private fun tracker(map: GameMap) = GbaTracker(MemoryReader { _, n -> ByteArray(n) }, map)

    @Test
    fun `a Nat Dex run's move types run to its last move, and a vanilla run's to Psycho Boost`() {
        // rc32 audit P2 #28: the map stopped at 354, so Roost or Dragon Pulse were never drawn as same-type.
        val natDex = logMoveTypes(tracker(GameMap.EMERALD_U.copy(namesFromLists = true, expandedSpeciesIds = true)))
        assertTrue("DRAGON PULSE" in natDex && "ROOST" in natDex && "MALIGNANT CHAIN" in natDex, "${natDex.size} moves")
        assertTrue("PSYCHO BOOST" in natDex)
        assertTrue(natDex.size > 354, "${natDex.size} moves")
    }

    @Test
    fun `the viewer's data is the cached log with the tables beside it`() {
        val f = File.createTempFile("view", ".log")
        try {
            f.writeText(javaClass.getResource("/logs/emerald.log")!!.readText(Charsets.UTF_8))
            val t = tracker(GameMap.EMERALD_U)
            val data = loadLogView(f, t, LogTrainerRules(t, frlg = false))
            assertSame(RandomizerLog.parse(f), data.log, "Open Book's parse of the same file is this one")
            assertTrue(data.routes.any { it.mapId == 17 }, "Route 101")
            assertTrue(data.names !== LogNames.PLAIN, "the game's names, from its tracker (LogNames)")
            val plain = loadLogView(f, null, null)
            assertNotNull(plain.log)
            assertTrue(plain.moveTypes.isEmpty() && plain.routes.isEmpty())
            assertSame(LogNames.PLAIN, plain.names)
        } finally { f.delete() }
    }
}
