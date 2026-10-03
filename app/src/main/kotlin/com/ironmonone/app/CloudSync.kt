package com.ironmonone.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Cloud sync of the backup zip, through the system document picker.
 *
 * The player links one file once: the picker offers Google Drive (and
 * Dropbox, OneDrive, a phone folder) and the app keeps a persistable
 * write permission on the document it created. From then on, whenever
 * the Play screen pauses or closes, the backup is rewritten into that
 * file if anything in it changed, and the Drive app carries it up. On
 * another phone, link the same file and RESTORE pulls it down.
 *
 * Why the picker and not the Drive API: the API needs an OAuth client
 * registered against this build's signing key in a Google Cloud project,
 * a Play Services dependency, and a consent screen, for one file. The
 * document provider is the same contract every cloud app already ships,
 * and needs nothing registered. Only the backup zip goes up (Backup): never
 * a library game or a patch, but it does hold the randomized game of the
 * run in play and of every saved attempt, up to 512 MB each for a DS game,
 * and the pictures chosen for the tracker and Play as your Pokemon
 * (rc32 audit P2 #2, #94).
 *
 * The link lives in prep/cloudsync.txt, which the backup does NOT admit:
 * a link is a fact about this phone, and a restore onto another phone
 * must not carry it over.
 */
object CloudSync {

    const val CONFIG = "prep/cloudsync.txt"
    const val SUGGESTED_NAME = "KaizoCore-sync.zip"
    /** The backup is rewritten no more often than this unless asked by hand. */
    const val MIN_INTERVAL_MS = 2 * 60 * 1000L

    /**
     * [restorePending]: linked to restore from (another phone's file) and not restored yet. Nothing is written to the
     * file while it is set (rc33 audit P0-9).
     */
    data class Link(val uri: String, val provider: String, val lastSync: Long, val fingerprint: String, val restorePending: Boolean = false)

    private const val PENDING = "restore-pending"

    sealed class Result {
        object NotLinked : Result()
        object Unchanged : Result()
        /** Linked to restore from, not restored yet: nothing was written. */
        object RestorePending : Result()
        data class Written(val files: Int) : Result()
        data class Failed(val reason: String) : Result()
    }

    // ------------------------------------------------------------- config
    fun parse(text: String): Link? {
        val l = text.lines()
        if (l.size < 2 || l[0].isBlank()) return null
        return Link(l[0].trim(), l.getOrElse(1) { "" }.trim(), l.getOrNull(2)?.trim()?.toLongOrNull() ?: 0L, l.getOrNull(3)?.trim() ?: "",
            restorePending = l.getOrNull(4)?.trim() == PENDING)
    }

    fun format(link: Link): String =
        "${link.uri}\n${link.provider}\n${link.lastSync}\n${link.fingerprint}\n" + (if (link.restorePending) "$PENDING\n" else "")

    fun load(filesDir: File): Link? = runCatching { parse(File(filesDir, CONFIG).readText()) }.getOrNull()

    fun save(filesDir: File, link: Link?) {
        val f = File(filesDir, CONFIG)
        // Whole or not at all (SafeWrite): written in place, a kill in the middle lost the link, or its restore-pending
        // mark (rc32 audit P2 #65).
        if (link == null) f.delete() else SafeWrite.text(f, format(link))
    }

    /** A human name for the provider that owns the document. */
    fun providerName(authority: String?): String = when {
        authority == null -> "a cloud folder"
        authority.contains("com.google.android.apps.docs") -> "Google Drive"
        authority.contains("com.dropbox") -> "Dropbox"
        authority.contains("com.microsoft.skydrive") -> "OneDrive"
        authority.contains("com.box.android") -> "Box"
        authority.contains("com.android.providers.downloads") -> "Downloads"
        authority.contains("com.android.externalstorage") -> "phone storage"
        // An unknown provider's package name meant nothing to a player (audit, 2026-09-27).
        else -> "your cloud folder"
    }

    /**
     * What the backup would contain, cheaply: path, size and mtime of every
     * admitted file. Same string means nothing to upload.
     */
    fun fingerprint(filesDir: File): String {
        val sb = StringBuilder()
        for (rel in Backup.collect(filesDir)) {
            val f = File(filesDir, rel)
            sb.append(rel).append(':').append(f.length()).append(':').append(f.lastModified()).append('\n')
        }
        val d = java.security.MessageDigest.getInstance("SHA-1").digest(sb.toString().toByteArray())
        return d.joinToString("") { "%02x".format(it) }
    }

    // --------------------------------------------------------------- link
    fun link(context: Context, uri: Uri, restorePending: Boolean = false): Link {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        runCatching { context.contentResolver.takePersistableUriPermission(uri, flags) }
        val link = Link(uri.toString(), providerName(uri.authority), 0L, "", restorePending)
        save(context.filesDir, link)
        return link
    }

    /**
     * "Use my file from another phone": linked with the restore pending, in one write (rc33 audit P0-9). The link was
     * saved first and the confirm lived only in the screen's memory, so if Android ended the app before the player
     * pressed Restore, the next sync put this nearly empty phone over the old phone's backup.
     */
    fun linkForRestore(context: Context, uri: Uri): Link = link(context, uri, restorePending = true)

    /** The restore went through: from now on the file is this phone's to keep in sync. */
    fun restoreDone(filesDir: File) {
        load(filesDir)?.takeIf { it.restorePending }?.let { save(filesDir, it.copy(restorePending = false)) }
    }

    fun unlink(context: Context) {
        load(context.filesDir)?.let { l ->
            runCatching {
                context.contentResolver.releasePersistableUriPermission(
                    Uri.parse(l.uri), Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }
        }
        save(context.filesDir, null)
    }

    // --------------------------------------------------------------- sync
    /** Rewrite the linked document from the current files. Blocking; call off the main thread. */
    fun sync(context: Context, force: Boolean = false): Result {
        val link = load(context.filesDir) ?: return Result.NotLinked
        // Another phone's file, not restored from yet: writing would replace that phone's backup with this one's.
        if (link.restorePending) return Result.RestorePending
        val fp = fingerprint(context.filesDir)
        val now = System.currentTimeMillis()
        if (!force) {
            if (fp == link.fingerprint) return Result.Unchanged
            if (now - link.lastSync < MIN_INTERVAL_MS) return Result.Unchanged
        }
        val uri = Uri.parse(link.uri)
        val n = runCatching {
            writeLock.withLock {
                // Sync now pressed while a restore holds syncs: checked under the lock the restore waits on, so the restart
                // never cuts a copy short (rc32 audit P2 #9).
                if (held) return Result.Failed(RESTORING)
                // The zip is made whole on this phone first, then copied over the synced file in one pass. Made
                // straight into the file, a failure part way (a file changing, no space) left the only cloud copy cut
                // short; and one sync at a time, so Sync now and a background sync never interleave (rc33 audit P1).
                val tmp = File(context.cacheDir, "cloudsync.zip.tmp")
                try {
                    val files = tmp.outputStream().buffered(1 shl 20).use { Backup.write(context.filesDir, it) }
                    // "wt": truncate. Without it a shorter backup leaves the old tail
                    // behind and the zip's central directory is no longer at the end.
                    (context.contentResolver.openOutputStream(uri, "wt") ?: error("no stream")).use { out ->
                        tmp.inputStream().buffered(1 shl 20).use { it.copyTo(out, 1 shl 20) }
                    }
                    files
                } finally { tmp.delete() }
            }
        }.getOrElse { e ->
            return Result.Failed(when (e) {
                is SecurityException -> "The link to ${link.provider} was lost. Link it again."
                is java.io.FileNotFoundException -> "The synced file is gone from ${link.provider}. Link it again."
                // Raw exception text ("write failed: ENOSPC ...") is for the
                // log, not the player (audit, 2026-09-27).
                else -> {
                    // Tagged KaizoCore so Feedback.logTail carries it into a bug report.
                    runCatching { android.util.Log.w("KaizoCore", "cloud sync write failed: ${e.message ?: e.javaClass.simpleName}") }
                    "Could not write to ${link.provider}. Check its space and connection, then Sync now."
                }
            })
        }
        save(context.filesDir, link.copy(lastSync = now, fingerprint = fp))
        return Result.Written(n)
    }

    /** Pull the linked document back. -1: not a KaizoCore backup; null: unreadable. */
    fun restore(context: Context): Int? {
        val link = load(context.filesDir) ?: return null
        return runCatching {
            context.contentResolver.openInputStream(Uri.parse(link.uri))!!.use { Backup.read(context.filesDir, it) }
        }.getOrNull()
    }

    // --------------------------------------------------------- background
    private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "cloud-sync").apply { isDaemon = true } }
    /** One write to the synced file at a time: a background sync and Sync now used to interleave on it. */
    internal val writeLock = ReentrantLock()
    private val running = AtomicBoolean(false)
    @Volatile var lastResult: Result? = null
        private set

    /**
     * A restore is under way (RestoreGate): no background sync starts, so none is cut off by the restart or rewrites the
     * file being restored from (rc32 audit P2 #9). In memory only: a crash can never leave syncing switched off.
     */
    @Volatile private var held = false

    fun hold() { held = true }
    fun release() { held = false }

    /** A sync is writing now, a background one or Sync now: a restore waits for it, and says so. */
    val syncing: Boolean get() = running.get() || writeLock.isLocked

    const val RESTORING = "A restore is under way. Sync again once it is done."


    /**
     * Waits, [timeoutMs] at most, for a sync under way, background or Sync now, to finish writing the synced file.
     * False when one is still writing: the restore's restart would cut its copy short (rc32 audit P2 #9).
     */
    fun awaitIdle(timeoutMs: Long): Boolean {
        val until = System.nanoTime() + timeoutMs * 1_000_000
        while (running.get()) {
            if (System.nanoTime() >= until) return false
            Thread.sleep(25)
        }
        val left = (until - System.nanoTime()).coerceAtLeast(0L)
        if (!writeLock.tryLock(left, TimeUnit.NANOSECONDS)) return false
        writeLock.unlock()
        return true
    }

    /** Fire-and-forget from lifecycle hooks; one at a time, never on the caller's thread. */
    fun syncInBackground(context: Context, onDone: ((Result) -> Unit)? = null) {
        if (held) return
        if (load(context.filesDir) == null) return
        if (!running.compareAndSet(false, true)) return
        val app = context.applicationContext
        worker.execute {
            try {
                // Held after it was asked for: a restore began in between.
                if (held) return@execute
                val r = sync(app)
                lastResult = r
                // A background sync that failed was never shown anywhere (rc33 audit P1).
                if (r is Result.Failed) SaveTrouble.report(SaveTrouble.CLOUD, r.reason)
                onDone?.invoke(r)
            } finally { running.set(false) }
        }
    }

    fun whenLabel(millis: Long): String =
        if (millis <= 0) "never" else java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT).format(java.util.Date(millis))
}
