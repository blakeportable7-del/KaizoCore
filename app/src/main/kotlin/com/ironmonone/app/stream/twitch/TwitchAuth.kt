package com.ironmonone.app.stream.twitch

import com.ironmonone.app.stream.Json
import java.io.File
import java.io.IOException

/**
 * A signed-in streamer: the user access token, the refresh token that replaces it, and who they are.
 *
 * [toString] names the account and nothing else, so a token cannot reach a log, an exception message or a crash report
 * by way of a string template. Nothing in the Twitch code logs at all.
 */
class TwitchTokens(
    val access: String,
    val refresh: String,
    /** When [access] runs out, epoch milliseconds. */
    val expiresAt: Long,
    val userId: String = "",
    val login: String = "",
    /** The display name, as Twitch shows it ("Blake_P"); [login] when Twitch gave none. */
    val name: String = "",
    val scopes: List<String> = emptyList(),
) {
    fun withUser(id: String, login: String, name: String = login) = TwitchTokens(access, refresh, expiresAt, id, login, name, scopes)
    fun shown(): String = name.ifBlank { login }
    override fun toString(): String = "TwitchTokens(login=$login)"

    /** The saved form, read back by [decode]. Only ever written encrypted (KeystoreTokenStore). */
    fun encode(): String = Json.write(linkedMapOf("v" to 1, "access" to access, "refresh" to refresh, "expiresAt" to expiresAt,
        "userId" to userId, "login" to login, "name" to name, "scopes" to scopes))

    companion object {
        fun decode(text: String): TwitchTokens? {
            val m = JsonIn.obj(text) ?: return null
            val access = JsonIn.str(m, "access")?.takeIf { it.isNotBlank() } ?: return null
            val refresh = JsonIn.str(m, "refresh")?.takeIf { it.isNotBlank() } ?: return null
            return TwitchTokens(access, refresh, JsonIn.long(m, "expiresAt") ?: 0L, JsonIn.str(m, "userId").orEmpty(),
                JsonIn.str(m, "login").orEmpty(), JsonIn.str(m, "name").orEmpty(),
                JsonIn.asList(m["scopes"])?.filterIsInstance<String>().orEmpty())
        }
    }
}

/** Where the tokens live. The app's is [KeystoreTokenStore]; a test keeps them in memory. */
interface TwitchTokenStore {
    fun load(): TwitchTokens?
    fun save(t: TwitchTokens)
    fun clear()
}

class MemoryTokenStore(var tokens: TwitchTokens? = null) : TwitchTokenStore {
    /** Every refresh token ever saved, oldest first: the rotation test reads it. */
    val saved = ArrayList<String>()
    override fun load() = tokens
    override fun save(t: TwitchTokens) { tokens = t; saved += t.refresh }
    override fun clear() { tokens = null }
}

/**
 * The tokens, encrypted with an AES-256-GCM key that lives in the Android Keystore and never leaves it, in one file:
 * filesDir/[FILE] (com.ironmonone.app.KeystoreSealedFile, which OBS's WebSocket password uses too since 2026-10-05).
 * RetroAchievements still keeps its token in a plain file, and androidx.security would be a new dependency for what
 * the platform's own Keystore does here in a page.
 *
 * Kept out of every copy of the app's data, each for its own reason:
 * - The app's backup zip and Cloud sync (Backup, CloudSync) take only an allowlist of paths under saves/, prep/ and
 *   attempts/; this file is at the top of filesDir, beside crash-sent.txt, where device-local state lives, and
 *   TwitchStorageTest proves Backup.admits refuses it.
 * - Android's own backup is off (allowBackup="false"). A device-to-device transfer can still copy the file on Android
 *   12 and up, but not the Keystore key, which is bound to this phone: the copy cannot be read, [load] finds that,
 *   deletes it, and the new phone asks to sign in.
 * - Crash reports carry stack traces and exception messages, never files or fields; no Twitch code puts a token in a
 *   message (TwitchTokens.toString, HttpReply.toString), and CrashReport.scrub drops anything token-shaped as well.
 */
class KeystoreTokenStore(file: File) : TwitchTokenStore {
    companion object {
        const val FILE = "twitch-session.bin"
        private const val ALIAS = "kaizocore-twitch-session"

        fun inFilesDir(filesDir: File) = KeystoreTokenStore(File(filesDir, FILE))
    }

