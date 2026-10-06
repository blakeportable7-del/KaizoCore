package com.ironmonone.tracker

import com.ironmonone.tracker.nuzlocke.BattleEnd
import com.ironmonone.tracker.nuzlocke.Gender
import com.ironmonone.tracker.nuzlocke.Method
import com.ironmonone.tracker.nuzlocke.NuzlockeStatics
import com.ironmonone.tracker.nuzlocke.NuzlockeSystem
import com.ironmonone.tracker.nuzlocke.NzArea
import com.ironmonone.tracker.nuzlocke.NzEnemy
import com.ironmonone.tracker.nuzlocke.NzItem
import com.ironmonone.tracker.nuzlocke.NzMon
import com.ironmonone.tracker.nuzlocke.NzOpponent
import com.ironmonone.tracker.nuzlocke.Snapshot

/**
 * The Game Boy side of the Nuzlocke rules engine (2026-09-30): a [TrackerState] with [GbNuzReads] in, the engine's
 * [Snapshot] out. Pure: no memory is read here; the reads are in Gen1Tracker and GbcTracker.
 *
 * What the Game Boy games do not have, and what stands in for it:
 * - A personality value. A Pokemon's id is its original trainer id and its DVs (see [id]).
 * - Gender in Generation 1. Wedlocke pairs go by catch order there, and the panel's rules page says so.
 * - Who ran. Generation 2 calls every escape a draw, so the escape clause is for the player to apply by hand.
 */
object Gen12Nuzlocke {

    // ---- a Pokemon's id

    /**
     * The id of a Pokemon: its original trainer id (16 bits) above its DVs (16 bits). Neither ever changes, and species
     * does, on evolution, so the species is not in it. A wild Pokemon's DVs are the ones it will have once caught and its
     * trainer id will be the player's, so the id of the enemy in a battle ([enemyId]) is the id of the catch it becomes,
     * which is how a catch that went to a box is still known. Two Pokemon of one trainer with the same DVs share an id
     * (about 1 in 65,000 pairs); nothing in a run comes close to enough Pokemon for that to be likely.
     */
    fun id(otId: Int, dvs: Int): Long = ((otId and 0xFFFF).toLong() shl 16) or (dvs and 0xFFFF).toLong()

    /** The DVs of a party member as one word: attack, defense, speed and special, a nibble each (the tracker stores them as the IVs list). */
    fun dvsOf(mon: PokemonDecoder.Mon): Int =
        ((mon.ivs[1] and 0xF) shl 12) or ((mon.ivs[2] and 0xF) shl 8) or ((mon.ivs[3] and 0xF) shl 4) or (mon.ivs[4] and 0xF)

    /** The id of a party member. The tracker keeps the original trainer id in the low half of its stand-in personality value. */
    fun partyId(mon: PokemonDecoder.Mon): Long = id((mon.pid and 0xFFFF).toInt(), dvsOf(mon))

    /** The id a wild enemy will have if caught; an id nothing else has when the player's trainer id could not be read. */
    fun enemyId(g: GbNuzReads): Long =
        if (g.enemyDvs < 0) 0L else if (g.playerId < 0) (1L shl 32) or g.enemyDvs.toLong() else id(g.playerId, g.enemyDvs)

    /**
     * The id of an egg: the id the Pokemon in it will have, so the Pokemon that hatches is the egg the engine followed
     * (rc32 audit P2 #140). HatchEggs writes the player's trainer id into the egg as it hatches (pokecrystal
     * engine/pokemon/breeding.asm, MON_ID from wPlayerID), and Crystal's Odd Egg carries another one until then
     * (data/events/odd_eggs.asm), so it is the player's id above the egg's DVs; the egg's own id when the player's could
     * not be read.
     */
    fun eggId(g: GbNuzReads, egg: GbEgg): Long = id(if (g.playerId >= 0) g.playerId else egg.otId, egg.dvs)

    // ---- the games' own formulas

    /** Generation 2 shininess (pokecrystal CheckShininess): Defense, Speed and Special DVs of 10, and bit 1 of the Attack DV set. */
    fun shiny(generation: Int, dvs: Int): Boolean {
        if (generation < 2 || dvs < 0) return false
        val atk = (dvs shr 12) and 0xF; val def = (dvs shr 8) and 0xF; val spd = (dvs shr 4) and 0xF; val spc = dvs and 0xF
        return (atk and 2) != 0 && def == 10 && spd == 10 && spc == 10
    }

    /**
     * Generation 2 gender (pokecrystal GetGender): the Attack and Speed DVs together, attack the high nibble, against the
     * species' ratio byte: always male at 0, always female at 254, genderless at 255, and otherwise male when the ratio is
     * below that number. Generation 1 has no gender, and a species whose ratio is unknown has none here either.
     */
    fun gender(generation: Int, ratio: Int?, dvs: Int): Gender? {
        if (generation < 2 || ratio == null || ratio !in 0..255 || dvs < 0) return null
        if (ratio == 255) return null
        if (ratio == 0) return Gender.MALE
        if (ratio == 254) return Gender.FEMALE
        val b = (((dvs shr 12) and 0xF) shl 4) or ((dvs shr 4) and 0xF)
        return if (ratio < b) Gender.MALE else Gender.FEMALE
    }

