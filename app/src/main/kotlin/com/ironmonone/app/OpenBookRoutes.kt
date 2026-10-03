package com.ironmonone.app

import com.ironmonone.core.RomKind
import com.ironmonone.tracker.GbaTracker

/**
 * Open Book's encounters for the route info screen: RandomizerLog.Data.Routes, from this run's log, kept for the run
 * (Play remembers one per run). Moved out of PlayScreen, where it ran on the main thread (rc32 audit P2 #27): the first
 * look parsed the whole log and built every route, and every look built a name map of up to 1,300 species. The route
 * info screen asks for it on Dispatchers.Default and says it is reading meanwhile (PcRouteInfoScreen).
 */
class OpenBookRoutes {
    /** The log the routes were built from: the parser keeps one instance while the file stays the same (RandomizerLog.parse). */
    private var log: RandomizerLog? = null
    private var byMap: Map<Int, LogRoute> = emptyMap()
    private var names: LogNames? = null

    /** How many times the routes were built, for the test that they are built once. */
    internal var builds = 0
        private set

    /**
     * The log's Pokemon for [area] ("Walking", "Surfing", a rod) on [mapId], most likely first, as the reference sorts
     * them ("table.sort ... rate desc, then pokemonID"); null when the area has no table in the log, the game is not a
     * Gen 3 run, or there is no log. Reads files: never on the main thread.
     */
    @Synchronized
    fun icons(t: GbaTracker, store: PrepStore, kind: RomKind?, badgeSet: String?, mapId: Int, area: String): List<RouteIcon>? {
        val type = AREA_TO_LOG[area] ?: return null
        if (kind == null || (badgeSet != "RSE" && badgeSet != "FRLG")) return null
        // No log yet is not kept as an answer: the next look reads again.
        val parsed = runCatching { store.currentRunLogFor(kind)?.let { RandomizerLog.parse(it) } }.getOrNull() ?: return null
        if (parsed !== log) {
            byMap = runCatching { LogRoutes.build(parsed, LogTrainerRules(t, badgeSet == "FRLG"), t).associateBy { it.mapId } }.getOrDefault(emptyMap())
            log = parsed
            builds++
        }
        // The log viewer's names (LogNames): MaxDex's ten-letter names ("RagingBolt", "MewtwoX") find their Pokemon too.
        val byName = names ?: LogNames.of(t).also { names = it }
        return byMap[mapId]?.areas?.get(type)
            ?.map { w -> RouteIcon(byName.speciesId(w.name), w.rate, w.levelMin, w.levelMax) }
            ?.sortedWith(compareByDescending<RouteIcon> { it.rate ?: 0.0 }.thenBy { it.species ?: 0 })
    }

    companion object {
        /** The route info screen's areas to the log's encounter tables. */
        private val AREA_TO_LOG = mapOf(
            "Walking" to LogEncType.GRASS, "Surfing" to LogEncType.SURFING, "RockSmash" to LogEncType.ROCKSMASH,
            "Old Rod" to LogEncType.OLDROD, "Good Rod" to LogEncType.GOODROD, "Super Rod" to LogEncType.SUPERROD,
        )
    }
}
