package com.ironmonone.app

import com.ironmonone.app.WalkingPals.Anim as A
import com.ironmonone.tracker.Overworld
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The frontend half of "Play as your Pokemon", driven tick by tick with a fake emulator side and fake art: what it
 * asks the emulator side to draw and when. Above all that a switch that is off costs nothing: no call per frame, and
 * so no write.
 */
class SpriteIsMeEngineTest {
    /** The emulator side, recording every call made to it. */
    private class FakePort : SpriteOverlayPort {
        val calls = ArrayList<String>()
        var snap = 0L
        var accept = true
        var sprite: Sprite? = null

        class Sprite(val argb: IntArray, val w: Int, val h: Int, val ox: Int, val oy: Int)

        override fun configure(words: LongArray): Boolean { calls += "configure(${words.size})"; return accept }
        override fun setEnabled(on: Boolean) { calls += "enabled=$on" }
        override fun setSprite(argb: IntArray, w: Int, h: Int, ox: Int, oy: Int): Boolean { calls += "sprite"; sprite = Sprite(argb, w, h, ox, oy); return true }
        override fun clearSprite() { calls += "clear"; sprite = null }
        override fun reset() { calls += "reset" }
        override fun snapshot(): Long { calls += "snapshot"; return snap }

        fun count(prefix: String) = calls.count { it.startsWith(prefix) }
    }

    /** Frame [frames], and what the game says; packed as the emulator side packs it. */
    private fun snap(frames: Long, moving: Boolean = false, facing: Int = 1, replaced: Boolean = true, wanted: Boolean = true): Long =
        (frames and 0xFFFFFFFFL) or ((if (replaced) 1L else 0L) shl 32) or ((if (moving) 1L else 0L) shl 33) or (facing.toLong() shl 34) or ((if (wanted) 1L else 0L) shl 37)

