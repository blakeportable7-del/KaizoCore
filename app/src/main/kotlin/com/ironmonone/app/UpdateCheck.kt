package com.ironmonone.app

import android.content.Context
import android.os.Build
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The in-app update check (2026-09-29). The site publishes one small file that names the newest
 * build, [URL]. The app reads it, compares the version code with its own and, when the site has
 * a newer one, offers to open the APK in the browser. It never downloads or installs anything
 * itself: the browser downloads, the player opens the file, Android asks its own questions.
 *
 * What is trusted, and why so little: the link the app opens comes out of a file fetched over
 * the network, so [parse] refuses an APK link that is not on willowcreek.group/kaizocore/ and a
 * notes or page link that is not on our site or the project's own repository. A wrong file can
 * then offer a build from our own site and nothing else.
 *
 * What it sends: one GET with User-Agent "KaizoCore" (Android's default names the phone model),
 * no cookie, no id, no query. The card on INFO promises exactly that, and a test holds it.
 *
 * Everything that touches the network runs on Dispatchers.IO, and every failure comes back as
 * null or false. A phone with no signal, a captive portal or a bad file must never reach the UI.
 */
object UpdateCheck {

    /** The one file the site serves: flat JSON, unknown keys ignored, key order not guaranteed. */
    const val URL = "https://willowcreek.group/kaizocore/latest.json"

    /** The APK has to live under here. The app opens whatever link it is given, so only our own site is trusted. */
    const val APK_PREFIX = "https://willowcreek.group/kaizocore/"
    private const val SITE_PREFIX = "https://willowcreek.group/"
    private const val REPO_PREFIX = "https://github.com/blakeportable7-del/KaizoCore/"
    private const val APP = "KaizoCore"

    /** Device-local state, directly in filesDir. Not in the backup: another phone's snooze means nothing here. */
    const val STATE_FILE = "update-check.txt"

    /** The automatic check asks at most this often. */
    const val CHECK_EVERY_MS = 20L * 60 * 60 * 1000
    /** After Download, so a player who is mid-install is not asked again at the next launch. */
    const val DOWNLOAD_SNOOZE_MS = 24L * 60 * 60 * 1000
    /** After Later. */
    const val LATER_SNOOZE_MS = 3L * 24 * 60 * 60 * 1000

    /** A real manifest is about 500 bytes. Anything past this is not one, and is not read further. */
    const val MAX_BODY = 16 * 1024

    private val VERSION = Regex("[A-Za-z0-9.+-]{1,40}")
    private val SHA256 = Regex("[0-9a-f]{64}")

    data class Manifest(
        val versionCode: Long,
        val version: String,
        val apk: String,
        val bytes: Long,
        val sha256: String,
        val notes: String?,
        val page: String?,
    )

    // ---------------------------------------------------------------- copy
    // What the player reads lives here, not in the composables, so a test can hold every line to
    // the house rules (plain, no em dashes, nothing about how the work is made).

    const val CARD_TITLE = "Updates"
    const val TOGGLE_LABEL = "Check for a new build once a day"
    const val CARD_HINT = "It asks willowcreek.group for one small file that names the newest build, and sends nothing " +
        "else: no account, no phone details, nothing about your games."
    const val CHECKING = "Checking..."
    const val CURRENT = "You have the newest build."
    const val UNREACHABLE = "Could not reach willowcreek.group. Try again later."
    /** Where a tapped link found nothing to open it: a link with no browser crashed the app once (audit, 2026-09-27). */
    const val NO_BROWSER = "No browser on this phone."

    fun promptTitle(m: Manifest): String = "KaizoCore ${m.version} is out"

    fun promptBody(installedName: String, m: Manifest): String =
        "You have $installedName. The new build is ${megabytes(m.bytes)} MB from willowcreek.group. " +
            "Update downloads it, checks it and asks Android to install it over this one. " +
            "Your saves, runs and settings stay."

    // One tap to update (UpdateInstall, 2026-09-29): the buttons and the lines under them.
    const val UPDATE = "Update"
    const val WHATS_NEW = "What's new"
    const val LATER = "Later"
    const val CANCEL = "Cancel"
    const val CLOSE = "Close"
    const val ALLOW = "Allow"
    const val TRY_AGAIN = "Try again"
    const val IN_BROWSER = "Download in the browser"
    const val NEEDS_ALLOW = "Android asks once before KaizoCore may install its own updates. Tap Allow, switch on " +
        "Allow from this source, then come back here."
    /** While the build is copied into Android's installer: no Close then, or Android's question had no one to come back to (rc32 audit P2 #105). */
    const val HANDING = "Handing it to Android..."
    const val INSTALLING = "Android is asking to install it. KaizoCore closes while it updates; open it again when it finishes."
    const val DECLINED = "Update canceled."

    /** Whole megabytes done, rounded down, so the count never claims more than has arrived. */
    fun downloading(done: Long, total: Long): String = "Downloading... ${done / 1_000_000} of ${megabytes(total)} MB"

    fun failText(why: UpdateInstall.Why, bytes: Long): String = when (why) {
        // The room for the download and for Android's own copy of it (rc32 audit P2 #103).
        UpdateInstall.Why.NO_SPACE -> "Not enough free space: the update needs about ${megabytes(UpdateInstall.needBytes(bytes))} MB."
        UpdateInstall.Why.SIZE, UpdateInstall.Why.CHECKSUM ->
            "The download did not match the build the site names, so it was not installed. Try again."
        UpdateInstall.Why.NETWORK -> "The download stopped. Check the connection and try again."
        // An answer from the site is not a connection problem (rc32 audit P3 #76).
        UpdateInstall.Why.HTTP -> "The site did not send the update. Try again later."
        UpdateInstall.Why.GONE -> "That build is no longer on willowcreek.group. Try again later."
        UpdateInstall.Why.CANCELLED -> "Download canceled."
    }

    /** Android's own words, when it gave any. */
    fun installFailed(message: String): String =
        "Android did not install it." + if (message.isBlank()) "" else " It said: $message"

    fun cardVersion(installedName: String): String = "This is KaizoCore $installedName."

    fun cardFound(m: Manifest): String = "${m.version} is out (${megabytes(m.bytes)} MB)."

    /** Whole decimal megabytes for the prompt (bytes over a million, rounded), never below 1. */
    fun megabytes(bytes: Long): Int = Math.round(bytes / 1e6).coerceIn(1L, 99_999L).toInt()

    // --------------------------------------------------------------- parse
    /**
     * The manifest in [json], or null when it is not exactly what the site publishes. Every
     * field the app acts on is checked; a link that is off our own site refuses the whole file
     * (apk) or is dropped while the rest stands (notes, page).
     */
    fun parse(json: String): Manifest? {
        val m = readFlat(json) ?: return null
        if (m["app"] != APP) return null
        val code = (m["versionCode"] as? Long)?.takeIf { it > 0 } ?: return null
        val version = (m["version"] as? String)?.takeIf { VERSION.matches(it) } ?: return null
        val apk = (m["apk"] as? String)?.takeIf { ours(it, APK_PREFIX) } ?: return null
        val bytes = (m["bytes"] as? Long)?.takeIf { it > 0 } ?: return null
        val sha = (m["sha256"] as? String)?.takeIf { SHA256.matches(it) } ?: return null
        val notes = (m["notes"] as? String)?.takeIf { ours(it, REPO_PREFIX, SITE_PREFIX) }
        val page = (m["page"] as? String)?.takeIf { ours(it, SITE_PREFIX) }
        return Manifest(code, version, apk, bytes, sha, notes, page)
    }

    /**
     * True for a link that starts with one of [prefixes] and is a single clean token: no space,
     * control character or backslash for a browser to read differently from us. The prefix ends
     * at a slash past the host, so a look-alike host or a user-info trick cannot pass it.
     */
    private fun ours(url: String, vararg prefixes: String): Boolean =
        prefixes.any { url.startsWith(it) } && url.none { it <= ' ' || it == '\u007f' || it == '\\' }

    /** A version code newer than the installed one. Older or equal is not an update. */
    fun isNewer(m: Manifest, installedCode: Long): Boolean = m.versionCode > installedCode

    /**
     * The build to offer instead of [held] once the site no longer has it (Why.GONE): [fresh], latest.json asked again,
     * when it names a newer one. A prompt held for the process outlived a release, its old APK answered with a redirect,
     * and the player was told to check the connection for good (rc32 audit P3 #76). Null when there is none: a release
     * pulled back, or no answer.
     */
    fun replacement(held: Manifest, fresh: Manifest?): Manifest? = fresh?.takeIf { it.versionCode > held.versionCode }

    /**
     * Whether the automatic check should ask now: on, and never asked or 20 hours ago. A last
     * check in the future means the clock moved back; waiting for it to catch up could take
     * years, so that counts as due (the next write puts a sane time back).
     */
    fun due(enabled: Boolean, lastCheckMs: Long, nowMs: Long): Boolean =
        enabled && (lastCheckMs <= 0 || lastCheckMs > nowMs || nowMs - lastCheckMs >= CHECK_EVERY_MS)

    /**
     * Whether to interrupt the player: newer, and not this very version snoozed and still inside
     * the snooze. A version newer than the snoozed one prompts at once; a snooze is about one
     * build, not about updates in general.
     */
    fun shouldPrompt(m: Manifest, installedCode: Long, snoozedCode: Long, snoozedUntilMs: Long, nowMs: Long): Boolean =
        isNewer(m, installedCode) && !(m.versionCode == snoozedCode && nowMs < snoozedUntilMs)

    // --------------------------------------------------------------- state
    /** Turned on until the player says otherwise: the card promises what it sends. */
    data class State(
        val enabled: Boolean = true,
        val lastCheckMs: Long = 0L,
        val snoozedCode: Long = 0L,
        val snoozedUntilMs: Long = 0L,
    )

    /** key=value lines. Unknown or broken lines are ignored, so a bad file is a default one. */
    fun parseState(text: String): State {
        val kv = HashMap<String, String>()
        for (line in text.lineSequence()) {
            val eq = line.indexOf('=')
            if (eq > 0) kv[line.substring(0, eq).trim()] = line.substring(eq + 1).trim()
        }
        return State(
            // Only an explicit false turns it off; a damaged line must not switch the player's choice back the other way.
            enabled = !kv["enabled"].equals("false", ignoreCase = true),
            lastCheckMs = kv["lastCheck"]?.toLongOrNull() ?: 0L,
            snoozedCode = kv["snoozedCode"]?.toLongOrNull() ?: 0L,
            snoozedUntilMs = kv["snoozedUntil"]?.toLongOrNull() ?: 0L,
        )
    }

    fun formatState(s: State): String =
        "enabled=${s.enabled}\nlastCheck=${s.lastCheckMs}\nsnoozedCode=${s.snoozedCode}\nsnoozedUntil=${s.snoozedUntilMs}\n"

    fun stateFile(filesDir: File): File = File(filesDir, STATE_FILE)

    fun loadState(filesDir: File): State =
        runCatching { parseState(stateFile(filesDir).readText()) }.getOrDefault(State())

    /** False when the write failed; the old file is then untouched (SafeWrite). */
    fun saveState(filesDir: File, s: State): Boolean = SafeWrite.text(stateFile(filesDir), formatState(s))

    private val lock = Any()

    /**
     * Read, change, write, under one lock. The launch check, the toggle and a snooze can each
     * write from a different thread; writing back a copy read earlier would undo whichever
     * finished first (a switch turned off while the check was on the wire came back on).
     */
    private fun edit(filesDir: File, change: (State) -> State): State = synchronized(lock) {
        val next = change(loadState(filesDir))
        saveState(filesDir, next)
        next
    }

    fun setEnabled(filesDir: File, on: Boolean) { edit(filesDir) { it.copy(enabled = on) } }

    fun snooze(filesDir: File, versionCode: Long, forMs: Long, nowMs: Long) {
        edit(filesDir) { it.copy(snoozedCode = versionCode, snoozedUntilMs = nowMs + forMs) }
    }

    // --------------------------------------------------------------- fetch
    /**
     * Asks the site for the manifest. Blocking: callers wrap it in withContext(Dispatchers.IO).
     * Null for anything but a whole, valid manifest in a 200 answer, including every exception.
     *
     * A redirect is not followed. The card says the question goes to willowcreek.group and
     * nowhere else, and a followed redirect could send it to another host. The user agent is set
     * because the default one names the phone's model.
     */
    fun fetch(url: String = URL, timeoutMs: Int = 6000): Manifest? {
        var conn: HttpURLConnection? = null
        return try {
            conn = java.net.URL(url).openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = timeoutMs
            conn.readTimeout = timeoutMs
            conn.useCaches = false
            conn.instanceFollowRedirects = false
            conn.setRequestProperty("User-Agent", APP)
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty("Cache-Control", "no-cache")
            if (conn.responseCode != 200) return null
            val body = conn.inputStream.use { readBounded(it, MAX_BODY, timeoutMs) } ?: return null
            parse(String(body, Charsets.UTF_8))
        } catch (e: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }

    /**
     * Reads the whole stream, or null when it runs past [max] bytes or takes longer than
     * [budgetMs]. The read timeout only bounds each read, so a server that trickles a byte at a
     * time could otherwise hold the check for as long as it liked.
     */
    private fun readBounded(input: InputStream, max: Int, budgetMs: Int): ByteArray? {
        val deadline = System.nanoTime() + budgetMs * 1_000_000L
        val out = ByteArrayOutputStream()
        val buf = ByteArray(2048)
        while (true) {
            val n = input.read(buf)
            if (n < 0) return out.toByteArray()
            if (out.size() + n > max) return null
            out.write(buf, 0, n)
            if (System.nanoTime() > deadline) return null
        }
    }

    // ------------------------------------------------------------ installed
    /** The installed build: its version code, and its name without the +buildid suffix. */
    data class Installed(val code: Long, val name: String)

    /** From the package manager, since buildConfig is not enabled in this project. Null if it cannot be read. */
    fun installed(context: Context): Installed? = runCatching {
        val p = context.packageManager.getPackageInfo(context.packageName, 0)
        Installed(versionCode(p), displayVersion(p.versionName ?: ""))
    }.getOrNull()

    /**
     * The build's version code, the one place it is read. longVersionCode is API 28 and minSdk is 26: called bare on
     * Android 8.0 and 8.1 it threw, and runCatching turned the crash report's version and the next run's app stamp
     * into "unknown" on every build there (rc32 audit P3 #35).
     */
    @Suppress("DEPRECATION")
    fun versionCode(p: android.content.pm.PackageInfo): Long =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) p.longVersionCode else p.versionCode.toLong()

    /** "1.0.0-rc30+c8e0a70" is shown as "1.0.0-rc30": the build id says which build, the name says which release. */
    fun displayVersion(versionName: String): String = versionName.substringBefore('+').ifBlank { "?" }

    // -------------------------------------------------------------- checks
    /**
     * One automatic check, with the fetch passed in so a test can drive it. Returns the manifest
     * only when it is due, enabled, fetched, newer and not snoozed. The time of the ask is
     * recorded whether or not the answer came: a phone with no signal must not ask again at
     * every launch, and the card promises once a day.
     */
    fun checkOnce(filesDir: File, installedCode: Long, nowMs: Long, fetcher: () -> Manifest? = { fetch() }): Manifest? {
        val before = loadState(filesDir)
        if (!due(before.enabled, before.lastCheckMs, nowMs)) return null
        val m = fetcher()
        // Re-read inside the lock: a snooze or a switch may have changed while the request was out.
        val after = edit(filesDir) { it.copy(lastCheckMs = nowMs) }
        return m?.takeIf { shouldPrompt(it, installedCode, after.snoozedCode, after.snoozedUntilMs, nowMs) }
    }

    /**
     * The launch path. Nothing at all while a staged demo mode is set (screenshots must not
     * depend on the network); the update demo is shown by the prompt itself, from [demo].
     */
    suspend fun checkIfDue(context: Context): Manifest? = withContext(Dispatchers.IO) {
        runCatching {
            // A download from an earlier launch was installed (it is this build) or abandoned. Once a process: this runs
            // again whenever the activity is rebuilt, maybe while INFO's card is downloading (RC35-NOTICED N #8).
            UpdateInstall.sweepOnce(context.cacheDir)
            if (Demo.mode != null) return@runCatching null
            val inst = installed(context) ?: return@runCatching null
            checkOnce(context.filesDir, inst.code, System.currentTimeMillis())
        }.getOrNull()
    }

    /** What Check now found, for the card on INFO. */
    sealed class Outcome {
        data class Newer(val manifest: Manifest) : Outcome()
        object Current : Outcome()
        /** No answer, a bad answer or an answer that is not ours: the card says the same thing for all three. */
        object Unreachable : Outcome()
    }

    /**
     * The Check now button: asks whether or not it is due, snoozed or switched off, and records
     * the time like the automatic check does. Blocking, so callers wrap it in withContext.
     */
    fun checkNow(filesDir: File, installedCode: Long, nowMs: Long, fetcher: () -> Manifest? = { fetch() }): Outcome {
        val m = fetcher()
        edit(filesDir) { it.copy(lastCheckMs = nowMs) }
        return when {
            m == null -> Outcome.Unreachable
            isNewer(m, installedCode) -> Outcome.Newer(m)
            else -> Outcome.Current
        }
    }

    /** Check now for the card: the installed build and the clock from the phone, the update demo without the network. */
    fun checkNow(context: Context): Outcome = runCatching {
        val inst = installed(context) ?: return Outcome.Unreachable
        if (Demo.mode == "update") return Outcome.Newer(demo(inst.code))
        checkNow(context.filesDir, inst.code, System.currentTimeMillis())
    }.getOrDefault(Outcome.Unreachable)

    /**
     * A sample newer build for staging the prompt in screenshots (adb: am start --es demo update),
     * so the dialog can be checked on a phone with no newer build published. Not a real build:
     * the link is a file that does not exist, and the demo never writes a snooze.
     */
    fun demo(installedCode: Long): Manifest = Manifest(
        versionCode = installedCode + 1,
        version = "1.0.0-rc99",
        apk = APK_PREFIX + "KaizoCore-1.0.0-rc99.apk",
        bytes = 120_385_434L,
        sha256 = "0".repeat(64),
        notes = REPO_PREFIX + "releases/tag/v1.0.0-rc99",
        page = "https://willowcreek.group/kaizocore",
    )

    // ---------------------------------------------------------------- json
    /**
     * A flat JSON object as a map: strings, integers as Long, other numbers as Double, true and
     * false, null. A nested object or array is read past and kept as a marker, so a key the app
     * does not know about may carry anything. Null when the text is not exactly one well-formed
     * object (a leading byte order mark is allowed: Windows tools write one). Internal for the
     * tests. Android's org.json does not run in a JVM unit test, and six keys do not justify a
     * library in an offline build.
     */
    internal fun readFlat(json: String): Map<String, Any?>? =
        runCatching { FlatReader(json).document() }.getOrNull()
}

/** What a nested value becomes in [UpdateCheck.readFlat]: present, and not usable as any field. */
private object Nested

/** Deeper than this is refused, not recursed into: a manifest has no reason to nest. */
private const val MAX_DEPTH_JSON = 8

private class FlatReader(private val s: String) {
    private var i = 0

    private fun fail(): Nothing = throw IllegalArgumentException("json")

    private fun ws() {
        while (i < s.length && (s[i] == ' ' || s[i] == '\t' || s[i] == '\n' || s[i] == '\r')) i++
    }

    private fun expect(c: Char) {
        if (i < s.length && s[i] == c) i++ else fail()
    }

    fun document(): Map<String, Any?> {
        if (s.startsWith('\uFEFF')) i = 1
        ws()
        val out = obj(0)
        ws()
        if (i != s.length) fail()
        return out
    }

    // Depth is bounded: a file of nothing but opening brackets must be refused, not recursed into.
    private fun obj(depth: Int): Map<String, Any?> {
        if (depth > MAX_DEPTH_JSON) fail()
        expect('{')
        val out = LinkedHashMap<String, Any?>()
        ws()
        if (i < s.length && s[i] == '}') { i++; return out }
        while (true) {
            ws()
            val key = str()
            ws(); expect(':'); ws()
            val v = value(depth + 1)
            // A key given twice has no single meaning; refusing it beats picking one.
            if (out.containsKey(key)) fail()
            out[key] = v
            ws()
            if (i < s.length && s[i] == ',') { i++; continue }
            expect('}')
            return out
        }
    }

    private fun arr(depth: Int) {
        if (depth > MAX_DEPTH_JSON) fail()
        expect('[')
        ws()
        if (i < s.length && s[i] == ']') { i++; return }
        while (true) {
            ws(); value(depth + 1); ws()
            if (i < s.length && s[i] == ',') { i++; continue }
            expect(']')
            return
        }
    }

    private fun value(depth: Int): Any? {
        if (i >= s.length) fail()
        val c = s[i]
        return when {
            c == '"' -> str()
            c == '{' -> { obj(depth); Nested }
            c == '[' -> { arr(depth); Nested }
            c == 't' -> lit("true", true)
            c == 'f' -> lit("false", false)
            c == 'n' -> lit("null", null)
            c == '-' || c in '0'..'9' -> num()
            else -> fail()
        }
    }

    private fun lit(word: String, v: Any?): Any? {
        if (!s.startsWith(word, i)) fail()
        i += word.length
        return v
    }

    private fun str(): String {
        expect('"')
        val sb = StringBuilder()
        while (true) {
            if (i >= s.length) fail()
            val c = s[i++]
            when {
                c == '"' -> return sb.toString()
                // A raw control character inside a string is not JSON; a truncated file often ends in one.
                c < ' ' -> fail()
                c != '\\' -> sb.append(c)
                else -> {
                    if (i >= s.length) fail()
                    when (val e = s[i++]) {
                        '"', '\\', '/' -> sb.append(e)
                        'b' -> sb.append('\b')
                        'f' -> sb.append('\u000C')
                        'n' -> sb.append('\n')
                        'r' -> sb.append('\r')
                        't' -> sb.append('\t')
                        'u' -> {
                            if (i + 4 > s.length) fail()
                            val hex = s.substring(i, i + 4)
                            // toInt(16) alone would take a sign or a space.
                            if (!hex.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) fail()
                            sb.append(hex.toInt(16).toChar())
                            i += 4
                        }
                        else -> fail()
                    }
                }
            }
        }
    }

    private fun digits() {
        val start = i
        while (i < s.length && s[i] in '0'..'9') i++
        if (i == start) fail()
    }

    /** JSON's own number grammar. A whole number is a Long; anything else (or too big) a Double, which no field accepts. */
    private fun num(): Any {
        val start = i
        if (s[i] == '-') i++
        if (i >= s.length) fail()
        if (s[i] == '0') i++ else digits()
        var whole = true
        if (i < s.length && s[i] == '.') { whole = false; i++; digits() }
        if (i < s.length && (s[i] == 'e' || s[i] == 'E')) {
            whole = false; i++
            if (i < s.length && (s[i] == '+' || s[i] == '-')) i++
            digits()
        }
        val token = s.substring(start, i)
        return if (whole) token.toLongOrNull() ?: token.toDouble() else token.toDouble()
    }
}
