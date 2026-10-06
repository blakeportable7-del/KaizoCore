package com.ironmonone.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File

/**
 * Every run on every platform: how far it got and how it ended, so hundreds of attempts read
 * as progress (Blake, 2026-09-29, roadmap item 6: personal best, attempts, time played, how
 * each run ended, a death card). The DS tracker's own past-run log (PastRun, SeedLogger's
 * format) stays as it is; this sits beside it for every game.
 *
 * One TSV per game under the prep folder (runhistory-<game id>.tsv), one line per run, newest
 * last. A line never holds a tab or a newline of its own: names are flattened when written.
 */
data class RunRecord(
    val attempt: Int,
    /** The seed as the Run tab shows it (16 hex digits), or "" when unknown. */
    val seed: String,
    /** The settings file the run was randomized with. */
    val ruleset: String,
    /** Epoch milliseconds the run started (0 when unknown) and ended. */
    val started: Long,
    val ended: Long,
    /** Time actually played, when the run was timed; else 0. */
    val playSeconds: Int,
    val outcome: Outcome,
    val badges: Int,
    /** The Pokemon that fainted (or the lead when a run ended some other way). */
    val lead: Mon?,
    /** The Pokemon that did it; null outside a battle. */
    val killer: Mon?,
    /** The trainer, "Leader Brock" or "Rival May"; "" for a wild Pokemon or none. */
    val trainer: String,
    val location: String,
    /** State loads, Time Machine restores and battle retries during the run (RunEvents): the integrity line. */
    val restores: Int = 0,
    /** Resumes after the app closed mid-game (RunEvents RESUME): not chosen, but they go back up to minutes. */
    val resumes: Int = 0,
    /** File > Restart during the run (RunEvents RESET): back to the last in-game save. */
    val resets: Int = 0,
    /** The run began from the last run's in-game save, kept by the player (RunEvents KEPT_SAVE). */
    val keptSave: Boolean = false,
    /** Its settings file was not one KaizoCore comes with: a custom game, counted apart (CustomRuns). */
    val custom: Boolean = false,
    /** What its official file ran without, "50% levels" or "no PART 2" (ExtraPasses.variant); "" when nothing. */
    val variant: String = "",
    /** It was built from a run code (RunEvents CODE). */
    val fromCode: Boolean = false,
    /**
     * The game-over rule it was played under when that is not its settings file's own ("Entire party faints"), or
     * "changed during the run" when it went back to the file's own before the end; "" when it was the file's (R12).
     */
    val lossRule: String = "",
    /**
     * Badge bit to the seconds played when the run earned that badge (RunProgress): the run's splits, which the stream's
     * timer compares a later attempt with. Empty for a run filed before splits, or one that earned no badge in sight.
     */
    val splits: Map<Int, Int> = emptyMap(),
    /**
     * Heart & Soul's pool for the run ("VANILLA" or "NATDEX", HnsPool), which decides whether it was held to the Nat. Dex
     * rules: its settings files are the Emerald Nat. Dex ones either way. "" for every other game, and for a Heart & Soul
     * run filed before the pool was recorded.
     */
    val hnsPool: String = "",
) {
    data class Mon(val species: Int, val name: String, val level: Int)

    enum class Outcome {
        LOST,
        WON,
        /** A new run was started before this one ended. */
        ENDED,
    }

    fun encode(): String = listOf(
        attempt.toString(), seed, flat(ruleset), started.toString(), ended.toString(), playSeconds.toString(),
        outcome.name, badges.toString(), mon(lead), mon(killer), flat(trainer), flat(location), restores.toString(),
        resumes.toString(), resets.toString(), if (keptSave) "1" else "0", if (custom) "1" else "0", flat(variant),
        if (fromCode) "1" else "0", flat(lossRule), splitsText(splits), hnsPool,
    ).joinToString("\t")

    companion object {
        /** Splits as a line keeps them: "0:754,1:1820", badge bit then seconds, in badge order. */
        fun splitsText(splits: Map<Int, Int>): String = splits.entries.sortedBy { it.key }.joinToString(",") { "${it.key}:${it.value}" }

        /** [splitsText] read back; anything that is not "bit:seconds" is skipped. */
        fun parseSplits(text: String): Map<Int, Int> = text.split(',').mapNotNull { part ->
            val p = part.trim().split(':')
            val bit = p.getOrNull(0)?.toIntOrNull()?.takeIf { it in 0..31 } ?: return@mapNotNull null
            val at = p.getOrNull(1)?.toIntOrNull()?.takeIf { it >= 0 } ?: return@mapNotNull null
            bit to at
        }.toMap()

        private fun flat(s: String) = s.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ').replace('|', '/')
        private fun mon(m: Mon?) = m?.let { "${it.species}|${flat(it.name)}|${it.level}" } ?: ""
        private fun mon(s: String): Mon? {
            val p = s.split('|'); if (p.size < 3) return null
            return Mon(p[0].toIntOrNull() ?: return null, p[1], p[2].toIntOrNull() ?: 0)
        }

        fun decode(line: String): RunRecord? {
            val p = line.split('\t')
            if (p.size < 12) return null
            return RunRecord(
                attempt = p[0].toIntOrNull() ?: return null, seed = p[1], ruleset = p[2],
                started = p[3].toLongOrNull() ?: 0L, ended = p[4].toLongOrNull() ?: 0L,
                playSeconds = p[5].toIntOrNull() ?: 0,
                outcome = runCatching { Outcome.valueOf(p[6]) }.getOrNull() ?: return null,
                badges = p[7].toIntOrNull() ?: 0, lead = mon(p[8]), killer = mon(p[9]),
                trainer = p[10], location = p[11], restores = p.getOrNull(12)?.toIntOrNull() ?: 0,
                resumes = p.getOrNull(13)?.toIntOrNull() ?: 0,
                resets = p.getOrNull(14)?.toIntOrNull() ?: 0, keptSave = p.getOrNull(15) == "1",
                custom = p.getOrNull(16) == "1", variant = p.getOrNull(17).orEmpty(), fromCode = p.getOrNull(18) == "1",
                lossRule = p.getOrNull(19).orEmpty(), splits = parseSplits(p.getOrNull(20).orEmpty()),
                hnsPool = p.getOrNull(21).orEmpty(),
            )
        }
    }
}

