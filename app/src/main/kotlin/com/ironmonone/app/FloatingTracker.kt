package com.ironmonone.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
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
     * On screen, and never the whole width or height: the resize grabs sit outside the window's edge (FloatGrabs), so a
     * strip of [FloatGrabs.OUT] is left beside it and under it, and however it is moved one grab of each kind can be
     * reached. It still moves flush to every edge.
     */
    fun clamped(windowW: Float, windowH: Float): FloatFrame {
        val w = w.coerceIn(MIN_W, maxOf(MIN_W, windowW - FloatGrabs.OUT))
        val h = h.coerceIn(MIN_H, maxOf(MIN_H, windowH - FloatGrabs.OUT))
        return FloatFrame(x.coerceIn(0f, (windowW - w).coerceAtLeast(0f)), y.coerceIn(0f, (windowH - h).coerceAtLeast(0f)), w, h)
    }

    /** Dragged by its left edge: the right edge stays where it is, and the window grows no wider than [maxW]. */
    fun leftEdge(dx: Float, maxW: Float = Float.MAX_VALUE): FloatFrame {
        val right = x + w
        val nx = (x + dx).coerceIn(maxOf(0f, right - maxOf(maxW, MIN_W)), right - MIN_W)
        return copy(x = nx, w = right - nx)
    }
}

/**
 * Where a finger resizes the window, in dp (Blake, 2026-10-03: "shave off the buffer space around that tracker and bring
 * the edge to the lines"). The window used to keep a 16 dp border of its own inside its edge to resize by, so no grab sat
 * over SETUP or the corner arrow; its edge now meets the tracker's boxes, and the grabs reach OUT past the edge instead,
 * over the game, and IN only over the window's own margin, which holds no button. Each is cut to the box the window
 * moves in, and one with less than [MIN] left is drawn at no size, so it takes no new touch but a drag already on it
 * carries on.
 */
internal object FloatGrabs {
    /** How far a side's or the bottom's grab reaches past the edge. */
    const val OUT = 18f
    /** How far it reaches in: the window's margin (FLOAT_MARGIN), short of the boxes' lines. */
    const val IN = 2f
    /** How far a bottom corner's grab reaches out both ways: more than a side's, so a corner is easy to catch. */
    const val CORNER = 26f
    /** Less than this left of a grab is no grab. */
    const val MIN = 4f

    data class Rect(val x: Float, val y: Float, val w: Float, val h: Float) {
        val usable: Boolean get() = w > 0f && h > 0f
    }

    data class Grabs(val left: Rect, val right: Rect, val bottom: Rect, val bottomLeft: Rect, val bottomRight: Rect)

