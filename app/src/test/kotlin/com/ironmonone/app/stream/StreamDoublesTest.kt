package com.ironmonone.app.stream

import com.ironmonone.app.DsViewState
import com.ironmonone.app.GbaViewState
import com.ironmonone.tracker.EnemyInfo
import com.ironmonone.tracker.HealTotals
import com.ironmonone.tracker.PokemonDecoder
import com.ironmonone.tracker.TrackedMon
import com.ironmonone.tracker.TrackerState
import com.ironmonone.tracker.nds.Gen4
import com.ironmonone.tracker.nds.NdsTrackedMon
import com.ironmonone.tracker.nds.NdsTrackerState
import org.junit.Assume
import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The stream page in a double or triple battle shows the Pokemon the phone's swap shows, yours and the opponent's, and
 * where each stands, GBA and DS (rc34; Blake: "fix 1 ... don't stop"). It always drew the party's first and the
 * opponent's left-hand one. A single battle's page is as it was.
 */
class StreamDoublesTest {
    private fun mon(species: Int, level: Int) = PokemonDecoder.Mon(
        pid = species.toLong(), level = level, nickname = "", species = species, heldItem = 0, friendship = 70,
        moves = listOf(33, 0, 0, 0), pp = listOf(35, 0, 0, 0), ivs = List(6) { 0 }, evs = List(6) { 0 }, ppUps = List(4) { 0 },
        abilitySlot = 0, nature = 0, shiny = false, status = 0, curHp = 50, maxHp = 50, atk = 5, def = 5, spe = 5, spAtk = 5, spDef = 5,
    )

    private fun own(species: Int, name: String, level: Int) = TrackedMon(mon(species, level), name, emptyList(), null)
    private fun foe(species: Int, name: String, level: Int) = EnemyInfo(species, name, level, 30, 30, 0, 0, null, emptyList())

    private val pika = own(25, "Pikachu", 21)
    private val bulba = own(1, "Bulbasaur", 22)
    private val char = own(4, "Charmander", 23)
    private val pidgey = foe(16, "Pidgey", 24)
    private val rattata = foe(19, "Rattata", 25)

    // Not randomized, so "Hide stats until summary shown" keeps out of the way.
    private fun doubles() = TrackerState(
        3, listOf(pika, bulba, char), inBattle = true, isWildBattle = false, enemy = pidgey, enemyRight = rattata,
        enemyOnField = listOf(0, 1), doubles = true, ownOnField = 0, ownRightOnField = 2, gameDataRandomized = false,
        healPercent = 40, healCount = 1, healHp = 20, ownRightHeals = HealTotals(25, 20, 1),
    )

    private fun singles() = TrackerState(
        3, listOf(pika, bulba, char), inBattle = true, isWildBattle = false, enemy = pidgey, enemyOnField = listOf(0),
        gameDataRandomized = false, healPercent = 40, healCount = 1,
    )

    private val run = StreamSnapshot.Run("Emerald", "GBA", 3, true)

    /** The phone's view after [swaps] taps of the swap in [s]'s battle. */
    private fun viewAfter(s: TrackerState, swaps: Int) = GbaViewState().also { v ->
        v.onRead(s.copy(inBattle = false), autoSwap = false); v.onRead(s, autoSwap = false)
        repeat(swaps) { v.swap(s, stacked = false) }
    }

    @Suppress("UNCHECKED_CAST")
    private fun card(snap: Map<String, Any?>, key: String) = snap[key] as Map<String, Any?>

    @Test
    fun `a double battle's stream shows the Pokemon the phone shows, and where each stands`() {
        val s = doubles()
        // Two taps: your right-hand Charmander, which faces the opponent's left.
        val right = StreamSnapshot.build(run, s, null, StreamSnapshot.Notes(), gbaView = viewAfter(s, 2))
        assertEquals("Charmander", card(right, "own")["name"])
        assertEquals("Pidgey", card(right, "enemy")["name"])
        assertEquals(mapOf("own" to "MINE ON THE RIGHT", "foe" to "FOE ON THE RIGHT"), right["sides"])
        assertEquals(mapOf("percent" to 25, "count" to 1), right["heals"], "the heals are a share of Charmander's HP")
        // A third: the opponent's right-hand Rattata, your left facing it.
        val third = StreamSnapshot.build(run, s, null, StreamSnapshot.Notes(), gbaView = viewAfter(s, 3))
        assertEquals("Pikachu", card(third, "own")["name"])
        assertEquals("Rattata", card(third, "enemy")["name"])
        assertEquals(mapOf("own" to "MINE ON THE LEFT", "foe" to "FOE ON THE LEFT"), third["sides"])
        assertEquals(mapOf("percent" to 40, "count" to 1), third["heals"])
        // The party still goes out whole, in its order.
        @Suppress("UNCHECKED_CAST")
        assertEquals(listOf("Pikachu", "Bulbasaur", "Charmander"), (third["party"] as List<Map<String, Any?>>).map { it["name"] })
    }

