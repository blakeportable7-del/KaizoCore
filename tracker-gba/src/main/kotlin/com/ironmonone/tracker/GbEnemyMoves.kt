package com.ironmonone.tracker

/**
 * The opponent's moves seen this encounter on a Game Boy game, by the
 * references' rule for the byte they read (eMove: wEnemyMoveNum in Gen 1,
 * the first byte of wEnemyMoveStruct in Gen 2).
 *
 * That byte is not only the move the opponent used. It keeps the last
 * battle's move until something overwrites it, and Gen 2's AI loads every
 * move it weighs into the same struct while it decides (pokecrystal
 * engine/battle/ai/scoring.asm AIGetEnemyMove). So the references record it
 * only when it is one of the opponent's own four moves (Gen 1 reference
 * Battle.updateTrackedInfo, Gen 2 reference Battle.updateTrackedInfoGen2),
 * and Gen 2's only once per enemy turn: wEnemyTurnsTaken must have moved
 * past the turn the last move was recorded on, and it starting over at 0
 * starts the count over (Gen 2 reference Battle.lua:429-455).
 *
 * Gen 2 compares the opponent's four as its battle struct holds them on the
 * same tick. Gen 1 compares the four it had before it first moved ([seeGen1]).
 */
internal class GbEnemyMoves {
    /** The moves recorded, oldest first. */
    val seen = LinkedHashSet<Int>()
    private var species = -1
    private var lastTurn = 0
    private var startMoves: List<Int>? = null

    /** Out of battle: nothing carries into the next one. */
    fun clear() { species = -1; lastTurn = 0; startMoves = null; seen.clear() }

    /**
     * One Gen 1 reading (Gen 1 reference Battle.updateTrackedInfo, Battle.lua
     * 791-835). Its turn counter (gTurn, wAILayer2Encouragement) is cleared
     * when a battle starts and when the opponent is sent out, and counts up as
     * the opponent's move runs (pokered engine/battle/core.asm). While it is
     * 0 the reference takes the opponent's four moves
     * (populateBattlePartyObject); once it is not, the move byte counts when
     * it is one of those four. So the byte left from the last battle is not
     * recorded before the new opponent has moved (wEnemyMoveNum is not
     * cleared with the battle data), and a move gained in battle (Mimic,
     * Transform) is not recorded as the species' own.
     */
    fun seeGen1(species: Int, move: Int, moves: List<Int>, turns: Int) {
        if (species != this.species) { this.species = species; lastTurn = 0; startMoves = null; seen.clear() }
        if (turns == 0 || startMoves == null) startMoves = moves
        if (turns == 0) return
        if (move in 1..165 && move in startMoves!!) seen.add(move)
    }

    /**
     * One reading. [turns] is wEnemyTurnsTaken for Gen 2 and null for Gen 1,
     * which has no once-per-turn rule. A new opponent starts a fresh list.
     */
    fun see(species: Int, move: Int, known: List<Int>, ids: IntRange, turns: Int? = null) {
        if (species != this.species) { this.species = species; lastTurn = 0; seen.clear() }
        if (turns == 0) lastTurn = 0
        if (move !in ids || move !in known) return
        if (turns != null) {
            if (turns <= lastTurn) return
            lastTurn = turns
        }
        seen.add(move)
    }
}
