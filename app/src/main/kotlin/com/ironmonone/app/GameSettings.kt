package com.ironmonone.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import kotlinx.coroutines.flow.collectLatest
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

    /**
     * Whole or not at all (SafeWrite). When the first rename failed this deleted the file and renamed again, so a kill in
     * between left the game with no settings at all, and nothing synced the temp file first (RC35-NOTICED N #4).
     */
    private fun write(f: File, p: Properties) {
        runCatching {
            val bytes = java.io.ByteArrayOutputStream().also { p.store(it, null) }.toByteArray()
            SafeWrite.bytes(f, bytes)
        }
    }
}

/**
 * The landscape panes for one Play visit: the tracker column's share of the window and the floating window's frame
 * (rc32 audit P2 #46). Both were rebuilt from the values read when the game opened whenever the window changed size,
 * that is on every rotation, and the reset was then saved over the player's choice; a portrait window's clamp went
 * into the floating window's saved frame. Now the share is kept and the width follows the window there is, and the
 * frame is the one last set (FloatingTracker only draws, and so only moves it, in landscape; it clamps it to the
 * window it is drawn in).
 *
 * Play reads none of this in its own body, so a drag recomposes the column or the window alone, not the screen
 * (rc32 audit P2 #56): it is saved through [saveWith].
 */
class PaneSizes(fraction: Float?, frame: List<Float>?) {
    /** The tracker column as a share of the window's width. */
    var fraction by androidx.compose.runtime.mutableStateOf(fraction?.takeIf { it in 0.1f..0.9f } ?: DEFAULT_FRACTION)
    /** The floating window's frame in dp, null until it has been moved or sized: then the default place is used. */
    var frame by androidx.compose.runtime.mutableStateOf(frame?.takeIf { it.size == 4 }?.let { FloatFrame(it[0], it[1], it[2], it[3]) })

    /** The column's width in a window [windowW] dp wide. */
    fun width(windowW: Float): Float = windowW * fraction

    /**
     * The tracker's left edge dragged [dx] dp to the right in a window [windowW] wide (TrackerEdge.kt): the tracker
     * keeps at least 150 dp and the game at least 170, because the game column is weighted and has no minimum of its own.
     */
    fun drag(dx: Float, windowW: Float) {
        if (windowW <= 0f) return
        val max = (windowW - 170f).coerceAtLeast(150f)
        fraction = (width(windowW) - dx).coerceIn(150f, max) / windowW
    }

    /** The frame the floating window opens at in a [windowW] by [windowH] window. */
    fun frameFor(windowW: Float, windowH: Float): FloatFrame = frame ?: FloatFrame.default(windowW, windowH)

    /**
     * Saves the visit's settings 300 ms after the last change (a drag changes these on every event), until cancelled.
     * The panes are read through snapshotFlow, so their changes reach the disk without recomposing Play; [speed],
     * [muted] and [dsTopOnly] are the caller's keys. A share outside 0.1..0.9 is not kept, as before.
     */
    suspend fun saveWith(speed: Int, muted: Boolean, dsTopOnly: Boolean, save: (GameSettings.Values) -> Unit) {
        androidx.compose.runtime.snapshotFlow { fraction to frame }.collectLatest { (f, fr) ->
            kotlinx.coroutines.delay(300)
            val v = GameSettings.Values(speed, muted, dsTopOnly, f.takeIf { it in 0.1f..0.9f }, fr?.let { listOf(it.x, it.y, it.w, it.h) })
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { save(v) }
        }
    }

    companion object {
        /**
         * The column's share by default, from the reference streaming layout: about a quarter of the width for the
         * camera and tracker stack, leaving three quarters for the game. A fixed 340dp took about 40% of a landscape
         * phone, which both squeezed the game and letterboxed it.
         */
        const val DEFAULT_FRACTION = 0.26f
    }
}
