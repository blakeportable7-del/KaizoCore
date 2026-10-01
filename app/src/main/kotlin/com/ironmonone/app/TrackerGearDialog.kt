package com.ironmonone.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ironmonone.tracker.LossCondition

/**
 * The tracker's gear (the reference's SettingsGear, which opens NavigationMenu).
 * Ported: the Setup and Gameplay options this panel honours, the Notebook
 * (every species marked or noted this run), and Manage Data's clear. Not
 * ported: theme, language, updates, extensions, streaming and quickload
 * screens, which belong to BizHawk or to this app's own settings.
 *
 * Every label here is in sp, at 12 or more, so it follows the phone's font size, and every row wraps and
 * grows instead of clipping (2026-09-30, UX audit P0-15). It was 6 to 8dp text in reference pixels.
 */
@Composable
fun TrackerGearDialog(
    speciesName: (Int) -> String,
    /** Show the "Auto Pokemon Themes" toggle (Gen 3 and Game Boy, and DS with the DS tracker's rules). */
    showAutoThemes: Boolean = false,
    /** The settings file of the run being played: a condition chosen here is remembered for it. */
    runSettingsName: String? = null,
    marks: StatMarks,
    onCleared: () -> Unit,
    onRules: () -> Unit = {},
    onCoverage: () -> Unit = {},
    onStats: (() -> Unit)? = null,
    onTrainers: (() -> Unit)? = null,
    onBattleDetails: (() -> Unit)? = null,
    onCatchRates: (() -> Unit)? = null,
    onNotebook: (() -> Unit)? = null,
    onHeals: (() -> Unit)? = null,
    onTimeMachine: (() -> Unit)? = null,
    onPastRuns: (() -> Unit)? = null,
    onStatistics: (() -> Unit)? = null,
    onEvoData: (() -> Unit)? = null,
    onTrackedPokemon: (() -> Unit)? = null,
    onTourney: (() -> Unit)? = null,
    showTimerToggle: Boolean = false,
    onColorTheme: (() -> Unit)? = null,
    /** HGSS only: the reference's Badges Appearance choices that apply here. */
    showBadgeOptions: Boolean = false,
    /** Red to Crystal: options the Game Boy references have no working version of are not offered. */
    gameBoy: Boolean = false,
    /** Gold, Silver and Crystal: Tracker Extras' "Estimate Pokemon IV Potential" for the lead (IvEstimate). */
    ivPotential: (() -> String)? = null,
    /** A DS game: the DS tracker's own run-over setting instead of the Gen 3 one. */
    ds: Boolean = false,
    onDismiss: () -> Unit,
) {
    var confirmClear by remember { mutableStateOf(false) }
    // ExtrasScreen's result line: empty until the button is pressed, gone when the screen is left.
    var ivText by remember { mutableStateOf("") }
    // The game over lines screen (DeathQuotes) is opened from here so PlayScreen, at the verifier's limit, carries nothing for it.
    var linesOpen by remember { mutableStateOf(false) }
    // The game and the mode in Play, so each row shows only where it does something (GearScope; Blake, 2026-10-01: "the
    // setup menu should be variable depending on the mode, and the game").
    val appFiles = androidx.compose.ui.platform.LocalContext.current.applicationContext.filesDir
    val scope = remember(gameBoy, ds) { GearScope.of(appFiles, gameBoy, ds) }
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.width(320.dp).heightIn(max = 560.dp).background(Pc.Ground).border(1.dp, Pc.Border)
                .verticalScroll(rememberScrollState()).padding(10.dp),
        ) {
            DialogText("TRACKER SETUP", 18, Pc.Gold, heading = true)
            // The rule that ends the run heads the dialog: it was about 35 rows down, after every display option
            // (2026-09-30, UX audit P0-15). On every game, Game Boy included, in a Kaizo IronMON run: a Nuzlocke ends by
            // its own rules and a library game has no run, and a pick made there carried into the next run (2026-10-01).
            if (scope.ironmon) {
                RunOverSection(ds, runSettingsName)
                Spacer(Modifier.height(8.dp))
            }
            // A library game has no run and no Nuzlocke, so no rules of its own to read.
            if (!scope.plain) GearButton("RULES FOR THIS RUN") { onRules() }
            // Coverage calc reads Gen 3 and DS type data; on a Game Boy game it opened nothing (2026-09-30, feature check).
            if (!gameBoy) GearButton("COVERAGE CALC") { onCoverage() }
            // No Game Boy tracker reads these statistics: eight of the ten rows read 0 there (2026-10-01).
            if (!gameBoy) onStats?.let { GearButton("STATS") { it() } }
            onTrainers?.let { GearButton("TRAINERS ON ROUTE") { it() } }
            onBattleDetails?.let { GearButton("BATTLE DETAILS") { it() } }
            onCatchRates?.let { GearButton("CATCH RATES") { it() } }
            onHeals?.let { GearButton("HEALS IN BAG") { it() } }
            onTimeMachine?.let { GearButton("TIME MACHINE") { it() } }
            ivPotential?.let { judge ->
                GearButton("ESTIMATE POK\u00c9MON IV POTENTIAL") { ivText = judge() }
                if (ivText.isNotEmpty()) DialogText(ivText, 13, Pc.Text)
            }
            // Written when a Kaizo IronMON run ends, so they belong to one, as the Tourney tracker's scores do (2026-10-01).
            if (scope.ironmon) onPastRuns?.let { GearButton("PAST RUNS") { it() } }
            if (scope.ironmon) onStatistics?.let { GearButton("STATISTICS") { it() } }
            onEvoData?.let { GearButton("EVO DATA") { it() } }
            onTrackedPokemon?.let { GearButton("TRACKED POKEMON") { it() } }
            if (scope.ironmon) onTourney?.let { GearButton("TOURNEY TRACKER") { it() } }
            onColorTheme?.let { GearButton("EDIT COLOR THEME") { it() } }
            // Shown only on the IronMON game over screen.
            if (scope.ironmon) GearButton(DeathQuotesCopy.TITLE) { linesOpen = true }
            // The extension's own name for itself, and on DS the DS tracker's label (AppearanceOptionsScreen,
            // from its key AUTO_POKEMON_THEMES); turning it off gives the user's colours back at once.
            if (showAutoThemes) GearToggle(if (ds) "Auto Pok\u00e9mon themes" else "Auto Pok\u00e9mon Themes", TrackerOptions.autoPokemonThemes) {
                TrackerOptions.autoPokemonThemes = it; TrackerOptions.save()
                if (!it) AutoTheme.release()
            }
            // Play as your Pokemon draws on Game Boy Advance games only; elsewhere the section was a head and a note saying so.
            if (scope.gen3) {
                Spacer(Modifier.height(8.dp))
                SpriteIsMeGearSection()   // Play as your Pokemon: every mode, the phone's setting (SpriteIsMeUi.kt)
            }

            GearHead("Options")
            // A Nuzlocke has its own switch, off until the player turns it on (Blake, 2026-09-30); every other game the
            // PC tracker's, on (TrackerOptions.ballPickerShows).
            if (gameBoy) {
                // No Game Boy tracker reports the lab, so no ball is ever picked there (2026-10-01).
            } else if (NuzlockeTracking.inPlay()) {
                GearToggle("Show random ball picker", TrackerOptions.nuzlockeBallPicker) { TrackerOptions.nuzlockeBallPicker = it; TrackerOptions.save() }
            } else if (scope.ironmon && TrackerOptions.journeyRun()) {
                // Its Rule #1 lets you choose any of the three starters: a switch here would do nothing (rules check, 2026-10-01).
                DialogText("IronMON Journey lets you choose any starter, so there is no ball picker.", 12, Pc.Dim)
            } else {
                GearToggle("Show random ball picker", TrackerOptions.showBallPicker) { TrackerOptions.showBallPicker = it; TrackerOptions.save() }
            }
            // The rows marked scope.gen3 are read only by the Gen 3 tracker, and !ds or !gameBoy the same for the DS and
            // Game Boy panels: a switch that does nothing is worse than none (2026-09-30, feature check; 2026-10-01).
            if (scope.gen3) GearToggle("Show starter ball info", TrackerOptions.showStarterBallInfo) { TrackerOptions.showStarterBallInfo = it; TrackerOptions.save() }
            GearToggle("Show physical special icons", TrackerOptions.showCategoryIcons) { TrackerOptions.showCategoryIcons = it; TrackerOptions.save() }
            GearToggle("Show heals as whole number", TrackerOptions.healsWhole) { TrackerOptions.healsWhole = it; TrackerOptions.save() }
            // Gen 1 to 3 only, like the four below marked DS: the DS panel reads none of them, and a switch that does
            // nothing is worse than none (2026-09-30, feature check).
            if (!ds) GearToggle("Show team view", TrackerOptions.showTeamView) { TrackerOptions.showTeamView = it; TrackerOptions.save() }
            if (!gameBoy) GearToggle("Display repel usage", TrackerOptions.showRepel) { TrackerOptions.showRepel = it; TrackerOptions.save() }
            if (scope.gen3) GearToggle("Display pedometer", TrackerOptions.displayPedometer) { TrackerOptions.displayPedometer = it; TrackerOptions.save() }
            if (!ds) GearToggle("Determine friendship readiness", TrackerOptions.determineFriendship) { TrackerOptions.determineFriendship = it; TrackerOptions.save() }
            GearToggle("Show move effectiveness", TrackerOptions.showMoveEffectiveness) { TrackerOptions.showMoveEffectiveness = it; TrackerOptions.save() }
            GearToggle("Calculate variable damage", TrackerOptions.calculateVariableDamage) { TrackerOptions.calculateVariableDamage = it; TrackerOptions.save() }
            if (!ds) GearToggle("Count enemy PP usage", TrackerOptions.countEnemyPp) { TrackerOptions.countEnemyPp = it; TrackerOptions.save() }
            if (scope.gen3) GearToggle("Show Pok\u00e9 Ball catch rate", TrackerOptions.showCatchRate) { TrackerOptions.showCatchRate = it; TrackerOptions.save() }
            if (!ds) GearToggle("Show last damage calcs", TrackerOptions.showLastDamage) { TrackerOptions.showLastDamage = it; TrackerOptions.save() }
            // Calc Atk (UTDZac's extension, Gen 3 only): tapping the last attack opens it; by default only in wild battles.
            if (scope.gen3 && TrackerOptions.showLastDamage) GearToggle("Damage calc in wild battles only", TrackerOptions.calcAtkWildOnly) { TrackerOptions.calcAtkWildOnly = it; TrackerOptions.save() }
            // BattleOptionsScreen on DS (labels from the keys): AUTO_SWAP_TO_ENEMY, off there, and
            // ENABLE_ENEMY_LOCKING, whose going off lets a locked opponent go (onToggleClick, lua:27-33).
            if (ds) {
                GearToggle("Auto swap to enemy", TrackerOptions.dsAutoSwapToEnemy) { TrackerOptions.dsAutoSwapToEnemy = it; TrackerOptions.save() }
                GearToggle("Enable enemy locking", TrackerOptions.dsEnemyLocking) {
                    TrackerOptions.dsEnemyLocking = it; TrackerOptions.save()
                    if (!it) dsView.unlock()
                }
            } else GearToggle("Auto swap to enemy", TrackerOptions.autoSwapToEnemy(gameBoy)) { TrackerOptions.chooseAutoSwapToEnemy(it); TrackerOptions.save() }
            if (scope.gen3) GearToggle("Show nicknames", TrackerOptions.showNicknames) { TrackerOptions.showNicknames = it; TrackerOptions.save() }
            if (scope.gen3) GearToggle("Display gender", TrackerOptions.displayGender) { TrackerOptions.displayGender = it; TrackerOptions.save() }
            // AppearanceOptionsScreen's EXPERIENCE_BAR and SHOW_ACCURACY_AND_EVASION on DS (labels from their keys, on).
            if (ds) {
                GearToggle("Experience bar", TrackerOptions.dsExpBar) { TrackerOptions.dsExpBar = it; TrackerOptions.save() }
                GearToggle("Show accuracy and evasion", TrackerOptions.dsAccEva) { TrackerOptions.dsAccEva = it; TrackerOptions.save() }
            } else if (!gameBoy) GearToggle("Show experience points bar", TrackerOptions.showExpBar) { TrackerOptions.showExpBar = it; TrackerOptions.save() }
            if (scope.gen3) GearToggle("Color stat numbers by nature", TrackerOptions.colorStatNumbers) { TrackerOptions.colorStatNumbers = it; TrackerOptions.save() }
            if (!ds) GearToggle("Right justified numbers", TrackerOptions.rightJustifiedNumbers) { TrackerOptions.rightJustifiedNumbers = it; TrackerOptions.save() }
            // The DS tracker's own counter (AppearanceOptionsScreen's SHOW_POKECENTER_HEALS, labelled from its key).
            if (ds) GearToggle("Show Pok\u00e9center heals", TrackerOptions.dsPokecenterHeals) { TrackerOptions.dsPokecenterHeals = it; TrackerOptions.save() }
            else GearToggle("Track PC Heals", TrackerOptions.trackPcHeals) { TrackerOptions.trackPcHeals = it; TrackerOptions.save() }
            // No summary screen is read on a Game Boy game (SummaryChecks.hidesStats), so no switch there.
            // Clean library games have nothing randomized to hide or reveal (2026-10-01): these three act on randomized games.
            if (!gameBoy && !ds && scope.randomized) GearToggle("Hide stats until summary shown", TrackerOptions.hideStatsUntilSummary) { TrackerOptions.hideStatsUntilSummary = it; if (it) SummaryChecks.forgetAll(); TrackerOptions.save() }
            if (scope.gen3 && !scope.natDex) GearToggle("Show data for vanilla game", TrackerOptions.showDataForVanillaGame) { TrackerOptions.showDataForVanillaGame = it; TrackerOptions.save() }
            // Only while a second display is connected: a dual-screen handheld or an external screen.
            val ctx = androidx.compose.ui.platform.LocalContext.current
            val second = remember(ctx) { ctx.getSystemService(android.hardware.display.DisplayManager::class.java)?.let { presentationDisplay(it) } != null }
            if (second) GearToggle("Tracker on the second screen (view only)", TrackerOptions.trackerOnSecondScreen) { TrackerOptions.trackerOnSecondScreen = it; TrackerOptions.save() }
            if (!ds && (gameBoy || scope.randomized)) GearToggle("Reveal info if randomized", TrackerOptions.revealInfoIfRandomized) { TrackerOptions.revealInfoIfRandomized = it; TrackerOptions.save() }
            if (!ds && (gameBoy || scope.randomized)) GearToggle("Open Book Play Mode", TrackerOptions.openBookPlayMode) { TrackerOptions.openBookPlayMode = it; TrackerOptions.save() }
            // Not a PC tracker option: KaizoCore's guide pictures, off until the player turns them on (FrlgPictures, 2026-09-30).
            // FireRed and LeafGreen only (Blake, 2026-10-01: "if you are playing emerald, and the set up menu mentions fire
            // red maps, that's a problem").
            if (scope.frlg) GearToggle("FireRed and LeafGreen dungeon maps (routes and item spots)", TrackerOptions.frlgGuidePictures) { TrackerOptions.frlgGuidePictures = it; TrackerOptions.save() }
            // Not a PC tracker option either: the type chart under a move's info, off until turned on (2026-09-30).
            if (!ds) GearToggle("Type matchups in move info", TrackerOptions.showTypeMatchups) { TrackerOptions.showTypeMatchups = it; TrackerOptions.save() }

            GearHead("Carousel")
            if (!ds) GearToggle("Allow bottom box rotation", TrackerOptions.allowCarouselRotation) { TrackerOptions.allowCarouselRotation = it; TrackerOptions.save() }
            // The speed matters only while the box rotates, and the DS carousel never does (2026-10-01).
            if (!ds && TrackerOptions.allowCarouselRotation) {
                DialogText("Speed:", 14, Pc.Text, Modifier.padding(top = 4.dp))
                // Five choices across the width, each a 48dp target. The chosen one is underlined as well as gold.
                Row(Modifier.fillMaxWidth()) {
                    for (sp in listOf("1/2", "1", "2", "3", "4")) {
                        val on = TrackerOptions.carouselSpeed == sp
                        Box(
                            Modifier.weight(1f).heightIn(min = PcMin.DIALOG_TOUCH_DP.dp)
                                .selectable(selected = on, role = Role.RadioButton) { TrackerOptions.carouselSpeed = sp; TrackerOptions.save() },
                            contentAlignment = Alignment.Center,
                        ) {
                            DialogText("${sp}x", 14, if (on) Pc.Gold else Pc.Text)
                            if (on) Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp).width(24.dp).height(2.dp).background(Pc.Gold))
                        }
                    }
                }
            }
            DialogText("Info to show:", 12, Pc.Dim, Modifier.padding(top = 4.dp))
            for ((key, label) in listOf(
                "Badges" to "Gym badges", "Notes" to "Notes on Pok" + "\u00E9" + "mon", "RouteInfo" to "Wild encounters in area",
                "Trainers" to "Trainers defeated in area", "LastAttack" to "Last attack damage",
                "BattleDetails" to "Additional battle details", "Pedometer" to "Step pedometer",
            ).filter { (key, _) ->
                // What each game's carousel can show: the area, trainer, battle and step items are the Gen 3 tracker's.
                when (key) { "RouteInfo", "Trainers", "BattleDetails", "Pedometer" -> scope.gen3; "LastAttack" -> !ds; else -> true }
            }) GearToggle(label, TrackerOptions.carouselShows(key)) { TrackerOptions.setCarouselItem(key, it); TrackerOptions.save() }
            if (TrackerOptions.trackPcHeals && !ds) GearToggle("PC heals count downward", TrackerOptions.pcHealsCountDownward) { TrackerOptions.pcHealsCountDownward = it; TrackerOptions.save() }
            GearToggle("Animated Pok\u00e9mon (Walking Pals)", TrackerOptions.animatedSprites) { TrackerOptions.animatedSprites = it; TrackerOptions.save() }
            if (TrackerOptions.animatedSprites) GearToggle("Allow sprites to walk", TrackerOptions.spritesWalk) { TrackerOptions.spritesWalk = it; TrackerOptions.save() }
            if (showTimerToggle) GearToggle("Show timer", TrackerOptions.showTimer) { TrackerOptions.showTimer = it; TrackerOptions.save() }
            if (showBadgeOptions) {
                GearToggle("Show both badge sets", TrackerOptions.showBothBadgeSets) { TrackerOptions.showBothBadgeSets = it; TrackerOptions.save() }
                if (TrackerOptions.showBothBadgeSets) GearToggle("Kanto badges first", TrackerOptions.kantoBadgesFirst) { TrackerOptions.kantoBadgesFirst = it; TrackerOptions.save() }
            }

            GearHead("Landscape tracker")
            LandscapeTracker.entries.forEach { m ->
                GearToggle(m.label, TrackerOptions.landscapeTracker == m, radio = true) {
                    if (it) { TrackerOptions.landscapeTracker = m; TrackerOptions.save() }
                }
            }

            GearHead("Notebook")
            onNotebook?.let { GearButton("OPEN NOTEBOOK") { it() }; Spacer(Modifier.height(4.dp)) }
            val noted = remember(marks) { (marks.markedSpecies() + marks.notedSpecies()).sorted() }
            if (noted.isEmpty()) DialogText("Nothing marked or noted this run.", 12, Pc.Dim)
            noted.forEach { sp ->
                val m = marks.of(sp)
                val summary = StatMarks.STAT_NAMES.indices.mapNotNull { i ->
                    when (m.getOrElse(i) { 0 }) { 1 -> "${StatMarks.STAT_NAMES[i]}+"; 2 -> "${StatMarks.STAT_NAMES[i]}--"; 3 -> "${StatMarks.STAT_NAMES[i]}="; else -> null }
                }.joinToString(" ")
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                    DialogText(speciesName(sp).uppercase(), 12, Pc.Text, Modifier.width(104.dp))
                    Column(Modifier.weight(1f)) {
                        if (summary.isNotEmpty()) DialogText(summary, 12, Pc.Gold)
                        val note = marks.noteFor(sp)
                        if (note.isNotEmpty()) DialogText(note, 12, Pc.Dim)
                    }
                }
            }

            // The app's own, not the reference's: NEW RUN takes a run made ahead
            // (NextRunJob). Off stops it and deletes one waiting, freeing its space.
            // A run's NEW RUN takes it; a library game has no new run to get ready (2026-10-01).
            if (scope.isRun) {
                GearHead("New runs")
                GearToggle("Get the next run ready in the background", NextRunJob.ahead) { NextRunJob.setAhead(ctx, it) }
            }

            GearHead("Manage data")
            if (!confirmClear) {
                GearButton("CLEAR TRACKED DATA") { confirmClear = true }
            } else {
                DialogText("Marks, notes, routes, moves and abilities for this run. Sure?", 13, Pc.Negative)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GearButton("YES, CLEAR", Modifier.weight(1f), accent = true) { marks.clear(); confirmClear = false; onCleared() }
                    GearButton("CANCEL", Modifier.weight(1f)) { confirmClear = false }
                }
            }
            Spacer(Modifier.height(12.dp))
            GearButton("CLOSE") { onDismiss() }
        }
    }
    if (linesOpen) DeathQuotesDialog { linesOpen = false }
}

