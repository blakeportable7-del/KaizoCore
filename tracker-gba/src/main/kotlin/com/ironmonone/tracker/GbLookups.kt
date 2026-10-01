package com.ironmonone.tracker

/**
 * What the tracker panel asks a Game Boy tracker for beyond its TrackerState:
 * the lookups GbaTracker answers for Gen 3 through PlayScreen's trackerRef.
 * Until 2026-09-29 the Game Boy panels ran with trackerRef null and nothing in
 * its place, so on Red, Blue, Yellow, Gold, Silver and Crystal the move info
 * had no summary, the Pokemon info had no weight, evolution or weaknesses,
 * and "Moves 3/7 (16)" was a bare "Moves" (parity audit, PARITY-FINDINGS.md).
 *
 * Where each answer comes from, as the Game Boy references do it:
 * - [learnLevels]: the ROM's own learnset table, read by the tracker (the
 *   Gen 2 reference reads it out of the ROM, PokemonData.lua:269-298). Only the
 *   levels of NEW moves, as the references' movelvls: Lv.1 entries are
 *   dropped (PokemonData.lua:290 in the Gen 2 reference).
 * - [moveDescription], [weight], [evolution]: the references' tables
 *   (MoveData.lua `summary`, PokemonData.lua `weight` and `evolution`),
 *   converted by tools/extract_gb_data.py into gen1/ and gen2/ resources.
 * - [effectivenessAgainst]: PokemonData.getEffectiveness, the species' types
 *   from the ROM against that generation's chart ([generation] 1 is the Gen 1
 *   tracker's MoveData.TypeToEffectiveness, see Gen3Types.effect).
 */
interface GbLookups {
    /** 1 for Red, Blue and Yellow; 2 for Gold, Silver and Crystal. */
    val generation: Int
    fun speciesName(species: Int): String
    fun baseStats(species: Int): BaseStats?
    fun moveRowFor(id: Int): MoveRow?
    fun moveDescription(id: Int): String?
    /** Kilograms as the reference writes them ("6.9"); null when unknown. */
    fun weight(species: Int): String?
    /** A bare level, or the method (FRIEND, THUNDER, EEVEE_STONES ...); null when it does not evolve. */
    fun evolution(species: Int): String?
    /** Every attacking type that is not neutral against this species, by multiplier, highest first. */
    fun effectivenessAgainst(species: Int): Map<Double, List<String>>
    /** The levels it learns new moves at, ascending as the ROM lists them. */
    fun learnLevels(species: Int): List<Int>
}

/**
 * One generation's reference tables: gen1/ or gen2/ species-extra.tsv
 * (id, name, evolution, weight) and movedesc.tsv (id, name, summary).
 */
internal class GbTables(generation: Int) {
    private val extra = HashMap<Int, Pair<String, String>>()
    private val names = HashMap<Int, String>()
    private val desc = HashMap<Int, String>()

    init {
        val dir = "/gen$generation"
        rows("$dir/species-extra.tsv") { p ->
            if (p.size >= 4) p[0].toIntOrNull()?.let { extra[it] = p[2] to p[3]; names[it] = p[1] }
        }
        rows("$dir/movedesc.tsv") { p -> if (p.size >= 3) p[0].toIntOrNull()?.let { desc[it] = p[2] } }
    }

    /** PokemonData.lua's `name`, as the reference writes it ("Cyndaquil"). */
    fun name(species: Int): String? = names[species]?.trim()?.takeIf { it.isNotEmpty() }

    private fun rows(resource: String, row: (List<String>) -> Unit) {
        javaClass.getResourceAsStream(resource)?.bufferedReader(Charsets.UTF_8)?.useLines { lines ->
            lines.forEach { row(it.split('\t')) }
        }
    }

    fun evolution(species: Int): String? = EvoText.clean(extra[species]?.first)
    fun weight(species: Int): String? = extra[species]?.second?.trim()?.takeIf { it.isNotEmpty() }
    fun moveDescription(id: Int): String? = desc[id]?.trim()?.takeIf { it.isNotEmpty() }
}

/**
 * PokemonData.getEffectiveness for a [t1]/[t2] defender (Gen 3 type ids): the
 * multiplier of every attacking type that is not neutral, keyed highest first,
 * the shape GbaTracker.effectivenessAgainst gives the info screen. [gen1] is
 * the Gen 1 tracker's chart.
 */
internal fun weaknessesOf(t1: Int, t2: Int, gen1: Boolean): Map<Double, List<String>> {
    val out = sortedMapOf<Double, MutableList<String>>(compareByDescending { it })
    for (atk in Gen3Types.ALL) {
        val e = Gen3Types.effect(atk, t1, gen1) * (if (t2 == t1) 1.0 else Gen3Types.effect(atk, t2, gen1))
        if (e != 1.0) out.getOrPut(e) { mutableListOf() }.add(Gen3Types.name(atk))
    }
    return out
}

/**
 * The levels in one Game Boy learnset: (level, move) pairs after the
 * evolution entries, 0 ending each list. [at] is the entry's ROM offset,
 * [evoSize] the size of an evolution entry by its type byte (null for a
 * type the game does not have, which ends the read empty rather than walking
 * off into other data).
 */
internal fun gbLearnLevels(rom: ByteArray, at: Int, evoSize: (Int) -> Int?): List<Int> {
    fun u8(o: Int): Int = if (o in rom.indices) rom[o].toInt() and 0xFF else 0
    var p = at
    var evos = 0
    while (u8(p) != 0) {
        p += evoSize(u8(p)) ?: return emptyList()
        if (++evos > 8 || p !in rom.indices) return emptyList()
    }
    p++
    val out = ArrayList<Int>(16)
    var pairs = 0
    while (u8(p) != 0 && pairs++ < 64) {
        if (p + 1 !in rom.indices) break
        val level = u8(p)
        if (level > 1) out += level     // the references' movelvls hold NEW moves only
        p += 2
    }
    return out
}

/** A pointer out of a Game Boy bank-local table, as the randomizer resolves it (AbstractGBCRomHandler.calculateOffset). */
internal fun gbOffset(bank: Int, pointer: Int): Int = if (pointer < 0x4000) pointer else (pointer % 0x4000) + bank * 0x4000
