package com.ironmonone.app.stream.twitch

import com.ironmonone.app.stream.WebSocket
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI
import java.security.SecureRandom
import java.util.Base64
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

/**
 * The client side of RFC 6455, for EventSub: WebSocket.kt is the server side (the stream kit's), and this is its mirror.
 * Frames from the server are never masked, frames to it always are (RFC 6455 section 5.1). It answers pings, joins
 * fragments, and reads close frames; it sends only pongs and closes, because EventSub refuses anything else from the
 * client (close code 4001). wss:// is TLS through the platform's own SSLSocketFactory, with the host name checked
 * against the certificate; ws:// is for the tests' local fake server.
 */
class WsClient private constructor(private val socket: Socket, private val input: InputStream, private val out: OutputStream) : Closeable {

    sealed class Message {
        class Text(val text: String) : Message()
        class Close(val code: Int, val reason: String) : Message()
    }

    /** Reads may block this long before SocketTimeoutException (EventSub's keepalive window, plus slack). */
    fun timeout(ms: Int) { socket.soTimeout = ms }

    /**
     * The next text message, or a close. Pings are answered and skipped, as are pongs and binary messages. Returns a
     * Close with code 1006 when the connection ends without one.
     */
    fun read(): Message {
        val parts = ByteArrayOutputStream()
        var opcode = -1
        while (true) {
            val f = readFrame() ?: return Message.Close(1006, "")
            when (f.opcode) {
                WebSocket.OP_PING -> { send(WebSocket.OP_PONG, f.payload); continue }
                WebSocket.OP_PONG -> continue
                WebSocket.OP_CLOSE -> {
                    val code = if (f.payload.size >= 2) ((f.payload[0].toInt() and 0xFF) shl 8) or (f.payload[1].toInt() and 0xFF) else 1005
                    val reason = if (f.payload.size > 2) String(f.payload, 2, f.payload.size - 2, Charsets.UTF_8) else ""
                    runCatching { send(WebSocket.OP_CLOSE, f.payload.copyOf(minOf(2, f.payload.size))) }
                    return Message.Close(code, reason)
                }
                WebSocket.OP_CONTINUATION -> if (opcode < 0) throw WebSocket.ProtocolError(WebSocket.CLOSE_PROTOCOL_ERROR, "continuation with nothing to continue")
                else -> {
                    if (opcode >= 0) throw WebSocket.ProtocolError(WebSocket.CLOSE_PROTOCOL_ERROR, "new message inside a fragmented one")
                    opcode = f.opcode
                }
            }
            if (parts.size() + f.payload.size > MAX_MESSAGE) throw WebSocket.ProtocolError(WebSocket.CLOSE_TOO_BIG, "message too big")
            parts.write(f.payload)
            if (f.fin) {
                if (opcode == WebSocket.OP_TEXT) return Message.Text(parts.toString("UTF-8"))
                parts.reset(); opcode = -1  // a binary message: EventSub sends none, and it is not ours to read
            }
        }
    }

    fun close(code: Int = WebSocket.CLOSE_NORMAL) {
        runCatching { send(WebSocket.OP_CLOSE, byteArrayOf((code ushr 8).toByte(), code.toByte())) }
        runCatching { socket.close() }
    }

    override fun close() = close(WebSocket.CLOSE_NORMAL)

    @Synchronized
    private fun send(opcode: Int, payload: ByteArray) {
        out.write(masked(opcode, payload, ByteArray(4).also { random.nextBytes(it) }))
        out.flush()
    }

    /** A server frame: unmasked, the shortest length form, control frames whole and short. */
    private fun readFrame(): WebSocket.Frame? {
        val b0 = input.read()
        if (b0 < 0) return null
        val b1 = input.read()
        if (b1 < 0) throw EOFException("stream ended inside a frame header")
        if ((b0 and 0x70) != 0) throw WebSocket.ProtocolError(WebSocket.CLOSE_PROTOCOL_ERROR, "reserved bits set")
        if ((b1 and 0x80) != 0) throw WebSocket.ProtocolError(WebSocket.CLOSE_PROTOCOL_ERROR, "server frames are never masked")
        val fin = (b0 and 0x80) != 0
        val opcode = b0 and 0x0F
        var len = (b1 and 0x7F).toLong()
        if (len == 126L) len = readN(2).fold(0L) { a, b -> (a shl 8) or (b.toLong() and 0xFF) }
        else if (len == 127L) len = readN(8).fold(0L) { a, b -> (a shl 8) or (b.toLong() and 0xFF) }
        if (len < 0 || len > MAX_MESSAGE) throw WebSocket.ProtocolError(WebSocket.CLOSE_TOO_BIG, "frame too big")
        if (opcode >= WebSocket.OP_CLOSE && (!fin || len > 125)) throw WebSocket.ProtocolError(WebSocket.CLOSE_PROTOCOL_ERROR, "bad control frame")
        return WebSocket.Frame(fin, opcode, readN(len.toInt()))
    }

