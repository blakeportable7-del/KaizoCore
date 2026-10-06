package com.ironmonone.app.stream

import com.ironmonone.app.RunRecord

/**
 * The run timer and splits source (/timer, Blake's streamer list item 3, 2026-10-05).
 *
 * What the timer measures is the app's own time played for the attempt (RunClock), the time the run history and the
 * game over card already show: it counts while KaizoCore is in front with the run's game open and its tracker reading,
 * and stops while the app is in the background, the screen is off, or a new run is being made. Fast forward counts as
 * real time, not game time. Once the run is over (StreamHub.ended) the timer holds the time it ended at.
 *
 * A split is the time played when a badge was first earned, kept per run by RunProgress and filed with the run
 * (RunRecord.splits). The best earlier run on the same game and settings file that has splits is the one compared with
 * ([bestForSplits]): a win first, then the most badges, then the fastest to its last badge, then the first attempt to
 * get there. A badge's delta is this run's split minus that run's, negative when ahead. A badge the best run reached
 * and this one has not yet is listed with the best run's time only, so the streamer sees what is coming.
 *
 * Everything here is plain data in and JSON out, so the JVM tests hold the arithmetic.
 */
object StreamTimer {

    /** What the timer page reads when no run is in Play: it stays see-through. */
    const val NO_RUN = "{\"run\":false}"

    private val SPLITS_BEST = compareByDescending<RunRecord> { it.outcome == RunRecord.Outcome.WON }
        .thenByDescending { it.badges }
        .thenBy { it.splits.values.maxOrNull() ?: Int.MAX_VALUE }
        .thenBy { it.attempt }

    /**
     * The run to compare [attempt] on [seed] with: the best of [runs] on [ruleset] that has splits, not counting the run
     * itself (its own record, once it is filed). Null when no earlier run on these settings has any.
     */
    fun bestForSplits(runs: List<RunRecord>, ruleset: String, attempt: Int, seed: String): RunRecord? =
        runs.filter { it.ruleset == ruleset && it.splits.isNotEmpty() && !(it.attempt == attempt && it.seed == seed) }
            .sortedWith(SPLITS_BEST).firstOrNull()

    /**
     * One row a badge, in badge order: "badge" is its number (bit + 1), "at" this run's split in seconds (null: not yet),
     * "best" the best run's (null: it never got that badge), "delta" at minus best when both are there.
     */
    fun rows(current: Map<Int, Int>, best: Map<Int, Int>): List<Map<String, Any?>> =
        (current.keys + best.keys).toSortedSet().map { bit ->
            val at = current[bit]
            val b = best[bit]
            linkedMapOf("badge" to bit + 1, "at" to at, "best" to b, "delta" to if (at != null && b != null) at - b else null)
        }

    /** What the page says about the run it compares with: "Best: attempt 7, 5 badges". */
    fun bestInfo(r: RunRecord): Map<String, Any?> = linkedMapOf(
        "attempt" to r.attempt, "badges" to r.badges, "won" to (r.outcome == RunRecord.Outcome.WON), "seconds" to r.playSeconds,
    )

    /**
     * The timer's JSON. [attempt] is null where no attempt is counted (a Nuzlocke). [ms] is the time played, [running]
     * whether it is counting now, [ended] how the run ended ("LOST", "WON") or null while it goes on.
     */
    fun json(attempt: Int?, ms: Long, running: Boolean, ended: String?, rows: List<Map<String, Any?>>, best: Map<String, Any?>?): String =
        Json.write(linkedMapOf(
            "run" to true, "attempt" to attempt, "ms" to ms.coerceAtLeast(0L), "running" to running, "ended" to ended,
            "splits" to rows, "best" to best,
        ))

    /** "1:02:03" or "42:13", as the page prints a time. */
    fun clock(seconds: Int): String = com.ironmonone.app.playTimeText(seconds.coerceAtLeast(0))

    /** "-0:45", "+1:02:03" or "0:00": a delta as the page prints it. */
    fun delta(seconds: Int): String = when {
        seconds < 0 -> "-" + clock(-seconds)
        seconds > 0 -> "+" + clock(seconds)
        else -> "0:00"
    }
}
