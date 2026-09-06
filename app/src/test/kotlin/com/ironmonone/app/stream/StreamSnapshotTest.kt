package com.ironmonone.app.stream

import com.ironmonone.tracker.BaseStats
import com.ironmonone.tracker.EnemyInfo
import com.ironmonone.tracker.PokemonDecoder
import com.ironmonone.tracker.MoveRow
import com.ironmonone.tracker.TrackedMon
import com.ironmonone.tracker.TrackerState
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The stream snapshot obeys the tracker's information rule: the enemy card
 * carries marks, seen moves and the ability GUESS, never the real numbers.
 */
class StreamSnapshotTest {

    private fun mon(species: Int, level: Int) = PokemonDecoder.Mon(
        pid = 1, level = level, nickname = "", species = species, heldItem = 0, friendship = 0,
        moves = listOf(1, 2), pp = listOf(10, 20), ivs = List(6) { 0 }, evs = List(6) { 0 },
        ppUps = List(4) { 0 }, abilitySlot = 0, nature = 0, shiny = false, status = 0,
        curHp = 61, maxHp = 74, atk = 55, def = 40, spe = 52, spAtk = 61, spDef = 38,
    )

    private val base = BaseStats(70, 94, 50, 66, 94, 50, type1 = 6, type2 = 2, ability1 = 68, ability2 = 0)

    @Test
    fun `own card carries real stats and moves, enemy card carries marks and guesses`() {
        val own = TrackedMon(mon(414, 23), "Mothim", listOf("Gust"), base, abilityName = "Swarm",
            itemName = "Oran Berry", moveRows = listOf(MoveRow(16, "Gust", 31, 35, 40, 100, 2, "PHYSICAL")),
            movesLearned = 6, movesTotal = 11, nextMoveLevel = 26)
        val enemy = EnemyInfo(551, "Sandile", 21, 33, 60, 4, 17, BaseStats(50, 72, 35, 65, 35, 35, 4, 17, 22, 153),
            movesSeen = listOf("Bite"), abilityGuess = "Intimidate / Moxie")
        val state = TrackerState(1, listOf(own), inBattle = true, isWildBattle = true,
            enemyTeam = listOf(true, false), enemy = enemy, badges = 0b101, routeName = "Route 3",
            routeSpecies = listOf(1, 2, 3), mapId = 7, steps = 12, healPercent = 44, healCount = 7)
        val notes = StreamSnapshot.Notes(
            marksOf = { if (it == 551) intArrayOf(0, 1, 0, 0, 2, 3) else IntArray(6) },
            movesSeenOf = { if (it == 551) listOf("Sand Tomb") else emptyList() },
            encountersOf = { 2 }, lastSeenLevelOf = { 19 }, routeSeenOf = { 2 },
        )
        val snap = StreamSnapshot.build(StreamSnapshot.Run("FireRed", "GBA", 812, true), state, null, notes)
        val json = Json.write(snap)

        @Suppress("UNCHECKED_CAST")
        val p = (snap["party"] as List<Map<String, Any?>>)[0]
        assertEquals("Mothim", p["name"]); assertEquals(listOf("Bug", "Flying"), p["types"])
        assertEquals(55, (p["stats"] as Map<*, *>)["atk"])
        @Suppress("UNCHECKED_CAST")
        val e = snap["enemy"] as Map<String, Any?>
        assertEquals(listOf(0, 1, 0, 0, 2, 3), e["marks"])
        assertEquals(listOf("Sand Tomb", "Bite"), e["movesSeen"])
        assertEquals(55, e["hpPercent"])
        assertNull(e["ability"], "no reveal, no ability")
        assertEquals("Intimidate / Moxie", e["abilityGuess"])
        assertFalse(e.containsKey("stats"), "the enemy's real stats never leave the phone")
        assertFalse(json.contains("\"atk\":72"), "Sandile's real attack is not in the JSON anywhere")
        assertEquals(listOf(true, false), snap["enemyTeam"])
        assertEquals(812, snap["attempt"]); assertEquals(2, ((snap["route"] as Map<*, *>)["seen"]))
        assertContains(json, "\"outcome\":null")
    }

    @Test
    fun `no state is an empty, valid snapshot`() {
        val snap = StreamSnapshot.build(StreamSnapshot.Run("Hack", "GBA", 1, false), null, null, StreamSnapshot.Notes())
        assertEquals(false, snap["tracked"]); assertEquals(emptyList<Any>(), snap["party"]); assertNull(snap["enemy"])
        assertTrue(Json.write(snap).startsWith("{\"app\":\"KaizoCore\""))
    }
}
