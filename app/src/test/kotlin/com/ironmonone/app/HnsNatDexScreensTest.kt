package com.ironmonone.app

import com.ironmonone.tracker.GbaTracker
import com.ironmonone.tracker.HnsMovePower
import com.ironmonone.tracker.MoveRow
import com.ironmonone.tracker.MoveRules
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * What the Nat. Dex Heart & Soul sweep (2026-10-06) changed on the tracker's screens, with no ROM: the route screen keeps
 * a run's own wild tables to itself, and the move rows print Heart & Soul's power labels and its own categories.
 */
class HnsNatDexScreensTest {
    private fun source(runTables: Boolean, seen: List<Int>) = RouteInfoSource(
        mapId = 11, name = "ROUTE 29",
        vanilla = mapOf("Walking" to listOf(GbaTracker.RouteMon(16, 0.4, 2, 4), GbaTracker.RouteMon(1011, 0.3, 3, 5), GbaTracker.RouteMon(19, 0.3, 2, 3))),
        tracked = { if (it == "Walking") seen else emptyList() },
        runTables = runTables,
    )

    @Test
    fun `a Heart and Soul run's route table shows only the Pokemon met there, with every chance and level`() {
        // Heart & Soul: the table is the run's own; Pidgey (16) was met, the other two were not.
        val hns = source(runTables = true, seen = listOf(16))
        val percents = routeIcons(hns, "Walking", null, false, showPercents = true, showLevels = false, openBook = false)
        assertEquals(listOf(16, null, null), percents.map { it.species })
        assertEquals(listOf(0.4, 0.3, 0.3), percents.map { it.rate })
        assertEquals(listOf(2 to 4, 3 to 5, 2 to 3), routeIcons(hns, "Walking", null, false, false, showLevels = true, openBook = false).map { it.minLv to it.maxLv })
        // Open Book shows everything.
        assertEquals(listOf(16, 1011, 19), routeIcons(hns, "Walking", null, false, true, false, openBook = true).map { it.species })
        // Every other game's table is its vanilla game's (RouteData), shown as before.
        assertEquals(listOf(16, 1011, 19), routeIcons(source(false, emptyList()), "Walking", null, false, true, false, false).map { it.species })
        // The default view is unchanged: met ones in order, then a "?" for each one left.
        assertEquals(listOf(16, null, null), routeIcons(hns, "Walking", null, false, false, false, false).map { it.species })
    }

    private fun row(id: Int, name: String, power: Int, type: Int, category: String) =
        MoveRow(id = id, name = name, pp = 15, ppMax = 15, power = power, acc = 100, type = type, category = category,
            powerLabel = HnsMovePower.label(name, power))

    private fun ctx(own: Boolean, hpType: Int?, weather: String? = null) = MoveContext(
        inBattle = true, viewingOwn = true, attackerTypes = listOf(0), targetTypes = listOf(0),
        source = MoveRules.Side(50, 100, 100), target = MoveRules.Side(50), weather = weather, hiddenPowerType = hpType,
        natDex = true, ownCategories = own,
    )

    @Test
    fun `Heart and Soul's Hidden Power and Weather Ball keep the ROM's special category`() {
        val hp = row(MoveRules.HIDDEN_POWER, "Hidden Power", 60, 0, "SPE")
        assertEquals("SPE", hp.toPcMove(ctx(own = true, hpType = 1)).category, "a Fighting Hidden Power is special on Heart & Soul")
        assertEquals("PHY", hp.toPcMove(ctx(own = false, hpType = 1)).category, "Gen 3's rule elsewhere")
        val wb = row(MoveRules.WEATHER_BALL, "Weather Ball", 50, 0, "SPE")
        assertEquals("SPE", wb.toPcMove(ctx(own = true, hpType = null, weather = "SANDSTORM")).category)
    }

    @Test
    fun `Heart and Soul's worked-out powers print their label, not the table's 1`() {
        assertEquals("WT", row(447, "Grass Knot", 1, 12, "SPE").toPcMove(ctx(true, null)).powerText)
        assertEquals("<SP", row(360, "Gyro Ball", 1, 8, "PHY").toPcMove(ctx(true, null)).powerText)
        // Ruination's fixed share of HP: no power, and the chart's 2x and 1/2 do not apply, as Super Fang's.
        val ruination = row(803, "Ruination", 1, 17, "SPE").toPcMove(ctx(true, null).copy(targetTypes = listOf(1)))
        assertEquals("0", ruination.powerText); assertEquals(null, ruination.effect)
        assertEquals(0.5, row(44, "Bite", 60, 17, "PHY").toPcMove(ctx(true, null).copy(targetTypes = listOf(1))).effect, "a Dark move with a power: the chart applies")
        // A real power, or a move with no label, prints its number.
        assertEquals("80", row(447, "Grass Knot", 80, 12, "SPE").toPcMove(ctx(true, null)).powerText)
        assertEquals("1", MoveRules.basePower(999, 1))
    }
}
