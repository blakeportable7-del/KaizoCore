package com.ironmonone.app

import java.io.File
import java.util.Properties

/**
 * Per-game settings, keyed by the session id (GameSession.id).
 *
 * What a standard emulator remembers per title rather than per app: turbo
 * speed, mute, the DS one-screen mode and the landscape tracker width. Before
 * this, speed and mute were app-wide (a deliberate choice for NEW RUN, which
 * still holds: the IronMON run is one session id, so a re-roll keeps them),
 * and the DS screen mode and tracker width were forgotten on every visit.
 *
 * Speed and mute keep a global "last used" copy. A game opened for the first
 * time inherits the desk setup the player last had, which is what the
 * pre-session app did for every game; a game that has its own file wins.
 *
 * Plain .properties files under prep/games/, one per session id, so a
 * corrupt or missing file falls back to defaults rather than failing.
 */
class GameSettings(private val dir: File) {

    init { dir.mkdirs() }

    data class Values(
        val speed: Int = 1,
        val muted: Boolean = false,
        val dsTopOnly: Boolean = false,
        /** Landscape tracker pane, as a fraction of window width; null = the default. */
        val trackerFraction: Float? = null,
        /** The floating tracker's frame in dp (x, y, w, h); null = the default placement. */
        val floatFrame: List<Float>? = null,
    )

    private fun file(id: String) = File(dir, id.replace(Regex("[^A-Za-z0-9._-]"), "_") + ".properties")
    private val global = File(dir, "_last.properties")

    private fun read(f: File): Properties? =
        f.takeIf { it.exists() }?.let { p -> runCatching { Properties().apply { p.inputStream().use { load(it) } } }.getOrNull() }

    private fun Properties.speed() = getProperty("speed")?.trim()?.toIntOrNull()?.takeIf { it in 1..16 }
    private fun Properties.bool(k: String) = getProperty(k)?.trim()?.let { it == "1" || it == "true" }

    fun load(id: String): Values {
        val own = read(file(id))
        val last = read(global)
        return Values(
            speed = own?.speed() ?: last?.speed() ?: 1,
            muted = own?.bool("muted") ?: last?.bool("muted") ?: false,
            dsTopOnly = own?.bool("dsTopOnly") ?: false,
            trackerFraction = own?.getProperty("trackerFraction")?.trim()?.toFloatOrNull()
                ?.takeIf { it in 0.1f..0.9f },
            floatFrame = own?.getProperty("floatFrame")?.split(',')?.mapNotNull { it.trim().toFloatOrNull() }?.takeIf { it.size == 4 },
        )
    }

    fun save(id: String, v: Values) {
        val p = Properties().apply {
            setProperty("speed", v.speed.toString())
            setProperty("muted", if (v.muted) "1" else "0")
            setProperty("dsTopOnly", if (v.dsTopOnly) "1" else "0")
            v.trackerFraction?.let { setProperty("trackerFraction", it.toString()) }
            v.floatFrame?.takeIf { it.size == 4 }?.let { setProperty("floatFrame", it.joinToString(",")) }
        }
        write(file(id), p)
        // The desk setup a new game inherits: speed and mute only.
        val g = read(global) ?: Properties()
        g.setProperty("speed", v.speed.toString())
        g.setProperty("muted", if (v.muted) "1" else "0")
        write(global, g)
    }

    private fun write(f: File, p: Properties) {
        runCatching {
            val tmp = File(f.parentFile, f.name + ".tmp")
            tmp.outputStream().use { p.store(it, null) }
            if (!tmp.renameTo(f)) { f.delete(); tmp.renameTo(f) }
        }
    }
}
