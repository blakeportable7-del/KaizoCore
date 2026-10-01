package com.ironmonone.app.stream

import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The wire format, the sound chunking, the per-page outbox and the engine that runs them. */
class GameStreamTest {

    private fun le32(b: ByteArray, at: Int) =
        (b[at].toLong() and 0xFF) or ((b[at + 1].toLong() and 0xFF) shl 8) or ((b[at + 2].toLong() and 0xFF) shl 16) or ((b[at + 3].toLong() and 0xFF) shl 24)

    private fun payloadOf(frame: ByteArray): ByteArray {
        val f = ServerFrame.parse(frame)
        assertEquals(WebSocket.OP_BINARY, f.opcode)
        assertTrue(f.fin)
        return f.payload
    }

    // ------------------------------------------------------------------ the wire format

    @Test
    fun `a picture message is an 8 byte header with the counter, then the PNG`() {
        val png = byteArrayOf(9, 8, 7)
        val m = GameWire.video(0x0102_0304L, png)
        assertContentEquals(byteArrayOf(1, 0, 0, 0, 4, 3, 2, 1, 9, 8, 7), m)
        // Only the low 32 bits travel.
        assertEquals(2L, le32(GameWire.video(0x1_0000_0002L, png), 4))
    }

    @Test
    fun `a sound header is 8 bytes, so the samples start on an even offset`() {
        val h = GameWire.audioHeader(65536)
        assertContentEquals(byteArrayOf(2, 0, 2, 0, 0, 0, 1, 0), h)
        assertEquals(0, GameWire.HEADER % 2, "an Int16Array in the page needs an even byte offset")
    }

    // ------------------------------------------------------------------ the chunker

    private fun body(m: ByteArray) = m.copyOfRange(GameWire.HEADER, m.size)

    @Test
    fun `sound is cut into messages of at most maxFrames whole frames, each with its header`() {
        val pcm = ByteArray(10 * 4) { it.toByte() }
        val msgs = AudioChunker(maxFrames = 4).chunk(pcm, pcm.size, 32768)
        assertEquals(listOf(4, 4, 2), msgs.map { (it.size - GameWire.HEADER) / 4 })
        for (m in msgs) {
            assertContentEquals(GameWire.audioHeader(32768), m.copyOf(GameWire.HEADER))
        }
        assertContentEquals(pcm, msgs.map { body(it) }.reduce { a, b -> a + b }, "the samples, in order, all of them")
    }

    @Test
    fun `a partial frame is held for the next call and never sent short`() {
        val c = AudioChunker(maxFrames = 100)
        val all = ByteArray(16) { (it + 1).toByte() }
        // 6 bytes: one frame goes and two bytes wait.
        val first = c.chunk(all, 6, 48000)
        assertEquals(listOf(4), first.map { body(it).size })
        // 2 waiting and 8 new make 10: two frames go and two bytes wait.
        val second = c.chunk(all.copyOfRange(6, 14), 8, 48000)
        assertEquals(listOf(8), second.map { body(it).size })
        // 2 waiting and 2 new make 4: one frame goes and nothing waits.
        val third = c.chunk(all.copyOfRange(14, 16), 2, 48000)
        assertEquals(listOf(4), third.map { body(it).size })
        // All 16 bytes came out, in order.
        assertContentEquals(all, (first + second + third).map { body(it) }.reduce { a, b -> a + b })
    }

    @Test
    fun `less than one frame sends nothing and keeps the bytes`() {
        val c = AudioChunker()
        assertTrue(c.chunk(byteArrayOf(1, 2, 3), 3, 44100).isEmpty())
        val next = c.chunk(byteArrayOf(4), 1, 44100)
        assertEquals(listOf(1, 2, 3, 4), body(next.single()).map { it.toInt() })
        assertTrue(c.chunk(ByteArray(0), 0, 44100).isEmpty())
    }

    @Test
    fun `the rate on each message is the one given with that call`() {
        val c = AudioChunker()
        val a = c.chunk(ByteArray(8), 8, 32768).single()
        val b = c.chunk(ByteArray(8), 8, 65536).single()
        assertEquals(32768L, le32(a, 4))
        assertEquals(65536L, le32(b, 4))
    }

    @Test
    fun `a frame is cut at 1024 by default, about 20 milliseconds`() {
        val msgs = AudioChunker().chunk(ByteArray(2500 * 4), 2500 * 4, 48000)
        assertEquals(listOf(1024, 1024, 452), msgs.map { (it.size - GameWire.HEADER) / 4 })
    }

    // ------------------------------------------------------------------ the outbox