    /** The lock itself, shared with OBS's password (SealedFile.kt, 2026-10-05): same file format, same key alias as before. */
    private val sealed = com.ironmonone.app.KeystoreSealedFile(file, ALIAS)

    @Synchronized
    override fun load(): TwitchTokens? {
        val bytes = sealed.read() ?: return null
        val t = runCatching { TwitchTokens.decode(String(bytes, Charsets.UTF_8)) }.getOrNull()
        // Opened but not a sign-in: useless, so it goes, and sign-in is asked for.
        if (t == null) sealed.clear()
        return t
    }

    @Synchronized
    override fun save(t: TwitchTokens) {
        try {
            sealed.write(t.encode().toByteArray(Charsets.UTF_8))
        } catch (e: IOException) {
            throw IOException("could not save the Twitch sign-in")
        }
    }

    @Synchronized
    override fun clear() {
        sealed.clear()
    }
}

/**
 * Twitch sign-in for a public client: the Device Code Grant Flow, refresh, validate and revoke (id.twitch.tv, docs
 * checked 2026-10-05). Every call blocks; [clock] and [sleep] are the test's.
 */
class TwitchAuth(
    private val http: TwitchHttp = UrlHttp,
    private val endpoints: Twitch.Endpoints = Twitch.Endpoints(),
    private val clock: () -> Long = System::currentTimeMillis,
    private val sleep: (Long) -> Unit = { Thread.sleep(it) },
) {
    /** What the player is shown: [userCode] to type at [verificationUri]. [deviceCode] stays in the app. */
    class DeviceCode(val deviceCode: String, val userCode: String, val verificationUri: String, val intervalSeconds: Int, val expiresAt: Long) {
        override fun toString() = "DeviceCode($userCode)"
    }

    sealed class Poll {
        object Pending : Poll()
        object SlowDown : Poll()
        object Expired : Poll()
        object Denied : Poll()
        object Cancelled : Poll()
        class Done(val tokens: TwitchTokens) : Poll()
        class Failed(val why: String) : Poll()
    }

    sealed class Refresh {
        class Ok(val tokens: TwitchTokens) : Refresh()
        /** Twitch refused the refresh token (used, revoked, or 30 days unused): only a new sign-in helps. */
        object Rejected : Refresh()
        /** Nothing came back, or Twitch had trouble: try again later with the same token. */
        object Unreachable : Refresh()
    }

    sealed class Validate {
        class Ok(val userId: String, val login: String, val scopes: List<String>, val expiresInSeconds: Long) : Validate()
        object Invalid : Validate()
        object Unreachable : Validate()
    }

    private val scopeText get() = Twitch.SCOPES.joinToString(" ")

    /** POST /oauth2/device. Throws [TwitchTrouble] with words for the player when it cannot start. */
    fun start(): DeviceCode {
        val r = try {
            http.request("POST", "${endpoints.id}/oauth2/device", emptyMap(),
                TwitchHttp.form("client_id" to Twitch.CLIENT_ID, "scopes" to scopeText), null)
        } catch (e: IOException) {
            throw TwitchTrouble("Could not reach Twitch. Check the phone's connection and try again.")
        }
        val m = JsonIn.obj(r.body)
        val device = JsonIn.str(m, "device_code")
        val user = JsonIn.str(m, "user_code")
        if (!r.ok || device.isNullOrBlank() || user.isNullOrBlank()) throw TwitchTrouble("Twitch did not give a code (${r.code}). Try again in a minute.")
        val interval = (JsonIn.long(m, "interval") ?: 5L).toInt().coerceIn(1, 60)
        val expires = (JsonIn.long(m, "expires_in") ?: 1800L).coerceIn(30, 3600)
        val uri = JsonIn.str(m, "verification_uri")?.takeIf { it.startsWith("https://") } ?: ACTIVATE
        return DeviceCode(device, user, uri, interval, clock() + expires * 1000)
    }

    /** One POST /oauth2/token for the device code. */
    fun pollOnce(code: DeviceCode): Poll {
        if (clock() >= code.expiresAt) return Poll.Expired
        val r = try {
            http.request("POST", "${endpoints.id}/oauth2/token", emptyMap(), TwitchHttp.form(
                "client_id" to Twitch.CLIENT_ID, "scopes" to scopeText, "device_code" to code.deviceCode,
                "grant_type" to "urn:ietf:params:oauth:grant-type:device_code"), null)
        } catch (e: IOException) {
            return Poll.Pending  // a dropped poll is not an answer: ask again at the next interval
        }
        if (r.ok) return tokensFrom(r)?.let { Poll.Done(it) } ?: Poll.Failed("Twitch's answer was not readable.")
        val message = (JsonIn.str(JsonIn.obj(r.body), "message") ?: "").lowercase()
        return when {
            "authorization_pending" in message -> Poll.Pending
            "slow_down" in message -> Poll.SlowDown
            "expired" in message || "invalid device code" in message -> Poll.Expired
            "denied" in message -> Poll.Denied
            r.code >= 500 || r.code == 429 -> Poll.Pending
            else -> Poll.Failed("Twitch said no (${r.code}).")
        }
    }

    /**
     * Polls at the interval Twitch gave until the player approves, the code runs out, or [cancelled]. slow_down adds 5
     * seconds to the interval for good, as RFC 8628 section 3.5 asks.
     */
    fun await(code: DeviceCode, cancelled: () -> Boolean): Poll {
        var interval = code.intervalSeconds.toLong()
        while (true) {
            if (cancelled()) return Poll.Cancelled
            sleep(interval * 1000)
            if (cancelled()) return Poll.Cancelled
            when (val p = pollOnce(code)) {
                Poll.Pending -> {}
                Poll.SlowDown -> interval += 5
                else -> return p
            }
        }
    }

    /**
     * POST /oauth2/token with the refresh token. A public client's refresh token works once and lapses after 30 days
     * unused, so the caller saves the new one before anything else (TwitchChat.refreshNow).
     */
    fun refresh(old: TwitchTokens): Refresh {
        val r = try {
            http.request("POST", "${endpoints.id}/oauth2/token", emptyMap(), TwitchHttp.form(
                "client_id" to Twitch.CLIENT_ID, "grant_type" to "refresh_token", "refresh_token" to old.refresh), null)
        } catch (e: IOException) {
            return Refresh.Unreachable
        }
        if (r.ok) {
            val t = tokensFrom(r) ?: return Refresh.Unreachable
            return Refresh.Ok(TwitchTokens(t.access, t.refresh, t.expiresAt, old.userId, old.login, old.name, t.scopes.ifEmpty { old.scopes }))
        }
        return if (r.code == 400 || r.code == 401 || r.code == 403) Refresh.Rejected else Refresh.Unreachable
    }

    /** GET /oauth2/validate: who the token is for. Twitch asks apps to call it at start and then hourly. */
    fun validate(access: String): Validate {
        val r = try {
            http.request("GET", "${endpoints.id}/oauth2/validate", mapOf("Authorization" to "OAuth $access"), null, null)
        } catch (e: IOException) {
            return Validate.Unreachable
        }
        if (r.code == 401) return Validate.Invalid
        if (!r.ok) return Validate.Unreachable
        val m = JsonIn.obj(r.body) ?: return Validate.Unreachable
        val id = JsonIn.str(m, "user_id") ?: return Validate.Invalid
        return Validate.Ok(id, JsonIn.str(m, "login").orEmpty(), JsonIn.asList(m["scopes"])?.filterIsInstance<String>().orEmpty(),
            JsonIn.long(m, "expires_in") ?: 0L)
    }

    /** POST /oauth2/revoke. Best effort: signing out forgets the tokens whatever Twitch answers. */
    fun revoke(token: String): Boolean = runCatching {
        http.request("POST", "${endpoints.id}/oauth2/revoke", emptyMap(),
            TwitchHttp.form("client_id" to Twitch.CLIENT_ID, "token" to token), null).ok
    }.getOrDefault(false)

    private fun tokensFrom(r: HttpReply): TwitchTokens? {
        val m = JsonIn.obj(r.body) ?: return null
        val access = JsonIn.str(m, "access_token")?.takeIf { it.isNotBlank() } ?: return null
        val refresh = JsonIn.str(m, "refresh_token")?.takeIf { it.isNotBlank() } ?: return null
        val expires = (JsonIn.long(m, "expires_in") ?: 3600L).coerceAtLeast(60)
        val scopes = when (val s = m["scope"]) {
            is List<*> -> s.filterIsInstance<String>()
            is String -> s.split(' ').filter { it.isNotBlank() }
            else -> emptyList()
        }
        return TwitchTokens(access, refresh, clock() + expires * 1000, scopes = scopes)
    }

    companion object {
        const val ACTIVATE = "https://www.twitch.tv/activate"
    }
}
