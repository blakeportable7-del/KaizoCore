package com.ironmonone.app

import com.ironmonone.tracker.GbcTracker
import com.ironmonone.tracker.Gen1Tracker
import com.ironmonone.tracker.MemoryReader
import com.ironmonone.tracker.NuzlockeAdapters
import com.ironmonone.tracker.nds.NdsGameMap
import com.ironmonone.tracker.nds.NdsMemoryReader
import com.ironmonone.tracker.nds.NdsNuzlocke
import com.ironmonone.tracker.nds.NdsTracker
import com.ironmonone.tracker.nuzlocke.NuzlockeEngine
import com.ironmonone.tracker.nuzlocke.NuzlockeLedger
import com.ironmonone.tracker.nuzlocke.NuzlockePreset
import com.ironmonone.tracker.nuzlocke.NuzlockeRules
import com.ironmonone.tracker.nuzlocke.NuzlockeSystem
import com.ironmonone.tracker.nuzlocke.Outcome
import com.ironmonone.tracker.nuzlocke.RunMeta
import com.ironmonone.tracker.nuzlocke.Snapshot
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The staged Nuzlocke runs of the demo (gb-nuz and nds-nuz, 2026-09-30), driven through the real adapters and the real
 * engine, stage by stage, the way the Play screen drives them. They exist so a phone with no save that far in can show
 * the ledger at work, and a demo that does not do what its stages say would waste the one look at the device: this proves
 * each stage lands where the script says, and that the run they make is the one the comments describe.
 *
 * The Game Boy scripts read the loaded game's own ROM (names, types, place keys, caps), so they run on the real dumps and
 * are skipped, with a line saying so, for one that is not on this machine.
 */
class NuzlockeDemoTest {

    private val roms: File = System.getenv("IRONMON_ROMS")?.let(::File)?.takeIf { it.isDirectory } ?: File("C:/Users/bepor/IronMonOne/.vendor/roms")

    private fun rom(name: String): ByteArray? {
        val f = File(roms, name)
        if (!f.isFile) { println("SKIP: $name is not on this machine"); return null }
        return f.readBytes()
    }

    /** A game whose memory reads as zeros: the demo builds its own state, the tracker only lends its tables. */
    private class Blank(private val base: Long) : MemoryReader {
        override fun read(address: Long, length: Int): ByteArray = if (address >= base && address - base + length <= 0x10000) ByteArray(length) else ByteArray(0)
    }

    private class Run(system: NuzlockeSystem) {
        val ledger = NuzlockeLedger(RunMeta("nz-demo", "demo", "Demo", NuzlockeRules.forPreset(NuzlockePreset.STANDARD), 1_000L).also { it.system = system })
        val engine = NuzlockeEngine(ledger)
        var at = 10_000L
        var last: Snapshot? = null
        /** A stage stays on screen for many polls; three make the point. */
        fun stage(snapshot: () -> Snapshot?) = repeat(3) { last = assertNotNull(snapshot()); engine.update(last!!, at); at += 700 }
    }

    /** The scripted run of the Game Boy demo on the loaded game: the same seven stages for Generation 1 and 2. */
    private fun checkGameBoy(system: NuzlockeSystem, area1: String, area2: String, next: (Int) -> Snapshot?) {
        val r = Run(system)
        r.stage { next(0) }
        assertTrue(r.ledger.meta.started, "the run begins with the starter and the balls")
        val starter = r.ledger.roster.values.single()
        assertTrue(starter.inParty)
        assertEquals("EMBER", starter.nickname)
        assertNull(r.ledger.areas[area1]?.encounter, "nothing met yet")

        r.stage { next(1) }
        val enc = assertNotNull(r.ledger.areas.getValue(area1).encounter)
        assertEquals(Outcome.IN_PROGRESS, enc.outcome)

        r.stage { next(2) }
        assertEquals(2, r.ledger.roster.size, "the ball caught it and it is in the party")

        r.stage { next(3) }
        assertEquals(Outcome.CAUGHT, r.ledger.areas.getValue(area1).encounter!!.outcome)
        assertEquals(enc.pid, r.ledger.areas.getValue(area1).encounter!!.monId, "the enemy's id is the id of the catch")
        val friend = r.ledger.roster.getValue(enc.pid)
        assertEquals("PIP", friend.nickname); assertTrue(friend.alive && friend.inParty)

        r.stage { next(4) }
        assertNull(r.ledger.areas[area2]?.encounter, "the second route has nothing met yet")
        assertEquals(area2, r.last!!.area.name)

        r.stage { next(5) }
        assertEquals(Outcome.IN_PROGRESS, r.ledger.areas.getValue(area2).encounter!!.outcome)
        assertFalse(starter.alive, "the starter fainted against the wild Pokemon")
        assertEquals(1, r.ledger.graveyard.size)

        r.stage { next(6) }
        assertEquals(Outcome.FAINTED, r.ledger.areas.getValue(area2).encounter!!.outcome, "the caught Pokemon won the battle")
        assertTrue(friend.alive)
        assertTrue(r.ledger.warnings.isEmpty(), "nothing the demo does breaks a rule: " + r.ledger.warnings.map { it.text })
    }

    @Test
    fun `gb-nuz on Red is a run the ledger follows from the starter to a faint and a win`() {
        val t = Gen1Tracker(Blank(Gen1Tracker.RAM), rom("red-u.gbc") ?: return)
        checkGameBoy(NuzlockeSystem.GEN1, "Route 1", "Route 2") { NuzlockeAdapters.snapshot(Demo.gb1Nuz(t, it)) }
        // The place keys and the caps are the game's own, read through the tracker.
        val caps = assertNotNull(Demo.gb1Nuz(t, 0).nuz?.gb?.caps)
        assertTrue(caps.fromRom, "the boss levels come out of the loaded ROM")
    }

