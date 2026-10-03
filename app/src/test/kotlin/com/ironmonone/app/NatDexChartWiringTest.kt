package com.ironmonone.app

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * rc33 audit P1 #70: on the Nat. Dex expansion every chart the tracker draws takes the expansion's (Fairy attacks,
 * no Steel resist to Ghost or Dark): the move rows, the move info, Calc Atk, Type Defenses and the Coverage Calculator.
 * The charts themselves are tested in tracker-gba; this pins that each screen asks for the right one.
 */
class NatDexChartWiringTest {
    private fun src(name: String) = File("src/main/kotlin/com/ironmonone/app/$name").readText()
    private fun count(text: String, s: String) = text.split(s).size - 1

    @Test
    fun `every chart on a Nat Dex game takes its chart`() {
        assertEquals(2, count(src("MoveDecor.kt"), "power = adj.power, natDex = ctx.natDex, maxDex = ctx.maxDex)"), "both move rows")
        val panel = src("TrackerPanel.kt")
        assertTrue("natDex: Boolean = speciesTotal > 411," in panel, "the panel knows the expansion by its species")
        assertEquals(2, count(panel, "generation = generation, natDex = natDex, maxDex = maxDex),"), "your card and the opponent's")
        assertEquals(2, count(panel, "gen1 = generation == 1, natDex = natDex)"), "both move infos")
        assertTrue("MoveMatchup.general(mv.type, gen1, natDex)" in panel)
        val play = src("PlayScreen.kt")
        assertTrue("trackerRef?.expandedSpeciesIds == true -> 1283" in play, "Play hands the expansion's species count")
        assertEquals(2, count(play, "natDex = session.kind?.isNatDex == true) },"), "Type Defenses, both layouts")
        assertTrue("allTypes = gba.typeNames," in play, "the Coverage Calculator offers Fairy")
        assertTrue("natDex = t.expandedSpeciesIds," in src("CalcAtkScreen.kt"))
    }

    @Test
    fun `the move info of a Fairy move has its chart on the expansion`() {
        val saved = TrackerOptions.showTypeMatchups
        try {
            TrackerOptions.showTypeMatchups = true
            val moonblast = com.ironmonone.tracker.MoveRow(585, "MOONBLAST", 15, null, 95, 100, 18, "SPE").toPcMove(null)
            assertEquals(listOf("Dark", "Dragon", "Fighting"), detailOf(moonblast, null, natDex = true).typeChart?.strongAgainst)
        } finally { TrackerOptions.showTypeMatchups = saved }
    }
}
