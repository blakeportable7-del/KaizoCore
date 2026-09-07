package com.ironmonone.app

/**
 * The PC tracker's "Startup favorites" (StreamerScreen.lua: three Pokemon,
 * shown on the new-game screen; Options.lua defaults them to 1,4,7). Here
 * they are three names on the RUN screen, kept in PrepStore's favorites file
 * as a comma list, and shown on the tracker's no-party card for the
 * generations whose reference tracker has the feature: Gen 1, 2 and 3. The
 * NDS tracker has no favorites screen (its only mention is a stream chat
 * command that reads a file), so the DS panel shows nothing. On a Gen 3 run
 * the lab ball call also shouts a match.
 */
object Favorites {
    const val SLOTS = 3

    /** Name -> id from the tracker's own species table (the Gen 3 build's internal ids past 251, which is what the sprite pack is keyed by). */
    val idByName: Map<String, Int> by lazy {
        val m = HashMap<String, Int>()
        com.ironmonone.tracker.GbaTracker::class.java.getResourceAsStream("/natdex/species.tsv")
            ?.bufferedReader()?.useLines { lines ->
                for (line in lines) {
                    val tab = line.indexOf('\t'); if (tab < 0) continue
                    val id = line.substring(0, tab).toIntOrNull() ?: continue
                    m[line.substring(tab + 1).trim().lowercase()] = id
                }
            }
        m
    }

    fun idOf(name: String): Int? = idByName[name.trim().lowercase()]

    /** Every known name in dex order, spelled for display (Bulbasaur, Mr. Mime, Ho-Oh). */
    val namesInOrder: List<String> by lazy {
        idByName.entries.sortedBy { it.value }.map { e -> e.key.split(' ').joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } } }
    }

    /**
     * Blake, 2026-09-07: "as you type the first letter, the name of the pokemon
     * comes up, and the list narrows as you type more." Names that START with
     * what was typed, in dex order, at most [limit]; nothing for an empty box
     * or an exact match already typed.
     */
    fun suggest(typed: String, limit: Int = 8): List<String> {
        val q = typed.trim().lowercase()
        if (q.isEmpty()) return emptyList()
        val hits = namesInOrder.filter { it.lowercase().startsWith(q) }
        if (hits.size == 1 && hits[0].lowercase() == q) return emptyList()
        return hits.take(limit)
    }

    /** The three slots as typed in the favorites file, padded to [SLOTS]. */
    fun slots(text: String): List<String> {
        val typed = text.split(',', '\n').map { it.trim() }
        return List(SLOTS) { typed.getOrElse(it) { "" } }
    }
    fun slots(store: PrepStore): List<String> = slots(store.favoritesText())

    fun text(slots: List<String>): String = slots.joinToString(",") { it.trim() }
    fun save(store: PrepStore, slots: List<String>) = store.saveFavorites(text(slots))

    /** "FAVORITES: SCYTHER / GENGAR", or null when none are set. */
    fun line(names: Collection<String>): String? =
        if (names.isEmpty()) null else "FAVORITES: " + names.joinToString(" / ") { it.uppercase() }
    fun line(store: PrepStore): String? = line(store.loadFavorites().toList())
}
