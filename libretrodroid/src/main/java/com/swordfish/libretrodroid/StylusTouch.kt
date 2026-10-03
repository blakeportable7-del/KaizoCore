package com.swordfish.libretrodroid

import android.view.MotionEvent

/**
 * KaizoCore (rc32 audit P2 #126): what a touch on the game view does to the DS stylus, kept apart from MotionEvent so a
 * JVM test can drive it. The stylus presses at the pointer it follows while that pointer is down or moving, and lifts on
 * its up, on a cancel (the system taking the gesture), and when the pointer it follows leaves while another stays down.
 */
object StylusTouch {
    enum class Act { PRESS, LIFT, NONE }

    /** [trackedPointerUp]: an ACTION_POINTER_UP is for the pointer the stylus follows. */
    fun act(actionMasked: Int, trackedPointerUp: Boolean): Act = when (actionMasked) {
        MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> Act.PRESS
        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> Act.LIFT
        MotionEvent.ACTION_POINTER_UP -> if (trackedPointerUp) Act.LIFT else Act.NONE
        else -> Act.NONE
    }
}
