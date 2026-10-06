package com.ironmonone.app.stream

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
 *
 * It also tells ObsLink when a battle starts and ends and when a run ends, so OBS can switch scenes and save its replay
 * buffer by itself (2026-10-05). That needs no STREAM: it is keyed on the tracker's flag and the latch alone.
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
    // Twitch chat commands answer from the same snapshot (Stream Connect, twitch/ChatCommands), so it is built while they listen too.
    val chat = com.ironmonone.app.stream.twitch.TwitchChat.link?.wantsSnapshot?.collectAsState()?.value == true
    LaunchedEffect(on || chat, gba, nds, marksVersion, session.id, com.ironmonone.app.gbaView.view, com.ironmonone.app.dsView.key) {
        if (!on && !chat) return@LaunchedEffect
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
    StreamExtras(on, session, platform, run, gba, nds, latch, ended)
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
    // OBS's own reactions (ObsLink, 2026-10-05): every game's battle flag, and a Kaizo IronMON run's end as the game-over
    // latch has it, whether STREAM is on or not (OBS may take the phone's picture some other way). Only noted here: the
    // link's own thread does the talking, so a closed OBS or a lost Wi-Fi never reaches the game.
    val filesDir = androidx.compose.ui.platform.LocalContext.current.applicationContext.filesDir
    // load() reads the sealed password through the Keystore, so it runs off the main thread (2026-10-05).
    LaunchedEffect(Unit) { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { ObsLink.app.load(filesDir) } }
    val inBattle = gba?.inBattle ?: nds?.inBattle ?: false
    LaunchedEffect(inBattle) { ObsLink.app.battle(inBattle) }
    LaunchedEffect(ended) { ObsLink.app.runEnded(ended != null, ended == com.ironmonone.tracker.RunOutcome.LOST) }
    // Play composes this for as long as a game is open in it, so its going is the game stopping: Play left for another
    // tab, the game closed, or another game opened (session.id). The effects above stop with it and would leave OBS on
    // the battle or game over scene, so OBS goes back to the game scene (2026-10-05). Here and not in PlayScreen, which
    // is at ART's verifier size limit.
    androidx.compose.runtime.DisposableEffect(session.id) { onDispose { ObsLink.app.gameStopped() } }
}

/**
 * The game over card and the run timer's feed (streamer list items 1 and 3, 2026-10-05), beside StreamFeed and called
 * from it, so Play's own call is unchanged.
 *
 *  - Which run the timer follows goes to the hub whatever the stream is doing: it is two values, and the timer is right
 *    the moment STREAM goes on. RunClock counts it (Play's poll loops), RunProgress keeps its splits.
 *  - The splits table is read from files (the run's splits, its game's history) only while the stream is on, off the
 *    main thread, again whenever the badges change or the run ends.
 *  - The game over card is built from the latch Play already has ([ended], the popup's), the death card the popup shows
 *    (RunHistoryHook.card, only when it is this run's) and the popup's line, on the main thread like the popup.
 */
