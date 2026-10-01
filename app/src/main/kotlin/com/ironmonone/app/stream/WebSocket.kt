package com.ironmonone.app.stream

import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.util.Base64

/**
 * The parts of RFC 6455 a picture-and-sound stream needs, and nothing more:
 * the opening handshake, frames from the server (never masked), frames from
 * the client (always masked), ping, pong and close. No extensions (so no
 * compression to negotiate) and no subprotocols.
 *
 * It is hand-written for the reason the rest of this package is: the server
 * is five routes on a plain socket, and a WebSocket library would be a
 * dependency and a supply chain for a few hundred lines of framing.
 * The tests use the RFC's own examples (its section 1.3 accept key and the
 * frames of section 5.7). Added 2026-09-29 for the stream kit, which sends the
 * game's picture and sound over one.
 */
object WebSocket {
    /** RFC 6455 section 1.3: appended to the client's key before hashing. */
    const val GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"

    const val OP_CONTINUATION = 0x0
    const val OP_TEXT = 0x1
    const val OP_BINARY = 0x2
    const val OP_CLOSE = 0x8
    const val OP_PING = 0x9
    const val OP_PONG = 0xA

    const val CLOSE_NORMAL = 1000
    const val CLOSE_GOING_AWAY = 1001
    const val CLOSE_PROTOCOL_ERROR = 1002
    const val CLOSE_TOO_BIG = 1009

    /** One frame from the client, already unmasked. */
    class Frame(val fin: Boolean, val opcode: Int, val payload: ByteArray)

    /** The client broke the protocol; [code] is the close code to answer with. */
    class ProtocolError(val code: Int, message: String) : IOException(message)

    /** Sec-WebSocket-Accept for a client's Sec-WebSocket-Key. */
    fun acceptKey(clientKey: String): String {
        val sha = MessageDigest.getInstance("SHA-1").digest((clientKey + GUID).toByteArray(Charsets.ISO_8859_1))
        return Base64.getEncoder().encodeToString(sha)
    }

    /** A key is 16 random bytes in base64: 24 characters, padded. Anything else is not a WebSocket client. */
    fun validKey(key: String?): Boolean {
        if (key == null || key.length != 24) return false
        return try { Base64.getDecoder().decode(key).size == 16 } catch (e: IllegalArgumentException) { false }
    }

    /** The 101 reply that completes the handshake, header block and blank line included. */
    fun handshakeResponse(clientKey: String): ByteArray =
        ("HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n" +
            "Sec-WebSocket-Accept: ${acceptKey(clientKey)}\r\n\r\n").toByteArray(Charsets.ISO_8859_1)

    /**
     * One frame from the server: never masked (RFC 6455 section 5.1: a client
     * closes the connection on a masked frame), with the shortest length field
     * the payload allows (125 or less in the header byte, then 16 bits, then 64).
     */
    fun encode(opcode: Int, payload: ByteArray, off: Int = 0, len: Int = payload.size - off, fin: Boolean = true): ByteArray {
        require(off >= 0 && len >= 0 && off + len <= payload.size) { "payload range" }
        val headLen = when { len < 126 -> 2; len <= 0xFFFF -> 4; else -> 10 }
        val out = ByteArray(headLen + len)
        out[0] = ((if (fin) 0x80 else 0) or (opcode and 0x0F)).toByte()
        when (headLen) {
            2 -> out[1] = len.toByte()
            4 -> {
                out[1] = 126
                out[2] = (len ushr 8).toByte()
                out[3] = len.toByte()
            }
            else -> {
                out[1] = 127
                for (i in 0 until 8) out[2 + i] = ((len.toLong() ushr (56 - 8 * i)) and 0xFF).toByte()
            }
        }
        System.arraycopy(payload, off, out, headLen, len)
        return out
    }

    fun text(message: String): ByteArray = encode(OP_TEXT, message.toByteArray(Charsets.UTF_8))

    /** A close frame: the 16-bit code, then an optional reason of at most 123 bytes. */
    fun close(code: Int, reason: String = ""): ByteArray {
        val r = reason.toByteArray(Charsets.UTF_8).let { if (it.size > 123) it.copyOf(123) else it }
        val body = ByteArray(2 + r.size)
        body[0] = (code ushr 8).toByte()
        body[1] = code.toByte()
        System.arraycopy(r, 0, body, 2, r.size)
        return encode(OP_CLOSE, body)
    }

    /**
     * Reads one frame from a client. Returns null when the stream ends cleanly
     * between frames, throws [EOFException] when it ends inside one, and throws
     * [ProtocolError] for a frame RFC 6455 forbids: unmasked (clients must mask),
     * reserved bits or an unknown opcode, a control frame that is fragmented or
     * longer than 125 bytes, a length that is not in its shortest form, or a
     * payload over [maxPayload] (close code 1009).
     */
    fun readFrame(input: InputStream, maxPayload: Int): Frame? {
        val b0 = input.read()
        if (b0 < 0) return null
        val b1 = input.read()
        if (b1 < 0) throw EOFException("stream ended inside a frame header")

        val fin = (b0 and 0x80) != 0
        if ((b0 and 0x70) != 0) throw ProtocolError(CLOSE_PROTOCOL_ERROR, "reserved bits set")
        val opcode = b0 and 0x0F
        if (opcode != OP_CONTINUATION && opcode != OP_TEXT && opcode != OP_BINARY &&
            opcode != OP_CLOSE && opcode != OP_PING && opcode != OP_PONG) {
            throw ProtocolError(CLOSE_PROTOCOL_ERROR, "unknown opcode $opcode")
        }
        if ((b1 and 0x80) == 0) throw ProtocolError(CLOSE_PROTOCOL_ERROR, "client frames must be masked")

        var len = (b1 and 0x7F).toLong()
        if (len == 126L) {
            len = readFully(input, 2).let { ((it[0].toLong() and 0xFF) shl 8) or (it[1].toLong() and 0xFF) }
            if (len < 126) throw ProtocolError(CLOSE_PROTOCOL_ERROR, "length not in its shortest form")
        } else if (len == 127L) {
            len = readFully(input, 8).fold(0L) { acc, b -> (acc shl 8) or (b.toLong() and 0xFF) }
            if (len < 0) throw ProtocolError(CLOSE_PROTOCOL_ERROR, "length has its top bit set")
            if (len <= 0xFFFF) throw ProtocolError(CLOSE_PROTOCOL_ERROR, "length not in its shortest form")
        }
        if (opcode >= OP_CLOSE && (!fin || len > 125)) {
            throw ProtocolError(CLOSE_PROTOCOL_ERROR, "control frames are whole and at most 125 bytes")
        }
        if (len > maxPayload) throw ProtocolError(CLOSE_TOO_BIG, "frame too big")

        val mask = readFully(input, 4)
        val payload = readFully(input, len.toInt())
        for (i in payload.indices) payload[i] = (payload[i].toInt() xor mask[i and 3].toInt()).toByte()
        return Frame(fin, opcode, payload)
    }

    private fun readFully(input: InputStream, n: Int): ByteArray {
        val out = ByteArray(n)
        var got = 0
        while (got < n) {
            val r = input.read(out, got, n - got)
            if (r < 0) throw EOFException("stream ended inside a frame")
            got += r
        }
        return out
    }
}
