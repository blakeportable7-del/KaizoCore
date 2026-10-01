package com.ironmonone.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Where a screen deep inside a tab can send the player, provided by the shell (MainActivity) so a screen needs no
 * callback of its own (2026-09-30). The Play screen cannot take new parameters: its composable sits at ART's
 * verifier limit.
 */
internal class ShellNav(
    val openMyGames: () -> Unit,
    val openKaizo: () -> Unit,
)

internal val LocalShellNav = staticCompositionLocalOf<ShellNav?> { null }

/** The Play tab's words when there is nothing to play, kept apart so a test can hold them (2026-09-30). */
internal object PlayNothingCopy {
    const val NOTHING = "Nothing to play yet"
    const val NOTHING_LINE = "Pick a game from your library to play it, or start a Kaizo IronMON run."
    const val PICK_A_GAME = "Pick a game"
    const val START_A_RUN = "Start a Kaizo IronMON run"
    const val GONE = "That game is not in your library anymore"
    fun goneLine(title: String) = "$title was removed or renamed. Pick another game."
    const val FAILED = "The new run did not start"
    const val BACK_TO_KAIZO = "Back to Kaizo IronMON"
}

/**
 * The Play tab with no game to open (2026-09-30, UX audit P1: "the Play tab is a dead end"). It said "No run yet.
 * Randomize one in Kaizo IronMON on Home, then come back here" to a player who only wanted to play a game, and a
 * removed game pointed at "the ROMs tab", which no longer exists. Each case now says what happened and has the
 * button for the next step. Drawn here and not in PlayScreen, which is at ART's verifier limit.
 */
@Composable
internal fun PlayNothing(modifier: Modifier, isRun: Boolean, title: String, failure: String?) {
    val nav = LocalShellNav.current
    Column(modifier.fillMaxSize().padding(16.dp)) {
        when {
            !isRun -> EmptyState(
                PlayNothingCopy.GONE, PlayNothingCopy.goneLine(title),
                actionLabel = PlayNothingCopy.PICK_A_GAME, onAction = nav?.openMyGames,
            )
            failure != null -> EmptyState(
                PlayNothingCopy.FAILED, newRunFailureCopy(failure),
                actionLabel = PlayNothingCopy.BACK_TO_KAIZO, onAction = nav?.openKaizo,
            )
            else -> {
                EmptyState(
                    PlayNothingCopy.NOTHING, PlayNothingCopy.NOTHING_LINE,
                    actionLabel = PlayNothingCopy.PICK_A_GAME, onAction = nav?.openMyGames,
                )
                nav?.let {
                    Spacer(Modifier.height(12.dp))
                    com.ironmonone.app.gen3.Gen3Button(PlayNothingCopy.START_A_RUN, onClick = it.openKaizo)
                }
            }
        }
    }
}
