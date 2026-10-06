package com.ironmonone.app.stream

import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * The phone as a client of OBS's own WebSocket server (obs-websocket 5, built into OBS 28 and later; Tools, WebSocket
 * Server Settings), so OBS can switch scenes and save its replay buffer by itself (Blake's streamer list, item 2).
 *
 * The protocol, from obs-websocket's docs/generated/protocol.md, version 5:
 *  - The opening handshake asks for the subprotocol "obswebsocket.json" (JSON text frames).
 *  - The server says Hello (op 0). When it has a password, Hello carries "authentication": a challenge and a salt.
 *  - The client says Identify (op 1) with rpcVersion 1, the answer to the challenge when asked, and eventSubscriptions 0:
 *    this app listens to nothing, so OBS sends it nothing it did not ask for.
 *    The answer is base64(sha256(base64(sha256(password + salt)) + challenge)).
 *  - The server says Identified (op 2), or closes with 4009 for a wrong answer.
 *  - A Request (op 6) is answered by a RequestResponse (op 7) with the same requestId and a requestStatus.
 *
 * Hand-written, like the rest of this package: a client is a socket, a masked frame writer and an unmasked frame
 * reader on top of what WebSocket.kt already does for the server side. Everything here blocks, so it only ever runs
 * on ObsLink's own thread or a Test button's IO coroutine, never on the game's or the UI's.
 */
object ObsProtocol {
    const val DEFAULT_PORT = 4455
    const val SUBPROTOCOL = "obswebsocket.json"
    const val RPC_VERSION = 1

    const val OP_HELLO = 0
    const val OP_IDENTIFY = 1
    const val OP_IDENTIFIED = 2
    const val OP_REQUEST = 6
    const val OP_RESPONSE = 7

    /** obs-websocket's WebSocketCloseCode::AuthenticationFailed. */
    const val CLOSE_AUTH_FAILED = 4009

    /** obs-websocket's RequestStatus::OutputNotRunning: SaveReplayBuffer while the replay buffer is off. */
    const val STATUS_OUTPUT_NOT_RUNNING = 501

    /** The answer to a Hello's challenge. */
    fun auth(password: String, salt: String, challenge: String): String {
        val secret = b64(sha256(password + salt))
        return b64(sha256(secret + challenge))
    }

    fun identify(authentication: String?): String = Json.write(linkedMapOf(
        "op" to OP_IDENTIFY,
        "d" to linkedMapOf<String, Any?>("rpcVersion" to RPC_VERSION).apply {
            if (authentication != null) put("authentication", authentication)
            put("eventSubscriptions", 0)
        },
    ))

    fun request(type: String, id: String, data: Map<String, Any?>? = null): String = Json.write(linkedMapOf(
        "op" to OP_REQUEST,
        "d" to linkedMapOf<String, Any?>("requestType" to type, "requestId" to id).apply { if (data != null) put("requestData", data) },
    ))

    private fun sha256(s: String) = MessageDigest.getInstance("SHA-256").digest(s.toByteArray(Charsets.UTF_8))
    private fun b64(b: ByteArray) = Base64.getEncoder().encodeToString(b)
}

/** Why OBS could not be reached or used, in words for the settings screen. */
class ObsError(val kind: Kind, message: String) : IOException(message) {
    enum class Kind {
        /** Nothing answered: OBS is closed, its server is off, the address is wrong or the Wi-Fi is down. Worth retrying. */
        UNREACHABLE,
        /** Something answered that is not obs-websocket 5. Worth retrying: OBS may be starting. */
        NOT_OBS,
        /** OBS asks for a password and none is set. Not retried until the settings change. */
        PASSWORD_NEEDED,
        /** OBS turned the password down. Not retried until the settings change. */
        WRONG_PASSWORD,
        /** The connection dropped. Worth retrying. */
        CLOSED,
    }

    /** Retrying cannot help: the player has to change the settings. */
    val final: Boolean get() = kind == Kind.PASSWORD_NEEDED || kind == Kind.WRONG_PASSWORD
}

/** OBS answered a request with a failure (a scene that is gone, a replay buffer that is off). The connection is fine. */
class ObsRefused(val type: String, val code: Int, val comment: String) : Exception("$type: $code $comment")

/**
 * One connection to OBS, made whole by [open] (handshake, Hello, Identify) or not at all. [call] sends one request and
 * waits for its answer. Not thread-safe: one thread uses it, and [close] may come from any thread to end a wait.
 */
class ObsSocket private constructor(
    private val sock: Socket,
    private val input: InputStream,
    private val out: OutputStream,
    /** The version Hello named, like "5.5.2". */
    val obsWebSocketVersion: String,
) : Closeable {
    private var nextId = 1

    /**
     * Sends [type] with [data] and returns its responseData (an empty map when there is none). Throws [ObsRefused]
     * when OBS answers with a failure, and [ObsError] when the connection fails.
     */
    fun call(type: String, data: Map<String, Any?>? = null): Map<String, Any?> {
        val id = "kc" + nextId++
        sendText(ObsProtocol.request(type, id, data))
        while (true) {
            val msg = readMessage()
            if (msg.op != ObsProtocol.OP_RESPONSE) continue
            if (msg.d["requestId"] != id) continue
            val status = msg.d["requestStatus"] as? Map<*, *>
            if (status?.get("result") != true) {
                throw ObsRefused(type, (status?.get("code") as? Number)?.toInt() ?: 0, status?.get("comment") as? String ?: "")
            }
            @Suppress("UNCHECKED_CAST")
            return (msg.d["responseData"] as? Map<String, Any?>) ?: emptyMap()
        }
    }

    override fun close() {
        runCatching { out.write(clientFrame(OP_CLOSE, byteArrayOf(0x03, 0xE8.toByte()))); out.flush() }
        runCatching { sock.close() }
    }

    private class Message(val op: Int, val d: Map<String, Any?>)

    private fun sendText(text: String) = write(clientFrame(OP_TEXT, text.toByteArray(Charsets.UTF_8)))

    private fun write(frame: ByteArray) {
        try { out.write(frame); out.flush() } catch (e: IOException) { throw ObsError(ObsError.Kind.CLOSED, "The connection to OBS dropped.") }
    }

    /** The next JSON message from OBS. Pings are answered on the way; a close or a broken stream throws [ObsError]. */
    private fun readMessage(): Message {
        while (true) {
            val (op, payload) = try { readServerFrame(input) } catch (e: SocketTimeoutException) {
                throw ObsError(ObsError.Kind.CLOSED, "OBS stopped answering.")
            } catch (e: IOException) {
                if (e is ObsError) throw e
                throw ObsError(ObsError.Kind.CLOSED, "The connection to OBS dropped.")
            }
            when (op) {
                OP_PING -> write(clientFrame(OP_PONG, payload))
                OP_CLOSE -> throw closed(payload)
                OP_TEXT -> {
                    val doc = runCatching { JsonIn.parse(String(payload, Charsets.UTF_8)) }.getOrNull() as? Map<*, *> ?: continue
                    val o = (doc["op"] as? Number)?.toInt() ?: continue
                    @Suppress("UNCHECKED_CAST")
                    return Message(o, (doc["d"] as? Map<String, Any?>) ?: emptyMap())
                }
            }
        }
    }

    companion object {
        private const val OP_CONT = 0x0
        private const val OP_TEXT = 0x1
        private const val OP_BIN = 0x2
        private const val OP_CLOSE = 0x8
        private const val OP_PING = 0x9
        private const val OP_PONG = 0xA

        /** A scene list of hundreds of names is a few tens of KB; anything past this is not OBS talking. */
        private const val MAX_MESSAGE = 4 shl 20
        private val random = SecureRandom()

        /**
         * Connects to [host]:[port] and identifies, with [password] when OBS asks for one. [connectMs] bounds the TCP
         * connect, [ioMs] every read after it. Throws [ObsError] for every way it can fail; never anything else that
         * is an IOException.
         */
        /**
         * Nothing answered at [host]:[port]. At 127.0.0.1 (the USB cable, 2026-10-06) the phone is asking itself, which
         * reaches OBS on the PC only once the PC has run `adb reverse` for that port, and again after every replug: the
         * line says so, with the port filled in.
         */
        internal fun unreachable(host: String, port: Int): String {
            val h = host.trim().lowercase()
            val cable = h == "localhost" || h == "::1" || h == "[::1]" || Regex("127(\\.[0-9]{1,3}){3}").matches(h)
            return "OBS did not answer at $host:$port." +
                if (cable) " On a USB cable, run adb reverse tcp:$port tcp:$port on the PC first, and again after you plug the phone back in." else ""
        }

        fun open(host: String, port: Int, password: String, connectMs: Int = 3000, ioMs: Int = 5000): ObsSocket {
            val sock = Socket()
            try {
                try {
                    sock.connect(InetSocketAddress(host, port), connectMs)
                } catch (e: IOException) {
                    throw ObsError(ObsError.Kind.UNREACHABLE, unreachable(host, port))
                } catch (e: IllegalArgumentException) {
                    throw ObsError(ObsError.Kind.UNREACHABLE, "That port is not a port.")
                }
                sock.soTimeout = ioMs
                sock.tcpNoDelay = true
                val input = BufferedInputStream(sock.getInputStream())
                val out = sock.getOutputStream()
                handshake(host, port, input, out)
                val s = identify(sock, input, out, password)
                return s
            } catch (e: ObsError) {
                runCatching { sock.close() }
                throw e
            } catch (e: IOException) {
                runCatching { sock.close() }
                throw ObsError(ObsError.Kind.CLOSED, "The connection to OBS dropped.")
            }
        }

        private fun notObs() = ObsError(ObsError.Kind.NOT_OBS,
            "Something answered at that address, but not OBS's WebSocket server. Check the port in OBS, Tools, WebSocket Server Settings.")

        private fun handshake(host: String, port: Int, input: InputStream, out: OutputStream) {
            val key = Base64.getEncoder().encodeToString(ByteArray(16).also { random.nextBytes(it) })
            val hostHeader = (if (host.contains(':')) "[$host]" else host) + ":$port"
            out.write(("GET / HTTP/1.1\r\nHost: $hostHeader\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n" +
                "Sec-WebSocket-Key: $key\r\nSec-WebSocket-Version: 13\r\nSec-WebSocket-Protocol: ${ObsProtocol.SUBPROTOCOL}\r\n\r\n")
                .toByteArray(Charsets.ISO_8859_1))
            out.flush()
            val status = try { line(input) } catch (e: SocketTimeoutException) { throw notObs() } ?: throw notObs()
            val headers = HashMap<String, String>()
            var count = 0
            while (true) {
                val h = line(input) ?: throw notObs()
                if (h.isEmpty()) break
                if (++count > 100) throw notObs()
                val c = h.indexOf(':')
                if (c > 0) headers[h.substring(0, c).trim().lowercase()] = h.substring(c + 1).trim()
            }
            if (status.split(' ').getOrNull(1) != "101") throw notObs()
            if (headers["sec-websocket-accept"] != WebSocket.acceptKey(key)) throw notObs()
        }

        private fun identify(sock: Socket, input: InputStream, out: OutputStream, password: String): ObsSocket {
            val hello = readJson(input, out) ?: throw notObs()
            if ((hello["op"] as? Number)?.toInt() != ObsProtocol.OP_HELLO) throw notObs()
            val d = hello["d"] as? Map<*, *> ?: throw notObs()
            val version = d["obsWebSocketVersion"] as? String ?: ""
            val challenge = d["authentication"] as? Map<*, *>
            val answer = if (challenge != null) {
                if (password.isEmpty()) throw ObsError(ObsError.Kind.PASSWORD_NEEDED, "OBS asks for a password. Enter the one in OBS, Tools, WebSocket Server Settings.")
                ObsProtocol.auth(password, challenge["salt"] as? String ?: "", challenge["challenge"] as? String ?: "")
            } else null
            out.write(clientFrame(OP_TEXT, ObsProtocol.identify(answer).toByteArray(Charsets.UTF_8)))
            out.flush()
            val reply = readJson(input, out) ?: throw notObs()
            if ((reply["op"] as? Number)?.toInt() != ObsProtocol.OP_IDENTIFIED) throw notObs()
            return ObsSocket(sock, input, out, version)
        }

        /** One JSON message during the opening, answering pings. Null for a message that is not a JSON object. */
        private fun readJson(input: InputStream, out: OutputStream): Map<*, *>? {
            while (true) {
                val (op, payload) = try { readServerFrame(input) } catch (e: SocketTimeoutException) { throw notObs() }
                when (op) {
                    OP_PING -> { out.write(clientFrame(OP_PONG, payload)); out.flush() }
                    OP_CLOSE -> throw closed(payload)
                    OP_TEXT -> return runCatching { JsonIn.parse(String(payload, Charsets.UTF_8)) }.getOrNull() as? Map<*, *>
                }
            }
        }

        /** A close from OBS as the error it means: 4009 is a wrong password, anything else a drop. */
        private fun closed(payload: ByteArray): ObsError {
            val code = if (payload.size >= 2) ((payload[0].toInt() and 0xFF) shl 8) or (payload[1].toInt() and 0xFF) else 0
            return if (code == ObsProtocol.CLOSE_AUTH_FAILED) ObsError(ObsError.Kind.WRONG_PASSWORD, "OBS turned the password down.")
            else ObsError(ObsError.Kind.CLOSED, "OBS closed the connection.")
        }

        /** A client frame: always masked (RFC 6455 section 5.3), the shortest length form. */
        internal fun clientFrame(opcode: Int, payload: ByteArray): ByteArray {
            val len = payload.size
            val b = ByteArrayOutputStream(len + 14)
            b.write(0x80 or (opcode and 0x0F))
            when {
                len < 126 -> b.write(0x80 or len)
                len <= 0xFFFF -> { b.write(0x80 or 126); b.write(len ushr 8); b.write(len and 0xFF) }
                else -> { b.write(0x80 or 127); for (i in 7 downTo 0) b.write(((len.toLong() ushr (8 * i)) and 0xFF).toInt()) }
            }
            val mask = ByteArray(4).also { random.nextBytes(it) }
            b.write(mask)
            for (i in 0 until len) b.write(payload[i].toInt() xor mask[i and 3].toInt())
            return b.toByteArray()
        }

        /**
         * One whole message from the server (fragments joined; control frames come back on their own). A server frame
         * must not be masked; one that is, or one too big, is not OBS.
         */
        private fun readServerFrame(input: InputStream): Pair<Int, ByteArray> {
            var opcode = -1
            val body = ByteArrayOutputStream()
            while (true) {
                val b0 = input.read()
                if (b0 < 0) throw EOFException()
                val b1 = input.read()
                if (b1 < 0) throw EOFException()
                if ((b1 and 0x80) != 0) throw ObsError(ObsError.Kind.NOT_OBS, "OBS sent a masked frame.")
                val op = b0 and 0x0F
                var len = (b1 and 0x7F).toLong()
                if (len == 126L) len = ((read1(input) shl 8) or read1(input)).toLong()
                else if (len == 127L) { len = 0; repeat(8) { len = (len shl 8) or read1(input).toLong() } }
                if (len < 0 || len > MAX_MESSAGE || body.size() + len > MAX_MESSAGE) throw ObsError(ObsError.Kind.NOT_OBS, "OBS sent too much at once.")
                val p = readFully(input, len.toInt())
                val fin = (b0 and 0x80) != 0
                if (op >= OP_CLOSE) return op to p
                if (op != OP_CONT) opcode = op
                body.write(p)
                if (fin) return (if (opcode == OP_BIN) OP_BIN else opcode) to body.toByteArray()
            }
        }

        private fun read1(input: InputStream): Int { val b = input.read(); if (b < 0) throw EOFException(); return b }

        private fun readFully(input: InputStream, n: Int): ByteArray {
            val p = ByteArray(n)
            var got = 0
            while (got < n) {
                val r = input.read(p, got, n - got)
                if (r < 0) throw EOFException()
                got += r
            }
            return p
        }

        private fun line(input: InputStream): String? {
            val sb = StringBuilder()
            while (true) {
                val b = input.read()
                if (b < 0) return if (sb.isEmpty()) null else sb.toString()
                if (b == '\n'.code) return sb.toString().trimEnd('\r')
                if (sb.length > 8192) return null
                sb.append(b.toChar())
            }
        }
    }
}

/**
 * A small JSON reader for what OBS sends: objects (order kept), arrays, strings, numbers (Long or Double), true, false
 * and null. Nesting is bounded, so a hostile answer cannot recurse the thread off its stack. org.json is stubbed out
 * on the JVM test classpath, which is why the app has its own.
 */
internal object JsonIn {
    private const val MAX_DEPTH = 32

    fun parse(text: String): Any? {
        val p = P(text)
        val v = p.value(0)
        p.ws()
        require(p.i == text.length) { "text after the value" }
        return v
    }

    private class P(val s: String) {
        var i = 0
        fun ws() { while (i < s.length && s[i].let { it == ' ' || it == '\t' || it == '\n' || it == '\r' }) i++ }
        private fun at(): Char { require(i < s.length) { "unexpected end" }; return s[i] }

        fun value(depth: Int): Any? {
            require(depth <= MAX_DEPTH) { "too deep" }
            ws()
            return when (val c = at()) {
                '{' -> obj(depth)
                '[' -> arr(depth)
                '"' -> str()
                't' -> lit("true", true)
                'f' -> lit("false", false)
                'n' -> lit("null", null)
                else -> if (c == '-' || c in '0'..'9') num() else throw IllegalArgumentException("unexpected $c")
            }
        }

        private fun lit(w: String, v: Any?): Any? { require(s.startsWith(w, i)); i += w.length; return v }

        private fun num(): Any {
            val start = i
            if (at() == '-') i++
            var real = false
            while (i < s.length && (s[i] in '0'..'9' || s[i] in ".eE+-")) { if (s[i] in ".eE") real = true; i++ }
            val t = s.substring(start, i)
            return if (real) t.toDouble() else t.toLongOrNull() ?: t.toDouble()
        }

        private fun obj(depth: Int): Map<String, Any?> {
            val m = LinkedHashMap<String, Any?>()
            i++; ws()
            if (at() == '}') { i++; return m }
            while (true) {
                ws()
                val k = str()
                ws(); require(at() == ':'); i++
                m[k] = value(depth + 1)
                ws()
                when (at()) { ',' -> i++; '}' -> { i++; return m }; else -> throw IllegalArgumentException("bad object") }
            }
        }

        private fun arr(depth: Int): List<Any?> {
            val l = ArrayList<Any?>()
            i++; ws()
            if (at() == ']') { i++; return l }
            while (true) {
                l += value(depth + 1)
                ws()
                when (at()) { ',' -> i++; ']' -> { i++; return l }; else -> throw IllegalArgumentException("bad array") }
            }
        }

        private fun str(): String {
            require(at() == '"')
            i++
            val sb = StringBuilder()
            while (true) {
                val c = at(); i++
                when (c) {
                    '"' -> return sb.toString()
                    '\\' -> when (val e = at().also { i++ }) {
                        'n' -> sb.append('\n'); 't' -> sb.append('\t'); 'r' -> sb.append('\r')
                        'b' -> sb.append('\b'); 'f' -> sb.append('\u000C')
                        'u' -> { require(i + 4 <= s.length); sb.append(s.substring(i, i + 4).toInt(16).toChar()); i += 4 }
                        else -> sb.append(e)
                    }
                    else -> sb.append(c)
                }
            }
        }
    }
}
