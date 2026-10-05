package com.ironmonone.tracker

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Play as your Pokemon on Heart & Soul (2026-10-05): its overworld table is the comfort build's own symbols
 * (HnsLayout, exported by tools/hns/layout.py), and the structs the native side reads at fixed offsets
 * (libretrodroid's sprite_core.h) must be the build's. A rebuild that moved a field turns this red before a phone
 * draws the Pokemon in the wrong place.
 */
class OverworldHnsTest {

    private val spriteCore: String by lazy { File("../libretrodroid/src/main/cpp/sprite_core.h").readText() }

    /** A constexpr of sprite_core.h, e.g. kSpriteBytes = 0x44. */
    private fun constant(name: String): Int {
        val m = Regex("""constexpr\s+uint32_t\s+$name\s*=\s*(0x[0-9A-Fa-f]+|\d+)""").find(spriteCore) ?: error("$name not in sprite_core.h")
        val v = m.groupValues[1]
        return if (v.startsWith("0x")) v.substring(2).toInt(16) else v.toInt()
    }

    @Test
    fun `Heart and Soul gets its own table without a scan, made of the build's symbols`() {
        val nothing = MemoryReader { _, _ -> error("the Heart & Soul table needs no read of the game") }
        val a = Overworld.resolve(HnsMaps.KAIZO, nothing)
        assertSame(Overworld.HEARTSOUL_KAIZO, a)
        a!!
        assertEquals(HnsMaps.NAME, a.name)
        assertEquals(HnsLayout.gMain, a.main)
        assertEquals(HnsLayout.CB2_Overworld, a.cb2Overworld)
        assertEquals(HnsLayout.CB2_OverworldBasic, a.cb2OverworldBasic)
        assertEquals(1L, a.cb2Overworld and 1L, "gMain.callback2 holds the Thumb address")
        assertEquals(1L, a.cb2OverworldBasic and 1L)
        assertEquals(HnsLayout.gPlayerAvatar, a.playerAvatar)
        assertEquals(HnsLayout.gSprites, a.sprites)
        assertEquals(HnsLayout.gObjectEvents, a.objectEvents)
        assertEquals(HnsLayout.gPlttBufferFaded - HnsLayout.gPlttBufferUnfaded, a.plttFaded - a.plttUnfaded)
        assertEquals(11, a.toConfig().size)
        assertTrue(Overworld.hasTable(HnsMaps.KAIZO))
        assertTrue("Heart & Soul" in Overworld.whyNot(HnsMaps.KAIZO))
    }

    @Test
    fun `the structs sprite_core reads are the comfort build's sizes and offsets`() {
        assertEquals(constant("kSpriteBytes"), HnsLayout.Sprite.SIZE, "sizeof(struct Sprite)")
        assertEquals(constant("kMaxSprites"), HnsLayout.MAX_SPRITES)
        assertEquals(constant("kObjectEventBytes"), HnsLayout.ObjectEvent.SIZE, "sizeof(struct ObjectEvent)")
        assertEquals(constant("kObjectEventCount"), HnsLayout.OBJECT_EVENTS_COUNT)
        // readPlayer: gMain+4, avatar +4/+5 (and +2/+3 for moving), sprite oam at 0, x/y/x2/y2 at 0x20-0x26, the
        // centre vector at 0x28/0x29, inUse/coordOffsetEnabled/invisible as bits 0-2 of 0x3E, facing in 0x18's low nibble.
        assertEquals(4, HnsLayout.Main.callback2.offset)
        assertEquals(0x38, HnsLayout.Main.oamBuffer.offset)
        assertEquals(128 * 8, HnsLayout.Main.oamBuffer.size)
        assertEquals(2, HnsLayout.PlayerAvatar.runningState.offset)
        assertEquals(3, HnsLayout.PlayerAvatar.tileTransitionState.offset)
        assertEquals(4, HnsLayout.PlayerAvatar.spriteId.offset)
        assertEquals(5, HnsLayout.PlayerAvatar.objectEventId.offset)
        assertEquals(0, HnsLayout.Sprite.oam.offset)
        assertEquals(listOf(0x20, 0x22, 0x24, 0x26), listOf(HnsLayout.Sprite.x, HnsLayout.Sprite.y, HnsLayout.Sprite.x2, HnsLayout.Sprite.y2).map { it.offset })
        assertEquals(0x28, HnsLayout.Sprite.centerToCornerVecX.offset)
        assertEquals(0x29, HnsLayout.Sprite.centerToCornerVecY.offset)
        for ((f, bit) in listOf(HnsLayout.Sprite.inUse to 0, HnsLayout.Sprite.coordOffsetEnabled to 1, HnsLayout.Sprite.invisible to 2)) {
            assertEquals(0x3E, f.offset); assertEquals(bit, f.shift); assertEquals(1, f.width)
        }
        assertEquals(0x18, HnsLayout.ObjectEvent.facingDirection.offset)
        assertEquals(0, HnsLayout.ObjectEvent.facingDirection.shift)
        assertEquals(4, HnsLayout.ObjectEvent.facingDirection.width)
        assertTrue("sp[0x3E]" in spriteCore && "+ 0x18u" in spriteCore, "sprite_core.h still reads the offsets this test holds")
    }

    @Test
    fun `the table sits where the native side allows, as configPlausible and the scan's range checks say`() {
        val a = Overworld.HEARTSOUL_KAIZO
        assertTrue(a.main in 0x03000000L..0x03007000L)
        assertTrue(a.oamBufferOffset in 0x30..0x40)
        assertTrue(a.playerAvatar in 0x02000000L..0x0203FFE0L)
        assertTrue(a.sprites in 0x02000000L..(0x0203FFFFL - 64L * 0x44))
        assertTrue(a.coordOffsetX in 0x02000000L..0x0203FFFCL && a.coordOffsetY in 0x02000000L..0x0203FFFCL)
        assertTrue(a.plttUnfaded in 0x02000000L..0x0203FBFFL && a.plttFaded in 0x02000000L..0x0203FBFFL)
        assertTrue(a.objectEvents in 0x02000000L..0x0203FDBFL)
        assertTrue(a.cb2Overworld in 0x08000001L..0x09FFFFFFL && a.cb2OverworldBasic in 0x08000001L..0x09FFFFFFL)
    }
}
