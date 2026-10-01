package com.ironmonone.tracker.nuzlocke

/*
 * What the rules engine is told about the game, one poll at a time (2026-09-29).
 *
 * This is deliberately not TrackerState. A Gen 3 tracker, a Game Boy tracker and a DS tracker each read a
 * different set of addresses, and each has an adapter that turns its own state into a Snapshot; the engine
 * never learns which console it is looking at. A field an adapter cannot fill stays at its default
 * (BattleEnd.UNKNOWN, null bag, no opponent) and the engine falls back on what it can see: enemy HP, the
 * party growing, a new personality value showing up.
 */

/** How a wild battle was started, as far as the rules care. */
enum class Method(val label: String) {
    WALK("grass or cave"), SURF("surfing"), UNDERWATER("diving"), ROCK_SMASH("rock smash"), ROD("fishing"), HEADBUTT("headbutt tree"),
    /** A set battle: a static Pokemon, a legendary, a roamer, the first battle of the game. */
    STATIC("set battle");

    val isWater: Boolean get() = this == SURF || this == UNDERWATER || this == ROD
}

/** How the last battle ended, from the game's own outcome value where there is one. */
enum class BattleEnd { UNKNOWN, WON, LOST, DREW, RAN, MON_FLED, CAUGHT }

class NzItem(val name: String, val qty: Int)

data class NzMon(
    /** A number that names this Pokemon for good: the Gen 3 personality value, or a fingerprint on other consoles. */
    val id: Long,
    val species: Int,
    val speciesName: String,
    val nickname: String,
    val level: Int,
    val hp: Int,
    val maxHp: Int,
    val isEgg: Boolean,
    val gender: Gender?,
    val types: List<Int>,
    val shiny: Boolean,
) {
    /** A real party member: not an egg, not a slot that has not decoded yet. */
    val real: Boolean get() = !isEgg && level > 0 && maxHp > 0
    val fainted: Boolean get() = real && hp <= 0
}

data class NzEnemy(
    val id: Long,
    val species: Int,
    val speciesName: String,
    val level: Int,
    val hp: Int,
    val maxHp: Int,
    val gender: Gender?,
    val types: List<Int>,
    val shiny: Boolean,
)

data class NzOpponent(
    val trainerId: Int,
    /** "LEADER Roxanne": what the game calls them. */
    val label: String,
    /** The tracker's group: Gym, Elite4, Boss, Rival or Other. */
    val group: String,
    /** The level cap table's key when the trainer is one of its bosses. */
    val bossKey: String?,
    /** The highest level on their team, read from the game; null when it could not be. */
    val maxLevel: Int?,
)

/**
 * Where the player is: the map's name as the game's route data gives it, and its id. On the Game Boy games the name is
 * the place (every floor of a cave shares it) and [detail] is the specific map ("Mt. Moon B1F"), which the switch that
 * keeps floors apart uses; a Gen 3 or DS name has none and is grouped by NuzlockeAreas.
 */
data class NzArea(val name: String?, val mapId: Int?, val detail: String? = null)

class Snapshot(
    /** False when the tracker could not read the game this poll; the engine ignores such a poll. */
    val readable: Boolean = true,
    val area: NzArea = NzArea(null, null),
    val inBattle: Boolean = false,
    val wild: Boolean = false,
    /** A Pokemon Tower ghost fought without the Silph Scope: never an encounter. */
    val ghost: Boolean = false,
    val method: Method = Method.WALK,
    val enemy: NzEnemy? = null,
    val opponent: NzOpponent? = null,
    /** How the last battle ended, once the game says. */
    val end: BattleEnd = BattleEnd.UNKNOWN,
    /** The battle's turn counter, when the game has one: 0 at the start of a battle. */
    val turn: Int? = null,
    val party: List<NzMon> = emptyList(),
    /** The game's own party count, to tell a whole read of the party from a partial one. */
    val partyCount: Int = party.size,
    /** Gym badges as bits, badge 1 in bit 0. */
    val badges: Int = 0,
    /** Poke Balls in the bag; null when it could not be read. */
    val ballCount: Int? = null,
    /** The bag without Poke Balls, item id to item, read while a battle is on and once after it; null otherwise. */
    val bag: Map<Int, NzItem>? = null,
    /** The game's battle style: true is Set, false is Shift, null when it could not be read. */
    val battleStyleSet: Boolean? = null,
    val caps: LevelCapTable? = null,
    /** Keys of the caps table's bosses that are beaten. */
    val beaten: Set<String> = emptySet(),
)
