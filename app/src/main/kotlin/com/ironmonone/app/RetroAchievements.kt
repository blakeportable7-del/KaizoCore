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
    class Store(private val file: File) {
        fun load(): Pair<String, String>? {
            val lines = runCatching { file.readLines() }.getOrNull() ?: return null
            return if (lines.size >= 2 && lines[0].isNotBlank() && lines[1].isNotBlank()) lines[0] to lines[1] else null
        }
        fun save(user: String, token: String) { runCatching { file.parentFile?.mkdirs(); file.writeText("$user\n$token\n") } }
        fun clear() { file.delete() }
        var hardcore: Boolean
            get() = File(file.parentFile, "ra-hardcore").exists()
            set(v) { val f = File(file.parentFile, "ra-hardcore"); if (v) f.writeText("1") else f.delete() }
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

    /** The listener the Play screen installs on the view. Replies are posted back on the main thread. */
    fun listener(mainPost: (() -> Unit) -> Unit, onEvent: (Int, String, String, Int, String, Int) -> Unit) =
        object : GLRetroView.CheevosListener {
            override fun onServerCall(id: Int, url: String, postData: String, contentType: String) {
                perform(id, url, postData, contentType) { rid, body, status ->
                    mainPost { runCatching { LibretroDroid.cheevosServerResponse(rid, body, status) } }
                }
            }
            override fun onEvent(type: Int, title: String, description: String, points: Int, badgeUrl: String, result: Int) =
                onEvent(type, title, description, points, badgeUrl, result)
        }

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
