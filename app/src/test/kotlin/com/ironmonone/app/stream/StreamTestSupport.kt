package com.ironmonone.app.stream

import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.io.EOFException
import java.io.InputStream
import java.net.Socket
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/** A [GameFeed] a test drives by hand: it records the capture switch and serves what it is given. */
class FakeGameFeed : GameFeed {
    val captureLog = CopyOnWriteArrayList<Boolean>()
    @Volatile var capturing = false
        private set

    private class Pic(val counter: Long, val w: Int, val h: Int, val rgb: ByteArray)

    @Volatile private var latest: Pic? = null
    private var counter = 0L
    private val sound = ConcurrentLinkedQueue<Pair<ByteArray, Int>>()

    override fun setCapture(on: Boolean) {
        capturing = on
        captureLog += on
    }

    @Synchronized fun push(w: Int, h: Int, rgb: ByteArray) {
        require(rgb.size == w * h * 3)
        latest = Pic(++counter, w, h, rgb.copyOf())
    }

    fun pushSound(pcm: ByteArray, rate: Int) { sound.add(pcm.copyOf() to rate) }

    override fun frame(afterCounter: Long, into: RawFrame): Boolean {
        val f = latest ?: return false
        if (f.counter <= afterCounter) return false
        System.arraycopy(f.rgb, 0, into.prepare(f.w, f.h), 0, f.rgb.size)
        into.counter = f.counter
        return true
    }

    override fun audio(into: ByteArray, rate: IntArray): Int {
        val s = sound.poll() ?: return 0
        val n = minOf(s.first.size, into.size)
        System.arraycopy(s.first, 0, into, 0, n)
        rate[0] = s.second
        return n
    }
}

/** A viewer that just keeps what it is sent. */
internal class RecordingViewer : GameStream.Viewer {
    val video = LinkedBlockingQueue<ByteArray>()
    val audio = LinkedBlockingQueue<ByteArray>()
    override fun sendVideo(frame: ByteArray) { video.add(frame) }
    override fun sendAudio(frame: ByteArray) { audio.add(frame) }
}

/** Reads a frame FROM the server, so unmasked. Written apart from WebSocket.encode so it can catch its mistakes. */
class ServerFrame(val fin: Boolean, val opcode: Int, val payload: ByteArray) {
    companion object {
        fun parse(bytes: ByteArray): ServerFrame = read(ByteArrayInputStream(bytes))!!.also {
            // nothing may follow the frame
        }

        fun read(input: InputStream): ServerFrame? {
            val b0 = input.read()
            if (b0 < 0) return null
            val b1 = input.read()
            if (b1 < 0) throw EOFException()
            check(b1 and 0x80 == 0) { "a server frame must not be masked" }
            var len = (b1 and 0x7F).toLong()
            if (len == 126L) len = (input.read().toLong() shl 8) or input.read().toLong()
            else if (len == 127L) { len = 0; repeat(8) { len = (len shl 8) or input.read().toLong() } }
            val p = ByteArray(len.toInt())
            var got = 0
            while (got < p.size) {
                val r = input.read(p, got, p.size - got)
                if (r < 0) throw EOFException()
                got += r
            }
            return ServerFrame(b0 and 0x80 != 0, b0 and 0x0F, p)
        }
    }
}

/** A WebSocket client over a raw socket: it says what a browser says, and masks what it sends. */
internal class WsTestClient(port: Int, path: String, extraHeaders: Map<String, String> = emptyMap(), key: String = KEY, version: String? = "13", upgrade: Boolean = true) : AutoCloseable {
    val sock = Socket("127.0.0.1", port).apply { soTimeout = 5000; tcpNoDelay = true }
    private val input = BufferedInputStream(sock.getInputStream())
    private val output = sock.getOutputStream()
    val status: String
    val headers = LinkedHashMap<String, String>()
    private var body = ""

