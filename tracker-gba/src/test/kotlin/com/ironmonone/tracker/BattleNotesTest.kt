package com.ironmonone.tracker

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Battle.lua's last-attack bookkeeping and Utils.calculateMoveStars. */
class BattleNotesTest {
    private val ENEMY = 1; private val YOU = 0

    @Test
    fun `the enemy's hit is counted, and shown only once its turn is over`() {
        val w = DamageWatch()
        w.tick(turn = 1, attacker = YOU, total = 0, enemyMove = 0)
        w.tick(turn = 1, attacker = ENEMY, total = 23, enemyMove = 17)   // Wing Attack lands
        assertEquals(23, w.damageReceived); assertEquals(17, w.lastEnemyMoveId)
        assertFalse(w.ready, "never shown while the attack is playing out")
        w.tick(turn = 2, attacker = YOU, total = 23, enemyMove = 17)     // next turn
        assertTrue(w.ready); assertEquals(23, w.damageReceived)
    }

    @Test
    fun `your own attacks move the baseline but never count as damage taken`() {
        val w = DamageWatch()
        w.tick(1, YOU, 0, 0)
        w.tick(1, YOU, 10, 0)            // e.g. recoil on your side
        w.tick(1, ENEMY, 25, 33)         // only the 15 the enemy dealt counts
        assertEquals(15, w.damageReceived)
    }

    @Test
    fun `a multi-hit move adds up, and the next move starts from zero`() {
        val w = DamageWatch()
        w.tick(1, ENEMY, 0, 0)
        w.tick(1, ENEMY, 8, 42)          // Pin Missile, hit 1
        w.tick(1, ENEMY, 16, 42)         // hit 2
        assertEquals(16, w.damageReceived)
        w.tick(2, ENEMY, 16, 42)
        w.tick(2, ENEMY, 26, 17)         // a new attack next turn
        assertEquals(10, w.damageReceived); assertEquals(17, w.lastEnemyMoveId)
    }

    @Test
    fun `reset clears it for a new battle`() {
        val w = DamageWatch()
        w.tick(1, ENEMY, 0, 0); w.tick(1, ENEMY, 30, 17); w.tick(2, YOU, 30, 17)
        w.reset()
        assertFalse(w.ready); assertEquals(0, w.damageReceived)
    }

    @Test
    fun `a move seen long enough ago that newer moves may have replaced it is starred`() {
        // Seen at Lv.10; the species learns moves at 14, 18 and 22; now Lv.25.
        val tracked = listOf(52 to 10)
        assertEquals(setOf(52), MoveStars.of(tracked, 25, listOf(1, 14, 18, 22)))
        // Seen at Lv.20: only one move (22) since, and it ranks 1 -> still starred.
        assertEquals(setOf(52), MoveStars.of(listOf(52 to 20), 25, listOf(1, 14, 18, 22)))
        // Seen at Lv.23: nothing learned since -> no star.
        assertEquals(emptySet(), MoveStars.of(listOf(52 to 23), 25, listOf(1, 14, 18, 22)))
    }

    @Test
    fun `the older of two moves needs fewer new moves to be starred than the newer`() {
        // A seen at Lv.10, B at Lv.20; learns at 22 only; now Lv.25.
        // A: 1 learned since, rank 1 -> starred. B: 1 learned since, rank 2 -> not.
        val tracked = listOf(85 to 20, 52 to 10)   // most recent first
        assertEquals(setOf(52), MoveStars.of(tracked, 25, listOf(1, 22)))
    }

    @Test
    fun `the age rank counts every tracked move, not only the four shown`() {
        // Utils.lua:568-579 ranks each of the four against all of Tracker.getMoves (rc32 audit P2 #137).
        // Four seen at Lv.20 and an older one at Lv.10; now Lv.30, learning at 25: one move since, rank 2. No star.
        assertEquals(emptySet(), MoveStars.of(listOf(101 to 20, 102 to 20, 103 to 20, 104 to 20, 33 to 10), 30, listOf(1, 10, 20, 25)))
        // The audit's case: A to D at Lv.25, E and F at Lv.10, now Lv.30, learning at 26 and 28. Two since, rank 3.
        assertEquals(emptySet(), MoveStars.of(listOf(1 to 25, 2 to 25, 3 to 25, 4 to 25, 5 to 10, 6 to 10), 30, listOf(26, 28)))
        // Only the four shown can be starred: a fifth, seen long ago, counts in the ranks and is never starred itself.
        assertEquals(emptySet(), MoveStars.of(listOf(101 to 28, 102 to 28, 103 to 28, 104 to 28, 33 to 2), 30, listOf(5, 10)))
    }

    @Test
    fun `a move seen at level 1 is never starred`() {
        assertEquals(emptySet(), MoveStars.of(listOf(33 to 1), 30, listOf(1, 5, 10, 15, 20)))
    }
}
