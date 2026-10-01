package com.ironmonone.tracker.nuzlocke

/**
 * Evolution lines for the dupes clause (2026-09-29): which species belong together, babies included.
 *
 * The lines are the vanilla ones (resources nuzlocke/families-<system>.tsv), so a species on no line is its own
 * line. A randomizer that shuffles evolutions changes who belongs together and this cannot know; the dupes clause is
 * then a suggestion the player corrects by hand.
 *
 * Each [NuzlockeSystem] has its own table, because the species numbers differ: Generations 1 and 2 and the Gen 3
 * games count the way natdex/species.tsv does (Gen 3 numbers past 251 are its own), the DS games use national dex
 * numbers. Gen 1 to 3 files write species names, resolved through natdex/species.tsv; the DS files write the numbers.
 */
object NuzlockeFamilies {

    private class Table(val roots: Map<Int, Int>, val idsByName: Map<String, Int>)

    private val tables = HashMap<NuzlockeSystem, Table>()

    private fun table(system: NuzlockeSystem): Table = synchronized(tables) {
        tables.getOrPut(system) {
            when (system) {
                NuzlockeSystem.GEN4, NuzlockeSystem.GEN5 -> {
                    val names = readResource("/nuzlocke/species-${system.key}.tsv")
                    Table(load(readResource(system.familiesResource), idNames(names), strict = false), namesToIds(names))
                }
                else -> {
                    val names = readResource("/natdex/species.tsv")
                    Table(load(readResource(system.familiesResource), names, strict = false), namesToIds(names))
                }
            }
        }
    }

    /** The number that stands for [species]' whole line: the lowest id in it. A species alone is its own line. */
    fun lineOf(species: Int, system: NuzlockeSystem = NuzlockeSystem.GEN3): Int = table(system).roots[species] ?: species

    fun sameLine(a: Int, b: Int, system: NuzlockeSystem = NuzlockeSystem.GEN3): Boolean = lineOf(a, system) == lineOf(b, system)

    /** The species a typed name means, for a Pokemon entered by hand, so the dupes clause can see it. Null for a name no game has. */
    fun speciesId(name: String, system: NuzlockeSystem = NuzlockeSystem.GEN3): Int? = table(system).idsByName[name.trim().lowercase()]

    /** Every species that shares a line with [species], itself included. */
    fun members(species: Int, system: NuzlockeSystem = NuzlockeSystem.GEN3): List<Int> {
        val roots = table(system).roots
        val line = roots[species] ?: return listOf(species)
        return roots.filterValues { it == line }.keys.sorted()
    }

    /** Name (lower case) to number, from a species list: the first of a name wins, and the table's gaps ("none") are not species. */
    private fun namesToIds(names: String): Map<String, Int> {
        val out = HashMap<String, Int>()
        names.lineSequence().forEach { line ->
            val tab = line.indexOf('\t')
            val id = if (tab > 0) line.substring(0, tab).toIntOrNull() else null
            val name = if (tab > 0) line.substring(tab + 1).substringBefore('\t').trim().lowercase() else ""
            if (id != null && name.isNotEmpty() && name != "none") out.putIfAbsent(name, id)
        }
        return out
    }

    /** A DS species file (id, name, ratio, types...) as the plain "id tab name" list [load] reads. */
    private fun idNames(species: String): String = buildString {
        species.lineSequence().forEach { line ->
            if (line.startsWith("#")) return@forEach
            val p = line.split('\t')
            if (p.size >= 2 && p[0].trim().toIntOrNull() != null) append(p[0].trim()).append('\t').append(p[1].trim()).append('\n')
        }
    }

    /**
     * Reads the table. [strict] throws on a Generation 1 to 3 name that does not resolve, which a test asks for
     * so a typo cannot pass quietly; the lines after "#natdex" are always allowed to name species a game lacks.
     * A token that is a number is a species number (the DS files), taken as it stands when [names] lists it.
     */
    internal fun load(
        families: String = readResource("/nuzlocke/families-gen3.tsv"),
        names: String = readResource("/natdex/species.tsv"),
        strict: Boolean,
    ): Map<Int, Int> {
        val idOf = HashMap<String, Int>()
        val known = HashSet<Int>()
        names.lineSequence().forEach { line ->
            val tab = line.indexOf('\t')
            if (tab <= 0) return@forEach
            val id = line.substring(0, tab).toIntOrNull() ?: return@forEach
            val name = line.substring(tab + 1).trim()
            if (name.isNotEmpty() && name != "none") { idOf.putIfAbsent(name, id); known += id }
        }
        val parent = HashMap<Int, Int>()
        fun find(x: Int): Int {
            var r = x
            while (true) { val p = parent[r] ?: break; if (p == r) break; r = p }
            var c = x
            while (c != r) { val n = parent[c] ?: break; parent[c] = r; c = n }
            return r
        }
        fun union(a: Int, b: Int) {
            val ra = find(a); val rb = find(b)
            if (ra != rb) parent[maxOf(ra, rb)] = minOf(ra, rb)
        }
        var lenient = false
        families.lineSequence().forEach { raw ->
            val line = raw.trim()
            if (line.isEmpty()) return@forEach
            if (line == "#natdex") { lenient = true; return@forEach }
            if (line.startsWith("#")) return@forEach
            // A DS line carries its names after a tab and a hash, as a comment.
            val ids = line.substringBefore('#').split(',').mapNotNull { n ->
                val token = n.trim()
                val id = idOf[token] ?: token.toIntOrNull()?.takeIf { it in known }
                if (id == null && strict && !lenient) error("families: no species called \"$token\" in \"$line\"")
                id
            }
            ids.forEach { parent.putIfAbsent(it, it) }
            for (i in 1 until ids.size) union(ids[0], ids[i])
        }
        return parent.keys.associateWith { find(it) }
    }

    private fun readResource(path: String): String =
        NuzlockeFamilies::class.java.getResourceAsStream(path)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
}
