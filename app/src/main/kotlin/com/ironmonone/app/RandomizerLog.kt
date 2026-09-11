package com.ironmonone.app

import java.io.File

/**
 * The randomizer's log, parsed into what the game-over screen's "Inspect the
 * log" shows: the reference's data/RandomizerLog.lua, ported.
 *
 * The log is what the ZX engine (and the Nat. Dex fork) writes as it
 * randomizes: three header lines (version, seed, settings string), then
 * sections each opened by a `--Name--` line. The reference locates each
 * section by that header and reads the lines under it with one pattern per
 * section; the patterns here are the same ones, written as Kotlin regexes, and
 * every one was checked against a log of each family (Gen 1, 2, 3, 4 and 5)
 * produced by the bundled engine on 2026-09-07. Gen 1 has five stats and no
 * abilities, Gen 2 has no abilities, Gen 5 has a third ability: the base stats
 * row is read by its header's column names so all five shapes land in one
 * type.
 *
 * Nothing here is game data. Every name is the log's own spelling (the
 * reference's UsePokemonNamesFromLog), which is also the one the game shows.
 */
class RandomizerLog private constructor(
    val version: String,
    val seed: String,
    val settingsString: String,
    val game: String,
    val pokemon: List<Pokemon>,
    val tms: List<Tm>,
    val trainers: List<Trainer>,
    val routes: List<RouteSet>,
    val starters: List<String>,
    val statics: List<Pair<String, String>>,
    val pickup: List<String>,
) {
    class Pokemon(
        val id: Int,
        val name: String,
        val types: List<String>,
        /** HP, ATK, DEF, SPA, SPD, SPE; Gen 1 carries HP, ATK, DEF, SPE, SPEC (five). */
        val statNames: List<String>,
        val stats: List<Int>,
        val abilities: List<String>,
        val item: String,
    ) {
        val bst: Int get() = stats.sum()
        var evolutions: List<String> = emptyList(); internal set
        /** Level-up moves in order: level to move. */
        var moves: List<Pair<Int, String>> = emptyList(); internal set
        var evoMoves: List<String> = emptyList(); internal set
        var eggMoves: List<String> = emptyList(); internal set
        var tmsLearnable: List<Int> = emptyList(); internal set
        /**
         * The ability columns as the log writes them, a slot with none as "---"
         * (AbilityData's entry 0): the DS viewer numbers them and marks the third
         * as the hidden ability, so an empty second slot has to keep its place.
         */
        var abilitySlots: List<String> = emptyList(); internal set
    }

    class Tm(val number: Int, val move: String)

    class PartyMon(val name: String, val level: Int, val item: String?)

    class Trainer(val number: Int, val originalName: String, val name: String, val party: List<PartyMon>) {
        val fullName: String get() = if (name.isBlank()) originalName else name
        /** RandomizerLog.splitTrainerClassAndName on the game's name: "LEADER ROXANNE" is LEADER and ROXANNE. */
        val cls: String get() = splitClassAndName(originalName).first
        val shortName: String get() = splitClassAndName(originalName).second
        /** The same split of the randomizer's custom name ("Chief Kate"). */
        val customCls: String get() = splitClassAndName(name).first
        val customShortName: String get() = splitClassAndName(name).second
        /** RandomizerLog's trainer.maxlevel, which the Rival and Boss filters sort by. */
        val maxLevel: Int get() = party.maxOfOrNull { it.level } ?: 0
    }

    class Encounter(val name: String, val minLevel: Int, val maxLevel: Int)

    class RouteSet(val number: Int, val name: String, val rate: Int, val encounters: List<Encounter>)

    fun pokemonNamed(name: String): Pokemon? =
        pokemon.firstOrNull { it.name.equals(name, ignoreCase = true) }
            ?: NAME_ALIASES[name.trim().uppercase()]?.let { alias -> pokemon.firstOrNull { it.name.equals(alias, ignoreCase = true) } }

    /**
     * RandomizerLog.parseTrainers' moveIds: a Pokemon forgets its oldest move
     * first, so the four it knows at [level] are the last four it learned at or
     * below it, in the order it learned them.
     */
    fun movesAt(p: Pokemon, level: Int): List<String> {
        val out = ArrayList<String>()
        for ((lv, mv) in p.moves.asReversed()) {
            if (lv <= level) { out.add(0, mv); if (out.size >= 4) break }
        }
        return out
    }

    companion object {
        /**
         * Names a log uses for a Pokemon its own stats table lists under another name.
         * Black and White 2's trainers carry "Keldeo-R" where the table says Keldeo;
         * the DS tracker's parser maps it the same way (pokemonIDMappings["keldeo-r"] = 647).
         */
        private val NAME_ALIASES = mapOf("KELDEO-R" to "KELDEO")

        /**
         * RandomizerLog.splitTrainerClassAndName: the last word is the name, except
         * that a couple ("YOUNG COUPLE GIA & JES") keeps both names and "LT. SURGE"
         * keeps two words.
         */
        fun splitClassAndName(full: String): Pair<String, String> {
            val f = full.trim()
            val re = when {
                f.contains("&") -> Regex("^(.*?)\\s*(\\S+\\s*&\\s*\\S+)$")
                f.contains("Lt. ", ignoreCase = true) -> Regex("^(.*?)\\s*(\\S+\\s\\S+)$")
                else -> Regex("^(.*?)\\s*(\\S+)$")
            }
            val m = re.find(f) ?: return "" to f
            return m.groupValues[1].trim() to m.groupValues[2].trim()
        }

        fun parse(file: File): RandomizerLog? = runCatching { parse(file.readText(Charsets.UTF_8)) }.getOrNull()

        fun parse(text: String): RandomizerLog {
            // A BOM on line one is what the engine writes; strip it or "Randomizer Version" never matches.
            val lines = text.removePrefix("\uFEFF").split('\n').map { it.trimEnd('\r') }
            fun section(name: String): Int {
                val i = lines.indexOfFirst { it.trim() == "--$name--" }
                return if (i < 0) -1 else i + 1
            }
            fun body(start: Int): List<String> {
                if (start < 0) return emptyList()
                val out = ArrayList<String>()
                var i = start
                while (i < lines.size && !lines[i].trim().let { it.startsWith("--") && it.endsWith("--") && it.length > 4 } && !lines[i].startsWith("-----")) {
                    out += lines[i]; i++
                }
                return out
            }

            val version = Regex("Randomizer Version:\\s*(\\S+)").find(lines.getOrNull(0) ?: "")?.groupValues?.get(1) ?: ""
            val seed = Regex("^Random Seed:\\s*(\\d+)").find(lines.getOrNull(1) ?: "")?.groupValues?.get(1) ?: ""
            val settings = Regex("^Settings String:\\s*(.+)$").find(lines.getOrNull(2) ?: "")?.groupValues?.get(1)?.trim() ?: ""
            val game = lines.firstOrNull { it.startsWith("Randomization of ") }
                ?.let { Regex("^Randomization of\\s*(.+?)\\s+completed").find(it)?.groupValues?.get(1) } ?: ""

            // Base stats: read by the header's column names so every generation's shape fits.
            val pokemon = ArrayList<Pokemon>()
            val byName = HashMap<String, Pokemon>()
            body(section("Pokemon Base Stats & Types")).let { rows ->
                val header = rows.firstOrNull { it.trimStart().startsWith("NUM|") } ?: return@let
                val cols = header.split('|').map { it.trim() }
                val statCols = cols.filter { it in STAT_COLUMNS }
                for (row in rows) {
                    if (row === header || row.isBlank()) continue
                    val cells = row.split('|').map { it.trim() }
                    if (cells.size < cols.size - 1) continue
                    val id = cells[0].toIntOrNull() ?: continue
                    fun cell(col: String) = cols.indexOf(col).takeIf { it >= 0 && it < cells.size }?.let { cells[it] } ?: ""
                    val stats = statCols.map { cell(it).toIntOrNull() ?: 0 }
                    val abilities = listOf("ABILITY1", "ABILITY2", "ABILITY3").map { cell(it) }
                        .filter { it.isNotBlank() && !it.all { c -> c == '-' } }
                    val p = Pokemon(
                        id = id, name = cell("NAME"),
                        types = cell("TYPE").split('/').map { it.trim() }.filter { it.isNotEmpty() },
                        statNames = statCols.map { STAT_LABELS[it] ?: it }, stats = stats,
                        abilities = abilities, item = cell("ITEM"),
                    )
                    p.abilitySlots = listOf("ABILITY1", "ABILITY2", "ABILITY3").filter { it in cols }
                        .map { cell(it).let { a -> if (a.isBlank() || a.all { c -> c == '-' }) "---" else a } }
                    pokemon += p; byName[p.name.uppercase()] = p
                }
            }

            // Evolutions: "BULBASAUR -> NIDORINA" or "EEVEE -> A, B, C".
            for (row in body(section("Randomized Evolutions"))) {
                val m = Regex("^(.+?)\\s*->\\s*(.+)$").find(row.trim()) ?: continue
                byName[m.groupValues[1].trim().uppercase()]?.evolutions =
                    m.groupValues[2].split(',').map { it.trim() }.filter { it.isNotEmpty() }
            }

            // Movesets: "001 BULBASAUR -> NIDORINA" opens a block; "Level 10: MOVE", "Learned upon evolution: MOVE", "Egg Moves:" then " - MOVE".
            run {
                var cur: Pokemon? = null
                val moves = ArrayList<Pair<Int, String>>(); val evo = ArrayList<String>(); val egg = ArrayList<String>()
                var inEgg = false
                fun flush() { cur?.let { it.moves = moves.toList(); it.evoMoves = evo.toList(); it.eggMoves = egg.toList() }; moves.clear(); evo.clear(); egg.clear(); inEgg = false }
                for (row in body(section("Pokemon Movesets"))) {
                    val open = Regex("^\\d+\\s+(.+?)\\s*->").find(row)
                    if (open != null) { flush(); cur = byName[open.groupValues[1].trim().uppercase()]; continue }
                    val lv = Regex("^Level\\s+(\\d+)\\s*:\\s*(.+)$").find(row.trim())
                    if (lv != null) { moves += lv.groupValues[1].toInt() to lv.groupValues[2].trim(); inEgg = false; continue }
                    val ev = Regex("^Learned upon evolution:\\s*(.+)$").find(row.trim())
                    if (ev != null) { evo += ev.groupValues[1].trim(); continue }
                    if (row.trim() == "Egg Moves:") { inEgg = true; continue }
                    if (inEgg && row.trim().startsWith("- ")) egg += row.trim().removePrefix("- ").trim()
                }
                flush()
            }

            val tms = body(section("TM Moves")).mapNotNull { row ->
                Regex("^TM(\\d+)\\s+(.+)$").find(row.trim())?.let { Tm(it.groupValues[1].toInt(), it.groupValues[2].trim()) }
            }
            for (row in body(section("TM Compatibility"))) {
                val m = Regex("^\\s*\\d+\\s+(.+?)\\s*\\|(.*)$").find(row) ?: continue
                byName[m.groupValues[1].trim().uppercase()]?.tmsLearnable =
                    Regex("TM(\\d+)").findAll(m.groupValues[2]).map { it.groupValues[1].toInt() }.toList()
            }

            // Trainers: "#12 (HIKER SAWYER => Designer Jill)@310058 - SNEASEL@NUGGET Lv32, XATU Lv47"
            val trainers = body(section("Trainers Pokemon")).mapNotNull { row ->
                val m = Regex("^#(\\d+)\\s+\\(([^)]*)\\)\\S*\\s+-\\s+(.*)$").find(row.trim()) ?: return@mapNotNull null
                val names = m.groupValues[2].split("=>").map { it.trim() }
                val party = m.groupValues[3].split(',').mapNotNull { part ->
                    val pm = Regex("^\\s*(.+?)(?:@(.+?))?\\s+Lv(\\d+)\\s*$").find(part) ?: return@mapNotNull null
                    PartyMon(pm.groupValues[1].trim(), pm.groupValues[3].toInt(), pm.groupValues[2].takeIf { it.isNotBlank() })
                }
                Trainer(m.groupValues[1].toInt(), names[0], names.getOrNull(1) ?: "", party)
            }

            // Wild: "Set #1 - ROUTE 101 Grass/Cave (rate=20)" then "CASTFORM Lv3 ..." or "Buneary Lvs 68-90 ...".
            val routes = ArrayList<RouteSet>()
            run {
                var num = 0; var name = ""; var rate = 0
                var enc = ArrayList<Encounter>()
                fun flush() { if (num > 0) routes += RouteSet(num, name, rate, enc.toList()); enc = ArrayList() }
                for (row in body(section("Wild Pokemon"))) {
                    val head = Regex("^Set #(\\d+)\\s*-\\s*(.+?)\\s*\\(rate=(\\d+)\\)").find(row.trim())
                    if (head != null) { flush(); num = head.groupValues[1].toInt(); name = head.groupValues[2].trim(); rate = head.groupValues[3].toInt(); continue }
                    val e = Regex("^(.+?)\\s+Lvs?\\s*(\\d+)(?:-(\\d+))?").find(row.trim()) ?: continue
                    val lo = e.groupValues[2].toInt()
                    enc += Encounter(e.groupValues[1].trim(), lo, e.groupValues[3].toIntOrNull() ?: lo)
                }
                flush()
            }

            val starters = body(section("Random Starters")).mapNotNull { Regex("^Set starter \\d+ to (.+)$").find(it.trim())?.groupValues?.get(1)?.trim() }
            val statics = body(section("Static Pokemon")).mapNotNull { row ->
                Regex("^(.+?)\\s*=>\\s*(.+)$").find(row.trim())?.let { it.groupValues[1].trim() to it.groupValues[2].trim() }
            }
            val pickup = body(section("Pickup Items")).map { it.trim() }.filter { it.isNotEmpty() }

            return RandomizerLog(version, seed, settings, game, pokemon, tms, trainers, routes, starters, statics, pickup)
        }

        private val STAT_COLUMNS = listOf("HP", "ATK", "DEF", "SATK", "SDEF", "SPD", "SPE", "SPEC")
        private val STAT_LABELS = mapOf("HP" to "HP", "ATK" to "ATK", "DEF" to "DEF", "SATK" to "SPA", "SDEF" to "SPD", "SPD" to "SPE", "SPE" to "SPE", "SPEC" to "SPC")
    }
}
