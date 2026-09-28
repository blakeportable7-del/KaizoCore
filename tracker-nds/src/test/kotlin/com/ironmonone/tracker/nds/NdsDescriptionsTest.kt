package com.ironmonone.tracker.nds

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The DS reference's hover text on your ability and held item (MainScreen.setUpMainPokemonInfo). */
class NdsDescriptionsTest {
    @Test
    fun `ability descriptions use the Gen 4 or Gen 5 wording`() {
        assertEquals("Helps repel wild Pokémon.", NdsLogData.abilityDescription(1, gen = 4))
        assertEquals("Has a 10% chance of making target Pokémon flinch with each hit.", NdsLogData.abilityDescription(1, gen = 5))
        assertEquals("Raises Speed one stage after each turn.", NdsLogData.abilityDescription(3, gen = 4))
    }

    @Test
    fun `held item descriptions, and a nature berry says whether it is liked`() {
        assertEquals("Consumed at 1/2 max HP to recover 10 HP.", NdsLogData.heldItemDescription(155, nature = 0))
        // Aguav is disliked by Naughty (index 4) and liked by Hardy (index 0).
        assertTrue(NdsLogData.heldItemDescription(162, nature = 4).endsWith(" Your Pokémon will dislike this."))
        assertTrue(NdsLogData.heldItemDescription(162, nature = 0).endsWith(" Yum!"))
        assertEquals("", NdsLogData.heldItemDescription(0, nature = 0))
    }
}