class RunHistory(private val file: File) {
    private val runs = ArrayList<RunRecord>()

    init {
        runCatching { if (file.isFile) file.readLines(Charsets.UTF_8).mapNotNullTo(runs) { RunRecord.decode(it) } }
    }

    /** Adds [r] and saves; a run already recorded for the same attempt and seed is replaced. */
    fun record(r: RunRecord) {
        runs.removeAll { it.attempt == r.attempt && it.seed == r.seed }
        runs += r
        SafeWrite.text(file, runs.joinToString("") { it.encode() + "\n" })
    }

    fun all(): List<RunRecord> = runs.toList()

    /** The run filed for [attempt] on [seed], or null. */
    fun find(attempt: Int, seed: String): RunRecord? = runs.lastOrNull { it.attempt == attempt && it.seed == seed }

    /** The best run on [r]'s settings file other than [r] itself, or null when there is none. */
    fun bestOther(r: RunRecord): RunRecord? =
        runs.filter { it.ruleset == r.ruleset && !(it.attempt == r.attempt && it.seed == r.seed) }
            .sortedWith(BEST_FIRST).firstOrNull()

    /**
     * The best run, for [ruleset] or across all: a win first, then the most badges; among equals
     * the first attempt that got there, since that is when the player first reached it.
     */
    fun best(ruleset: String? = null): RunRecord? =
        runs.filter { ruleset == null || it.ruleset == ruleset }.sortedWith(BEST_FIRST).firstOrNull()

    /** Whether [r] is the best run so far (a new personal best, or equal to the first that reached it). */
    fun isBest(r: RunRecord): Boolean = best(r.ruleset)?.let { it.attempt == r.attempt && it.seed == r.seed } == true

