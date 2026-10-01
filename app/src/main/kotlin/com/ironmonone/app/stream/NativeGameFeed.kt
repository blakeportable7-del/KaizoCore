package com.ironmonone.app.stream

import com.swordfish.libretrodroid.LibretroDroid
import java.nio.ByteBuffer

/**
 * The game's picture and sound, read from the emulator host's taps over JNI
 * (cpp/streamtap.h). Direct buffers, because the native side writes straight into
 * them; the copy out to a plain array is one bulk get.
 *
 * The JVM tests never touch this object (it would load the native library), they
 * use a fake [GameFeed]. Only the stream's pump thread reads, one thread at a time.
 * Added 2026-09-29 for the stream kit.
 */
internal object NativeGameFeed : GameFeed {
    /** About 0.12 s of stereo at 65 kHz per drain is far more than the pump ever finds waiting. */
    const val SOUND_BYTES = 32 * 1024

    /** A DS with both screens stacked is 256 x 384; this is room for the biggest layouts too. */
    private const val FIRST_FRAME_BYTES = 512 * 512 * 3

    private val size = IntArray(2)
    private val rateOut = IntArray(1)
    private var frameBuf: ByteBuffer = ByteBuffer.allocateDirect(FIRST_FRAME_BYTES)
    private val soundBuf: ByteBuffer = ByteBuffer.allocateDirect(SOUND_BYTES)

    override fun setCapture(on: Boolean) {
        LibretroDroid.setStreamCapture(on)
    }

    override fun frame(afterCounter: Long, into: RawFrame): Boolean {
        var counter = LibretroDroid.streamFrame(frameBuf, afterCounter, size)
        if (counter == -1L) {
            // A DS layout change made the picture bigger than the buffer; the native side
            // reported the size it needs.
            frameBuf = ByteBuffer.allocateDirect(size[0] * size[1] * 3)
            counter = LibretroDroid.streamFrame(frameBuf, afterCounter, size)
        }
        if (counter <= 0L) return false
        val rgb = into.prepare(size[0], size[1])
        frameBuf.rewind()
        frameBuf.get(rgb, 0, rgb.size)
        into.counter = counter
        return true
    }

    override fun audio(into: ByteArray, rate: IntArray): Int {
        val n = LibretroDroid.streamAudio(soundBuf, rateOut)
        if (n <= 0) return 0
        val take = minOf(n, into.size and 3.inv())      // whole stereo frames only
        soundBuf.rewind()
        soundBuf.get(into, 0, take)
        rate[0] = rateOut[0]
        return take
    }
}