    /**
     * Art whose every pixel says which frame it is: 0xFF, animation, row, index. Four Gen 1-3 Pokemon by Gen 3's ids,
     * and one bigger than the emulator side takes ([BIG], a Nat. Dex build's id), whose frames are clear but for a
     * 100 x 80 block 20 in and 30 down.
     */
    private class FakeArt(var ownPictureReady: Boolean = false, var ownSheetsReady: Boolean = false) : SpriteIsMeArt {
        /** The tables are read ([pal] finds Pokemon) and their sheets decoded ([palSheets]): true unless a test says not yet. */
        var palsReady = true
        var sheetsReady = true
        /** What [readiness] says; a test bumps it when it makes something ready, as AndroidSpriteArt's decodes do. */
        var ready = 0
        val species = setOf(25, 94, 133, 277, ONE_STEP, HELD_STEP)
        val sheets = mapOf(
            WalkingPals.Anim.IDLE to WalkingPals.Sheet(32, 40, 1, 4, intArrayOf(40, 6, 6)),
            WalkingPals.Anim.WALK to WalkingPals.Sheet(32, 32, 1, 7, intArrayOf(8, 10, 8, 10)),
            WalkingPals.Anim.SLEEP to WalkingPals.Sheet(24, 32, 5, 7, intArrayOf(30, 35)),
            WalkingPals.Anim.FAINT to WalkingPals.Sheet(40, 32, 0, 5, intArrayOf(8, 12, 4, 10)),
        )
        val bigPal = WalkingPals.Pal(WalkingPals.Pack.NATIONAL, "384-mega")
        val bigSheets = mapOf(WalkingPals.Anim.IDLE to WalkingPals.Sheet(144, 136, -55, -25, intArrayOf(10, 10)))
        val own = OwnSheets(mapOf(
            WalkingPals.Anim.IDLE to WalkingPals.Sheet(32, 32, 0, 0, intArrayOf(10, 10)),
            WalkingPals.Anim.WALK to WalkingPals.Sheet(32, 32, 0, 0, intArrayOf(5, 5, 5)),
        ), mapOf(WalkingPals.Anim.IDLE to 8, WalkingPals.Anim.WALK to 8))
        /** [ONE_STEP]'s: a walk of one frame, as Eternamax Eternatus's is. */
        val oneStepSheets = mapOf(
            WalkingPals.Anim.IDLE to WalkingPals.Sheet(32, 40, 1, 4, intArrayOf(40, 6, 6)),
            WalkingPals.Anim.WALK to WalkingPals.Sheet(32, 32, 1, 7, intArrayOf(16)),
        )
        /** [HELD_STEP]'s: a twitch and then one frame held 120 game frames, as Silcoon's and Cascoon's walks are. */
        val heldStepSheets = mapOf(
            WalkingPals.Anim.IDLE to WalkingPals.Sheet(32, 40, 1, 4, intArrayOf(2, 2, 120)),
            WalkingPals.Anim.WALK to WalkingPals.Sheet(32, 32, 1, 7, intArrayOf(2, 2, 120)),
        )
        override fun pal(id: Int, dex: WalkingPals.Dex) = when {
            !palsReady -> null
            id == BIG && dex == WalkingPals.Dex.NAT_DEX -> bigPal
            id in species && id <= 411 -> WalkingPals.Pal(WalkingPals.Pack.GEN3, id.toString())
            else -> null
        }
        /** Every one of these has a shiny: its frames carry [SHINY]. */
        override fun look(pal: WalkingPals.Pal, look: WalkingPals.Look) = pal.copy(shiny = look.shiny)
        /** Frames cropped out of a sheet: on the phone, each a new array and a copy of its pixels. */
        var palFrames = 0
        var ownFrames = 0
        override fun palSheets(pal: WalkingPals.Pal) = when {
            !sheetsReady -> null
            pal == bigPal -> bigSheets
            pal == WalkingPals.Pal(WalkingPals.Pack.GEN3, ONE_STEP.toString()) -> oneStepSheets
            pal == WalkingPals.Pal(WalkingPals.Pack.GEN3, HELD_STEP.toString()) -> heldStepSheets
            else -> sheets.takeIf { pal.pack == WalkingPals.Pack.GEN3 && pal.key.toInt() in species }
        }
        override fun readiness() = ready
        override fun palFrame(pal: WalkingPals.Pal, anim: WalkingPals.Anim, sheet: WalkingPals.Sheet, row: Int, index: Int): ArtPixels {
            palFrames++
            return palPixels(pal, anim, sheet, row, index)
        }
        private fun palPixels(pal: WalkingPals.Pal, anim: WalkingPals.Anim, sheet: WalkingPals.Sheet, row: Int, index: Int): ArtPixels =
            if (pal == bigPal) ArtPixels(sheet.w, sheet.h, IntArray(sheet.w * sheet.h) { i ->
                val x = i % sheet.w; val y = i / sheet.w
                if (x in 20 until 120 && y in 30 until 110) color(anim, row, index) else 0
            })
            else ArtPixels(sheet.w, sheet.h, IntArray(sheet.w * sheet.h) { color(anim, row, index) or (if (pal.shiny) SHINY else 0) })
        override fun ownReady() = ownPictureReady || ownSheetsReady
        override fun ownSheets() = own.takeIf { ownSheetsReady }
        override fun ownFrame(anim: WalkingPals.Anim, sheet: WalkingPals.Sheet, row: Int, index: Int): ArtPixels {
            ownFrames++
            return ArtPixels(sheet.w, sheet.h, IntArray(sheet.w * sheet.h) { color(anim, row, index) })
        }
        override fun ownPicture(): SpriteArt.Fitted? = SpriteArt.Fitted(ArtPixels(2, 1, intArrayOf(0xFFFF0000.toInt(), 0xFF0000FF.toInt())), 15, 31).takeIf { ownPictureReady }
        companion object {
            const val BIG = 1098
            const val ONE_STEP = 151
            const val HELD_STEP = 152
            /** A bit no plain frame's pixel has: the shiny's. */
            const val SHINY = 0x800000
            fun color(anim: WalkingPals.Anim, row: Int, index: Int) = 0xFF000000.toInt() or (anim.ordinal shl 16) or (row shl 8) or index
        }
    }

