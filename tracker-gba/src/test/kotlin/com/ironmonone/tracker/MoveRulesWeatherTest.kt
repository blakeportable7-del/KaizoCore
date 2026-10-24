package com.ironmonone.tracker

import kotlin.test.Test
import kotlin.test.assertEquals

/** The weather on moves, as pokehns-expansion works it out (GetWeatherDamageModifier, Solar Beam, the accuracy skips). */
class MoveRulesWeatherTest {
    private val fire = 10; private val water = 11; private val electric = 13

    @Test
    fun `sun and rain move Fire and Water, and Solar Beam drops in rain, sand and hail`() {
        assertEquals(1, MoveRules.weatherMark(53, fire, "SUN"))
        assertEquals(-1, MoveRules.weatherMark(53, fire, "RAIN"))
        assertEquals(1, MoveRules.weatherMark(57, water, "RAIN"))
        assertEquals(-1, MoveRules.weatherMark(57, water, "SUN"))
        assertEquals(0, MoveRules.weatherMark(85, electric, "RAIN"), "Thunderbolt is not a weather move")
        for (w in listOf("RAIN", "SANDSTORM", "HAIL")) assertEquals(-1, MoveRules.weatherMark(76, 12, w), w)
        assertEquals(0, MoveRules.weatherMark(76, 12, "SUN"))
        assertEquals(0, MoveRules.weatherMark(53, fire, null), "Cloud Nine or Air Lock out: no weather")
    }

    @Test
    fun `Thunder never misses in rain and is 50 in sun, Blizzard never misses in hail on the later rules`() {
        val side = MoveRules.Side(level = 30, curHp = 50, maxHp = 50, friendship = 0)
        fun acc(id: Int, w: String?, later: Boolean = true) = MoveRules.adjust(id, null, "110", "70", side, side, true, true, w, false, laterRules = later).acc
        assertEquals("0", acc(87, "RAIN"))
        assertEquals("50", acc(87, "SUN"))
        assertEquals("70", acc(87, "SANDSTORM"))
        assertEquals("0", acc(59, "HAIL"))
        assertEquals("70", acc(59, "HAIL", later = false), "Gen 3's Blizzard can miss in hail")
    }
}
