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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
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

/** The floating window's frame, in dp. Persisted per game with the other play settings. */
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
    fun clamped(windowW: Float, windowH: Float): FloatFrame {
        val w = w.coerceIn(MIN_W, windowW); val h = h.coerceIn(MIN_H, windowH)
        return FloatFrame(x.coerceIn(0f, (windowW - w).coerceAtLeast(0f)), y.coerceIn(0f, (windowH - h).coerceAtLeast(0f)), w, h)
    }

    /** Dragged by its left edge: the right edge stays where it is. */
    fun leftEdge(dx: Float): FloatFrame {
        val right = x + w
        val nx = (x + dx).coerceIn(0f, right - MIN_W)
        return copy(x = nx, w = right - nx)
    }
}

/** The title bar's height, in dp: a lock and DOCK a thumb can hit, and a strip a thumb can drag by. */
private const val BAR_DP = 40

/** The border a finger resizes by while the window is unlocked: each side and the bottom. */
private const val EDGE_DP = 16

/** A bottom corner's square: the border both ways and a little more, so a corner is easy to catch. */
private const val CORNER_DP = 28

/**
 * 2.2: the tracker as a window over the game in landscape. One draggable Box: the title bar moves it, its sides, its
 * bottom and its bottom corners resize it, and a double tap on the bar puts it back where the dock would be. The lock
 * at the bar's left pins it, size and place, until it is unlocked (Blake, 2026-10-02: "Undocked, I want to be able to
 * move it around, make it any size and lock it into place").
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
    /** The tracker's menu (TrackerCornerMenu) at the bar's right, handed the way back to the dock. */
    menu: @Composable (dock: () -> Unit) -> Unit,
    /** The run's attempt: the title reads it in a Kaizo IronMON run (Blake, 2026-10-02: "Replace 'Tracker' with the attempt and number"). */
    attempt: Int = 0,
    content: @Composable () -> Unit,
) {
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
        fun move(f: FloatFrame) = onFrame(f.clamped(areaW, areaH))
        val shown = live
        val frameDp = if (locked) 0f else EDGE_DP.toFloat()
        // The tracker's own height, as last laid out. The window fits it: the height the player sets is the most it
        // may take, and blank space under the cards is not drawn (Blake, 2026-10-02: "Wasted space").
        var contentH by remember { mutableStateOf(0f) }
        val fitH = if (contentH > 0f) (BAR_DP + contentH + frameDp).coerceIn(FloatFrame.MIN_H, shown.h) else shown.h
        Box(
            Modifier
                .offset { IntOffset((shown.x * density).roundToInt(), (shown.y * density).roundToInt()) }
                .size(shown.w.dp, fitH.dp)
                // The Main background colour and the player's image, once behind the whole window; the panel
                // inside it paints nothing of its own (TrackerBackdrop.kt).
                .hostBackdrop()
                .border(1.dp, Pc.Border),
        ) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().height(BAR_DP.dp).background(Pc.Ground)
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
                    LockButton(locked) { TrackerOptions.floatingLocked = !locked; TrackerOptions.save() }
                    // Six dots: the bar is a handle while it can move.
                    if (!locked) GripDots()
                    val attemptTitle = ironmonRunInPlay(attempt)
                    Text(
                        if (attemptTitle) "ATTEMPT $attempt" else "TRACKER",
                        color = if (attemptTitle) Pc.Text else Pc.Dim, fontSize = if (attemptTitle) 13.sp else 11.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f).padding(start = 6.dp),
                    )
                    // The attempt, FILE, the screens and DOCK: one menu at the window's top right (Blake, 2026-10-02).
                    PcCanvas(Modifier.width(PcMin.TOUCH_DP.dp)) { menu(onDock) }
                }
                // Unlocked, the window keeps a border of its own to resize by, so no handle sits over a button of the
                // tracker's (SETUP and the corner arrow are at its right edge). Locked, the tracker has it all.
                val frameW = if (locked) 0.dp else EDGE_DP.dp
                // The room is the height the player gave the window, not the fitted one: fitted to one card it would
                // never again have room for two.
                val room = (shown.h - BAR_DP - frameDp).coerceAtLeast(0f).dp
                Box(
                    Modifier.weight(1f).fillMaxWidth()
                        .then(if (locked) Modifier else Modifier.background(Pc.Ground.copy(alpha = 0.6f)))
                        .padding(start = frameW, end = frameW, bottom = frameW),
                ) {
                    TrackerScroll(Modifier.fillMaxSize(), background = null) {
                        Column(Modifier.fillMaxWidth().onSizeChanged { contentH = it.height / density }) {
                            CompositionLocalProvider(LocalBackdropHosted provides true, LocalTrackerRoom provides room, LocalAttemptInTitle provides true) { content() }
                        }
                    }
                }
            }
            if (!locked) {
                // The border: each side and the bottom resize one way, the bottom corners both at once.
                EdgeHandle(Modifier.align(Alignment.CenterEnd).width(EDGE_DP.dp).fillMaxHeight().padding(top = BAR_DP.dp), areaW, areaH) { dx, _ ->
                    move(live.copy(w = live.w + dx))
                }
                EdgeHandle(Modifier.align(Alignment.CenterStart).width(EDGE_DP.dp).fillMaxHeight().padding(top = BAR_DP.dp), areaW, areaH) { dx, _ ->
                    move(live.leftEdge(dx))
                }
                EdgeHandle(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(EDGE_DP.dp), areaW, areaH) { _, dy ->
                    move(live.copy(h = live.h + dy))
                }
                EdgeHandle(Modifier.align(Alignment.BottomEnd).size(CORNER_DP.dp), areaW, areaH) { dx, dy ->
                    move(live.copy(w = live.w + dx, h = live.h + dy))
                }
                EdgeHandle(Modifier.align(Alignment.BottomStart).size(CORNER_DP.dp), areaW, areaH) { dx, dy ->
                    move(live.leftEdge(dx).let { it.copy(h = it.h + dy) })
                }
                CornerGrip(Modifier.align(Alignment.BottomEnd).padding(2.dp).size(EDGE_DP.dp), mirrored = false)
                CornerGrip(Modifier.align(Alignment.BottomStart).padding(2.dp).size(EDGE_DP.dp), mirrored = true)
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
    onDock: () -> Unit,
    menu: @Composable (dock: () -> Unit) -> Unit,
    attempt: Int = 0,
    content: @Composable () -> Unit,
) = FloatingTracker(panes.frameFor(windowW, windowH), windowW, windowH, onFrame = { panes.frame = it }, onDock = onDock,
    menu = menu, attempt = attempt, content = content)

/** A strip or corner that resizes the window as it is dragged, in dp. */
@Composable
private fun EdgeHandle(modifier: Modifier, areaW: Float, areaH: Float, onDrag: (Float, Float) -> Unit) {
    val density = LocalDensity.current.density
    Box(
        modifier.pointerInput(areaW, areaH) {
            detectDragGestures { change, drag ->
                change.consume()
                onDrag(drag.x / density, drag.y / density)
            }
        },
    )
}

@Composable
private fun CornerGrip(modifier: Modifier, mirrored: Boolean) {
    val color = Pc.Text.copy(alpha = 0.8f)
    Canvas(modifier) {
        val s = size.minDimension
        for (i in 1..3) {
            val o = s * i / 3.6f
            val a = if (mirrored) Offset(0f, s - o) else Offset(s, s - o)
            val b = if (mirrored) Offset(o, s) else Offset(s - o, s)
            drawLine(color, a, b, strokeWidth = 2.dp.toPx())
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

/** A padlock, shut while the window is pinned. A screen reader hears what a tap will do. */
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
