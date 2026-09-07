package com.ironmonone.app

import java.io.File

/**
 * Per-species stat markings and notes — the note-taking that makes IronMON
 * playable.
 *
 * You cannot see an enemy's real stats, so you record what a fight teaches you:
 * it outsped me, it shrugged off a special hit. Mark it once and the note is
 * waiting the next time that species appears.
 *
 * The states and the cycle order are besteon's, not invented here (Constants
 * .STAT_STATES and the `(state + 1) % 4` click handler in TrackerScreen.lua):
 *
 *   0 blank = unmarked,  1 "+" = good,  2 "−" = bad,  3 "=" = average
 *
 * Markings are per RUN, not forever: a new seed re-randomizes every base stat,
 * so notes from the last attempt would be actively misleading. [clear] runs on
 * New Run.
 */
class StatMarks(private val file: File) {

    companion object {
        const val STATES = 4
        /** Display order, matching the tracker's stat block. */
        val STAT_NAMES = listOf("HP", "ATK", "DEF", "SPA", "SPD", "SPE")
        /** Move 165. The reference never tracks it (Tracker.TrackMove). */
        const val STRUGGLE = 165
        const val COUNT = 6

        /**
         * Constants.STAT_STATES, verbatim. State 2 is TWO HYPHENS in the
         * reference, not a minus sign - the glyph is part of the look.
         */
        fun symbol(state: Int): String = when (state) {
            1 -> "+"
            2 -> "--"
            3 -> "="
            else -> " "
        }
    }

    // species -> six states. Kept in memory; the file is the durable copy.
    private val marks = HashMap<Int, IntArray>()

    /**
     * Free-text note per species, the tracker's other half: the stat cells say
     * "special defence is bad", a note says "the Blizzard is what kills you".
     * Stored beside the marks and cleared with them on New Run.
     *
     * Both files use ':' as the separator and one record per line. A note is
     * flattened to a single line on save, so a multi-line note can never eat the
     * following record when it is read back.
     */
    private val notes = HashMap<Int, String>()
    private val notesFile = File(file.parentFile, "notes.txt")

    private val routeSeen = HashMap<Int, MutableSet<Int>>()
    private val routeFile = File(file.parentFile, "routes.txt")

    /** One tracked move, the reference's `{ id, level, minLv, maxLv }` (Tracker.TrackMove). */
    data class SeenMove(val id: Int, val name: String, val minLv: Int, val maxLv: Int)

    /** Per species, most recently seen FIRST, the way Tracker.TrackMove keeps its list. */
    private val movesSeen = HashMap<Int, MutableList<SeenMove>>()
    private val movesFile = File(file.parentFile, "moves.txt")

    /**
     * Abilities REVEALED by battle triggers, per species. The reference's
     * Tracker.TrackAbility: the panel shows an enemy ability only once a
     * battle script has shown it activating, and remembers it all run.
     */
    private val abilitiesSeen = HashMap<Int, String>()
    private val abilitiesFile = File(file.parentFile, "abilities.txt")

    init {
        load()
        loadNotes()
        loadRoutes()
        loadMoves()
        loadAbilities()
    }

    private fun load() {
        marks.clear()
        if (!file.exists()) return
        runCatching {
            file.forEachLine { line ->
                val cut = line.indexOf(':')
                if (cut > 0) {
                    val species = line.substring(0, cut).toIntOrNull()
                        ?: return@forEachLine
                    val values = line.substring(cut + 1).split(',')
                        .mapNotNull { it.toIntOrNull() }
                    if (values.size == COUNT) {
                        marks[species] = IntArray(COUNT) {
                            values[it].coerceIn(0, STATES - 1)
                        }
                    }
                }
            }
        }
    }

    private fun save() {
        runCatching {
            file.parentFile?.mkdirs()
            file.bufferedWriter().use { w ->
                marks.forEach { (species, values) ->
                    // Skip all-blank rows so the file stays small and honest.
                    if (values.any { it != 0 }) {
                        w.write(species.toString() + ":" + values.joinToString(","))
                        w.newLine()
                    }
                }
            }
        }
    }