    fun totalPlaySeconds(): Long = runs.sumOf { it.playSeconds.toLong() }

    /** The Pokemon that ended the most runs, most first: (name, count). */
    fun commonKillers(limit: Int = 5): List<Pair<String, Int>> =
        runs.filter { it.outcome == RunRecord.Outcome.LOST }.mapNotNull { it.killer?.name }
            .groupingBy { it }.eachCount().entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .take(limit).map { it.key to it.value }

    private companion object {
        /** A win first, then the most badges; among equals the first attempt that got there. */
        val BEST_FIRST = compareByDescending<RunRecord> { it.outcome == RunRecord.Outcome.WON }
            .thenByDescending { it.badges }
            .thenBy { it.attempt }
    }
}

/** Whether [a] got further than [b]: a win beats anything short of one, then more badges. */
fun beats(a: RunRecord, b: RunRecord): Boolean {
    val aw = a.outcome == RunRecord.Outcome.WON; val bw = b.outcome == RunRecord.Outcome.WON
    return if (aw != bw) aw else a.badges > b.badges
}

/**
 * The game-over popup's run lines (roadmap item 6, the death card): the run as filed and how it
 * stands against the best other run on the same settings file. [earlier] is a popup that found
 * the attempt already over: the player played on after the loss, so the card shows the loss
 * that ended the run, not the one just now.
 */
data class DeathCard(val game: String, val record: RunRecord, val best: RunRecord?, val earlier: Boolean) {
    /** A new personal best: past an earlier run on these settings. The first run on them is not one. */
    val newBest: Boolean get() = best != null && beats(record, best)

    /**
     * The first line as (label, text): what ended the run, or null for a win (the title says it)
     * and when nothing is known. A trainer names the place well enough, so the location is only
     * added for a wild Pokemon, which keeps the line short enough for the popup.
     */
    fun headline(): Pair<String, String>? {
        val r = record
        if (r.outcome == RunRecord.Outcome.WON) return null
        val where = r.location.takeIf { it.isNotBlank() }
        val k = r.killer
        val text = when {
            k != null && r.trainer.isNotBlank() -> "Lv.${k.level} ${k.name} (${r.trainer})"
            k != null -> "Lv.${k.level} ${k.name}" + (where?.let { ", $it" } ?: "")
            else -> where ?: return null
        }
        return (if (earlier) "FIRST LOSS" else if (k != null) "LOST TO" else "ENDED ON") to text
    }

    /** Badges, time played and, when any, what went back in time: "1 badge, 42:13 played, 2 state loads or retries". */
    fun statsText(): String {
        val r = record
        val badges = if (r.badges == 1) "1 badge" else "${r.badges} badges"
        val back = if (r.restores > 0 || r.resumes > 0 || r.resets > 0 || r.keptSave)
            ", " + integrityText(r.restores, r.resumes, r.resets, r.keptSave).replaceFirstChar { it.lowercase() } else ""
        // What the run went without, and where it came from (R4, R7).
        val how = listOfNotNull(r.variant.takeIf { it.isNotBlank() }, "from a run code".takeIf { r.fromCode }, lossRuleText(r.lossRule))
            .joinToString("") { ", $it" }
        return badges + (if (r.playSeconds > 0) ", ${playTimeText(r.playSeconds)} played" else "") + back + how
    }

    /** What the Share button sends: the run's line, and a new best when it is one. */
    fun shareText(): String = runSummaryLine(record, game) +
        if (!newBest) "" else if (CareerStats.rewinds(record) > 0) " New best, after state loads, retries or restarts." else " New best."

    /** The best other run, when this is not a new best: "Best: 3 badges, attempt 7". */
    fun bestText(): String? {
        val b = best ?: return null
        if (newBest) return null
        val what = if (b.outcome == RunRecord.Outcome.WON) "won" else if (b.badges == 1) "1 badge" else "${b.badges} badges"
        return "Best: $what, attempt ${b.attempt}"
    }
}

