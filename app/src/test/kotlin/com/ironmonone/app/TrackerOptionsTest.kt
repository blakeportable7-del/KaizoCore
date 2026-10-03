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
        TrackerOptions.dsLossCondition = LossCondition.LEAD
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
        DiskWriter.drain()   // written on the writer's thread (rc32 audit P3 #71)
        assertEquals("showBallPicker=false\nshowCategoryIcons=true\nhealsWhole=true\nshowTeamView=false\nautoPokemonThemes=false\nrestorePoints=true\nshowTimer=false\ntourneyTracker=false\nkantoBadgesFirst=false\nshowBothBadgeSets=true\nlogCustomTrainerNames=false\nlogShowUnlearnableGymTms=true\nlogShowPreEvolutions=false\nlossCondition=EntirePartyFaints\ndsLossCondition=LeadPokemonFaints\nlandscapeTracker=FLOATING\nfloatingLocked=false\nshowRepel=true\nanimatedSprites=true\nspritesWalk=false\ndetermineFriendship=false\ndisplayPedometer=true\nshowMoveEffectiveness=false\nshowCatchRate=true\ncalculateVariableDamage=true\ncountEnemyPp=false\nshowLastDamage=true\ncalcAtkWildOnly=true\nshowNicknames=false\ndisplayGender=false\nshowExpBar=true\ncolorStatNumbers=false\nrightJustifiedNumbers=true\ntrackPcHeals=false\npcHealsCountDownward=true\nhideStatsUntilSummary=false\nshowDataForVanillaGame=true\nopenBookPlayMode=false\nrevealInfoIfRandomized=true\nallowCarouselRotation=true\ncarouselItems=Badges,Notes,RouteInfo,Trainers,LastAttack,BattleDetails,Pedometer,GachaMon\ncarouselSpeed=1\nshowStarterBallInfo=false\ndsPokecenterHeals=false\ndsExpBar=true\ndsAccEva=true\ndsAutoSwapToEnemy=false\ndsEnemyLocking=false\ntrackerOnSecondScreen=false\nfrlgGuidePictures=false\nshowTypeMatchups=false\nnuzlockeBallPicker=false\n", f.readText())
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

    /**
     * "Auto swap to enemy": on in the Gen 3 reference, off in both Game Boy
     * references (Options.lua). Until the player picks it, each game gets its
     * own reference's default; a pick holds for every game and survives a reload.
     */
    @Test
    fun `auto swap follows each game's reference until the player picks it`() {
        val f = java.io.File(dir, "tracker-options.txt")
        TrackerOptions.load(f)
        assertTrue(TrackerOptions.autoSwapToEnemy(gameBoy = false)); assertFalse(TrackerOptions.autoSwapToEnemy(gameBoy = true))
        TrackerOptions.chooseAutoSwapToEnemy(true); TrackerOptions.save(); TrackerOptions.load(f)
        assertTrue(TrackerOptions.autoSwapToEnemy(gameBoy = true), "picked on: on for Game Boy games too")
        TrackerOptions.chooseAutoSwapToEnemy(false); TrackerOptions.save(); TrackerOptions.load(f)
        assertFalse(TrackerOptions.autoSwapToEnemy(gameBoy = false), "picked off: off for Gen 3 too")
        // A file from before the pick was kept apart wrote every key: its "true" was the old default, its "false" a pick.
        DiskWriter.drain(); f.writeText("autoSwapToEnemy=true\n"); TrackerOptions.load(f)
        assertTrue(TrackerOptions.autoSwapToEnemy(gameBoy = false)); assertFalse(TrackerOptions.autoSwapToEnemy(gameBoy = true))
        f.writeText("autoSwapToEnemy=false\n"); TrackerOptions.load(f)
        assertFalse(TrackerOptions.autoSwapToEnemy(gameBoy = false)); assertFalse(TrackerOptions.autoSwapToEnemy(gameBoy = true))
        TrackerOptions.load(java.io.File(dir, "none.txt"))   // leave no pick behind for the other tests
    }

    @Test
    fun `each settings file keeps its own game over condition`() {
        val f = java.io.File(dir, "tracker-options.txt")
        TrackerOptions.load(f)
        // First run from a file: the reference's keyword default.
        TrackerOptions.startRunWith("FRLG Ultimate.rnqs")
        assertEquals(LossCondition.ENTIRE_PARTY, TrackerOptions.lossCondition)
        TrackerOptions.startRunWith("FRLG Kaizo.rnqs")
        assertEquals(LossCondition.LEAD, TrackerOptions.lossCondition)
        // Changed while playing a Kaizo run: that file remembers it, and it survives a reload.
        TrackerOptions.chooseLossCondition(LossCondition.HIGHEST_LEVEL, "FRLG Kaizo.rnqs")
        TrackerOptions.load(f)
        assertEquals(LossCondition.HIGHEST_LEVEL, TrackerOptions.lossConditionFor("FRLG Kaizo.rnqs"))
        assertEquals(LossCondition.ENTIRE_PARTY, TrackerOptions.lossConditionFor("FRLG Ultimate.rnqs"))
        TrackerOptions.startRunWith("FRLG Kaizo.rnqs")
        assertEquals(LossCondition.HIGHEST_LEVEL, TrackerOptions.lossCondition)
        // Leave no remembered entries for the other tests' snapshot.
        TrackerOptions.load(java.io.File(dir, "none.txt"))
    }

    @Test
    fun `the DS run-over setting starts from the settings file, as Gen 1 to 3's does, lead with no file`() {
        val f = java.io.File(dir, "tracker-options.txt")
        TrackerOptions.load(f)
        // MiscConstants.lua:91: FAINT_DETECTION = ON_FIRST_SLOT_FAINT, the DS reference's one setting for every game.
        assertEquals(LossCondition.LEAD, TrackerOptions.dsLossCondition)
        // IronMON rules check R6 (2026-09-30): the rules end a Standard run when the whole party is down, on DS too.
        TrackerOptions.startRunWith("HGSS Standard.rnqs")
        assertEquals(LossCondition.ENTIRE_PARTY, TrackerOptions.lossCondition)
        assertEquals(LossCondition.ENTIRE_PARTY, TrackerOptions.dsLossCondition)
        TrackerOptions.dsLossCondition = LossCondition.HIGHEST_LEVEL; TrackerOptions.save()
        TrackerOptions.dsLossCondition = LossCondition.LEAD
        TrackerOptions.load(f)
        assertEquals(LossCondition.HIGHEST_LEVEL, TrackerOptions.dsLossCondition)
        assertEquals("Lead Pok\u00e9mon faints", TrackerOptions.dsLossLabel(LossCondition.LEAD))
        TrackerOptions.load(java.io.File(dir, "none.txt"))
    }

    @Test
    fun `the DS poller hands the DS tracker the DS setting`() {
        // Wiring proof: fails if the DS poller goes back to the Gen 3 condition.
        val src = java.io.File("src/main/kotlin/com/ironmonone/app/PlayScreen.kt").readText()
        assertTrue("t.lossCondition = TrackerOptions.dsLossCondition" in src, "the DS tracker no longer reads its own run-over setting")
    }
}
