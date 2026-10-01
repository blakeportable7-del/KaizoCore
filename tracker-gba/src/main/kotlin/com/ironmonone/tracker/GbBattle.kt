package com.ironmonone.tracker

/**
 * A battler's stat stages on a Game Boy game, as the panel draws them.
 *
 * Battle.updateStatStages (Gen 1 reference Battle.lua:735-761) reads the
 * player's six mod bytes at StatChange (wPlayerMonStatMods) and the enemy's
 * 0x14 on (wEnemyMonStatMods): attack, defense, speed, special, accuracy,
 * evasion. updateStatStagesGen2 (Gen 2 reference Battle.lua:699-722) reads
 * seven at wPlayerStatLevels and 8 on for the enemy, with special attack and
 * special defense apart. The game keeps them 1..13 with 7 neutral, and
 * TrackerScreen.lua's StatConvert draws raw - 7 chevrons (Gen 1 reference
 * :838-889, Gen 2 :807-858), so they come out here as the panel's 0..12
 * with 6 neutral. Anything outside 1..13 is not a battle's stages yet.
 */
internal fun gbStatStages(raw: ByteArray, names: List<String>): StatStages {
    if (raw.size < names.size) return emptyMap()
    val v = names.indices.map { raw.u8(it) }
    if (v.any { it !in 1..13 }) return emptyMap()
    return names.indices.associate { names[it] to v[it] - 1 }
}

/**
 * The "Team:" row of a trainer battle as the Game Boy references draw it
 * (Drawing.drawTrainerTeamPokeballs over Tracker.getPokemon(1..6, false),
 * TrackerScreen.lua:825-828 in the Gen 1 reference, :794-797 in the Gen 2):
 * one ball per enemy Pokemon the tracker knows, fainted ones in the grey
 * palette. They only ever know one. Program.updatePokemonTeams fills
 * otherTeam[1] from estats, the opponent on the field, and nothing else
 * (Gen 1 reference Program.lua:546-565, Gen 2 :641-660), so slots 2 to 6
 * stay 0 and draw nothing: one ball, grey once the opponent on the field has
 * fainted. The game's whole enemy party is not read here.
 */
internal fun gbEnemyTeam(trainerBattle: Boolean, enemy: EnemyInfo?): List<Boolean> =
    if (trainerBattle && enemy != null) listOf(enemy.curHp > 0) else emptyList()

/** The stage bytes' order in pokered's wPlayerMonStatMods (Gen 1: one Special, shown as SPA). */
internal val GEN1_STAGES = listOf("ATK", "DEF", "SPE", "SPA", "ACC", "EVA")

/** The stage bytes' order in pokecrystal's and pokegold's wPlayerStatLevels. */
internal val GEN2_STAGES = listOf("ATK", "DEF", "SPE", "SPA", "SPD", "ACC", "EVA")

/**
 * When the carousel's LAST_ATTACK line shows, and which move, on a Game Boy
 * game: Battle.processBattleTurn (Gen 1 reference Battle.lua:695-731), the
 * Gen 2 reference's processBattleTurnGen2 (Battle.lua:379-408), the same code.
 *
 * Each read: a new value of the turn counter (gTurn) clears "the enemy has
 * attacked"; a non-zero enemy move byte (eMove) sets it and becomes the last
 * move. The line shows while the enemy has not attacked since the counter last
 * moved and there is a last move (TrackerScreen.lua:422, :391 in the Gen 2
 * reference), with "Show last damage calcs" on. A new battle starts
 * the counter at 0 (beginNewBattle), the end of one forgets the move
 * (endCurrentBattle). The references never read damage on a Game Boy game,
 * so the line is always "Last move: X".
 *
 * gTurn is wAILayer2Encouragement in Gen 1, which a new opponent resets
 * together with wEnemyMoveNum and the enemy's move bumps (pokered
 * engine/battle/core.asm), and wPlayerTurnsTaken in Gen 2.
 */
internal class GbLastMove {
    private var turnCount = -1
    private var enemyHasAttacked = false
    private var lastEnemyMoveId = 0
    private var battling = false

    /** One read: [turn] the gTurn byte, [move] the eMove byte, in battle or not. */
    fun read(inBattle: Boolean, turn: Int, move: Int) {
        if (inBattle && !battling) { turnCount = 0; enemyHasAttacked = false }   // beginNewBattle
        if (!inBattle && battling) { turnCount = -1; lastEnemyMoveId = 0 }       // endCurrentBattle
        battling = inBattle
        if (!inBattle) return
        if (turn != turnCount) { turnCount = turn; enemyHasAttacked = false }
        if (move != 0) { enemyHasAttacked = true; lastEnemyMoveId = move }
    }

    /** The move the line shows, or 0 while it is not time. */
    val shown: Int get() = if (battling && !enemyHasAttacked) lastEnemyMoveId else 0
}