    @Test
    fun `the picture is one slot and a newer one replaces an unsent older one`() {
        val o = Outbox(1000)
        o.putVideo(byteArrayOf(1)); o.putVideo(byteArrayOf(2)); o.putVideo(byteArrayOf(3))
        assertContentEquals(byteArrayOf(3), o.take(0))
        assertNull(o.take(0))
        assertEquals(2L, o.droppedVideo)
    }

    @Test
    fun `sound keeps its order and past the cap the oldest goes`() {
        val o = Outbox(100)
        for (i in 1..4) o.putAudio(ByteArray(40) { i.toByte() })
        assertEquals(3, o.take(0)!![0].toInt())
        assertEquals(4, o.take(0)!![0].toInt())
        assertNull(o.take(0))
        assertEquals(2L, o.droppedAudio)
        // One message bigger than the cap is still sent: the last one is never dropped.
        o.putAudio(ByteArray(500))
        assertEquals(500, o.take(0)!!.size)
    }

    @Test
    fun `control frames go first, then sound, then the picture`() {
        val o = Outbox(1000)
        o.putVideo(byteArrayOf(30)); o.putAudio(byteArrayOf(20)); o.putControl(byteArrayOf(10))
        assertEquals(listOf(10, 20, 30), listOf(o.take(0), o.take(0), o.take(0)).map { it!![0].toInt() })
    }

    @Test
    fun `take waits for a message and gives up after the timeout`() {
        val o = Outbox(1000)
        val t0 = System.nanoTime()
        assertNull(o.take(60))
        assertTrue(System.nanoTime() - t0 >= TimeUnit.MILLISECONDS.toNanos(50), "it should have waited")
        Thread { Thread.sleep(50); o.putControl(byteArrayOf(7)) }.start()
        assertContentEquals(byteArrayOf(7), o.take(5000))
    }

    @Test
    fun `close wakes a waiter and nothing is accepted after it`() {
        val o = Outbox(1000)
        Thread { Thread.sleep(50); o.close() }.start()
        val t0 = System.nanoTime()
        assertNull(o.take(5000))
        assertTrue(System.nanoTime() - t0 < TimeUnit.SECONDS.toNanos(2), "close should wake it, not the timeout")
        o.putVideo(byteArrayOf(1)); o.putAudio(byteArrayOf(2)); o.putControl(byteArrayOf(3))
        assertNull(o.take(0))
    }

    // ------------------------------------------------------------------ the engine

    private fun stream(feed: GameFeed = FakeGameFeed(), max: Int = GameStream.MAX_VIEWERS) = GameStream(feed, max)

    private fun pixels(seed: Long) = TestPics.structured(24, 16, seed)

    @Test
    fun `the capture switch is off with no viewer, on with one, and off when the last leaves`() {
        val feed = FakeGameFeed()
        val gs = stream(feed)
        assertFalse(feed.capturing, "nobody watching: the taps stay off")
        assertEquals(emptyList(), feed.captureLog, "and were never touched")

        val a = RecordingViewer(); val b = RecordingViewer()
        assertTrue(gs.add(a)); assertTrue(feed.capturing)
        assertTrue(gs.add(b)); assertTrue(feed.capturing)
        assertEquals(2, gs.viewerCount)
        gs.remove(a); assertTrue(feed.capturing, "one page is still watching")
        gs.remove(b); assertFalse(feed.capturing)
        assertEquals(listOf(true, false), feed.captureLog)
    }

    @Test
    fun `removing a viewer that is not there changes nothing`() {
        val feed = FakeGameFeed()
        val gs = stream(feed)
        val a = RecordingViewer()
        gs.remove(a)
        assertEquals(emptyList(), feed.captureLog)
        gs.add(a); gs.remove(a); gs.remove(a)
        assertEquals(listOf(true, false), feed.captureLog)
    }

    @Test
    fun `shutdown turns capture off even with pages still connected`() {
        val feed = FakeGameFeed()
        val gs = stream(feed)
        gs.add(RecordingViewer()); gs.add(RecordingViewer())
        assertTrue(feed.capturing)
        gs.shutdown()
        assertFalse(feed.capturing)
        assertEquals(0, gs.viewerCount)
    }

    @Test
    fun `no more than the limit are accepted`() {
        val gs = stream(max = 2)
        assertTrue(gs.add(RecordingViewer())); assertTrue(gs.add(RecordingViewer()))
        assertFalse(gs.add(RecordingViewer()))
        assertEquals(2, gs.viewerCount)
        gs.shutdown()
    }

