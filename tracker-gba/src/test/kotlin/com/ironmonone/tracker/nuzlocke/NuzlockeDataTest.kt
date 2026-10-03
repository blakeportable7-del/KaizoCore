package com.ironmonone.tracker.nuzlocke

import com.ironmonone.tracker.GbData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The data files of the Game Boy and DS families as data (2026-09-30): the level caps, the evolution lines, the
 * species tables, the statics and the Game Boy trainer and map tables. The numbers in them were read out of the games
 * and their disassemblies by the tools in tools/nuzlocke (each has a `--check` that reads the ROMs again); what is here
 * is what a Kotlin reader depends on and what an edit by hand could break: the shape of each file, the cross-references
 * between them, and a few facts about the games that a wrong file would get wrong.
 */
class NuzlockeDataTest {

    private fun resource(path: String): String =
        NuzlockeDataTest::class.java.getResourceAsStream(path)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: error("no resource $path")

    private fun rows(path: String): List<List<String>> =
        resource(path).lineSequence().filter { it.isNotBlank() && !it.startsWith("#") }.map { it.split('\t') }.toList()

    private val games = listOf(
        NuzlockeSystem.GEN1 to "rb", NuzlockeSystem.GEN1 to "y", NuzlockeSystem.GEN2 to "gs", NuzlockeSystem.GEN2 to "c",
        NuzlockeSystem.GEN4 to "dp", NuzlockeSystem.GEN4 to "pt", NuzlockeSystem.GEN4 to "hgss",
        NuzlockeSystem.GEN5 to "bw", NuzlockeSystem.GEN5 to "b2w2",
    )

    private fun table(system: NuzlockeSystem, game: String) = LevelCapTable.standard(game, system)

    // ================================================================ level caps

    @Test
    fun `every game has its eight gyms in order, the Elite Four and the Champion`() {
        for ((system, game) in games) {
            val t = table(system, game)
            val label = "${system.key} $game"
            assertTrue(t.bosses.isNotEmpty(), label)
            assertEquals(t.bosses.map { it.key }.distinct(), t.bosses.map { it.key }, "$label: keys are unique")
            assertEquals(t.bosses.sortedBy { it.seq }.map { it.key }, t.bosses.map { it.key }, "$label: in fight order")
            assertEquals(t.bosses.map { it.seq }.distinct(), t.bosses.map { it.seq }, "$label: seq is unique")
            assertEquals((1..8).map { "gym$it" }, t.bosses.filter { it.kind == "gym" }.map { it.key }, "$label: eight gyms in order")
            assertEquals((1..4).map { "e4-$it" }, t.bosses.filter { it.kind == "e4" }.map { it.key }, "$label: four in the League")
            assertEquals(1, t.bosses.count { it.kind == "champion" }, "$label: one last fight")
            assertEquals("champion", t.bosses.first { it.kind == "champion" }.key, label)
            assertTrue(t.bosses.all { it.cap in 1..100 && it.trainerIds.isNotEmpty() && it.label.isNotBlank() && it.ace.isNotBlank() }, label)
            for (b in t.bosses) assertTrue(b.label.all { it.code in 32..126 } && b.ace.all { it.code in 32..126 }, "$label ${b.key}: plain text")
        }
    }

    @Test
    fun `the ladder runs gyms, League and Champion, and a boss's place reads from its key`() {
        for ((system, game) in games) {
            val t = table(system, game)
            val ladder = t.bosses.filter { it.onLadder }
            val kinds = ladder.map { it.kind }
            val label = "${system.key} $game"
            assertEquals(List(8) { "gym" } + List(4) { "e4" }, kinds.take(12), "$label: eight gyms, then the League")
            assertTrue(kinds.drop(12).all { it == "champion" || it == "post" }, "$label: the last fight and what comes around it")
            assertEquals(1, kinds.count { it == "champion" }, label)
            // Black and White fight N before Ghetsis, who is their last fight; everywhere else the Champion comes first and the rest follows.
            if (game == "bw") assertEquals("champion", kinds.last(), label) else assertEquals("champion", kinds[12], label)
            for (g in t.bosses.filter { it.kind == "gym" }) assertEquals("Gym ${g.key.removePrefix("gym")}", g.place, "$label ${g.key}")
        }
    }