    @Test
    fun `gb-nuz on Yellow keeps Yellow's rows and caps`() {
        val t = Gen1Tracker(Blank(Gen1Tracker.RAM), rom("yellow-u.gbc") ?: return)
        assertEquals("y", Demo.gb1Nuz(t, 0).nuz!!.gb!!.game)
        assertEquals(12, Demo.gb1Nuz(t, 0).nuz!!.gb!!.caps!!.byKey("gym1")!!.cap, "Yellow's Brock")
    }

    @Test
    fun `gb-nuz on Crystal is a run the ledger follows from the starter to a faint and a win`() {
        val t = GbcTracker(Blank(GbcTracker.RAM), rom("crystal-u.gbc") ?: return)
        checkGameBoy(NuzlockeSystem.GEN2, "Route 29", "Route 30") { NuzlockeAdapters.snapshot(Demo.gb2Nuz(t, it)) }
        assertEquals("c", Demo.gb2Nuz(t, 0).nuz!!.gb!!.game)
        assertTrue(assertNotNull(Demo.gb2Nuz(t, 0).nuz!!.gb!!.caps).fromRom)
    }

    @Test
    fun `gb-nuz gives each Game Boy Pokemon a gender from the ROM's ratio, the way the game does`() {
        val t = GbcTracker(Blank(GbcTracker.RAM), rom("crystal-u.gbc") ?: return)
        val s = assertNotNull(NuzlockeAdapters.snapshot(Demo.gb2Nuz(t, 3)))
        // Cyndaquil with attack 7 and speed 7 (b = 119) against one female in eight (31), and Sentret with 3 and 10 (b = 58) against even odds (127).
        assertEquals(listOf(com.ironmonone.tracker.nuzlocke.Gender.MALE, com.ironmonone.tracker.nuzlocke.Gender.FEMALE), s.party.map { it.gender })
    }

    // ---------------------------------------------------------------- DS

    private fun checkDs(map: NdsGameMap, gameName: String, system: NuzlockeSystem) {
        val t = NdsTracker(NdsMemoryReader { _, _ -> ByteArray(0) }, null, map)
        val r = Run(system)
        fun snap(stage: Int) = NdsNuzlocke.snapshot(Demo.ndsNuz(t, stage))
        assertEquals(gameName, Demo.ndsNuz(t, 0).gameName, "the running game's own name")

        r.stage { snap(0) }
        assertTrue(r.ledger.meta.started, "no ball count on a DS game: the rules begin with the first Pokemon")
        val starter = r.ledger.roster.values.single()
        assertNull(r.ledger.areas["Route 201"]?.encounter)

        r.stage { snap(1) }
        val enc = assertNotNull(r.ledger.areas.getValue("Route 201").encounter)
        assertEquals(Outcome.IN_PROGRESS, enc.outcome)

        r.stage { snap(2) }
        assertEquals(2, r.ledger.roster.size, "it joined the party during the battle")

        r.stage { snap(3) }
        assertEquals(Outcome.CAUGHT, r.ledger.areas.getValue("Route 201").encounter!!.outcome)
        val friend = r.ledger.roster.getValue(enc.pid)
        assertTrue(friend.alive && friend.inParty)

        r.stage { snap(4) }
        assertNull(r.ledger.areas["Route 202"]?.encounter)

        r.stage { snap(5) }
        assertEquals(Outcome.IN_PROGRESS, r.ledger.areas.getValue("Route 202").encounter!!.outcome)
        assertFalse(starter.alive)

        r.stage { snap(6) }
        r.stage { snap(7) }
        assertEquals(Outcome.FAINTED, r.ledger.areas.getValue("Route 202").encounter!!.outcome, "the enemy's HP was 0 when the battle ended")
        assertEquals(1, r.ledger.graveyard.size)
        assertTrue(r.ledger.warnings.isEmpty(), "nothing the demo does breaks a rule: " + r.ledger.warnings.map { it.text })
    }

    @Test
    fun `nds-nuz on a Generation 4 game is a run the ledger follows from the starter to a faint and a win`() =
        checkDs(NdsGameMap.PLATINUM, "Pokemon Platinum", NuzlockeSystem.GEN4)

    @Test
    fun `nds-nuz on a Generation 5 game is the same run`() = checkDs(NdsGameMap.B2W2, "Pokemon Black 2", NuzlockeSystem.GEN5)

    @Test
    fun `the demo modes are routed by the same prefixes the Play screen already tests for`() {
        val play = File("src/main/kotlin/com/ironmonone/app/PlayScreen.kt").readText().replace("\r\n", "\n")
        assertTrue("""Demo.mode?.takeIf { it.startsWith("gb-") }""" in play)
        assertTrue("""Demo.mode?.takeIf { it.startsWith("nds") }""" in play)
        val demo = File("src/main/kotlin/com/ironmonone/app/Demo.kt").readText().replace("\r\n", "\n")
        assertTrue("""if (mode == "gb-nuz") return gb1Nuz(t)""" in demo)
        assertTrue("""if (mode == "gb-nuz") return gb2Nuz(t)""" in demo)
        assertTrue("""if (mode == "nds-nuz") return ndsNuz(t)""" in demo)
        assertTrue("gb-nuz".startsWith("gb-") && "nds-nuz".startsWith("nds"))
    }
}
