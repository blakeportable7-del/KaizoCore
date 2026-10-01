package com.ironmonone.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import com.swordfish.libretrodroid.GLRetroView

/**
 * The game-over popup and its actions, moved out of PlayScreen() whole. That
 * method sits at ART's verifier limit, and this was one of its larger blocks.
 * Draws nothing unless [latch] is open; what it shows comes from the latch
 * (the run as it ended), never from the tracker's live read.
 */
@Composable
internal fun GameOverHost(
    latch: GameOverLatch,
    family: GameOverFamily,
    hidden: Boolean,
    ndsState: com.ironmonone.tracker.nds.NdsTrackerState?,
    trackerState: com.ironmonone.tracker.TrackerState?,
    store: PrepStore,
    session: GameSession,
    retro: GLRetroView?,
    battleStartState: ByteArray?,
    raHardcore: Boolean,
    spriteFor: (Int) -> ImageBitmap?,
    onStatus: (String) -> Unit,
    onInspectLog: (java.io.File) -> Unit,
    onNewGame: () -> Unit,
    onGrade: (() -> Unit)?,
    /** The game picture's bounds on screen: the popup sits over it. */
    gameFrame: androidx.compose.ui.geometry.Rect? = null,
) {
    // Here and not in PlayScreen, which sits at the verifier's limit: the popup is for Kaizo IronMON runs only.
    val filesDir = androidx.compose.ui.platform.LocalContext.current.applicationContext.filesDir
    latch.applies = PlayRules.ironmonGameOver(PlayRules.kind(session, filesDir))
    // The stream's run-over view and its randomized data wait for this, not for the tracker's live read.
    val ended = if (latch.applies) latch.outcome else null
    androidx.compose.runtime.SideEffect { com.ironmonone.app.stream.StreamHub.ended = ended }
    if (!latch.open || hidden) return
    val team: List<GameOverMon> = ndsState?.party?.map { GameOverMon(it.mon.species, it.speciesName, it.mon.level, it.mon.curHp == 0, it.mon.shiny) }
        ?: trackerState?.party?.map { GameOverMon(it.mon.species, it.speciesName, it.mon.level, it.mon.curHp == 0, it.mon.shiny) }
        ?: emptyList()
    val ctx = androidx.compose.ui.platform.LocalContext.current
    // The staged screenshot modes have no run and no battle: they read a log
    // from the app's external files dir and offer Retry, so the popup shows
    // every action it can carry. Never taken outside Demo.mode.
    val logFile = (if (session.isRun) session.kind?.let { store.currentRunLogFor(it) } else null)
        ?: Demo.mode?.let { ctx.getExternalFilesDir(null)?.let { d -> java.io.File(d, "demo.log") }?.takeIf { it.isFile } }
    // The death card: the run as RunHistoryHook filed it. The staged screenshot modes show a
    // made-up one so the layout can be checked; never outside Demo.mode.
    val card = if (Demo.mode != null) Demo.deathCard(store.attempt()) else RunHistoryHook.card.takeIf { session.isRun }
    GameOverDialog(
        family = family,
        won = latch.outcome == com.ironmonone.tracker.RunOutcome.WON,
        attempt = store.attempt(),
        team = team,
        spriteOf = { m -> if (ndsState != null) remember(m.species, m.shiny) { PcAssets.dsSprite(ctx, m.species, m.shiny) } else spriteFor(m.species) },
        dsCause = latch.dsCause,
        canRetry = (battleStartState != null || Demo.mode != null) && !raHardcore,
        onInspectLog = logFile?.let { f -> { onInspectLog(f) } },
        onContinue = { latch.close() },
        onRetry = {
            // The reference's loadTempSaveState: back to the moment the battle began.
            // A failed restore leaves the popup up: closing it would leave the
            // player in the lost battle with nothing to press.
            if (battleStartState != null) {
                val ok = retro?.unserializeState(battleStartState) == true
                if (ok) { latch.retried(); store.runEvents(session)?.add(RunEvents.Kind.RETRY, "battle start"); RunHistoryHook.retried(store, session) }
                onStatus(if (ok) "Back to the start of the battle." else "Could not restore the battle.")
            } else latch.retried()
        },
        onSaveAttempt = {
            val kind = session.kind
            if (kind == null || !session.isRun) false
            else {
                // The state is taken where it always was; the copying (a whole ROM) goes off the main thread.
                val state = runCatching { retro?.serializeState() }.getOrNull()
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    store.saveAttempt(kind, store.attempt(), store.lastSeedText(), state)
                }
            }
        },
        onNewGame = { latch.close(); onNewGame() },
        onGrade = onGrade,
        gameFrame = gameFrame,
        card = card,
        onShare = card?.let { c ->
            {
                runCatching {
                    val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(android.content.Intent.EXTRA_TEXT, c.shareText())
                    }
                    ctx.startActivity(android.content.Intent.createChooser(send, "Share this run"))
                }
            }
        },
    )
}
