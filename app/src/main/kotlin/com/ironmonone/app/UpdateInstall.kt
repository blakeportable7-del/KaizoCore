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
 * held to willowcreek.group/kaizocore/, or this project's own release file on GitHub that the
 * site sends it on to ([Redirects]). A file of the wrong size or checksum is deleted and never
 * offered to the installer, wherever it came from.
 */
object UpdateInstall {

    /** Under cacheDir: a download that is not installed is worth nothing after a restart. */
    const val DIR = "update"
    const val APK = "KaizoCore-update.apk"

    /** The installer's answer comes back to this, through a PendingIntent only this app can send. */
    const val ACTION_STATUS = "com.ironmonone.app.UPDATE_INSTALL_STATUS"

    /** Room left over after the download, so an update never fills a phone to the last byte. */
    const val SPARE_BYTES = 32L * 1024 * 1024

    /**
     * The free space an update of [bytes] takes: the download, Android's own copy of it in the install session (the
     * download is still there while that is written), and [SPARE_BYTES] for the libraries Android unpacks and to spare.
     * Room for one copy let a phone download 125 MB and then hear only "Android did not install it." (rc32 audit P2 #103).
     */
    fun needBytes(bytes: Long): Long = 2 * bytes + SPARE_BYTES

    /** How often progress is reported: a 120 MB file in about 240 steps. */
    private const val PROGRESS_STEP = 512L * 1024

    /**
     * Why a download failed. [GONE]: the site answered "not found", or a redirect anywhere but [Redirects] allows, which is
     * what a build replaced by a newer one gets (each release sends the old APK on to the page), and not a connection
     * problem (rc32 audit P3 #76).
     */
    enum class Why { NO_SPACE, NETWORK, HTTP, GONE, SIZE, CHECKSUM, CANCELLED }

    /**
     * Where the APK's address may send a download on (Blake, 2026-10-02, rc32 audit P3 #75: "redirects to GitHub's release
     * files"): from the site's own APK address to this project's release files on GitHub, and from there to the host
     * GitHub serves release files from. Each step only in that order, so at most two, and nothing else is followed: a
     * redirect anywhere else, another repository or another host, is a build the site no longer has ([Why.GONE]). The
     * size and SHA-256 that latest.json names stay the gate for every byte, wherever it came from. An APK on GitHub can
     * then be offered by a redirect on the site, which builds that refused every redirect could never follow.
     */
    class Redirects(
        /** The site's own APK addresses: where every download starts (UpdateCheck.parse holds latest.json to it). */
        private val site: String,
        /** This project's release files. */
        private val releases: String,
        /** Where GitHub serves release files from, each as a prefix that ends past its host. */
        private val assets: List<String>,
    ) {
        /** Whether a redirect from [from] to [to] (both absolute) may be followed. */
        fun mayFollow(from: String, to: String): Boolean = plain(to) && when {
            from.startsWith(site) -> to.startsWith(releases)
            from.startsWith(releases) -> assets.any { to.startsWith(it) }
            else -> false
        }

        /**
         * One clean token, like UpdateCheck.ours: no space, control character or backslash for a server to read
         * differently, and no dot segment or encoded dot or slash in the path to walk out of a prefix with. The query
         * is left alone: GitHub's own signed address carries an encoded slash in it.
         */
        private fun plain(u: String): Boolean {
            if (u.any { it <= ' ' || it == '\u007f' || it == '\\' }) return false
            val path = u.substringBefore('?').substringBefore('#')
            return !DOT_SEGMENT.containsMatchIn(path) && !path.contains("%2e", ignoreCase = true) && !path.contains("%2f", ignoreCase = true)
        }

        companion object {
            private val DOT_SEGMENT = Regex("/\\.\\.?(/|$)")

            /**
             * The real ones. GitHub sends a release file to release-assets.githubusercontent.com (checked 2026-10-02 on
             * v1.0.0-rc33's APK); objects.githubusercontent.com is where it sent them before. Each only under its
             * release-file path, so nothing else GitHub serves from either host is taken.
             */
            val KAIZOCORE = Redirects(
                site = UpdateCheck.APK_PREFIX,
                releases = "https://github.com/blakeportable7-del/KaizoCore/releases/download/",
                assets = listOf(
                    "https://release-assets.githubusercontent.com/github-production-release-asset/",
                    "https://objects.githubusercontent.com/github-production-release-asset-2e65be/",
                ),
            )
        }
    }

