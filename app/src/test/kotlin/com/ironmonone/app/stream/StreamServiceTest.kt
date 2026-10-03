package com.ironmonone.app.stream

import java.io.File
import java.net.ServerSocket
import java.nio.file.Files
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.w3c.dom.NodeList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The stream runs as a foreground service (rc32 audit P3 #80; Blake, 2026-10-02). Its "KaizoCore is streaming"
 * notification was a plain one, which outlives its process on Android: after Home, swiping KaizoCore away in Recents
 * ended the server and left the notification up with nothing streaming. Now StreamService starts and stops with the
 * server, the notification is the service's, and a swipe from Recents ends both. Android classes, so past the hub the
 * wiring and the manifest are read.
 */
class StreamServiceTest {
    private fun src(path: String) = File(path).readText().replace("\r\n", "\n")
    private fun NodeList.elements() = (0 until length).map { item(it) as Element }
    private val android = "http://schemas.android.com/apk/res/android"

    /** Runs [body] with the hub serving a fake game, on a free port, and recording what it asks of the service. */
    private fun hub(body: (dir: File, calls: MutableList<Boolean>) -> Unit) {
        val calls = mutableListOf<Boolean>()
        val keep = StreamHub.service
        val dir = Files.createTempDirectory("stream-service").toFile()
        StreamHub.feed = FakeGameFeed()
        StreamHub.service = { calls += it }
        try {
            body(dir, calls)
        } finally {
            StreamHub.stop()
            StreamHub.service = keep
            StreamHub.feed = null
            dir.deleteRecursively()
        }
    }

    @Test
    fun `the service starts once the server is up, and only then`() = hub { dir, calls ->
        ServerSocket(0).use { busy ->
            assertNull(StreamHub.start(dir, busy.localPort), "the port is taken")
            assertEquals(emptyList(), calls, "no server, no service")
        }
        assertNotNull(StreamHub.start(dir, 0))
        assertTrue(StreamHub.running)
        assertEquals(listOf(true), calls, "started with the server")
        StreamHub.start(dir, 0)
        assertEquals(listOf(true), calls, "a server already up starts nothing more")
    }

    @Test
    fun `the service stops with the server`() = hub { dir, calls ->
        assertNotNull(StreamHub.start(dir, 0))
        calls.clear()
        StreamHub.stop()
        assertFalse(StreamHub.running)
        assertEquals(listOf(false), calls, "stopped with it")
    }

    @Test
    fun `a swipe from Recents ends the stream and the service, and Android does not bring the service back`() {
        val svc = src("src/main/kotlin/com/ironmonone/app/stream/StreamService.kt")
        assertTrue("override fun onTaskRemoved(rootIntent: Intent?) {\n        StreamHub.stop()\n        stopSelf()" in svc)
        assertTrue("return START_NOT_STICKY" in svc)
        assertFalse("START_STICKY\n" in svc || "START_REDELIVER_INTENT" in svc)
        // Started from the player's tap with startService, then made a foreground service of the type the manifest names.
        assertTrue("if (on) context.startService(intent) else context.stopService(intent)" in svc)
        assertTrue("ServiceCompat.startForeground(this, StreamReminder.ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)" in svc)
        assertTrue("{ on -> appContext?.let { StreamService.follow(it, on) } }" in src("src/main/kotlin/com/ironmonone/app/stream/StreamHub.kt"))
    }

    @Test
    fun `the manifest declares the service, its type and reason, and each permission with what it is for`() {
        val doc = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            .newDocumentBuilder().parse(File("src/main/AndroidManifest.xml"))
        val permissions = doc.getElementsByTagName("uses-permission").elements()
        for (p in listOf("android.permission.FOREGROUND_SERVICE", "android.permission.FOREGROUND_SERVICE_SPECIAL_USE")) {
            val e = permissions.singleOrNull { it.getAttributeNS(android, "name") == p }
            assertNotNull(e, p)
            // The comment right above it says what it is for, as every other permission's does.
            val note = generateSequence(e.previousSibling) { it.previousSibling }.first { it.nodeType != Node.TEXT_NODE }
            assertEquals(Node.COMMENT_NODE, note.nodeType, p)
            assertTrue("StreamService" in note.textContent, p)
        }
        val service = doc.getElementsByTagName("service").elements().single { it.getAttributeNS(android, "name") == ".stream.StreamService" }
        assertEquals("false", service.getAttributeNS(android, "exported"))
        assertEquals("specialUse", service.getAttributeNS(android, "foregroundServiceType"))
        assertEquals("false", service.getAttributeNS(android, "stopWithTask"), "true would stop it without onTaskRemoved")
        val reason = service.getElementsByTagName("property").elements().single()
        assertEquals("android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE", reason.getAttributeNS(android, "name"))
        val why = reason.getAttributeNS(android, "value")
        assertTrue("OBS" in why && 0x2014.toChar() !in why && 0x2013.toChar() !in why, why)
    }

    @Test
    fun `in front the notification is quiet and says how to stop, in the background it pops up with the way back`() {
        assertEquals(StreamReminder.Words(StreamReminder.TEXT, alerts = true), StreamReminder.words(away = true))
        assertEquals(StreamReminder.Words(StreamReminder.TEXT_ON, alerts = false), StreamReminder.words(away = false))
        val reminder = src("src/main/kotlin/com/ironmonone/app/stream/StreamReminder.kt")
        assertTrue(".setSilent(!w.alerts)" in reminder)
        // Back in front, the service's notification changes its words; only one no service holds is taken down.
        assertTrue("if (StreamService.up && StreamHub.running && allowed(context)) build(context, service = true) else null" in reminder)
        assertTrue("if (n != null) NotificationManagerCompat.from(context).notify(ID, n) else NotificationManagerCompat.from(context).cancel(ID)" in reminder)
        // The service shows the words for where KaizoCore is when it starts: the player may have left already.
        assertTrue("StreamReminder.build(this, service = true)" in src("src/main/kotlin/com/ironmonone/app/stream/StreamService.kt"))
    }
}
