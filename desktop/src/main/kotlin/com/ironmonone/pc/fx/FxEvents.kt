package com.ironmonone.pc.fx

import com.ironmonone.tracker.TrackerState

/**
 * What changed between two reads of the game that is worth a moment on stream (the animation exploration, 2026-10-09,
 * on the copy). Each event names the side it happened to: your lead ([Side.OWN]) or the opponent ([Side.FOE]). The card
 * shows one of them at a time; the layer plays what belongs to the side on screen and keeps the rest (a badge) for the
 * card it fits.
 */
sealed class FxEvent {
    enum class Side { OWN, FOE }

    /** A major status began ([kind]: SLP, PSN, BRN, FRZ, PAR), or ended ([kind] empty, [was] the one it was). */
    data class Status(val side: Side, val kind: String, val was: String) : FxEvent()

    /** A stat stage moved: [stat] is the card's label (ATK, DEF, SPA, SPD, SPE, ACC, EVA), [delta] -6 to +6. */
    data class Stage(val side: Side, val stat: String, val delta: Int, val stage: Int) : FxEvent()

    /** HP went down by [amount] of [max]; [big] when it took a third or more (a hard hit). */
    data class Damage(val side: Side, val amount: Int, val max: Int, val big: Boolean) : FxEvent()

    /** HP went up by [amount] (a potion, a berry, Leech Seed, a drain move). */
    data class Heal(val side: Side, val amount: Int, val max: Int) : FxEvent()

    data class Faint(val side: Side) : FxEvent()

    /** Your lead gained a level. */
    data class LevelUp(val from: Int, val to: Int) : FxEvent()

    /** A badge was won: [index] 0-based, [count] the badges now held. */
    data class Badge(val index: Int, val count: Int, val set: String) : FxEvent()

    /** The Elite Four and the champion beaten (TrackerState.leagueBeaten turned on). */
    object League : FxEvent()

    /** A battle began: wild or a trainer. */
    data class BattleStart(val wild: Boolean) : FxEvent()

    /** A battle ended with your lead still standing. */
    object BattleWon : FxEvent()

    /** An item was used on [side]'s Pokemon: [item] is the game's id (its icon, name and use come from the ROM). */
    data class ItemUsed(val side: Side, val item: Int) : FxEvent()

    // Lasting effects (a substitute, Taunt, Leech Seed, Spikes, the weather ...) are not events: FxLasting reads what the
    // game lists each time and FxLayer.sync keeps the card to it.

    /** The game showed [side]'s ability [name] (its pop-up: TrackerState.abilitiesRevealed). */
    data class Ability(val side: Side, val name: String) : FxEvent()

    /** Your lead learned [move] into move slot [slot]: at a level up ([levelUp]) or taught (a TM, an HM, a tutor). */
    data class MoveLearned(val slot: Int, val move: String, val levelUp: Boolean) : FxEvent()

    /** Your lead's effort values in [stat] (the card's label) moved by [delta] outside battle: a vitamin, a feather, a berry. */
    data class Effort(val stat: String, val delta: Int) : FxEvent()

    /** Your lead gained [levels] outside battle: a Rare Candy or an Exp. Candy. */
    data class Candy(val levels: Int) : FxEvent()
}

/**
 * The events between [prev] and [now]. Pure: two states in, a list out, so it can be checked without a window. A
 * change of lead or of opponent is not damage or a status for anyone (the next Pokemon came out), so HP, stages and
 * status compare only the same Pokemon (species and, for the opponent, its personality value).
 */
