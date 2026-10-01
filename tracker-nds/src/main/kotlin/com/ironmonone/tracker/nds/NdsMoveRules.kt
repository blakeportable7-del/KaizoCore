package com.ironmonone.tracker.nds

import com.ironmonone.tracker.Gen3Types

/**
 * The DS tracker's rules for one move row on its main screen, from MoveUtils.lua
 * and MainScreen.lua (NDS-Ironmon-Tracker). Types are the names the move table
 * and the sidecar write ("FIRE"). The chart is Gen3Types': MoveData.EFFECTIVE_DATA
 * was compared with it cell for cell on 2026-09-29 (110 cells, no difference).
 */
object NdsMoveRules {
    /** PokemonData.PLATE_TO_TYPE (PokemonData.lua:72-89): Flame Plate (298) to Iron Plate (313). */
    val PLATE_TO_TYPE: Map<Int, String> = mapOf(
        298 to "FIRE", 299 to "WATER", 300 to "ELECTRIC", 301 to "GRASS", 302 to "ICE", 303 to "FIGHTING",
        304 to "POISON", 305 to "GROUND", 306 to "FLYING", 307 to "PSYCHIC", 308 to "BUG", 309 to "ROCK",
        310 to "GHOST", 311 to "DRAGON", 312 to "DARK", 313 to "STEEL",
    )

    /** MoveUtils.netEffectiveness's NO_EFFECT_PAIRINGS (MoveUtils.lua:18-25). */
    private val NO_EFFECT = mapOf(
        "NORMAL" to "GHOST", "FIGHTING" to "GHOST", "PSYCHIC" to "DARK",
        "GROUND" to "FLYING", "GHOST" to "NORMAL", "POISON" to "STEEL",
    )

    /** Graphics.TEXT.NO_POWER: a power the row prints as "---" (a text power such as WT is not one). */
    fun noPower(m: NdsMoveInfo): Boolean = m.power <= 0 && m.powerText.isEmpty()

    /**
     * MoveUtils.netEffectiveness (MoveUtils.lua:17-56) against a target of
     * [targetTypes], as MainScreen.setUpMoveEffectiveness calls it (lua:368-374).
     *
     * - A move with no power: 0 against the one type its type cannot touch
     *   (Normal and Fighting on Ghost, Psychic on Dark, Ground on Flying, Ghost
     *   on Normal, Poison on Steel), but only when it is not a status move or it
     *   is Poison (Toxic on Steel); anything else 1.
     * - Future Sight, and Doom Desire at 100 accuracy, 1 (the "or" binds after
     *   the "and", lua:37).
     * - Otherwise the chart over the target's types.
     *
     * Two things are copied as the reference does them. The setup passes
     * `opposingPokemon == program.SELECTED_PLAYERS.ENEMY`, a table compared with
     * a number, so the move is never the enemy's: Hidden Power takes the type
     * set with the arrows ([hiddenPowerType]) on either card. And Judgment takes
     * the type of the plate [targetHeldItem] holds, the Pokemon being hit
     * (lua:46, pkmnData is the target).
     */
    fun effectiveness(move: NdsMoveInfo, targetTypes: List<String>, targetHeldItem: Int, hiddenPowerType: String): Double {
        val types = targetTypes.map { it.trim().uppercase() }.filter { it.isNotEmpty() }.distinct()
        val type = move.type.trim().uppercase()
        if (noPower(move)) {
            if (!move.category.equals("STATUS", ignoreCase = true) || type == "POISON") {
                NO_EFFECT[type]?.let { immune -> if (immune in types) return 0.0 }
            }
            return 1.0
        }
        if (move.name == "Future Sight" || (move.name == "Doom Desire" && move.accuracy == 100)) return 1.0
        var moveType = type
        if (move.name == "Hidden Power") moveType = hiddenPowerType.uppercase()
        if (move.name == "Judgment") PLATE_TO_TYPE[targetHeldItem]?.let { moveType = it }
        val attack = Gen4Types.idOf(moveType) ?: return 1.0
        var e = 1.0
        for (t in types) Gen4Types.idOf(t)?.let { e *= Gen3Types.effect(attack, it) }
        return e
    }

    /**
     * MoveUtils.isSTAB (MoveUtils.lua:262-276) as readMovesIntoUI calls it, with the
     * move and its own Pokemon only (MainScreen.lua:504): a move with a power
     * (a text power such as WT counts) whose type is one of the Pokemon's.
     * Called with no Hidden Power type, so Hidden Power is never STAB; Judgment
     * takes the type of the plate its user holds. The caller draws it only in
     * battle.
     */
    fun isStab(move: NdsMoveInfo, userTypes: List<String>, userHeldItem: Int): Boolean {
        if (noPower(move) || move.name == "Hidden Power") return false
        var moveType = move.type.trim().uppercase()
        if (move.name == "Judgment") PLATE_TO_TYPE[userHeldItem]?.let { moveType = it }
        return userTypes.any { it.trim().uppercase() == moveType }
    }

