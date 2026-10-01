package com.ironmonone.tracker

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * InfoScreen's ability view (InfoScreen.lua:1029-1041) draws the description, a gap, "In Emerald:"
 * (Resources.InfoScreen.LabelEmeraldAbility, Languages/English.lua:441) and the Emerald-only effect,
 * in every game: nothing there checks the game. The app appended the Emerald text in brackets with
 * no label, and only on Ruby, Sapphire and Emerald (parity audit, 2026-09-28).
 */
class AbilityDescriptionTest {
    private fun tracker(map: GameMap) = GbaTracker(MemoryReader { _, n -> ByteArray(n) }, map)

    @Test
    fun `the Emerald note is labelled and shown in every Gen 3 game`() {
        val stench = "While at the head of the party, decreases the wild encounter rate by 50%.\n\n" +
            "In Emerald:\nIn the Battle Pyramid, the wild encounter rate is only decreased by 25%."
        for (map in listOf(GameMap.FIRERED_U_V10, GameMap.LEAFGREEN_U, GameMap.RUBY_U, GameMap.SAPPHIRE_U, GameMap.EMERALD_U))
            assertEquals(stench, tracker(map).abilityDescription(1), map.name)
    }

    @Test
    fun `an ability without an Emerald note has no label`() {
        val drizzle = tracker(GameMap.EMERALD_U).abilityDescription(2)
        assertEquals(true, drizzle?.startsWith("Changes weather to rain when switched in."))
        assertEquals(false, drizzle?.contains("In Emerald"))
        assertEquals(null, tracker(GameMap.EMERALD_U).abilityDescription(9999))
    }
}