    private val S = SpriteIsMeSettings
    private var afk = false
    /** The clock the game's own steps are timed by (SpriteIsMeEngine's now). */
    private var clock = 0L
    private lateinit var port: FakePort
    private lateinit var art: FakeArt
    private lateinit var engine: SpriteIsMeEngine

    @BeforeTest fun setUp() {
        S.load(java.nio.file.Files.createTempFile("sim", ".txt").toFile().also { it.delete() })
        afk = false
        clock = 0L
        port = FakePort(); art = FakeArt()
        engine = SpriteIsMeEngine(port, art, { clock }) { afk }
        assertTrue(engine.configure(Overworld.FIRERED_U_V10))
        S.on = true
    }

    @AfterTest fun tearDown() { S.reset() }

    private fun shown(): Int = port.sprite!!.argb[0]

    // ------------------------------------------------------------------ off costs nothing

    @Test
    fun `switched off, no tick calls anything, and the emulator side is only ever told to stop`() {
        S.on = false
        val before = port.calls.toList()
        repeat(500) { engine.tick() }
        assertEquals(before, port.calls, "a switch that was never on makes no call per frame")
        assertFalse(engine.isOn)
        // On, running, then off: exactly one call to switch it off, and then silence.
        S.on = true
        port.snap = snap(10)
        engine.lead = SpriteIsMeLogic.Lead(25, 30, false)
        engine.tick(); assertTrue(engine.isOn)
        S.on = false
        port.calls.clear()
        engine.tick()
        assertEquals(listOf("enabled=false"), port.calls)
        port.calls.clear()
        repeat(500) { engine.tick() }
        assertEquals(emptyList(), port.calls, "off again: nothing at all, no snapshot, no sprite")
        // Nothing that writes to game memory is ever called with the switch off: the sprite setter is not.
        assertEquals(0, port.count("sprite"))
    }

    @Test
    fun `a refused table means the engine never does anything`() {
        val p = FakePort().also { it.accept = false }
        val e = SpriteIsMeEngine(p, art) { false }
        assertFalse(e.configure(Overworld.EMERALD_U))
        S.on = true
        repeat(50) { e.tick() }
        assertEquals(listOf("configure(11)"), p.calls)
    }

    // ------------------------------------------------------------------ what it draws, and when

    @Test
    fun `it pushes a sprite when the frame changes and only then`() {
        engine.lead = SpriteIsMeLogic.Lead(25, 30, false)
        port.snap = snap(100)
        engine.tick()
        assertEquals(1, port.count("enabled=true"))
        assertEquals(1, port.count("sprite"))
        assertEquals(A.IDLE.let { FakeArt.color(it, 0, 0) }, shown())
        assertEquals(1, port.sprite!!.ox); assertEquals(4, port.sprite!!.oy, "the sheet's own offset against the box")
        assertEquals(32, port.sprite!!.w); assertEquals(40, port.sprite!!.h)
        // Same frame, same stamp: not even a resolve.
        port.calls.clear()
        repeat(20) { engine.tick() }
        assertEquals(0, port.count("sprite"))
        // Time moves on inside the same animation frame: still nothing pushed.
        port.snap = snap(120); engine.tick()
        port.snap = snap(139); engine.tick()
        assertEquals(0, port.count("sprite"))
        // The next idle frame.
        port.snap = snap(140); engine.tick()
        assertEquals(1, port.count("sprite"))
        assertEquals(FakeArt.color(A.IDLE, 0, 1), shown())
    }

    @Test
    fun `walking and facing come from the game, not the pad`() {
        engine.lead = SpriteIsMeLogic.Lead(25, 30, false)
        port.snap = snap(1, moving = true, facing = 4); engine.tick()
        assertEquals(FakeArt.color(A.WALK, 2, 0), shown(), "walking east: the walk sheet, the east row")
        port.snap = snap(9, moving = true, facing = 4); engine.tick()
        assertEquals(FakeArt.color(A.WALK, 2, 1), shown())
        port.snap = snap(12, moving = true, facing = 3); engine.tick()
        assertEquals(FakeArt.color(A.WALK, 6, 1), shown(), "turned west")
        port.snap = snap(14, moving = false, facing = 3); engine.tick()
        assertEquals(FakeArt.color(A.IDLE, 6, 0), shown(), "stopped: idle, still facing west")
    }

