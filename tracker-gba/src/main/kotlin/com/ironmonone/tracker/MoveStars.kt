package com.ironmonone.tracker

/**
 * Utils.calculateMoveStars: which of an opponent's tracked moves it might have
 * forgotten since you saw it use them, drawn with a "*" after the name.
 *
 * For each of the first four tracked moves (most recent first), count the
 * level-up moves the species has learned since the level the move was last
 * seen at, up to its level now. Rank the moves by age: a move seen at a higher
 * level than another ranks above it. A move is starred when it was not seen at
 * level 1 and at least as many new moves have come in as its rank - enough to
 * have pushed it out.
 *
 * The rank counts every tracked move, not only the four shown (Utils.lua:568-579 compares each of the four against all
 * of Tracker.getMoves, which is never trimmed). Counted among the four alone, older sightings lowered every rank, and
 * the card could star all four moves where the PC tracker stars none (rc32 audit P2 #137).
 */
object MoveStars {
    /**
     * [tracked] is (move id, level last seen), most recent first. [moveLevels]
     * is the species' learnset levels; Lv.1 entries never count, since no move
     * is seen below level 1.
     */
    fun of(tracked: List<Pair<Int, Int>>, level: Int, moveLevels: List<Int>): Set<Int> {
        if (level <= 1 || tracked.isEmpty()) return emptySet()
        val all = tracked.map { (id, lv) -> id to maxOf(lv, 1) }
        val out = HashSet<Int>()
        all.take(4).forEachIndexed { i, (id, lv) ->
            val learnedSince = moveLevels.count { it > lv && it <= level }
            val rank = 1 + all.indices.count { j -> j != i && lv > all[j].second }
            if (lv != 1 && learnedSince >= rank) out += id
        }
        return out
    }
}
