package com.ironmonone.tracker.nuzlocke

/**
 * What the DS tracker cannot see of a species (2026-09-30): its gender ratio and, on a game that was not randomized (no
 * sidecar), its types. The DS games keep them in the personal data archive of the ROM; resources
 * nuzlocke/species-gen4.tsv and species-gen5.tsv hold them for every species: id, name, the ratio byte, type 1, type 2
 * (the ids are national dex numbers, the types Gen 3 type ids, which Gen 4 and 5 share).
 */
object NuzlockeSpecies {

    class Row(val id: Int, val name: String, val genderRatio: Int, val type1: Int, val type2: Int)

    private val cache = HashMap<NuzlockeSystem, Map<Int, Row>>()

    private fun table(system: NuzlockeSystem): Map<Int, Row> = synchronized(cache) {
        cache.getOrPut(system) {
            val text = NuzlockeSpecies::class.java.getResourceAsStream("/nuzlocke/species-${system.key}.tsv")
                ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            parse(text)
        }
    }

    internal fun parse(text: String): Map<Int, Row> = text.lineSequence()
        .filter { it.isNotBlank() && !it.startsWith("#") }
        .mapNotNull { line ->
            val p = line.split('\t')
            if (p.size < 5) return@mapNotNull null
            val id = p[0].trim().toIntOrNull() ?: return@mapNotNull null
            val ratio = p[2].trim().toIntOrNull()?.takeIf { it in 0..255 } ?: return@mapNotNull null
            val t1 = p[3].trim().toIntOrNull() ?: return@mapNotNull null
            val t2 = p[4].trim().toIntOrNull() ?: t1
            Row(id, p[1].trim(), ratio, t1, t2)
        }.associateBy { it.id }

    fun row(system: NuzlockeSystem, species: Int): Row? = table(system)[species]

    /** The types of a species as Gen 3 type ids, one or two, or empty when the species is not in the table. */
    fun types(system: NuzlockeSystem, species: Int): List<Int> =
        row(system, species)?.let { if (it.type1 == it.type2) listOf(it.type1) else listOf(it.type1, it.type2) } ?: emptyList()

    /**
     * The gender of a Pokemon from its personality value and its species' ratio byte, as Generations 3 to 5 do it: always
     * male at 0, always female at 254, genderless at 255, and otherwise female when the low byte of the personality value
     * is below the ratio. Null for a genderless species or one the table does not have.
     */
    fun gender(system: NuzlockeSystem, species: Int, pid: Long): Gender? {
        val ratio = row(system, species)?.genderRatio ?: return null
        return when (ratio) {
            255 -> null
            0 -> Gender.MALE
            254 -> Gender.FEMALE
            else -> if ((pid and 0xFF).toInt() < ratio) Gender.FEMALE else Gender.MALE
        }
    }
}
