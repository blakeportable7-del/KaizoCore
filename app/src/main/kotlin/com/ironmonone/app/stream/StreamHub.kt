package com.ironmonone.app.stream

import com.ironmonone.tracker.RunOutcome
import java.net.NetworkInterface

/**
 * Process-wide home for the stream server, so it outlives the Play screen
 * (which is disposed on every tab switch) and OBS keeps its connection while
 * the player checks the ROMs tab.
 *
 * The Play screen pushes prebuilt JSON strings in; the server threads only
 * ever read a reference. Nothing here touches Compose.
 */
object StreamHub {
    const val PORT = 8642

    @Volatile var state: String = "{}"
        private set
    @Volatile var version: Long = 0L
        private set
    /** The attempt number, only while a run is in play; empty in any other game (see [publish]). */
    @Volatile var attempts: String = ""
    @Volatile var dex: String = "[]"

    /**
     * How the Kaizo IronMON run in Play ended, once its game-over popup has latched it (GameOverHost), else null
     * (2026-09-30, IronMON rules check). The stream's run-over view and /dex.json, every species as randomized, wait
     * for it: the randomizer's data is the log, which the PC tracker opens mid-run only behind a warning, and the
     * tracker's live read used to open that tab at a Nuzlocke lead's faint while the run went on.
     */
    @Volatile var ended: RunOutcome? = null
    @Volatile var page: String = "<p>Tracker page missing from the build.</p>"

    @Volatile private var server: StreamServer? = null
    @Volatile var token: String = ""
        private set

    val running: Boolean get() = server != null

    /** Called on the thread that started the stream, once it is up: MainActivity asks for the reminder's permission. */
    @Volatile var onStarted: (() -> Unit)? = null

    fun publish(json: String, attempt: Int) {
        if (json != state) { state = json; version++ }
        // Attempts belong to a run. Any other game (Play any game, ROM Hacks) would
        // otherwise show the last run's number, which is stale, so it says nothing.
        attempts = if (StreamSnapshot.isRun(json)) attempt.toString() else ""
    }

    /**
     * Start on the fixed port; returns the URL to show, or null when the port is taken.
     *
     * The token is kept in [filesDir]/prep/stream-token.txt and reused. A fresh
     * one on every start broke the browser source saved in OBS each time the
     * stream was switched on (2026-09-27, audit). Since it now lasts, it is 32
     * random bits from SecureRandom rather than 16 from Math.random. Per phone:
     * left out of the backup on purpose (BackupCoverageTest).
     */
    fun start(filesDir: java.io.File): String? {
        if (server != null) return url()
        token = stableToken(java.io.File(filesDir, "prep/stream-token.txt"))
        val s = StreamServer(token, { page }, { state }, { version }, { attempts }, { if (ended != null) dex else null }, NativeGameFeed, { wifiAddress() })
        val ok = runCatching { s.start(PORT) }.isSuccess
        if (!ok) return null
        server = s
        onStarted?.invoke()
        return url()
    }

    fun stop() { server?.stop(); server = null }

    /** The saved token, or a new one saved for next time. Unreadable or malformed = new. */
    internal fun stableToken(f: java.io.File): String {
        runCatching { f.readText().trim() }.getOrNull()?.takeIf { it.matches(Regex("[0-9a-f]{8}")) }?.let { return it }
        val t = "%08x".format(java.security.SecureRandom().nextInt())
        runCatching { f.parentFile?.mkdirs(); f.writeText(t) }
        return t
    }

    /**
     * The address to open on the PC: the setup guide, which hands out the OBS scene and
     * every individual link. (It was the tracker's own address until the game itself
     * could be streamed, 2026-09-29.)
     */
    fun url(): String = "http://${wifiAddress() ?: "<phone-ip>"}:$PORT/?k=$token"

    /** The phone's LAN address: the first non-loopback IPv4 on an up interface. */
    fun wifiAddress(): String? = runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .sortedBy { if (it.name.startsWith("wlan")) 0 else 1 }
            .flatMap { it.inetAddresses.toList() }
            .firstOrNull { it is java.net.Inet4Address && !it.isLoopbackAddress }
            ?.hostAddress
    }.getOrNull()
}