    @Test
    fun `no trainer belongs to two fights of a game`() {
        for ((system, game) in games) {
            val ids = table(system, game).bosses.flatMap { b -> b.trainerIds.map { it to b.key } }
            val seen = HashMap<Int, String>()
            for ((id, key) in ids) {
                val other = seen.put(id, key)
                assertTrue(other == null || other == key, "${system.key} $game: trainer $id is in $other and in $key")
            }
        }
    }

    @Test
    fun `the badge bits are the game's, and each gym leader earns a different one`() {
        for ((system, game) in games) {
            val t = table(system, game)
            val label = "${system.key} $game"
            val gymBits = t.bosses.filter { it.kind == "gym" }.map { it.badge }
            assertTrue(gymBits.all { it != null }, "$label: every gym earns a badge")
            assertEquals(gymBits.size, gymBits.toSet().size, "$label: no two gyms share a badge")
            assertEquals((0..7).toSet(), gymBits.filterNotNull().toSet(), "$label: the eight badges of the game")
            // Everything else that has a badge has its own bit too, and the extras (a rival, a team boss) have none.
            val all = t.bosses.mapNotNull { it.badge }
            assertEquals(all.size, all.toSet().size, "$label: no bit twice")
            assertTrue(t.bosses.filter { it.kind == "rival" || it.kind == "boss" }.all { it.badge == null }, "$label: extras earn none")
            assertTrue(t.bosses.filter { it.kind == "e4" || it.kind == "champion" }.all { it.badge == null }, "$label: the League earns none")
        }
        // Generation 1 and 5 follow the fight order, Platinum does not: Fantina is the third gym and the fifth badge.
        for (game in listOf("rb", "y")) assertEquals((0..7).toList(), table(NuzlockeSystem.GEN1, game).bosses.filter { it.kind == "gym" }.map { it.badge!! })
        for (game in listOf("bw", "b2w2")) assertEquals((0..7).toList(), table(NuzlockeSystem.GEN5, game).bosses.filter { it.kind == "gym" }.map { it.badge!! })
        val pt = table(NuzlockeSystem.GEN4, "pt").bosses.filter { it.kind == "gym" }
        assertEquals("Fantina", pt[2].label); assertEquals(4, pt[2].badge, "Platinum's third gym earns the fifth badge")
    }

    @Test
    fun `the Kanto gyms come after the Johto League in Generation 2 and Heart Gold and Soul Silver, in bits 8 to 15`() {
        for ((system, game) in listOf(NuzlockeSystem.GEN2 to "gs", NuzlockeSystem.GEN2 to "c", NuzlockeSystem.GEN4 to "hgss")) {
            val t = table(system, game)
            val kanto = t.bosses.filter { it.kind == "post" && it.badge != null }
            assertEquals(8, kanto.size, "${system.key} $game: the eight Kanto leaders")
            assertEquals((8..15).toSet(), kanto.map { it.badge!! }.toSet(), "${system.key} $game")
            assertTrue(kanto.all { it.seq > t.byKey("champion")!!.seq }, "they come after the Champion")
            assertTrue(kanto.filter { it.key != "blue" }.all { it.group == "Kanto gyms" }, "any order, but Blue")
            assertNotNull(t.byKey("red")).let { red -> assertEquals("Mt. Silver", red.group); assertNull(red.badge) }
        }
    }

    @Test
    fun `the Elite Four is fought in any order only where the game lets it`() {
        for ((system, game) in games) {
            val e4 = table(system, game).bosses.filter { it.kind == "e4" }
            val anyOrder = system == NuzlockeSystem.GEN5
            for (b in e4) assertEquals(if (anyOrder) "Elite Four, any order" else "", b.group, "${system.key} $game ${b.key}")
        }
    }

