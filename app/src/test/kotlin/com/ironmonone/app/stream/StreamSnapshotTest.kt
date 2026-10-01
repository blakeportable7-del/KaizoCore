package com.ironmonone.app.stream

import com.ironmonone.tracker.BaseStats
import com.ironmonone.tracker.EnemyInfo
import com.ironmonone.app.TrackerOptions
import com.ironmonone.tracker.GameOver
import com.ironmonone.tracker.PokemonDecoder
import com.ironmonone.tracker.MoveRow
import com.ironmonone.tracker.RandomizedFlags
import com.ironmonone.tracker.RunOutcome
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
        assertNull(e["abilityGuess"], "randomization unknown: the ROM's two abilities stay on the phone, as on its card")
        assertFalse(e.containsKey("hp") || e.containsKey("maxHp"), "a percentage only, as the bar in the game")
        assertFalse(e.containsKey("stats"), "the enemy's real stats never leave the phone")
        assertFalse(json.contains("\"atk\":72"), "Sandile's real attack is not in the JSON anywhere")
        assertEquals(listOf(true, false), snap["enemyTeam"])
        assertEquals(812, snap["attempt"]); assertEquals(2, ((snap["route"] as Map<*, *>)["seen"]))
        assertContains(json, "\"outcome\":null")
    }

    @Test
    fun `moves read as the phone shows them, not the ROM's raw power`() {
        // Return at full friendship and a Weather Ball in the rain (QA 2026-09-29: the page said 1 and 50).
        val returner = mon(123, 25).copy(friendship = 255, moves = listOf(216, 311), pp = listOf(20, 10))
        val rows = listOf(MoveRow(216, "RETURN", 20, 20, 1, 100, 0, "PHY"), MoveRow(311, "WEATHER BALL", 10, 10, 50, 100, 0, "PHY"))
        val own = TrackedMon(returner, "SCYTHER", listOf("RETURN", "WEATHER BALL"), base, moveRows = rows)
        val state = TrackerState(1, listOf(own), inBattle = false, isWildBattle = false, weather = "RAIN")
        val snap = StreamSnapshot.build(StreamSnapshot.Run("Emerald", "GBA", 1, true), state, null, StreamSnapshot.Notes())
        @Suppress("UNCHECKED_CAST")
        val moves = ((snap["party"] as List<Map<String, Any?>>)[0]["moves"] as List<Map<String, Any?>>)
        val phone = with(com.ironmonone.app.MoveDecorAccess) {
            rows.map { it.shown(com.ironmonone.app.ownMoveContext(own, null, "RAIN", null)) }
        }
        assertEquals(phone.map { it.powerText }, moves.map { it["power"].toString() }, "the page's power is the phone's")
        assertFalse(moves[0]["power"] == 1, "Return is not the ROM's placeholder 1")
    }

    private fun battle(rand: RandomizedFlags?, gameOver: GameOver? = null): TrackerState {
        val own = TrackedMon(mon(414, 23), "Mothim", listOf("Gust"), base, abilityName = "Swarm", itemName = "Oran Berry",
            moveRows = listOf(MoveRow(16, "Gust", 31, 35, 40, 100, 2, "PHYSICAL")))
        val enemy = EnemyInfo(551, "Sandile", 21, 33, 60, 4, 17, BaseStats(50, 72, 35, 65, 35, 35, 4, 17, 22, 153),
            movesSeen = listOf("Bite"), abilityGuess = "Intimidate / Moxie")
        return TrackerState(1, listOf(own), inBattle = true, isWildBattle = true, enemy = enemy, randomized = rand,
            gameDataRandomized = rand?.gameData ?: true, gameOver = gameOver)
    }

    private fun flags(types: Boolean = false, abilities: Boolean = false) =
        RandomizedFlags(types, abilities, false, false, false, false, false, false, false, false)

    @Suppress("UNCHECKED_CAST")
    private fun enemyOf(snap: Map<String, Any?>) = snap["enemy"] as Map<String, Any?>

    @Suppress("UNCHECKED_CAST")
    private fun leadOf(snap: Map<String, Any?>) = (snap["party"] as List<Map<String, Any?>>)[0]

    /** The IronMON dev team's rule (2026-09-30): the stream reveals nothing the phone's own card does not. */
    @Test
    fun `the opponent's abilities and types go out only where the phone's card shows them`() {
        val run = StreamSnapshot.Run("Emerald", "GBA", 5, true)
        val saved = Triple(TrackerOptions.openBookPlayMode, TrackerOptions.revealInfoIfRandomized, TrackerOptions.showDataForVanillaGame)
        try {
            TrackerOptions.openBookPlayMode = false; TrackerOptions.revealInfoIfRandomized = true; TrackerOptions.showDataForVanillaGame = true
            assertEquals("Intimidate / Moxie", enemyOf(StreamSnapshot.build(run, battle(flags()), null, StreamSnapshot.Notes()))["abilityGuess"],
                "abilities not randomized: the card shows both, so may the stream")
            assertNull(enemyOf(StreamSnapshot.build(run, battle(flags(abilities = true)), null, StreamSnapshot.Notes()))["abilityGuess"],
                "abilities randomized: the seed's own data stays on the phone")
            TrackerOptions.openBookPlayMode = true
            assertEquals("Intimidate / Moxie", enemyOf(StreamSnapshot.build(run, battle(flags(abilities = true)), null, StreamSnapshot.Notes()))["abilityGuess"],
                "Open Book shows everything, on the card and so on the stream")
            TrackerOptions.openBookPlayMode = false

            assertEquals(listOf("Ground", "Dark"), enemyOf(StreamSnapshot.build(run, battle(flags(types = true)), null, StreamSnapshot.Notes()))["types"],
                "Reveal info if randomized on (its default): the types show")
            TrackerOptions.revealInfoIfRandomized = false
            assertEquals(listOf("?"), enemyOf(StreamSnapshot.build(run, battle(flags(types = true)), null, StreamSnapshot.Notes()))["types"],
                "off, with the types randomized: the one unknown icon, as on the card")
            assertEquals(listOf("Ground", "Dark"), enemyOf(StreamSnapshot.build(run, battle(flags()), null, StreamSnapshot.Notes()))["types"],
                "off, with the types as the game made them: shown")
        } finally {
            TrackerOptions.openBookPlayMode = saved.first; TrackerOptions.revealInfoIfRandomized = saved.second; TrackerOptions.showDataForVanillaGame = saved.third
        }
    }

    @Test
    fun `hide stats until summary shown hides the same things on the stream as on the phone`() {
        val saved = TrackerOptions.hideStatsUntilSummary
        val attempt = 424242
        try {
            TrackerOptions.hideStatsUntilSummary = true
            val run = StreamSnapshot.Run("Emerald", "GBA", attempt, true)
            val snap = StreamSnapshot.build(run, battle(flags(abilities = true)), null, StreamSnapshot.Notes())
            val p = leadOf(snap)
            assertEquals("Mothim", p["name"]); assertEquals(23, p["level"]); assertEquals(listOf("Bug", "Flying"), p["types"])
            for (k in listOf("hp", "maxHp", "ability", "item", "stats")) assertNull(p[k], "$k waits for the summary")
            assertEquals(emptyList<Any>(), p["moves"]); assertEquals(emptyMap<String, Int>(), p["stages"])
            assertFalse(Json.write(snap).contains("Swarm"), "the ability is nowhere in the JSON")

            com.ironmonone.app.SummaryChecks.mark(attempt)
            val after = leadOf(StreamSnapshot.build(run, battle(flags(abilities = true)), null, StreamSnapshot.Notes()))
            assertEquals(74, after["maxHp"]); assertEquals("Swarm", after["ability"]); assertEquals(55, (after["stats"] as Map<*, *>)["atk"])

            // A Game Boy game has no summary screen to watch: never hidden there (SummaryChecks.hidesStats).
            val gb = leadOf(StreamSnapshot.build(StreamSnapshot.Run("Crystal", "GBC", attempt + 1, true, generation = 2), battle(flags(abilities = true)), null, StreamSnapshot.Notes()))
            assertEquals(74, gb["maxHp"])
        } finally {
            TrackerOptions.hideStatsUntilSummary = saved
            com.ironmonone.app.SummaryChecks.forgetAll()
        }
    }

    @Test
    fun `the run-over view follows the latched Kaizo IronMON end, never the tracker's live read`() {
        // A Nuzlocke lead fainting reads as a loss on the tracker; the run goes on, so the stream does too.
        val live = StreamSnapshot.build(StreamSnapshot.Run("Emerald", "GBA", 5, true, ended = null), battle(flags(), GameOver.LOST), null, StreamSnapshot.Notes())
        assertNull(live["outcome"])
        val over = StreamSnapshot.build(StreamSnapshot.Run("Emerald", "GBA", 5, true, ended = RunOutcome.LOST), battle(flags()), null, StreamSnapshot.Notes())
        assertEquals("LOST", over["outcome"], "latched, it stays over after the whiteout heal clears the live read")
    }

    @Test
    fun `no state is an empty, valid snapshot`() {
        val snap = StreamSnapshot.build(StreamSnapshot.Run("Hack", "GBA", 1, false), null, null, StreamSnapshot.Notes())
        assertEquals(false, snap["tracked"]); assertEquals(emptyList<Any>(), snap["party"]); assertNull(snap["enemy"])
        assertTrue(Json.write(snap).startsWith("{\"app\":\"KaizoCore\""))
    }
}
