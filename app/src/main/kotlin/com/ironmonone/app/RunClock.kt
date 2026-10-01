package com.ironmonone.app

import java.io.File

/**
 * Time played per attempt, for the run history and the death card (roadmap item 6). It
 * counts between tracker reads: each read adds the time since the last one, up to a few
 * seconds, so a paused or backgrounded game (no reads) adds nothing and a gap never counts
 * as play. Kept per run in prep/run-clock.txt, "game#attempt=seconds" per line (attempts count per game).
 */
object RunClock {
    /** Longest gap between two reads that still counts as play, in milliseconds. */
    const val MAX_STEP_MS = 3_000L

    private val seconds = HashMap<String, Long>()
    private val carryMs = HashMap<String, Long>()
    private var lastKey = ""
    private var lastAt = 0L
    private var dirtySince = 0L
    private var file: File? = null

    fun load(f: File) {
        file = f
        seconds.clear(); carryMs.clear(); lastKey = ""; lastAt = 0L
        seconds.putAll(readSeconds(f))
    }

    /**
     * The seconds [f] holds, by run key, as [load] reads them and as Your stats does (CareerStats): a line that is not
     * "game#attempt=seconds" is skipped, and a file that is missing or cannot be read is an empty map.
     */
    fun readSeconds(f: File): Map<String, Long> {
        val out = HashMap<String, Long>()
        runCatching {
            if (f.exists()) f.forEachLine { line ->
                val cut = line.lastIndexOf('=')
                if (cut > 0) {
                    val s = line.substring(cut + 1).trim().toLongOrNull()
                    if (s != null && s >= 0) out[line.substring(0, cut).trim()] = s
                }
            }
        }
        return out
    }

    /** The key of a run: the game and its attempt number, since attempts count per game. */
    fun key(game: String, attempt: Int): String = "$game#$attempt"

    /** Seconds played in the run [key]. */
    fun of(key: String): Int = (seconds[key] ?: 0L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

    /**
     * A tracker read of the run [key] at [nowMs] (a monotonic clock). The first read of a run,
     * or one after a gap longer than [MAX_STEP_MS], only sets the mark.
     */
    fun observe(key: String, nowMs: Long) {
        if (key != lastKey) { lastKey = key; lastAt = nowMs; return }
        val step = nowMs - lastAt
        lastAt = nowMs
        if (step <= 0 || step > MAX_STEP_MS) return
        val ms = (carryMs[key] ?: 0L) + step
        seconds[key] = (seconds[key] ?: 0L) + ms / 1000
        carryMs[key] = ms % 1000
        if (dirtySince == 0L) dirtySince = nowMs
        if (nowMs - dirtySince >= 10_000L) save()
    }

    /**
     * A new run takes [key]: attempts count per settings file since 2026-09-30, so a run of another file can have
     * played under it. Its seconds move to a key of their own, "[key]~n", so Your stats (which adds every line)
     * still counts them, and the new run starts at 0.
     */
    fun retire(key: String) {
        val had = seconds.remove(key) ?: return
        carryMs.remove(key)
        if (lastKey == key) lastKey = ""
        var i = 1
        while (seconds.containsKey("$key~$i")) i++
        seconds["$key~$i"] = had
        save()
    }

    fun save() {
        dirtySince = 0L
        val f = file ?: return
        runCatching {
            f.parentFile?.mkdirs()
            SafeWrite.text(f, seconds.entries.sortedBy { it.key }.joinToString("") { "${it.key}=${it.value}\n" })
        }
    }
}
