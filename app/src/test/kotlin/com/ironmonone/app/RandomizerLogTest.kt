package com.ironmonone.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertNotSame

/**
 * The parser against trimmed real logs: each fixture is the bundled ZX
 * engine's own output for a Kaizo preset on Blake's dumps (2026-09-07), cut
 * to the first rows of every section. Five families, five shapes of the base
 * stats table.
 */
class RandomizerLogTest {
    private fun load(name: String): RandomizerLog =
        RandomizerLog.parse(javaClass.getResource("/logs/$name.log")!!.readText(Charsets.UTF_8))

    @Test
    fun `header, game and settings are read from the first lines and the closing line`() {
        val log = load("emerald")
        assertEquals("4.6.1", log.version)
        assertEquals("186610104527268", log.seed)
        assertTrue(log.settingsString.startsWith("322WRIEE"))
        assertEquals("Emerald (U)", log.game)
    }

    @Test
    fun `Gen 3 base stats carry six stats, two abilities and a held item`() {
        val b = load("emerald").pokemonNamed("BULBASAUR")
        assertNotNull(b)
        assertEquals(listOf("GRASS", "POISON"), b.types)
        assertEquals(listOf("HP", "ATK", "DEF", "SPA", "SPD", "SPE"), b.statNames)
        assertEquals(listOf(56, 22, 107, 31, 84, 17), b.stats)
        assertEquals(317, b.bst)
        assertEquals(listOf("HUGE POWER"), b.abilities)
        assertEquals("TM36 (rare)", b.item)
        assertEquals(listOf("NIDORINA"), b.evolutions)
    }

    @Test
    fun `Gen 1 has five stats and no abilities, Gen 5 has three abilities`() {
        val g1 = load("blue").pokemonNamed("BULBASAUR")!!
        assertEquals(listOf("HP", "ATK", "DEF", "SPE", "SPC"), g1.statNames)
        assertEquals(listOf(38, 26, 72, 43, 74), g1.stats)
        assertTrue(g1.abilities.isEmpty())
        val g5 = load("black2").pokemonNamed("Bulbasaur")!!
        assertEquals(listOf("Heatproof", "Swarm", "Cute Charm"), g5.abilities)
        val g2 = load("silver").pokemonNamed("BULBASAUR")!!
        assertEquals(6, g2.stats.size); assertTrue(g2.abilities.isEmpty())
    }

    @Test
    fun `movesets give level-up moves in order and egg moves separately`() {
        val b = load("emerald").pokemonNamed("BULBASAUR")!!
        assertEquals(1 to "DREAM EATER", b.moves.first())
        assertEquals(46 to "SHADOW BALL", b.moves.last())
        assertTrue(b.moves.size >= 14)
        assertEquals("MAGIC COAT", b.eggMoves.first())
    }

    @Test
    fun `TMs, TM compatibility, trainers with held items, wild sets and starters`() {
        val log = load("emerald")
        assertEquals("COTTON SPORE", log.tms.first { it.number == 1 }.move)
        assertEquals(listOf(2, 5, 6), load("emerald").pokemonNamed("BULBASAUR")!!.tmsLearnable.take(3))
        val t1 = log.trainers.first()
        assertEquals(1, t1.number); assertEquals("HIKER SAWYER", t1.originalName); assertEquals("Designer Jill", t1.name)
        assertEquals("SNEASEL", t1.party[0].name); assertEquals(32, t1.party[0].level)
        // A held item rides on the species with "@", as the engine writes it for a Gen 5 Elite Four member.
        val withItem = RandomizerLog.parse(listOf("v", "s", "x", "--Trainers Pokemon--", "#38 (Elite Four Shauntal => Biker Noelle) - Armaldo Lv84, Terrakion@Sitrus Berry Lv87").joinToString(System.lineSeparator())).trainers.single()
        assertEquals("Sitrus Berry", withItem.party[1].item); assertEquals("Terrakion", withItem.party[1].name); assertEquals(87, withItem.party[1].level)
        assertEquals(null, withItem.party[0].item)
        val r1 = log.routes.first()
        assertEquals("ROUTE 101 Grass/Cave", r1.name); assertEquals(20, r1.rate)
        assertEquals("CASTFORM", r1.encounters[0].name); assertEquals(3, r1.encounters[0].minLevel)
        val ds = load("black2").routes.first().encounters[0]
        assertEquals(68, ds.minLevel); assertEquals(90, ds.maxLevel)
        assertEquals(listOf("SKIPLOOM", "EXEGGUTOR", "FURRET"), log.starters)
        assertEquals("LILEEP" to "SANDSHREW", log.statics.first())
    }

