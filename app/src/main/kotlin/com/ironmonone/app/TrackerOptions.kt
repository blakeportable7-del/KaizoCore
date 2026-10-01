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
 * tracker's game-over read (the Game Boy references have no such option; the player
 * chooses anyway, Blake 2026-09-29). Kept in prep/tracker-options.txt as key=value lines.
 */
/** 2.2: how the tracker sits in landscape. */
enum class LandscapeTracker(val label: String) { DOCKED("Docked beside the game"), FLOATING("Floating window over the game"), HIDDEN("Hidden, game full screen") }

object TrackerOptions {
    var landscapeTracker by mutableStateOf(LandscapeTracker.DOCKED)
    var showBallPicker by mutableStateOf(true)
    /** "Show random ball picker" in a Nuzlocke: its own switch, off by default (Blake, 2026-09-30). */
    var nuzlockeBallPicker by mutableStateOf(false)
    var showCategoryIcons by mutableStateOf(true)
    var healsWhole by mutableStateOf(false)
    /** Options["Show Team View"]: the six-box party strip (TeamViewArea.lua). Off by default, as in the reference. */
    var showTeamView by mutableStateOf(false)
    /** Auto Pokemon Themes (Fellshadow's Gen 3 extension): the lead's own colours. Off, as enabling it is opt-in there. */
    var autoPokemonThemes by mutableStateOf(false)
    /** Options["Enable restore points"] (TimeMachineScreen): on by default, as in the reference. */
    var restorePoints by mutableStateOf(true)
    /** settings.timer.ENABLED (DS TimerScreen), off by default. */
    var showTimer by mutableStateOf(false)
    /** settings.tourneyTracker.ENABLED (DS TourneyTracker), off by default. */
    var tourneyTracker by mutableStateOf(false)
    /** badgesAppearance.PRIMARY_BADGE_SET (HGSS): Johto first by default, as in the reference. */
    var kantoBadgesFirst by mutableStateOf(false)
    /** badgesAppearance.SHOW_BOTH_BADGES: both HGSS rows from the start. Default on, as in the reference. */
    var showBothBadgeSets by mutableStateOf(true)
    /** LogOverlay's "Custom Trainer Names": the log viewer shows the randomizer's names instead of the game's. */
    var logCustomTrainerNames by mutableStateOf(false)
    /** Options["Show unlearnable Gym TMs"], on by default in the reference. */
    var logShowUnlearnableGymTms by mutableStateOf(true)
    /** Options["Show Pre Evolutions"], off by default in the reference. */
    var logShowPreEvolutions by mutableStateOf(false)
    var lossCondition by mutableStateOf(LossCondition.LEAD)
    /**
     * The reference keeps a Game Over condition per profile, first taken from the settings
     * file's name (QuickloadScreen.SettingsKeywordToGameOverMap). Here, per settings file:
     * what the player chose for it, else the keyword default.
     */
    private val lossBySettings = LinkedHashMap<String, LossCondition>()

    fun lossConditionFor(settingsName: String): LossCondition =
        lossBySettings[settingsName] ?: LossCondition.forSettingsName(modeName(settingsName))

    /** A new run from [settingsName] starts with that file's condition. */
    fun startRunWith(settingsName: String) {
        lossCondition = lossConditionFor(settingsName)
        dsLossCondition = dsLossConditionFor(settingsName)
        save()
    }

    /** The player changed the condition while playing a run from [settingsName]. */
    fun chooseLossCondition(c: LossCondition, settingsName: String?) {
        lossCondition = c
        settingsName?.takeIf { it.isNotBlank() }?.let { lossBySettings[it] = c }
        save()
    }

    /**
     * The DS tracker's own "Run is considered over when:" (settings.trackedInfo.FAINT_DETECTION,
     * TrackedInfoScreen.lua:238-244) is one setting for every DS game, Lead Pokemon faints by default
     * (MiscConstants.lua:91). Here it follows the run's settings file as Gen 1 to 3 do
     * (LossCondition.forSettingsName), and a pick is kept for that file (2026-09-30, IronMON rules check R6):
     * with the lead for every file, a DS Standard or Ultimate run was filed as lost when its lead fainted,
     * where the rules end it when the whole party is down, and Kaizo Doubles never ended on the second slot.
     */
    var dsLossCondition by mutableStateOf(LossCondition.LEAD)
    private val dsLossBySettings = LinkedHashMap<String, LossCondition>()

