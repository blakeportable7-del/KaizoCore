package com.ironmonone.tracker

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * B-to-Run on Gold, Silver and Crystal (2026-10-02): run only from the battle's own menu, never from the Pack, the
 * party screen or the move menu, where B means "back" and the run's A would pick something. The battle menu is a 2D
 * menu at (8, 12), 2 by 2, columns six apart, with B disabled, so its joypad filter is A alone.
 */
class Gen2ActionMenuTest {
    private class Wram : MemoryReader {
        val bytes = ByteArray(0x8000)
        fun put(off: Long, v: Int) { bytes[off.toInt()] = v.toByte() }
        override fun read(address: Long, length: Int): ByteArray {
            val o = (address - GbcTracker.RAM).toInt()
            return if (o >= 0 && o + length <= bytes.size) bytes.copyOfRange(o, o + length) else ByteArray(0)
        }
    }

    private fun rom(title: String) = ByteArray(0x60000).also { r ->
        title.forEachIndexed { i, c -> r[0x134 + i] = c.code.toByte() }
        r[0x143] = 0x80.toByte()
    }

    /** The bytes Init2DMenuCursorPosition and Place2DMenuCursor leave for the battle menu, cursor drawn at [tile]. */
    private fun menu(w: Wram, map: Gen2Map, tilemap: Int, battle: Int = 1, cursorY: Int = 1, cursorX: Int = 1) {
        w.put(map.battleMode, battle)
        val m = map.menu2D
        listOf(0x0E, 0x09, 2, 2, 0, 0, 0x26, 0x01, cursorY, cursorX).forEachIndexed { i, v -> w.put(m + i, v) }
        val tile = tilemap + (0x0E + (cursorY - 1) * 2) * 20 + 0x09 + (cursorX - 1) * 6
        w.put(m + 11, tile and 0xFF); w.put(m + 12, tile shr 8)
        w.put(tile - 0xC000L, 0xED)
    }

    @Test
    fun `the battle menu is the action menu, on Crystal and on Gold and Silver`() {
        for ((map, title, tilemap) in listOf(Triple(Gen2Map.CRYSTAL, "PM_CRYSTAL", 0xC4A0), Triple(Gen2Map.GS, "POKEMON_GLD", 0xC3A0),
                                              Triple(Gen2Map.GS, "POKEMON_SLV", 0xC3A0))) {
            for (y in 1..2) for (x in 1..2) {
                val w = Wram().also { menu(it, map, tilemap, cursorY = y, cursorX = x) }
                assertTrue(GbcTracker(w, rom(title)).isChoosingActionInWild(), "$title at $y,$x")
            }
        }
    }

    @Test
    fun `the Pack, the move menu, a trainer battle and a menu already gone are not`() {
        val map = Gen2Map.CRYSTAL
        val t = { w: Wram -> GbcTracker(w, rom("PM_CRYSTAL")).isChoosingActionInWild() }
        assertFalse(t(Wram().also { menu(it, map, 0xC4A0); it.put(map.menu2D + 7, 0x03) }), "a menu that takes B")
        assertFalse(t(Wram().also { menu(it, map, 0xC4A0); it.put(map.menu2D + 3, 1); it.put(map.menu2D + 2, 4) }), "the move menu")
        assertFalse(t(Wram().also { menu(it, map, 0xC4A0, battle = 2) }), "a trainer battle never offers the run")
        assertFalse(t(Wram().also { menu(it, map, 0xC4A0, battle = 0) }), "no battle")
        // ExitMenu puts back what was under the cursor.
        assertFalse(t(Wram().also { menu(it, map, 0xC4A0); it.put(0x04A0L + 14 * 20 + 9, 0x7F) }), "menu gone")
        // The Safari menu is spaced eleven apart: no RUN where the run's RIGHT, DOWN lands.
        assertFalse(t(Wram().also { menu(it, map, 0xC4A0); it.put(map.menu2D + 6, 0x2B) }), "Safari")
    }

    /** The cartridge itself: BattleMenuHeader, and the addresses its 2D menu code writes, in the player's own dump. */
    @Test
    fun `Crystal's cartridge holds that battle menu and those addresses`() {
        val f = File("C:/Users/bepor/IronMonOne/.vendor/roms/crystal-u.gbc")
        if (!f.exists()) { println("SKIP: crystal-u.gbc missing"); return }
        val b = f.readBytes()
        fun has(p: IntArray, wild: Set<Int> = emptySet()) = (0..b.size - p.size).any { i ->
            p.indices.all { it in wild || (b[i + it].toInt() and 0xFF) == p[it] }
        }
        // db MENU_BACKUP_TILES; menu_coords 8, 12, 19, 17; dw .MenuData; db 1; .MenuData: STATICMENU_CURSOR | DISABLE_B, dn 2, 2, 6
        assertTrue(has(intArrayOf(0x40, 0x0C, 0x08, 0x11, 0x13, 0, 0, 0x01, 0x81, 0x22, 0x06), setOf(5, 6)), "BattleMenuHeader")
        assertTrue(has(intArrayOf(0xEA, 0xA1, 0xCF)), "ld [w2DMenuCursorInitY], a")
        assertTrue(has(intArrayOf(0xEA, 0xA8, 0xCF)), "ld [wMenuJoypadFilter], a")
        assertTrue(has(intArrayOf(0xEA, 0xAC, 0xCF)), "ld [wCursorCurrentTile], a")
    }
}