    /** For the window at [f], drawn [drawnH] tall with a title bar [bar] tall (a drag there moves it), in an [areaW] by [areaH] box. */
    fun of(f: FloatFrame, drawnH: Float, bar: Float, areaW: Float, areaH: Float): Grabs {
        val bottom = f.y + drawnH
        val right = f.x + f.w
        fun cut(x0: Float, y0: Float, x1: Float, y1: Float): Rect {
            val l = x0.coerceIn(0f, areaW); val r = x1.coerceIn(0f, areaW)
            val t = y0.coerceIn(0f, areaH); val b = y1.coerceIn(0f, areaH)
            return if (r - l < MIN || b - t < MIN) Rect(l, t, 0f, 0f) else Rect(l, t, r - l, b - t)
        }
        return Grabs(
            left = cut(f.x - OUT, f.y + bar, f.x + IN, bottom),
            right = cut(right - IN, f.y + bar, right + OUT, bottom),
            bottom = cut(f.x, bottom - IN, right, bottom + OUT),
            bottomLeft = cut(f.x - CORNER, bottom - IN, f.x + IN, bottom + CORNER),
            bottomRight = cut(right - IN, bottom - IN, right + CORNER, bottom + CORNER),
        )
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

/** The title bar's height, in dp: a lock and the menu a thumb can hit (PcMin.TOUCH_DP), and a strip a thumb can drag by. */
private const val BAR_DP = 44

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
 * 2.2: the tracker as a window over the game in landscape. One draggable Box: the title bar moves it, grabs round its
 * sides, its bottom and its bottom corners resize it, and a double tap on the bar puts it back where the dock would be.
 * The lock at the bar's left pins it, size and place, until it is unlocked (Blake, 2026-10-02: "Undocked, I want to be
 * able to move it around, make it any size and lock it into place").
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
    onDock: () -> Unit,
    /** The tracker's menu (TrackerCornerMenu) in the title bar, handed the way back to the dock. */
    menu: @Composable (dock: () -> Unit) -> Unit,
    /** The run's attempt: the title reads it in a Kaizo IronMON run (Blake, 2026-10-02: "Replace 'Tracker' with the attempt and number"). */
    attempt: Int = 0,
    content: @Composable () -> Unit,
) {
    // Leaving the HUD or the window for the dock leaves the HUD off, so the next Float is a window again.
    val dock = { TrackerOptions.trackerHud = false; onDock() }
    if (TrackerHud.ENABLED && TrackerOptions.trackerHud) {
        // The Tracker HUD rides on this path, so Play lays the game out as for the window: never moved, never smaller.
        CompositionLocalProvider(LocalHudMenu provides HudMenuItem(TrackerHud.MENU_FLOAT) { TrackerOptions.trackerHud = false; TrackerOptions.save() }) {
            TrackerHudLayer(menu = { menu(dock) }, attempt = attempt, content = content)
        }
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
        val locked = TrackerOptions.floatingLocked
        // See-through (FloatingSeeThrough): the fills fade, the words take an outline, and locked, the empty space
        // takes no touch, so a tap there reaches the game.
        val solid = TrackerOptions.floatingSolid
        val fade = FloatingSeeThrough.fade(solid)
        val outline = if (FloatingSeeThrough.seeThrough(solid)) trackerTextOutline(2.5f * density) else null
        // The panel's first row, handed up to the bar (WindowBar.kt).
        val barSlot = remember { WindowBarSlot() }
        val parts = barSlot.parts
        // One row at every width (WindowBarFit.top): narrow, the grip marks go, then the gear moves into the menu.
        val top = WindowBarFit.top(live.w, swap = parts?.swap != null, gear = parts?.onGear != null, locked = locked)
        val barDp = WindowBarFit.ROW_DP
        fun move(f: FloatFrame) = onFrame(f.clamped(areaW, areaH))
        val shown = live
        // The tracker's own height, as last laid out. The window fits it: the height the player sets is the most it
        // may take, and blank space under the cards is not drawn (Blake, 2026-10-02: "Wasted space").
        var contentH by remember { mutableStateOf(0f) }
        val fitH = if (contentH > 0f) (barDp + contentH).coerceIn(FloatFrame.MIN_H, shown.h) else shown.h
        Box(
            Modifier
                .offset { IntOffset((shown.x * density).roundToInt(), (shown.y * density).roundToInt()) }
                .size(shown.w.dp, fitH.dp)
                .border(1.dp, Pc.Border),
        ) {
            // The Main background colour and the player's image, once behind the whole window; the panel inside it
            // paints nothing of its own (TrackerBackdrop.kt). Its own layer, so see-through fades it alone.
            Box(Modifier.matchParentSize().graphicsLayer { alpha = fade }.hostBackdrop())
            CompositionLocalProvider(LocalWindowFade provides fade, LocalTrackerTextShadow provides outline, LocalWindowBar provides barSlot) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().height(barDp.dp).background(windowFill(Pc.Ground))
                        .then(if (locked) Modifier else Modifier
                            .pointerInput(areaW, areaH) {
                                detectDragGestures { change, drag ->
                                    change.consume()
                                    move(live.copy(x = live.x + drag.x / density, y = live.y + drag.y / density))
                                }
                            }
                            .pointerInput(areaW, areaH) {
                                detectTapGestures(onDoubleTap = { move(FloatFrame.default(areaW, areaH)) })
                            }),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // One group at the bar's left: the lock, the grip, the title and the menu side by side, and the
                    // rest of the bar a handle to drag by (Blake, 2026-10-03: "the hamburger menu and lock and attempt
                    // will be moved closer together"). Every button keeps its touch box, which reaches past the slim
                    // row above and below (overhang).
                    Box(Modifier.overhang()) { LockButton(locked) { TrackerOptions.floatingLocked = !locked; TrackerOptions.save() } }
                    // Six dots: the bar is a handle while it can move.
                    if (top.grip) GripDots()
                    // One row (Blake, 2026-10-04, "less bulky"): the panel's first row, the battle banner or the
                    // area's bar, is drawn here (WindowBar.kt): one text slot, the swap, the gear, then the menu.
                    val segs = WindowBarText.withAttempt(attempt.takeIf { ironmonRunInPlay(attempt) }, parts?.segments ?: emptyList())
                        .ifEmpty { listOf(BarSegment("TRACKER", Pc.Dim, 0)) }
                    Box(Modifier.weight(1f).padding(start = 6.dp, end = 2.dp).overhang()) {
                        WindowBarTextSlot(segs, parts?.onTextTap, parts?.tapLabel, Modifier.fillMaxWidth())
                    }
                    parts?.extra?.invoke()
                    parts?.swap?.let { Box(Modifier.overhang()) { SwapIconButton(it) } }
                    if (top.gearInRow) parts?.onGear?.let { Box(Modifier.overhang()) { TrackerGearButton(onClick = it) } }
                    // What the row has no room for is in the menu: Tracker Setup, and the text's tap.
                    val moved = listOfNotNull(
                        parts?.onGear?.takeIf { !top.gearInRow }?.let { HudMenuItem("Tracker Setup", it) },
                        parts?.onTextTap?.takeIf { top.textTapInMenu }?.let { HudMenuItem(parts.tapLabel ?: "Trainer info", it) },
                    )
                    // The attempt, FILE, the screens and DOCK: one menu, beside the title (Blake, 2026-10-02).
                    CompositionLocalProvider(
                        LocalHudMenu provides HudMenuItem(TrackerHud.MENU_HUD) { TrackerOptions.trackerHud = true; TrackerOptions.save() }.takeIf { TrackerHud.ENABLED },
                        LocalWindowMenuItems provides moved,
                    ) {
                        Box(Modifier.overhang()) { PcCanvas(Modifier.width(PcMin.TOUCH_DP.dp)) { menu(dock) } }
                    }
                }
                // The room is the height the player gave the window, not the fitted one: fitted to one card it would
                // never again have room for two.
                val room = (shown.h - barDp).coerceAtLeast(0f).dp
                // Wide enough for columns (TrackerWideView): both cards side by side, so the room is no reason to swap.
                val wideView = TrackerWideView.applies(shown.w, room.value)
                Box(
                    Modifier.weight(1f).fillMaxWidth()
                        .then(if (locked) Modifier else Modifier.background(windowFill(Pc.Ground.copy(alpha = 0.6f)))),
                ) {
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
        }
        if (!locked) {
            // The grabs, outside the edge (FloatGrabs): each side and the bottom resize one way, the bottom corners both
            // at once. The corners come last, so they are on top where they meet a side.
            val g = FloatGrabs.of(shown, fitH, barDp.toFloat(), areaW, areaH)
            EdgeHandle(g.right, areaW, areaH) { dx, _ -> move(live.copy(w = live.w + dx)) }
            EdgeHandle(g.left, areaW, areaH) { dx, _ -> move(live.leftEdge(dx, areaW - FloatGrabs.OUT)) }
            EdgeHandle(g.bottom, areaW, areaH) { _, dy -> move(live.copy(h = live.h + dy)) }
            EdgeHandle(g.bottomRight, areaW, areaH) { dx, dy -> move(live.copy(w = live.w + dx, h = live.h + dy)) }
            EdgeHandle(g.bottomLeft, areaW, areaH) { dx, dy -> move(live.leftEdge(dx, areaW - FloatGrabs.OUT).let { it.copy(h = it.h + dy) }) }
            // The grip marks sit just outside the bottom corners, where their grabs are.
            val right = shown.x + shown.w
            val bottom = shown.y + fitH
            if (g.bottomRight.usable) CornerGrip(right - GRIP_INSET, bottom - GRIP_INSET, mirrored = false)
            if (g.bottomLeft.usable) CornerGrip(shown.x - GRIP_DP + GRIP_INSET, bottom - GRIP_INSET, mirrored = true)
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
    onDock: () -> Unit,
    menu: @Composable (dock: () -> Unit) -> Unit,
    attempt: Int = 0,
    content: @Composable () -> Unit,
) = FloatingTracker(panes.frameFor(windowW, windowH), windowW, windowH, onFrame = { panes.frame = it }, onDock = onDock,
    menu = menu, attempt = attempt, content = content)

/**
 * A strip or corner that resizes the window as it is dragged, in dp, at [r] in the box the window moves in. It stays
 * composed at no size while it has no room, so a drag that runs the edge into the side of the box carries on.
 */
@Composable
private fun EdgeHandle(r: FloatGrabs.Rect, areaW: Float, areaH: Float, onDrag: (Float, Float) -> Unit) {
    val density = LocalDensity.current.density
    Box(
        Modifier
            .offset { IntOffset((r.x * density).roundToInt(), (r.y * density).roundToInt()) }
            .size(r.w.dp, r.h.dp)
            .pointerInput(areaW, areaH) {
                detectDragGestures { change, drag ->
                    change.consume()
                    onDrag(drag.x / density, drag.y / density)
                }
            },
    )
}

/** A corner's grip mark, in dp: its square, and how far the square reaches in over the window's corner. */
private const val GRIP_DP = 16f
private const val GRIP_INSET = 3f

/** Three short strokes across a bottom corner, at ([x], [y]) in dp, with a dark edge so they read over any game. */
@Composable
private fun CornerGrip(x: Float, y: Float, mirrored: Boolean) {
    val color = Pc.Text.copy(alpha = 0.85f)
    val density = LocalDensity.current.density
    Canvas(
        Modifier
            .offset { IntOffset((x * density).roundToInt(), (y * density).roundToInt()) }
            .size(GRIP_DP.dp),
    ) {
        val s = size.minDimension
        for (i in 1..3) {
            val o = s * i / 3.6f
            val a = if (mirrored) Offset(0f, s - o) else Offset(s, s - o)
            val b = if (mirrored) Offset(o, s) else Offset(s - o, s)
            drawLine(Color.Black.copy(alpha = 0.5f), a, b, strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round)
            drawLine(color, a, b, strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
        }
    }
}

@Composable
private fun GripDots() {
    val color = Pc.Dim
    Canvas(Modifier.padding(start = 2.dp).size(width = 10.dp, height = 16.dp)) {
        val r = 1.5.dp.toPx()
        for (row in 0..2) for (col in 0..1) {
            drawCircle(color, r, Offset(size.width * (0.25f + col * 0.5f), size.height * (0.2f + row * 0.3f)))
        }
    }
}

/** A padlock, shut while the window is pinned, in a touch box of [BAR_DP]. A screen reader hears what a tap will do. */
@Composable
private fun LockButton(locked: Boolean, onClick: () -> Unit) {
    val color = if (locked) Pc.Gold else Pc.Text
    Box(
        Modifier.size(BAR_DP.dp).clickable(role = Role.Button) { onClick() }
            .semantics {
                contentDescription = if (locked) "Unlock the tracker window" else "Lock the tracker window in place"
                stateDescription = if (locked) "Locked" else "Unlocked"
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(width = 14.dp, height = 18.dp)) {
            val w = size.width
            val h = size.height
            val stroke = 2.dp.toPx()
            val body = h * 0.52f
            // The shackle: closed over the body, or swung up and open on its right.
            val shackleW = w * 0.62f
            val left = (w - shackleW) / 2
            val lift = if (locked) 0f else h * 0.16f
            drawArc(
                color, startAngle = 180f, sweepAngle = 180f, useCenter = false,
                topLeft = Offset(left, stroke / 2 - lift), size = Size(shackleW, shackleW),
                style = Stroke(stroke),
            )
            drawLine(color, Offset(left, shackleW / 2 - lift), Offset(left, h - body), strokeWidth = stroke)
            if (locked) drawLine(color, Offset(left + shackleW, shackleW / 2), Offset(left + shackleW, h - body), strokeWidth = stroke)
            drawRoundRect(color, topLeft = Offset(0f, h - body), size = Size(w, body), cornerRadius = CornerRadius(2.dp.toPx()))
        }
    }
}