    @Test
    fun `rival and team boss rows exist only for the DS games and carry every starter's version of the fight`() {
        for ((system, game) in games) {
            val extras = table(system, game).bosses.filter { it.kind == "rival" || it.kind == "boss" }
            if (system == NuzlockeSystem.GEN1 || system == NuzlockeSystem.GEN2) assertTrue(extras.isEmpty(), "${system.key} $game")
            else assertTrue(extras.size >= 4, "${system.key} $game has only ${extras.size} extra cap points")
            assertEquals(extras.map { it.key }.distinct(), extras.map { it.key }, "unique keys")
        }
    }

    @Test
    fun `the caps that were checked against the games stand as they were checked`() {
        // Verified against the ROMs by GbTrainerRomTest (Generations 1 and 2) and by the tools' --check for the DS games.
        assertEquals(listOf(14, 21, 24, 29, 43, 43, 47, 50), table(NuzlockeSystem.GEN1, "rb").bosses.filter { it.kind == "gym" }.map { it.cap })
        assertEquals(listOf(12, 21, 28, 32, 50, 50, 54, 55), table(NuzlockeSystem.GEN1, "y").bosses.filter { it.kind == "gym" }.map { it.cap })
        assertEquals(listOf(56, 58, 60, 62), table(NuzlockeSystem.GEN1, "rb").bosses.filter { it.kind == "e4" }.map { it.cap })
        assertEquals(65, table(NuzlockeSystem.GEN1, "rb").byKey("champion")!!.cap)
        assertEquals(listOf(9, 16, 20, 25, 30, 35, 31, 40), table(NuzlockeSystem.GEN2, "c").bosses.filter { it.kind == "gym" }.map { it.cap }, "Pryce, the seventh gym, is lower than Jasmine")
        assertEquals(50, table(NuzlockeSystem.GEN2, "c").byKey("champion")!!.cap)
        assertEquals(81, table(NuzlockeSystem.GEN2, "c").byKey("red")!!.cap)
        // Fantina is the same leader with two teams: 36 as Diamond and Pearl's fifth gym, 26 as Platinum's third.
        assertEquals(36, table(NuzlockeSystem.GEN4, "dp").byKey("gym5")!!.cap); assertEquals("Fantina", table(NuzlockeSystem.GEN4, "dp").byKey("gym5")!!.label)
        assertEquals(26, table(NuzlockeSystem.GEN4, "pt").byKey("gym3")!!.cap); assertEquals("Fantina", table(NuzlockeSystem.GEN4, "pt").byKey("gym3")!!.label)
        assertEquals(listOf(53, 55, 57, 59, 62), table(NuzlockeSystem.GEN4, "pt").bosses.filter { it.kind == "e4" || it.kind == "champion" }.map { it.cap }, "Platinum's first-run Elite Four and Cynthia")
        assertEquals(listOf(57, 59, 61, 63, 66), table(NuzlockeSystem.GEN4, "dp").bosses.filter { it.kind == "e4" || it.kind == "champion" }.map { it.cap })
        assertEquals(listOf(42, 44, 46, 47, 50), table(NuzlockeSystem.GEN4, "hgss").bosses.filter { it.kind == "e4" || it.kind == "champion" }.map { it.cap })
        assertEquals(88, table(NuzlockeSystem.GEN4, "hgss").byKey("red")!!.cap, "Red on Mt. Silver")
        assertEquals(59, table(NuzlockeSystem.GEN5, "b2w2").byKey("champion")!!.cap, "Iris")
    }

    // ================================================================ trainers and places of the Game Boy games

