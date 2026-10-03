package com.ironmonone.app

import android.content.Context
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.time.LocalDate

/**
 * A crash report that goes to Blake, only when the player says so (2026-09-29).
 *
 * Before this a report left the phone through the share sheet, by hand, and most
 * players never did it, so a crash in the field was never seen. Now Send report
 * posts it to the site, which emails it, and "Always send" turns that on for good.
 * It is opt-in: with the switch off the app asks first, every time.
 *
 * What goes is what a fix needs and nothing that names the player's files: the
 * app and Android versions, the phone model, the family of the game that was open, and the report
 * text, run through [scrub] first. Nothing here touches the network except
 * [send], which is blocking and belongs on Dispatchers.IO.
 */
object CrashReport {

    /** The site's crash endpoint: a JSON body of at most [MAX_BODY_BYTES], answered 200 {"ok":true} once it is emailed. */
    const val ENDPOINT = "https://willowcreek.group/api/kaizocore-crash"

    /** The server refuses a bigger body (413), so a long report is cut to fit rather than lost. */
    const val MAX_BODY_BYTES = 60_000

    /** Reports that may go in one local calendar day, automatic or by hand: a crash loop must not fill Blake's inbox. */
    const val MAX_PER_DAY = 3

    /** Device-local state, in filesDir and not under prep/: a restore onto another phone must not carry the switch or the count. */
    const val STATE_FILE = "crash-sent.txt"

    private const val TRIMMED = "[trimmed]"

    /** A line longer than this is cut when a report has to be trimmed, so one runaway line cannot use the whole budget. */
    private const val LINE_CAP = 2_000

    /** The four short fields are Build constants; capped anyway so the body's size cannot depend on them. */
    private const val FIELD_CAP = 200

    /** How much of the site's answer is read. `{"ok":true}` is 11 bytes. */
    private const val ANSWER_MAX = 4_096

    // ------------------------------------------------------------ scrub

    private const val EXTENSIONS = "gba|gbc|gb|nds|3ds|cia|cci|zip|7z|sav|srm|dsv|state|st[0-9]|ips|ups|bps|xdelta"

    /**
     * Where the player's own files live. From the first of these to the end of the line is theirs:
     * an exception message puts the path a failed open was given there, and a path with spaces in
     * it ("Pokemon - FireRed Version (USA, Europe) (Rev 1).gba") must not leak its tail. A stack
     * frame never holds one, so nothing a fix needs goes with it. /data/app/ is not here: that
     * is the app's own libraries, and a native frame is the part of a report that matters most.
     */
    private val PATH = Regex("(?i)(?:/storage/|/sdcard|/mnt/|/data/user/|/data/data/|/data/media/|content://|file://).*")

    /** A quoted name that ends in a game or save extension, spaces and all. */
    private val QUOTED = Regex("(?i)\"[^\"\\r\\n]*\\.(?:$EXTENSIONS)\"|'[^'\\r\\n]*\\.(?:$EXTENSIONS)'")

    /**
     * A bare name that ends in one. It has to END there: the lookahead wants a space, a quote,
     * a closing bracket or a comma, colon or semicolon, the end of a sentence or the end of the text
     * next, so a package such as com.ironmonone.tracker.nds in the middle of a stack frame is
     * left alone. It starts only at the start of a token (the lookbehind), so a long run with no
     * spaces in it is scanned once and not once per character.
     */
    private val NAMED = Regex("(?i)(?<![^\\s\"'<>])[^\\s\"'<>]*\\.(?:$EXTENSIONS)(?=[\\s\"'),;:\\]}>]|\\.(?:\\s|\\z)|\\z)")

    /**
     * [text] with the player's paths and file names taken out. The promise on every screen that
     * offers a report is "never a ROM, a save or a file name", and this is what keeps it: paths
     * are replaced with `<path>`, names with `<file>`. Library frames (.so), signal names,
     * Cause lines and stack frames come through untouched.
     */
    fun scrub(text: String): String = text
        .replace(PATH, "<path>")
        .replace(QUOTED, "<file>")
        .replace(NAMED, "<file>")

    // ---------------------------------------------------------- payload

