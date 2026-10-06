package com.ironmonone.tracker.nuzlocke

import com.ironmonone.tracker.Gen3Types

/*
 * Team types (2026-10-06, Nuzlify's "team type coverage and weaknesses"): for every attacking type, how many of the
 * team it hits hard, how many take little from it and how many take nothing, and which types the team's own damaging
 * moves hit hard. A boss with a type the team is weak to and nobody resists is the one to plan for.
 *
 * Plain data in, plain data out: a member is its types and its damaging moves' types, all Gen 3 type ids, which is
 * what the ledger's roster keeps for every console.
 */
object NuzlockeCoverage {

    data class Member(val name: String, val types: List<Int>, val moveTypes: List<Int> = emptyList())

    /** One attacking type against the team: who takes double or more, who takes half or less, who takes nothing. */
    data class Row(val type: Int, val weak: List<String>, val resist: List<String>, val immune: List<String>) {
        /** More of the team is hit hard than takes it well. */
        val danger: Boolean get() = weak.size >= 2 && weak.size > resist.size + immune.size
    }

    data class Report(
        val rows: List<Row>,
        /** Defending types none of the team's damaging moves hits for double or more. Empty when no moves are known. */
        val notCovered: List<Int>,
        val movesKnown: Boolean,
    )

    /** The types [system] has: no Dark or Steel on Red, Blue and Yellow; Fairy only where a team member or move has it. */
    fun typesOf(system: NuzlockeSystem, team: List<Member>): List<Int> {
        val fairy = team.any { 18 in it.types || 18 in it.moveTypes }
        return when (system) {
            NuzlockeSystem.GEN1 -> NuzlockeSystem.GEN1.types
            else -> Gen3Types.typesFor(fairy)
        }
    }

    private fun effect(attack: Int, defend: List<Int>, system: NuzlockeSystem, natDex: Boolean): Double {
        val gen1 = system == NuzlockeSystem.GEN1
        return defend.distinct().fold(1.0) { acc, t -> acc * Gen3Types.effect(attack, t, gen1, natDex) }
    }

    fun report(team: List<Member>, system: NuzlockeSystem): Report {
        val types = typesOf(system, team)
        val natDex = 18 in types
        val members = team.filter { it.types.isNotEmpty() }
        val rows = types.map { a ->
            val weak = ArrayList<String>(); val resist = ArrayList<String>(); val immune = ArrayList<String>()
            for (m in members) {
                val e = effect(a, m.types, system, natDex)
                when {
                    e == 0.0 -> immune += m.name
                    e > 1.0 -> weak += m.name
                    e < 1.0 -> resist += m.name
                }
            }
            Row(a, weak, resist, immune)
        }
        val moveTypes = members.flatMap { it.moveTypes }.distinct()
        val notCovered = if (moveTypes.isEmpty()) emptyList() else types.filter { d ->
            moveTypes.none { a -> effect(a, listOf(d), system, natDex) > 1.0 }
        }
        return Report(rows, notCovered, moveTypes.isNotEmpty())
    }
}
