package com.ironmonone.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * The tracker's scrolling column, with a sign that there is more below (Blake, 2026-10-02, on whether the docked tracker
 * scrolls: it did, and nothing said so). A small arrow sits at the foot while the column can scroll further; a tap on
 * it scrolls most of a screen down. Only the arrow takes touches, so a swipe anywhere scrolls as before.
 */
@Composable
internal fun TrackerScroll(modifier: Modifier, background: Color? = Pc.Page, content: @Composable ColumnScope.() -> Unit) {
    // rc35's signature, kept: Play calls this, and one more parameter cost Play's method 2 bytes at the verifier's limit
    // (rc35.1). The floating window's choice is the overload below.
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    Box(modifier) {
        Column(
            Modifier.fillMaxSize().then(if (background != null) Modifier.background(background) else Modifier).verticalScroll(scroll),
            content = content,
        )
        if (scroll.canScrollForward) MoreBelow(Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp)) {
            scope.launch { scroll.animateScrollTo((scroll.value + scroll.viewportSize * 3 / 4).coerceAtMost(scroll.maxValue)) }
        }
    }
}

/**
 * [TrackerScroll] or, with [swipe] false, a column that takes no touch of its own, so a tap on its empty space reaches
 * what is under it (a locked, see-through floating window, FloatingSeeThrough.swipeScrolls). A swipe still scrolls it:
 * the activity tells a swipe from a tap before Compose sees the touch, and moves the column itself (WindowSwipe). No
 * arrows: Blake, 2026-10-04, on rc35.1's up and down arrows, "that arrow on tracker is really annoying".
 */
@Composable
internal fun TrackerScroll(modifier: Modifier, background: Color?, swipe: Boolean, content: @Composable ColumnScope.() -> Unit) {
    if (swipe) TrackerScroll(modifier, background, content) else SwipeColumn(modifier, background, content)
}

/**
 * The column with no scroll gesture: laid out at its full height, clipped to the box, and moved by [WindowSwipe] while it
 * is on screen. Nothing here takes a touch.
 */
@Composable
private fun SwipeColumn(modifier: Modifier, background: Color?, content: @Composable ColumnScope.() -> Unit) {
    var offset by remember { mutableIntStateOf(0) }
    var max by remember { mutableIntStateOf(0) }
    val target = remember { arrayOfNulls<WindowSwipe.Target>(1) }
    DisposableEffect(Unit) { onDispose { if (WindowSwipe.target === target[0]) WindowSwipe.target = null } }
    Column(
        modifier.then(if (background != null) Modifier.background(background) else Modifier).clipToBounds()
            .onGloballyPositioned { c ->
                val r = c.boundsInWindow()
                // A finger moving up shows what is below: the column moves the other way.
                val t = WindowSwipe.Target(r.left, r.top, r.right, r.bottom) { dy -> offset = (offset.coerceIn(0, max) - dy.roundToInt()).coerceIn(0, max) }
                target[0] = t
                WindowSwipe.target = t
            }
            .layout { measurable, c ->
                val p = measurable.measure(c.copy(minHeight = 0, maxHeight = Constraints.Infinity))
                val h = if (c.hasBoundedHeight) c.maxHeight else p.height
                val most = (p.height - h).coerceAtLeast(0)
                if (max != most) max = most
                layout(p.width, h) { p.place(0, -offset.coerceIn(0, most)) }
            },
        content = content,
    )
}

/** A down arrow on a dark disc, readable over any tracker theme or player image. */
@Composable
private fun MoreBelow(modifier: Modifier, onClick: () -> Unit) {
    // The tracker's touch box (PcMin.TOUCH_DP) around a smaller disc.
    Box(
        modifier.size(PcMin.TOUCH_DP.dp)
            .clickable(role = Role.Button) { onClick() }
            .semantics { contentDescription = "More below. Scroll down" },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(28.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.55f)), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(width = 12.dp, height = 7.dp)) {
                val w = 2.dp.toPx()
                drawLine(Color.White, Offset(0f, 0f), Offset(size.width / 2f, size.height), strokeWidth = w, cap = StrokeCap.Round)
                drawLine(Color.White, Offset(size.width, 0f), Offset(size.width / 2f, size.height), strokeWidth = w, cap = StrokeCap.Round)
            }
        }
    }
}