    init {
        val sb = StringBuilder("GET $path HTTP/1.1\r\nHost: 127.0.0.1:$port\r\n")
        if (upgrade) sb.append("Upgrade: websocket\r\nConnection: keep-alive, Upgrade\r\nSec-WebSocket-Key: $key\r\n")
        if (version != null) sb.append("Sec-WebSocket-Version: $version\r\n")
        extraHeaders.forEach { (k, v) -> sb.append("$k: $v\r\n") }
        sb.append("\r\n")
        output.write(sb.toString().toByteArray(Charsets.ISO_8859_1)); output.flush()
        status = line()!!
        while (true) {
            val l = line()!!
            if (l.isEmpty()) break
            val c = l.indexOf(':')
            headers[l.substring(0, c).trim().lowercase()] = l.substring(c + 1).trim()
        }
        if (!status.contains(" 101 ")) {
            val n = headers["content-length"]?.toInt() ?: 0
            body = String(ByteArray(n).also { b -> var got = 0; while (got < n) { val r = input.read(b, got, n - got); if (r < 0) break; got += r } }, Charsets.UTF_8)
        }
    }

    val text: String get() = body

    private fun line(): String? {
        val sb = StringBuilder()
        while (true) {
            val b = input.read()
            if (b < 0) return if (sb.isEmpty()) null else sb.toString()
            if (b == '\n'.code) return sb.toString().trimEnd('\r')
            sb.append(b.toChar())
        }
    }

    /** The next frame from the server, or null when it closed the connection. */
    fun next(): ServerFrame? = try { ServerFrame.read(input) } catch (e: java.net.SocketException) { null }

    /** The next frame that is not a ping (the phone pings on a timer). */
    fun nextData(): ServerFrame? {
        while (true) {
            val f = next() ?: return null
            if (f.opcode != WebSocket.OP_PING) return f
        }
    }

    fun send(opcode: Int, payload: ByteArray = ByteArray(0), fin: Boolean = true, masked: Boolean = true) {
        val mask = byteArrayOf(0x37, 0xFA.toByte(), 0x21, 0x3D)
        val head = java.io.ByteArrayOutputStream()
        head.write((if (fin) 0x80 else 0) or opcode)
        val m = if (masked) 0x80 else 0
        when {
            payload.size < 126 -> head.write(m or payload.size)
            payload.size <= 0xFFFF -> { head.write(m or 126); head.write(payload.size ushr 8); head.write(payload.size and 0xFF) }
            else -> throw IllegalArgumentException("test frames are small")
        }
        if (masked) head.write(mask)
        val body = if (masked) ByteArray(payload.size) { (payload[it].toInt() xor mask[it and 3].toInt()).toByte() } else payload
        head.write(body)
        output.write(head.toByteArray()); output.flush()
    }

    fun sendRaw(bytes: ByteArray) { output.write(bytes); output.flush() }

    override fun close() { runCatching { sock.close() } }

    companion object { const val KEY = "dGhlIHNhbXBsZSBub25jZQ==" }
}

/** A small strict JSON reader of the tests' own: numbers with a point or exponent are Doubles, the rest Longs. */
internal class MiniJson(private val s: String) {
    private var i = 0

    fun parse(): Any? {
        val v = value()
        ws()
        check(i == s.length) { "text after the value, at $i" }
        return v
    }

    private fun ws() { while (i < s.length && s[i] in " \t\r\n") i++ }

    private fun value(): Any? {
        ws()
        return when (val c = s[i]) {
            '{' -> obj()
            '[' -> arr()
            '"' -> str()
            't' -> lit("true", true)
            'f' -> lit("false", false)
            'n' -> lit("null", null)
            else -> if (c == '-' || c.isDigit()) num() else error("unexpected '$c' at $i")
        }
    }

    private fun lit(word: String, v: Any?): Any? {
        check(s.startsWith(word, i)) { "bad literal at $i" }
        i += word.length
        return v
    }

    private fun num(): Any {
        val start = i
        if (s[i] == '-') i++
        while (i < s.length && s[i].isDigit()) i++
        var real = false
        if (i < s.length && s[i] == '.') { real = true; i++; while (i < s.length && s[i].isDigit()) i++ }
        if (i < s.length && (s[i] == 'e' || s[i] == 'E')) {
            real = true; i++
            if (s[i] == '+' || s[i] == '-') i++
            while (i < s.length && s[i].isDigit()) i++
        }
        val t = s.substring(start, i)
        return if (real) t.toDouble() else t.toLong()
    }

    private fun obj(): Map<String, Any?> {
        val m = LinkedHashMap<String, Any?>()
        i++
        ws()
        if (s[i] == '}') { i++; return m }
        while (true) {
            ws()
            check(s[i] == '"') { "a key was expected at $i" }
            val k = str()
            ws()
            check(s[i++] == ':') { "a colon was expected at ${i - 1}" }
            check(k !in m) { "duplicate key $k" }
            m[k] = value()
            ws()
            when (val c = s[i++]) {
                ',' -> continue
                '}' -> return m
                else -> error("bad object separator '$c' at ${i - 1}")
            }
        }
    }

