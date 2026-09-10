package com.ironmonone.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.ironmonone.core.Platform
import com.ironmonone.tracker.RunOutcome
import com.ironmonone.tracker.nds.NdsRunOver

/** Which reference's game-over screen a platform clones. */
fun gameOverFamily(platform: Platform): GameOverFamily = when (platform) {
    Platform.NDS -> GameOverFamily.DS
    Platform.GBC -> GameOverFamily.GEN12
    else -> GameOverFamily.GEN3
}

/**
 * When the game-over popup is open.
 *
 * Blake, 2026-09-10: on Black 2 it "popped up and then disappeared". The popup
 * was drawn for as long as the tracker's live outcome was non-null, and a loss
 * whites out to a Pokemon Center that heals the party, so the outcome cleared
 * a few seconds later and took the popup with it. Neither reference derives
 * the screen from live state. Both latch it and close it only on a button:
 *
 * - DS (NDS-Ironmon-Tracker): Program.onRunEnded returns early once
 *   tracker.hasRunEnded(), otherwise opens RUN_OVER_SCREEN and calls
 *   tracker.setRunOver(). Once per run; only new tracked data (a new seed)
 *   clears it.
 * - Gen 1 to 3 (Ironmon-Tracker GameOverScreen): checkForGameOver refuses while
 *   GameOverScreen.isDisplayed, which it sets on firing and which
 *   Battle.beginNewBattle clears. So it re-arms when the next battle begins,
 *   and Retry re-arms it through loadTempSaveState -> Battle.resetBattle.
 *
 * The popup closes only through [close] (the X, Continue playing, New game)
 * or [retried]. A tap outside it no longer counts (GameOverDialog).
 */
class GameOverLatch(private val family: GameOverFamily) {
    /** What the popup shows: the run as it ended, not as the tracker reads it now. */
    var outcome by mutableStateOf<RunOutcome?>(null)
        private set
    var dsCause by mutableStateOf<NdsRunOver?>(null)
        private set
    var open by mutableStateOf(false)
        private set
    /** GameOverScreen.isDisplayed, inverted (Gen 1 to 3); !tracker.hasRunEnded() (DS). */
    var armed by mutableStateOf(true)
        private set

    /** One tracker read. True exactly when the popup has just fired, so the caller logs the run once. */
    fun onRead(live: RunOutcome?, liveCause: NdsRunOver?): Boolean {
        if (live == null || !armed) return false
        outcome = live
        dsCause = liveCause
        open = true
        armed = false
        return true
    }

    /** Battle.beginNewBattle: GameOverScreen.isDisplayed = false. The DS latch holds for the whole run. */
    fun onBattleBegan() {
        if (family != GameOverFamily.DS) armed = true
    }

    /** The X, Continue playing, New game: the popup goes, the latch stays. */
    fun close() {
        open = false
    }

    /**
     * Retry the battle: the loss is undone, so the replayed battle can end the
     * run again. The DS reference has no Retry; the app's re-arms the same way.
     */
    fun retried() {
        open = false
        armed = true
    }

    /** A new run: the tracker data starts over, as a new seed's does in the reference. */
    fun reset() {
        outcome = null
        dsCause = null
        open = false
        armed = true
    }
}