    fun dsLossConditionFor(settingsName: String): LossCondition =
        dsLossBySettings[settingsName] ?: LossCondition.forSettingsName(modeName(settingsName))

    /** The player changed the DS condition while playing a run from [settingsName]. */
    fun chooseDsLossCondition(c: LossCondition, settingsName: String?) {
        dsLossCondition = c
        settingsName?.takeIf { it.isNotBlank() }?.let { dsLossBySettings[it] = c }
        save()
    }

    /**
     * The DS tracker's SHOW_POKECENTER_HEALS, "Show Pokecenter heals" (AppearanceOptionsScreen.lua:119,
     * off by default, MiscConstants.lua:18): the manual counter beside the heals. Not the Gen 3
     * "Track PC Heals", which counts from the game's statistics and can count either way.
     */
    var dsPokecenterHeals by mutableStateOf(false)
    /**
     * The DS tracker's EXPERIENCE_BAR, "Experience bar", on by default (MiscConstants.lua:14):
     * holding your Pokemon's level shows the bar in its place. The Gen 3 "Show experience
     * points bar" (off there) is a different, always-drawn bar.
     */
    var dsExpBar by mutableStateOf(true)
    /** The DS tracker's SHOW_ACCURACY_AND_EVASION, "Show accuracy and evasion", on by default (MiscConstants.lua:16). */
    var dsAccEva by mutableStateOf(true)
    /**
     * The DS tracker's AUTO_SWAP_TO_ENEMY, "Auto swap to enemy", off by default
     * (MiscConstants.lua:39): the opponent comes into view as each new one's pause
     * ends (DsViewState.onOpponentReady). The Gen 3 option of that name is on there.
     */
    var dsAutoSwapToEnemy by mutableStateOf(false)
    /** The DS tracker's ENABLE_ENEMY_LOCKING, "Enable enemy locking", off by default (MiscConstants.lua:44). */
    var dsEnemyLocking by mutableStateOf(false)
    /**
     * The tracker on a second display when there is one (SecondScreenHost): a dual-screen
     * handheld, or an external screen, view only for now. Off by default until it has been seen
     * on a real second screen; the gear offers the switch only while a second display is there.
     */
    var trackerOnSecondScreen by mutableStateOf(false)

    /**
     * FireRed and LeafGreen's dungeon maps (FrlgPictures), off by default (Blake, 2026-09-30). They are guide
     * pictures with routes, item spots and trainer counts, the same in every game and nothing from the player's own
     * seed, but the PC tracker has no such thing, and the IronMON dev team asked that the app not coach: a player
     * turns them on.
     */
    var frlgGuidePictures by mutableStateOf(false)

    /**
     * "Strong against", "Resisted by" and "No effect on" under a move's info (MoveMatchup.general): the general type
     * chart for the move's type, nothing about the opponent. Blake asked for them on 2026-09-05; the PC tracker's move
     * screen has none, so since 2026-09-30 they sit behind this switch, off by default (Blake: "the switch option").
     */
    var showTypeMatchups by mutableStateOf(false)

