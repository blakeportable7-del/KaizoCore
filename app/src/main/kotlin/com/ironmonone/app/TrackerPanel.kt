package com.ironmonone.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ironmonone.app.gen3.Gen3
import com.ironmonone.tracker.EnemyInfo
import com.ironmonone.tracker.Gen3Types
import com.ironmonone.tracker.MoveRow
import com.ironmonone.tracker.TrackedMon
import com.ironmonone.tracker.TrackerState

/**
 * The IronMON tracker, recreated to match the PC tracker's own interface rather
 * than restyled into this app's Gen 3 chrome: black ground, hairline boxes, a
 * two-column head with the sprite and type chips on the left and the stat block
 * ruled off on the right, then the Moves bar with PP / Pow / Acc.
 *
 * The enemy's stat column is the reference's own STAT_STAGE buttons: an 8x8
 * marking box per stat, cycling blank / + / -- / =, at the reference's
 * offsets. It used to be six tinted note cells instead - the same information,
 * but not the same thing to look at.
 *
 * One substitution, forced rather than chosen: Constants.Font names
 * "Franklin Gothic Medium", which Android does not have, so the panel uses
 * sans-serif-condensed. Everything else here is taken from Constants.lua and
 * TrackerScreen.lua rather than designed.
 */

/**
 * The PC tracker's own default theme, taken from its Theme.lua rather than
 * eyeballed. That file documents the default as a theme code string:
 *
 *   FFFFFF FFFFFF 00FF00 FF0000 FFFF00 FFFFFF AAAAAA 222222 AAAAAA 222222 000000
 *   [text] [l.box text] [positive] [negative] [intermediate] [header]
 *   [u.border] [u.fill] [l.border] [l.fill] [main background]
 *
 * So the boxes are dark grey on a black page, not black on black, and the gold
 * on held item and ability is "Intermediate text" = pure yellow.
 */
/**
 * The GBA tracker panel. Layout and colours live in PcTracker.kt so the DS panel
 * renders identically instead of drifting into a second design.
 */

