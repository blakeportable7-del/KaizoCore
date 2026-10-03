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

/**
 * When a Game Boy run is won (rc32 audit P2 #135). Neither Game Boy reference has a working win check (the Gen 2
 * one's IsInHallOfFame is Emerald's table, Ironmon-gen-2-tracker RouteData.lua:513-517), so a won run never latched
 * YOU WON and was later filed as ended by a new run. Read as the GBA tracker reads its own (GbaTracker.readGameOver:
 * a won battle against the final trainer), and given from that battle's end until the next battle begins, as the
 * GBA's battle outcome byte holds it, so the app's latch takes it once.
 *
 * - Gold, Silver and Crystal: "To win Ironmon you must defeat Red at the top of Mt. Silver" (rulesets/GSC). A
 *   trainer battle against class RED, 0x3F (pokecrystal constants/trainer_constants.asm:686, pokegold :637), that
 *   ends with wBattleResult's low two bits at WIN, 0 (constants/battle_constants.asm:263-265). The class is kept
 *   while the battle is on: CleanUpBattleRAM clears wOtherTrainerClass with wBattleMode (engine/battle/core.asm:8298-8302).
 * - Red, Blue and Yellow: the Champion, class RIVAL3, 0x2B (pokered and pokeyellow constants/trainer_constants.asm:60),
 *   beaten. wTrainerClass stays set after the battle, but a blackout zeroes wBattleResult as it heals the party
 *   (engine/events/black_out.asm:3-4), so the result is read on the first read after the battle and only counts with
 *   the Champion's last Pokemon at 0 HP in wEnemyMon and the player still in CHAMPIONS_ROOM, 0x78 (map_constants.asm:198),
 *   which a blackout leaves at once. The Hall of Fame is no test of its own: the main menu loads a won game's save
 *   with wCurMap on HALL_OF_FAME before Continue is chosen (engine/menus/main_menu.asm:11, :118).
 */
internal class GbWin private constructor(private val finalClass: Int, private val finalMap: Int?) {
    private var vsFinal = false
    private var won = false

    /**
     * One read. [battleByte] is wIsInBattle or wBattleMode, [trainerClass] wTrainerClass or wOtherTrainerClass and [result]
     * wBattleResult, -1 when unread. Gen 1 also gives [enemyHp], the HP in wEnemyMon, and [mapId], wCurMap. True while won.
     */
    fun read(battleByte: Int, trainerClass: Int, result: Int, enemyHp: Int? = null, mapId: Int? = null): Boolean {
        if (battleByte != 0) {
            // A battle is on, or Gen 1's lost-battle mark (which is never the final trainer's win).
            won = false
            vsFinal = battleByte == 2 && trainerClass == finalClass
            return false
        }
        if (vsFinal) {
            vsFinal = false
            won = result >= 0 && (result and 3) == 0 && (enemyHp == null || enemyHp == 0) && (finalMap == null || mapId == finalMap)
        }
        return won
    }

    companion object {
        const val RIVAL3 = 0x2B
        const val CHAMPIONS_ROOM = 0x78
        const val RED = 0x3F

        fun gen1() = GbWin(RIVAL3, CHAMPIONS_ROOM)
        fun gen2() = GbWin(RED, null)
    }
}
