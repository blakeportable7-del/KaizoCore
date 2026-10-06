package com.ironmonone.app.stream.twitch

import com.ironmonone.app.stream.Json
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.IOException
import java.util.concurrent.Executor
import java.util.concurrent.Executors

/** Which commands answer and whether chat is answered at all. Saved in [file] when there is one (the app's is under prep/settings/). */
class ChatSettings(private val file: File? = null) {
    @Volatile var answering: Boolean = true
        private set
    @Volatile private var off: Set<ChatCommand> = emptySet()

    init {
        file?.let { f ->
            runCatching { f.readLines() }.getOrNull()?.forEach { line ->
                val k = line.substringBefore('=').trim(); val v = line.substringAfter('=', "").trim()
                when (k) {
                    "answering" -> answering = v != "0"
                    "off" -> off = v.split(',').mapNotNull { ChatCommand.of(it.trim()) }.toSet()
                }
            }
        }
    }

    fun enabled(c: ChatCommand): Boolean = c !in off

    fun setEnabled(c: ChatCommand, on: Boolean) { off = if (on) off - c else off + c; save() }

    fun setAnswering(on: Boolean) { answering = on; save() }

    private fun save() {
        val f = file ?: return
        runCatching {
            f.parentFile?.mkdirs()
            com.ironmonone.app.SafeWrite.text(f, "answering=${if (answering) 1 else 0}\noff=${off.joinToString(",") { it.word }}\n")
        }
    }
}

/**
 * Stream Connect's engine: sign-in, the chat connection and the answers, on threads of its own. Nothing here runs on
 * the emulator's thread or the main thread, nothing here blocks either, and nothing here throws out of its threads:
 * the game only ever hands the stream page a JSON string (StreamHub.state), and this reads that string.
 *
 * A connection that drops (Wi-Fi gone, Twitch down, a keepalive missed) is tried again after 1, 2, 4 ... seconds, up to
 * a minute, with a little jitter; one that stayed up a minute starts that count again. Only Twitch saying the sign-in
 * is no longer good (a refresh token refused, the subscription revoked, a scope missing) stops it, and then the
 * settings screen asks to connect again.
 *
 * [pace] scales every wait (the tests run the clock fast); [sendOn] is where replies go out, off the reading thread.
 */