    /** A JSON string literal: quote, backslash and everything below 0x20 escaped, and a stray half of a surrogate pair made U+FFFD. */
    internal fun jsonString(s: String): String = buildString(s.length + 2) {
        append('"')
        var i = 0
        while (i < s.length) {
            val c = s[i]
            when {
                c == '"' -> append("\\\"")
                c == '\\' -> append("\\\\")
                c == '\n' -> append("\\n")
                c == '\r' -> append("\\r")
                c == '\t' -> append("\\t")
                c.code < 0x20 -> append("\\u").append(c.code.toString(16).padStart(4, '0'))
                Character.isHighSurrogate(c) && i + 1 < s.length && Character.isLowSurrogate(s[i + 1]) -> {
                    append(c); append(s[i + 1]); i++
                }
                Character.isSurrogate(c) -> append('\uFFFD')
                else -> append(c)
            }
            i++
        }
        append('"')
    }

    /**
     * The request body: `{"v":1,"app":...,"device":...,"android":...,"family":...,"report":...}`.
     * [report] is scrubbed here, so no caller can forget it. The whole body is at most
     * [MAX_BODY_BYTES] of UTF-8. A report that would not fit keeps whole lines from the top, so the
     * header and the newest crash (the reports are written newest first) survive, and ends in a
     * line reading `[trimmed]`. [family] null is JSON null.
     */
    fun payload(app: String, device: String, android: String, family: String?, report: String): String {
        fun body(r: String): String = buildString {
            append("{\"v\":1,\"app\":").append(jsonString(app.take(FIELD_CAP)))
            append(",\"device\":").append(jsonString(device.take(FIELD_CAP)))
            append(",\"android\":").append(jsonString(android.take(FIELD_CAP)))
            append(",\"family\":").append(if (family == null) "null" else jsonString(family.take(FIELD_CAP)))
            append(",\"report\":").append(jsonString(r))
            append('}')
        }
        fun fits(b: String) = b.toByteArray(Charsets.UTF_8).size <= MAX_BODY_BYTES

        val clean = scrub(report)
        body(clean).let { if (fits(it)) return it }

        // Every character is at least one byte, so nothing past the first MAX_BODY_BYTES characters can fit;
        // the last line of that stretch may be half a line and is dropped.
        val cut = clean.length > MAX_BODY_BYTES
        var lines = clean.take(MAX_BODY_BYTES).split('\n').map { capLine(it) }
        if (cut) lines = lines.dropLast(1)
        fun with(k: Int) = body((lines.take(k).dropLastWhile { it.isBlank() } + TRIMMED).joinToString("\n"))
        // The most lines that fit, found by halving: k = 0 is the marker alone, and a longer report is never smaller.
        var lo = 0
        var hi = lines.size
        while (lo < hi) {
            val mid = (lo + hi + 1) / 2
            if (fits(with(mid))) lo = mid else hi = mid - 1
        }
        return with(lo)
    }

    private fun capLine(line: String): String {
        if (line.length <= LINE_CAP) return line
        val end = if (Character.isHighSurrogate(line[LINE_CAP - 1])) LINE_CAP - 1 else LINE_CAP
        return line.substring(0, end) + "..."
    }

    // ------------------------------------------------------------- send

    /** How a send ended. Only [Sent] means the report reached Blake's inbox. */
    sealed class SendResult {
        /** The site answered 200 {"ok":true}: it was emailed. */
        object Sent : SendResult()
        /** The site answered and did not send it (200 with ok false, or a refusal). Trying again would not change that. */
        data class NotSent(val reason: String) : SendResult()
        /** No answer that means yes or no: no connection, a timeout, the rate limit, a server error. A later try may work. */
        data class Failed(val reason: String) : SendResult()
    }

