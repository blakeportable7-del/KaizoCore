package com.ironmonone.app

import com.ironmonone.core.Platform
import com.swordfish.libretrodroid.GLRetroView
import com.swordfish.libretrodroid.LibretroDroid
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * RetroAchievements, the app side. The client itself is rcheevos' rc_client
 * in native code (cheevos.cpp); this is what it cannot do alone: HTTP, the
 * session token on disk, and the events the player sees.
 *
 * What is stored: the username and the session token the server issued.
 * The password is used once for the login request and never written.
 * Hardcore is the player's choice; the app enforces its half (no rewind,
 * no cheats, no slow motion, no state loading) through [hardcoreOn].
 *
 * The server is retroachievements.org; nothing else is contacted. Every
 * request is one the rc_client asked for, sent with the URL and body it
 * built, and the reply is handed straight back.
 */
object RetroAchievements {

    data class Summary(
        val loggedIn: Boolean = false, val user: String = "", val score: Int = 0,
        val hardcore: Boolean = false, val gameLoaded: Boolean = false, val game: String = "",
        val unlocked: Int = 0, val total: Int = 0, val pointsUnlocked: Int = 0, val pointsTotal: Int = 0,
        val loadError: String = "",
    )

    data class Achievement(val id: Int, val title: String, val description: String, val points: Int,
                           val unlocked: Boolean, val bucket: String, val progress: Float)

    /** rc_client event types the app reacts to. */
    const val EV_ACHIEVEMENT = 1
    const val EV_RESET = 14
    const val EV_GAME_COMPLETED = 15
    const val EV_SERVER_ERROR = 16
    const val EV_DISCONNECTED = 17
    const val EV_RECONNECTED = 18
    const val EV_LOGIN_DONE = 100
    const val EV_GAME_LOADED = 101

    /** rcheevos console ids. */
    fun consoleId(p: Platform): Int = when (p) {
        Platform.GBA -> 5; Platform.GBC -> 6; Platform.NDS -> 18
    }

    // ------------------------------------------------------------ storage
    const val HARDCORE_FAILED = "Could not save the hardcore switch. If this phone is out of space, free some, then set it again."

    class Store(private val file: File) {
        fun load(): Pair<String, String>? {
            val lines = runCatching { file.readLines() }.getOrNull() ?: return null
            return if (lines.size >= 2 && lines[0].isNotBlank() && lines[1].isNotBlank()) lines[0] to lines[1] else null
        }
        /** Whole or not at all (SafeWrite): rewritten in place, a kill or a full phone left the token cut short (RC35-NOTICED N #15). */
        fun save(user: String, token: String) { SafeWrite.text(file, "$user\n$token\n") }
        /**
         * Signing out, or a token the server refused. Hardcore goes with the session (rc32 audit P2 #54): the flag
         * outlived it, and a signed-out player, whose dialog has no hardcore switch, found every game refusing to
         * reopen where it was left.
         */
        fun clear() { file.delete(); hardcore = false }
        var hardcore: Boolean
            get() = File(file.parentFile, "ra-hardcore").exists()
            set(v) {
                val f = File(file.parentFile, "ra-hardcore")
                // A full phone threw here, out of the switch's tap, and closed the app mid-game (rc32 audit P2 #16).
                if (runCatching { if (v) f.writeText("1") else f.delete() }.isFailure) SaveTrouble.report(SaveTrouble.SETTING, HARDCORE_FAILED)
            }
        /** Hardcore with a signed-in session on disk: a flag an older build left behind after a sign-out counts for nothing. */
        fun hardcoreSignedIn(): Boolean = hardcore && load() != null
    }

    // ------------------------------------------------------------ network
    private val net = Executors.newSingleThreadExecutor { r -> Thread(r, "ra-http").apply { isDaemon = true } }

    /** One request the client asked for; the reply goes back through JNI on the caller's thread of choice. */
    fun perform(id: Int, url: String, postData: String, contentType: String, onReply: (Int, String, Int) -> Unit) {
        net.execute {
            var status = 0; var body = ""
            runCatching {
                val c = URL(url).openConnection() as HttpURLConnection
                c.connectTimeout = 15_000; c.readTimeout = 30_000
                c.setRequestProperty("User-Agent", "KaizoCore/1.0")
                if (postData.isNotEmpty()) {
                    c.requestMethod = "POST"; c.doOutput = true
                    c.setRequestProperty("Content-Type", contentType.ifBlank { "application/x-www-form-urlencoded" })
                    c.outputStream.use { it.write(postData.toByteArray()) }
                }
                status = c.responseCode
                body = (if (status < 400) c.inputStream else c.errorStream)?.bufferedReader()?.readText() ?: ""
            }.onFailure { status = 0; body = "" }
            onReply(id, body, status)
        }
    }

