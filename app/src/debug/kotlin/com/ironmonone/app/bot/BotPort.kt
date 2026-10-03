package com.ironmonone.app.bot

import android.util.Log
import android.view.KeyEvent
import com.ironmonone.app.SpriteMotion
import com.swordfish.libretrodroid.GLRetroView
import com.swordfish.libretrodroid.LibretroDroid
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.security.MessageDigest
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * The test bot's port: what tools/kcbot on the PC uses to play a game in the app. Debug builds only
 * (src/debug); a players' build has none of this.
 *
 * It reads and writes the game's memory, holds buttons for an exact number of the game's frames, waits frames,
 * and takes and loads the bot's own states. Everything that must happen between two frames runs on the
 * emulation thread, through GLRetroView's step hook; the HTTP threads only queue it and wait. One bot request
 * runs at a time.
 *
 * 127.0.0.1:8650 only (the PC runs `adb forward tcp:8650 tcp:8650`), and every request carries the token from
 * files/bot/token, as the header X-Bot-Token or ?k=.
 *
 *   GET  /ping                      {"ok":true,"frames":..,"steps":..,"speed":..,"idleMs":..}
 *   GET  /mem?a=<hex>&n=<len>       raw bytes, read between two frames (empty = unmapped)
 *   GET  /mems?r=<hex>:<len>,...    several ranges read in the same gap, one after another
 *   POST /mem?a=<hex>               write the body there, between two frames
 *   POST /press?k=A,B&f=<n>&r=<m>   hold the buttons for n frames, release, then wait m frames
 *   POST /wait?f=<n>                wait n frames
 *   POST /touch?x=<-1..1>&y=<-1..1>&f=<n>&r=<m>   touch the game view (normalized as GLRetroView does) for n frames
 *   GET  /state                     the bot's own state of the game (not one of the player's slots)
 *   POST /state                     load one, through GLRetroView so the in-game save guard still applies
 *   POST /stall?ms=<n>              hold the emulation thread for n ms (to test that the app never freezes on it)
 *   POST /frame?k=A,UP&r=<hex>:<len>,...&f=<n>   hold exactly these buttons, then read memory: 8 bytes of frame
 *                                   count, then the ranges as /mems. In lockstep it runs exactly n frames (default 1)
 *                                   with those buttons and reads after them, as an emulator's frame advance does;
 *                                   otherwise the buttons hold from the next frame on and memory is read as it stands.
 *   POST /lock?on=1|0               lockstep on or off: the bot runs every frame itself (see [locked])
 *   POST /reset                     the console's reset button (the in-game save stays)
 *
 * A frame here is one of the game's frames: a step at 8x speed is 8 of them.
 * 503 means the game is not running (no game open, or the app in the background).
 */
internal object BotPort {
    const val PORT = 8650
    private const val TAG = "KaizoBot"
    private const val MAX_BODY = 64 * 1024 * 1024

    private val started = AtomicBoolean(false)
    @Volatile private var token = ByteArray(0)

    private val steps = AtomicLong(0)
    private val frames = AtomicLong(0)
    @Volatile private var speed = 1
    @Volatile private var lastStepAt = 0L
    private val jobs = ConcurrentLinkedQueue<Job>()
    /** Buttons the bot holds down. Emulation thread only. */
    private val held = HashSet<Int>()
    /** One bot request at a time: two overlapping presses would fight over the buttons. */
    private val one = Any()

    private class Job(val work: (GLRetroView) -> Any?) {
        val done = CountDownLatch(1)
        val cancelled = AtomicBoolean(false)
        @Volatile var result: Any? = null
        @Volatile var error: Throwable? = null
    }

    private class NotRunning : Exception("The game is not running.")

    /**
     * Lockstep: the bot plays frame by frame. The render loop runs no frame of its own (GLRetroView.holdSteps) and
     * this hook runs each frame the bot asks for, at 1x per call, then reads memory, all between two draws. Many
     * frames fit between two draws, so a quick bot plays faster than real time. The bot going quiet for
     * [LOCK_IDLE_MS] hands the game back.
     */
    @Volatile private var locked = false
    @Volatile private var lastFrameCallAt = 0L
    private const val LOCK_IDLE_MS = 3_000L
    /** How long one draw may hold the emulation thread for the bot's frames: the screen then refreshes 20 times a second. */
    private const val DRAW_BUDGET_NS = 50_000_000L
    private val frameCalls = java.util.concurrent.LinkedBlockingQueue<FrameCall>()

