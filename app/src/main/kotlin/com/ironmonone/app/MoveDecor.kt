package com.ironmonone.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import com.ironmonone.tracker.EnemyInfo
import com.ironmonone.tracker.MoveRow
import com.ironmonone.tracker.MoveRules
import com.ironmonone.tracker.TrackedMon

/**
 * What the move table needs from the battle for one card (DataHelper.lua
 * 255-370): who is attacking, who is the target, and the weather.
 *
 * Types are the species' base types. The reference reads the battlers' live
 * types, which differ only after something like Color Change or Conversion.
 */
internal data class MoveContext(
    val inBattle: Boolean,
    val viewingOwn: Boolean,
    val attackerTypes: List<Int>,
    val targetTypes: List<Int>,
    val source: MoveRules.Side,
    val target: MoveRules.Side?,
    val weather: String?,
)

private fun kg(s: String?): Double? = s?.trim()?.toDoubleOrNull()

/** Your Pokemon's card: its moves against the opponent, when there is one. */
internal fun ownMoveContext(p: TrackedMon, enemy: EnemyInfo?, weather: String?, weight: ((Int) -> String?)?): MoveContext {
    val m = p.mon
    return MoveContext(
        inBattle = enemy != null, viewingOwn = true,
        attackerTypes = listOfNotNull(p.base?.type1, p.base?.type2),
        targetTypes = enemy?.let { listOf(it.type1, it.type2) } ?: emptyList(),
        source = MoveRules.Side(m.level, m.curHp, m.maxHp, m.friendship, kg(weight?.invoke(m.species))),
        target = enemy?.let { MoveRules.Side(it.level, weightKg = kg(weight?.invoke(it.species))) },
        weather = weather,
    )
}

/** The opponent's card: its moves against your active Pokemon. */
internal fun enemyMoveContext(e: EnemyInfo, lead: TrackedMon?, weather: String?, weight: ((Int) -> String?)?): MoveContext =
    MoveContext(
        inBattle = true, viewingOwn = false,
        attackerTypes = listOf(e.type1, e.type2),
        targetTypes = lead?.base?.let { listOf(it.type1, it.type2) } ?: emptyList(),
        source = MoveRules.Side(e.level, e.curHp, e.maxHp),
        target = lead?.mon?.let { MoveRules.Side(it.level, weightKg = kg(weight?.invoke(it.species))) },
        weather = weather,
    )

/** One ROM move row as the PC tracker draws it (MoveRules). */
internal fun MoveRow.toPcMove(ctx: MoveContext?): PcMove {
    val shownType = MoveRules.shownType(id, type)
    val base = MoveRules.basePower(id, power)
    val acc0 = (acc ?: 0).toString()
    val adj = ctx?.let {
        MoveRules.adjust(id, shownType, base, acc0, it.source, it.target, it.inBattle, it.viewingOwn, it.weather, TrackerOptions.determineFriendship)
    } ?: MoveRules.Shown(shownType, base, acc0)
    // A Weather Ball that took the weather's type changes category with it:
    // Gen 3 splits physical and special by type.
    val newType = adj.type
    val cat = if (id == MoveRules.WEATHER_BALL && newType != null && newType != shownType) {
        if (newType <= 8) "PHY" else "SPE"
    } else category
    val battling = ctx != null && ctx.inBattle
    return PcMove(
        id = id, name = name, pp = pp, ppMax = ppMax, power = power, acc = acc,
        color = adj.type?.let { pcTypeColor(it) } ?: Pc.Text,
        category = cat,
        type = adj.type, typeName = adj.type?.let(com.ironmonone.tracker.Gen3Types::name),
        priority = priority, contact = contact,
        powerText = adj.power, accText = adj.acc,
        stab = battling && MoveRules.isStab(id, adj.type, cat, adj.power, ctx!!.attackerTypes),
        effect = if (battling) MoveRules.effectiveness(id, adj.type, cat, ctx!!.targetTypes).takeIf { it != 1.0 } else null,
    )
}

/**
 * Drawing.drawMoveEffectiveness: a green chevron up for 2x (two for 4x), a red
 * chevron down for 1/2 (two for 1/4), and a red X where it has no effect. The
 * chevrons are the reference's 4 by 2, one pixel thick.
 */
@Composable
internal fun PcEffectGlyph(effect: Double, modifier: Modifier = Modifier) {
    if (effect == 0.0) {
        PixText("X", PcRef.FONT, Pc.Negative, modifier)
        return
    }
    val up = effect > 1.0
    val two = effect >= 4.0 || effect <= 0.25
    val color = if (up) Pc.Positive else Pc.Negative
    Canvas(modifier.width(5.rp).height(9.rp)) {
        val u = size.width / 5f
        fun chevron(y: Float, pointUp: Boolean) {
            val tip = (if (pointUp) y else y + 2f) * u + u / 2
            val foot = (if (pointUp) y + 2f else y) * u + u / 2
            drawLine(color, Offset(0.5f * u, foot), Offset(2.5f * u, tip), strokeWidth = u)
            drawLine(color, Offset(2.5f * u, tip), Offset(4.5f * u, foot), strokeWidth = u)
        }
        if (up) { chevron(4f, true); if (two) chevron(2f, true) }
        else { chevron(0f, false); if (two) chevron(2f, false) }
    }
}
