package com.ironmonone.app.stream.twitch

import java.io.IOException
import java.net.SocketTimeoutException

/** One chat line from channel.chat.message, the fields Stream Connect reads. */
class ChatLine(
    val messageId: String,
    val text: String,
    val broadcasterId: String,
    val chatterId: String,
    val chatterName: String,
    /** Set in a Shared Chat session for a line that came from another channel; null for this channel's own. */
    val sourceBroadcasterId: String?,
)

/**
 * One EventSub WebSocket session (dev.twitch.tv/docs/eventsub/handling-websocket-events, checked 2026-10-05): connect,
 * read the welcome, subscribe to channel.chat.message with the welcome's session id, then read until the session ends.
 *
 * - Keepalive: Twitch sends session_keepalive when it has nothing else for keepalive_timeout_seconds; a read that hears
 *   nothing for that long plus [slackMs] means the connection is dead (Wi-Fi gone, Twitch gone), and [run] returns
 *   [End.Lost] for the caller to reconnect with backoff.
 * - session_reconnect: connect to the reconnect_url, wait for its welcome, then close the old connection. The
 *   subscription carries over, so nothing is subscribed again.
 * - revocation: the subscription is gone ([End.Revoked] with Twitch's status).
 * - Duplicates: a message_id seen before is dropped.
 */
class EventSub(
    private val connect: (String) -> WsClient = { WsClient.connect(it) },
    /** Creates the chat subscription for a session id. */
    private val subscribe: (String) -> Subscribed,
    private val onChat: (ChatLine) -> Unit,
    /** After every message, keepalives included: the caller's hourly token check. False ends the session. */
    private val tick: () -> Boolean = { true },
    private val slackMs: Int = 10_000,
    /** How long the first message may take; Twitch sends the welcome at once. */
    private val welcomeMs: Int = 15_000,
) {
    sealed class Subscribed {
        object Ok : Subscribed()
        /** 401: the access token is no good; the caller refreshes and reconnects. */
        object Unauthorized : Subscribed()
        /** 403: the token lacks a scope, which only a new sign-in fixes. */
        object Forbidden : Subscribed()
        class Failed(val code: Int) : Subscribed()
    }

    sealed class End {
        object Stopped : End()
        class Lost(val why: String) : End()
        class Closed(val code: Int) : End()
        class Revoked(val status: String) : End()
        class NotSubscribed(val result: Subscribed) : End()
        object TickStopped : End()
    }

    @Volatile private var current: WsClient? = null
    @Volatile private var stopping = false
    private val seen = object : LinkedHashMap<String, Boolean>(64, 0.75f, false) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Boolean>?) = size > 500
    }

    /** From any thread: ends [run] by closing the socket under it. */
    fun stop() {
        stopping = true
        current?.close()
    }

    /** Blocks until the session ends. Never throws. */
    fun run(url: String, onListening: () -> Unit = {}): End {
        try {
            var ws = connect(url).also { current = it }
            if (stopping) { ws.close(); return End.Stopped }
            ws.timeout(welcomeMs)
            val welcome = readJson(ws) ?: return End.Lost("no welcome")
            if (type(welcome) != "session_welcome") { ws.close(); return End.Lost("first message was not a welcome") }
            var keepaliveMs = keepalive(welcome)
            val sessionId = JsonIn.str(JsonIn.child(welcome, "payload", "session"), "id")
            if (sessionId.isNullOrBlank()) { ws.close(); return End.Lost("welcome without a session") }
            val sub = subscribe(sessionId)
            if (sub != Subscribed.Ok) { ws.close(); return End.NotSubscribed(sub) }
            onListening()
            while (true) {
                if (stopping) { ws.close(); return End.Stopped }
                ws.timeout(keepaliveMs + slackMs)
                val msg = try { ws.read() } catch (e: SocketTimeoutException) {
                    ws.close(); return End.Lost("keepalive missed")
                }
                if (msg is WsClient.Message.Close) { ws.close(); return if (stopping) End.Stopped else End.Closed(msg.code) }
                val m = JsonIn.obj((msg as WsClient.Message.Text).text) ?: continue
                val id = JsonIn.str(JsonIn.child(m, "metadata"), "message_id")
                if (id != null) { if (seen.containsKey(id)) continue; seen[id] = true }
                when (type(m)) {
                    "session_keepalive" -> {}
                    "notification" -> chat(m)?.let { line -> runCatching { onChat(line) } }
                    "session_reconnect" -> {
                        val next = JsonIn.str(JsonIn.child(m, "payload", "session"), "reconnect_url")
                        // Never from TLS down to plain: ws:// is only ever the tests' local server.
                        val allowed = next != null && (next.startsWith("wss://") || (!url.startsWith("wss://") && next.startsWith("ws://")))
                        if (!allowed || next == null) { ws.close(); return End.Lost("bad reconnect address") }
                        val fresh = connect(next)
                        fresh.timeout(welcomeMs)
                        val w = readJson(fresh)
                        if (w == null || type(w) != "session_welcome") { fresh.close(); ws.close(); return End.Lost("no welcome after reconnect") }
                        keepaliveMs = keepalive(w)
                        ws.close()
                        ws = fresh; current = fresh
                    }
                    "revocation" -> {
                        ws.close()
                        return End.Revoked(JsonIn.str(JsonIn.child(m, "payload", "subscription"), "status") ?: "revoked")
                    }
                }
                if (!tick()) { ws.close(); return End.TickStopped }
            }
        } catch (e: IOException) {
            current?.close()
            return if (stopping) End.Stopped else End.Lost(e.javaClass.simpleName)
        } catch (e: RuntimeException) {
            current?.close()
            return if (stopping) End.Stopped else End.Lost(e.javaClass.simpleName)
        }
    }

    private fun readJson(ws: WsClient): Map<String, Any?>? = when (val m = ws.read()) {
        is WsClient.Message.Text -> JsonIn.obj(m.text)
        is WsClient.Message.Close -> null
    }

    private fun type(m: Map<String, Any?>) = JsonIn.str(JsonIn.child(m, "metadata"), "message_type")

    private fun keepalive(welcome: Map<String, Any?>): Int =
        ((JsonIn.long(JsonIn.child(welcome, "payload", "session"), "keepalive_timeout_seconds") ?: 30L).coerceIn(10, 600) * 1000).toInt()

    private fun chat(m: Map<String, Any?>): ChatLine? {
        if (JsonIn.str(JsonIn.child(m, "metadata"), "subscription_type") != CHAT_TYPE) return null
        val e = JsonIn.child(m, "payload", "event") ?: return null
        return ChatLine(
            messageId = JsonIn.str(e, "message_id") ?: return null,
            text = JsonIn.str(JsonIn.child(e, "message"), "text") ?: return null,
            broadcasterId = JsonIn.str(e, "broadcaster_user_id").orEmpty(),
            chatterId = JsonIn.str(e, "chatter_user_id").orEmpty(),
            chatterName = JsonIn.str(e, "chatter_user_name") ?: JsonIn.str(e, "chatter_user_login").orEmpty(),
            sourceBroadcasterId = JsonIn.str(e, "source_broadcaster_user_id")?.takeIf { it.isNotBlank() },
        )
    }

    companion object {
        const val CHAT_TYPE = "channel.chat.message"
    }
}
