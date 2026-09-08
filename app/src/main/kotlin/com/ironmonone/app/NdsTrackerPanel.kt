package com.ironmonone.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ironmonone.tracker.nds.NdsMoveInfo
import com.ironmonone.tracker.nds.NdsTrackedMon
import com.ironmonone.tracker.nds.NdsTrackerState

/**
 * The Platinum tracker, rendered through the same PC-tracker blocks the GBA
 * panel uses, so the two consoles look identical rather than each growing its
 * own layout. No sprites yet: Gen 4 art lives in compressed archives inside the
 * ROM rather than in a flat table the emulator exposes.
 */

/**
 * Gen 4's real per-move split, read from the ROM's move table rather than
 * derived from the type. That derivation is a Gen 3 rule and is simply WRONG
 * here: Gen 4 is where the split stopped following the type, so a physical
 * Fire move or a special Rock move exists and must not be mislabelled.
 */
private fun categoryOf(category: String): String = when (category.uppercase()) {
    "PHYSICAL" -> "PHY"
    "SPECIAL" -> "SPE"
    "STATUS" -> "STA"
    else -> "?"
}

private fun movesOf(p: NdsTrackedMon): List<PcMove> =
    p.moves.mapIndexed { i, m ->
        PcMove(
            id = m.id,
            name = m.name,
            pp = p.mon.pp.getOrElse(i) { 0 },
            // Base PP raised by this mon's PP Ups, same rule as Gen 3.
            ppMax = m.pp.takeIf { it > 0 }?.let { base ->
                base + (base / 5) * p.mon.ppUps.getOrElse(i) { 0 }
            },
            power = m.power,
            acc = m.accuracy,
            color = pcTypeColorByName(m.type),
            typeName = m.type,
            category = categoryOf(m.category),
        )
    }

/** A move this species used in an EARLIER battle, drawn from the ROM's table at base PP. */
private fun rememberedMove(m: NdsMoveInfo): PcMove = PcMove(
    id = m.id, name = m.name, pp = m.pp, ppMax = null, power = m.power, acc = m.accuracy,
    color = pcTypeColorByName(m.type), typeName = m.type, category = categoryOf(m.category),
)

/**
 * The enemy's move rows: what it has used across the WHOLE run, most recent
 * first, this battle's live rows winning where they overlap. The panel used
 * to draw only this battle, so a second meeting with a species started blind.
 */
private fun enemyMovesOf(e: NdsTrackedMon, runWide: List<StatMarks.SeenMove>, moveInfoFor: (Int) -> NdsMoveInfo?): List<PcMove> {
    val now = movesOf(e)
    val merged = runWide.mapNotNull { sm -> now.firstOrNull { it.id == sm.id } ?: moveInfoFor(sm.id)?.let(::rememberedMove) } + now
    return merged.distinctBy { if (it.id != 0) it.id.toString() else it.name }
}

private fun typeChipsOf(p: NdsTrackedMon): List<Pair<String, androidx.compose.ui.graphics.Color>> {
    val info = p.info ?: return emptyList()
    val chips = mutableListOf<Pair<String, androidx.compose.ui.graphics.Color>>()
    if (info.type1.isNotBlank()) chips += info.type1 to pcTypeColorByName(info.type1)
    if (info.type2.isNotBlank() && info.type2 != info.type1) {
        chips += info.type2 to pcTypeColorByName(info.type2)
    }
    return chips
}

