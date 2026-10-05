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
 * One Team View box (TeamViewArea.createPartyMemberBox, lua:58-230). An egg (isEgg, :62) is
 * "EGG" over the egg icon, no status, the unknown type, "Lv.?", no HP or EXP bar, "---" for
 * its item and ability, and nothing to tap (:87-130, :159-229); it used to show the species
 * or nickname with its stats. [eggSpecies] is the egg icon's id (PokemonData.Values.EggId).
 */
internal data class TeamBox(
    val name: String, val iconSpecies: Int, val status: String, val types: List<Pair<String, Int>>,
    val level: String, val hp: Float?, val exp: Float?, val item: String, val ability: String, val tappable: Boolean,
) {
    companion object {
        fun of(p: TrackedMon, eggSpecies: Int): TeamBox {
            val m = p.mon
            if (m.isEgg) return TeamBox("EGG", eggSpecies, "", listOf(InfoRules.UNKNOWN_TYPE), "Lv.?", null, null, "---", "---", tappable = false)
            val types = p.base?.let { b -> listOf(Gen3Types.name(b.type1) to b.type1) + (if (b.type2 != b.type1) listOf(Gen3Types.name(b.type2) to b.type2) else emptyList()) } ?: emptyList()
            return TeamBox(
                name = m.nickname.ifBlank { p.speciesName }, iconSpecies = m.species,
                status = if (m.curHp <= 0) "FNT" else p.statusCondition, types = types, level = "Lv.${m.level}",
                hp = if (m.maxHp > 0) m.curHp.toFloat() / m.maxHp else 0f,
                exp = if (p.expTotal > 0) p.expNow.toFloat() / p.expTotal else null,
                item = if (m.heldItem != 0) p.itemName else "---", ability = p.abilityName.ifBlank { "---" }, tappable = true,
            )
        }
    }
}

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
    /** The egg's icon: 412, PokemonData.Values.EggId; the Nat. Dex pack's egg is 1284. */
    eggSpecies: Int = 412,
) {
    if (party.isEmpty()) return
    // The reference has 390 units across for six boxes; this panel has 150,
    // so the six go three to a row or every nickname clips at six letters.
    val boxW = (PcRef.WIDTH - 2 * PcRef.MARGIN) / 3
    party.take(6).chunked(3).forEach { row -> Row(Modifier.fillMaxWidth()) {
        row.forEach { p ->
            val box = TeamBox.of(p, eggSpecies)
            // Through boxFill like every other tracker box, so a picture behind the tracker shows through it (rc32 audit P3 #21).
            Column(
                Modifier.width(boxW.rp).background(trackerBoxFill(Pc.Ground)).border(1.rp, Pc.Border).padding(1.rp),
            ) {
                PixText(box.name, PcRef.FONT - 1, Pc.Text)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(if (box.tappable) Modifier.clickable { onMon(p) } else Modifier) { PcSprite(romPicture(p.picture) ?: spriteFor(box.iconSpecies)) }
                    if (box.status.isNotEmpty()) PixText(box.status, PcRef.FONT - 3, Pc.Negative)
                }
                Column(Modifier.let { m -> if (onTypes != null && box.tappable) m.clickable { onTypes(p) } else m }) {
                    box.types.forEach { (name, type) -> PcTypeChip(name, pcTypeColor(type)) }
                }
                PixText(box.level, PcRef.FONT - 2, Pc.Text)
                box.hp?.let { hp -> PcBar(hp, when { hp >= 0.5f -> Pc.Positive; hp >= 0.2f -> Pc.Gold; else -> Pc.Negative }) }
                box.exp?.let { PcBar(it, Pc.Text) }
                Spacer(Modifier.height(1.rp))
                PixText(box.item, PcRef.FONT - 2, Pc.Gold)
                PixText(box.ability, PcRef.FONT - 2, Pc.Gold,
                    Modifier.let { m -> if (onAbility != null && box.tappable) m.clickable { onAbility(p) } else m })
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
