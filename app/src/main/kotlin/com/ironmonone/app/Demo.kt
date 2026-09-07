package com.ironmonone.app

import com.ironmonone.tracker.EnemyInfo
import com.ironmonone.tracker.GameOver
import com.ironmonone.tracker.GbaTracker
import com.ironmonone.tracker.GbcTracker
import com.ironmonone.tracker.Gen1Tracker
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
 * with modes gba-battle, gba-over, gb-battle, gb-over (Gen 1 and 2), nds-battle,
 * nds-over (Gen 4 and 5, picked from the game's map). While set, the
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

    private fun gen3Mon(species: Int, level: Int, hp: Int, maxHp: Int, moves: List<Int>, pp: List<Int>, item: Int, abilitySlot: Int, nature: Int, st: IntArray) =
        PokemonDecoder.Mon(
            pid = 0x2A6B41C7L, level = level, nickname = "", species = species, heldItem = item, friendship = 120,
            moves = moves, pp = pp, ivs = listOf(27, 31, 14, 30, 9, 22), evs = listOf(40, 62, 18, 55, 12, 20), ppUps = listOf(0, 0, 0, 0),
            abilitySlot = abilitySlot, nature = nature, shiny = false, status = 0, curHp = hp, maxHp = maxHp,
            atk = st[0], def = st[1], spe = st[2], spAtk = st[3], spDef = st[4],
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
        // Scyther 123: Wing Attack 17, Slash 163, Swords Dance 14, Pursuit 228. Oran Berry is item 139 (Cheri is 133).
        val scyther = gen3Mon(123, 25, 61, 78, listOf(17, 163, 14, 228), listOf(31, 20, 18, 20), 139, 0, 3, intArrayOf(66, 50, 63, 38, 50))
        val party = listOf(tracked(t, scyther, 6, 11, 29))
        if (mode == "gba-over") return TrackerState(
            partyCount = 1, party = listOf(tracked(t, scyther.copy(curHp = 0), 6, 11, 29)),
            inBattle = false, isWildBattle = false, badges = 0b11, badgeSet = "RSE",
            healPercent = 0, healCount = 0, routeName = "Mauville City", steps = 18422, gameOver = GameOver.LOST,
        )
        // Magneton 82, level 22 in Emerald: Sonic Boom 49 and Thunder Wave 86 seen so far.
        val magneton = gen3Mon(82, 22, 44, 63, listOf(49, 86, 84, 48), listOf(20, 20, 30, 40), 0, 0, 0, intArrayOf(45, 50, 38, 58, 40))
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


    /** Red, Blue, Yellow: Lt. Surge's gym. A Nidoking against his Raichu. Gen 1 has no items or abilities. */
    fun gb1(t: Gen1Tracker, mode: String): TrackerState {
        // Nidoking 34: Horn Attack 30, Poison Sting 40, Double Kick 24, Focus Energy 116. Raichu 26, level 24 in Red.
        val nido = gen3Mon(34, 22, 58, 71, listOf(30, 40, 24, 116), listOf(21, 30, 26, 30), 0, 0, 0, intArrayOf(52, 43, 48, 41, 41))
        val party = listOf(t.trackedOf(nido))
        if (mode == "gb-over") return TrackerState(
            partyCount = 1, party = listOf(t.trackedOf(nido.copy(curHp = 0))), inBattle = false, isWildBattle = false,
            badges = 0b11, badgeSet = "RBY", gameOver = GameOver.LOST,
        )
        val rb = t.baseStats(26)
        val enemy = EnemyInfo(
            species = 26, speciesName = t.speciesName(26), level = 24, curHp = 47, maxHp = 66,
            type1 = rb?.type1 ?: 13, type2 = rb?.type2 ?: 13, base = rb,
            movesSeen = listOf(t.moveRowOf(84, 30, null).name, t.moveRowOf(86, 20, null).name),   // Thundershock, Thunder Wave
            moveRows = listOf(t.moveRowOf(84, 30, null), t.moveRowOf(86, 20, null)),
        )
        return TrackerState(
            partyCount = 1, party = party, inBattle = true, isWildBattle = false,
            enemyTeam = listOf(false, false, true), enemy = enemy,
            badges = 0b11, badgeSet = "RBY", healPercent = 44, healCount = 3,
        )
    }

    /** Gold, Silver, Crystal: Whitney's gym, the third badge, so two are held. An Ampharos against her Miltank. */
    fun gb2(t: GbcTracker, mode: String): TrackerState {
        // Ampharos 181: ThunderPunch 9, Cotton Spore 178, Thunder Wave 86, Headbutt 29. Miltank 241, level 20.
        val amph = gen3Mon(181, 27, 74, 96, listOf(9, 178, 86, 29), listOf(15, 40, 20, 15), 0, 0, 0, intArrayOf(55, 52, 39, 71, 60))
        val party = listOf(t.trackedOf(amph))
        if (mode == "gb-over") return TrackerState(
            partyCount = 1, party = listOf(t.trackedOf(amph.copy(curHp = 0))), inBattle = false, isWildBattle = false,
            badges = 0b11, badgeSet = "GSC", gameOver = GameOver.LOST,
        )
        val mb = t.baseStats(241)
        val enemy = EnemyInfo(
            species = 241, speciesName = t.speciesName(241), level = 20, curHp = 61, maxHp = 78,
            type1 = mb?.type1 ?: 0, type2 = mb?.type2 ?: 0, base = mb,
            movesSeen = listOf(t.moveName(205), t.moveName(23)),   // Rollout, Stomp
            moveRows = listOf(t.moveRowOf(205, 20, null), t.moveRowOf(23, 20, null)),
        )
        return TrackerState(
            partyCount = 1, party = party, inBattle = true, isWildBattle = false,
            enemyTeam = listOf(false, true), enemy = enemy,
            badges = 0b11, badgeSet = "GSC", healPercent = 58, healCount = 5,
        )
    }

    private fun gen4Mon(species: Int, level: Int, hp: Int, maxHp: Int, moves: List<Int>, pp: List<Int>, item: Int, ability: Int, st: IntArray) = Gen4.Mon(
        pid = 0x5C19E2A4L, species = species, heldItem = item, abilityId = ability, level = level, curHp = hp, maxHp = maxHp,
        atk = st[0], def = st[1], spe = st[2], spAtk = st[3], spDef = st[4], moves = moves, pp = pp, ppUps = listOf(0, 0, 0, 0),
        ivs = listOf(30, 24, 12, 31, 8, 19), shiny = false, nature = 13, isEgg = false,
    )

    /** HeartGold, SoulSilver: Whitney's gym, the third badge, so two are held. A Lucario against her Miltank. */
    private fun nds4(t: NdsTracker, mode: String): NdsTrackerState {
        // Lucario 448 (Steadfast): Force Palm 395, Bone Rush 198, Metal Claw 232, Quick Attack 98. Miltank 241, level 19 in HGSS.
        val luca = gen4Mon(448, 30, 79, 98, listOf(395, 198, 232, 98), listOf(10, 10, 35, 30), 0, 80, intArrayOf(79, 55, 66, 82, 55))
        val lMoves = listOf(
            NdsMoveInfo("Force Palm", 60, 100, "FIGHTING", 10, "PHY"), NdsMoveInfo("Bone Rush", 25, 80, "GROUND", 10, "PHY"),
            NdsMoveInfo("Metal Claw", 50, 95, "STEEL", 35, "PHY"), NdsMoveInfo("Quick Attack", 40, 100, "NORMAL", 30, "PHY"),
        )
        val l = NdsTrackedMon(
            mon = luca, speciesName = t.speciesName(448),
            info = NdsSpeciesInfo("Lucario", "FIGHTING", "STEEL", 525, "Steadfast", "Inner Focus"),
            abilityName = "Steadfast", itemName = "-", moves = lMoves, movesLearned = 9, movesTotal = 16, nextMoveLevel = 33,
        )
        if (mode == "nds-over") return NdsTrackerState(
            partyCount = 1, party = listOf(l.copy(mon = luca.copy(curHp = 0))), located = true,
            resolvedBase = 0x0227C2F4L, badges = 0b11, badgeSet = "HGSS", runOver = NdsRunOver.STANDARD,
        )
        val milt = gen4Mon(241, 19, 61, 78, listOf(205, 23, 111, 45), listOf(20, 20, 40, 40), 0, 47, intArrayOf(38, 45, 44, 22, 31))
        val enemy = NdsTrackedMon(
            mon = milt, speciesName = t.speciesName(241),
            info = NdsSpeciesInfo("Miltank", "NORMAL", "NORMAL", 490, "Thick Fat", "Scrappy"),
            abilityName = "?", itemName = "-",
            moves = listOf(NdsMoveInfo("Rollout", 30, 90, "ROCK", 20, "PHY"), NdsMoveInfo("Stomp", 65, 100, "NORMAL", 20, "PHY")),
        )
        return NdsTrackerState(
            partyCount = 1, party = listOf(l), located = true, inBattle = true, isWildBattle = false, enemy = enemy,
            resolvedBase = 0x0227C2F4L, badges = 0b11, badgeSet = "HGSS", healPercent = 50, healCount = 3,
        )
    }

    /** Black 2, Burgh's gym: a Zangoose against his Leavanny. A Gen 4 game gets Whitney instead. */
    fun nds(t: NdsTracker, mode: String): NdsTrackerState {
        if (t.map.generation == 4) return nds4(t, mode)
        // Zangoose 335 (Immunity), Leavanny 542 (Swarm / Chlorophyll); B2W2 Burgh's Leavanny is level 24.
        val zangoose = gen4Mon(335, 20, 48, 67, listOf(163, 24, 44, 372), listOf(20, 30, 25, 20), 0, 17, intArrayOf(55, 33, 44, 33, 33))
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
        val leavanny = gen4Mon(542, 24, 58, 72, listOf(206, 210, 332, 400), listOf(25, 30, 20, 15), 0, 68, intArrayOf(58, 50, 52, 41, 50))
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