    @Test
    fun `asleep after 55 seconds without input, and fainted at 0 HP`() {
        engine.lead = SpriteIsMeLogic.Lead(25, 30, false)
        port.snap = snap(1); engine.tick()
        assertEquals(A.IDLE.ordinal, (shown() shr 16) and 255)
        afk = true; port.snap = snap(2); engine.tick()
        assertEquals(A.SLEEP.ordinal, (shown() shr 16) and 255)
        assertEquals(5, port.sprite!!.ox); assertEquals(7, port.sprite!!.oy)
        afk = false
        engine.lead = SpriteIsMeLogic.Lead(25, 0, false); port.snap = snap(3); engine.tick()
        assertEquals(A.FAINT.ordinal, (shown() shr 16) and 255)
        engine.lead = SpriteIsMeLogic.Lead(25, 30, true); port.snap = snap(4); engine.tick()
        assertEquals(A.SLEEP.ordinal, (shown() shr 16) and 255, "the lead is asleep")
    }

    @Test
    fun `a new lead is a new sprite at once`() {
        engine.lead = SpriteIsMeLogic.Lead(25, 30, false); port.snap = snap(5); engine.tick()
        val n = port.count("sprite")
        engine.lead = SpriteIsMeLogic.Lead(94, 30, false); engine.tick()
        assertEquals(n + 1, port.count("sprite"), "pushed although the emulator frame did not move")
    }

    @Test
    fun `with nobody to be the trainer is left alone, and comes back when there is`() {
        // No lead: nothing pushed, and no clear either (nothing was there).
        port.snap = snap(1); engine.tick()
        assertEquals(0, port.count("sprite")); assertEquals(0, port.count("clear"))
        // A lead appears, then the party empties (an unreadable read keeps the last lead; an empty one clears it).
        engine.lead = SpriteIsMeLogic.Lead(25, 30, false); port.snap = snap(2); engine.tick()
        assertEquals(1, port.count("sprite"))
        engine.lead = null; port.snap = snap(3); engine.tick()
        assertEquals(1, port.count("clear"), "the sprite is taken back, so the trainer shows again")
        // Still no Pokemon: the trainer stays the trainer (Blake, 2026-09-30: "you will be the default sprite until you
        // have a lead pokemon"), with nothing pushed and nothing cleared again.
        port.calls.clear(); port.snap = snap(4); engine.tick()
        assertEquals(0, port.count("sprite")); assertEquals(0, port.count("clear"))
        // The next lead is drawn again.
        engine.lead = SpriteIsMeLogic.Lead(133, 30, false); port.snap = snap(5); engine.tick()
        assertEquals(1, port.count("sprite"))
    }

    @Test
    fun `always use ignores the party but still mirrors the lead's state`() {
        S.who = SpriteIsMeSettings.Who.ALWAYS; S.always = 94
        engine.lead = SpriteIsMeLogic.Lead(25, 0, false)
        port.snap = snap(1); engine.tick()
        assertEquals(A.FAINT.ordinal, (shown() shr 16) and 255)
        engine.lead = null; port.snap = snap(2); engine.tick()
        assertEquals(A.IDLE.ordinal, (shown() shr 16) and 255, "no lead to mirror: 94 still drawn, idle")
        assertTrue(port.count("sprite") >= 2, "both were pushed")
    }

    /**
     * The emulator side takes 128 x 128 at most (sprite_core.h's kMaxSpriteDim), and a few later sheets have bigger
     * frames, mostly clear: the frame is cut to what shows and moved by as much, so nothing is refused and every pixel
     * lands where it would have.
     */
    @Test
    fun `a frame bigger than the emulator side takes is cut to what shows, at the same place`() {
        engine.lead = SpriteIsMeLogic.Lead(FakeArt.BIG, 30, false, WalkingPals.Dex.NAT_DEX)
        port.snap = snap(1); engine.tick()
        val p = assertNotNull(port.sprite, "drawn, not refused")
        assertEquals(100 to 80, p.w to p.h)
        assertTrue(p.w <= SpriteArt.MAX_FRAME && p.h <= SpriteArt.MAX_FRAME)
        assertEquals(-55 + 20, p.ox); assertEquals(-25 + 30, p.oy)
        assertEquals(FakeArt.color(A.IDLE, 0, 0), p.argb[0])
        assertTrue(p.argb.all { it == FakeArt.color(A.IDLE, 0, 0) }, "only the block that shows")
    }

