package com.ironmonone.tracker

/**
 * Battle.lua's last-attack bookkeeping (lines 320-360), for the carousel's
 * "Wing Attack: 23 damage".
 *
 * gTakenDmg is the damage your Pokemon has taken. Each poll, any change in it
 * while the ENEMY is the attacker (an odd battler) is added to the damage its
 * current move has done; a change while you are attacking only moves the
 * baseline. A new battle turn resets the "enemy has attacked" flag, which is
 * what lets the carousel show the number: it stays hidden while the enemy's
 * attack is still playing out, so it never spoils the move or the damage.
 */
class DamageWatch {
    var lastEnemyMoveId = 0; private set
    var damageReceived = 0; private set
    var enemyHasAttacked = false; private set
    private var turnCount = -1
    private var prevDamageTotal = 0

    /** Battle.lua:98, a new battle. */
    fun reset() {
        lastEnemyMoveId = 0; damageReceived = 0; enemyHasAttacked = false
        turnCount = -1; prevDamageTotal = 0
    }

    /**
     * One poll. [turn] is gBattleResults +0x13, [attacker] gBattlerAttacker,
     * [total] gTakenDmg read as a word, [enemyMove] gBattleResults +0x24.
     */
    fun tick(turn: Int, attacker: Int, total: Int, enemyMove: Int) {
        if (turn != turnCount) {
            turnCount = turn
            prevDamageTotal = total
            enemyHasAttacked = false
        }
        val delta = total - prevDamageTotal
        if (delta == 0) return
        if (attacker % 2 != 0) {
            if (enemyMove != 0) {
                // A new move resets the damage counted for the last one.
                if (!enemyHasAttacked) { damageReceived = 0; enemyHasAttacked = true }
                lastEnemyMoveId = enemyMove
                damageReceived += delta
                prevDamageTotal = total
            }
        } else {
            prevDamageTotal = total
        }
    }

    /** The carousel item's timing: between the enemy's attacks, once one has hit you. */
    val ready: Boolean get() = !enemyHasAttacked && lastEnemyMoveId != 0
}