/**
 * The integrity line (roadmap item 7): what went back in time during the run, from its own
 * event log. "No state loads or retries", or "2 state loads or retries, 1 resume after the app
 * closed". It says only what the log measures: save states, Time Machine, Retry the battle and
 * resumes; an in-game reset is the game's own and is not seen.
 */
fun integrityText(restores: Int, resumes: Int, resets: Int = 0, keptSave: Boolean = false): String {
    val main = when (restores) { 0 -> "No state loads or retries"; 1 -> "1 state load or retry"; else -> "$restores state loads or retries" }
    val back = when (resumes) { 0 -> ""; 1 -> ", 1 resume after the app closed"; else -> ", $resumes resumes after the app closed" }
    // File > Restart and a kept save, since 2026-09-30 (IronMON rules check R5, R11): said, never folded into the count.
    val restart = when (resets) { 0 -> ""; 1 -> ", 1 restart"; else -> ", $resets restarts" }
    val kept = if (keptSave) ", started from the last run's in-game save" else ""
    return main + back + restart + kept
}

/** A record's [RunRecord.lossRule] as the card and the shared line say it, or null for the file's own rule. */
fun lossRuleText(rule: String): String? = when {
    rule.isBlank() -> null
    rule == LOSS_RULE_CHANGED -> "game over rule changed during the run"
    else -> "game over when " + rule.replaceFirstChar { it.lowercase() }
}

const val LOSS_RULE_CHANGED = "changed during the run"

/**
 * What a run's record keeps of its game-over rule (R12): [inEffect]'s label when it is not [settingsName]'s own rule,
 * else [LOSS_RULE_CHANGED] when the run's log shows it was changed on the way, else "".
 */
fun lossRuleAtEnd(inEffect: com.ironmonone.tracker.LossCondition, settingsName: String, label: String, changedOnTheWay: Boolean): String = when {
    settingsName.isBlank() -> ""
    inEffect != com.ironmonone.tracker.LossCondition.forSettingsName(settingsName) -> label
    changedOnTheWay -> LOSS_RULE_CHANGED
    else -> ""
}

