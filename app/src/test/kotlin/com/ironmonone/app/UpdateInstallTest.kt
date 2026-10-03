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
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

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

    // ------------------------------------------------------------------ redirects (rc32 audit P3 #75, Blake 2026-10-02)

    /**
     * Blake, 2026-10-02: "redirects to GitHub's release files". The real allowlist, held to real addresses: the site's APK
     * may go on to this project's release file on GitHub, and that on to the host GitHub serves release files from.
     * Nothing here touches the network.
     */
    @Test
    fun `only this project's release files on GitHub, and GitHub's file host after them, may be followed`() {
        val r = UpdateInstall.Redirects.KAIZOCORE
        val site = "https://willowcreek.group/kaizocore/KaizoCore-1.0.0-rc35.apk"
        val gh = "https://github.com/blakeportable7-del/KaizoCore/releases/download/v1.0.0-rc35/KaizoCore-1.0.0-rc35.apk"
        // Where GitHub sent v1.0.0-rc33's APK on 2026-10-02 (its signature made up), and the host it used before.
        val asset = "https://release-assets.githubusercontent.com/github-production-release-asset/1359426863/d430450c-f513-4a83-ad50-9bca25eaf818" +
            "?sp=r&sv=2018-11-09&sr=b&spr=https&se=2026-10-03T03%3A00%3A00Z&sig=abc%2Bdef%3D" +
            "&response-content-disposition=attachment%3B%20filename%3DKaizoCore-1.0.0-rc33.apk&response-content-type=application%2Fvnd.android.package-archive"
        val older = "https://objects.githubusercontent.com/github-production-release-asset-2e65be/1359426863/123456" +
            "?X-Amz-Algorithm=AWS4-HMAC-SHA256&X-Amz-Credential=x%2F20261002%2Fus-east-1%2Fs3%2Faws4_request"
        assertTrue(r.mayFollow(site, gh))
        assertTrue(r.mayFollow(gh, asset), "the encoded slash in GitHub's own query is not a way out of the path")
        assertTrue(r.mayFollow(gh, older))
        for (bad in listOf(
            "https://github.com/someone/KaizoCore/releases/download/v1/x.apk",
            "https://github.com/blakeportable7-del/KaizoCore-mirror/releases/download/v1/x.apk",
            "https://github.com/blakeportable7-del/KaizoCore/archive/refs/heads/main.zip",
            "https://github.com/blakeportable7-del/KaizoCore/releases/download/../../../someone/x/releases/download/v1/x.apk",
            "https://github.com/blakeportable7-del/KaizoCore/releases/download/%2e%2e/%2E%2E/someone/x.apk",
            "https://github.com/blakeportable7-del/KaizoCore/releases/download/v1%2f..%2f..%2fx.apk",
            "http://github.com/blakeportable7-del/KaizoCore/releases/download/v1/x.apk",
            "https://github.com@evil.example/blakeportable7-del/KaizoCore/releases/download/v1/x.apk",
            "https://github.com.evil.example/blakeportable7-del/KaizoCore/releases/download/v1/x.apk",
            "https://github.com:8443/blakeportable7-del/KaizoCore/releases/download/v1/x.apk",
            "https://github.com/blakeportable7-del/KaizoCore/releases/download/v1/x.apk\\@evil.example",
            "https://github.com/blakeportable7-del/KaizoCore/releases/download/v1/x .apk",
            asset,
            "https://willowcreek.group/kaizocore",
        )) assertFalse(r.mayFollow(site, bad), "from the site: $bad")
        for (bad in listOf(
            "https://evil.githubusercontent.com/github-production-release-asset/1/2",
            "https://release-assets.githubusercontent.com/other/1/2",
            "https://release-assets.githubusercontent.com.evil.example/github-production-release-asset/1/2",
            "https://objects.githubusercontent.com/github-production-repository-file-7b20d/1/2",
            "https://raw.githubusercontent.com/blakeportable7-del/KaizoCore/main/x.apk",
            "https://github.com/blakeportable7-del/KaizoCore/releases/download/v1/again.apk",
            "http://release-assets.githubusercontent.com/github-production-release-asset/1/2",
        )) assertFalse(r.mayFollow(gh, bad), "from GitHub: $bad")
        assertFalse(r.mayFollow(asset, asset), "nothing past the file host")
        assertFalse(r.mayFollow("https://example.com/x.apk", gh), "and nothing from anywhere else")
    }

    /** Local stand-ins, in the real ones' shape, for the site, this project's releases on GitHub and GitHub's file host. */
    private fun standIns(host: String) = UpdateInstall.Redirects(
        site = "$host/kaizocore/",
        releases = "$host/github.com/blakeportable7-del/KaizoCore/releases/download/",
        assets = listOf("$host/release-assets/github-production-release-asset/"),
    )

    private val ghPath = "/github.com/blakeportable7-del/KaizoCore/releases/download/v1.0.0-rc35/KaizoCore-1.0.0-rc35.apk"
    private val assetPath = "/release-assets/github-production-release-asset/1359426863/d430450c"
    private fun moved(to: String): (Wire) -> Unit = { w -> w.reply(302, ByteArray(0), "text/plain", listOf("Location" to to)) }

    @Test
    fun `the site may send the APK on to the project's release file on GitHub, and GitHub on to its file host`() = withDir { dir ->
        val asked = Collections.synchronizedList(ArrayList<Request>())
        serve(
            "/kaizocore/KaizoCore-1.0.0-rc35.apk" to { w -> asked += w.request; moved(ghPath)(w) },
            ghPath to { w -> asked += w.request; moved("$assetPath?sp=r&sig=abc%2Bdef&response-content-type=application%2Fvnd.android.package-archive")(w) },
            assetPath to { w -> asked += w.request; w.reply(200, build) },
        ) { host ->
            val got = UpdateInstall.download("$host/kaizocore/KaizoCore-1.0.0-rc35.apk", dir, build.size.toLong(), buildSha,
                freeBytes = plenty, redirects = standIns(host))
            assertIs<UpdateInstall.Download.Done>(got)
            assertContentEquals(build, got.file.readBytes())
            assertEquals(listOf("/kaizocore/KaizoCore-1.0.0-rc35.apk", ghPath, assetPath), asked.map { it.target.substringBefore('?') })
            assertTrue(asked.last().target.endsWith("response-content-type=application%2Fvnd.android.package-archive"), "GitHub's own address, query and all")
            for (r in asked) {
                assertEquals("KaizoCore", r.header("User-Agent"), "the user agent names no phone, at every step")
                assertEquals("identity", r.header("Accept-Encoding"))
            }
        }
    }

    @Test
    fun `bytes that came by a redirect are held to latest json's size and checksum all the same`() = withDir { dir ->
        val other = build.copyOf().also { it[7] = (it[7] + 1).toByte() }
        serve(
            "/kaizocore/a.apk" to moved(ghPath), ghPath to moved(assetPath), assetPath to { w -> w.reply(200, other) },
            "/kaizocore/b.apk" to moved("$ghPath.b"), "$ghPath.b" to moved("$assetPath.b"), "$assetPath.b" to { w -> w.reply(200, build.copyOf(build.size - 1)) },
        ) { host ->
            assertEquals(UpdateInstall.Download.Failed(UpdateInstall.Why.CHECKSUM),
                UpdateInstall.download("$host/kaizocore/a.apk", dir, build.size.toLong(), buildSha, freeBytes = plenty, redirects = standIns(host)))
            val short = UpdateInstall.download("$host/kaizocore/b.apk", dir, build.size.toLong(), buildSha, freeBytes = plenty, redirects = standIns(host))
            assertIs<UpdateInstall.Download.Failed>(short)
            assertEquals(UpdateInstall.Why.SIZE, short.why)
            assertEquals(emptyList(), leftovers(dir))
        }
    }

    @Test
    fun `a redirect to another repository or host, past GitHub, or past its file host is a build the site no longer has`() = withDir { dir ->
        val elsewhere = AtomicInteger()
        val other = "/github.com/someone/KaizoCore/releases/download/v1/x.apk"
        serve(
            "/kaizocore/other-repo.apk" to moved(other),
            "/kaizocore/other-host.apk" to moved("https://evil.example/x.apk"),
            "/kaizocore/skips-github.apk" to moved("$assetPath.c"),
            "/kaizocore/dots.apk" to moved("/github.com/blakeportable7-del/KaizoCore/releases/download/../../../../someone/x.apk"),
            "/kaizocore/github-elsewhere.apk" to moved("$ghPath.d"), "$ghPath.d" to moved("/somewhere/else.apk"),
            "/kaizocore/three.apk" to moved("$ghPath.e"), "$ghPath.e" to moved("$assetPath.e"), "$assetPath.e" to moved("$assetPath.f"),
            "/kaizocore/no-location.apk" to { w -> w.reply(302, ByteArray(0), "text/plain") },
            other to { w -> elsewhere.incrementAndGet(); w.reply(200, build) },
            "$assetPath.c" to { w -> elsewhere.incrementAndGet(); w.reply(200, build) },
            "/somewhere/else.apk" to { w -> elsewhere.incrementAndGet(); w.reply(200, build) },
            "$assetPath.f" to { w -> elsewhere.incrementAndGet(); w.reply(200, build) },
        ) { host ->
            for (name in listOf("other-repo", "other-host", "skips-github", "dots", "github-elsewhere", "three", "no-location")) {
                val got = UpdateInstall.download("$host/kaizocore/$name.apk", dir, build.size.toLong(), buildSha, freeBytes = plenty, redirects = standIns(host))
                assertEquals(UpdateInstall.Download.Failed(UpdateInstall.Why.GONE, "HTTP 302"), got, name)
            }
            assertEquals(0, elsewhere.get(), "nothing anywhere else was asked for")
            assertEquals(emptyList(), leftovers(dir))
        }
    }

    @Test
    fun `an error answer is refused, and a redirect anywhere but the project's release files is not followed`() = withDir { dir ->
        val elsewhere = AtomicInteger()
        serve(
            "/gone.apk" to { w -> w.reply(404, "not here".toByteArray(), "text/plain") },
            "/moved.apk" to { w -> w.reply(302, ByteArray(0), "text/plain", listOf("Location" to "/other.apk")) },
            "/retired.apk" to { w -> w.reply(301, ByteArray(0), "text/plain", listOf("Location" to "/kaizocore")) },
            "/broken.apk" to { w -> w.reply(500, "oops".toByteArray(), "text/plain") },
            "/other.apk" to { w -> elsewhere.incrementAndGet(); w.reply(200, build) },
        ) { host ->
            // A build the site sends on, or no longer has, is one a newer build replaced (rc32 audit P3 #76).
            assertEquals(UpdateInstall.Download.Failed(UpdateInstall.Why.GONE, "HTTP 404"),
                UpdateInstall.download("$host/gone.apk", dir, build.size.toLong(), buildSha, freeBytes = plenty))
            assertEquals(UpdateInstall.Download.Failed(UpdateInstall.Why.GONE, "HTTP 302"),
                UpdateInstall.download("$host/moved.apk", dir, build.size.toLong(), buildSha, freeBytes = plenty))
            assertEquals(UpdateInstall.Download.Failed(UpdateInstall.Why.GONE, "HTTP 301"),
                UpdateInstall.download("$host/retired.apk", dir, build.size.toLong(), buildSha, freeBytes = plenty))
            assertEquals(UpdateInstall.Download.Failed(UpdateInstall.Why.HTTP, "HTTP 500"),
                UpdateInstall.download("$host/broken.apk", dir, build.size.toLong(), buildSha, freeBytes = plenty))
            assertEquals(0, elsewhere.get(), "the file is fetched from the one address latest.json named")
            assertEquals(emptyList(), leftovers(dir))
        }
    }

    /**
     * rc32 audit P3 #76: a prompt held for the process outlived a release; its old APK now answers with a redirect, and the
     * player was told to check the connection, again at every Try again. The site answering is not a connection problem,
     * and latest.json is asked again for the build that replaced it.
     */
    @Test
    fun `a build the site no longer has is said as such, and a newer one in latest json takes its place`() {
        val connection = UpdateCheck.failText(UpdateInstall.Why.NETWORK, 120_536_427)
        assertEquals("The download stopped. Check the connection and try again.", connection)
        assertNotEquals(connection, UpdateCheck.failText(UpdateInstall.Why.GONE, 120_536_427))
        assertNotEquals(connection, UpdateCheck.failText(UpdateInstall.Why.HTTP, 120_536_427))
        assertTrue("no longer" in UpdateCheck.failText(UpdateInstall.Why.GONE, 120_536_427))
        val held = UpdateCheck.demo(41).copy(versionCode = 42, version = "1.0.0-rc33")
        val newer = held.copy(versionCode = 43, version = "1.0.0-rc34")
        assertEquals(newer, UpdateCheck.replacement(held, newer))
        assertNull(UpdateCheck.replacement(held, held), "the same build again: a release pulled back, not a new one")
        assertNull(UpdateCheck.replacement(held, held.copy(versionCode = 41)))
        assertNull(UpdateCheck.replacement(held, null), "no answer")
        // The updater asks again on that answer, and offers what it finds in place of the held build.
        val ui = read("UpdateUi.kt")
        val gone = ui.substringAfter("if (got.why == UpdateInstall.Why.GONE) {").substringBefore("\n                        }")
        assertTrue("UpdateCheck.fetch()" in gone && "UpdateCheck.replacement(m, it)" in gone && "onReplaced" in gone, gone)
        assertTrue("onReplaced = { newer -> found = newer; heldForProcess = newer }" in ui, "the prompt shows the newer build")
    }

    /**
     * rc32 audit P2 #103: the room checked was for one copy, and Android writes a second one into its install session, so
     * a nearly full phone downloaded the whole build and then heard "Android did not install it", and Try again fetched
     * it all again. The room is for both, and a build downloaded and checked is tried again from the phone.
     */
    @Test
    fun `a build downloaded and checked is kept for the install to be tried again`() = withDir { dir ->
        serve("/a.apk" to { w -> w.reply(200, build) }) { host ->
            val got = UpdateInstall.download("$host/a.apk", dir, build.size.toLong(), buildSha, freeBytes = plenty)
            assertIs<UpdateInstall.Download.Done>(got)
            assertEquals(got.file, UpdateInstall.downloaded(dir, build.size.toLong(), buildSha))
            assertNull(UpdateInstall.downloaded(dir, build.size.toLong(), "0".repeat(64)), "another build's checksum")
            assertNull(UpdateInstall.downloaded(dir, build.size + 1L, buildSha), "another build's size")
            got.file.appendBytes(byteArrayOf(1))
            assertNull(UpdateInstall.downloaded(dir, build.size.toLong(), buildSha), "a file that changed is not trusted")
        }
        assertNull(UpdateInstall.downloaded(File(dir, "none"), 10, "0".repeat(64)))
        assertEquals(2L * 120_536_427 + UpdateInstall.SPARE_BYTES, UpdateInstall.needBytes(120_536_427))
        val ui = read("UpdateUi.kt")
        assertTrue("UpdateInstall.downloaded(dir, m.bytes, m.sha256)?.let { UpdateInstall.Download.Done(it) }" in ui, "Try again starts from it")
        assertTrue("if (handed.noSpace) UpdateCheck.failText(UpdateInstall.Why.NO_SPACE, m.bytes)" in ui, "and a full phone is told so")
    }

    // ------------------------------------------------------------------ Android's answer outlives the screen (rc32 audit P2 #105)

    @Test
    fun `a confirmation that comes with no screen up is kept, and shown when one comes back`() {
        var ended = 0
        val a = UpdateAnswers<String>(onEnded = { ended++ })
        val told = ArrayList<UpdateInstall.Answer>()
        val shown = ArrayList<String>()
        a.handed(7)
        // The update's screen was closed while its build was handed over: no screen attached, no activity up.
        a.onStatus(7, UpdateInstall.Answer.CONFIRM, "confirm 7", "", show = { null })
        assertTrue(a.waitingToShow, "kept, not dropped")
        a.resumed { shown += it; true }
        assertEquals(listOf("confirm 7"), shown, "Android's question is put up when KaizoCore is back")
        assertFalse(a.waitingToShow)
        // The player answers no: a screen that is attached hears it, and the session is over.
        val l: (UpdateInstall.Answer, String) -> Unit = { ans, _ -> told += ans }
        a.attach(l)
        a.onStatus(7, UpdateInstall.Answer.DECLINED, null, "", show = { true })
        assertEquals(listOf(UpdateInstall.Answer.DECLINED), told)
        assertEquals(1, ended, "the update is no longer under way")
        // The same session again, or another one, is not acted on.
        a.onStatus(7, UpdateInstall.Answer.REFUSED, null, "late", show = { true })
        a.onStatus(8, UpdateInstall.Answer.CONFIRM, "confirm 8", "", show = { shown += it; true })
        assertEquals(listOf(UpdateInstall.Answer.DECLINED), told)
        assertEquals(listOf("confirm 7"), shown, "an answer for a build that was not handed over starts nothing")
        a.detach(l)
    }

    @Test
    fun `a confirmation shown at once needs no resume, and one that cannot be shown ends the update as refused`() {
        var ended = 0
        val a = UpdateAnswers<String>(onEnded = { ended++ })
        val told = ArrayList<Pair<UpdateInstall.Answer, String>>()
        a.attach { ans, msg -> told += ans to msg }
        a.handed(3)
        a.onStatus(3, UpdateInstall.Answer.CONFIRM, "c", "", show = { true })
        assertFalse(a.waitingToShow)
        a.resumed { fail("already shown") }
        assertEquals(0, ended)
        a.onStatus(3, UpdateInstall.Answer.CONFIRM, "c", "no activity", show = { false })
        assertEquals(listOf(UpdateInstall.Answer.REFUSED to "no activity"), told)
        assertEquals(1, ended)
        // A new hand-over is a new session, with nothing kept from the last.
        a.handed(4)
        a.onStatus(4, UpdateInstall.Answer.INSTALLED, null, "", show = { true })
        assertEquals(UpdateInstall.Answer.INSTALLED, told.last().first)
        assertEquals(2, ended)
    }

    @Test
    fun `the status receiver lives with the app, and nothing can be pressed while the build is handed over`() {
        val main = read("MainActivity.kt")
        assertTrue("UpdateStatus.register(this)" in main.substringAfter("override fun onCreate(").substringBefore("setContent {"))
        assertTrue("UpdateStatus.resumed(this)" in main.substringAfter("override fun onResume()").substringBefore("}"))
        assertTrue("UpdateStatus.paused(this)" in main.substringAfter("override fun onPause()").substringBefore("}"))
        val ui = read("UpdateUi.kt")
        assertTrue("ContextCompat.registerReceiver(context.applicationContext, receiver" in ui, "registered on the application, not a screen")
        assertFalse("unregisterReceiver" in ui, "and never taken down with a screen")
        assertTrue("UpdateStatus.answers.attach(listener)" in ui && "UpdateStatus.answers.detach(listener)" in ui)
        assertTrue("UpdateInstall.install(context, got.file) { id -> UpdateStatus.answers.handed(id) }" in ui, "the session is known before its answer can come")
        assertTrue("Step.Handing -> Unit\n                    Step.Installing -> Gen3Button(UpdateCheck.CLOSE)" in ui, "no Close until Android has the build")
        assertTrue("enabled = !checking && installed != null && !UpdateStatus.busy" in ui, "Check now waits for the update")
        assertTrue("onSession(id)\n            installer.openSession(id)" in read("UpdateInstall.kt"))
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
            // Room for the download and for Android's copy of it (rc32 audit P2 #103): one copy and the spare is not enough.
            val oneCopy: (File) -> Long = { build.size + UpdateInstall.SPARE_BYTES }
            assertEquals(UpdateInstall.Download.Failed(UpdateInstall.Why.NO_SPACE),
                UpdateInstall.download("$host/a.apk", dir, build.size.toLong(), buildSha, freeBytes = oneCopy))
            val tight: (File) -> Long = { UpdateInstall.needBytes(build.size.toLong()) - 1 }
            assertEquals(UpdateInstall.Download.Failed(UpdateInstall.Why.NO_SPACE),
                UpdateInstall.download("$host/a.apk", dir, build.size.toLong(), buildSha, freeBytes = tight))
            assertEquals(0, asked.get(), "the phone is asked for room before the site is asked for the file")
            val enough: (File) -> Long = { UpdateInstall.needBytes(build.size.toLong()) }
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
            // Above the 400 ms timeout with room, below the server's 5 s stall (rc32 audit P3 #83).
            assertTrue(ms < 4_000, "gave up after $ms ms")
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

    /**
     * RC35-NOTICED N #8: the launch check swept on every first composition of the prompt that held nothing, so an
     * activity rebuilt while INFO's card was downloading deleted the download under way. Only a process's first sweep
     * clears the folder, and a download of the process's own counts as that first look.
     */
    @Test
    fun `the launch sweep runs once a process and never under a download of its own`() {
        val cache = Files.createTempDirectory("cache").toFile()
        val elsewhere = Files.createTempDirectory("dl").toFile()
        try {
            val d = UpdateInstall.dir(cache).apply { mkdirs() }
            File(d, UpdateInstall.APK).writeText("old build")
            val once = java.util.concurrent.atomic.AtomicBoolean(false)
            UpdateInstall.sweepOnce(cache, once)
            assertFalse(d.exists(), "the launch's sweep clears the last launch's download")
            d.mkdirs()
            val under = File(d, UpdateInstall.APK + ".part").apply { writeText("half of this launch's download") }
            UpdateInstall.sweepOnce(cache, once)   // the activity rebuilt, the prompt composed again
            assertTrue(under.isFile, "the download under way is still there")
            // A download started before the first sweep is this process's own: the process's sweep is spent by it.
            UpdateInstall.download("http://127.0.0.1:1/a.apk", elsewhere, 10, "0".repeat(64), freeBytes = { 0L }, timeoutMs = 200)
            UpdateInstall.sweepOnce(cache)
            assertTrue(under.isFile, "a download of its own marks the process swept")
        } finally {
            cache.deleteRecursively(); elsewhere.deleteRecursively()
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
            UpdateCheck.TRY_AGAIN, UpdateCheck.IN_BROWSER, UpdateCheck.NEEDS_ALLOW, UpdateCheck.HANDING, UpdateCheck.INSTALLING, UpdateCheck.DECLINED,
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
        assertEquals("Not enough free space: the update needs about 275 MB.", UpdateCheck.failText(UpdateInstall.Why.NO_SPACE, 120_536_427),
            "the download and Android's copy of it (rc32 audit P2 #103)")
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
        assertTrue("if (step != Step.Installing && step != Step.Handing) return" in ui, "only the updater that handed over a build acts on an answer")
        assertTrue("if (!UpdateInstall.canInstall(context)) {" in ui, "the permission is asked for before 120 MB is downloaded")
        assertTrue("Lifecycle.Event.ON_RESUME) u.resumed()" in ui, "back from Android's settings, it carries on")
        assertTrue("val stop = { cancel || !alive.isActive }" in ui, "a screen that goes away stops the download")
        assertTrue("onDismiss = { if (!u.busy) close(UpdateCheck.LATER_SNOOZE_MS) }" in ui, "a tap outside does not drop a download under way")
        assertTrue("UpdateInstall.sweepOnce(context.cacheDir)" in read("UpdateCheck.kt"), "an old download is cleared at launch, once")
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
