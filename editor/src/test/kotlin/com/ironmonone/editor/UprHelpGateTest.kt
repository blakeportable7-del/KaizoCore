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
        assertTrue(help.startsWith("Select this to allow yourself to limit"), help)
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
