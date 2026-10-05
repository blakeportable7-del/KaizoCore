package com.ironmonone.app

import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.ViewConfiguration

/**
 * Swipe to scroll a locked, see-through floating window, while a tap on its empty space still reaches the game (Blake,
 * 2026-10-04, on rc35.1's arrows: "I wish I could just scroll up or down to reveal the enemy and my pokemon, also that
 * arrow on tracker is really annoying").
 *
 * A scrolling column in Compose takes every touch on it, and a tap that went to the column never reaches the game's
 * controls under the window. So the decision is made before Compose sees the touch, in the activity
 * ([MainActivity.dispatchTouchEvent]): a touch that starts on the window's content is held back until it is known.
 * - It moves past the touch slop: a swipe. It scrolls the window, and no part of it ever goes on, so the game never sees it.
 * - It lifts first: a tap. The held press goes on as it was, then the lift, at least [MIN_PRESS_MS] later so the game
 *   reads the press on a frame. Compose then sends it where it always went: a button or tappable row in the window
 *   takes it, and empty space passes it to the game (FloatingSeeThrough.passesThrough).
 * - It stays down and still for [HOLD_MS]: a held press. It goes on from there, as a tap would, and the rest with it.
 * Unlocked, solid, docked and portrait never register a window here, so nothing is held back.
 */
object WindowSwipe {
    const val HOLD_MS = 250L
    const val MIN_PRESS_MS = 50L

    /** The window's content in window pixels, and how to scroll it by a finger's move; null while nothing is registered. */
    class Target(val left: Float, val top: Float, val right: Float, val bottom: Float, val scrollBy: (Float) -> Unit) {
        fun contains(x: Float, y: Float) = x >= left && x < right && y >= top && y < bottom
    }

    @Volatile var target: Target? = null

    private var filter: Filter<MotionEvent>? = null

    /** The activity's touch, through the filter; [pass] is the activity's own dispatch. */
    fun dispatch(context: android.content.Context, ev: MotionEvent, pass: (MotionEvent) -> Boolean): Boolean {
        // Nothing registered and nothing held: the touch goes on untouched, as before rc35.2.
        if (target == null && filter?.idle != false) return pass(ev)
        val f = filter ?: run {
            val handler = Handler(Looper.getMainLooper())
            Filter<MotionEvent>(
                slop = ViewConfiguration.get(context).scaledTouchSlop.toFloat(),
                deliver = { e -> pass(e); e.recycle() },
                drop = { e -> e.recycle() },
                later = { ms, run -> handler.postDelayed(run, ms) },
                now = { android.os.SystemClock.uptimeMillis() },
            ).also { filter = it }
        }
        // The filter keeps a copy of anything it holds back: the system recycles [ev] after this call.
        return f.onTouch({ MotionEvent.obtain(ev) }, ev.actionMasked, ev.x, ev.y, target) ?: pass(ev)
    }

    /** What one touch turned out to be, by where it went before it lifted. */
    enum class Kind { PENDING, TAP, SWIPE }

    /** Tap or swipe, by distance alone: a swipe is any move past [slop] from where it went down. */
    class Classifier(private val slop: Float) {
        private var x0 = 0f
        private var y0 = 0f
        var kind = Kind.PENDING
            private set

        fun down(x: Float, y: Float) { x0 = x; y0 = y; kind = Kind.PENDING }

        fun move(x: Float, y: Float): Kind {
            if (kind == Kind.PENDING && Math.hypot((x - x0).toDouble(), (y - y0).toDouble()) > slop) kind = Kind.SWIPE
            return kind
        }

        fun up(): Kind { if (kind == Kind.PENDING) kind = Kind.TAP; return kind }
    }

    /**
     * The touch filter, free of Android so it is tested as it runs. [onTouch] returns null to let the event go on as
     * usual, or true when it held it back or used it. [copy] makes a copy to hold; held copies go out by [deliver] and are
     * thrown away by [drop].
     */
    class Filter<E>(
        slop: Float,
        private val deliver: (E) -> Unit,
        private val drop: (E) -> Unit,
        private val later: (Long, () -> Unit) -> Unit,
        private val now: () -> Long,
    ) {
        private enum class State { IDLE, HELD, SCROLLING, PASSING }
        private var state = State.IDLE
        private val held = ArrayList<E>()
        private val classifier = Classifier(slop)
        private var lastY = 0f
        private var downAt = 0L
        private var gesture = 0
        /** A tap's lift, waiting for its [MIN_PRESS_MS]. */
        private var liftDue: E? = null

        /** Holding nothing back and in no touch of its own. */
        val idle get() = (state == State.IDLE || state == State.PASSING) && held.isEmpty() && liftDue == null

        fun onTouch(copy: () -> E, action: Int, x: Float, y: Float, target: Target?): Boolean? {
            if (action == MotionEvent.ACTION_DOWN) {
                flushLift()
                releaseHeld()
                gesture++
                if (target == null || !target.contains(x, y)) { state = State.PASSING; return null }
                state = State.HELD
                classifier.down(x, y)
                lastY = y
                downAt = now()
                held += copy()
                val g = gesture
                later(HOLD_MS) { if (gesture == g && state == State.HELD) { sendHeld(); state = State.PASSING } }
                return true
            }
            return when (state) {
                State.IDLE, State.PASSING -> null
                State.SCROLLING -> {
                    if (action == MotionEvent.ACTION_MOVE) { target?.scrollBy?.invoke(y - lastY); lastY = y }
                    if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) state = State.IDLE
                    true
                }
                State.HELD -> when (action) {
                    MotionEvent.ACTION_MOVE -> {
                        if (classifier.move(x, y) == Kind.SWIPE) {
                            releaseHeld()
                            state = State.SCROLLING
                            target?.scrollBy?.invoke(y - lastY)
                            lastY = y
                        }
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        classifier.up()
                        sendHeld()
                        state = State.IDLE
                        val wait = MIN_PRESS_MS - (now() - downAt)
                        if (wait <= 0) deliver(copy()) else {
                            liftDue = copy()
                            val g = gesture
                            later(wait) { if (gesture == g) flushLift() }
                        }
                        true
                    }
                    MotionEvent.ACTION_CANCEL -> { releaseHeld(); state = State.IDLE; true }
                    else -> {
                        // A second finger: what was held goes on, and the rest of the touch with it.
                        sendHeld(); state = State.PASSING; null
                    }
                }
            }
        }

        private fun sendHeld() { val h = held.toList(); held.clear(); h.forEach(deliver) }
        private fun releaseHeld() { held.forEach(drop); held.clear() }
        private fun flushLift() { liftDue?.let { liftDue = null; deliver(it) } }
    }
}
