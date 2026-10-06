package com.ironmonone.app

import com.ironmonone.core.RomKind
import com.ironmonone.tracker.nuzlocke.RunStatus
import java.io.File

/** One game and mode's best run, for the list on Your stats. */
data class BestRun(
    /** The game as the Library names it; a patched build counts as the game it was made from. */
    val game: String,
    /** The mode as the settings file names it: "Kaizo", "Super Kaizo", or the file's own name for one made by hand. */
    val mode: String,
    val won: Boolean,
    val badges: Int,
    val attempt: Int,
    /** The trainer, else the Pokemon, that ended the run; null for a win, or when the run did not record it. */
    val endedBy: String?,
    /** State loads, Time Machine restores, battle retries and File > Restart during that run (its RunRecord). */
    val rewinds: Int = 0,
)

/**
 * The lifetime view (2026-09-30, "Your stats"): what every game and every mode adds up to. Nothing here is tracked
 * for its own sake. It is worked out from what the app already keeps, each thing from the place that holds it:
 *
 * - runs started: the attempt counters (prep/attempts/<game>.txt), which every new run bumps, whether it ended or not;
 * - wins, the best run, the winning streak and the most common way a run ended: the run history (prep/runhistory-<game>.tsv);
 * - time played: RunClock (prep/run-clock.txt);
 * - the DS runs from before the run history existed: the DS tracker's past runs (prep/pastruns-<game>.tsv), which have no mode
 *   and no attempt number, so they count as wins, as time and as ways a run ended, but not as best runs or as streaks;
 * - Nuzlocke runs: the ledgers (prep/nuzlocke/<run>.txt), by how each stands.
 *
 * A file that is missing, half written or full of junk is read as far as it can be: a line that is not a record is
 * skipped and the rest counts. [compute] takes plain data, so a test can hand it any history.
 */