    private fun arr(): List<Any?> {
        val l = ArrayList<Any?>()
        i++
        ws()
        if (s[i] == ']') { i++; return l }
        while (true) {
            l += value()
            ws()
            when (val c = s[i++]) {
                ',' -> continue
                ']' -> return l
                else -> error("bad array separator '$c' at ${i - 1}")
            }
        }
    }

    private fun str(): String {
        check(s[i] == '"')
        i++
        val sb = StringBuilder()
        while (true) {
            val c = s[i++]
            when {
                c == '"' -> return sb.toString()
                c == '\\' -> when (val e = s[i++]) {
                    '"', '\\', '/' -> sb.append(e)
                    'b' -> sb.append(8.toChar())
                    'f' -> sb.append(12.toChar())
                    'n' -> sb.append('\n')
                    'r' -> sb.append('\r')
                    't' -> sb.append('\t')
                    'u' -> { sb.append(s.substring(i, i + 4).toInt(16).toChar()); i += 4 }
                    else -> error("bad escape '$e' at ${i - 1}")
                }
                c.code < 0x20 -> error("a raw control character (${c.code}) inside a string at ${i - 1}")
                else -> sb.append(c)
            }
        }
    }
}

/** Pictures for the tests. */
internal object TestPics {
    /** A picture with structure in it: a diagonal ramp, a flat block, a repeated stripe and some noise. */
    fun structured(w: Int, h: Int, seed: Long = 7): ByteArray {
        val rnd = java.util.Random(seed)
        val out = ByteArray(w * h * 3)
        for (y in 0 until h) for (x in 0 until w) {
            val o = (y * w + x) * 3
            val (r, g, b) = when {
                y < h / 4 -> Triple(40, 80, 160)                                   // flat
                y < h / 2 -> Triple((x * 255 / maxOf(1, w - 1)), (y * 255 / maxOf(1, h - 1)), ((x + y) * 3) and 0xFF)   // ramps
                y < 3 * h / 4 -> Triple(if ((x / 3) % 2 == 0) 250 else 5, 128, 200) // stripes, the same on every row
                else -> Triple(rnd.nextInt(256), rnd.nextInt(256), rnd.nextInt(256)) // noise
            }
            out[o] = r.toByte(); out[o + 1] = g.toByte(); out[o + 2] = b.toByte()
        }
        return out
    }

    fun noise(w: Int, h: Int, seed: Long): ByteArray = ByteArray(w * h * 3).also { java.util.Random(seed).nextBytes(it) }

    /**
     * Decodes a PNG with the JDK's own decoder (javax.imageio), as an independent judge
     * of the encoder. The unit tests compile against android.jar, which has no
     * javax.imageio, so it is reached by reflection; on the JVM they run on it is there.
     */
    fun decode(png: ByteArray): Triple<Int, Int, ByteArray> {
        val read = Class.forName("javax.imageio.ImageIO").getMethod("read", java.io.InputStream::class.java)
        val img = read.invoke(null, ByteArrayInputStream(png)) ?: error("not a PNG the JDK can read")
        val cls = img.javaClass
        val w = cls.getMethod("getWidth").invoke(img) as Int
        val h = cls.getMethod("getHeight").invoke(img) as Int
        val int = Int::class.javaPrimitiveType
        val argb = IntArray(w * h)
        cls.getMethod("getRGB", int, int, int, int, IntArray::class.java, int, int).invoke(img, 0, 0, w, h, argb, 0, w)
        val out = ByteArray(w * h * 3)
        for (i in argb.indices) {
            val p = argb[i]
            out[i * 3] = (p shr 16).toByte(); out[i * 3 + 1] = (p shr 8).toByte(); out[i * 3 + 2] = p.toByte()
        }
        return Triple(w, h, out)
    }
}

/** Waits for [cond] up to [ms], for the things a thread does a moment after the test asks. */
internal fun waitFor(ms: Long = 5000, cond: () -> Boolean): Boolean {
    val end = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(ms)
    while (System.nanoTime() < end) {
        if (cond()) return true
        Thread.sleep(5)
    }
    return cond()
}
