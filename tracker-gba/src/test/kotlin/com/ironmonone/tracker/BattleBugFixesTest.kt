package com.ironmonone.tracker

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Three field bugs from 2026-09-01, each pinned here:
 *
 *  - "[SLP]" beside a freshly met opponent (status read from gBattleMons at a
 *    vanilla offset inside a struct Nat. Dex grew; now read from the party).
 *  - The route encounter list appearing only sometimes (map id believed on
 *    a single mid-transition poll; now debounced).
 *  - B-mashing walking the player after a battle (flee gated on a stale poll;
 *    now gated on the live action-menu state).
 */
class BattleBugFixesTest {

    private val map = GameMap.EMERALD_U

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

    /** A vanilla-layout party mon; pid%24==0 so the growth block is first. */
    private fun mon(species: Int, level: Int, curHp: Int, maxHp: Int, status: Long): ByteArray {
        val plain = ByteArray(48)
        plain[0] = species.toByte(); plain[1] = (species shr 8).toByte()
        val m = ByteArray(100)
        val pid = 0x18L                     // nonzero, and 24 % 24 == 0
        m.putU32(0, pid); m.putU32(4, pid)  // otId == pid -> key is zero
        for (w in 0 until 12) m.putU32(0x20 + w * 4, plain.u32(w * 4))
        m.putU32(0x50, status)
        m[0x54] = level.toByte()
        m[0x56] = curHp.toByte(); m[0x57] = (curHp shr 8).toByte()
        m[0x58] = maxHp.toByte(); m[0x59] = (maxHp shr 8).toByte()
        return m
    }

    /** Mid-battle memory: battler 1 is species 25 Lv8 with 20/20 HP. */
    private fun fighting(wild: Boolean, garbageAt4C: Long = 0): Fake = Fake().apply {
        put(map.battlersCount, 2, 1)
        put(map.battleMons, 25, 2)                   // battler 0 species
        val b1 = map.battleMons + map.battleMonSize
        put(b1, 25, 2)
        put(b1 + 0x28, 20, 2); put(b1 + 0x2A, 8, 1); put(b1 + 0x2C, 20, 2)
        put(b1 + 0x4C, garbageAt4C, 4)
        put(map.battleOutcome, 0, 1)
        put(map.battleMainFunc, map.handleTurnAction, 4)
        put(map.battleCommunication, map.actionMenuState.toLong(), 1)   // the action menu is up
        put(map.battleTypeFlags, if (wild) 0x0 else 0x8, 4)
    }

    private fun settle(m: Fake): Pair<GbaTracker, TrackerState> {
        val t = GbaTracker(m, map); t.read(); return t to t.read()
    }

    // ------------------------------------------------------------ [SLP]

    @Test
    fun `opponent status comes from its party slot`() {
        val m = fighting(wild = true)
        m.put(map.enemyParty, mon(25, 8, 20, 20, status = 0x02))   // asleep, 2 turns
        val (_, s) = settle(m)
        val e = assertNotNull(s.enemy)
        assertEquals("SLP", e.statusCondition)
    }

    @Test
    fun `garbage in gBattleMons cannot fake a sleep`() {
        // The old read: 0x07 at +0x4C is "asleep" under the vanilla layout.
        val m = fighting(wild = true, garbageAt4C = 0x07)
        m.put(map.enemyParty, mon(25, 8, 20, 20, status = 0))       // healthy
        val (_, s) = settle(m)
        assertEquals("", assertNotNull(s.enemy).statusCondition,
            "a status was rendered from gBattleMons bytes, not from the party")
    }

    @Test
    fun `no matching party slot means no status, never a guess`() {
        val m = fighting(wild = true, garbageAt4C = 0x07)
        m.put(map.enemyParty, mon(25, 30, 20, 20, status = 0x02))   // wrong level
        val (_, s) = settle(m)
        assertEquals("", assertNotNull(s.enemy).statusCondition)
    }

    // ------------------------------------------------------------ route

    private fun mapId(m: Fake, id: Int) = m.put(map.mapHeader + 0x12, id.toLong(), 2)

