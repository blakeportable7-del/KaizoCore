package com.ironmonone.app

import java.io.File

/**
 * The run's events: every time its game was put back to another moment, a
 * save state loaded (and a load undone), a Time Machine restore, a battle
 * retried from the game-over screen, and a resume after the app closed
 * mid-game (CrashResume). Each with when it happened and which slot or point.
 *
 * Groundwork for the integrity line Blake agreed to (2026-09-29); nothing
 * shows it yet. It lives with the run's other notes, beside StatMarks' files
 * (prep/integrity.txt), dies with them on a new run (PrepStore.clearRunNotes)
 * and goes with them into a saved attempt (PrepStore.saveAttempt).
 *
 * One line per event, appended: "time<TAB>kind<TAB>slot<TAB>detail", time in
 * epoch milliseconds. A line cut off by a crash is skipped when read, and the
 * next event starts on a line of its own.
 */
class RunEvents(val file: File) {

    enum class Kind(val key: String) {
        LOAD("load"), UNDO("undo"), RESTORE("restore"), RETRY("retry"), RESUME("resume"),
        /** File > Restart: back to the last in-game save, as a soft reset is (2026-09-30, IronMON rules check R11). */
        RESET("reset"),
        /** The new seed kept the last run's in-game save, by the player's choice (R5). */
        KEPT_SAVE("keptsave"),
        /** The run was built from a run code, its detail saying when this game's history already held the seed (R7). */
        CODE("code"),
        /** "Game is considered over when" set, during the run, to other than its settings file's own rule (R12). */
        RULE("rule");

        companion object {
            fun of(key: String): Kind? = entries.firstOrNull { it.key == key }
        }
    }

    data class Entry(val at: Long, val kind: Kind, val slot: String, val detail: String)

    fun add(kind: Kind, slot: String, detail: String = "", at: Long = System.currentTimeMillis()) {
        runCatching {
            file.parentFile?.mkdirs()
            java.io.RandomAccessFile(file, "rw").use { f ->
                val len = f.length()
                // A crash mid-append leaves a line with no end; do not run this one into it.
                val lead = if (len > 0 && f.run { seek(len - 1); read() } != '\n'.code) "\n" else ""
                f.seek(len)
                f.write((lead + listOf(at.toString(), kind.key, clean(slot), clean(detail)).joinToString("\t") + "\n").toByteArray(Charsets.UTF_8))
            }
        }
    }

    fun entries(): List<Entry> = runCatching {
        if (!file.isFile) return emptyList()
        file.readLines(Charsets.UTF_8).mapNotNull { line ->
            val p = line.split('\t')
            if (p.size != 4) return@mapNotNull null
            val at = p[0].toLongOrNull() ?: return@mapNotNull null
            val kind = Kind.of(p[1]) ?: return@mapNotNull null
            Entry(at, kind, p[2], p[3])
        }
    }.getOrDefault(emptyList())

    private fun clean(s: String) = s.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ')
}
