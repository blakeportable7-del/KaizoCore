package com.ironmonone.tracker

import com.ironmonone.tracker.nuzlocke.BattleEnd
import com.ironmonone.tracker.nuzlocke.Gender
import com.ironmonone.tracker.nuzlocke.Method
import com.ironmonone.tracker.nuzlocke.NzArea
import com.ironmonone.tracker.nuzlocke.NzEnemy
import com.ironmonone.tracker.nuzlocke.NzItem
import com.ironmonone.tracker.nuzlocke.NzMon
import com.ironmonone.tracker.nuzlocke.NuzlockeStatics
import com.ironmonone.tracker.nuzlocke.NuzlockeSystem
import com.ironmonone.tracker.nuzlocke.NzOpponent
import com.ironmonone.tracker.nuzlocke.Snapshot

/**
 * The Gen 3 side of the Nuzlocke rules engine (2026-09-29): a [TrackerState] in, the engine's [Snapshot] out.
 *
 * Pure: no memory is read here (the reads are in GbaTracker.readNuzlocke and arrive as [TrackerState.nuz]). A
 * Game Boy or DS tracker gets an adapter of its own with the same shape, and the engine does not change.
 */
object Gen3Nuzlocke {

    /** gBattleOutcome as the engine reads it: 1 won, 2 lost, 3 drew, 4 ran, 5 teleported, 6 wild fled, 7 caught, 8 no Safari Balls, 9 forfeited, 10 the wild one teleported. */
    fun battleEnd(outcome: Int): BattleEnd = when (outcome) {
        1 -> BattleEnd.WON
        2 -> BattleEnd.LOST
        3 -> BattleEnd.DREW
        4, 5, 8, 9 -> BattleEnd.RAN
        6, 10 -> BattleEnd.MON_FLED
        7 -> BattleEnd.CAUGHT
        else -> BattleEnd.UNKNOWN
    }

    /** RouteData's encounter area names, as the tracker gives them for a wild battle. */
    fun method(area: String?): Method = when (area) {
        "Surfing" -> Method.SURF
        "Underwater" -> Method.UNDERWATER
        "RockSmash" -> Method.ROCK_SMASH
        "Old Rod", "Good Rod", "Super Rod" -> Method.ROD
        "Static" -> Method.STATIC
        else -> Method.WALK
    }

    /**
     * The battle's [method], and a set battle the game gives no sign of: Ruby, Sapphire and Emerald start Kecleon, New
     * Mauville's item-ball Voltorb and the hideout's Electrode with dowildbattle, whose battle type is any wild battle's,
     * so they read as walking and took the area's first encounter with statics free (rc33 audit P1 #76). They are found
     * by place and level in nuzlocke/statics-gen3.tsv, as the Game Boy and DS adapters find theirs.
     */
    internal fun method(s: TrackerState, n: NuzlockeReads): Method {
        val m = method(s.encounterArea)
        val e = s.enemy ?: return m
        if (m != Method.WALK || !s.inBattle || !s.isWildBattle) return m
        // Gen 3's own species ids are the national dex numbers up to Celebi, the only ones a row names.
        return if (NuzlockeStatics.isStatic(NuzlockeSystem.GEN3, listOf(n.staticsGame), s.routeName, e.level, e.species.takeIf { it in 1..251 }))
            Method.STATIC else m
    }

    private fun gender(ratio: Int?, pid: Long): Gender? = when (Gender3.of(ratio ?: 255, pid)) {
        Gender3.MALE -> Gender.MALE
        Gender3.FEMALE -> Gender.FEMALE
        else -> null
    }

    private fun types(a: Int, b: Int): List<Int> = if (a == b) listOf(a) else listOf(a, b)

    /** The engine's view of this state, or null when it has none to give: a Game Boy state, a failed read, an unreadable ROM. */
    fun snapshot(s: TrackerState): Snapshot? {
        val n = s.nuz ?: return null
        if (s.unreadable) return null
        val party = s.party.map { p ->
            NzMon(
                id = p.mon.pid, species = p.mon.species, speciesName = p.speciesName, nickname = p.mon.nickname,
                level = p.mon.level, hp = p.mon.curHp, maxHp = p.mon.maxHp, isEgg = p.mon.isEgg,
                gender = gender(p.base?.genderRatio, p.mon.pid),
                types = p.base?.let { types(it.type1, it.type2) } ?: emptyList(),
                shiny = p.mon.shiny,
            )
        }
        val enemy = s.enemy?.takeIf { !it.isGhost }?.let { e ->
            NzEnemy(
                id = e.pid, species = e.species, speciesName = e.speciesName, level = e.level, hp = e.curHp, maxHp = e.maxHp,
                gender = gender(e.base?.genderRatio, e.pid), types = types(e.type1, e.type2), shiny = n.enemyShiny,
            )
        }
        return Snapshot(
            readable = true,
            // The map section and whether it is a building (MAP_TYPE_INDOOR, 8): a gift there counts for its town or route.
            area = NzArea(s.routeName, s.mapId, section = n.mapSection.takeIf { it >= 0 }, indoor = n.mapType == 8),
            inBattle = s.inBattle,
            wild = s.isWildBattle,
            // A lesson is nobody's encounter, like a ghost: no area used up, no catch (2026-09-30, UX audit P0-9).
            ghost = s.isGhostBattle || n.lesson,
            facility = n.facility,
            method = method(s, n),
            enemy = enemy,
            opponent = n.opponent?.let { NzOpponent(it.trainerId, it.label, it.group, it.bossKey, it.maxLevel) },
            end = if (s.inBattle) BattleEnd.UNKNOWN else battleEnd(n.battleOutcome),
            turn = n.turn.takeIf { it >= 0 },
            party = party,
            partyCount = s.partyCount,
            badges = s.badges,
            ballCount = n.ballCount.takeIf { it >= 0 },
            bag = n.bag?.mapValues { NzItem(it.value.name, it.value.qty) },
            battleStyleSet = n.battleStyleSet,
            caps = n.caps,
            beaten = n.beaten,
        )
    }
}
