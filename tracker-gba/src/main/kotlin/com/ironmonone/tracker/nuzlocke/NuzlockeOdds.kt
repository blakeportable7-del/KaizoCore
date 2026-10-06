package com.ironmonone.tracker.nuzlocke

import kotlin.math.floor
import kotlin.math.roundToInt

/*
 * Survival chances (2026-10-06, Nuzlify's damage calc "with survival chances"): for one hit of one move, every damage
 * roll the game can make, with and without a critical hit, against the HP your Pokemon has, gives the chance it lives.
 *
 * The rolls are the game's own: Generations 3 to 5 multiply by 85 to 100 percent (16 rolls, each as likely), Game Boy
 * games by 217 to 255 out of 255 (39 rolls). A critical hit doubles the damage (Generation 1 doubles the attacker's
 * level instead) and comes 1 time in 16 at the normal rate (17 in 256 on Gold, Silver and Crystal; on Red, Blue and
 * Yellow it is the attacker's base Speed over 512). Accuracy is left out on purpose: a miss is never something to
 * plan a Nuzlocke on, and the words say "if it hits".
 */
object NuzlockeOdds {

    /** One attack. Stats are the ones in the battle, after nature; [effectiveness] is the type multiplier, 0 to 4. */
    data class Hit(
        val generation: Int,
        val level: Int,
        val power: Int,
        val attack: Int,
        val defense: Int,
        val stab: Boolean,
        val effectiveness: Double,
        /** Anything else that multiplies the damage (a screen, weather, an item); 1 when nothing. */
        val other: Double = 1.0,
        /** A move that does a fixed amount (Seismic Toss, Dragon Rage): that amount, rolls and crits apart. */
        val fixed: Int? = null,
    )

    /** Every damage the hit can do, one entry per roll, each as likely. */
    fun rolls(h: Hit, crit: Boolean): IntArray {
        if (h.fixed != null) return intArrayOf(if (h.effectiveness == 0.0) 0 else h.fixed)
        if (h.effectiveness == 0.0 || h.power <= 0) return intArrayOf(0)
        val def = maxOf(1, h.defense)
        return if (h.generation <= 2) {
            val level = if (crit && h.generation == 1) h.level * 2 else h.level
            var base = floor(floor((2 * level / 5 + 2).toDouble() * h.power * h.attack / def) / 50).toInt()
            if (crit && h.generation == 2) base *= 2
            base = minOf(base, 997) + 2
            var d = (base * h.other).toInt()
            if (h.stab) d += d / 2
            d = (d * h.effectiveness).toInt()
            IntArray(39) { i -> if (d <= 1) d else d * (217 + i) / 255 }
        } else {
            var base = floor(floor((2 * h.level / 5 + 2).toDouble() * h.power * h.attack / def) / 50).toInt()
            base = (base * h.other).toInt() + 2
            if (crit) base *= 2
            IntArray(16) { i ->
                if (h.generation == 3) {
                    // pokeemerald: STAB and type before the roll.
                    var d = base
                    if (h.stab) d = d * 15 / 10
                    d = (d * h.effectiveness).toInt()
                    maxOf(1, d * (85 + i) / 100)
                } else {
                    // Generations 4 and 5: the roll, then STAB, then type.
                    var d = base * (85 + i) / 100
                    if (h.stab) d = d * 15 / 10
                    maxOf(1, (d * h.effectiveness).toInt())
                }
            }
        }
    }

    /** The normal chance of a critical hit. [baseSpeed] is the attacker's, which only Red, Blue and Yellow use. */
    fun critChance(generation: Int, baseSpeed: Int = 0): Double = when (generation) {
        1 -> minOf(255, baseSpeed / 2) / 256.0
        2 -> 17.0 / 256
        else -> 1.0 / 16
    }

    /** What one hit does to [hp]: its damage as a share of [maxHp], and the chance of living through it. */
    data class Odds(
        val minDamage: Int,
        val maxDamage: Int,
        /** Without a critical hit. */
        val minPercent: Int,
        val maxPercent: Int,
        /** The chance to live through one hit, critical hits counted, 0 to 1. */
        val survive: Double,
        /** The same without critical hits, and with one. */
        val surviveNoCrit: Double,
        val surviveCrit: Double,
        /** Hits it takes to faint from full HP at the highest and lowest roll, critical hits left out; 0 for a move that does nothing. */
        val hitsAtBest: Int,
        val hitsAtWorst: Int,
    )

    fun odds(h: Hit, hp: Int, maxHp: Int, critChance: Double): Odds {
        val normal = rolls(h, crit = false)
        val crit = if (h.fixed != null) normal else rolls(h, crit = true)
        val sNo = normal.count { it < hp }.toDouble() / normal.size
        val sCrit = crit.count { it < hp }.toDouble() / crit.size
        val lo = normal.min(); val hi = normal.max()
        fun hits(d: Int) = if (d <= 0) 0 else (maxHp + d - 1) / d
        return Odds(
            lo, hi,
            if (maxHp > 0) lo * 100 / maxHp else 0, if (maxHp > 0) hi * 100 / maxHp else 0,
            sNo * (1 - critChance) + sCrit * critChance, sNo, sCrit,
            hits(lo), hits(hi),
        )
    }

    /** "96 in 100": a chance as a count out of 100, never 0 or 100 unless it is certain. */
    fun inHundred(p: Double): String {
        val n = when {
            p <= 0.0 -> 0
            p >= 1.0 -> 100
            else -> (p * 100).roundToInt().coerceIn(1, 99)
        }
        return "$n in 100"
    }

    /** The odds in words: "Survives one hit 94 in 100 (a critical hit faints it). Takes 31 to 37% of its HP." */
    fun words(o: Odds): String {
        if (o.maxDamage <= 0) return "Does nothing to it."
        val pct = if (o.minPercent == o.maxPercent) "${o.minPercent}%" else "${o.minPercent} to ${o.maxPercent}%"
        val live = when {
            o.survive >= 1.0 -> "Always survives one hit"
            o.survive <= 0.0 -> "Faints from one hit, every time"
            else -> "Survives one hit " + inHundred(o.survive)
        }
        val critNote = when {
            o.survive <= 0.0 || o.survive >= 1.0 -> ""
            o.surviveNoCrit >= 1.0 && o.surviveCrit <= 0.0 -> " (only a critical hit faints it)"
            o.surviveNoCrit >= 1.0 -> " (only a critical hit can faint it)"
            else -> ""
        }
        val takes = "Takes $pct of its HP" + if (o.hitsAtWorst > 0) ", ${hitsText(o.hitsAtWorst, o.hitsAtBest)} from full." else "."
        return "$live$critNote. $takes"
    }

    private fun hitsText(worst: Int, best: Int): String =
        if (worst == best) "$worst ${if (worst == 1) "hit" else "hits"}" else "$worst to $best hits"

    /** A stat as Generations 3 to 5 make it: base, IV and EV at a level, times the nature (0.9, 1 or 1.1). HP when [hp]. */
    fun stat(base: Int, iv: Int, ev: Int, level: Int, nature: Double = 1.0, hp: Boolean = false): Int {
        val core = (2 * base + iv + ev / 4) * level / 100
        return if (hp) core + level + 10 else floor((core + 5) * nature).toInt()
    }

    /**
     * The moves that do a set amount, by Gen 3 move id, and the amount at [level]: Seismic Toss and Night Shade the
     * level, Dragon Rage 40, Sonic Boom 20. Null for every other move.
     */
    fun fixedDamage(moveId: Int, level: Int): Int? = when (moveId) {
        69, 101 -> level
        82 -> 40
        49 -> 20
        else -> null
    }
}
