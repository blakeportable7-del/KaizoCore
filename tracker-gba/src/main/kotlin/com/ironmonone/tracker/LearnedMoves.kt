package com.ironmonone.tracker

/**
 * The tracker's "Moves 1/8 (16)" header: moves learned so far, out of how many,
 * and the level of the next one. Utils.getMovesLearnedHeader in Ironmon-Tracker.
 *
 * The reference counts over PokemonData movelvls, "the levels at which a
 * Pokemon learns NEW moves", and that list never contains the moves a Pokemon
 * starts with: vanilla Bulbasaur learns Tackle at Lv.1, and its movelvls begin
 * at 4. The Gen 3 tracker here reads the learnset straight out of the ROM,
 * which does carry the Lv.1 entries, so it counted a Pokemon's starting moves as
 * learned: Blake's Lv.5 Flaaffy starter read "Moves 4/9 (9)" where the PC
 * tracker reads "Moves 0/5 (9)" (2026-09-19).
 *
 * Checked against every Gen 1 and Gen 2 species in a FireRed ROM: the ROM's
 * levels with the Lv.1 entries removed equal the reference's movelvls for all
 * 251, and for none of them without that step. A randomized ROM keeps the
 * learn levels and only adds Lv.1 moves ("guarantee four starting moves"), so
 * dropping Lv.1 gives the reference's answer there too.
 *
 * The DS tracker needs none of this: its tables are converted from the DS
 * reference's own movelvls, which already leave Lv.1 out.
 */
object LearnedMoves {
    data class Header(val learned: Int, val total: Int, val next: Int?)

    /** [levels] is the learnset's levels in ROM order (ascending). */
    fun of(levels: List<Int>, level: Int): Header {
        val learnt = levels.filter { it > 1 }
        return Header(
            learned = learnt.count { it <= level },
            total = learnt.size,
            next = learnt.firstOrNull { it > level },
        )
    }
}
