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
) {
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
            latch.retried()
            if (battleStartState != null) {
                val ok = retro?.unserializeState(battleStartState) == true
                onStatus(if (ok) "Back to the start of the battle." else "Could not restore the battle.")
            }
        },
        onSaveAttempt = {
            val kind = session.kind
            if (kind == null || !session.isRun) false
            else store.saveAttempt(kind, store.attempt(), store.lastSeedText(), runCatching { retro?.serializeState() }.getOrNull())
        },
        onNewGame = { latch.close(); onNewGame() },
        onGrade = onGrade,
    )
}
