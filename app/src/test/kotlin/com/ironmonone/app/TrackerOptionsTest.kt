package com.ironmonone.app

import com.ironmonone.tracker.LossCondition
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TrackerOptionsTest {
    private val dir = Files.createTempDirectory("tko").toFile()
    @AfterTest fun cleanup() {
        dir.deleteRecursively()
        TrackerOptions.showBallPicker = true; TrackerOptions.showCategoryIcons = true
        TrackerOptions.healsWhole = false; TrackerOptions.lossCondition = LossCondition.LEAD
        TrackerOptions.landscapeTracker = LandscapeTracker.DOCKED
    }

    @Test
    fun `options round-trip through the file and a missing file leaves the defaults`() {
        val f = java.io.File(dir, "tracker-options.txt")
        TrackerOptions.load(f)
        assertTrue(TrackerOptions.showBallPicker); assertFalse(TrackerOptions.healsWhole)
        TrackerOptions.showBallPicker = false; TrackerOptions.healsWhole = true
        TrackerOptions.lossCondition = LossCondition.ENTIRE_PARTY
        TrackerOptions.save()
        TrackerOptions.landscapeTracker = LandscapeTracker.FLOATING
        TrackerOptions.save()
        assertEquals("showBallPicker=false\nshowCategoryIcons=true\nhealsWhole=true\nshowTeamView=false\nrestorePoints=true\nlossCondition=EntirePartyFaints\nlandscapeTracker=FLOATING\n", f.readText())
        TrackerOptions.showBallPicker = true; TrackerOptions.healsWhole = false; TrackerOptions.lossCondition = LossCondition.LEAD
        TrackerOptions.load(f)
        assertFalse(TrackerOptions.showBallPicker); assertTrue(TrackerOptions.healsWhole)
        assertEquals(LossCondition.ENTIRE_PARTY, TrackerOptions.lossCondition)
        assertEquals(LandscapeTracker.FLOATING, TrackerOptions.landscapeTracker)
    }
}
