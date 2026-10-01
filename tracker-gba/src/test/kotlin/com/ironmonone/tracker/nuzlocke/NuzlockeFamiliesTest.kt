package com.ironmonone.tracker.nuzlocke

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** The evolution lines the dupes clause reads (2026-09-29). */
class NuzlockeFamiliesTest {

    private fun idOf(name: String): Int {
        val line = NuzlockeFamiliesTest::class.java.getResourceAsStream("/natdex/species.tsv")!!.bufferedReader(Charsets.UTF_8)
            .lineSequence().first { it.substringAfter('\t') == name }
        return line.substringBefore('\t').toInt()
    }

    @Test
    fun `every name in the Generation 1 to 3 block of the table is a species`() {
        // Strict: a typo throws here instead of quietly leaving a line out.
        val lines = NuzlockeFamilies.load(strict = true)
        assertTrue(lines.size > 250, "only ${lines.size} species are on a line")
    }

    @Test
    fun `a name that is not a species is caught in strict mode, and skipped after the natdex marker`() {
        val names = "1\tBulbasaur\n2\tIvysaur\n3\tVenusaur\n"
        assertFailsWith<IllegalStateException> { NuzlockeFamilies.load("Bulbasaur,Ivysaurr\n", names, strict = true) }
        // Lenient (the app's own mode) and the natdex block: unresolved names are dropped, the rest stands.
        assertEquals(mapOf(1 to 1, 2 to 1), NuzlockeFamilies.load("Bulbasaur,Ivysaur,Nothing\n", names, strict = false))
        assertEquals(mapOf(1 to 1, 2 to 1), NuzlockeFamilies.load("Bulbasaur,Ivysaur\n#natdex\nIvysaur,Nothing\n", names, strict = true))
    }

    @Test
    fun `an evolution line is one line from the first stage to the last`() {
        for (line in listOf(
            listOf("Bulbasaur", "Ivysaur", "Venusaur"), listOf("Pidgey", "Pidgeotto", "Pidgeot"), listOf("Rattata", "Raticate"),
            listOf("Treecko", "Grovyle", "Sceptile"), listOf("Zigzagoon", "Linoone"), listOf("Magikarp", "Gyarados"),
            listOf("Dratini", "Dragonair", "Dragonite"), listOf("Larvitar", "Pupitar", "Tyranitar"),
        )) {
            val root = NuzlockeFamilies.lineOf(idOf(line[0]))
            for (n in line) assertEquals(root, NuzlockeFamilies.lineOf(idOf(n)), n)
        }
        assertNotEquals(NuzlockeFamilies.lineOf(Sp.PIDGEY), NuzlockeFamilies.lineOf(Sp.RATTATA))
        assertTrue(NuzlockeFamilies.sameLine(Sp.PIDGEY, Sp.PIDGEOT))
        assertTrue(NuzlockeFamilies.sameLine(Sp.RATTATA, Sp.RATICATE))
    }

    @Test
    fun `babies belong to the line they hatch into`() {
        for ((baby, adult) in listOf("Pichu" to "Raichu", "Cleffa" to "Clefable", "Igglybuff" to "Wigglytuff", "Togepi" to "Togetic",
            "Tyrogue" to "Hitmontop", "Smoochum" to "Jynx", "Elekid" to "Electabuzz", "Magby" to "Magmar", "Azurill" to "Azumarill", "Wynaut" to "Wobbuffet"))
            assertTrue(NuzlockeFamilies.sameLine(idOf(baby), idOf(adult)), "$baby and $adult")
    }

    @Test
    fun `a branching line holds every branch`() {
        val eevee = listOf("Eevee", "Vaporeon", "Jolteon", "Flareon", "Espeon", "Umbreon").map { idOf(it) }
        assertEquals(1, eevee.map { NuzlockeFamilies.lineOf(it) }.toSet().size)
        val wurmple = listOf("Wurmple", "Silcoon", "Beautifly", "Cascoon", "Dustox").map { idOf(it) }
        assertEquals(1, wurmple.map { NuzlockeFamilies.lineOf(it) }.toSet().size)
        assertEquals(wurmple.sorted(), NuzlockeFamilies.members(idOf("Dustox")).filter { it < 412 }.sorted())
        val nincada = listOf("Nincada", "Ninjask", "Shedinja").map { idOf(it) }
        assertEquals(1, nincada.map { NuzlockeFamilies.lineOf(it) }.toSet().size)
        assertEquals(1, listOf("Poliwag", "Poliwrath", "Politoed").map { NuzlockeFamilies.lineOf(idOf(it)) }.toSet().size)
        assertEquals(1, listOf("Gloom", "Vileplume", "Bellossom").map { NuzlockeFamilies.lineOf(idOf(it)) }.toSet().size)
    }

