package com.ironmonone.tracker

/**
 * What the Play screen needs to know about a run, whichever tracker produced it.
 *
 * The GBA and DS trackers emit different state types because they clone
 * different PC trackers, and their panels rightly differ. But the SCREEN
 * around the panel asked the same four questions of both - am I in a battle,
 * is it wild, who is the opponent, is the run over - and answered each with an
 * `if (isNds)`. This is the one answer. A Gen 1 or Gen 5 tracker joins by
 * implementing it; the screen does not change.
 */
interface RunView {
    val inBattle: Boolean
    val isWildBattle: Boolean
    /** Species id of the opponent on screen, or -1 when there is none. */
    val enemySpeciesId: Int
    /** Set once the run has ended, either way. */
    val outcome: RunOutcome?
}

enum class RunOutcome { WON, LOST }
