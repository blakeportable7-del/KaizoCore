package com.ironmonone.app

import com.ironmonone.tracker.GameMap
import com.ironmonone.tracker.GbaTracker
import com.ironmonone.tracker.MemoryReader
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * How the Gen 3 log viewer names a MaxDex or Nat. Dex log's Pokemon and moves (LogNames, rc34). The fixtures are cut
 * from real logs: maxdex.log from a MaxDex run on 2026-10-03 (whose forms the viewer showed as "Eternatuse",
 * "Rayquazam" and "Mewtwox"), natdex.log from Nat. Dex 1.2.1's own randomizer on the FireRed build, FRLG NatDex v1.2
 * Kaizo. Neither holds anything of a ROM but the names the randomizer printed.
 */
class LogNamesTest {
    private fun log(name: String) = RandomizerLog.parse(File("src/test/resources/logs/$name").readText(Charsets.UTF_8))
    private val maxDex = log("maxdex.log")

    /**
     * MaxDex 1.0 on a ROM that holds only its name table, at the pointer its Game Freak header keeps at 0x144: the
     * fixture's Pokemon at their own ids, ten letters at most, as firered-maxdex.gba has them (the real table is read in
     * the test below, and in tracker-gba's MaxDexMapTest).
     */
    private fun maxDexTracker(table: Map<Int, String> = ROM_NAMES): GbaTracker {
        val base = 0x08800000L
        val mem = MemoryReader { address, length ->
            when {
                address == 0x08000144L -> ByteArray(4) { ((base shr (8 * it)) and 0xFFL).toByte() }
                address >= base && (address - base) % 11 == 0L -> table[((address - base) / 11).toInt()]?.let { gen3(it, length) } ?: ByteArray(length)
                else -> ByteArray(length)
            }
        }
        return GbaTracker(mem, GameMap.MAXDEX_FR_10)
    }

    @Test
    fun `MaxDex's forms read as the tracker names them, sorted by BST as the viewer sorts them`() {
        val names = LogNames.of(maxDexTracker())
        val top = LogSearch.pokemonRows(maxDex, "", LogFilter.NAME, LogSort.BST, names).take(11)
        // Blake's screen on 2026-10-03 read "Eternatuse", "Rayquazam", "Mewtwox", "Mewtwoy", "Zygardem", "Groudonp"...
        assertEquals(
            listOf("Eternatus-E", "Rayquaza-M", "Mewtwo-X", "Mewtwo-Y", "Zygarde-M", "Groudon-P", "Kyogre-P", "Necrozma-U",
                "Zygarde-C", "Tyranitar-M", "Garchomp-M"),
            top.map { names.species(it.name) },
        )
        assertEquals(listOf("Eternatuse", "Rayquazam", "Mewtwox"), top.take(3).map { logTitle(it.name) }, "what logTitle made of them")
    }

    @Test
    fun `every Pokemon in the MaxDex log reads as the tracker names it, the ROM's irregular cuts too`() {
        val names = LogNames.of(maxDexTracker())
        val expected = mapOf(
            "Bulbasaur" to "Bulbasaur", "Nidoran♀" to "Nidoran F", "Farfetch’d" to "Farfetch'd", "Mewtwo" to "Mewtwo",
            "Flechinder" to "Fletchinder", "Jangmo-o" to "Jangmo-o", "ScreamTail" to "Scream Tail", "RagingBolt" to "Raging Bolt",
            "KangaskhaM" to "Kangaskhan-M", "MewtwoX" to "Mewtwo-X", "MewtwoY" to "Mewtwo-Y", "MarowakA" to "Marowak-A",
            "WeezingG" to "Weezing-G", "FarfetchdG" to "Farfetch'd-G", "Mr. MimeG" to "Mr. Mime-G", "BurmyT" to "Burmy-T",
            "RotomFr" to "Rotom-Frost", "DarmanitZG" to "Darmanitan-GZ", "PumpkabooX" to "Pumpkaboo-J",
            "Zygarde10" to "Zygarde-10", "ToxtricitL" to "Toxtricity-L", "EternatusE" to "Eternatus-E", "RayquazaM" to "Rayquaza-M",
        )
        for ((logName, shown) in expected) {
            assertTrue(maxDex.pokemonNamed(logName) != null, "$logName is in the fixture")
            assertEquals(shown, names.species(logName), logName)
        }
        // Not one of the fixture's Pokemon is left as logTitle mangled it.
        for (p in maxDex.pokemon) assertTrue(names.speciesId(p.name) != null, "${p.name} found its Pokemon")
    }