    @Test
    fun `a Gen 1 trainer line with an address suffix and a Gen 4 line without one both parse`() {
        val g1 = load("blue").trainers.first()
        assertEquals("YOUNGSTER", g1.originalName); assertEquals("Rich Girl", g1.name); assertEquals(2, g1.party.size)
        val g4 = load("platinum").trainers.first()
        assertEquals("Youngster Tristan", g4.originalName); assertEquals("WAILORD", g4.party[0].name)
    }

    @Test
    fun `a negative seed is read with its sign`() {
        // rc33 audit P1 #44: the engines log the signed long, negative for about half of all runs, and the seed came back blank.
        val emerald = javaClass.getResource("/logs/emerald.log")!!.readText(Charsets.UTF_8)
        val negative = RandomizerLog.parse(emerald.replace("Random Seed: 186610104527268", "Random Seed: -5375266838930524653"))
        assertEquals("-5375266838930524653", negative.seed)
    }

    @Test
    fun `evolutions joined with and are all kept`() {
        // rc33 audit P1 #45: "GLOOM -> WEEZING and ARBOK" lost both.
        assertEquals(listOf("WEEZING", "ARBOK"), load("emerald").pokemonNamed("GLOOM")?.evolutions)
        assertEquals(listOf("Cradily", "Abomasnow"), load("black2").pokemonNamed("Gloom")?.evolutions)
        val eevee = RandomizerLog.parse(javaClass.getResource("/logs/emerald.log")!!.readText(Charsets.UTF_8)
            .replace("GLOOM           -> WEEZING and ARBOK", "GLOOM           -> WEEZING, ARBOK, ONIX, SEEL and KOFFING"))
        assertEquals(listOf("WEEZING", "ARBOK", "ONIX", "SEEL", "KOFFING"), eevee.pokemonNamed("GLOOM")?.evolutions)
    }

    @Test
    fun `starters are read from whichever of the engine's three headers it wrote`() {
        // rc33 audit P1 #46: picked starters (Build your own) are Custom, IronMON Journey's are 2-Evolution.
        val emerald = javaClass.getResource("/logs/emerald.log")!!.readText(Charsets.UTF_8)
        for (header in listOf("--Custom Starters--", "--Random 2-Evolution Starters--"))
            assertEquals(listOf("SKIPLOOM", "EXEGGUTOR", "FURRET"), RandomizerLog.parse(emerald.replace("--Random Starters--", header)).starters, header)
    }

    private fun text(name: String): String = javaClass.getResource("/logs/$name.log")!!.readText(Charsets.UTF_8)

    /** [log] without the section [name] (its header line and every line under it), with [instead] in its place. */
    private fun without(log: String, name: String, instead: String): String {
        val lines = log.split('\n')
        val start = lines.indexOfFirst { it.trim() == "--$name--" }
        if (start < 0) return log
        var end = start + 1
        while (end < lines.size && !lines[end].trim().let { it.startsWith("--") && it.endsWith("--") && it.length > 4 }) end++
        return (lines.subList(0, start) + listOf(instead) + lines.subList(end, lines.size)).joinToString("\n")
    }

    @Test
    fun `a two-pass log reads its header wherever it starts, and keeps PART 2's seed and string`() {
        // rc32 audit P2 #69: "== PART 1" on the first line left the version, seed and settings string empty.
        val blue = text("blue")
        val log = RandomizerLog.parse("== PART 1: RBY Kaizo.rnqs ==\n" + blue + "\n== PART 2: RBY PART 2.rnqs (seed 0000000000000001) ==\n" +
            blue.replace("Random Seed: ", "Random Seed: 9"))
        val one = load("blue")
        assertEquals("4.6.1", log.version)
        assertEquals(one.seed, log.seed)
        assertTrue(log.settingsString.isNotEmpty())
        assertEquals(one.settingsString, log.settingsString)
        val part2 = log.passes.single()
        assertEquals("RBY PART 2.rnqs", part2.file)
        assertFalse(part2.before)
        assertEquals("9" + one.seed, part2.seed, "PART 2's own decimal seed, from its own header")
        assertEquals(one.settingsString, part2.settingsString)
        assertEquals(one.pokemonNamed("BULBASAUR")!!.stats, log.pokemonNamed("BULBASAUR")!!.stats, "every section from PART 1, as before")
        val share = logShareText(log)
        assertTrue("1. PART 1" in share && "2. PART 2, RBY PART 2.rnqs" in share, share)
        assertTrue(share.indexOf("Random Seed: ${one.seed}") < share.indexOf("Random Seed: 9${one.seed}"), "PART 1 is loaded first")
    }

