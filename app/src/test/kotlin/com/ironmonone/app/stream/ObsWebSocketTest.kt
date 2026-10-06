package com.ironmonone.app.stream

import java.io.ByteArrayInputStream
import java.net.ServerSocket
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The phone as an obs-websocket 5 client (ObsSocket), against FakeObs: the handshake, the password challenge, the
 * requests, and every way it fails, each as the error the settings screen can put into words.
 */
class ObsWebSocketTest {

    @Test
    fun `the challenge answer is obs-websocket's, checked against a value worked out apart`() {
        // base64(sha256(base64(sha256(password + salt)) + challenge)), computed with Python's hashlib for this test.
        assertEquals("1Ct943GAT+6YQUUX47Ia/ncufilbe6+oD6lY+5kaCu4=",
            ObsProtocol.auth("supersecretpassword", "lM1GncleQOaCu9lT1yeUZhFYnqhsLLP1G5lAGo3ixaI=", "+IxH4CnCiqpX1rM9scsNynZzbOe4KhDeYcTNS3PDaeY="))
    }

    @Test
    fun `with no password it identifies, subscribes to nothing and reads the scenes`() {
        FakeObs().use { obs ->
            ObsSocket.open("127.0.0.1", obs.port, "").use { c ->
                assertEquals("5.5.2", c.obsWebSocketVersion)
                @Suppress("UNCHECKED_CAST")
                val scenes = (c.call("GetSceneList")["scenes"] as List<Map<String, Any?>>).map { it["sceneName"] }
                assertEquals(obs.scenes.toSet(), scenes.toSet())
            }
            val id = obs.identifies.single()
            assertEquals(1L, id["rpcVersion"])
            assertEquals(0L, id["eventSubscriptions"], "no events: OBS sends the phone nothing it did not ask for")
            assertFalse(id.containsKey("authentication"), "no answer when OBS set no challenge")
        }
    }

    @Test
    fun `with the right password it gets in, and the answer is the one OBS works out`() {
        FakeObs(password = "hunter2 with spaces").use { obs ->
            ObsSocket.open("127.0.0.1", obs.port, "hunter2 with spaces").use { c -> c.call("GetVersion") }
            assertEquals(ObsProtocol.auth("hunter2 with spaces", obs.salt, obs.challenge), obs.identifies.single()["authentication"])
        }
    }

    @Test
    fun `a wrong password is said as such and is not one to retry`() {
        FakeObs(password = "right").use { obs ->
            val e = assertFailsWith<ObsError> { ObsSocket.open("127.0.0.1", obs.port, "wrong") }
            assertEquals(ObsError.Kind.WRONG_PASSWORD, e.kind)
            assertTrue(e.final)
            assertContains(e.message!!, "password")
        }
    }

    @Test
    fun `OBS asking for a password when none is set says where to find it`() {
        FakeObs(password = "set").use { obs ->
            val e = assertFailsWith<ObsError> { ObsSocket.open("127.0.0.1", obs.port, "") }
            assertEquals(ObsError.Kind.PASSWORD_NEEDED, e.kind)
            assertTrue(e.final)
            assertContains(e.message!!, "WebSocket Server Settings")
            assertTrue(obs.identifies.isEmpty(), "nothing is sent without an answer to give")
        }
    }

    @Test
    fun `nothing listening is unreachable, quickly, and worth another try`() {
        val port = freePort()
        val t0 = System.nanoTime()
        val e = assertFailsWith<ObsError> { ObsSocket.open("127.0.0.1", port, "", connectMs = 1000) }
        assertEquals(ObsError.Kind.UNREACHABLE, e.kind)
        assertFalse(e.final)
        assertTrue((System.nanoTime() - t0) / 1_000_000 < 3000)
    }

    @Test
    fun `an address that does not resolve is unreachable, not a crash`() {
        val e = assertFailsWith<ObsError> { ObsSocket.open("no-such-pc.invalid", 4455, "", connectMs = 1000) }
        assertEquals(ObsError.Kind.UNREACHABLE, e.kind)
        assertEquals(ObsError.Kind.UNREACHABLE, assertFailsWith<ObsError> { ObsSocket.open("127.0.0.1", 70000, "") }.kind)
    }

