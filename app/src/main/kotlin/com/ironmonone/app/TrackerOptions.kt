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
    /** Options["Display repel usage"], off by default in the reference. */
    var showRepel by mutableStateOf(false)
    /** Options["Pokemon icon set"] = Walking Pals, on Gen 1-3 (Blake, 2026-09-15: on by default). */
    var animatedSprites by mutableStateOf(true)
    /** Options["Allow sprites to walk"], on by default in the reference. */
    var spritesWalk by mutableStateOf(true)
    private val determineFriendshipState = mutableStateOf(true)
    /** Options["Determine friendship readiness"]: on, as in the reference. Mirrored to the tracker (TrackerPrefs). */
    var determineFriendship: Boolean
        get() = determineFriendshipState.value
        set(v) { determineFriendshipState.value = v; com.ironmonone.tracker.TrackerPrefs.determineFriendship = v }
    /**
     * Options["Display pedometer"]: off, as in the reference, where the pedometer
     * stays out of the carousel until it is turned on (Program.Pedometer:isInUse).
     */
    var displayPedometer by mutableStateOf(false)
    // Six switches for behaviours on by default in the reference (Options.lua).
    /** "Show move effectiveness": the chevrons and X beside a move's power in battle. */
    var showMoveEffectiveness by mutableStateOf(true)
    /** "Show Poke Ball catch rate": the wild battle's "~ 33%  to catch" header. */
    var showCatchRate by mutableStateOf(true)
    /** "Calculate variable damage": Return, Low Kick, Weather Ball and the rest as numbers. */
    var calculateVariableDamage by mutableStateOf(true)
    private val countEnemyPpState = mutableStateOf(true)
    /** "Count enemy PP usage": the opponent's real remaining PP. Mirrored to the tracker. */
    var countEnemyPp: Boolean
        get() = countEnemyPpState.value
        set(v) { countEnemyPpState.value = v; com.ironmonone.tracker.TrackerPrefs.countEnemyPp = v }
    /** "Show last damage calcs": the carousel's "Wing Attack: 23 damage". */
    var showLastDamage by mutableStateOf(true)
    /** "Auto swap to enemy": a battle opens on the opponent's card. */
    var autoSwapToEnemy by mutableStateOf(true)
    // Off by default in the reference (Options.lua).
    /** "Show nicknames": your Pokemon's nickname in place of its species, when it has one. */
    var showNicknames by mutableStateOf(false)
    /** "Display gender": the male or female symbol after the name. */
    var displayGender by mutableStateOf(false)
    /** "Show experience points bar": a bar under your Pokemon's level. */
    var showExpBar by mutableStateOf(false)
    /** "Color stat numbers by nature": the number takes the nature colour as well as the label. */
    var colorStatNumbers by mutableStateOf(false)
    /** "Right justified numbers": off, the numbers start at their column, as the reference draws them. */
    var rightJustifiedNumbers by mutableStateOf(false)

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
                    "showRepel" -> showRepel = v == "true"
                    "animatedSprites" -> animatedSprites = v == "true"
                    "spritesWalk" -> spritesWalk = v == "true"
                    "determineFriendship" -> determineFriendship = v == "true"
                    "displayPedometer" -> displayPedometer = v == "true"
                    "showMoveEffectiveness" -> showMoveEffectiveness = v == "true"
                    "showCatchRate" -> showCatchRate = v == "true"
                    "calculateVariableDamage" -> calculateVariableDamage = v == "true"
                    "countEnemyPp" -> countEnemyPp = v == "true"
                    "showLastDamage" -> showLastDamage = v == "true"
                    "autoSwapToEnemy" -> autoSwapToEnemy = v == "true"
                    "showNicknames" -> showNicknames = v == "true"
                    "displayGender" -> displayGender = v == "true"
                    "showExpBar" -> showExpBar = v == "true"
                    "colorStatNumbers" -> colorStatNumbers = v == "true"
                    "rightJustifiedNumbers" -> rightJustifiedNumbers = v == "true"
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

    fun text(): String = "showBallPicker=$showBallPicker\nshowCategoryIcons=$showCategoryIcons\nhealsWhole=$healsWhole\nshowTeamView=$showTeamView\nrestorePoints=$restorePoints\nshowTimer=$showTimer\ntourneyTracker=$tourneyTracker\nkantoBadgesFirst=$kantoBadgesFirst\nshowBothBadgeSets=$showBothBadgeSets\nlogCustomTrainerNames=$logCustomTrainerNames\nlogShowUnlearnableGymTms=$logShowUnlearnableGymTms\nlogShowPreEvolutions=$logShowPreEvolutions\nlossCondition=${lossCondition.key}\nlandscapeTracker=${landscapeTracker.name}\nshowRepel=$showRepel\nanimatedSprites=$animatedSprites\nspritesWalk=$spritesWalk\ndetermineFriendship=$determineFriendship\ndisplayPedometer=$displayPedometer\nshowMoveEffectiveness=$showMoveEffectiveness\nshowCatchRate=$showCatchRate\ncalculateVariableDamage=$calculateVariableDamage\ncountEnemyPp=$countEnemyPp\nshowLastDamage=$showLastDamage\nautoSwapToEnemy=$autoSwapToEnemy\nshowNicknames=$showNicknames\ndisplayGender=$displayGender\nshowExpBar=$showExpBar\ncolorStatNumbers=$colorStatNumbers\nrightJustifiedNumbers=$rightJustifiedNumbers\n"
}
