package com.ironmonone.app

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/** The floating window's frame, in dp: the window as drawn, title bar and all. Persisted per game with the other play settings. */
data class FloatFrame(val x: Float, val y: Float, val w: Float, val h: Float) {
    companion object {
        /** As small as it will go: the title bar and a few rows. Blake, 2026-10-02: "make it any size". */
        const val MIN_W = 160f
        const val MIN_H = 120f
        /** Top right, a third of the width, most of the height: where the dock would have been. */
        fun default(windowW: Float, windowH: Float): FloatFrame {
            val w = (windowW * 0.32f).coerceIn(MIN_W, 360f)
            val h = (windowH - 24f).coerceAtLeast(MIN_H)
            return FloatFrame(windowW - w - 8f, 12f, w, h)
        }
    }

    /**
     * On screen, and never the whole width or height: a strip of [FloatGrabs.OUT] is left beside it and under it, so the
     * corner's resize button, which sits half outside the corner, can always be reached. It still moves flush to every edge.
     */
    fun clamped(windowW: Float, windowH: Float): FloatFrame {
        val w = w.coerceIn(MIN_W, maxOf(MIN_W, windowW - FloatGrabs.OUT))
        val h = h.coerceIn(MIN_H, maxOf(MIN_H, windowH - FloatGrabs.OUT))
        return FloatFrame(x.coerceIn(0f, (windowW - w).coerceAtLeast(0f)), y.coerceIn(0f, (windowH - h).coerceAtLeast(0f)), w, h)
    }
}

/**
 * The room round the window, in dp (Blake, 2026-10-03: "shave off the buffer space around that tracker and bring the edge
 * to the lines"): the window's edge meets the tracker's boxes, with [IN] of margin, and [OUT] is kept free beside and
 * under it for the corner's resize button (Blake, 2026-10-05: "a long hold would show the floating tracker expand corner
 * button").
 */
internal object FloatGrabs {
    /** How far the corner button reaches past the edge, and the strip kept free for it. */
    const val OUT = 18f
    /** The window's margin (FLOAT_MARGIN), short of the boxes' lines. */
    const val IN = 2f
    /** The corner button: a thumb's target. */
    const val CORNER = 44f

    /** The corner button's top left, in dp, for the window at [f] drawn [drawnH] tall in an [areaW] by [areaH] box. */
    fun corner(f: FloatFrame, drawnH: Float, areaW: Float, areaH: Float): Pair<Float, Float> {
        val x = (f.x + f.w - CORNER / 2f - 4f).coerceIn(0f, (areaW - CORNER).coerceAtLeast(0f))
        val y = (f.y + drawnH - CORNER / 2f - 4f).coerceIn(0f, (areaH - CORNER).coerceAtLeast(0f))
        return x to y
    }
}

/**
 * The room the tracker's panels leave round their boxes (their PcRef.MARGIN), where something narrower is wanted; null
 * keeps the panel's own. Only the floating window sets it: docked, in portrait and on a second display nothing changes.
 */
val LocalTrackerMargin = compositionLocalOf<PaddingValues?> { null }

/** The room a panel leaves round its boxes: its own PcRef.MARGIN, or the floating window's (LocalTrackerMargin). */
@Composable
internal fun trackerMargin(): PaddingValues = LocalTrackerMargin.current ?: PaddingValues(PcRef.MARGIN.rp)

/** The floating window's margin: the boxes' lines 1 dp inside its border (Blake, 2026-10-03: "bring the edge to the lines"). */
private val FLOAT_MARGIN = PaddingValues(FloatGrabs.IN.dp)

/**
 * Lays a button out at its full touch box but takes only the row's height: the box reaches past the slim row above and
 * below, centred on it, so the row is drawn thin and every target stays [PcMin.TOUCH_DP] tall.
 */
private fun Modifier.overhang(): Modifier = layout { measurable, c ->
    val p = measurable.measure(c.copy(minHeight = 0, maxHeight = Constraints.Infinity))
    val h = if (c.hasBoundedHeight) minOf(p.height, c.maxHeight) else p.height
    layout(p.width, h) { p.place(0, (h - p.height) / 2) }
}

/**
 * 2.2: the tracker as a window over the game in landscape. It carries no buttons of its own (Blake, 2026-10-05:
 * "completely removing the gear and the hamburger menu buttons off the tracker", "remove the lock, a long hold would lock
 * in place, and a long hold would show the floating tracker expand corner button"). It is locked whenever it is shown,
 * and its rows take their taps as they do docked. Hold it half a second (FloatHold) and it unlocks, with a ring and a
 * buzz: a drag anywhere on it moves it, the corner button resizes it, a double tap puts it back where the dock would be.
 * Hold again to lock it; opening the FILE bar locks it, and so do six seconds untouched. While the bar is open it steps
 * down below it, and goes back when the bar closes.
 *
 * It moves inside the box it is drawn in, measured here. It used to be clamped to LocalConfiguration's screen size,
 * which leaves the system bars out while the play screen draws edge to edge, so it stopped short of the right edge
 * ("I can't move it all the way to the screen's edge").
 */
