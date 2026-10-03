package com.ironmonone.app

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The docked tracker in landscape, with no bar between it and the game (Blake, 2026-10-03: "i really dislike the
 * vertical bar between the game screen and tracker on landscape mode"). The game column meets the tracker column, and
 * the tracker's own left edge resizes it:
 *
 * - A drag that starts within [TrackerEdge.ZONE_DP] of the edge moves the split, clamped by [PaneSizes.drag]. The zone
 *   lies over the tracker, never over the game, so it cannot take a touch from an on-screen button.
 * - It is read on the column itself in the Initial pass, so it sees each touch before the cards do. There is no layer
 *   with pointerInput over the cards: Compose gives a touch to the topmost sibling only, so such a strip would have
 *   taken the taps of everything under it.
 * - Nothing is consumed until the finger has moved sideways past the touch slop ([EdgeDrag]): a tap in the zone is the
 *   card's, and a swipe up or down is the column's.
 * - A thin grip is drawn at the edge while the finger drags, and only then.
 * - There is no double tap to reset: a double tap there would also tap the card. Dragging back is the way back, and the
 *   clamp keeps both columns usable.
 * - TalkBack gets the same resize as two actions on a node over the edge that takes no touch ([EdgeLabel]).
 *
 * The column is a share of the window, not a dragged pixel width, so the split matches the reference layout on any
 * screen, and it scrolls with an arrow at its foot while there is more below (TrackerScroll).
 */
@Composable
internal fun DockedTracker(panes: PaneSizes, windowW: Float, content: @Composable () -> Unit) {
    val dragging = remember { mutableStateOf(false) }
    Box(
        Modifier.width(panes.width(windowW).dp).fillMaxHeight()
            .edgeResize(panes, windowW, dragging)
            .drawWithContent {
                drawContent()
                if (dragging.value) edgeGrip()
            },
    ) {
        TrackerScroll(Modifier.fillMaxSize()) { content() }
        EdgeLabel(panes, windowW)
    }
}

/** The rules of the edge that need no Compose, so they can be held to on the JVM (TrackerEdgeTest). */
internal object TrackerEdge {
    /** How far from the tracker's left edge a drag moves the split, in dp: the width the bar had. */
    const val ZONE_DP = 24f

    /** One TalkBack action moves the edge this share of the window. */
    const val STEP = 0.05f

    /** TalkBack's "Make tracker wider": true when the tracker grew, false at the clamp (170 dp of game is left). */
    fun wider(panes: PaneSizes, windowW: Float): Boolean = step(panes, windowW, -windowW * STEP)

    /** TalkBack's "Make tracker narrower": true when the tracker shrank, false at the clamp (150 dp of tracker). */
    fun narrower(panes: PaneSizes, windowW: Float): Boolean = step(panes, windowW, windowW * STEP)

    private fun step(panes: PaneSizes, windowW: Float, dx: Float): Boolean {
        val before = panes.fraction
        panes.drag(dx, windowW)
        return panes.fraction != before
    }

    /** The tracker's share of the screen in whole percent, as TalkBack reads it. */
    fun percent(panes: PaneSizes): Int = (panes.fraction * 100f).roundToInt()
}

/**
 * One touch on the docked tracker, decided move by move, in px ([zone] and [slop] too). A touch that comes down within
 * [zone] of the left edge and then moves sideways past [slop], before it moves that far up or down, moves the split of
 * [panes] from then on; until then it is only watched. A tap, a touch that came down anywhere else and a touch that
 * scrolled first are the tracker's, and nothing of them is consumed.
 */
internal class EdgeDrag(
    private val zone: Float,
    private val slop: Float,
    private val density: Float,
    private val panes: PaneSizes,
    private val windowW: Float,
) {
    /** What a move does: nothing yet ([WATCH]), moves the split and is taken ([TAKE]), or is the tracker's ([LEAVE]). */
    enum class Step { WATCH, TAKE, LEAVE }

    /** True from the move that passed the slop sideways until the finger lifts. */
    var resizing = false
        private set
    private var watching = false
    private var dx = 0f
    private var dy = 0f

    /** A finger came down [x] px from the column's left edge. False: the touch is the tracker's, stop watching. */
    fun down(x: Float): Boolean {
        resizing = false
        dx = 0f
        dy = 0f
        watching = x in 0f..zone
        return watching
    }

    /**
     * The finger moved ([mx], [my]) px. On the move that starts the resize the split moves all the way from where the
     * finger came down, so the edge stays under it; each move after that moves it as far.
     */
    fun move(mx: Float, my: Float): Step {
        if (resizing) {
            panes.drag(mx / density, windowW)
            return Step.TAKE
        }
        if (!watching) return Step.LEAVE
        dx += mx
        dy += my
        return when {
            abs(dx) > slop && abs(dx) >= abs(dy) -> {
                resizing = true
                panes.drag(dx / density, windowW)
                Step.TAKE
            }
            // The column's scroll starts at the same slop: from here the swipe is its.
            abs(dy) >= slop -> {
                watching = false
                Step.LEAVE
            }
            else -> Step.WATCH
        }
    }

    /** The finger lifted. True only after a resize, when the lift is taken too; a tap's lift is the card's. */
    fun up(): Boolean {
        val took = resizing
        resizing = false
        watching = false
        return took
    }
}

/**
 * The drag, on the tracker column itself. Keyed on the window as well as the panes: a block made for the first window
 * clamped against its width ever after (rc32 audit P3 #56).
 */
private fun Modifier.edgeResize(panes: PaneSizes, windowW: Float, dragging: MutableState<Boolean>): Modifier =
    pointerInput(panes, windowW) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val edge = EdgeDrag(TrackerEdge.ZONE_DP.dp.toPx(), viewConfiguration.touchSlop, density, panes, windowW)
            if (!edge.down(down.position.x)) return@awaitEachGesture
            try {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) {
                        if (edge.up()) change.consume()
                        break
                    }
                    val moved = change.positionChange()
                    val step = edge.move(moved.x, moved.y)
                    if (step == EdgeDrag.Step.LEAVE) break
                    if (step == EdgeDrag.Step.TAKE) {
                        change.consume()
                        dragging.value = true
                    }
                }
            } finally {
                dragging.value = false
            }
        }
    }

