package com.ironmonone.app.stream

import java.io.ByteArrayInputStream
import java.io.EOFException
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * RFC 6455, against the RFC's own examples where it has them: the accept key of
 * section 1.3 and the frames of section 5.7.
 */
class WebSocketTest {

    private fun bytes(vararg b: Int) = ByteArray(b.size) { b[it].toByte() }
    private fun ascii(s: String) = s.toByteArray(Charsets.US_ASCII)
    private fun read(b: ByteArray, max: Int = 1 shl 20) = WebSocket.readFrame(ByteArrayInputStream(b), max)

    /** A client frame the way a browser writes it: masked with [mask], short length forms by size. */
    private fun masked(b0: Int, payload: ByteArray, mask: ByteArray = bytes(1, 2, 3, 4), lenBytes: ByteArray? = null): ByteArray {
        val len = lenBytes ?: when {
            payload.size < 126 -> bytes(0x80 or payload.size)
            else -> bytes(0x80 or 126, payload.size ushr 8, payload.size and 0xFF)
        }
        val body = ByteArray(payload.size) { (payload[it].toInt() xor mask[it and 3].toInt()).toByte() }
        return byteArrayOf(b0.toByte()) + len + mask + body
    }

    // ------------------------------------------------------------------ the handshake

    @Test
    fun `the RFC example key gives the RFC example accept value`() {
        assertEquals("s3pPLMBiTxaQ9kYGzzhZRbK+xOo=", WebSocket.acceptKey("dGhlIHNhbXBsZSBub25jZQ=="))
    }

    @Test
    fun `only a padded 16 byte base64 key is a key`() {
        assertTrue(WebSocket.validKey("dGhlIHNhbXBsZSBub25jZQ=="))
        assertFalse(WebSocket.validKey(null))
        assertFalse(WebSocket.validKey(""))
        assertFalse(WebSocket.validKey("dGhlIHNhbXBsZSBub25jZQ"), "unpadded")
        assertFalse(WebSocket.validKey("c2hvcnQ="), "5 bytes")
        assertFalse(WebSocket.validKey("dGhlIHNhbXBsZSBub25j!Q=="), "not base64")
        assertFalse(WebSocket.validKey("dGhlIHNhbXBsZSBub25jZQ== "), "25 characters")
    }

    @Test
    fun `the handshake reply is the 101 head with the accept value and no extensions`() {
        val head = String(WebSocket.handshakeResponse("dGhlIHNhbXBsZSBub25jZQ=="), Charsets.ISO_8859_1)
        assertEquals(
            "HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n" +
                "Sec-WebSocket-Accept: s3pPLMBiTxaQ9kYGzzhZRbK+xOo=\r\n\r\n",
            head,
        )
    }

    // ------------------------------------------------------------------ frames from the server

    @Test
    fun `server frames are the RFC's, and never masked`() {
        // 5.7: a single-frame unmasked text message, "Hello".
        assertContentEquals(bytes(0x81, 0x05, 0x48, 0x65, 0x6c, 0x6c, 0x6f), WebSocket.encode(WebSocket.OP_TEXT, ascii("Hello")))
        // 5.7: an unmasked ping request.
        assertContentEquals(bytes(0x89, 0x05, 0x48, 0x65, 0x6c, 0x6c, 0x6f), WebSocket.encode(WebSocket.OP_PING, ascii("Hello")))
        // 5.7: a fragmented unmasked text message, "Hel" then "lo".
        assertContentEquals(bytes(0x01, 0x03, 0x48, 0x65, 0x6c), WebSocket.encode(WebSocket.OP_TEXT, ascii("Hel"), fin = false))
        assertContentEquals(bytes(0x80, 0x02, 0x6c, 0x6f), WebSocket.encode(WebSocket.OP_CONTINUATION, ascii("lo")))
        // The text helper is the same frame.
        assertContentEquals(bytes(0x81, 0x02, 0x68, 0x69), WebSocket.text("hi"))
    }