    // ------------------------------------------------------------------ shiny (Blake, 2026-10-03)

    @Test
    fun `a shiny lead walks as its shiny, and the always use switch takes effect at once`() {
        engine.lead = SpriteIsMeLogic.Lead(25, 30, false, look = WalkingPals.Look(shiny = true))
        port.snap = snap(1); engine.tick()
        assertEquals(FakeArt.color(A.IDLE, 0, 0) or FakeArt.SHINY, shown(), "the shiny lead")
        S.who = SpriteIsMeSettings.Who.ALWAYS; S.always = 94; engine.tick()
        assertEquals(FakeArt.color(A.IDLE, 0, 0), shown(), "always use: the plain one while the switch is off")
        val n = port.count("sprite")
        S.alwaysShiny = true; engine.tick()
        assertEquals(n + 1, port.count("sprite"), "pushed although the emulator has not moved")
        assertEquals(FakeArt.color(A.IDLE, 0, 0) or FakeArt.SHINY, shown(), "its shiny")
        S.alwaysShiny = false; engine.tick()
        assertEquals(FakeArt.color(A.IDLE, 0, 0), shown(), "and back")
    }

    // ------------------------------------------------------------------ the player's own sprite

    @Test
    fun `a picture mirrors when facing left and lifts a pixel on every other step`() {
        art.ownPictureReady = true; S.own = SpriteIsMeSettings.Own.PICTURE; S.who = SpriteIsMeSettings.Who.OWN
        port.snap = snap(0, facing = 1); engine.tick()
        val p = port.sprite!!
        assertEquals(2, p.w); assertEquals(15, p.ox); assertEquals(31, p.oy)
        assertEquals(0xFFFF0000.toInt(), p.argb[0]); assertEquals(0xFF0000FF.toInt(), p.argb[1])
        port.snap = snap(1, facing = 3); engine.tick()
        assertEquals(0xFF0000FF.toInt(), port.sprite!!.argb[0], "facing left: mirrored")
        port.snap = snap(2, facing = 4); engine.tick()
        assertEquals(0xFFFF0000.toInt(), port.sprite!!.argb[0], "facing right: as it is")
        // Walking: the bob, a pixel up on the second beat.
        port.snap = snap(8, moving = true, facing = 4); engine.tick()
        assertEquals(30, port.sprite!!.oy)
        port.snap = snap(16, moving = true, facing = 4); engine.tick()
        assertEquals(31, port.sprite!!.oy)
        port.snap = snap(24, moving = false, facing = 4); engine.tick()
        assertEquals(31, port.sprite!!.oy, "standing: no bob")
        // Facing unknown keeps the last direction.
        port.snap = snap(25, facing = 3); engine.tick()
        port.snap = snap(26, facing = 0); engine.tick()
        assertEquals(0xFF0000FF.toInt(), port.sprite!!.argb[0])
    }

    @Test
    fun `a sheet set animates like a Pokemon's, with the lead's state`() {
        art.ownSheetsReady = true; S.own = SpriteIsMeSettings.Own.SHEET; S.who = SpriteIsMeSettings.Who.OWN
        engine.lead = SpriteIsMeLogic.Lead(25, 30, false)
        port.snap = snap(1, moving = true, facing = 3); engine.tick()
        assertEquals(FakeArt.color(A.WALK, 6, 0), shown())
        assertEquals(0, port.sprite!!.ox)
        port.snap = snap(7, moving = true, facing = 3); engine.tick()
        assertEquals(FakeArt.color(A.WALK, 6, 1), shown())
        // The set has no faint sheet: it borrows idle, and nothing breaks.
        engine.lead = SpriteIsMeLogic.Lead(25, 0, false); port.snap = snap(8); engine.tick()
        assertEquals(A.IDLE.ordinal, (shown() shr 16) and 255)
    }