/** The grip, drawn while a finger drags the edge: a thin rail down the edge with a handle at its middle. */
private fun DrawScope.edgeGrip() {
    val rail = 2.dp.toPx()
    val w = 5.dp.toPx()
    val h = 44.dp.toPx()
    val line = 1.dp.toPx()
    val top = (size.height - h) / 2f
    drawRect(Pc.Text.copy(alpha = 0.6f), topLeft = Offset.Zero, size = Size(rail, size.height))
    drawRoundRect(Color.Black.copy(alpha = 0.4f), topLeft = Offset(0f, top - line), size = Size(w + line, h + 2 * line),
        cornerRadius = CornerRadius((w + line) / 2f))
    drawRoundRect(Pc.Text, topLeft = Offset(0f, top), size = Size(w, h), cornerRadius = CornerRadius(w / 2f))
}

/**
 * TalkBack's way to the resize: a node over the edge with the two actions and the tracker's share of the screen.
 * Compose lets TalkBack focus a node that speaks only when it has no children (or merges them, which here would read
 * every card as one), so the actions sit on this leaf and not on the tracker column. It has no pointerInput, so it
 * takes no touch: Compose hit-tests pointer input only, and a tap there still reaches the card under it.
 */
@Composable
private fun EdgeLabel(panes: PaneSizes, windowW: Float) {
    val percent = TrackerEdge.percent(panes)
    Box(
        Modifier.width(TrackerEdge.ZONE_DP.dp).fillMaxHeight().semantics {
            contentDescription = "Resize tracker"
            stateDescription = "$percent percent of the screen"
            customActions = listOf(
                CustomAccessibilityAction("Make tracker wider") { TrackerEdge.wider(panes, windowW) },
                CustomAccessibilityAction("Make tracker narrower") { TrackerEdge.narrower(panes, windowW) },
            )
        },
    )
}