    private fun readN(n: Int): ByteArray {
        val b = ByteArray(n)
        var got = 0
        while (got < n) {
            val r = input.read(b, got, n - got)
            if (r < 0) throw EOFException("stream ended inside a frame")
            got += r
        }
        return b
    }

    companion object {
        /** EventSub's messages are a few KB; a chat message notification is the biggest. */
        const val MAX_MESSAGE = 1 shl 20
        private const val MAX_HEADERS = 16 * 1024
        private val random = SecureRandom()

        /** A client frame: FIN, the opcode, the mask bit, the length, the mask, the masked payload. */
        internal fun masked(opcode: Int, payload: ByteArray, mask: ByteArray): ByteArray {
            val len = payload.size
            val head = when { len < 126 -> 2; len <= 0xFFFF -> 4; else -> 10 }
            val out = ByteArray(head + 4 + len)
            out[0] = (0x80 or (opcode and 0x0F)).toByte()
            when (head) {
                2 -> out[1] = (0x80 or len).toByte()
                4 -> { out[1] = (0x80 or 126).toByte(); out[2] = (len ushr 8).toByte(); out[3] = len.toByte() }
                else -> { out[1] = (0x80 or 127).toByte(); for (i in 0 until 8) out[2 + i] = ((len.toLong() ushr (56 - 8 * i)) and 0xFF).toByte() }
            }
            System.arraycopy(mask, 0, out, head, 4)
            for (i in 0 until len) out[head + 4 + i] = (payload[i].toInt() xor mask[i and 3].toInt()).toByte()
            return out
        }

        /**
         * Opens [url] (wss:// or ws://) and completes the opening handshake, or throws IOException. The 101's
         * Sec-WebSocket-Accept must be the one for our key, or it is not a WebSocket server answering.
         */
        fun connect(url: String, timeoutMs: Int = 15_000, tls: SSLSocketFactory? = null): WsClient {
            val u = URI(url)
            val secure = when (u.scheme?.lowercase()) { "wss" -> true; "ws" -> false; else -> throw IOException("not a WebSocket address") }
            val host = u.host ?: throw IOException("no host")
            val port = if (u.port > 0) u.port else if (secure) 443 else 80
            val raw = Socket()
            try {
                raw.connect(InetSocketAddress(host, port), timeoutMs)
                raw.soTimeout = timeoutMs
                raw.tcpNoDelay = true
                val socket: Socket = if (!secure) raw else {
                    val s = (tls ?: SSLSocketFactory.getDefault() as SSLSocketFactory).createSocket(raw, host, port, true) as SSLSocket
                    runCatching { s.sslParameters = s.sslParameters.apply { endpointIdentificationAlgorithm = "HTTPS" } }
                    s.startHandshake()
                    // Checked again by hand: not every platform honours the endpoint check on a layered socket.
                    if (!HttpsURLConnection.getDefaultHostnameVerifier().verify(host, s.session)) {
                        s.close(); throw IOException("certificate does not match $host")
                    }
                    s
                }
                val input = BufferedInputStream(socket.getInputStream())
                val out = socket.getOutputStream()
                val key = Base64.getEncoder().encodeToString(ByteArray(16).also { random.nextBytes(it) })
                val path = (u.rawPath?.ifEmpty { "/" } ?: "/") + (u.rawQuery?.let { "?$it" } ?: "")
                val hostHeader = if (u.port > 0) "$host:$port" else host
                out.write(("GET $path HTTP/1.1\r\nHost: $hostHeader\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n" +
                    "Sec-WebSocket-Key: $key\r\nSec-WebSocket-Version: 13\r\nUser-Agent: KaizoCore\r\n\r\n").toByteArray(Charsets.ISO_8859_1))
                out.flush()
                val head = readHead(input)
                val status = head.firstOrNull()?.split(' ')?.getOrNull(1)?.toIntOrNull()
                if (status != 101) throw IOException("WebSocket handshake refused ($status)")
                val accept = head.drop(1).firstOrNull { it.lowercase().startsWith("sec-websocket-accept:") }?.substringAfter(':')?.trim()
                if (accept != WebSocket.acceptKey(key)) throw IOException("WebSocket handshake answered with the wrong key")
                return WsClient(socket, input, out)
            } catch (e: IOException) {
                runCatching { raw.close() }
                throw e
            } catch (e: RuntimeException) {
                runCatching { raw.close() }
                throw IOException("WebSocket connect failed", e)
            }
        }

        /** The status line and headers, up to the blank line, read a byte at a time so no frame byte is swallowed. */
        private fun readHead(input: InputStream): List<String> {
            val lines = ArrayList<String>()
            val line = StringBuilder()
            var total = 0
            while (true) {
                val c = input.read()
                if (c < 0) throw EOFException("connection closed during the handshake")
                if (++total > MAX_HEADERS) throw IOException("handshake headers too long")
                if (c == '\n'.code) {
                    val l = line.toString().trimEnd('\r')
                    if (l.isEmpty()) return lines
                    lines += l; line.setLength(0)
                } else line.append(c.toChar())
            }
        }
    }
}
