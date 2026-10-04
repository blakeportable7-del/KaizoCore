package com.ironmonone.app

import android.graphics.RectF
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import com.swordfish.libretrodroid.GLRetroView

/**
 * The DS beside a docked tracker (Blake, 2026-10-02: "I want the top screen to be able to take up more space and the
 * bottom second screen to be below the tracker in tracker docked mode. ... It give the player a larger screen without
 * wasted space and can see the tracker, the main top screen and the bottom screen").
 *
 * melonDS's Hybrid Top draws the top screen large and the touch screen small at the foot of a column beside it, with
 * black above the small one: the "large empty black space" of his screenshot. Here the game takes the whole width and
 * its picture is drawn flush right (GLRetroView.viewport, which the view's touches map through unchanged), so the
 * touch screen sits in the bottom right corner and the tracker fills the box above it. The pad keeps to the left of
 * that column, so no button sits on the touch screen.
 */
object DsDock {
    /** The picture's viewport as fractions of the game view, and the tracker's box at its top right in pixels. */
    data class Geometry(
        val left: Float, val top: Float, val width: Float, val height: Float,
        val trackerW: Float, val trackerH: Float,
    )

    /** For a game view of [viewW] by [viewH] pixels and the core's hybrid [ratio]; null with no room to lay out. */
    fun geometry(viewW: Float, viewH: Float, ratio: Int): Geometry? {
        if (viewW <= 0f || viewH <= 0f) return null
        val r = ratio.coerceIn(2, 3)
        val f = ScreenTap.frame(ScreenTap.DsLayout("hybrid-top", hybridRatio = r))
        val touch = f.touch ?: return null
        val scale = minOf(viewW / f.width, viewH / f.height)
        val w = f.width * scale
        val h = f.height * scale
        val left = viewW - w
        val top = (viewH - h) / 2f
        // The column starts where the big screen ends, 256 x ratio across the frame.
        val column = left + 256f * r * scale
        return Geometry(left / viewW, top / viewH, w / viewW, h / viewH, trackerW = viewW - column, trackerH = top + touch.top * scale)
    }

    /** The game view's viewport for [g]; the whole view when the layout does not apply. */
    fun viewport(g: Geometry?): RectF = g?.let { RectF(it.left, it.top, it.left + it.width, it.top + it.height) } ?: RectF(0f, 0f, 1f, 1f)

    /**
     * The docked tracker's width in pixels while it is drawn over the play area, else 0; [dsDockIn] keeps it. The bars
     * across the top of the game, the File strip and the layout editor's, end at its left edge (dsDockClearance): they
     * ran on under it, and their last chips, MENU among them, could not be reached even scrolled to the end (Blake,
     * 2026-10-03: "You can't select the last buttons on this menu").
     */
    var coverPx by mutableFloatStateOf(0f)

    /** What [coverPx] is for a layout of [g]: its tracker's width, or nothing with no dock. */
    fun cover(g: Geometry?): Float = g?.trackerW ?: 0f
}

/** The room a bar across the top of the play area leaves at its right end for a docked DS tracker (DsDock.coverPx). */
@Composable
internal fun dsDockClearance(): Dp = with(LocalDensity.current) { DsDock.coverPx.toDp() }

/**
 * The docked DS layout for the game in Play, or null where it does not apply: [base] holds what PlayScreen knows (a
 * tracked DS game, Hybrid Top, full screen landscape, the tracker on this display), and the tracker must be docked and
 * open. Sets the game view's viewport to match, and back to the whole view when it stops applying.
 */
@Composable
internal fun dsDockIn(retro: GLRetroView?, ui: PlayUiState, base: Boolean, trackerOpen: Boolean, gameColumn: IntSize, coreValues: Map<String, String>): DsDock.Geometry? {
    val docked = TrackerOptions.landscapeTracker != LandscapeTracker.FLOATING &&
        trackerOpen && (TrackerOptions.landscapeTracker == LandscapeTracker.DOCKED || ui.trackerPeek)
    // "Top" in the small column leaves the touch screen out of it.
    val g = if (base && docked && coreValues["melonds_hybrid_small_screen"] != "Top")
        DsDock.geometry(gameColumn.width.toFloat(), gameColumn.height.toFloat(), coreValues["melonds_hybrid_ratio"]?.toIntOrNull() ?: 2)
    else null
    SideEffect {
        val want = DsDock.viewport(g)
        if (retro != null && retro.viewport != want) retro.viewport = want
        DsDock.coverPx = DsDock.cover(g)
    }
    // Leaving Play takes the dock with it, so no bar elsewhere keeps the room.
    DisposableEffect(Unit) { onDispose { DsDock.coverPx = 0f } }
    return g
}

/** The pad kept to the left of the tracker's column, off the touch screen below it. */
@Composable
internal fun Modifier.clearOfDsDock(g: DsDock.Geometry?): Modifier =
    if (g == null) this else this.padding(end = with(LocalDensity.current) { g.trackerW.toDp() })

/** The tracker in the box above the touch screen; short, so one card at a time with SEE FOE (TrackerRoom). */
@Composable
internal fun DsDockTracker(g: DsDock.Geometry, modifier: Modifier, content: @Composable () -> Unit) {
    val d = LocalDensity.current
    val w = with(d) { g.trackerW.toDp() }
    val h = with(d) { g.trackerH.toDp() }
    TrackerScroll(modifier.size(w, h)) {
        CompositionLocalProvider(LocalTrackerRoom provides h) { content() }
    }
}
