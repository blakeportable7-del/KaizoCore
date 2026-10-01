package com.ironmonone.tracker.nuzlocke

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The Hardcore clauses: the level cap checked when a boss battle starts, items used in battle, the Set battle
 * style, and the Champion (2026-09-29).
 */
class NuzlockeBossTest {

    private val frlg = LevelCapTable.standard("frlg")

    private fun hardcore() = Sim(rules(NuzlockePreset.HARDCORE)).also { it.caps = frlg }

    private fun capWarnings(s: Sim) = s.warnings(WarnKind.CAP)

    @Test
    fun `a Pokemon over the cap when a boss battle starts is a warning, one that is under is not`() {
        val s = hardcore().starter(mon(1, Sp.SQUIRTLE, "SQUIRTLE", 16, nickname = "Shell"))
        s.party = s.party + mon(2, Sp.PIDGEY, "PIDGEY", 12, nickname = "Peck")
        s.idle()
        s.trainerBattle(brock(14))
        val w = capWarnings(s).single()
        assertTrue("Shell" in w.text && "level 16" in w.text && "cap of 14" in w.text, w.text)
        assertTrue(s.ledger.events.any { it.kind == "boss" && "1 over" in it.text })
    }

    @Test
    fun `crossing the cap inside the battle is allowed`() {
        val s = hardcore().starter(mon(1, Sp.SQUIRTLE, "SQUIRTLE", 13, nickname = "Shell"))
        s.trainerBattle(brock(14))
        assertTrue(capWarnings(s).isEmpty())
        // Two levels gained mid-battle, one poll at a time.
        s.party = listOf(mon(1, Sp.SQUIRTLE, "SQUIRTLE", 14, nickname = "Shell"))
        s.turn = 3; s.poll()
        s.party = listOf(mon(1, Sp.SQUIRTLE, "SQUIRTLE", 15, nickname = "Shell"))
        s.turn = 5; s.poll()
        assertTrue(capWarnings(s).isEmpty(), capWarnings(s).joinToString { it.text })
    }

    @Test
    fun `crossing the cap inside the battle is allowed on a console with no turn counter too`() {
        // The Game Boy and DS adapters have no turn to give (null), so nothing but the once-per-battle check
        // keeps a level gained mid-battle from being warned about.
        val s = hardcore().starter(mon(1, Sp.SQUIRTLE, "SQUIRTLE", 13, nickname = "Shell"))
        s.trainerBattle(brock(14), turnNow = null)
        assertTrue(capWarnings(s).isEmpty())
        s.party = listOf(mon(1, Sp.SQUIRTLE, "SQUIRTLE", 15, nickname = "Shell"))
        s.turn = null; s.poll()
        s.party = listOf(mon(1, Sp.SQUIRTLE, "SQUIRTLE", 16, nickname = "Shell"))
        s.turn = null; s.poll()
        assertTrue(capWarnings(s).isEmpty(), capWarnings(s).joinToString { it.text })
        assertEquals(1, s.ledger.events.count { it.kind == "boss" }, "the check ran once, at the start")
    }

    @Test
    fun `a battle joined after it began is not checked`() {
        val s = hardcore().starter(mon(1, Sp.SQUIRTLE, "SQUIRTLE", 30, nickname = "Shell"))
        s.trainerBattle(brock(14), turnNow = 4)
        assertTrue(capWarnings(s).isEmpty())
        assertTrue(s.ledger.events.any { it.kind == "boss" && "not checked" in it.text })
    }

    @Test
    fun `without level caps in the rules nothing is checked`() {
        val s = Sim(rules(NuzlockePreset.STANDARD)).also { it.caps = frlg }.starter(mon(1, Sp.SQUIRTLE, "SQUIRTLE", 40))
        s.trainerBattle(brock(14))
        assertTrue(capWarnings(s).isEmpty())
    }

