package com.ironmonone.tracker

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Heart & Soul's three battle weathers that change what a move does (HnsWeather, 2026-10-05): named on the weather line,
 * applied to the move rows' effectiveness, and nothing else changes for any other weather or game.
 */
class HnsWeatherTest {
    private val electric = 13
    private val fire = 10
    private val water = 11
    private val flying = 2
    private val normal = 0
    private val ground = 4
    /** An id with no special case in MoveRules (not typeless, not a fixed-damage or status table entry). */
    private val plainMove = 9000

    @Test
    fun `strong winds turns a 2x on a Flying defender into 1x`() {
        assertEquals(2.0, MoveRules.effectiveness(plainMove, electric, "SPE", listOf(flying, flying), natDex = true))
        assertEquals(1.0, MoveRules.effectiveness(plainMove, electric, "SPE", listOf(flying, flying), natDex = true, weather = HnsWeather.STRONG_WINDS))
        // Flying/Water against Electric: the Flying half drops to 1x, the Water half keeps its 2x.
        assertEquals(4.0, MoveRules.effectiveness(plainMove, electric, "SPE", listOf(flying, water), natDex = true))
        assertEquals(2.0, MoveRules.effectiveness(plainMove, electric, "SPE", listOf(flying, water), natDex = true, weather = HnsWeather.STRONG_WINDS))
        // Not super effective on Flying: nothing changes (Ground still cannot touch it; Electric on Water keeps its 2x).
        assertEquals(0.0, MoveRules.effectiveness(plainMove, ground, "PHY", listOf(flying, normal), natDex = true, weather = HnsWeather.STRONG_WINDS))
        assertEquals(2.0, MoveRules.effectiveness(plainMove, electric, "SPE", listOf(water, water), natDex = true, weather = HnsWeather.STRONG_WINDS))
    }

    @Test
    fun `heavy rain makes a damaging Fire move show as no effect, extreme sun a Water move`() {
        assertEquals(1.0, MoveRules.effectiveness(plainMove, fire, "SPE", listOf(normal, normal), natDex = true))
        assertEquals(0.0, MoveRules.effectiveness(plainMove, fire, "SPE", listOf(normal, normal), natDex = true, weather = HnsWeather.HEAVY_RAIN))
        assertEquals(0.0, MoveRules.effectiveness(plainMove, water, "PHY", listOf(normal, normal), natDex = true, weather = HnsWeather.EXTREME_SUN))
        // A Fire status move still works in heavy rain; a Water move is untouched by heavy rain.
        assertEquals(1.0, MoveRules.effectiveness(plainMove, fire, "STA", listOf(normal, normal), natDex = true, weather = HnsWeather.HEAVY_RAIN))
        assertEquals(1.0, MoveRules.effectiveness(plainMove, water, "SPE", listOf(normal, normal), natDex = true, weather = HnsWeather.HEAVY_RAIN))
    }

    @Test
    fun `plain rain and sun still show as rain and sun and change nothing`() {
        // B_WEATHER_RAIN_NORMAL is bit 0, B_WEATHER_SUN_NORMAL bit 3 (include/constants/battle.h).
        assertEquals("RAIN", HnsWeather.line(1))
        assertEquals("SUN", HnsWeather.line(HnsLayout.B_WEATHER_SUN and HnsLayout.B_WEATHER_SUN_PRIMAL.inv()))
        assertEquals(HnsWeather.HEAVY_RAIN, HnsWeather.line(HnsLayout.B_WEATHER_RAIN_PRIMAL))
        assertEquals(HnsWeather.EXTREME_SUN, HnsWeather.line(HnsLayout.B_WEATHER_SUN_PRIMAL))
        assertEquals(HnsWeather.STRONG_WINDS, HnsWeather.line(HnsLayout.B_WEATHER_STRONG_WINDS))
        assertEquals("SANDSTORM", HnsWeather.line(HnsLayout.B_WEATHER_SANDSTORM))
        assertEquals("HAIL", HnsWeather.line(HnsLayout.B_WEATHER_SNOW))
        assertNull(HnsWeather.line(HnsLayout.B_WEATHER_FOG))
        assertNull(HnsWeather.line(0))
        // Heavy rain is still rain to Calc Atk and Weather Ball (the Gen 3 word); strong winds has no word.
        assertEquals(0x01, HnsWeather.word(HnsLayout.B_WEATHER_RAIN_PRIMAL))
        assertEquals(0x20, HnsWeather.word(HnsLayout.B_WEATHER_SUN_PRIMAL))
        assertEquals(0, HnsWeather.word(HnsLayout.B_WEATHER_STRONG_WINDS))
        // Plain rain leaves every multiplier as the chart has it.
        assertEquals(1.0, MoveRules.effectiveness(plainMove, fire, "SPE", listOf(normal, normal), natDex = true, weather = "RAIN"))
        assertEquals(2.0, MoveRules.effectiveness(plainMove, electric, "SPE", listOf(flying, flying), natDex = true, weather = "RAIN"))
    }

    @Test
    fun `the other games' weather names are what they were`() {
        assertEquals("RAIN", gen3WeatherName(0x01))
        assertEquals("SANDSTORM", gen3WeatherName(0x08))
        assertEquals("SUN", gen3WeatherName(0x20))
        assertEquals("HAIL", gen3WeatherName(0x80))
        assertNull(gen3WeatherName(0x100))
        assertNull(gen3WeatherName(0))
    }
}
