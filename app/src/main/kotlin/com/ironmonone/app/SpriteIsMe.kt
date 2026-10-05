package com.ironmonone.app

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalContext
import com.ironmonone.core.Platform
import com.ironmonone.tracker.GameMap
import com.ironmonone.tracker.MemoryReader
import com.ironmonone.tracker.Overworld
import com.ironmonone.tracker.OverworldAddresses
import com.swordfish.libretrodroid.GLRetroView
import com.swordfish.libretrodroid.SpriteOverlay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * "Play as your Pokemon" (Sprite Is Me by UTDZac, MIT): turns the player's character into a Pokemon, or the
 * player's own sprite, while walking around a Gen 3 game.
 *
 * The emulator side does the part that has to happen between two frames of the game (libretrodroid's
 * sprite_overlay.cpp and sprite_core.h): read the player out of the game's RAM, hide the trainer in the game's
 * own OAM buffer, draw the sprite into the picture at the game's own pixels, faded as the game fades. This side
 * decides WHICH sprite and WHICH frame: the lead from the party, the facing and stepping from the game itself
 * (read back from the emulator side in one call, no lock), sleep after 55 seconds without input or while the lead
 * sleeps, faint at 0 HP. It hands the emulator side new pixels only when the frame changes.
 *
 * It works in every mode on Home, and asks nothing about the run: a Library game with no rules, a Kaizo IronMON
 * run, a Nuzlocke, a ROM hack. What it needs is a Gen 3 game whose ROM header the tracker knows, and it refuses
 * to guess beyond that (see the emulator side's checks). One line in PlayScreen, [SpriteIsMeHost], is all the
 * Play screen knows of it.
 */

/** Why the section is or is not on offer for the game that is running. */
object SpriteIsMeSupport {
    sealed interface State {
        /** No game yet, or still looking. */
        object Unknown : State
        /** Not a Game Boy Advance game. */
        object NotGba : State
        data class Supported(val game: String) : State
        /** A GBA game this does not work on, with the line to show. */
        data class Unsupported(val why: String) : State
    }

    var state: State by mutableStateOf(State.Unknown)

    /**
     * What to tell the player about their own sprite when it cannot be drawn: a sheet past the bound, or art that would
     * not decode, either way played as the lead instead (rc32 audit P2 #88, P3 #66). Null when there is nothing to say.
     * Set as the art is prepared (AndroidSpriteArt.prepare).
     */
    var ownNote: String? by mutableStateOf(null)
}

/** The emulator side, as the engine sees it. The phone's is [JniOverlayPort]; the tests count calls on a fake. */
interface SpriteOverlayPort {
    fun configure(words: LongArray): Boolean
    fun setEnabled(on: Boolean)
    fun setSprite(argb: IntArray, w: Int, h: Int, ox: Int, oy: Int): Boolean
    fun clearSprite()
    fun reset()
    /** Frame counter, replaced, stepping, facing: the emulator side's last frame in one number. */
    fun snapshot(): Long
}

object JniOverlayPort : SpriteOverlayPort {
    override fun configure(words: LongArray) = SpriteOverlay.configure(words)
    override fun setEnabled(on: Boolean) = SpriteOverlay.setEnabled(on)
    override fun setSprite(argb: IntArray, w: Int, h: Int, ox: Int, oy: Int) = SpriteOverlay.setSprite(argb, w, h, ox, oy)
    override fun clearSprite() = SpriteOverlay.clearSprite()
    override fun reset() = SpriteOverlay.reset()
    override fun snapshot() = SpriteOverlay.snapshot()
}

/** The emulator side's last frame, unpacked (the layout is in SpriteOverlay.java and sprite_core.h's packSnapshot). */
object SpriteSnapshot {
    fun frames(s: Long): Long = s and 0xFFFFFFFFL
    fun replaced(s: Long): Boolean = ((s ushr 32) and 1L) != 0L
    fun stepping(s: Long): Boolean = ((s ushr 33) and 1L) != 0L
    fun facing(s: Long): Int = ((s ushr 34) and 7L).toInt()
    fun wanted(s: Long): Boolean = ((s ushr 37) and 1L) != 0L
}

/** What the engine draws with: the Walking Pals sheets, and the player's own. Android decodes them; the tests give plain pixels. */
interface SpriteIsMeArt {
    /** The sheets of species [id], numbered as [dex] says (WalkingPals.Index.find), when it has an idle sheet to draw; else null. */
    fun pal(id: Int, dex: WalkingPals.Dex): WalkingPals.Pal?
    /** [pal] as [look] says: its form, then its shiny, each where it ships (WalkingPals.Index.look); [pal] itself here. */
    fun look(pal: WalkingPals.Pal, look: WalkingPals.Look): WalkingPals.Pal = pal
    fun palSheets(pal: WalkingPals.Pal): Map<WalkingPals.Anim, WalkingPals.Sheet>?
    fun palFrame(pal: WalkingPals.Pal, anim: WalkingPals.Anim, sheet: WalkingPals.Sheet, row: Int, index: Int): ArtPixels?
    /** The imported picture or sheet set is on disk and readable. */
    fun ownReady(): Boolean
    fun ownSheets(): OwnSheets?
    fun ownFrame(anim: WalkingPals.Anim, sheet: WalkingPals.Sheet, row: Int, index: Int): ArtPixels?
    fun ownPicture(): SpriteArt.Fitted?

    /**
     * Grows each time something a frame is drawn with becomes ready: the Walking Pals tables (read on a thread of their
     * own), a Pokemon's sheets or the player's own art (both decoded off the main thread). The emulator side steps only
     * while it has a picture, so its snapshot stands still while the art is on its way, and a tick that found it not
     * ready was never repeated: a resumed run stayed the trainer until the switch was turned off and on (2026-10-03).
     */
    fun readiness(): Int
}

/** The player's sheet set as the animator wants it: the sheets and how many rows (facings) each has. */
class OwnSheets(val sheets: Map<WalkingPals.Anim, WalkingPals.Sheet>, val rows: Map<WalkingPals.Anim, Int>)

/**
 * One tick of the sprite choice, per display frame, with nothing Android in it so the tests drive it with a fake
 * port and fake art. With the switch off a tick makes no call at all after the one that switches the emulator
 * side off; with it on, a tick makes one cheap read and, only when the frame changed, one push. [now] is the clock the
 * game's own steps are timed by and [afk] the phone's idle clock (no key for 55 seconds).
 */
class SpriteIsMeEngine(
    private val port: SpriteOverlayPort,
    private val art: SpriteIsMeArt,
    private val now: () -> Long = System::nanoTime,
    private val afk: () -> Boolean,
) {
    /** The lead Pokemon, kept up to date by whoever reads the party (a few times a second). */
    @Volatile var lead: SpriteIsMeLogic.Lead? = null

    private data class Key(val source: SpriteIsMeLogic.Source, val pal: WalkingPals.Pal?, val anim: WalkingPals.Anim?, val row: Int, val index: Int, val flip: Boolean, val bob: Int, val art: Int)

    private val animator = SpriteIsMeLogic.Animator()
    private var nativeOn = false
    private var pushed: Key? = null
    private var facing = 1
    private var configured = false
    private var seen = false
    private var seenSnap = 0L
    private var seenWho = SpriteIsMeSettings.Who.LEAD
    private var seenAlways = 0
    private var seenAlwaysShiny = false
    private var seenOwn = SpriteIsMeSettings.Own.NONE
    private var seenArt = 0
    private var seenLead: SpriteIsMeLogic.Lead? = null
    private var seenAfk = false
    private var seenReady = 0
    /** When the game last showed the player stepping or turning, by [now]; null until it has. */
    private var gameMovedAt: Long? = null

    /** Give the emulator side this game's addresses. */
    fun configure(a: OverworldAddresses): Boolean {
        configured = port.configure(a.toConfig())
        pushed = null
        seen = false
        nativeOn = false
        return configured
    }

    val isOn: Boolean get() = nativeOn

    fun tick() {
        if (!configured) return
        val s = SpriteIsMeSettings
        if (!s.on) {
            if (nativeOn) { port.setEnabled(false); nativeOn = false; pushed = null }
            return
        }
        if (!nativeOn) { port.setEnabled(true); nativeOn = true; animator.reset(); pushed = null; seen = false }

        val snap = port.snapshot()
        val frames = SpriteSnapshot.frames(snap)
        val movingNow = SpriteSnapshot.stepping(snap)
        val facingNow = SpriteSnapshot.facing(snap)
        // The game's own steps and turns are the player's doing, whatever path the keys came by. Only the phone's idle
        // clock counted, and keys that never reached it (the test bot's) left every sprite asleep 55 seconds in: one pose
        // in every direction through a walk (2026-10-03). A walk held past 55 seconds did the same.
        val t = now()
        if (movingNow || (facingNow in 1..4 && facingNow != facing)) gameMovedAt = t
        if (facingNow in 1..4) facing = facingNow

        // Nothing moved since the last tick (the emulator is paused, or between frames on a fast display): nothing to work out.
        // Compared value by value: a hash of them collided in a test (two different snapshots, one hash) and froze the sprite.
        // The art's readiness is one of the things that move: while it is on its way the snapshot stands still.
        val leadNow = lead
        val afkNow = afk() && gameMovedAt.let { it == null || t - it >= SpriteIsMeLogic.IDLE_NANOS }
        val readyNow = art.readiness()
        if (seen && snap == seenSnap && s.who == seenWho && s.always == seenAlways && s.alwaysShiny == seenAlwaysShiny &&
            s.own == seenOwn && s.artVersion == seenArt && leadNow == seenLead && afkNow == seenAfk && readyNow == seenReady) return
        seen = true; seenSnap = snap; seenWho = s.who; seenAlways = s.always; seenAlwaysShiny = s.alwaysShiny
        seenOwn = s.own; seenArt = s.artVersion; seenLead = leadNow; seenAfk = afkNow; seenReady = readyNow

        val ownReady = art.ownReady()
        val choice = SpriteIsMeLogic.choose(s.who, s.always, s.own, ownReady, leadNow, art::pal, s.alwaysShiny, art::look)
        val out = resolve(choice, frames, movingNow, facingNow)
        if (out == null) {
            if (pushed != null) { port.clearSprite(); pushed = null }
            return
        }
        val (key, pixels, ox, oy) = out
        // No pixels: the frame is the one already pushed, and nothing was copied for it.
        if (pixels != null && key != pushed) {
            if (port.setSprite(pixels.argb, pixels.w, pixels.h, ox, oy)) pushed = key
        }
    }

    fun stop() {
        if (nativeOn) port.setEnabled(false)
        port.clearSprite()
        port.reset()
        nativeOn = false; pushed = null; configured = false
    }

    /** A frame to show: its key, and its pixels, which are null when it is the frame already pushed. */
    private data class Out(val key: Key, val pixels: ArtPixels?, val ox: Int, val oy: Int)

    /**
     * The frame to show now, or null for none. The key comes first and the pixels only for a new one: the snapshot
     * changes every emulated frame, so this runs 60 times a second on the main thread, and it used to crop, copy and
     * scan a frame each time only to find it was the one already shown (rc32 audit P3 #65).
     */
    private fun resolve(c: SpriteIsMeLogic.Choice, frames: Long, moving: Boolean, facingNow: Int): Out? {
        val version = SpriteIsMeSettings.artVersion
        when (c.source) {
            SpriteIsMeLogic.Source.NONE -> return null
            SpriteIsMeLogic.Source.PICTURE -> {
                val fitted = art.ownPicture() ?: return null
                val flip = facing == 3
                val bob = SpriteArt.bobOffset(moving, (frames / SpriteIsMeLogic.BOB_FRAMES).toInt())
                val key = Key(c.source, null, null, 0, 0, flip, bob, version)
                if (key == pushed) return Out(key, null, 0, 0)
                val px = if (flip) SpriteArt.flipH(fitted.pixels) else fitted.pixels
                return Out(key, px, fitted.ox, fitted.oy + bob)
            }
            SpriteIsMeLogic.Source.PAL -> {
                val pal = c.pal ?: return null
                val sheets = art.palSheets(pal) ?: return null
                val f = animator.frame(frames, moving, facingNow, seenAfk, c.lead, sheets) { if (it == WalkingPals.Anim.IDLE || it == WalkingPals.Anim.WALK) 8 else 1 } ?: return null
                val sheet = sheets[f.anim] ?: return null
                // A walk frame that stays up longer than two steps steps as a picture does, a pixel up on every other
                // beat, so every Pokemon shows its walk (Blake, 2026-10-03: "Fix it for all sprites"): a walk of one
                // frame, no walk and an idle of one (11 shipped sheets), and Silcoon's and Cascoon's 120-frame hold.
                val walking = SpriteIsMeLogic.wantedAnim(seenAfk, c.lead, moving) == WalkingPals.Anim.WALK
                val held = sheet.durations.size < 2 || sheet.durations[f.index] > SpriteIsMeLogic.HELD_FRAMES
                val bob = if (walking && held) SpriteArt.bobOffset(true, (frames / SpriteIsMeLogic.BOB_FRAMES).toInt()) else 0
                val key = Key(c.source, pal, f.anim, f.row, f.index, false, bob, version)
                if (key == pushed) return Out(key, null, 0, 0)
                val px = art.palFrame(pal, f.anim, sheet, f.row, f.index) ?: return null
                // A few later sheets have frames past what the emulator side takes, mostly clear: cut to what shows.
                val placed = SpriteArt.placeFrame(px, sheet.x, sheet.y)
                return Out(key, placed.pixels, placed.ox, placed.oy + bob)
            }
            SpriteIsMeLogic.Source.SHEET -> {
                val own = art.ownSheets() ?: return null
                val f = animator.frame(frames, moving, facingNow, seenAfk, c.lead, own.sheets) { own.rows[it] ?: 1 } ?: return null
                val sheet = own.sheets[f.anim] ?: return null
                val key = Key(c.source, null, f.anim, f.row, f.index, false, 0, version)
                if (key == pushed) return Out(key, null, 0, 0)
                val px = art.ownFrame(f.anim, sheet, f.row, f.index) ?: return null
                return Out(key, px, sheet.x, sheet.y)
            }
        }
    }
}

/** What the Play screen calls. Starts and stops the engine with the game view, and reads the party while it is on. */
@Composable
fun SpriteIsMeHost(retro: GLRetroView?, platform: Platform) {
    val ctx = LocalContext.current.applicationContext
    SpriteIsMeSettings.ensureLoaded(ctx.filesDir)
    val gba = platform == Platform.GBA
    LaunchedEffect(retro, gba) {
        if (!gba) { SpriteIsMeSupport.state = SpriteIsMeSupport.State.NotGba; return@LaunchedEffect }
        if (retro == null) { SpriteIsMeSupport.state = SpriteIsMeSupport.State.Unknown; return@LaunchedEffect }
        SpriteIsMeRunner.run(ctx, retro)
    }
    DisposableEffect(retro) {
        onDispose {
            runCatching { SpriteIsMeRunner.stopNative() }
            SpriteIsMeSupport.state = SpriteIsMeSupport.State.Unknown
            AutoTheme.heartSoul = false
        }
    }
}

/** The part of the host that talks to the game: finds out which game it is, then feeds the engine. */
internal object SpriteIsMeRunner {
    /** The messages the tracker's resolve() raises for a core that has not started, which is worth another try. */
    private const val UNREADABLE = "ROM header unreadable"

    /** The game's own language: the fourth letter of its game code, E for the US English builds these addresses are. */
    const val ENGLISH = 'E'

    @Volatile private var engine: SpriteIsMeEngine? = null

    fun stopNative() {
        engine?.stop()
        engine = null
    }

    suspend fun run(ctx: Context, retro: GLRetroView) {
        val reader = MemoryReader { a, n -> retro.readMemory(a, n) }
        val map = resolve(reader) ?: return
        // Which numbering the auto themes read the lead by (AutoTheme.heartSoul); this is where Play names its game.
        AutoTheme.heartSoul = map.hns
        // The table for a retail game; for Nat. Dex and MaxDex, whose layouts are in no table, the addresses read out of the
        // game's own code (tracker-gba's OverworldScan, once, off the main thread).
        val addresses = withContext(Dispatchers.Default) { runCatching { Overworld.resolve(map, reader) }.getOrNull() }
        if (addresses == null) {
            SpriteIsMeSupport.state = SpriteIsMeSupport.State.Unsupported(refusal(map))
            return
        }
        val art = AndroidSpriteArt(ctx)
        val eng = SpriteIsMeEngine(JniOverlayPort, art, afk = {
            System.nanoTime() - SpriteMotion.lastInputNanos >= WalkingPals.IDLE_SECONDS_UNTIL_SLEEP * 1_000_000_000L
        })
        if (!eng.configure(addresses)) {
            SpriteIsMeSupport.state = SpriteIsMeSupport.State.Unsupported(SpriteIsMeCopy.NOT_KNOWN)
            return
        }
        engine = eng
        SpriteIsMeSupport.state = SpriteIsMeSupport.State.Supported(addresses.name)
        kotlinx.coroutines.coroutineScope {
            // What the frames draw, decoded here and never in a tick: the player's own art each time it changes, and the
            // Pokemon played as (rc32 audit P2 #88, #106).
            launch(Dispatchers.Default) { art.prepare() }
            // The party, a few times a second and off the main thread: it takes the core's lock, as the tracker's reads do.
            launch(Dispatchers.Default) {
                while (true) {
                    if (SpriteIsMeSettings.on) {
                        val r = runCatching { SpriteLead.read(reader, map) }.getOrNull()
                        when (r) {
                            is SpriteLead.Reading.Found -> eng.lead = r.lead
                            SpriteLead.Reading.None -> eng.lead = null
                            else -> {}
                        }
                        delay(500)
                    } else {
                        snapshotFlow { SpriteIsMeSettings.on }.first { it }
                    }
                }
            }
            // The frames: one tick per display frame while the switch is on, and no wakeups at all while it is off.
            while (true) {
                if (!SpriteIsMeSettings.on) {
                    eng.tick()   // switches the emulator side off, once
                    snapshotFlow { SpriteIsMeSettings.on }.first { it }
                }
                withFrameNanos { }
                eng.tick()
            }
        }
    }

    /**
     * The line for a GBA game whose overworld [Overworld.resolve] did not find: a MaxDex or Nat. Dex build's, a game named
     * by its header whose code moved, as a hack built from the decompilations does (rc32 audit P2 #87: it showed the
     * switch and never changed the character, with no word), or a game this does not know.
     */
    internal fun refusal(map: GameMap): String = when {
        map.nameSet == "maxdex" -> SpriteIsMeCopy.MAX_DEX
        map.hns -> SpriteIsMeCopy.HEART_SOUL
        Overworld.isNatDex(map) -> SpriteIsMeCopy.NAT_DEX
        Overworld.hasTable(map) -> SpriteIsMeCopy.NO_OVERWORLD
        else -> SpriteIsMeCopy.NOT_KNOWN
    }

    /**
     * The tracker's own reading of which game this is (the ROM header), retried while the core starts. Null when the
     * game is not one this can work on, with the state set to say why.
     */
    private suspend fun resolve(reader: MemoryReader): GameMap? {
        while (true) {
            val code = withContext(Dispatchers.Default) { runCatching { reader.read(0x080000AC, 4) }.getOrDefault(ByteArray(0)) }
            if (code.size == 4) {
                if (code[3].toInt().toChar() != ENGLISH) {
                    SpriteIsMeSupport.state = SpriteIsMeSupport.State.Unsupported(SpriteIsMeCopy.NOT_KNOWN)
                    return null
                }
                val outcome = withContext(Dispatchers.Default) { runCatching { GameMap.resolve(reader) } }
                val map = outcome.getOrNull()
                if (map != null) return map
                val msg = outcome.exceptionOrNull()?.message.orEmpty()
                if (msg == UNREADABLE) { delay(700); continue }
                SpriteIsMeSupport.state = SpriteIsMeSupport.State.Unsupported(SpriteIsMeCopy.NOT_KNOWN)
                return null
            }
            delay(700)
        }
    }
}
