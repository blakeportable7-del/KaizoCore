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

    // ------------------------------------------------------------ Game Boy (forOwnGb)

    private fun gb(evo: String, level: Int = 5, stones: Set<EvoText.GbStone> = emptySet(), friendship: Int = 0, option: Boolean = true) =
        EvoText.forOwnGb(evo, level, stones, friendship, option)

    /** Gen 1 reference TrackerScreen.lua:748: every readiness colour sits inside "Determine friendship readiness". */
    @Test
    fun `on a Game Boy game the option off draws every method in the default colour`() {
        assertEquals(Label("16", Tone.PLAIN), gb("16", level = 20, option = false), "level-ready, still plain")
        assertEquals(Label("THUNDER", Tone.PLAIN), gb("THUNDER", stones = setOf(EvoText.GbStone.THUNDER), option = false))
        assertEquals(Label("FRIEND", Tone.PLAIN), gb("FRIEND", friendship = 255, option = false), "no READY without the option")
        assertEquals(Label("16", Tone.WAITING), gb("16", level = 14))
        assertEquals(Label("16", Tone.READY), gb("16", level = 15))
    }

    /** Utils.isReadyToEvolveByLevel reads the first number in the method; the Game Boy methods are strings. */
    @Test
    fun `a Game Boy water-or-level method is ready by its level, and by a Water Stone`() {
        assertEquals(Label("37/WTR", Tone.WAITING), gb("WATER37", level = 35))
        assertEquals(Label("37/WTR", Tone.READY), gb("WATER37", level = 36))
        assertEquals(Label("37/WTR", Tone.READY), gb("WATER37", stones = setOf(EvoText.GbStone.WATER)))
        assertEquals(Label("37/WTR", Tone.WAITING), gb("WATER37", stones = setOf(EvoText.GbStone.MOON)))
    }

    /** MiscData.EvolutionStones of the Game Boy references: which stone readies which method. */
    @Test
    fun `Game Boy stones follow the references' stone table`() {
        assertEquals(Label("THUNDER", Tone.READY), gb("THUNDER", stones = setOf(EvoText.GbStone.THUNDER)))
        assertEquals(Label("THUNDER", Tone.WAITING), gb("THUNDER", stones = setOf(EvoText.GbStone.FIRE)))
        assertEquals(Label("STONE", Tone.READY), gb("EEVEE_STONES", stones = setOf(EvoText.GbStone.MOON)), "the table lists STONES under the Moon Stone")
        assertEquals(Label("STONE", Tone.WAITING), gb("EEVEE_STONES", stones = setOf(EvoText.GbStone.LEAF)))
        assertEquals(Label("LEAF", Tone.READY), gb("LEAF", stones = setOf(EvoText.GbStone.LEAF)))
        assertEquals(Label("SUN", Tone.WAITING), gb("SUN", stones = EvoText.GbStone.entries.toSet()), "no Sun Stone in the references' table")
    }

    /** DataHelper.lua:166-170 and Program.lua:23: READY at 220, no partial fill. */
    @Test
    fun `a Game Boy friendship evolution reads READY at 220 and has no fill`() {
        assertEquals(Label("READY", Tone.READY), gb("FRIEND", friendship = 220))
        assertEquals(Label("FRIEND", Tone.WAITING), gb("FRIEND", friendship = 219))
        assertNull(EvoText.forOwnGb(null, 5, emptySet(), 0, true))
    }

    @Test
    fun `the info screen's detailed words, as Utils getDetailedEvolutionsInfo gives them`() {
        assertEquals(listOf("Level 16"), EvoText.detailed("16"))
        assertEquals(listOf("Fire Stone"), EvoText.detailed("FIRE"))
        assertEquals(listOf("Thunderstone"), EvoText.detailed("THUNDER"))
        assertEquals(listOf("Water Stone"), EvoText.detailed("WATER"))
        assertEquals(listOf("Moon Stone"), EvoText.detailed("MOON"))
        assertEquals(listOf("Leaf Stone"), EvoText.detailed("LEAF"))
        assertEquals(listOf("Sun Stone"), EvoText.detailed("SUN"))
        assertEquals(listOf("5 Diff. Stones"), EvoText.detailed("EEVEE_STONES"))
        assertEquals(listOf("220 Friendship"), EvoText.detailed("FRIEND"))
        assertEquals(listOf("160 Friendship"), EvoText.detailed("FRIEND", friendshipRequired = 160), "the ROM's requirement")
        assertEquals(listOf("Leaf Stone", "Sun Stone"), EvoText.detailed("LEAF_SUN"))
        assertEquals(listOf("Level 30", "Water Stone"), EvoText.detailed("WATER30"))
        assertEquals(listOf("Level 37", "Water Stone"), EvoText.detailed("WATER37"))
        assertEquals(listOf("Water Stone", "Level 37"), EvoText.detailed("WATER37_REV, -- Level 37 replaces trade evolution for Politoed"))
        assertEquals(listOf("---"), EvoText.detailed(null))
        // The Game Boy trackers' table spells the one stone differently.
        assertEquals(listOf("Thunder Stone"), EvoText.detailed("THUNDER", generation = 1))
    }

    @Test
    fun `the log viewer's short words, one per evolution`() {
        assertEquals(listOf("Lv.16"), EvoText.short("16"))
        assertEquals(listOf("Friend"), EvoText.short("FRIEND"))
        assertEquals(listOf("Fire"), EvoText.short("FIRE"))
        assertEquals(listOf("Thunder", "Water", "Fire", "Sun", "Moon"), EvoText.short("EEVEE_STONES"))
        assertEquals(listOf("Leaf", "Sun"), EvoText.short("LEAF_SUN"))
        assertEquals(listOf("Water", "Lv.37"), EvoText.short("WATER37_REV"))
        assertEquals(listOf("---"), EvoText.short(null))
    }
}
