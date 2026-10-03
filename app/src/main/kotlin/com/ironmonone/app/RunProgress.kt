package com.ironmonone.app

import com.ironmonone.tracker.TrackerState
import com.ironmonone.tracker.nds.NdsTrackerState
import java.io.File

/**
 * How far the run in play has got, as Play last saw it: the most badges, the lead and where (rc32 audit P3 #58). A run
 * replaced by a new one before it ended is filed then (PrepStore.fileOpenRunAsEnded), when no tracker is reading the
 * game, and it went into the history with 0 badges and no lead whatever it had reached: Your stats' best run and the
 * death card's Best never counted it.
 *
 * Kept in prep/run-progress.txt with the attempt and seed it belongs to, written (off the main thread, DiskWriter) only
 * when the badges or the lead change, and deleted with the run's other notes on a new run. Only what the tracker
 * already shows the player.
 */
internal object RunProgress {
    const val FILE = "run-progress.txt"

    data class Seen(val attempt: Int, val seed: String, val badges: Int, val lead: RunRecord.Mon?, val location: String)

    fun file(filesDir: File): File = File(filesDir, "prep/run-progress.txt")

    /** The run's game, attempt and seed, read once per run rather than on every poll; [forget] drops them. */
    private var run: Triple<String, Int, String>? = null
    /** What was last handed to the writer, for the run in [run]. */
    private var last: Seen? = null

    /** A Game Boy or GBA run's poll (KeptSave.observe hands it over). */
    fun observe(store: PrepStore, session: GameSession, s: TrackerState?) {
        if (s == null || s.unreadable) return
        val lead = s.party.firstOrNull { !it.mon.isEgg }?.let { RunRecord.Mon(it.mon.species, it.speciesName, it.mon.level) }
        note(store, session, Integer.bitCount(s.badges), lead, s.routeName.orEmpty())
    }

    /** A DS run's poll. */
    fun observe(store: PrepStore, session: GameSession, s: NdsTrackerState?) {
        if (s == null || !s.located) return
        val lead = s.party.firstOrNull { !it.mon.isEgg }?.let { RunRecord.Mon(it.mon.species, it.speciesName, it.mon.level) }
        note(store, session, Integer.bitCount(s.badges), lead, s.areaName)
    }

    @Synchronized
    internal fun note(store: PrepStore, session: GameSession, badges: Int, lead: RunRecord.Mon?, location: String) {
        if (!session.isRun || Demo.mode != null || lead == null) return
        val game = session.kind?.id ?: return
        // The run's attempt and seed, and what the file holds for them, read once per run rather than on every poll.
        val ids = run?.takeIf { it.first == game } ?: Triple(game, store.attempt(game), store.lastSeedText()).also { ids ->
            run = ids
            last = read(store.files)?.takeIf { it.attempt == ids.second && it.seed == ids.third }
        }
        if (ids.third.isBlank()) return
        val before = last
        // The most badges the run has had: a state loaded from before a gym does not take one away.
        val most = maxOf(badges, before?.badges ?: 0)
        if (before != null && before.badges == most && before.lead == lead) return
        last = Seen(ids.second, ids.third, most, lead, location).also { DiskWriter.write(file(store.files), format(it)) }
    }

    /** A new run is going in: the next poll reads its attempt and seed again. */
    @Synchronized
    fun forget() { run = null; last = null }

    /** What the file says, through the writer (DiskWriter.read); null when there is none or it cannot be read. */
    fun read(filesDir: File): Seen? = DiskWriter.read(file(filesDir))?.let { parse(it) }

    fun format(s: Seen): String = buildString {
        append("attempt=").append(s.attempt).append('\n')
        append("seed=").append(s.seed).append('\n')
        append("badges=").append(s.badges).append('\n')
        s.lead?.let { append("lead=").append(it.species).append('|').append(flat(it.name)).append('|').append(it.level).append('\n') }
        append("location=").append(flat(s.location)).append('\n')
    }

    fun parse(text: String): Seen? {
        val m = text.lines().mapNotNull { l -> l.indexOf('=').takeIf { it > 0 }?.let { l.substring(0, it) to l.substring(it + 1) } }.toMap()
        val attempt = m["attempt"]?.toIntOrNull() ?: return null
        val seed = m["seed"]?.takeIf { it.isNotBlank() } ?: return null
        val lead = m["lead"]?.split('|')?.takeIf { it.size == 3 }?.let { p ->
            p[0].toIntOrNull()?.let { RunRecord.Mon(it, p[1], p[2].toIntOrNull() ?: 0) }
        }
        return Seen(attempt, seed, m["badges"]?.toIntOrNull()?.coerceIn(0, 16) ?: 0, lead, m["location"].orEmpty())
    }

    private fun flat(s: String) = s.replace('\n', ' ').replace('\r', ' ').replace('|', '/')
}