@Composable
private fun StreamExtras(
    on: Boolean,
    session: GameSession,
    platform: Platform,
    run: RunIds,
    gba: TrackerState?,
    nds: NdsTrackerState?,
    latch: GameOverLatch,
    ended: com.ironmonone.tracker.RunOutcome?,
) {
    val files = androidx.compose.ui.platform.LocalContext.current.applicationContext.filesDir
    val counted = session.isRun && !com.ironmonone.app.NuzlockeTracking.inPlay()
    LaunchedEffect(session.id, run.attempt, run.seed, counted) {
        StreamHub.timerRun = session.kind?.id?.takeIf { session.isRun && com.ironmonone.app.Demo.mode == null }
            ?.let { StreamHub.TimerRun(com.ironmonone.app.RunClock.key(it, run.attempt), run.attempt.takeIf { counted }) }
    }
    val badgeBits = gba?.badges ?: nds?.badges ?: 0
    LaunchedEffect(on, session.id, run.attempt, run.seed, badgeBits, ended) {
        if (!on) return@LaunchedEffect
        val kind = session.kind
        if (!session.isRun || kind == null) { StreamHub.splitRows = emptyList(); StreamHub.splitBest = null; return@LaunchedEffect }
        val (rows, best) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                val store = com.ironmonone.app.PrepStore(files)
                val ruleset = store.loadLastRun()?.takeIf { it.first == kind.id }?.second.orEmpty()
                val history = com.ironmonone.app.RunHistory(store.runHistoryFile(kind)).all()
                val best = StreamTimer.bestForSplits(history, ruleset, run.attempt, run.seed)
                StreamTimer.rows(com.ironmonone.app.RunProgress.splitsOf(files, run.attempt, run.seed), best?.splits.orEmpty()) to best
            }.getOrDefault(emptyList<Map<String, Any?>>() to null)
        }
        StreamHub.splitRows = rows
        StreamHub.splitBest = best?.let { StreamTimer.bestInfo(it) }
    }
    // Read here, in composition, so a card filed or a prize card made a moment after the latch brings the card up to date.
    val card = com.ironmonone.app.RunHistoryHook.card
    val prize = com.ironmonone.app.GachaMon.prizeCard
    LaunchedEffect(on, ended, card, prize, run.attempt, run.seed) {
        if (!on) return@LaunchedEffect
        StreamHub.gameOver = if (ended == null) "null" else Json.write(StreamSnapshot.gameOver(ending(session, platform, run, gba, nds, latch, ended, card, prize)))
    }
}

/** The ending as StreamSnapshot.gameOver takes it, from what the popup has. */
private fun ending(
    session: GameSession, platform: Platform, run: RunIds, gba: TrackerState?, nds: NdsTrackerState?, latch: GameOverLatch,
    ended: com.ironmonone.tracker.RunOutcome, card: com.ironmonone.app.DeathCard?, prize: com.ironmonone.app.GachaMonEntry?,
): StreamSnapshot.Ending {
    // The death card is the last one filed: only this run's is this run's.
    val mine = card?.takeIf { it.record.attempt == run.attempt && it.record.seed == run.seed }
    val won = ended == com.ironmonone.tracker.RunOutcome.WON
    // The popup's line, the same one it shows (DeathQuotes.shown is drawn once per run and kept).
    val quote = runCatching {
        val family = com.ironmonone.app.gameOverFamily(platform)
        val cause = latch.dsCause ?: com.ironmonone.tracker.nds.NdsRunOver.STANDARD
        if (family == com.ironmonone.app.GameOverFamily.DS) {
            com.ironmonone.app.DeathQuotes.shown(com.ironmonone.app.DeathQuotes.dsSource(cause), run.attempt, com.ironmonone.app.ndsRunOverLines(cause), forLoss = !won)
        } else {
            com.ironmonone.app.DeathQuotes.shown(com.ironmonone.app.DeathQuotes.PC_SOURCE, run.attempt, com.ironmonone.app.PcGameOverQuotes, forLoss = !won)
        }
    }.getOrNull()
    // GachaMon is a Game Boy Advance run's: the prize card made at this game over, else the fallen lead's own capture.
    val gba3 = platform == Platform.GBA
    val leadSpecies = mine?.record?.lead?.species ?: latch.team.firstOrNull { it.fainted }?.species
    val prizeHere = prize?.takeIf { gba3 && it.card.seedNumber == run.attempt }
    val capture = if (!gba3 || prizeHere != null || leadSpecies == null) null
        else com.ironmonone.app.GachaMon.recent.lastOrNull { it.card.pokemonId == leadSpecies && it.card.seedNumber == run.attempt }
    return StreamSnapshot.Ending(
        outcome = ended, title = session.title, attempt = run.attempt, seed = run.seed.ifEmpty { null },
        counted = !com.ironmonone.app.NuzlockeTracking.inPlay(), card = mine, quote = quote, team = latch.team,
        badgeBits = gba?.badges ?: nds?.badges ?: 0,
        gachamon = prizeHere ?: capture, gachamonFrom = if (prizeHere != null) "prize" else if (capture != null) "lead" else null,
    )
}
