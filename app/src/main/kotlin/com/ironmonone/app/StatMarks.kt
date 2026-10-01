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
        /** The DS tracker's PokemonData.TYPE_LIST (PokemonData.lua:3-20): Hidden Power's types, in the arrows' order. */
        val DS_HIDDEN_POWER_TYPES = listOf(
            "BUG", "DARK", "DRAGON", "ELECTRIC", "FIGHTING", "FIRE", "FLYING", "GHOST",
            "GRASS", "GROUND", "ICE", "POISON", "PSYCHIC", "ROCK", "STEEL", "WATER",
        )

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
    /**
     * Tracker.Data.encounterTable: per map AND encounter area ("Walking",
     * "Surfing", a rod...), the wild species met there in order of appearance,
     * which is what the route info screen shows (Tracker.TrackRouteEncounter).
     * Keyed "mapId|area".
     */
    private val routeAreaSeen = HashMap<String, LinkedHashSet<Int>>()
    private val routeFile = File(file.parentFile, "routes.txt")

    /**
     * The DS tracker's encounterData (Tracker.updateEncounterData): per area name, each
     * wild species met there and every level it was met at, ascending. Recorded for a
     * new wild enemy (BattleHandlerBase._logNewEnemy, enemyTrainerID 0).
     */
    private val dsEncounters = HashMap<String, LinkedHashMap<Int, java.util.TreeSet<Int>>>()
    private val dsEncounterFile = File(file.parentFile, "ds-encounters.txt")

    /** One tracked move, the reference's `{ id, level, minLv, maxLv }` (Tracker.TrackMove). */
    data class SeenMove(
        val id: Int, val name: String, val minLv: Int, val maxLv: Int,
        /** The level it was last seen at (Tracker.TrackMove's level), which the move stars read. */
        val lastLv: Int = maxLv,
    )

    /** Per species, most recently seen FIRST, the way Tracker.TrackMove keeps its list. */
    private val movesSeen = HashMap<Int, MutableList<SeenMove>>()
    private val movesFile = File(file.parentFile, "moves.txt")

    /**
     * Abilities REVEALED by battle triggers, per species. The reference's
     * Tracker.TrackAbility: the panel shows an enemy ability only once a
     * battle script has shown it activating, and remembers it all run.
     */
    private val abilitiesSeen = HashMap<Int, MutableList<String>>()
    private val abilitiesFile = File(file.parentFile, "abilities.txt")

    // Tracker.TrackEncounter and recordLastLevelsSeen (GBA), amountSeen and lastLevelSeen
    // (DS): how often each species was met, wild and on trainers apart, and its level the
    // last time. Part of the run's tracked data, as in the reference, so it outlasts the
    // Play screen. One line per species, "species:wild,trainer,lastLevel" (0 = none).
    private val encWild = HashMap<Int, Int>()
    private val encTrainer = HashMap<Int, Int>()
    private val lastLevels = HashMap<Int, Int>()
    private val encountersFile = File(file.parentFile, "encounters.txt")
    // Tracker.TrackSafariEncounter: each Safari Zone map's wild Pokemon, met order, with the
    // highest level each was met at. "mapId:species/level,species/level" per line.
    private val safari = HashMap<Int, LinkedHashMap<Int, Int>>()
    private val safariFile = File(file.parentFile, "safari.txt")

    /**
     * The DS tracker's run-wide values in its trackedData (Tracker.lua:5-17), kept
     * with the run as the reference keeps them with the ROM: key=value lines.
     * currentHiddenPowerType starts at BUG, PokemonData.TYPE_LIST's first entry.
     */
    private val dsTracked = HashMap<String, String>()
    private val dsTrackedFile = File(file.parentFile, "ds-tracked.txt")

    init {
        load()
        loadNotes()
        loadRoutes()
        loadMoves()
        loadAbilities()
        loadEncounters()
        loadSafari()
        loadDsTracked()
    }

    private fun loadDsTracked() {
        dsTracked.clear()
        if (!dsTrackedFile.exists()) return
        runCatching {
            dsTrackedFile.forEachLine { line ->
                val cut = line.indexOf('='); if (cut > 0) dsTracked[line.substring(0, cut)] = line.substring(cut + 1)
            }
        }
    }

    private fun saveDsTracked() {
        runCatching {
            dsTrackedFile.parentFile?.mkdirs()
            SafeWrite.text(dsTrackedFile, dsTracked.entries.joinToString("") { "${it.key}=${it.value}\n" })
        }
    }

    /** Tracker.getCurrentHiddenPowerType: the one Hidden Power type the DS tracker keeps for the run. */
    fun dsHiddenPowerType(): String = dsTracked["hiddenPowerType"]?.takeIf { it in DS_HIDDEN_POWER_TYPES } ?: DS_HIDDEN_POWER_TYPES[0]

    /** Tracker.getPokecenterCount: the DS tracker's Pokecenter heals, 10 to start (Tracker.lua:16). */
    fun dsPokecenterCount(): Int = dsTracked["pokecenterCount"]?.toIntOrNull()?.coerceIn(0, 99) ?: 10

    /** Tracker.increasePokecenterCount / decreasePokecenterCount (Tracker.lua:199-205): one either way, 0 to 99. */
    fun bumpDsPokecenter(up: Boolean): Int {
        val n = (dsPokecenterCount() + if (up) 1 else -1).coerceIn(0, 99)
        dsTracked["pokecenterCount"] = n.toString()
        saveDsTracked()
        return n
    }

    /**
     * A Survival run on DS (PcHeals.observeDsSurvival): the counter starts at [limit]'s heals,
     * once per run. A count the player already has is kept, and a run already past the 8th
     * badge (the app updated mid-run) gets no bonus added. True when it just armed.
     */
    fun armDsSurvival(limit: PcHeals.Limit, badges: Int, leagueBeaten: Boolean = false): Boolean {
        if (dsTracked["survivalArmed"] == "1") return false
        dsTracked["survivalArmed"] = "1"
        if (dsTracked["pokecenterCount"] == null) dsTracked["pokecenterCount"] = limit.start.toString()
        if (badges >= 8) dsTracked["survivalBonus"] = "1"
        // Met past the Johto League (the app updated mid-run): the Kanto heals were the player's to count.
        if (leagueBeaten) dsTracked["survivalKanto"] = "1"
        saveDsTracked()
        return true
    }

    /**
     * HeartGold and SoulSilver: the 7 heals for Kanto once the Johto League is beaten, on an armed Survival run, once
     * (2026-09-30). The rules: "If you complete the Elite 4 in a Johto game you earn an additional 7 heals for Kanto."
     * [leagueBeaten] is the game's own League byte (NdsTracker.readLeagueBeaten), which only those two games have.
     */
    fun dsSurvivalKanto(leagueBeaten: Boolean): Boolean {
        if (!leagueBeaten || dsTracked["survivalArmed"] != "1" || dsTracked["survivalKanto"] == "1") return false
        dsTracked["survivalKanto"] = "1"
        dsTracked["pokecenterCount"] = (dsPokecenterCount() + PcHeals.KANTO_HEALS).coerceIn(0, 99).toString()
        saveDsTracked()
        return true
    }

    /** The 8th badge's bonus heal on an armed DS Survival run, once. True when it was just added. */
    fun dsSurvivalBadges(badges: Int): Boolean {
        if (badges < 8 || dsTracked["survivalArmed"] != "1" || dsTracked["survivalBonus"] == "1") return false
        dsTracked["survivalBonus"] = "1"
        bumpDsPokecenter(true)
        return true
    }

    /**
     * Tracker.increaseHiddenPowerType / decreaseHiddenPowerType (Tracker.lua:424-434):
     * the next or previous type in TYPE_LIST, wrapping. The main screen's "<" and ">"
     * on your own Hidden Power's row (MainScreen.onChangeHiddenPower).
     */
    fun stepDsHiddenPower(forward: Boolean): String {
        val n = DS_HIDDEN_POWER_TYPES.size
        val i = DS_HIDDEN_POWER_TYPES.indexOf(dsHiddenPowerType())
        val t = DS_HIDDEN_POWER_TYPES[if (forward) (i + 1) % n else (i + n - 1) % n]
        dsTracked["hiddenPowerType"] = t
        saveDsTracked()
        return t
    }

    private fun loadSafari() {
        safari.clear()
        if (!safariFile.exists()) return
        runCatching {
            safariFile.forEachLine { line ->
                val cut = line.indexOf(':')
                if (cut <= 0) return@forEachLine
                val map = line.substring(0, cut).toIntOrNull() ?: return@forEachLine
                val seen = LinkedHashMap<Int, Int>()
                line.substring(cut + 1).split(',').forEach { e ->
                    val sp = e.substringBefore('/').trim().toIntOrNull() ?: return@forEach
                    val lv = e.substringAfter('/', "").trim().toIntOrNull() ?: return@forEach
                    if (sp > 0 && lv in 1..100) seen[sp] = lv
                }
                if (seen.isNotEmpty()) safari[map] = seen
            }
        }
    }

    /** A StringBuilder with a writer's two calls, so each save below reads as it did when it wrote in place. */
    private class Lines {
        val text = StringBuilder()
        fun write(s: String) { text.append(s) }
        fun newLine() { text.append('\n') }
    }

    /**
     * Writes one of the run's note files whole or not at all (SafeWrite). All eight were written in place, which
     * empties the file first: a kill or a crash in that moment left the run's marks, notes or route record blank,
     * and every tap on a stat box rewrites the marks (2026-09-30, UX audit P0-3).
     */
    private fun writeWhole(target: File, fill: (Lines) -> Unit) {
        val lines = Lines()
        fill(lines)
        SafeWrite.text(target, lines.text.toString())
    }

    private fun saveSafari() {
        runCatching {
            safariFile.parentFile?.mkdirs()
            writeWhole(safariFile) { w ->
                safari.forEach { (map, seen) ->
                    w.write(map.toString() + ":" + seen.entries.joinToString(",") { (sp, lv) -> "$sp/$lv" })
                    w.newLine()
                }
            }
        }
    }

    /**
     * Tracker.TrackSafariEncounter: [species] met on Safari Zone map [mapId] at [level], raising
     * a level already kept. Returns true when the record changed.
     */
    fun seeSafari(mapId: Int, species: Int, level: Int, save: Boolean = true): Boolean {
        if (mapId <= 0 || species <= 0 || level !in 1..100) return false
        val seen = safari.getOrPut(mapId) { LinkedHashMap() }
        if (level <= (seen[species] ?: 0)) return false
        seen[species] = level
        if (save) saveSafari()
        return true
    }

    /**
     * The Safari record for [mapId] as the reference's !pivots lists it (EventData.getPivots):
     * highest level first, then by species.
     */
    fun safariSeen(mapId: Int): List<Pair<Int, Int>> =
        safari[mapId]?.entries?.map { it.key to it.value }
            ?.sortedWith(compareByDescending<Pair<Int, Int>> { it.second }.thenBy { it.first }) ?: emptyList()

    private fun loadEncounters() {
        encWild.clear(); encTrainer.clear(); lastLevels.clear()
        if (!encountersFile.exists()) return
        runCatching {
            encountersFile.forEachLine { line ->
                val cut = line.indexOf(':')
                if (cut <= 0) return@forEachLine
                val sp = line.substring(0, cut).toIntOrNull() ?: return@forEachLine
                val v = line.substring(cut + 1).split(',').map { it.trim().toIntOrNull() ?: 0 }
                v.getOrNull(0)?.takeIf { it > 0 }?.let { encWild[sp] = it }
                v.getOrNull(1)?.takeIf { it > 0 }?.let { encTrainer[sp] = it }
                v.getOrNull(2)?.takeIf { it in 1..100 }?.let { lastLevels[sp] = it }
            }
        }
    }

    private fun saveEncounters() {
        runCatching {
            encountersFile.parentFile?.mkdirs()
            writeWhole(encountersFile) { w ->
                (encWild.keys + encTrainer.keys + lastLevels.keys).sorted().forEach { sp ->
                    w.write("$sp:${encWild[sp] ?: 0},${encTrainer[sp] ?: 0},${lastLevels[sp] ?: 0}")
                    w.newLine()
                }
            }
        }
    }

    /**
     * Tracker.TrackEncounter: [species] met once more, in a wild battle or a trainer's.
     * [save] false keeps it off disk (a staged Demo battle must not write into the run).
     */
    fun trackEncounter(species: Int, wild: Boolean, save: Boolean = true) {
        if (species <= 0) return
        val m = if (wild) encWild else encTrainer
        m[species] = (m[species] ?: 0) + 1
        if (save) saveEncounters()
    }

    /** Tracker.getEncounters: how often [species] was met in wild (or in trainer) battles this run. */
    fun encounters(species: Int, wild: Boolean): Int = (if (wild) encWild else encTrainer)[species] ?: 0

    /** The DS tracker's getAmountSeen: every meeting, wild and trainer together. */
    fun totalEncounters(species: Int): Int = encounters(species, true) + encounters(species, false)

    /**
     * Tracker.recordLastLevelsSeen: each (species, level), levels outside 1-100 skipped.
     * Returns true when one changed.
     */
    fun recordLastLevels(mons: List<Pair<Int, Int>>, save: Boolean = true): Boolean {
        var changed = false
        mons.forEach { (sp, lv) ->
            if (sp > 0 && lv in 1..100 && lastLevels[sp] != lv) { lastLevels[sp] = lv; changed = true }
        }
        if (changed && save) saveEncounters()
        return changed
    }

    /** Tracker.getLastLevelSeen: [species]' level the last time a battle with it ended, or null. */
    fun lastLevelSeen(species: Int): Int? = lastLevels[species]

    /** Every species met in a battle this run. */
    fun encounteredSpecies(): Set<Int> = encWild.keys + encTrainer.keys

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
            writeWhole(file) { w ->
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
            writeWhole(notesFile) { w ->
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
        loadDsEncounters()
        routeSeen.clear()
        routeAreaSeen.clear()
        if (!routeFile.exists()) return
        runCatching {
            routeFile.forEachLine { line ->
                val cut = line.indexOf(':')
                if (cut <= 0) return@forEachLine
                val key = line.substring(0, cut)
                val ids = line.substring(cut + 1).split(',').mapNotNull { it.trim().toIntOrNull() }
                if ('|' in key) routeAreaSeen[key] = LinkedHashSet(ids)
                else key.toIntOrNull()?.let { mapId -> routeSeen[mapId] = LinkedHashSet(ids) }
            }
        }
    }

    private fun saveRoutes() {
        runCatching {
            routeFile.parentFile?.mkdirs()
            writeWhole(routeFile) { w ->
                routeSeen.forEach { (mapId, mons) ->
                    if (mons.isNotEmpty()) {
                        w.write(mapId.toString() + ":" + mons.joinToString(","))
                        w.newLine()
                    }
                }
                routeAreaSeen.forEach { (key, mons) ->
                    if (mons.isNotEmpty()) { w.write(key + ":" + mons.joinToString(",")); w.newLine() }
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
                                // Saved before lastLv existed: the highest level stands in.
                                .let { m -> if (f.size >= 5) m.copy(lastLv = f[4].toIntOrNull() ?: m.maxLv) else m }
                            else SeenMove(0, rec, 0, 0)
                        }.toMutableList()
                }
            }
        }
    }

    private fun saveMoves() {
        runCatching {
            movesFile.parentFile?.mkdirs()
            writeWhole(movesFile) { w ->
                movesSeen.forEach { (sp, moves) ->
                    if (moves.isNotEmpty()) {
                        w.write(sp.toString() + ":" + moves.joinToString("|") { "${it.id}~${it.name.replace('|', ' ').replace('~', ' ')}~${it.minLv}~${it.maxLv}~${it.lastLv}" })
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
                // "species:A" (one tracked) or "species:A|B" (both), the
                // file of builds before 2026-09-27 being the first form.
                if (cut > 0) line.substring(0, cut).toIntOrNull()?.let {
                    val names = line.substring(cut + 1).split('|').filter { n -> n.isNotBlank() }.take(3)
                    if (names.isNotEmpty()) abilitiesSeen[it] = names.toMutableList()
                }
            }
        }
    }

    private fun saveAbilities() {
        runCatching {
            abilitiesFile.parentFile?.mkdirs()
            writeWhole(abilitiesFile) { w ->
                abilitiesSeen.forEach { (sp, names) ->
                    w.write(sp.toString() + ":" + names.joinToString("|") { it.replace('|', ' ') })
                    w.newLine()
                }
            }
        }
    }

    /** Records a revealed ability; returns true when it is new. */
    fun revealAbility(species: Int, name: String, max: Int = 2): Boolean {
        if (species <= 0 || name.isBlank() || name == "?") return false
        // Tracker.TrackAbility: the first becomes slot 1, a DIFFERENT one slot 2,
        // and a species with two tracked keeps them. It used to keep one and
        // let a later reveal overwrite it (2026-09-27, Blake: "check the pc version").
        val names = abilitiesSeen.getOrPut(species) { mutableListOf() }
        // The DS reference keeps every one it sees (a Gen 5 species can have three with its hidden ability).
        if (name in names || names.size >= max) return false
        names += name
        saveAbilities()
        return true
    }

    /** The first tracked ability, or null. */
    fun abilityFor(species: Int): String? = abilitiesSeen[species]?.firstOrNull()

    /** The second tracked ability, once a different one has been seen. */
    fun secondAbilityFor(species: Int): String? = abilitiesSeen[species]?.getOrNull(1)

    /** Both tracked abilities, in the order they were seen. */
    fun abilitiesFor(species: Int): List<String> = abilitiesSeen[species].orEmpty()

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
            val upd = old.copy(minLv = minOf(old.minLv, level), maxLv = maxOf(old.maxLv, level), lastLv = level)
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
        val set = routeSeen.getOrPut(mapId) { LinkedHashSet() }
        val added = set.add(species)
        if (added) saveRoutes()
        return added
    }

    fun seenOnRoute(mapId: Int): Set<Int> = routeSeen[mapId] ?: emptySet()

    /** Tracker.updateEncounterData: [species] met at [level] in the DS area [area]. */
    fun seeDsEncounter(area: String, species: Int, level: Int): Boolean {
        if (area.isBlank() || species <= 0 || level <= 0) return false
        val added = dsEncounters.getOrPut(area) { LinkedHashMap() }.getOrPut(species) { java.util.TreeSet() }.add(level)
        if (added) saveDsEncounters()
        return added
    }

    /** Tracker.getEncounterData(area).encountersSeen: species to its levels, ascending. */
    fun dsEncountersIn(area: String): Map<Int, List<Int>> =
        dsEncounters[area]?.mapValues { (_, v) -> v.toList() } ?: emptyMap()

    private fun loadDsEncounters() {
        dsEncounters.clear()
        if (!dsEncounterFile.exists()) return
        runCatching {
            dsEncounterFile.forEachLine { line ->
                val p = line.split('	')
                if (p.size < 2) return@forEachLine
                val m = LinkedHashMap<Int, java.util.TreeSet<Int>>()
                p[1].split(',').forEach { e ->
                    val sp = e.substringBefore(':').toIntOrNull() ?: return@forEach
                    m[sp] = java.util.TreeSet(e.substringAfter(':', "").split('/').mapNotNull { it.toIntOrNull() })
                }
                if (m.isNotEmpty()) dsEncounters[p[0]] = m
            }
        }
    }

    private fun saveDsEncounters() {
        runCatching {
            dsEncounterFile.parentFile?.mkdirs()
            writeWhole(dsEncounterFile) { w ->
                dsEncounters.forEach { (area, mons) ->
                    w.write(area.replace('	', ' ') + "	" + mons.entries.joinToString(",") { (sp, lv) -> sp.toString() + ":" + lv.joinToString("/") })
                    w.newLine()
                }
            }
        }
    }

    /** Tracker.TrackRouteEncounter: a wild species met in [area] of [mapId], once, in order. */
    fun seeOnRouteArea(mapId: Int, area: String, species: Int): Boolean {
        if (mapId <= 0 || species <= 0 || area.isBlank()) return false
        val added = routeAreaSeen.getOrPut("$mapId|$area") { LinkedHashSet() }.add(species)
        if (added) saveRoutes()
        return added
    }

    /** Tracker.getRouteEncounters: the species met in [area] of [mapId], in order of appearance. */
    fun seenOnRouteArea(mapId: Int, area: String): List<Int> = routeAreaSeen["$mapId|$area"]?.toList() ?: emptyList()

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
    fun notedSpecies(): Set<Int> = notes.filterValues { it.isNotBlank() }.keys
    fun movesSeenSpecies(): Set<Int> = movesSeen.filterValues { it.isNotEmpty() }.keys
    fun abilitySeenSpecies(): Set<Int> = abilitiesSeen.keys

    /** New Run: last attempt's notes describe a different randomization. */
    fun clear() {
        marks.clear()
        notes.clear()
        routeSeen.clear()
        routeAreaSeen.clear()
        dsEncounters.clear()
        runCatching { dsEncounterFile.delete() }
        movesSeen.clear()
        abilitiesSeen.clear()
        encWild.clear(); encTrainer.clear(); lastLevels.clear()
        dsTracked.clear()
        runCatching { dsTrackedFile.delete() }
        runCatching { if (encountersFile.exists()) encountersFile.writeText("") }
        safari.clear()
        runCatching { if (safariFile.exists()) safariFile.writeText("") }
        runCatching { if (file.exists()) file.writeText("") }
        runCatching { if (notesFile.exists()) notesFile.writeText("") }
        runCatching { if (routeFile.exists()) routeFile.writeText("") }
        runCatching { if (movesFile.exists()) movesFile.writeText("") }
        runCatching { if (abilitiesFile.exists()) abilitiesFile.writeText("") }
    }
}
