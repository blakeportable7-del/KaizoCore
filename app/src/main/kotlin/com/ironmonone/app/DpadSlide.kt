package com.ironmonone.app

import android.view.KeyEvent

/**
 * Sliding on the touch d-pad (The Other Toby, 2026-10-10: "slide a finger from one direction to the next without
 * lifting"). The arrow the thumb went down on keeps the gesture; as the thumb moves, the direction is the one whose arm
 * of the plus it is over, by angle from the plus's centre, so it can leave the arrows' boxes and still steer. Inside a
 * third of a cell from the centre nothing changes, so a thumb resting on the middle does not flicker between arrows.
 * No diagonals: one direction at a time, as the four separate arrows always pressed.
 */
internal object DpadSlide {
    /** Where an arrow sits on the plus's 3 by 3 grid. */
    fun grid(key: Int): Pair<Int, Int> = when (key) {
        KeyEvent.KEYCODE_DPAD_UP -> 1 to 0
        KeyEvent.KEYCODE_DPAD_LEFT -> 0 to 1
        KeyEvent.KEYCODE_DPAD_RIGHT -> 2 to 1
        else -> 1 to 2
    }

    /** The direction held for a thumb at ([dx], [dy]) from the plus's centre; [held] inside the dead zone. */
    fun direction(dx: Float, dy: Float, cell: Float, held: Int): Int {
        if (dx * dx + dy * dy < (cell / 3f) * (cell / 3f)) return held
        return if (kotlin.math.abs(dx) > kotlin.math.abs(dy)) {
            if (dx < 0) KeyEvent.KEYCODE_DPAD_LEFT else KeyEvent.KEYCODE_DPAD_RIGHT
        } else {
            if (dy < 0) KeyEvent.KEYCODE_DPAD_UP else KeyEvent.KEYCODE_DPAD_DOWN
        }
    }
}