@Composable
private fun PartyCard(
    onMoveHistory: ((Int, String, Int) -> Unit)? = null,
    onTypeDefenses: ((String, Int, Int) -> Unit)? = null,
    p: TrackedMon,
    spriteFor: (Int) -> androidx.compose.ui.graphics.ImageBitmap?,
    healPercent: Int = -1,
    healCount: Int = 0,
    onMoveInfo: ((PcMove) -> Unit)? = null,
    onAbilityInfo: ((String) -> Unit)? = null,
    onNameInfo: (() -> Unit)? = null,
    moveCtx: MoveContext? = null,
) {
    val m = p.mon
    PcCard {
        PcHeadBlock(
            // "Show nicknames": the nickname in place of the species when it has one
            // that differs from the species name (DataHelper.lua:159).
            name = (if (TrackerOptions.showNicknames && m.nickname.isNotBlank() && !m.nickname.equals(p.speciesName, ignoreCase = true)) m.nickname
                else p.speciesName) + (if (m.shiny) " *" else ""),
            status = if (m.curHp <= 0) "FNT" else p.statusCondition,
            level = m.level, curHp = m.curHp, maxHp = m.maxHp,
            typeChips = listOfNotNull(
                p.base?.type1?.let { Gen3Types.name(it) to pcTypeColor(it) },
                p.base?.type2?.takeIf { it != p.base?.type1 }
                    ?.let { Gen3Types.name(it) to pcTypeColor(it) },
            ),
            onTypesTap = p.base?.let { b -> onTypeDefenses?.let { cb -> { cb(p.speciesName, b.type1, b.type2) } } },
            itemLine = p.itemName.takeIf { it != "-" } ?: "",
            abilityLine = p.abilityName,
            onAbilityTap = onAbilityInfo?.let { cb -> { cb(p.abilityName) } },
            onNameTap = onNameInfo,
            sprite = spriteFor(m.species),
            iconSpecies = m.species,
            evo = p.evo,
            gender = if (TrackerOptions.displayGender) com.ironmonone.tracker.Gender3.of(p.base?.genderRatio ?: 255, m.pid) else null,
            expFraction = if (TrackerOptions.showExpBar && p.expTotal > 0) p.expNow.toFloat() / p.expTotal else null,
            // Only the lead carries the Heals strip: the number is a share of
            // the lead's max HP, so repeating it under every party member would
            // print the same percentage against six different Pokemon.
            belowHead = if (healPercent >= 0) {
                { PcHealsBlock(healPercent, healCount, wholeHp = healPercent * p.mon.maxHp / 100) }
            } else null,
        ) {
            PcStatRow("HP", "${m.maxHp}", p.statStages["HP"], nature = m.nature, rightJustify = TrackerOptions.rightJustifiedNumbers, colorNumber = TrackerOptions.colorStatNumbers)
            PcStatRow("ATK", "${m.atk}", p.statStages["ATK"], nature = m.nature, rightJustify = TrackerOptions.rightJustifiedNumbers, colorNumber = TrackerOptions.colorStatNumbers)
            PcStatRow("DEF", "${m.def}", p.statStages["DEF"], nature = m.nature, rightJustify = TrackerOptions.rightJustifiedNumbers, colorNumber = TrackerOptions.colorStatNumbers)
            if (p.base?.singleSpecial == true) {
                // Gen 1: one Special stat. The Gen 1 reference tracker lists it as SPA, once.
                PcStatRow("SPA", "${m.spAtk}", p.statStages["SPA"], nature = m.nature, rightJustify = TrackerOptions.rightJustifiedNumbers, colorNumber = TrackerOptions.colorStatNumbers)
            } else {
                PcStatRow("SPA", "${m.spAtk}", p.statStages["SPA"], nature = m.nature, rightJustify = TrackerOptions.rightJustifiedNumbers, colorNumber = TrackerOptions.colorStatNumbers)
                PcStatRow("SPD", "${m.spDef}", p.statStages["SPD"], nature = m.nature, rightJustify = TrackerOptions.rightJustifiedNumbers, colorNumber = TrackerOptions.colorStatNumbers)
            }
            PcStatRow("SPE", "${m.spe}", p.statStages["SPE"], nature = m.nature, rightJustify = TrackerOptions.rightJustifiedNumbers, colorNumber = TrackerOptions.colorStatNumbers)
            PcStatRow("BST", p.base?.bst?.toString() ?: "?", rightJustify = TrackerOptions.rightJustifiedNumbers)
        }
        // "Moves 3/11 (17)" - learned so far / total this species learns, and
        // the level the next one arrives at, exactly as the PC tracker shows it.
        PcMovesSection(
            p.moveRows.map { it.toPcMove(moveCtx) },
            referenceColumns = true, rightJustify = TrackerOptions.rightJustifiedNumbers,
            header = if (p.movesTotal > 0) "Moves ${p.movesLearned}/${p.movesTotal}" else "Moves",
            nextLevel = p.nextMoveLevel.takeIf { p.movesTotal > 0 },
            nextHot = p.nextMoveLevel?.let { m.level + 1 >= it } == true,
            onMoveTap = onMoveInfo,
            onHeaderTap = onMoveHistory?.let { cb -> { cb(m.species, p.speciesName, m.level) } },
        )
    }
}

