package com.ironmonone.tracker

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A double battle's right-hand battlers (Battle.lua:288-297, Combatants.RightOwn and RightOther): your battler 2 and the
 * opponent's battler 3 are read with their own stat stages and heals, so the swap can show them (rc34; Blake,
 * 2026-10-03: "on doubles how do you cycle through your party pokemon in the tracker?"). Until then only battler 0 and
 * battler 1 were ever read.
 */
class DoublesReadsTest {

    private class Fake : MemoryReader {
        val bytes = HashMap<Long, Byte>()
        fun put(addr: Long, value: Long, len: Int) {
            for (i in 0 until len) bytes[addr + i] = ((value shr (8 * i)) and 0xFF).toByte()
        }
        fun put(addr: Long, data: ByteArray) {
            data.forEachIndexed { i, b -> bytes[addr + i] = b }
        }
        override fun read(address: Long, length: Int) =
            ByteArray(length) { bytes[address + it] ?: 0 }
    }

    /** A vanilla-layout party mon. [pid] a multiple of 24 with otId == pid: the growth block first, the key zero. */
    private fun mon(species: Int, level: Int, curHp: Int, maxHp: Int, pid: Long): ByteArray {
        val plain = ByteArray(48)
        plain[0] = species.toByte(); plain[1] = (species shr 8).toByte()
        val m = ByteArray(100)
        m.putU32(0, pid); m.putU32(4, pid)
        for (w in 0 until 12) m.putU32(0x20 + w * 4, plain.u32(w * 4))
        m[0x54] = level.toByte()
        m[0x56] = curHp.toByte(); m[0x57] = (curHp shr 8).toByte()
        m[0x58] = maxHp.toByte(); m[0x59] = (maxHp shr 8).toByte()
        return m
    }

    /** gBattleMons[battler]: species, HP, level, max HP, and its stage block (HP ATK DEF SPE SPA SPD ACC EVA). */
    private fun Fake.battler(map: GameMap, b: Int, species: Int, level: Int, hp: Int, stages: ByteArray = ByteArray(8) { 6 }) {
        val at = map.battleMons + b.toLong() * map.battleMonSize
        put(at, species.toLong(), 2)
        put(at + map.battleMonHp, hp.toLong(), 2); put(at + map.battleMonHp + 2, level.toLong(), 1); put(at + map.battleMonHp + 4, hp.toLong(), 2)
        put(at + 0x18, stages)
    }

    /**
     * A trainer's double battle against [opponent]: your Pikachu (slot 1, 30 HP) on the left and Charmander (slot 3,
     * 40 HP) on the right, Bulbasaur on the bench; the opponent's Pidgey on its left and Rattata on its right. One Potion
     * in the bag. [battlers] 2 makes it a single battle, where only battlers 0 and 1 stand.
     */
    private fun fight(map: GameMap = GameMap.EMERALD_U, opponent: Int = 1, battlers: Int = 4, rightSlot: Int = 2): Fake = Fake().apply {
        put(map.battlersCount, battlers.toLong(), 1)
        battler(map, 0, 25, 10, 30, byteArrayOf(6, 8, 6, 6, 6, 6, 6, 6))          // ATK +2
        battler(map, 1, 16, 8, 20)
        battler(map, 2, 4, 12, 40, byteArrayOf(6, 6, 4, 6, 6, 6, 6, 6))           // DEF -2
        battler(map, 3, 19, 9, 22, byteArrayOf(6, 6, 6, 9, 6, 6, 6, 6))           // SPE +3
        put(map.battleOutcome, 0, 1)
        put(map.battleMainFunc, map.handleTurnAction, 4)
        put(map.battleTypeFlags, 0x9, 4)                                           // TRAINER | DOUBLE
        put(map.partyCount, 3, 1)
        put(map.party, mon(25, 10, 30, 30, 0x18))
        put(map.party + map.monLayout.size, mon(1, 5, 20, 20, 0x30))
        put(map.party + 2L * map.monLayout.size, mon(4, 12, 40, 40, 0x48))
        // gBattlerPartyIndexes: yours slot 1 and [rightSlot], the opponent's its slots 1 and 2.
        put(map.battlerPartyIndexes, 0, 2); put(map.battlerPartyIndexes + 2, 0, 2)
        put(map.battlerPartyIndexes + 4, rightSlot.toLong(), 2); put(map.battlerPartyIndexes + 6, 1, 2)
        put(map.enemyParty, mon(16, 8, 20, 20, 0x60))
        put(map.enemyParty + map.monLayout.size, mon(19, 9, 22, 22, 0x78))
        put(map.trainerOpponent, opponent.toLong(), 2)
        // The bag: one Potion (20 HP) in the Items pocket, the security key zero.
        put(map.saveBlock1Ptr, 0x02025000L, 4)
        put(0x02025000L + map.bagItemsOffset, 13, 2); put(0x02025000L + map.bagItemsOffset + 2, 1, 2)
    }

