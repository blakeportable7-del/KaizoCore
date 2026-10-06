package com.ironmonone.tracker.nuzlocke

import com.ironmonone.tracker.Gen3Types

/*
 * One boss Pokemon's moves against one of yours (2026-10-06): for each move that does damage, how much of your
 * Pokemon's HP it takes and the chance your Pokemon lives through one hit (NuzlockeOdds). Only ever fed a team that
 * NuzlockeScout showed, so everything it uses is the game's own public data.
 *
 * The boss's stats come from its species' base stats, its level and the IVs its trainer gives it, with a neutral
 * nature and no stat changes, held items or abilities: the screen says so.
 */
object NuzlockeMatchup {

    /** Your Pokemon: its name, Gen 3 types, stats (max HP, Atk, Def, Spe, Sp. Atk, Sp. Def) and HP now. */
    data class Defender(val name: String, val types: List<Int>, val stats: List<Int>, val hp: Int)

    data class Line(val move: String, val text: String, val survive: Double?)

    /** Gen 3 decides a move's kind by its type: Normal to Steel (0 to 8) are physical. */
    fun physical(type: Int): Boolean = type in 0..8

    fun lines(attacker: ScoutMon, you: Defender, generation: Int = 3): List<Line> {
        if (you.stats.size < 6 || attacker.base.size < 6) return emptyList()
        val natDex = 18 in you.types || 18 in attacker.types
        val gen1 = generation == 1
        fun stat(i: Int) = NuzlockeOdds.stat(attacker.base[i], attacker.ivs, 0, attacker.level)
        return attacker.moves.mapNotNull { m ->
            val type = m.type ?: return@mapNotNull null
            val fixed = NuzlockeOdds.fixedDamage(m.id, attacker.level)
            if (m.power <= 0 && fixed == null) return@mapNotNull null
            if (m.power == 1 && fixed == null) return@mapNotNull Line(m.name, "Its power changes in battle, so it is not worked out.", null)
            val eff = you.types.distinct().fold(1.0) { acc, t -> acc * Gen3Types.effect(type, t, gen1, natDex) }
            val phys = physical(type)
            val hit = NuzlockeOdds.Hit(
                generation = generation, level = attacker.level, power = m.power,
                attack = if (phys) stat(1) else stat(4), defense = if (phys) you.stats[2] else you.stats[5],
                stab = type in attacker.types, effectiveness = eff, fixed = fixed,
            )
            val odds = NuzlockeOdds.odds(hit, you.hp, you.stats[0], NuzlockeOdds.critChance(generation, attacker.base[3]))
            Line(m.name, NuzlockeOdds.words(odds), odds.survive)
        }
    }

    /** Who moves first at the same priority, by Speed (its own worked out as [lines] does): a line in words, or null when unknown. */
    fun speed(attacker: ScoutMon, you: Defender): String? {
        if (you.stats.size < 6 || attacker.base.size < 6) return null
        val its = NuzlockeOdds.stat(attacker.base[3], attacker.ivs, 0, attacker.level)
        val yours = you.stats[3]
        return when {
            yours > its -> "${you.name} moves first (Speed $yours against about $its)."
            yours < its -> "${attacker.speciesName} moves first (Speed about $its against $yours)."
            else -> "Both have Speed $its: either may move first."
        }
    }

    /** The worst of [lines]: the move your Pokemon is least likely to live through. */
    fun worst(lines: List<Line>): Line? = lines.filter { it.survive != null }.minByOrNull { it.survive!! }
}