    private class FrameCall(val keys: Set<Int>, val count: Int, val ranges: List<Pair<Long, Int>>) {
        val done = CountDownLatch(1)
        @Volatile var frames = 0L
        @Volatile var parts: List<ByteArray> = emptyList()
        @Volatile var error: Throwable? = null
    }

    private val hook = GLRetroView.StepHook { view ->
        steps.incrementAndGet()
        lastStepAt = System.currentTimeMillis()
        if (!locked) {
            val s = view.frameSpeed.coerceAtLeast(1)
            speed = s
            frames.addAndGet(s.toLong())
        }
        runJobs(view)
        if (locked) runFrameCalls(view)
    }

    private fun runJobs(view: GLRetroView) {
        while (true) {
            val job = jobs.poll() ?: break
            if (job.cancelled.get()) continue
            try {
                job.result = job.work(view)
            } catch (t: Throwable) {
                job.error = t
            }
            job.done.countDown()
        }
    }

    /** Lockstep: the bot's frames, one call at a time, until the draw's time is up or the bot pauses. */
    private fun runFrameCalls(view: GLRetroView) {
        val start = System.nanoTime()
        while (true) {
            // Wait for the bot's next call for the rest of this draw's time, so a call is answered the moment it
            // arrives instead of at the next draw.
            val left = DRAW_BUDGET_NS - (System.nanoTime() - start)
            if (left <= 0) break
            val call = frameCalls.poll(left, TimeUnit.NANOSECONDS) ?: break
            try {
                setHeld(call.keys)
                // Exactly the frames asked for, with none of the real-time pacing a player's step has.
                LibretroDroid.stepBot(call.count)
                frames.addAndGet(call.count.toLong())
                call.parts = call.ranges.map { (a, n) -> LibretroDroid.readMemory(a, n) }
                call.frames = frames.get()
            } catch (t: Throwable) {
                call.error = t
            }
            call.done.countDown()
            runJobs(view)
        }
        if (System.currentTimeMillis() - lastFrameCallAt > LOCK_IDLE_MS) unlock(view)
    }

    private fun lock(view: GLRetroView) {
        if (locked) return
        LibretroDroid.setFrameSpeed(1)
        LibretroDroid.setAudioEnabled(false)
        lastFrameCallAt = System.currentTimeMillis()
        locked = true
        GLRetroView.holdSteps = true
        Log.i(TAG, "lockstep on")
    }

    private fun unlock(view: GLRetroView) {
        if (!locked) return
        locked = false
        GLRetroView.holdSteps = false
        setHeld(emptySet())
        LibretroDroid.setFrameSpeed(view.frameSpeed.coerceAtLeast(1))
        LibretroDroid.setAudioEnabled(view.frameSpeed <= 1)
        // A call still waiting gets an answer instead of a timeout.
        while (true) {
            val call = frameCalls.poll() ?: break
            call.error = NotRunning()
            call.done.countDown()
        }
        Log.i(TAG, "lockstep off")
    }

    /**
     * One key, as a player's press reaches the app: the core, and the walking sprites' idle clock and facing
     * (SpriteMotion), which the pad and a controller feed too. The bot's keys went to the core alone, so in a run the
     * bot drove every sprite fell asleep 55 seconds in and never walked or turned (2026-10-03).
     */
    private fun key(action: Int, code: Int) {
        LibretroDroid.onKeyEvent(0, action, code)
        SpriteMotion.key(action, code)
    }

    /** Emulation thread only: from the next frame on, exactly [keys] are held. */
    private fun setHeld(keys: Set<Int>) {
        for (code in held.toList()) if (code !in keys) {
            key(KeyEvent.ACTION_UP, code)
            held.remove(code)
        }
        for (code in keys) if (held.add(code)) key(KeyEvent.ACTION_DOWN, code)
    }

    fun start(token: String) {
        if (token.isEmpty() || !started.compareAndSet(false, true)) return
        this.token = token.toByteArray(Charsets.UTF_8)
        GLRetroView.stepHooks.add(hook)
        val server = ServerSocket()
        server.reuseAddress = true
        // Loopback on a phone (the PC comes in through adb forward). On the emulator also its own virtual network,
        // which nothing outside this PC can reach, so the PC can use the emulator's port redirect: a round trip
        // there is a fraction of adb forward's.
        server.bind(InetSocketAddress(InetAddress.getByName(if (isEmulator()) "0.0.0.0" else "127.0.0.1"), PORT))
        Thread({
            while (true) {
                val c = runCatching { server.accept() }.getOrNull() ?: break
                Thread({ runCatching { serve(c) }; runCatching { c.close() } }, "bot-conn").apply { isDaemon = true }.start()
            }
        }, "bot-accept").apply { isDaemon = true }.start()
        Log.i(TAG, "Bot port listening on 127.0.0.1:$PORT")
    }

