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
    /**
     * The passes run beside the one this log describes, in the order they were applied: the 60% levels' pre-pass
     * before it, Gen 1's PART 2 after it (rc32 audit P2 #69). Empty for a run of one pass.
     */
    val passes: List<Pass> = emptyList(),
    /** Lines the app wrote into the log's header, without their "KaizoCore: " mark (rc32 audit P3 #90). */
    val notes: List<String> = emptyList(),
    /**
     * The randomizer left base stats, types and abilities as the game has them, so the log has no table of them
     * (Build your own from "The game as it is"); [pokemon] is then read from the moveset blocks, or empty when those
     * are unchanged too (rc32 audit P2 #71).
     */
    val statsUnchanged: Boolean = false,
) {
    /**
     * One pass of a run made in more than one: its file, its seed as Premade Seed takes it (decimal), and its
     * settings string, empty when the log did not keep it (a 60% levels log written before rc34).
     */
    class Pass(val label: String, val file: String, val seed: String, val settingsString: String, val before: Boolean)

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

    /**
     * The log's Pokemon by upper-case name, the first of a name kept as the scan that came before it kept it: it was a
     * linear search, run for every wild encounter and party member the viewer draws (rc32 audit P2 #27).
     */
    private val byName: Map<String, Pokemon> by lazy {
        LinkedHashMap<String, Pokemon>().also { m -> for (p in pokemon) m.putIfAbsent(p.name.uppercase(), p) }
    }

    fun pokemonNamed(name: String): Pokemon? =
        byName[name.uppercase()] ?: NAME_ALIASES[name.trim().uppercase()]?.let { byName[it] }

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

        // Every pattern is made once, here: parse built each one again for every row it read, tens of thousands for a
        // DS log, on the main thread when Inspect the log opened (rc32 audit P2 #27).
        private val COUPLE = Regex("^(.*?)\\s*(\\S+\\s*&\\s*\\S+)$")
        private val TWO_WORDS = Regex("^(.*?)\\s*(\\S+\\s\\S+)$")
        private val LAST_WORD = Regex("^(.*?)\\s*(\\S+)$")
        private val VERSION = Regex("Randomizer Version:\\s*(\\S+)")
        private val SEED = Regex("^Random Seed:\\s*(-?\\d+)")
        private val SETTINGS = Regex("^Settings String:\\s*(.+)$")
        private val GAME = Regex("^Randomization of\\s*(.+?)\\s+completed")
        private val EVOLUTION = Regex("^(.+?)\\s*->\\s*(.+)$")
        private val MOVESET_OPEN = Regex("^(\\d+)\\s+(.+?)\\s*->")
        private val MOVESET_STAT = Regex("^(HP|ATK|DEF|SPA|SPD|SPE|SPEC)\\s+(\\d+)$")
        private val LEVEL_MOVE = Regex("^Level\\s+(\\d+)\\s*:\\s*(.+)$")
        private val EVO_MOVE = Regex("^Learned upon evolution:\\s*(.+)$")
        private val TM_ROW = Regex("^TM(\\d+)\\s+(.+)$")
        private val TM_COMPAT = Regex("^\\s*\\d+\\s+(.+?)\\s*\\|(.*)$")
        private val TM_NUMBER = Regex("TM(\\d+)")
        private val TRAINER = Regex("^#(\\d+)\\s+\\(([^)]*)\\)\\S*\\s+-\\s+(.*)$")
        private val PARTY_MON = Regex("^\\s*(.+?)(?:@(.+?))?\\s+Lv(\\d+)\\s*$")
        private val WILD_SET = Regex("^Set #(\\d+)\\s*-\\s*(.+?)\\s*\\(rate=(\\d+)\\)")
        private val WILD_MON = Regex("^(.+?)\\s+Lvs?\\s*(\\d+)(?:-(\\d+))?")
        private val STARTER = Regex("^Set starter \\d+ to (.+)$")
        private val STATIC = Regex("^(.+?)\\s*=>\\s*(.+)$")
        // The passes (Randomizers.twoPass and withPrePass): their markers, and the 60% levels' trailer sentence.
        private val PART_TWO = Regex("^== PART 2: (.+) \\(seed ([0-9a-fA-F]{16})\\) ==$")
        private val PRE_PASS = Regex("^== PRE-PASS: (.+) \\(seed ([0-9a-fA-F]{16})\\) ==$")
        private val SIXTY = Regex("^60% levels: \"(.+?)\" raised trainer and wild levels 6% first \\(seed ([0-9a-fA-F]{16})\\), then \"(.+?)\" ran")

        /** The mark of a line the app wrote into a log's header ([notes]). */
        const val NOTE = "KaizoCore: "

        /** The engine's line for a log with no base stats table (Randomizer.java). */
        private const val STATS_UNCHANGED = "Pokemon base stats & type: unchanged"

        /**
         * RandomizerLog.splitTrainerClassAndName: the last word is the name, except
         * that a couple ("YOUNG COUPLE GIA & JES") keeps both names and "LT. SURGE"
         * keeps two words.
         */
        fun splitClassAndName(full: String): Pair<String, String> {
            val f = full.trim()
            val re = when {
                f.contains("&") -> COUPLE
                f.contains("Lt. ", ignoreCase = true) -> TWO_WORDS
                else -> LAST_WORD
            }
            val m = re.find(f) ?: return "" to f
            return m.groupValues[1].trim() to m.groupValues[2].trim()
        }

        /**
         * The last logs read, by path, kept while the file's size and time stay the same: Inspect the log and Play's
         * Open Book read the same log, and a 2.4 MB Black 2 log was parsed again each time (rc32 audit P2 #27).
         */
        private val cache = LinkedHashMap<String, Pair<String, RandomizerLog>>()
        private const val CACHED = 2

        fun parse(file: File): RandomizerLog? {
            val key = file.absolutePath
            val stamp = "${file.length()} ${file.lastModified()}"
            synchronized(cache) { cache[key]?.let { (s, log) -> if (s == stamp) return log } }
            val log = runCatching { parse(file.readText(Charsets.UTF_8)) }.getOrNull() ?: return null
            synchronized(cache) {
                cache.remove(key)
                cache[key] = stamp to log
                while (cache.size > CACHED) cache.remove(cache.keys.first())
            }
            return log
        }

        /** The app's notes in a log file's header ([notes]), read without the rest of it: RunJob says them when a run is made. */
        fun notesOf(file: File): List<String> = runCatching {
            val bom = Char(0xFEFF).toString()
            file.useLines(Charsets.UTF_8) { seq ->
                seq.map { it.trimEnd('\r').removePrefix(bom) }.takeWhile { !isSection(it) }
                    .filter { it.startsWith(NOTE) }.map { it.removePrefix(NOTE).trim() }.filter { it.isNotEmpty() }.toList()
            }
        }.getOrDefault(emptyList())

        /** A section's header line, "--Name--"; a line of dashes is the log's tail, not a section. */
        private fun isSection(line: String): Boolean =
            line.trim().let { it.startsWith("--") && it.endsWith("--") && it.length > 4 && !it.all { c -> c == '-' } }

        /** The decimal seed Premade Seed takes, for a pass logged by its 16 hex digits. */
        private fun decimalSeed(hex: String): String = runCatching { java.lang.Long.parseUnsignedLong(hex, 16).toString() }.getOrDefault("")

        fun parse(text: String): RandomizerLog {
            // The engine writes a BOM at the start of its log, and a two-pass log holds one per pass: every line is read without it.
            val lines = text.split('\n').map { it.trimEnd('\r').removePrefix("\uFEFF") }
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

            // The header: the lines before the first section, each found by what it says. They were read from lines 1,
            // 2 and 3, and a two-pass log opens with "== PART 1: <file> ==", so all three read empty (rc32 audit P2 #69).
            val head = lines.subList(0, lines.indexOfFirst { isSection(it) }.let { if (it < 0) lines.size else it })
            val version = head.firstNotNullOfOrNull { VERSION.find(it)?.groupValues?.get(1) } ?: ""
            val seed = head.firstNotNullOfOrNull { SEED.find(it)?.groupValues?.get(1) } ?: ""
            val settings = head.firstNotNullOfOrNull { SETTINGS.find(it)?.groupValues?.get(1)?.trim() } ?: ""
            val notes = head.filter { it.startsWith(NOTE) }.map { it.removePrefix(NOTE).trim() }.filter { it.isNotEmpty() }
            val game = lines.firstOrNull { it.startsWith("Randomization of ") }
                ?.let { GAME.find(it)?.groupValues?.get(1) } ?: ""

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
            // With no table (the randomizer left stats, types and abilities alone) each moveset block makes its Pokemon,
            // since it repeats the stats: the Pokemon tab, the wild areas and the trainers' moves were all empty for such
            // a log (rc32 audit P2 #71). Types and abilities stay empty: the log does not give them.
            val fromMovesets = pokemon.isEmpty()

            // Movesets: "001 BULBASAUR -> NIDORINA" opens a block, then its stats ("HP  45"); "Level 10: MOVE",
            // "Learned upon evolution: MOVE", "Egg Moves:" then " - MOVE".
            run {
                var cur: Pokemon? = null
                var made: Pair<Int, String>? = null
                val stats = LinkedHashMap<String, Int>()
                val moves = ArrayList<Pair<Int, String>>(); val evo = ArrayList<String>(); val egg = ArrayList<String>()
                var inEgg = false
                fun flush() {
                    made?.let { (id, name) ->
                        // The table's own order: HP ATK DEF SPE SPC on Gen 1, HP ATK DEF SPA SPD SPE on the rest.
                        val order = if ("SPEC" in stats) listOf("HP", "ATK", "DEF", "SPE", "SPEC") else listOf("HP", "ATK", "DEF", "SPA", "SPD", "SPE")
                        // The block already names them as the viewer does (its SPD is Sp. Def, the table's is Speed); only SPEC is SPC.
                        val p = Pokemon(id, name, emptyList(), order.map { if (it == "SPEC") "SPC" else it }, order.map { stats[it] ?: 0 }, emptyList(), "")
                        // A second block of a name (the Nidoran forms) goes to the first, as the table's lookups do.
                        if (byName.putIfAbsent(name.uppercase(), p) == null) { pokemon += p; cur = p }
                    }
                    cur?.let { it.moves = moves.toList(); it.evoMoves = evo.toList(); it.eggMoves = egg.toList() }
                    moves.clear(); evo.clear(); egg.clear(); stats.clear(); inEgg = false; made = null; cur = null
                }
                for (row in body(section("Pokemon Movesets"))) {
                    val open = MOVESET_OPEN.find(row)
                    if (open != null) {
                        flush()
                        val name = open.groupValues[2].trim()
                        if (fromMovesets) made = (open.groupValues[1].toIntOrNull() ?: 0) to name else cur = byName[name.uppercase()]
                        continue
                    }
                    val line = row.trim()
                    val stat = if (fromMovesets) MOVESET_STAT.find(line) else null
                    if (stat != null) { stats[stat.groupValues[1]] = stat.groupValues[2].toInt(); continue }
                    val lv = LEVEL_MOVE.find(line)
                    if (lv != null) { moves += lv.groupValues[1].toInt() to lv.groupValues[2].trim(); inEgg = false; continue }
                    val ev = EVO_MOVE.find(line)
                    if (ev != null) { evo += ev.groupValues[1].trim(); continue }
                    if (line == "Egg Moves:") { inEgg = true; continue }
                    if (inEgg && line.startsWith("- ")) egg += line.removePrefix("- ").trim()
                }
                flush()
            }

            // Evolutions: "BULBASAUR -> NIDORINA", "GLOOM -> WEEZING and ARBOK" or "EEVEE -> A, B and C": the engines
            // join the last one with " and " (Randomizer.java), which a split on commas alone dropped (rc33 audit P1 #45).
            // Read after the movesets, so a Pokemon made from its block takes its evolutions too.
            for (row in body(section("Randomized Evolutions"))) {
                val m = EVOLUTION.find(row.trim()) ?: continue
                byName[m.groupValues[1].trim().uppercase()]?.evolutions =
                    m.groupValues[2].replace(" and ", ", ").split(',').map { it.trim() }.filter { it.isNotEmpty() }
            }

            val tms = body(section("TM Moves")).mapNotNull { row ->
                TM_ROW.find(row.trim())?.let { Tm(it.groupValues[1].toInt(), it.groupValues[2].trim()) }
            }
            for (row in body(section("TM Compatibility"))) {
                val m = TM_COMPAT.find(row) ?: continue
                byName[m.groupValues[1].trim().uppercase()]?.tmsLearnable =
                    TM_NUMBER.findAll(m.groupValues[2]).map { it.groupValues[1].toInt() }.toList()
            }

            // Trainers: "#12 (HIKER SAWYER => Designer Jill)@310058 - SNEASEL@NUGGET Lv32, XATU Lv47"
            val trainers = body(section("Trainers Pokemon")).mapNotNull { row ->
                val m = TRAINER.find(row.trim()) ?: return@mapNotNull null
                val names = m.groupValues[2].split("=>").map { it.trim() }
                val party = m.groupValues[3].split(',').mapNotNull { part ->
                    val pm = PARTY_MON.find(part) ?: return@mapNotNull null
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
                    val set = WILD_SET.find(row.trim())
                    if (set != null) { flush(); num = set.groupValues[1].toInt(); name = set.groupValues[2].trim(); rate = set.groupValues[3].toInt(); continue }
                    val e = WILD_MON.find(row.trim()) ?: continue
                    val lo = e.groupValues[2].toInt()
                    enc += Encounter(e.groupValues[1].trim(), lo, e.groupValues[3].toIntOrNull() ?: lo)
                }
                flush()
            }

            // The engines write one of three headers, by how the starters were chosen: picked in Build your own is Custom,
            // and every IronMON Journey preset is 2-Evolution (rc33 audit P1 #46). The first that lists any is the one.
            val starters = listOf("Random Starters", "Custom Starters", "Random 2-Evolution Starters").asSequence()
                .map { h -> body(section(h)).mapNotNull { STARTER.find(it.trim())?.groupValues?.get(1)?.trim() } }
                .firstOrNull { it.isNotEmpty() } ?: emptyList()
            val statics = body(section("Static Pokemon")).mapNotNull { row ->
                STATIC.find(row.trim())?.let { it.groupValues[1].trim() to it.groupValues[2].trim() }
            }
            val pickup = body(section("Pickup Items")).map { it.trim() }.filter { it.isNotEmpty() }

            return RandomizerLog(
                version, seed, settings, game, pokemon, tms, trainers, routes, starters, statics, pickup,
                passes = passes(lines), notes = notes,
                statsUnchanged = fromMovesets && lines.any { it.trim().equals(STATS_UNCHANGED, ignoreCase = true) },
            )
        }

        /**
         * The passes beside the logged one (rc32 audit P2 #69). PART 2's marker is followed by PART 2's own log, whose
         * header gives its decimal seed and settings string. The 60% levels' pre-pass has a marker of its own since
         * rc34, followed by its seed and settings string; a log from before has only the trailer sentence, with the
         * seed in hex and no settings string.
         */
        private fun passes(lines: List<String>): List<Pass> {
            fun headerAfter(i: Int): Pair<String, String> {
                var s = ""; var st = ""
                for (j in i + 1 until lines.size) {
                    val l = lines[j].trim()
                    if (isSection(l) || l.startsWith("== ")) break
                    if (s.isEmpty()) SEED.find(l)?.let { s = it.groupValues[1] }
                    if (st.isEmpty()) SETTINGS.find(l)?.let { st = it.groupValues[1].trim() }
                    if (s.isNotEmpty() && st.isNotEmpty()) break
                }
                return s to st
            }
            val out = ArrayList<Pass>()
            val pre = lines.indexOfFirst { PRE_PASS.matches(it.trim()) }
            if (pre >= 0) {
                val m = PRE_PASS.find(lines[pre].trim())!!
                val (s, st) = headerAfter(pre)
                out += Pass("60% levels pre-pass", m.groupValues[1], s.ifEmpty { decimalSeed(m.groupValues[2]) }, st, before = true)
            } else lines.firstNotNullOfOrNull { SIXTY.find(it.trim()) }?.let { m ->
                out += Pass("60% levels pre-pass", m.groupValues[1], decimalSeed(m.groupValues[2]), "", before = true)
            }
            val two = lines.indexOfFirst { PART_TWO.matches(it.trim()) }
            if (two >= 0) {
                val m = PART_TWO.find(lines[two].trim())!!
                val (s, st) = headerAfter(two)
                out += Pass("PART 2", m.groupValues[1], s.ifEmpty { decimalSeed(m.groupValues[2]) }, st, before = false)
            }
            return out
        }

        private val STAT_COLUMNS = listOf("HP", "ATK", "DEF", "SATK", "SDEF", "SPD", "SPE", "SPEC")
        private val STAT_LABELS = mapOf("HP" to "HP", "ATK" to "ATK", "DEF" to "DEF", "SATK" to "SPA", "SDEF" to "SPD", "SPD" to "SPE", "SPE" to "SPE", "SPEC" to "SPC")
    }
}