data class CareerStats(
    val runsStarted: Int,
    val wins: Int,
    val playSeconds: Long,
    /** The Pokemon that ended the most runs, with how many. */
    val topCause: Pair<String, Int>?,
    /** The most runs won one straight after another in one game (attempt numbers that follow each other). */
    val longestWinStreak: Int,
    val bests: List<BestRun>,
    val nuzlockeStarted: Int,
    val nuzlockeFinished: Int,
    val nuzlockeLost: Int,
    /** Of [wins], those reached after state loads, retries or restarts (IronMON rules check R10, 2026-09-30). */
    val winsAfterRewinds: Int = 0,
) {
    /** Nothing has been played that this page counts. */
    val isEmpty: Boolean get() = runsStarted == 0 && nuzlockeStarted == 0

    /** Everything [compute] works from, read from where the app keeps it. */
    class Inputs(
        /** Game id to every run of its history that could be read. */
        val records: Map<String, List<RunRecord>>,
        /** Game id to its attempt counter. */
        val attempts: Map<String, Int>,
        /** "game#attempt" to seconds played (RunClock). */
        val clock: Map<String, Long>,
        /** Every run of every DS past-runs log. */
        val pastRuns: List<PastRun>,
        /** How each Nuzlocke ledger stands. */
        val nuzlocke: List<RunStatus>,
    ) {
        companion object {
            fun from(filesDir: File): Inputs {
                val prep = File(filesDir, "prep")
                fun files(dir: File, prefix: String, suffix: String): List<File> =
                    (dir.listFiles { f -> f.isFile && f.name.startsWith(prefix) && f.name.endsWith(suffix) } ?: emptyArray()).sortedBy { it.name }
                val records = LinkedHashMap<String, List<RunRecord>>()
                for (f in files(prep, "runhistory-", ".tsv")) {
                    records[f.name.removePrefix("runhistory-").removeSuffix(".tsv")] = runCatching { RunHistory(f).all() }.getOrDefault(emptyList())
                }
                val attempts = LinkedHashMap<String, Int>()
                for (f in files(File(prep, "attempts"), "", ".txt")) {
                    attempts[f.name.removeSuffix(".txt")] = runCatching { f.readText().trim().toIntOrNull() }.getOrNull()?.coerceAtLeast(0) ?: 0
                }
                val past = files(prep, "pastruns-", ".tsv").flatMap { f -> runCatching { PastRunStore(f).all() }.getOrDefault(emptyList()) }
                val nuzlocke = runCatching { NuzlockeStore(filesDir).list().map { it.header.status } }.getOrDefault(emptyList())
                return Inputs(records, attempts, RunClock.readSeconds(File(prep, "run-clock.txt")), past, nuzlocke)
            }
        }
    }

    companion object {
        val EMPTY = CareerStats(0, 0, 0L, null, 0, emptyList(), 0, 0, 0)

        fun read(filesDir: File): CareerStats = compute(Inputs.from(filesDir))

        /** For the screen: whatever goes wrong while reading is an empty page, not a crash. */
        fun readOrEmpty(filesDir: File): CareerStats = runCatching { read(filesDir) }.getOrDefault(EMPTY)

        /** The game a history file belongs to, as the Library names it. A patched build is the game it was made from. */
        fun gameName(id: String): String {
            val kind = RomKind.byId(id)
            val base = kind?.baseId?.let { RomKind.byId(it) } ?: kind
            return base?.displayName ?: id
        }

        /**
         * The mode a run was played in, from the settings file it was randomized with: "Kaizo", "Chaos Kaizo (Nat. Dex)",
         * or the file's own name. [hnsPool], a Heart & Soul run's pool (RunRecord.hnsPool): its files are the Emerald Nat.
         * Dex ones whatever the pool, and only a Nat. Dex pool run is held to the Nat. Dex rules, so a Vanilla one reads
         * "Kaizo (Vanilla)". Empty, as for every other game, the file decides.
         */
        fun modeLabel(ruleset: String, hnsPool: String = ""): String {
            val stem = ruleset.replace(Regex("(?i)[.]rnqs$"), "").trim()
            if (stem.isEmpty()) return StatsCopy.MODE_UNKNOWN
            val info = RnqsInfo.parse(ruleset)
            val key = info.ruleset ?: return stem
            return RnqsInfo.rulesetLabel(key) + when (hnsPool) {
                com.ironmonone.app.engine.HnsEngine.Pool.VANILLA.name -> StatsCopy.HNS_VANILLA
                com.ironmonone.app.engine.HnsEngine.Pool.NATDEX.name -> StatsCopy.NAT_DEX
                else -> if (info.natDex) StatsCopy.NAT_DEX else ""
            }
        }

        /**
         * What went back in time during [r]: state loads, Time Machine restores, battle retries, a backup restore and
         * File > Restart. A resume after the app closed is not chosen, and is not counted here.
         */
        fun rewinds(r: RunRecord): Int = r.restores + r.resets

        /**
         * Most runs won in a row: a win whose attempt number follows the win before it. Any run that was not a clean win
         * (a win after state loads, retries or restarts is one, since 2026-09-30), or a gap in the numbers, ends it.
         */
        fun winStreak(runs: List<RunRecord>): Int {
            var best = 0
            var cur = 0
            var prev = Int.MIN_VALUE
            for (r in runs.sortedBy { it.attempt }) {
                if (r.outcome != RunRecord.Outcome.WON || rewinds(r) > 0) { cur = 0; prev = r.attempt; continue }
                cur = if (cur > 0 && r.attempt == prev + 1) cur + 1 else 1
                prev = r.attempt
                if (cur > best) best = cur
            }
            return best
        }

        /**
         * The DS past runs from before the run history, the only ones counted from the log: dated before the history's
         * first record began. Since the history began, every DS run that ends files a record at the moment it logs its
         * past run, so a later past run is a record's own (counted from the record), the first end of a run whose loss
         * was retried (Retry left its line in the log), or a library game the DS log took for a run before rc32 gated it:
         * none of them is another run (rc32 audit P2 #14). With no record at all, every past run is older.
         */
        fun olderPastRuns(pastRuns: List<PastRun>, recs: List<RunRecord>): List<PastRun> {
            val historyBegan = recs.mapNotNull { r -> (r.started.takeIf { it > 0 } ?: r.ended).takeIf { it > 0 } }.minOrNull()
                ?: return pastRuns
            return pastRuns.filter { it.date < historyBegan }
        }

        fun compute(inp: Inputs): CareerStats {
            val all = inp.records.flatMap { (id, rs) -> rs.map { id to it } }
            val recs = all.map { it.second }
            // A DS run from before the run history is counted from its past run; any later past run is not a run of its own.
            val older = olderPastRuns(inp.pastRuns, recs)

            val wins = recs.count { it.outcome == RunRecord.Outcome.WON } + older.count { it.progress == PastRun.WON }
            val winsAfterRewinds = recs.count { it.outcome == RunRecord.Outcome.WON && rewinds(it) > 0 }

            // The counters count every run that was started. A game with results and no counter (a partial restore) still has its results.
            val perGame = (inp.records.keys + inp.attempts.keys).sumOf { id -> maxOf(inp.attempts[id] ?: 0, inp.records[id]?.size ?: 0) }
            val started = maxOf(perGame, recs.size + older.size)

            val seconds = inp.clock.values.sum() + older.sumOf { it.seconds.coerceAtLeast(0).toLong() }

            val causes = recs.filter { it.outcome == RunRecord.Outcome.LOST }.mapNotNull { it.killer?.name } +
                older.filter { it.progress != PastRun.WON }.map { it.enemy.name }
            val top = causes.map { it.trim() }.filter { it.isNotEmpty() }.groupBy { it.lowercase() }.values
                .map { group -> (group.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key ?: group.first()) to group.size }
                .sortedWith(compareByDescending<Pair<String, Int>> { it.second }.thenBy { it.first.lowercase() })
                .firstOrNull()

            // Attempt numbers count per settings file (PrepStore), so a streak is counted within one file's runs.
            val streak = inp.records.values.flatMap { runs -> runs.groupBy { it.ruleset }.values }.maxOfOrNull { winStreak(it) } ?: 0

            // A custom game is its own row, never a best of the mode it is named after (IronMON rules check R2).
            // So is a run that went without what its rules add: "Kaizo (50% levels)" (R4).
            val bests = all.groupBy { (id, r) -> gameName(id) to CustomRuns.label(modeLabel(r.ruleset, r.hnsPool), r.custom) + (r.variant.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: "") }
                .map { (key, runs) ->
                    val best = runs.map { it.second }.sortedBy { it.attempt }.reduce { b, r -> if (beats(r, b)) r else b }
                    BestRun(
                        game = key.first, mode = key.second, won = best.outcome == RunRecord.Outcome.WON, badges = best.badges, attempt = best.attempt,
                        endedBy = if (best.outcome == RunRecord.Outcome.WON) null else (best.trainer.takeIf { it.isNotBlank() } ?: best.killer?.name?.takeIf { it.isNotBlank() }),
                        rewinds = rewinds(best),
                    )
                }
                .sortedWith(compareBy<BestRun> { it.game.lowercase() }.thenBy { it.mode.lowercase() })

            return CareerStats(
                winsAfterRewinds = winsAfterRewinds,
                runsStarted = started, wins = wins, playSeconds = seconds, topCause = top, longestWinStreak = streak, bests = bests,
                nuzlockeStarted = inp.nuzlocke.size,
                nuzlockeFinished = inp.nuzlocke.count { it == RunStatus.COMPLETE },
                nuzlockeLost = inp.nuzlocke.count { it == RunStatus.OVER },
            )
        }
    }
}

