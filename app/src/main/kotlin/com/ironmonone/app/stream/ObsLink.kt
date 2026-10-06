package com.ironmonone.app.stream

import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * What the player set on the Stream page for OBS: where it runs, its password, and what to do when. A scene left
 * blank is never switched to. Kept in prep/obs-link.txt, one key=value a line, all but the password; left out of the
 * backup on purpose (BackupCoverageTest): the PC's address belongs to this phone's network.
 *
 * The password is not in that file (2026-10-05): it is sealed with the Android Keystore in filesDir/[ObsLink.PASSWORD_FILE]
 * (KeystoreSealedFile, the Twitch sign-in's lock), outside the backup too. A file from before, with a "password=" line,
 * is read once and moved over by ObsLink.load.
 */
data class ObsSettings(
    val enabled: Boolean = false,
    val host: String = "",
    val port: Int = ObsProtocol.DEFAULT_PORT,
    val password: String = "",
    val battleScene: String = "",
    val overworldScene: String = "",
    val gameOverScene: String = "",
    val saveReplay: Boolean = false,
) {
    /** The scenes the app may switch between; OBS on any other scene (Starting soon, Be right back) is left alone. */
    val ownScenes: Set<String> get() = setOf(battleScene, overworldScene, gameOverScene).filter { it.isNotBlank() }.toSet()

    /**
     * A battle or game-over scene with no game scene to come back to. The game scene ([overworldScene]) is the one the
     * streamer plays on; the app only ever switches from one of its own scenes, so without it there is nowhere to start.
     */
    val needsGameScene: Boolean get() = (battleScene.isNotBlank() || gameOverScene.isNotBlank()) && overworldScene.isBlank()

    /** Something to do, and somewhere to do it. */
    val usable: Boolean get() = enabled && host.isNotBlank() && port in 1..65535 && !needsGameScene &&
        (overworldScene.isNotBlank() || saveReplay)

    /** The saved form, without the password: that is sealed on its own (ObsLink.PASSWORD_FILE). */
    fun encode(): String = listOf(
        "enabled" to (if (enabled) "1" else "0"), "host" to host, "port" to port.toString(),
        "battle" to battleScene, "overworld" to overworldScene, "gameover" to gameOverScene, "replay" to (if (saveReplay) "1" else "0"),
    ).joinToString("") { (k, v) -> k + "=" + flat(v) + "\n" }

    companion object {
        private fun flat(s: String) = s.replace('\n', ' ').replace('\r', ' ')

        /** Reads [encode]'s form. A "password=" line is a file from before the password was sealed: ObsLink.load moves it. */
        fun decode(text: String): ObsSettings {
            val m = text.lines().filter { '=' in it }.associate { it.substringBefore('=') to it.substringAfter('=') }
            return ObsSettings(
                enabled = m["enabled"] == "1", host = m["host"].orEmpty().trim(),
                port = m["port"]?.trim()?.toIntOrNull() ?: ObsProtocol.DEFAULT_PORT, password = m["password"].orEmpty(),
                battleScene = m["battle"].orEmpty(), overworldScene = m["overworld"].orEmpty(),
                gameOverScene = m["gameover"].orEmpty(), saveReplay = m["replay"] == "1",
            )
        }

        /**
         * What the player typed as the PC's address, as a host: "ws://192.168.1.20:4455/" and "192.168.1.20" are the
         * same PC. A port typed into the address wins over the port box only when the box is left at its default.
         */
        fun cleanHost(typed: String): Pair<String, Int?> {
            var h = typed.trim().removePrefix("ws://").removePrefix("wss://").removePrefix("http://").trimEnd('/')
            var port: Int? = null
            if (h.startsWith("[")) return h.substringBefore(']').removePrefix("[") to h.substringAfter("]:", "").toIntOrNull()
            if (h.count { it == ':' } == 1) { port = h.substringAfter(':').toIntOrNull(); h = h.substringBefore(':') }
            return h to port
        }
    }
}

/**
 * The scene the app wants OBS on, and when, given what the game says. Pure, with the time handed in, so the tests run
 * it on a clock of their own.
 *
 * The battle flag flickers: the tracker reads it from memory, which changes a frame or two apart at a battle's start and
 * end, and a scene switch on every flicker would thrash OBS. So a change of the flag counts only once it has held for
 * [settleMs]; a flicker shorter than that is never seen. A run's end is a latch, not a flicker, so it counts at once,
 * and a loss's game-over scene wins over the battle the run was lost in.
 *
 * The game stopping (Play left, or another game opened in it) is not a flicker either: [stopped] goes back to the game
 * scene at once, whatever the battle flag and the latch last said, since nothing will say otherwise until the game is
 * back (2026-10-05: leaving Play mid-battle left OBS on the battle scene). The next fact from the game ends that.
 */
class ObsDirector(private val settleMs: Long = SETTLE_MS) {
    private var battle = false
    private var battleSince = 0L
    private var settledBattle = false
    private var lost = false
    private var ended = false
    private var last: String? = null
    /** The game stopped ([stopped]) and has said nothing since. */
    private var away = false
    /** When a run ended and its replay is still to be saved, else null. */
    var replayAsked: Long? = null
        private set

    fun battle(on: Boolean, now: Long) {
        away = false
        if (on != battle) { battle = on; battleSince = now }
    }

    /** The run in play ended ([ended]) and, if so, whether it was lost. A new end asks for one replay save. */
    fun ended(ended: Boolean, lost: Boolean, now: Long) {
        away = false
        if (ended && !this.ended) replayAsked = now
        this.ended = ended
        this.lost = ended && lost
    }

    /**
     * The game stopped: back to the game scene at once, no settle. The battle is over as far as OBS goes (a battle the
     * game is still in when it comes back must hold [settleMs] again). The run's end is kept as it was, so the same end
     * coming back with the game is not a new one and asks for no second replay save.
     */
    fun stopped(now: Long) {
        away = true
        battle = false
        settledBattle = false
        battleSince = now
    }

    fun replaySaved() { replayAsked = null }

    /** The scene for this moment, or null for "no switch": the same scene as last time, or none set for the state. */
    fun scene(s: ObsSettings, now: Long): String? {
        if (battle != settledBattle && now - battleSince >= settleMs) settledBattle = battle
        val want = when {
            away -> s.overworldScene
            lost && s.gameOverScene.isNotBlank() -> s.gameOverScene
            // After a loss with no game-over scene set, nothing moves: the whiteout's walk home is not the overworld.
            lost -> null
            settledBattle -> s.battleScene
            else -> s.overworldScene
        }?.takeIf { it.isNotBlank() } ?: return null
        return if (want == last) null else want
    }

    /** [scene] was dealt with: sent, or left alone because OBS was elsewhere. Not asked again until it changes. */
    fun applied(scene: String) { last = scene }

    /** A new connection: the next scene goes out even if it was the last one sent (OBS may have moved meanwhile). */
    fun forget() { last = null }

    /** When the flag waiting to settle will have settled, or null when none is waiting. */
    fun wakeAt(): Long? = if (battle != settledBattle) battleSince + settleMs else null

    companion object {
        const val SETTLE_MS = 1200L
    }
}

/** How long to wait before the next try after [failures] failed ones in a row: 1, 2, 4, 8, 16, then 30 seconds. */
fun obsBackoffMs(failures: Int, steps: LongArray = OBS_BACKOFF): Long = steps[failures.coerceIn(0, steps.size - 1)]

val OBS_BACKOFF = longArrayOf(1000, 2000, 4000, 8000, 16000, 30000)

/**
 * The phone's link to OBS: one thread of its own that keeps a connection while the player has it turned on, and sends
 * the scene changes and the replay save the game calls for (Blake's streamer list, item 2).
 *
 * [battle], [runEnded] and [gameStopped] are what the game calls. They only note the fact and wake the thread: they never wait on
 * the network, never throw, and cost nothing with OBS closed or the link off. Everything that touches the network runs
 * on the link's thread. OBS closed, the Wi-Fi gone or a request refused are states the settings screen shows, retried
 * after [backoff] (1 s up to 30 s), never errors that reach the game. A password OBS turns down is not retried until
 * the settings change: it will not get better by asking again.
 *
 * Where the game's facts come from: StreamFeed, which Play composes for every game, hands over the tracker's
 * inBattle (Game Boy, GBA and DS) and the game-over latch's outcome for a Kaizo IronMON run (GameOverHost latches it).
 */
class ObsLink(
    private val clock: () -> Long = System::currentTimeMillis,
    private val backoff: LongArray = OBS_BACKOFF,
    settleMs: Long = ObsDirector.SETTLE_MS,
    /** A quiet link asks OBS for its version this often, so a closed OBS shows as such within this time. */
    private val keepAliveMs: Long = 15_000,
    /** A replay asked for while OBS was away is still worth saving this long after the end: the buffer still holds it. */
    private val replayGraceMs: Long = 15_000,
    private val connectMs: Int = 3000,
    private val ioMs: Int = 5000,
    /** Where the password is kept, given filesDir: sealed with the Keystore in the app, in memory in a test. */
    private val passwordAt: (File) -> com.ironmonone.app.SealedFile =
        { com.ironmonone.app.KeystoreSealedFile(File(it, PASSWORD_FILE), PASSWORD_ALIAS) },
) {
    enum class State { OFF, CONNECTING, CONNECTED, WAITING, STOPPED }

    /** What the settings screen shows: the state and one plain line about it. [note] is the last thing OBS turned down. */
    data class Status(val state: State, val line: String, val note: String = "")

    private val _status = MutableStateFlow(Status(State.OFF, OFF_LINE))
    val status: StateFlow<Status> get() = _status

    private val lock = Object()
    private val director = ObsDirector(settleMs)
    @Volatile private var settings = ObsSettings()
    /** Bumped whenever the settings change or the link is stopped: a thread from an older generation ends itself. */
    @Volatile private var generation = 0
    @Volatile private var thread: Thread? = null
    @Volatile private var conn: ObsSocket? = null
    @Volatile private var file: File? = null
    @Volatile private var sealed: com.ironmonone.app.SealedFile? = null

    /** What OBS was told, for the tests: "scene:<name>" or "replay". */
    internal val sent = java.util.concurrent.CopyOnWriteArrayList<String>()

    /** The battle flag as the tracker reads it now. Never blocks, never throws. */
    fun battle(on: Boolean) {
        try {
            synchronized(lock) { director.battle(on, clock()); lock.notifyAll() }
        } catch (_: Throwable) { }
    }

    /**
     * The run's end as the game-over latch has it: [ended] once it latched, [lost] for a loss. A retried battle opens
     * it again (false). Never blocks, never throws.
     */
    fun runEnded(ended: Boolean, lost: Boolean) {
        try {
            synchronized(lock) { director.ended(ended, lost, clock()); lock.notifyAll() }
        } catch (_: Throwable) { }
    }

    /**
     * The game stopped: Play was left, or the game in it was closed or swapped for another (StreamFeed's dispose). OBS
     * goes back to the game scene now, if it is on one of ours. Never blocks, never throws.
     */
    fun gameStopped() {
        try {
            synchronized(lock) { director.stopped(clock()); lock.notifyAll() }
        } catch (_: Throwable) { }
    }

    /**
     * Reads prep/obs-link.txt and the sealed password once and starts the link if it is on. Later calls do nothing.
     * Touches the Keystore: call it off the main thread.
     *
     * A file from before 2026-10-05 still has the password in it as plain text. It is sealed, then the file is written
     * again without it; if sealing fails the file is left as it was (the password still works) and the move is tried
     * again at the next start.
     */
    fun load(filesDir: File) {
        try {
            synchronized(lock) {
                if (file != null) return
                val f = File(filesDir, "prep/obs-link.txt")
                val box = passwordAt(filesDir)
                file = f
                sealed = box
                val saved = runCatching { ObsSettings.decode(f.readText()) }.getOrDefault(ObsSettings())
                val password = if (saved.password.isNotEmpty()) {
                    runCatching {
                        box.write(saved.password.toByteArray(Charsets.UTF_8))
                        if (!com.ironmonone.app.SafeWrite.text(f, saved.encode())) error("not rewritten")
                    }
                    saved.password
                } else runCatching { box.read()?.toString(Charsets.UTF_8) }.getOrNull().orEmpty()
                apply(saved.copy(password = password))
            }
        } catch (_: Throwable) { }
    }

    fun settings(): ObsSettings = settings

    /**
     * New settings from the screen: saved, and the link starts over with them (or stops). The password goes to the
     * sealed file (none: the file goes), the rest to prep/obs-link.txt. Touches the Keystore: call it off the main thread.
     */
    fun update(s: ObsSettings) {
        synchronized(lock) {
            sealed?.let { box ->
                runCatching { if (s.password.isEmpty()) box.clear() else box.write(s.password.toByteArray(Charsets.UTF_8)) }
            }
            file?.let { f -> runCatching { f.parentFile?.mkdirs(); com.ironmonone.app.SafeWrite.text(f, s.encode()) } }
            apply(s)
        }
    }

    /** Applies [s] without saving: the tests, and [load]. */
    internal fun apply(s: ObsSettings) {
        synchronized(lock) {
            settings = s
            generation++
            runCatching { conn?.close() }
            conn = null
            director.forget()
            if (!s.enabled) { publish(Status(State.OFF, OFF_LINE)); lock.notifyAll(); return }
            if (!s.usable) { publish(Status(State.OFF, whatIsMissing(s))); lock.notifyAll(); return }
            val gen = generation
            publish(Status(State.CONNECTING, "Connecting to OBS..."))
            thread = Thread({ run(gen) }, "obs-link").apply { isDaemon = true; start() }
            lock.notifyAll()
        }
    }

    /** Ends the link's thread and its connection. */
    fun stop() {
        synchronized(lock) {
            generation++
            runCatching { conn?.close() }
            conn = null
            publish(Status(State.OFF, OFF_LINE))
            lock.notifyAll()
        }
    }

    private fun publish(s: Status) { _status.value = s }

    private fun live(gen: Int) = gen == generation

    private fun run(gen: Int) {
        var failures = 0
        var nextTry = 0L
        var lastBeat = 0L
        var note = ""
        while (live(gen)) {
            try {
                val s = settings
                val now = clock()
                var c = conn
                if (c == null && now >= nextTry) {
                    publish(Status(State.CONNECTING, "Connecting to OBS at ${s.host}:${s.port}...", note))
                    try {
                        val opened = ObsSocket.open(s.host, s.port, s.password, connectMs, ioMs)
                        synchronized(lock) {
                            if (!live(gen)) { runCatching { opened.close() }; return }
                            conn = opened
                            director.forget()
                        }
                        c = opened
                        failures = 0
                        lastBeat = now
                        publish(Status(State.CONNECTED, connectedLine(opened), note))
                    } catch (e: ObsError) {
                        c = null
                        if (!live(gen)) return
                        if (e.final) {
                            publish(Status(State.STOPPED, e.message ?: "OBS refused.", note))
                            // Waits for new settings: apply() starts a new thread, and this one ends.
                            synchronized(lock) { while (live(gen)) lock.wait(1000) }
                            return
                        }
                        val wait = obsBackoffMs(failures++, backoff)
                        nextTry = clock() + wait
                        publish(Status(State.WAITING, (e.message ?: "OBS did not answer.") + " Trying again in ${secondsText(wait)}.", note))
                    }
                }
                if (c != null) {
                    try {
                        val n = work(c, s, note)
                        if (n != note) { note = n; publish(_status.value.copy(note = n)) }
                        if (clock() - lastBeat >= keepAliveMs) { c.call("GetVersion"); lastBeat = clock() }
                    } catch (e: ObsError) {
                        synchronized(lock) { if (conn === c) conn = null }
                        runCatching { c.close() }
                        if (!live(gen)) return
                        val wait = obsBackoffMs(failures++, backoff)
                        nextTry = clock() + wait
                        publish(Status(State.WAITING, "Lost OBS: ${e.message} Trying again in ${secondsText(wait)}.", note))
                    }
                }
                synchronized(lock) {
                    if (!live(gen)) return
                    val t = clock()
                    var until = t + 1000
                    if (conn == null) until = minOf(until, nextTry)
                    else {
                        until = minOf(until, lastBeat + keepAliveMs)
                        director.wakeAt()?.let { until = minOf(until, it) }
                    }
                    if (until > t && !pendingWork()) lock.wait((until - t).coerceIn(1, 1000))
                }
            } catch (e: InterruptedException) {
                return
            } catch (e: Throwable) {
                // Nothing here may end the app: a bug in a reply's shape is a note, and the link carries on.
                note = "Something unexpected came back from OBS."
                publish(_status.value.copy(note = note))
                runCatching { Thread.sleep(1000) }
            }
        }
    }

    /** True when a scene change or a replay save is waiting to go out now. Under [lock]. */
    private fun pendingWork(): Boolean = conn != null && (director.replayAsked != null || peekScene() != null)

    private fun peekScene(): String? = director.scene(settings, clock())

    /**
     * Sends what is due on [c]: the replay save, then the scene. Returns the note to show (what OBS last turned down).
     * A refusal is not a broken link; a broken link throws ObsError for [run] to retry.
     */
    private fun work(c: ObsSocket, s: ObsSettings, noteIn: String): String {
        var note = noteIn
        val (replay, scene) = synchronized(lock) {
            val asked = director.replayAsked
            if (asked != null && clock() - asked > replayGraceMs) director.replaySaved()
            (director.replayAsked != null && s.saveReplay) to director.scene(s, clock())
        }
        if (!s.saveReplay) synchronized(lock) { director.replaySaved() }
        if (replay) {
            try {
                c.call("SaveReplayBuffer")
                sent += "replay"
                note = ""
            } catch (e: ObsRefused) {
                note = if (e.code == ObsProtocol.STATUS_OUTPUT_NOT_RUNNING)
                    "The run ended, but OBS's replay buffer was not running. Start it in OBS (Controls, Start Replay Buffer)."
                else "OBS did not save the replay: ${e.comment.ifBlank { "code ${e.code}" }}"
            }
            synchronized(lock) { director.replaySaved() }
        }
        if (scene != null) {
            try {
                val now = c.call("GetCurrentProgramScene")
                val current = (now["sceneName"] ?: now["currentProgramSceneName"]) as? String
                // Only from a scene of ours: the streamer's own Starting soon or Be right back scene is left alone.
                if (current != null && current != scene && current in s.ownScenes) {
                    c.call("SetCurrentProgramScene", mapOf("sceneName" to scene))
                    sent += "scene:$scene"
                }
                // A switch that worked clears a switch that did not, and nothing else (the replay's note stays).
                if (note.startsWith(SCENE_NOTE)) note = ""
            } catch (e: ObsRefused) {
                note = "$SCENE_NOTE \"$scene\". Is it still called that? Test the connection to pick again."
            }
            synchronized(lock) { director.applied(scene) }
        }
        return note
    }

    private fun connectedLine(c: ObsSocket) = "Connected to OBS" + (c.obsWebSocketVersion.takeIf { it.isNotBlank() }?.let { " (WebSocket $it)" } ?: "") + "."

    private fun secondsText(ms: Long): String { val s = (ms + 999) / 1000; return if (s == 1L) "1 second" else "$s seconds" }

    /** What a Test connection found: OBS's scenes and whether its replay buffer is running, or why it could not. */
    data class Probe(val ok: Boolean, val line: String, val scenes: List<String> = emptyList(), val replayRunning: Boolean? = null)

    companion object {
        const val OFF_LINE = "Off."

        /** OBS's WebSocket password, sealed (KeystoreSealedFile), at the top of filesDir beside twitch-session.bin. */
        const val PASSWORD_FILE = "obs-password.bin"
        private const val PASSWORD_ALIAS = "kaizocore-obs-password"
        private const val SCENE_NOTE = "OBS could not switch to"

        /** Why [s], turned on, cannot run yet, in the words the screen shows. */
        fun whatIsMissing(s: ObsSettings): String = when {
            s.host.isBlank() -> "Enter your PC's address."
            s.port !in 1..65535 -> "The port is a number from 1 to 65535. OBS uses 4455."
            s.needsGameScene -> "Pick your game scene too: the app switches back to it."
            else -> "Pick your game scene, or turn on the replay save."
        }

        /** The app's one link. Play's StreamFeed feeds it, the Stream page sets it. */
        val app = ObsLink()

        /**
         * Connects once with [s], reads the scene list and the replay buffer's state, and hangs up. Blocks: call it off
         * the main thread. Never throws.
         */
        fun probe(s: ObsSettings, connectMs: Int = 3000, ioMs: Int = 5000): Probe = try {
            ObsSocket.open(s.host, s.port, s.password, connectMs, ioMs).use { c ->
                @Suppress("UNCHECKED_CAST")
                val scenes = (c.call("GetSceneList")["scenes"] as? List<Map<String, Any?>>).orEmpty()
                    .sortedBy { (it["sceneIndex"] as? Number)?.toInt() ?: 0 }.reversed()
                    .mapNotNull { it["sceneName"] as? String }
                val replay = runCatching { c.call("GetReplayBufferStatus")["outputActive"] as? Boolean }.getOrNull()
                Probe(true, "Connected. OBS has ${scenes.size} scene${if (scenes.size == 1) "" else "s"}.", scenes, replay)
            }
        } catch (e: ObsError) {
            Probe(false, e.message ?: "Could not reach OBS.")
        } catch (e: Throwable) {
            Probe(false, "Could not reach OBS.")
        }
    }
}