    /** The most redirects one download follows: the site to GitHub, GitHub to its file host. */
    const val MAX_REDIRECTS = 2

    sealed class Download {
        data class Done(val file: File) : Download()
        data class Failed(val why: Why, val detail: String = "") : Download()
    }

    fun dir(cacheDir: File): File = File(cacheDir, DIR)

    /** A download left from an earlier launch was installed (it is the running build) or abandoned. Either way it goes. */
    fun sweep(cacheDir: File) {
        runCatching { dir(cacheDir).deleteRecursively() }
    }

    /** Set once this process has swept, or has begun a download of its own: from then on nothing in the folder is old. */
    private val swept = java.util.concurrent.atomic.AtomicBoolean(false)

    /**
     * [sweep], once a process. The launch check asks for it on the first composition of the update prompt that holds
     * nothing, and an activity Android rebuilt while INFO's card was downloading composed the prompt again: the sweep
     * deleted the download under way (RC35-NOTICED N #8). A download from an earlier launch can only be there at this
     * process's first look, so the first is the only one. [done] is the process's own flag except in a test.
     */
    internal fun sweepOnce(cacheDir: File, done: java.util.concurrent.atomic.AtomicBoolean = swept) {
        if (done.compareAndSet(false, true)) sweep(cacheDir)
    }

    /**
     * Fetches [url] into [dir], holding it to exactly [bytes] bytes and to [sha256]. Blocking and
     * long (120 MB), so on Dispatchers.IO. [cancelled] is asked between reads, [onProgress] told
     * every half megabyte. A redirect is followed only where [redirects] allows, by this code and
     * never by HttpURLConnection. Every failure deletes what was written and says why; nothing throws.
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
        redirects: Redirects = Redirects.KAIZOCORE,
    ): Download {
        val part = File(dir, "$APK.part")
        val done = File(dir, APK)
        var conn: HttpURLConnection? = null
        // What this process downloads is never an old download: no launch sweep after this one (sweepOnce).
        swept.set(true)
        return try {
            dir.mkdirs()
            part.delete()
            done.delete()
            if (freeBytes(dir) < needBytes(bytes)) return Download.Failed(Why.NO_SPACE)
            var at = url
            var followed = 0
            var c = open(at, timeoutMs)
            conn = c
            while (c.responseCode in 300..399) {
                // On to this project's release file on GitHub, or from there to GitHub's file host, and nowhere else (P3 #75).
                val next = c.getHeaderField("Location")?.let { runCatching { URL(URL(at), it).toString() }.getOrNull() }
                if (next == null || followed >= MAX_REDIRECTS || !redirects.mayFollow(at, next)) {
                    return Download.Failed(Why.GONE, "HTTP ${c.responseCode}")
                }
                runCatching { c.disconnect() }
                at = next
                followed++
                c = open(at, timeoutMs)
                conn = c
            }
            val code = c.responseCode
            if (code == 404 || code == 410) return Download.Failed(Why.GONE, "HTTP $code")
            if (code != 200) return Download.Failed(Why.HTTP, "HTTP $code")
            val length = c.contentLengthLong
            if (length >= 0 && length != bytes) return Download.Failed(Why.SIZE, "$length bytes offered, $bytes expected")
            val md = MessageDigest.getInstance("SHA-256")
            val got = c.inputStream.use { input -> copyCounted(input, part, bytes, md, onProgress, cancelled) }
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

    /** One request, its redirects left to [download]. */
    private fun open(url: String, timeoutMs: Int): HttpURLConnection = (URL(url).openConnection() as HttpURLConnection).apply {
        instanceFollowRedirects = false
        useCaches = false
        connectTimeout = timeoutMs
        readTimeout = timeoutMs
        setRequestProperty("User-Agent", "KaizoCore")
        // The byte count is held to the file's own size, so no compressed transfer.
        setRequestProperty("Accept-Encoding", "identity")
    }

