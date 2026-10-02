package com.ironmonone.tracker

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * B-to-Run reads the action menu from gBattleCommunication[0] (2026-10-01): the address and the byte that means "the
 * action menu is up" are checked here against each game's own HandleTurnActionSelectionState. Its literal pool holds
 * gBattleCommunication, and its switch on that byte (ldrb r0, [r0]; cmp r0, #N) has N + 1 states: 9 where the enum
 * starts with STATE_TURN_START_RECORD (Emerald, and the Emerald Nat. Dex build), so the menu waits at 2; 7 or 8 where
 * it does not (FireRed, LeafGreen, Ruby, Sapphire, the FireRed Nat. Dex build), so it waits at 1.
 *
 * The ROMs are the player's own dumps in .vendor/roms; a missing one is skipped, as in FireRedNatDexTest.
 */
class ActionMenuStateRomTest {
    private val dir = File("C:/Users/bepor/IronMonOne/.vendor/roms")

    private fun rom(name: String): Pair<ByteArray, GameMap>? {
        val f = File(dir, name)
        if (!f.exists()) { println("SKIP: $name missing"); return null }
        val bytes = f.readBytes()
        val mem = MemoryReader { address, length ->
            val off = (address - 0x08000000L).toInt()
            if (address >= 0x08000000L && off >= 0 && off + length <= bytes.size) bytes.copyOfRange(off, off + length)
            else ByteArray(0)
        }
        return GameMap.resolveOrNull(mem)?.let { bytes to it }
    }

    private fun u16(b: ByteArray, at: Int) = (b[at].toInt() and 0xFF) or ((b[at + 1].toInt() and 0xFF) shl 8)
    private fun u32(b: ByteArray, at: Int) = u16(b, at).toLong() or (u16(b, at + 2).toLong() shl 16)

    private fun check(name: String) {
        val (b, map) = rom(name) ?: return
        val fn = ((map.handleTurnAction and 1L.inv()) - 0x08000000L).toInt()
        assertTrue(map.battleCommunication != 0L, "$name: no gBattleCommunication")
        val pool = (fn until fn + 0x80 step 4).map { u32(b, it) }
        assertTrue(map.battleCommunication in pool, "$name: HandleTurnActionSelectionState does not use ${map.battleCommunication.toString(16)}")
        val sw = (fn until fn + 0x60 step 2).first { u16(b, it) == 0x7800 && (u16(b, it + 2) and 0xFF00) == 0x2800 }
        val states = (u16(b, sw + 2) and 0xFF) + 1
        assertEquals(if (states == 9) 2 else 1, map.actionMenuState, "$name: $states states")
    }

    @Test fun `Emerald`() = check("emerald-u.gba")
    @Test fun `FireRed 1_0 and 1_1, LeafGreen`() { check("firered-u-v10.gba"); check("firered-u-v11.gba"); check("leafgreen-u.gba") }
    @Test fun `Ruby and Sapphire`() { check("ruby-u.gba"); check("sapphire-u.gba") }
    @Test fun `both Nat Dex builds, through the ROM's own slot`() { check("emerald-natdex-121.gba"); check("firered-natdex-121.gba") }
}
