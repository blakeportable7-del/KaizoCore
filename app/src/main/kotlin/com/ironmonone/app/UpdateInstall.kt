package com.ironmonone.app

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * One tap to update (2026-09-29, Blake: "i want it to one tap"). The first version opened the
 * APK in the browser and left the player to find the download and open it. Now Update fetches
 * the build itself, holds it to the size and SHA-256 that latest.json names, and hands it to
 * Android's own installer in one session.
 *
 * What Android keeps for itself: the first time, it asks the player to let KaizoCore install
 * apps (Settings, Install unknown apps), and it asks the player to confirm the install. Once
 * KaizoCore has installed its own update it is the installer of record, and from Android 12 the
 * session asks Android not to ask again (USER_ACTION_NOT_REQUIRED): the player's tap on Update is
 * the one tap. Android alone decides whether that holds, and asks whenever its conditions are not
 * met; tested on the emulator, the first update asked and the one after it did not (2026-09-29).
 * Android refuses any build not signed with the same key as this one.
 *
 * The download is only ever the file latest.json names, which [UpdateCheck.parse] has already
 * held to willowcreek.group/kaizocore/. A file of the wrong size or checksum is deleted and
 * never offered to the installer.
 */
object UpdateInstall {

    /** Under cacheDir: a download that is not installed is worth nothing after a restart. */
    const val DIR = "update"
    const val APK = "KaizoCore-update.apk"

    /** The installer's answer comes back to this, through a PendingIntent only this app can send. */
    const val ACTION_STATUS = "com.ironmonone.app.UPDATE_INSTALL_STATUS"

    /** Room left over after the download, so an update never fills a phone to the last byte. */
    const val SPARE_BYTES = 32L * 1024 * 1024

    /** How often progress is reported: a 120 MB file in about 240 steps. */
    private const val PROGRESS_STEP = 512L * 1024

    enum class Why { NO_SPACE, NETWORK, HTTP, SIZE, CHECKSUM, CANCELLED }

    sealed class Download {
        data class Done(val file: File) : Download()
        data class Failed(val why: Why, val detail: String = "") : Download()
    }

    fun dir(cacheDir: File): File = File(cacheDir, DIR)

    /** A download left from an earlier launch was installed (it is the running build) or abandoned. Either way it goes. */
    fun sweep(cacheDir: File) {
        runCatching { dir(cacheDir).deleteRecursively() }
    }

    /**
     * Fetches [url] into [dir], holding it to exactly [bytes] bytes and to [sha256]. Blocking and
     * long (120 MB), so on Dispatchers.IO. [cancelled] is asked between reads, [onProgress] told
     * every half megabyte. Every failure deletes what was written and says why; nothing throws.
     */
    fun download(
        url: String,
        dir: File,
        bytes: Long,
        sha256: String,
        onProgress: (done: Long, total: Long) -> Unit = { _, _ -> },
        cancelled: () -> Boolean = { false },
        freeBytes: (File) -> Long = { it.usableSpace },
        timeoutMs: Int = 15_000,
    ): Download {
        val part = File(dir, "$APK.part")
        val done = File(dir, APK)
        var conn: HttpURLConnection? = null
        return try {
            dir.mkdirs()
            part.delete()
            done.delete()
            if (freeBytes(dir) < bytes + SPARE_BYTES) return Download.Failed(Why.NO_SPACE)
            conn = URL(url).openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = false
            conn.useCaches = false
            conn.connectTimeout = timeoutMs
            conn.readTimeout = timeoutMs
            conn.setRequestProperty("User-Agent", "KaizoCore")
            // The byte count is held to the file's own size, so no compressed transfer.
            conn.setRequestProperty("Accept-Encoding", "identity")
            val code = conn.responseCode
            if (code != 200) return Download.Failed(Why.HTTP, "HTTP $code")
            val length = conn.contentLengthLong
            if (length >= 0 && length != bytes) return Download.Failed(Why.SIZE, "$length bytes offered, $bytes expected")
            val md = MessageDigest.getInstance("SHA-256")
            val got = conn.inputStream.use { input -> copyCounted(input, part, bytes, md, onProgress, cancelled) }
            when {
                got == CANCELLED -> fail(part, Why.CANCELLED)
                got > bytes -> fail(part, Why.SIZE, "more than $bytes bytes")
                got != bytes -> fail(part, Why.SIZE, "$got of $bytes bytes")
                hex(md.digest()) != sha256 -> fail(part, Why.CHECKSUM)
                !part.renameTo(done) -> fail(part, Why.NETWORK, "rename")
                else -> Download.Done(done)
            }
        } catch (e: Exception) {
            fail(part, Why.NETWORK, e.javaClass.simpleName)
        } finally {
            runCatching { conn?.disconnect() }
        }
    }

    private const val CANCELLED = -1L