    private fun loadNotes() {
        notes.clear()
        if (!notesFile.exists()) return
        runCatching {
            notesFile.forEachLine { line ->
                val cut = line.indexOf(':')
                if (cut > 0) {
                    line.substring(0, cut).toIntOrNull()?.let {
                        notes[it] = line.substring(cut + 1)
                    }
                }
            }
        }
    }

    private fun saveNotes() {
        runCatching {
            notesFile.parentFile?.mkdirs()
            notesFile.bufferedWriter().use { w ->
                notes.forEach { (species, text) ->
                    val flat = text.lines().joinToString(" ").trim()
                    if (flat.isNotEmpty()) {
                        w.write(species.toString() + ":" + flat)
                        w.newLine()
                    }
                }
            }
        }
    }

    /**
     * Which species you have actually run into on each map.
     *
     * The reference's route info lists what a route CAN hold; this is the other
     * half - what you have seen there this run. Stored per map id, one line per
     * map, and cleared with the marks on a new run because a new seed puts
     * different Pokemon on every route.
     */

    private fun loadRoutes() {
        routeSeen.clear()
        if (!routeFile.exists()) return
        runCatching {
            routeFile.forEachLine { line ->
                val cut = line.indexOf(':')
                if (cut > 0) line.substring(0, cut).toIntOrNull()?.let { mapId ->
                    routeSeen[mapId] = line.substring(cut + 1).split(',')
                        .mapNotNull { it.trim().toIntOrNull() }.toMutableSet()
                }
            }
        }
    }

    private fun saveRoutes() {
        runCatching {
            routeFile.parentFile?.mkdirs()
            routeFile.bufferedWriter().use { w ->
                routeSeen.forEach { (mapId, mons) ->
                    if (mons.isNotEmpty()) {
                        w.write(mapId.toString() + ":" + mons.sorted().joinToString(","))
                        w.newLine()
                    }
                }
            }
        }
    }

    /**
     * Moves each enemy species has been SEEN using, for the whole run.
     *
     * The reference's Tracker.TrackMove persists these across encounters -
     * meeting a second Poochyena shows what the first one used. This port
     * cleared them at battle end, so every encounter started blind, which
     * throws away exactly the knowledge the tracker exists to keep.
     */

    private fun loadMoves() {
        movesSeen.clear()
        if (!movesFile.exists()) return
        runCatching {
            movesFile.forEachLine { line ->
                val cut = line.indexOf(':')
                if (cut > 0) line.substring(0, cut).toIntOrNull()?.let { sp ->
                    movesSeen[sp] = line.substring(cut + 1).split('|')
                        .filter { it.isNotBlank() }.map { rec ->
                            // id~name~min~max; a record without '~' is the old names-only file.
                            val f = rec.split('~')
                            if (f.size >= 4) SeenMove(f[0].toIntOrNull() ?: 0, f[1], f[2].toIntOrNull() ?: 0, f[3].toIntOrNull() ?: 0)
                            else SeenMove(0, rec, 0, 0)
                        }.toMutableList()
                }
            }
        }
    }

    private fun saveMoves() {
        runCatching {
            movesFile.parentFile?.mkdirs()
            movesFile.bufferedWriter().use { w ->
                movesSeen.forEach { (sp, moves) ->
                    if (moves.isNotEmpty()) {
                        w.write(sp.toString() + ":" + moves.joinToString("|") { "${it.id}~${it.name.replace('|', ' ').replace('~', ' ')}~${it.minLv}~${it.maxLv}" })
                        w.newLine()
                    }
                }
            }
        }
    }

    private fun loadAbilities() {
        abilitiesSeen.clear()
        if (!abilitiesFile.exists()) return
        runCatching {
            abilitiesFile.forEachLine { line ->
                val cut = line.indexOf(':')
                if (cut > 0) line.substring(0, cut).toIntOrNull()?.let {
                    abilitiesSeen[it] = line.substring(cut + 1)
                }
            }
        }
    }