    /** TrackedInfoScreen's labels for the three, which differ from GameOptionsScreen's in one letter. */
    fun dsLossLabel(c: LossCondition): String = when (c) {
        LossCondition.LEAD -> "Lead Pok\u00e9mon faints"
        LossCondition.HIGHEST_LEVEL -> "Highest level faints"
        LossCondition.ENTIRE_PARTY -> "Entire party faints"
        // No DS reference has it; offered on every game (Blake, 2026-09-29: full control).
        LossCondition.EITHER_OF_FIRST_TWO -> "Either of your first two faints"
    }
    /** Options["Display repel usage"], off by default in the reference. */
    var showRepel by mutableStateOf(false)
    /**
     * Options["Pokemon icon set"] = Walking Pals (Blake, 2026-09-15: on by default). Every tracker reads it since
     * 2026-10-01, Gen 1 to 9: the Gen 1-3 panel, a Nat. Dex build's and the DS panel ("if we get the animation sprites
     * for all pokemon, we can include the gen 4 and more animations on the trackers").
     */
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
    /** Calc Atk's "Only display for wild encounters" (CalcAtk.lua ExtSettingsData), on by default. */
    var calcAtkWildOnly by mutableStateOf(true)
    /**
     * "Auto swap to enemy": a battle opens on the opponent's card. On by
     * default in the Gen 3 reference (Options.lua:8), off in both Game Boy
     * references (Options.lua:16 in each). Null until the player picks it, so
     * each game gets its own reference's default; a pick holds for every game.
     */
    private val autoSwapChoice = mutableStateOf<Boolean?>(null)
    fun autoSwapToEnemy(gameBoy: Boolean): Boolean = autoSwapChoice.value ?: !gameBoy
    fun chooseAutoSwapToEnemy(on: Boolean) { autoSwapChoice.value = on }
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
    /** "Track PC Heals": the Pokemon Center heal counter in the heals box (PcHeals). Off, as in the reference. */
    var trackPcHeals by mutableStateOf(false)
    /** "PC heals count downward": from 10 to 0, as the reference defaults; off, up from 0. */
    var pcHealsCountDownward by mutableStateOf(true)
    /** "Hide stats until summary shown": off, as in the reference (SummaryChecks). */
    var hideStatsUntilSummary by mutableStateOf(false)
    // The information rules (InfoRules), at the reference's defaults.
    /** "Show data for vanilla game": an unrandomized part of the opponent is shown in full. */
    var showDataForVanillaGame by mutableStateOf(true)
    /** "Open Book Play Mode": the opponent's abilities, stats and moves, randomized or not. */
    var openBookPlayMode by mutableStateOf(false)
    /** "Reveal info if randomized": off, the opponent's randomized move facts show as "?". */
    var revealInfoIfRandomized by mutableStateOf(true)
    /** "Show starter ball info": the Pokemon info screen for the ball being confirmed in the lab. */
    var showStarterBallInfo by mutableStateOf(false)
    // The carousel tab, at the reference's defaults (Options.lua 5-7).
    const val CAROUSEL_DEFAULT = "Badges,Notes,RouteInfo,Trainers,LastAttack,BattleDetails,Pedometer,GachaMon"
    /** "Allow carousel rotation" ("Allow bottom box rotation"). */
    var allowCarouselRotation by mutableStateOf(true)
    /** "CarouselItems": the reference's own keys, comma separated. */
    var carouselItems by mutableStateOf(CAROUSEL_DEFAULT)
    /** "CarouselSpeed": 1/2, 1, 2, 3 or 4. */
    var carouselSpeed by mutableStateOf("1")
    fun carouselShows(key: String) = key in carouselItems.split(",")
    fun setCarouselItem(key: String, on: Boolean) {
        // SetupScreen's saveCarouselSettings: the list in the setup's own order.
        val keys = listOf("Badges", "Notes", "RouteInfo", "Trainers", "LastAttack", "BattleDetails", "Pedometer", "GachaMon")
        carouselItems = keys.filter { if (it == key) on else carouselShows(it) }.joinToString(",")
    }

    /**
     * "Show random ball picker", as the tracker applies it: never in an IronMON Journey run, whose Rule #1 lets the
     * player choose any of the three starters (2026-09-30, IronMON rules check R16). The run's settings file is read
     * from prep/lastrun.txt beside this one, again only when that file changes. A Nuzlocke has its own switch,
     * off by default (Blake, 2026-09-30).
     */
    fun ballPickerShows(nuzlocke: Boolean = NuzlockeTracking.inPlay()): Boolean =
        if (nuzlocke) nuzlockeBallPicker
        else showBallPicker && !journeyRun()

    /** An IronMON Journey run, whose Rule #1 lets the player choose any of the three starters. */
    fun journeyRun(): Boolean = lastRunMode().contains("Journey", ignoreCase = true)

