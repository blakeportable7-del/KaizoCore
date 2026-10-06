package com.ironmonone.app.stream.twitch

import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Stream Connect for Twitch (FUTURE-PROJECTS "Streamer features" item 5, 2026-10-05): the PC tracker's chat commands,
 * answered by the phone. This file holds what every part of it shares: the app's identity at Twitch, where Twitch is,
 * the plain HTTP the app already uses everywhere else (HttpURLConnection, as CrashReport and RetroAchievements), and the
 * smallest JSON reader that is correct, for Twitch's answers.
 */
object Twitch {
    /**
     * KaizoCore's Twitch application, registered by Blake as a public client, so it has no secret. It names the app,
     * not a channel: every streamer signs in with their own Twitch account and the app acts only for them.
     */
    const val CLIENT_ID = "gge79biuenmcj3fpd4xrq1m21pfd35"

    /**
     * The least the app can ask for (Twitch docs, checked 2026-10-05): channel.chat.message over an EventSub WebSocket
     * needs user:read:chat from the user reading chat, and Send Chat Message with a user token needs user:write:chat.
     * The streamer is both the reader and the sender, in their own channel, so neither user:bot nor channel:bot (those
     * are for an app token) nor any moderator scope is needed. Reading their user id and name (/oauth2/validate) needs
     * no scope at all.
     */
    val SCOPES = listOf("user:read:chat", "user:write:chat")

    /** Where Twitch is. Tests point all three at local fake servers. */
    data class Endpoints(
        val id: String = "https://id.twitch.tv",
        val api: String = "https://api.twitch.tv",
        val eventSub: String = "wss://eventsub.wss.twitch.tv/ws",
    )
}

/** One HTTP answer: the status code and at most [TwitchHttp.MAX_BODY] of the body. */
class HttpReply(val code: Int, val body: String) {
    val ok: Boolean get() = code in 200..299
    /** Never the body: an answer can carry a token, and a string like this one ends up in exception messages and logs. */
    override fun toString(): String = "HTTP $code"
}

/** The one thing the Twitch code asks of the network. Throws IOException when nothing came back. */
fun interface TwitchHttp {
    fun request(method: String, url: String, headers: Map<String, String>, body: String?, contentType: String?): HttpReply

    companion object {
        const val MAX_BODY = 256 * 1024

        /** application/x-www-form-urlencoded, for the id.twitch.tv calls. */
        fun form(vararg pairs: Pair<String, String>): String =
            pairs.joinToString("&") { (k, v) -> URLEncoder.encode(k, "UTF-8") + "=" + URLEncoder.encode(v, "UTF-8") }
    }
}

/** [TwitchHttp] over HttpURLConnection, the app's existing approach. Blocking: call it off the main thread. */
object UrlHttp : TwitchHttp {
    var connectTimeoutMs = 10_000
    var readTimeoutMs = 15_000

    override fun request(method: String, url: String, headers: Map<String, String>, body: String?, contentType: String?): HttpReply {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.requestMethod = method
            c.connectTimeout = connectTimeoutMs
            c.readTimeout = readTimeoutMs
            c.instanceFollowRedirects = false
            c.useCaches = false
            c.setRequestProperty("User-Agent", "KaizoCore")
            c.setRequestProperty("Accept", "application/json")
            for ((k, v) in headers) c.setRequestProperty(k, v)
            if (body != null) {
                c.doOutput = true
                c.setRequestProperty("Content-Type", contentType ?: "application/x-www-form-urlencoded")
                val bytes = body.toByteArray(Charsets.UTF_8)
                c.setFixedLengthStreamingMode(bytes.size)
                c.outputStream.use { it.write(bytes) }
            }
            val code = c.responseCode
            val stream: InputStream? = if (code >= 400) c.errorStream else runCatching { c.inputStream }.getOrNull()
            return HttpReply(code, stream?.use { readCapped(it) } ?: "")
        } finally {
            c.disconnect()
        }
    }

    private fun readCapped(input: InputStream): String {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(8192)
        while (out.size() < TwitchHttp.MAX_BODY) {
            val n = input.read(buf)
            if (n < 0) break
            out.write(buf, 0, minOf(n, TwitchHttp.MAX_BODY - out.size()))
        }
        return out.toString("UTF-8")
    }
}

/**
 * Reads JSON into maps, lists, strings, Longs, Doubles, booleans and null. org.json is stubbed out on the JVM test
 * classpath (Json.kt's note), and Twitch's answers are small, so this is the whole of it. Throws
 * IllegalArgumentException on anything that is not JSON; nesting is capped so a hostile body cannot overflow the stack.
 */
internal object JsonIn {
    private const val MAX_DEPTH = 48

    fun parse(text: String): Any? {
        val p = Parser(text)
        p.ws()
        val v = p.value(0)
        p.ws()
        if (p.i != text.length) throw IllegalArgumentException("trailing characters at ${p.i}")
        return v
    }