    private fun isEmulator(): Boolean =
        android.os.Build.HARDWARE == "ranchu" || android.os.Build.HARDWARE == "goldfish" ||
            android.os.Build.PRODUCT.startsWith("sdk_gphone")

    /** Runs [work] on the emulation thread between two frames. Throws [NotRunning] if no frame comes in time. */
    private fun <T> onStep(timeoutMs: Long = 5_000, work: (GLRetroView) -> T): T {
        val job = Job(work)
        jobs.add(job)
        if (!job.done.await(timeoutMs, TimeUnit.MILLISECONDS)) {
            job.cancelled.set(true)
            // A job the emulation thread already took still finishes; wait a moment so its buttons are known.
            job.done.await(200, TimeUnit.MILLISECONDS)
            if (job.done.count > 0) throw NotRunning()
        }
        job.error?.let { throw it }
        @Suppress("UNCHECKED_CAST")
        return job.result as T
    }

    /** Waits until the game has run to [target] frames; a stall of more than 5 seconds is [NotRunning]. */
    private fun waitUntil(target: Long) {
        var seen = frames.get()
        var lastProgress = System.currentTimeMillis()
        while (seen < target) {
            Thread.sleep(2)
            val now = frames.get()
            if (now != seen) {
                seen = now
                lastProgress = System.currentTimeMillis()
            } else if (System.currentTimeMillis() - lastProgress > 5_000) {
                throw NotRunning()
            }
        }
    }

    private val KEYS = mapOf(
        "A" to KeyEvent.KEYCODE_BUTTON_A, "B" to KeyEvent.KEYCODE_BUTTON_B,
        "X" to KeyEvent.KEYCODE_BUTTON_X, "Y" to KeyEvent.KEYCODE_BUTTON_Y,
        "L" to KeyEvent.KEYCODE_BUTTON_L1, "R" to KeyEvent.KEYCODE_BUTTON_R1,
        "START" to KeyEvent.KEYCODE_BUTTON_START, "SELECT" to KeyEvent.KEYCODE_BUTTON_SELECT,
        "UP" to KeyEvent.KEYCODE_DPAD_UP, "DOWN" to KeyEvent.KEYCODE_DPAD_DOWN,
        "LEFT" to KeyEvent.KEYCODE_DPAD_LEFT, "RIGHT" to KeyEvent.KEYCODE_DPAD_RIGHT,
    )

    private fun releaseAll() {
        runCatching {
            onStep {
                for (code in held) key(KeyEvent.ACTION_UP, code)
                held.clear()
            }
        }
    }

    private fun press(keys: List<Int>, hold: Long, after: Long): Long {
        try {
            val down = onStep {
                for (code in keys) {
                    key(KeyEvent.ACTION_DOWN, code)
                    held.add(code)
                }
                frames.get()
            }
            waitUntil(down + hold)
            val up = onStep {
                for (code in keys) {
                    key(KeyEvent.ACTION_UP, code)
                    held.remove(code)
                }
                frames.get()
            }
            waitUntil(up + after)
            return frames.get()
        } catch (t: Throwable) {
            releaseAll()
            throw t
        }
    }

    private fun touch(x: Float, y: Float, hold: Long, after: Long): Long {
        try {
            val down = onStep { LibretroDroid.onTouchEvent(x, y); frames.get() }
            waitUntil(down + hold)
            val up = onStep { LibretroDroid.onTouchEvent(-10f, 10f); frames.get() }
            waitUntil(up + after)
            return frames.get()
        } catch (t: Throwable) {
            runCatching { onStep { LibretroDroid.onTouchEvent(-10f, 10f) } }
            throw t
        }
    }

    // ---- HTTP -------------------------------------------------------------------------------------------------

    private class Request(val method: String, val path: String, val query: Map<String, String>, val headers: Map<String, String>, val body: ByteArray)

