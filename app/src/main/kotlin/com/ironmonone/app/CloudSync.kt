package com.ironmonone.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

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
 * needs nothing registered, and keeps the ROM rule intact: only the
 * backup zip goes up, and the backup never holds a ROM.
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

    data class Link(val uri: String, val provider: String, val lastSync: Long, val fingerprint: String)

    sealed class Result {
        object NotLinked : Result()
        object Unchanged : Result()
        data class Written(val files: Int) : Result()
        data class Failed(val reason: String) : Result()
    }

    // ------------------------------------------------------------- config
    fun parse(text: String): Link? {
        val l = text.lines()
        if (l.size < 2 || l[0].isBlank()) return null
        return Link(l[0].trim(), l.getOrElse(1) { "" }.trim(), l.getOrNull(2)?.trim()?.toLongOrNull() ?: 0L, l.getOrNull(3)?.trim() ?: "")
    }

    fun format(link: Link): String = "${link.uri}\n${link.provider}\n${link.lastSync}\n${link.fingerprint}\n"

    fun load(filesDir: File): Link? = runCatching { parse(File(filesDir, CONFIG).readText()) }.getOrNull()

    fun save(filesDir: File, link: Link?) {
        val f = File(filesDir, CONFIG)
        if (link == null) f.delete() else { f.parentFile?.mkdirs(); f.writeText(format(link)) }
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
        else -> authority
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
    fun link(context: Context, uri: Uri): Link {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        runCatching { context.contentResolver.takePersistableUriPermission(uri, flags) }
        val link = Link(uri.toString(), providerName(uri.authority), 0L, "")
        save(context.filesDir, link)
        return link
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
        val fp = fingerprint(context.filesDir)
        val now = System.currentTimeMillis()
        if (!force) {
            if (fp == link.fingerprint) return Result.Unchanged
            if (now - link.lastSync < MIN_INTERVAL_MS) return Result.Unchanged
        }
        val uri = Uri.parse(link.uri)
        val n = runCatching {
            // "wt": truncate. Without it a shorter backup leaves the old tail
            // behind and the zip's central directory is no longer at the end.
            (context.contentResolver.openOutputStream(uri, "wt") ?: error("no stream")).use { Backup.write(context.filesDir, it) }
        }.getOrElse { e ->
            return Result.Failed(when (e) {
                is SecurityException -> "The link to ${link.provider} was lost. Link it again."
                is java.io.FileNotFoundException -> "The synced file is gone from ${link.provider}. Link it again."
                else -> e.message ?: e.javaClass.simpleName
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
    private val running = AtomicBoolean(false)
    @Volatile var lastResult: Result? = null
        private set

    /** Fire-and-forget from lifecycle hooks; one at a time, never on the caller's thread. */
    fun syncInBackground(context: Context, onDone: ((Result) -> Unit)? = null) {
        if (load(context.filesDir) == null) return
        if (!running.compareAndSet(false, true)) return
        val app = context.applicationContext
        worker.execute {
            try {
                val r = sync(app)
                lastResult = r
                onDone?.invoke(r)
            } finally { running.set(false) }
        }
    }

    fun whenLabel(millis: Long): String =
        if (millis <= 0) "never" else java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT).format(java.util.Date(millis))
}
