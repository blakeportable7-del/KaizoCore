package com.ironmonone.app.stream

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket

/**
 * One page connected to /game.ws, after the HTTP request has been read.
 *
 * Two threads per page. The one that called [run] reads what the page sends
 * (nothing but pings, pongs and a close, in practice) and the writer thread is the
 * ONLY one that writes to the socket, so a frame is never interleaved with
 * another. Everything the writer sends comes from an [Outbox], which is where a
 * slow page loses old frames instead of slowing the phone.
 *
 * The phone pings every few seconds. A page that answers keeps the read timeout
 * from firing; a page that vanished without a goodbye (Wi-Fi dropped, laptop
 * shut) times out, the session ends, and if it was the last one the emulator's
 * taps go off. The page, for its part, reconnects by itself when this ends.
 * Added 2026-09-29 for the stream kit.
 */
internal class GameSocket(
    private val sock: Socket,
    private val input: InputStream,
    private val stream: GameStream,
    private val pingEveryMs: Long = PING_EVERY_MS,
    private val readTimeoutMs: Int = READ_TIMEOUT_MS,
) : GameStream.Viewer {

    private val out: OutputStream = sock.getOutputStream()
    private val outbox = Outbox(AUDIO_BACKLOG_BYTES)
    @Volatile private var finished = false
    @Volatile private var closeFrame: ByteArray? = null

    override fun sendVideo(frame: ByteArray) = outbox.putVideo(frame)
    override fun sendAudio(frame: ByteArray) = outbox.putAudio(frame)

    /** Ends the session from outside (server stop): the page sees the socket drop and reconnects. */
    fun abort() {
        finished = true
        outbox.close()
        closeSocket()
    }

    /**
     * Completes the handshake and serves the page until it or the phone ends the
     * session. Returns false, having written nothing, when the stream is full.
     */
    fun run(clientKey: String): Boolean {
        outbox.putControl(WebSocket.text(GameWire.HELLO))
        if (!stream.add(this)) return false

        val writer = Thread({ writeLoop() }, "stream-ws-out").apply { isDaemon = true }
        try {
            sock.tcpNoDelay = true       // small sound messages must not wait on Nagle's timer
            sock.keepAlive = true
            sock.soTimeout = readTimeoutMs
            out.write(WebSocket.handshakeResponse(clientKey))
            out.flush()
            writer.start()               // only after the 101 has gone out
            readLoop()
        } catch (e: WebSocket.ProtocolError) {
            sendClose(WebSocket.close(e.code, e.message ?: ""))
        } catch (e: IOException) {
            // The page went away, or stopped answering pings. Nothing to tell it.
        } finally {
            stream.remove(this)
            finished = true
            outbox.close()
            if (writer.isAlive) {
                // Give a close frame queued above a moment to reach the page.
                try { writer.join(CLOSE_GRACE_MS) } catch (e: InterruptedException) { }
            }
            closeSocket()
        }
        return true
    }

    private fun readLoop() {
        var inMessage = false
        while (!finished) {
            val f = WebSocket.readFrame(input, MAX_INCOMING) ?: return
            when (f.opcode) {
                // One pong waiting at most, the newest (Outbox.putPong; rc32 audit P3 #77).
                WebSocket.OP_PING -> outbox.putPong(WebSocket.encode(WebSocket.OP_PONG, f.payload))
                WebSocket.OP_PONG -> { }
                WebSocket.OP_CLOSE -> { sendClose(closeReply(f.payload)); return }
                WebSocket.OP_TEXT, WebSocket.OP_BINARY -> {
                    if (inMessage) throw WebSocket.ProtocolError(WebSocket.CLOSE_PROTOCOL_ERROR, "a new message began inside a fragmented one")
                    inMessage = !f.fin
                }
                WebSocket.OP_CONTINUATION -> {
                    if (!inMessage) throw WebSocket.ProtocolError(WebSocket.CLOSE_PROTOCOL_ERROR, "continuation with nothing to continue")
                    inMessage = !f.fin
                }
            }
        }
    }

    /** The answer to a client's close: its own code echoed when it is a legal one. */
    private fun closeReply(payload: ByteArray): ByteArray {
        if (payload.isEmpty()) return WebSocket.encode(WebSocket.OP_CLOSE, payload)
        if (payload.size == 1) return WebSocket.close(WebSocket.CLOSE_PROTOCOL_ERROR)
        val code = ((payload[0].toInt() and 0xFF) shl 8) or (payload[1].toInt() and 0xFF)
        val legal = code in 1000..1003 || code in 1007..1011 || code in 3000..4999
        return if (legal) WebSocket.close(code) else WebSocket.close(WebSocket.CLOSE_PROTOCOL_ERROR)
    }

    private fun sendClose(frame: ByteArray) {
        closeFrame = frame
        outbox.putControl(frame)
    }

    private fun writeLoop() {
        var lastPing = System.nanoTime()
        try {
            while (!finished) {
                // Wake for the next message, or for the next ping if that comes first.
                val untilPing = pingEveryMs - (System.nanoTime() - lastPing) / 1_000_000L
                val frame = outbox.take(untilPing.coerceIn(1L, WAKE_EVERY_MS))
                val now = System.nanoTime()
                if (now - lastPing >= pingEveryMs * 1_000_000L) {
                    out.write(PING)
                    lastPing = now
                }
                if (frame != null) {
                    out.write(frame)
                    if (frame === closeFrame) return     // the close went out: that is the end
                }
            }
            // The session ended before the loop took a queued close frame: it still goes.
            closeFrame?.let { out.write(it) }
        } catch (e: IOException) {
            // The page is gone; the reader will notice the closed socket.
        } finally {
            closeSocket()
        }
    }

    private fun closeSocket() { try { sock.close() } catch (e: IOException) { } }

    companion object {
        const val PING_EVERY_MS = 5_000L
        /** Four pings unanswered. */
        const val READ_TIMEOUT_MS = 20_000
        /** About a third of a second of sound at mGBA's rate: beyond it, the oldest goes. */
        const val AUDIO_BACKLOG_BYTES = 96 * 1024
        private const val MAX_INCOMING = 64 * 1024
        private const val WAKE_EVERY_MS = 1_000L
        private const val CLOSE_GRACE_MS = 300L
        private val PING = WebSocket.encode(WebSocket.OP_PING, ByteArray(0))
    }
}