    @Test
    fun `every boss of the Game Boy games is a trainer the tables know, under the boss's own name`() {
        for ((system, game) in games.filter { it.first == NuzlockeSystem.GEN1 || it.first == NuzlockeSystem.GEN2 }) {
            val gen = if (system == NuzlockeSystem.GEN1) 1 else 2
            val keys = if (game == "gs") listOf("g", "gs") else listOf(game)
            for (b in table(system, game).bosses) for (id in b.trainerIds) {
                val who = assertNotNull(GbData.trainer(gen, keys, id shr 8, id and 0xFF), "${system.key} $game ${b.key}: class ${id shr 8} number ${id and 0xFF}")
                assertTrue(b.label.substringBefore(' ') in who.first, "${b.key}: the table says ${b.label} and the trainer table says ${who.first}")
                val group = who.second
                val want = when (b.kind) { "gym" -> "Gym"; "e4", "champion" -> "Elite4"; else -> null }
                if (want != null) assertEquals(want, group, "${b.key}: ${who.first}")
                else assertTrue(group == "Gym" || group == "Boss", "${b.key}: ${who.first} is $group")
            }
        }
    }

    @Test
    fun `every trainer class of the Game Boy games has a row for the whole class`() {
        for ((gen, classes) in listOf(1 to 47, 2 to 66)) {
            val shared = rows("/nuzlocke/trainers-gen$gen.tsv").filter { it[0] == "*" && it[2] == "0" }.map { it[1].toInt() }.toSet()
            val missing = (1..classes).filterNot { it in shared }
            // Generation 2 skips a few class numbers the game never uses; every class that has a name has a row.
            assertTrue(missing.size <= if (gen == 2) 2 else 0, "generation $gen has no row for classes $missing")
        }
    }

    @Test
    fun `a Game Boy trainer's group is one of the five, and its label is plain text`() {
        for (gen in 1..2) for (r in rows("/nuzlocke/trainers-gen$gen.tsv")) {
            assertEquals(5, r.size, r.toString())
            assertTrue(r[0] in listOf("*", "rb", "y", "gs", "g", "s", "c"), r.toString())
            assertTrue(r[4] in listOf("Gym", "Elite4", "Boss", "Rival", "Other"), r.toString())
            assertTrue(r[3].isNotBlank() && r[3].all { it.code in 32..126 }, r.toString())
        }
    }

    @Test
    fun `each map of the Game Boy games is one place, and the ids are the game's`() {
        for ((gen, keys) in listOf(1 to listOf("rb", "y"), 2 to listOf("gs", "c"))) {
            val rows = rows("/nuzlocke/areas-gen$gen.tsv")
            for (r in rows) {
                assertTrue(r.size in 3..4, r.toString())
                assertTrue(r[0] in keys, r.toString())
                assertTrue(r[2].isNotBlank() && r[2].all { it.code in 32..126 }, r.toString())
            }
            for (k in keys) {
                val ids = rows.filter { it[0] == k }.map { it[1].toInt() }
                assertEquals(ids.size, ids.toSet().size, "$k: an id twice")
                assertTrue(ids.size > 90, "$k has only ${ids.size} maps")
            }
        }
        // Spot checks that a shifted column would get wrong.
        fun place(gen: Int, game: String, id: Int) = GbData.place(gen, listOf(game), id)
        assertEquals("Pallet Town", place(1, "rb", 0)!!.place)
        assertEquals("Route 1", place(1, "rb", 12)!!.place)
        assertEquals("Mt. Moon", place(1, "rb", 59)!!.place); assertEquals("Mt. Moon 1F", place(1, "rb", 59)!!.detail)
        assertEquals("Mt. Moon", place(1, "rb", 61)!!.place); assertEquals("Mt. Moon B2F", place(1, "rb", 61)!!.detail)
        assertEquals("Pokemon Tower", place(1, "rb", 148)!!.place); assertEquals("Pokemon Tower 7F", place(1, "rb", 148)!!.detail)
        assertEquals("Safari Zone East", place(1, "rb", 217)!!.place)
        assertEquals("Route 1", place(1, "y", 12)!!.place)
        assertNull(place(1, "rb", 11), "an unused map id is no place")
        assertEquals("Route 29", place(2, "c", 2)!!.place)
        assertEquals("New Bark Town", place(2, "c", 1)!!.place)
        assertEquals("Olivine City", place(2, "gs", 257)!!.place, "map group 1, number 1")
        assertEquals("Mahogany Town", place(2, "gs", 513)!!.place, "map group 2, number 1")
    }

