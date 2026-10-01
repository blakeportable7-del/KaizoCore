package com.ironmonone.app

import java.io.File

/**
 * Coming back to a game the app closed in the middle of.
 *
 * While the Play screen has a game up, prep/playing.txt names it, and the
 * screen deletes it when it closes the normal way (leaving the tab, the app
 * finishing). Still there at launch, it means the process died with the game
 * open: a crash, Android freeing memory behind a phone call, a swipe from
 * recents, a dead battery. That game's auto slot (AutoSave) is loaded as soon
 * as its core is up, so the run comes back where it was: to the moment it was
 * left when Android paused the app first, or at most AutoSave.EVERY_MS back
 * after a crash. Only a slot stamped for the run in play is loaded, the same
 * check as every load (PlayScreen.loadState), and every such resume goes into
 * the run's events (RunEvents).
 *
 * A resume that itself kills the app must not do it again on every launch:
 * the marker says "resuming" while the load runs, and a launch that finds
 * that leaves the slot in File > States for the player to try.
 */
object CrashResume {

    enum class Plan { RESUME, TRIED, NONE }

    /** What the status line says when a game left the normal way opens where it was left. */
    const val RETURNED = "Back where you left off. File, Restart starts from the title screen."

    /** What the marker said: the session that was open, and whether a resume of it was under way. */
    data class Left(val sessionId: String, val resuming: Boolean)

    /** Markers already read in this process: only a launch finds what the last process left. */
    private val read = HashSet<String>()

    fun parse(marker: File): Left? = runCatching {
        val t = marker.takeIf { it.isFile }?.readText()?.trim() ?: return null
        val cut = t.indexOf(' ')
        if (cut <= 0) return null
        when (t.substring(0, cut)) {
            "playing" -> Left(t.substring(cut + 1), resuming = false)
            "resuming" -> Left(t.substring(cut + 1), resuming = true)
            else -> null
        }
    }.getOrNull()

    /** What the last process left, the first time this process asks; after that, null. */
    fun leftover(marker: File): Left? = synchronized(read) {
        if (!read.add(marker.absolutePath)) null else parse(marker)
    }

    fun playing(marker: File, sessionId: String) = mark(marker, "playing $sessionId")
    fun resuming(marker: File, sessionId: String) = mark(marker, "resuming $sessionId")
    fun closed(marker: File) { runCatching { marker.delete() } }

    /**
     * The Play screen closing. The normal way (leaving the tab, the activity
     * finishing) clears the marker. An activity Android destroys without
     * finishing is brought back, in this same process when it lives, and its
     * Play screen resumes then, so the marker stays and is read once more.
     */
    fun left(marker: File, finishing: Boolean, destroyed: Boolean) {
        if (finishing || !destroyed) closed(marker)
        else synchronized(read) { read.remove(marker.absolutePath) }
    }

    private fun mark(marker: File, text: String) {
        runCatching { marker.parentFile?.mkdirs(); marker.writeText(text) }
    }

    /** The auto slot holds a state of the run in play: it is there, and stamped with [stamp], a known identity. */
    fun usable(slot: StateSlots.Slot, stamp: String): Boolean =
        slot.exists && PrepStore.stampKnown(stamp) && runCatching { slot.stamp.readText().trim() }.getOrNull() == stamp

    fun plan(left: Left?, sessionId: String, usable: Boolean, loadsAllowed: Boolean): Plan = when {
        left == null || left.sessionId != sessionId || !usable -> Plan.NONE
        left.resuming -> Plan.TRIED
        !loadsAllowed -> Plan.NONE
        else -> Plan.RESUME
    }

    /**
     * Once a core is up for [session]: resumes its auto [slot] if the last
     * process died with it open, and marks it open now. [stamp] is the run's
     * identity (PrepStore.stateStamp); [loadsAllowed] is false in
     * RetroAchievements hardcore; [load] puts a state into the core and says
     * whether the core took it; [why] says how the last process ended.
     * Returns the line for the status toast, or null.
     */
    suspend fun atCoreUp(
        marker: File, session: GameSession, slot: StateSlots.Slot, stamp: String, loadsAllowed: Boolean,
        events: RunEvents?, why: () -> String, load: suspend (ByteArray) -> Boolean,
    ): String? {
        // A close in this same process queued its auto-save moments ago: read the slot after it.
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { AutoSave.drain() }
        val usable = usable(slot, stamp)
        val plan = plan(leftover(marker), session.id, usable, loadsAllowed)
        if (plan != Plan.RESUME) {
            // Left the normal way (a tab switch, Back, the app closed), and the auto slot is that very moment
            // (its left mark, AutoSave): the game opens there, as every emulator a player has used does. It used
            // to boot to the title screen and name the auto-save in a toast, which read as lost progress, and the
            // next pause then wrote over it (2026-09-30, UX audit P0-4). Nothing is rewound, so nothing goes on
            // the run's record; a load that kills the app is not tried again, by the same marker as below.
            if (plan == Plan.NONE && usable && loadsAllowed && slot.leftMark.isFile) {
                resuming(marker, session.id)
                val bytes = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { StateSlots.readOrNull(slot.file) }
                val ok = bytes != null && bytes.isNotEmpty() && load(bytes)
                playing(marker, session.id)
                // Opened there once: the mark goes, so a later open with no new leaving snapshot (an empty serialize,
                // an unknown stamp) offers the slot instead of dropping the player into an older moment (R14).
                if (ok) runCatching { slot.leftMark.delete() }
                return if (ok) RETURNED else "Could not load the auto-save. It is still in File > States."
            }
            playing(marker, session.id)
            return when {
                plan == Plan.TRIED -> "The auto-save did not load when the app last opened. It is still in File > States."
                usable -> "Auto-save from ${slot.savedLabel()}: File > States > Resume."
                else -> null
            }
        }
        resuming(marker, session.id)
        val bytes = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { StateSlots.readOrNull(slot.file) }
        val ok = bytes != null && bytes.isNotEmpty() && load(bytes)
        playing(marker, session.id)
        if (!ok) return "Could not load the auto-save. It is still in File > States."
        events?.add(RunEvents.Kind.RESUME, "auto", "saved ${slot.savedAt}, ${why()}")
        return "Back where you were: the auto-save from ${slot.savedLabel()}."
    }

    /**
     * How the last process ended, from Android's own record (API 30 on), in
     * a few words for the run's events: a crash is not a phone call.
     */
    fun lastExit(context: android.content.Context): String = runCatching {
        if (android.os.Build.VERSION.SDK_INT < 30) return "exit reason not recorded"
        val am = context.getSystemService(android.app.ActivityManager::class.java)
        val e = am.getHistoricalProcessExitReasons(context.packageName, 0, 1).firstOrNull()
            ?: return "exit reason not recorded"
        val what = when (e.reason) {
            android.app.ApplicationExitInfo.REASON_CRASH -> "the app crashed"
            android.app.ApplicationExitInfo.REASON_CRASH_NATIVE -> "the app crashed in native code"
            android.app.ApplicationExitInfo.REASON_ANR -> "the app stopped responding"
            android.app.ApplicationExitInfo.REASON_LOW_MEMORY -> "Android freed memory"
            android.app.ApplicationExitInfo.REASON_SIGNALED -> "the process was killed"
            android.app.ApplicationExitInfo.REASON_USER_REQUESTED -> "the app was force stopped"
            android.app.ApplicationExitInfo.REASON_EXIT_SELF -> "the app exited"
            else -> "exit reason ${e.reason}"
        }
        "$what at ${e.timestamp}"
    }.getOrDefault("exit reason not recorded")
}