    /** Copies [input] to [out], hashing as it goes. The count, one past [limit] if the source ran long, or [CANCELLED]. */
    private fun copyCounted(
        input: InputStream,
        out: File,
        limit: Long,
        md: MessageDigest,
        onProgress: (Long, Long) -> Unit,
        cancelled: () -> Boolean,
    ): Long = FileOutputStream(out).use { o ->
        val buf = ByteArray(64 * 1024)
        var total = 0L
        var reported = 0L
        while (true) {
            if (cancelled()) return CANCELLED
            val n = input.read(buf)
            if (n < 0) break
            total += n
            // A longer file is not this build: stop reading it rather than fill the phone.
            if (total > limit) return total
            md.update(buf, 0, n)
            o.write(buf, 0, n)
            if (total - reported >= PROGRESS_STEP || total == limit) {
                onProgress(total, limit)
                reported = total
            }
        }
        o.fd.sync()
        total
    }

    private fun fail(part: File, why: Why, detail: String = ""): Download {
        runCatching { part.delete() }
        return Download.Failed(why, detail)
    }

    private fun hex(b: ByteArray): String = b.joinToString("") { "%02x".format(it) }

    /**
     * The update demo (`--es demo update`): no build newer than this one exists, so the demo
     * "downloads" the installed APK itself, with progress, and the install that follows is the
     * real one, a reinstall of the same build. That runs the permission, the installer session
     * and Android's confirmation on a phone before any real update is published.
     */
    fun demoDownload(context: Context, dir: File, onProgress: (Long, Long) -> Unit, cancelled: () -> Boolean): Download {
        val source = File(context.applicationInfo.sourceDir)
        val part = File(dir, "$APK.part")
        val done = File(dir, APK)
        return try {
            dir.mkdirs()
            part.delete()
            done.delete()
            val size = source.length()
            val got = source.inputStream().use { copyCounted(it, part, size, MessageDigest.getInstance("SHA-256"), onProgress, cancelled) }
            when {
                got == CANCELLED -> fail(part, Why.CANCELLED)
                got != size -> fail(part, Why.SIZE)
                !part.renameTo(done) -> fail(part, Why.NETWORK, "rename")
                else -> Download.Done(done)
            }
        } catch (e: Exception) {
            fail(part, Why.NETWORK, e.javaClass.simpleName)
        }
    }

    // --------------------------------------------------------------- install

    /** Whether the player has let KaizoCore install apps. Without it Android refuses the session. */
    fun canInstall(context: Context): Boolean = runCatching { context.packageManager.canRequestPackageInstalls() }.getOrDefault(false)

    /** Android's own switch for it, on this app's page. */
    fun allowIntent(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + context.packageName))

    /**
     * Hands [apk] to Android's installer as one session: an update of this package, whole. The
     * answer comes to [ACTION_STATUS]. Blocking (it copies the file), so on Dispatchers.IO.
     * False when the session could not be made or written.
     */
    fun install(context: Context, apk: File): Boolean {
        val installer = context.packageManager.packageInstaller
        var id = -1
        return try {
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            params.setAppPackageName(context.packageName)
            params.setSize(apk.length())
            // The player already said yes by tapping Update. Android asks again unless KaizoCore
            // installed the build it is replacing (it asks the first time, from the browser's install).
            if (Build.VERSION.SDK_INT >= 31) params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            id = installer.createSession(params)
            installer.openSession(id).use { session ->
                session.openWrite("base.apk", 0, apk.length()).use { out ->
                    apk.inputStream().use { it.copyTo(out, 64 * 1024) }
                    session.fsync(out)
                }
                // Mutable: the installer writes its status and its confirmation intent into it.
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
                val status = PendingIntent.getBroadcast(context, id, Intent(ACTION_STATUS).setPackage(context.packageName), flags)
                session.commit(status.intentSender)
            }
            true
        } catch (e: Exception) {
            if (id >= 0) runCatching { installer.abandonSession(id) }
            false
        }
    }

    /** What an installer answer means for the screen. */
    enum class Answer {
        /** Android wants the player to confirm: show [confirmIntent]. */
        CONFIRM,
        INSTALLED,
        /** The player said no, or backed out of Android's question. */
        DECLINED,
        REFUSED,
    }

    /** CONFIRM only when Android sent something to show; a confirmation with nothing in it is a refusal. */
    fun answer(status: Int, hasConfirm: Boolean): Answer = when (status) {
        PackageInstaller.STATUS_PENDING_USER_ACTION -> if (hasConfirm) Answer.CONFIRM else Answer.REFUSED
        PackageInstaller.STATUS_SUCCESS -> Answer.INSTALLED
        PackageInstaller.STATUS_FAILURE_ABORTED -> Answer.DECLINED
        else -> Answer.REFUSED
    }

    fun status(intent: Intent): Int = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)

    /** Android's own words for a refusal, if it gave any. */
    fun message(intent: Intent): String =
        intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)?.takeIf { it.isNotBlank() }?.take(200) ?: ""

    /** The confirmation screen Android sent with STATUS_PENDING_USER_ACTION. */
    fun confirmIntent(intent: Intent): Intent? =
        if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
        else @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_INTENT)
}