    /**
     * The last run's mode in the words the rules here read (RunModeName): its game's family and the mode its settings
     * file and sidecar hold, read again only when prep/lastrun.txt changes. It read the file's name alone before
     * (2026-10-01, rules check), so an editor-saved file with a plain name lost its mode.
     */
    private var lastRunCache: Pair<Long, String>? = null
    private fun lastRunMode(): String {
        val f = file?.let { File(it.parentFile, "lastrun.txt") } ?: return ""
        val stamp = f.lastModified()
        lastRunCache?.takeIf { it.first == stamp }?.let { return it.second }
        val lines = runCatching { f.readLines() }.getOrDefault(emptyList())
        val name = lines.getOrNull(1).orEmpty()
        return (if (name.isBlank()) "" else RunModeName.ofPrep(f.parentFile, lines.getOrNull(0), name)).also { lastRunCache = stamp to it }
    }

    /** [settingsName]'s mode in the same words, on the last run's game: a pick is kept per file, the default by mode. */
    private fun modeName(settingsName: String): String {
        val prep = file?.parentFile ?: return settingsName
        val kindId = runCatching { File(prep, "lastrun.txt").readLines().getOrNull(0) }.getOrNull()
        return RunModeName.ofPrep(prep, kindId, settingsName)
    }

    private var file: File? = null

