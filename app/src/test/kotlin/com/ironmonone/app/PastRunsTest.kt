package com.ironmonone.app

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/** PastRun.lua and SeedLogger: the file round-trips, sorts and filters as the reference does, and the statistics count the same things. */
class PastRunsTest {
    private fun mon(name: String, bst: Int, t1: String, t2: String, ability: String, moves: List<String>) = PastRun.RunMon(1, name, 10, bst, t1, t2, ability, moves)

    @Test
    fun `round trip, sorting, badge filter, removal and statistics`() {
        val f = File.createTempFile("pastruns", ".tsv"); f.delete(); f.deleteOnExit()
        val store = PastRunStore(f)
        store.log(PastRun(1000, 60, mon("ZUBAT", 245, "Poison", "Flying", "Inner Focus", listOf("Bite", "Wing Attack")), mon("ONIX", 385, "Rock", "Ground", "Sturdy", listOf("Tackle")), "", 0, PastRun.NOWHERE))
        store.log(PastRun(2000, 120, mon("ABSOL", 465, "Dark", "Dark", "Pressure", listOf("Bite")), mon("GARDEVOIR", 518, "Psychic", "Psychic", "Trace", listOf("Psychic")), "", 3, PastRun.PAST_LAB))
        store.log(PastRun(3000, 30, mon("MEW", 600, "Psychic", "Psychic", "Synchronize", listOf("Psychic")), mon("MEW", 600, "Psychic", "Psychic", "Synchronize", listOf("Psychic")), "", 8, PastRun.WON))
        val again = PastRunStore(f)
        assertEquals(3, again.totalRuns()); assertEquals(2, again.totalRunsPastLab()); assertEquals(210L, again.totalSeconds())
        assertEquals(listOf("MEW", "ABSOL", "ZUBAT"), again.sorted("NEWEST", 0).map { it.fainted.name })
        assertEquals(listOf("ZUBAT", "ABSOL", "MEW"), again.sorted("OLDEST", 0).map { it.fainted.name })
        assertEquals(listOf("ABSOL", "MEW", "ZUBAT"), again.sorted("A_TO_Z", 0).map { it.fainted.name })
        assertEquals(listOf("MEW"), again.sorted("NEWEST", 4).map { it.fainted.name })
        assertEquals(listOf("Bite", "Wing Attack"), again.all()[0].fainted.moves)
        val stats = again.statistics().toMap()
        assertEquals(listOf("Past Lab" to 2, "1 Badge" to 2, "2 Badges" to 2, "3 Badges" to 2, "4 Badges" to 1, "5 Badges" to 1, "6 Badges" to 1, "7 Badges" to 1, "8 Badges" to 1, "Won" to 1), stats["Overall Progress"])
        assertEquals(listOf("< 300" to 1, "300 - 399" to 0, "400 - 499" to 1, "500+" to 1), stats["BST Ranges You Ran"])
        assertEquals(listOf("Dark" to 1, "Flying" to 1, "Poison" to 1, "Psychic" to 1), stats["Types You Ran"])
        assertEquals(listOf("Psychic" to 2, "Ground" to 1, "Rock" to 1), stats["Types You Lost to"])
        assertEquals(listOf("Bite" to 2, "Psychic" to 1, "Wing Attack" to 1), stats["Moves You Had"])
        again.removeNoBadgeRuns()
        assertEquals(2, again.totalRuns()); assertEquals(2, PastRunStore(f).totalRuns())
    }
}
