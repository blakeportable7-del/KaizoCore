package com.ironmonone.app

import com.ironmonone.tracker.BaseStats
import com.ironmonone.tracker.EvoText
import com.ironmonone.tracker.GbLookups
import com.ironmonone.tracker.GbaTracker

/**
 * The tracker panel's lookups (move summaries, the Pokemon info screen's
 * weight, evolution and weaknesses, the learn levels behind "Moves 3/7 (16)")
 * for whichever tracker is running: the Gen 3 one (PlayScreen's trackerRef)
 * or a Game Boy one ([GbLookups]).
 *
 * The panel used to be handed trackerRef alone, which is null on Red, Blue,
 * Yellow, Gold, Silver and Crystal, so every one of these came back empty on
 * a Game Boy game (parity audit, 2026-09-28). Only one of the two is ever
 * set, the platform decides which; when the Gen 3 tracker is there its answer
 * is the answer, null included, so nothing changes on a GBA game.
 */
internal class PanelLookups(
    private val gba: () -> GbaTracker?,
    private val gb: () -> GbLookups?,
) {
    fun moveDescription(id: Int): String? = pick({ it.moveDescription(id) }, { it.moveDescription(id) })
    fun weight(species: Int): String? = pick({ it.weight(species) }, { it.weight(species) })
    fun evolution(species: Int): String? = pick({ it.evolution(species) }, { it.evolution(species) })
    /**
     * The info screen's evolution lines (Utils.getDetailedEvolutionsInfo): EvoText.detailed, with
     * the Gen 3 ROM's friendship requirement; the Game Boy trackers use 220, their default.
     */
    fun evolutionDetails(species: Int): List<String> {
        val g = gba()
        return if (g != null) EvoText.detailed(g.evolution(species), g.friendshipRequired())
        else gb()?.let { EvoText.detailed(it.evolution(species), EvoText.DEFAULT_REQUIRED, it.generation) } ?: listOf("---")
    }
    fun effectiveness(species: Int): Map<Double, List<String>> =
        pick({ it.effectivenessAgainst(species) }, { it.effectivenessAgainst(species) }) ?: emptyMap()
    fun moveLevels(species: Int): List<Int> =
        pick({ t -> t.learnset(species).map { it.first } }, { it.learnLevels(species) }) ?: emptyList()
    fun speciesBase(species: Int): BaseStats? = pick({ it.baseStats(species) }, { it.baseStats(species) })
    fun speciesName(species: Int): String = pick({ it.speciesName(species) }, { it.speciesName(species) }) ?: "#$species"

    private fun <T> pick(fromGba: (GbaTracker) -> T?, fromGb: (GbLookups) -> T?): T? {
        val g = gba()
        return if (g != null) fromGba(g) else gb()?.let(fromGb)
    }
}
