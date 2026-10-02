package com.ironmonone.tracker

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * B-to-Run on Red, Blue and Yellow (2026-10-02): run only from the battle's own menu. It fired on every B in a wild
 * battle, so a B in the item list was followed by the run's A, which in Gen 1 uses the item at once (a Poke Ball is
 * thrown). The battle menu is the one menu that never watches B (DisplayBattleMenu: row 0x0E, column 0x09 watching
 * RIGHT and A, or 0x0F watching LEFT and A, two items a column), with its cursor on screen.
 */
class Gen1ActionMenuTest {
    private class Wram : MemoryReader {
        val bytes = ByteArray(0x2000)
        fun put(off: Long, v: Int) { bytes[off.toInt()] = v.toByte() }
        override fun read(address: Long, length: Int): ByteArray {
            val o = (address - Gen1Tracker.RAM).toInt()
            return if (o >= 0 && o + length <= bytes.size) bytes.copyOfRange(o, o + length) else ByteArray(0)
        }
    }

    private fun rom(title: String) = ByteArray(0x50000).also { r -> title.forEachIndexed { i, c -> r[0x134 + i] = c.code.toByte() } }

    /** Memory as DisplayBattleMenu leaves it, cursor on [item] (0 top, 1 bottom) of the [left] or right column. */
    private fun menu(w: Wram, map: Gen1Map, left: Boolean, item: Int = 0, battle: Int = 1) {
        w.put(map.inBattle, battle)
        val col = if (left) 0x09 else 0x0F
        val m = Gen1Tracker.MENU
        w.put(m, 0x0E); w.put(m + 1, col); w.put(m + 2, item); w.put(m + 4, 1); w.put(m + 5, if (left) 0x11 else 0x21)
        w.put(Gen1Tracker.TILE_MAP + (0x0E + item * 2) * 20L + col, Gen1Tracker.CURSOR_TILE)
    }

    @Test
    fun `the battle menu in either column is the action menu, in Red, Blue and Yellow`() {
        for ((map, title) in listOf(Gen1Map.RED_BLUE to "POKEMON RED", Gen1Map.RED_BLUE to "POKEMON BLUE", Gen1Map.YELLOW to "POKEMON YELLOW")) {
            for (left in listOf(true, false)) for (item in 0..1) {
                val w = Wram().also { menu(it, map, left, item) }
                assertTrue(Gen1Tracker(w, rom(title)).isChoosingActionInWild(), "$title left=$left item=$item")
            }
        }
    }

    @Test
    fun `the item list, the move menu, a trainer battle and a menu already gone are not`() {
        val map = Gen1Map.RED_BLUE
        val t = { w: Wram -> Gen1Tracker(w, rom("POKEMON RED")).isChoosingActionInWild() }
        // The item list (DisplayListMenuID) watches B; the move menu watches UP, DOWN, A and B.
        assertFalse(t(Wram().also { menu(it, map, true); it.put(Gen1Tracker.MENU + 5, 0x03) }), "watches B")
        assertFalse(t(Wram().also { menu(it, map, true); it.put(Gen1Tracker.MENU + 5, 0xC3); it.put(Gen1Tracker.MENU, 0x0C) }), "move menu")
        assertFalse(t(Wram().also { menu(it, map, true, battle = 2) }), "a trainer battle never offers the run")
        assertFalse(t(Wram().also { menu(it, map, false, battle = 0) }), "no battle")
        // The menu bytes outlive the menu: once a text box covers it, its cursor is gone.
        assertFalse(t(Wram().also { menu(it, map, false, 1); it.put(Gen1Tracker.TILE_MAP + 0x10 * 20L + 0x0F, 0x7F) }), "covered")
        // A Safari battle's menu sits in columns 0x01 and 0x0D, and has no RUN where the macro would look.
        assertFalse(t(Wram().also { menu(it, map, true); it.put(Gen1Tracker.MENU + 1, 0x01) }), "Safari")
    }

    /** The cartridges themselves: DisplayBattleMenu writes these bytes to these addresses, in each of the player's dumps. */
    @Test
    fun `each Gen 1 cartridge sets its battle menu up exactly so`() {
        val dir = File("C:/Users/bepor/IronMonOne/.vendor/roms")
        // ld hl, wTopMenuItemY (0xCC24); ld a, $0E; ld [hli], a; ld a, b; ld [hli], a; inc hl; inc hl; ld a, $01; ld [hli], a
        val head = intArrayOf(0x21, 0x24, 0xCC, 0x3E, 0x0E, 0x22, 0x78, 0x22, 0x23, 0x23, 0x3E, 0x01, 0x22)
        val leftKeys = head + intArrayOf(0x36, 0x11)         // ld [hl], PAD_RIGHT | PAD_A
        val rightKeys = head + intArrayOf(0x3E, 0x21, 0x22)   // ld a, PAD_LEFT | PAD_A; ld [hli], a
        for (name in listOf("red-u.gbc", "blue-u.gbc", "yellow-u.gbc")) {
            val f = File(dir, name)
            if (!f.exists()) { println("SKIP: $name missing"); continue }
            val b = f.readBytes()
            fun has(p: IntArray) = (0..b.size - p.size).any { i -> p.indices.all { (b[i + it].toInt() and 0xFF) == p[it] } }
            assertTrue(has(leftKeys), "$name: left column")
            assertTrue(has(rightKeys), "$name: right column")
        }
    }
}
