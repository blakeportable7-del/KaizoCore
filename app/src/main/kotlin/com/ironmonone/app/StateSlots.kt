package com.ironmonone.app

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Save states as a list the UI can draw: eight per game, each with the
 * state file, its stamp, a thumbnail taken at save time, and when it was
 * saved. Paths come from SessionPaths, so slots 1..3 are the files the app
 * has always used and nothing existing moves.
 *
 * Slot 0 is the AUTO-SAVE: written when the game is left (tab switch, app
 * pause, exit) and while it is played (AutoSave), load-only, so a session
 * can be picked up where it stopped, or near it after a crash.
 * A slot can be LOCKED (a marker file beside it): SAVE refuses it until it
 * is unlocked, so a favourite checkpoint cannot be overwritten by a thumb.
 * Every overwrite keeps the previous state as a BACKUP (`.bak`) that can
 * be restored once, which is what Delta calls automatic backups.
 */
object StateSlots {
    const val COUNT = 8
    const val AUTO = 0
    const val LOCK_FAILED = "Could not lock the slot. If this phone is out of space, free some, then try again."

    private val writeLocks = java.util.concurrent.ConcurrentHashMap<String, Any>()

    /**
     * Write a state through a .tmp and a rename. Returns null on success, or
     * the sentence to show. The write used to run bare in a background task,
     * so a full phone threw there and closed the app (audit, 2026-09-27); the
     * previous save is left as it was and the .tmp is removed.
     *
     * One writer per file at a time, and the slot is never deleted to make way
     * for the new one (rc33 audit P0-2): two quick saves to one slot shared
     * the .tmp, the second rename failed, its fallback deleted the slot the
     * first had just written, and it still said "Saved to slot N."
     */
    fun writeAtomic(f: File, bytes: ByteArray): String? = synchronized(writeLocks.getOrPut(f.absolutePath) { Any() }) {
        val tmp = File(f.parentFile, f.name + ".tmp")
        try {
            f.parentFile?.mkdirs()
            // Flushed to the disk before the rename, not only handed to the
            // page cache: after a dead battery the slot holds the old state
            // or the new one, never a torn one.
            java.io.FileOutputStream(tmp).use { out -> out.write(bytes); out.fd.sync() }
            replace(tmp, f)
            null
        } catch (e: java.io.IOException) {
            tmp.delete()
            if ((e.message ?: "").contains("ENOSPC") || (e.message ?: "").contains("No space"))
                "Could not save: this phone is out of space. Your last save is still there."
            else "Could not save: ${e.message ?: "the file could not be written"}. Your last save is still there."
        }
    }

