package com.ironmonone.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.ironmonone.tracker.LossCondition
import java.io.File

/**
 * The tracker's own options, the subset of the reference's Setup and Gameplay
 * tabs that this panel honours. Each one is read where it takes effect:
 * "Show random ball picker" gates the lab ball picker (TrackerScreen.canShowBallPicker),
 * "Show physical special icons" gates PcCategoryIcon, "Show heals as whole number"
 * changes the Heals line, and "Game is considered over when" is handed to every
 * tracker's game-over read. Kept in prep/tracker-options.txt as key=value lines.
 */
/** 2.2: how the tracker sits in landscape. */
enum class LandscapeTracker(val label: String) { DOCKED("Docked beside the game"), FLOATING("Floating window over the game"), HIDDEN("Hidden, game full screen") }

object TrackerOptions {
    var landscapeTracker by mutableStateOf(LandscapeTracker.DOCKED)
    var showBallPicker by mutableStateOf(true)
    var showCategoryIcons by mutableStateOf(true)
    var healsWhole by mutableStateOf(false)
    /** Options["Show Team View"]: the six-box party strip (TeamViewArea.lua). Off by default, as in the reference. */
    var showTeamView by mutableStateOf(false)
    /** Options["Enable restore points"] (TimeMachineScreen): on by default, as in the reference. */
    var restorePoints by mutableStateOf(true)
    /** settings.timer.ENABLED (DS TimerScreen), off by default. */
    var showTimer by mutableStateOf(false)
    /** settings.tourneyTracker.ENABLED (DS TourneyTracker), off by default. */
    var tourneyTracker by mutableStateOf(false)
    /** badgesAppearance.PRIMARY_BADGE_SET (HGSS): Johto first by default, as in the reference. */
    var kantoBadgesFirst by mutableStateOf(false)
    /** badgesAppearance.SHOW_BOTH_BADGES: both rows once Kanto has begun. Default on, as in the reference. */
    var showBothBadgeSets by mutableStateOf(true)
    /** LogOverlay's "Custom Trainer Names": the log viewer shows the randomizer's names instead of the game's. */
    var logCustomTrainerNames by mutableStateOf(false)
    /** Options["Show unlearnable Gym TMs"], on by default in the reference. */
    var logShowUnlearnableGymTms by mutableStateOf(true)
    /** Options["Show Pre Evolutions"], off by default in the reference. */
    var logShowPreEvolutions by mutableStateOf(false)
    var lossCondition by mutableStateOf(LossCondition.LEAD)

    private var file: File? = null

    fun load(f: File) {
        file = f
        if (!f.exists()) return
        runCatching {
            f.forEachLine { line ->
                val cut = line.indexOf('='); if (cut <= 0) return@forEachLine
                val k = line.substring(0, cut).trim(); val v = line.substring(cut + 1).trim()
                when (k) {
                    "showBallPicker" -> showBallPicker = v == "true"
                    "showCategoryIcons" -> showCategoryIcons = v == "true"
                    "healsWhole" -> healsWhole = v == "true"
                    "showTeamView" -> showTeamView = v == "true"
                    "restorePoints" -> restorePoints = v == "true"
                    "showTimer" -> showTimer = v == "true"
                    "tourneyTracker" -> tourneyTracker = v == "true"
                    "kantoBadgesFirst" -> kantoBadgesFirst = v == "true"
                    "showBothBadgeSets" -> showBothBadgeSets = v == "true"
                    "logCustomTrainerNames" -> logCustomTrainerNames = v == "true"
                    "logShowUnlearnableGymTms" -> logShowUnlearnableGymTms = v == "true"
                    "logShowPreEvolutions" -> logShowPreEvolutions = v == "true"
                    "lossCondition" -> lossCondition = LossCondition.byKey(v)
                    "landscapeTracker" -> landscapeTracker = LandscapeTracker.entries.firstOrNull { it.name == v } ?: LandscapeTracker.DOCKED
                }
            }
        }
    }

    fun save() {
        val f = file ?: return
        runCatching {
            f.parentFile?.mkdirs()
            f.writeText(text())
        }
    }

    fun text(): String = "showBallPicker=$showBallPicker\nshowCategoryIcons=$showCategoryIcons\nhealsWhole=$healsWhole\nshowTeamView=$showTeamView\nrestorePoints=$restorePoints\nshowTimer=$showTimer\ntourneyTracker=$tourneyTracker\nkantoBadgesFirst=$kantoBadgesFirst\nshowBothBadgeSets=$showBothBadgeSets\nlogCustomTrainerNames=$logCustomTrainerNames\nlogShowUnlearnableGymTms=$logShowUnlearnableGymTms\nlogShowPreEvolutions=$logShowPreEvolutions\nlossCondition=${lossCondition.key}\nlandscapeTracker=${landscapeTracker.name}\n"
}
