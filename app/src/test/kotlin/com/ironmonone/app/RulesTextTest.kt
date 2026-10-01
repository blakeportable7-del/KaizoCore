package com.ironmonone.app

import com.ironmonone.tracker.LossCondition
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The IronMON rules check's small fixes (2026-09-30): R6, R9, R16, R17, R18. */
class RulesTextTest {
    private fun src(name: String) = File("src/main/kotlin/com/ironmonone/app/$name").readText().replace("\r\n", "\n")

    @Test
    fun `a DS run starts on its settings file's game-over rule, and a pick is kept for that file`() {
        val saved = TrackerOptions.text()
        val f = Files.createTempFile("tracker-options", ".txt").toFile()
        try {
            TrackerOptions.load(f)
            TrackerOptions.startRunWith("HGSS Standard.rnqs")
            assertEquals(LossCondition.ENTIRE_PARTY, TrackerOptions.dsLossCondition, "Standard: the whole party")
            TrackerOptions.startRunWith("DPPt Kaizo Doubles.rnqs")
            assertEquals(LossCondition.EITHER_OF_FIRST_TWO, TrackerOptions.dsLossCondition, "Kaizo Doubles: either of the first two")
            TrackerOptions.startRunWith("B2W2 Kaizo.rnqs")
            assertEquals(LossCondition.LEAD, TrackerOptions.dsLossCondition)
            // The player's pick for a file comes back with that file, and survives a reload.
            TrackerOptions.chooseDsLossCondition(LossCondition.HIGHEST_LEVEL, "B2W2 Kaizo.rnqs")
            TrackerOptions.startRunWith("HGSS Standard.rnqs")
            TrackerOptions.load(f)
            TrackerOptions.startRunWith("B2W2 Kaizo.rnqs")
            assertEquals(LossCondition.HIGHEST_LEVEL, TrackerOptions.dsLossCondition)
            assertTrue("dsLossConditionFor.B2W2 Kaizo.rnqs=HighestLevelFaints" in f.readText())
            // The gear dialog keeps the pick for the run's file, as the Gen 3 rows do.
            assertTrue("TrackerOptions.chooseDsLossCondition(c, runSettingsName)" in src("TrackerGearDialog.kt"))
        } finally {
            f.writeText(saved); TrackerOptions.load(f); f.delete()
        }
    }

    @Test
    fun `Red, Blue and Yellow Survival end on the highest level, so the HM friend can lead a gym`() {
        assertEquals(LossCondition.HIGHEST_LEVEL, LossCondition.forSettingsName("RBY Survival.rnqs"))
        assertEquals(LossCondition.LEAD, LossCondition.forSettingsName("FRLG Survival.rnqs"), "only Red, Blue and Yellow have that rule")
        assertEquals(LossCondition.LEAD, LossCondition.forSettingsName("RBY Kaizo.rnqs"))
        assertEquals(LossCondition.ENTIRE_PARTY, LossCondition.forSettingsName("RBY Standard.rnqs"))
        // The HM friend in the lead slot fainted on purpose; the main Pokemon, the highest level, is fine.
        val party = listOf(com.ironmonone.tracker.LossMon(12, 0), com.ironmonone.tracker.LossMon(40, 88))
        assertFalse(LossCondition.forSettingsName("RBY Survival.rnqs").lostMons(party))
        assertTrue(LossCondition.LEAD.lostMons(party), "which the lead rule called a loss")
    }

    @Test
    fun `a Nuzlocke shows the ball picker only when its own switch is on, off by default`() {
        val saved = TrackerOptions.text()
        val dir = Files.createTempDirectory("nuzballs").toFile()
        val f = File(dir, "tracker-options.txt")
        try {
            TrackerOptions.load(f)
            TrackerOptions.showBallPicker = true
            TrackerOptions.nuzlockeBallPicker = false
            File(dir, "lastrun.txt").writeText("emerald-u\nRSE Kaizo.rnqs\n")
            assertTrue(TrackerOptions.ballPickerShows(nuzlocke = false), "Kaizo IronMON keeps the PC tracker's default")
            assertFalse(TrackerOptions.ballPickerShows(nuzlocke = true), "Blake, 2026-09-30: off in a Nuzlocke")
            TrackerOptions.nuzlockeBallPicker = true
            assertTrue(TrackerOptions.ballPickerShows(nuzlocke = true))
            TrackerOptions.showBallPicker = false
            assertTrue(TrackerOptions.ballPickerShows(nuzlocke = true), "the two switches are separate")
            // The gear shows the switch that applies to the game in Play.
            val gear = src("TrackerGearDialog.kt")
            assertTrue("if (NuzlockeTracking.inPlay()) {\n                GearToggle(\"Show random ball picker\", TrackerOptions.nuzlockeBallPicker)" in gear)
        } finally {
            f.writeText(saved); TrackerOptions.load(f)
        }
    }

    @Test
    fun `no ball picker in a Journey run, whose rules let you pick any starter`() {
        val saved = TrackerOptions.text()
        val dir = Files.createTempDirectory("journey").toFile()
        val f = File(dir, "tracker-options.txt")
        try {
            TrackerOptions.load(f)
            TrackerOptions.showBallPicker = true
            File(dir, "lastrun.txt").writeText("emerald-u\nRSE Kaizo.rnqs\n")
            assertTrue(TrackerOptions.ballPickerShows())
            File(dir, "lastrun.txt").writeText("emerald-u\nRSE Ironmon Journey.rnqs\n")
            File(dir, "lastrun.txt").setLastModified(System.currentTimeMillis() + 5000)
            assertFalse(TrackerOptions.ballPickerShows())
            // Every place that draws the ball asks this, not the switch alone.
            assertFalse("TrackerOptions.showBallPicker }" in src("PlayScreen.kt"))
            assertEquals(2, Regex("TrackerOptions\\.ballPickerShows\\(\\)").findAll(src("TrackerPanel.kt")).count())
        } finally {
            f.writeText(saved); TrackerOptions.load(f)
        }
    }

    @Test
    fun `the favorites line names the mode's limits, and Build your own no longer calls every mode official`() {
        assertTrue("within your mode's legendary and BST limits" in RunCopy.FAVORITES_LINE)
        val build = src("BuildYourGame.kt")
        assertFalse("The official \${m.label} settings" in build)
        assertFalse("one of its official modes" in build)
        assertTrue("settings that come with KaizoCore" in build)
        for (s in listOf(RunCopy.FAVORITES_LINE, RulesetCatalog.modeLine("survival"), RulesetCatalog.modeLine("ironmonjourney")))
            assertFalse('\u2014' in s || '\u2013' in s, s)
    }
}
