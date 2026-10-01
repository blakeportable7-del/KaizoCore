package com.ironmonone.tracker.nuzlocke

/**
 * Set battles the games do not flag (2026-09-30): the Snorlax in the road, a legendary, the Team Rocket traps. Gen 3
 * tells them apart by the battle's own flags; on the other games only a few battles carry one (Gen 2's battle type),
 * so the rest are a table: a wild battle at [StaticRow.place] at exactly [StaticRow.level] is a static, not the area's
 * first encounter (resources nuzlocke/statics-<system>.tsv, built from the research docs and the games' map scripts).
 *
 * The species in the file is a note for people, and a randomized game changes the species and keeps the place and
 * level. The exception is a row with a sixth column, the national dex number: an ordinary wild slot of that place has
 * that level too, so the row only counts when the wild Pokemon is that species (a randomized game does not match it).
 */
object NuzlockeStatics {

    class StaticRow(val game: String, val place: String, val level: Int, val species: Int? = null)

    private val cache = HashMap<NuzlockeSystem, List<StaticRow>>()

    private fun rows(system: NuzlockeSystem): List<StaticRow> = synchronized(cache) {
        cache.getOrPut(system) {
            val text = NuzlockeStatics::class.java.getResourceAsStream("/nuzlocke/statics-${system.key}.tsv")
                ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            parse(text)
        }
    }

    /** Rows of a statics file: game, place, level, a species note, a note and then, optionally, the dex number. A line that does not parse is skipped. */
    internal fun parse(text: String): List<StaticRow> = text.lineSequence()
        .filter { it.isNotBlank() && !it.startsWith("#") }
        .mapNotNull { line ->
            val p = line.split('\t')
            if (p.size < 3) return@mapNotNull null
            val level = p[2].trim().toIntOrNull()?.takeIf { it in 1..100 } ?: return@mapNotNull null
            StaticRow(p[0].trim(), p[1].trim(), level, p.getOrNull(5)?.trim()?.toIntOrNull()?.takeIf { it > 0 })
        }.toList()

    internal fun all(system: NuzlockeSystem): List<StaticRow> = rows(system)

    /**
     * Whether a wild battle at [place] at [level] is a static in one of [games] (the game's own key first, then its
     * family's: "g" then "gs"). The place is compared as the tracker names it, without regard to case. [species] is the
     * wild Pokemon's national dex number when it is known; a row that names a species only matches that one.
     */
    fun isStatic(system: NuzlockeSystem, games: List<String>, place: String?, level: Int, species: Int? = null): Boolean {
        if (place.isNullOrBlank()) return false
        return rows(system).any { r ->
            r.level == level && r.game in games && r.place.equals(place.trim(), ignoreCase = true) &&
                (r.species == null || r.species == species)
        }
    }
}
