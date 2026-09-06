package com.ironmonone.app

import com.ironmonone.tracker.Gen3Types

/**
 * General type-chart facts about a move's own type: which defending types it
 * hits hard, which resist it, which it cannot touch. Nothing about the
 * Pokemon across the field.
 *
 * History, so it is not re-litigated: the first version measured the move
 * against the CURRENT OPPONENT's live types ("vs Geodude: Super effective
 * (x4)"). Blake vetoed it on 2026-09-05 - it hands the player an advantage
 * the ruleset does not allow. What remains is the chart, which any player
 * may know by heart; the opponent's typing is still theirs to work out.
 */
object MoveMatchup {

    data class General(
        val strongAgainst: List<String>,
        val resistedBy: List<String>,
        val noEffectOn: List<String>,
    )

    fun general(moveType: Int?): General? {
        if (moveType == null || moveType !in Gen3Types.ALL) return null
        val strong = ArrayList<String>(); val weak = ArrayList<String>(); val none = ArrayList<String>()
        for (t in Gen3Types.ALL) {
            val m = Gen3Types.effect(moveType, t)
            when {
                m == 0.0 -> none += Gen3Types.name(t)
                m > 1.0 -> strong += Gen3Types.name(t)
                m < 1.0 -> weak += Gen3Types.name(t)
            }
        }
        // Alphabetical, not chart order: "Fire, Ground, Rock" scans; "Ground,
        // Rock, Fire" makes the reader hunt.
        return General(strong.sorted(), weak.sorted(), none.sorted())
    }
}
