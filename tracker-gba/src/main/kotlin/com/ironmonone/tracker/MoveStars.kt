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
 */
object MoveStars {
    /**
     * [tracked] is (move id, level last seen), most recent first. [moveLevels]
     * is the species' learnset levels; Lv.1 entries never count, since no move
     * is seen below level 1.
     */
    fun of(tracked: List<Pair<Int, Int>>, level: Int, moveLevels: List<Int>): Set<Int> {
        if (level <= 1 || tracked.isEmpty()) return emptySet()
        val four = tracked.take(4).map { (id, lv) -> id to maxOf(lv, 1) }
        val out = HashSet<Int>()
        four.forEachIndexed { i, (id, lv) ->
            val learnedSince = moveLevels.count { it > lv && it <= level }
            val rank = 1 + four.indices.count { j -> j != i && lv > four[j].second }
            if (lv != 1 && learnedSince >= rank) out += id
        }
        return out
    }
}