    /** [parse] for an object, or null for anything else, including text that is not JSON. */
    fun obj(text: String): Map<String, Any?>? = runCatching { parse(text) }.getOrNull()?.let { asObj(it) }

    @Suppress("UNCHECKED_CAST")
    fun asObj(v: Any?): Map<String, Any?>? = v as? Map<String, Any?>

    fun asList(v: Any?): List<Any?>? = v as? List<Any?>

    fun str(m: Map<String, Any?>?, k: String): String? = m?.get(k) as? String

    fun long(m: Map<String, Any?>?, k: String): Long? = when (val v = m?.get(k)) {
        is Long -> v
        is Double -> v.toLong()
        is String -> v.toLongOrNull()
        else -> null
    }

    fun child(m: Map<String, Any?>?, vararg path: String): Map<String, Any?>? {
        var cur = m
        for (k in path) cur = asObj(cur?.get(k)) ?: return null
        return cur
    }

    private class Parser(val s: String) {
        var i = 0

        fun ws() { while (i < s.length && (s[i] == ' ' || s[i] == '\n' || s[i] == '\r' || s[i] == '\t')) i++ }

        private fun fail(what: String): Nothing = throw IllegalArgumentException("$what at $i")

        fun value(depth: Int): Any? {
            if (depth > MAX_DEPTH) fail("nested too deep")
            if (i >= s.length) fail("unexpected end")
            return when (s[i]) {
                '{' -> obj(depth)
                '[' -> list(depth)
                '"' -> string()
                't' -> word("true", true)
                'f' -> word("false", false)
                'n' -> word("null", null)
                else -> number()
            }
        }

        private fun word(w: String, v: Any?): Any? {
            if (!s.startsWith(w, i)) fail("bad literal")
            i += w.length
            return v
        }

        private fun obj(depth: Int): Map<String, Any?> {
            i++
            val out = LinkedHashMap<String, Any?>()
            ws()
            if (i < s.length && s[i] == '}') { i++; return out }
            while (true) {
                ws()
                if (i >= s.length || s[i] != '"') fail("expected a key")
                val k = string()
                ws()
                if (i >= s.length || s[i] != ':') fail("expected ':'")
                i++
                ws()
                out[k] = value(depth + 1)
                ws()
                if (i >= s.length) fail("unexpected end")
                when (s[i]) {
                    ',' -> i++
                    '}' -> { i++; return out }
                    else -> fail("expected ',' or '}'")
                }
            }
        }

        private fun list(depth: Int): List<Any?> {
            i++
            val out = ArrayList<Any?>()
            ws()
            if (i < s.length && s[i] == ']') { i++; return out }
            while (true) {
                ws()
                out += value(depth + 1)
                ws()
                if (i >= s.length) fail("unexpected end")
                when (s[i]) {
                    ',' -> i++
                    ']' -> { i++; return out }
                    else -> fail("expected ',' or ']'")
                }
            }
        }

        fun string(): String {
            i++
            val sb = StringBuilder()
            while (true) {
                if (i >= s.length) fail("unterminated string")
                val c = s[i++]
                when {
                    c == '"' -> return sb.toString()
                    c == '\\' -> {
                        if (i >= s.length) fail("unterminated escape")
                        when (val e = s[i++]) {
                            '"' -> sb.append('"'); '\\' -> sb.append('\\'); '/' -> sb.append('/')
                            'b' -> sb.append('\b'); 'f' -> sb.append('\u000C'); 'n' -> sb.append('\n')
                            'r' -> sb.append('\r'); 't' -> sb.append('\t')
                            'u' -> {
                                if (i + 4 > s.length) fail("short unicode escape")
                                sb.append(s.substring(i, i + 4).toIntOrNull(16)?.toChar() ?: fail("bad unicode escape"))
                                i += 4
                            }
                            else -> fail("bad escape '$e'")
                        }
                    }
                    c < ' ' -> fail("control character in string")
                    else -> sb.append(c)
                }
            }
        }

        private fun number(): Any {
            val start = i
            if (i < s.length && s[i] == '-') i++
            while (i < s.length && (s[i].isDigit() || s[i] == '.' || s[i] == 'e' || s[i] == 'E' || s[i] == '+' || s[i] == '-')) i++
            val t = s.substring(start, i)
            if (t.isEmpty() || t == "-") fail("expected a value")
            val whole = t.none { it == '.' || it == 'e' || it == 'E' }
            return (if (whole) t.toLongOrNull() else null) ?: t.toDoubleOrNull() ?: fail("bad number '$t'")
        }
    }
}

/** A failure the player should read about; [message] is written for them and never carries a token or a body. */
class TwitchTrouble(message: String) : IOException(message)