    /**
     * [tmp] over [target] in one step, replacing it. Never deletes [target] first, so a failure leaves the old
     * file and throws (an IOException the caller reports) instead of looking like a success.
     */
    fun replace(tmp: File, target: File) {
        val opts = arrayOf(java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE)
        try {
            java.nio.file.Files.move(tmp.toPath(), target.toPath(), *opts)
        } catch (e: java.nio.file.AtomicMoveNotSupportedException) {
            java.nio.file.Files.move(tmp.toPath(), target.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        }
    }

    /** Read a state, or null with the reason when it cannot be read. */
    fun readOrNull(f: File): ByteArray? = runCatching { f.readBytes() }.getOrNull()

    /** When each battery save on disk was read from the core (System.nanoTime), so an older read never replaces it. */
    private val sramReadAt = java.util.concurrent.ConcurrentHashMap<String, Long>()

    /**
     * The battery save's one writer (rc32 audit P2 #51, P3 #54): Play's flush on a pause and the flush while the game
     * runs (AutoSave.keepFresh) both go through it. An empty buffer, from a core mid-teardown or not yet running, writes
     * nothing, so a good save is never replaced by it. A read taken before the one on disk ([readAt], System.nanoTime)
     * is dropped: a flush queued behind a pause must not put the older in-game save back. Returns null, or the
     * sentence to show.
     */
    fun writeSram(f: File, bytes: ByteArray, readAt: Long = System.nanoTime()): String? {
        if (bytes.isEmpty()) return null
        return synchronized(writeLocks.getOrPut(f.absolutePath) { Any() }) {
            if (readAt < (sramReadAt[f.absolutePath] ?: Long.MIN_VALUE)) return@synchronized null
            writeAtomic(f, bytes).also { if (it == null) sramReadAt[f.absolutePath] = readAt }
        }
    }

    /** A slot as a sentence names it: "Slot 3", or "The auto-save", never "Slot 0", a name the app shows nowhere (rc32 audit P3 #63). */
    fun named(n: Int, capital: Boolean = true): String = when {
        n != AUTO -> (if (capital) "Slot " else "slot ") + n
        capital -> "The auto-save"
        else -> "the auto-save"
    }

    /** What LOAD says for a slot with nothing in it. */
    fun emptyLine(n: Int): String = "${named(n)} is empty."

    /**
     * Why a load of slot [n] is refused, or null to load it (rc32 audit P3 #54). [got] is the slot's run stamp, null when
     * it has none; [want] is this run's (PrepStore.stateStamp). A save state restores the whole of RAM, so one taken in
     * another randomization would put that game's memory under this one's data tables, and the tracker would read a
     * party that cannot exist. A run with no seed on disk (a NEW RUN cut off half way) matches nothing, not even another
     * unknown; a stamp that differs is checked before a missing one.
     */
    fun loadRefusal(n: Int, got: String?, want: String): String? = when {
        // This run, saved on another build of its game (StateStamp, 2026-10-06): its memory belongs to that build's code.
        StateStamp.otherBuild(got, want) -> StateStamp.otherBuildLine(named(n))
        got != null && !StateStamp.matches(got, want) -> "${named(n)} is from a different run, so it was not loaded."
        got == null -> "${named(n)} is from an older version, so it was not loaded."
        else -> null
    }

    /**
     * UNDO on [slot], off the main thread (rc32 audit P2 #62): a DS state runs tens of MB, and the swap read and wrote
     * it three times in the dialog's click handler. Returns the line for the status toast.
     */
    suspend fun undo(slot: Slot): String = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        if (!slot.hasBackup) return@withContext "Nothing to undo."
        val failed = slot.swapBackup()
        if (failed == null) "${named(slot.n)}: previous state restored."
        else "Could not undo: ${failed.message ?: "the files could not be swapped"}."
    }

    /**
     * [a] and [b] trade places by renames, either one may be missing. A kill part way leaves the file that was [a]
     * under [a].swap, which [recover] puts back where it belongs.
     */
    private fun swap(a: File, b: File) {
        val tmp = File(a.parentFile, a.name + ".swap")
        if (a.exists()) replace(a, tmp)
        if (b.exists()) replace(b, a)
        if (tmp.exists()) replace(tmp, b)
    }

    /** Finishes or undoes a swap a kill cut short: [a]'s old file went to [a].swap and has not reached [b] yet. */
    private fun recover(a: File, b: File) {
        val tmp = File(a.parentFile, a.name + ".swap")
        if (!tmp.exists()) return
        runCatching { if (!a.exists()) replace(tmp, a) else if (!b.exists()) replace(tmp, b) }
    }

    data class Slot(val n: Int, val file: File, val stamp: File, val thumb: File) {
        val exists: Boolean get() = file.exists() && file.length() > 0
        val savedAt: Long get() = if (exists) file.lastModified() else 0L
        val sizeBytes: Long get() = if (exists) file.length() else 0L
        val isAuto: Boolean get() = n == AUTO
        val lockFile: File get() = File(file.parentFile, file.nameWithoutExtension + ".lock")
        val locked: Boolean get() = lockFile.exists()
        val backup: File get() = File(file.parentFile, file.nameWithoutExtension + ".bak")
        val backupThumb: File get() = File(file.parentFile, file.nameWithoutExtension + ".bak.png")
        /** The backup's run stamp: UNDO puts it back with the state it belongs to. */
        val backupStamp: File get() = File(stamp.parentFile, stamp.nameWithoutExtension + ".bak.id")
        val hasBackup: Boolean get() = backup.exists() && backup.length() > 0
        /**
         * Beside the auto slot only: there while the state in it is the moment the game was left or paused
         * (AutoSave writes it with that snapshot and removes it with any later one). CrashResume reads it.
         */
        val leftMark: File get() = File(file.parentFile, file.nameWithoutExtension + ".left")
        fun savedLabel(): String = if (!exists) "empty"
            else SimpleDateFormat("d MMM HH:mm", Locale.getDefault()).format(Date(savedAt))
        fun title(): String = if (isAuto) "Auto-save" else "Slot $n"

