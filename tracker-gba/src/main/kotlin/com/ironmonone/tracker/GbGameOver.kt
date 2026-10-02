package com.ironmonone.tracker

/**
 * When a Game Boy run is lost, by the Game Boy references' rule, which has no
 * "Game is considered over when" option: Gen 1 reference
 * Battle.updateBattleStatus (Battle.lua:589-624), Gen 2 reference
 * Battle.updateBattleStatusGen2 (Battle.lua:132-162), the same lines in both.
 *
 * - Which Pokemon: party slot 1 (`Tracker.getPokemon(1, true)`,
 *   getPokemonGen2 in Gen 2), whatever is in battle.
 * - When: only while the battle byte reads 0 (`lastBattleStatus == 0`,
 *   gBattleTypeFlags: wIsInBattle in Gen 1, wBattleMode in Gen 2), so never
 *   during a battle. Gen 1's 0xFF, the lost-battle mark pokered sets before
 *   the blackout (home/overworld.asm, LOST_BATTLE), is not 0 either.
 * - Once: DataHelper.Gameover is set when it fires and cleared when the
 *   next battle begins. That half is the app's GameOverLatch, which re-arms
 *   on a battle beginning for the Gen 1 to 3 trackers.
 *
 * Ours used the Gen 3 conditions and read them on every poll, so a lead
 * fainting mid-battle ended the run before the battle was over.
 */
internal object GbGameOver {
    /** pokered's wIsInBattle after a lost battle, until the blackout clears it. */
    const val LOST_BATTLE = 0xFF
    /**
     * [condition] is the player's "Game is considered over when" choice. The Game Boy references
     * have no such option and always use the lead; Blake, 2026-09-29, gives the player full
     * control of the mode, so a Standard or Ultimate run can end on the entire party as its rules
     * say. The timing stays the references': only once the battle byte reads 0.
     */
    fun lost(battleByte: Int, party: List<TrackedMon>, condition: LossCondition = LossCondition.LEAD): Boolean {
        // Gen 1's lost-battle mark (wIsInBattle 0xFF, pokered's LOST_BATTLE). The blackout that follows sets the byte
        // to 0 and heals the party in one routine (ResetStatusAndHalveMoneyOnBlackout, then HealParty), so no poll ever
        // read "battle over, lead at 0 HP" and a blackout never ended the run (rc33 audit P1). The mark holds through
        // the "blacked out" text and the fade. Only with every Pokemon at 0 HP: a byte the game has not set yet at
        // power-on is never a loss. Gen 2's battle byte never reads 0xFF.
        if (battleByte == LOST_BATTLE) return party.isNotEmpty() && party.all { it.mon.curHp == 0 }
        if (battleByte != 0) return false
        return condition.lostMons(party.map { LossMon(it.mon.level, it.mon.curHp) })
    }
}