    @Test
    fun `a Game Boy static is at a place the game has, and its level is a level`() {
        for ((system, gen, keys) in listOf(Triple(NuzlockeSystem.GEN1, 1, listOf("rb", "y")), Triple(NuzlockeSystem.GEN2, 2, listOf("gs", "c", "g", "s")))) {
            val places = rows("/nuzlocke/areas-gen$gen.tsv").groupBy({ it[0] }, { it[2] })
            for (r in NuzlockeStatics.all(system)) {
                assertTrue(r.game in keys, "${system.key}: game ${r.game}")
                val known = if (r.game == "g" || r.game == "s") places.getValue("gs") else places.getValue(r.game)
                assertTrue(r.place in known, "${system.key} ${r.game}: ${r.place} is not a place of the game")
                assertTrue(r.level in 1..100)
            }
        }
    }

    @Test
    fun `a Generation 3 static is at a place the tracker names in its game, and a species check is Gen 3's own number`() {
        // rc33 audit P1 #76. Ruby and Sapphire share the "rs" rows; each hideout row names the version's own hideout.
        fun names(version: String) = rows("/gen3/routeinfo-$version.tsv").mapNotNull { it.getOrNull(2) }.toSet()
        val places = mapOf("rs" to names("ruby") + names("sapphire"), "e" to names("emerald"))
        val rows = NuzlockeStatics.all(NuzlockeSystem.GEN3)
        assertEquals(9, rows.size)
        assertEquals(rows.size, rows.map { Triple(it.game, it.place.lowercase(), it.level) }.toSet().size, "two rows for one game, place and level")
        for (r in rows) {
            assertTrue(r.place in places.getValue(r.game), "gen3 ${r.game}: ${r.place} is not a place the tracker names")
            assertTrue(r.level in 1..100)
            assertTrue(r.species == null || r.species in 1..251, "${r.place}: species ${r.species}")
        }
    }

    // ================================================================ statics

    @Test
    fun `a static names its game, place and level, and a species check is a national dex number of that generation`() {
        val most = mapOf(NuzlockeSystem.GEN1 to 151, NuzlockeSystem.GEN2 to 251, NuzlockeSystem.GEN4 to 493, NuzlockeSystem.GEN5 to 649)
        for ((system, top) in most) {
            val rows = NuzlockeStatics.all(system)
            assertTrue(rows.size >= 12, "${system.key} has only ${rows.size} statics")
            val keys = rows.map { Triple(it.game, it.place.lowercase(), it.level) }
            assertEquals(keys.size, keys.toSet().size, "${system.key}: two rows for one game, place and level")
            for (r in rows) {
                assertTrue(r.place.isNotBlank() && r.place.all { it.code in 32..126 }, r.place)
                assertTrue(r.species == null || r.species in 1..top, "${system.key} ${r.place}: species ${r.species}")
            }
        }
    }

    @Test
    fun `a static with a species check matches only that species, and one without it any`() {
        val g1 = listOf("rb")
        assertTrue(NuzlockeStatics.isStatic(NuzlockeSystem.GEN1, g1, "Route 16", 30), "Snorlax's row names no species")
        assertTrue(NuzlockeStatics.isStatic(NuzlockeSystem.GEN1, g1, "route 16", 30, 143), "the place is not case sensitive")
        assertFalse(NuzlockeStatics.isStatic(NuzlockeSystem.GEN1, g1, "Route 16", 31))
        assertTrue(NuzlockeStatics.isStatic(NuzlockeSystem.GEN1, g1, "Route 12", 30, 143))
        assertFalse(NuzlockeStatics.isStatic(NuzlockeSystem.GEN1, g1, "Route 12", 30, 44))
        assertFalse(NuzlockeStatics.isStatic(NuzlockeSystem.GEN1, g1, "Route 12", 30, null))
        assertTrue(NuzlockeStatics.isStatic(NuzlockeSystem.GEN1, listOf("y"), "Power Plant", 40, 100), "Yellow has the Voltorb rows too")
        assertFalse(NuzlockeStatics.isStatic(NuzlockeSystem.GEN1, g1, null, 30))
        assertFalse(NuzlockeStatics.isStatic(NuzlockeSystem.GEN1, g1, " ", 30))
        assertFalse(NuzlockeStatics.isStatic(NuzlockeSystem.GEN1, listOf("c"), "Route 16", 30), "another game's key")
    }