@Composable
private fun NdsPartyCard(
    onMoveHistory: ((Int, String, Int) -> Unit)? = null,
    onTypeDefenses: ((String, String, String) -> Unit)? = null,
    p: NdsTrackedMon,
    healPercent: Int = -1,
    healCount: Int = 0,
) {
    val m = p.mon
    val context = androidx.compose.ui.platform.LocalContext.current
    val sprite = remember(m.species, m.shiny) {
        PcAssets.dsSprite(context, m.species, m.shiny)
    }
    PcCard {
        PcHeadBlock(
            name = p.speciesName + (if (m.shiny) " *" else "") +
                (if (p.statusCondition.isNotEmpty()) "  [" + p.statusCondition + "]" else ""),
            level = m.level, curHp = m.curHp, maxHp = m.maxHp,
            typeChips = typeChipsOf(p),
            onTypesTap = p.info?.let { i -> onTypeDefenses?.let { cb -> { cb(p.speciesName, i.type1, i.type2) } } },
            itemLine = p.itemName.takeIf { it != "-" } ?: "",
            abilityLine = p.abilityName,
            sprite = sprite,
            belowHead = if (healPercent >= 0) {
                { PcHealsBlock(healPercent, healCount, wholeHp = healPercent * m.maxHp / 100) }
            } else null,
        ) {
            PcStatRow("HP", "${m.maxHp}", p.statStages["HP"], nature = m.nature)
            PcStatRow("ATK", "${m.atk}", p.statStages["ATK"], nature = m.nature)
            PcStatRow("DEF", "${m.def}", p.statStages["DEF"], nature = m.nature)
            PcStatRow("SPA", "${m.spAtk}", p.statStages["SPA"], nature = m.nature)
            PcStatRow("SPD", "${m.spDef}", p.statStages["SPD"], nature = m.nature)
            PcStatRow("SPE", "${m.spe}", p.statStages["SPE"], nature = m.nature)
            PcStatRow("BST", p.info?.bst?.toString() ?: "?")
        }
        PcMovesSection(
            movesOf(p),
            header = if (p.movesTotal > 0) {
                // Reference wording is "Moves 4/16 (13)" - no colon
                // (Utils.getMovesLearnedHeader). The GBA panel already
                // matched; this one had drifted.
                "Moves ${p.movesLearned}/${p.movesTotal}" +
                    (p.nextMoveLevel?.let { " ($it)" } ?: "")
            } else "Moves",
            onHeaderTap = onMoveHistory?.let { cb -> { cb(p.mon.species, p.speciesName, p.mon.level) } },
        )
    }
}

@Composable
private fun NdsEnemyCard(
    onMoveHistory: ((Int, String, Int) -> Unit)? = null,
    onTypeDefenses: ((String, String, String) -> Unit)? = null,
    e: NdsTrackedMon,
    revealedAbility: String?,
    marks: IntArray,
    encounters: Int,
    onCycleMark: (Int) -> Unit,
    note: String,
    onEditNote: () -> Unit,
    /** Every move this species has used this run, most recent first (the reference's trackMove). */
    movesSeenRunWide: List<StatMarks.SeenMove> = emptyList(),
    moveInfoFor: (Int) -> NdsMoveInfo? = { null },
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val sprite = remember(e.mon.species) {
        PcAssets.dsSprite(context, e.mon.species, false)
    }
    PcCard {
        PcHeadBlock(
            name = e.speciesName +
                (if (e.statusCondition.isNotEmpty()) "  [" + e.statusCondition + "]" else ""),
            level = e.mon.level,
            curHp = e.mon.curHp, maxHp = e.mon.maxHp,
            typeChips = typeChipsOf(e),
            onTypesTap = e.info?.let { i -> onTypeDefenses?.let { cb -> { cb(e.speciesName, i.type1, i.type2) } } },
            itemLine = "",
            // Revealed-on-activation, like the PC tracker: until a battle
            // trigger shows the ability, the line stays unrevealed.
            abilityLine = revealedAbility ?: "---",
            sprite = sprite,
        ) {
            // Enemy stats are unknown: this column is the notebook.
            PcMarkColumn(marks, onCycleMark)
            PcStatRow("BST", e.info?.bst?.toString() ?: "?")
            // Live stage chevrons, the reference's in-battle markers.
            e.statStages.filterKeys { it != "ACC" && it != "EVA" }
                .filterValues { it != 6 }
                .forEach { (n, st) -> PcStatRow(n, "", st) }
        }
        androidx.compose.foundation.layout.Box(
            Modifier.fillMaxWidth().height(1.dp).background(Pc.Border))
        Column(Modifier.padding(4.dp)) {
            if (encounters > 1) {
                PixText("seen $encounters times", 7, Pc.Dim)
                Spacer(Modifier.height(2.dp))
            }

        }
        androidx.compose.foundation.layout.Box(
            Modifier.fillMaxWidth().height(1.dp).background(Pc.Border))
        // The reference's tracked moves for this opponent: only what it has used, this run.
        PcMovesSection(enemyMovesOf(e, movesSeenRunWide, moveInfoFor), header = "Moves", onHeaderTap = onMoveHistory?.let { cb -> { cb(e.mon.species, e.speciesName, e.mon.level) } })
        PcNoteRow(note, onEditNote)
    }
}

