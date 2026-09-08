package com.ironmonone.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import com.ironmonone.tracker.Gen3Types
import com.ironmonone.tracker.TrackedMon

/**
 * TeamViewArea.lua: the whole party in six boxes, each with the nickname,
 * the icon, the status, the types, the level, an HP bar coloured at the
 * reference's 50% and 20% thresholds, an EXP bar, the held item and the
 * ability. The icon opens Pokemon info, the types open Type Defenses, the
 * ability opens its description. Drawn in the panel's 150-wide reference
 * units like everything else on the tracker.
 */
@Composable
fun PcTeamView(
    party: List<TrackedMon>,
    spriteFor: (Int) -> ImageBitmap?,
    onMon: (TrackedMon) -> Unit,
    onTypes: ((TrackedMon) -> Unit)?,
    onAbility: ((TrackedMon) -> Unit)?,
) {
    if (party.isEmpty()) return
    // The reference has 390 units across for six boxes; this panel has 150,
    // so the six go three to a row or every nickname clips at six letters.
    val boxW = (PcRef.WIDTH - 2 * PcRef.MARGIN) / 3
    party.take(6).chunked(3).forEach { row -> Row(Modifier.fillMaxWidth()) {
        row.forEach { p ->
            Column(
                Modifier.width(boxW.rp).background(Pc.Ground).border(1.rp, Pc.Border).padding(1.rp),
            ) {
                PixText(p.mon.nickname.ifBlank { p.speciesName }, PcRef.FONT - 1, Pc.Text)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.clickable { onMon(p) }) { PcSprite(spriteFor(p.mon.species)) }
                    val status = if (p.mon.curHp <= 0) "FNT" else p.statusCondition
                    if (status.isNotEmpty()) PixText(status, PcRef.FONT - 3, Pc.Negative)
                }
                val base = p.base
                Column(Modifier.let { m -> if (onTypes != null) m.clickable { onTypes(p) } else m }) {
                    if (base != null) {
                        PcTypeChip(Gen3Types.name(base.type1), pcTypeColor(base.type1))
                        if (base.type2 != base.type1) PcTypeChip(Gen3Types.name(base.type2), pcTypeColor(base.type2))
                    }
                }
                PixText("Lv.${p.mon.level}", PcRef.FONT - 2, Pc.Text)
                val hp = if (p.mon.maxHp > 0) p.mon.curHp.toFloat() / p.mon.maxHp else 0f
                PcBar(hp, when { hp >= 0.5f -> Pc.Positive; hp >= 0.2f -> Pc.Gold; else -> Pc.Negative })
                if (p.expTotal > 0) PcBar(p.expNow.toFloat() / p.expTotal, Pc.Text)
                Spacer(Modifier.height(1.rp))
                PixText(if (p.mon.heldItem != 0) p.itemName else "---", PcRef.FONT - 2, Pc.Gold)
                PixText(p.abilityName.ifBlank { "---" }, PcRef.FONT - 2, Pc.Gold,
                    Modifier.let { m -> if (onAbility != null) m.clickable { onAbility(p) } else m })
            }
        }
    } }
}

/** Drawing.drawPercentageBar: a 3-unit bar, filled left to right inside the box border. */
@Composable
private fun PcBar(fraction: Float, fill: Color) {
    Box(Modifier.fillMaxWidth().height(3.rp).border(1.rp, Pc.Border).background(Pc.Page)) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(3.rp).background(fill))
    }
}
