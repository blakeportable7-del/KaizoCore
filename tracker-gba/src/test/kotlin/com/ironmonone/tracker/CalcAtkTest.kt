package com.ironmonone.tracker

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * The native Calc Atk against the extension's own Lua (calcLowHighStat run through lupa over
 * 4000 seeded inputs: tools/parity/calcatk_fixture.py -> calcatk/calcatk-fixture.tsv).
 */
class CalcAtkTest {
    @Test fun `every fixture row matches the extension`() {
        val stream = javaClass.getResourceAsStream("/calcatk/calcatk-fixture.tsv") ?: fail("fixture missing")
        var n = 0; var found = 0
        stream.bufferedReader(Charsets.UTF_8).useLines { lines ->
            lines.forEach { line ->
                if (line.startsWith("#") || line.isBlank()) return@forEach
                val f = line.split('\t')
                val i = CalcAtk.Inputs(
                    level = f[0].toInt(), damage = f[1].toInt(), defense = f[2].toInt(), power = f[3].toInt(),
                    effectiveness = f[4].toDouble(), other = f[5].toDouble(), stab = f[6] == "1", crit = f[7] == "1",
                    weather = f[8].toInt(), burned = f[9] == "1", screen = f[10] == "1",
                )
                val want = f[11].toInt() to f[12].toInt()
                assertEquals(want, CalcAtk.estimate(i), "row $line")
                n++; if (CalcAtk.found(want)) found++
            }
        }
        assertEquals(4000, n)
        assertTrue(found > 500, "the fixture exercises real estimates, not only misses ($found)")
    }

    private fun fill(
        id: Int, power: String, type: Int, category: String = if (type in 0..8) "PHY" else "SPE",
        ownTypes: List<Int> = listOf(0), enemyTypes: List<Int> = listOf(0), weight: Double? = null,
        cur: Int = 10, max: Int = 10, burned: Boolean = false, wild: Boolean = true, weather: Int? = null,
    ) = CalcAtk.autoFill(id, power, type, category, damage = 12, ownTypes = ownTypes, ownDef = 40, ownSpd = 30,
        ownWeightKg = weight, enemyTypes = enemyTypes, enemyLevel = 20, enemyCurHp = cur, enemyMaxHp = max,
        enemyBurned = burned, enemyBaseFriendship = 70, wild = wild, weatherWord = weather)

    @Test fun `auto fill takes the move, your stat and the matchups as the extension does`() {
        // Tackle (Normal, physical) from a Normal enemy onto a Rock Pokemon: STAB, 1/2x, your DEF.
        val t = fill(33, "35", 0, ownTypes = listOf(5), enemyTypes = listOf(0))
        assertEquals(CalcAtk.Inputs(level = 20, damage = 12, defense = 40, power = 35, effectiveness = 0.5, stab = true), t.inputs)
        assertFalse(t.guessed)
        // Ember (Fire, special) is special in Gen 3: your SPD; halved in rain, boosted in sun; no burn halving.
        assertEquals(30, fill(52, "40", 10).inputs.defense)
        assertEquals(CalcAtk.WEATHER_HALVED, fill(52, "40", 10, weather = 1).inputs.weather)
        assertEquals(CalcAtk.WEATHER_BOOSTED, fill(52, "40", 10, weather = 96).inputs.weather)
        assertFalse(fill(52, "40", 10, burned = true).inputs.burned)
        assertTrue(fill(33, "35", 0, burned = true).inputs.burned)
    }

    @Test fun `auto fill special cases follow getMovePowerAndType`() {
        // Weather Ball in rain: Water and doubled, so special; its weather box reads its own Normal type.
        val wb = fill(MoveRules.WEATHER_BALL, "50", 0, weather = 5)
        assertEquals(100, wb.inputs.power); assertEquals(30, wb.inputs.defense); assertEquals(CalcAtk.WEATHER_NONE, wb.inputs.weather)
        // Low Kick by YOUR weight: 30 kg is 60 power.
        assertEquals(60, fill(MoveRules.LOW_KICK, "WT", 1, weight = 30.0).inputs.power)
        // Flail at 10% HP (4.8 of 48): 150, guessed; full HP: 20.
        fill(MoveRules.FLAIL, "<HP", 0, cur = 1, max = 10).let { assertEquals(150, it.inputs.power); assertTrue(it.guessed) }
        assertEquals(20, fill(MoveRules.FLAIL, "<HP", 0).inputs.power)
        // Return from a wild Pokemon of base friendship 70: 28, to the nearest ten; from a trainer's, unknown.
        assertEquals(30, fill(MoveRules.RETURN, ">FR", 0).inputs.power)
        assertEquals(0, fill(MoveRules.RETURN, ">FR", 0, wild = false).inputs.power)
        assertEquals(70, fill(MoveRules.FRUSTRATION, "<FR", 0).inputs.power)
        // Triple Kick 10 + 20 + 30; Double Kick twice; Fury Attack's hits cannot be told apart.
        fill(167, "10", 1).let { assertEquals(60, it.inputs.power); assertTrue(it.guessed) }
        fill(24, "30", 1).let { assertEquals(60, it.inputs.power); assertFalse(it.guessed) }
        assertTrue(fill(31, "15", 0).guessed)
        // Eruption at half HP: 75 to the nearest ten (the extension's own rounding gave 1).
        assertEquals(80, fill(MoveRules.ERUPTION, ">HP", 10, cur = 5, max = 10).inputs.power)
    }

    @Test fun `a plain hit reads back the stat that dealt it`() {
        // Lv.20, power 40, your DEF 30, attack 35: 85-100% of the formula.
        val i = CalcAtk.Inputs(level = 20, damage = 0, defense = 30, power = 40)
        val (lo, hi) = CalcAtk.damageRange(i, 35)
        for (d in lo..hi) {
            val (low, high) = CalcAtk.estimate(i.copy(damage = d))
            assertTrue(35 in low..high, "damage $d gives $low-$high")
        }
        assertFalse(CalcAtk.found(CalcAtk.estimate(i.copy(damage = 999))))
    }
}
