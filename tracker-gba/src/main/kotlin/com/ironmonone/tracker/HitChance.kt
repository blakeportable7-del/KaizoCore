package com.ironmonone.tracker

/**
 * A move's chance to hit and to land a critical hit, as Heart & Soul works them out (Blake, 2026-10-10: "add the
 * abilities and items to the accuracy math", then "oh, critical hits"). It follows the game's own code step by step,
 * in its order and with its integer arithmetic (pokehns-expansion src/battle_util.c): CanMoveSkipAccuracyCalc (10403),
 * GetTotalAccuracy (10473), DoesOHKOMoveMissTarget (10608), gAccuracyStageRatios (battle_script_commands.c 849),
 * and CalcCritChanceStage with the odds the build's B_CRIT_CHANCE picks (7978 and 8031; Gen 3's since patch 0047).
 *
 * Only what the player can know goes in: your own ability and item always; the opponent's only once the game has shown
 * them (null ability, or [Hold.UNKNOWN], leaves that side's part out, and [Result.unknowns] says so). Most of the
 * opponent abilities this file knows never show a pop-up in Heart & Soul (Compound Eyes, Hustle, Victory Star, Sand
 * Veil, Snow Cloak, Tangled Feet, Wonder Skin, Unaware, No Guard, Super Luck, Merciless, Shell and Battle Armor), so
 * for an opponent they count only after another pop-up names them (your Trace, Role Play, Skill Swap); Keen Eye,
 * Mind's Eye, Illuminate, Sturdy and Ripen show theirs. Its held items announce themselves never (BrightPowder, the
 * lenses) or only through Frisk, Knock Off, Trick or Thief.
 */
object HitChance {
    /** What a move brings to the checks, all from gMovesInfo ([Rom.move] reads them). */
    data class Move(
        val accuracy: Int, val physical: Boolean = false, val status: Boolean = false, val type: Int = 0,
        val ignoresEvasion: Boolean = false, val fiftyInSun: Boolean = false, val sureInRain: Boolean = false,
        val sureInHailSnow: Boolean = false, val sureOnSameType: Boolean = false, val minimizeDouble: Boolean = false,
        /** accIncreaseByTenOnSameType: x1.1 when the user is NOT of the move's type (Sheer Cold's Gen 7 rule, inverted as the game writes it). */
        val plusTenOffType: Boolean = false,
        /** EFFECT_OHKO: Guillotine, Horn Drill, Fissure, Sheer Cold. Level against level, stages ignored. */
        val ohko: Boolean = false,
        val critStage: Int = 0, val alwaysCrit: Boolean = false,
    )

    /** A held item's part: the hold effect (HOLD_EFFECT_*, [Rom.hold] maps the ROM's ids) and its strength. */
    enum class Hold { NONE, UNKNOWN, WIDE_LENS, ZOOM_LENS, EVASION_UP, SCOPE_LENS, LUCKY_PUNCH, LEEK }

    enum class Weather { NONE, SUN, RAIN, SAND, HAIL_SNOW, FOG }

    /** One battler: its stages (-6 to +6), ability (null: not known), held item, types, and the states the checks read. */
    data class Side(
        val accStage: Int = 0, val evaStage: Int = 0, val ability: String? = null,
        val hold: Hold = Hold.NONE, val holdParam: Int = 0, val types: Set<Int> = emptySet(),
        val level: Int = 0, val species: String? = null,
        val confused: Boolean = false, val minimized: Boolean = false, val foresight: Boolean = false,
        /** Lock-On or Mind Reader taken against the other side: its next move cannot miss. */
        val sureHit: Boolean = false,
        val usedMicleBerry: Boolean = false, val umbrella: Boolean = false, val ally: String? = null,
        /** Focus Energy or Dire Hit (+2), Dragon Cheer (+1), Laser Focus (sure crit next turn). */
        val focusEnergy: Boolean = false, val dragonCheer: Boolean = false, val laserFocus: Boolean = false,
        val poisoned: Boolean = false,
        /** Lucky Chant on this side: no critical hits against it. */
        val luckyChant: Boolean = false,
    )

    /** [chance] in percent (may pass 100: a sure hit then), or [sure]; [why] names each step that moved it. */
    data class Result(val chance: Int, val sure: Boolean, val why: List<String>, val unknowns: List<String>)

