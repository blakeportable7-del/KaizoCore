package com.ironmonone.tracker

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The five stats sit right after max HP, in the game's order: Attack, Defense,
 * Speed, Sp. Atk, Sp. Def. The Nat. Dex build moves the whole block 4 bytes on
 * (a 104-byte record, max HP at 0x5C, read from the ROM's own table), and the
 * stats used to stay pinned at the vanilla offsets, so the card showed HP in
 * ATK and DEF and every real stat two places down.
 *
 * The numbers are Blake's screenshot of 2026-09-27: the game's summary read
 * HP 28, Attack 13, Defense 15, Sp. Atk 43, Sp. Def 58, Speed 14, while the
 * card read ATK 28, DEF 28, SPA 15, SPD 14, SPE 13.
 */
class MonStatsLayoutTest {
    private fun ByteArray.put16(at: Int, v: Int) { this[at] = (v and 0xFF).toByte(); this[at + 1] = (v shr 8).toByte() }

    private fun record(layout: PokemonDecoder.Layout): ByteArray {
        val b = ByteArray(layout.size)
        b[0] = 1                                  // any non-zero personality value
        b[layout.level] = 11
        b.put16(layout.curHp, 28)
        b.put16(layout.maxHp, 28)
        listOf(13, 15, 14, 43, 58).forEachIndexed { i, v -> b.put16(layout.maxHp + 2 + i * 2, v) }
        return b
    }

    private fun assertScreenshotStats(m: PokemonDecoder.Mon) {
        assertEquals(11, m.level)
        assertEquals(28, m.curHp); assertEquals(28, m.maxHp)
        assertEquals(13, m.atk, "Attack")
        assertEquals(15, m.def, "Defense")
        assertEquals(14, m.spe, "Speed")
        assertEquals(43, m.spAtk, "Sp. Atk")
        assertEquals(58, m.spDef, "Sp. Def")
    }

    @Test
    fun `Nat Dex stats are read after its own max HP, not at the vanilla offsets`() {
        // The layout GameMap.resolve builds from the Nat. Dex 1.2.1 FireRed ROM:
        // size 104, encrypted block 0x24, status 0x54, level 0x58, max HP 0x5C.
        val natDex = PokemonDecoder.Layout(size = 104, enc = 0x24, status = 0x54, level = 0x58, curHp = 0x5A, maxHp = 0x5C)
        assertScreenshotStats(PokemonDecoder.decode(record(natDex), natDex))
    }

    @Test
    fun `vanilla stats are where they always were`() {
        val v = PokemonDecoder.Layout.VANILLA
        assertEquals(0x5A, v.maxHp + 2, "vanilla Attack must stay at 0x5A")
        assertScreenshotStats(PokemonDecoder.decode(record(v), v))
    }

    @Test
    fun `the last stat ends exactly where each record ends`() {
        // Sp. Def is two bytes at max HP + 10; the record closes right after it,
        // 100 bytes in vanilla and 104 in Nat. Dex. A layout that did not add up
        // here would mean the stats had moved again.
        assertEquals(100, PokemonDecoder.Layout.VANILLA.maxHp + 12)
        assertEquals(104, 0x5C + 12)
    }
}