    @Test
    fun `a form's sprite and evolution words come from the tracker's own id, never the log's number`() {
        val names = LogNames.of(maxDexTracker())
        // The log numbers MewtwoX 1039, which is Okidogi in the tracker; its own id is 1064.
        assertEquals(1039, maxDex.pokemonNamed("MewtwoX")!!.id)
        assertEquals(1064, names.speciesId("MewtwoX"))
        assertEquals(1198, names.speciesId("PumpkabooX"))
        assertEquals(687, names.speciesId("Flechinder"), "the log's 662")
        assertEquals(1185, names.speciesId("DarmanitZG"))
    }

    @Test
    fun `evolutions, a trainer's team and the Misc tab name forms the same way`() {
        val names = LogNames.of(maxDexTracker())
        fun evos(name: String) = maxDex.pokemonNamed(name)!!.evolutions.map(names::species)
        assertEquals(listOf("Manectric", "Toxtricity-L"), evos("Pikachu"))
        assertEquals(listOf("Marowak-A"), evos("Charmander"))
        assertEquals(listOf("Burmy-T"), evos("Weedle"))
        assertEquals(listOf("Weezing-G"), evos("Zubat"))
        assertEquals(listOf("Pikachu"), LogSearch.preEvolutions(maxDex, maxDex.pokemonNamed("ToxtricitL")!!).map { names.species(it.name) })
        // The team as the trainer page lists it, the Pokemon outside the fixture's table by the tracker's names too.
        val team = { n: Int -> maxDex.trainers.first { it.number == n }.party.map { names.species(it.name) } }
        assertEquals(listOf("Altaria", "Ting-Lu", "Mewtwo-X"), team(278))
        assertEquals(listOf("Rotom-Frost", "Iron Moth", "Regieleki"), team(316))
        assertEquals(listOf("Darmanitan-GZ", "Armaldo", "Sneasler"), team(366))
        assertEquals(listOf("Mewtwo" to "Mewtwo", "Deoxys" to "Deoxys"), maxDex.statics.map { (a, b) -> names.speciesAsWritten(a) to names.speciesAsWritten(b) })
        assertEquals("Pumpkaboo-J", names.species(maxDex.routes.single().encounters.first { it.name.startsWith("Pumpkaboo") }.name))
    }

    @Test
    fun `moves read as the tracker names them, where logTitle broke four`() {
        val names = LogNames.of(maxDexTracker())
        fun moves(name: String) = maxDex.pokemonNamed(name)!!.moves.map { names.move(it.second) }
        assertTrue("U-turn" in moves("Oddish"), "logTitle made it U-Turn")
        assertTrue("Roar of Time" in moves("Dugtrio"), "Roar Of Time")
        assertTrue("V-create" in moves("Mankey") && "Trick-or-Treat" in moves("Mankey"), "V-Create and Trick-Or-Treat")
        assertEquals("Parabolic Charge", names.move("Parabolic Charge"))
        assertEquals("Gigaton Hammer", names.move(maxDex.tms.first().move))
    }

    @Test
    fun `a search for mewtwo x finds Mewtwo-X, in the list, the suggestions and the trainers`() {
        val t = maxDexTracker()
        val names = LogNames.of(t)
        assertEquals(listOf("MewtwoX"), LogSearch.pokemonRows(maxDex, "mewtwo x", LogFilter.NAME, LogSort.POKEDEX, names).map { it.name })
        assertEquals(listOf("MewtwoX"), LogSearch.pokemonRows(maxDex, "Mewtwo-X", LogFilter.NAME, LogSort.POKEDEX, names).map { it.name })
        assertEquals("MewtwoX", LogSuggest.pokemon(maxDex, "mewtwo x", names = names).first().name)
        // Only the name shown carries the J.
        assertEquals("PumpkabooX", LogSuggest.pokemon(maxDex, "pumpkaboo j", names = names).first().name)
        assertEquals(listOf("PumpkabooX"), LogSearch.pokemonRows(maxDex, "pumpkaboo-j", LogFilter.NAME, LogSort.POKEDEX, names).map { it.name })
        assertEquals(listOf("ScreamTail"), LogSearch.pokemonRows(maxDex, "scream tail", LogFilter.NAME, LogSort.POKEDEX, names).map { it.name })
        // The trainer whose team holds it, and an A to Z that sorts by the names shown.
        val rules = LogTrainerRules(t, frlg = true)
        assertEquals(listOf(278), rules.rows(maxDex, LogTrainerFilter.ALL, "mewtwo x", false, LogFilter.NAME, names = names).map { it.number })
        val alpha = LogSearch.pokemonRows(maxDex, "", LogFilter.NAME, LogSort.ALPHA, names).map { names.species(it.name) }
        assertEquals(alpha.sorted(), alpha)
        // A suggestion shows the tracker's name and puts it in the box.
        assertEquals("Mewtwo-X", names.speciesAsWritten("MewtwoX"))
    }