    @Test
    fun `the cap comes from the boss's real team when the game gave it`() {
        // A randomized, level-scaled game: Brock's ace is level 22, not 14.
        val s = hardcore().starter(mon(1, Sp.SQUIRTLE, "SQUIRTLE", 20, nickname = "Shell"))
        s.trainerBattle(brock(22))
        assertTrue(capWarnings(s).isEmpty(), "level 20 is under the real cap of 22")
        val t = hardcore().starter(mon(1, Sp.SQUIRTLE, "SQUIRTLE", 23, nickname = "Shell"))
        t.trainerBattle(brock(22))
        assertEquals(1, capWarnings(t).size)
    }

    @Test
    fun `the Elite Four is entered with the last member's level and fought without a cap`() {
        val s = hardcore().starter(mon(1, Sp.SQUIRTLE, "SQUIRTLE", 59, nickname = "Shell"))
        // FireRed and LeafGreen: Lance's Dragonite is level 60, so 59 is under the League cap.
        s.trainerBattle(NzOpponent(410, "ELITE FOUR LORELEI", "Elite4", "e4-1", 54))
        assertTrue(capWarnings(s).isEmpty())
        s.finish(BattleEnd.WON)
        // Inside the League the cap may be passed: no check at the next member.
        s.party = listOf(mon(1, Sp.SQUIRTLE, "SQUIRTLE", 65, nickname = "Shell"))
        s.trainerBattle(NzOpponent(411, "ELITE FOUR BRUNO", "Elite4", "e4-2", 56))
        assertTrue(capWarnings(s).isEmpty())
        // Walking in over the League cap is a warning.
        val t = hardcore().starter(mon(1, Sp.SQUIRTLE, "SQUIRTLE", 61, nickname = "Shell"))
        t.trainerBattle(NzOpponent(410, "ELITE FOUR LORELEI", "Elite4", "e4-1", 54))
        assertEquals("cap of 60", capWarnings(t).single().text.substringAfter("over the ").substringBefore(" for"))
    }

    @Test
    fun `rivals and team leaders only count as bosses when the rules say so`() {
        val rival = NzOpponent(326, "RIVAL BLUE", "Rival", null, 18)
        val off = hardcore().starter(mon(1, Sp.SQUIRTLE, "SQUIRTLE", 20, nickname = "Shell"))
        off.trainerBattle(rival)
        assertTrue(capWarnings(off).isEmpty())
        val on = Sim(rules(NuzlockePreset.HARDCORE) { it.copy(capExtraBosses = true) }).also { it.caps = frlg }
            .starter(mon(1, Sp.SQUIRTLE, "SQUIRTLE", 20, nickname = "Shell"))
        on.trainerBattle(rival)
        assertEquals(1, capWarnings(on).size)
    }

    @Test
    fun `an ordinary trainer is never checked`() {
        val s = hardcore().starter(mon(1, Sp.SQUIRTLE, "SQUIRTLE", 50, nickname = "Shell"))
        s.trainerBattle(NzOpponent(300, "YOUNGSTER JOEY", "Other", null, 9))
        assertTrue(capWarnings(s).isEmpty())
    }

    @Test
    fun `an item that leaves the bag in a battle is a warning, and a Poke Ball is not an item`() {
        val s = hardcore().starter()
        s.bag = mapOf(13 to NzItem("POTION", 3), 14 to NzItem("ANTIDOTE", 1))
        s.wildBattle(foe(100))
        // A Potion is used, and Poke Balls are not in the bag map at all.
        s.bag = mapOf(13 to NzItem("POTION", 2), 14 to NzItem("ANTIDOTE", 1))
        s.poll()
        s.finish(BattleEnd.WON)
        val w = s.warnings(WarnKind.ITEM).single()
        assertEquals("Used Potion in battle against Rattata.", w.text)
    }