    @Test
    fun `imported art nobody can read yet is the lead's Pokemon, not a blank`() {
        S.who = SpriteIsMeSettings.Who.OWN; S.own = SpriteIsMeSettings.Own.PICTURE      // set, but not on disk
        engine.lead = SpriteIsMeLogic.Lead(25, 30, false); port.snap = snap(1); engine.tick()
        assertNotNull(port.sprite)
        assertEquals(FakeArt.color(A.IDLE, 0, 0), shown())
    }

    @Test
    fun `changing the art or the settings takes effect on the next tick`() {
        engine.lead = SpriteIsMeLogic.Lead(25, 30, false); port.snap = snap(1); engine.tick()
        val n = port.count("sprite")
        S.artVersion++
        engine.tick()
        assertEquals(n + 1, port.count("sprite"), "the art changed: pushed again")
    }

    @Test
    fun `stopping switches the emulator side off, drops the sprite and forgets the game`() {
        engine.lead = SpriteIsMeLogic.Lead(25, 30, false); port.snap = snap(1); engine.tick()
        port.calls.clear()
        engine.stop()
        assertEquals(listOf("enabled=false", "clear", "reset"), port.calls)
        port.calls.clear()
        repeat(10) { engine.tick() }
        assertEquals(emptyList(), port.calls, "a stopped engine does nothing until it is configured again")
    }

    // ------------------------------------------------------------------ a frame is copied once (rc32 audit P3 #65)

    /**
     * The snapshot carries the frame counter, so every emulated frame gets past the tick's check, 60 times a second on the
     * main thread, and each one cropped and copied a frame before finding it was the one already shown.
     */
    @Test
    fun `a frame is copied only when it changes, not on every emulated frame`() {
        engine.lead = SpriteIsMeLogic.Lead(25, 30, false)
        repeat(120) { i -> port.snap = snap(i.toLong()); engine.tick() }
        // Idle's frames last 40, 6 and 6 game frames: a new one begins at 0, 40, 46, 52, 92, 98 and 104.
        assertEquals(7, port.count("sprite"))
        assertEquals(7, art.palFrames, "one copy for each frame shown, not one a tick")
        // The player's own sheets the same way: walking, a new frame every 5 game frames.
        art.ownSheetsReady = true; S.own = SpriteIsMeSettings.Own.SHEET; S.who = SpriteIsMeSettings.Who.OWN
        port.calls.clear()
        repeat(120) { i -> port.snap = snap(1_000L + i, moving = true, facing = 4); engine.tick() }
        assertEquals(24, port.count("sprite"))
        assertEquals(24, art.ownFrames)
    }

    // ------------------------------------------------------------------ the emulator has not moved, but something else did

    private fun anim(): Int = (shown() shr 16) and 255

    @Test
    fun `falling asleep changes the sprite although the game is paused`() {
        // A game left open on a menu runs no frames, so the snapshot does not change; the 55 seconds still pass.
        engine.lead = SpriteIsMeLogic.Lead(25, 30, false)
        port.snap = snap(50); engine.tick()
        assertEquals(A.IDLE.ordinal, anim())
        afk = true; engine.tick()
        assertEquals(A.SLEEP.ordinal, anim(), "asleep after 55 seconds without input, paused or not")
        afk = false; engine.tick()
        assertEquals(A.IDLE.ordinal, anim(), "awake at the next input")
    }