/** "1:02:03" or "42:13": play time as a run timer shows it. */
fun playTimeText(seconds: Int): String {
    val h = seconds / 3600; val m = seconds % 3600 / 60; val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

/**
 * The death card's line, plain enough to paste anywhere: what ended the run, where, how far it
 * got and the seed. [game] is the game's title as the Library shows it.
 */
fun runSummaryLine(r: RunRecord, game: String): String {
    val notes = listOfNotNull("custom".takeIf { r.custom }, r.variant.takeIf { it.isNotBlank() }, "from a run code".takeIf { r.fromCode },
        lossRuleText(r.lossRule))
    val head = "Attempt ${r.attempt}, $game" +
        (if (r.ruleset.isNotBlank()) " (${r.ruleset.removeSuffix(".rnqs")}${notes.joinToString("") { ", $it" }})" else "")
    val how = when (r.outcome) {
        RunRecord.Outcome.WON -> "won"
        RunRecord.Outcome.ENDED -> "ended"
        RunRecord.Outcome.LOST -> r.killer?.let { k ->
            "lost to Lv.${k.level} ${k.name}" + (if (r.trainer.isNotBlank()) " (${r.trainer})" else "")
        } ?: "lost"
    }
    val where = if (r.location.isNotBlank() && r.outcome == RunRecord.Outcome.LOST) " on ${r.location}" else ""
    val badges = if (r.badges == 1) "1 badge" else "${r.badges} badges"
    val time = if (r.playSeconds > 0) ", ${playTimeText(r.playSeconds)}" else ""
    val seed = if (r.seed.isNotBlank()) " Seed ${r.seed}." else ""
    return "$head: $how$where. $badges$time. ${integrityText(r.restores, r.resumes, r.resets, r.keptSave)}.$seed"
}

/**
 * The run that just ended, from the tracker state it ended on: [gba] for Game Boy and GBA
 * games, [nds] for DS. The fainted Pokemon (or the lead), the one on the other side, where.
 */
/** The integrity count: state loads, Time Machine restores and battle retries. */
fun rewinds(events: List<RunEvents.Entry>): Int =
    events.count { it.kind == RunEvents.Kind.LOAD || it.kind == RunEvents.Kind.RESTORE || it.kind == RunEvents.Kind.RETRY }

fun runRecordAtEnd(
    attempt: Int, seed: String, ruleset: String, started: Long, playSeconds: Int, won: Boolean,
    gba: com.ironmonone.tracker.TrackerState?, nds: com.ironmonone.tracker.nds.NdsTrackerState?,
    trainerName: String?, restores: Int = 0, resumes: Int = 0, resets: Int = 0, keptSave: Boolean = false,
    custom: Boolean = false, variant: String = "", fromCode: Boolean = false, lossRule: String = "", hnsPool: String = "",
    splits: Map<Int, Int> = emptyMap(),
): RunRecord {
    val lead = gba?.party?.let { p -> (p.firstOrNull { it.mon.curHp == 0 } ?: p.firstOrNull())?.let { RunRecord.Mon(it.mon.species, it.speciesName, it.mon.level) } }
        ?: nds?.party?.let { p -> (p.firstOrNull { it.mon.curHp == 0 } ?: p.firstOrNull())?.let { RunRecord.Mon(it.mon.species, it.speciesName, it.mon.level) } }
    val killer = if (won) null else gba?.enemy?.let { RunRecord.Mon(it.species, it.speciesName, it.level) }
        ?: if (won) null else nds?.enemy?.let { RunRecord.Mon(it.mon.species, it.speciesName, it.mon.level) }
    return RunRecord(
        attempt = attempt, seed = seed, ruleset = ruleset, started = started, ended = System.currentTimeMillis(),
        playSeconds = playSeconds, outcome = if (won) RunRecord.Outcome.WON else RunRecord.Outcome.LOST,
        badges = Integer.bitCount(gba?.badges ?: nds?.badges ?: 0),
        lead = lead, killer = killer, trainer = if (won) "" else trainerName.orEmpty(),
        location = gba?.routeName ?: nds?.areaName.orEmpty(), restores = restores, resumes = resumes,
        resets = resets, keptSave = keptSave, custom = custom, variant = variant, fromCode = fromCode, lossRule = lossRule,
        hnsPool = hnsPool,
        splits = splits,
    )
}

/**
 * The filing rule, apart from the app's stores so it can be tested. The first end of an attempt
 * stays its record unless Retry reopened it ([reopened]); otherwise [build] makes the new record,
 * given the retries counted so far, and it replaces any earlier one. Returns the record and
 * whether it is the one filed earlier.
 */
fun fileRunEnd(history: RunHistory, attempt: Int, seed: String, reopened: Boolean, build: (restores: Int) -> RunRecord): Pair<RunRecord, Boolean> {
    val filed = history.find(attempt, seed)
    if (filed != null && !reopened) return filed to true
    val r = build(filed?.restores ?: 0)
    history.record(r)
    return r to false
}

/**
 * Whether Retry undid [filed] after it was filed, by the run's own event log. The app's memory of a retry dies with the
 * process, so after a restart the run's real end was never filed (rc33 audit P1 #49); the RETRY line on disk outlives it.
 * A re-filing gets a newer end, so an older RETRY no longer reopens it.
 */
fun retriedAfter(filed: RunRecord?, events: List<RunEvents.Entry>?): Boolean =
    filed != null && events?.any { it.kind == RunEvents.Kind.RETRY && it.at > filed.ended } == true

/** Retry undid the loss filed for [attempt]: count the retry on it. False when nothing was filed. */
fun fileRetry(history: RunHistory, attempt: Int, seed: String): Boolean {
    val filed = history.find(attempt, seed) ?: return false
    history.record(filed.copy(restores = filed.restores + 1))
    return true
}

/**
 * Files runs as they end and holds the card the game-over popup shows. The first end of an
 * attempt is its record: a player who plays on after a loss (Continue playing) has still lost
 * that run, so a later loss in the same attempt shows the filed one ([DeathCard.earlier]).
 * Retry the battle undoes the loss: the record counts the retry and the next end replaces it.
 */
object RunHistoryHook {
    var card by mutableStateOf<DeathCard?>(null)
        private set

    /** Attempts whose filed loss Retry undid, "game#attempt#seed": the next end replaces it. */
    private val reopened = HashSet<String>()

    private fun key(game: String, attempt: Int, seed: String) = "$game#$attempt#$seed"

    /**
     * The game-over popup opened: file the run in its game's history and build its card. Called
     * once per opening (the latch), never for a staged Demo battle or a Library game.
     */
    fun recordRunEnd(
        store: PrepStore, session: GameSession, tracker: com.ironmonone.tracker.GbaTracker?,
        gba: com.ironmonone.tracker.TrackerState?, nds: com.ironmonone.tracker.nds.NdsTrackerState?, won: Boolean,
    ) {
        card = null
        val kind = session.kind ?: return
        if (!session.isRun || Demo.mode != null) return
        runCatching {
            val attempt = store.attempt(kind.id)
            val seed = store.lastSeedText()
            val history = RunHistory(store.runHistoryFile(kind))
            val k = key(kind.id, attempt, seed)
            // The run's own event log (RunEvents) is the count; it dies with the run's notes.
            val events = store.runEvents(session)?.entries()
            // A win after a loss the player did not retry is still a lost run: the loss stays.
            val (r, earlier) = fileRunEnd(history, attempt, seed,
                reopened = k in reopened || retriedAfter(history.find(attempt, seed), events)) { restores ->
                val trainer = gba?.opponentTrainerId?.takeIf { gba.inBattle && !gba.isWildBattle }
                    ?.let { id -> tracker?.trainer(id) }?.let { "${it.className} ${it.name}".trim() }
                runRecordAtEnd(
                    attempt = attempt, seed = seed, ruleset = store.loadLastRun()?.second.orEmpty(),
                    started = store.currentRunFor(kind).lastModified(), playSeconds = RunClock.of(RunClock.key(kind.id, attempt)),
                    won = won, gba = gba, nds = nds, trainerName = trainer,
                    restores = events?.let { rewinds(it) } ?: restores,
                    resumes = events?.count { it.kind == RunEvents.Kind.RESUME } ?: 0,
                    resets = events?.count { it.kind == RunEvents.Kind.RESET } ?: 0,
                    keptSave = events?.any { it.kind == RunEvents.Kind.KEPT_SAVE } == true,
                    custom = store.lastRunCustom(), variant = store.lastRunVariant().orEmpty(),
                    fromCode = events?.any { it.kind == RunEvents.Kind.CODE } == true,
                    lossRule = store.loadLastRun()?.second?.let { name ->
                        val ds = nds != null
                        val rule = if (ds) TrackerOptions.dsLossCondition else TrackerOptions.lossCondition
                        lossRuleAtEnd(rule, RunModeName.of(store.settingsFile(name), kind.family), if (ds) TrackerOptions.dsLossLabel(rule) else rule.label,
                            changedOnTheWay = events?.any { it.kind == RunEvents.Kind.RULE } == true)
                    }.orEmpty(),
                    hnsPool = HnsPool.ofRun(store)?.name.orEmpty(),
                    // The time each badge was earned, for a later attempt's timer to compare with (RunProgress).
                    splits = RunProgress.splitsOf(store.files, attempt, seed),
                )
            }
            reopened -= k
            card = DeathCard(session.title, r, history.bestOther(r), earlier = earlier)
        }
    }

    /** Retry the battle worked: the filed loss is undone, and the retry is counted on it. */
    fun retried(store: PrepStore, session: GameSession) {
        val kind = session.kind ?: return
        if (!session.isRun || Demo.mode != null) return
        runCatching {
            val attempt = store.attempt(kind.id)
            val seed = store.lastSeedText()
            if (fileRetry(RunHistory(store.runHistoryFile(kind)), attempt, seed)) reopened += key(kind.id, attempt, seed)
        }
    }
}