/**
 * Everything Your stats says (2026-09-30), in one place so the copy rules are checked on all of it: no em dash, nothing
 * about how the work is made, plain and dry. CareerStatsScreen has no wording of its own.
 */
internal object StatsCopy {
    const val TITLE = "Your stats"
    const val EMPTY = "Nothing here yet. Start a Kaizo IronMON run or a Nuzlocke and your numbers start here."
    const val RUNS_STARTED = "Runs started"
    const val RUNS_WON = "Runs won"
    const val TIME_PLAYED = "Time played in runs"
    const val ENDED_MOST = "Ended the most runs"
    const val STREAK = "Longest winning streak"
    const val NOTHING_YET = "Nothing yet"
    const val BEST_TITLE = "Best run in each game and mode"
    const val NO_BEST = "No finished run yet."
    const val NUZLOCKE_TITLE = "Nuzlocke runs"
    const val NUZLOCKE_STARTED = "Started"
    const val NUZLOCKE_FINISHED = "Finished, Champion beaten"
    const val NUZLOCKE_LOST = "Lost"
    const val NOTE = "Best runs and winning streaks come from runs saved since run history was added. " +
        "A run that went back in time with state loads, retries or restarts says so, and does not count toward a streak."
    const val MODE_UNKNOWN = "Mode not recorded"
    const val NAT_DEX = " (Nat. Dex)"
    /** A Heart & Soul run of the Vanilla pool (Gen 1 to 3), held to Emerald's rules. */
    const val HNS_VANILLA = " (Vanilla)"

