package com.ironmonone.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import java.io.File

/**
 * Which rules the game in Play follows (2026-09-30, UX audit P0-1, P0-6, P0-8).
 *
 * The IronMON game-over popup, with its Retry and New game, belongs to a Kaizo IronMON run. A Nuzlocke ends by its
 * own rules, which its ledger keeps ("Run over: Whiteout"), and a game played from the library has no run to end.
 * Before this the popup fired in any tracked game: a Nuzlocke lead fainting opened GAME OVER, whose Retry put a dead
 * Pokémon back and whose New game rolled an IronMON seed over the Kaizo run in progress, and a plain Emerald got
 * GAME OVER at its first faint. File > Rules showed the IronMON rulebook in a Nuzlocke too.
 */
object PlayRules {

    enum class Kind { IRONMON, NUZLOCKE, PLAIN }

    fun kind(isRun: Boolean, nuzlocke: Boolean): Kind = when {
        nuzlocke -> Kind.NUZLOCKE
        isRun -> Kind.IRONMON
        else -> Kind.PLAIN
    }

    /** The game in Play: a Nuzlocke when it (or the randomized run) has a ledger. Main thread. */
    fun kind(session: GameSession, filesDir: File): Kind =
        kind(session.isRun, runCatching { NuzlockeTracking.current(filesDir) != null }.getOrDefault(false))

    /** The IronMON game-over popup and its latch. */
    fun ironmonGameOver(k: Kind): Boolean = k == Kind.IRONMON

    /** Rules in the File menu and in Tracker Setup: the Nuzlocke's own rules in a Nuzlocke, the mode's otherwise. */
    fun nuzlockeRules(k: Kind): Boolean = k == Kind.NUZLOCKE
}

/**
 * Whether the tracker panel's own game-over card belongs on screen: the tracker says the game is over
 * ([ended]) and the game in Play is a Kaizo IronMON run. The panels drew the PC tracker's card in any tracked
 * game, so a Nuzlocke showed "Game Over, Attempt 37" at its first faint (emulator, 2026-09-30). Worked out when
 * [ended] turns on, not on every poll, because it reads the session from the disk; any failure keeps the card.
 */
@Composable
fun ironmonGameOverCard(ended: Boolean): Boolean {
    val context = LocalContext.current
    return remember(ended) {
        ended && runCatching {
            val filesDir = context.applicationContext.filesDir
            PlayRules.ironmonGameOver(PlayRules.kind(PrepStore(filesDir).session(), filesDir))
        }.getOrDefault(true)
    }
}
