package com.ironmonone.app.stream.twitch

import com.ironmonone.app.stream.WebSocket
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** EventSub over a real local WebSocket server: welcome, keepalive, reconnect, revocation, a dead connection. */
class EventSubTest {

    private class Run(val es: EventSub, val subscribed: MutableList<String>, val lines: MutableList<ChatLine>) {
        @Volatile var end: EventSub.End? = null
        lateinit var t: Thread
    }

    private fun start(fake: FakeEventSub, slackMs: Int = 10_000, sub: (String) -> EventSub.Subscribed = { EventSub.Subscribed.Ok }): Run {
        val subscribed = CopyOnWriteArrayList<String>()
        val lines = CopyOnWriteArrayList<ChatLine>()
        val es = EventSub(subscribe = { subscribed += it; sub(it) }, onChat = { lines += it }, slackMs = slackMs)
        val r = Run(es, subscribed, lines)
        r.t = thread(isDaemon = true) { r.end = es.run(fake.url + "?keepalive_timeout_seconds=10") }
        return r
    }

    @Test
    fun `the welcome's session id is subscribed, chat arrives, keepalives and duplicates change nothing`() {
        FakeEventSub().use { fake ->
            val r = start(fake)
            val c = fake.next()
            assertEquals("/ws?keepalive_timeout_seconds=10", fake.paths[0], "the keepalive window is asked for on the address")
            c.welcome("AQoQexAWVYKSTIu4ec_2VAxyuhAB")
            waitFor(what = "subscribe") { r.subscribed.isNotEmpty() }
            assertEquals(listOf("AQoQexAWVYKSTIu4ec_2VAxyuhAB"), r.subscribed)
            c.keepalive(1)
            c.chat("a1", "!pokemon")
            c.chat("a1", "!pokemon")  // the same message_id again: dropped
            c.keepalive(2)
            c.chat("a2", "!moves  Mothim", chatter = "7", source = "555")
            waitFor(what = "two lines") { r.lines.size == 2 }
            Thread.sleep(100)
            assertEquals(listOf("!pokemon", "!moves  Mothim"), r.lines.map { it.text })
            assertEquals("7", r.lines[1].chatterId); assertEquals("555", r.lines[1].sourceBroadcasterId)
            assertEquals(null, r.lines[0].sourceBroadcasterId)
            r.es.stop(); r.t.join(3000)
            assertEquals(EventSub.End.Stopped, r.end)
        }
    }

    @Test
    fun `a ping is answered with a pong carrying the same bytes`() {
        FakeEventSub().use { fake ->
            val r = start(fake)
            val c = fake.next()
            c.welcome("s1")
            waitFor { r.subscribed.isNotEmpty() }
            c.ping("hello")
            val f = assertNotNull(c.fromClient.poll(3, TimeUnit.SECONDS))
            assertEquals(WebSocket.OP_PONG, f.opcode); assertEquals("hello", String(f.payload))
            r.es.stop(); r.t.join(3000)
        }
    }

    @Test
    fun `session_reconnect moves to the new address, keeps the subscription, and closes the old connection after the welcome`() {
        FakeEventSub().use { fake ->
            val r = start(fake)
            val old = fake.next()
            old.welcome("s1")
            waitFor { r.subscribed.size == 1 }
            old.reconnect(fake.url + "?reconnect=1")
            val fresh = fake.next()
            assertTrue(fake.paths[1].endsWith("?reconnect=1"))
            Thread.sleep(150)
            assertTrue(old.fromClient.none { it.opcode == WebSocket.OP_CLOSE }, "the old connection stays open until the new welcome")
            fresh.welcome("s2")
            val bye = assertNotNull(old.fromClient.poll(3, TimeUnit.SECONDS))
            assertEquals(WebSocket.OP_CLOSE, bye.opcode)
            fresh.chat("b1", "!attempts")
            waitFor(what = "chat on the new connection") { r.lines.size == 1 }
            assertEquals(listOf("s1"), r.subscribed, "subscriptions carry over: nothing is subscribed again")
            r.es.stop(); r.t.join(3000)
        }
    }

    @Test
    fun `revocation ends the session with Twitch's reason`() {
        FakeEventSub().use { fake ->
            val r = start(fake)
            val c = fake.next()
            c.welcome("s1")
            waitFor { r.subscribed.isNotEmpty() }
            c.revoke("authorization_revoked")
            r.t.join(3000)
            assertEquals("authorization_revoked", assertIs<EventSub.End.Revoked>(r.end).status)
        }
    }

    @Test
    fun `a connection that goes quiet past the keepalive window is lost, and a close frame is reported with its code`() {
        FakeEventSub().use { fake ->
            // keepalive 10 s from the welcome; the slack takes it down to 300 ms so the test does not wait 10 s.
            val r = start(fake, slackMs = -9_700)
            fake.next().welcome("s1")
            r.t.join(5000)
            assertEquals("keepalive missed", assertIs<EventSub.End.Lost>(r.end).why)
        }
        FakeEventSub().use { fake ->
            val r = start(fake)
            val c = fake.next()
            c.welcome("s1")
            waitFor { r.subscribed.isNotEmpty() }
            c.closeWith(4003)
            r.t.join(3000)
            assertEquals(4003, assertIs<EventSub.End.Closed>(r.end).code)
        }
    }

    @Test
    fun `a refused subscription ends the session and says why`() {
        FakeEventSub().use { fake ->
            val r = start(fake) { EventSub.Subscribed.Unauthorized }
            fake.next().welcome("s1")
            r.t.join(3000)
            assertEquals(EventSub.Subscribed.Unauthorized, assertIs<EventSub.End.NotSubscribed>(r.end).result)
        }
    }

    @Test
    fun `client frames are masked and a server frame is read whole when fragmented`() {
        val mask = byteArrayOf(0x37, 0xFA.toByte(), 0x21, 0x3D)
        // RFC 6455 section 5.7: a masked "Hello".
        val f = WsClient.masked(WebSocket.OP_TEXT, "Hello".toByteArray(), mask)
        assertEquals(listOf(0x81, 0x85, 0x37, 0xfa, 0x21, 0x3d, 0x7f, 0x9f, 0x4d, 0x51, 0x58), f.map { it.toInt() and 0xFF })
        FakeEventSub().use { fake ->
            val r = start(fake)
            val c = fake.next()
            // The welcome in two fragments: "first half" (FIN 0) then a continuation (FIN 1).
            val text = """{"metadata":{"message_id":"w","message_type":"session_welcome"},"payload":{"session":{"id":"frag","keepalive_timeout_seconds":10}}}""".toByteArray()
            val half = text.size / 2
            c.socket.getOutputStream().apply {
                write(WebSocket.encode(WebSocket.OP_TEXT, text, 0, half, fin = false))
                write(WebSocket.encode(WebSocket.OP_CONTINUATION, text, half, text.size - half, fin = true))
                flush()
            }
            waitFor(what = "fragmented welcome") { r.subscribed == listOf("frag") }
            r.es.stop(); r.t.join(3000)
        }
    }
}
