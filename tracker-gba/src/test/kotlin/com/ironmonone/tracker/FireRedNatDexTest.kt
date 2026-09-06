package com.ironmonone.tracker

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * FireRed Nat. Dex, the build that reported "TRACKER CANNOT READ THIS ROM".
 *
 * Both Nat. Dex builds lay the party block out identically - the count byte,
 * the player party at count+3, the enemy party 0x270 past that - but the
 * FireRed build TRANSPOSES the two party pointer slots relative to Emerald:
 *
 *   Emerald  count 020244D1  slot27C 020244D4  slot280 02024744
 *   FireRed  count 020242F5  slot27C 02024568  slot280 020242F8
 *
 * Reading slot 0x27C as "the party" is right on Emerald and hands back the
 * ENEMY party on FireRed, so every FireRed Nat. Dex run decoded the wrong
 * block, failed the sanity check, and hid the tracker for the whole session.
 */
class FireRedNatDexTest {

    private val rom = File(
        "C:/Users/bepor/IronMonOne/.vendor/roms/firered-natdex-121.gba"
    )

    private fun map(): GameMap? {
        if (!rom.exists()) return null
        val bytes = rom.readBytes()
        val mem = MemoryReader { address, length ->
            val off = (address - 0x08000000L).toInt()
            if (address >= 0x08000000L && off >= 0 && off + length <= bytes.size)
                bytes.copyOfRange(off, off + length)
            else ByteArray(0)
        }
        return GameMap.resolveOrNull(mem)
    }

    @Test
    fun `the party comes from the slot the extension documents`() {
        val m = map() ?: run { println("SKIP: FR natdex rom missing"); return }
        assertEquals(0x020242F5L, m.partyCount)
        // Slot 0x27C is gPlayerParty per NatDexExtension.lua:17690. An earlier
        // version picked "the slot at count+3" instead, which is how EMERALD
        // happens to sit and is not a rule - vanilla FireRed puts a 0x25B gap
        // between count and party. That guess was chasing the wrong bug.
        assertEquals(0x02024568L, m.party)
        assertEquals(0x020242F8L, m.enemyParty)
    }

    @Test
    fun `the Pokemon struct is the expansion's 104-byte shape, not vanilla`() {
        val m = map() ?: run { println("SKIP: FR natdex rom missing"); return }
        // THE actual cause of "TRACKER CANNOT READ THIS ROM": Nat. Dex inserts
        // four bytes before the encrypted substructures, so the struct is 104
        // bytes and everything from 0x20 on shifts by +4. Decoded against the
        // vanilla layout a real party comes out as impossible values.
        assertEquals(104, m.monLayout.size)
        assertEquals(0x24, m.monLayout.enc)
        assertEquals(0x54, m.monLayout.status)
        assertEquals(0x58, m.monLayout.level)
        assertEquals(0x5A, m.monLayout.curHp)
        assertEquals(0x5C, m.monLayout.maxHp)
    }

    @Test
    fun `the rest of the FireRed Nat Dex map resolves too`() {
        val m = map() ?: run { println("SKIP: FR natdex rom missing"); return }
        assertEquals("Nat. Dex", m.name)
        assertTrue(m.baseStats in 0x08000000L..0x09FFFFFFL, "baseStats ${m.baseStats}")
        assertTrue(m.saveBlock1Ptr in 0x03000000L..0x03007FFFL, "sb1 ${m.saveBlock1Ptr}")
        assertTrue(m.mapHeader in 0x02000000L..0x0203FFFFL, "mapHeader ${m.mapHeader}")
        // Learnsets must be the 1.21 table in the wide format, as on Emerald.
        assertTrue(m.learnsetWide)
        assertTrue(m.levelUpLearnsets in 0x08000000L..0x09FFFFFFL)
    }

    /**
     * The battler stride comes from the ROM, not from a constant.
     *
     * Hardcoded at 0x58 it read the enemy battler four bytes early on both
     * Nat. Dex builds, so the opponent's card never rendered in ANY battle -
     * while the battle banner still appeared, because battler 0 is at offset
     * 0 and survives a wrong stride. The symptom looked like "the enemy panel
     * is missing"; the cause was one number.
     */
    @Test
    fun `the battle mon stride is read from the ROM`() {
        val m = map() ?: return          // ROM absent: nothing to assert
        assertEquals(92, m.battleMonSize,
            "Nat. Dex publishes sizeofBattlePokemon = 92 at 0x08000436")
        // And the vanilla maps must NOT have moved.
        assertEquals(0x58, GameMap.EMERALD_U.battleMonSize)
        assertEquals(0x58, GameMap.FIRERED_U_V10.battleMonSize)
    }

    /**
     * The save-block offsets come from the ROM, not from vanilla constants.
     *
     * Six of seven were hardcoded to the vanilla layout while the pointers
     * around them were already resolved from the slot table. The visible
     * symptom was a step counter reading 8,487,400 on a fresh save - the
     * wrong address, XORed with a key fetched from another wrong address -
     * but badges and the bag (and so the Heals line) read the wrong place too.
     *
     * Values are the ones the shipped ROM publishes, dumped independently.
     */
    @Test
    fun `save block offsets are read from the ROM`() {
        val m = map() ?: return
        assertEquals(0x13B0L, m.gameStatsOffset, "gameStatsOffset")
        assertEquals(0x40CL, m.encryptionKeyOffset, "encryptionKeyOffset")
        assertEquals(0x1194L, m.badgeOffset, "badgeOffset")
        assertEquals(0x328L, m.bagItemsOffset, "bagItemsOffset")
        assertEquals(0x69CL, m.bagBerriesOffset, "bagBerriesOffset")
        assertEquals(120, m.bagItemsSlots, "bagItemsSlots")
        assertEquals(43, m.bagBerriesSlots, "bagBerriesSlots")

        // The vanilla maps must be untouched by this.
        assertEquals(0x1200L, GameMap.FIRERED_U_V10.gameStatsOffset)
        assertEquals(0xF20L, GameMap.FIRERED_U_V10.encryptionKeyOffset)
    }
}