    private val OK_TRUE = Regex("\"ok\"\\s*:\\s*true")
    private val OK_FALSE = Regex("\"ok\"\\s*:\\s*false")
    private val REASON = Regex("\"reason\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"")

    /** What the site's answer means. Only 200 with `"ok":true` is a yes. */
    internal fun answer(status: Int, text: String): SendResult = when (status) {
        200 -> when {
            OK_TRUE.containsMatchIn(text) -> SendResult.Sent
            OK_FALSE.containsMatchIn(text) ->
                SendResult.NotSent(REASON.find(text)?.groupValues?.get(1)?.take(200)?.ifBlank { null } ?: "not sent")
            else -> SendResult.Failed("unexpected answer")
        }
        429 -> SendResult.Failed("too many reports just now")
        400, 405, 413 -> SendResult.NotSent("refused ($status)")
        else -> SendResult.Failed("HTTP $status")
    }

    /**
     * POSTs [body] and reads how it went. Blocking, so callers use `withContext(Dispatchers.IO)`.
     * It never throws: every failure is a [SendResult]. The answer is read only as far as
     * [ANSWER_MAX] bytes and only for [timeoutMs] (twice that at the outside, connecting and then
     * waiting). Redirects are not followed: the report goes to the one address it was made for.
     */
    fun send(body: String, url: String = ENDPOINT, timeoutMs: Int = 8000): SendResult {
        var open: HttpURLConnection? = null
        return try {
            val deadline = System.nanoTime() + 2L * timeoutMs * 1_000_000L
            val c = URL(url).openConnection() as HttpURLConnection
            open = c
            c.instanceFollowRedirects = false
            c.useCaches = false
            c.connectTimeout = timeoutMs
            c.readTimeout = timeoutMs
            c.requestMethod = "POST"
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            c.setRequestProperty("User-Agent", "KaizoCore")
            val bytes = body.toByteArray(Charsets.UTF_8)
            c.setFixedLengthStreamingMode(bytes.size)
            c.outputStream.use { it.write(bytes) }
            val status = c.responseCode
            val text = (if (status < 400) c.inputStream else c.errorStream)?.use { readUpTo(it, ANSWER_MAX, deadline) }.orEmpty()
            answer(status, text)
        } catch (e: SocketTimeoutException) {
            SendResult.Failed("timed out")
        } catch (e: java.io.IOException) {
            SendResult.Failed("no connection (${e.javaClass.simpleName})")
        } catch (e: Exception) {
            SendResult.Failed(e.javaClass.simpleName)
        } finally {
            runCatching { open?.disconnect() }
        }
    }

    private fun readUpTo(input: InputStream, max: Int, deadline: Long): String {
        val buf = ByteArray(max)
        var n = 0
        // nanoTime differences, never nanoTime values, are what may be compared.
        while (n < max && deadline - System.nanoTime() > 0) {
            val r = input.read(buf, n, max - n)
            if (r < 0) break
            n += r
        }
        return String(buf, 0, n, Charsets.UTF_8)
    }

    // ------------------------------------------------------------ state

    /**
     * What is remembered between launches. [auto] is the "Always send" switch and is OFF until the
     * player turns it on. [lastSent] is the stamp of the newest crash already delivered, so the
     * same report is never sent twice. [day] and [sentToday] count deliveries on one local
     * calendar day.
     */
    data class State(
        val auto: Boolean = false,
        val lastSent: String? = null,
        val day: String? = null,
        val sentToday: Int = 0,
    )

    /** The file's text: one `key=value` per line. */
    fun formatState(s: State): String = buildString {
        appendLine("auto=${if (s.auto) 1 else 0}")
        s.lastSent?.let { appendLine("last=$it") }
        s.day?.let { appendLine("day=$it"); appendLine("count=${s.sentToday}") }
    }

    /** The state in [text]. A missing, empty or garbled file is the default: nothing sent, auto off. */
    fun parseState(text: String?): State {
        var auto = false
        var last: String? = null
        var day: String? = null
        var count = 0
        for (line in text.orEmpty().lineSequence()) {
            val eq = line.indexOf('=')
            if (eq <= 0) continue
            val v = line.substring(eq + 1).trim()
            when (line.substring(0, eq).trim()) {
                "auto" -> auto = v == "1"
                "last" -> last = v.ifEmpty { null }
                "day" -> day = v.ifEmpty { null }
                "count" -> count = v.toIntOrNull()?.coerceIn(0, 1_000) ?: 0
            }
        }
        return State(auto, last, day, count)
    }

    /** Deliveries already made on [today]: yesterday's count is not today's. */
    fun sentOn(s: State, today: String): Int = if (s.day == today) s.sentToday else 0

    enum class Verdict { GO, SENT_ALREADY, DAY_FULL }

    /**
     * May the report whose newest crash is [stamp] go now? Not if that crash was already delivered
     * (equal stamps: a later crash has a later one), and not once [MAX_PER_DAY] have gone on
     * [today]. Both are refused for a hand tap as much as for an automatic send.
     */
    fun mayGo(s: State, stamp: String?, today: String): Verdict = when {
        stamp != null && stamp == s.lastSent -> Verdict.SENT_ALREADY
        sentOn(s, today) >= MAX_PER_DAY -> Verdict.DAY_FULL
        else -> Verdict.GO
    }

    /** The state after a delivery of the report stamped [stamp] on [today]. */
    fun afterSent(s: State, stamp: String?, today: String): State =
        s.copy(lastSent = stamp ?: s.lastSent, day = today, sentToday = sentOn(s, today) + 1)

    /** The local calendar date, as the day counter keeps it. */
    fun localDay(): String = LocalDate.now().toString()

    private val stateLock = Any()

    /** Held for a whole send, so a launch's automatic send and a tap on INFO cannot both deliver one report. */
    private val sendLock = Any()

    fun load(dir: File): State = runCatching {
        parseState(File(dir, STATE_FILE).takeIf { it.isFile }?.readText())
    }.getOrDefault(State())

    fun save(dir: File, s: State): Boolean = SafeWrite.text(File(dir, STATE_FILE), formatState(s))

    /** Reads, changes and writes the state as one step, so a switch flipped while a send is finishing is not undone by it. */
    fun update(dir: File, change: (State) -> State): State = synchronized(stateLock) {
        change(load(dir)).also { save(dir, it) }
    }

    fun setAuto(dir: File, on: Boolean): State = update(dir) { it.copy(auto = on) }

    /** Should this launch send [report] itself, with no dialog: the switch is on and the report may go. */
    fun mayAutoSend(dir: File, report: String, today: String = localDay()): Boolean {
        val s = load(dir)
        return s.auto && mayGo(s, CrashLog.newestStamp(report), today) == Verdict.GO
    }

    // ---------------------------------------------------------- deliver

    /** What the site is told besides the report itself. */
    data class Meta(val app: String, val device: String, val android: String, val family: String?)

    /**
     * The whole send, blocking: refuses what already went and a report past the day's limit, sends,
     * and records a delivery. It records nothing on a failure, so a try that failed does not use
     * up one of the day's three or block the next. A report already delivered answers [SendResult.Sent]
     * without sending again.
     */
    fun deliver(
        dir: File,
        meta: Meta,
        report: String,
        today: String = localDay(),
        url: String = ENDPOINT,
        timeoutMs: Int = 8000,
    ): SendResult = synchronized(sendLock) {
        val stamp = CrashLog.newestStamp(report)
        when (mayGo(load(dir), stamp, today)) {
            Verdict.SENT_ALREADY -> return SendResult.Sent
            Verdict.DAY_FULL -> return SendResult.NotSent("three reports already went today")
            Verdict.GO -> Unit
        }
        val result = send(payload(meta.app, meta.device, meta.android, meta.family, report), url, timeoutMs)
        if (result === SendResult.Sent) update(dir) { afterSent(it, stamp, today) }
        result
    }

    /** The phone's half of a send: app version, device, Android and the game that was open. Reads files, so not on the main thread. */
    fun meta(context: Context): Meta {
        val d = Feedback.device(context)
        return Meta(BuildConfigish.version(context), d.model, d.android, keptGame(context.filesDir))
    }

    // ------------------------------------------------------------- game

    /** The game that was open at the newest crash, kept beside the report when it is collected (CrashLog.collect). */
    const val GAME_FILE = "crash-game.txt"

    /**
     * The game the report names (rc32 audit P3 #25): the one Play had open when the app died. Play's marker
     * (prep/playing.txt, CrashResume) names it and is still there when the report is collected at the next launch;
     * [session] is the game Play opens, the same one while the marker names it. Its family, else its console for a
     * game the tracker does not read; null when no game was open. It was the last Kaizo run's family, whatever crashed.
     */
    internal fun gameOf(markerSession: String?, session: GameSession?): String? {
        if (markerSession == null || session == null || session.id != markerSession) return null
        return session.kind?.family ?: when (session.platform) {
            com.ironmonone.core.Platform.GBA -> "GBA"
            com.ironmonone.core.Platform.NDS -> "DS"
            com.ironmonone.core.Platform.GBC -> "Game Boy"
        }
    }

    /** [gameOf] for this phone. Reads files: at collection, off the main thread. */
    internal fun gameAtExit(store: PrepStore): String? =
        CrashResume.parse(store.playMarker)?.let { left -> gameOf(left.sessionId, store.session()) }

    /** Keeps [game] for the report just collected; none when no game was open. */
    internal fun keepGame(dir: File, game: String?) {
        runCatching { if (game == null) File(dir, GAME_FILE).delete() else SafeWrite.text(File(dir, GAME_FILE), game) }
    }

    internal fun keptGame(dir: File): String? =
        runCatching { File(dir, GAME_FILE).takeIf { it.isFile }?.readText()?.trim()?.ifEmpty { null } }.getOrNull()

    /** From the phone: send [report]. Blocking; call it on Dispatchers.IO. */
    fun submit(context: Context, report: String): SendResult = deliver(context.filesDir, meta(context), report)
}
