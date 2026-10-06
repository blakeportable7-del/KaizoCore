package com.ironmonone.tracker

import com.ironmonone.tracker.nuzlocke.BossCap
import com.ironmonone.tracker.nuzlocke.NuzlockeScout
import com.ironmonone.tracker.nuzlocke.ScoutMon
import com.ironmonone.tracker.nuzlocke.ScoutMove
import com.ironmonone.tracker.nuzlocke.ScoutSource

/**
 * Boss scouting's view of a Gen 3 game (2026-10-06), Heart & Soul included: each team out of the ROM's trainer table
 * (GbaTracker.trainer), its moves the trainer's own or, for a team with none, the last four learned by its level, as
 * the game gives them. Asked for nothing until NuzlockeScout's fence is open; each team is read once.
 */
internal class Gen3Scout(private val t: GbaTracker) : ScoutSource {

    private val teams = HashMap<Int, Pair<String, List<ScoutMon>>?>()

    override fun team(trainerId: Int): Pair<String, List<ScoutMon>>? = synchronized(teams) {
        if (trainerId in teams) return teams[trainerId]
        val read = runCatching { read(trainerId) }.getOrNull()
        // A read that failed may work on a later look (the ROM still loading); one that worked is kept.
        if (read != null) teams[trainerId] = read
        read
    }

    private fun read(trainerId: Int): Pair<String, List<ScoutMon>>? {
        val info = t.trainer(trainerId) ?: return null
        val name = listOf(info.className, info.name).map { it.trim() }.filter { it.isNotEmpty() }.joinToString(" ")
        val party = info.party.map { m ->
            val own = m.moves.filter { it != 0 }
            val ids = own.ifEmpty { NuzlockeScout.defaultMoves(t.learnset(m.species), m.level) }
            val base = t.baseStats(m.species)
            ScoutMon(
                species = m.species,
                speciesName = t.speciesName(m.species),
                level = m.level,
                item = m.heldItem.takeIf { it != 0 }?.let { t.itemName(it) }?.takeIf { it.isNotBlank() && !it.startsWith("#") },
                moves = ids.map { id ->
                    val row = t.moveRowFor(id)
                    ScoutMove(id, row?.name ?: t.moveName(id), row?.power ?: 0, row?.type, row?.acc)
                },
                ivs = m.ivs,
                types = base?.let { if (it.type1 == it.type2) listOf(it.type1) else listOf(it.type1, it.type2) }.orEmpty(),
                base = base?.let { listOf(it.hp, it.atk, it.def, it.spe, it.spAtk, it.spDef) }.orEmpty(),
                defaultMoves = own.isEmpty(),
            )
        }
        return name to party
    }

    override fun teamsRandomized(): Boolean? = runCatching { t.trainerTeamsRandomized() }.getOrNull()

    override fun speciesName(species: Int): String = t.speciesName(species)

    override fun idsFor(boss: BossCap): List<Int> {
        val rival = t.rivalChoice ?: return boss.trainerIds
        if (boss.trainerIds.size < 2) return boss.trainerIds
        val (mine, rest) = boss.trainerIds.partition { t.whichRival(it) == rival }
        return mine + rest
    }
}
