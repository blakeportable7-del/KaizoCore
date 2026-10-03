package com.ironmonone.app

import com.ironmonone.tracker.nds.Gen4
import com.ironmonone.tracker.nds.NdsExperience
import com.ironmonone.tracker.nds.NdsSpeciesInfo
import com.ironmonone.tracker.nds.NdsTrackedMon
import com.ironmonone.tracker.nds.NdsTrackerState
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The DS heals box on the panel (MainScreen.setUpMiscInfo) and the run's Pokecenter count (Tracker.lua). */
class NdsHealsViewTest {
    private fun tm(pid: Long, maxHp: Int) = NdsTrackedMon(
        mon = Gen4.decodeParty(Gen4.encodeParty(pid, 25, 20, maxHp, maxHp, listOf(84, 0, 0, 0)))!!,
        speciesName = "Pikachu", info = null, abilityName = "-", itemName = "-", moves = emptyList(),
    )
    private val lead = tm(0x10, 30)
    private val second = tm(0x20, 60)
    private fun state(healsPid: Long) = NdsTrackerState(2, listOf(lead, second), located = true,
        healingItems = mapOf(155 to 2), statusItems = mapOf(18 to 1), healsPid = healsPid)

    @Test
    fun `the box is on the card the heals are measured against, with the reference's two lines`() {
        val s = state(healsPid = 0x20)
        assertNull(ndsHealsView(s, lead, false, false, 10, false, false))
        val v = assertNotNull(ndsHealsView(s, second, false, false, 10, false, false))
        assertEquals("Heals: 33% (2)", v.heals, "two Orans on 60 HP")
        assertEquals("Status items: 1", v.status)
        assertEquals(listOf("2 Oran Berries (10 HP)"), v.healingList)
        assertEquals("Heals: 20 HP (2)", ndsHealsView(s, second, true, false, 10, false, false)?.heals)
    }

    @Test
    fun `the Pokecenter counter needs its setting, and gives way to the tourney and to ACC and EVA`() {
        val s = state(healsPid = 0x10)
        assertNull(ndsHealsView(s, lead, false, pokecenterOn = false, pokecenterCount = 10, tourneyOn = false, accEvaOn = false)?.pokecenter)
        assertEquals(7, ndsHealsView(s, lead, false, pokecenterOn = true, pokecenterCount = 7, tourneyOn = false, accEvaOn = false)?.pokecenter)
        assertNull(ndsHealsView(s, lead, false, pokecenterOn = true, pokecenterCount = 7, tourneyOn = true, accEvaOn = false)?.pokecenter)
        // Out of battle ACC and EVA do not show, so the counter does.
        assertEquals(7, ndsHealsView(s, lead, false, pokecenterOn = true, pokecenterCount = 7, tourneyOn = false, accEvaOn = true)?.pokecenter)
        val battle = s.copy(inBattle = true)
        assertNull(ndsHealsView(battle, lead, false, pokecenterOn = true, pokecenterCount = 7, tourneyOn = false, accEvaOn = true)?.pokecenter)
    }

    @Test
    fun `ACC and EVA show in battle with the setting on, your side's stages`() {
        val staged = lead.copy(statStages = mapOf("ATK" to 6, "ACC" to 5, "EVA" to 8))
        val s = NdsTrackerState(2, listOf(staged, second), located = true, inBattle = true, healsPid = 0x10)
        assertEquals(5 to 8, ndsHealsView(s, staged, false, false, 10, false, accEvaOn = true)?.accEva)
        assertNull(ndsHealsView(s, staged, false, false, 10, false, accEvaOn = false)?.accEva, "the setting off")
        assertNull(ndsHealsView(s.copy(inBattle = false), staged, false, false, 10, false, accEvaOn = true)?.accEva, "out of battle")
        assertEquals(6 to 6, ndsHealsView(s.copy(party = listOf(lead, second)), lead, false, false, 10, false, accEvaOn = true)?.accEva,
            "no stages read yet: neutral")
    }

