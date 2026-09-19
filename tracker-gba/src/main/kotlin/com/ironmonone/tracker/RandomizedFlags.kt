package com.ironmonone.tracker

/**
 * Which parts of the game's data a randomizer changed: PokemonData and
 * MoveData checkIfDataIsRandomized (Ironmon-Tracker v9.3.1).
 *
 * The reference samples three Pokemon - Bulbasaur, Lapras and Shuckle - and
 * two moves - Air Cutter and Clamp - against their vanilla values. The
 * off-by-default "Hide stats until summary shown" and the information rules
 * ("Reveal info if randomized", "Show data for vanilla game", Open Book) all
 * depend on the answer.
 *
 * Checked against Blake's vanilla FireRed and Emerald dumps: every flag reads
 * vanilla on both.
 */
data class RandomizedFlags(
    val types: Boolean,
    val abilities: Boolean,
    val stats: Boolean,
    val moveLearnSet: Boolean,
    val friendshipBase: Boolean,
    val expYield: Boolean,
    val moveType: Boolean,
    val movePower: Boolean,
    val moveAccuracy: Boolean,
    val movePP: Boolean,
) {
    /** PokemonData.isGameDataRandomized. */
    val gameData: Boolean get() = types || abilities || stats || moveLearnSet || friendshipBase || expYield

    /** MoveData.isMoveDataRandomized (the move category split is not part of a Gen 3 ROM here). */
    val moves: Boolean get() = moveType || movePower || moveAccuracy || movePP

    /** A sampled Pokemon: stats in the ROM's order (HP, Atk, Def, Spe, SpA, SpD); learnset as (move, level). */
    data class Mon(
        val stats: List<Int>, val types: Pair<Int, Int>, val abilities: Pair<Int, Int>,
        val friendship: Int, val expYield: Int, val learnset: List<Pair<Int, Int>>,
    )
    data class Move(val power: Int, val type: Int, val acc: Int, val pp: Int)

    companion object {
        private const val OVERGROW = 65; private const val WATER_ABSORB = 11
        private const val SHELL_ARMOR = 75; private const val STURDY = 5

        fun detect(bulbasaur: Mon, lapras: Mon, shuckle: Mon, airCutter: Move, clamp: Move): RandomizedFlags {
            fun learnChanged(m: Mon, first3: List<Pair<Int, Int>>) = m.learnset.size >= 3 && m.learnset.take(3) != first3
            return RandomizedFlags(
                types = bulbasaur.types != (12 to 3) || lapras.types != (11 to 15) || shuckle.types != (6 to 5),
                abilities = bulbasaur.abilities.first != OVERGROW ||
                    (bulbasaur.abilities.second != OVERGROW && bulbasaur.abilities.second != 0) ||
                    lapras.abilities != (WATER_ABSORB to SHELL_ARMOR) ||
                    shuckle.abilities.first != STURDY ||
                    (shuckle.abilities.second != STURDY && shuckle.abilities.second != 0),
                stats = bulbasaur.stats != listOf(45, 49, 49, 45, 65, 65) ||
                    lapras.stats != listOf(130, 85, 80, 60, 85, 95) ||
                    shuckle.stats != listOf(20, 10, 230, 5, 10, 230),
                moveLearnSet = learnChanged(bulbasaur, listOf(33 to 1, 45 to 4, 73 to 7)) ||
                    learnChanged(lapras, listOf(55 to 1, 45 to 1, 47 to 1)) ||
                    learnChanged(shuckle, listOf(132 to 1, 110 to 1, 35 to 9)),
                friendshipBase = bulbasaur.friendship != 70 || lapras.friendship != 70 || shuckle.friendship != 70,
                expYield = bulbasaur.expYield != 64 || lapras.expYield != 219 || shuckle.expYield != 80,
                moveType = airCutter.type != 2 || clamp.type != 11,
                movePower = airCutter.power != 55 || clamp.power != 35,
                moveAccuracy = airCutter.acc != 95 || clamp.acc != 75,
                movePP = airCutter.pp != 25 || clamp.pp != 10,
            )
        }
    }
}