    /** One in [oneIn] (0: never, 1: always); [stage] the crit stage the game reached (-1 blocked, -2 always). */
    data class Crit(val oneIn: Int, val stage: Int, val why: List<String>, val unknowns: List<String>) {
        val percent: Double get() = if (oneIn == 0) 0.0 else 100.0 / oneIn
    }

    private val RATIO = listOf(33 to 100, 36 to 100, 43 to 100, 50 to 100, 60 to 100, 75 to 100, 1 to 1, 133 to 100, 166 to 100, 2 to 1, 233 to 100, 133 to 50, 3 to 1)
    /**
     * GetCriticalHitOdds (battle_util.c), by the build's own B_CRIT_CHANCE (GEN_1 is 0 there): Gen 7+ 1/24, 1/8, 1/2, 1/1;
     * Gen 6 1/16, 1/8, 1/2, 1/1; Gens 3 to 5 1/16, 1/8, 1/4, 1/3, 1/2. The Nat. Dex Kaizo build (patch 0047) is Gen 3's.
     */
    private val CRIT_ODDS = when {
        HnsLayout.B_CRIT_CHANCE >= 6 -> intArrayOf(24, 8, 2, 1, 1)
        HnsLayout.B_CRIT_CHANCE == 5 -> intArrayOf(16, 8, 2, 1, 1)
        else -> intArrayOf(16, 8, 4, 3, 2)
    }

    /** 110 as "1.1", 90 as "0.9", 105 as "1.05". */
    private fun times(percent: Int) = (percent / 100.0).toString().trimEnd('0').trimEnd('.')

    private fun named(a: String?, vararg names: String) = a != null && names.any { it.equals(a, ignoreCase = true) }

    private fun unknowns(atk: Side, def: Side) = buildList {
        if (atk.ability == null) add("attacker's ability")
        if (def.ability == null) add("target's ability")
        if (atk.hold == Hold.UNKNOWN) add("attacker's item")
        if (def.hold == Hold.UNKNOWN) add("target's item")
    }

