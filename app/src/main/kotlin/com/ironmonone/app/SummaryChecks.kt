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

    /**
     * Whether the viewed Pokemon's stats are hidden now. DataHelper.lua:140-151: on a
     * randomized game, until this attempt has checked a summary, the VIEWED Pokemon, yours or
     * the opponent's, is drawn from a blank stand-in that keeps only its icon, name and level.
     * Never on a Game Boy game ([generation] 1 or 2): the Game Boy references have no summary
     * screen to watch. Their GameSettings.initialize sets only WRAM and ROM addresses (Gen 1
     * reference GameSettings.lua:138-223) and never calls setEwramAddresses (:505), the one
     * place sMonSummaryScreen is set (:526); with the option on, Program.lua:253-257 reads that
     * nil address on every update, a Lua error (the Gen 2 reference is the same). Hiding the
     * card anyway hid it for good: nothing on a Game Boy game ever marks a summary as seen.
     */
    fun hidesStats(optionOn: Boolean, gameDataRandomized: Boolean, attempt: Int, generation: Int): Boolean =
        optionOn && generation >= 3 && gameDataRandomized && !checked(attempt)

    /** [hidesStats] with the option as set. */
    fun hides(attempt: Int, gameDataRandomized: Boolean, generation: Int = 3): Boolean =
        hidesStats(TrackerOptions.hideStatsUntilSummary, gameDataRandomized, attempt, generation)

    fun mark(attempt: Int) {
        if (attempt in checked) return
        checked += attempt
        save()
    }

    /** A new run took attempt [attempt]: an earlier run under the same number (another settings file's) is not it. */
    fun forget(attempt: Int) {
        if (!checked.remove(attempt)) return
        save()
    }

    /** Whole or not at all, off the main thread (DiskWriter): it was rewritten in place (rc32 audit P2 #65). */
    private fun save() {
        file?.let { DiskWriter.write(it, if (checked.isEmpty()) "" else checked.joinToString("\n", postfix = "\n")) }
    }

    /**
     * GameOptionsScreen.lua:204-210: turning "Hide stats until summary shown" on sets
     * hasCheckedSummary false, so the card hides again until a summary is opened. Only the
     * current attempt's mark matters, so every mark goes.
     */
    fun forgetAll() {
        checked.clear()
        save()
    }

    fun load(f: File) {
        file = f
        checked.clear()
        runCatching { DiskWriter.read(f)?.lineSequence()?.forEach { l -> l.trim().toIntOrNull()?.let { checked += it } } }
    }
}