    private fun saveAbilities() {
        runCatching {
            abilitiesFile.parentFile?.mkdirs()
            abilitiesFile.bufferedWriter().use { w ->
                abilitiesSeen.forEach { (sp, name) ->
                    w.write(sp.toString() + ":" + name)
                    w.newLine()
                }
            }
        }
    }

    /** Records a revealed ability; returns true when it is new. */
    fun revealAbility(species: Int, name: String): Boolean {
        if (species <= 0 || name.isBlank()) return false
        if (abilitiesSeen[species] == name) return false
        abilitiesSeen[species] = name
        saveAbilities()
        return true
    }

    fun abilityFor(species: Int): String? = abilitiesSeen[species]

    /**
     * The reference's Tracker.TrackMove, per move: Struggle is never tracked; a
     * new move goes to the FRONT; a move seen again widens its level range and,
     * if it had slipped below the top four, comes back to the front. Returns
     * true when anything changed.
     */
    fun addMovesSeen(species: Int, moves: List<Pair<Int, String>>, level: Int): Boolean {
        if (species <= 0 || moves.isEmpty()) return false
        val list = movesSeen.getOrPut(species) { mutableListOf() }
        var changed = false
        for ((id, name) in moves) {
            if (id <= 0 || id == STRUGGLE || name.isBlank()) continue
            val at = list.indexOfFirst { it.id == id }
            if (at < 0) { list.add(0, SeenMove(id, name, level, level)); changed = true; continue }
            val old = list[at]
            val upd = old.copy(minLv = minOf(old.minLv, level), maxLv = maxOf(old.maxLv, level))
            if (upd != old) { list[at] = upd; changed = true }
            if (at > 3) { list.removeAt(at); list.add(0, upd); changed = true }
        }
        if (changed) saveMoves()
        return changed
    }

    /** Every move this species has shown this run, most recent first. */
    fun movesSeenFor(species: Int): List<SeenMove> = movesSeen[species] ?: emptyList()

    /** Records a sighting. Returns true when it is new for this map. */
    fun seeOnRoute(mapId: Int, species: Int): Boolean {
        if (mapId <= 0 || species <= 0) return false
        val set = routeSeen.getOrPut(mapId) { mutableSetOf() }
        val added = set.add(species)
        if (added) saveRoutes()
        return added
    }

    fun seenOnRoute(mapId: Int): Set<Int> = routeSeen[mapId] ?: emptySet()

    fun noteFor(species: Int): String = notes[species] ?: ""

    fun setNote(species: Int, text: String) {
        val flat = text.lines().joinToString(" ").trim()
        if (flat.isEmpty()) notes.remove(species) else notes[species] = flat
        saveNotes()
    }

    /** Six states for [species]; all blank when nothing was marked. */
    fun of(species: Int): IntArray = marks[species]?.copyOf() ?: IntArray(COUNT)

    /** Advances one stat to the next state and persists. Returns the new state. */
    fun cycle(species: Int, statIndex: Int): Int {
        if (statIndex !in 0 until COUNT) return 0
        val values = marks.getOrPut(species) { IntArray(COUNT) }
        values[statIndex] = (values[statIndex] + 1) % STATES
        save()
        return values[statIndex]
    }

    fun setAll(species: Int, values: IntArray) {
        if (values.size != COUNT) return
        marks[species] = values.copyOf()
        save()
    }

    /** True when this species carries any mark at all. */
    fun hasAny(species: Int): Boolean = marks[species]?.any { it != 0 } == true

    fun markedSpecies(): Set<Int> = marks.filterValues { v -> v.any { it != 0 } }.keys

    /** New Run: last attempt's notes describe a different randomization. */
    fun clear() {
        marks.clear()
        notes.clear()
        routeSeen.clear()
        movesSeen.clear()
        abilitiesSeen.clear()
        runCatching { if (file.exists()) file.writeText("") }
        runCatching { if (notesFile.exists()) notesFile.writeText("") }
        runCatching { if (routeFile.exists()) routeFile.writeText("") }
        runCatching { if (movesFile.exists()) movesFile.writeText("") }
        runCatching { if (abilitiesFile.exists()) abilitiesFile.writeText("") }
    }
}
