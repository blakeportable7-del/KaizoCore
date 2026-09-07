package com.ironmonone.app

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GameSettingsTest {

    private fun fresh() = GameSettings(Files.createTempDirectory("games").toFile())

    @Test
    fun `defaults when nothing was ever saved`() {
        val v = fresh().load("run")
        assertEquals(1, v.speed); assertFalse(v.muted); assertFalse(v.dsTopOnly); assertNull(v.trackerFraction)
    }

    @Test
    fun `each game keeps its own, and a new game inherits only speed and mute`() {
        val s = fresh()
        s.save("run", GameSettings.Values(speed = 8, muted = true, dsTopOnly = true, trackerFraction = 0.4f, floatFrame = listOf(12f, 8f, 300f, 400f)))
        val run = s.load("run")
        assertEquals(8, run.speed); assertTrue(run.muted); assertTrue(run.dsTopOnly); assertEquals(0.4f, run.trackerFraction)
        assertEquals(listOf(12f, 8f, 300f, 400f), run.floatFrame, "2.2: the floating tracker frame rides with the other play settings")

        val other = s.load("lib-deadbeef")
        assertEquals(8, other.speed, "desk setup inherited")
        assertTrue(other.muted)
        assertFalse(other.dsTopOnly, "screen mode is per game")
        assertNull(other.trackerFraction, "pane width is per game")

        s.save("lib-deadbeef", GameSettings.Values(speed = 2, muted = false))
        assertEquals(8, s.load("run").speed, "the run's own file still wins")
        assertEquals(2, s.load("lib-deadbeef").speed)
        assertEquals(2, s.load("lib-never-seen").speed, "the last used desk setup moved on")
    }

    @Test
    fun `a corrupt file is defaults, not a crash, and out-of-range values are dropped`() {
        val dir = Files.createTempDirectory("games").toFile()
        val s = GameSettings(dir)
        File(dir, "run.properties").writeText("speed=999\nmuted=maybe\ntrackerFraction=7")
        val v = s.load("run")
        assertEquals(1, v.speed); assertFalse(v.muted); assertNull(v.trackerFraction)
        File(dir, "x.properties").writeBytes(byteArrayOf(0, 1, 2, -1))
        assertEquals(1, s.load("x").speed)
    }
}
