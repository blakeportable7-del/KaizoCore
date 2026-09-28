package com.ironmonone.editor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The generated help, gating and per-game tables.
 *
 * All three come from the vendored desktop randomizer via tools/extract_upr*.py.
 * These pin the specific facts the editor's behaviour depends on, so a bad
 * regeneration fails here rather than silently greying out the wrong controls.
 */
class UprHelpGateTest {

    @Test
    fun `descriptions are the randomizer's own, and plain text`() {
        val help = SettingsReflector.helpFor("limitPokemon")
        assertNotNull(help, "limitPokemon should have upstream help")
        // Worded for a phone since 2026-09-27 ("Select this" became "Turn this on").
        assertTrue(help.startsWith("Turn this on to allow yourself to limit"), help)
        // Bundle.properties stores Swing HTML; showing markup would be worse
        // than showing nothing.
        assertTrue("<" !in help, "markup leaked into help: $help")
    }

    @Test
    fun `a field with no upstream tooltip returns null, not invented text`() {
        assertNull(SettingsReflector.helpFor("selectedEXPCurve"))
    }

    @Test
    fun `the dependent-dead rule from the audit is present`() {
        // The exact case: "Base stats follow evolutions" does nothing while
        // "Base statistics mod" is Unchanged, and the editor used to accept
        // the edit anyway.
        val gate = SettingsReflector.gateFor("baseStatsFollowEvolutions")
        assertNotNull(gate)
        assertEquals("baseStatisticsMod", gate.whenField)
        assertEquals("UNCHANGED", gate.equalsValue)
    }

    @Test
    fun `help keeps its line breaks and reads for a phone`() {
        // The old extractor dropped the backslash of every newline escape: "being selected. nThis bans".
        val trap = SettingsReflector.helpFor("banTrappingAbilities")!!
        assertTrue("\nThis bans Arena Trap" in trap, trap)
        assertTrue(" nThis" !in trap, trap)
        // Stripped HTML lists ran together; they are bullet lines now.
        assertTrue("\n\u2022 " in SettingsReflector.helpFor("doubleBattleMode")!!)
        // Desktop-layout words mean nothing in a phone list.
        val layout = Regex("to the right|slider below|option above|select amount of new|Use this slider", RegexOption.IGNORE_CASE)
        listOf("updateBaseStats", "updateMoves", "movesetsForceGoodDamaging", "movesetsGoodDamagingPercent",
            "additionalBossTrainerPokemon", "trainersLevelModifier", "useMinimumCatchRate").forEach { id ->
            val h = SettingsReflector.helpFor(id)!!
            assertTrue(!layout.containsMatchIn(h), "$id: $h")
        }
    }

    @Test
    fun `an enum's help describes the setting and every value`() {
        val h = SettingsReflector.helpFor("baseStatisticsMod")!!
        assertTrue(h.startsWith("How each Pok\u00e9mon's base stats change."), h)
        assertTrue("\u2022 Shuffle:" in h && "\u2022 Random:" in h, h)
    }

    @Test
    fun `a field keeps every gate it has`() {
        // Keyed by field, the map kept only the LAST line: balanceShakingGrass
        // lost its Area mapping rule and stayed live there.
        val gates = SettingsReflector.gatesFor("balanceShakingGrass").map { it.equalsValue }.toSet()
        assertEquals(setOf("AREA_MAPPING", "GLOBAL_MAPPING"), gates)
        // The switch-then-number pairs the extractor cannot see.
        assertEquals(SettingsReflector.Gate("trainersLevelModified", "false"), SettingsReflector.gateFor("trainersLevelModifier"))
    }

    @Test
    fun `Gen 3 cannot use mega evolutions`() {
        // The other case from the audit: the editor offered "Abilities follow
        // mega evolutions" while editing FireRed.
        assertTrue(SettingsReflector.unsupportedIn("abilitiesFollowMegaEvolutions", "GEN3"))
        assertTrue(SettingsReflector.unsupportedIn("baseStatsFollowMegaEvolutions", "GEN3"))
    }

    @Test
    fun `unknown or unlisted combinations stay visible`() {
        // Absent means SHOWN - the safe direction. A missing rule leaves a
        // control usable; a wrong one hides something the game supports.
        assertTrue(!SettingsReflector.unsupportedIn("baseStatisticsMod", "GEN3"))
        assertTrue(!SettingsReflector.unsupportedIn("abilitiesFollowMegaEvolutions", null))
    }

    @Test
    fun `the app's own generation names work, not just the table's`() {
        // The app's enum is GBA3/NDS4; the table is keyed GEN3/GEN4. Passing
        // the app's name matched nothing and turned the whole filter off
        // WITHOUT any error - the section counts were simply unchanged.
        assertTrue(SettingsReflector.unsupportedIn("abilitiesFollowMegaEvolutions", "GBA3"))
        assertTrue(SettingsReflector.unsupportedIn("abilitiesFollowMegaEvolutions", "GEN3"))
        assertTrue(SettingsReflector.unsupportedIn("allowWildAltFormes", "NDS4"))
    }
}