    /**
     * The build downloaded and checked earlier in this process, when it is in [dir] whole and still matches [bytes] and
     * [sha256]: an install Android did not take is tried again from it, not fetched again (rc32 audit P2 #103). Reads the
     * whole file to check it, so on Dispatchers.IO. Null when there is none.
     */
    fun downloaded(dir: File, bytes: Long, sha256: String): File? = runCatching {
        val f = File(dir, APK).takeIf { it.isFile && it.length() == bytes } ?: return null
        val md = MessageDigest.getInstance("SHA-256")
        f.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) { val n = input.read(buf); if (n < 0) break; md.update(buf, 0, n) }
        }
        f.takeIf { hex(md.digest()) == sha256 }
    }.getOrNull()

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
        swept.set(true)
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

    /** How a hand-over to Android's installer went. */
    sealed class Handed {
        /** Committed: Android answers for [session] at [ACTION_STATUS]. */
        data class Committed(val session: Int) : Handed()
        /** The session could not be made or written; [noSpace] when the phone ran out of room for Android's copy. */
        data class Failed(val noSpace: Boolean) : Handed()
    }

    /**
     * Hands [apk] to Android's installer as one session: an update of this package, whole. The
     * answer comes to [ACTION_STATUS], for the session [onSession] is told as soon as it exists, so
     * an answer can never come before anyone knows which session it is for. Blocking (it copies the
     * file), so on Dispatchers.IO.
     */
    fun install(context: Context, apk: File, onSession: (Int) -> Unit = {}): Handed {
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
            onSession(id)
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
            Handed.Committed(id)
        } catch (e: Exception) {
            if (id >= 0) runCatching { installer.abandonSession(id) }
            // The copy Android writes is the second one on the phone: short of room, say so rather than "did not install".
            Handed.Failed(noSpace = apk.parentFile?.usableSpace?.let { it < apk.length() + SPARE_BYTES } == true)
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

    /** The session an answer is for. */
    fun session(intent: Intent): Int = intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1)

    /** Android's own words for a refusal, if it gave any. */
    fun message(intent: Intent): String =
        intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)?.takeIf { it.isNotBlank() }?.take(200) ?: ""

    /** The confirmation screen Android sent with STATUS_PENDING_USER_ACTION. */
    fun confirmIntent(intent: Intent): Intent? =
        if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
        else @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_INTENT)
}

/**
 * Android's answers to an update, kept for the process and not for the screen that asked (rc32 audit P2 #105). The
 * answer used to reach a receiver that lived with the update's screen: Close while Android was being handed the build,
 * Check now, or leaving the page unregistered it while the copy and the commit ran on, and Android's question then
 * reached no one, never appeared, and the update silently did not happen. Only the session last handed over is acted
 * on. Its confirmation goes to the screen that is up ([onStatus]'s show), or waits for the next one ([resumed]); every
 * other answer ends the session and goes to the updates on screen, if there are any (the one that handed the build
 * acts on it). [C] is the confirmation Android sends: an Intent on the phone, plain in the tests.
 */
internal class UpdateAnswers<C>(private val onEnded: () -> Unit = {}) {
    private var session = -1
    private var waiting: C? = null
    private val listeners = ArrayList<(UpdateInstall.Answer, String) -> Unit>()

    /** The session KaizoCore just handed a build to. */
    @Synchronized fun handed(id: Int) { session = id; waiting = null }

    /** An update on screen, told how a session ended. */
    @Synchronized fun attach(l: (UpdateInstall.Answer, String) -> Unit) { listeners += l }

    @Synchronized fun detach(l: (UpdateInstall.Answer, String) -> Unit) { listeners.removeAll { it === l } }

    /** A confirmation kept for the next screen. */
    val waitingToShow: Boolean @Synchronized get() = waiting != null

    /**
     * One answer from Android, for session [id]. A confirmation is shown with [show], which says true when it is up,
     * false when it could not be, and null when there is no screen up to show it on: it is kept then, for [resumed].
     */
    @Synchronized fun onStatus(id: Int, answer: UpdateInstall.Answer, confirm: C?, message: String, show: (C) -> Boolean?) {
        if (id < 0 || id != session) return
        if (answer == UpdateInstall.Answer.CONFIRM && confirm != null) {
            when (show(confirm)) {
                true -> waiting = null
                null -> waiting = confirm
                false -> end(UpdateInstall.Answer.REFUSED, message)
            }
            return
        }
        end(answer, message)
    }

    /** A screen is up again: a confirmation that had none to show on is shown now. */
    @Synchronized fun resumed(show: (C) -> Boolean?) {
        val c = waiting ?: return
        when (show(c)) {
            true -> waiting = null
            null -> Unit
            false -> end(UpdateInstall.Answer.REFUSED, "")
        }
    }

    private fun end(answer: UpdateInstall.Answer, message: String) {
        session = -1
        waiting = null
        for (l in listeners.toList()) l(answer, message)
        onEnded()
    }
}