    @Test
    fun `a 60% levels log gives the pre-pass first, with what the log kept of it`() {
        // rc32 audit P2 #69: Share Seed left the pre-pass out, which rebuilds the game at 50% levels.
        val old = RandomizerLog.parse(text("emerald").trimEnd() + "\n------------------------------------------------------------------\n" +
            "60% levels: \"RSE PRE-PASS.rnqs\" raised trainer and wild levels 6% first (seed 0000000000000abc), then \"RSE Kaizo.rnqs\" ran over its output.\n")
        val pre = old.passes.single()
        assertTrue(pre.before)
        assertEquals("RSE PRE-PASS.rnqs", pre.file)
        assertEquals("2748", pre.seed, "the hex seed, in the decimal Premade Seed takes")
        assertEquals("", pre.settingsString, "a log from before rc34 did not keep it")
        assertEquals("186610104527268", old.seed, "the logged pass's own seed is still the run's")
        val share = logShareText(old)
        assertTrue("PRE-PASS" in share && "not kept in this log" in share, share)
        assertTrue(share.indexOf("Random Seed: 2748") < share.indexOf("Random Seed: 186610104527268"), "the pre-pass is loaded first")
        // A log written now keeps the pre-pass's seed and settings string (Randomizers.withPrePass).
        val dir = kotlin.io.path.createTempDirectory("sixty").toFile()
        try {
            val rom = java.io.File(dir, "emerald.gba").apply { writeBytes(ByteArray(64)) }
            val pre2 = java.io.File(dir, "RSE PRE-PASS.rnqs").apply { writeText("pre") }
            val kaizo = java.io.File(dir, "RSE Kaizo.rnqs").apply { writeText("kaizo") }
            val out = com.ironmonone.app.engine.Randomizers.withPrePass(rom, pre2, kaizo, java.io.File(dir, "current.gba"), 186610104527268L) { src, st, d, sd ->
                d.writeBytes(src.readBytes())
                com.ironmonone.app.engine.NatDexEngine.Outcome(sd,
                    if (st.name == "RSE Kaizo.rnqs") text("emerald") else "Randomizer Version: 4.6.1\nRandom Seed: $sd\nSettings String: 322PREPASSSTRING\n\n--Trainers Pokemon--\n")
            }
            val now = RandomizerLog.parse(out.logText)
            val p = now.passes.single()
            assertEquals(com.ironmonone.app.engine.Randomizers.preSeed(186610104527268L).toString(), p.seed)
            assertEquals("322PREPASSSTRING", p.settingsString)
            assertTrue("Settings String: 322PREPASSSTRING" in logShareText(now))
            assertEquals("186610104527268", now.seed)
            assertEquals(load("emerald").trainers.size, now.trainers.size, "the pre-pass's own sections are not the run's")
        } finally { dir.deleteRecursively() }
    }

    @Test
    fun `a log with no base stats table makes its Pokemon from the moveset blocks`() {
        // rc32 audit P2 #71: Build your own from "The game as it is" lost the Pokemon tab, the wild areas and the trainers' moves.
        val log = RandomizerLog.parse(without(text("emerald"), "Pokemon Base Stats & Types", "Pokemon base stats & type: unchanged"))
        val b = assertNotNull(log.pokemonNamed("BULBASAUR"))
        assertEquals(listOf("HP", "ATK", "DEF", "SPA", "SPD", "SPE"), b.statNames)
        assertEquals(listOf(56, 22, 107, 31, 84, 17), b.stats)
        assertEquals(1 to "DREAM EATER", b.moves.first())
        assertEquals(listOf("NIDORINA"), b.evolutions)
        assertEquals(listOf(2, 5, 6), b.tmsLearnable.take(3))
        assertTrue(b.types.isEmpty(), "the log does not give them")
        assertTrue(log.statsUnchanged)
        val g1 = RandomizerLog.parse(without(text("blue"), "Pokemon Base Stats & Types", "Pokemon base stats & type: unchanged")).pokemonNamed("BULBASAUR")!!
        assertEquals(listOf("HP", "ATK", "DEF", "SPE", "SPC"), g1.statNames, "the Gen 1 table's order, not the block's")
        assertEquals(listOf(38, 26, 72, 43, 74), g1.stats)
        assertFalse(load("emerald").statsUnchanged)
    }

