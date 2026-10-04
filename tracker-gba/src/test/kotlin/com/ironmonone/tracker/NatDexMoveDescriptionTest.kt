package com.ironmonone.tracker

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Every Nat. Dex and MaxDex move has a description on the info screen (Blake, 2026-10-03: "fairy wind didn't have a
 * description in emerald, need to add descriptions to all moves"). Past Gen 3's 354 the text is natdex/movedesc.tsv,
 * made by tools/trainer-data/convert_natdex_move_desc.py from three sources in order: the Nat. Dex Extension's own
 * descriptions, the DS tracker's for a move Black and White had, then Pokemon Showdown's shortDesc, with KaizoCore's
 * own line where that says nothing a player can use. MaxDex finds it by name.
 */
class NatDexMoveDescriptionTest {
    private val zero = MemoryReader { _, n -> ByteArray(n) }
    private val emerald = GbaTracker(zero, GameMap.EMERALD_U)
    private val fireRed = GbaTracker(zero, GameMap.FIRERED_U_V10)
    private val natDex = GbaTracker(zero, GameMap.EMERALD_U.copy(namesFromLists = true, expandedSpeciesIds = true))
    private val natDexFireRed = GbaTracker(zero, GameMap.FIRERED_U_V10.copy(namesFromLists = true, expandedSpeciesIds = true))
    private val maxDex = GbaTracker(zero, GameMap.MAXDEX_FR_10)

    /** A resource table's rows by id, its # lines left out. */
    private fun table(resource: String): Map<Int, List<String>> =
        javaClass.getResourceAsStream(resource)!!.bufferedReader(Charsets.UTF_8).readLines()
            .filter { !it.startsWith("#") }.map { it.split('\t') }
            .mapNotNull { p -> p[0].toIntOrNull()?.let { it to p } }.toMap()

    /** id to the second column of a resource table. */
    private fun rows(resource: String): Map<Int, String> = table(resource).mapValues { it.value[1] }

    private fun key(name: String) = name.lowercase().filter { it in 'a'..'z' || it in '0'..'9' }

    private val described by lazy { table("/natdex/movedesc.tsv") }

    @Test
    fun `Fairy Wind keeps the extension's own description on Nat Dex and on MaxDex`() {
        val text = "Deals damage and has no secondary effect."   // NatDexExtension.lua, natDexMoveDescriptions[584]
        assertEquals("Fairy Wind", natDex.moveName(584))
        assertEquals(text, natDex.moveDescription(584))
        assertEquals(text, natDexFireRed.moveDescription(584))
        assertEquals("Fairy Wind", maxDex.moveName(358))
        assertEquals(text, maxDex.moveDescription(358), "MaxDex numbers it 358 and finds it by name")
        assertEquals("natdex", described[584]!![3], "the extension's text comes first")
    }

    @Test
    fun `a move Black and White already had keeps the DS tracker's words, by id on Nat Dex and by name on MaxDex`() {
        val roost = "Heals the user for half its max HP. If the user is flying, its flying type is ignored until the end of this turn."
        assertEquals(roost, natDex.moveDescription(355))
        assertEquals(roost, maxDex.moveDescription(361))
        val uTurn = natDex.moveDescription(369)
        assertTrue(uTurn!!.contains("switches out"), uTurn)
        assertEquals(uTurn, maxDex.moveDescription(375))
        // Every DS row is the DS tracker's own Gen 5 text, word for word; it numbers these moves as Nat. Dex does.
        val ds = File("../tracker-nds/src/main/resources/nds/move-desc.tsv").readLines(Charsets.UTF_8)
            .filter { !it.startsWith("#") }.map { it.split('\t') }.associate { it[0].toInt() to it[2] }
        val dsRows = described.filterValues { it[3] == "nds" }
        assertEquals(201, dsRows.size)
        for ((id, row) in dsRows) assertEquals(ds[id], row[2], "$id ${row[1]}")
    }

    @Test
    fun `a move from X and Y on takes Pokemon Showdown's short description, never the long one`() {
        val flyingPress = "Combines Flying in its type effectiveness."   // data/text/moves.ts, flyingpress.shortDesc
        assertEquals("Flying Press", natDex.moveName(560))
        assertEquals(flyingPress, natDex.moveDescription(560))
        val maxDexId = rows("/maxdex/moves.tsv").entries.single { it.value == "Flying Press" }.key
        assertEquals(flyingPress, maxDex.moveDescription(maxDexId))
        assertEquals("50% chance to badly poison the target.", natDex.moveDescription(847), "Malignant Chain, the last")
        val doodle = natDex.moveDescription(rows("/natdex/moves.tsv").entries.single { it.value == "Doodle" }.key)!!
        assertTrue(doodle.length < 100, "Doodle's desc ran to 837 characters: $doodle")
    }

    @Test
    fun `KaizoCore's own lines stand in where Showdown's says nothing a player can use`() {
        val own = described.values.filter { it[3] == "kaizocore" }.associate { it[1] to it[2] }
        assertEquals(
            setOf("Happy Hour", "Celebrate", "Hold Hands", "High Horsepower", "Leafage", "Dragon Hammer", "Dynamax Cannon",
                "Behemoth Blade", "Behemoth Bash", "Branch Poke"),
            own.keys,
        )
        assertEquals("Doubles the prize money from the battle.", natDex.moveDescription(603), "Happy Hour")
        assertEquals("The Pokémon congratulates you. It has no effect in battle.", natDex.moveDescription(606), "Celebrate")
        assertEquals("Inflicts regular damage with no additional effect.", own["Dynamax Cannon"])
        for (t in listOf(natDex, natDexFireRed, maxDex)) for (id in 1..t.lastMoveId) {
            val d = t.moveDescription(id)!!
            assertFalse("no competitive use" in d.lowercase(), "$id: $d")
            assertTrue(d != "No additional effect.", "$id: $d")
        }
    }

    @Test
    fun `nothing from Showdown or KaizoCore runs longer than the longest Gen 3 description`() {
        val gen3 = table("/gen3/movedesc.tsv").values
        val cap = gen3.maxOf { it[2].length }
        assertEquals(174, cap, "Magnitude's, the longest in gen3/movedesc.tsv")
        assertEquals("Magnitude", gen3.single { it[2].length == cap }[1])
        val capped = described.values.filter { it[3] == "showdown" || it[3] == "kaizocore" }
        assertEquals(281, capped.size)
        for (row in capped) assertTrue(row[2].length <= cap, "${row[0]} ${row[1]}: ${row[2].length} characters")
    }

    @Test
    fun `every Nat Dex and MaxDex move has a description, and none is a placeholder or carries an em dash`() {
        assertTrue(described.keys.all { it > GbaTracker.VANILLA_LAST_MOVE }, "ids 1-354 keep gen3/movedesc.tsv's text")
        val natDexMoves = rows("/natdex/moves.tsv")
        for ((id, row) in described) {
            assertEquals(natDexMoves[id], row[1], "movedesc.tsv's name for $id is natdex/moves.tsv's")
            assertTrue(row[3] in setOf("natdex", "nds", "showdown", "kaizocore"), "$id ${row[1]}: source ${row[3]}")
        }
        assertEquals(natDexMoves.keys.filter { it > GbaTracker.VANILLA_LAST_MOVE }.toSet(), described.keys, "one row per move past 354")
        for (t in listOf(natDex, natDexFireRed)) {
            val left = natDexMoves.keys.filter { t.moveDescription(it) == null }
            assertEquals(emptyList(), left, "Nat. Dex moves with no description")
        }
        val maxDexMoves = rows("/maxdex/moves.tsv")
        assertEquals(841, maxDexMoves.size)
        assertEquals(emptyList(), maxDexMoves.keys.filter { maxDex.moveDescription(it) == null }, "MaxDex moves with no description")
        val names = described.values.map { key(it[1]) }.toSet()
        for ((id, name) in maxDexMoves) if (id > GbaTracker.VANILLA_LAST_MOVE) assertTrue(key(name) in names, "MaxDex $id $name")

        for (t in listOf(natDex, natDexFireRed, maxDex)) for (id in 1..t.lastMoveId) {
            val d = t.moveDescription(id)!!
            assertFalse("not implemented yet" in d.lowercase(), "the extension's placeholder is not a description ($id)")
            assertFalse(d.contains(0x2014.toChar()), "no em dash ($id)")
        }
    }

    @Test
    fun `ids 1 to 354 keep the Gen 3 text on every build, and the five games are unchanged`() {
        val gen3 = rows("/gen3/movedesc.tsv")
        assertEquals(354, gen3.size)
        for (id in 1..GbaTracker.VANILLA_LAST_MOVE) {
            val text = emerald.moveDescription(id)
            assertNotNull(text, "Emerald $id")
            for (t in listOf(fireRed, natDex, natDexFireRed, maxDex)) assertEquals(text, t.moveDescription(id), "move $id")
        }
        assertEquals("Deals damage and has no secondary effect.", emerald.moveDescription(1))
        for (t in listOf(emerald, fireRed)) for (id in listOf(0, 355, 560, 584, 847, 9999)) assertNull(t.moveDescription(id), "$id")
        for (t in listOf(natDex, maxDex)) for (id in listOf(0, -1, 9999)) assertNull(t.moveDescription(id), "$id")
    }
}
