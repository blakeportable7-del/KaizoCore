package com.ironmonone.app

import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** "Track PC Heals" (TrackerScreen.lua PCHeal buttons, Program.lua:1203, Utils.getCenterHealColor). */
class PcHealsTest {
    private val dir = Files.createTempDirectory("pch").toFile()
    private val file get() = java.io.File(dir, "pc-heals.txt")

    @BeforeTest fun setUp() {
        TrackerOptions.trackPcHeals = true; TrackerOptions.pcHealsCountDownward = true
        PcHeals.autoTracking = false
        PcHeals.load(file)
    }
    @AfterTest fun tearDown() {
        TrackerOptions.trackPcHeals = false; TrackerOptions.pcHealsCountDownward = true
        PcHeals.autoTracking = false
        dir.deleteRecursively()
    }

    @Test
    fun `counting down starts at 10, up at 0, and stays within 0 to 99`() {
        assertEquals(10, PcHeals.count(1))
        TrackerOptions.pcHealsCountDownward = false
        assertEquals(0, PcHeals.count(2))
        PcHeals.add(2, -5); assertEquals(0, PcHeals.count(2))
        PcHeals.add(2, 150); assertEquals(99, PcHeals.count(2))
    }

    @Test
    fun `a new heal moves the count only with the heart on`() {
        PcHeals.observe(3, 4)                       // first reading: the baseline, never a heal
        assertEquals(10, PcHeals.count(3))
        PcHeals.observe(3, 5)                       // a heal, heart off
        assertEquals(10, PcHeals.count(3))
        PcHeals.autoTracking = true
        PcHeals.observe(3, 6)                       // a heal, heart on
        assertEquals(9, PcHeals.count(3))
        PcHeals.observe(3, 0)                       // an unreadable save, not a reset
        PcHeals.observe(3, 6)
        assertEquals(9, PcHeals.count(3), "the zero blip did not count as a heal")
    }

    @Test
    fun `the count survives a restart, per attempt`() {
        PcHeals.add(7, -3)
        PcHeals.load(file)
        assertEquals(7, PcHeals.count(7)); assertEquals(10, PcHeals.count(8))
    }

    @Test
    fun `colours follow the reference`() {
        assertEquals(Pc.Negative, PcHeals.color(0)); assertEquals(Pc.Gold, PcHeals.color(5)); assertEquals(Pc.Text, PcHeals.color(6))
        TrackerOptions.pcHealsCountDownward = false
        assertEquals(Pc.Text, PcHeals.color(4)); assertEquals(Pc.Gold, PcHeals.color(9)); assertEquals(Pc.Negative, PcHeals.color(10))
    }
}