class TwitchLink(
    private val store: TwitchTokenStore,
    val settings: ChatSettings,
    private val facts: ChatFacts,
    private val state: () -> String,
    private val http: TwitchHttp = UrlHttp,
    private val endpoints: Twitch.Endpoints = Twitch.Endpoints(),
    private val clock: () -> Long = System::currentTimeMillis,
    private val connect: (String) -> WsClient = { WsClient.connect(it) },
    private val pace: (Long) -> Long = { it },
    private val sendOn: Executor = Executors.newSingleThreadExecutor { r -> Thread(r, "twitch-send").apply { isDaemon = true } },
    private val keepaliveSeconds: Int = 30,
    val gate: ChatGate = ChatGate(clock),
) {
    enum class Phase { SIGNED_OUT, GETTING_CODE, WAITING_FOR_CODE, CONNECTING, LISTENING, RECONNECTING, PAUSED }

    /** What the settings screen draws. [code] and [link] only while waiting for the code; [note] is a line for the player. */
    data class Status(val phase: Phase, val name: String = "", val code: String = "", val link: String = "", val note: String = "") {
        val signedIn: Boolean get() = phase in setOf(Phase.CONNECTING, Phase.LISTENING, Phase.RECONNECTING, Phase.PAUSED)
    }

    private val _status = MutableStateFlow(Status(Phase.SIGNED_OUT))
    val status: StateFlow<Status> = _status.asStateFlow()

    /** True while chat is being answered or about to be: the stream snapshot must be built for it (StreamFeed). */
    private val _wantsSnapshot = MutableStateFlow(false)
    val wantsSnapshot: StateFlow<Boolean> = _wantsSnapshot.asStateFlow()

    val auth = TwitchAuth(http, endpoints, clock, sleep = { napAny(it) })

    @Volatile private var tokens: TwitchTokens? = null
    @Volatile private var gen = 0
    @Volatile private var eventSub: EventSub? = null
    @Volatile private var forceRefresh = false
    @Volatile private var lastValidated = 0L
    private val wake = Object()
    private val refreshLock = Any()
    private val sentIds = object : LinkedHashMap<String, Boolean>(32, 0.75f, false) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Boolean>?) = size > 200
    }

    /** Replies that went out, newest last (message text only): the tests read it. */
    val sent = java.util.Collections.synchronizedList(ArrayList<String>())

    private fun setStatus(s: Status) {
        _status.value = s
        _wantsSnapshot.value = s.phase == Phase.CONNECTING || s.phase == Phase.LISTENING || s.phase == Phase.RECONNECTING
    }

    private fun name() = tokens?.shown().orEmpty()

    // ------------------------------------------------------------------ what the screen calls

    /** At app start: a saved sign-in goes straight back to answering chat (unless the player paused it). */
    fun start() {
        val t = runCatching { store.load() }.getOrNull()
        tokens = t
        when {
            t == null -> setStatus(Status(Phase.SIGNED_OUT))
            !settings.answering -> setStatus(Status(Phase.PAUSED, t.shown()))
            else -> listen()
        }
    }

    /** Connect Twitch: asks Twitch for a code, shows it, and waits for the player to enter it. */
    fun signIn() {
        val g = restart()
        setStatus(Status(Phase.GETTING_CODE, note = "Asking Twitch for a code..."))
        background("twitch-sign-in") { signInFlow(g) }
    }

    fun cancelSignIn() {
        restart()
        setStatus(Status(Phase.SIGNED_OUT))
    }

    /** Sign out: stops answering, forgets the tokens on this phone, and asks Twitch to revoke them. */
    fun signOut() {
        val t = tokens ?: runCatching { store.load() }.getOrNull()
        restart()
        tokens = null
        runCatching { store.clear() }
        setStatus(Status(Phase.SIGNED_OUT, note = "Signed out."))
        if (t != null) background("twitch-revoke") { auth.revoke(t.access) }
    }

    /** The switch: answer chat, or stay signed in and quiet. */
    fun setAnswering(on: Boolean) {
        settings.setAnswering(on)
        if (tokens == null) return
        if (on) listen() else { restart(); setStatus(Status(Phase.PAUSED, name())) }
    }

    /** Stops every thread; the sign-in stays saved. */
    fun shutdown() { restart() }

    // ------------------------------------------------------------------ threads

    private fun alive(g: Int) = gen == g

    /** Ends whatever is running and returns the new generation. */
    private fun restart(): Int {
        synchronized(wake) { gen++; wake.notifyAll() }
        eventSub?.stop()
        eventSub = null
        return gen
    }

    /** Waits [ms] (paced), or less when [restart] wakes it. False when this generation has ended. */
    private fun nap(ms: Long, g: Int): Boolean {
        synchronized(wake) {
            if (!alive(g)) return false
            val p = pace(ms)
            if (p > 0) wake.wait(p)
        }
        return alive(g)
    }

    private fun napAny(ms: Long) {
        synchronized(wake) { val p = pace(ms); if (p > 0) wake.wait(p) }
    }

    private fun background(name: String, body: () -> Unit) {
        Thread({ runCatching(body) }, name).apply { isDaemon = true }.start()
    }

    private fun listen() {
        val g = restart()
        setStatus(Status(Phase.CONNECTING, name()))
        background("twitch-chat") { listenLoop(g) }
    }

    private fun signInFlow(g: Int) {
        val code = try { auth.start() } catch (e: IOException) {
            if (alive(g)) setStatus(Status(Phase.SIGNED_OUT, note = e.message ?: "Could not reach Twitch."))
            return
        }
        if (!alive(g)) return
        setStatus(Status(Phase.WAITING_FOR_CODE, code = code.userCode, link = code.verificationUri))
        when (val p = auth.await(code) { !alive(g) }) {
            is TwitchAuth.Poll.Done -> {
                val t = withUser(p.tokens) ?: run {
                    if (alive(g)) setStatus(Status(Phase.SIGNED_OUT, note = "Twitch signed you in but did not say who you are. Try again."))
                    return
                }
                if (!alive(g)) return
                if (!Twitch.SCOPES.all { it in t.scopes || t.scopes.isEmpty() }) {
                    setStatus(Status(Phase.SIGNED_OUT, note = "Twitch did not give permission to read and send chat. Try again."))
                    return
                }
                tokens = t
                runCatching { store.save(t) }
                settings.setAnswering(true)
                setStatus(Status(Phase.CONNECTING, t.shown()))
                listenLoop(g)
            }
            TwitchAuth.Poll.Expired -> if (alive(g)) setStatus(Status(Phase.SIGNED_OUT, note = "The code ran out of time. Tap Connect Twitch for a new one."))
            TwitchAuth.Poll.Denied -> if (alive(g)) setStatus(Status(Phase.SIGNED_OUT, note = "Twitch was told no. Tap Connect Twitch to try again."))
            is TwitchAuth.Poll.Failed -> if (alive(g)) setStatus(Status(Phase.SIGNED_OUT, note = p.why))
            else -> {}
        }
    }

    /** Fills in who the tokens belong to (GET /oauth2/validate), or null when Twitch will not say. */
    private fun withUser(t: TwitchTokens): TwitchTokens? = when (val v = auth.validate(t.access)) {
        is TwitchAuth.Validate.Ok -> {
            lastValidated = clock()
            TwitchTokens(t.access, t.refresh, t.expiresAt, v.userId, v.login, v.login, v.scopes.ifEmpty { t.scopes })
        }
        else -> null
    }

    /** Twitch no longer accepts the sign-in: forget it and ask for a new one. */
    private fun signedOutByTwitch(g: Int, note: String) {
        if (!alive(g)) return
        tokens = null
        runCatching { store.clear() }
        setStatus(Status(Phase.SIGNED_OUT, note = note))
    }

    internal fun backoff(failures: Int): Long {
        val base = 1000L shl (failures - 1).coerceIn(0, 6)
        return minOf(base, 60_000L) + (Math.random() * 500).toLong()
    }

    private fun listenLoop(g: Int) {
        var failures = 0
        while (alive(g)) {
            val t = freshTokens(g) ?: return
            if (!alive(g)) return
            val es = EventSub(connect = connect, subscribe = { subscribe(it) }, onChat = { onChat(it) }, tick = { hourly() })
            eventSub = es
            if (!alive(g)) { es.stop(); return }
            val started = clock()
            val end = es.run("${endpoints.eventSub}?keepalive_timeout_seconds=$keepaliveSeconds") {
                if (alive(g)) setStatus(Status(Phase.LISTENING, t.shown()))
            }
            if (!alive(g)) return
            when (end) {
                is EventSub.End.Revoked -> {
                    if (end.status == "version_removed") {
                        setStatus(Status(Phase.PAUSED, name(), note = "Twitch changed how chat is read. Update KaizoCore."))
                        return
                    }
                    signedOutByTwitch(g, "Twitch ended the connection to your account. Connect again to answer chat.")
                    return
                }
                is EventSub.End.NotSubscribed -> when (end.result) {
                    EventSub.Subscribed.Unauthorized -> { forceRefresh = true }
                    EventSub.Subscribed.Forbidden -> {
                        signedOutByTwitch(g, "Twitch needs you to connect again to read chat.")
                        return
                    }
                    else -> {}
                }
                EventSub.End.TickStopped -> { forceRefresh = true }
                else -> {}
            }
            if (clock() - started > 60_000) failures = 0
            failures++
            val wait = backoff(failures)
            setStatus(Status(Phase.RECONNECTING, name(), note = "Lost Twitch. Trying again in ${(wait + 999) / 1000} s."))
            if (!nap(wait, g)) return
        }
    }

    /**
     * Tokens fit to use: refreshed when they run out within five minutes or Twitch refused them, and their owner known.
     * Null when this generation ended or Twitch wants a new sign-in.
     */
    private fun freshTokens(g: Int): TwitchTokens? {
        var tries = 0
        while (alive(g)) {
            var t = tokens ?: runCatching { store.load() }.getOrNull()?.also { tokens = it }
            if (t == null) { signedOutByTwitch(g, "Connect Twitch to answer chat."); return null }
            if (forceRefresh || t.expiresAt - clock() < 5 * 60_000) {
                when (val r = refreshNow(t)) {
                    is TwitchAuth.Refresh.Ok -> { t = r.tokens }
                    TwitchAuth.Refresh.Rejected -> { signedOutByTwitch(g, "Twitch signed you out. Connect again to answer chat."); return null }
                    TwitchAuth.Refresh.Unreachable -> {
                        tries++
                        setStatus(Status(Phase.RECONNECTING, t.shown(), note = "Twitch is not answering. Trying again."))
                        if (!nap(backoff(tries), g)) return null
                        continue
                    }
                }
            }
            if (t.userId.isEmpty() || clock() - lastValidated > HOUR) {
                when (val v = auth.validate(t.access)) {
                    is TwitchAuth.Validate.Ok -> {
                        lastValidated = clock()
                        if (t.userId != v.userId || t.login != v.login) {
                            t = t.withUser(v.userId, v.login, v.login)
                            tokens = t
                            runCatching { store.save(t) }
                        }
                    }
                    TwitchAuth.Validate.Invalid -> {
                        // Refreshed already and still refused: the sign-in is gone.
                        if (++tries > 2) { signedOutByTwitch(g, "Twitch signed you out. Connect again to answer chat."); return null }
                        forceRefresh = true
                        continue
                    }
                    TwitchAuth.Validate.Unreachable -> if (t.userId.isEmpty()) {
                        tries++
                        setStatus(Status(Phase.RECONNECTING, t.shown(), note = "Twitch is not answering. Trying again."))
                        if (!nap(backoff(tries), g)) return null
                        continue
                    }
                }
            }
            return t
        }
        return null
    }

    /**
     * One refresh at a time. A public client's refresh token works once, so the new pair is saved before anything uses
     * it; a second caller that waited for the lock finds the tokens already replaced and uses those.
     */
    internal fun refreshNow(old: TwitchTokens): TwitchAuth.Refresh {
        synchronized(refreshLock) {
            val now = tokens
            if (now != null && now !== old && !forceRefresh) return TwitchAuth.Refresh.Ok(now)
            val r = auth.refresh(now ?: old)
            if (r is TwitchAuth.Refresh.Ok) {
                tokens = r.tokens
                forceRefresh = false
                runCatching { store.save(r.tokens) }
            }
            return r
        }
    }

    /** Twitch asks for a validate every hour; a token it refuses ends the session for a refresh. */
    private fun hourly(): Boolean {
        val t = tokens ?: return false
        if (clock() - lastValidated < HOUR) return true
        return when (auth.validate(t.access)) {
            is TwitchAuth.Validate.Ok -> { lastValidated = clock(); true }
            TwitchAuth.Validate.Invalid -> false
            TwitchAuth.Validate.Unreachable -> true
        }
    }

    private fun helixHeaders(t: TwitchTokens) = mapOf("Client-Id" to Twitch.CLIENT_ID, "Authorization" to "Bearer ${t.access}")

    /** POST /helix/eventsub/subscriptions: channel.chat.message for the streamer's own channel, read as themself. */
    private fun subscribe(sessionId: String): EventSub.Subscribed {
        val t = tokens ?: return EventSub.Subscribed.Unauthorized
        val body = Json.write(linkedMapOf(
            "type" to EventSub.CHAT_TYPE, "version" to "1",
            "condition" to linkedMapOf("broadcaster_user_id" to t.userId, "user_id" to t.userId),
            "transport" to linkedMapOf("method" to "websocket", "session_id" to sessionId)))
        val r = try {
            http.request("POST", "${endpoints.api}/helix/eventsub/subscriptions", helixHeaders(t), body, "application/json")
        } catch (e: IOException) {
            return EventSub.Subscribed.Failed(-1)
        }
        return when (r.code) {
            202, 409 -> EventSub.Subscribed.Ok
            401 -> EventSub.Subscribed.Unauthorized
            403 -> EventSub.Subscribed.Forbidden
            else -> EventSub.Subscribed.Failed(r.code)
        }
    }

    /** A chat line: answered when it is one of the commands, switched on, from this channel, and the gate allows it. */
    internal fun onChat(line: ChatLine) {
        if (!settings.answering) return
        val t = tokens ?: return
        if (line.broadcasterId.isNotEmpty() && line.broadcasterId != t.userId) return
        // Shared Chat: a line from another streamer's channel is theirs to answer, and a reply would go to all of them.
        if (line.sourceBroadcasterId != null && line.sourceBroadcasterId != t.userId) return
        synchronized(sentIds) { if (sentIds.containsKey(line.messageId)) return }
        val ask = ChatAsk.parse(line.text) ?: return
        if (!settings.enabled(ask.command)) return
        if (!gate.admit(ask.command)) return
        val reply = ChatAnswers.answer(ask, runCatching { state() }.getOrDefault("{}"), facts)
        runCatching { sendOn.execute { runCatching { send(reply, line.messageId) } } }
    }

    /** POST /helix/chat/messages as the streamer, as a reply to the asking line. One refresh and retry on a 401. */
    private fun send(text: String, replyTo: String) {
        repeat(2) { attempt ->
            val t = tokens ?: return
            val body = Json.write(linkedMapOf("broadcaster_id" to t.userId, "sender_id" to t.userId, "message" to text,
                "reply_parent_message_id" to replyTo))
            val r = try {
                http.request("POST", "${endpoints.api}/helix/chat/messages", helixHeaders(t), body, "application/json")
            } catch (e: IOException) { return }
            if (r.code == 401 && attempt == 0) {
                forceRefresh = true
                if (refreshNow(t) !is TwitchAuth.Refresh.Ok) return
                return@repeat
            }
            if (r.ok) {
                val first = JsonIn.asList(JsonIn.obj(r.body)?.get("data"))?.firstOrNull()?.let { JsonIn.asObj(it) }
                JsonIn.str(first, "message_id")?.let { id -> synchronized(sentIds) { sentIds[id] = true } }
                if (first?.get("is_sent") != false) sent += text
            }
            return
        }
    }

    companion object {
        const val HOUR = 3_600_000L
    }
}
