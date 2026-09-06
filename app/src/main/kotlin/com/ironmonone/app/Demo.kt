package com.ironmonone.app

import com.ironmonone.tracker.EnemyInfo
import com.ironmonone.tracker.GameOver
import com.ironmonone.tracker.GbaTracker
import com.ironmonone.tracker.PokemonDecoder
import com.ironmonone.tracker.TrackedMon
import com.ironmonone.tracker.TrackerState
import com.ironmonone.tracker.nds.Gen4
import com.ironmonone.tracker.nds.NdsMoveInfo
import com.ironmonone.tracker.nds.NdsRunOver
import com.ironmonone.tracker.nds.NdsSpeciesInfo
import com.ironmonone.tracker.nds.NdsTrackedMon
import com.ironmonone.tracker.nds.NdsTracker
import com.ironmonone.tracker.nds.NdsTrackerState

/**
 * Screenshot mode for the website. Reached only from adb:
 *
 *   am start -n com.ironmonone.app/.MainActivity --es demo gba-battle
 *
 * with modes gba-battle, gba-over, nds-battle, nds-over. While set, the
 * Play screen shows a staged run state on the tracker panel instead of what
 * the emulator's memory says. Names, base stats, types, move power and
 * accuracy still come from the ROM that is running (through the tracker's
 * own lookups), so the card is the real card for those Pokemon; only the
 * fact that they are in the party is staged. Nothing else in the app reads
 * this, and a normal launch never sets it.
 */
object Demo {
    @Volatile var mode: String? = null
    /** The attempt number the overlay and the game-over card show while staged. */
    const val ATTEMPT = 37

    private fun gen3Mon(species: Int, level: Int, hp: Int, maxHp: Int, moves: List<Int>, pp: List<Int>, item: Int, abilitySlot: Int, nature: Int) =
        PokemonDecoder.Mon(
            pid = 0x2A6B41C7L, level = level, nickname = "", species = species, heldItem = item, friendship = 120,
            moves = moves, pp = pp, ivs = listOf(27, 31, 14, 30, 9, 22), evs = listOf(40, 62, 18, 55, 12, 20), ppUps = listOf(0, 0, 0, 0),
            abilitySlot = abilitySlot, nature = nature, shiny = false, status = 0, curHp = hp, maxHp = maxHp,
            atk = 82, def = 56, spe = 79, spAtk = 41, spDef = 57,
        )

    private fun tracked(t: GbaTracker, mon: PokemonDecoder.Mon, learned: Int, total: Int, next: Int?, status: String = ""): TrackedMon {
        val base = t.baseStats(mon.species)
        return TrackedMon(
            mon = mon, speciesName = t.speciesName(mon.species), moveNames = mon.moves.map { t.moveName(it) }, base = base,
            abilityName = t.abilityNameOf(mon, base), itemName = if (mon.heldItem == 0) "-" else t.itemName(mon.heldItem),
            moveRows = t.moveRowsOf(mon), movesLearned = learned, movesTotal = total, nextMoveLevel = next, statusCondition = status,
        )
    }

    /** Emerald, Wattson's gym: a Scyther against the third of his four. */
    fun gba(t: GbaTracker, mode: String): TrackerState {
        // Scyther 123: Wing Attack 17, Slash 163, Swords Dance 14, Pursuit 228. Oran Berry is item 133.
        val scyther = gen3Mon(123, 25, 61, 78, listOf(17, 163, 14, 228), listOf(31, 20, 18, 20), 133, 0, 3)
        val party = listOf(tracked(t, scyther, 6, 11, 29))
        if (mode == "gba-over") return TrackerState(
            partyCount = 1, party = listOf(tracked(t, scyther.copy(curHp = 0), 6, 11, 29)),
            inBattle = false, isWildBattle = false, badges = 0b11, badgeSet = "RSE",
            healPercent = 0, healCount = 0, routeName = "Mauville City", steps = 18422, gameOver = GameOver.LOST,
        )
        // Magneton 82, level 22 in Emerald: Sonic Boom 49 and Thunder Wave 86 seen so far.
        val magneton = gen3Mon(82, 22, 44, 63, listOf(49, 86, 84, 48), listOf(20, 20, 30, 40), 0, 0, 0)
        val eb = t.baseStats(82)
        val enemy = EnemyInfo(
            species = 82, speciesName = t.speciesName(82), level = 22, curHp = 44, maxHp = 63,
            type1 = eb?.type1 ?: 13, type2 = eb?.type2 ?: 8, base = eb,
            movesSeen = listOf(t.moveName(49), t.moveName(86)), moveRows = t.moveRowsOf(magneton).take(2),
            abilityGuess = eb?.let { b -> listOf(b.ability1, b.ability2).filter { it != 0 }.joinToString("/") { t.abilityName(it) } } ?: "?",
        )
        return TrackerState(
            partyCount = 1, party = party, inBattle = true, isWildBattle = false,
            enemyTeam = listOf(false, false, true, true), enemy = enemy,
            badges = 0b11, badgeSet = "RSE", healPercent = 62, healCount = 4,
            routeName = "Mauville City", steps = 18422,
        )
    }

