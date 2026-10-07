package com.ironmonone.app

import com.ironmonone.tracker.GbaTracker

/**
 * The names the Gen 3 log viewer shows for the log's Pokemon and moves (rc34, 2026-10-03).
 *
 * A vanilla log writes them in capitals ("BULBASAUR", "MUD-SLAP"), and they read the reference's way ([logTitle]), as
 * they always have. MaxDex and Nat. Dex logs write the game's own names in mixed case, and [logTitle] broke them:
 * MaxDex's ROM cuts a form to ten letters ("MewtwoX", "EternatusE", "ToxtricitL") and some long names too
 * ("ScreamTail", "Flechinder"), which read "Mewtwox" and "Screamtail"; Nat. Dex 1.2.1 writes "Necrozma-DM" and
 * "Jangmo-o", which read "Necrozma-Dm" and "Jangmo-O". A mixed-case name the tracker knows now reads as the tracker
 * names it ("Mewtwo-X", "Scream Tail", "Necrozma-DM"); a name in capitals, or one it does not know, reads as before.
 *
 * A name is matched to the tracker's species id, never by the log's number: MaxDex's log numbers its forms on from
 * 1026, where the tracker's 1039 is Okidogi. It is found by MaxDex's own ten-letter names, read from its ROM
 * (GbaTracker.logSpeciesIds), and by the tracker's names compared on letters and digits alone ([key]), so "MewtwoX" is
 * "Mewtwo-X", "Nidoran♀" is "Nidoran F" and the log's "Farfetch’d" is the tracker's "Farfetch'd". The sprites, the
 * evolution words, a team member's IVs and the searches all go through that id.
 */
class LogNames(
    /** The tracker's species id by the [key] of a name. */
    private val ids: Map<String, Int>,
    /** The tracker's name for a species id. */
    private val speciesName: (Int) -> String,
    /** The tracker's move names by [key]. */
    private val moveNames: Map<String, String> = emptyMap(),
) {
    /** The tracker's species id for the Pokemon the log calls [logName], or null. */
    fun speciesId(logName: String): Int? = ids[key(logName)]

    /** How the viewer names the Pokemon the log calls [logName]. */
    fun species(logName: String): String = trackerSpecies(logName) ?: logTitle(logName)

    /** How the viewer names the move the log calls [logName]. */
    fun move(logName: String): String = (if (mixedCase(logName)) moveNames[key(logName)] else null) ?: logTitle(logName)

    /**
     * A Pokemon the viewer shows as the log writes it (the Misc tab's starters and statics, a suggestion put in the
     * search box): the tracker's name where [species] would show it, else the log's own text, capitals and all.
     */
    fun speciesAsWritten(logName: String): String = trackerSpecies(logName) ?: logName

    /**
     * Whether a search for [query] finds the Pokemon the log calls [logName]: in the log's name or the one shown, then
     * on letters and digits alone, so "mewtwo x" finds MaxDex's "MewtwoX" and "pumpkaboo j" its "PumpkabooX".
     */
    fun finds(logName: String, query: String): Boolean {
        val q = query.trim()
        if (logName.contains(q, ignoreCase = true)) return true
        val shown = species(logName)
        if (shown.contains(q, ignoreCase = true)) return true
        val k = key(q)
        return k.isNotEmpty() && (key(logName).contains(k) || key(shown).contains(k))
    }

    private fun trackerSpecies(logName: String): String? =
        if (!mixedCase(logName)) null else speciesId(logName)?.let(speciesName)?.takeIf { it.isNotBlank() && !it.startsWith("#") }

    companion object {
        /** No tracker to ask: every name reads the reference's way. */
        val PLAIN = LogNames(emptyMap(), { "" })

        /** A name compared on letters and digits alone, accents dropped and the gender signs read as f and m. */
        fun key(s: String): String = LogSuggest.normalize(s)

        /** The game's own mixed case; a vanilla log's capitals keep [logTitle]. */
        private fun mixedCase(s: String): Boolean = s.any { it.isLowerCase() }

        /**
         * The game's names from the tracker that played the run: MaxDex's ten-letter names from its ROM first (they
         * are what its log prints), then each species' own name, and the moves to the game's last. Reads the ROM, so
         * never on the main thread.
         */
        fun of(t: GbaTracker): LogNames {
            val ids = HashMap<String, Int>()
            fun add(name: String, id: Int) {
                if (name.startsWith("#")) return
                val k = key(name)
                if (k.isNotEmpty()) ids.putIfAbsent(k, id)
            }
            for ((name, id) in t.logSpeciesIds()) add(name, id)
            // Heart & Soul's run to 1572, past the Nat. Dex build's 1300 (GbaTracker.speciesIdCount).
            for (id in 1..t.speciesIdCount) add(t.speciesName(id), id)
            // Heart & Soul's forms share their species' name in the game (every Rotom is ROTOM), and its log names each
            // by its constant (ROTOM-HEAT, HnsGame.displayNames): found by those too, or a form's page had no picture.
            if (t.heartSoul) hnsLogNames(t).forEach { (id, name) -> add(name, id) }
            val moves = HashMap<String, String>()
            for (id in 1..t.lastMoveId) {
                val n = t.moveName(id)
                val k = key(n)
                if (!n.startsWith("#") && k.isNotEmpty()) moves.putIfAbsent(k, n)
            }
            return LogNames(ids, t::speciesName, moves)
        }

        /**
         * The names Heart & Soul's log gives its species (HnsGame.displayNames), from the build's species constants
         * (the comfort build's species file, the one build the tracker reads) and the game's own names. Empty without
         * the app's assets.
         */
        private fun hnsLogNames(t: GbaTracker): Map<Int, String> = runCatching {
            val assets = com.ironmonone.app.engine.HnsEngine.assetText ?: return emptyMap()
            val file = com.ironmonone.app.engine.hns.HnsSpeciesFile.parse(assets(com.ironmonone.app.engine.HnsEngine.BUILDS.first().speciesAsset))
            val consts = file.species.associate { it.id to it.const }
            com.ironmonone.app.engine.hns.HnsGame.displayNames((1..t.speciesIdCount).filter { t.speciesExists(it) }
                .map { Triple(it, consts[it].orEmpty(), t.speciesName(it)) })
        }.getOrDefault(emptyMap())
    }
}
