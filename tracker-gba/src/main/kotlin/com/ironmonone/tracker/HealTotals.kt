package com.ironmonone.tracker

/**
 * The Heals line's three numbers for a Pokemon with [maxHp] HP: the healing carried as a share of its max HP, the HP it
 * adds up to, and how many healing items there are. Program.recalcLeadPokemonHealingInfo (Program.lua:1654-1683), per
 * item id:
 *
 *   percentageAmt = quantity * min(amount / maxHP * 100, 100)     a flat heal, capped at a full heal each
 *   percentageAmt = quantity * amount                              a percentage heal
 *   healingValue += floor(percentageAmt * maxHP / 100 + 0.5)
 *
 * and TrackerScreen.lua:1267-1269 prints both with "%.0f", which rounds. The panel used to truncate the percent and
 * rebuild the HP from it (percent * maxHp / 100), so one Potion on a 30 HP Pokemon read 66% and 19 HP where the PC
 * tracker reads 67% and 20 HP (rc32 audit P2 #99). The Game Boy references sum the same float percent and print it the
 * same way (Ironmon-gen-tracker Program.lua:1291-1302, TrackerScreen.lua:799).
 */
data class HealTotals(val percent: Int, val hp: Int, val count: Int) {
    companion object {
        val NONE = HealTotals(0, 0, 0)

        /**
         * [items] is item id to quantity, the bag's stacks of one item added together. [heal] gives an item's
         * (amount, isPercentage) when it heals HP, null otherwise.
         */
        fun of(items: Map<Int, Int>, maxHp: Int, heal: (Int) -> Pair<Double, Boolean>?): HealTotals {
            if (maxHp <= 0) return NONE
            var percent = 0.0
            var hp = 0L
            var count = 0
            for ((id, qty) in items) {
                if (qty <= 0) continue
                val (amount, isPercentage) = heal(id) ?: continue
                val share = if (isPercentage) qty * amount else qty * minOf(amount / maxHp * 100, 100.0)
                percent += share
                hp += Math.floor(share * maxHp / 100 + 0.5).toLong()
                count += qty
            }
            // DataHelper.lua:378-380: at most 9999 percent, 99999 HP and 99 items (healnum, "Max of 99"; the Game Boy
            // trackers' DataHelper.lua:331-332 caps the same two); "%.0f" rounds half to even, as Math.rint does. The count
            // was printed whole (RC35-NOTICED N #33).
            return HealTotals(Math.rint(minOf(percent, 9999.0)).toInt(), minOf(hp, 99999L).toInt(), minOf(count, 99))
        }
    }
}
