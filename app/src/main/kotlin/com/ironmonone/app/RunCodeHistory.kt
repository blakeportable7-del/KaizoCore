package com.ironmonone.app

/**
 * A run code's seed against this phone's run history (IronMON rules check R7, 2026-09-30). A code rebuilds its seed as
 * a fresh attempt, and a player's own code from a run they died on is one: nothing said so, and the record was clean.
 * Now the build asks first, saying which attempt played the seed and how it ended, and the new run's record says it
 * came from a code (RunEvents CODE).
 */
internal object RunCodeHistory {
    /** The run [history] holds for [seedText] on [settingsName], the newest first; null when it holds none. */
    fun playedBefore(history: RunHistory, seedText: String, settingsName: String): RunRecord? =
        history.all().filter { it.seed == seedText && it.ruleset == settingsName }.maxByOrNull { it.ended }

    fun playedBefore(store: PrepStore, plan: RunCodes.Plan.Ready): RunRecord? =
        runCatching { playedBefore(RunHistory(store.runHistoryFile(plan.kind)), "%016x".format(plan.code.seed), plan.settings.name) }.getOrNull()

    /** "You played this seed as attempt 7, lost to Leader Brock. Built again, it is a new attempt, and its record says it came from a code." */
    fun playedLine(r: RunRecord): String {
        val how = when (r.outcome) {
            RunRecord.Outcome.WON -> "won"
            RunRecord.Outcome.ENDED -> "left before it ended"
            RunRecord.Outcome.LOST -> r.trainer.takeIf { it.isNotBlank() }?.let { "lost to $it" }
                ?: r.killer?.let { "lost to Lv.${it.level} ${it.name}" } ?: "lost"
        }
        return "You played this seed as attempt ${r.attempt}, $how. Built again, it is a new attempt, and its record says it came from a code."
    }
}
