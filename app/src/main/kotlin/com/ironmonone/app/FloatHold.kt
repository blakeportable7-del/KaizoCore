package com.ironmonone.app

import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.ViewConfiguration

/**
 * The floating window's lock, with no lock button (Blake, 2026-10-05: "remove the lock, a long hold would lock in place,
 * and a long hold would show the floating tracker expand corner button"). It is locked whenever it is shown. A finger
 * held still on it for [HOLD_MS] unlocks it, with a ring and a buzz: then a drag on it moves it and the corner resizes
 * it. Another hold locks it again, and so do opening the FILE bar and [IDLE_MS] with no touch on it.
 *
 * The hold is watched before Compose sees the touch ([MainActivity.dispatchTouchEvent]), as the see-through window's
 * swipe is (WindowSwipe): a locked, see-through window takes no touch on its empty parts, so nothing in Compose would
 * ever hear a hold there. Nothing is held back: every event goes on as it came, until the hold fires; then the rest of
 * that touch is cancelled for everything under it, so the row the finger rested on does not open its card on the lift.
 */
object FloatHold {
    /** Long enough not to fire on a tap, short enough not to feel slow. */
    const val HOLD_MS = 500L
    /** An unlocked window locks itself after this long untouched. */
    const val IDLE_MS = 6000L
    /** The ring shows after this much of the hold, so a tap never flashes it. */
    const val RING_AFTER_MS = 110L

    /** The window as drawn, in window pixels, while one is shown; null otherwise. */
    @Volatile var bounds: TapZone.Rect? = null

    /** A part of the window with a long press of its own (the swap shows its words), left out of the hold. */
    @Volatile var skip: TapZone.Rect? = null

    /** A hold fired: the window's lock flips. Set by the window while it is shown. */
    @Volatile var onHold: (() -> Unit)? = null

    /** A finger came down on the window, at ([x], [y]) in window pixels, or the touch ended (null): for the ring and the idle clock. */
    @Volatile var onPress: ((Float, Float) -> Unit)? = null
    @Volatile var onRelease: (() -> Unit)? = null

    /** What one touch does to the hold, free of Android so it is tested as it runs. */
    class Watch(private val slop: Float) {
        private var watching = false
        private var swallowing = false
        private var x0 = 0f
        private var y0 = 0f
        private var gesture = 0

        /** A touch that started on the window and has not moved, lifted or met a second finger. */
        val armed: Boolean get() = watching
        /** The hold has fired: the rest of this touch is the window's, and goes nowhere else. */
        val swallows: Boolean get() = swallowing

        /** A new touch; true when it came down on [bounds], and the returned gesture number is the one [fire] must name. */
        fun down(x: Float, y: Float, bounds: TapZone.Rect?, skip: TapZone.Rect? = null): Int {
            gesture++
            swallowing = false
            watching = bounds?.contains(x, y) == true && skip?.contains(x, y) != true
            x0 = x; y0 = y
            return gesture
        }

        fun move(x: Float, y: Float) {
            if (watching && Math.hypot((x - x0).toDouble(), (y - y0).toDouble()) > slop) watching = false
        }

        /** A second finger: a pinch or a two-thumb press, never a hold. */
        fun secondFinger() { watching = false }

        /** The touch ended; true when the hold had fired and this lift is swallowed too. */
        fun end(): Boolean { val s = swallowing; watching = false; swallowing = false; return s }

        /** [HOLD_MS] after [down]: fires when that touch is still on the window, still, and still down. */
        fun fire(g: Int): Boolean {
            if (g != gesture || !watching) return false
            watching = false
            swallowing = true
            return true
        }
    }

    private var watch: Watch? = null
    private val handler by lazy { Handler(Looper.getMainLooper()) }

    /** The activity's touch: watched, passed on, and after a hold the rest of it cancelled. [pass] is the next dispatch. */
    fun dispatch(context: android.content.Context, ev: MotionEvent, pass: (MotionEvent) -> Boolean): Boolean {
        val b = bounds
        val w = watch ?: Watch(ViewConfiguration.get(context).scaledTouchSlop.toFloat()).also { watch = it }
        if (b == null && !w.armed && !w.swallows) return pass(ev)
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val g = w.down(ev.x, ev.y, b, skip)
                if (w.armed) {
                    onPress?.invoke(ev.x, ev.y)
                    handler.postDelayed({
                        if (w.fire(g)) {
                            onHold?.invoke()
                            // Everything under the finger lets go now: the press it was given never becomes a tap.
                            val cancel = MotionEvent.obtain(ev.downTime, android.os.SystemClock.uptimeMillis(), MotionEvent.ACTION_CANCEL, ev.x, ev.y, 0)
                            pass(cancel)
                            cancel.recycle()
                        }
                    }, HOLD_MS)
                }
            }
            MotionEvent.ACTION_MOVE -> if (!w.swallows) { val was = w.armed; w.move(ev.x, ev.y); if (was && !w.armed) onRelease?.invoke() }
            MotionEvent.ACTION_POINTER_DOWN -> if (!w.swallows && w.armed) { w.secondFinger(); onRelease?.invoke() }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (w.armed || w.swallows) onRelease?.invoke()
                if (w.end()) return true
            }
        }
        return if (w.swallows) true else pass(ev)
    }
}

/** The window's lock rules, apart from Compose so they are tested as they run. */
object FloatLockRules {
    /** A hold flips the lock. */
    fun afterHold(locked: Boolean): Boolean = !locked

    /** Opening the FILE bar locks an unlocked window: the taps that follow are the bar's, and a stray drag must not carry it off. */
    fun locksForBar(barOpen: Boolean, locked: Boolean): Boolean = barOpen && !locked

    /** Unlocked and untouched for [FloatHold.IDLE_MS]: it locks itself. */
    fun idleLocks(locked: Boolean, touchedAt: Long, now: Long): Boolean = !locked && now - touchedAt >= FloatHold.IDLE_MS

    /** What the toast says. */
    const val UNLOCKED = "Unlocked: drag it, or pull the corner to resize."
    const val LOCKED = "Locked in place."
    const val LOCKED_IDLE = "Locked again after six seconds untouched."
    const val LOCKED_FOR_BAR = "The window locked while the menu is open."
    const val TIP = "Unlocked. Drag to move. Hold to lock."
}
