package com.ironmonone.app

import com.ironmonone.tracker.Overworld
import com.ironmonone.tracker.OverworldAddresses
import com.ironmonone.tracker.OverworldMath
import com.ironmonone.tracker.OverworldScan
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * "Play as your Pokemon", the part that can only run inside the emulator's video callback: the real
 * sprite_core.h, built for WebAssembly (WasmSpriteCore) and driven over fake Gen 3 memory laid out
 * exactly as a game lays it out. What each scenario proves is stated in its name.
 *
 * The scenarios use FireRed's addresses unless they say otherwise; the layout is the same in the
 * five games (OverworldAddressTest proves that against their ROMs) and Ruby and Sapphire's
 * different oamBuffer offset gets its own scenario.
 */
class SpriteCoreWasmTest {
    private val core: WasmSpriteCore? = WasmSpriteCore.shared()

    private fun run(body: (WasmSpriteCore) -> Unit) { core?.let(body) }

    // ------------------------------------------------------------------ fake game

    /** One OAM entry as the game's oamBuffer holds it. */
    private class Oam(val y: Int, val x: Int, val shape: Int, val size: Int, val tile: Int, val pal: Int = 0,
                      val affine: Int = 0, val priority: Int = 2) {
        fun bytes(): ByteArray {
            val a0 = (y and 0xFF) or (affine shl 8) or (shape shl 14)
            val a1 = (x and 0x1FF) or (size shl 14)
            val a2 = (tile and 0x3FF) or (priority shl 10) or (pal shl 12)
            return ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putShort(a0.toShort()).putShort(a1.toShort()).putShort(a2.toShort()).putShort(0).array()
        }
    }

    private val DUMMY = byteArrayOf(0xA0.toByte(), 0, 0, 0, 0, 0, 0, 0)   // gDummyOamData: y = 160, all else 0

    private class Player(
        val id: Int = 2, val x: Int = 128, val y: Int = 100, val x2: Int = 0, val y2: Int = 0,
        val vecX: Int = -8, val vecY: Int = -16, val shape: Int = 2, val size: Int = 2,
        val tile: Int = 0x40, val pal: Int = 0, val flags: Int = 3,
    )

    private inner class Game(val c: WasmSpriteCore, val a: OverworldAddresses = Overworld.FIRERED_U_V10) {
        var offX = -8
        var offY = -28

        fun le16(v: Int) = byteArrayOf(v.toByte(), (v shr 8).toByte())
        fun le32(v: Long) = byteArrayOf(v.toByte(), (v shr 8).toByte(), (v shr 16).toByte(), (v shr 24).toByte())

        /** Fresh memory, configured for this game, in the overworld with a standing player facing down. */
        fun install(): Game {
            c.call("h_reset")
            c.call("h_configure", a.main, a.oamBufferOffset, a.cb2Overworld, a.cb2OverworldBasic, a.playerAvatar, a.sprites,
                a.coordOffsetX, a.coordOffsetY, a.plttUnfaded, a.plttFaded, a.objectEvents)
            callback2(a.cb2Overworld)
            oam(emptyList())
            avatar(2, 0, 0, 0)
            player(Player())
            offsets(offX, offY)
            facing(1)
            return this
        }

        fun callback2(v: Long) = c.writeAddr(a.main + 4, le32(v))
        fun avatar(spriteId: Int, objectEventId: Int, running: Int, transition: Int) =
            c.writeAddr(a.playerAvatar, byteArrayOf(0, 0, running.toByte(), transition.toByte(), spriteId.toByte(), objectEventId.toByte(), 0, 0))

        fun player(p: Player) {
            val b = ByteBuffer.allocate(0x44).order(ByteOrder.LITTLE_ENDIAN)
            b.putShort(0, ((p.shape shl 14) or ((p.y + p.y2 + offY + p.vecY) and 0xFF)).toShort())
            b.putShort(2, ((p.size shl 14) or ((p.x + p.x2 + offX + p.vecX) and 0x1FF)).toShort())
            b.putShort(4, ((p.tile and 0x3FF) or (2 shl 10) or (p.pal shl 12)).toShort())
            b.putShort(0x20, p.x.toShort()); b.putShort(0x22, p.y.toShort())
            b.putShort(0x24, p.x2.toShort()); b.putShort(0x26, p.y2.toShort())
            b.put(0x28, p.vecX.toByte()); b.put(0x29, p.vecY.toByte())
            b.put(0x3E, p.flags.toByte())
            c.writeAddr(a.sprites + p.id * 0x44L, b.array())
        }

        fun offsets(x: Int, y: Int) {
            offX = x; offY = y
            c.writeAddr(a.coordOffsetX, le16(x)); c.writeAddr(a.coordOffsetY, le16(y))
        }

        fun facing(dir: Int, objectEventId: Int = 0) {
            val oe = ByteArray(0x24); oe[0x18] = dir.toByte()
            c.writeAddr(a.objectEvents + objectEventId * 0x24L, oe)
        }

        fun oam(entries: List<Oam>) {
            val buf = ByteArray(1024)
            for (i in 0 until 128) (entries.getOrNull(i)?.bytes() ?: DUMMY).copyInto(buf, i * 8)
            c.writeAddr(a.main + a.oamBufferOffset, buf)
        }

        fun readOam(): ByteArray = c.readAddr(a.main + a.oamBufferOffset, 1024)

        /** The player's palette: OBJ palette [slot], colours 1-15, as the game holds it unfaded, faded, and as palette RAM shows it. */
        fun palettes(slot: Int, unfaded: IntArray, faded: IntArray, shown: IntArray? = null) {
            fun words(v: IntArray) = ByteArray(v.size * 2).also { b -> v.forEachIndexed { i, w -> b[i * 2] = w.toByte(); b[i * 2 + 1] = (w shr 8).toByte() } }
            c.writeAddr(a.plttUnfaded + 0x200 + slot * 32L + 2, words(unfaded))
            c.writeAddr(a.plttFaded + 0x200 + slot * 32L + 2, words(faded))
            if (shown != null) c.writeAddr(0x05000200L + slot * 32L + 2, words(shown))
        }

        fun sprite(w: Int, h: Int, ox: Int = 0, oy: Int = 0, argb: Int = 0xFFFF0000.toInt()) {
            val px = ByteArray(w * h * 4)
            for (i in 0 until w * h) {
                px[i * 4] = (argb shr 16).toByte(); px[i * 4 + 1] = (argb shr 8).toByte(); px[i * 4 + 2] = argb.toByte(); px[i * 4 + 3] = (argb ushr 24).toByte()
            }
            c.write(WasmSpriteCore.KIND_SPRITE, 0, px)
            c.call("h_sprite", w, h, ox, oy)
        }

        /** One refresh. Returns whether the sprite was drawn into this frame. */
        fun step(wanted: Boolean = true, haveSprite: Boolean = true, format: Int = 2, noPicture: Boolean = false,
                 width: Int = 240, height: Int = 160, pitch: Int = -1): Boolean {
            val p = if (pitch >= 0) pitch else 240 * (if (format == 1) 4 else 2) + (if (format == 1) 64 else 32)
            return c.call("h_step", if (wanted) 1 else 0, if (haveSprite) 1 else 0, format, width, height, p, if (noPicture) 1 else 0) == 1L
        }

        fun box(): Pair<Int, Int> = c.call("h_debug", 2).toInt() to c.call("h_debug", 3).toInt()
        fun reason(): Int = c.call("h_reason").toInt()
        fun writes(): Int = c.call("h_writes").toInt()
        fun reads(): Int = c.call("h_reads").toInt()
        fun hidden(): Int = c.call("h_debug", 4).toInt()
        fun flags(): Int = c.call("h_flags").toInt()

        fun clearFrame() = c.fill(WasmSpriteCore.KIND_FRAME, 0, c.call("h_size", 3).toInt(), 0)

        /** A pixel of the 565 frame (pitch as [step] uses it). */
        fun px565(x: Int, y: Int, pitch: Int = 512): Int {
            val b = c.read(WasmSpriteCore.KIND_FRAME, y * pitch + x * 2, 2)
            return (b[0].toInt() and 255) or ((b[1].toInt() and 255) shl 8)
        }

        fun pxX8888(x: Int, y: Int, pitch: Int = 1024): Int {
            val b = c.read(WasmSpriteCore.KIND_FRAME, y * pitch + x * 4, 4)
            return (b[0].toInt() and 255) or ((b[1].toInt() and 255) shl 8) or ((b[2].toInt() and 255) shl 16) or ((b[3].toInt() and 255) shl 24)
        }
    }

