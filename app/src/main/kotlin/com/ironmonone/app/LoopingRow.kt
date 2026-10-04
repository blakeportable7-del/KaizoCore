package com.ironmonone.app

import android.view.accessibility.AccessibilityManager
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp

/**
 * A row of chips that goes round (Blake, 2026-10-03, on the File strip and the layout editor's bar: "They both should
 * have infinite scroll"). Past the last chip the first comes around again, and back past the first the last, for as
 * long as the finger goes. The row is a LazyRow of [COUNT] copies of the chips, opened at the [MIDDLE] one, so a fling
 * never meets an end and nothing jumps; every copy is the same chips, so each works from any copy.
 *
 * Where one copy fits, it is drawn once and nothing scrolls. With a screen reader on it is drawn once too and scrolls
 * to its ends, so TalkBack reads the chips once instead of an endless list.
 */
internal object LoopRow {
    /** Copies of the chips: millions of dp of row either way from the middle, which no thumb gets through. */
    const val COUNT = 20_000

    /** Where it opens: the start of a copy at the row's left edge. */
    const val MIDDLE = COUNT / 2

    /** Whether the row goes round: only when one copy is wider than its window, and never for a screen reader. */
    fun loops(rowPx: Int, windowPx: Int, reader: Boolean): Boolean = !reader && windowPx > 0 && rowPx > windowPx

    /** How many copies are drawn. */
    fun copies(loops: Boolean): Int = if (loops) COUNT else 1

    /** The first copy shown: the middle one while it goes round, else the only one. */
    fun start(loops: Boolean): Int = if (loops) MIDDLE else 0
}

/** One looping row's scroll and sizes, hoisted so the File strip's arrows can move it. */
@Stable
internal class LoopRowState(private val readerOn: State<Boolean>) {
    val list = LazyListState()

    /** One copy's width and the row's window, in pixels, as last laid out. */
    var rowPx by mutableIntStateOf(0)
        internal set
    var windowPx by mutableIntStateOf(0)
        internal set

    val loops: Boolean get() = LoopRow.loops(rowPx, windowPx, readerOn.value)

    /** Something is out of sight that way: going round, there always is. */
    val canBack: Boolean get() = loops || list.canScrollBackward
    val canForward: Boolean get() = loops || list.canScrollForward
    val overflows: Boolean get() = canBack || canForward

    /** Most of a window that way, for an arrow; going round it carries on into the next copy. */
    suspend fun page(forward: Boolean) {
        val step = windowPx * 0.6f
        list.animateScrollBy(if (forward) step else -step)
    }
}

@Composable
internal fun rememberLoopRowState(): LoopRowState {
    val reader = rememberScreenReaderOn()
    return remember(reader) { LoopRowState(reader) }
}

/**
 * Whether a screen reader is exploring the screen by touch (TalkBack), kept up to date while it is turned on or off.
 */
@Composable
internal fun rememberScreenReaderOn(): State<Boolean> {
    val context = LocalContext.current
    val manager = remember(context) { context.getSystemService(AccessibilityManager::class.java) }
    val on = remember(manager) { mutableStateOf(manager?.isTouchExplorationEnabled == true) }
    DisposableEffect(manager) {
        val listener = AccessibilityManager.TouchExplorationStateChangeListener { on.value = it }
        manager?.addTouchExplorationStateChangeListener(listener)
        onDispose { manager?.removeTouchExplorationStateChangeListener(listener) }
    }
    return on
}

/**
 * The chips in [content], [gap] apart, going round once they are wider than the row (see [LoopRow]). A copy that fits is
 * drawn once, in the middle of the row if [center].
 */
@Composable
internal fun LoopingRow(
    state: LoopRowState,
    modifier: Modifier = Modifier,
    gap: Dp,
    center: Boolean = false,
    content: @Composable RowScope.() -> Unit,
) {
    BoxWithConstraints(modifier) {
        val window = maxWidth
        val loops = state.loops
        // Into the middle when it starts going round, back to the only copy when it stops.
        LaunchedEffect(loops) { state.list.scrollToItem(LoopRow.start(loops)) }
        LazyRow(
            Modifier.fillMaxWidth().onSizeChanged { state.windowPx = it.width },
            state = state.list,
            horizontalArrangement = Arrangement.spacedBy(gap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items(LoopRow.copies(loops)) {
                Row(
                    // The only copy is as wide as the row at least, so a short one can sit in the middle of it.
                    if (loops) Modifier else Modifier.widthIn(min = window),
                    horizontalArrangement = if (center) Arrangement.Center else Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Measured at its own width (a lazy row's items have no limit across), so it says when it no longer fits.
                    Row(
                        Modifier.onSizeChanged { state.rowPx = it.width },
                        horizontalArrangement = Arrangement.spacedBy(gap),
                        verticalAlignment = Alignment.CenterVertically,
                        content = content,
                    )
                }
            }
        }
    }
}
