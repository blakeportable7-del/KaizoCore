package com.ironmonone.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
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
    /** Your Pokemon's Hidden Power type as set (HiddenPowerTypes); null for the opponent or unset. */
    val hiddenPowerType: Int? = null,
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
        hiddenPowerType = HiddenPowerTypes.of(m.pid),
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
    val shownType = MoveRules.shownType(id, type, ctx?.hiddenPowerType)
    val base = MoveRules.basePower(id, power)
    val acc0 = (acc ?: 0).toString()
    // "Calculate variable damage" off: the labels stay labels, and Weather Ball
    // keeps its own type (the reference only recolours it with the option on).
    val adj = ctx?.takeIf { TrackerOptions.calculateVariableDamage }?.let {
        MoveRules.adjust(id, shownType, base, acc0, it.source, it.target, it.inBattle, it.viewingOwn, it.weather, TrackerOptions.determineFriendship)
    } ?: MoveRules.Shown(shownType, base, acc0)
    // A Weather Ball that took the weather's type changes category with it:
    // Gen 3 splits physical and special by type.
    val newType = adj.type
    // Gen 3 splits physical and special by type, so a Weather Ball that took
    // the weather's type, or a Hidden Power with its type set, follows it.
    // Hidden Power with no type set has no category at all: MoveData.getCategory
    // maps the unknown type to NONE, so no icon is drawn.
    val cat = when {
        id == MoveRules.HIDDEN_POWER -> newType?.let { if (it <= 8) "PHY" else "SPE" }
        id == MoveRules.WEATHER_BALL && newType != null && newType != shownType -> if (newType <= 8) "PHY" else "SPE"
        else -> category
    }
    val battling = ctx != null && ctx.inBattle
    return PcMove(
        id = id, name = name, pp = pp, ppMax = ppMax, power = power, acc = acc,
        color = adj.type?.let { pcTypeColor(it) } ?: Pc.Text,
        category = cat,
        type = adj.type, typeName = adj.type?.let(com.ironmonone.tracker.Gen3Types::name),
        priority = priority, contact = contact,
        powerText = adj.power, accText = adj.acc,
        stab = battling && MoveRules.isStab(id, adj.type, cat, adj.power, ctx!!.attackerTypes),
        effect = if (battling && TrackerOptions.showMoveEffectiveness) MoveRules.effectiveness(id, adj.type, cat, ctx!!.targetTypes).takeIf { it != 1.0 } else null,
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

/** Constants.PixelImages.SWORD_ATTACK, 14 by 13, exactly as the reference draws it. */
private val SWORD = listOf(
    "00000000000110",
    "00000000001010",
    "00000000010110",
    "00000000101100",
    "00000001011000",
    "00000010110000",
    "10000101100000",
    "11001011000000",
    "01110110000000",
    "00111100000000",
    "01011000000000",
    "10101100000000",
    "11000110000000",
)

@Composable
internal fun PcPixelImage(rows: List<String>, color: Color, modifier: Modifier = Modifier) {
    val w = rows.maxOf { it.length }; val h = rows.size
    Canvas(modifier.width(w.rp).height(h.rp)) {
        val u = size.width / w
        rows.forEachIndexed { y, r -> r.forEachIndexed { x, c -> if (c == '1') drawRect(color, Offset(x * u, y * u), Size(u, u)) } }
    }
}

/**
 * The carousel's last attack (TrackerScreen.lua LAST_ATTACK): the sword, then
 * "Wing Attack: 23 damage". The sword turns red when that hit is enough to
 * knock your Pokemon out.
 */
@Composable
internal fun PcLastAttackLine(text: String, lethal: Boolean) {
    Row(
        Modifier.fillMaxWidth().background(Pc.Ground).border(1.dp, Pc.Border)
            .padding(horizontal = 6.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PcPixelImage(SWORD, if (lethal) Pc.Negative else Pc.Text)
        Spacer(Modifier.width(6.dp))
        PixText(text, 8, Pc.Text)
    }
}

/** Constants.Char widths, the reference font's pixel width per character; 1 where unlisted. */
private val CHAR_WIDTH: Map<Char, Int> = mapOf('A' to 4, 'B' to 4, 'C' to 4, 'D' to 5, 'E' to 4, 'F' to 4, 'G' to 5, 'H' to 5, 'I' to 1, 'J' to 2, 'K' to 5, 'L' to 3, 'M' to 6, 'N' to 5, 'O' to 5, 'P' to 4, 'Q' to 5, 'R' to 5, 'S' to 4, 'T' to 3, 'U' to 4, 'V' to 4, 'W' to 7, 'X' to 4, 'Y' to 5, 'Z' to 4, 'a' to 4, 'b' to 4, 'c' to 3, 'd' to 4, 'e' to 4, 'f' to 1, 'g' to 4, 'h' to 4, 'i' to 1, 'j' to 2, 'k' to 4, 'l' to 1, 'm' to 7, 'n' to 4, 'o' to 4, 'p' to 4, 'q' to 3, 'r' to 2, 's' to 3, 't' to 2, 'u' to 4, 'v' to 3, 'w' to 5, 'x' to 3, 'y' to 3, 'z' to 3, '0' to 4, '1' to 4, '2' to 4, '3' to 4, '4' to 4, '5' to 4, '6' to 4, '7' to 4, '8' to 4, '9' to 4, '.' to 1, '-' to 2, '\'' to 1, ' ' to 1)

/** Utils.calcWordPixelLength: each character's width plus a pixel between them. */
internal fun referenceWidth(text: String): Int =
    if (text.isEmpty()) 0 else text.sumOf { (CHAR_WIDTH[it] ?: 1) + 1 } - 1

/**
 * TrackerScreen.lua's GENDER ICON: beside the name when the name is under 45
 * pixels wide, over the icon otherwise. Measured on the name in title case, as
 * the reference prints it, so a Pokemon gets the same placement it would there.
 */
internal fun genderFitsAfter(name: String): Boolean =
    referenceWidth(name.lowercase().replaceFirstChar { it.uppercaseChar() }) < 45

/** Constants.PixelImages MALE_SYMBOL and FEMALE_SYMBOL, in the default text colour. */
private val MALE_SYMBOL = listOf("00000111", "00000011", "00110101", "01001000", "10000100", "10000100", "01001000", "00110000")
private val FEMALE_SYMBOL = listOf("01110", "10001", "10001", "10001", "01110", "00100", "01110", "00100")

@Composable
internal fun PcGenderSymbol(gender: Int, modifier: Modifier = Modifier) =
    PcPixelImage(if (gender == com.ironmonone.tracker.Gender3.MALE) MALE_SYMBOL else FEMALE_SYMBOL, Pc.Text, modifier)

/**
 * Drawing.drawPercentageBar, 60 by 3: a rounded outline in the box border
 * colour, filled from the left in the default text colour.
 */
@Composable
internal fun PcExpBar(fraction: Float, modifier: Modifier = Modifier) {
    Canvas(modifier.width(61.rp).height(4.rp)) {
        val u = size.width / 61f
        val w = 60f; val h = 3f
        val fill = kotlin.math.min(kotlin.math.floor(w * fraction.coerceIn(0f, 1f) + 0.5f), w)
        drawRect(Pc.Text, Offset(0f, 0f), Size(fill * u, h * u))
        drawRect(Pc.Border, Offset(u, 0f), Size((w - 1) * u, u))            // top
        drawRect(Pc.Border, Offset(u, h * u), Size((w - 1) * u, u))        // bottom
        drawRect(Pc.Border, Offset(0f, u), Size(u, (h - 1) * u))            // left
        drawRect(Pc.Border, Offset(w * u, u), Size(u, (h - 1) * u))        // right
    }
}
