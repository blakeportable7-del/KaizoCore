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
        TrackerOptions.determineFriendship = true; TrackerOptions.displayPedometer = false
        com.ironmonone.tracker.TrackerPrefs.determineFriendship = true
        TrackerOptions.showMoveEffectiveness = true; TrackerOptions.countEnemyPp = true
        com.ironmonone.tracker.TrackerPrefs.countEnemyPp = true
        TrackerOptions.showExpBar = false; TrackerOptions.rightJustifiedNumbers = false
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
        TrackerOptions.determineFriendship = false; TrackerOptions.displayPedometer = true
        TrackerOptions.showMoveEffectiveness = false; TrackerOptions.countEnemyPp = false
        TrackerOptions.showExpBar = true; TrackerOptions.rightJustifiedNumbers = true
        TrackerOptions.save()
        assertEquals("showBallPicker=false\nshowCategoryIcons=true\nhealsWhole=true\nshowTeamView=false\nrestorePoints=true\nshowTimer=false\ntourneyTracker=false\nkantoBadgesFirst=false\nshowBothBadgeSets=true\nlogCustomTrainerNames=false\nlogShowUnlearnableGymTms=true\nlogShowPreEvolutions=false\nlossCondition=EntirePartyFaints\nlandscapeTracker=FLOATING\nshowRepel=true\nanimatedSprites=true\nspritesWalk=false\ndetermineFriendship=false\ndisplayPedometer=true\nshowMoveEffectiveness=false\nshowCatchRate=true\ncalculateVariableDamage=true\ncountEnemyPp=false\nshowLastDamage=true\nautoSwapToEnemy=true\nshowNicknames=false\ndisplayGender=false\nshowExpBar=true\ncolorStatNumbers=false\nrightJustifiedNumbers=true\ntrackPcHeals=false\npcHealsCountDownward=true\nhideStatsUntilSummary=false\nshowDataForVanillaGame=true\nopenBookPlayMode=false\nrevealInfoIfRandomized=true\nallowCarouselRotation=true\ncarouselItems=Badges,Notes,RouteInfo,Trainers,LastAttack,BattleDetails,Pedometer,GachaMon\ncarouselSpeed=1\nshowStarterBallInfo=false\n", f.readText())
        TrackerOptions.showBallPicker = true; TrackerOptions.healsWhole = false; TrackerOptions.lossCondition = LossCondition.LEAD
        TrackerOptions.spritesWalk = true
        TrackerOptions.showRepel = false
        TrackerOptions.determineFriendship = true; TrackerOptions.displayPedometer = false
        TrackerOptions.showMoveEffectiveness = true; TrackerOptions.countEnemyPp = true
        TrackerOptions.showExpBar = false; TrackerOptions.rightJustifiedNumbers = false
        TrackerOptions.load(f)
        assertFalse(TrackerOptions.spritesWalk)
        assertTrue(TrackerOptions.showRepel)
        assertFalse(TrackerOptions.determineFriendship); assertTrue(TrackerOptions.displayPedometer)
        // The tracker module sees the loaded value, not just the app.
        assertFalse(com.ironmonone.tracker.TrackerPrefs.determineFriendship)
        assertFalse(TrackerOptions.showMoveEffectiveness); assertFalse(TrackerOptions.countEnemyPp)
        assertTrue(TrackerOptions.showExpBar); assertTrue(TrackerOptions.rightJustifiedNumbers)
        assertFalse(com.ironmonone.tracker.TrackerPrefs.countEnemyPp, "the tracker sees the enemy PP switch")
        assertFalse(TrackerOptions.showBallPicker); assertTrue(TrackerOptions.healsWhole)
        assertEquals(LossCondition.ENTIRE_PARTY, TrackerOptions.lossCondition)
        assertEquals(LandscapeTracker.FLOATING, TrackerOptions.landscapeTracker)
    }
}
