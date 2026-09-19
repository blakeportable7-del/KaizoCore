package com.ironmonone.app

import androidx.compose.runtime.mutableStateListOf
import java.io.File

/**
 * "Hide stats until summary shown": whether this attempt has opened a Pokemon
 * summary in the game yet (Tracker.Data.hasCheckedSummary, set by Program.lua:552
 * when sMonSummaryScreen is non-zero). Until then, on a randomized game, your
 * Pokemon's card shows only its icon, name and level. Kept per attempt, so a
 * new run hides again.
 */
object SummaryChecks {
    private val checked = mutableStateListOf<Int>()
    private var file: File? = null

    fun checked(attempt: Int): Boolean = attempt in checked

    fun mark(attempt: Int) {
        if (attempt in checked) return
        checked += attempt
        runCatching { file?.let { it.parentFile?.mkdirs(); it.writeText(checked.joinToString("\n", postfix = "\n")) } }
    }

    fun load(f: File) {
        file = f
        checked.clear()
        runCatching { if (f.exists()) f.forEachLine { l -> l.trim().toIntOrNull()?.let { checked += it } } }
    }
}
