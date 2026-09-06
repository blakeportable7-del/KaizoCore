package com.ironmonone.tracker

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Leaving the battle.
 *
 * The enemy card stayed on screen for the rest of the session after a trainer
 * battle, showing an impossible species with impossible HP. The cause was
 * deciding "in a battle" from gBattlersCount alone, which Gen 3 does NOT clear
 * when a battle ends - the reference tracker reads three signals for exactly
 * this reason (Battle.lua:143).
 */
class BattleStatusTest {

    private val map = GameMap.EMERALD_U

    /** A writable slice of GBA memory, so a battle can be staged and ended. */
    private class Fake : MemoryReader {
        val bytes = HashMap<Long, Byte>()
        fun put(addr: Long, value: Long, len: Int) {
            for (i in 0 until len) bytes[addr + i] = ((value shr (8 * i)) and 0xFF).toByte()
        }
        override fun read(address: Long, length: Int) =
            ByteArray(length) { bytes[address + it] ?: 0 }
    }

    /** Memory as it looks mid-battle against a real Pokemon. */
    private fun fighting(): Fake = Fake().apply {
        put(map.battlersCount, 2, 1)
        put(map.battleMons, 25, 2)              // player's active mon: a real species
        put(map.battleOutcome, 0, 1)            // 0 = battle is live
        put(map.battleMainFunc, map.handleTurnAction, 4)
        put(map.battleTypeFlags, 0x8, 4)        // bit 3 = trainer battle
    }

    private fun inBattle(m: Fake): Boolean = GbaTracker(m, map).read().inBattle

    /** Memory mid-battle against a WILD Pokemon: trainer bit clear. */
    private fun fightingWild(): Fake = fighting().apply {
        put(map.battleTypeFlags, 0x0, 4)
    }

    /** Drives two polls: the first enters the battle, the second may arm it. */
    private fun settle(m: Fake): TrackerState {
        val t = GbaTracker(m, map)
        t.read()
        return t.read()
    }

    @Test
    fun `a wild encounter reads as wild, not as a trainer`() {
        val s = settle(fightingWild())
        assertTrue(s.inBattle, "a wild encounter did not register as a battle")
        assertTrue(s.isWildBattle, "a wild encounter was labelled a trainer battle")
        assertTrue(s.enemyTeam.isEmpty(), "a wild Pokemon must not get a pokeball row")
    }

    @Test
    fun `a trainer battle reads as a trainer`() {
        val s = settle(fighting())
        assertTrue(s.inBattle)
        assertFalse(s.isWildBattle, "a trainer battle was labelled wild")
    }

    /**
     * The data-ready gate is PER KIND, and each kind has its own intro symbol:
     * wild arms on BattleIntroDrawPartySummaryScreens, trainer on
     * BattleIntroOpponentSendsOutMonAnimation (reference Battle.lua:155-163).
     *
     * Every existing test here staged HandleTurnActionSelectionState, which is
     * in BOTH lists - so a wrong intro address for either kind would have gone
     * unnoticed, and the symptom is the panel never showing that kind of
     * opponent.
     */
    @Test
    fun `a wild encounter arms on its own intro symbol`() {
        val m = fightingWild()
        m.put(map.battleMainFunc, map.introDrawPartySummary, 4)
        assertTrue(settle(m).inBattle, "wild never became data-ready on its intro")
    }

    @Test
    fun `a trainer battle arms on its own intro symbol`() {
        val m = fighting()
        m.put(map.battleMainFunc, map.introOpponentSendsOut, 4)
        assertTrue(settle(m).inBattle, "trainer never became data-ready on its intro")
    }

    @Test
    fun `the intro symbols are not interchangeable`() {
        // Sitting on the OTHER kind's intro must not arm the panel: that is
        // what makes the two symbols worth having separately.
        val wildOnTrainerIntro = fightingWild()
        wildOnTrainerIntro.put(map.battleMainFunc, map.introOpponentSendsOut, 4)
        assertFalse(settle(wildOnTrainerIntro).inBattle)

        val trainerOnWildIntro = fighting()
        trainerOnWildIntro.put(map.battleMainFunc, map.introDrawPartySummary, 4)
        assertFalse(settle(trainerOnWildIntro).inBattle)
    }

    @Test
    fun `a live battle reads as a battle`() {
        val m = fighting()
        val t = GbaTracker(m, map)
        t.read()                       // enter
        assertTrue(t.read().inBattle)  // data ready on the next poll
    }

    @Test
    fun `the battle ends when the outcome is set, even though battlersCount does not clear`() {
        val m = fighting()
        val t = GbaTracker(m, map)
        t.read(); assertTrue(t.read().inBattle)

        // The battle is won. This is EXACTLY the reported state: the game has
        // left the battle but gBattlersCount still says two battlers.
        m.put(map.battleOutcome, 1, 1)
        m.put(map.battleMainFunc, map.returnToOverworld, 4)
        assertTrue(m.read(map.battlersCount, 1)[0].toInt() > 0, "precondition: count is stale")

        val after = t.read()
        assertFalse(after.inBattle, "battlersCount is stale; the outcome is the exit signal")
        assertTrue(after.enemy == null, "no enemy card once the battle is over")
    }

    @Test
    fun `an impossible species is not a battle`() {
        // The reported card read species #37343 with HP 3850/0. The reference
        // calls a battle struct holding a species like that a "fake battle".
        val m = fighting()
        m.put(map.battleMons, 37343, 2)
        val t = GbaTracker(m, map)
        t.read()
        assertFalse(t.read().inBattle)
    }

    @Test
    fun `the OLD rule would have failed this`() {
        // Isolation: prove the test above is actually testing the fix. Under
        // "inBattle = battlersCount > 0" the ended battle still reads as live,
        // which is the bug. If this ever stops being true, the test above has
        // stopped being a test.
        val m = fighting()
        m.put(map.battleOutcome, 1, 1)
        m.put(map.battleMainFunc, map.returnToOverworld, 4)
        val oldRule = m.read(map.battlersCount, 1)[0].toInt() > 0
        assertTrue(oldRule, "the old rule says in-battle here")
        assertFalse(inBattle(m), "the new rule says the battle is over")
    }
}