    fun number(n: Int): String = n.toString()

    fun timePlayed(seconds: Long): String {
        val minutes = seconds.coerceAtLeast(0) / 60
        val h = minutes / 60
        val m = minutes % 60
        return if (h > 0) "$h h $m min" else "$m min"
    }

    fun cause(top: Pair<String, Int>?): String =
        top?.let { (name, n) -> if (n == 1) "$name, 1 run" else "$name, $n runs" } ?: NOTHING_YET

    fun streak(n: Int): String = when (n) { 0 -> NOTHING_YET; 1 -> "1 run"; else -> "$n runs in a row" }

    fun badges(n: Int): String = if (n == 1) "1 badge" else "$n badges"

    /** The game and mode, then how far the best run got. */
    fun bestTitle(b: BestRun): String = "${b.game}, ${b.mode}"

    fun bestResult(b: BestRun): String =
        (if (b.won) "Won" else badges(b.badges) + (b.endedBy?.let { ", lost to $it" } ?: "")) + ". Attempt ${b.attempt}." +
            (if (b.rewinds > 0) " After ${rewindCount(b.rewinds)}." else "")

    /** "2 state loads, retries or restarts", counted. */
    fun rewindCount(n: Int): String = if (n == 1) "1 state load, retry or restart" else "$n state loads, retries or restarts"

    /** Runs won (R10); how many of them went back in time is [winsNote], a line of its own. */
    fun wins(n: Int): String = number(n)

    /**
     * How many wins went back in time (R10), under Runs won, or null when none did. It was part of the number, which
     * then took the row and squeezed "Runs won" to nothing at a large font (rc32 audit P2 #15).
     */
    fun winsNote(afterRewinds: Int): String? = if (afterRewinds == 0) null else "$afterRewinds after state loads, retries or restarts"

    /** Every fixed line, and one of each shape a number makes, for the copy-rule test. */
    val all: List<String> = listOf(
        TITLE, EMPTY, RUNS_STARTED, RUNS_WON, TIME_PLAYED, ENDED_MOST, STREAK, NOTHING_YET, BEST_TITLE, NO_BEST,
        NUZLOCKE_TITLE, NUZLOCKE_STARTED, NUZLOCKE_FINISHED, NUZLOCKE_LOST, NOTE, MODE_UNKNOWN,
        timePlayed(0), timePlayed(4_500), timePlayed(90_000), cause(null), cause("Zangoose" to 1), cause("Zangoose" to 9),
        streak(0), streak(1), streak(3),
        bestResult(BestRun("Game", "Kaizo", won = true, badges = 8, attempt = 4, endedBy = null)),
        bestResult(BestRun("Game", "Kaizo", won = false, badges = 1, attempt = 4, endedBy = "Leader Brock")),
        bestResult(BestRun("Game", "Kaizo", won = false, badges = 3, attempt = 4, endedBy = null)),
        bestResult(BestRun("Game", "Kaizo", won = true, badges = 8, attempt = 4, endedBy = null, rewinds = 2)),
        wins(3), winsNote(1)!!, winsNote(2)!!,
    )
}
