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
    /** Every species as randomized (StreamFeed), built once the run is over; null until then, which /dex.json refuses. */
    @Volatile var dex: String? = null

    /**
     * How the Kaizo IronMON run in Play ended, once its game-over popup has latched it (GameOverHost), else null
     * (2026-09-30, IronMON rules check). The stream's run-over view and /dex.json, every species as randomized, wait
     * for it: the randomizer's data is the log, which the PC tracker opens mid-run only behind a warning, and the
     * tracker's live read used to open that tab at a Nuzlocke lead's faint while the run went on.
     */
    @Volatile var ended: RunOutcome? = null
    @Volatile var page: String = "<p>Tracker page missing from the build.</p>"

    /**
     * Where the favorites' pictures come from (StreamFavoritesSource): the app sets it once (MainActivity), and the
     * server asks it on every request for one, so a favorite edited anywhere reaches OBS at once, Play open or not.
     * Null: every favorite is the empty picture.
     */
    @Volatile var favorites: StreamFavoritesSource? = null

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
     *
     * [port] is for the tests: the app always serves on [PORT], which the OBS scene and saved browser sources name.
     */
    fun start(filesDir: java.io.File, port: Int = PORT): String? {
        if (server != null) return url()
        token = stableToken(java.io.File(filesDir, "prep/stream-token.txt"))
        val s = StreamServer(token, { page }, { state }, { version }, { attempts }, { if (ended != null) dex else null }, feed ?: NativeGameFeed, { wifiAddress() },
            runOver = { ended != null }, favorite = { n -> favorites?.pictures()?.getOrNull(n - 1) })
        val ok = runCatching { s.start(port) }.isSuccess
        if (!ok) return null
        server = s
        service(true)
        onStarted?.invoke()
        return url()
    }

    fun stop() { server?.stop(); server = null; service(false) }

    /**
     * StreamService, started with the server and stopped with it, so the stream runs as a foreground service and its
     * notification goes when it does (rc32 audit P3 #80). Only ever started from the player's tap on STREAM, with
     * KaizoCore in front. A test records the calls instead.
     */
    internal var service: (Boolean) -> Unit = { on -> appContext?.let { StreamService.follow(it, on) } }

    /** The game's picture and sound: null is the emulator's, NativeGameFeed, which loads the native library; a test sets a fake. */
    internal var feed: GameFeed? = null

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
    fun url(): String = url(wifiAddress())

    internal fun url(address: String?): String = "http://${address ?: "<phone-ip>"}:$PORT/?k=$token"

    /**
     * What to do when the phone has no address a PC beside it can open (rc32 audit P3 #79): the status line and the
     * menu printed the <phone-ip> placeholder in its place, and nothing said Wi-Fi was the trouble.
     */
    const val NO_WIFI = "Connect the phone to the same Wi-Fi as your PC, or turn on its hotspot."

    /** Play's status line once STREAM is tapped: [url] as [start] gave it, null when the port was taken. */
    fun startedLine(url: String?): String = startedLine(url, wifiAddress())

    internal fun startedLine(url: String?, address: String?): String = when {
        url == null -> "Port $PORT is busy."
        address == null -> "Stream on. $NO_WIFI"
        else -> "On your PC, open $url"
    }

    /** The FILE menu's line while the stream is on: the link to open, or what to do for one. */
    fun menuLine(): String = menuLine(wifiAddress())

    internal fun menuLine(address: String?): String =
        if (address == null) "Stream: no Wi-Fi address. $NO_WIFI" else "Stream: " + url(address)

    /** Copy link: hands [copy] the link and says so, or, with no address to put in it, copies nothing and says what to do. */
    fun copyLink(copy: (String) -> Unit): String = copyLink(wifiAddress(), copy)

    internal fun copyLink(address: String?, copy: (String) -> Unit): String {
        if (address == null) return NO_WIFI
        copy(url(address))
        return "Stream link copied."
    }

    /** The app's context, for ConnectivityManager's word on which interfaces carry mobile data (MainActivity sets it). */
    @Volatile var appContext: android.content.Context? = null

    /** The phone's LAN address, the one a PC beside it can open (see [pickAddress]), or null when it has none. */
    fun wifiAddress(): String? = runCatching {
        val cellular = cellularInterfaces()
        pickAddress(NetworkInterface.getNetworkInterfaces().toList().filter { it.isUp && !it.isLoopback }.map { nif ->
            Candidate(nif.name, nif.inetAddresses.toList().filterIsInstance<java.net.Inet4Address>().mapNotNull { it.hostAddress },
                cellular = nif.name in cellular)
        })
    }.getOrNull()

    /** One network interface as [pickAddress] sees it: its name, its IPv4 addresses, and whether the system calls it mobile data. */
    internal data class Candidate(val name: String, val ipv4: List<String>, val cellular: Boolean = false)

    /**
     * The address to print for the PC (rc32 audit P3 #79). It was the first IPv4 of any interface that was up, so with
     * Wi-Fi off it was a mobile data address (a carrier's 10.x, or the 192.0.0.4 of a 464XLAT clat interface) that no PC
     * can open. Mobile data is left out, by the system's transport flag and by the names the radios' interfaces have; so
     * are the clat interfaces; and only an address a PC on the same network can have is taken: the private ranges, and
     * 100.64.0.0/10, which a VPN such as Tailscale hands out (the server admits it, StreamServer.local). Wi-Fi first, then
     * a hotspot or a tether, then the rest. Null when nothing is left.
     */
    internal fun pickAddress(candidates: List<Candidate>): String? {
        fun mobileName(n: String) = MOBILE_NAMES.any { n.startsWith(it) }
        fun rank(n: String) = when {
            n.startsWith("wlan") -> 0
            SHARED_NAMES.any { n.startsWith(it) } -> 1
            else -> 2
        }
        return candidates.filter { !it.cellular && !mobileName(it.name) }
            .sortedBy { rank(it.name) }
            .flatMap { it.ipv4 }
            .firstOrNull { reachable(it) }
    }

    /** 10/8, 172.16/12, 192.168/16 and 100.64/10. */
    private fun reachable(ip: String): Boolean {
        val b = ip.split('.').mapNotNull { it.toIntOrNull() }.takeIf { it.size == 4 } ?: return false
        return b[0] == 10 || (b[0] == 172 && b[1] in 16..31) || (b[0] == 192 && b[1] == 168) || (b[0] == 100 && b[1] in 64..127)
    }

    /** The radios' interfaces (Qualcomm, MediaTek, Unisoc and the rest) and the clat interfaces of 464XLAT. */
    private val MOBILE_NAMES = listOf("rmnet", "r_rmnet", "rev_rmnet", "ccmni", "seth_", "pdp", "ppp", "wwan", "clat", "v4-")
    /** A hotspot's or a tether's interface: what a PC is on when the phone is its network. */
    private val SHARED_NAMES = listOf("swlan", "ap", "softap", "wigig", "rndis", "usb", "ncm", "eth", "bt-pan")

    /** The interfaces ConnectivityManager says carry mobile data; empty when it cannot say. */
    private fun cellularInterfaces(): Set<String> {
        val cm = appContext?.getSystemService(android.net.ConnectivityManager::class.java) ?: return emptySet()
        return runCatching {
            @Suppress("DEPRECATION")
            cm.allNetworks.filter { n -> cm.getNetworkCapabilities(n)?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) == true }
                .mapNotNull { n -> cm.getLinkProperties(n)?.interfaceName }.toSet()
        }.getOrDefault(emptySet())
    }
}
