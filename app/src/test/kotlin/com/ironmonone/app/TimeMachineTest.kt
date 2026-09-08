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
