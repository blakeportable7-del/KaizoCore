package com.ironmonone.app.stream

import java.util.Arrays
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Where the game's picture and sound come from. The app's is [NativeGameFeed],
 * over JNI to the emulator host; the tests use a fake, so nothing here needs the
 * native library.
 *
 * Only one thread reads (the stream's pump), except [setCapture].
 * Added 2026-09-29 for the stream kit.
 */
interface GameFeed {
    /** Turns the native taps on or off. While off, playing costs nothing extra. */
    fun setCapture(on: Boolean)

    /** Fills [into] with the newest frame if it is newer than [afterCounter]; false when there is none. */
    fun frame(afterCounter: Long, into: RawFrame): Boolean

    /**
     * Fills [into] with buffered sound (16-bit little-endian stereo, whole frames) and
     * returns how many BYTES it wrote, 0 for none. `rate[0]` gets the sample rate in Hz.
     */
    fun audio(into: ByteArray, rate: IntArray): Int
}

/** One frame as the tap hands it over: tightly packed RGB, row by row from the top. */
class RawFrame {
    var counter: Long = 0
    var width: Int = 0
    var height: Int = 0
    /** Exactly width * height * 3 bytes once [prepare] has run, so two frames compare with Arrays.equals. */
    var rgb: ByteArray = ByteArray(0)
        private set

    /** Sizes [rgb] for a frame of this size (reallocating only when the size changed) and returns it. */
    fun prepare(w: Int, h: Int): ByteArray {
        val n = w * h * 3
        if (rgb.size != n) rgb = ByteArray(n)
        width = w
        height = h
        return rgb
    }
}

/**
 * What goes over the socket, besides the JSON text messages.
 *
 * Every binary message starts with the same 8 bytes so the page can read the sound
 * as an Int16Array from offset 8 (which must be even):
 *
 *     byte 0      tag: 1 = a picture, 2 = sound
 *     byte 1      0
 *     byte 2      sound: channels (always 2); picture: 0
 *     byte 3      0
 *     bytes 4-7   little-endian u32: picture = the frame counter, sound = sample rate in Hz
 *
 * A picture then carries one PNG. Sound then carries 16-bit little-endian PCM,
 * interleaved left and right, a whole number of frames (4 bytes each).
 */
object GameWire {
    const val VIDEO = 1
    const val AUDIO = 2
    const val HEADER = 8

    fun video(counter: Long, png: ByteArray): ByteArray {
        val out = ByteArray(HEADER + png.size)
        out[0] = VIDEO.toByte()
        putInt(out, 4, counter.toInt())
        System.arraycopy(png, 0, out, HEADER, png.size)
        return out
    }

    fun audioHeader(rate: Int): ByteArray {
        val h = ByteArray(HEADER)
        h[0] = AUDIO.toByte()
        h[2] = 2
        putInt(h, 4, rate)
        return h
    }

    /** The first text message on every socket, so the page can tell what it reached. */
    const val HELLO = "{\"type\":\"hello\",\"protocol\":1}"

    private fun putInt(b: ByteArray, at: Int, v: Int) {
        b[at] = v.toByte(); b[at + 1] = (v ushr 8).toByte(); b[at + 2] = (v ushr 16).toByte(); b[at + 3] = (v ushr 24).toByte()
    }
}

/**
 * Cuts the sound drained from the phone into small messages, at frame boundaries.
 *
 * A message is at most [maxFrames] stereo frames (about 20 ms), so the page can
 * schedule it without waiting for more. A drain never ends in the middle of a
 * frame from the tap, but the guard is here anyway: a trailing partial frame is
 * held and completed by the next call, never sent short.
 */
class AudioChunker(private val maxFrames: Int = 1024) {
    private val carry = ByteArray(3)
    private var carryLen = 0

    init { require(maxFrames > 0) { "maxFrames" } }

    /** [pcm] holds [len] bytes of 16-bit stereo. Returns finished wire messages, header included. */
    fun chunk(pcm: ByteArray, len: Int, rate: Int): List<ByteArray> {
        if (len <= 0) return emptyList()
        val src: ByteArray
        val total: Int
        if (carryLen > 0) {
            src = ByteArray(carryLen + len)
            System.arraycopy(carry, 0, src, 0, carryLen)
            System.arraycopy(pcm, 0, src, carryLen, len)
            total = src.size
        } else {
            src = pcm
            total = len
        }
        val whole = total - total % FRAME_BYTES
        val out = ArrayList<ByteArray>()
        var at = 0
        while (at < whole) {
            val n = minOf(maxFrames * FRAME_BYTES, whole - at)
            val msg = ByteArray(GameWire.HEADER + n)
            System.arraycopy(GameWire.audioHeader(rate), 0, msg, 0, GameWire.HEADER)
            System.arraycopy(src, at, msg, GameWire.HEADER, n)
            out += msg
            at += n
        }
        carryLen = total - whole
        System.arraycopy(src, whole, carry, 0, carryLen)
        return out
    }

    private companion object { const val FRAME_BYTES = 4 }
}

/**
 * What one connected page has waiting to be written, and how it is dropped when the
 * page falls behind. A slow Wi-Fi link must cost the phone some frames, never the
 * emulator its speed and never memory.
 *
 *  - control frames (close, hello) always go, first;
 *  - the pong is ONE slot, after them: a newer one replaces an unsent older one,
 *    as RFC 6455 5.5.3 lets an endpoint answer only the most recent ping. A page
 *    that pinged without reading queued one per ping until the heap ran out
 *    (rc32 audit P3 #77);
 *  - sound waits in order but is capped: past [audioCapBytes] the OLDEST goes, so
 *    what is left is the newest sound and the delay stays bounded;
 *  - the picture is ONE slot: a newer frame replaces an unsent older one.
 *
 * Every message here is a finished WebSocket frame, so a message made once can be
 * handed to every viewer.
 */
internal class Outbox(private val audioCapBytes: Int) {
    private val lock = ReentrantLock()
    private val ready = lock.newCondition()
    private val control = ArrayDeque<ByteArray>()
    private var pong: ByteArray? = null
    private val audio = ArrayDeque<ByteArray>()
    private var audioBytes = 0
    private var video: ByteArray? = null
    private var closed = false

    var droppedVideo = 0L
        private set
    var droppedAudio = 0L
        private set

    fun putControl(frame: ByteArray) {
        lock.withLock {
            if (closed) return
            control.addLast(frame)
            ready.signalAll()
        }
    }

    /** The answer to a ping: it replaces one still waiting. */
    fun putPong(frame: ByteArray) {
        lock.withLock {
            if (closed) return
            pong = frame
            ready.signalAll()
        }
    }

    fun putAudio(frame: ByteArray) {
        lock.withLock {
            if (closed) return
            audio.addLast(frame)
            audioBytes += frame.size
            while (audioBytes > audioCapBytes && audio.size > 1) {
                audioBytes -= audio.removeFirst().size
                droppedAudio++
            }
            ready.signalAll()
        }
    }

    fun putVideo(frame: ByteArray) {
        lock.withLock {
            if (closed) return
            if (video != null) droppedVideo++
            video = frame
            ready.signalAll()
        }
    }

    /** The next frame to write, or null after [timeoutMs] with nothing to send (or once closed and empty). */
    fun take(timeoutMs: Long): ByteArray? = lock.withLock {
        var left = TimeUnit.MILLISECONDS.toNanos(timeoutMs)
        while (control.isEmpty() && pong == null && audio.isEmpty() && video == null) {
            if (closed || left <= 0) return null
            left = ready.awaitNanos(left)
        }
        control.removeFirstOrNull()?.let { return it }
        pong?.let { pong = null; return it }
        audio.removeFirstOrNull()?.let { audioBytes -= it.size; return it }
        val v = video
        video = null
        v
    }

    fun close() = lock.withLock { closed = true; ready.signalAll() }
}

/**
 * The stream's engine: while at least one page is connected it reads the phone's
 * picture and sound and hands them to every page.
 *
 * The rules that shape it:
 *  - The emulator's taps are on only while a viewer is connected. The first viewer
 *    turns them on and starts the pump; the last one to leave stops the pump and
 *    turns them off.
 *  - The picture is encoded ONCE per frame, on the pump's thread, whatever the
 *    number of viewers, and a frame identical to the last one is not encoded at
 *    all (a menu or a pause costs nothing).
 *  - The pump takes the newest frame and skips the ones in between, at most about
 *    60 a second, so a slow encoder or link means fewer frames, not a growing delay.
 *  - A viewer that joins gets the newest picture at once, so it is not blank until
 *    the game next changes.
 *  - It never asks what the game is: a run, a library game, a ROM hack, tracked or not,
 *    the picture and sound go out the same (Blake, 2026-09-29: every mode).
 */
class GameStream(private val feed: GameFeed, private val maxViewers: Int = MAX_VIEWERS) {

    /** One connected page. Messages are finished WebSocket frames, shared between viewers. */
    internal interface Viewer {
        fun sendVideo(frame: ByteArray)
        fun sendAudio(frame: ByteArray)
    }

    private val viewers = CopyOnWriteArrayList<Viewer>()
    private var pump: Pump? = null
    @Volatile private var lastPicture: ByteArray? = null
    /**
     * Set by [shutdown], for good. A page whose connection was still arriving as the server stopped used to be added
     * after it: the taps came back on and a pump ran on this stream, which the next STREAM ON's pump then raced for
     * the same native buffers (rc32 audit P3 #82).
     */
    private var closed = false

    val viewerCount: Int get() = viewers.size

    /** False when [maxViewers] are already connected, or the stream has been shut down. */
    @Synchronized
    internal fun add(viewer: Viewer): Boolean {
        if (closed || viewers.size >= maxViewers) return false
        viewers.add(viewer)
        if (viewers.size == 1) startPump()
        lastPicture?.let { viewer.sendVideo(it) }
        return true
    }

    @Synchronized
    internal fun remove(viewer: Viewer) {
        if (!viewers.remove(viewer)) return
        if (viewers.isEmpty()) stopPump()
    }

    /** Server shutdown: no viewer stays, none can join after it, and the taps go off whatever happened before. */
    @Synchronized
    fun shutdown() {
        closed = true
        viewers.clear()
        stopPump()
        runCatching { feed.setCapture(false) }
    }

    private fun startPump() {
        // A stale picture from an earlier session must not greet a new viewer.
        lastPicture = null
        runCatching { feed.setCapture(true) }
        pump = Pump().also { it.start() }
    }

    private fun stopPump() {
        pump?.let { it.stopNow(); runCatching { it.join(1000) } }
        pump = null
        runCatching { feed.setCapture(false) }
    }

    private inner class Pump : Thread("stream-game-pump") {
        @Volatile private var stop = false

        init { isDaemon = true }

        fun stopNow() { stop = true }

        override fun run() {
            val encoder = PngEncoder()
            var current = RawFrame()
            var previous = RawFrame()
            var lastCounter = 0L
            var lastEncodedNs = 0L
            val sound = ByteArray(SOUND_READ_BYTES)
            val rate = IntArray(1)
            val chunker = AudioChunker()
            var emptyPasses = 0
            try {
                while (!stop) {
                    var worked = false
                    try {
                        // Sound first: a late picture is a hiccup, late sound is a crackle.
                        while (!stop) {
                            val n = feed.audio(sound, rate)
                            if (n <= 0) break
                            worked = true
                            for (msg in chunker.chunk(sound, n, rate[0])) {
                                val frame = WebSocket.encode(WebSocket.OP_BINARY, msg)
                                for (v in viewers) v.sendAudio(frame)
                            }
                            if (n < sound.size) break
                        }

                        val now = System.nanoTime()
                        if (!stop && now - lastEncodedNs >= MIN_FRAME_GAP_NS && feed.frame(lastCounter, current)) {
                            worked = true
                            lastCounter = current.counter
                            lastEncodedNs = now
                            if (!sameAs(current, previous)) {
                                val png = encoder.encode(current.rgb, current.width, current.height)
                                val frame = WebSocket.encode(WebSocket.OP_BINARY, GameWire.video(current.counter, png))
                                lastPicture = frame
                                for (v in viewers) v.sendVideo(frame)
                                val t = previous; previous = current; current = t
                            }
                        }
                    } catch (e: Exception) {
                        // One bad read must not end the stream; do not spin on it either.
                        sleepQuietly(50)
                    }
                    if (worked) emptyPasses = 0 else sleepQuietly(idleSleepMs(++emptyPasses))
                }
            } finally {
                encoder.release()
            }
        }

        private fun sameAs(a: RawFrame, b: RawFrame) =
            a.width == b.width && a.height == b.height && Arrays.equals(a.rgb, b.rgb)

        private fun sleepQuietly(ms: Long) { try { sleep(ms) } catch (e: InterruptedException) { stop = true } }
    }

    companion object {
        /** Pages at once. Each costs a socket and two threads, and the picture is encoded once for all. */
        const val MAX_VIEWERS = 6

        /**
         * How long the pump sleeps after its [emptyPasses]-th pass in a row with no sound and no new picture: 4 ms for
         * the first eight, then twice as long each pass, to 100 ms. With the game paused, in the background or on
         * another tab, a connected OBS kept the pump at 250 passes a second, two JNI calls each (rc32 audit P3 #78).
         * The first picture or sound after a still stretch can wait up to that 100 ms.
         */
        internal fun idleSleepMs(emptyPasses: Int): Long =
            if (emptyPasses <= IDLE_FAST_PASSES) IDLE_SLEEP_MS
            else minOf(MAX_IDLE_SLEEP_MS, IDLE_SLEEP_MS shl minOf(emptyPasses - IDLE_FAST_PASSES, 5))

        private const val SOUND_READ_BYTES = 32 * 1024
        private const val IDLE_SLEEP_MS = 4L
        private const val IDLE_FAST_PASSES = 8
        private const val MAX_IDLE_SLEEP_MS = 100L
        /** About 66 pictures a second at most, whatever the core's speed. */
        private const val MIN_FRAME_GAP_NS = 15_000_000L
    }
}