@Composable
private fun EnemyCard(
    onMoveHistory: ((Int, String, Int) -> Unit)? = null,
    onTypeDefenses: ((String, Int, Int) -> Unit)? = null,
    e: EnemyInfo,
    revealedAbility: String?,
    spriteFor: (Int) -> androidx.compose.ui.graphics.ImageBitmap?,
    marks: IntArray,
    onCycleMark: (Int) -> Unit,
    movesSeenRunWide: List<StatMarks.SeenMove> = emptyList(),
    moveRowFor: (Int) -> MoveRow? = { null },
    lastSeenLevel: Int? = null,
    isWild: Boolean = false,
    encounters: Int = 0,
    routeName: String? = null,
    team: List<Boolean> = emptyList(),
    onMoveInfo: ((PcMove) -> Unit)? = null,
    /** The species' learnset levels from the ROM, for the move count. */
    moveLevels: List<Int> = emptyList(),
    moveCtx: MoveContext? = null,
    catchText: String? = null,
    onCatchTap: (() -> Unit)? = null,
) {
    PcCard {
        PcHeadBlock(
            name = e.speciesName,
            status = if (e.curHp <= 0) "FNT" else e.statusCondition,
            level = e.level, curHp = e.curHp, maxHp = e.maxHp,
            encounterLine =
                if (lastSeenLevel != null) "Last seen Lv.$lastSeenLevel"
                else "New encounter",
            onTypesTap = onTypeDefenses?.let { cb -> { cb(e.speciesName, e.type1, e.type2) } },
            typeChips = listOf(Gen3Types.name(e.type1) to pcTypeColor(e.type1))
                + (if (e.type2 != e.type1)
                    listOf(Gen3Types.name(e.type2) to pcTypeColor(e.type2)) else emptyList()),
            // DataHelper.lua:234 puts the two possible abilities on the two
            // lines, the first suffixed " /" - it does not join them with a
            // slash onto one line, which is what made this overflow.
            itemLine = revealedAbility
                ?: e.abilityGuess.substringBefore(" / ")
                    .let { if (" / " in e.abilityGuess) "$it /" else it },
            abilityLine = if (revealedAbility != null) ""
                else e.abilityGuess.substringAfter(" / ", ""),
            sprite = spriteFor(e.species),
            iconSpecies = e.species,
            evo = e.evo,
            gender = if (TrackerOptions.displayGender) com.ironmonone.tracker.Gender3.of(e.base?.genderRatio ?: 255, e.pid) else null,
            // The box under the card: how often this has been seen, and for a
            // trainer the row of pokeballs showing how many they have left.
            belowHead = {
                Column(Modifier.fillMaxWidth().padding(horizontal = 2.rp, vertical = 1.rp)) {
                    PixText(
                        (if (isWild) "Seen (Wild): " else "Seen (Trainer): ") + encounters,
                        PcRef.FONT, Pc.Text,
                    )
                    if (isWild) PixText(routeName ?: "", PcRef.FONT, Pc.Text)
                    else PcTrainerTeam(team)
                }
            },
        ) {
            PcMarkColumn(marks, onCycleMark, singleSpecial = e.base?.singleSpecial == true)
            PcStatRow("BST", e.base?.bst?.toString() ?: "?", rightJustify = TrackerOptions.rightJustifiedNumbers)
            // Live stage chevrons for the enemy, when any stat has moved.
            e.statStages.filterKeys { it != "ACC" && it != "EVA" }
                .filterValues { it != 6 }
                .forEach { (n, st) -> PcStatRow(n, "", st) }
        }
        // The enemy gets the SAME moves table as the player, which is what
        // the reference does - it fills the ordinary moves area from tracked
        // moves rather than printing a sentence. The asterisk is the
        // reference's own marker for "more tracked than will fit"
        // (TrackerScreen.lua:1487).
        // Tracker.getMoves: the WHOLE run's sightings for this species, most recent
        // first. A move used in an earlier battle is drawn from the ROM's table at
        // its base PP; this battle's rows win where they overlap. Before 2026-09-06
        // this only ever drew the current battle, so every encounter started blind.
        val thisBattle = e.moveRows.distinctBy { it.id }
        val seen = (movesSeenRunWide.mapNotNull { sm -> thisBattle.firstOrNull { it.id == sm.id } ?: moveRowFor(sm.id) } +
            thisBattle).distinctBy { it.id }
        val learned = com.ironmonone.tracker.LearnedMoves.of(moveLevels, e.level)
        // Utils.calculateMoveStars: a tracked move it may have forgotten since.
        val starred = com.ironmonone.tracker.MoveStars.of(movesSeenRunWide.map { it.id to it.lastLv }, e.level, moveLevels)
        PcMovesSection(
            rows = seen.take(4).map { r -> r.toPcMove(moveCtx).let { if (r.id in starred) it.copy(name = it.name + "*") else it } },
            referenceColumns = true, rightJustify = TrackerOptions.rightJustifiedNumbers,
            catchText = catchText,
            onCatchTap = onCatchTap,
            nextLevel = learned.next.takeIf { learned.total > 0 },
            // Utils.getMovesLearnedHeader counts for the opponent too, at ITS
            // level: "Moves* 1/5 (9)", the asterisk (no space) once more than
            // four of its moves have been seen. This read "Moves *" with no count.
            header = "Moves" + (if (seen.size > 4) "*" else "") +
                (if (learned.total > 0) " ${learned.learned}/${learned.total}" else ""),
            onHeaderTap = onMoveHistory?.let { cb -> { cb(e.species, e.speciesName, e.level) } },
            onMoveTap = onMoveInfo,
        )
    }
}