/**
 * The DS tracker's closing lines, from PlaythroughConstants.RUN_OVER_MESSAGES.
 *
 * The DS tracker does something the GBA one does not: it works out HOW the run
 * ended and picks the line from that. Losing to a Shedinja gets its own set;
 * so does losing to something a hundred BST below you. The "exclusive" causes
 * draw only from their own list, while a standard loss draws from the twelve
 * defaults - which is why these are separate lists rather than one pool.
 */
private val NDS_RUN_OVER_MESSAGES: Map<com.ironmonone.tracker.nds.NdsRunOver, List<String>> =
    mapOf(
        com.ironmonone.tracker.nds.NdsRunOver.WON to listOf(
            "Congratulations!",
            "My god... you actually did it...",
            "I'm speechless.",
            "The end of a long, arduous journey...",
            "On this day, the planets aligned...",
        ),
        com.ironmonone.tracker.nds.NdsRunOver.SHEDINJA to listOf(
            "Never feels good to lose to that.",
            "There are over 20 fire moves in the game, and you didn't roll a single one.",
            "It was bound to happen at some point.",
            "The one Pokemon you didn't want to see...",
        ),
        com.ironmonone.tracker.nds.NdsRunOver.IMPOSTER to listOf(
            "Sometimes, you really just can't face yourself.",
            "Looking in the mirror really is that painful.",
            "Dark Link was a lot easier than this...",
            "Does this mean we can ban it now?",
        ),
        com.ironmonone.tracker.nds.NdsRunOver.ENEMY_LOWER_BST to listOf(
            "Sometimes, the weaker triumph.",
            "A surprising outcome.",
            "Miracles really can happen.",
            "I don't think anyone saw that coming.",
            "Huh?",
            "Surely that Pokemon had Huge Power.",
        ),
        com.ironmonone.tracker.nds.NdsRunOver.STANDARD to listOf(
            "There's always next time...",
            "Some things were just not meant to be.",
            "Oh well.",
            "Could have been worse, I guess. Or not.",
            "Against all odds... you did not triumph.",
            "How unfortunate.",
            "That's just the way it goes sometimes.",
            "You should definitely pick the left ball next attempt.",
            "Having fun yet?",
            "The house always wins.",
            "Anything that can go wrong, will go wrong.",
            "Looks like your luck finally ran out.",
        ),
    )

/** The pool the DS tracker draws its closing line from for [cause]; the popup uses the same one. */
internal fun ndsRunOverLines(cause: com.ironmonone.tracker.nds.NdsRunOver): List<String> =
    NDS_RUN_OVER_MESSAGES[cause] ?: NDS_RUN_OVER_MESSAGES.getValue(com.ironmonone.tracker.nds.NdsRunOver.STANDARD)

/**
 * The DS end-of-run card. Same shape as the GBA one, but the message comes from
 * the cause rather than a single pool.
 *
 * The line is chosen by attempt number, not at random: a message that reshuffles
 * on every tracker poll is unreadable.
 */
@Composable
fun PcNdsRunOver(
    cause: com.ironmonone.tracker.nds.NdsRunOver,
    attempt: Int,
    party: List<NdsTrackedMon>,
) {
    val won = cause == com.ironmonone.tracker.nds.NdsRunOver.WON
    val lines = NDS_RUN_OVER_MESSAGES[cause]
        ?: NDS_RUN_OVER_MESSAGES.getValue(com.ironmonone.tracker.nds.NdsRunOver.STANDARD)
    val line = lines[((attempt % lines.size) + lines.size) % lines.size]
    PcCard {
        Column(Modifier.fillMaxWidth().padding(6.dp)) {
            PixText(if (won) "R u n  W o n" else "R u n  O v e r", 11, Pc.Gold)
            Spacer(Modifier.height(5.dp))
            Row {
                PixText("Attempt:", 8, Pc.Text, Modifier.width(66.dp))
                PixText("$attempt", 8, Pc.Text)
            }
            Spacer(Modifier.height(5.dp))
            PixText(line, 9, if (won) Pc.Positive else Pc.Negative)
        }
        androidx.compose.foundation.layout.Box(
            Modifier.fillMaxWidth().height(1.dp).background(Pc.Border))
        Column(Modifier.padding(4.dp)) {
            PixText("Final team", 8, Pc.Text)
            Spacer(Modifier.height(3.dp))
            party.forEach { p ->
                val m = p.mon
                Row(Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
                    PixText(
                        p.speciesName, 8,
                        if (m.curHp == 0) Pc.Negative else Pc.Text,
                        Modifier.weight(1f),
                    )
                    PixText("Lv.${m.level}", 7, Pc.Dim, Modifier.width(46.dp))
                    PixText(
                        "${m.maxHp}/${m.atk}/${m.def}/${m.spAtk}/${m.spDef}/${m.spe}",
                        7, Pc.Dim,
                    )
                }
            }
        }
    }
}

