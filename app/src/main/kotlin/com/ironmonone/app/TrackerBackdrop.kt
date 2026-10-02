package com.ironmonone.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.ceil

/**
 * The tracker's main background (2026-09-29): the Main background colour and, over it, the player's image
 * dimmed ([TrackerBackground]). Every place that used to paint the colour behind the tracker paints this
 * instead: the two panels, the floating window and the second display.
 *
 * True inside a host that has already painted it, the floating window and the second display, so the panel
 * in them draws nothing of its own and the image is one picture behind the whole window, not one per panel.
 * PlayScreen.kt paints the colour behind the docked landscape pane itself and is not touched (it is at the
 * limit of what ART verifies), so that pane shows the colour above and below the panel, where the image
 * covers only the panel's own box.
 */
val LocalBackdropHosted = compositionLocalOf { false }

/**
 * True for the tracker drawn on the second screen (SecondScreen's presentation window). A Compose Dialog opened from
 * there has no window token on Android 12 and later and crashes the app, so nothing on that screen may open one by
 * itself (rc33 audit P0-7: the starter-ball info did, from game state, where the touch block could not stop it).
 */
val LocalOnSecondScreen = androidx.compose.runtime.staticCompositionLocalOf { false }

/** One draw of the backdrop into the box being drawn. The plan is [TrackerBackground.plan]; this only runs it. */
fun DrawScope.paintBackdrop() {
    val img = TrackerBackground.image
    val ops = TrackerBackground.plan(
        Pc.Page, img?.width ?: 0, img?.height ?: 0,
        ceil(size.width).toInt(), ceil(size.height).toInt(), TrackerBackground.fit, TrackerBackground.dim,
    )
    for (op in ops) when (op) {
        is TrackerBackground.Op.Colour -> drawRect(op.color)
        is TrackerBackground.Op.Picture -> img?.let {
            val p = op.placement
            drawImage(
                it, IntOffset(p.srcX, p.srcY), IntSize(p.srcW, p.srcH),
                IntOffset(p.dstX, p.dstY), IntSize(p.dstW, p.dstH), filterQuality = FilterQuality.Medium,
            )
        }
        is TrackerBackground.Op.Dim -> drawRect(
            Color.Black.copy(alpha = op.alpha), Offset(op.x.toFloat(), op.y.toFloat()), Size(op.w.toFloat(), op.h.toFloat()),
        )
    }
}

private val paintBackdropBehind: DrawScope.() -> Unit = { paintBackdrop() }

/** The panels' background: the backdrop, unless a host already paints it behind them. */
@Composable
fun trackerBackdrop(): Modifier =
    if (LocalBackdropHosted.current) Modifier else Modifier.drawBehind(paintBackdropBehind)

/** A host's background, for a window that holds panels: the backdrop, painted once behind all of it. */
fun Modifier.hostBackdrop(): Modifier = drawBehind(paintBackdropBehind)