    @Test
    fun `the statics parser skips what it cannot read and keeps what it can`() {
        val text = "# comment\n\nrb\tRoute 16\t30\tSnorlax\tnote\nrb\tBad\tnotalevel\tX\tnote\nrb\tShort\nrb\tRoute 12\t30\tSnorlax\tnote\t143\ny\tPlace\t101\tX\tn\n"
        val parsed = NuzlockeStatics.parse(text)
        assertEquals(listOf("Route 16", "Route 12"), parsed.map { it.place })
        assertEquals(listOf(null, 143), parsed.map { it.species })
    }

    // ================================================================ evolution lines

    private fun lines(system: NuzlockeSystem): List<List<String>> =
        rows("/nuzlocke/families-${system.key}.tsv").map { r -> r[0].substringBefore('#').split(',').map { it.trim() }.filter { it.isNotEmpty() } }

    @Test
    fun `every name in the Game Boy families is a species, and every number in the DS families is one of its game`() {
        NuzlockeFamilies.load(resource("/nuzlocke/families-gen1.tsv"), resource("/natdex/species.tsv"), strict = true)
        NuzlockeFamilies.load(resource("/nuzlocke/families-gen2.tsv"), resource("/natdex/species.tsv"), strict = true)
        for ((system, top) in listOf(NuzlockeSystem.GEN4 to 493, NuzlockeSystem.GEN5 to 649)) {
            val all = lines(system).flatten()
            assertTrue(all.all { it.toIntOrNull() in 1..top }, "${system.key}: every token is a species number up to $top")
            NuzlockeFamilies.load(resource("/nuzlocke/families-${system.key}.tsv"), resource("/nuzlocke/species-${system.key}.tsv"), strict = true)
        }
    }

    @Test
    fun `a species is on one line at most, and a line is at least two species`() {
        for (system in listOf(NuzlockeSystem.GEN1, NuzlockeSystem.GEN2, NuzlockeSystem.GEN4, NuzlockeSystem.GEN5)) {
            val ls = lines(system)
            assertTrue(ls.all { it.size >= 2 }, "${system.key}: a line of one is no line")
            val all = ls.flatten()
            assertEquals(all.size, all.toSet().size, "${system.key}: ${all.groupBy { it }.filter { it.value.size > 1 }.keys} is on two lines")
        }
    }

    @Test
    fun `the Generation 1 lines are Generation 1's, and only the species of the game are in them`() {
        val ls = lines(NuzlockeSystem.GEN1)
        assertEquals(54, ls.size); assertEquals(126, ls.flatten().size)
        val ids = ls.flatten().map { assertNotNull(NuzlockeFamilies.speciesId(it, NuzlockeSystem.GEN1), it) }
        assertTrue(ids.all { it in 1..151 })
        fun same(a: String, b: String) = NuzlockeFamilies.sameLine(NuzlockeFamilies.speciesId(a, NuzlockeSystem.GEN1)!!, NuzlockeFamilies.speciesId(b, NuzlockeSystem.GEN1)!!, NuzlockeSystem.GEN1)
        assertTrue(same("Eevee", "Flareon") && same("Eevee", "Vaporeon") && same("Eevee", "Jolteon"))
        assertTrue(same("Poliwag", "Poliwrath")); assertFalse(same("Nidoran F", "Nidoran M"), "two lines")
        assertTrue(same("Nidoran F", "Nidoqueen") && same("Nidoran M", "Nidoking"))
        assertFalse(same("Pikachu", "Eevee"))
        assertEquals(setOf("Eevee", "Vaporeon", "Jolteon", "Flareon"), NuzlockeFamilies.members(NuzlockeFamilies.speciesId("Eevee")!!, NuzlockeSystem.GEN1).map { id -> lines(NuzlockeSystem.GEN1).flatten().first { NuzlockeFamilies.speciesId(it, NuzlockeSystem.GEN1) == id } }.toSet())
    }

