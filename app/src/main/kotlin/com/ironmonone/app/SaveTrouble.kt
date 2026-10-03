package com.ironmonone.app

import android.content.Context

/**
 * Saves that fail where no screen is waiting for the answer: the battery save written as a game is left or paused,
 * the auto-save's writer, the Nuzlocke ledger's (rc33 audit P0-8). On a full phone each one failed in silence and the
 * next session opened on older progress. Each failure is logged; the first of each kind in a minute is also shown as
 * a toast, so a full phone is told, not told on every pause.
 */
object SaveTrouble {
    @Volatile private var app: Context? = null
    private val lastShown = java.util.concurrent.ConcurrentHashMap<String, Long>()
    const val QUIET_MS = 60_000L

    const val BATTERY = "battery"
    const val AUTO = "auto"
    const val LEDGER = "ledger"
    const val CLOUD = "cloud"
    /** The files the background writer keeps (DiskWriter): tracker notes, play time, tracker settings. */
    const val NOTES = "notes"
    /** A cheat list, a slot lock or the hardcore switch, saved from a tap in Play (rc32 audit P2 #16). */
    const val SETTING = "setting"
    /** Save this attempt, refused for want of space (rc32 audit P2 #66). */
    const val ATTEMPT = "attempt"

    /** SafeWrite answers yes or no, so the ledger's line cannot say why. */
    const val LEDGER_FAILED = "Could not save the Nuzlocke ledger. If this phone is out of space, free some; it saves again with your next change."
    const val NOTES_FAILED = "Could not save your tracker notes and settings. If this phone is out of space, free some; they save again with your next change."

    fun init(context: Context) { app = context.applicationContext }

    /** [kind] is which save failed (BATTERY, AUTO, LEDGER); [sentence] is what the player reads. */
    fun report(kind: String, sentence: String, now: Long = System.currentTimeMillis()) {
        runCatching { android.util.Log.w("KaizoCore", "save failed ($kind): $sentence") }
        if (!shouldShow(kind, now)) return
        val c = app ?: return
        runCatching {
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                android.widget.Toast.makeText(c, sentence, android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }

    /** True for the first failure of [kind], and again once [QUIET_MS] have passed since the last one shown. */
    internal fun shouldShow(kind: String, now: Long): Boolean {
        val last = lastShown[kind]
        if (last != null && now - last < QUIET_MS) return false
        lastShown[kind] = now
        return true
    }

    internal fun forget() = lastShown.clear()
}
