package com.ironmonone.app

import android.content.Context
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration

/**
 * A tap on the bare game screen, for the FILE bar (Blake, 2026-10-05: "screen tap, tapped at the upper 33% of the play
 * screen opens the semi transparent bar"). TapZone decides whether a tap counts; this reads the taps.
 *
 * Read from the game view's own touches. The pad's buttons and every other control sit above the view and take their
 * touches themselves, so only bare screen reaches it; the listener only watches, so the view still hands every touch
 * to the core. A tap on the DS touch screen is play, not a call for the menu, so it changes nothing.
 */
object ScreenTap {
    /** Held longer than this, a touch is a hold, not a tap. */
    const val TAP_MS = 400L

    /** The DS screens as the core lays them out: one of PadLayout.DS_LAYOUTS, the gap, the hybrid ratio. */
    data class DsLayout(val name: String, val gap: Int = 0, val hybridRatio: Int = 2)

    /** A rectangle in the core's frame, in its pixels. */
    data class Area(val left: Float, val top: Float, val right: Float, val bottom: Float) {
        fun contains(x: Float, y: Float) = x >= left && x < right && y >= top && y < bottom
    }

    /** The core's frame for a layout, and where in it the touch screen is drawn (null: it is not). */
    data class Frame(val width: Float, val height: Float, val touch: Area?)

    private const val W = 256f
    private const val H = 192f

    /**
     * melonDS's own layout (libretro/melonDS, src/libretro/screenlayout.cpp, read 2026-10-02): stacked screens take the
     * gap between them, side by side takes none, and a hybrid frame is the big screen at the ratio plus a column of one
     * small screen and twice the ratio, with the small touch screen at the foot of that column.
     */
    fun frame(l: DsLayout): Frame {
        val g = l.gap.coerceIn(0, 126).toFloat()
        val ratio = l.hybridRatio.coerceIn(2, 3)
        val r = ratio.toFloat()
        val hybridW = W * r + W + r * 2
        return when (l.name) {
            "bottom-top" -> Frame(W, H * 2 + g, Area(0f, 0f, W, H))
            "left-right" -> Frame(W * 2, H, Area(W, 0f, W * 2, H))
            "right-left" -> Frame(W * 2, H, Area(0f, 0f, W, H))
            "hybrid-top" -> {
                val x = W * r + (ratio / 2)
                Frame(hybridW, H * r, Area(x, H * (r - 1), x + W, H * r))
            }
            "hybrid-bottom" -> Frame(hybridW, H * r, Area(0f, 0f, W * r, H * r))
            "top" -> Frame(W, H, null)
            "bottom" -> Frame(W, H, Area(0f, 0f, W, H))
            // "top-bottom", and the core's own fallback for a name it does not know (NdsScreens.classicName).
            else -> Frame(W, H * 2 + g, Area(0f, H + g, W, H * 2 + g))
        }
    }

    /** A finger's down, moves and up, to a tap or nothing. Apart from MotionEvent so a test can drive it. */
    class Taps(private val slopPx: Float) {
        private var tracking = false
        private var downX = 0f
        private var downY = 0f
        private var downAt = 0L

        fun down(x: Float, y: Float, at: Long) { tracking = true; downX = x; downY = y; downAt = at }
        fun move(x: Float, y: Float) { if (tracking && kotlin.math.hypot(x - downX, y - downY) > slopPx) tracking = false }
        /** A second finger, or the system taking the gesture. */
        fun cancel() { tracking = false }
        /** Where the tap was, when this up ends one. */
        fun up(at: Long): Pair<Float, Float>? {
            val tap = (downX to downY).takeIf { tracking && at - downAt <= TAP_MS }
            tracking = false
            return tap
        }
    }

    /** The game view's listener. It answers false, so the view, and the DS stylus with it, still gets every touch. */
    fun listener(ui: PlayUiState, context: Context): View.OnTouchListener {
        val taps = Taps(ViewConfiguration.get(context).scaledTouchSlop.toFloat())
        return View.OnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> taps.down(e.x, e.y, e.eventTime)
                MotionEvent.ACTION_MOVE -> taps.move(e.x, e.y)
                MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_CANCEL -> taps.cancel()
                MotionEvent.ACTION_UP -> taps.up(e.eventTime)?.let { (x, y) -> ui.onScreenTap(x, y, v.width.toFloat(), v.height.toFloat()) }
            }
            false
        }
    }
}
