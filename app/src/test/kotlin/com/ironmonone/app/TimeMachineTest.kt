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
    /** The clock that only goes forward (elapsedRealtime on the phone); [at] moves it and the wall clock together. */
    private var clock = 0L
    private fun machine(wait: Long = TimeMachine.WAIT_MS) = TimeMachine(wait) { clock }
    private fun TimeMachine.at(t: Long, enabled: Boolean, inBattle: Boolean, mapKnown: Boolean, mapName: String?, snapshot: () -> ByteArray?) {
        clock = t; tick(t, enabled, inBattle, mapKnown, mapName, snapshot)
    }

    @Test
    fun `a point every four minutes on a map out of battle`() {
        val tm = machine()
        tm.at(T0, enabled = true, inBattle = false, mapKnown = true, mapName = "Route 101", snapshot = snap)
        assertEquals(1, tm.points.size); assertEquals("# 1 - Route 101", tm.points[0].label)
        tm.at(T0 + 60_000, true, false, true, "Route 101", snap)
        assertEquals(1, tm.points.size)
        tm.at(T0 + TimeMachine.WAIT_MS, true, true, true, "Route 101", snap)      // in battle: no
        tm.at(T0 + TimeMachine.WAIT_MS, true, false, false, null, snap)           // no map: no
        tm.at(T0 + TimeMachine.WAIT_MS, false, false, true, null, snap)           // disabled: no
        assertEquals(1, tm.points.size)
        tm.at(T0 + TimeMachine.WAIT_MS, true, false, true, null, snap)
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
        val tm = machine(TimeMachine.GB_WAIT_MS)
        tm.at(T0, enabled = true, inBattle = false, mapKnown = true, mapName = "Route 29", snapshot = snap)
        tm.at(T0 + TimeMachine.WAIT_MS, true, false, true, "Route 29", snap)
        assertEquals(1, tm.points.size)
        tm.at(T0 + 5 * 60_000, true, false, true, "Route 30", snap)
        assertEquals(2, tm.points.size); assertEquals("# 2 - Route 30", tm.points[0].label)
        val play = java.io.File("src/main/kotlin/com/ironmonone/app/PlayScreen.kt").readText().replace("\r\n", "\n")
        assertTrue("val timeMachine = remember(session.id) { TimeMachine.forPlatform(platform) }" in play)
        assertEquals(TimeMachine.GB_WAIT_MS, TimeMachine.forPlatform(com.ironmonone.core.Platform.GBC).waitMs)
        assertEquals(TimeMachine.WAIT_MS, TimeMachine.forPlatform(com.ironmonone.core.Platform.GBA).waitMs)
        assertEquals(TimeMachine.WAIT_MS, TimeMachine.forPlatform(com.ironmonone.core.Platform.NDS).waitMs)
        assertTrue("one is created every \${tm.waitMs / 60_000} minutes." in java.io.File("src/main/kotlin/com/ironmonone/app/TimeMachine.kt").readText())
        // The map now read on a Game Boy game must not start the route records the Game Boy references never keep.
        assertTrue("it.isWildBattle && records && Demo.mode == null && trackerRef != null" in play)
    }

    /**
     * rc33 audit P0-3: a restore through the dialog left `viewing` on, so `cleanup` returned early for the rest of
     * the visit and restore points (tens of MB each on DS) piled up. The dialog now holds the flag in a
     * DisposableEffect, so leaving it any way at all, a restore included, clears the flag and trims.
     */
    @Test
    fun `nothing is trimmed while the list is open, and leaving it any way trims`() {
        val tm = machine()
        tm.viewing = true
        for (i in 1..14) tm.create(null, "Map", i * 1000L, snap)
        assertEquals("kept while the player is choosing", 14, tm.points.size)
        // What the dialog's onDispose does, whichever way it closed.
        tm.viewing = false; tm.cleanup()
        assertEquals(10, tm.points.size)
        assertEquals("# 14 - Map", tm.points[0].label)
        // The wiring: the flag lives in the dialog's DisposableEffect, set nowhere else in composition.
        val side = java.io.File("src/main/kotlin/com/ironmonone/app/SideScreens.kt").readText().replace("\r\n", "\n")
        assertTrue(Regex("DisposableEffect\\(timeMachine\\) \\{\\s*timeMachine.viewing = true\\s*onDispose \\{ timeMachine.viewing = false; timeMachine.cleanup\\(\\) \\}").containsMatchIn(side))
        assertEquals(1, Regex("timeMachine.viewing = true").findAll(side).count())
    }

    /** rc32 audit P2 #52: a DS game's points are capped lower, and Play's periodic point is taken off the main thread. */
    @Test
    fun `a DS game keeps fewer megabytes of points`() {
        val ds = TimeMachine.forPlatform(com.ironmonone.core.Platform.NDS) { 0L }
        val big: () -> ByteArray? = { ByteArray(10 * 1024 * 1024) }
        for (i in 1..10) ds.create(null, "Map", i * 1000L, big)
        assertTrue(ds.points.sumOf { it.bytes.size.toLong() } <= TimeMachine.DS_MAX_BYTES)
        assertEquals(4, ds.points.size)
        assertEquals("# 10 - Map", ds.points[0].label)
        assertEquals("the others as before", TimeMachine.MAX_BYTES, TimeMachine.forPlatform(com.ironmonone.core.Platform.GBA).maxBytes)
        val play = java.io.File("src/main/kotlin/com/ironmonone/app/PlayScreen.kt").readText()
        assertTrue("timeMachine.tick(System.currentTimeMillis(), TrackerOptions.restorePoints, inBattle, mapKnown, trackerState?.routeName, retro)" in play)
    }

    @Test
    fun `ten are kept, newest first, and a restore leaves a return point`() {
        val tm = machine()
        for (i in 1..12) tm.create(null, "Map", i * 1000L, snap)
        assertEquals(10, tm.points.size); assertEquals("# 12 - Map", tm.points[0].label); assertEquals("# 3 - Map", tm.points[9].label)
        tm.backupCurrent(20_000, snap)
        assertEquals(TimeMachine.RETURN_LABEL, tm.points[0].label); assertEquals(10, tm.points.size)
        tm.backupCurrent(21_000, snap)
        assertEquals(1, tm.points.count { it.label == TimeMachine.RETURN_LABEL })
        tm.clear(); assertTrue(tm.points.isEmpty())
        assertNull(machine().create(null, null, 0) { null })
    }

    /**
     * rc32 audit P2 #10: the phone's clock set back. Paced on it, no point was made until it caught up, and sorted by it
     * the newest points went to the end and were the first trimmed. Paced on the clock that only goes forward and kept
     * in the order they were made, the points keep coming and the newest stay.
     */
    @Test
    fun `setting the phone's clock back neither stops the points nor trims the newest`() {
        val tm = machine()
        val wall = 1_800_000_000_000L
        clock = 1_000
        tm.tick(wall, true, false, true, "Route 101", snap)
        clock += TimeMachine.WAIT_MS
        tm.tick(wall - 3_600_000, true, false, true, "Route 102", snap)   // the phone now says an hour earlier
        assertEquals("a point four minutes on, whatever the phone's clock says", 2, tm.points.size)
        for (i in 3..12) { clock += TimeMachine.WAIT_MS; tm.tick(wall - 3_600_000 + i, true, false, true, "Map $i", snap) }
        assertEquals(10, tm.points.size)
        assertEquals("the newest first, and kept", "# 12 - Map 12", tm.points[0].label)
        assertEquals("# 3 - Map 3", tm.points[9].label)
    }
}