    /**
     * The chance [atk]'s [move] hits [def]. [weatherOn] is false while Cloud Nine or Air Lock is out (HasWeatherEffect);
     * [defMovedThisTurn] is Zoom Lens's condition.
     */
    fun of(move: Move, atk: Side, def: Side, weather: Weather = Weather.NONE, weatherOn: Boolean = true,
           gravity: Boolean = false, defMovedThisTurn: Boolean = false): Result {
        val why = ArrayList<String>()
        val unknown = unknowns(atk, def)
        val w = if (weatherOn) weather else Weather.NONE

        if (move.ohko) return ohko(move, atk, def, unknown)

        // CanMoveSkipAccuracyCalc: the cases that never roll.
        val sure = when {
            atk.sureHit -> "Lock-On"
            named(atk.ability, "No Guard") || named(def.ability, "No Guard") -> "No Guard"
            move.minimizeDouble && def.minimized -> "Minimized target"
            move.accuracy == 0 -> "Never misses"
            move.sureInRain && w == Weather.RAIN && !def.umbrella -> "Rain"
            move.sureInHailSnow && w == Weather.HAIL_SNOW -> "Hail"
            move.sureOnSameType && move.type in atk.types -> "Same type"
            else -> null
        }
        if (sure != null) return Result(100, true, listOf(sure), unknown)

        // GetTotalAccuracy.
        var accStage = atk.accStage + 6
        var evaStage = def.evaStage + 6
        if (named(atk.ability, "Unaware", "Keen Eye", "Mind's Eye", "Illuminate")) { if (evaStage != 6) why += "${atk.ability} ignores evasion"; evaStage = 6 }
        if (move.ignoresEvasion) { if (evaStage != 6) why += "Ignores evasion"; evaStage = 6 }
        if (named(def.ability, "Unaware")) { if (accStage != 6) why += "Unaware ignores accuracy"; accStage = 6 }
        val buff = (if (def.foresight) accStage else accStage + 6 - evaStage).coerceIn(0, 12)
        if (def.foresight && evaStage != 6) why += "Foresight"
        var moveAcc = move.accuracy
        if (w == Weather.SUN && !def.umbrella && move.fiftyInSun) { moveAcc = 50; why += "Sun: 50" }
        if (named(def.ability, "Wonder Skin") && move.status && moveAcc > 50) { moveAcc = 50; why += "Wonder Skin: 50" }
        val (n, d) = RATIO[buff]
        var calc = n * moveAcc / d
        if (buff != 6) why += "Stages ${if (buff > 6) "+" else ""}${buff - 6}"

        when {
            named(atk.ability, "Compound Eyes") -> { calc = calc * 130 / 100; why += "Compound Eyes x1.3" }
            named(atk.ability, "Victory Star") -> { calc = calc * 110 / 100; why += "Victory Star x1.1" }
            named(atk.ability, "Hustle") && move.physical -> { calc = calc * 80 / 100; why += "Hustle x0.8" }
        }
        when {
            named(def.ability, "Sand Veil") && w == Weather.SAND -> { calc = calc * 80 / 100; why += "Sand Veil x0.8" }
            named(def.ability, "Snow Cloak") && w == Weather.HAIL_SNOW -> { calc = calc * 80 / 100; why += "Snow Cloak x0.8" }
            named(def.ability, "Tangled Feet") && def.confused -> { calc = calc * 50 / 100; why += "Tangled Feet x0.5" }
        }
        if (named(atk.ally, "Victory Star")) { calc = calc * 110 / 100; why += "Ally's Victory Star x1.1" }
        if (move.plusTenOffType && move.type !in atk.types) { calc = calc * 110 / 100; why += "Off-type x1.1" }
        when (atk.hold) {
            Hold.WIDE_LENS -> { calc = calc * (100 + atk.holdParam) / 100; why += "Wide Lens x${times(100 + atk.holdParam)}" }
            Hold.ZOOM_LENS -> if (defMovedThisTurn) { calc = calc * (100 + atk.holdParam) / 100; why += "Zoom Lens x${times(100 + atk.holdParam)}" }
            else -> {}
        }
        if (def.hold == Hold.EVASION_UP) { calc = calc * (100 - def.holdParam) / 100; why += "Bright Powder x${times(100 - def.holdParam)}" }
        if (atk.usedMicleBerry) {
            if (named(atk.ability, "Ripen")) { calc = calc * 140 / 100; why += "Micle Berry (Ripen) x1.4" }
            else { calc = calc * 120 / 100; why += "Micle Berry x1.2" }
        }
        if (gravity) { calc = calc * 5 / 3; why += "Gravity x5/3" }
        if (w == Weather.FOG) { calc = calc * 60 / 100; why += "Fog x0.6" }
        return Result(calc, false, why, unknown)
    }

    /** DoesOHKOMoveMissTarget: a higher-level target or Sturdy never falls; otherwise accuracy plus the level gap. */
    private fun ohko(move: Move, atk: Side, def: Side, unknown: List<String>): Result {
        if (def.level > atk.level) return Result(0, false, listOf("Target is higher level"), unknown)
        if (named(def.ability, "Sturdy")) return Result(0, false, listOf("Sturdy"), unknown)
        if (atk.sureHit) return Result(100, true, listOf("Lock-On"), unknown)
        if (named(atk.ability, "No Guard") || named(def.ability, "No Guard")) return Result(100, true, listOf("No Guard"), unknown)
        val why = arrayListOf("One-hit KO: ${move.accuracy} + ${atk.level - def.level} levels")
        var odds = move.accuracy + (atk.level - def.level)
        if (move.plusTenOffType && move.type !in atk.types) { odds -= 10; why += "Off-type -10" }
        return Result(odds.coerceAtLeast(0), false, why, unknown)
    }

    private val LEEK_USERS = setOf("farfetch'd", "sirfetch'd")

