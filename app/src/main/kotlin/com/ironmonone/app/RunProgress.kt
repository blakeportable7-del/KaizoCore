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
 *
 * 2026-10-05 (the stream's timer and splits): it also keeps which badges the run has been seen with ([Seen.bits]) and,
 * for each badge seen being earned, the run's time played at that moment (RunClock) as its split ([Seen.splits], by
 * badge bit). A badge already there at the first look of a run, or in a file from before splits, was not seen being
 * earned and gets no split. A state loaded from before a gym takes no split away: the first time a badge was earned
 * stands. The splits go into the run's record when it is filed (RunRecord.splits), which is what a later attempt on the
 * same settings file is compared with.
 */
internal object RunProgress {
    const val FILE = "run-progress.txt"

    data class Seen(
        val attempt: Int, val seed: String, val badges: Int, val lead: RunRecord.Mon?, val location: String,
        /** Every badge bit the run has been seen with; -1 when not known (a file from before splits, or no bits given). */
        val bits: Int = -1,
        /** Badge bit to the run's seconds played when that badge was first seen earned. */
        val splits: Map<Int, Int> = emptyMap(),
    )

    fun file(filesDir: File): File = File(filesDir, "prep/run-progress.txt")

    /** The run's game, attempt and seed, read once per run rather than on every poll; [forget] drops them. */
    private var run: Triple<String, Int, String>? = null
    /** What was last handed to the writer, for the run in [run]. */
    private var last: Seen? = null

    /** A Game Boy or GBA run's poll (KeptSave.observe hands it over). */
    fun observe(store: PrepStore, session: GameSession, s: TrackerState?) {
        if (s == null || s.unreadable) return
        val lead = s.party.firstOrNull { !it.mon.isEgg }?.let { RunRecord.Mon(it.mon.species, it.speciesName, it.mon.level) }
        note(store, session, Integer.bitCount(s.badges), lead, s.routeName.orEmpty(), bits = s.badges, seconds = ::playedNow)
    }

    /** A DS run's poll. */
    fun observe(store: PrepStore, session: GameSession, s: NdsTrackerState?) {
        if (s == null || !s.located) return
        val lead = s.party.firstOrNull { !it.mon.isEgg }?.let { RunRecord.Mon(it.mon.species, it.speciesName, it.mon.level) }
        note(store, session, Integer.bitCount(s.badges), lead, s.areaName, bits = s.badges, seconds = ::playedNow)
    }

    /** The run's time played so far (RunClock), for a badge's split: asked only when a badge is new. */
    private fun playedNow(): Int = run?.let { RunClock.of(RunClock.key(it.first, it.second)) } ?: 0

    /**
     * [badges] is how many the game shows; [bits] which ones (-1: not known, so no splits); [seconds] the run's time
     * played, asked only when a badge is new.
     */
    @Synchronized
    internal fun note(
        store: PrepStore, session: GameSession, badges: Int, lead: RunRecord.Mon?, location: String,
        bits: Int = -1, seconds: () -> Int = { 0 },
    ) {
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
        // Which badges were seen being earned. At the first look of a run, or with a file from before splits, the ones
        // already there are where the run starts from, and get no split.
        val known = before?.bits?.takeIf { it >= 0 }
        val seen = if (bits < 0) known ?: -1 else (known ?: bits) or bits
        val fresh = if (bits < 0 || known == null) 0 else bits and known.inv()
        val splits = if (fresh == 0) before?.splits.orEmpty() else {
            val at = seconds()
            before?.splits.orEmpty() + (0 until 32).filter { fresh and (1 shl it) != 0 }.associateWith { at }
        }
        if (before != null && before.badges == most && before.lead == lead && before.bits == seen && before.splits == splits) return
        last = Seen(ids.second, ids.third, most, lead, location, seen, splits).also { DiskWriter.write(file(store.files), format(it)) }
    }

    /** The splits of the run [attempt] on [seed] as they stand: what [note] last kept, else the file's. Empty for none. */
    @Synchronized
    fun splitsOf(filesDir: File, attempt: Int, seed: String): Map<Int, Int> =
        (last?.takeIf { it.attempt == attempt && it.seed == seed } ?: read(filesDir)?.takeIf { it.attempt == attempt && it.seed == seed })
            ?.splits.orEmpty()

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
        if (s.bits >= 0) append("bits=").append(s.bits).append('\n')
        if (s.splits.isNotEmpty()) append("splits=").append(RunRecord.splitsText(s.splits)).append('\n')
    }

    fun parse(text: String): Seen? {
        val m = text.lines().mapNotNull { l -> l.indexOf('=').takeIf { it > 0 }?.let { l.substring(0, it) to l.substring(it + 1) } }.toMap()
        val attempt = m["attempt"]?.toIntOrNull() ?: return null
        val seed = m["seed"]?.takeIf { it.isNotBlank() } ?: return null
        val lead = m["lead"]?.split('|')?.takeIf { it.size == 3 }?.let { p ->
            p[0].toIntOrNull()?.let { RunRecord.Mon(it, p[1], p[2].toIntOrNull() ?: 0) }
        }
        return Seen(attempt, seed, m["badges"]?.toIntOrNull()?.coerceIn(0, 16) ?: 0, lead, m["location"].orEmpty(),
            bits = m["bits"]?.trim()?.toIntOrNull()?.takeIf { it >= 0 } ?: -1, splits = RunRecord.parseSplits(m["splits"].orEmpty()))
    }

    private fun flat(s: String) = s.replace('\n', ' ').replace('\r', ' ').replace('|', '/')
}
