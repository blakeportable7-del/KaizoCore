@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.ironmonone.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.ironmonone.tracker.Gen3Types
import com.ironmonone.tracker.nuzlocke.NuzlockeCoverage
import com.ironmonone.tracker.nuzlocke.NuzlockeMatchup
import com.ironmonone.tracker.nuzlocke.NuzlockeOdds
import com.ironmonone.tracker.nuzlocke.NuzlockeScout
import com.ironmonone.tracker.nuzlocke.NuzlockeSystem
import com.ironmonone.tracker.nuzlocke.ScoutMon
import com.ironmonone.tracker.nuzlocke.ScoutTeam
import com.ironmonone.tracker.nuzlocke.Snapshot

/*
 * The ledger's BOSSES and TYPES tabs (2026-10-06, the Nuzlify best practices): the teams still to come with the chance
 * your Pokemon survives each of their moves, and the team's weak spots by type. The rules of what may be shown are in
 * tracker-gba (NuzlockeScout's fence, NuzlockeMatchup, NuzlockeCoverage); this file only draws them.
 */

/** The bosses still to come, when NuzlockeScout's fence lets them be shown. */
@Composable
internal fun BossesTab(live: NuzlockeTracking.Live, snapshot: Snapshot?, @Suppress("UNUSED_PARAMETER") rev: Int) {
    val meta = live.ledger.meta
    if (snapshot == null) {
        DialogText("Play the game to see the bosses still to come.", 12, Pc.Dim)
        return
    }
    val beaten = snapshot.beaten + meta.beatenBosses
    val view = remember(snapshot.caps, beaten, snapshot.scout, meta.bind, meta.rules.preset) {
        runCatching { NuzlockeScout.view(meta, snapshot.caps, beaten, snapshot.scout) }
            .getOrDefault(NuzlockeScout.View.Hidden(NuzlockeScout.Closed.NO_DATA))
    }
    var picked by remember { mutableStateOf<ScoutMon?>(null) }
    when (view) {
        is NuzlockeScout.View.Hidden -> DialogText(view.why.text, 12, Pc.Dim)
        is NuzlockeScout.View.Shown -> {
            DialogText("The game's own teams. Tap a Pokemon to see how yours hold up against it.", 12, Pc.Dim)
            NzSection(if (view.next.size > 1) "Next, in any order" else "Next")
            for (t in view.next) TeamBlock(t) { picked = it }
            if (view.later.isNotEmpty()) {
                NzSection("Later")
                for (t in view.later) TeamBlock(t) { picked = it }
            }
        }
    }
    picked?.let { mon -> MatchupSheet(live, snapshot, mon) { picked = null } }
}

@Composable
private fun TeamBlock(team: ScoutTeam, onPick: (ScoutMon) -> Unit) {
    Spacer(Modifier.height(6.dp))
    DialogText("${team.boss.place}: ${team.name}", 13, Pc.Gold)
    DialogText("Highest level ${team.boss.cap}" + (if (team.boss.group.isNotBlank() && team.boss.kind == "post") ", ${team.boss.group}" else ""), 12, Pc.Dim)
    for (m in team.party) {
        Column(Modifier.fillMaxWidth().heightIn(min = PcMin.DIALOG_TOUCH_DP.dp).clickable(role = Role.Button) { onPick(m) }.padding(vertical = 4.dp)) {
            Row(Modifier.fillMaxWidth()) {
                DialogText(m.speciesName, 13, Pc.Text, Modifier.weight(1f))
                DialogText("Lv ${m.level}", 12, Pc.Dim)
            }
            val types = m.types.joinToString("/") { Gen3Types.name(it) }
            DialogText(listOfNotNull(types.ifEmpty { null }, m.item?.let { "holds $it" }).joinToString(", "), 12, Pc.Dim)
            if (m.moves.isNotEmpty()) DialogText(m.moves.joinToString(", ") { it.name } + if (m.defaultMoves) " (the last four it learned)" else "", 12, Pc.Text)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Pc.Border.copy(alpha = 0.3f)))
    }
}

/** One boss Pokemon against each of your party: the chance to live through each of its moves. */
@Composable
private fun MatchupSheet(live: NuzlockeTracking.Live, snapshot: Snapshot, mon: ScoutMon, onClose: () -> Unit) {
    val gen = when (live.ledger.meta.system) {
        NuzlockeSystem.GEN1 -> 1; NuzlockeSystem.GEN2 -> 2; NuzlockeSystem.GEN3 -> 3; NuzlockeSystem.GEN4 -> 4; NuzlockeSystem.GEN5 -> 5
    }
    InfoSheet("${mon.speciesName} LV ${mon.level}", onClose) {
        val party = snapshot.party.filter { it.real && it.stats.size >= 6 }
        if (party.isEmpty()) DialogText("The tracker cannot read your party's stats right now.", 12, Pc.Dim)
        for (p in party) {
            val name = live.ledger.roster[p.id]?.shownName ?: p.nickname.ifBlank { p.speciesName }
            val you = NuzlockeMatchup.Defender(name, p.types, p.stats, p.hp)
            val lines = NuzlockeMatchup.lines(mon, you, gen)
            NzSection("$name, HP ${p.hp}/${p.maxHp}")
            if (p.fainted) DialogText("Fainted.", 12, Pc.Negative)
            NuzlockeMatchup.speed(mon, you)?.let { DialogText(it, 12, Pc.Dim) }
            if (lines.isEmpty()) DialogText("None of its moves does damage.", 12, Pc.Dim)
            for (l in lines) {
                val live = l.survive
                val color = when {
                    live == null -> Pc.Dim
                    live >= 1.0 -> Pc.Positive
                    live <= 0.5 -> Pc.Negative
                    else -> Pc.Gold
                }
                DialogText(l.move, 12, Pc.Text, Modifier.padding(top = 3.dp))
                DialogText(l.text, 12, color)
            }
        }
        Spacer(Modifier.height(8.dp))
        DialogText(
            "One hit, if it hits, at the HP your Pokemon has now, critical hits counted. The boss's Pokemon is taken with a " +
                "neutral nature and no stat changes, held items or abilities, so read it as a guide.",
            12, Pc.Dim,
        )
    }
}