    @Test
    fun `a picture reaches every page, decodes to the same pixels and carries its counter`() {
        val feed = FakeGameFeed()
        val gs = stream(feed)
        val a = RecordingViewer(); val b = RecordingViewer()
        gs.add(a); gs.add(b)
        val pic = pixels(1)
        feed.push(24, 16, pic)
        for (v in listOf(a, b)) {
            val msg = payloadOf(assertNotNull(v.video.poll(5, TimeUnit.SECONDS), "a picture"))
            assertEquals(GameWire.VIDEO, msg[0].toInt())
            assertEquals(1L, le32(msg, 4), "the tap's frame counter")
            val (w, h, rgb) = TestPics.decode(msg.copyOfRange(GameWire.HEADER, msg.size))
            assertEquals(24 to 16, w to h)
            assertContentEquals(pic, rgb)
        }
        gs.shutdown()
    }

    @Test
    fun `a frame identical to the last one is not sent again, a changed one is`() {
        val feed = FakeGameFeed()
        val gs = stream(feed)
        val v = RecordingViewer()
        gs.add(v)
        feed.push(24, 16, pixels(1))
        assertNotNull(v.video.poll(5, TimeUnit.SECONDS))
        feed.push(24, 16, pixels(1)); feed.push(24, 16, pixels(1))
        Thread.sleep(250)
        assertTrue(v.video.isEmpty(), "the same picture three times is one message")
        feed.push(24, 16, pixels(2))
        assertNotNull(v.video.poll(5, TimeUnit.SECONDS), "a different picture goes")
        gs.shutdown()
    }

    @Test
    fun `a size change is followed`() {
        val feed = FakeGameFeed()
        val gs = stream(feed)
        val v = RecordingViewer()
        gs.add(v)
        feed.push(24, 16, pixels(1))
        assertNotNull(v.video.poll(5, TimeUnit.SECONDS))
        val wide = TestPics.structured(40, 8, 5)
        feed.push(40, 8, wide)
        val got = payloadOf(assertNotNull(v.video.poll(5, TimeUnit.SECONDS)))
        val (w, h, rgb) = TestPics.decode(got.copyOfRange(GameWire.HEADER, got.size))
        assertEquals(40 to 8, w to h)
        assertContentEquals(wide, rgb)
        gs.shutdown()
    }

    @Test
    fun `a page that joins later gets the newest picture at once`() {
        val feed = FakeGameFeed()
        val gs = stream(feed)
        val first = RecordingViewer()
        gs.add(first)
        feed.push(24, 16, pixels(3))
        val sent = assertNotNull(first.video.poll(5, TimeUnit.SECONDS))
        val late = RecordingViewer()
        gs.add(late)
        assertContentEquals(sent, late.video.poll(0, TimeUnit.SECONDS), "already there, with no new frame from the game")
        gs.shutdown()
    }

    @Test
    fun `a page that comes back after everyone left gets the game's picture, not the one cached from before`() {
        val feed = FakeGameFeed()
        val gs = stream(feed)
        val first = RecordingViewer()
        gs.add(first)
        feed.push(24, 16, pixels(3))
        assertNotNull(first.video.poll(5, TimeUnit.SECONDS))
        gs.remove(first)
        feed.push(24, 16, pixels(9))                    // the game moved on while nobody watched
        val again = RecordingViewer()
        gs.add(again)
        val msg = payloadOf(assertNotNull(again.video.poll(5, TimeUnit.SECONDS)))
        assertContentEquals(pixels(9), TestPics.decode(msg.copyOfRange(GameWire.HEADER, msg.size)).third)
        gs.shutdown()
    }

    @Test
    fun `sound reaches every page as whole frames with the rate it was drained at`() {
        val feed = FakeGameFeed()
        val gs = stream(feed)
        val v = RecordingViewer()
        gs.add(v)
        val pcm = ByteArray(8 * 4) { (it * 3).toByte() }
        feed.pushSound(pcm, 65536)
        val msg = payloadOf(assertNotNull(v.audio.poll(5, TimeUnit.SECONDS), "sound"))
        assertEquals(GameWire.AUDIO, msg[0].toInt())
        assertEquals(2, msg[2].toInt(), "two channels")
        assertEquals(65536L, le32(msg, 4))
        assertContentEquals(pcm, msg.copyOfRange(GameWire.HEADER, msg.size))
        gs.shutdown()
    }

    @Test
    fun `one bad read does not end the stream`() {
        val inner = FakeGameFeed()
        val flaky = object : GameFeed by inner {
            var failures = 2
            override fun frame(afterCounter: Long, into: RawFrame): Boolean {
                if (failures-- > 0) throw IllegalStateException("a read that failed")
                return inner.frame(afterCounter, into)
            }
        }
        val gs = stream(flaky)
        val v = RecordingViewer()
        gs.add(v)
        inner.push(24, 16, pixels(4))
        assertNotNull(v.video.poll(5, TimeUnit.SECONDS), "the picture arrives after the failures")
        gs.shutdown()
    }
}