    /** One connection, as many requests as the bot sends on it (keep-alive): a frame-by-frame bot sends ~60 a second. */
    private fun serve(c: Socket) {
        c.soTimeout = 120_000
        c.tcpNoDelay = true
        val input = BufferedInputStream(c.getInputStream())
        val out = java.io.BufferedOutputStream(c.getOutputStream())
        while (true) {
            val req = readRequest(input) ?: return
            val given = (req.headers["x-bot-token"] ?: req.query["k"] ?: "").toByteArray(Charsets.UTF_8)
            if (!MessageDigest.isEqual(given, token)) return respond(out, 403, "text/plain", "Wrong token.".toByteArray())
            try {
                synchronized(one) { route(req, out) }
            } catch (e: NotRunning) {
                respond(out, 503, "text/plain", (e.message ?: "").toByteArray())
            } catch (e: IllegalArgumentException) {
                respond(out, 400, "text/plain", (e.message ?: "Bad request.").toByteArray())
            } catch (t: Throwable) {
                Log.w(TAG, "bot request failed", t)
                respond(out, 500, "text/plain", t.toString().toByteArray())
            }
            if (req.headers["connection"].equals("close", ignoreCase = true)) return
        }
    }

    private fun route(req: Request, out: OutputStream) {
        val q = req.query
        when ("${req.method} ${req.path}") {
            "GET /ping" -> json(out, ping())
            "GET /mem" -> {
                val a = hex(q["a"]); val n = int(q["n"], 1, 1 shl 24)
                respond(out, 200, "application/octet-stream", onStep { LibretroDroid.readMemory(a, n) })
            }
            "GET /mems" -> {
                val ranges = ranges(q["r"] ?: throw IllegalArgumentException("r is missing"))
                val parts = onStep { ranges.map { (a, n) -> LibretroDroid.readMemory(a, n) } }
                val buf = ByteArrayOutputStream()
                writeParts(buf, parts)
                respond(out, 200, "application/octet-stream", buf.toByteArray())
            }
            "POST /mem" -> {
                val a = hex(q["a"])
                val wrote = onStep { LibretroDroid.writeMemory(a, req.body) }
                json(out, """{"wrote":$wrote}""")
            }
            "POST /press" -> {
                val keys = (q["k"] ?: "").split(',').filter { it.isNotBlank() }.map {
                    KEYS[it.trim().uppercase()] ?: throw IllegalArgumentException("unknown button $it")
                }
                val f = press(keys, int(q["f"], 1, 100_000, 6).toLong(), int(q["r"], 0, 100_000, 2).toLong())
                json(out, """{"frames":$f}""")
            }
            "POST /wait" -> {
                val n = int(q["f"], 0, 1_000_000, 1).toLong()
                val start = onStep { frames.get() }
                waitUntil(start + n)
                json(out, """{"frames":${frames.get()}}""")
            }
            "POST /touch" -> {
                val x = (q["x"] ?: "").toFloatOrNull() ?: throw IllegalArgumentException("x")
                val y = (q["y"] ?: "").toFloatOrNull() ?: throw IllegalArgumentException("y")
                val f = touch(x, y, int(q["f"], 1, 100_000, 8).toLong(), int(q["r"], 0, 100_000, 2).toLong())
                json(out, """{"frames":$f}""")
            }
            "POST /frame" -> {
                val keys = (q["k"] ?: "").split(',').filter { it.isNotBlank() }.map {
                    KEYS[it.trim().uppercase()] ?: throw IllegalArgumentException("unknown button $it")
                }.toSet()
                val ranges = ranges(q["r"])
                val (f, parts) = if (locked) {
                    lastFrameCallAt = System.currentTimeMillis()
                    val call = FrameCall(keys, int(q["f"], 1, 3600, 1), ranges)
                    frameCalls.add(call)
                    if (!call.done.await(5_000, TimeUnit.MILLISECONDS)) {
                        frameCalls.remove(call)
                        throw NotRunning()
                    }
                    call.error?.let { throw it }
                    call.frames to call.parts
                } else onStep {
                    setHeld(keys)
                    frames.get() to ranges.map { (a, n) -> LibretroDroid.readMemory(a, n) }
                }
                val buf = ByteArrayOutputStream()
                for (i in 7 downTo 0) buf.write((f ushr (i * 8)).toInt() and 0xFF)
                writeParts(buf, parts)
                respond(out, 200, "application/octet-stream", buf.toByteArray())
            }
            "POST /reset" -> {
                // The console's reset button: the game restarts from its title screen. The in-game save stays.
                onStep(15_000) { view -> view.reset(false) }
                json(out, """{"reset":true}""")
            }
            "POST /lock" -> {
                val on = q["on"] != "0"
                onStep { view -> if (on) lock(view) else unlock(view) }
                json(out, """{"locked":$locked}""")
            }
            "POST /stall" -> {
                // Holds the emulation thread for ms milliseconds, the way a torn-down or stuck view would. For
                // proving the app's own waits on it give up instead of freezing the app (the AYN Thor ANR).
                val ms = int(q["ms"], 1, 60_000).toLong()
                jobs.add(Job { Thread.sleep(ms) })
                json(out, """{"stalling":$ms}""")
            }
            "GET /state" -> respond(out, 200, "application/octet-stream", onStep(15_000) { it.serializeState(false) })
            "POST /state" -> {
                val ok = onStep(15_000) { it.unserializeState(req.body, false) }
                json(out, """{"loaded":$ok}""")
            }
            else -> respond(out, 404, "text/plain", "No such route.".toByteArray())
        }
    }