    @Test
    fun `each choice takes effect on the next tick although the emulator has not moved`() {
        engine.lead = SpriteIsMeLogic.Lead(25, 30, false)
        port.snap = snap(3); engine.tick()
        var n = port.count("sprite")
        fun pushedAnother(what: String) { assertEquals(n + 1, port.count("sprite"), what); n++ }

        // Who, on its own: the Pokemon to always use is picked first, and playing as the lead it changes nothing.
        S.always = 94; engine.tick()
        assertEquals(n, port.count("sprite"), "94 is picked but the lead is still being played")
        S.who = SpriteIsMeSettings.Who.ALWAYS; engine.tick()
        pushedAnother("who: always use, and nothing else changed")
        // Which one, on its own.
        S.always = 133; engine.tick()
        pushedAnother("which Pokemon to always use")
        // Back to the lead, with none: nothing to be, so the trainer is back.
        S.who = SpriteIsMeSettings.Who.LEAD; engine.lead = null; engine.tick()
        assertEquals(1, port.count("clear"), "no lead: the sprite is taken back")
        // The player's own art, once chosen and readable, is drawn with no lead too.
        art.ownPictureReady = true
        S.who = SpriteIsMeSettings.Who.OWN; engine.tick()
        S.own = SpriteIsMeSettings.Own.PICTURE; engine.tick()
        pushedAnother("the player's own picture")
        assertEquals(2, port.sprite!!.w, "it is the picture that was pushed")
    }

    // ------------------------------------------------------------------ a resumed run, a reset and Continue (2026-10-03)

    /** The emulator side with nothing to draw: no step, so the same snapshot every frame (sprite_overlay.cpp). */
    private val standingStill = snap(0, replaced = false, wanted = false)

    /**
     * On the emulator a MaxDex run resumed with the switch on came back as the trainer, and stayed the trainer through
     * turns and saves until the switch was turned off and on. Every game does it: in a new session nothing is decoded
     * yet, so the first tick with a lead finds its art on the way, and the emulator side, which steps only while it has
     * a picture, holds its snapshot still. Nothing else moved, so the tick never looked again. The art says when it is in.
     */
    @Test
    fun `a run that starts with the switch on draws the lead once its art is in, with nothing else changing`() {
        port.snap = standingStill
        art.palsReady = false; art.sheetsReady = false
        engine.lead = SpriteIsMeLogic.Lead(25, 30, false)
        repeat(30) { engine.tick() }
        assertEquals(0, port.count("sprite"), "the tables are still being read")
        art.palsReady = true; art.ready++
        repeat(30) { engine.tick() }
        assertEquals(0, port.count("sprite"), "the lead's sheets are still being decoded")
        art.sheetsReady = true; art.ready++
        engine.tick()
        assertEquals(1, port.count("sprite"), "drawn as soon as they are in: no turn, no save, no switch")
        assertEquals(FakeArt.color(A.IDLE, 0, 0), shown())
        repeat(30) { engine.tick() }
        assertEquals(1, port.count("sprite"), "and pushed once")
    }

    @Test
    fun `after a reset and Continue the lead is drawn again, a new one too, without touching the switch`() {
        engine.lead = SpriteIsMeLogic.Lead(25, 30, false); port.snap = snap(10); engine.tick()
        assertEquals(1, port.count("sprite"))
        // Reset: no party on the title screen, so the trainer, and the emulator side stands still.
        engine.lead = null; port.snap = standingStill; engine.tick()
        assertEquals(1, port.count("clear"))
        // Continue with the same lead: its sheets are still in.
        engine.lead = SpriteIsMeLogic.Lead(25, 30, false); engine.tick()
        assertEquals(2, port.count("sprite"))
        // Reset again and Continue a save whose lead is another Pokemon, its sheets still being decoded.
        engine.lead = null; engine.tick()
        art.sheetsReady = false
        engine.lead = SpriteIsMeLogic.Lead(94, 30, false)
        repeat(30) { engine.tick() }
        assertEquals(2, port.count("sprite"))
        art.sheetsReady = true; art.ready++
        engine.tick()
        assertEquals(3, port.count("sprite"), "drawn once its sheets are in")
    }

    @Test
    fun `the player's own picture is drawn once it is read, not only when a setting changes`() {
        S.who = SpriteIsMeSettings.Who.OWN; S.own = SpriteIsMeSettings.Own.PICTURE
        port.snap = standingStill
        repeat(10) { engine.tick() }
        assertEquals(0, port.count("sprite"), "no lead, and the picture not read yet: the trainer")
        art.ownPictureReady = true; art.ready++
        engine.tick()
        assertEquals(1, port.count("sprite"))
        assertEquals(2, port.sprite!!.w, "the picture")
    }

    // ------------------------------------------------------------------ the game's own steps (2026-10-03)