    @Test
    fun `a battle that used no item, or a rule that is off, warns of nothing`() {
        val quiet = hardcore().starter()
        quiet.bag = mapOf(13 to NzItem("POTION", 3))
        quiet.wildBattle(foe(100)); quiet.finish(BattleEnd.WON)
        assertTrue(quiet.warnings(WarnKind.ITEM).isEmpty())

        val off = Sim(rules(NuzlockePreset.STANDARD)).starter()
        off.bag = mapOf(13 to NzItem("POTION", 3))
        off.wildBattle(foe(100))
        off.bag = mapOf(13 to NzItem("POTION", 1))
        off.poll(); off.finish(BattleEnd.WON)
        assertTrue(off.warnings(WarnKind.ITEM).isEmpty())
    }

    @Test
    fun `a bag that changed wholesale is a misread and not a list of items used`() {
        val s = hardcore().starter()
        s.bag = (1..10).associate { 100 + it to NzItem("ITEM$it", 5) }
        s.wildBattle(foe(100))
        s.bag = emptyMap()
        s.poll(); s.finish(BattleEnd.WON)
        assertTrue(s.warnings(WarnKind.ITEM).isEmpty())
    }

    @Test
    fun `Set battle style is a reminder that shows once per stretch of Shift`() {
        val s = hardcore().starter()
        s.style = false
        s.poll(); s.poll()
        assertEquals(1, s.warnings(WarnKind.STYLE).size)
        assertTrue(s.ledger.meta.styleShift)
        s.style = true
        s.poll()
        assertFalse(s.ledger.meta.styleShift)
        s.style = false
        s.poll()
        assertEquals(2, s.warnings(WarnKind.STYLE).size)
    }

    @Test
    fun `a game that cannot say its battle style gets no reminder`() {
        val s = hardcore().starter()
        s.style = null
        repeat(3) { s.poll() }
        assertTrue(s.warnings(WarnKind.STYLE).isEmpty())
    }

    @Test
    fun `beating the Champion completes the run and saves the survivors`() {
        val s = Sim(rules(NuzlockePreset.GENLOCKE)).also { it.caps = frlg }.starter(mon(1, Sp.SQUIRTLE, "SQUIRTLE", 60, nickname = "Shell"))
        s.catchIt(foe(100), mon(100, Sp.PIDGEY, "PIDGEY", 50, nickname = "Peck"))
        s.party = listOf(s.party[0], s.party[1].copy(hp = 0))
        s.poll()
        s.party = listOf(s.party[0])
        s.idle()
        s.trainerBattle(NzOpponent(438, "RIVAL BLUE", "Elite4", "champion", 63))
        s.finish(BattleEnd.WON)
        assertEquals(RunStatus.COMPLETE, s.ledger.meta.status)
        assertEquals("Champion beaten", s.ledger.meta.endReason)
        assertEquals(listOf("Squirtle"), s.ledger.meta.heirsOut.map { it.speciesName })
        assertEquals(60, s.ledger.meta.heirsOut.single().level)
        // Nothing more is recorded after it.
        val rev = s.ledger.revision
        s.wildEncounter(foe(300), BattleEnd.WON)
        assertEquals(rev, s.ledger.revision)
    }

    @Test
    fun `a Champion already beaten before the ledger began does not complete it`() {
        val s = Sim().also { it.caps = frlg; it.beaten = setOf("champion") }.starter()
        repeat(3) { s.poll() }
        assertEquals(RunStatus.ACTIVE, s.ledger.meta.status)
        assertNull(s.ledger.meta.heirsOut.firstOrNull())
    }

    @Test
    fun `losing to the Champion does not complete the run`() {
        val s = Sim().also { it.caps = frlg }.starter()
        s.trainerBattle(NzOpponent(438, "RIVAL BLUE", "Elite4", "champion", 63))
        s.finish(BattleEnd.LOST)
        assertEquals(RunStatus.ACTIVE, s.ledger.meta.status)
        assertNotNull(s.ledger.roster[1L])
    }
}
