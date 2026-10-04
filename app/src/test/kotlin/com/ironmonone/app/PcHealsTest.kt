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
    fun `a new run clears what another game left under its attempt number`() {
        PcHeals.arm(5, PcHeals.Limit.REVIVAL)
        PcHeals.add(5, -2)
        assertEquals(3, PcHeals.count(5))
        PcHeals.forgetAttempt(5)                        // Emerald's attempt 5 after FireRed's
        PcHeals.load(file)
        assertEquals(10, PcHeals.count(5), "the default again, after a reload too")
        TrackerOptions.trackPcHeals = false
        PcHeals.arm(5, PcHeals.Limit.SURVIVAL)
        assertEquals(true, TrackerOptions.trackPcHeals, "the new run arms")
        assertEquals(10, PcHeals.count(5))
    }

    @Test
    fun `a run armed past the 8th badge gets no second bonus`() {
        PcHeals.autoTracking = true
        PcHeals.arm(6, PcHeals.Limit.SURVIVAL, badges = 8)
        PcHeals.observeBadges(6, 8, PcHeals.Limit.SURVIVAL)
        assertEquals(10, PcHeals.count(6))
    }

    @Test
    fun `Gold, Silver and Crystal Survival add the rules' Kanto heals once the League is beaten, once`() {
        // The number is the rules' own, on both Johto families (rulesets/<family>/survival.md, "10 Heal Limit").
        for (family in listOf("GSC", "HGSS")) {
            val rules = java.io.File("src/main/assets/rulesets/$family/survival.md").readText()
            assertEquals(PcHeals.KANTO_HEALS, PcHeals.kantoHealsIn(rules), family)
        }
        assertEquals(null, PcHeals.kantoHealsIn("10 Heal Limit: Once you earn your 8th badge you earn a bonus 11th heal."))
        PcHeals.arm(12, PcHeals.Limit.SURVIVAL)
        PcHeals.observeLeague(12, false, PcHeals.Limit.SURVIVAL)
        assertEquals(10, PcHeals.count(12), "the League not beaten yet")
        PcHeals.observeLeague(12, true, PcHeals.Limit.SURVIVAL)
        assertEquals(17, PcHeals.count(12))
        PcHeals.observeLeague(12, true, PcHeals.Limit.SURVIVAL)
        assertEquals(17, PcHeals.count(12), "once")
        PcHeals.load(file)
        PcHeals.observeLeague(12, true, PcHeals.Limit.SURVIVAL)
        assertEquals(17, PcHeals.count(12), "once, after a restart too")
        // Counting up, they come off the count.
        PcHeals.arm(13, PcHeals.Limit.SURVIVAL)
        TrackerOptions.pcHealsCountDownward = false     // the player turned the counter round after it armed
        PcHeals.add(13, 9 - PcHeals.count(13))
        PcHeals.observeLeague(13, true, PcHeals.Limit.SURVIVAL)
        assertEquals(2, PcHeals.count(13))
        TrackerOptions.pcHealsCountDownward = true
        // Survival Revival grants none; a run first seen after the League had them by hand already.
        PcHeals.arm(14, PcHeals.Limit.REVIVAL)
        PcHeals.observeLeague(14, true, PcHeals.Limit.REVIVAL)
        assertEquals(5, PcHeals.count(14))
        PcHeals.arm(15, PcHeals.Limit.SURVIVAL, badges = 9, leagueBeaten = true)
        PcHeals.observeLeague(15, true, PcHeals.Limit.SURVIVAL)
        assertEquals(10, PcHeals.count(15))
    }

    @Test
    fun `colours follow the reference`() {
        assertEquals(Pc.Negative, PcHeals.color(0)); assertEquals(Pc.Gold, PcHeals.color(5)); assertEquals(Pc.Text, PcHeals.color(6))
        TrackerOptions.pcHealsCountDownward = false
        assertEquals(Pc.Text, PcHeals.color(4)); assertEquals(Pc.Gold, PcHeals.color(9)); assertEquals(Pc.Negative, PcHeals.color(10))
    }

    @Test
    fun `a Survival settings file asks for its limit`() {
        assertEquals(PcHeals.Limit.SURVIVAL, PcHeals.limitFor("FRLG Survival.rnqs"))
        assertEquals(PcHeals.Limit.REVIVAL, PcHeals.limitFor("FRLG Survival Revival.rnqs"))
        assertEquals(null, PcHeals.limitFor("FRLG Kaizo.rnqs"))
        assertEquals(null, PcHeals.limitFor(null))
        java.io.File(dir, "lastrun.txt").writeText("firered-u-v10\nFRLG Survival.rnqs")
        assertEquals(PcHeals.Limit.SURVIVAL, PcHeals.limitForLastRun(), "the run in play, beside the counter's file")
    }

    @Test
    fun `a Survival run switches the counter on at its limit, once, and the player keeps control`() {
        TrackerOptions.trackPcHeals = false; TrackerOptions.pcHealsCountDownward = false
        PcHeals.arm(7, PcHeals.Limit.REVIVAL)
        kotlin.test.assertTrue(TrackerOptions.trackPcHeals); kotlin.test.assertTrue(TrackerOptions.pcHealsCountDownward)
        assertEquals(5, PcHeals.count(7))
        // The player turns it off: arming again for the same attempt changes nothing.
        TrackerOptions.trackPcHeals = false
        PcHeals.arm(7, PcHeals.Limit.REVIVAL)
        kotlin.test.assertFalse(TrackerOptions.trackPcHeals)
        // Saved with the counts: a restart does not arm the attempt again.
        TrackerOptions.trackPcHeals = true
        PcHeals.load(file)
        TrackerOptions.trackPcHeals = false
        PcHeals.arm(7, PcHeals.Limit.REVIVAL)
        kotlin.test.assertFalse(TrackerOptions.trackPcHeals)
        // Not a Survival run: nothing.
        PcHeals.arm(8, null)
        assertEquals(10, PcHeals.count(8).let { if (TrackerOptions.pcHealsCountDownward) it else 10 })
    }

    @Test
    fun `the 8th badge adds the bonus heal once, heart or no heart`() {
        PcHeals.observeBadges(9, 8, PcHeals.Limit.SURVIVAL)
        assertEquals(10, PcHeals.count(9), "not armed yet: nothing")
        PcHeals.arm(9, PcHeals.Limit.SURVIVAL)
        PcHeals.observeBadges(9, 7, PcHeals.Limit.SURVIVAL)
        assertEquals(10, PcHeals.count(9))
        PcHeals.observeBadges(9, 8, PcHeals.Limit.SURVIVAL)
        assertEquals(11, PcHeals.count(9))
        PcHeals.observeBadges(9, 8, PcHeals.Limit.SURVIVAL)
        assertEquals(11, PcHeals.count(9), "once")
        PcHeals.observeBadges(10, 8, null)
        assertEquals(10, PcHeals.count(10), "not a Survival run")
        PcHeals.arm(11, PcHeals.Limit.SURVIVAL)
        TrackerOptions.trackPcHeals = false
        PcHeals.observeBadges(11, 8, PcHeals.Limit.SURVIVAL)
        assertEquals(10, PcHeals.count(11), "a counter the player switched off is left alone")
    }

    /**
     * rc32 audit P2 #43: the counter's sheet offers automatic counting only where the game keeps a heal statistic: a
     * Game Boy game has none, so its heals are entered by hand and the switch is left out.
     */
    @Test
    fun `only a game with a heal statistic is offered automatic counting`() {
        val lastRun = java.io.File(dir, "lastrun.txt")
        lastRun.writeText("red-u\nGB Survival.rnqs\n")
        assertEquals(false, PcHeals.lastRunCountsItself(), "Red")
        lastRun.writeText("crystal-u\nGSC Survival.rnqs\n")
        assertEquals(false, PcHeals.lastRunCountsItself(), "Crystal")
        lastRun.writeText("emerald-u\nRSE Survival.rnqs\n")
        assertEquals(true, PcHeals.lastRunCountsItself(), "Emerald")
        lastRun.delete()
        assertEquals(true, PcHeals.lastRunCountsItself(), "no run on record: the switch is offered, as before")
    }
}
