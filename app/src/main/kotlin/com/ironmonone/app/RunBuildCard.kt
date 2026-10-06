package com.ironmonone.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The tracker card's words and button when the tracker will not read the game in Play (RunBuild, 2026-10-06). Its state
 * lives here and not in PlayScreen(), which sits at ART's verifier limit: PlayScreen holds one of these, sets [refused]
 * from its tracker loop (GameMap.resolve's UnreadableBuild) and [onMove] once its NEW RUN code is in scope.
 */
internal class RunBuildCard {
    /** The tracker refused this game's build. Reset when a tracker is made. */
    var refused by mutableStateOf(false)
    var note by mutableStateOf<String?>(null)
    var action by mutableStateOf<Pair<String, () -> Unit>?>(null)
    /** Moves the run (PlayScreen's NEW RUN with move = true); set by PlayScreen. */
    var onMove: () -> Unit = {}
}

/** The card for [session], worked out again when the game in Play changes ([gameKey]) or the tracker refuses it. */
@Composable
internal fun rememberRunBuildCard(store: PrepStore, session: GameSession, gameKey: Int): RunBuildCard {
    val card = remember(session.id) { RunBuildCard() }
    val nav = LocalShellNav.current
    // Read again when the launch's refresh (HnsRefresh) has made this version's Heart & Soul: the button becomes Move.
    val refreshed by HnsRefresh.result
    LaunchedEffect(session.id, gameKey, card.refused, refreshed) {
        if (!card.refused) { card.note = null; card.action = null; return@LaunchedEffect }
        val (state, haveNew) = withContext(Dispatchers.IO) {
            runCatching {
                val st = if (session.isRun) RunBuild.state(store) else RunBuild.State.CURRENT
                val have = session.kind?.let { k -> store.listPrepared().any { it.first.id == k.id } } == true
                st to have
            }.getOrDefault(RunBuild.State.CURRENT to false)
        }
        val kind = session.kind
        card.note = RunBuild.trackerNote(kind, session.isRun, state, haveNew, refused = true)
        card.action = when {
            session.isRun && state == RunBuild.State.OLDER && haveNew -> RunBuild.MOVE_BUTTON to { card.onMove() }
            kind?.isHns == true && nav != null -> RunBuild.REMAKE_BUTTON to nav.openHeartSoul
            kind != null && nav != null -> RunBuild.REMAKE_BUTTON to nav.openPatched
            else -> null
        }
    }
    return card
}
