package com.ironmonone.app

import android.view.KeyEvent

/**
 * Emulator actions driven from a physical key or pad button, resolved
 * through KeyBindings.activeActions and delivered to whatever Play screen
 * is up. Same shape as NewRunCombo: the screen sets the handlers while it
 * is showing and clears them when it leaves, so a key pressed on another
 * tab does nothing.
 *
 * Fast forward is a HOLD: down raises the speed, up drops it back, which
 * is how Delta and Pizza Boy treat a mapped fast-forward button.
 */
object QuickActions {
    var onQuickSave: (() -> Unit)? = null
    var onQuickLoad: (() -> Unit)? = null
    var onFastForward: ((Boolean) -> Unit)? = null
    var onOpenStates: (() -> Unit)? = null
    var onRewind: ((Boolean) -> Unit)? = null

    /** True when the key belonged to an action and was consumed. */
    fun handle(action: Int, keyCode: Int): Boolean {
        val a = KeyBindings.activeActions[keyCode] ?: return false
        when (action) {
            KeyEvent.ACTION_DOWN -> when (a) {
                KeyBindings.Action.QUICK_SAVE -> onQuickSave?.invoke()
                KeyBindings.Action.QUICK_LOAD -> onQuickLoad?.invoke()
                KeyBindings.Action.FAST_FORWARD -> onFastForward?.invoke(true)
                KeyBindings.Action.SAVE_STATES -> onOpenStates?.invoke()
                KeyBindings.Action.REWIND -> onRewind?.invoke(true)
            }
            KeyEvent.ACTION_UP -> when (a) {
                KeyBindings.Action.FAST_FORWARD -> onFastForward?.invoke(false)
                KeyBindings.Action.REWIND -> onRewind?.invoke(false)
                else -> {}
            }
        }
        return true
    }

    fun clear() { onQuickSave = null; onQuickLoad = null; onFastForward = null; onOpenStates = null; onRewind = null }
}
