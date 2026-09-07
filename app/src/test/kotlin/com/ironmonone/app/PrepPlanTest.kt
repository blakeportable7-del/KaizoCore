package com.ironmonone.app

import com.ironmonone.core.RomKind
import kotlin.test.Test
import kotlin.test.assertTrue

/** PREP describes what it will do per game, and offers Nat. Dex only where it exists. */
class PrepPlanTest {
    @Test
    fun `each family gets its own plan`() {
        assertTrue(PrepPlan.lines(RomKind.EMERALD_U).first().startsWith("Nat. Dex:"))
        assertTrue(PrepPlan.lines(RomKind.FIRERED_U_V10).first().startsWith("Standard only"))
        assertTrue(PrepPlan.lines(RomKind.RED_U).first().contains("two passes"))
        assertTrue(PrepPlan.lines(RomKind.BLACK2_U).any { it.contains("never patched") })
        assertTrue(PrepPlan.lines(RomKind.RUBY_U).any { it == "Settings files: RSE." })
        assertTrue(PrepPlan.lines(RomKind.EMERALD_NATDEX_121).first().startsWith("Already a Nat. Dex"))
    }
}