/** The team's weak spots by type: the party, or the party and the box. */
@Composable
internal fun TypesTab(live: NuzlockeTracking.Live, snapshot: Snapshot?, @Suppress("UNUSED_PARAMETER") rev: Int) {
    val ledger = live.ledger
    var withBox by remember { mutableStateOf(false) }
    val moveTypes = snapshot?.party?.associate { it.id to it.moveTypes }.orEmpty()
    val mons = (if (withBox) ledger.party + ledger.boxed else ledger.party).filter { it.types.isNotEmpty() }
    val team = mons.map { NuzlockeCoverage.Member(it.shownName, it.types, moveTypes[it.id].orEmpty()) }
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        NzChip("PARTY", !withBox) { withBox = false }
        NzChip("PARTY AND BOX", withBox) { withBox = true }
    }
    if (team.isEmpty()) {
        Spacer(Modifier.height(6.dp))
        DialogText("Nobody to look at yet.", 12, Pc.Dim)
        return
    }
    val report = NuzlockeCoverage.report(team, ledger.meta.system)
    val danger = report.rows.filter { it.danger }
    NzSection("Watch out for")
    if (danger.isEmpty()) DialogText("No type hits more of the team hard than the team takes well.", 12, Pc.Dim)
    for (r in danger) DialogText("${Gen3Types.name(r.type)}: hits ${r.weak.joinToString(", ")} hard, and only ${r.resist.size + r.immune.size} take it well.", 12, Pc.Gold)
    NzSection("Every type against you")
    for (r in report.rows) {
        val parts = listOfNotNull(
            r.weak.takeIf { it.isNotEmpty() }?.let { "weak: " + it.joinToString(", ") },
            r.resist.takeIf { it.isNotEmpty() }?.let { "takes little: " + it.joinToString(", ") },
            r.immune.takeIf { it.isNotEmpty() }?.let { "takes nothing: " + it.joinToString(", ") },
        )
        Column(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
            DialogText(Gen3Types.name(r.type), 12, if (r.danger) Pc.Gold else Pc.Text)
            DialogText(parts.joinToString(". ").ifEmpty { "Normal damage to everyone." }, 12, Pc.Dim)
        }
    }
    NzSection("Your moves")
    when {
        !report.movesKnown -> DialogText("The party's moves are not known yet. They show while the game is running.", 12, Pc.Dim)
        report.notCovered.isEmpty() -> DialogText("Some move of the party hits every type hard.", 12, Pc.Positive)
        else -> DialogText("No move in the party hits these hard: " + report.notCovered.joinToString(", ") { Gen3Types.name(it) } + ".", 12, Pc.Text)
    }
    if (withBox) DialogText("Boxed Pokemon count for the types they take; their moves are not known.", 12, Pc.Dim)
}

/** The odds words for Calc Atk, from the attack range it worked out: one line, or null when it found nothing. */
internal fun calcAtkSurvival(inputs: com.ironmonone.tracker.CalcAtk.Inputs, estimate: Pair<Int, Int>, hp: Int, maxHp: Int): String? {
    if (hp <= 0 || maxHp <= 0 || inputs.level <= 0 || estimate.first > estimate.second) return null
    val other = inputs.other.takeIf { it != 0.0 } ?: 1.0
    val weather = when (inputs.weather) { com.ironmonone.tracker.CalcAtk.WEATHER_BOOSTED -> 1.5; com.ironmonone.tracker.CalcAtk.WEATHER_HALVED -> 0.5; else -> 1.0 }
    fun at(attack: Int) = NuzlockeOdds.odds(
        NuzlockeOdds.Hit(
            generation = 3, level = inputs.level, power = maxOf(1, inputs.power), attack = attack, defense = maxOf(1, inputs.defense),
            stab = inputs.stab, effectiveness = if (inputs.effectiveness == 0.0) 1.0 else inputs.effectiveness,
            other = other * weather * (if (inputs.burned) 0.5 else 1.0) * (if (inputs.screen) 0.5 else 1.0),
        ),
        hp, maxHp, NuzlockeOdds.critChance(3),
    )
    val low = at(estimate.first); val high = at(estimate.second)
    val a = NuzlockeOdds.inHundred(high.survive).removeSuffix(" in 100")
    val b = NuzlockeOdds.inHundred(low.survive)
    return if (a + " in 100" == b) "The same hit again at $hp HP: survives $b." else "The same hit again at $hp HP: survives $a to $b."
}
