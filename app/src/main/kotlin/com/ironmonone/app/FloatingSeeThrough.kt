package com.ironmonone.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow

/**
 * The floating window's see-through mode (Blake, 2026-10-04: "can the tracker have a transparent mode? so you can see
 * all the important stuff but can also see the game screen behind it?"). Tracker Setup's "Window see-through" slider sets
 * how solid the window's fills are, [MIN] to [SOLID] percent in steps of [STEP]; [SOLID] is the default and changes
 * nothing. Only the fills fade: the window's background and image, the box fills and the title bar. Text, numbers,
 * sprites, icons, type pills, rule marks, HP bars, borders and buttons stay as they are, and the text takes a dark
 * outline so it reads over a bright game. Docked, portrait and the second display never set it.
 */
object FloatingSeeThrough {
    const val SOLID = 100
    const val MIN = 30
    const val STEP = 10

    fun clamp(percent: Int): Int = (Math.round(percent / STEP.toFloat()) * STEP).coerceIn(MIN, SOLID)

    /** The alpha the window's fills are multiplied by. */
    fun fade(percent: Int): Float = clamp(percent) / 100f

    fun seeThrough(percent: Int): Boolean = clamp(percent) < SOLID

    /**
     * Whether a tap on the window's empty space reaches the game and its on-screen controls beneath: only while it is
     * see-through and locked. The tracker's own buttons and tappable rows always keep their taps ([interactive]), and an
     * unlocked window keeps every touch, to be moved and sized.
     */
    fun passesThrough(locked: Boolean, percent: Int, interactive: Boolean): Boolean =
        locked && seeThrough(percent) && !interactive

    /**
     * Whether a swipe on the window scrolls it. A scrolling column takes every touch on it, so while taps pass through
     * the column scrolls by its arrows instead (TrackerScroll's swipe = false).
     */
    fun swipeScrolls(locked: Boolean, percent: Int): Boolean = !passesThrough(locked, percent, interactive = false)

    /** Said under the slider. */
    const val NOTE = "Locked and see-through, taps on empty parts of the window go to the game."
}

/** How solid the tracker's fills are drawn here: 1 everywhere but a see-through floating window. */
val LocalWindowFade = compositionLocalOf { 1f }

/** The outline the tracker's text takes over a see-through window; null, as everywhere else, draws none. */
val LocalTrackerTextShadow = compositionLocalOf<Shadow?> { null }

/** A dark halo round each letter, [blurPx] wide: reads as an outline over any game picture. */
fun trackerTextOutline(blurPx: Float): Shadow = Shadow(Color.Black, Offset.Zero, blurPx)

/** A tracker box's fill (TrackerBackground.boxFill), faded with the window it is in. */
@Composable
fun trackerBoxFill(c: Color): Color = windowFill(TrackerBackground.boxFill(c))

/** A fill faded with the window it is in, and nothing else: [c] as it is outside a see-through window. */
@Composable
fun windowFill(c: Color): Color = faded(c, LocalWindowFade.current)

/** [c] with its alpha multiplied by [fade]; at 1 or more, [c] itself. */
fun faded(c: Color, fade: Float): Color = if (fade >= 1f) c else c.copy(alpha = c.alpha * fade)
