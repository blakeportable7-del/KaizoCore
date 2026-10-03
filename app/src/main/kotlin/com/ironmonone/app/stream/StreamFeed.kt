package com.ironmonone.app.stream

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.ironmonone.app.GameOverLatch
import com.ironmonone.app.GameSession
import com.ironmonone.app.RunIds
import com.ironmonone.app.StatMarks
import com.ironmonone.core.Platform
import com.ironmonone.tracker.GbaTracker
import com.ironmonone.tracker.TrackerState
import com.ironmonone.tracker.nds.NdsTracker
import com.ironmonone.tracker.nds.NdsTrackerState

/**
 * What Play hands the stream page, moved out of PlayScreen, which sits at ART's verifier limit.
 *
 * The state is built only while the stream is on (rc32 audit P3 #50): it was built on every tracker change, with file
 * reads on the main thread, for a server that was not running. Turning STREAM on changes [on], so the state as it is
 * goes out at once. Every change the panel would redraw for is a new snapshot; the hub dedupes identical JSON, so the
 * page hears only of a real one. The snapshot is built here on the main thread, where StatMarks is written: built off
 * it, it read the move lists while a battle changed them, a rare crash (rc33 audit P0-10). Only the JSON is written
 * off it.
 *
 * In a double or triple battle the page shows what the phone's swap shows (rc34): the battle views are keys here, so a
 * tap of the swap goes out at once, not with the next change in the game.
 *
 * The post-game browser's data, every species as randomized, is read out of the game only once it can be served
 * (rc32 audit P3 #51): the stream is on and the Kaizo IronMON run is over ([latch], what GameOverHost hands
 * StreamHub.ended). It was read at every tracker start, thousands of reads competing with the game and the first
 * polls, for a page that showed it only after the end. Until it is built /dex.json refuses, as it does before the end.
 */
@Composable
internal fun StreamFeed(
    on: Boolean,
    session: GameSession,
    platform: Platform,
    run: RunIds,
    statMarks: StatMarks,
    marksVersion: Int,
    gba: TrackerState?,
    nds: NdsTrackerState?,
    ref: GbaTracker?,
    nref: NdsTracker?,
    latch: GameOverLatch,
) {
    LaunchedEffect(on, gba, nds, marksVersion, session.id, com.ironmonone.app.gbaView.view, com.ironmonone.app.dsView.key) {
        if (!on) return@LaunchedEffect
        val notes = StreamSnapshot.Notes(
            marksOf = { statMarks.of(it) }, noteOf = { statMarks.noteFor(it) },
            movesSeenOf = { statMarks.movesSeenFor(it).map { m -> m.name } }, abilityOf = { statMarks.abilityFor(it) },
            encountersOf = { statMarks.totalEncounters(it) }, lastSeenLevelOf = { statMarks.lastLevelSeen(it) },
            routeSeenOf = { statMarks.seenOnRoute(it).size },
        )
        val shown = StreamSnapshot.Run(
            session.title, platform.name, run.attempt, session.tracked,
            run.seed.takeIf { session.isRun && it.isNotEmpty() }, session.isRun, session.kind?.generation?.number ?: 3)
        val snap = StreamSnapshot.build(shown, gba, nds, notes, ref, com.ironmonone.app.gbaView, com.ironmonone.app.dsView)
        val json = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { Json.write(snap) }
        StreamHub.publish(json, shown.attempt)
    }
    val ended = if (latch.applies) latch.outcome else null
    LaunchedEffect(on, ended, ref, nref) {
        // A Game Boy game has no dex here: say so, rather than serve the last GBA or DS game's (rc33 audit P1 #34).
        if (ref == null && nref == null) { StreamHub.dex = "[]"; return@LaunchedEffect }
        StreamHub.dex = null
        if (!on || ended == null) return@LaunchedEffect
        StreamHub.dex = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            runCatching {
                Json.write(StreamSnapshot.dex(ref, nref, if (ref != null) (if (ref.expandedSpeciesIds) 1284 else 412) else 650))
            }.getOrDefault("[]")
        }
    }
}