        /**
         * Locks or unlocks the slot. False when the phone refused: it threw out of the tap and closed the app on a full
         * phone (rc32 audit P2 #16), and it is said instead (SaveTrouble).
         */
        fun setLocked(on: Boolean): Boolean {
            val ok = runCatching { if (on) lockFile.writeText("1") else lockFile.delete() }.isSuccess
            if (!ok) SaveTrouble.report(SaveTrouble.SETTING, LOCK_FAILED)
            return ok
        }

        /** Keep the current state as the backup before it is overwritten, with its thumbnail and its run stamp. */
        fun keepBackup() {
            synchronized(writeLocks.getOrPut(file.absolutePath) { Any() }) {
                if (!exists) return
                runCatching { file.copyTo(backup, overwrite = true) }
                runCatching { if (thumb.exists()) thumb.copyTo(backupThumb, overwrite = true) else backupThumb.delete() }
                runCatching { if (stamp.exists()) stamp.copyTo(backupStamp, overwrite = true) else backupStamp.delete() }
            }
        }

        /** Swap the backup back in; the state it replaces becomes the backup. */
        fun restoreBackup(): Boolean = hasBackup && swapBackup() == null

        /**
         * The swap, by renames under the slot's write lock (rc32 audit P2 #62): it copied each file three times, and
         * Kotlin's copyTo deletes the target first, so a kill part way left a torn slot. A quick save to the same slot
         * waits for it. The run stamp goes with its state (rc33 audit P1): UNDO kept the overwriting save's stamp, so
         * an older seed's state then passed the run check and loaded into this run. A backup made before rc33 has no
         * stamp of its own, and an unstamped state is refused, never loaded into the wrong run. Null when done.
         */
        internal fun swapBackup(): Throwable? = synchronized(writeLocks.getOrPut(file.absolutePath) { Any() }) {
            runCatching {
                swap(file, backup)
                swap(thumb, backupThumb)
                swap(stamp, backupStamp)
            }.exceptionOrNull()
        }

        /** Puts back a swap a kill cut short ([swap]): the slot, its thumbnail and its stamp. */
        internal fun recoverSwap() {
            synchronized(writeLocks.getOrPut(file.absolutePath) { Any() }) {
                recover(file, backup); recover(thumb, backupThumb); recover(stamp, backupStamp)
            }
        }
    }

    fun thumbFile(stateFile: File): File = File(stateFile.parentFile, stateFile.nameWithoutExtension + ".png")

    fun slot(filesDir: File, session: GameSession, n: Int): Slot {
        val f = SessionPaths.slot(filesDir, session, n)
        return Slot(n, f, SessionPaths.slotStamp(filesDir, session, n), thumbFile(f))
    }

    /** The eight numbered slots, any UNDO a kill cut short put right first. */
    fun list(filesDir: File, session: GameSession): List<Slot> = (1..COUNT).map { slot(filesDir, session, it).also { s -> s.recoverSwap() } }

    /** The auto-save slot for this game. */
    fun auto(filesDir: File, session: GameSession): Slot = slot(filesDir, session, AUTO)

    /** The most recently saved slot, for "continue where I left off". Null when none. */
    fun latest(slots: List<Slot>): Slot? = slots.filter { it.exists }.maxByOrNull { it.savedAt }
}
