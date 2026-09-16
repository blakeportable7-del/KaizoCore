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
        TrackerOptions.animatedSprites = true; TrackerOptions.spritesWalk = true
        TrackerOptions.showRepel = false
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
        TrackerOptions.spritesWalk = false
        TrackerOptions.showRepel = true
        TrackerOptions.save()
        assertEquals("showBallPicker=false\nshowCategoryIcons=true\nhealsWhole=true\nshowTeamView=false\nrestorePoints=true\nshowTimer=false\ntourneyTracker=false\nkantoBadgesFirst=false\nshowBothBadgeSets=true\nlogCustomTrainerNames=false\nlogShowUnlearnableGymTms=true\nlogShowPreEvolutions=false\nlossCondition=EntirePartyFaints\nlandscapeTracker=FLOATING\nshowRepel=true\nanimatedSprites=true\nspritesWalk=false\n", f.readText())
        TrackerOptions.showBallPicker = true; TrackerOptions.healsWhole = false; TrackerOptions.lossCondition = LossCondition.LEAD
        TrackerOptions.spritesWalk = true
        TrackerOptions.showRepel = false
        TrackerOptions.load(f)
        assertFalse(TrackerOptions.spritesWalk)
        assertTrue(TrackerOptions.showRepel)
        assertFalse(TrackerOptions.showBallPicker); assertTrue(TrackerOptions.healsWhole)
        assertEquals(LossCondition.ENTIRE_PARTY, TrackerOptions.lossCondition)
        assertEquals(LandscapeTracker.FLOATING, TrackerOptions.landscapeTracker)
    }
}
