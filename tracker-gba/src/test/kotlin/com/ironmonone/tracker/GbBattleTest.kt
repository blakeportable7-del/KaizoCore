package com.ironmonone.tracker

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The Game Boy battle pieces the references draw in battle: stat stages
 * (Battle.updateStatStages), the carousel's last-move line (processBattleTurn)
 * and its text (TrackerScreen.lua LAST_ATTACK).
 */
class GbBattleTest {

    @Test
    fun `stat stages come out with 6 neutral, and bytes outside 1 to 13 are no stages`() {
        val raw = byteArrayOf(9, 7, 6, 13, 1, 7)
        assertEquals(mapOf("ATK" to 8, "DEF" to 6, "SPE" to 5, "SPA" to 12, "ACC" to 0, "EVA" to 6), gbStatStages(raw, GEN1_STAGES))
        assertEquals(emptyMap(), gbStatStages(byteArrayOf(7, 7, 0, 7, 7, 7), GEN1_STAGES), "a 0 is not a battle's stage")
        assertEquals(emptyMap(), gbStatStages(byteArrayOf(7, 7, 7), GEN2_STAGES), "a short read")
        assertEquals(7, GEN2_STAGES.size, "Gen 2 has Sp. Atk and Sp. Def apart")
    }

    /** Battle.processBattleTurn (Gen 1 reference Battle.lua:695-731, Gen 2 :379-408), read by read. */
    @Test
    fun `the last move shows once the turn counter moves with no move byte, until the enemy moves again`() {
        val l = GbLastMove()
        l.read(inBattle = true, turn = 0, move = 55)       // last battle's byte: it is the last move, but the enemy "has attacked"
        assertEquals(0, l.shown)
        l.read(true, turn = 1, move = 33)                  // the enemy's move runs
        assertEquals(0, l.shown)
        l.read(true, turn = 0, move = 0)                   // Gen 1: the next Pokemon is sent out, both bytes cleared
        assertEquals(33, l.shown)
        l.read(true, turn = 0, move = 0)
        assertEquals(33, l.shown, "it stays until the enemy moves again")
        l.read(true, turn = 1, move = 52)
        assertEquals(0, l.shown)
        l.read(false, turn = 1, move = 52)                 // endCurrentBattle forgets it
        assertEquals(0, l.shown)
        l.read(true, turn = 3, move = 0)                   // a new battle: the counter starts at 0, and no move is known
        assertEquals(0, l.shown)
    }

    /** Drawing.drawTrainerTeamPokeballs over what the Game Boy references know: the opponent on the field. */
    @Test
    fun `a trainer battle's team row is the one ball the references know, grey once it faints`() {
        fun foe(hp: Int) = EnemyInfo(species = 25, speciesName = "PIKACHU", level = 5, curHp = hp, maxHp = 20,
            type1 = 13, type2 = 13, base = null, movesSeen = emptyList())
        assertEquals(listOf(true), gbEnemyTeam(trainerBattle = true, enemy = foe(12)))
        assertEquals(listOf(false), gbEnemyTeam(trainerBattle = true, enemy = foe(0)))
        assertEquals(emptyList(), gbEnemyTeam(trainerBattle = false, enemy = foe(12)), "a wild battle has no team")
        assertEquals(emptyList(), gbEnemyTeam(trainerBattle = true, enemy = null))
    }

    @Test
    fun `the line reads Last move when no damage was counted, and only damage turns the sword red`() {
        assertEquals("Last move: Tackle", LastAttack.text("Tackle", 0, teams = false))
        assertEquals("Tackle: 12 damage", LastAttack.text("Tackle", 12, teams = false))
        assertEquals("Total received: 12 damage", LastAttack.text("Tackle", 12, teams = true))
        assertFalse(LastAttack.lethal(0, 0), "no damage counted: the colour is left alone")
        assertTrue(LastAttack.lethal(12, 12))
        assertFalse(LastAttack.lethal(12, 13))
    }
}
