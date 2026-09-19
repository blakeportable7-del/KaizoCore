package com.ironmonone.tracker

import com.ironmonone.tracker.RandomizedFlags.Mon
import com.ironmonone.tracker.RandomizedFlags.Move
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** PokemonData / MoveData checkIfDataIsRandomized, on the vanilla values read from Blake's dumps. */
class RandomizedFlagsTest {
    private val bulbasaur = Mon(listOf(45, 49, 49, 45, 65, 65), 12 to 3, 65 to 0, 70, 64, listOf(33 to 1, 45 to 4, 73 to 7, 22 to 10))
    private val lapras = Mon(listOf(130, 85, 80, 60, 85, 95), 11 to 15, 11 to 75, 70, 219, listOf(55 to 1, 45 to 1, 47 to 1))
    private val shuckle = Mon(listOf(20, 10, 230, 5, 10, 230), 6 to 5, 5 to 0, 70, 80, listOf(132 to 1, 110 to 1, 35 to 9))
    private val airCutter = Move(55, 2, 95, 25)
    private val clamp = Move(35, 11, 75, 10)

    @Test
    fun `a vanilla game reads vanilla on every check`() {
        val f = RandomizedFlags.detect(bulbasaur, lapras, shuckle, airCutter, clamp)
        assertFalse(f.gameData, "$f"); assertFalse(f.moves, "$f")
    }

    @Test
    fun `each change is caught on its own`() {
        fun d(b: Mon = bulbasaur, l: Mon = lapras, s: Mon = shuckle, a: Move = airCutter, c: Move = clamp) =
            RandomizedFlags.detect(b, l, s, a, c)
        assertTrue(d(b = bulbasaur.copy(types = 10 to 10)).types)
        assertTrue(d(l = lapras.copy(abilities = 11 to 11)).abilities)
        assertTrue(d(s = shuckle.copy(stats = listOf(20, 10, 230, 5, 10, 231))).stats)
        assertTrue(d(b = bulbasaur.copy(learnset = listOf(33 to 1, 45 to 4, 74 to 7))).moveLearnSet)
        assertTrue(d(b = bulbasaur.copy(friendship = 0)).friendshipBase)
        assertTrue(d(l = lapras.copy(expYield = 100)).expYield)
        assertTrue(d(c = clamp.copy(type = 0)).moveType)
        assertTrue(d(a = airCutter.copy(power = 60)).movePower)
        assertTrue(d(c = clamp.copy(acc = 100)).moveAccuracy)
        assertTrue(d(a = airCutter.copy(pp = 30)).movePP)
        // Every change reports as a randomized game.
        assertTrue(d(b = bulbasaur.copy(expYield = 1)).gameData)
    }

    @Test
    fun `a second ability of 0 is not a change, and a short learnset is not checked`() {
        assertFalse(RandomizedFlags.detect(bulbasaur.copy(abilities = 65 to 0), lapras, shuckle, airCutter, clamp).abilities)
        assertFalse(RandomizedFlags.detect(bulbasaur.copy(learnset = listOf(1 to 1)), lapras, shuckle, airCutter, clamp).moveLearnSet)
    }
}