    private fun settle(m: Fake, map: GameMap = GameMap.EMERALD_U): TrackerState {
        val t = GbaTracker(m, map); t.read(); return t.read()
    }

    @Test
    fun `a double battle reads your right-hand battler and the opponent's, each with its own stages`() {
        val s = settle(fight())
        assertTrue(s.inBattle); assertTrue(s.doubles)
        assertEquals(0, s.ownOnField)
        assertEquals(25, s.onField?.mon?.species)
        assertEquals(2, s.ownRightOnField)
        val right = assertNotNull(s.ownRight)
        assertEquals(4, right.mon.species)
        assertEquals(4, right.statStages["DEF"], "battler 2's stage block, not battler 0's")
        assertEquals(8, s.party[0].statStages["ATK"])
        assertTrue(s.party[1].statStages.isEmpty(), "the bench has none")
        assertEquals(16, s.enemy?.species)
        val foeRight = assertNotNull(s.enemyRight)
        assertEquals(19, foeRight.species)
        assertEquals(9, foeRight.level)
        assertEquals(9, foeRight.statStages["SPE"])
        assertEquals(6, s.enemy?.statStages?.get("SPE"), "the left one keeps its own")
        assertEquals(listOf(0, 1), s.enemyOnField)
    }

    @Test
    fun `the heals are a share of whichever of yours is viewed`() {
        val s = settle(fight())
        assertEquals(67, s.healPercent, "a Potion is 20 of Pikachu's 30 HP")
        assertEquals(50, s.ownRightHeals.percent, "and 20 of Charmander's 40")
        assertEquals(20, s.ownRightHeals.hp)
        assertEquals(1, s.ownRightHeals.count)
    }

    @Test
    fun `a single battle has no right-hand side`() {
        val s = settle(fight(battlers = 2))
        assertTrue(s.inBattle); assertFalse(s.doubles)
        assertEquals(-1, s.ownRightOnField)
        assertNull(s.ownRight)
        assertNull(s.enemyRight)
        assertEquals(HealTotals.NONE, s.ownRightHeals)
        assertFalse(s.allyHidden)
        assertTrue(s.party[2].statStages.isEmpty(), "battler 2's block is not read")
    }

    @Test
    fun `a right-hand battler past your party is a partner's, not one of yours`() {
        val s = settle(fight(rightSlot = 3))
        assertTrue(s.doubles)
        assertEquals(-1, s.ownRightOnField)
        assertNull(s.ownRight)
        assertNotNull(s.enemyRight, "the opponent's side is read all the same")
    }

    @Test
    fun `Emerald's Space Center fight hides your partner, and nothing else does`() {
        // Battle.EnemyTrainersToHideAlly (Battle.lua:68-75): Tabitha (514) and Maxie (734), on Emerald only.
        assertTrue(settle(fight(opponent = 734)).allyHidden)
        assertTrue(settle(fight(opponent = 514)).allyHidden)
        assertFalse(settle(fight(opponent = 733)).allyHidden)
        assertFalse(settle(fight(opponent = 734, battlers = 2)).allyHidden, "a single battle has no partner")
        val fr = GameMap.FIRERED_U_V10
        assertFalse(settle(fight(fr, opponent = 734), fr).allyHidden, "FireRed's trainer 734 is someone else")
    }
}