    // ---- the battle

    /** Generation 2's wBattleType values that matter (pokecrystal constants/battle_constants.asm). */
    private const val TUTORIAL = 3; private const val FISH = 4; private const val ROAMING = 5; private const val CONTEST = 6
    private const val FORCE_SHINY = 7; private const val TREE = 8; private const val TRAP = 9; private const val FORCE_ITEM = 10
    private const val CELEBI = 11; private const val SUICUNE = 12

    /** Generation 1's: the Old Man's lesson is not an encounter, and neither is Yellow's opening battle with Pikachu (BATTLE_TYPE_PIKACHU). */
    private const val OLD_MAN = 1
    private const val PIKACHU_OPENING = 4

    /** A battle that is a lesson or a ghost: not an encounter of the area, whatever its Pokemon. */
    fun notAnEncounter(g: GbNuzReads): Boolean =
        if (g.generation == 1) g.ghost || g.battleType == OLD_MAN || (g.game == "y" && g.battleType == PIKACHU_OPENING)
        else g.battleType == TUTORIAL

    /**
     * How the wild battle began. Generation 2 says so in its battle type (fishing, a headbutt tree, and every kind of set
     * battle: a roamer, the contest, a forced shiny, the Team Rocket traps, Celebi, Suicune); the rest are the map and
     * level in the statics table (and, for the few rows that name one, the species). Generation 1 only knows the player
     * is surfing.
     */
    fun method(g: GbNuzReads, enemyLevel: Int, enemySpecies: Int? = null): Method {
        if (g.generation == 2) when (g.battleType) {
            FISH -> return Method.ROD
            TREE -> return Method.HEADBUTT
            ROAMING, CONTEST, FORCE_SHINY, TRAP, FORCE_ITEM, CELEBI, SUICUNE -> return Method.STATIC
        }
        val system = if (g.generation == 1) NuzlockeSystem.GEN1 else NuzlockeSystem.GEN2
        if (NuzlockeStatics.isStatic(system, g.gameKeys, g.place, enemyLevel, enemySpecies)) return Method.STATIC
        return if (g.surfing) Method.SURF else Method.WALK
    }

    /**
     * How the last battle ended, from what the game leaves behind, and what the tracker saw of the battle.
     *
     * Generation 1: wBattleResult is 0 for a win, 1 for a loss and 2 both when the player ran and when a ball caught the
     * Pokemon, so a catch is told by wCapturedMonSpecies having been set. A wild Pokemon that ran, or Teleport, Roar,
     * Whirlwind and the Poke Doll, leave it at 0: the enemy's HP at its last look says whether it fainted. The escape flag
     * is set by the wild Pokemon's own Teleport, Roar or Whirlwind as well as by yours ([enemyLeft]): only yours is a run.
     * Generation 2: the low two bits are 0 a win, 1 a loss, 2 a draw, and a draw is how every escape ends. A catch leaves a
     * win, and wWildMon having been set (the flag [GbNuzReads.captured] latches); a tracker that missed the flag falls back
     * on the wild Pokemon having been standing at its last look.
     */
    /** TELEPORT, ROAR and WHIRLWIND (pokered constants/move_constants.asm). */
    private val LEAVING_MOVES = setOf(0x64, 0x2E, 0x12)

    /**
     * pokered and pokeyellow engine/battle/effects.asm, SwitchAndTeleportEffect: the enemy's successful Teleport, Roar or
     * Whirlwind sets wEscapedFromBattle exactly as the player's does, so on its own the flag read as "you ran" and the
     * escape clause never applied to a wild Abra that teleported (rc33 audit P1 #75). It was the wild Pokemon that left
     * when its move ([enemyMove], wEnemyMoveNum) is one of the three and yours ([playerMove], wPlayerMoveNum) is not.
     */
    fun enemyLeft(enemyMove: Int, playerMove: Int): Boolean = enemyMove in LEAVING_MOVES && playerMove !in LEAVING_MOVES

    fun battleEnd(g: GbNuzReads): BattleEnd {
        val r = g.battleResult
        if (r < 0) return BattleEnd.UNKNOWN
        if (g.generation == 1) return when {
            r == 1 -> BattleEnd.LOST
            !g.lastWild -> if (r == 0) BattleEnd.WON else BattleEnd.UNKNOWN
            g.captured -> BattleEnd.CAUGHT
            r == 2 -> BattleEnd.RAN
            r != 0 -> BattleEnd.UNKNOWN
            g.escaped -> if (g.enemyFled) BattleEnd.MON_FLED else BattleEnd.RAN
            g.enemyHpLast == 0 -> BattleEnd.WON
            g.enemyHpLast > 0 -> BattleEnd.MON_FLED
            else -> BattleEnd.UNKNOWN
        }
        return when (r and 3) {
            1 -> BattleEnd.LOST
            2 -> if (g.lastWild) BattleEnd.RAN else BattleEnd.DREW
            0 -> when {
                !g.lastWild -> BattleEnd.WON
                g.captured -> BattleEnd.CAUGHT
                g.enemyHpLast == 0 -> BattleEnd.WON
                g.enemyHpLast > 0 -> BattleEnd.CAUGHT
                else -> BattleEnd.UNKNOWN
            }
            else -> BattleEnd.UNKNOWN
        }
    }