    @Test
    fun `without MaxDex's name table a cut name is left as before, never given another Pokemon's`() {
        val names = LogNames.of(maxDexTracker(emptyMap()))
        // A whole name with its letter on the end is the tracker's still: letters and digits alone, they are the same.
        assertEquals("Mewtwo-X", names.species("MewtwoX"))
        assertEquals("Scream Tail", names.species("ScreamTail"))
        // A name the ROM cut finds nothing, and reads as it did.
        for (n in listOf("PumpkabooX", "DarmanitZG", "RotomFr", "KangaskhaM", "Flechinder")) {
            assertNull(names.speciesId(n), n)
            assertEquals(logTitle(n), names.species(n), n)
        }
    }

    @Test
    fun `Nat Dex writes the tracker's names already, and they no longer lose their capitals`() {
        val natDexLog = log("natdex.log")
        val names = LogNames.of(GbaTracker(MemoryReader { _, n -> ByteArray(n) }, GameMap.FIRERED_U_V11.copy(namesFromLists = true, expandedSpeciesIds = true)))
        val expected = mapOf(
            "Necrozma-DM" to "Necrozma-DM", "Necrozma-DW" to "Necrozma-DW", "Tauros-PF" to "Tauros-PF", "Tauros-PW" to "Tauros-PW",
            "Darmanitan-GZ" to "Darmanitan-GZ", "Jangmo-o" to "Jangmo-o", "Hakamo-o" to "Hakamo-o", "Kommo-o" to "Kommo-o",
            "Mewtwo-X" to "Mewtwo-X", "Rotom-Heat" to "Rotom-Heat", "Pumpkaboo-J" to "Pumpkaboo-J", "Mewtwo" to "Mewtwo",
            "Nidoran♀" to "Nidoran F", "Farfetch’d" to "Farfetch'd", "Farfetch’d-G" to "Farfetch'd-G",
        )
        for ((logName, shown) in expected) {
            assertTrue(natDexLog.pokemonNamed(logName) != null, "$logName is in the fixture")
            assertEquals(shown, names.species(logName), logName)
        }
        assertEquals("Necrozma-Dm", logTitle("Necrozma-DM"), "what logTitle made of it")
        // By the tracker's id, not the log's 1045.
        assertEquals(1045, natDexLog.pokemonNamed("Mewtwo-X")!!.id)
        assertEquals(1064, names.speciesId("Mewtwo-X"))
        for (p in natDexLog.pokemon) assertTrue(names.speciesId(p.name) != null, "${p.name} found its Pokemon")
    }

    @Test
    fun `a vanilla log's capitals read exactly as before`() {
        val emerald = log("emerald.log")
        // The game's own names are the log's, in capitals: the tracker knows every one, and still logTitle reads them.
        val byId = emerald.pokemon.associateBy { it.id }
        val vanilla = LogNames(emerald.pokemon.associate { LogNames.key(it.name) to it.id }, { byId.getValue(it).name },
            emerald.pokemon.flatMap { p -> p.moves.map { it.second } }.associateBy { LogNames.key(it) })
        for (p in emerald.pokemon) {
            assertEquals(logTitle(p.name), vanilla.species(p.name), p.name)
            assertEquals(p.name, vanilla.speciesAsWritten(p.name), "the Misc tab keeps the log's capitals")
            for ((_, mv) in p.moves) assertEquals(logTitle(mv), vanilla.move(mv), mv)
        }
        assertEquals("Nidoran♀", vanilla.species("NIDORAN♀"))
        assertEquals("Mud-Slap", vanilla.move("MUD-SLAP"))
        for (p in emerald.trainers.flatMap { it.party }) assertEquals(logTitle(p.name), vanilla.species(p.name))
        // The searches find what they found: the name holding what was typed, in Pokedex order.
        for (q in listOf("bulba", "char", "NIDORAN", "zz", "pidgeot", "saur")) {
            val before = emerald.pokemon.filter { it.name.contains(q, ignoreCase = true) }.sortedBy { it.id }.map { it.id }
            assertEquals(before, LogSearch.pokemonRows(emerald, q, LogFilter.NAME, LogSort.POKEDEX, vanilla).map { it.id }, q)
        }
        assertEquals(LogSearch.pokemonRows(emerald, "", LogFilter.NAME, LogSort.ALPHA).map { it.id },
            LogSearch.pokemonRows(emerald, "", LogFilter.NAME, LogSort.ALPHA, vanilla).map { it.id })
        // And with no tracker at all, every name is logTitle's, a MaxDex one too.
        assertEquals("Mewtwox", LogNames.PLAIN.species("MewtwoX"))
        assertEquals("U-Turn", LogNames.PLAIN.move("U-turn"))
    }