    @Test
    fun `the DS tables know the games' own lines, branches and babies`() {
        fun id(system: NuzlockeSystem, name: String) = assertNotNull(NuzlockeFamilies.speciesId(name, system), name)
        fun same(system: NuzlockeSystem, a: String, b: String) = NuzlockeFamilies.sameLine(id(system, a), id(system, b), system)
        for (system in listOf(NuzlockeSystem.GEN4, NuzlockeSystem.GEN5)) {
            for ((baby, adult) in listOf("Pichu" to "Raichu", "Cleffa" to "Clefable", "Igglybuff" to "Wigglytuff", "Togepi" to "Togekiss", "Tyrogue" to "Hitmontop",
                "Smoochum" to "Jynx", "Elekid" to "Electivire", "Magby" to "Magmortar", "Azurill" to "Azumarill", "Wynaut" to "Wobbuffet",
                "Budew" to "Roserade", "Munchlax" to "Snorlax", "Riolu" to "Lucario", "Mime Jr." to "Mr. Mime", "Happiny" to "Blissey", "Bonsly" to "Sudowoodo"))
                assertTrue(same(system, baby, adult), "${system.key}: $baby and $adult")
            assertTrue(same(system, "Eevee", "Umbreon") && same(system, "Eevee", "Leafeon") && same(system, "Eevee", "Glaceon"), system.key)
            assertTrue(same(system, "Wurmple", "Dustox") && same(system, "Wurmple", "Beautifly"), system.key)
            assertTrue(same(system, "Ralts", "Gallade") && same(system, "Ralts", "Gardevoir"), system.key)
            assertTrue(same(system, "Snorunt", "Froslass") && same(system, "Snorunt", "Glalie"), system.key)
            assertTrue(same(system, "Poliwag", "Politoed") && same(system, "Slowpoke", "Slowking"), system.key)
            assertFalse(same(system, "Nidoran F", "Nidoran M"), system.key)
            assertFalse(same(system, "Pikachu", "Eevee"), system.key)
        }
        assertTrue(same(NuzlockeSystem.GEN5, "Larvesta", "Volcarona") && same(NuzlockeSystem.GEN5, "Deerling", "Sawsbuck") && same(NuzlockeSystem.GEN5, "Tympole", "Seismitoad"))
        assertNull(NuzlockeFamilies.speciesId("Sylveon", NuzlockeSystem.GEN5), "no Fairy Pokemon before Generation 6")
        assertNull(NuzlockeFamilies.speciesId("Victini", NuzlockeSystem.GEN4), "Victini is Generation 5")
    }

    // ================================================================ species tables of the DS games