    /** "a:n,a:n" (hex address, decimal length) as pairs; empty or missing is none. */
    private fun ranges(r: String?): List<Pair<Long, Int>> =
        (r ?: "").split(',').filter { it.isNotBlank() }.map {
            val p = it.split(':', limit = 2)
            hex(p[0]) to int(p.getOrNull(1), 1, 1 shl 24)
        }

    /** Each range as its length in 4 bytes (big endian, -1 = unmapped), then its bytes. */
    private fun writeParts(buf: ByteArrayOutputStream, parts: List<ByteArray>) {
        for (p in parts) {
            val len = if (p.isEmpty()) -1 else p.size
            buf.write(byteArrayOf((len ushr 24).toByte(), (len ushr 16).toByte(), (len ushr 8).toByte(), len.toByte()))
            buf.write(p)
        }
    }

    private fun ping(): String {
        val idle = if (lastStepAt == 0L) -1 else System.currentTimeMillis() - lastStepAt
        return """{"ok":true,"frames":${frames.get()},"steps":${steps.get()},"speed":$speed,"idleMs":$idle}"""
    }

    private fun hex(s: String?): Long =
        s?.removePrefix("0x")?.toLongOrNull(16) ?: throw IllegalArgumentException("address is missing or not hex")

    private fun int(s: String?, min: Int, max: Int, default: Int? = null): Int {
        val v = s?.toIntOrNull() ?: default ?: throw IllegalArgumentException("a number is missing")
        if (v < min || v > max) throw IllegalArgumentException("$v is out of range")
        return v
    }

    private fun readRequest(input: InputStream): Request? {
        val line = readLine(input) ?: return null
        val headers = LinkedHashMap<String, String>()
        while (true) {
            val h = readLine(input) ?: return null
            if (h.isEmpty()) break
            if (headers.size >= 100) return null
            val colon = h.indexOf(':')
            if (colon > 0) headers[h.substring(0, colon).trim().lowercase()] = h.substring(colon + 1).trim()
        }
        val parts = line.split(' ')
        if (parts.size < 2) return null
        val (path, query) = parts[1].split('?', limit = 2).let { it[0] to it.getOrElse(1) { "" } }
        val q = query.split('&').filter { it.isNotEmpty() }.associate { kv ->
            val (k, v) = kv.split('=', limit = 2).let { it[0] to it.getOrElse(1) { "" } }
            URLDecoder.decode(k, "UTF-8") to URLDecoder.decode(v, "UTF-8")
        }
        val length = headers["content-length"]?.toIntOrNull() ?: 0
        if (length < 0 || length > MAX_BODY) return null
        val body = ByteArray(length)
        var got = 0
        while (got < length) {
            val n = input.read(body, got, length - got)
            if (n < 0) return null
            got += n
        }
        return Request(parts[0], path, q, headers, body)
    }

    private fun readLine(input: InputStream): String? {
        val sb = StringBuilder()
        while (true) {
            val b = input.read()
            if (b < 0) return if (sb.isEmpty()) null else sb.toString()
            if (b == '\n'.code) return sb.toString()
            if (b != '\r'.code) {
                if (sb.length >= 8192) return null
                sb.append(b.toChar())
            }
        }
    }

    private fun json(out: OutputStream, body: String) = respond(out, 200, "application/json", body.toByteArray())

    private fun respond(out: OutputStream, status: Int, type: String, body: ByteArray) {
        val reason = when (status) { 200 -> "OK"; 400 -> "Bad Request"; 403 -> "Forbidden"; 404 -> "Not Found"; 503 -> "Service Unavailable"; else -> "Error" }
        val head = "HTTP/1.1 $status $reason\r\nContent-Type: $type\r\nContent-Length: ${body.size}\r\n\r\n"
        out.write(head.toByteArray(Charsets.ISO_8859_1))
        out.write(body)
        out.flush()
    }
}
