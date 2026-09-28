package com.ironmonone.app.stream

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
    @Volatile var attempts: String = "0"
    @Volatile var dex: String = "[]"
    @Volatile var page: String = "<p>Tracker page missing from the build.</p>"

    @Volatile private var server: StreamServer? = null
    @Volatile var token: String = ""
        private set

    val running: Boolean get() = server != null

    fun publish(json: String, attempt: Int) {
        if (json != state) { state = json; version++ }
        attempts = attempt.toString()
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
        val s = StreamServer(token, { page }, { state }, { version }, { attempts }, { dex })
        val ok = runCatching { s.start(PORT) }.isSuccess
        if (!ok) return null
        server = s
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

    fun url(): String = "http://${wifiAddress() ?: "<phone-ip>"}:$PORT/tracker?k=$token"

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