    private fun types(a: Int, b: Int): List<Int> = if (a == b) listOf(a) else listOf(a, b)

    /**
     * The party's name as the ledger reads it: the game's default name is the species' own. A male Nidoran left unnamed
     * is NIDORAN and the male sign (pokered data/pokemon/names.asm:5, pokecrystal :34), which GbText reads NIDORAN, while
     * gen2/species.tsv calls dex 32 NIDORAN M: so the ledger called it nicknamed and never asked for a name. A name that
     * is the species' own without its " M" or " F" is given as the species name, which counts as no nickname (rc32 audit
     * P3 #106).
     */
    internal fun nickname(decoded: String, speciesName: String): String {
        val plain = speciesName.removeSuffix(" M").removeSuffix(" F")
        return if (plain != speciesName && decoded.equals(plain, ignoreCase = true)) speciesName else decoded
    }

    /** The engine's view of this state, or null when it has none to give: no Game Boy reads, or a failed read. */
    fun snapshot(s: TrackerState): Snapshot? {
        val g = s.nuz?.gb ?: return null
        if (s.unreadable) return null
        val gen = g.generation
        val party = s.party.mapIndexed { i, p ->
            val dvs = dvsOf(p.mon)
            NzMon(
                id = partyId(p.mon), species = p.mon.species, speciesName = p.speciesName,
                nickname = nickname(g.nicknames.getOrNull(i) ?: "", p.speciesName),
                level = p.mon.level, hp = p.mon.curHp, maxHp = p.mon.maxHp, isEgg = false,
                gender = gender(gen, p.base?.genderRatio, dvs),
                types = p.base?.let { types(it.type1, it.type2) } ?: emptyList(),
                shiny = shiny(gen, dvs),
                moveTypes = Gen3Nuzlocke.damagingTypes(p.moveRows),
                stats = listOf(p.mon.maxHp, p.mon.atk, p.mon.def, p.mon.spe, p.mon.spAtk, p.mon.spDef),
            )
        } + g.eggs.map { e ->
            // The party's eggs, which the tracker's party leaves out: the engine notes where each joined, and gives the
            // Pokemon that hatches to that place (rc32 audit P2 #140).
            NzMon(
                id = eggId(g, e), species = e.species, speciesName = "EGG", nickname = "", level = e.level,
                hp = 0, maxHp = 0, isEgg = true, gender = null, types = emptyList(), shiny = false,
            )
        }
        val wild = s.isWildBattle
        val enemy = s.enemy?.let { e ->
            NzEnemy(
                id = if (wild) enemyId(g) else 0L, species = e.species, speciesName = e.speciesName, level = e.level,
                hp = e.curHp, maxHp = e.maxHp,
                gender = if (wild) gender(gen, e.base?.genderRatio, g.enemyDvs) else null,
                types = types(e.type1, e.type2),
                shiny = wild && shiny(gen, g.enemyDvs),
            )
        }
        return Snapshot(
            readable = true,
            area = NzArea(g.place, s.mapId, g.detail),
            inBattle = s.inBattle,
            wild = wild,
            ghost = s.inBattle && notAnEncounter(g),
            method = if (wild && enemy != null) method(g, enemy.level, enemy.species) else Method.WALK,
            enemy = enemy,
            opponent = g.opponent?.let { NzOpponent(it.trainerId, it.label, it.group, it.bossKey, it.maxLevel) },
            end = if (s.inBattle) BattleEnd.UNKNOWN else battleEnd(g),
            turn = g.turn.takeIf { it >= 0 },
            party = party,
            // The game's count when it says more: a slot that failed to decode for one read ends the party list, and the
            // engine would take the short list as whole, box the rest and, with the lead fainted, log a whiteout (rc32
            // audit P3 #111). TrackerState.partyCount stays the list's size, which the panel's no-party card reads. The
            // eggs are in [party] here, so their slots are in the count: an egg that did not read is a read that is not whole.
            partyCount = maxOf(s.partyCount, g.partyCount) + g.eggSlots,
            badges = s.badges,
            ballCount = g.ballCount.takeIf { it >= 0 },
            bag = g.bag?.mapValues { NzItem(it.value.name, it.value.qty) },
            battleStyleSet = g.battleStyleSet,
            caps = g.caps,
            beaten = g.caps?.beatenByBadges(s.badges) ?: emptySet(),
            scout = s.nuz?.scout,
        )
    }
}