    @Test
    fun `a web page at that port is not OBS`() {
        ServerSocket(0).use { s ->
            Thread {
                runCatching {
                    s.accept().use { c ->
                        c.getInputStream().read(ByteArray(2048))
                        c.getOutputStream().write("HTTP/1.1 200 OK\r\nContent-Length: 2\r\n\r\nhi".toByteArray())
                    }
                }
            }.start()
            val e = assertFailsWith<ObsError> { ObsSocket.open("127.0.0.1", s.localPort, "", ioMs = 2000) }
            assertEquals(ObsError.Kind.NOT_OBS, e.kind)
        }
    }

    @Test
    fun `a socket that accepts and says nothing is not OBS either, and does not hang`() {
        ServerSocket(0).use { s ->
            Thread { runCatching { s.accept().use { Thread.sleep(3000) } } }.start()
            val t0 = System.nanoTime()
            val e = assertFailsWith<ObsError> { ObsSocket.open("127.0.0.1", s.localPort, "", ioMs = 500) }
            assertEquals(ObsError.Kind.NOT_OBS, e.kind)
            assertTrue((System.nanoTime() - t0) / 1_000_000 < 2500)
        }
    }

    @Test
    fun `a refused request is a refusal with OBS's code, and the connection carries on`() {
        FakeObs().use { obs ->
            obs.replayActive = false
            ObsSocket.open("127.0.0.1", obs.port, "").use { c ->
                val r = assertFailsWith<ObsRefused> { c.call("SaveReplayBuffer") }
                assertEquals(ObsProtocol.STATUS_OUTPUT_NOT_RUNNING, r.code)
                assertFailsWith<ObsRefused> { c.call("SetCurrentProgramScene", mapOf("sceneName" to "Not a scene")) }
                c.call("SetCurrentProgramScene", mapOf("sceneName" to "Battle"))
                assertEquals("Battle", obs.scene)
            }
        }
    }

    @Test
    fun `a dropped connection is an ObsError, never anything else`() {
        FakeObs().use { obs ->
            ObsSocket.open("127.0.0.1", obs.port, "").use { c ->
                obs.dropAll()
                val e = assertFailsWith<ObsError> { repeat(3) { c.call("GetVersion") } }
                assertEquals(ObsError.Kind.CLOSED, e.kind)
            }
        }
    }

    @Test
    fun `what the phone sends is masked, as RFC 6455 requires of a client, and reads back whole`() {
        for (size in listOf(0, 5, 125, 126, 300, 70_000)) {
            val payload = ByteArray(size) { (it * 7).toByte() }
            val frame = ObsSocket.clientFrame(0x1, payload)
            assertTrue(frame[1].toInt() and 0x80 != 0, "the mask bit is set for $size bytes")
            // The server side's own reader refuses an unmasked frame, so this also proves the mask is there.
            val back = WebSocket.readFrame(ByteArrayInputStream(frame), 1 shl 20)!!
            assertEquals(0x1, back.opcode)
            assertTrue(back.fin)
            assertTrue(payload.contentEquals(back.payload), "$size bytes come back as sent")
        }
    }

    @Test
    fun `the requests are obs-websocket's shape`() {
        @Suppress("UNCHECKED_CAST")
        val m = MiniJson(ObsProtocol.request("SetCurrentProgramScene", "kc1", mapOf("sceneName" to "Battle \"2\""))).parse() as Map<String, Any?>
        assertEquals(6L, m["op"])
        assertEquals(mapOf("requestType" to "SetCurrentProgramScene", "requestId" to "kc1", "requestData" to mapOf("sceneName" to "Battle \"2\"")), m["d"])
        @Suppress("UNCHECKED_CAST")
        val bare = (MiniJson(ObsProtocol.request("SaveReplayBuffer", "kc2")).parse() as Map<String, Any?>)["d"] as Map<String, Any?>
        assertNull(bare["requestData"])
    }

    @Test
    fun `the JSON reader takes what OBS sends and refuses a nest too deep`() {
        assertEquals(mapOf("a" to listOf(1L, 2.5, "xé\n", true, null)), JsonIn.parse("""{"a":[1,2.5,"xé\n",true,null]}"""))
        assertFailsWith<IllegalArgumentException> { JsonIn.parse("[".repeat(100) + "]".repeat(100)) }
        assertFailsWith<IllegalArgumentException> { JsonIn.parse("{\"a\":") }
    }
}
