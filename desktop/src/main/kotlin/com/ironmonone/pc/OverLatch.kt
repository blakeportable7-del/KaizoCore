package com.ironmonone.pc

import com.ironmonone.tracker.GameOver
import com.ironmonone.tracker.TrackedMon

/**
 * When the game over card is up (PLAN row 6), the app's GameOverLatch for a Gen 3 run, which follows the reference's
 * GameOverScreen: checkForGameOver refuses while isDisplayed, which it sets on firing and which Battle.beginNewBattle
 * clears. So the card stays after a whiteout's heal has cleared the tracker's live read, and goes only with Continue
 * playing or Retry; a new battle arms it again, and Retry re-arms it on the first read without a loss (the state the
 * battle began from lands a frame or two after the click, so the next read can still be the lost battle).
 */
class OverLatch {
    class Ending(val outcome: GameOver, val team: List<TrackedMon>) {
        var teamIndex = 0
        var quote = ""
        var retryConfirm = false
        var save = com.ironmonone.pcui.PcSaveStatus.NOT_CLICKED
        val won: Boolean get() = outcome == GameOver.WON
    }

    var ending: Ending? = null
        private set
    private var armed = true
    private var awaitingClear = false
    private var wasInBattle = false

    /** One tracker read. True exactly when the card has just fired. */
    fun onRead(live: GameOver?, inBattle: Boolean, team: () -> List<TrackedMon>): Boolean {
        // Battle.beginNewBattle: GameOverScreen.isDisplayed = false.
        if (inBattle && !wasInBattle && !awaitingClear) armed = true
        wasInBattle = inBattle
        if (awaitingClear) {
            if (live == null) { awaitingClear = false; armed = true }
            return false
        }
        if (live == null || !armed) return false
        ending = Ending(live, team())
        armed = false
        return true
    }

    /** Continue playing: the card goes; the latch stays until the next battle. */
    fun dismiss() { ending = null }

    /** Retry the battle: the loss is undone, so the replayed battle can end the run again. */
    fun retried() {
        ending = null
        armed = false
        awaitingClear = true
    }

    /** A new game loaded: the tracker data starts over. */
    fun reset() {
        ending = null
        armed = true
        awaitingClear = false
        wasInBattle = false
    }
}