    /** The process's own main thread, for replies; made on first use (a JVM test has no Looper). */
    private val main by lazy { android.os.Handler(android.os.Looper.getMainLooper()) }

    /**
     * The listener the Play screen installs on the view. A server reply goes back to the client on the main thread through
     * [post], the app's own main handler, never through the view: a view that had left the screen (a tab switch, NEW RUN)
     * never ran its posts, so the reply was dropped and the client stayed "logging in" until the app was killed (rc33
     * audit P1 #36). The request then holds no view either. [respond] is the client's door, a test's stand-in.
     */
    fun listener(
        onEvent: (Int, String, String, Int, String, Int) -> Unit,
        post: (Runnable) -> Unit = { main.post(it) },
        respond: (Int, String, Int) -> Unit = { id, body, status -> LibretroDroid.cheevosServerResponse(id, body, status) },
    ) =
        object : GLRetroView.CheevosListener {
            override fun onServerCall(id: Int, url: String, postData: String, contentType: String) {
                perform(id, url, postData, contentType) { rid, body, status ->
                    post(Runnable { runCatching { respond(rid, body, status) } })
                }
            }
            override fun onEvent(type: Int, title: String, description: String, points: Int, badgeUrl: String, result: Int) =
                onEvent(type, title, description, points, badgeUrl, result)
        }

    /**
     * Whether a failed sign-in was the server refusing the saved token (rc_error.h: -33 access denied, -34 invalid
     * credentials, -35 expired token). Only then is the token deleted: offline (-32), a sign-in already in flight (-25),
     * an abort (-31) or a server hiccup used to sign the player out for good (rc33 audit P1 #37).
     */
    fun tokenRefused(result: Int): Boolean = result == -33 || result == -34 || result == -35

    /**
     * The game and the achievement client restarted together, between frames on the emulation thread. rcheevos waits for
     * this after hardcore goes on mid-game (RC_CLIENT_EVENT_RESET) and counts nothing until it comes; nothing answered
     * that event, so every achievement stopped until the game was opened again (rc33 audit P1 #38).
     */
    fun restartGame(view: GLRetroView?) {
        view?.queueEvent { runCatching { LibretroDroid.reset(); LibretroDroid.cheevosReset() } }
    }

    /** A sign-in that is already under way (rc_error.h RC_INVALID_STATE): not an error to show. */
    const val SIGN_IN_IN_FLIGHT = -25

    // --------------------------------------------------------------- JSON
    private fun field(json: String, key: String): String? {
        val m = Regex("\"" + Regex.escape(key) + "\":(\"((?:[^\"\\\\]|\\\\.)*)\"|[^,}\\]]+)").find(json) ?: return null
        return m.groups[2]?.value?.replace("\\\"", "\"")?.replace("\\\\", "\\") ?: m.groups[1]?.value?.trim()
    }

    fun parseSummary(json: String): Summary = Summary(
        loggedIn = field(json, "loggedIn") == "true", user = field(json, "user") ?: "",
        score = field(json, "score")?.toIntOrNull() ?: 0, hardcore = field(json, "hardcore") == "true",
        gameLoaded = field(json, "gameLoaded") == "true", game = field(json, "game") ?: "",
        unlocked = field(json, "unlocked")?.toIntOrNull() ?: 0, total = field(json, "total")?.toIntOrNull() ?: 0,
        pointsUnlocked = field(json, "pointsUnlocked")?.toIntOrNull() ?: 0, pointsTotal = field(json, "pointsTotal")?.toIntOrNull() ?: 0,
        loadError = field(json, "loadError") ?: "",
    )

    fun parseAchievements(json: String): List<Achievement> =
        Regex("\\{[^{}]*\\}").findAll(json).map { m ->
            val o = m.value
            Achievement(
                id = field(o, "id")?.toIntOrNull() ?: 0, title = field(o, "title") ?: "", description = field(o, "description") ?: "",
                points = field(o, "points")?.toIntOrNull() ?: 0, unlocked = field(o, "unlocked") == "true",
                bucket = field(o, "bucket") ?: "", progress = field(o, "progress")?.toFloatOrNull() ?: 0f,
            )
        }.toList()

    /** The app's half of hardcore: what must be off while it is on. */
    fun hardcoreOn(summary: Summary?): Boolean = summary?.hardcore == true && summary.loggedIn
}
