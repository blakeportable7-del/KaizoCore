package com.ironmonone.tracker

import com.ironmonone.tracker.EvoText.Label
import com.ironmonone.tracker.EvoText.Tone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.fail

/** "Lv.5 (30)" as TrackerScreen.lua draws it (Ironmon-Tracker v9.3.1). */
class EvoTextTest {
    private val noBag = { fail("the bag must not be read for this evolution") }
    private fun own(evo: String?, level: Int = 5, bag: Set<Int> = emptySet(), friendship: Int = 70, base: Int = 70, req: Int = 220) =
        EvoText.forOwn(evo, level, { bag }, friendship, base, req)

    @Test
    fun `a level evolution waits in the intermediate colour, and is ready one level early`() {
        assertEquals(Label("30", Tone.WAITING), EvoText.forOwn("30", 5, noBag, 70, 70, 220))
        assertEquals(Label("30", Tone.WAITING), own("30", level = 28))
        // Utils.isReadyToEvolveByLevel: (level + 1) >= target.
        assertEquals(Label("30", Tone.READY), own("30", level = 29))
    }

    @Test
    fun `a stone evolution is ready only with that stone in the bag`() {
        assertEquals(Label("THUNDER", Tone.WAITING), own("THUNDER"))
        assertEquals(Label("THUNDER", Tone.READY), own("THUNDER", bag = setOf(96)))
        assertEquals(Label("THUNDER", Tone.WAITING), own("THUNDER", bag = setOf(95)))
        assertEquals(Label("STONE", Tone.READY), own("EEVEE_STONES", bag = setOf(95)))
    }

    @Test
    fun `a combined method is not level-ready, only stone-ready`() {
        assertEquals(Label("30/WTR", Tone.WAITING), own("WATER30", level = 60))
        assertEquals(Label("30/WTR", Tone.READY), own("WATER30", bag = setOf(97)))
    }

    @Test
    fun `friendship reads READY at the requirement`() {
        assertEquals(Label("READY", Tone.READY), own("FRIEND", friendship = 220))
    }

    @Test
    fun `friendship short of it fills green a letter at a time`() {
        // Halfway from base 70 to 220: floor(6 * 0.5) = 3 letters, "FRI".
        assertEquals(Label("FRIEND", Tone.PLAIN, 3), own("FRIEND", friendship = 145))
        assertEquals(Label("FRIEND", Tone.PLAIN, 0), own("FRIEND", friendship = 70))
        assertEquals(Label("FRIEND", Tone.PLAIN, 0), own("FRIEND", friendship = 0))
        assertEquals(Label("FRIEND", Tone.PLAIN, 5), own("FRIEND", friendship = 219))
    }

    @Test
    fun `with friendship readiness off, FRIEND waits and never reads READY`() {
        val off = { f: Int -> EvoText.forOwn("FRIEND", 30, { emptySet() }, f, 70, 220, determineFriendship = false) }
        assertEquals(Label("FRIEND", Tone.WAITING), off(145))
        assertEquals(Label("FRIEND", Tone.WAITING), off(255))
    }

    @Test
    fun `nothing is drawn when it does not evolve`() {
        assertNull(own(null)); assertNull(own("")); assertNull(own("NONE"))
        assertNull(EvoText.forEnemy(null))
    }

    @Test
    fun `the opponent gets the text in the default colour and no readiness`() {
        assertEquals(Label("30", Tone.PLAIN), EvoText.forEnemy("30"))
        assertEquals(Label("FRIEND", Tone.PLAIN), EvoText.forEnemy("FRIEND"))
    }

    @Test
    fun `the Lua comments our table caught are cleaned off`() {
        assertEquals("37", EvoText.clean("37\", -- Level 37 replaces trade evolution"))
        assertEquals("WATER37_REV", EvoText.clean("WATER37_REV, -- Level 37 replaces trade evolution for Politoed"))
        assertEquals(Label("WTR/37", Tone.WAITING), own("WATER37_REV, -- Level 37 replaces trade evolution for Politoed"))
    }

    @Test
    fun `every evolution in our species table has an abbreviation`() {
        val stream = javaClass.getResourceAsStream("/gen3/species-extra.tsv") ?: fail("gen3/species-extra.tsv missing")
        var evolving = 0
        stream.bufferedReader(Charsets.UTF_8).useLines { lines ->
            lines.forEach { line ->
                val raw = line.split('\t').getOrNull(2) ?: return@forEach
                if (EvoText.clean(raw) == null) return@forEach
                evolving++
                assertNotNull(EvoText.abbreviation(raw), "no abbreviation for \"$raw\" in: $line")
            }
        }
        // Non-vacuous: the table holds 136 level evolutions and 36 other methods.
        kotlin.test.assertTrue(evolving >= 150, "only $evolving evolving species read; the table did not load")
    }
}
