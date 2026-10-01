package com.ironmonone.tracker

/**
 * The Game Boy games' own map and trainer names as data (2026-09-30): which place a map belongs to, and who a trainer
 * class and number is. Both are tables from the games' disassemblies (resources nuzlocke/areas-gen1.tsv,
 * areas-gen2.tsv, trainers-gen1.tsv, trainers-gen2.tsv), kept apart from the tracker so the Nuzlocke rules read them
 * and the panel does not have to.
 */
object GbData {

    /** A map's place ("Mt. Moon") and the map itself ("Mt. Moon B1F"). */
    class Place(val place: String, val detail: String)

    private class Areas(val byKey: Map<Pair<String, Int>, Place>)
    private class Trainers(val rows: Map<Triple<String, Int, Int>, Pair<String, String>>)

    private val areas = HashMap<Int, Areas>()
    private val trainers = HashMap<Int, Trainers>()

    private fun text(resource: String): String =
        GbData::class.java.getResourceAsStream(resource)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""

    private fun rows(text: String): List<List<String>> =
        text.lineSequence().filter { it.isNotBlank() && !it.startsWith("#") }.map { it.split('\t') }.toList()

    private fun areasOf(generation: Int): Areas = synchronized(areas) {
        areas.getOrPut(generation) {
            val m = HashMap<Pair<String, Int>, Place>()
            for (p in rows(text("/nuzlocke/areas-gen$generation.tsv"))) {
                if (p.size < 3) continue
                val id = p[1].trim().toIntOrNull() ?: continue
                val place = p[2].trim().takeIf { it.isNotEmpty() } ?: continue
                m[p[0].trim() to id] = Place(place, p.getOrNull(3)?.trim()?.takeIf { it.isNotEmpty() } ?: place)
            }
            Areas(m)
        }
    }

    private fun trainersOf(generation: Int): Trainers = synchronized(trainers) {
        trainers.getOrPut(generation) {
            val m = HashMap<Triple<String, Int, Int>, Pair<String, String>>()
            for (p in rows(text("/nuzlocke/trainers-gen$generation.tsv"))) {
                if (p.size < 5) continue
                val cls = p[1].trim().toIntOrNull() ?: continue
                val no = p[2].trim().toIntOrNull() ?: continue
                m[Triple(p[0].trim(), cls, no)] = p[3].trim() to p[4].trim()
            }
            Trainers(m)
        }
    }

    /** The place a map id is in, for [game] (`rb`, `y`, `gs`, `c`). Null for a map the table does not name. */
    fun place(generation: Int, games: List<String>, id: Int): Place? {
        val t = areasOf(generation)
        for (g in games) t.byKey[g to id]?.let { return it }
        return null
    }

    /**
     * Who a trainer is: its label and its group (Gym, Elite4, Boss, Rival, Other). A row for the exact party number wins
     * over the row for the whole class (number 0), and a row for the game itself over the shared row (`*`).
     */
    fun trainer(generation: Int, games: List<String>, trainerClass: Int, no: Int): Pair<String, String>? {
        val t = trainersOf(generation).rows
        for (n in listOf(no, 0)) for (g in games + "*") t[Triple(g, trainerClass, n)]?.let { return it }
        return null
    }
}