    @Test
    fun `the viewer's pages name Pokemon and moves through LogNames`() {
        val src = File("src/main/kotlin/com/ironmonone/app")
        val direct = Regex("""logTitle\((p|m|w|it|evo|pre)\.name\)|logTitle\((mv|r\.move|t\.move)\)""")
        for (f in listOf("LogPokemon.kt", "LogTrainers.kt", "LogRoutes.kt", "LogSearchField.kt", "LogSearch.kt", "LogTms.kt", "LogViewer.kt")) {
            val text = File(src, f).readText()
            assertFalse(direct.containsMatchIn(text), "$f: ${direct.find(text)?.value}")
        }
        val viewer = File(src, "LogViewer.kt").readText()
        assertTrue("LogNames.of(tracker)" in viewer)
        assertTrue("names.speciesId(p.name)?.let(sf)" in viewer, "the sprites")
        assertTrue("names.speciesId(lp.name)?.let { com.ironmonone.tracker.EvoText.short(t.evolution(it)) }" in viewer, "the evolution words")
        assertTrue("byName.speciesId(w.name)" in File(src, "OpenBookRoutes.kt").readText(), "Open Book's icons")
    }

    /** The real table, when the MaxDex dump is to hand: every name it holds finds its own Pokemon, and the fixture reads as above. */
    @Test
    fun `on the real MaxDex dump every ten-letter name finds its own Pokemon`() {
        val rom = Dumps.rom("firered-maxdex.gba") ?: return println("SKIP: no firered-maxdex.gba")
        val bytes = rom.readBytes()
        val mem = MemoryReader { address, length ->
            val off = address - 0x08000000L
            if (off >= 0 && off + length <= bytes.size) bytes.copyOfRange(off.toInt(), off.toInt() + length) else ByteArray(length)
        }
        val t = GbaTracker(mem, GameMap.MAXDEX_FR_10)
        val names = LogNames.of(t)
        val table = t.logSpeciesIds()
        assertTrue(table.size > 1200, "${table.size} names")
        for ((n, id) in table) assertEquals(id, names.speciesId(n), n)
        for ((id, n) in ROM_NAMES) assertEquals(id, table[n.uppercase()], "the fake table above is the real one: $n")
        for (p in maxDex.pokemon) assertEquals(t.speciesName(names.speciesId(p.name)!!), names.species(p.name), p.name)
    }

    companion object {
        /** The fixture's Pokemon in MaxDex's ROM name table, by its ids (checked against the dump above when it is here). */
        val ROM_NAMES = mapOf(
            1 to "Bulbasaur", 4 to "Charmander", 13 to "Weedle", 25 to "Pikachu", 29 to "Nidoran♀", 41 to "Zubat",
            43 to "Oddish", 51 to "Dugtrio", 56 to "Mankey", 83 to "Farfetch'd", 150 to "Mewtwo", 338 to "Manectric",
            405 to "Groudon", 406 to "Rayquaza", 687 to "Flechinder", 807 to "Jangmo-o", 1010 to "ScreamTail", 1046 to "RagingBolt",
            1060 to "KangaskhaM", 1064 to "MewtwoX", 1065 to "MewtwoY", 1071 to "TyranitarM", 1092 to "GarchompM", 1098 to "RayquazaM",
            1099 to "KyogreP", 1100 to "GroudonP", 1118 to "MarowakA", 1124 to "FarfetchdG", 1125 to "WeezingG", 1126 to "Mr. MimeG",
            1169 to "BurmyT", 1175 to "RotomFr", 1185 to "DarmanitZG", 1198 to "PumpkabooX", 1202 to "Zygarde10", 1203 to "ZygardeC",
            1214 to "NecrozmaU", 1215 to "ToxtricitL", 1221 to "EternatusE", 1270 to "ZygardeM",
        )

        /** Gen 3's English text for the letters these names use, ended by 0xFF and padded to [length]. */
        fun gen3(s: String, length: Int): ByteArray {
            val out = ByteArray(length) { 0xFF.toByte() }
            s.forEachIndexed { i, c ->
                out[i] = when (c) {
                    in 'A'..'Z' -> 0xBB + (c - 'A')
                    in 'a'..'z' -> 0xD5 + (c - 'a')
                    in '0'..'9' -> 0xA1 + (c - '0')
                    ' ' -> 0x00
                    '.' -> 0xAD
                    '-' -> 0xAE
                    '\'' -> 0xB4
                    '♂' -> 0xB5
                    '♀' -> 0xB6
                    else -> error("no Gen 3 byte for $c")
                }.toByte()
            }
            return out
        }
    }
}
