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

        fun setLocked(on: Boolean) { if (on) lockFile.writeText("1") else lockFile.delete() }

        /** Keep the current state as the backup before it is overwritten, with its thumbnail and its run stamp. */
        fun keepBackup() {
            if (!exists) return
            runCatching { file.copyTo(backup, overwrite = true) }
            runCatching { if (thumb.exists()) thumb.copyTo(backupThumb, overwrite = true) else backupThumb.delete() }
            runCatching { if (stamp.exists()) stamp.copyTo(backupStamp, overwrite = true) else backupStamp.delete() }
        }

        /** Swap the backup back in; the state it replaces becomes the backup. */
        fun restoreBackup(): Boolean {
            if (!hasBackup) return false
            val tmp = File(file.parentFile, file.name + ".swap")
            return runCatching {
                if (exists) file.copyTo(tmp, overwrite = true)
                backup.copyTo(file, overwrite = true)
                if (tmp.exists()) { tmp.copyTo(backup, overwrite = true); tmp.delete() } else backup.delete()
                val t = File(file.parentFile, thumb.name + ".swap")
                if (thumb.exists()) thumb.copyTo(t, overwrite = true)
                if (backupThumb.exists()) backupThumb.copyTo(thumb, overwrite = true) else thumb.delete()
                if (t.exists()) { t.copyTo(backupThumb, overwrite = true); t.delete() } else backupThumb.delete()
                // The run stamp goes with its state (rc33 audit P1): UNDO kept the overwriting save's stamp, so an
                // older seed's state then passed the run check and loaded into this run. A backup made before rc33
                // has no stamp of its own, and an unstamped state is refused, never loaded into the wrong run.
                val st = File(stamp.parentFile, stamp.name + ".swap")
                if (stamp.exists()) stamp.copyTo(st, overwrite = true)
                if (backupStamp.exists()) backupStamp.copyTo(stamp, overwrite = true) else stamp.delete()
                if (st.exists()) { st.copyTo(backupStamp, overwrite = true); st.delete() } else backupStamp.delete()
                true
            }.getOrDefault(false)
        }
    }

    fun thumbFile(stateFile: File): File = File(stateFile.parentFile, stateFile.nameWithoutExtension + ".png")

    fun slot(filesDir: File, session: GameSession, n: Int): Slot {
        val f = SessionPaths.slot(filesDir, session, n)
        return Slot(n, f, SessionPaths.slotStamp(filesDir, session, n), thumbFile(f))
    }

    /** The eight numbered slots. */
    fun list(filesDir: File, session: GameSession): List<Slot> = (1..COUNT).map { slot(filesDir, session, it) }

    /** The auto-save slot for this game. */
    fun auto(filesDir: File, session: GameSession): Slot = slot(filesDir, session, AUTO)

    /** The most recently saved slot, for "continue where I left off". Null when none. */
    fun latest(slots: List<Slot>): Slot? = slots.filter { it.exists }.maxByOrNull { it.savedAt }
}
