package com.ironmonone.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.ironmonone.core.Platform
import com.ironmonone.tracker.RunOutcome
import com.ironmonone.tracker.nds.NdsRunOver

/**
 * Whether the tracker panel's own game-over card may stand in for the battle and the party. The PC tracker's Continue
 * playing goes back to the tracker screen (GameOverScreen.lua:75-90), but the panels drew the card from the tracker's
 * live read, which says LOST until the battle ends (Gen 3) or the party is healed (Game Boy), so after Continue the
 * panel kept showing GAME OVER instead of the battle (rc32 audit P3 #72). The latch says when its ending was dismissed;
 * a new ending, Retry, a new run and another game's latch bring the card back.
 */
object GameOverCard {
    /** The popup's ending was closed with Continue playing, the X or New game. */
    var dismissed by mutableStateOf(false)
        internal set

    /** The panel's card: the game says the run is over ([ended]) and that ending's popup was not dismissed. */
    fun shows(ended: Boolean): Boolean = ended && !dismissed
}

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
    // Another game's dismissal is not this one's (GameOverCard).
    init { GameOverCard.dismissed = false }

    /** What the popup shows: the run as it ended, not as the tracker reads it now. */
    var outcome by mutableStateOf<RunOutcome?>(null)
        private set
    var dsCause by mutableStateOf<NdsRunOver?>(null)
        private set
    /**
     * The team the run ended with, fallen members and all, as the read that fired the latch had it. Drawn from the
     * live read, a whiteout's heal turned the fallen team healthy under the open popup (rc32 audit P3 #27).
     */
    var team by mutableStateOf<List<GameOverMon>>(emptyList())
        private set
    /** The team of the latest read, kept current by GameOverHost so that the read which fires the latch names its team. */
    internal var teamNow: () -> List<GameOverMon> = { emptyList() }
    var open by mutableStateOf(false)
        private set
    /** GameOverScreen.isDisplayed, inverted (Gen 1 to 3); !tracker.hasRunEnded() (DS). */
    var armed by mutableStateOf(true)
        private set
    /**
     * Retry was pressed and the loss has not been seen to go yet. Restoring the
     * battle-start state lands a frame or two after the tap, so the next read
     * can still be the old, lost battle. Re-arming on the tap fired the popup
     * again on that stale read, and Retry had to be pressed twice (Blake,
     * 2026-09-27). The latch re-arms on the first read without a loss instead.
     */
    private var awaitingClear = false

    /**
     * Whether the game in Play is a Kaizo IronMON run, the only kind the popup ends (2026-09-30, UX audit P0-1).
     * GameOverHost keeps it current (PlayRules). While it is false no loss is latched: a Nuzlocke ends by its own
     * rules, and its lead fainting opened GAME OVER, whose Retry undid a death and whose New game rolled an IronMON
     * seed over the Kaizo run. A library game has no run to end at all.
     */
    var applies = true

    /** One tracker read. True exactly when the popup has just fired, so the caller logs the run once. */
    fun onRead(live: RunOutcome?, liveCause: NdsRunOver?): Boolean {
        if (!applies) return false
        if (awaitingClear) {
            if (live == null) { awaitingClear = false; armed = true }
            return false
        }
        if (live == null || !armed) return false
        outcome = live
        dsCause = liveCause
        team = runCatching { teamNow() }.getOrDefault(emptyList())
        open = true
        armed = false
        GameOverCard.dismissed = false
        return true
    }

    /** Battle.beginNewBattle: GameOverScreen.isDisplayed = false. The DS latch holds for the whole run. */
    fun onBattleBegan() {
        if (family != GameOverFamily.DS) armed = true
    }

    /** The X, Continue playing, New game: the popup goes, the latch stays, and so does the panel's card (GameOverCard). */
    fun close() {
        open = false
        GameOverCard.dismissed = true
    }

    /**
     * Retry the battle: the loss is undone, so the replayed battle can end the
     * run again. The DS reference has no Retry; the app's re-arms the same way.
     */
    fun retried() {
        open = false
        armed = false
        awaitingClear = true
        // The loss is undone: the stream's run-over view and its randomized data close again (StreamHub.ended).
        outcome = null
        dsCause = null
        team = emptyList()
        GameOverCard.dismissed = false
    }

    /** A new run: the tracker data starts over, as a new seed's does in the reference. */
    fun reset() {
        outcome = null
        dsCause = null
        team = emptyList()
        open = false
        armed = true
        awaitingClear = false
        GameOverCard.dismissed = false
    }

    /**
     * One tracker read in Play, moved out of PlayScreen (verifier limit). When the popup has just fired, the run is
     * filed in its history and, on a DS, logged in Past Runs (Program.onRunEnded) once per run and with the time it
     * was played (RunClock), not the time since Play opened (rc32 audit P2 #41, #48). The [timer] follows the latch as
     * the reference's follows tracker.hasRunEnded(): held while the run is over, counting again once Retry undoes the
     * loss (it stopped for good, so a retried run's timer stayed frozen).
     */
    internal fun read(
        live: RunOutcome?, cause: NdsRunOver?, timer: RunTimer, store: PrepStore, session: GameSession,
        tracker: com.ironmonone.tracker.GbaTracker?, gba: com.ironmonone.tracker.TrackerState?,
        nds: com.ironmonone.tracker.nds.NdsTrackerState?, pastRuns: PastRunStore?, progress: () -> Int,
    ) {
        when {
            onRead(live, cause) -> {
                timer.stop()
                val won = outcome == RunOutcome.WON
                RunHistoryHook.recordRunEnd(store, session, tracker, gba, nds, won)
                if (nds != null && pastRuns != null && Demo.mode == null) {
                    val attempt = session.kind?.let { store.attempt(it.id) } ?: 0
                    val seconds = session.kind?.takeIf { session.isRun }?.let { RunClock.of(RunClock.key(it.id, attempt)) } ?: timer.seconds().toInt()
                    PastRun.fromDs(nds, won, seconds, progress(), attempt, store.lastSeedText())
                        ?.let { pastRuns.logEnd(it, store.runEvents(session)?.entries()) }
                }
            }
            armed -> timer.resume()
            else -> timer.stop()
        }
    }

    companion object {
        /**
         * The latch for a visit to Play. A DS run's starts fired when the run's end is on record for this attempt and
         * seed and no Retry came after it (rc32 audit P2 #41): the reference keeps hasRunEnded in the run's tracked
         * data, and a latch made fresh for each visit let the next faint log the run again, and the champion log a win
         * for a run already lost. Gen 1 to 3 latches re-arm at every battle on purpose, and start armed as before.
         */
        fun forPlay(platform: Platform, store: PrepStore, session: GameSession): GameOverLatch =
            GameOverLatch(gameOverFamily(platform)).also { if (it.family == GameOverFamily.DS && runEndedBefore(store, session)) it.armed = false }

        /** The run in Play has an end on record that no Retry the battle reopened (fileRunEnd, retriedAfter). */
        internal fun runEndedBefore(store: PrepStore, session: GameSession): Boolean {
            val kind = session.kind ?: return false
            if (!session.isRun || Demo.mode != null) return false
            return runCatching {
                val filed = RunHistory(store.runHistoryFile(kind)).find(store.attempt(kind.id), store.lastSeedText()) ?: return false
                !retriedAfter(filed, store.runEvents(session)?.entries())
            }.getOrDefault(false)
        }
    }
}