    private val RED565 = 0xF800

    /** The four entries a walking player shows: two halves, the affine water reflection, and someone else's sprite. */
    private fun sampleOam() = listOf(
        Oam(y = 3, x = 3, shape = 1, size = 1, tile = 0x30, pal = 4),                  // 0: another sprite before the player's
        Oam(y = 56, x = 112, shape = 2, size = 2, tile = 0x40),                        // 1: the player, one 16x32 entry
        Oam(y = 90, x = 112, shape = 2, size = 2, tile = 0x40, pal = 3, affine = 1, priority = 3),  // 2: the reflection, an affine copy
        Oam(y = 40, x = 40, shape = 1, size = 1, tile = 0x60),                         // 3: someone else
        Oam(y = 58, x = 112, shape = 1, size = 1, tile = 0x44),                        // 4: a subsprite half using the block's later tiles
    )

    // ------------------------------------------------------------------ the main path

    @Test
    fun `a standing player is replaced where the extension draws, one frame after the trainer is hidden`() = run { c ->
        standingPlayer(c, Overworld.FIRERED_U_V10)
    }

    /** The whole standing-player scenario against one game's table: where it hides, what it writes, when it draws. */
    private fun standingPlayer(c: WasmSpriteCore, a: OverworldAddresses) {
        val g = Game(c, a).install()
        g.oam(sampleOam())
        g.sprite(32, 32)
        val before = g.readOam()

        assertFalse(g.step(), "nothing to draw on the first frame: the picture was built before the trainer was hidden")
        assertEquals(104 to 56, g.box(), "the standing player's box is (104, 56), the extension's own fixed spot")
        assertEquals(3, g.hidden(), "the player's entry, the reflection and the later half; nothing else")
        assertEquals(3, g.writes(), "one two-byte write per hidden entry")
        assertEquals(0, g.reason())
        val after = g.readOam()
        for (i in 0 until 128) {
            val hidden = i in setOf(1, 2, 4)
            val changed = !before.copyOfRange(i * 8, i * 8 + 8).contentEquals(after.copyOfRange(i * 8, i * 8 + 8))
            assertEquals(hidden, changed, "entry $i")
        }
        // Only attr0's bits 8-9 changed, to 0b10, on the affine reflection too (bit 8 cleared, not just bit 9 set).
        fun attr0(b: ByteArray, i: Int) = (b[i * 8].toInt() and 255) or ((b[i * 8 + 1].toInt() and 255) shl 8)
        for (i in setOf(1, 2, 4)) {
            assertEquals(0x200, attr0(after, i) and 0x300, "entry $i is disabled")
            assertEquals(attr0(before, i) and 0x300.inv(), attr0(after, i) and 0x300.inv(), "entry $i kept its other bits")
            assertContentEquals(before.copyOfRange(i * 8 + 2, i * 8 + 8), after.copyOfRange(i * 8 + 2, i * 8 + 8), "entry $i kept attr1 and attr2")
        }

        assertTrue(g.step(), "the next picture carries the replacement")
        assertEquals(RED565, g.px565(104, 56)); assertEquals(RED565, g.px565(135, 87)); assertEquals(RED565, g.px565(120, 72))
        assertEquals(0, g.px565(103, 56)); assertEquals(0, g.px565(136, 88)); assertEquals(0, g.px565(104, 55)); assertEquals(0, g.px565(135, 88))
    }