    @Test
    fun `the DS species tables have every species of the game, with a ratio byte and types the games use`() {
        for ((system, top) in listOf(NuzlockeSystem.GEN4 to 493, NuzlockeSystem.GEN5 to 649)) {
            val rows = rows("/nuzlocke/species-${system.key}.tsv")
            assertEquals(top, rows.size, system.key)
            assertEquals((1..top).toList(), rows.map { it[0].toInt() }, "${system.key}: every number once, in order")
            for (r in rows) {
                val ratio = r[2].toInt()
                assertTrue(ratio in 0..255, "${system.key} ${r[1]}: ratio $ratio")
                for (t in r.subList(3, 5).map { it.toInt() }) assertTrue(t in 0..17 && t != 9, "${system.key} ${r[1]}: type $t (9 is the ??? slot and there is no 18)")
                assertTrue(r[1].isNotBlank() && r[1].all { it.code in 32..126 }, "${system.key} ${r[0]}")
            }
            fun row(id: Int) = assertNotNull(NuzlockeSpecies.row(system, id), "$id")
            assertEquals(31, row(1).genderRatio); assertEquals(listOf(12, 3), listOf(row(1).type1, row(1).type2))
            assertEquals(127, row(25).genderRatio); assertEquals(listOf(13), NuzlockeSpecies.types(system, 25))
            assertEquals(255, row(81).genderRatio); assertEquals(listOf(13, 8), NuzlockeSpecies.types(system, 81), "Magnemite: Electric and Steel")
            assertEquals(254, row(113).genderRatio, "Chansey")
            assertEquals(0, row(128).genderRatio, "Tauros")
            assertEquals(listOf(11, 4), NuzlockeSpecies.types(system, 195), "Quagsire: Water and Ground")
            assertEquals(listOf(8, 4), NuzlockeSpecies.types(system, 208), "Steelix")
            assertEquals(listOf(17), NuzlockeSpecies.types(system, 197), "Umbreon is Dark alone")
            assertEquals(255, row(151).genderRatio, "Mew has no gender")
            assertEquals(listOf(10, 2), NuzlockeSpecies.types(system, 6), "Charizard: Fire and Flying")
        }
        assertEquals(255, NuzlockeSpecies.row(NuzlockeSystem.GEN5, 649)!!.genderRatio, "Genesect")
        assertEquals(listOf(6, 8), NuzlockeSpecies.types(NuzlockeSystem.GEN5, 649), "Genesect: Bug and Steel")
        assertEquals(255, NuzlockeSpecies.row(NuzlockeSystem.GEN4, 493)!!.genderRatio, "Arceus")
        assertNull(NuzlockeSpecies.row(NuzlockeSystem.GEN4, 494), "Victini is a Generation 5 Pokemon")
        assertEquals(494, NuzlockeSpecies.row(NuzlockeSystem.GEN5, 494)!!.id)
    }

    @Test
    fun `gender from a personality value follows the ratio, at its edges too`() {
        val g = NuzlockeSystem.GEN4
        assertEquals(Gender.FEMALE, NuzlockeSpecies.gender(g, 1, 0x1234_5600L), "low byte 0 is under 31")
        assertEquals(Gender.FEMALE, NuzlockeSpecies.gender(g, 1, 0x1234_561EL), "30")
        assertEquals(Gender.MALE, NuzlockeSpecies.gender(g, 1, 0x1234_561FL), "31 is not under 31")
        assertEquals(Gender.MALE, NuzlockeSpecies.gender(g, 128, 0xFFFF_FF00L), "Tauros is always male")
        assertEquals(Gender.FEMALE, NuzlockeSpecies.gender(g, 113, 0xFFL), "Chansey is always female")
        assertNull(NuzlockeSpecies.gender(g, 81, 0x55L), "Magnemite has none")
        assertNull(NuzlockeSpecies.gender(g, 9999, 0x55L), "an unknown species")
        assertEquals(Gender.FEMALE, NuzlockeSpecies.gender(g, 25, 0x7EL), "127 against 127: 126 is under it")
        assertEquals(Gender.MALE, NuzlockeSpecies.gender(g, 25, 0x7FL))
    }

    // ================================================================ the words

    @Test
    fun `nothing a player reads in these files or notes has a dash the copy rules forbid`() {
        val bad = setOf('\u2014', '\u2013')
        for (path in listOf("levelcaps-gen1", "levelcaps-gen2", "levelcaps-gen4", "levelcaps-gen5", "trainers-gen1", "trainers-gen2", "areas-gen1", "areas-gen2",
            "statics-gen1", "statics-gen2", "statics-gen3", "statics-gen4", "statics-gen5", "families-gen1", "families-gen2", "families-gen4", "families-gen5", "species-gen4", "species-gen5")) {
            val text = resource("/nuzlocke/$path.tsv")
            assertTrue(text.none { it in bad }, "$path has a dash")
            assertTrue(text.all { it.code < 128 }, "$path is not plain ASCII")
        }
    }
}