    fun load(f: File) {
        file = f
        lossBySettings.clear()   // the file is the whole record
        dsLossBySettings.clear()
        autoSwapChoice.value = null
        if (!f.exists()) return
        runCatching {
            f.forEachLine { line ->
                val cut = line.indexOf('='); if (cut <= 0) return@forEachLine
                // A per-settings line keys on a file name, which may itself hold '='; the value never does.
                if (line.startsWith("lossConditionFor.")) {
                    val eq = line.lastIndexOf('=')
                    lossBySettings[line.substring("lossConditionFor.".length, eq)] = LossCondition.byKey(line.substring(eq + 1).trim())
                    return@forEachLine
                }
                if (line.startsWith("dsLossConditionFor.")) {
                    val eq = line.lastIndexOf('=')
                    dsLossBySettings[line.substring("dsLossConditionFor.".length, eq)] = LossCondition.byKey(line.substring(eq + 1).trim())
                    return@forEachLine
                }
                val k = line.substring(0, cut).trim(); val v = line.substring(cut + 1).trim()
                when (k) {
                    "showBallPicker" -> showBallPicker = v == "true"
                    "nuzlockeBallPicker" -> nuzlockeBallPicker = v == "true"
                    "showCategoryIcons" -> showCategoryIcons = v == "true"
                    "healsWhole" -> healsWhole = v == "true"
                    "showTeamView" -> showTeamView = v == "true"
                    "autoPokemonThemes" -> autoPokemonThemes = v == "true"
                    "restorePoints" -> restorePoints = v == "true"
                    "showTimer" -> showTimer = v == "true"
                    "tourneyTracker" -> tourneyTracker = v == "true"
                    "kantoBadgesFirst" -> kantoBadgesFirst = v == "true"
                    "showBothBadgeSets" -> showBothBadgeSets = v == "true"
                    "logCustomTrainerNames" -> logCustomTrainerNames = v == "true"
                    "logShowUnlearnableGymTms" -> logShowUnlearnableGymTms = v == "true"
                    "logShowPreEvolutions" -> logShowPreEvolutions = v == "true"
                    "lossCondition" -> lossCondition = LossCondition.byKey(v)
                    "dsLossCondition" -> dsLossCondition = LossCondition.byKey(v)
                    "dsPokecenterHeals" -> dsPokecenterHeals = v == "true"
                    "dsExpBar" -> dsExpBar = v == "true"
                    "dsAccEva" -> dsAccEva = v == "true"
                    "dsAutoSwapToEnemy" -> dsAutoSwapToEnemy = v == "true"
                    "dsEnemyLocking" -> dsEnemyLocking = v == "true"
                    "trackerOnSecondScreen" -> trackerOnSecondScreen = v == "true"
                    "frlgGuidePictures" -> frlgGuidePictures = v == "true"
                    "showTypeMatchups" -> showTypeMatchups = v == "true"
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
                    "calcAtkWildOnly" -> calcAtkWildOnly = v == "true"
                    // A pick. Files before it wrote every key, so their "true" was the old
                    // default and only their "false" was a pick.
                    "autoSwapToEnemyChoice" -> autoSwapChoice.value = v == "true"
                    "autoSwapToEnemy" -> if (v == "false" && autoSwapChoice.value == null) autoSwapChoice.value = false
                    "showNicknames" -> showNicknames = v == "true"
                    "displayGender" -> displayGender = v == "true"
                    "showExpBar" -> showExpBar = v == "true"
                    "colorStatNumbers" -> colorStatNumbers = v == "true"
                    "rightJustifiedNumbers" -> rightJustifiedNumbers = v == "true"
                    "trackPcHeals" -> trackPcHeals = v == "true"
                    "pcHealsCountDownward" -> pcHealsCountDownward = v == "true"
                    "hideStatsUntilSummary" -> hideStatsUntilSummary = v == "true"
                    "showDataForVanillaGame" -> showDataForVanillaGame = v == "true"
                    "openBookPlayMode" -> openBookPlayMode = v == "true"
                    "revealInfoIfRandomized" -> revealInfoIfRandomized = v == "true"
                    "showStarterBallInfo" -> showStarterBallInfo = v == "true"
                    "allowCarouselRotation" -> allowCarouselRotation = v == "true"
                    "carouselItems" -> carouselItems = v
                    "carouselSpeed" -> if (v in listOf("1/2", "1", "2", "3", "4")) carouselSpeed = v
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

    fun text(): String = "showBallPicker=$showBallPicker\nshowCategoryIcons=$showCategoryIcons\nhealsWhole=$healsWhole\nshowTeamView=$showTeamView\nautoPokemonThemes=$autoPokemonThemes\nrestorePoints=$restorePoints\nshowTimer=$showTimer\ntourneyTracker=$tourneyTracker\nkantoBadgesFirst=$kantoBadgesFirst\nshowBothBadgeSets=$showBothBadgeSets\nlogCustomTrainerNames=$logCustomTrainerNames\nlogShowUnlearnableGymTms=$logShowUnlearnableGymTms\nlogShowPreEvolutions=$logShowPreEvolutions\nlossCondition=${lossCondition.key}\ndsLossCondition=${dsLossCondition.key}\nlandscapeTracker=${landscapeTracker.name}\nshowRepel=$showRepel\nanimatedSprites=$animatedSprites\nspritesWalk=$spritesWalk\ndetermineFriendship=$determineFriendship\ndisplayPedometer=$displayPedometer\nshowMoveEffectiveness=$showMoveEffectiveness\nshowCatchRate=$showCatchRate\ncalculateVariableDamage=$calculateVariableDamage\ncountEnemyPp=$countEnemyPp\nshowLastDamage=$showLastDamage\ncalcAtkWildOnly=$calcAtkWildOnly\n${autoSwapChoice.value?.let { "autoSwapToEnemyChoice=$it\n" } ?: ""}showNicknames=$showNicknames\ndisplayGender=$displayGender\nshowExpBar=$showExpBar\ncolorStatNumbers=$colorStatNumbers\nrightJustifiedNumbers=$rightJustifiedNumbers\ntrackPcHeals=$trackPcHeals\npcHealsCountDownward=$pcHealsCountDownward\nhideStatsUntilSummary=$hideStatsUntilSummary\nshowDataForVanillaGame=$showDataForVanillaGame\nopenBookPlayMode=$openBookPlayMode\nrevealInfoIfRandomized=$revealInfoIfRandomized\nallowCarouselRotation=$allowCarouselRotation\ncarouselItems=$carouselItems\ncarouselSpeed=$carouselSpeed\nshowStarterBallInfo=$showStarterBallInfo\ndsPokecenterHeals=$dsPokecenterHeals\ndsExpBar=$dsExpBar\ndsAccEva=$dsAccEva\ndsAutoSwapToEnemy=$dsAutoSwapToEnemy\ndsEnemyLocking=$dsEnemyLocking\ntrackerOnSecondScreen=$trackerOnSecondScreen\nfrlgGuidePictures=$frlgGuidePictures\nshowTypeMatchups=$showTypeMatchups\nnuzlockeBallPicker=$nuzlockeBallPicker\n" +
        lossBySettings.entries.joinToString("") { (name, c) -> "lossConditionFor.$name=${c.key}\n" } +
        dsLossBySettings.entries.joinToString("") { (name, c) -> "dsLossConditionFor.$name=${c.key}\n" }
}