    @Test
    fun `lines that only look alike stay apart`() {
        assertNotEquals(NuzlockeFamilies.lineOf(idOf("Nidoran F")), NuzlockeFamilies.lineOf(idOf("Nidoran M")))
        assertNotEquals(NuzlockeFamilies.lineOf(idOf("Roselia")), NuzlockeFamilies.lineOf(idOf("Oddish")))
        assertNotEquals(NuzlockeFamilies.lineOf(idOf("Plusle")), NuzlockeFamilies.lineOf(idOf("Minun")))
        assertNotEquals(NuzlockeFamilies.lineOf(idOf("Volbeat")), NuzlockeFamilies.lineOf(idOf("Illumise")))
        assertNotEquals(NuzlockeFamilies.lineOf(idOf("Kadabra")), NuzlockeFamilies.lineOf(idOf("Machoke")))
    }

    @Test
    fun `a species on no line is its own line, and so is an id no game has`() {
        for (alone in listOf("Kangaskhan", "Absol", "Ditto", "Lapras", "Spinda")) {
            val id = idOf(alone)
            assertEquals(id, NuzlockeFamilies.lineOf(id), alone)
            assertEquals(listOf(id), NuzlockeFamilies.members(id), alone)
        }
        assertEquals(999999, NuzlockeFamilies.lineOf(999999))
        assertEquals(listOf(999999), NuzlockeFamilies.members(999999))
    }

    @Test
    fun `a line is named by its lowest id, and every member is at or above it`() {
        for (id in 1..411) {
            val line = NuzlockeFamilies.lineOf(id)
            assertTrue(line <= id, "$id is on line $line")
            assertEquals(line, NuzlockeFamilies.lineOf(line), "the root of $id is not its own root")
        }
    }

    @Test
    fun `the Nat Dex block joins later evolutions and babies to their vanilla line`() {
        // Magnezone and Leafeon exist only on a Nat. Dex build; they share their line with the Gen 1 to 3 species.
        assertTrue(NuzlockeFamilies.sameLine(idOf("Magneton"), idOf("Magnezone")))
        assertTrue(NuzlockeFamilies.sameLine(idOf("Eevee"), idOf("Leafeon")))
        assertTrue(NuzlockeFamilies.sameLine(idOf("Eevee"), idOf("Sylveon")))
        assertTrue(NuzlockeFamilies.sameLine(idOf("Rhydon"), idOf("Rhyperior")))
        assertTrue(NuzlockeFamilies.sameLine(idOf("Roselia"), idOf("Budew")))
        assertTrue(NuzlockeFamilies.sameLine(idOf("Roselia"), idOf("Roserade")))
        assertTrue(NuzlockeFamilies.sameLine(idOf("Chansey"), idOf("Happiny")))
        assertTrue(NuzlockeFamilies.sameLine(idOf("Snorlax"), idOf("Munchlax")))
        // And they do not swallow a neighbour.
        assertNotEquals(NuzlockeFamilies.lineOf(idOf("Roselia")), NuzlockeFamilies.lineOf(idOf("Magneton")))
    }

    @Test
    fun `a name typed by hand finds its species, whatever the case and spacing`() {
        // The ledger's area editor turns what the player types into a species so the dupes clause can see it.
        assertEquals(25, NuzlockeFamilies.speciesId("Pikachu"))
        assertEquals(25, NuzlockeFamilies.speciesId("  pIKAchu "))
        assertEquals(idOf("Treecko"), NuzlockeFamilies.speciesId("treecko"))
        assertEquals(idOf("Nidoran F"), NuzlockeFamilies.speciesId("nidoran f"))
        assertEquals(null, NuzlockeFamilies.speciesId("Pikachuu"))
        assertEquals(null, NuzlockeFamilies.speciesId(""))
        // The table's gaps are not species.
        assertEquals(null, NuzlockeFamilies.speciesId("none"))
    }
}
