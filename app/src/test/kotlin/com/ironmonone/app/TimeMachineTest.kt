package com.ironmonone.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** TimeMachineScreen's rules: the four-minute wait, no points in battle or off a map, ten kept newest first, the return point on restore. */
class TimeMachineTest {
    private val T0 = 1_000_000L
    private var n = 0
    private val snap: () -> ByteArray? = { n++; byteArrayOf(1, 2, 3) }

    @Test
    fun `a point every four minutes on a map out of battle`() {
        val tm = TimeMachine()
        tm.tick(T0, enabled = true, inBattle = false, mapKnown = true, mapName = "Route 101", snapshot = snap)
        assertEquals(1, tm.points.size); assertEquals("# 1 - Route 101", tm.points[0].label)
        tm.tick(T0 + 60_000, true, false, true, "Route 101", snap)
        assertEquals(1, tm.points.size)
        tm.tick(T0 + TimeMachine.WAIT_MS, true, true, true, "Route 101", snap)      // in battle: no
        tm.tick(T0 + TimeMachine.WAIT_MS, true, false, false, null, snap)           // no map: no
        tm.tick(T0 + TimeMachine.WAIT_MS, false, false, true, null, snap)           // disabled: no
        assertEquals(1, tm.points.size)
        tm.tick(T0 + TimeMachine.WAIT_MS, true, false, true, null, snap)
        assertEquals(2, tm.points.size); assertEquals("# 2 - Unknown Area", tm.points[0].label)
    }

    /**
     * Both Game Boy references wait five minutes (TimeMachineScreen.lua:19,
     * "one is created every 5 minutes"), and their map is read now, so a
     * Game Boy game gets its points by itself; the play screen asks for
     * that wait on a Game Boy game and says so in the dialog.
     */
    @Test
    fun `a Game Boy game makes a point every five minutes`() {
        val tm = TimeMachine(TimeMachine.GB_WAIT_MS)
        tm.tick(T0, enabled = true, inBattle = false, mapKnown = true, mapName = "Route 29", snapshot = snap)
        tm.tick(T0 + TimeMachine.WAIT_MS, true, false, true, "Route 29", snap)
        assertEquals(1, tm.points.size)
        tm.tick(T0 + 5 * 60_000, true, false, true, "Route 30", snap)
        assertEquals(2, tm.points.size); assertEquals("# 2 - Route 30", tm.points[0].label)
        val play = java.io.File("src/main/kotlin/com/ironmonone/app/PlayScreen.kt").readText().replace("\r\n", "\n")
        assertTrue("TimeMachine(if (platform == com.ironmonone.core.Platform.GBC) TimeMachine.GB_WAIT_MS else TimeMachine.WAIT_MS)" in play)
        assertTrue("one is created every \${tm.waitMs / 60_000} minutes." in java.io.File("src/main/kotlin/com/ironmonone/app/TimeMachine.kt").readText())
        // The map now read on a Game Boy game must not start the route records the Game Boy references never keep.
        assertTrue("it.isWildBattle && records && Demo.mode == null && trackerRef != null" in play)
    }

    @Test
    fun `ten are kept, newest first, and a restore leaves a return point`() {
        val tm = TimeMachine()
        for (i in 1..12) tm.create(null, "Map", i * 1000L, snap)
        assertEquals(10, tm.points.size); assertEquals("# 12 - Map", tm.points[0].label); assertEquals("# 3 - Map", tm.points[9].label)
        tm.backupCurrent(20_000, snap)
        assertEquals(TimeMachine.RETURN_LABEL, tm.points[0].label); assertEquals(10, tm.points.size)
        tm.backupCurrent(21_000, snap)
        assertEquals(1, tm.points.count { it.label == TimeMachine.RETURN_LABEL })
        tm.clear(); assertTrue(tm.points.isEmpty())
        assertNull(TimeMachine().create(null, null, 0) { null })
    }
}