object FxEvents {
    fun between(prev: TrackerState?, now: TrackerState): List<FxEvent> {
        if (prev == null) return emptyList()
        val out = ArrayList<FxEvent>()
        if (now.inBattle && !prev.inBattle) out += FxEvent.BattleStart(now.isWildBattle)

        val a = prev.lead; val b = now.lead
        if (a != null && b != null && a.mon.species == b.mon.species && a.mon.pid == b.mon.pid) {
            side(FxEvent.Side.OWN, a.mon.curHp, b.mon.curHp, b.mon.maxHp, a.statusCondition, b.statusCondition,
                if (prev.inBattle && now.inBattle) a.statStages else emptyMap(), if (now.inBattle) b.statStages else emptyMap(), out)
            if (b.mon.level > a.mon.level) out += FxEvent.LevelUp(a.mon.level, b.mon.level)
            // Outside battle a level comes only from a candy, and effort values only from a vitamin, a feather or a berry.
            val outside = !prev.inBattle && !now.inBattle
            if (outside && b.mon.level > a.mon.level) out += FxEvent.Candy(b.mon.level - a.mon.level)
            if (outside) for (i in 0 until minOf(6, a.mon.evs.size, b.mon.evs.size)) {
                val d = b.mon.evs[i] - a.mon.evs[i]
                if (d != 0) out += FxEvent.Effort(EV_STATS[i], d)
            }
            // A move id the lead did not know: learned (the same set reordered is not a new move).
            for ((i, id) in b.mon.moves.withIndex()) if (id != 0 && id !in a.mon.moves)
                out += FxEvent.MoveLearned(i, b.moveNames.getOrElse(i) { "" }, levelUp = b.mon.level > a.mon.level || now.inBattle)
        }

        val e = prev.enemy; val f = now.enemy
        if (prev.inBattle && now.inBattle && e != null && f != null && e.species == f.species && e.pid == f.pid) {
            side(FxEvent.Side.FOE, e.curHp, f.curHp, f.maxHp, e.statusCondition, f.statusCondition, e.statStages, f.statStages, out)
        }

        // The abilities the game showed since the last read (each read's own list, so no comparison): the card plays
        // each the moment it is revealed, on the side whose Pokemon it is.
        if (now.inBattle) for ((species, name) in now.abilitiesRevealed) {
            val s = when (species) { f?.species -> FxEvent.Side.FOE; b?.mon?.species -> FxEvent.Side.OWN; else -> null } ?: continue
            out += FxEvent.Ability(s, name)
        }

        if (prev.inBattle && !now.inBattle && (b?.mon?.curHp ?: 0) > 0) out += FxEvent.BattleWon
        // Badges are bits, badge 1 in bit 0 (TrackerState.badges; Heart & Soul has 16): the new one is the bit that came on.
        val gained = now.badges and prev.badges.inv()
        if (gained != 0) out += FxEvent.Badge(Integer.numberOfTrailingZeros(gained), Integer.bitCount(now.badges), now.badgeSet)
        if (now.leagueBeaten && !prev.leagueBeaten) out += FxEvent.League
        return out
    }

    /** The order of a Gen 3 Pokemon's effort values (its EV substructure): HP, Attack, Defense, Speed, Sp. Atk, Sp. Def. */
    private val EV_STATS = listOf("HP", "ATK", "DEF", "SPE", "SPA", "SPD")

    private fun side(s: FxEvent.Side, hp0: Int, hp1: Int, max: Int, st0: String, st1: String,
                     stages0: Map<String, Int>, stages1: Map<String, Int>, out: MutableList<FxEvent>) {
        if (hp1 < hp0) {
            if (hp1 <= 0) out += FxEvent.Faint(s)
            else out += FxEvent.Damage(s, hp0 - hp1, max, big = max > 0 && (hp0 - hp1) * 3 >= max)
        } else if (hp1 > hp0 && hp0 > 0) out += FxEvent.Heal(s, hp1 - hp0, max)
        if (st1 != st0 && hp1 > 0) out += FxEvent.Status(s, st1, st0)
        for ((stat, v1) in stages1) {
            val v0 = stages0[stat] ?: 6
            if (v1 != v0) out += FxEvent.Stage(s, stat, v1 - v0, v1 - 6)
        }
    }
}
