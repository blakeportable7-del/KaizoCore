package com.ironmonone.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * The carousel's BATTLE_DETAILS item (TrackerScreen.lua:769-787): the viewed battler's
 * BattleDetailsScreen summary, the first battle detail that concerns it
 * (BattleDetailsScreen.summarizeDetails), shown only while there is one. The line used to
 * read the weather and how often the opponent had been seen, which the reference never shows
 * there, and the summary the tracker already built went unused.
 */
internal object BattleSummary {
    /**
     * Battle.getViewedIndex (Battle.lua:243-254) in a single battle: 0 your battler, 1 the opponent's. A double
     * battle's right-hand battlers are 2 and 3, which GbaViewState.viewedBattler names.
     */
    fun viewedIndex(viewingOwn: Boolean): Int = if (viewingOwn) 0 else 1

    /** The line's text for the viewed battler, or null when it has nothing to show (BattleDetailsScreen.hasDetails). */
    fun line(summaries: List<String>, viewingOwn: Boolean): String? = line(summaries, viewedIndex(viewingOwn))

    /** The same for battler [battler], 0 to 3. */
    fun line(summaries: List<String>, battler: Int): String? =
        summaries.getOrNull(battler)?.takeIf { it.isNotBlank() }
}

/** Constants.PixelImages.SPARKLES, 12x12: TrackerScreen.Buttons.BattleDetailsSummary's icon. */
private val SPARKLES = listOf(
    "000000000000", "001000010000", "010100010000", "001000101000", "000011000110", "100000101000",
    "000000010000", "000100010000", "000100000000", "011011000100", "000100000000", "000100000000",
)

/** TrackerScreen.Buttons.BattleDetailsSummary: the sparkles and the summary; a tap opens Battle Details. */
@Composable
internal fun PcBattleSummaryLine(text: String, onTap: (() -> Unit)?) {
    // Through boxFill like every other tracker box, so a picture behind the tracker shows through it (rc32 audit P3 #21).
    Row(
        Modifier.fillMaxWidth().background(TrackerBackground.boxFill(Pc.LowerGroundX ?: Pc.Ground)).border(1.dp, Pc.LowerBorder)
            .then(if (onTap != null) Modifier.clickable { onTap() } else Modifier)
            .padding(horizontal = 6.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PcPixelImage(SPARKLES, Pc.LowerText)
        Spacer(Modifier.width(6.dp))
        PixText(text, 8, Pc.LowerText)
    }
}
