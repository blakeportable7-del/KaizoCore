package com.ironmonone.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset

/**
 * Accuracy and evasion stages on the stats column (TrackerScreen.lua:1435-1448): in battle, once
 * either has moved from neutral, the BST row gives way to "Acc", a column of chevrons for each,
 * and "Eva". The tracker read both stages; nothing drew them.
 */
internal object StageChevrons {
    /** TrackerScreen.lua:1437: in battle, with accuracy or evasion off 6, neutral. */
    fun accEvaReplacesBst(inBattle: Boolean, acc: Int, eva: Int): Boolean = inBattle && (acc != 6 || eva != 6)

    /**
     * Drawing.drawChevronsVerticalIntensity (Drawing.lua:301-322) with [max] 3: a chevron per
     * stage up to three, pointing up for a raised stage and down for a lowered one, bottom first;
     * true where one takes the positive or negative colour, which the first ones do past [max].
     */
    fun of(intensity: Int, max: Int = 3): List<Boolean> {
        val weight = kotlin.math.abs(intensity)
        return (0 until max).filter { weight > it }.map { weight > max + it }
    }
}

/**
 * One column of the reference's chevrons for a stage [intensity] (-6 to 6): each 4 wide, 2 tall and
 * 1 thick, 2 apart (Drawing.drawChevron), in the default text colour, coloured past three.
 */
@Composable
internal fun PcStageChevrons(intensity: Int) {
    val marks = StageChevrons.of(intensity)
    val up = intensity > 0
    val color = if (up) Pc.Positive else Pc.Negative
    Canvas(Modifier.width(5.rp).height(9.rp)) {
        val u = size.width / 5f
        fun line(x1: Int, y1: Int, x2: Int, y2: Int, c: androidx.compose.ui.graphics.Color) =
            drawLine(c, Offset((x1 + 0.5f) * u, (y1 + 0.5f) * u), Offset((x2 + 0.5f) * u, (y2 + 0.5f) * u), strokeWidth = u)
        marks.forEachIndexed { k, colored ->
            val c = if (colored) color else Pc.Text
            // The reference's y + height + thickness + 1 (up) or y + thickness + 2 (down), 2 higher per chevron.
            if (up) { val foot = 7 - 2 * k; line(0, foot, 2, foot - 2, c); line(2, foot - 2, 4, foot, c) }
            else { val foot = 5 - 2 * k; line(0, foot, 2, foot + 2, c); line(2, foot + 2, 4, foot, c) }
        }
    }
}

/** The row in BST's place: "Acc", accuracy's chevrons, evasion's, "Eva" (TrackerScreen.lua:1439-1444). */
@Composable
internal fun PcAccEvaRow(acc: Int, eva: Int) {
    Row(Modifier.fillMaxWidth().height(10.rp).padding(start = 1.rp, end = 2.rp), verticalAlignment = Alignment.CenterVertically) {
        PixText("Acc", PcRef.FONT, Pc.Text)
        Spacer(Modifier.weight(1f))
        PcStageChevrons(acc - 6)
        Spacer(Modifier.width(2.rp))
        PcStageChevrons(eva - 6)
        Spacer(Modifier.weight(1f))
        PixText("Eva", PcRef.FONT, Pc.Text)
    }
}
