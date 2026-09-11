package com.ironmonone.app

import com.ironmonone.tracker.nds.NdsLogData
import kotlin.math.floor

/**
 * What NDS-Ironmon-Tracker's LogViewer shows, built from the parsed log and
 * the game's tables (NdsLogData): LogViewerScreen.formatTrainerGroups,
 * RandomizerLogParser.parseRouteData, StatisticsOrganizer.createLogStatistics,
 * GymTMScreen, SearchScreen and TeamInfoScreen's rules, ported. Pure data, so
 * every rule is tested on real logs (DsLogTest); DsLogViewer draws it.
 */
class DsLog(
    val log: RandomizerLog,
    val game: NdsLogData.Game,
    starterNumber: Int,
    /** The tracker's name for a national number: the reference names every Pokemon from PokemonData. */
    private val speciesName: (Int) -> String? = { null },
) {
    /** LogInfo.getStarterNumber: 1, 2 or 3, which of the log's starters the run began with. */
    val starterNumber: Int = starterNumber.coerceIn(1, 3)

    /** One battle of a group, its team picked by the starter. [position] is its 1-based place in the group. */
    class Battle(val position: Int, val id: Int, val name: String, val location: String, val badge: Int?, val iv: Int)

    class Group(val index: Int, val name: String, val type: Int, val battles: List<Battle>) {
        val isRival: Boolean get() = type == NdsLogData.RIVAL
        /** TeamInfoScreen and convertTrainerGroupsToSortedIndices: a rival's fights are numbered, "Hugh 3". */
        fun battleName(b: Battle): String = if (isRival) "${b.name} ${b.position}" else b.name
    }

    /** TeamInfoScreen.formatPokemon: a trainer's Pokemon with its stats estimated from the trainer's IVs. */
    class TeamMon(
        val pokemon: RandomizerLog.Pokemon?,
        val name: String,
        val level: Int,
        val item: String?,
        val stats: List<Int>,
        val moves: List<String>,
        val ability: String,
    )

    class PivotMon(val pokemon: RandomizerLog.Pokemon, val minLevel: Int, val maxLevel: Int, val percent: Int)

    /** A Gym TMs row; [tm] -1 is HeartGold and SoulSilver's empty spacer. */
    class GymTm(val tm: Int, val move: String, val badgeSet: String, val badge: Int, val group: Group?, val leader: Battle?)

    class Statistic(val name: String, val description: String, val top: List<RandomizerLog.Pokemon>)

    class SearchRow(val pokemon: RandomizerLog.Pokemon, val label: String)

    /** A trainer with a match; [found] are the matching team members, 0-based. */
    class TrainerMatch(val group: Group, val battle: Battle, val found: List<Int>) {
        val battleName: String get() = group.battleName(battle)
    }

    private val maxNational = if (game.gen == 5) 649 else 493

    val byId: Map<Int, RandomizerLog.Pokemon> = log.pokemon.associateBy { it.id }

    /** The name the reference shows (PokemonData's, "Lucario"): the game's own or, for a form past the national numbers, the log's, title-cased either way (HeartGold's ROM spells them in capitals). */
    fun nameOf(p: RandomizerLog.Pokemon): String = logTitle(
        (if (p.id in 1..maxNational) speciesName(p.id) else null)?.takeIf { it.isNotBlank() && !it.startsWith("#") } ?: p.name,
    )

    /** MiscUtils.sortPokemonIDsByName: the Pokemon tab's order, and the stat page's arrows. */
    val sortedPokemon: List<RandomizerLog.Pokemon> = log.pokemon.sortedWith(compareBy({ nameOf(it) }, { it.id }))

    private val trainersById = log.trainers.associateBy { it.number }

    /**
     * LogViewerScreen.formatTrainerGroups: Black and White's first gym is one of
     * three leaders picked by the starter, a rival's team is one of three the
     * same way, and a rival's battles carry the group's name.
     */
    val groups: List<Group> = NdsLogData.groupRows(game.trainerTable).groupBy { it.group }.toSortedMap().map { (gi, rows) ->
        val first = rows.first()
        val rival = first.type == NdsLogData.RIVAL
        val battles = rows.groupBy { it.battle }.toSortedMap().map { (bi, alts) ->
            val r = if (alts.size == 3) alts.sortedBy { it.alt }[this.starterNumber - 1] else alts.first()
            val id = if (r.ids.size == 3) r.ids[this.starterNumber - 1] else r.ids.firstOrNull() ?: 0
            Battle(bi, id, if (rival) first.groupName else r.label, if (rival) r.label else "", r.badge, r.iv)
        }
        Group(gi, first.groupName, first.type, battles)
    }

    fun team(b: Battle): List<RandomizerLog.PartyMon> = trainersById[b.id]?.party ?: emptyList()

    fun badgeSetFor(g: Group): String = game.badgePrefix + if (g.name == "Kanto Gyms") "_K" else ""

    private fun slotsOf(p: RandomizerLog.Pokemon): List<String> = p.abilitySlots.ifEmpty { p.abilities }

    fun teamMon(b: Battle, m: RandomizerLog.PartyMon): TeamMon {
        val p = log.pokemonNamed(m.name)
        val stats = STAT_KEYS.map { k -> if (p == null) 0 else estimateStat(k, LogSearch.statOf(p, k), m.level, b.iv) }
        return TeamMon(
            p, p?.let { nameOf(it) } ?: logTitle(m.name), m.level, m.item, stats,
            if (p == null) emptyList() else log.movesAt(p, m.level),
            // formatPokemon: pokemon.ability = abilities[1].
            p?.let { slotsOf(it).firstOrNull() } ?: "---",
        )
    }

    /** PokemonStatScreen.readAbilitiesIntoUI: "1. X", "2. X", and on Gen 5 "3. X (HA)". */
    fun abilityLines(p: RandomizerLog.Pokemon): List<String> {
        val slots = slotsOf(p)
        return (0 until if (game.gen == 5) 3 else 2).mapNotNull { i ->
            slots.getOrNull(i)?.let { "${i + 1}. $it" + if (i == 2) " (HA)" else "" }
        }
    }

    /**
     * PokemonStatScreen.readCurrentEvoIntoUI's label: the species' own evolution
     * method, its long name for this evolution when it has several, "Level N"
     * for a level, "---" for none.
     */
    fun evoText(p: RandomizerLog.Pokemon, index: Int): String {
        val code = if (p.id in 1..maxNational) NdsLogData.evoMethod(p.id) else ""
        if (code.isEmpty()) return "---"
        val t = NdsLogData.evoNames[code]?.let { it.getOrNull(index) ?: return "" } ?: code
        return if (t.toIntOrNull() != null) "Level $t" else t
    }

    fun tmMove(tm: Int): String = log.tms.firstOrNull { it.number == tm }?.move ?: ""

    /** GymTMScreen: each gym TM with its badge and leader; past the spacer, Kanto's badges and the second gym group. */
    val gymTms: List<GymTm> = run {
        val leaders = groups.filter { it.type == NdsLogData.GYM_LEADERS }
        game.gymTms.mapIndexed { i, tm ->
            val index = i + 1
            if (tm == -1) GymTm(-1, "", "", 0, null, null)
            else {
                val kanto = index > 9
                val badge = if (kanto) index - 9 else index
                val g = leaders.getOrNull(if (kanto) 1 else 0)
                GymTm(tm, tmMove(tm), game.badgePrefix + if (kanto) "_K" else "", badge, g, g?.battles?.getOrNull(badge - 1))
            }
        }
    }

    /** StatisticsOrganizer.createLogStatistics: six lists, the ten best (or worst) by a sum of stats. */
    val statistics: List<Statistic> = STATISTICS.map { (name, keys, descending) ->
        val sum = { p: RandomizerLog.Pokemon -> keys.sumOf { LogSearch.statOf(p, it) } }
        val sorted = if (descending) log.pokemon.sortedByDescending(sum) else log.pokemon.sortedBy(sum)
        Statistic(name, DESCRIPTIONS.getValue(name), sorted.take(10))
    }

    /** MiscUtils.convertTrainerGroupsToSortedIndices: every battle, by the name Search shows. */
    val sortedTrainers: List<Pair<Group, Battle>> =
        groups.flatMap { g -> g.battles.map { g to it } }.sortedBy { (g, b) -> g.battleName(b) }

    /** SearchScreen.findPokemonWithMove: one row per time it learns the move, with the levels it keeps it. */
    fun pokemonWithMove(move: String): List<SearchRow> {
        val key = norm(move)
        return sortedPokemon.flatMap { p ->
            p.moves.indices.filter { norm(p.moves[it].second) == key }.map { SearchRow(p, levelRange(it, p.moves)) }
        }
    }

    /** SearchScreen.findPokemonWithAbility and getAbilityText. */
    fun pokemonWithAbility(ability: String): List<SearchRow> {
        val key = norm(ability)
        return sortedPokemon.filter { p -> slotsOf(p).any { norm(it) == key } }.map { p ->
            val total = slotsOf(p).count { it != "---" }
            SearchRow(p, if (total > 1) "1 of $total abilities" else "Only ability")
        }
    }

    /** SearchScreen.findTrainersWithMatch: a move counts only among the four a team member knows at its level. */
    fun trainersWith(name: String, move: Boolean): List<TrainerMatch> {
        val key = norm(name)
        return sortedTrainers.mapNotNull { (g, b) ->
            val team = team(b)
            val found = team.indices.filter { i ->
                val p = log.pokemonNamed(team[i].name) ?: return@filter false
                if (move) log.movesAt(p, team[i].level).any { norm(it) == key } else slotsOf(p).any { norm(it) == key }
            }
            if (found.isEmpty()) null else TrainerMatch(g, b, found)
        }
    }

    /** LocationData encounterAreaOrder: the Pivots tab's areas. */
    val pivotAreas: List<String> = NdsLogData.pivotAreas(game.locationTable)

    /** RandomizerLogParser.parseRouteData: area to encounter type to its Pokemon, most likely first. */
    val pivots: Map<String, Map<String, List<PivotMon>>> = buildPivots()

    private fun buildPivots(): Map<String, Map<String, List<PivotMon>>> {
        val out = LinkedHashMap<String, LinkedHashMap<String, List<PivotMon>>>()
        val vg = game.versionGroup
        var sprout = 0
        for (set in log.routes) {
            for (type in game.pivotTypes) {
                if (!set.name.contains(type)) continue
                if (type == "Bug Catching") {
                    // readBugCatchingEntry: the post-National Dex contests only, one per day.
                    if (set.name.contains("Pre-National")) continue
                    for (day in BUG_DAYS) if (set.name.contains(day)) {
                        out.getOrPut("Bug Catching") { LinkedHashMap() }[day] = read(set.encounters, BUG_CATCHING)
                    }
                    continue
                }
                var area = Regex("^(.+) " + Regex.escape(type)).find(set.name)?.groupValues?.get(1) ?: continue
                if (area == "Sprout Tower") { sprout++; area = "$area ${sprout}F" }
                val number = set.number.toString()
                val validRoute = ROUTE_NAME_TO_CORRECT_SET[vg]?.get(area)?.get(type)?.let { it == number } ?: true
                val excluded = EXCLUDED_ROUTE_PIVOTS[vg]?.get(area)?.contains(type) == true
                val shown = formatPivotType(area, number, type)
                area = ROUTE_NUMBER_TO_CORRECT_NAME[vg]?.get(number) ?: area
                if (area !in pivotAreas || !validRoute || excluded) continue
                val byType = out.getOrPut(area) { LinkedHashMap() }
                if (shown != "Headbutt") {
                    byType[shown] = read(set.encounters, if (shown == "Old Rod") OLD_ROD else STANDARD)
                } else {
                    // Common trees are the first six lines, rare trees the six after.
                    byType["Headbutt(C)"] = read(set.encounters, HEADBUTT)
                    byType["Headbutt(R)"] = read(set.encounters.drop(6), HEADBUTT)
                }
            }
        }
        return out
    }

    /** readStandardEncounter / readNonstandardEncounter: each line is a slot; a species' slots add up. */
    private fun read(encounters: List<RandomizerLog.Encounter>, percents: IntArray): List<PivotMon> {
        val acc = LinkedHashMap<Int, PivotMon>()
        encounters.forEachIndexed { i, e ->
            val pct = percents.getOrNull(i) ?: return@forEachIndexed
            val p = log.pokemonNamed(e.name) ?: return@forEachIndexed
            val had = acc[p.id]
            acc[p.id] = if (had == null) PivotMon(p, e.minLevel, e.maxLevel, pct)
            else PivotMon(p, minOf(had.minLevel, e.minLevel), maxOf(had.maxLevel, e.maxLevel), had.percent + pct)
        }
        // PivotsScreen.sortIDs: most likely first, then the lower levels.
        return acc.values.sortedWith(compareByDescending<PivotMon> { it.percent }.thenBy { it.minLevel }.thenBy { it.maxLevel })
    }

    companion object {
        val STAT_KEYS = listOf("HP", "ATK", "DEF", "SPA", "SPD", "SPE")

        /** How move and ability names from the log and the game's tables are compared. */
        fun norm(s: String): String = s.lowercase().filter { it.isLetterOrDigit() }

        /** TeamInfoScreen.estimateStat. */
        fun estimateStat(stat: String, base: Int, level: Int, iv: Int): Int {
            val core = (iv + 2.0 * base) * level / 100.0
            return floor(core + (if (stat == "HP") 10.0 + level else 5.0) + 0.5).toInt()
        }

        /** MoveUtils.calculateLevelRangeOfMove: kept until the fourth move after it replaces it. */
        fun levelRange(index: Int, moves: List<Pair<Int, String>>): String {
            val start = moves[index].first
            var end = moves.getOrNull(index + 4)?.let { it.first - 1 } ?: 100
            if (end < start) end = start
            return "Lv. $start - $end"
        }

        /**
         * LogInfo.setStarterNumberFromPlayerPokemonID: which "Set starter N" the
         * run began with. The first Pokemon the tracker saw comes first; the
         * party after it (and what each evolved from) covers a run the tracker
         * joined late. 1 when nothing matches, as the reference defaults.
         */
        fun starterNumber(log: RandomizerLog, candidates: List<Int>): Int {
            val starters = log.starters.mapIndexedNotNull { i, n -> log.pokemonNamed(n)?.let { it.id to i + 1 } }.toMap()
            for (id in candidates) {
                var cur: RandomizerLog.Pokemon? = log.pokemon.firstOrNull { it.id == id }
                var steps = 0
                while (cur != null && steps < 3) {
                    starters[cur.id]?.let { return it }
                    val name = cur.name
                    cur = log.pokemon.firstOrNull { p -> p.evolutions.any { it.equals(name, ignoreCase = true) } }
                    steps++
                }
            }
            return 1
        }

        /** RandomizerLogParser.checkGameName: "Randomization of Pokemon Black 2 (U) completed." is "Pokemon Black 2". */
        fun logGameName(log: RandomizerLog): String =
            Regex("^(Pokemon [A-Za-z0-9 ]+)").find(log.game)?.groupValues?.get(1)?.trim() ?: ""

        val ENCOUNTER_TYPES = listOf(
            "Grass", "Ext. Grass", "Int. Grass", "Cave", "Shaking Spots", "Old Rod",
            "Headbutt(C)", "Headbutt(R)", "Dark Grass", "Tuesday", "Thursday", "Saturday",
        )

        private val STANDARD = intArrayOf(20, 20, 10, 10, 10, 10, 5, 5, 4, 4, 1, 1)
        private val OLD_ROD = intArrayOf(60, 30, 5, 4, 1)
        private val HEADBUTT = intArrayOf(50, 15, 15, 10, 5, 5)
        private val BUG_CATCHING = intArrayOf(20, 20, 10, 10, 10, 10, 5, 5, 5, 5)
        private val BUG_DAYS = listOf("Tuesday", "Thursday", "Saturday")

        /** By version group: an area listed twice keeps only this set. */
        private val ROUTE_NAME_TO_CORRECT_SET = mapOf(
            1 to mapOf("Lake Verity" to mapOf("Grass/Cave" to "341"), "Route 204" to mapOf("Grass/Cave" to "379", "Old Rod" to "382")),
            2 to mapOf("Lake Verity" to mapOf("Grass/Cave" to "345"), "Route 204" to mapOf("Grass/Cave" to "383", "Old Rod" to "386")),
            3 to mapOf("Ruins of Alph" to mapOf("Grass/Cave" to "68"), "Dark Cave" to mapOf("Grass/Cave" to "365")),
            4 to mapOf("Wellspring Cave" to mapOf("Grass/Cave" to "181", "Shaking Spots" to "182")),
        )
        private val EXCLUDED_ROUTE_PIVOTS = mapOf(
            1 to mapOf("Route 218" to setOf("Grass/Cave")),
            2 to mapOf("Route 218" to setOf("Grass/Cave")),
        )
        private val DUPLICATE_ROUTE_TO_NEW_PIVOT_TYPE = mapOf("142" to "Ext. Grass", "148" to "Int. Grass")
        private val ROUTE_NUMBER_TO_CORRECT_NAME = mapOf(
            4 to mapOf(
                "18" to "Pinwheel Exterior", "19" to "Pinwheel Exterior", "20" to "Pinwheel Exterior",
                "21" to "Pinwheel Interior", "22" to "Pinwheel Interior", "23" to "Pinwheel Interior",
            ),
        )

        /** formatPivotType: Doubles Grass is Dark Grass, and "Grass/Cave" is whichever the area is. */
        fun formatPivotType(area: String, number: String, type: String): String {
            DUPLICATE_ROUTE_TO_NEW_PIVOT_TYPE[number]?.let { return it }
            val t = type.replace("Doubles Grass", "Dark Grass")
            return if (!area.contains("Cave")) t.replace("/Cave", "") else t.replace("Grass/", "")
        }

        private val STATISTICS = listOf(
            Triple("Best Special Attackers", listOf("SPA", "SPE"), true),
            Triple("Best Physical Attackers", listOf("ATK", "SPE"), true),
            Triple("Biggest Special Walls", listOf("HP", "SPD"), true),
            Triple("Best Defensive Tanks", listOf("HP", "DEF"), true),
            Triple("Bulkiest Overall", listOf("HP", "DEF", "SPD"), true),
            Triple("Most Frail", listOf("HP", "DEF", "SPD"), false),
        )
        private val DESCRIPTIONS = mapOf(
            "Best Special Attackers" to "The highest amount of Special Attack and Speed.",
            "Best Physical Attackers" to "The highest amount of Attack and Speed.",
            "Biggest Special Walls" to "The highest amount of HP and Special Defense.",
            "Best Defensive Tanks" to "The highest amount of HP and Defense.",
            "Bulkiest Overall" to "The highest amount of HP, Defense, and Special Defense.",
            "Most Frail" to "The worst amount of HP, Defense and Special Defense.",
        )
    }
}