    /** CalcCritChanceStage and its odds, for a damaging [move]. [noCrits] is the first rival battle (KaizoCore_IsFirstRivalBattle). */
    fun crit(move: Move, atk: Side, def: Side, noCrits: Boolean = false): Crit {
        val unknown = unknowns(atk, def)
        if (noCrits) return Crit(0, -1, listOf("First rival battle"), unknown)
        if (def.luckyChant) return Crit(0, -1, listOf("Lucky Chant"), unknown)
        val armor = named(def.ability, "Battle Armor", "Shell Armor")
        val always = when {
            atk.laserFocus -> "Laser Focus"
            move.alwaysCrit -> "Always crits"
            named(atk.ability, "Merciless") && def.poisoned -> "Merciless"
            else -> null
        }
        if (armor) return Crit(0, -1, listOf(def.ability!!), unknown)
        if (always != null) return Crit(1, -2, listOf(always), unknown)
        val why = ArrayList<String>()
        var stage = 0
        if (atk.focusEnergy) { stage += 2; why += "Focus Energy +2" }
        if (atk.dragonCheer) { stage += 1; why += "Dragon Cheer +1" }
        if (move.critStage > 0) { stage += move.critStage; why += "High crit +${move.critStage}" }
        val sp = atk.species?.lowercase()
        when {
            atk.hold == Hold.SCOPE_LENS -> { stage += 1; why += "Scope Lens +1" }
            atk.hold == Hold.LUCKY_PUNCH && sp == "chansey" -> { stage += 2; why += "Lucky Punch +2" }
            atk.hold == Hold.LEEK && sp in LEEK_USERS -> { stage += 2; why += "Leek +2" }
        }
        if (named(atk.ability, "Super Luck")) { stage += 1; why += "Super Luck +1" }
        stage = stage.coerceAtMost(CRIT_ODDS.size - 1)
        return Crit(CRIT_ODDS[stage], stage, why, unknown)
    }

    /** [id]'s accuracy flags out of the Heart & Soul ROM behind [memory]. */
    fun move(memory: MemoryReader, id: Int): Move? = Rom.move(HnsData(memory), id)

    /** [id]'s hold effect and strength out of the Heart & Soul ROM behind [memory]. */
    fun item(memory: MemoryReader, id: Int): Pair<Hold, Int> = Rom.item(HnsData(memory), id)

    /** The ROM side: move flags out of gMovesInfo and hold effects out of gItemsInfo. */
    internal object Rom {
        /** HOLD_EFFECT_* ids of this build (layout-kaizo.json enums.HOLD_EFFECT; HitChanceTest holds them to it). */
        const val HOLD_EVASION_UP = 22
        const val HOLD_SCOPE_LENS = 40
        const val HOLD_LUCKY_PUNCH = 45
        const val HOLD_LEEK = 48
        const val HOLD_WIDE_LENS = 63
        const val HOLD_ZOOM_LENS = 65

        /** gMovesInfo[id].effect for EFFECT_OHKO (include/constants/battle_move_effects.h; HitChanceTest reads it off the four moves). */
        const val EFFECT_OHKO = 29

        fun hold(holdEffect: Int): Hold = when (holdEffect) {
            HOLD_EVASION_UP -> Hold.EVASION_UP
            HOLD_SCOPE_LENS -> Hold.SCOPE_LENS
            HOLD_LUCKY_PUNCH -> Hold.LUCKY_PUNCH
            HOLD_LEEK -> Hold.LEEK
            HOLD_WIDE_LENS -> Hold.WIDE_LENS
            HOLD_ZOOM_LENS -> Hold.ZOOM_LENS
            else -> Hold.NONE
        }

        fun move(data: HnsData, id: Int): Move? {
            val r = data.move(id) ?: return null
            val M = HnsLayout.MoveInfo
            val category = M.category.at(r)
            return Move(
                accuracy = M.accuracy.at(r), physical = category == 0, status = category == 2, type = M.type.at(r),
                ignoresEvasion = M.ignoresTargetDefenseEvasionStages.at(r) != 0, fiftyInSun = M.accuracy50InSun.at(r) != 0,
                sureInRain = M.alwaysHitsInRain.at(r) != 0, sureInHailSnow = M.alwaysHitsInHailSnow.at(r) != 0,
                sureOnSameType = M.alwaysHitsOnSameType.at(r) != 0, minimizeDouble = M.minimizeDoubleDamage.at(r) != 0,
                plusTenOffType = M.accIncreaseByTenOnSameType.at(r) != 0, ohko = M.effect.at(r) == EFFECT_OHKO,
                critStage = M.criticalHitStage.at(r), alwaysCrit = M.alwaysCriticalHit.at(r) != 0,
            )
        }

        /** [item]'s hold effect and its strength (BrightPowder 10, Wide Lens 10, Zoom Lens 20). */
        fun item(data: HnsData, id: Int): Pair<Hold, Int> {
            if (id == 0) return Hold.NONE to 0
            val r = data.item(id) ?: return Hold.NONE to 0
            val I = HnsLayout.ItemInfo
            return hold(I.holdEffect.at(r)) to I.holdEffectParam.at(r)
        }
    }
}
