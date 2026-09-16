package com.ironmonone.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/**
 * Repel usage, cloned from Ironmon-Tracker's Program.ActiveRepel and
 * Drawing.drawRepelUsage (the DS tracker's RepelDrawer draws the same thing).
 *
 * The games keep the steps a repel has left in one save variable, which sits at
 * 0 when no repel is running: SaveBlock1 + gameVarsOffset + 0x40 on FireRed and
 * LeafGreen, + 0x42 on Ruby, Sapphire and Emerald; a fixed address per game on
 * the DS. How LONG the repel was is not stored, so the reference infers it from
 * the highest count it has seen this repel (100, Super 200, Max 250) and resets
 * to 100 when the count reaches 0 - [duration] is that rule.
 *
 * Gen 1 and Gen 2 are NOT included: both of those trackers carry the drawing
 * code but never call their update (`if false and Options[...]` in Program.lua),
 * so nothing is tracked there and nothing is cloned here.
 */
object Repel {
    /** The rules themselves live beside the trackers that read the value (RepelRules). */
    const val MAX_STEPS = com.ironmonone.tracker.RepelRules.MAX_STEPS

    fun duration(steps: Int, current: Int): Int = com.ironmonone.tracker.RepelRules.duration(steps, current)

    /** Drawing.drawRepelUsage: green, the intermediate colour at or below a half, red at or below a quarter. */
    fun barColor(steps: Int, duration: Int): Color {
        val f = if (duration <= 0) 0f else steps.toFloat() / duration
        return when {
            f <= 0.25f -> Pc.Negative
            f <= 0.5f -> Pc.Gold
            else -> Pc.Positive
        }
    }

    /** The share of the bar still filled, 0 to 1. */
    fun fraction(steps: Int, duration: Int): Float = com.ironmonone.tracker.RepelRules.fraction(steps, duration)

    /** RepelDrawer.onRepelUsage: the DS tracker's icon for the repel that was used. */
    fun dsIcon(duration: Int): String = com.ironmonone.tracker.RepelRules.dsIcon(duration)
}

/**
 * The repel icon with its usage bar, as the reference draws it in the tracker's
 * top right: the item, then a 4 by 21 bar whose filled part is what is left.
 * [dsIcons] picks the DS tracker's three item icons instead of Gen 3's one.
 */
@Composable
fun PcRepelBar(steps: Int, duration: Int, dsIcons: Boolean = false) {
    if (steps <= 0) return
    val ctx = LocalContext.current
    val name = if (dsIcons) Repel.dsIcon(duration) else "repelUsage"
    val art = remember(name) { PcAssets.icon(ctx, name) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (art != null) {
            Image(art, "Repel", Modifier.size(if (dsIcons) 14.rp else 20.rp), filterQuality = FilterQuality.None)
        }
        val icon = if (dsIcons) 14 else 20
        val bar = (icon * 21) / 24
        Box(Modifier.width(4.rp).height(icon.rp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.width(4.rp).height(bar.rp)) {
                // The outer bar (the reference draws a black outline over the box colour),
                // then the coloured remainder growing up from the bottom.
                drawRect(Pc.Page)
                val filled = size.height * Repel.fraction(steps, duration)
                drawRect(
                    Repel.barColor(steps, duration),
                    topLeft = androidx.compose.ui.geometry.Offset(0f, size.height - filled),
                    size = androidx.compose.ui.geometry.Size(size.width, filled),
                )
            }
        }
    }
}
