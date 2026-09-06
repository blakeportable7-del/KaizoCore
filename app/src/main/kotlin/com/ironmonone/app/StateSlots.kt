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
 * pause, exit), load-only, so a session can be picked up where it stopped.
 * A slot can be LOCKED (a marker file beside it): SAVE refuses it until it
 * is unlocked, so a favourite checkpoint cannot be overwritten by a thumb.
 * Every overwrite keeps the previous state as a BACKUP (`.bak`) that can
 * be restored once, which is what Delta calls automatic backups.
 */
object StateSlots {
    const val COUNT = 8
    const val AUTO = 0

    data class Slot(val n: Int, val file: File, val stamp: File, val thumb: File) {
        val exists: Boolean get() = file.exists() && file.length() > 0
        val savedAt: Long get() = if (exists) file.lastModified() else 0L
        val sizeBytes: Long get() = if (exists) file.length() else 0L
        val isAuto: Boolean get() = n == AUTO
        val lockFile: File get() = File(file.parentFile, file.nameWithoutExtension + ".lock")
        val locked: Boolean get() = lockFile.exists()
        val backup: File get() = File(file.parentFile, file.nameWithoutExtension + ".bak")
        val backupThumb: File get() = File(file.parentFile, file.nameWithoutExtension + ".bak.png")
        val hasBackup: Boolean get() = backup.exists() && backup.length() > 0
        fun savedLabel(): String = if (!exists) "empty"
            else SimpleDateFormat("d MMM HH:mm", Locale.getDefault()).format(Date(savedAt))
        fun title(): String = if (isAuto) "Auto-save" else "Slot $n"

        fun setLocked(on: Boolean) { if (on) lockFile.writeText("1") else lockFile.delete() }

        /** Keep the current state as the backup before it is overwritten. */
        fun keepBackup() {
            if (!exists) return
            runCatching { file.copyTo(backup, overwrite = true) }
            runCatching { if (thumb.exists()) thumb.copyTo(backupThumb, overwrite = true) else backupThumb.delete() }
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