    @Test
    fun `a single battle's stream is as it was`() {
        val s = singles()
        val plain = StreamSnapshot.build(run, s, null, StreamSnapshot.Notes())
        val viewed = StreamSnapshot.build(run, s, null, StreamSnapshot.Notes(), gbaView = viewAfter(s, 1))
        assertEquals(plain, viewed, "the phone's view changes nothing in a single battle")
        assertNull(viewed["own"])
        assertNull(viewed["sides"])
        assertEquals("Pidgey", card(viewed, "enemy")["name"])
    }

    private fun tm(pid: Long, species: Int, name: String) = NdsTrackedMon(
        mon = Gen4.decodeParty(Gen4.encodeParty(pid, species, 20, 40, 40, listOf(33, 0, 0, 0)))!!,
        speciesName = name, info = null, abilityName = "-", itemName = "-", moves = emptyList(),
    )

    @Test
    fun `a DS double battle's stream shows the slot the phone shows`() {
        val turtwig = tm(0x10, 387, "Turtwig"); val chimchar = tm(0x11, 390, "Chimchar")
        val starly = tm(0x90, 396, "Starly"); val bidoof = tm(0x91, 399, "Bidoof")
        val s = NdsTrackerState(2, listOf(turtwig, chimchar), located = true, inBattle = true, enemy = starly, healsPid = 0x10,
            playerBattlers = listOf(turtwig, chimchar), enemyBattlers = listOf(starly, bidoof))
        val ds = StreamSnapshot.Run("Platinum", "NDS", 3, true)
        val v = DsViewState().also { it.onRead(s); it.swap(s, allowed = true) }
        val mine = StreamSnapshot.build(ds, null, s, StreamSnapshot.Notes(), dsView = v)
        assertEquals("Chimchar", card(mine, "own")["name"])
        assertEquals("Starly", card(mine, "enemy")["name"])
        assertEquals(mapOf("own" to "MINE ON THE RIGHT", "foe" to "FOE ON THE RIGHT"), mine["sides"])
        v.swap(s, allowed = true); v.swap(s, allowed = true)
        val theirs = StreamSnapshot.build(ds, null, s, StreamSnapshot.Notes(), dsView = v)
        assertEquals("Turtwig", card(theirs, "own")["name"])
        assertEquals("Bidoof", card(theirs, "enemy")["name"])
        assertEquals(mapOf("own" to "MINE ON THE LEFT", "foe" to "FOE ON THE LEFT"), theirs["sides"])
        // A single DS battle is as it was.
        val one = s.copy(partyCount = 1, party = listOf(turtwig), playerBattlers = listOf(turtwig), enemyBattlers = listOf(starly))
        assertEquals(StreamSnapshot.build(ds, null, one, StreamSnapshot.Notes()),
            StreamSnapshot.build(ds, null, one, StreamSnapshot.Notes(), dsView = DsViewState().also { it.onRead(one) }))
    }

    private val tracker = File("src/main/assets/stream/tracker.html").readText()

    @Test
    fun `the page draws the Pokemon the snapshot names, and the party's first in a single battle`() {
        Assume.assumeTrue("node is not on the PATH, so the page's own script is not run", PageRunner.available)
        val s = doubles()
        val seen = PageRunner.run("tracker", tracker, "?k=abcd", listOf(
            mapOf("state" to StreamSnapshot.build(run, s, null, StreamSnapshot.Notes(), gbaView = viewAfter(s, 2))),
            mapOf("state" to StreamSnapshot.build(run, singles(), null, StreamSnapshot.Notes())),
        )).steps
        assertContains(seen[0].str("own"), "Charmander")
        assertContains(seen[0].str("own"), "MINE ON THE RIGHT")
        assertContains(seen[0].str("enemy"), "Pidgey")
        assertContains(seen[0].str("enemy"), "FOE ON THE RIGHT")
        assertContains(seen[0].str("enemy"), "vs Charmander")
        assertContains(seen[1].str("own"), "Pikachu")
        assertFalse("ON THE" in seen[1].str("own") || "ON THE" in seen[1].str("enemy"), "no words in a single battle")
        assertContains(seen[1].str("enemy"), "vs Pikachu")
    }

    @Test
    fun `Play hands the stream the phone's views, and a swap goes out at once`() {
        val feed = File("src/main/kotlin/com/ironmonone/app/stream/StreamFeed.kt").readText()
        assertTrue("StreamSnapshot.build(shown, gba, nds, notes, ref, com.ironmonone.app.gbaView, com.ironmonone.app.dsView)" in feed)
        assertTrue("LaunchedEffect(on || chat, gba, nds, marksVersion, session.id, com.ironmonone.app.gbaView.view, com.ironmonone.app.dsView.key) {" in feed)
    }
}
