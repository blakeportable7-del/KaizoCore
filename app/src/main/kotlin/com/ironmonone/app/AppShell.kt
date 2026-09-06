package com.ironmonone.app

import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * The one slot in the top bar that a screen may fill.
 *
 * The Play screen's emulator controls used to sit in a row between the game and
 * the tracker, which is the part of the screen actually worth reading. They live
 * behind a FILE button in the top bar now, so nothing stands between the two
 * things you are looking at.
 */
object AppBarActions {
    var content by mutableStateOf<(@Composable () -> Unit)?>(null)
}

/**
 * B pressed in a WILD battle runs, on every input path.
 *
 * This used to hang off the on-screen pad's B button alone, and the pad hides
 * itself the moment a Bluetooth controller connects - so for anyone playing on
 * a controller, which is the normal way to play, B-to-run simply did not exist.
 * The routing lives here so the pad, a controller and a keyboard all reach it.
 *
 * The wild-only rule is enforced by the Play screen, which clears [onFlee]
 * whenever the current battle is not a wild one. A trainer battle never offers
 * Run, and mashing the sequence into one would open the Bag instead.
 */
object FleeOnB {
    /** Set while a WILD battle is on screen; null at every other moment. */
    var onFlee: (() -> Unit)? = null

    fun handle(action: Int, keyCode: Int) {
        if (keyCode != android.view.KeyEvent.KEYCODE_BUTTON_B) return
        if (action != android.view.KeyEvent.ACTION_UP) return
        onFlee?.invoke()
    }
}

/**
 * A+B+Start, held - the IronMON reset gesture.
 *
 * On a real cartridge that combo is the soft reset every IronMON player already
 * has in their hands, so a NEW RUN button on screen was a second way to do a
 * thing the standard gesture already does. The button is gone.
 *
 * It fires on a HOLD, not a tap: those three buttons are pressed together often
 * enough in ordinary play that an instant trigger would wipe a run mid-battle.
 */
object NewRunCombo {
    private val NEEDED = setOf(
        KeyEvent.KEYCODE_BUTTON_A,
        KeyEvent.KEYCODE_BUTTON_B,
        KeyEvent.KEYCODE_BUTTON_START,
    )

    /** How long all three must stay down. */
    private const val HOLD_MS = 900L

    private val held = HashSet<Int>()
    private val handler = Handler(Looper.getMainLooper())
    private var pending: Runnable? = null

    /** Set by the Play screen while a game is loaded; cleared when it leaves. */
    var onFire: (() -> Unit)? = null

    /** Feed every button event that reaches the core, from any input path. */
    fun track(action: Int, keyCode: Int) {
        if (keyCode !in NEEDED) return
        when (action) {
            KeyEvent.ACTION_DOWN -> held += keyCode
            KeyEvent.ACTION_UP -> held -= keyCode
            else -> return
        }
        if (held.containsAll(NEEDED)) {
            if (pending == null) {
                val r = Runnable {
                    pending = null
                    // Re-check rather than trusting the timer: a release during
                    // the hold window must cancel, and removeCallbacks can race.
                    if (held.containsAll(NEEDED)) onFire?.invoke()
                }
                pending = r
                handler.postDelayed(r, HOLD_MS)
            }
        } else {
            pending?.let { handler.removeCallbacks(it) }
            pending = null
        }
    }

    /** A game teardown must not leave stale keys latched down. */
    fun reset() {
        held.clear()
        pending?.let { handler.removeCallbacks(it) }
        pending = null
    }
}