    private fun gen4Mon(species: Int, level: Int, hp: Int, maxHp: Int, moves: List<Int>, pp: List<Int>, item: Int, ability: Int) = Gen4.Mon(
        pid = 0x5C19E2A4L, species = species, heldItem = item, abilityId = ability, level = level, curHp = hp, maxHp = maxHp,
        atk = 71, def = 44, spe = 58, spAtk = 39, spDef = 43, moves = moves, pp = pp, ppUps = listOf(0, 0, 0, 0),
        ivs = listOf(30, 24, 12, 31, 8, 19), shiny = false, nature = 13, isEgg = false,
    )

    /** Black 2, Burgh's gym: a Zangoose against his Leavanny. */
    fun nds(t: NdsTracker, mode: String): NdsTrackerState {
        // Zangoose 335 (Immunity), Leavanny 542 (Swarm / Chlorophyll); B2W2 Burgh's Leavanny is level 24.
        val zangoose = gen4Mon(335, 20, 48, 67, listOf(163, 24, 44, 372), listOf(20, 30, 25, 20), 0, 17)
        val zMoves = listOf(
            NdsMoveInfo("Slash", 70, 100, "NORMAL", 20, "PHY"), NdsMoveInfo("Double Kick", 30, 100, "FIGHTING", 30, "PHY"),
            NdsMoveInfo("Bite", 60, 100, "DARK", 25, "PHY"), NdsMoveInfo("Assurance", 60, 100, "DARK", 10, "PHY"),
        )
        val z = NdsTrackedMon(
            mon = zangoose, speciesName = t.speciesName(335),
            info = NdsSpeciesInfo("Zangoose", "NORMAL", "NORMAL", 458, "Immunity", "Toxic Boost"),
            abilityName = "Immunity", itemName = "-", moves = zMoves, movesLearned = 7, movesTotal = 14, nextMoveLevel = 22,
        )
        if (mode == "nds-over") return NdsTrackerState(
            partyCount = 1, party = listOf(z.copy(mon = zangoose.copy(curHp = 0))), located = true,
            resolvedBase = 0x0221D3B4L, badges = 0b11, badgeSet = "B2W2", runOver = NdsRunOver.STANDARD,
        )
        val leavanny = gen4Mon(542, 24, 58, 72, listOf(206, 210, 332, 400), listOf(25, 30, 20, 15), 0, 68)
        val enemy = NdsTrackedMon(
            mon = leavanny, speciesName = t.speciesName(542),
            info = NdsSpeciesInfo("Leavanny", "BUG", "GRASS", 500, "Swarm", "Chlorophyll"),
            abilityName = "?", itemName = "-",
            moves = listOf(NdsMoveInfo("Razor Leaf", 55, 95, "GRASS", 25, "PHY"), NdsMoveInfo("Struggle Bug", 30, 100, "BUG", 20, "SPE")),
        )
        return NdsTrackerState(
            partyCount = 1, party = listOf(z), located = true, inBattle = true, isWildBattle = false, enemy = enemy,
            resolvedBase = 0x0221D3B4L, badges = 0b11, badgeSet = "B2W2", healPercent = 55, healCount = 3,
        )
    }
}