    /** Nat. Dex has no table: its addresses are read out of the dumps, when they are here (IRONMON_ROMS, never copied). */
    private fun natDexTables(): List<OverworldAddresses> {
        val dir = Dumps.romsDir()
        if (dir == null) { println("SpriteCoreWasmTest: Nat. Dex tables skipped, set IRONMON_ROMS"); return emptyList() }
        return listOf("firered-natdex-121.gba", "emerald-natdex-121.gba").mapNotNull { name ->
            val f = Dumps.file(dir, name)
            if (f == null) { println("SpriteCoreWasmTest: $name missing, skipped"); null }
            else OverworldScan.find(f.readBytes(), "Nat. Dex $name")
        }
    }

    @Test
    fun `every table the app has, and the Nat Dex tables read from the dumps, are accepted by the native side and work end to end`() = run { c ->
        fun plausible(a: OverworldAddresses) = c.call("h_plausible", a.main, a.oamBufferOffset, a.cb2Overworld, a.cb2OverworldBasic,
            a.playerAvatar, a.sprites, a.coordOffsetX, a.coordOffsetY, a.plttUnfaded, a.plttFaded, a.objectEvents) == 1L
        val natDex = natDexTables()
        if (System.getenv("IRONMON_ROMS") != null) assertEquals(2, natDex.size, "both Nat. Dex dumps are read")
        // Heart & Soul's table is its build's symbols (OverworldHnsTest), not a retail one: run through the same scenario.
        for (a in Overworld.ALL + Overworld.HEARTSOUL_KAIZO + natDex) {
            assertTrue(plausible(a), "${a.name}: the native side refuses this table")
            standingPlayer(c, a)
        }
        println("SpriteCoreWasmTest: ${Overworld.ALL.size} tables and ${natDex.size} Nat. Dex tables read from dumps, each run through the standing-player scenario")
    }