@Composable
fun TrackerPanel(
    /** Trainer Info for the opponent, from the TRAINER BATTLE banner. */
    onTrainerInfo: (() -> Unit)? = null,
    /** StatMarkingScoreSheet, from the game-over card. */
    onGradeNotes: (() -> Unit)? = null,
    /** RandomEvosScreen for a species, from Pokemon info; null when there is no table for it. */
    onRandomEvos: ((Int) -> Unit)? = null,
    hasRandomEvos: (Int) -> Boolean = { false },
    /** Move History for a card: (species, name, level). */
    onMoveHistory: ((Int, String, Int) -> Unit)? = null,
    /** Type Defenses for a card: (name, type1, type2) in Gen 3 ids. */
    onTypeDefenses: ((String, Int, Int) -> Unit)? = null,
    state: TrackerState?,
    onFlee: () -> Unit,
    modifier: Modifier = Modifier,
    ballCall: String? = null,
    favoriteLine: String? = null,
    spriteFor: (Int) -> androidx.compose.ui.graphics.ImageBitmap? = { null },
    unsupportedNote: String? = null,
    /** Opens the tracker's gear (the reference's SettingsGear). */
    onGear: (() -> Unit)? = null,
    enemyMarks: IntArray = IntArray(StatMarks.COUNT),
    enemyEncounters: Int = 0,
    /** Level this species was at the previous time it was met, if ever. */
    enemyLastSeenLevel: Int? = null,
    /**
     * Show your lead AND the enemy together instead of swapping between them.
     * True in landscape, where the column is tall enough for both.
     */
    stackBoth: Boolean = false,
    onCycleMark: (Int) -> Unit = {},
    enemyNote: String = "",
    onEditNote: () -> Unit = {},
    onRerollBall: (() -> Unit)? = null,
    routeName: String? = null,
    /** Every move this species has shown across the WHOLE run, not just now, most recent first. */
    movesSeenRunWide: List<StatMarks.SeenMove> = emptyList(),
    /** A row for a move id seen in an earlier battle, from the ROM's move table. */
    moveRowFor: (Int) -> MoveRow? = { null },
    /** The enemy's ability, if a battle trigger has revealed it this run. */
    revealedEnemyAbility: String? = null,
    routeSeen: Int = 0,
    routeTotal: Int = 0,
    routeTrainers: Int = 0,
    routeBosses: Int = 0,
    steps: Int = 0,
    onMoveDescription: ((Int) -> String?)? = null,
    onAbilityDescription: ((String) -> String?)? = null,
    onWeight: ((Int) -> String?)? = null,
    onEvolution: ((Int) -> String?)? = null,
    onEffectiveness: ((Int) -> Map<Double, List<String>>)? = null,
    onMoveLevels: ((Int) -> List<Int>)? = null,
    onSpeciesNote: ((Int) -> String)? = null,
    onRouteAreas: (() -> Map<String, List<Int>>)? = null,
    onRouteSeenSet: (() -> Set<Int>)? = null,
    onSpeciesName: ((Int) -> String)? = null,
    attempt: Int = 0,
    coverage: Map<Double, List<Int>> = emptyMap(),
    /** Tapping a wild battle's catch rate opens Catch Rates, as the reference's header button does. */
    onCatchRates: (() -> Unit)? = null,
) {
    // What the info screen is currently explaining, if anything.
    var info by remember {
        mutableStateOf<Triple<String, String?, String?>?>(null)
    }
    // The Pokemon info screen is its own mode, with its own shape.
    var monInfo by remember { mutableStateOf<TrackedMon?>(null) }
    // The move info screen (InfoScreen.lua:861), plus Blake's matchup line.
    var moveInfo by remember { mutableStateOf<MoveDetail?>(null) }
    var routeInfoOpen by remember { mutableStateOf(false) }
    if (routeInfoOpen) {
        PcRouteInfo(
            routeName = routeName ?: "Here",
            trainers = routeTrainers,
            bosses = routeBosses,
            areas = onRouteAreas?.invoke() ?: emptyMap(),
            seen = onRouteSeenSet?.invoke() ?: emptySet(),
            nameOf = { id -> onSpeciesName?.invoke(id) ?: "#$id" },
        ) { routeInfoOpen = false }
    }
    monInfo?.let { p ->
        PcPokemonInfo(
            name = p.speciesName,
            types = listOfNotNull(
                p.base?.type1?.let { Gen3Types.name(it) to pcTypeColor(it) },
                p.base?.type2?.takeIf { it != p.base?.type1 }
                    ?.let { Gen3Types.name(it) to pcTypeColor(it) },
            ),
            bst = p.base?.bst?.toString() ?: "?",
            weight = onWeight?.invoke(p.mon.species),
            evolution = onEvolution?.invoke(p.mon.species),
            effectiveness = onEffectiveness?.invoke(p.mon.species) ?: emptyMap(),
            moveLevels = onMoveLevels?.invoke(p.mon.species) ?: emptyList(),
            level = p.mon.level,
            note = onSpeciesNote?.invoke(p.mon.species) ?: "",
            // Coverage lives here now, not beside the card: the reference
            // keeps it on its own CoverageCalcScreen.
            coverage = coverage,
            onRandomEvos = if (onRandomEvos != null && hasRandomEvos(p.mon.species)) { { onRandomEvos(p.mon.species) } } else null,
        ) { monInfo = null }
    }
    moveInfo?.let { PcMoveInfoDialog(it) { moveInfo = null } }
    info?.let { (title, sub, body) ->
        PcInfoDialog(title, sub, body) { info = null }
    }

    // Everything below is measured in the reference's own 150-pixel-wide
    // coordinate system (see PcCanvas), so the panel is a scaled copy of the
    // PC tracker rather than a layout that reflows to whatever width the pane
    // happens to be. Reflowing is what wrapped "Golisopod-M" onto three lines.
    PcCanvas(modifier.fillMaxWidth()) {
      Column(Modifier.fillMaxWidth().background(Pc.Page).padding(PcRef.MARGIN.rp)) {
          // The reference's gear sits at the top of the tracker screen; SETUP is its NavigationMenu.ButtonSetup.
          onGear?.let { g ->
              Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                  // Program.ActiveRepel:shouldDisplay - only while one is running, never in
                  // battle or off the map, and the reference puts it in the top right.
                  if (TrackerOptions.showRepel && state != null && !state.inBattle && state.mapId != null && state.repelSteps > 0) {
                      PcRepelBar(state.repelSteps, state.repelDuration)
                      Spacer(Modifier.width(6.dp))
                  }
                  PcSmallButton("SETUP") { g() }
              }
              Spacer(Modifier.height(3.dp))
          }
          // TeamViewArea: the reference draws it under the game; here it heads the panel.
          if (TrackerOptions.showTeamView && state != null && !state.unreadable && state.party.isNotEmpty()) {
              PcTeamView(
                  party = state.party, spriteFor = spriteFor,
                  onMon = { monInfo = it },
                  onTypes = onTypeDefenses?.let { cb -> { p -> p.base?.let { b -> cb(p.speciesName, b.type1, b.type2) } } },
                  onAbility = { p -> info = Triple(p.abilityName, "Ability", onAbilityDescription?.invoke(p.abilityName)) },
              )
              Spacer(Modifier.height(3.dp))
          }
        when {
            unsupportedNote != null -> PcCard {
                PixText(unsupportedNote, 8, Pc.Negative, Modifier.padding(6.dp))
            }

            state == null -> PcCard {
                PixText("Tracker: waiting for the game...", 8, Pc.Dim, Modifier.padding(6.dp))
            }

            // The addresses for this ROM are wrong. Say so, loudly, rather
            // than render a Pokemon that does not exist.
            state.unreadable -> PcCard {
                Column(Modifier.padding(6.dp)) {
                    PixText("TRACKER CANNOT READ THIS ROM", 9, Pc.Negative)
                    Spacer(Modifier.height(4.dp))
                    PixText("The party decoded into impossible values, so the", 8, Pc.Dim)
                    PixText("memory addresses for this build are wrong.", 8, Pc.Dim)
                    Spacer(Modifier.height(4.dp))
                    PixText("Nothing here would be trustworthy, so it is hidden.", 8, Pc.Dim)
                    if (state.diagnostics.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        // Name the build and the addresses, so a screenshot of
                        // this card is enough to identify which ROM and which
                        // resolution went wrong.
                        PixText(state.diagnostics, 7, Pc.Dim)
                    }
                }
            }

            state.partyCount == 0 -> PcCard {
                Column(Modifier.padding(6.dp)) {
                    favoriteLine?.let {
                        PixText(it, 9, Pc.Negative); Spacer(Modifier.height(3.dp))
                    }
                    // Lab only, the way canShowBallPicker() gates it: in the
                    // lab, with no Pokemon. It used to show anywhere the party
                    // was empty.
                    ballCall?.takeIf { state.inLab && TrackerOptions.showBallPicker }?.let {
                        PcBallPicker(
                            when (it) { "LEFT" -> 0; "MIDDLE" -> 1; else -> 2 },
                            onReroll = onRerollBall)
                        Spacer(Modifier.height(3.dp))
                    }
                    state.playerX?.let {
                        Spacer(Modifier.height(2.dp))
                        PixText("pos $it,${state.playerY}", 7, Pc.Dim)
                    }
                }
            }

            // A finished run is the headline: it goes above the team, not
            // under it, so you are not scrolling to find out you lost.
            state.gameOver != null -> {
                PcGameOver(
                    won = state.gameOver == com.ironmonone.tracker.GameOver.WON,
                    attempt = attempt,
                    party = state.party,
                    onGrade = onGradeNotes,
                )
            }

            else -> {
                // ONE Pokemon, which is what the reference shows.
                //
                // Battle.getViewedPokemon (Battle.lua:257) returns a single
                // mon: your active one outside a battle, and inside one
                // whichever side is being viewed. The whole panel is built
                // around that - a 96x52 box, a 44x75 stat column, four move
                // rows - so stacking the entire party as six cards was never
                // the reference's screen, just this one's.
                //
                // In battle it opens on the ENEMY, matching the reference's
                // "Auto swap to enemy" default.
                // Battle.inActiveBattle: the animated icons do not walk in battle.
                androidx.compose.runtime.SideEffect { SpriteMotion.inBattle = state.inBattle }
                var viewingOwn by remember(state.inBattle) {
                    mutableStateOf(!(state.inBattle && TrackerOptions.autoSwapToEnemy))
                }
                if (state.inBattle) {
                    // Fleeing is a WILD-battle action only; a trainer battle
                    // never offers it, so the banner hides RUN entirely.
                    PcBattleBanner(
                        state.isWildBattle, onFlee,
                        viewingOwn = viewingOwn,
                        // No swap control when both are already on screen:
                        // it would toggle a view that is not hidden.
                        onSwapView = if (state.enemy != null && !stackBoth)
                            { { viewingOwn = !viewingOwn } } else null,
                        onTrainerTap = onTrainerInfo,
                    )
                    Spacer(Modifier.height(1.rp))
                }
                val enemy = state.enemy?.takeIf { state.inBattle && (stackBoth || !viewingOwn) }

                // LANDSCAPE shows both cards at once, yours on top: there is
                // vertical room for two, and marking the enemy's stats while
                // your own are still visible is the point. Portrait keeps the
                // reference's swap, which is what fits one column.
                //
                // The enemy card is emitted AFTER your lead. In swap mode
                // exactly one of the two is ever visible, so that order is
                // invisible there - it only sets the stacking order here.
                // Your lead, i.e. the active battler: the tracker keeps slot 0
                // as the viewed own Pokemon and puts its stat stages there.
                state.party.take(if (enemy != null && !stackBoth) 0 else 1).forEach { p ->
                    PartyCard(onMoveHistory = onMoveHistory, onTypeDefenses = onTypeDefenses, p, spriteFor,
                        healPercent = state.healPercent,
                        healCount = state.healCount,
                        onMoveInfo = { mv ->
                            moveInfo = detailOf(mv, onMoveDescription?.invoke(mv.id))
                                // Your own Hidden Power: the info screen gets the type arrows.
                                .copy(hiddenPowerPid = p.mon.pid.takeIf { mv.id == com.ironmonone.tracker.MoveRules.HIDDEN_POWER })
                        },
                        onAbilityInfo = { name ->
                            info = Triple(name, "Ability", onAbilityDescription?.invoke(name))
                        },
                        onNameInfo = { monInfo = p },
                        moveCtx = ownMoveContext(p, state.enemy?.takeIf { state.inBattle }, state.weather, onWeight))
                }
                if (enemy != null) {
                    EnemyCard(onMoveHistory = onMoveHistory, onTypeDefenses = onTypeDefenses, enemy, revealedEnemyAbility, spriteFor,
                        enemyMarks, onCycleMark, movesSeenRunWide, moveRowFor,
                        lastSeenLevel = enemyLastSeenLevel,
                        isWild = state.isWildBattle,
                        encounters = enemyEncounters,
                        routeName = routeName,
                        team = state.enemyTeam,
                        moveLevels = onMoveLevels?.invoke(enemy.species) ?: emptyList(),
                        moveCtx = enemyMoveContext(enemy, state.party.firstOrNull(), state.weather, onWeight),
                        catchText = state.catchPercent?.takeIf { state.isWildBattle && TrackerOptions.showCatchRate }?.let { "~ $it%  to catch" },
                        onCatchTap = onCatchRates,
                        onMoveInfo = { mv ->
                            moveInfo = detailOf(mv, onMoveDescription?.invoke(mv.id))
                        })
                }
                // The PC tracker's fourth area: one rotating strip, not a stack
                // of permanent rows.
                PcCarousel(
                    inBattle = state.inBattle,
                    badges = state.badges,
                    badgeSet = state.badgeSet,
                    note = enemyNote,
                    onEditNote = onEditNote,
                    lastAttack = state.lastAttackMove?.takeIf { TrackerOptions.showLastDamage }?.let { mv ->
                        (if (state.lastAttackTeams) "Total received" else mv) + ": ${state.lastAttackDamage} damage"
                    },
                    lastAttackLethal = state.party.firstOrNull()?.mon?.let { state.lastAttackDamage >= it.curHp } == true,
                    weather = state.weather,
                    encounters = enemyEncounters,
                    routeName = routeName,
                    routeSeen = routeSeen,
                    routeTotal = routeTotal,
                    routeTrainers = routeTrainers,
                    routeBosses = routeBosses,
                    steps = steps,
                    pedometerAllowed = state.mapId != null && state.gameOver == null,
                    onRouteTap = { routeInfoOpen = true },
                )
            }
        }

    }
    }
}


/**
 * A move popup's contents from a row and its description. The chart lines
 * are general facts about a damaging move's type - never about the opponent,
 * which is the player's to work out (Blake, 2026-09-05).
 */
private fun detailOf(mv: PcMove, summary: String?): MoveDetail =
    MoveDetail(
        name = mv.name, typeId = mv.type, typeName = mv.typeName,
        category = mv.category, contact = mv.contact,
        pp = mv.pp, ppMax = mv.ppMax, power = mv.power, acc = mv.acc,
        priority = mv.priority, summary = summary,
        typeChart = if ((mv.power ?: 0) > 0) MoveMatchup.general(mv.type) else null,
        powerText = mv.powerText,
    )