    @Test
    fun `a map id is adopted only after two consecutive polls`() {
        val m = Fake(); mapId(m, 5)
        val t = GbaTracker(m, map)
        assertEquals(null, t.read().mapId, "adopted on the first sighting")
        assertEquals(5, t.read().mapId)
        mapId(m, 9)                                   // one-poll blip
        assertEquals(5, t.read().mapId, "a single blip was believed")
        mapId(m, 5)
        assertEquals(5, t.read().mapId)
        mapId(m, 9); t.read()                         // real change: twice
        assertEquals(9, t.read().mapId)
        assertEquals(2, t.routeLogSnapshot().size, "one entry per adoption")
    }

    // ------------------------------------------------------------ flee

    @Test
    fun `flee gate is true only on the wild action menu, read live`() {
        val m = fighting(wild = true)
        val (t, _) = settle(m)
        assertTrue(t.isChoosingActionInWild())
        // The menu closes: the very next call is false, no poll required.
        m.put(map.battleMainFunc, map.returnToOverworld, 4)
        assertFalse(t.isChoosingActionInWild())
        m.put(map.battleMainFunc, map.handleTurnAction, 4)
        m.put(map.battleOutcome, 4, 1)                // fled
        assertFalse(t.isChoosingActionInWild())
    }

    /**
     * 2026-10-01 (Blake: "it is making the game locked into the bag"): gBattleMainFunc is the same inside the Bag, the
     * party screen and the move menu. Only the action menu itself may run, where the battle waits on the action.
     */
    @Test
    fun `flee gate is false in the Bag, the party screen and the move menu`() {
        val m = fighting(wild = true)
        val (t, _) = settle(m)
        m.put(map.battleCommunication, map.actionMenuState + 1L, 1)    // STATE_WAIT_ACTION_CASE_CHOSEN
        assertFalse(t.isChoosingActionInWild(), "the Bag is open")
        m.put(map.battleCommunication, map.actionMenuState - 1L, 1)    // STATE_BEFORE_ACTION_CHOSEN
        assertFalse(t.isChoosingActionInWild(), "the menu is not up yet")
        m.put(map.battleCommunication, map.actionMenuState.toLong(), 1)
        assertTrue(t.isChoosingActionInWild(), "back on the action menu")
    }

    @Test
    fun `flee gate is never true in a trainer battle`() {
        val (t, _) = settle(fighting(wild = false))
        assertFalse(t.isChoosingActionInWild())
    }

    // ------------------------------------------------------------ the Pokemon on the field (rc33 audit P1)

    /**
     * Slot 1 fainted at the start, or a switch: gBattlerPartyIndexes[0] names another slot, and the own card, its
     * stat stages, heals, Calc Atk and the matchups follow it (TrackerState.onField), as the reference's
     * Battle.getViewedPokemon does.
     */
    @Test
    fun `in a battle the Pokemon on the field is the one gBattlerPartyIndexes names, not slot 1`() {
        val m = fighting(wild = true)
        m.put(map.partyCount, 2, 1)
        m.put(map.party, mon(25, 8, 0, 20, status = 0))                          // slot 1: fainted
        m.put(map.party + map.monLayout.size, mon(4, 12, 30, 33, status = 0))   // slot 2: on the field
        m.put(map.battlerPartyIndexes, 1, 2)
        m.put(map.battleMons + 0x18, byteArrayOf(6, 8, 6, 6, 6, 6, 6, 6))      // battler 0: ATK +2
        val (t, s) = settle(m)
        assertEquals(1, s.ownOnField)
        assertEquals(4, s.onField?.mon?.species)
        assertEquals(8, s.party[1].statStages["ATK"], "the stages are the Pokemon's on the field")
        assertTrue(s.party[0].statStages.isEmpty(), "slot 1 is not on the field")
        // Out of the battle, the lead again.
        m.put(map.battleOutcome, 1, 1)
        m.put(map.battleMainFunc, map.returnToOverworld, 4)
        var after = t.read()
        repeat(3) { if (after.inBattle) after = t.read() }
        assertTrue(!after.inBattle)
        assertEquals(0, after.ownOnField)
        assertEquals(25, after.onField?.mon?.species)
    }
}