    /**
     * On the emulator Baxcalibur held one pose through 15 tiles of walking in four directions. The walk was played by the
     * test bot, whose keys never reached the phone's idle clock, so 55 seconds in the sprite slept: one sheet with one
     * row, whatever the game did. The game's own steps and turns count now, whatever path the keys took.
     */
    @Test
    fun `a walk the phone never heard is still a walk, and the sprite sleeps only when the game is still too`() {
        afk = true   // no key has reached the phone's idle clock for 55 seconds
        engine.lead = SpriteIsMeLogic.Lead(25, 30, false)
        port.snap = snap(1, moving = true, facing = 2); engine.tick()
        assertEquals(FakeArt.color(A.WALK, 4, 0), shown(), "walking north: the walk sheet, the north row")
        port.snap = snap(9, moving = true, facing = 2); engine.tick()
        assertEquals(FakeArt.color(A.WALK, 4, 1), shown(), "the next step")
        port.snap = snap(12, moving = true, facing = 3); engine.tick()
        assertEquals(FakeArt.color(A.WALK, 6, 1), shown(), "turned west")
        // Standing after the walk: awake and facing west, until 55 seconds with no key and no step.
        port.snap = snap(14, facing = 3); engine.tick()
        assertEquals(FakeArt.color(A.IDLE, 6, 0), shown())
        clock += SpriteIsMeLogic.IDLE_NANOS - 1; port.snap = snap(15, facing = 3); engine.tick()
        assertEquals(A.IDLE.ordinal, anim(), "not yet")
        clock += 1; port.snap = snap(16, facing = 3); engine.tick()
        assertEquals(A.SLEEP.ordinal, anim(), "55 seconds of nothing: asleep")
        // A turn in the game wakes it, keys or not.
        port.snap = snap(17, facing = 4); engine.tick()
        assertEquals(FakeArt.color(A.IDLE, 2, 0), shown(), "turned east: awake")
    }

    /** Blake, 2026-10-03: "Fix it for all sprites". A walk frame that stays up longer than two steps bobs as a picture does. */
    @Test
    fun `a Pokemon with one frame to walk on steps a pixel up and down, and stands still`() {
        engine.lead = SpriteIsMeLogic.Lead(FakeArt.ONE_STEP, 30, false)
        port.snap = snap(0, moving = true); engine.tick()
        assertEquals(FakeArt.color(A.WALK, 0, 0), shown())
        assertEquals(7, port.sprite!!.oy, "the walk sheet's own offset")
        port.snap = snap(8, moving = true); engine.tick()
        assertEquals(6, port.sprite!!.oy, "the second beat: a pixel up")
        port.snap = snap(16, moving = true); engine.tick()
        assertEquals(7, port.sprite!!.oy)
        port.snap = snap(24); engine.tick()
        assertEquals(4, port.sprite!!.oy, "standing: idle, no bob")
        // A walk with frames of its own does not bob.
        engine.lead = SpriteIsMeLogic.Lead(25, 30, false)
        port.snap = snap(32, moving = true); engine.tick()
        port.snap = snap(40, moving = true); engine.tick()
        assertEquals(7, port.sprite!!.oy)
        // A twitch and then a frame held 120 game frames: the twitch as it is, the hold with steps.
        engine.lead = SpriteIsMeLogic.Lead(FakeArt.HELD_STEP, 30, false)
        port.snap = snap(999); engine.tick()   // standing first, so the walk begins on its first frame
        port.snap = snap(1_000, moving = true); engine.tick()
        assertEquals(FakeArt.color(A.WALK, 0, 0) to 7, shown() to port.sprite!!.oy)
        port.snap = snap(1_002, moving = true); engine.tick()
        assertEquals(FakeArt.color(A.WALK, 0, 1) to 7, shown() to port.sprite!!.oy)
        port.snap = snap(1_004, moving = true); engine.tick()
        assertEquals(FakeArt.color(A.WALK, 0, 2), shown())
        val beats = (1_004L..1_040L).map { port.snap = snap(it, moving = true); engine.tick(); port.sprite!!.oy }.toSet()
        assertEquals(setOf(6, 7), beats, "the held frame steps")
    }
}