    @Test
    fun `length fields take the shortest form at each boundary`() {
        // 5.7: 256 bytes in one unmasked binary frame is 0x82 0x7E 0x0100.
        val f256 = WebSocket.encode(WebSocket.OP_BINARY, ByteArray(256))
        assertContentEquals(bytes(0x82, 0x7E, 0x01, 0x00), f256.copyOf(4)); assertEquals(4 + 256, f256.size)
        // 5.7: 64 KiB is 0x82 0x7F 0x0000000000010000.
        val f64k = WebSocket.encode(WebSocket.OP_BINARY, ByteArray(65536))
        assertContentEquals(bytes(0x82, 0x7F, 0, 0, 0, 0, 0, 1, 0, 0), f64k.copyOf(10)); assertEquals(10 + 65536, f64k.size)
        // The edges either side of each form.
        assertContentEquals(bytes(0x82, 125), WebSocket.encode(WebSocket.OP_BINARY, ByteArray(125)).copyOf(2))
        assertContentEquals(bytes(0x82, 126, 0, 126), WebSocket.encode(WebSocket.OP_BINARY, ByteArray(126)).copyOf(4))
        assertContentEquals(bytes(0x82, 126, 0xFF, 0xFF), WebSocket.encode(WebSocket.OP_BINARY, ByteArray(65535)).copyOf(4))
        assertContentEquals(bytes(0x82, 0), WebSocket.encode(WebSocket.OP_BINARY, ByteArray(0)))
        // None of them sets the mask bit.
        for (n in intArrayOf(0, 1, 125, 126, 65535, 65536)) {
            assertEquals(0, WebSocket.encode(WebSocket.OP_BINARY, ByteArray(n))[1].toInt() and 0x80, "mask bit at $n")
        }
    }

    @Test
    fun `a range of a larger array is framed, and a range outside it is refused`() {
        val src = bytes(9, 9, 1, 2, 3, 9)
        assertContentEquals(bytes(0x82, 3, 1, 2, 3), WebSocket.encode(WebSocket.OP_BINARY, src, 2, 3))
        assertFailsWith<IllegalArgumentException> { WebSocket.encode(WebSocket.OP_BINARY, src, 4, 5) }
    }

    @Test
    fun `close carries its code and a reason cut to 123 bytes`() {
        assertContentEquals(bytes(0x88, 0x02, 0x03, 0xE8), WebSocket.close(WebSocket.CLOSE_NORMAL))
        val withReason = WebSocket.close(1002, "no")
        assertContentEquals(bytes(0x88, 0x04, 0x03, 0xEA, 'n'.code, 'o'.code), withReason)
        val long = ServerFrame.parse(WebSocket.close(1000, "x".repeat(500)))
        assertEquals(2 + 123, long.payload.size)
    }

    // ------------------------------------------------------------------ frames from the client

    @Test
    fun `the RFC's masked Hello reads back as Hello`() {
        val f = assertNotNull(read(bytes(0x81, 0x85, 0x37, 0xfa, 0x21, 0x3d, 0x7f, 0x9f, 0x4d, 0x51, 0x58)))
        assertEquals(WebSocket.OP_TEXT, f.opcode); assertTrue(f.fin)
        assertEquals("Hello", String(f.payload, Charsets.US_ASCII))
    }

    @Test
    fun `the RFC's masked pong reads back with its body`() {
        val f = assertNotNull(read(bytes(0x8a, 0x85, 0x37, 0xfa, 0x21, 0x3d, 0x7f, 0x9f, 0x4d, 0x51, 0x58)))
        assertEquals(WebSocket.OP_PONG, f.opcode)
        assertEquals("Hello", String(f.payload, Charsets.US_ASCII))
    }

    @Test
    fun `the mask is four bytes used in turn, whatever the length`() {
        val plain = ByteArray(11) { (it * 17 + 3).toByte() }
        for (mask in listOf(bytes(0, 0, 0, 0), bytes(0xFF, 0xFF, 0xFF, 0xFF), bytes(0x01, 0x02, 0x04, 0x08))) {
            val f = assertNotNull(read(masked(0x82, plain, mask)))
            assertContentEquals(plain, f.payload)
        }
    }