/**
 * "Game is considered over when": the rule that ends the run (2026-09-30, UX audit P0-15). It sat about 35 rows
 * down; it heads Tracker Setup now. The PC trackers' names stay, for this and every option: they are what IronMON
 * players know.
 */
@Composable
private fun RunOverSection(ds: Boolean, runSettingsName: String?) {
    // A change during a run to other than its file's own rule goes on the run's log (RunRuleLog, R12).
    val filesDir = androidx.compose.ui.platform.LocalContext.current.applicationContext.filesDir
    // The PC trackers' own words for it, which IronMON players know (GameOptionsScreen, TrackedInfoScreen.lua:225).
    DialogText(if (ds) "Run is considered over when" else "Game is considered over when", 15, Pc.Gold,
        Modifier.padding(top = 8.dp, bottom = 2.dp), heading = true)
    if (ds) {
        // TrackedInfoScreen.lua:225-244: "Run is considered over when:". It starts from the settings file's rule,
        // as Gen 1 to 3 do, and a pick is kept for that file (TrackerOptions.dsLossCondition, IronMON rules check R6).
        LossCondition.entries.forEach { c ->
            GearToggle(TrackerOptions.dsLossLabel(c), TrackerOptions.dsLossCondition == c, radio = true) {
                if (it) { TrackerOptions.chooseDsLossCondition(c, runSettingsName); RunRuleLog.changed(filesDir, c, runSettingsName, TrackerOptions.dsLossLabel(c)) }
            }
        }
    } else {
        // GameOptionsScreen's "Game is considered over when", kept per settings file. The
        // Game Boy references have no such option and use the lead, which is the default
        // there too; the player still chooses (Blake, 2026-09-29: full control).
        LossCondition.entries.forEach { c ->
            GearToggle(c.label, TrackerOptions.lossCondition == c, radio = true) {
                if (it) { TrackerOptions.chooseLossCondition(c, runSettingsName); RunRuleLog.changed(filesDir, c, runSettingsName) }
            }
        }
    }
}