    @Test
    fun `a table that is not a game's is refused, address by address`() = run { c ->
        fun plausible(a: OverworldAddresses) = c.call("h_plausible", a.main, a.oamBufferOffset, a.cb2Overworld, a.cb2OverworldBasic,
            a.playerAvatar, a.sprites, a.coordOffsetX, a.coordOffsetY, a.plttUnfaded, a.plttFaded, a.objectEvents) == 1L
        val fr = Overworld.FIRERED_U_V10
        assertTrue(plausible(fr))
        val bad = listOf(
            "gMain past the usable IWRAM" to fr.copy(main = 0x03007001), "gMain in EWRAM" to fr.copy(main = 0x02000100),
            "gMain in the ROM" to fr.copy(main = 0x08000000), "gMain below IWRAM" to fr.copy(main = 0x02FFFFF0),
            "oamBuffer offset too small" to fr.copy(oamBufferOffset = 0x2F), "oamBuffer offset too large" to fr.copy(oamBufferOffset = 0x41),
            "CB2_Overworld without the Thumb bit" to fr.copy(cb2Overworld = 0x080565B4),
            "CB2_OverworldBasic without the Thumb bit" to fr.copy(cb2OverworldBasic = 0x080565A8),
            "CB2_Overworld in RAM" to fr.copy(cb2Overworld = 0x02000001), "CB2_OverworldBasic past the ROM" to fr.copy(cb2OverworldBasic = 0x0A000001),
            "gPlayerAvatar too near the end of EWRAM" to fr.copy(playerAvatar = 0x0203FFE1), "gPlayerAvatar in IWRAM" to fr.copy(playerAvatar = 0x03000000),
            "gSprites whose 64 sprites run out of EWRAM" to fr.copy(sprites = 0x0203F000), "gSprites in the ROM" to fr.copy(sprites = 0x08000000),
            "the X camera offset in the ROM" to fr.copy(coordOffsetX = 0x08000000), "the Y camera offset in nowhere" to fr.copy(coordOffsetY = 0x04000000),
            "gPlttBufferUnfaded whose 0x400 bytes run out" to fr.copy(plttUnfaded = 0x0203FC00), "gPlttBufferFaded in IWRAM" to fr.copy(plttFaded = 0x03000000),
            "gObjectEvents whose 16 events run out" to fr.copy(objectEvents = 0x0203FDC0), "gObjectEvents in the ROM" to fr.copy(objectEvents = 0x08000000),
            "every address zero" to OverworldAddresses("zero", 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
        )
        for ((what, a) in bad) assertFalse(plausible(a), what)
        // The edges that ARE accepted, so the limits are not stricter than a real game needs: Ruby and Sapphire's camera offsets and objects in IWRAM.
        assertTrue(plausible(Overworld.RUBY_U) && plausible(Overworld.SAPPHIRE_U))
        assertTrue(plausible(fr.copy(main = 0x03007000)) && plausible(fr.copy(oamBufferOffset = 0x30)) && plausible(fr.copy(oamBufferOffset = 0x40)))
    }

    @Test
    fun `the replacement is drawn where the box was at the previous refresh, and the trainer hidden for the next`() = run { c ->
        val g = Game(c).install()
        g.sprite(32, 32)
        g.step()                                         // refresh 1: sees the player at (104, 56)
        g.player(Player(y2 = -8))                        // the game moves the player up 8 before refresh 2
        assertTrue(g.step(), "refresh 2 draws")
        assertEquals(104 to 48, g.box(), "refresh 2 read the new box")
        assertEquals(RED565, g.px565(104, 56), "but drew at refresh 1's: 8 pixels lower")
        assertEquals(RED565, g.px565(135, 87))
        assertEquals(0, g.px565(104, 55), "not at refresh 2's")
        g.clearFrame()
        assertTrue(g.step(), "refresh 3 draws at refresh 2's box")
        assertEquals(RED565, g.px565(104, 48)); assertEquals(RED565, g.px565(135, 79)); assertEquals(0, g.px565(104, 47)); assertEquals(0, g.px565(104, 80))
    }

    // ------------------------------------------------------------------ the box

    @Test
    fun `the box follows the game's own coordinates`() = run { c ->
        data class Case(val name: String, val p: Player, val offX: Int, val offY: Int, val box: Pair<Int, Int>)
        val cases = listOf(
            Case("standing", Player(), -8, -28, 104 to 56),
            Case("mid-step to the east, sliding", Player(x2 = 6), -8, -28, 110 to 56),
            Case("ledge jump, 6 pixels up", Player(y2 = -6), -8, -28, 104 to 50),
            Case("surf bob, 1 pixel down", Player(y2 = 1), -8, -28, 104 to 57),
            Case("the camera scrolled: the world offset moves the player", Player(), -20, -10, 92 to 74),
            Case("coordOffsetEnabled off: the offsets are ignored", Player(flags = 1), -8, -28, 112 to 84),
            Case("a 32x32 bike sprite (16 tiles, centre-to-corner -16, -16)", Player(shape = 0, size = 2, vecX = -16, vecY = -16), -8, -28, 104 to 56),
            Case("a taller object: the feet line is the bottom edge, not the top", Player(shape = 2, size = 3, vecX = -16, vecY = -32, y = 84), -8, -28, 104 to 56),
            Case("partly off the left edge", Player(x = 20), -8, -28, -4 to 56),
            Case("partly off the bottom edge", Player(y = 200), -8, -28, 104 to 156),
        )
        for (t in cases) {
            val g = Game(c).install()
            g.offsets(t.offX, t.offY)
            g.player(t.p)
            g.sprite(32, 32)
            g.step()
            assertEquals(t.box, g.box(), t.name)
            assertEquals(0, g.reason(), t.name)
        }
    }

    @Test
    fun `a sprite drawn partly off the screen is clipped, not wrapped or refused`() = run { c ->
        val g = Game(c).install()
        g.player(Player(x = 20))              // box x = -4
        g.sprite(32, 32)
        g.step()
        g.clearFrame()
        assertTrue(g.step())
        assertEquals(RED565, g.px565(0, 56)); assertEquals(RED565, g.px565(27, 87)); assertEquals(0, g.px565(28, 56))
        assertEquals(0, g.px565(239, 56), "nothing wrapped round to the other edge")
    }

    @Test
    fun `the art's own offset and size move it against the box, and a big frame is clipped at the picture`() = run { c ->
        val g = Game(c).install()
        g.sprite(40, 56, ox = -3, oy = -1)     // Pikachu's idle frame in the Walking Pals table: 40x56 at (-3, -1)
        g.step(); g.step()
        assertEquals(RED565, g.px565(101, 55)); assertEquals(RED565, g.px565(140, 110))
        assertEquals(0, g.px565(100, 55)); assertEquals(0, g.px565(101, 54)); assertEquals(0, g.px565(141, 110)); assertEquals(0, g.px565(101, 111))
        // 128x128 is the biggest a sprite may be, and it is clipped to the 240x160 picture.
        g.clearFrame()
        g.sprite(128, 128, ox = -40, oy = -40)
        assertTrue(g.step())
        assertEquals(RED565, g.px565(64, 16)); assertEquals(RED565, g.px565(191, 143)); assertEquals(0, g.px565(63, 16)); assertEquals(0, g.px565(192, 16))
    }

    // ------------------------------------------------------------------ the guards: no draw, no write

    @Test
    fun `it acts only in the overworld, and writes nothing anywhere else`() = run { c ->
        val g = Game(c).install()
        g.oam(sampleOam()); g.sprite(32, 32)
        // Some other game state: CB2_InitBattle, a menu, the title screen.
        for (cb in listOf(0x08012345L, 0L, 0x0805661DL, 0xFFFFFFFFL and 0x7FFFFFFFL)) {
            g.callback2(cb)
            g.c.call("h_clearCounters")
            assertFalse(g.step(), "cb2 %08X".format(cb))
            assertEquals(4, g.reason(), "cb2 %08X is not the overworld".format(cb))
            assertEquals(0, g.writes(), "cb2 %08X: nothing written".format(cb))
            assertEquals(0, g.flags() and 1, "not replaced")
        }
        // Both overworld callbacks count: CB2_Overworld and CB2_OverworldBasic.
        for (cb in listOf(g.a.cb2Overworld, g.a.cb2OverworldBasic)) {
            g.callback2(cb)
            g.step()
            assertEquals(0, g.reason(), "cb2 %08X".format(cb))
            assertEquals(1, g.flags() and 1)
        }
        // The same address without the Thumb bit is not the callback: a pointer holds it set.
        g.callback2(g.a.cb2Overworld - 1)
        g.step()
        assertEquals(4, g.reason())
    }

    @Test
    fun `an unreadable game, a missing player and impossible sprite fields write nothing`() = run { c ->
        fun writesFor(name: String, expectReason: Int, setup: (Game) -> Unit) {
            val g = Game(c).install()
            g.oam(sampleOam()); g.sprite(32, 32)
            setup(g)
            g.c.call("h_clearCounters")
            g.step(); g.step()
            assertEquals(expectReason, g.reason(), name)
            assertEquals(0, g.writes(), "$name: it wrote to game memory")
            assertEquals(0, g.flags() and 1, "$name: replaced")
        }
        writesFor("sprite id 64 is out of range", 5) { it.avatar(64, 0, 0, 0) }
        writesFor("sprite id 255", 5) { it.avatar(255, 0, 0, 0) }
        writesFor("sprite not in use", 6) { it.player(Player(flags = 2)) }
        writesFor("sprite invisible", 6) { it.player(Player(flags = 1 or 2 or 4)) }
        writesFor("centre-to-corner Y not negative", 7) { it.player(Player(vecY = 0)) }
        writesFor("centre-to-corner Y absurd", 7) { it.player(Player(vecY = -100)) }
        writesFor("centre-to-corner X positive", 7) { it.player(Player(vecX = 8)) }
        writesFor("the prohibited OAM shape", 7) { it.player(Player(shape = 3)) }
        writesFor("box far off to the right", 8) { it.player(Player(x = 700)) }
        writesFor("box far off above", 8) { it.player(Player(y = -700)) }
        writesFor("an unreadable EWRAM", 3) { it.c.call("h_ewramReadable", 0); }
    }

    @Test
    fun `switched off, it reads nothing, writes nothing and draws nothing`() = run { c ->
        val g = Game(c).install()
        g.oam(sampleOam()); g.sprite(32, 32)
        val before = g.readOam()
        g.c.call("h_clearCounters")
        repeat(5) { assertFalse(g.step(wanted = false)) }
        assertEquals(0, g.writes(), "no memory write with the switch off")
        assertEquals(0, g.reads(), "no memory read with the switch off")
        assertContentEquals(before, g.readOam())
        assertEquals(0, g.flags() and 1)
        assertEquals(1, g.reason(), "off")
        // No picture chosen is the same as off.
        assertFalse(g.step(wanted = false, haveSprite = false))
        assertEquals(0, g.writes())
    }

    @Test
    fun `switching off finishes the frame the trainer was last hidden for, then stops`() = run { c ->
        val g = Game(c).install()
        g.oam(sampleOam()); g.sprite(32, 32)
        g.step(); assertTrue(g.step())                   // on: hidden at 1, drawn from 2
        assertTrue(g.c.call("h_pending") == 1L)
        g.c.call("h_clearCounters")
        assertTrue(g.step(wanted = false), "the frame after the last hide still gets its replacement")
        assertEquals(0, g.writes(), "and writes nothing new")
        assertFalse(g.step(wanted = false), "then nothing")
        assertTrue(g.c.call("h_pending") == 0L)
        assertEquals(0, g.writes())
        // Same when the picture is cleared while it is on.
        g.step(); g.step()
        assertTrue(g.step(wanted = false, haveSprite = true))
        assertFalse(g.step(wanted = false, haveSprite = false))
    }

    @Test
    fun `no picture to draw into still moves the game on`() = run { c ->
        val g = Game(c).install()
        g.oam(sampleOam()); g.sprite(32, 32)
        assertFalse(g.step(noPicture = true), "a duplicate frame has nothing to draw into")
        assertEquals(3, g.hidden(), "but the trainer is hidden for the next picture all the same")
        assertEquals(1, g.flags() and 1)
        assertTrue(g.step(), "and the next picture that does arrive has the replacement")
    }

    @Test
    fun `a frame that is not a 240x160 GBA picture is left entirely alone`() = run { c ->
        val g = Game(c).install()
        g.oam(sampleOam()); g.sprite(32, 32)
        g.step()
        assertTrue(g.c.call("h_pending") == 1L)
        g.c.call("h_clearCounters")
        assertFalse(g.step(width = 160, height = 144, pitch = 512), "a Game Boy sized frame")
        assertFalse(g.step(width = 240, height = 160, pitch = 100), "rows narrower than a row")
        assertFalse(g.step(format = 7))
        assertEquals(0, g.writes())
        assertEquals(0L, g.c.call("h_pending"), "and what was pending is forgotten")
    }

    // ------------------------------------------------------------------ the hide

    @Test
    fun `the hide takes the player's tiles and nothing else, byte for byte`() = run { c ->
        val entries = ArrayList<ByteArray>()
        entries += Oam(y = 56, x = 112, shape = 2, size = 2, tile = 0x40).bytes()                       // 0 player, tile 0x40
        entries += Oam(y = 72, x = 112, shape = 1, size = 1, tile = 0x44).bytes()                       // 1 a lower half at tile 0x44
        entries += Oam(y = 90, x = 112, shape = 2, size = 2, tile = 0x40, affine = 1).bytes()           // 2 affine reflection
        entries += Oam(y = 90, x = 112, shape = 2, size = 2, tile = 0x40, affine = 3).bytes()           // 3 double-size affine
        entries += Oam(y = 56, x = 112, shape = 2, size = 2, tile = 0x47).bytes()                       // 4 last tile of the block (8 tiles: 0x40..0x47)
        entries += Oam(y = 56, x = 112, shape = 2, size = 2, tile = 0x48).bytes()                       // 5 one past
        entries += Oam(y = 56, x = 112, shape = 2, size = 2, tile = 0x3F).bytes()                       // 6 one before
        entries += Oam(y = 56, x = 112, shape = 2, size = 2, tile = 0x41, affine = 2).bytes()           // 7 already disabled
        entries += Oam(y = 56, x = 112, shape = 2, size = 2, tile = 0x41, pal = 9, priority = 1).bytes() // 8 same tiles other palette: still the block's
        for (i in entries.size until 128) entries += DUMMY
        val oam = ByteArray(1024).also { b -> entries.forEachIndexed { i, e -> e.copyInto(b, i * 8) } }
        val before = oam.copyOf()
        c.write(WasmSpriteCore.KIND_SCRATCH, 0, oam)
        val n = c.call("h_hide", "@5:0", 0x40, 8)
        val after = c.read(WasmSpriteCore.KIND_SCRATCH, 0, 1024)
        assertEquals(6L, n)
        val hidden = setOf(0, 1, 2, 3, 4, 8)
        for (i in 0 until 128) {
            val same = before.copyOfRange(i * 8, i * 8 + 8).contentEquals(after.copyOfRange(i * 8, i * 8 + 8))
            assertEquals(i !in hidden, same, "entry $i")
        }
        // The result equals the Kotlin statement of the same rule.
        val mirror = before.copyOf()
        assertEquals(6, OverworldMath.hideTileRange(mirror, 0x40, 8))
        assertContentEquals(mirror, after)
    }

    @Test
    fun `the game's dummy entries are never touched, even when the block starts at tile 0`() = run { c ->
        val oam = ByteArray(1024)
        for (i in 0 until 128) DUMMY.copyInto(oam, i * 8)
        Oam(y = 56, x = 112, shape = 2, size = 2, tile = 0).bytes().copyInto(oam, 5 * 8)   // a real entry at tile 0
        val before = oam.copyOf()
        c.write(WasmSpriteCore.KIND_SCRATCH, 0, oam)
        assertEquals(1L, c.call("h_hide", "@5:0", 0, 8), "only the real entry")
        val after = c.read(WasmSpriteCore.KIND_SCRATCH, 0, 1024)
        for (i in 0 until 128) if (i != 5) assertContentEquals(before.copyOfRange(i * 8, i * 8 + 8), after.copyOfRange(i * 8, i * 8 + 8), "dummy $i")
        assertTrue((after[5 * 8 + 1].toInt() and 3) == 2)
    }

    @Test
    fun `a 32x32 bike hides sixteen tiles, a 16x32 walker eight`() = run { c ->
        for ((shape, size, tiles) in listOf(Triple(2, 2, 8), Triple(0, 2, 16), Triple(0, 0, 1), Triple(0, 3, 64), Triple(1, 3, 32), Triple(3, 0, 0))) {
            assertEquals(tiles.toLong(), c.call("h_tiles", shape, size), "shape $shape size $size")
            assertEquals(OverworldMath.tileCount(shape, size), tiles)
        }
        val g = Game(c).install()
        g.player(Player(shape = 0, size = 2, vecX = -16, vecY = -16, tile = 0x40))
        g.oam(listOf(
            Oam(y = 56, x = 104, shape = 0, size = 2, tile = 0x40), Oam(y = 56, x = 104, shape = 0, size = 1, tile = 0x4F),
            Oam(y = 56, x = 104, shape = 0, size = 1, tile = 0x50),
        ))
        g.sprite(32, 32)
        g.step()
        assertEquals(2, g.hidden(), "0x40 and 0x4F are the bike's, 0x50 is not")
    }

    @Test
    fun `Ruby and Sapphire's oamBuffer sits 4 bytes later, and their callbacks are their own`() = run { c ->
        for (a in listOf(Overworld.RUBY_U, Overworld.SAPPHIRE_U, Overworld.EMERALD_U, Overworld.LEAFGREEN_U, Overworld.FIRERED_U_V11)) {
            val g = Game(c, a).install()
            g.oam(sampleOam()); g.sprite(32, 32)
            g.step()
            assertEquals(3, g.hidden(), a.name)
            assertEquals(104 to 56, g.box(), a.name)
            val oam = g.readOam()
            assertEquals(2, oam[1 * 8 + 1].toInt() and 3, "${a.name}: entry 1 disabled")
            assertTrue(g.step(), a.name)
            assertEquals(RED565, g.px565(104, 56), a.name)
        }
        // Another game's callback is not this game's: FireRed's pointer means nothing in Ruby.
        val g = Game(c, Overworld.RUBY_U).install()
        g.sprite(32, 32)
        g.callback2(Overworld.FIRERED_U_V10.cb2Overworld)
        g.step()
        assertEquals(4, g.reason())
    }

    // ------------------------------------------------------------------ what the game does to the trainer's colours

    private val PAL = intArrayOf(0x7FFF, 0x001F, 0x03E0, 0x7C00, 0x4210, 0x2108, 0x1CE7, 0x35AD, 0x5294, 0x0C63, 0x6B5A, 0x0421, 0x7BDE, 0x3DEF, 0x5AD6)

    private fun fadedBy(pal: IntArray, coeff: Int, color: Int) = IntArray(pal.size) { OverworldMath.gameBlend(pal[it], coeff, color) }

    @Test
    fun `a fade the game applies to the trainer's palette is applied to the replacement`() = run { c ->
        for ((coeff, color) in listOf(4 to 0, 8 to 0, 16 to 0, 3 to 0x7FFF, 9 to 0x7FFF, 16 to 0x7FFF, 5 to 0x001F)) {
            val g = Game(c).install()
            g.sprite(32, 32, argb = 0xFFC86432.toInt())            // R 200, G 100, B 50
            val shown = fadedBy(PAL, coeff, color)
            g.palettes(0, PAL, shown, shown)
            g.step()
            assertTrue(g.step())
            assertEquals(coeff, g.c.call("h_debug", 5).toInt(), "the fade coefficient found for ($coeff, %04X)".format(color))
            assertEquals(color, g.c.call("h_debug", 6).toInt(), "the blend colour found")
            // The same three channels through the game's own formula.
            val q = intArrayOf(200 shr 3, 100 shr 3, 50 shr 3)
            val want = OverworldMath.gameBlend(q[0] or (q[1] shl 5) or (q[2] shl 10), coeff, color)
            val px = g.px565(120, 70)
            assertEquals(OverworldMath.packPixel(2, want and 31, (want shr 5) and 31, (want shr 10) and 31), px, "fade $coeff toward %04X".format(color))
        }
    }

    @Test
    fun `no fade leaves the colours as they are`() = run { c ->
        val g = Game(c).install()
        g.sprite(32, 32, argb = 0xFFC86432.toInt())
        g.palettes(0, PAL, PAL, PAL)
        g.step(); assertTrue(g.step())
        assertEquals(0, g.c.call("h_debug", 5).toInt())
        assertEquals(OverworldMath.packPixel(2, 200 shr 3, 100 shr 3, 50 shr 3), g.px565(120, 70))
    }

    @Test
    fun `palette RAM is what was shown, and without it the buffer the game is about to copy stands in`() = run { c ->
        // Palette RAM says black at 8; the faded buffer (which the game has not copied yet) says 12: the picture had 8.
        var g = Game(c).install()
        g.sprite(32, 32)
        g.palettes(0, PAL, fadedBy(PAL, 12, 0), fadedBy(PAL, 8, 0))
        g.step(); g.step()
        assertEquals(8, g.c.call("h_debug", 5).toInt(), "palette RAM wins")
        // With palette RAM unreadable, the faded buffer read one refresh earlier stands in for it.
        g = Game(c).install()
        g.sprite(32, 32)
        g.c.call("h_plttReadable", 0)
        g.palettes(0, PAL, fadedBy(PAL, 12, 0))
        g.step(); g.step()
        assertEquals(12, g.c.call("h_debug", 5).toInt(), "the faded buffer stands in")
        assertEquals(0, g.reason())
    }

    @Test
    fun `a palette that changed for some other reason is not read as a fade`() = run { c ->
        val g = Game(c).install()
        g.sprite(32, 32, argb = 0xFFC86432.toInt())
        val other = IntArray(15) { (it * 2113 + 977) and 0x7FFF }      // nothing like PAL run through any blend
        g.palettes(0, PAL, other, other)
        g.step(); assertTrue(g.step())
        assertEquals(0, g.c.call("h_debug", 5).toInt(), "no fade claimed")
        assertEquals(OverworldMath.packPixel(2, 200 shr 3, 100 shr 3, 50 shr 3), g.px565(120, 70))
    }

    // ------------------------------------------------------------------ pixel formats

    @Test
    fun `the sprite lands in RGB565, XRGB8888 and 0RGB1555 frames as the game's own colours would`() = run { c ->
        for (fmt in listOf(2, 1, 0)) {
            val g = Game(c).install()
            g.sprite(32, 32, argb = 0xFF3CA0F0.toInt())          // R 60, G 160, B 240
            g.step(format = fmt); g.clearFrame()
            assertTrue(g.step(format = fmt))
            val r5 = 60 shr 3; val g5 = 160 shr 3; val b5 = 240 shr 3
            val want = OverworldMath.packPixel(fmt, r5, g5, b5)
            val pitch = if (fmt == 1) 240 * 4 + 64 else 240 * 2 + 32
            val got = if (fmt == 1) g.pxX8888(120, 70, pitch) else g.px565(120, 70, pitch)
            assertEquals(want, got, "format $fmt")
            // Outside the sprite stays untouched, in the padding of the row too.
            val outside = if (fmt == 1) g.pxX8888(103, 70, pitch) else g.px565(103, 70, pitch)
            assertEquals(0, outside, "format $fmt outside")
        }
        // The exact values, spelled out.
        assertEquals(0x1F, OverworldMath.packPixel(2, 0, 0, 31)); assertEquals(0xF800, OverworldMath.packPixel(2, 31, 0, 0))
        assertEquals(0x07E0, OverworldMath.packPixel(2, 0, 31, 0)); assertEquals(0xFFFF, OverworldMath.packPixel(2, 31, 31, 31))
        assertEquals(0x7C00, OverworldMath.packPixel(0, 31, 0, 0)); assertEquals(0x03E0, OverworldMath.packPixel(0, 0, 31, 0))
        assertEquals(0xFF0000, OverworldMath.packPixel(1, 31, 0, 0)); assertEquals(0x00FF00, OverworldMath.packPixel(1, 0, 31, 0))
        assertEquals(0xFFFFFF, OverworldMath.packPixel(1, 31, 31, 31))
        assertEquals(0xF800, c.call("h_pack", 2, 31, 0, 0).toInt()); assertEquals(0x7C00, c.call("h_pack", 0, 31, 0, 0).toInt())
        assertEquals(0xFF0000, c.call("h_pack", 1, 31, 0, 0).toInt())
    }

    @Test
    fun `a partly transparent pixel mixes with the game's, a transparent one leaves it, an opaque one replaces it`() = run { c ->
        val g = Game(c).install()
        // Blue game picture behind, then a half-transparent white sprite pixel column, a fully transparent one and an opaque one.
        val blue = 0x001F
        val frame = ByteArray(512 * 160)
        for (i in 0 until 512 * 160 / 2) { frame[i * 2] = blue.toByte(); frame[i * 2 + 1] = (blue shr 8).toByte() }
        c.write(WasmSpriteCore.KIND_FRAME, 0, frame)
        val w = 3
        val px = ByteArray(w * 32 * 4)
        for (y in 0 until 32) for (x in 0 until w) {
            val i = (y * w + x) * 4
            px[i] = 255.toByte(); px[i + 1] = 255.toByte(); px[i + 2] = 255.toByte()
            px[i + 3] = when (x) { 0 -> 128; 1 -> 0; else -> 255 }.toByte()
        }
        c.write(WasmSpriteCore.KIND_SPRITE, 0, px)
        c.call("h_sprite", w, 32, 0, 0)
        g.step(); g.step()
        // x = 104: 50% white over blue. Blue in 565 is 0x001F (b 31 -> 255); white 255; mix = (255*128 + 0*127 + 127)/255 = 128 for r and g, 255 for b.
        assertEquals((128 shr 3 shl 11) or (128 shr 2 shl 5) or (255 shr 3), g.px565(104, 60))
        assertEquals(blue, g.px565(105, 60), "alpha 0 leaves the pixel")
        assertEquals(0xFFFF, g.px565(106, 60), "alpha 255 replaces it")
    }

    // ------------------------------------------------------------------ what the frontend reads back

    @Test
    fun `facing and stepping are the game's own, and the frame counter counts frames while on`() = run { c ->
        val g = Game(c).install()
        g.sprite(32, 32)
        g.step()
        assertEquals(1 or (1 shl 2), g.flags(), "standing, facing down")
        g.avatar(2, 0, running = 2, transition = 0); g.facing(4)
        g.step()
        assertEquals(1 or 2 or (4 shl 2), g.flags(), "walking (running state MOVING), facing east")
        g.avatar(2, 0, running = 0, transition = 1); g.facing(3)
        g.step()
        assertEquals(1 or 2 or (3 shl 2), g.flags(), "still stepping between tiles though the key is up, facing west")
        g.avatar(2, 0, running = 1, transition = 0); g.facing(2)
        g.step()
        assertEquals(1 or (2 shl 2), g.flags(), "turning in place is not stepping, facing north")
        g.avatar(2, 0, running = 0, transition = 2); g.facing(9)
        g.step()
        assertEquals(1, g.flags(), "tile centre is not stepping, and a facing outside 1-4 is unknown")
        assertEquals(5L, g.c.call("h_frames"), "five frames while on")
        g.step(wanted = false)
        assertEquals(5L, g.c.call("h_frames"), "off, it stops counting")
        // Another object event's facing is not the player's: the avatar names which one is.
        g.avatar(2, 3, 0, 0)
        g.facing(4, objectEventId = 3)
        g.facing(1, objectEventId = 0)
        g.step()
        assertEquals(4, (g.flags() shr 2) and 7)
    }

    @Test
    fun `configuring again starts clean, and a table that is not a game's stays unconfigured`() = run { c ->
        val g = Game(c).install()
        g.sprite(32, 32)
        g.step(); g.step()
        assertEquals(2L, g.c.call("h_frames"))
        val a = g.a
        c.call("h_configure", a.main, a.oamBufferOffset, a.cb2Overworld, a.cb2OverworldBasic, a.playerAvatar, a.sprites,
            a.coordOffsetX, a.coordOffsetY, a.plttUnfaded, a.plttFaded, a.objectEvents)
        assertEquals(0L, c.call("h_frames"))
        assertEquals(0L, c.call("h_pending"), "nothing pending after a new game is configured")
        // Never configured: it does nothing and says why.
        c.call("h_reset")
        c.call("h_sprite", 8, 8, 0, 0)
        assertFalse(g.step())
        assertEquals(2, g.reason())
        assertEquals(0, g.writes())
    }

    @Test
    fun `the packed snapshot the phone reads unpacks to what the frame did`() = run { c ->
        val g = Game(c).install()
        // The native side packs 64 bits; the harness hands them back as two halves, the way a Long crosses no boundary here.
        fun snap(wanted: Boolean): Long = (c.call("h_snapshotHi", if (wanted) 1 else 0) shl 32) or c.call("h_snapshotLo", if (wanted) 1 else 0)
        g.sprite(32, 32)
        g.oam(sampleOam())
        // Nothing has run yet: every field is zero, and "wanted" is whatever the caller says.
        assertEquals(0L, snap(false))
        assertEquals(1L shl 37, snap(true))
        // Standing, facing down: one frame, the trainer replaced, not stepping.
        g.step()
        var s = snap(true)
        assertEquals(1L, SpriteSnapshot.frames(s)); assertTrue(SpriteSnapshot.replaced(s))
        assertFalse(SpriteSnapshot.stepping(s)); assertEquals(1, SpriteSnapshot.facing(s)); assertTrue(SpriteSnapshot.wanted(s))
        // "wanted" is the caller's bit and the only one that follows the argument.
        s = snap(false)
        assertFalse(SpriteSnapshot.wanted(s)); assertEquals(1L, SpriteSnapshot.frames(s)); assertTrue(SpriteSnapshot.replaced(s))
        assertEquals(snap(true) xor (1L shl 37), s)
        // Each facing, standing and stepping: the fields do not bleed into one another.
        var n = 1L
        for (dir in 1..4) for (moving in listOf(false, true)) {
            g.avatar(2, 0, running = if (moving) 2 else 0, transition = 0); g.facing(dir)
            g.step(); n++
            s = snap(true)
            assertEquals(dir, SpriteSnapshot.facing(s), "facing $dir")
            assertEquals(moving, SpriteSnapshot.stepping(s), "stepping, facing $dir")
            assertEquals(n, SpriteSnapshot.frames(s)); assertTrue(SpriteSnapshot.replaced(s)); assertTrue(SpriteSnapshot.wanted(s))
            // The raw bit positions the Java side documents: replaced 32, stepping 33, facing 34-36, wanted 37.
            assertEquals(n or (1L shl 32) or ((if (moving) 1L else 0L) shl 33) or (dir.toLong() shl 34) or (1L shl 37), s)
        }
        // Out of the overworld: not replaced, not stepping, no facing, and the frames still count.
        g.callback2(0x08012345L)
        g.avatar(2, 0, running = 2, transition = 1); g.facing(4)
        g.step(); n++
        s = snap(true)
        assertFalse(SpriteSnapshot.replaced(s)); assertFalse(SpriteSnapshot.stepping(s)); assertEquals(0, SpriteSnapshot.facing(s))
        assertEquals(n, SpriteSnapshot.frames(s)); assertTrue(SpriteSnapshot.wanted(s))
        // Back in it, with a facing the game does not use: unknown, not a wrong direction.
        g.callback2(g.a.cb2Overworld); g.avatar(2, 0, 0, 0); g.facing(9)
        g.step()
        assertEquals(0, SpriteSnapshot.facing(snap(true)))
        assertTrue(SpriteSnapshot.replaced(snap(true)))
    }
}