    @Test
    fun `a log that lists no Pokemon keeps its wild areas, by name`() {
        // rc32 audit P2 #71: every wild encounter was dropped, so Route 101 vanished from the routes.
        val bare = without(without(text("emerald"), "Pokemon Base Stats & Types", "Pokemon base stats & type: unchanged"),
            "Pokemon Movesets", "Pokemon Movesets: Unchanged.")
        val log = RandomizerLog.parse(bare)
        assertTrue(log.pokemon.isEmpty() && log.statsUnchanged)
        val t = com.ironmonone.tracker.GbaTracker(com.ironmonone.tracker.MemoryReader { _, n -> ByteArray(n) }, com.ironmonone.tracker.GameMap.EMERALD_U)
        val r101 = LogRoutes.build(log, LogTrainerRules(t, frlg = false), t).first { it.mapId == 17 }
        val grass = r101.areas.getValue(LogEncType.GRASS)
        assertEquals(setOf("CASTFORM", "SANDSLASH"), grass.map { it.name }.toSet().intersect(setOf("CASTFORM", "SANDSLASH")))
        assertTrue(grass.all { it.pokemon == null })
        // A log with its list skips a name it cannot map, as the reference does.
        val listed = LogRoutes.build(load("emerald"), LogTrainerRules(t, frlg = false), t).first { it.mapId == 17 }
        assertTrue(listed.areas.getValue(LogEncType.GRASS).all { it.pokemon != null })
    }

    @Test
    fun `a log is read once while its file stays the same, and again when it changes`() {
        // rc32 audit P2 #27: Inspect the log and Open Book parsed the same log each time, on the main thread.
        val f = java.io.File.createTempFile("cache", ".log")
        try {
            f.writeText(text("emerald"))
            val a = assertNotNull(RandomizerLog.parse(f))
            assertSame(a, RandomizerLog.parse(f))
            f.writeText(text("emerald") + "\n")
            val b = assertNotNull(RandomizerLog.parse(f))
            assertNotSame(a, b, "a new length is a new log")
            assertEquals(a.seed, b.seed)
        } finally { f.delete() }
        // Its patterns are made once, outside parse, and the viewers read it off the main thread.
        val src = java.io.File("src/main/kotlin/com/ironmonone/app/RandomizerLog.kt").readText().replace("\r\n", "\n")
        val body = src.substringAfter("fun parse(text: String): RandomizerLog {").substringBefore("\n        }\n")
        assertFalse("Regex(" in body, "a pattern is built in parse")
        assertFalse("Regex(" in src.substringAfter("private fun passes(").substringBefore("\n        }\n"))
        for (viewer in listOf("LogViewer.kt", "DsLogViewer.kt")) {
            val v = java.io.File("src/main/kotlin/com/ironmonone/app/$viewer").readText()
            assertFalse("remember(file) { RandomizerLog.parse(file) }" in v, viewer)
            assertTrue("withContext(Dispatchers.Default)" in v, viewer)
        }
    }

    @Test
    fun `the name lookup keeps the first of a name and the Keldeo alias`() {
        val keldeo = RandomizerLog.parse(text("emerald").replace("  2|IVYSAUR      |", "  2|KELDEO       |"))
        assertEquals(2, keldeo.pokemonNamed("Keldeo-R")?.id, "Black 2's trainers say Keldeo-R")
        val twice = RandomizerLog.parse(text("emerald").replace("  2|IVYSAUR      |", "  2|BULBASAUR    |"))
        assertEquals(1, twice.pokemonNamed("bulbasaur")!!.id, "the first of two of a name, as the scan found it")
    }

    @Test
    fun `a note the app writes into the header is read back, and the rest of the header with it`() {
        // rc32 audit P3 #90: a Limit Pokemon the engine dropped was said nowhere.
        val noted = com.ironmonone.app.engine.ZxEngine.withNote(text("emerald"), com.ironmonone.app.engine.ZxEngine.LIMIT_DROPPED)
        val log = RandomizerLog.parse(noted)
        assertEquals(listOf(com.ironmonone.app.engine.ZxEngine.LIMIT_DROPPED), log.notes)
        assertEquals("186610104527268", log.seed); assertTrue(log.settingsString.startsWith("322WRIEE"))
        val f = java.io.File.createTempFile("noted", ".log")
        try {
            f.writeText(noted)
            assertEquals(listOf(com.ironmonone.app.engine.ZxEngine.LIMIT_DROPPED), RandomizerLog.notesOf(f))
        } finally { f.delete() }
        assertTrue(load("emerald").notes.isEmpty())
        val t = com.ironmonone.app.engine.ZxEngine.LIMIT_DROPPED
        assertFalse('—' in t || '–' in t || " - " in t, t)
    }
}