/** A section head in Tracker Setup: a hairline, then the head in the accent, in sp like every label here. */
@Composable
internal fun GearHead(text: String) {
    Spacer(Modifier.height(8.dp))
    Box(Modifier.fillMaxWidth().height(1.dp).background(Pc.Border))
    DialogText(text, 15, Pc.Gold, Modifier.padding(top = 8.dp, bottom = 2.dp), heading = true)
}

/**
 * A button in Tracker Setup, drawn like the rows around it: the full width (or its share of a row), flat,
 * square-cornered and framed in the tracker's own border, 48dp tall with a gap above and below, the label in the
 * rows' size. The shell's rounded buttons sat flush here at their own widths and ran into each other (Blake,
 * 2026-09-30: "This UI is bad, all the buttons are gross looking and overlapping").
 */
@Composable
internal fun GearButton(
    label: String,
    modifier: Modifier = Modifier.fillMaxWidth(),
    accent: Boolean = false,
    // True shows [label] as written: a Pokemon's name must not go through Shell.label.
    raw: Boolean = false,
    onClick: () -> Unit,
) {
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(2.dp)
    val text = if (raw) label else Shell.label(label)
    Box(
        modifier.padding(vertical = 3.dp).heightIn(min = PcMin.DIALOG_TOUCH_DP.dp)
            .background(Pc.Text.copy(alpha = 0.07f), shape)
            .border(1.dp, if (accent) Pc.Gold else Pc.Border, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        DialogText(text, GEAR_LABEL_SP, if (accent) Pc.Gold else Pc.Text)
    }
}

/** The size of a checkbox or radio row's label in Tracker Setup and the other tracker dialogs, in sp. */
internal const val GEAR_LABEL_SP = 14

/**
 * A reference-style checkbox row: a small box with the label beside it. Radio rows share it, drawn the same.
 * 48dp tall at least, the label in sp so it follows the phone's font size and wraps rather than clips, and the
 * state is exposed, so a screen reader says checked or selected (2026-09-30, UX audit P0-15). Before, it was a
 * single line of 7dp text that a bigger font could not reach.
 */
@Composable
internal fun GearToggle(label: String, on: Boolean, radio: Boolean = false, onChange: (Boolean) -> Unit) {
    val row = Modifier.fillMaxWidth().heightIn(min = PcMin.DIALOG_TOUCH_DP.dp)
    Row(
        if (radio) row.selectable(selected = on, role = Role.RadioButton) { onChange(true) }
        else row.toggleable(value = on, role = Role.Checkbox) { onChange(it) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // A pick-one row is round and a switch square, so a group never reads as boxes to tick (Blake, 2026-09-30:
        // "you can select multiple playas buttons at a time and you can't uncheck always use").
        val shape = if (radio) androidx.compose.foundation.shape.CircleShape else androidx.compose.ui.graphics.RectangleShape
        Box(Modifier.size(16.dp).border(1.dp, Pc.Border, shape), contentAlignment = Alignment.Center) {
            if (on) Box(Modifier.size(8.dp).background(Pc.Gold, shape))
        }
        Spacer(Modifier.width(10.dp))
        DialogText(label, GEAR_LABEL_SP, Pc.Text, Modifier.weight(1f).padding(vertical = 4.dp))
    }
}

/**
 * A PC-tracker glyph button (X, <, >, BACK) inside a 48dp touch area. The glyph
 * is drawn exactly as before; only the tappable box around it grew, since the
 * bare glyphs were ~20x15dp (2026-09-27, audit).
 */
@Composable
internal fun PcTap(
    text: String,
    size: Int,
    color: androidx.compose.ui.graphics.Color,
    spoken: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .clickable(onClickLabel = spoken) { onClick() }
            .semantics { contentDescription = spoken },
        contentAlignment = Alignment.Center,
    ) { PixText(text, size, color) }
}