@Composable
fun NdsTrackerPanel(
    /** The startup favorites line, shown before a party exists, as the DS tracker's title screen shows them. */
    favoriteLine: String? = null,
    /** Move History for a card: (species, name, level). */
    onMoveHistory: ((Int, String, Int) -> Unit)? = null,
    /** Type Defenses for a card: (name, type1, type2) as the sidecar names them. */
    onTypeDefenses: ((String, String, String) -> Unit)? = null,
    state: NdsTrackerState?,
    modifier: Modifier = Modifier,
    onFlee: () -> Unit = {},
    enemyMarks: IntArray = IntArray(StatMarks.COUNT),
    enemyEncounters: Int = 0,
    onCycleMark: (Int) -> Unit = {},
    enemyNote: String = "",
    onEditNote: () -> Unit = {},
    attempt: Int = 0,
    coverage: Map<Double, List<Int>> = emptyMap(),
    /** The enemy's ability, once a battle trigger has revealed it this run. */
    revealedEnemyAbility: String? = null,
    movesSeenRunWide: List<StatMarks.SeenMove> = emptyList(),
    moveInfoFor: (Int) -> NdsMoveInfo? = { null },
    /** Opens the tracker's gear (the reference's SettingsGear). */
    onGear: (() -> Unit)? = null,
) {
    // Same reference canvas as the GBA panel. Without it this panel would keep
    // the shared boxes' new REFERENCE-pixel sizes at 1dp each, i.e. the right
    // proportions at the wrong scale - the two trackers must not drift apart.
    PcCanvas(modifier.fillMaxWidth()) {
      Column(Modifier.fillMaxWidth().background(Pc.Page).padding(PcRef.MARGIN.rp)) {
          // The reference's gear sits at the top of the tracker screen; SETUP is its NavigationMenu.ButtonSetup.
          onGear?.let { g -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { PcSmallButton("SETUP") { g() } }; Spacer(Modifier.height(3.dp)) }
        when {
            state == null -> PcCard {
                PixText("DS tracker: waiting for the game...", 8, Pc.Dim,
                    Modifier.padding(6.dp))
            }

            !state.located && !state.inBattle -> PcCard {
                Column(Modifier.padding(6.dp)) {
                    PixText("No party yet - the tracker locks on once you", 8, Pc.Dim)
                    PixText("have a Pokemon.", 8, Pc.Dim)
                    Spacer(Modifier.height(3.dp))
                    PixText(
                        if (state.resolvedBase != 0L)
                            "party base 0x%08X - reading live memory"
                                .format(state.resolvedBase)
                        else "address chain not resolved yet",
                        7, Pc.Dim,
                    )
                    favoriteLine?.let { Spacer(Modifier.height(3.dp)); PixText(it, 7, Pc.Gold, wrap = true) }
                }
            }

            // A finished run goes above the team, not under it.
            state.runOver != null -> {
                // Bound locally: runOver comes from another module, so it
                // cannot be smart-cast in place.
                val cause = state.runOver!!
                PcNdsRunOver(cause, attempt, state.party)
            }

            else -> {
                if (state.inBattle) {
                    PcBattleBanner(state.isWildBattle, onFlee)
                    Spacer(Modifier.height(4.dp))
                    state.enemy?.let {
                        NdsEnemyCard(onMoveHistory = onMoveHistory, onTypeDefenses = onTypeDefenses, it, revealedEnemyAbility, enemyMarks,
                            enemyEncounters, onCycleMark, enemyNote, onEditNote,
                            movesSeenRunWide, moveInfoFor)
                    }
                }
                state.party.forEachIndexed { i, p ->
                    NdsPartyCard(onMoveHistory = onMoveHistory, onTypeDefenses = onTypeDefenses, p,
                        healPercent = if (i == 0) state.healPercent else -1,
                        healCount = state.healCount)
                }
                PcCoverage(coverage, coverage.values.sumOf { it.size })
                PcCarousel(
                    inBattle = state.inBattle,
                    badges = state.badges,
                    badgeSet = state.badgeSet,
                    note = enemyNote,
                    onEditNote = onEditNote,
                    encounters = enemyEncounters,
                )
            }
        }
    }
    }
}