@Composable
fun FloatingTracker(
    frame: FloatFrame,
    @Suppress("UNUSED_PARAMETER") windowW: Float,
    @Suppress("UNUSED_PARAMETER") windowH: Float,
    onFrame: (FloatFrame) -> Unit,
    /** The run's attempt: the title reads it in a Kaizo IronMON run (Blake, 2026-10-02: "Replace 'Tracker' with the attempt and number"). */
    attempt: Int = 0,
    content: @Composable () -> Unit,
) {
    if (TrackerHud.ENABLED && TrackerOptions.trackerHud) {
        // The Tracker HUD rides on this path, so Play lays the game out as for the window: never moved, never smaller.
        TrackerHudLayer(attempt = attempt, content = content)
        return
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val areaW = maxWidth.value
        val areaH = maxHeight.value
        val density = LocalDensity.current.density
        // The gesture blocks outlive a recomposition, so they read the frame
        // through this, not the `frame` captured when the drag began: that stale
        // copy made every move start from the old spot and the window jittered
        // instead of following the finger (2026-09-27, audit).
        val live by rememberUpdatedState(frame.clamped(areaW, areaH))
        val locked = FileBar.floatLocked
        val haptics = LocalHapticFeedback.current
        // Where the box the window moves in sits in the activity's window, for FloatHold's pixels.
        var areaOrigin by remember { mutableStateOf(Offset.Zero) }
        Box(Modifier.size(0.dp).onGloballyPositioned { areaOrigin = it.boundsInWindow().topLeft })
        // The ring under a holding finger, in this box's pixels.
        var ring by remember { mutableStateOf<Offset?>(null) }
        fun touched() { FileBar.floatTouchedAt = android.os.SystemClock.uptimeMillis() }
        DisposableEffect(Unit) {
            FileBar.floatLocked = true
            onDispose { FloatHold.bounds = null; FloatHold.skip = null; FloatHold.onHold = null; FloatHold.onPress = null; FloatHold.onRelease = null }
        }
        SideEffect {
            FloatHold.onHold = {
                FileBar.floatLocked = FloatLockRules.afterHold(FileBar.floatLocked)
                touched()
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                FileBar.say(if (FileBar.floatLocked) FloatLockRules.LOCKED else FloatLockRules.UNLOCKED)
            }
            FloatHold.onPress = { x, y -> ring = Offset(x - areaOrigin.x, y - areaOrigin.y); touched() }
            FloatHold.onRelease = { ring = null; touched() }
        }
        // The bar's taps are the bar's: an unlocked window locks when it opens.
        LaunchedEffect(FileBar.open) {
            if (FloatLockRules.locksForBar(FileBar.open, FileBar.floatLocked)) { FileBar.floatLocked = true; FileBar.say(FloatLockRules.LOCKED_FOR_BAR) }
        }
        // Six seconds untouched, unlocked: it locks itself.
        LaunchedEffect(locked, FileBar.floatTouchedAt) {
            if (locked) return@LaunchedEffect
            val now = android.os.SystemClock.uptimeMillis()
            kotlinx.coroutines.delay((FloatHold.IDLE_MS - (now - FileBar.floatTouchedAt)).coerceAtLeast(0L))
            if (FloatLockRules.idleLocks(FileBar.floatLocked, FileBar.floatTouchedAt, android.os.SystemClock.uptimeMillis())) {
                FileBar.floatLocked = true; FileBar.say(FloatLockRules.LOCKED_IDLE)
            }
        }
        // See-through (FloatingSeeThrough): the fills fade, the words take an outline, and locked, the empty space
        // takes no touch, so a tap there reaches the game.
        val solid = TrackerOptions.floatingSolid
        val fade = FloatingSeeThrough.fade(solid)
        val outline = if (FloatingSeeThrough.seeThrough(solid)) trackerTextOutline(2.5f * density) else null
        // The panel's first row, handed up to the bar (WindowBar.kt).
        val barSlot = remember { WindowBarSlot() }
        val parts = barSlot.parts
        val barDp = WindowBarFit.ROW_DP
        fun move(f: FloatFrame) = onFrame(f.clamped(areaW, areaH))
        // The tracker's own height, as last laid out. The window fits it: the height the player sets is the most it
        // may take, and blank space under the cards is not drawn (Blake, 2026-10-02: "Wasted space").
        var contentH by remember { mutableStateOf(0f) }
        val fitH = if (contentH > 0f) (barDp + contentH).coerceIn(FloatFrame.MIN_H, live.h) else live.h
        // Below the FILE bar while it is open, and back where it was when it closes.
        val shown = if (FileBar.open) live.copy(y = maxOf(live.y, minOf(FileBar.UNDER_DP.toFloat(), areaH - fitH))) else live
        var lastTap by remember { mutableStateOf(0L) }
        Box(
            Modifier
                .offset { IntOffset((shown.x * density).roundToInt(), (shown.y * density).roundToInt()) }
                .size(shown.w.dp, fitH.dp)
                .border(if (locked) 1.dp else 2.dp, if (locked) Pc.Border else TrackerHud.CYAN)
                .onGloballyPositioned { c ->
                    val r = c.boundsInWindow()
                    FloatHold.bounds = TapZone.Rect(r.left, r.top, r.right, r.bottom)
                }
                // The hold has no target of its own, so a screen reader is given it as an action.
                .semantics {
                    stateDescription = if (locked) "Locked" else "Unlocked"
                    customActions = listOf(CustomAccessibilityAction(if (locked) "Unlock to move and resize" else "Lock in place") {
                        FileBar.floatLocked = !locked; touched(); true
                    })
                },
        ) {
            // The Main background colour and the player's image, once behind the whole window; the panel inside it
            // paints nothing of its own (TrackerBackdrop.kt). Its own layer, so see-through fades it alone.
            Box(Modifier.matchParentSize().graphicsLayer { alpha = fade }.hostBackdrop())
            CompositionLocalProvider(LocalWindowFade provides fade, LocalTrackerTextShadow provides outline, LocalWindowBar provides barSlot) {
            Column(Modifier.fillMaxSize()) {
                // One row (Blake, 2026-10-04, "less bulky"): the panel's first row, the battle banner or the area's
                // bar, is drawn here (WindowBar.kt): the text slot and, in a battle, the swap.
                Row(Modifier.fillMaxWidth().height(barDp.dp).background(windowFill(Pc.Ground)), verticalAlignment = Alignment.CenterVertically) {
                    val segs = WindowBarText.withAttempt(attempt.takeIf { ironmonRunInPlay(attempt) }, parts?.segments ?: emptyList())
                        .ifEmpty { listOf(BarSegment("TRACKER", Pc.Dim, 0)) }
                    Box(Modifier.weight(1f).padding(start = 6.dp, end = 2.dp).overhang()) {
                        WindowBarTextSlot(segs, parts?.onTextTap, parts?.tapLabel, Modifier.fillMaxWidth())
                    }
                    parts?.extra?.invoke()
                    // A long press on the swap shows its words, so the window's hold leaves it alone (FloatHold.skip).
                    parts?.swap?.let {
                        Box(Modifier.overhang().onGloballyPositioned { c -> val r = c.boundsInWindow(); FloatHold.skip = TapZone.Rect(r.left, r.top, r.right, r.bottom) }) {
                            SwapIconButton(it)
                        }
                    } ?: SideEffect { FloatHold.skip = null }
                }
                // The room is the height the player gave the window, not the fitted one: fitted to one card it would
                // never again have room for two.
                val room = (live.h - barDp).coerceAtLeast(0f).dp
                // Wide enough for columns (TrackerWideView): both cards side by side, so the room is no reason to swap.
                val wideView = TrackerWideView.applies(live.w, room.value)
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    TrackerScroll(Modifier.fillMaxSize(), background = null, swipe = FloatingSeeThrough.swipeScrolls(locked, solid)) {
                        Column(Modifier.fillMaxWidth().onSizeChanged { contentH = it.height / density }) {
                            CompositionLocalProvider(
                                LocalBackdropHosted provides true, LocalTrackerRoom provides room.takeUnless { wideView },
                                LocalAttemptInTitle provides true, LocalTrackerMargin provides FLOAT_MARGIN, LocalTrackerWideView provides wideView,
                            ) { content() }
                        }
                    }
                }
            }
            }
            if (!locked) {
                // Unlocked, the window is a thing to move: every touch on it is the drag's, and a double tap puts it back
                // where the dock would be. Its rows take no taps until it is locked again.
                Box(
                    Modifier.matchParentSize().background(Pc.Ground.copy(alpha = 0.25f))
                        .pointerInput(areaW, areaH) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                down.consume()
                                touched()
                                var moved = false
                                while (true) {
                                    val ev = awaitPointerEvent()
                                    val c = ev.changes.firstOrNull { it.id == down.id } ?: break
                                    if (!c.pressed) { c.consume(); break }
                                    if (!moved && (c.position - down.position).getDistance() > viewConfiguration.touchSlop) moved = true
                                    if (moved) {
                                        val d = c.positionChange()
                                        move(live.copy(x = live.x + d.x / density, y = live.y + d.y / density))
                                        touched()
                                    }
                                    c.consume()
                                }
                                if (!moved) {
                                    val now = android.os.SystemClock.uptimeMillis()
                                    if (now - lastTap < 300L) { move(FloatFrame.default(areaW, areaH)); lastTap = 0L } else lastTap = now
                                }
                            }
                        },
                )
            }
        }
        if (!locked) {
            // What the hold did, said where the finger is not: over the window, or inside its top when it is at the top.
            val above = shown.y >= 40f
            Text(
                FloatLockRules.TIP, color = Color.Black, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .offset { IntOffset(((shown.x + 8f) * density).roundToInt(), ((if (above) shown.y - 34f else shown.y + barDp + 4f) * density).roundToInt()) }
                    .widthIn(max = (shown.w - 16f).coerceAtLeast(120f).dp)
                    .background(TrackerHud.CYAN).padding(horizontal = 8.dp, vertical = 5.dp),
            )
            // The corner button: pull it to resize.
            val (gx, gy) = FloatGrabs.corner(shown, fitH, areaW, areaH)
            Box(
                Modifier.offset { IntOffset((gx * density).roundToInt(), (gy * density).roundToInt()) }.size(FloatGrabs.CORNER.dp)
                    .clip(CircleShape).background(TrackerHud.CYAN)
                    .semantics { contentDescription = "Drag to resize" }
                    .pointerInput(areaW, areaH) {
                        detectDragGestures { change, drag ->
                            change.consume()
                            touched()
                            // It grows to the box's edge and stops there: past it, clamping would push the window off its place.
                            move(live.copy(w = (live.w + drag.x / density).coerceAtMost(areaW - FloatGrabs.OUT - live.x),
                                h = (live.h + drag.y / density).coerceAtMost(areaH - FloatGrabs.OUT - live.y)))
                        }
                    },
                contentAlignment = Alignment.Center,
            ) { CornerMark() }
        }
        ring?.let { p ->
            key(p) {
                val progress = remember { Animatable(0f) }
                LaunchedEffect(Unit) {
                    kotlinx.coroutines.delay(FloatHold.RING_AFTER_MS)
                    progress.animateTo(1f, tween((FloatHold.HOLD_MS - FloatHold.RING_AFTER_MS).toInt(), easing = LinearEasing))
                }
                Canvas(Modifier.offset { IntOffset((p.x - 30.dp.toPx()).roundToInt(), (p.y - 30.dp.toPx()).roundToInt()) }.size(60.dp)) {
                    if (progress.value <= 0f) return@Canvas
                    val st = 4.dp.toPx()
                    drawCircle(Color.Black.copy(alpha = 0.45f), radius = size.minDimension / 2 - st, style = Stroke(st))
                    drawArc(TrackerHud.CYAN, -90f, 360f * progress.value, false, style = Stroke(st, cap = StrokeCap.Round),
                        topLeft = Offset(st, st), size = androidx.compose.ui.geometry.Size(size.width - 2 * st, size.height - 2 * st))
                }
            }
        }
    }
}

