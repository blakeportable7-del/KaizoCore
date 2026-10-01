package com.ironmonone.app

import com.ironmonone.tracker.EnemyInfo
import com.ironmonone.tracker.GameOver
import com.ironmonone.tracker.GbNuzReads
import com.ironmonone.tracker.GbaTracker
import com.ironmonone.tracker.GbcTracker
import com.ironmonone.tracker.Gen1Tracker
import com.ironmonone.tracker.NuzlockeReads
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
 * with modes gba-battle, gba-over, gba-wild, gb-battle, gb-over (Gen 1 and 2), nds-battle, nds-wild,
 * nds-over (Gen 4 and 5, picked from the game's map), and the Nuzlocke scripts gba-nuz, gb-nuz and nds-nuz. While set, the
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

    /**
     * The death card the staged game-over popup shows, one per family: a trainer loss short of
     * the best on the GBA, a new best on the DS, so both kinds of line can be checked.
     */
    fun deathCard(attempt: Int): DeathCard {
        val m = mode.orEmpty()
        fun rec(a: Int, badges: Int, secs: Int, killer: RunRecord.Mon?, trainer: String, place: String) = RunRecord(
            attempt = a, seed = "5261db990e333467", ruleset = "Kaizo.rnqs", started = 0L, ended = 0L, playSeconds = secs,
            outcome = RunRecord.Outcome.LOST, badges = badges, lead = null, killer = killer, trainer = trainer, location = place,
        )
        return when {
            m.startsWith("nds") -> DeathCard("Pokemon HeartGold", rec(attempt, 2, 4360, RunRecord.Mon(241, "Miltank", 19), "Leader Whitney", "Goldenrod City"),
                rec(attempt - 5, 1, 2210, RunRecord.Mon(95, "Onix", 12), "Leader Falkner", "Violet City"), earlier = false)
            m.startsWith("gb-") -> DeathCard("Pokemon Red", rec(attempt, 0, 1530, RunRecord.Mon(95, "Onix", 12), "LEADER BROCK", "Pewter City"),
                rec(attempt - 3, 1, 2890, null, "", "Route 4"), earlier = false)
            else -> DeathCard("Pokemon Emerald", rec(attempt, 2, 6500, RunRecord.Mon(82, "Magneton", 22), "LEADER WATTSON", "Mauville City"),
                rec(attempt - 6, 3, 9120, null, "", "Route 119"), earlier = false)
        }
    }

    /** When the gba-nuz script started: its first poll. */
    private var nuzStart = 0L

    /**
     * gba-nuz: a stage every 7 s from the first poll, holding on the last. 0 Route 101, nothing met yet; 1 a wild
     * Zigzagoon; 2 caught, it joins the party; 3 Route 102; 4 Treecko faints against a wild Wurmple; 5 the battle is
     * won. The species are Emerald's internal numbers (Treecko 277, Zigzagoon 288, Wurmple 290) and the names come
     * from whichever GBA game is loaded, as the other staged states do.
     */
    private fun nuzStage(t: GbaTracker): TrackerState {
        val now = android.os.SystemClock.elapsedRealtime()
        if (nuzStart == 0L) nuzStart = now
        val stage = ((now - nuzStart) / 7_000L).toInt().coerceAtMost(5)
        val treecko = gen3Mon(277, 7, if (stage >= 4) 0 else 24, 24, listOf(1, 43, 0, 0), listOf(35, 30, 0, 0), 0, 0, 3, intArrayOf(12, 11, 16, 13, 12))
            .copy(pid = 0x1D2C3B4AL)
        val zigzagoon = gen3Mon(288, 3, 13, 13, listOf(33, 0, 0, 0), listOf(35, 0, 0, 0), 0, 0, 5, intArrayOf(7, 7, 9, 6, 7))
            .copy(pid = 0x5E6F7081L)
        fun wild(species: Int, level: Int, hp: Int, pid: Long): EnemyInfo {
            val b = t.baseStats(species)
            return EnemyInfo(
                species = species, speciesName = t.speciesName(species), level = level, curHp = hp, maxHp = hp,
                type1 = b?.type1 ?: 0, type2 = b?.type2 ?: 0, base = b, movesSeen = emptyList(), pid = pid,
            )
        }
        val party = if (stage >= 2) listOf(tracked(t, treecko), tracked(t, zigzagoon)) else listOf(tracked(t, treecko))
        val inBattle = stage == 1 || stage == 4
        val enemy = when (stage) { 1 -> wild(288, 3, 13, 0x5E6F7081L); 4 -> wild(290, 3, 12, 0x92A3B4C5L); else -> null }
        return TrackerState(
            partyCount = party.size, party = party, inBattle = inBattle, isWildBattle = inBattle, enemy = enemy,
            badgeSet = "RSE", routeName = if (stage >= 3) "Route 102" else "Route 101", mapId = if (stage >= 3) 17 else 16,
            encounterArea = if (inBattle) "Land" else null,
            // gBattleOutcome as the game leaves it: 7 caught, 1 won; 0 while a battle is on.
            nuz = com.ironmonone.tracker.NuzlockeReads(
                battleOutcome = when (stage) { 2, 3 -> 7; 5 -> 1; else -> 0 }, ballCount = 5, turn = if (inBattle) 1 else -1,
            ),
        )
    }

    private fun gen3Mon(species: Int, level: Int, hp: Int, maxHp: Int, moves: List<Int>, pp: List<Int>, item: Int, abilitySlot: Int, nature: Int, st: IntArray) =
        PokemonDecoder.Mon(
            pid = 0x2A6B41C7L, level = level, nickname = "", species = species, heldItem = item, friendship = 120,
            moves = moves, pp = pp, ivs = listOf(27, 31, 14, 30, 9, 22), evs = listOf(40, 62, 18, 55, 12, 20), ppUps = listOf(0, 0, 0, 0),
            abilitySlot = abilitySlot, nature = nature, shiny = false, status = 0, curHp = hp, maxHp = maxHp,
            atk = st[0], def = st[1], spe = st[2], spAtk = st[3], spDef = st[4],
        )

    /** When the gb-nuz and nds-nuz scripts started: their first polls. */
    private var gbNuzStart = 0L
    private var ndsNuzStart = 0L

    /** The original trainer id every Pokemon the gb-nuz player catches carries. */
    private const val GB_TRAINER_ID = 0x2B0E

    /** The DVs of the Pokemon the script catches, the wild one's and the party member's alike (attack 3 and speed 10: a female against most ratios). */
    private const val GB_FRIEND_DVS = 0x35A6

    /** A stage every 7 s from [start], holding on [last]. */
    private fun nuzStageOf(start: Long, last: Int): Int =
        ((android.os.SystemClock.elapsedRealtime() - start) / 7_000L).toInt().coerceIn(0, last)

    /**
     * A Game Boy Pokemon as the tracker decodes one: the stand-in personality value holds the species above the original
     * trainer id, and the DVs are in the IVs, which is what the Nuzlocke ledger makes a Pokemon's id from.
     */
    private fun gbMon(species: Int, level: Int, hp: Int, maxHp: Int, moves: List<Int>, dvs: Int, st: IntArray) = PokemonDecoder.Mon(
        pid = (species.toLong() shl 16) or GB_TRAINER_ID.toLong(), level = level, nickname = "", species = species, heldItem = 0, friendship = 70,
        moves = moves, pp = moves.map { if (it == 0) 0 else 30 },
        ivs = listOf(0, (dvs shr 12) and 15, (dvs shr 8) and 15, (dvs shr 4) and 15, dvs and 15, dvs and 15), evs = List(6) { 0 },
        ppUps = listOf(0, 0, 0, 0), abilitySlot = 0, nature = 0, shiny = false, status = 0, curHp = hp, maxHp = maxHp,
        atk = st[0], def = st[1], spe = st[2], spAtk = st[3], spDef = st[4],
    )

    /** What one gb-nuz stage holds, for either Game Boy family. */
    private class GbNuz(
        val stage: Int, val place: String, val party: List<PokemonDecoder.Mon>,
        val wild: Boolean, val wildSpecies: Int, val wildDvs: Int, val wildHp: Int,
    )

    /**
     * gb-nuz: a stage every 7 s from the first poll, holding on the last, the way a real run goes. 0 the first route,
     * nothing met yet; 1 a wild Pokemon; 2 the ball catches it, and it is in the party while the battle is still on;
     * 3 the battle is over; 4 the second route; 5 a wild Pokemon, and the starter faints; 6 the Pokemon that was caught
     * wins the battle. The species are national numbers, which both Game Boy families use; the names, types and gender
     * ratios come from the loaded game's own ROM, and so do the place keys and the level caps, through a real read of
     * the running game (gbNuzReads), as the other staged states take theirs.
     */
    private fun gbNuz(generation: Int, stage: Int): GbNuz {
        val starter = if (generation == 1) 4 else 155          // Charmander, Cyndaquil
        val caught = if (generation == 1) 16 else 161          // Pidgey, Sentret
        val second = if (generation == 1) 19 else 16           // Rattata, Pidgey
        val hero = gbMon(starter, 7, if (stage >= 5) 0 else 24, 24, listOf(10, 45, 0, 0), 0x7777, intArrayOf(14, 12, 13, 12, 12))
        val friend = gbMon(caught, 3, 13, 13, listOf(33, 0, 0, 0), GB_FRIEND_DVS, intArrayOf(7, 7, 9, 6, 7))
        val place = if (stage >= 4) (if (generation == 1) "Route 2" else "Route 30") else (if (generation == 1) "Route 1" else "Route 29")
        return GbNuz(
            stage, place, if (stage in 2..6) listOf(hero, friend) else listOf(hero),
            wild = stage == 1 || stage == 2 || stage == 5, wildSpecies = if (stage <= 2) caught else second,
            wildDvs = if (stage <= 2) GB_FRIEND_DVS else 0x1234, wildHp = when (stage) { 1 -> 13; 2 -> 6; else -> 9 },
        )
    }

    /**
     * What a Game Boy game leaves for the ledger at a gb-nuz stage: what a tracker would have read from its memory. The
     * catch is Generation 1's 2 (ran or caught) with the ball's flag, and Generation 2's win with the wild Pokemon still
     * standing; both persist until the next battle. The win at the end leaves the wild Pokemon at 0 HP. The place keys and
     * the caps are the running game's own, when it can be read.
     */
    private fun gbNuzReads(generation: Int, real: GbNuzReads?, n: GbNuz): GbNuzReads {
        val stage = n.stage
        val afterCatch = stage in 2..4
        return GbNuzReads(
            generation = generation, game = real?.game ?: if (generation == 1) "rb" else "c",
            gameKeys = real?.gameKeys ?: if (generation == 1) listOf("rb") else listOf("c"),
            place = n.place, detail = n.place, playerId = GB_TRAINER_ID,
            enemyDvs = if (n.wild) n.wildDvs else -1, enemyHpLast = when (stage) { 0 -> -1; 1 -> 13; 2, 3, 4 -> 6; 5 -> 9; else -> 0 },
            lastWild = true, battleResult = if (afterCatch && generation == 1) 2 else 0,
            captured = generation == 1 && afterCatch,
            ballCount = 5, turn = if (n.wild) 1 else -1, battleStyleSet = false,
            caps = real?.caps, nicknames = listOf("EMBER", "PIP").take(n.party.size),
        )
    }

    private fun gbNuzEnemy(n: GbNuz, name: (Int) -> String, base: (Int) -> com.ironmonone.tracker.BaseStats?): EnemyInfo? {
        if (!n.wild) return null
        val b = base(n.wildSpecies)
        return EnemyInfo(
            species = n.wildSpecies, speciesName = name(n.wildSpecies), level = 3, curHp = n.wildHp, maxHp = 13,
            type1 = b?.type1 ?: 0, type2 = b?.type2 ?: 0, base = b, movesSeen = emptyList(),
        )
    }

    /** The stage the clock says, counted from the first poll of a gb-nuz launch. */
    private fun gbClockStage(): Int {
        if (gbNuzStart == 0L) gbNuzStart = android.os.SystemClock.elapsedRealtime()
        return nuzStageOf(gbNuzStart, 6)
    }

    internal fun gb1Nuz(t: Gen1Tracker, stage: Int = gbClockStage()): TrackerState {
        val n = gbNuz(1, stage)
        val real = runCatching { t.read().nuz?.gb }.getOrNull()
        val party = n.party.map { t.trackedOf(it) }
        return TrackerState(
            partyCount = party.size, party = party, inBattle = n.wild, isWildBattle = n.wild,
            enemy = gbNuzEnemy(n, t::speciesName, t::baseStats), badgeSet = "RBY", mapId = if (n.stage >= 4) 13 else 12,
            nuz = NuzlockeReads(gb = gbNuzReads(1, real, n)),
        )
    }

    internal fun gb2Nuz(t: GbcTracker, stage: Int = gbClockStage()): TrackerState {
        val n = gbNuz(2, stage)
        val real = runCatching { t.read().nuz?.gb }.getOrNull()
        val party = n.party.map { t.trackedOf(it) }
        return TrackerState(
            partyCount = party.size, party = party, inBattle = n.wild, isWildBattle = n.wild,
            enemy = gbNuzEnemy(n, t::speciesName, t::baseStats), badgeSet = "GSC", mapId = if (n.stage >= 4) 4 else 2,
            nuz = NuzlockeReads(gb = gbNuzReads(2, real, n)),
        )
    }

    /** The stage the clock says, counted from the first poll of an nds-nuz launch. */
    private fun ndsClockStage(): Int {
        if (ndsNuzStart == 0L) ndsNuzStart = android.os.SystemClock.elapsedRealtime()
        return nuzStageOf(ndsNuzStart, 7)
    }

    /**
     * nds-nuz: the same run on a DS game, a stage every 7 s from the first poll, holding on the last. 0 the first route;
     * 1 a wild Pokemon; 2 the ball catches it and it joins the party while the battle is still on; 3 the battle is over;
     * 4 the second route; 5 a wild Pokemon, and the starter faints; 6 the Pokemon that was caught has beaten it (its HP
     * is 0 while the battle is still on); 7 the battle is over. A DS game leaves no word on how a battle ended, so the
     * ledger reads the faint and the growth of the party, which is what this shows. The species are national numbers,
     * and the name of the game is the running game's.
     */
    internal fun ndsNuz(t: NdsTracker, stage: Int = ndsClockStage()): NdsTrackerState {
        fun mon(species: Int, level: Int, hp: Int, pid: Long, named: Boolean = false): NdsTrackedMon {
            val m = gen4Mon(species, level, hp, 24, listOf(33, 0, 0, 0), listOf(35, 0, 0, 0), 0, 0, intArrayOf(14, 12, 13, 12, 12))
                .copy(pid = pid, otId = 40113, otSid = 22050, nicknamed = named)
            return NdsTrackedMon(mon = m, speciesName = t.speciesName(species), info = null, abilityName = "-", itemName = "-", moves = emptyList())
        }
        val hero = mon(387, 7, if (stage >= 5) 0 else 24, 0x3A5C1D02L, named = true)          // Turtwig
        val friend = mon(396, 3, 13, 0x7B2E9F41L, named = true)                                 // Starly
        val party = if (stage in 2..7) listOf(hero, friend) else listOf(hero)
        val wild = stage == 1 || stage == 2 || stage == 5 || stage == 6
        val foe = when (stage) {
            1 -> mon(396, 3, 13, 0x7B2E9F41L)
            2 -> mon(396, 3, 6, 0x7B2E9F41L)
            5 -> mon(399, 3, 12, 0x14D6A8C3L)                                                    // Bidoof
            6 -> mon(399, 3, 0, 0x14D6A8C3L)
            else -> null
        }
        return NdsTrackerState(
            partyCount = party.size, party = party, located = true, inBattle = wild, isWildBattle = wild, enemy = foe,
            resolvedBase = 0x0227C2F4L, badgeSet = t.map.badgePrefix, gameName = t.gameName,
            areaName = if (stage >= 4) "Route 202" else "Route 201", mapId = if (stage >= 4) 340 else 339,
        )
    }

    /**
     * The staged card goes through the same two rules as a live one: the move
     * count from the ROM learnset (LearnedMoves) and the evolution text
     * (EvoText), rather than numbers typed in here.
     */
    private fun tracked(t: GbaTracker, mon: PokemonDecoder.Mon, status: String = ""): TrackedMon {
        val base = t.baseStats(mon.species)
        val header = com.ironmonone.tracker.LearnedMoves.of(t.learnset(mon.species).map { it.first }, mon.level)
        return TrackedMon(
            mon = mon, speciesName = t.speciesName(mon.species), moveNames = mon.moves.map { t.moveName(it) }, base = base,
            abilityName = t.abilityNameOf(mon, base), itemName = if (mon.heldItem == 0) "-" else t.itemName(mon.heldItem),
            moveRows = t.moveRowsOf(mon), movesLearned = header.learned, movesTotal = header.total, nextMoveLevel = header.next,
            statusCondition = status,
            evo = com.ironmonone.tracker.EvoText.forOwn(
                t.evolution(mon.species), mon.level, { emptySet() }, mon.friendship,
                base?.baseFriendship ?: com.ironmonone.tracker.EvoText.DEFAULT_BASE, t.friendshipRequired(),
                com.ironmonone.tracker.TrackerPrefs.determineFriendship,
            ),
        )
    }

    /** Emerald, Wattson's gym: a Scyther against the third of his four. */
    fun gba(t: GbaTracker, mode: String): TrackerState {
        // Scyther 123: Wing Attack 17, Slash 163, Swords Dance 14, Pursuit 228. Oran Berry is item 139 (Cheri is 133).
        val scyther = gen3Mon(123, 25, 61, 78, listOf(17, 163, 14, 228), listOf(31, 20, 18, 20), 139, 0, 3, intArrayOf(66, 50, 63, 38, 50))
        // A nickname and part of a level's EXP, so the off-by-default "Show
        // nicknames" and "Show experience points bar" have something to show.
        val party = listOf(tracked(t, scyther.copy(nickname = "RAZOR")).copy(expNow = 1200, expTotal = 1951))
        // The lab, before the first Pokemon: the ball picker with its die. No repel: a new game has none to use
        // before its first Pokemon, and the lab showed the repel bar until 2026-10-01 (Blake: "Why is the repel logo
        // on this?").
        if (mode == "gba-lab") return TrackerState(
            partyCount = 0, party = emptyList(), inBattle = false, isWildBattle = false, inLab = true, badgeSet = "RSE",
            mapId = 17,
            // Torchic's ball on the confirm prompt, for "Show starter ball info".
            starterOffered = 280, starterBase = t.baseStats(280),
        )
        // Walking a town between battles: the badges and the pedometer take turns, and a Super Repel part-way down
        // shows the repel bar.
        if (mode == "gba-walk") return TrackerState(
            partyCount = 1, party = party, inBattle = false, isWildBattle = false, badges = 0b11, badgeSet = "RSE",
            healPercent = 62, healCount = 4, routeName = "Mauville City", steps = 18422, mapId = 89,
            repelSteps = 124, repelDuration = 200,
        )
        if (mode == "gba-over") return TrackerState(
            partyCount = 1, party = listOf(tracked(t, scyther.copy(curHp = 0))),
            inBattle = false, isWildBattle = false, badges = 0b11, badgeSet = "RSE",
            healPercent = 0, healCount = 0, routeName = "Mauville City", steps = 18422, gameOver = GameOver.LOST,
        )
        // FireRed and LeafGreen's map pictures (FrlgPictures, 2026-09-29): a wild Geodude in Mt. Moon 1F
        // (layout 114), so the map mark shows on the card and opens the viewer on a phone with no save that
        // far in. Species and moves come from whichever GBA game is loaded; only the place and the badge set
        // are FireRed's, and no encounter area is set, so nothing reads the loaded game's route data for it.
        if (mode == "gba-frlg") {
            val gb = t.baseStats(74)
            val geodude = EnemyInfo(
                species = 74, speciesName = t.speciesName(74), level = 9, curHp = 27, maxHp = 27,
                type1 = gb?.type1 ?: 5, type2 = gb?.type2 ?: 4, base = gb,
                movesSeen = emptyList(), moveRows = emptyList(),
                moves = listOf(33, 111, 0, 0), movePps = listOf(35, 40, 0, 0),
                evo = com.ironmonone.tracker.EvoText.forEnemy(t.evolution(74)),
            )
            return TrackerState(
                partyCount = 1, party = party, inBattle = true, isWildBattle = true,
                enemy = geodude, badges = 0b1, badgeSet = "FRLG",
                healPercent = 62, healCount = 4, routeName = "Mt. Moon 1F", steps = 4180, mapId = 114,
            )
        }
        // Nuzlocke (2026-09-29): a short scripted run the ledger follows, for a phone with no save that far in.
        // Start a Nuzlocke run on the loaded GBA game first (Home, Nuzlocke), then launch with --es demo gba-nuz.
        if (mode == "gba-nuz") return nuzStage(t)
        // A wild battle in the rain, staged so every move-table rule shows at
        // once: Return at full friendship, Flail at low HP, Low Kick into a
        // Geodude (20 kg), Weather Ball in rain, and the catch rate header.
        if (mode == "gba-wild") {
            val wildScyther = gen3Mon(123, 25, 20, 78, listOf(216, 237, 67, 311), listOf(20, 15, 20, 10), 0, 0, 3, intArrayOf(66, 50, 63, 38, 50))
                .copy(friendship = 255)
            val gb = t.baseStats(74)
            val geodude = EnemyInfo(
                species = 74, speciesName = t.speciesName(74), level = 20, curHp = 40, maxHp = 40,
                type1 = gb?.type1 ?: 5, type2 = gb?.type2 ?: 4, base = gb,
                movesSeen = emptyList(), moveRows = emptyList(),
                moves = listOf(33, 111, 88, 0), movePps = listOf(35, 40, 15, 0),
                evo = com.ironmonone.tracker.EvoText.forEnemy(t.evolution(74)),
            )
            return TrackerState(
                partyCount = 1, party = listOf(tracked(t, wildScyther)), inBattle = true, isWildBattle = true,
                enemy = geodude, weather = "RAIN", badges = 0b11, badgeSet = "RSE",
                // Route 111 is map 27; its Rock Smash table is Geodude at 100%, Lv 5-20
                // (RouteData), so the route info screen has a real area to open on.
                healPercent = 62, healCount = 4, routeName = "Route 111", steps = 18422, mapId = 27,
                encounterArea = "RockSmash",
                // The real formula (the Catch Rates screen's), with the reference's default ball.
                catchPercent = t.calcCatchRate(gb?.catchRate ?: 255, 40, 40, 20, 0, 4, false, 0, false, 0),
            )
        }
        // Magneton 82, level 22 in Emerald: Sonic Boom 49 and Thunder Wave 86 seen so far.
        val magneton = gen3Mon(82, 22, 44, 63, listOf(49, 86, 84, 48), listOf(20, 20, 30, 40), 0, 0, 0, intArrayOf(45, 50, 38, 58, 40))
        val eb = t.baseStats(82)
        val enemy = EnemyInfo(
            species = 82, speciesName = t.speciesName(82), level = 22, curHp = 44, maxHp = 63,
            evo = com.ironmonone.tracker.EvoText.forEnemy(t.evolution(82)),
            // Paralysed, so the staged card shows the status image over the icon.
            statusCondition = "PAR",
            type1 = eb?.type1 ?: 13, type2 = eb?.type2 ?: 8, base = eb,
            movesSeen = listOf(t.moveName(49), t.moveName(86)), moveRows = t.moveRowsOf(magneton).take(2),
            abilityGuess = eb?.let { b -> listOf(b.ability1, b.ability2).filter { it != 0 }.joinToString(" / ") { t.abilityName(it) } } ?: "?",
            // Its actual moveset, which Open Book or a vanilla game shows in place of those seen.
            moves = listOf(49, 86, 84, 48), movePps = listOf(19, 20, 30, 40),
        )
        return TrackerState(
            partyCount = 1, party = party, inBattle = true, isWildBattle = false,
            enemyTeam = listOf(false, false, true, true), enemy = enemy,
            badges = 0b11, badgeSet = "RSE", healPercent = 62, healCount = 4,
            routeName = "Mauville City", steps = 18422,
            // Mauville Gym, against Wattson, so the trainer screens read the real ROM.
            mapId = 89, opponentTrainerId = 267,
            // Its Sonic Boom hit for its fixed 20, shown between turns as the reference does.
            lastAttackMove = t.moveName(49), lastAttackDamage = 20, lastAttackMoveId = 49,
        )
    }


    /** Red, Blue, Yellow: Lt. Surge's gym. A Nidoking against his Raichu. Gen 1 has no items or abilities. */
    fun gb1(t: Gen1Tracker, mode: String): TrackerState {
        // Nuzlocke (2026-09-30): the same short run as gba-nuz, for a Game Boy phone with no save that far in.
        // Start a Nuzlocke run on the loaded game first (Home, Nuzlocke), then launch with --es demo gb-nuz.
        if (mode == "gb-nuz") return gb1Nuz(t)
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
        if (mode == "gb-nuz") return gb2Nuz(t)
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
            NdsMoveInfo(id = 395, name = "Force Palm", power = 60, accuracy = 100, type = "FIGHTING", pp = 10, category = "PHY"), NdsMoveInfo(id = 198, name = "Bone Rush", power = 25, accuracy = 80, type = "GROUND", pp = 10, category = "PHY"),
            NdsMoveInfo(id = 232, name = "Metal Claw", power = 50, accuracy = 95, type = "STEEL", pp = 35, category = "PHY"), NdsMoveInfo(id = 98, name = "Quick Attack", power = 40, accuracy = 100, type = "NORMAL", pp = 30, category = "PHY"),
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
        // A wild Sentret on Route 29, an area with LOCATION_DATA encounters, so the
        // encounter frame's pin and "seen/total" show (MainScreen readTrackedEncountersIntoLabel).
        if (mode == "nds-wild") {
            val sentret = gen4Mon(161, 3, 14, 14, listOf(33, 43, 0, 0), listOf(35, 30, 0, 0), 0, 70, intArrayOf(8, 7, 7, 7, 8))
            val wild = NdsTrackedMon(
                mon = sentret, speciesName = t.speciesName(161),
                info = NdsSpeciesInfo("Sentret", "NORMAL", "NORMAL", 215, "Run Away", "Keen Eye"),
                abilityName = "?", itemName = "-",
                moves = listOf(NdsMoveInfo(id = 33, name = "Tackle", power = 35, accuracy = 95, type = "NORMAL", pp = 35, category = "PHY")),
            )
            return NdsTrackerState(
                partyCount = 1, party = listOf(l), located = true, inBattle = true, isWildBattle = true, enemy = wild,
                resolvedBase = 0x0227C2F4L, badges = 0b11, badgeSet = "HGSS", healPercent = 50, healCount = 3,
                areaName = "Route 29",
            )
        }
        val milt = gen4Mon(241, 19, 61, 78, listOf(205, 23, 111, 45), listOf(20, 20, 40, 40), 0, 47, intArrayOf(38, 45, 44, 22, 31))
        val enemy = NdsTrackedMon(
            mon = milt, speciesName = t.speciesName(241),
            info = NdsSpeciesInfo("Miltank", "NORMAL", "NORMAL", 490, "Thick Fat", "Scrappy"),
            abilityName = "?", itemName = "-",
            moves = listOf(NdsMoveInfo(id = 205, name = "Rollout", power = 30, accuracy = 90, type = "ROCK", pp = 20, category = "PHY"), NdsMoveInfo(id = 23, name = "Stomp", power = 65, accuracy = 100, type = "NORMAL", pp = 20, category = "PHY")),
        )
        return NdsTrackerState(
            partyCount = 1, party = listOf(l), located = true, inBattle = true, isWildBattle = false, enemy = enemy,
            resolvedBase = 0x0227C2F4L, badges = 0b11, badgeSet = "HGSS", healPercent = 50, healCount = 3,
        )
    }

    /** Black 2, Burgh's gym: a Zangoose against his Leavanny. A Gen 4 game gets Whitney instead. */
    fun nds(t: NdsTracker, mode: String): NdsTrackerState {
        // Nuzlocke (2026-09-30): the same short run for a DS phone. Start a run on the loaded game first (Home, Nuzlocke),
        // then launch with --es demo nds-nuz.
        if (mode == "nds-nuz") return ndsNuz(t)
        if (t.map.generation == 4) return nds4(t, mode)
        // Zangoose 335 (Immunity), Leavanny 542 (Swarm / Chlorophyll); B2W2 Burgh's Leavanny is level 24.
        val zangoose = gen4Mon(335, 20, 48, 67, listOf(163, 24, 44, 372), listOf(20, 30, 25, 20), 0, 17, intArrayOf(55, 33, 44, 33, 33))
        val zMoves = listOf(
            NdsMoveInfo(id = 163, name = "Slash", power = 70, accuracy = 100, type = "NORMAL", pp = 20, category = "PHY"), NdsMoveInfo(id = 24, name = "Double Kick", power = 30, accuracy = 100, type = "FIGHTING", pp = 30, category = "PHY"),
            NdsMoveInfo(id = 44, name = "Bite", power = 60, accuracy = 100, type = "DARK", pp = 25, category = "PHY"), NdsMoveInfo(id = 372, name = "Assurance", power = 60, accuracy = 100, type = "DARK", pp = 10, category = "PHY"),
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
            moves = listOf(NdsMoveInfo(id = 75, name = "Razor Leaf", power = 55, accuracy = 95, type = "GRASS", pp = 25, category = "PHY"), NdsMoveInfo(id = 522, name = "Struggle Bug", power = 30, accuracy = 100, type = "BUG", pp = 20, category = "SPE")),
        )
        return NdsTrackerState(
            partyCount = 1, party = listOf(z), located = true, inBattle = true, isWildBattle = false, enemy = enemy,
            resolvedBase = 0x0221D3B4L, badges = 0b11, badgeSet = "B2W2", healPercent = 55, healCount = 3,
        )
    }
}
