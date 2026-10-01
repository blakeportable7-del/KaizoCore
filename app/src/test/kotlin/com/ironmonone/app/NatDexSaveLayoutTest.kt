package com.ironmonone.app

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The Nat. Dex builds' in-game save, read off the ROMs (2026-09-30), so RunSaves reads their party count where vanilla
 * keeps it. SavePlayerParty loads gPlayerPartyCount (the address the ROM publishes at 0x08000278) and stores it at
 * SaveBlock1 + 0x34 on FireRed and + 0x234 on Emerald, as vanilla does; only the party slots grew, to 104 bytes. The
 * save sections hold 0xFEC bytes, not 0xF80, but SaveBlock1's first chunk is still section 1.
 *
 * The ROMs are local files, never in the repository: without them this says so and passes.
 */
class NatDexSaveLayoutTest {
    private fun rom(name: String): ByteArray? =
        File("C:/Users/bepor/IronMonOne/.vendor/roms/$name").takeIf { it.exists() }?.readBytes()
            ?: run { println("SKIP: $name missing"); null }

    private fun u16(b: ByteArray, i: Int) = (b[i].toInt() and 0xFF) or ((b[i + 1].toInt() and 0xFF) shl 8)
    private fun u32(b: ByteArray, i: Int) = u16(b, i).toLong() or (u16(b, i + 2).toLong() shl 16)
    private fun ByteArray.find(p: ByteArray): List<Int> =
        (0..size - p.size).filter { i -> p.indices.all { this[i + it] == p[it] } }
    private fun bytes(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }
    private fun halves(vararg v: Int) = ByteArray(v.size * 2) { (v[it / 2] shr (8 * (it % 2))).toByte() }

    /**
     * How many times [store] (the instructions after `ldrb r1, [r1]`) writes the party count into SaveBlock1: each
     * must follow an `ldr r1, [pc, #n]` whose word is [partyCount], gPlayerPartyCount's address.
     */
    private fun countStores(rom: ByteArray, partyCount: Long, store: ByteArray): Int =
        rom.find(bytes(0x09, 0x78) + store).count { at ->
            val ldr = u16(rom, at - 2)
            if (ldr and 0xFF00 != 0x4900) return@count false
            val word = ((0x08000000 + at - 2 + 4) and 3.inv()) + (ldr and 0xFF) * 4 - 0x08000000
            u32(rom, word) == partyCount
        }

    /** SaveBlock1's chunks in the section table, at 0, X and 2X with X bytes each, right after SaveBlock2's one entry. */
    private fun saveBlock1Section(rom: ByteArray, x: Int): Int? {
        val at = rom.find(halves(0, x, x, x, 2 * x, x)).firstOrNull() ?: return null
        return if (u16(rom, at - 4) == 0 && u16(rom, at - 2) in 0x100..x) 1 else null
    }

    // adds r0, #0x34; strb r1, [r0]
    private val atX34 = bytes(0x34, 0x30, 0x01, 0x70)
    // movs r2, #0x8D; lsls r2, r2, #2; adds r0, r0, r2; strb r1, [r0]
    private val atX234 = bytes(0x8D, 0x22, 0x92, 0x00, 0x80, 0x18, 0x01, 0x70)

    @Test
    fun `FireRed Nat Dex keeps the party count at SaveBlock1 plus 0x34, in section 1`() {
        val rom = rom("firered-natdex-121.gba") ?: return
        assertEquals(1, countStores(rom, u32(rom, 0x278), atX34), "SavePlayerParty")
        assertEquals(0, countStores(rom, u32(rom, 0x278), atX234))
        assertEquals(1, saveBlock1Section(rom, 0xFEC))
    }

    @Test
    fun `Emerald Nat Dex keeps it at SaveBlock1 plus 0x234, in section 1`() {
        val rom = rom("emerald-natdex-121.gba") ?: return
        assertEquals(1, countStores(rom, u32(rom, 0x278), atX234), "SavePlayerParty")
        assertEquals(0, countStores(rom, u32(rom, 0x278), atX34))
        assertEquals(1, saveBlock1Section(rom, 0xFEC))
    }

    @Test
    fun `the vanilla ROMs give the answers the save reader already uses`() {
        val fr = rom("firered-u-v11.gba") ?: return
        assertEquals(1, countStores(fr, 0x02024029L, atX34))
        assertEquals(1, saveBlock1Section(fr, 0xF80))
        val em = rom("emerald-u.gba") ?: return
        assertEquals(1, countStores(em, 0x020244E9L, atX234))
        assertEquals(1, saveBlock1Section(em, 0xF80))
    }
}