/**
 * The window placed by Play's [panes] (rc32 audit P2 #46, #56): the frame is read here and not in Play's body, so a drag
 * recomposes the window alone, and where it was moved or sized to outlives a rotation.
 */
@Composable
internal fun FloatingTracker(
    panes: PaneSizes,
    windowW: Float,
    windowH: Float,
    attempt: Int = 0,
    content: @Composable () -> Unit,
) = FloatingTracker(panes.frameFor(windowW, windowH), windowW, windowH, onFrame = { panes.frame = it }, attempt = attempt, content = content)

/** Two arrows out of the corner, dark on the cyan button. */
@Composable
private fun CornerMark() {
    Canvas(Modifier.size(22.dp)) {
        val s = size.minDimension
        val st = 2.5.dp.toPx()
        val c = Color(0xFF08141C)
        drawLine(c, Offset(s * 0.2f, s * 0.8f), Offset(s * 0.8f, s * 0.2f), st, StrokeCap.Round)
        drawLine(c, Offset(s * 0.8f, s * 0.2f), Offset(s * 0.45f, s * 0.2f), st, StrokeCap.Round)
        drawLine(c, Offset(s * 0.8f, s * 0.2f), Offset(s * 0.8f, s * 0.55f), st, StrokeCap.Round)
        drawLine(c, Offset(s * 0.2f, s * 0.8f), Offset(s * 0.55f, s * 0.8f), st, StrokeCap.Round)
        drawLine(c, Offset(s * 0.2f, s * 0.8f), Offset(s * 0.2f, s * 0.45f), st, StrokeCap.Round)
    }
}