    /**
     * MoveUtils.getStars (MoveUtils.lua:58-124) for an opponent's moves as
     * Tracker.getMoves holds them: four slots of (move id, level it was last seen
     * at), an empty slot being (0, 1) (Tracker.lua:487-501). A slot gets a star
     * when it was not seen at level 1 and the species has learned at least as
     * many moves since as the slot's age rank, 1 plus the slots seen at a lower
     * level (the empty ones included): a move it may have forgotten.
     * [moveLevels] is the species' level-up levels, [level] its level now.
     */
    fun stars(slots: List<Pair<Int, Int>>, level: Int, moveLevels: List<Int>): List<Boolean> {
        val four = (slots + List(4) { 0 to 1 }).take(4)
        return four.mapIndexed { i, (_, seenAt) ->
            val rank = 1 + four.indices.count { j -> j != i && seenAt > four[j].second }
            val since = moveLevels.count { it > seenAt && it <= level }
            seenAt != 1 && since >= rank
        }
    }

    /** One Pokemon as MoveUtils.calculateVariableDamage reads it: HP, PokemonData weight (kg), battle stat stages. */
    data class Side(val curHp: Int = 0, val maxHp: Int = 0, val weightKg: Double = 0.0, val statStages: Map<String, Int>? = null)

    /**
     * MainScreen.checkForVariableMoves (lua:420-441), which runs under "Calculate
     * variable damage" (CALCULATE_VARIABLE_DAMAGE, on) and calls
     * MoveUtils.calculateVariableDamage (MoveUtils.lua:151-208): the power a row
     * prints in place of the table's, or null to keep it. Only these moves, and
     * only when each one's requirement holds:
     *
     * - Flail, Reversal: your own Pokemon, by its HP fraction (lua:295-310).
     * - Eruption, Water Spout: your own, 150 x HP fraction, at least 1, rounded (lua:313-320).
     * - Trump Card: either side, by the PP the row shows (lua:322-336).
     * - Low Kick, Grass Knot: in battle with an opposing Pokemon, by its weight (lua:278-292).
     * - Heat Crash, Heavy Slam: in battle with one, by its weight over the user's (lua:372-387).
     * - Punishment: in battle with one, 60 + 20 per raised stage it has, 60 to 200 (lua:338-357).
     *
     * Frustration, Gyro Ball, Electro Ball, Wring Out and Crush Grip are not
     * among them (calculateWringCrushDamage exists and nothing calls it), so they
     * keep the table's text.
     */
    fun variablePower(name: String, pp: Int, user: Side, target: Side?, userIsEnemy: Boolean, inBattle: Boolean): String? {
        val opposed = inBattle && target != null
        return when (name) {
            "Flail", "Reversal" -> if (userIsEnemy || user.maxHp <= 0) null else {
                val percent = user.curHp.toDouble() / user.maxHp * 100
                when {
                    percent < 4.17 -> "200"
                    percent < 10.42 -> "150"
                    percent < 20.83 -> "100"
                    percent < 35.42 -> "80"
                    percent < 68.75 -> "40"
                    else -> "20"
                }
            }
            "Water Spout", "Eruption" -> if (userIsEnemy || user.maxHp <= 0) null else
                kotlin.math.floor(maxOf(150.0 * user.curHp / user.maxHp, 1.0) + 0.5).toInt().toString()
            "Trump Card" -> when (minOf(pp, 5)) { 0 -> "0"; 1 -> "200"; 2 -> "80"; 3 -> "60"; 4 -> "50"; else -> "40" }
            "Low Kick", "Grass Knot" -> if (!opposed) null else {
                val w = target!!.weightKg
                when {
                    w < 10.0 -> "20"
                    w < 25.0 -> "40"
                    w < 50.0 -> "60"
                    w < 100.0 -> "80"
                    w < 200.0 -> "100"
                    else -> "120"
                }
            }
            "Heat Crash", "Heavy Slam" -> if (!opposed) null else {
                val ratio = target!!.weightKg / user.weightKg
                when {
                    ratio <= 0.2 -> "120"
                    ratio <= 0.25 -> "100"
                    ratio <= 0.3334 -> "80"
                    ratio <= 0.50 -> "60"
                    else -> "40"
                }
            }
            "Punishment" -> if (!opposed) null else {
                val stages = target!!.statStages ?: return "60"
                val raised = stages.values.filter { it > 6 }.sumOf { it - 6 }
                (60 + 20 * raised).coerceIn(60, 200).toString()
            }
            else -> null
        }
    }

    /**
     * MainScreen.readMovesIntoUI's Return (lua:510-516), outside the setting: your
     * own Pokemon's friendship / 2.5, at least 1, rounded down, printed only when
     * it reaches 100; otherwise the table's text stays.
     */
    fun returnPower(name: String, friendship: Int, userIsEnemy: Boolean): String? {
        if (name != "Return" || userIsEnemy) return null
        val power = kotlin.math.floor(maxOf(friendship / 2.5, 1.0)).toInt()
        return if (power >= 100) power.toString() else null
    }

    /**
     * BattleHandlerBase._setUpDelay (BattleHandlerBase.lua:103-113): after each
     * new opponent the main screen shows no effectiveness for this many frames
     * (_logNewEnemy: disableMoveEffectiveness, then a frame counter turns it back
     * on). 150 on Gen 4; on Gen 5 240 for a battle's first opponent, 90 after.
     */
    fun effectivenessDelayFrames(generation: Int, firstOfBattle: Boolean): Int =
        if (generation == 5) (if (firstOfBattle) 240 else 90) else 150
}