    @Test
    fun `the EXP bar is your own Pokemon's, with its setting`() {
        val m = Gen4.decodeParty(Gen4.encodeParty(0x77L, 25, 5, 20, 20, listOf(84, 0, 0, 0), experience = 89))!!
        // A sidecar from before the growth rate was written: the reference's Fluctuating, as it always was.
        val p = NdsTrackedMon(m, "Pikachu", NdsSpeciesInfo("Pikachu", "ELECTRIC", "ELECTRIC", 320, "", ""), "-", "-", emptyList())
        assertEquals(0.5, ndsExpFraction(p, on = true))
        assertNull(ndsExpFraction(p, on = false))
        val panel = File("src/main/kotlin/com/ironmonone/app/NdsTrackerPanel.kt").readText()
        assertEquals(1, Regex("holdExpFraction = ").findAll(panel).count(), "the party card holds the bar, the opponent's card does not")
        assertTrue("holdExpFraction = ndsExpFraction(p, TrackerOptions.dsExpBar)" in panel)
        assertTrue("pokecenterCount, TrackerOptions.tourneyTracker, TrackerOptions.dsAccEva, shownCarrier = p)" in panel, "the panel reads the DS ACC and EVA setting")
    }

    /**
     * rc32 audit P3 #118: the bar assumed Fluctuating for every Pokemon in every mode. A Medium Fast Pokemon at level 30 with
     * 27,000 EXP has just reached it, and read 1.06, a full bar. It follows the species' rate from the run's sidecar now.
     */
    @Test
    fun `the EXP bar follows the species' growth rate, and a game with no sidecar has none`() {
        val m = Gen4.decodeParty(Gen4.encodeParty(0x78L, 25, 30, 60, 60, listOf(84, 0, 0, 0), experience = 27_000))!!
        fun card(rate: Int?) = NdsTrackedMon(m, "Pikachu",
            rate?.let { NdsSpeciesInfo("Pikachu", "ELECTRIC", "ELECTRIC", 320, "", "", growthRate = it) }, "-", "-", emptyList())
        assertEquals(0.0, ndsExpFraction(card(NdsExperience.MEDIUM_FAST), on = true))
        assertEquals(3_240.0 / 3_051.0, ndsExpFraction(card(NdsExperience.FLUCTUATING), on = true)!!, 1e-9, "the old full bar")
        assertNull(ndsExpFraction(card(null), on = true), "no sidecar: a game not randomized here, whose curves are unknown")
    }

    @Test
    fun `the engine writes the growth rate as the ROM numbers it, last on the species' sidecar line`() {
        val zubat = com.dabomstew.pkrandomzx.pokemon.Pokemon().apply {
            number = 41; name = "Zubat"; hp = 40; attack = 45; defense = 35; spatk = 30; spdef = 40; speed = 55
            growthCurve = com.dabomstew.pkrandomzx.pokemon.ExpCurve.MEDIUM_SLOW
        }
        assertEquals("41\tZubat\t\t\t245\t\t\t3", com.ironmonone.app.engine.ZxEngine.sidecarLine(zubat) { "" })
    }

    @Test
    fun `the Pokecenter count starts at 10, stays within 0 to 99, and goes with the run`() {
        val dir = java.nio.file.Files.createTempDirectory("dspc").toFile()
        try {
            val marks = StatMarks(File(dir, "marks.txt"))
            assertEquals(10, marks.dsPokecenterCount())
            repeat(12) { marks.bumpDsPokecenter(up = false) }
            assertEquals(0, marks.dsPokecenterCount())
            repeat(120) { marks.bumpDsPokecenter(up = true) }
            assertEquals(99, StatMarks(File(dir, "marks.txt")).dsPokecenterCount(), "kept with the run")
            marks.clear()
            assertEquals(10, StatMarks(File(dir, "marks.txt")).dsPokecenterCount())
        } finally { dir.deleteRecursively() }
    }

    @Test
    fun `both DS panels have the counter, the panel the box`() {
        val play = File("src/main/kotlin/com/ironmonone/app/PlayScreen.kt").readText()
        assertEquals(2, Regex("onPokecenter = \\{ up -> statMarks\\.bumpDsPokecenter\\(up\\)").findAll(play).count())
        val panel = File("src/main/kotlin/com/ironmonone/app/NdsTrackerPanel.kt").readText()
        assertTrue("heals = ndsHealsView(state, p," in panel, "the party cards no longer carry the DS heals box")
    }
}
