package com.ironmonone.app

import android.content.pm.PackageInstaller
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.file.Files
import java.security.MessageDigest
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * One tap to update (UpdateInstall, 2026-09-29): the download is held to the size and SHA-256
 * latest.json names and a wrong file never reaches the installer; the installer's answers mean
 * what the screen says; and the parts that only run on a phone are wired as intended. The
 * network half runs for real against a server on 127.0.0.1.
 */
class UpdateInstallTest {

    private val build: ByteArray = Random(31).nextBytes(3 * 1024 * 1024 + 17)
    private val buildSha = sha(build)

    private fun sha(b: ByteArray) = MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }

    private fun withDir(block: (File) -> Unit) {
        val dir = Files.createTempDirectory("update").toFile()
        try { block(File(dir, UpdateInstall.DIR)) } finally { dir.deleteRecursively() }
    }

    private fun leftovers(dir: File): List<String> = dir.listFiles()?.map { it.name }.orEmpty()

    private val plenty: (File) -> Long = { Long.MAX_VALUE / 4 }

    // ------------------------------------------------------------------ download

    @Test
    fun `the build of the right size and checksum is kept, with progress to the end`() = withDir { dir ->
        val seen = ArrayList<Request>()
        serve("/kaizocore/KaizoCore-1.0.0-rc32.apk" to { w -> seen.add(w.request); w.reply(200, build) }) { host ->
            val progress = ArrayList<Pair<Long, Long>>()
            val got = UpdateInstall.download("$host/kaizocore/KaizoCore-1.0.0-rc32.apk?v=abc", dir, build.size.toLong(), buildSha,
                onProgress = { d, t -> progress.add(d to t) }, freeBytes = plenty)
            assertIs<UpdateInstall.Download.Done>(got)
            assertContentEquals(build, got.file.readBytes())
            assertEquals(listOf(UpdateInstall.APK), leftovers(dir), "the finished file and nothing else")
            assertTrue(progress.isNotEmpty() && progress.zipWithNext().all { (a, b) -> b.first > a.first }, "progress only goes up")
            assertEquals(build.size.toLong() to build.size.toLong(), progress.last(), "and ends at the whole file")
            assertEquals("KaizoCore", seen.single().header("User-Agent"), "the default user agent names the phone")
            assertEquals("identity", seen.single().header("Accept-Encoding"), "the byte count is the file's own")
        }
    }

    @Test
    fun `a wrong checksum is deleted and never offered`() = withDir { dir ->
        val other = build.copyOf().also { it[1000] = (it[1000] + 1).toByte() }
        serve("/a.apk" to { w -> w.reply(200, other) }) { host ->
            val got = UpdateInstall.download("$host/a.apk", dir, build.size.toLong(), buildSha, freeBytes = plenty)
            assertEquals(UpdateInstall.Download.Failed(UpdateInstall.Why.CHECKSUM), got)
            assertEquals(emptyList(), leftovers(dir))
        }
    }

    @Test
    fun `a file of another size is refused, whatever it claims`() = withDir { dir ->
        // Says so in its length.
        serve("/a.apk" to { w -> w.reply(200, build.copyOf(build.size - 1)) }) { host ->
            val got = UpdateInstall.download("$host/a.apk", dir, build.size.toLong(), buildSha, freeBytes = plenty)
            assertIs<UpdateInstall.Download.Failed>(got)
            assertEquals(UpdateInstall.Why.SIZE, got.why)
            assertEquals(emptyList(), leftovers(dir))
        }
        // Gives no length and runs long: it is cut off, not read to the end.
        val long = build + ByteArray(5 * 1024 * 1024)
        serve("/a.apk" to { w -> w.replyNoLength(200, long) }) { host ->
            val got = UpdateInstall.download("$host/a.apk", dir, build.size.toLong(), buildSha, freeBytes = plenty)
            assertIs<UpdateInstall.Download.Failed>(got)
            assertEquals(UpdateInstall.Why.SIZE, got.why)
            assertEquals(emptyList(), leftovers(dir))
        }
        // Gives no length and stops short.
        serve("/a.apk" to { w -> w.replyNoLength(200, build.copyOf(build.size / 2)) }) { host ->
            val got = UpdateInstall.download("$host/a.apk", dir, build.size.toLong(), buildSha, freeBytes = plenty)
            assertIs<UpdateInstall.Download.Failed>(got)
            assertEquals(UpdateInstall.Why.SIZE, got.why)
            assertEquals(emptyList(), leftovers(dir))
        }
    }

    @Test
    fun `an error answer is refused, and a redirect is not followed`() = withDir { dir ->
        val elsewhere = AtomicInteger()
        serve(
            "/gone.apk" to { w -> w.reply(404, "not here".toByteArray(), "text/plain") },
            "/moved.apk" to { w -> w.reply(302, ByteArray(0), "text/plain", listOf("Location" to "/other.apk")) },
            "/other.apk" to { w -> elsewhere.incrementAndGet(); w.reply(200, build) },
        ) { host ->
            assertEquals(UpdateInstall.Download.Failed(UpdateInstall.Why.HTTP, "HTTP 404"),
                UpdateInstall.download("$host/gone.apk", dir, build.size.toLong(), buildSha, freeBytes = plenty))
            assertEquals(UpdateInstall.Download.Failed(UpdateInstall.Why.HTTP, "HTTP 302"),
                UpdateInstall.download("$host/moved.apk", dir, build.size.toLong(), buildSha, freeBytes = plenty))
            assertEquals(0, elsewhere.get(), "the file is fetched from the one address latest.json named")
            assertEquals(emptyList(), leftovers(dir))
        }
    }

    @Test
    fun `Cancel stops the download and leaves nothing`() = withDir { dir ->
        serve("/a.apk" to { w -> w.reply(200, build) }) { host ->
            var reports = 0
            val got = UpdateInstall.download("$host/a.apk", dir, build.size.toLong(), buildSha,
                onProgress = { _, _ -> reports++ }, cancelled = { reports >= 1 }, freeBytes = plenty)
            assertEquals(UpdateInstall.Download.Failed(UpdateInstall.Why.CANCELLED), got)
            assertEquals(emptyList(), leftovers(dir))
        }
    }

    @Test
    fun `no room means no download at all`() = withDir { dir ->
        val asked = AtomicInteger()
        serve("/a.apk" to { w -> asked.incrementAndGet(); w.reply(200, build) }) { host ->
            val tight: (File) -> Long = { build.size + UpdateInstall.SPARE_BYTES - 1 }
            assertEquals(UpdateInstall.Download.Failed(UpdateInstall.Why.NO_SPACE),
                UpdateInstall.download("$host/a.apk", dir, build.size.toLong(), buildSha, freeBytes = tight))
            assertEquals(0, asked.get(), "the phone is asked for room before the site is asked for the file")
            val enough: (File) -> Long = { build.size + UpdateInstall.SPARE_BYTES }
            assertIs<UpdateInstall.Download.Done>(UpdateInstall.download("$host/a.apk", dir, build.size.toLong(), buildSha, freeBytes = enough))
        }
    }

    @Test
    fun `a server that stops sending fails within the timeout`() = withDir { dir ->
        serve("/a.apk" to { w ->
            w.out.write("HTTP/1.1 200 OK\r\nContent-Length: ${build.size}\r\nConnection: close\r\n\r\n".toByteArray())
            w.out.write(build, 0, 1024)
            w.out.flush()
            Thread.sleep(5_000)
        }) { host ->
            val started = System.nanoTime()
            val got = UpdateInstall.download("$host/a.apk", dir, build.size.toLong(), buildSha, freeBytes = plenty, timeoutMs = 400)
            val ms = (System.nanoTime() - started) / 1_000_000
            assertIs<UpdateInstall.Download.Failed>(got)
            assertEquals(UpdateInstall.Why.NETWORK, got.why)
            assertTrue(ms < 3_000, "gave up after $ms ms")
            assertEquals(emptyList(), leftovers(dir))
        }
    }

    @Test
    fun `nothing listening is a failure, not an exception`() = withDir { dir ->
        val port = ServerSocket(0).use { it.localPort }
        val got = UpdateInstall.download("http://127.0.0.1:$port/a.apk", dir, 10, "0".repeat(64), freeBytes = plenty, timeoutMs = 500)
        assertIs<UpdateInstall.Download.Failed>(got)
        assertEquals(UpdateInstall.Why.NETWORK, got.why)
    }

    @Test
    fun `an earlier download is swept away`() {
        val cache = Files.createTempDirectory("cache").toFile()
        try {
            val d = UpdateInstall.dir(cache).apply { mkdirs() }
            File(d, UpdateInstall.APK).writeText("old build")
            File(d, UpdateInstall.APK + ".part").writeText("half a build")
            UpdateInstall.sweep(cache)
            assertFalse(d.exists())
            UpdateInstall.sweep(cache)   // and nothing to sweep is no error
        } finally {
            cache.deleteRecursively()
        }
    }

    // ------------------------------------------------------------------ the installer's answer

    @Test
    fun `the installer's answers mean what the screen says`() {
        assertEquals(UpdateInstall.Answer.CONFIRM, UpdateInstall.answer(PackageInstaller.STATUS_PENDING_USER_ACTION, hasConfirm = true))
        assertEquals(UpdateInstall.Answer.REFUSED, UpdateInstall.answer(PackageInstaller.STATUS_PENDING_USER_ACTION, hasConfirm = false),
            "asked to confirm with nothing to show")
        assertEquals(UpdateInstall.Answer.INSTALLED, UpdateInstall.answer(PackageInstaller.STATUS_SUCCESS, hasConfirm = false))
        assertEquals(UpdateInstall.Answer.DECLINED, UpdateInstall.answer(PackageInstaller.STATUS_FAILURE_ABORTED, hasConfirm = false))
        for (s in listOf(
            PackageInstaller.STATUS_FAILURE, PackageInstaller.STATUS_FAILURE_BLOCKED, PackageInstaller.STATUS_FAILURE_CONFLICT,
            PackageInstaller.STATUS_FAILURE_INCOMPATIBLE, PackageInstaller.STATUS_FAILURE_INVALID, PackageInstaller.STATUS_FAILURE_STORAGE,
        )) assertEquals(UpdateInstall.Answer.REFUSED, UpdateInstall.answer(s, hasConfirm = true), "status $s")
    }

    // ------------------------------------------------------------------ the words

    @Test
    fun `the words are plain and say what happens`() {
        val all = listOf(
            UpdateCheck.UPDATE, UpdateCheck.WHATS_NEW, UpdateCheck.LATER, UpdateCheck.CANCEL, UpdateCheck.CLOSE, UpdateCheck.ALLOW,
            UpdateCheck.TRY_AGAIN, UpdateCheck.IN_BROWSER, UpdateCheck.NEEDS_ALLOW, UpdateCheck.INSTALLING, UpdateCheck.DECLINED,
            UpdateCheck.downloading(43_900_000, 120_536_427),
            UpdateCheck.installFailed(""), UpdateCheck.installFailed("INSTALL_FAILED_UPDATE_INCOMPATIBLE"),
        ) + UpdateInstall.Why.values().map { UpdateCheck.failText(it, 120_536_427) }
        for (t in all) {
            assertTrue(t.isNotBlank())
            assertFalse(0x2014.toChar() in t || 0x2013.toChar() in t, "a dash: $t")
            assertFalse("AI" in t.split(Regex("[^A-Za-z]+")), "AI: $t")
        }
        assertEquals("Downloading... 43 of 121 MB", UpdateCheck.downloading(43_900_000, 120_536_427), "whole megabytes done, rounded down")
        assertEquals("Downloading... 0 of 121 MB", UpdateCheck.downloading(0, 120_536_427))
        assertEquals("Not enough free space: the update needs about 154 MB.", UpdateCheck.failText(UpdateInstall.Why.NO_SPACE, 120_536_427))
        assertEquals("Android did not install it.", UpdateCheck.installFailed(""))
        assertEquals("Android did not install it. It said: no room", UpdateCheck.installFailed("no room"))
        assertEquals(
            "You have 1.0.0-rc31. The new build is 121 MB from willowcreek.group. Update downloads it, checks it and asks " +
                "Android to install it over this one. Your saves, runs and settings stay.",
            UpdateCheck.promptBody("1.0.0-rc31", UpdateCheck.demo(40).copy(version = "1.0.0-rc32", bytes = 120_536_427)),
        )
    }

    // ------------------------------------------------------------------ wiring (runs only on a phone)

    private val src = File("src/main/kotlin/com/ironmonone/app")
    private fun read(name: String) = File(src, name).readText().replace("\r\n", "\n")

    @Test
    fun `the parts that only run on a phone are wired as intended`() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        assertTrue("android.permission.REQUEST_INSTALL_PACKAGES" in manifest, "without it Android refuses the session")
        assertTrue("android.permission.UPDATE_PACKAGES_WITHOUT_USER_ACTION" in manifest,
            "without it Android asks to confirm every update, even one KaizoCore installed")
        val install = read("UpdateInstall.kt")
        assertTrue("PackageInstaller.SessionParams.MODE_FULL_INSTALL" in install)
        assertTrue("params.setAppPackageName(context.packageName)" in install, "an update of this app and no other")
        assertTrue("if (Build.VERSION.SDK_INT >= 31) params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)" in install,
            "one tap: Android is asked not to ask again when KaizoCore installed the build it replaces")
        assertTrue("if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0" in install, "the installer writes its answer into the intent")
        assertTrue("Intent(ACTION_STATUS).setPackage(context.packageName)" in install, "the answer comes back to this app only")
        assertTrue("session.commit(status.intentSender)" in install)
        assertTrue("installer.abandonSession(id)" in install, "a session that failed half-way is not left behind")
        val ui = read("UpdateUi.kt")
        assertTrue("ContextCompat.RECEIVER_NOT_EXPORTED" in ui, "no other app can send the answer")
        assertTrue("if (step != Step.Installing) return" in ui, "only the updater that handed over a build acts on an answer")
        assertTrue("if (!UpdateInstall.canInstall(context)) {" in ui, "the permission is asked for before 120 MB is downloaded")
        assertTrue("Lifecycle.Event.ON_RESUME) u.resumed()" in ui, "back from Android's settings, it carries on")
        assertTrue("val stop = { cancel || !alive.isActive }" in ui, "a screen that goes away stops the download")
        assertTrue("onDismiss = { if (!u.busy) close(UpdateCheck.LATER_SNOOZE_MS) }" in ui, "a tap outside does not drop a download under way")
        assertTrue("UpdateInstall.sweep(context.cacheDir)" in read("UpdateCheck.kt"), "an old download is cleared at launch")
    }

    // ------------------------------------------------------------------ a server on 127.0.0.1

    private class Request(val line: String, val headers: List<Pair<String, String>>) {
        val target: String get() = line.split(' ').getOrElse(1) { "" }
        fun header(name: String): String? = headers.firstOrNull { it.first.equals(name, ignoreCase = true) }?.second
    }

    private class Wire(val request: Request, val out: OutputStream)

    private fun Wire.reply(code: Int, body: ByteArray, type: String = "application/vnd.android.package-archive", extra: List<Pair<String, String>> = emptyList()) {
        val head = StringBuilder("HTTP/1.1 $code Status\r\nContent-Type: $type\r\nContent-Length: ${body.size}\r\nConnection: close\r\n")
        for ((k, v) in extra) head.append(k).append(": ").append(v).append("\r\n")
        head.append("\r\n")
        out.write(head.toString().toByteArray(Charsets.ISO_8859_1))
        out.write(body)
        out.flush()
    }

    /** An answer with no length: the body ends when the connection closes. */
    private fun Wire.replyNoLength(code: Int, body: ByteArray) {
        out.write("HTTP/1.1 $code Status\r\nContent-Type: application/octet-stream\r\nConnection: close\r\n\r\n".toByteArray(Charsets.ISO_8859_1))
        runCatching { out.write(body); out.flush() }
    }

    private fun readRequest(input: InputStream): Request? {
        val raw = ByteArrayOutputStream()
        var tail = 0
        while (raw.size() < 16 * 1024) {
            val b = input.read()
            if (b < 0) return null
            raw.write(b)
            tail = (tail shl 8) or b
            if (tail == 0x0D0A0D0A) break
        }
        val lines = String(raw.toByteArray(), Charsets.ISO_8859_1).split("\r\n").filter { it.isNotEmpty() }
        if (lines.isEmpty()) return null
        return Request(lines[0], lines.drop(1).map { it.substringBefore(':').trim() to it.substringAfter(':', "").trim() })
    }

    private fun serve(vararg routes: Pair<String, (Wire) -> Unit>, block: (host: String) -> Unit) {
        val listener = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        val open = Collections.synchronizedList(ArrayList<Socket>())
        Thread {
            while (!listener.isClosed) {
                val socket = try { listener.accept() } catch (e: Exception) { break }
                open.add(socket)
                Thread conn@{
                    try {
                        val request = readRequest(socket.getInputStream()) ?: return@conn
                        val handler = routes.firstOrNull { it.first == request.target.substringBefore('?') }?.second
                        val wire = Wire(request, socket.getOutputStream())
                        if (handler != null) handler(wire) else wire.reply(404, "not here".toByteArray(), "text/plain")
                    } catch (e: Exception) {
                        // The client hung up, which is what several of these tests are for.
                    } finally {
                        runCatching { socket.close() }
                    }
                }.apply { isDaemon = true }.start()
            }
        }.apply { isDaemon = true }.start()
        try {
            block("http://127.0.0.1:${listener.localPort}")
        } finally {
            runCatching { listener.close() }
            synchronized(open) { open.forEach { runCatching { it.close() } } }
        }
    }
}