    @Test
    fun `a 16 bit length and a 64 bit length read back`() {
        val p300 = ByteArray(300) { it.toByte() }
        assertContentEquals(p300, assertNotNull(read(masked(0x82, p300))).payload)
        val p70k = ByteArray(70_000) { (it * 7).toByte() }
        val len64 = bytes(0x80 or 127, 0, 0, 0, 0, 0, 1, 0x11, 0x70)      // 70000 = 0x11170
        assertContentEquals(p70k, assertNotNull(read(masked(0x82, p70k, lenBytes = len64))).payload)
    }

    @Test
    fun `fragments read as separate frames with the fin bit telling the end`() {
        val input = ByteArrayInputStream(masked(0x01, ascii("Hel")) + masked(0x80, ascii("lo")))
        val a = assertNotNull(WebSocket.readFrame(input, 1000))
        val b = assertNotNull(WebSocket.readFrame(input, 1000))
        assertEquals(WebSocket.OP_TEXT to false, a.opcode to a.fin)
        assertEquals(WebSocket.OP_CONTINUATION to true, b.opcode to b.fin)
        assertNull(WebSocket.readFrame(input, 1000), "a clean end between frames")
    }

    @Test
    fun `a client frame that is not masked is a protocol error`() {
        val e = assertFailsWith<WebSocket.ProtocolError> { read(bytes(0x81, 0x05, 0x48, 0x65, 0x6c, 0x6c, 0x6f)) }
        assertEquals(1002, e.code)
    }

    @Test
    fun `reserved bits, unknown opcodes and broken control frames are protocol errors`() {
        val p = ascii("x")
        assertEquals(1002, assertFailsWith<WebSocket.ProtocolError> { read(masked(0xC1, p)) }.code, "RSV1")
        assertEquals(1002, assertFailsWith<WebSocket.ProtocolError> { read(masked(0xA1, p)) }.code, "RSV2")
        assertEquals(1002, assertFailsWith<WebSocket.ProtocolError> { read(masked(0x93, p)) }.code, "RSV3")
        assertEquals(1002, assertFailsWith<WebSocket.ProtocolError> { read(masked(0x83, p)) }.code, "opcode 3")
        assertEquals(1002, assertFailsWith<WebSocket.ProtocolError> { read(masked(0x8B, p)) }.code, "opcode 11")
        assertEquals(1002, assertFailsWith<WebSocket.ProtocolError> { read(masked(0x09, p)) }.code, "a ping that is fragmented")
        assertEquals(1002, assertFailsWith<WebSocket.ProtocolError> { read(masked(0x89, ByteArray(126))) }.code, "a ping over 125 bytes")
    }

    @Test
    fun `a length that is not in its shortest form is a protocol error`() {
        // 5 written as 16 bits, and 300 written as 64 bits.
        val short16 = bytes(0x82, 0x80 or 126, 0, 5, 1, 2, 3, 4, 1, 2, 3, 4, 5)
        assertEquals(1002, assertFailsWith<WebSocket.ProtocolError> { read(short16) }.code)
        val long64 = byteArrayOf(0x82.toByte(), (0x80 or 127).toByte(), 0, 0, 0, 0, 0, 0, 1, 0x2C, 1, 2, 3, 4) + ByteArray(300)
        assertEquals(1002, assertFailsWith<WebSocket.ProtocolError> { read(long64) }.code)
        val topBit = bytes(0x82, 0x80 or 127, 0x80, 0, 0, 0, 0, 0, 0, 0, 1, 2, 3, 4)
        assertEquals(1002, assertFailsWith<WebSocket.ProtocolError> { read(topBit) }.code)
    }

    @Test
    fun `a payload over the limit is 1009 and the limit is inclusive`() {
        val p = ByteArray(200)
        assertEquals(1009, assertFailsWith<WebSocket.ProtocolError> { read(masked(0x82, p), max = 199) }.code)
        assertEquals(200, assertNotNull(read(masked(0x82, p), max = 200)).payload.size)
    }

    @Test
    fun `a stream that ends inside a frame is an EOF, not a frame`() {
        val whole = masked(0x82, ascii("hello"))
        for (cut in intArrayOf(1, 2, 3, 6, whole.size - 1)) {
            assertFailsWith<EOFException>("cut at $cut") { read(whole.copyOf(cut)) }
        }
        assertNull(read(ByteArray(0)))
    }
}
