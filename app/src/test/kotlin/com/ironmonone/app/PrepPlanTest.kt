package com.ironmonone.app

import com.ironmonone.core.RomKind
import kotlin.test.Test
import kotlin.test.assertTrue

/** PREP describes what it will do per game, and offers Nat. Dex only where it exists. */
class PrepPlanTest {
    @Test
    fun `each family gets its own plan`() {
        assertTrue(PrepPlan.lines(RomKind.EMERALD_U).first().startsWith("Nat. Dex:"))
        assertTrue(PrepPlan.lines(RomKind.LEAFGREEN_U).first().contains("Smart AI"), "Super Kaizo is offered where its patch exists")
        assertTrue(PrepPlan.lines(RomKind.RUBY_U).first().contains("No patch applies"))
        assertTrue(PrepPlan.lines(RomKind.FIRERED_U_V10).first().startsWith("Standard only"))
        assertTrue(PrepPlan.lines(RomKind.RED_U).first().contains("pseudo-fluctuating"))
        assertTrue(PrepPlan.lines(RomKind.RED_U).any { it.contains("two passes") })
        assertTrue(PrepPlan.lines(RomKind.CRYSTAL_U).first().contains("requires it for Crystal"))
        assertTrue(PrepPlan.lines(RomKind.HEARTGOLD_U).first().contains("Super Kaizo"))
        assertTrue(PrepPlan.lines(RomKind.RED_PF).first().startsWith("Already carries"))
        assertTrue(PrepPlan.lines(RomKind.BLACK2_U).any { it.contains("needs no patch") })
        assertTrue(PrepPlan.lines(RomKind.RUBY_U).any { it == "Settings files: RSE." })
        assertTrue(PrepPlan.lines(RomKind.EMERALD_NATDEX_121).first().startsWith("Already a Nat. Dex"))
    }
}
