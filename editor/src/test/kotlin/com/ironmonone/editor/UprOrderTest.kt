package com.ironmonone.editor

import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The editor must list a master control BEFORE the controls that depend on it.
 *
 * Reflection returns fields in an arbitrary order, so the picker rendered
 * "Abilities follow evolutions" above "Abilities mod" - the dependent above
 * the switch that decides whether it does anything at all. The order now comes
 * from the desktop randomizer's own form layout (see
 * tools/extract_upr_order.py); this pins the property that matters.
 */
class UprOrderTest {

    private fun order(): List<String> =
        SettingsReflector::class.java.classLoader!!
            .getResourceAsStream("upr-order.tsv")!!
            .bufferedReader().readLines()
            .filterNot { it.startsWith("#") || it.isBlank() }
            .map { it.substringBefore('\t') }

    @Test
    fun `the order resource ships and is substantial`() {
        assertTrue(order().size > 120, "only ${order().size} fields ordered")
    }

    @Test
    fun `every master mod precedes its dependents`() {
        val o = order()
        fun idx(s: String) = o.indexOf(s)
        // Each pair is (master, dependent) taken from the desktop GUI's own
        // grouping - the dependent is meaningless unless the master is set.
        val pairs = listOf(
            "baseStatisticsMod" to "baseStatsFollowEvolutions",
            "baseStatisticsMod" to "assignEvoStatsRandomly",
            "abilitiesMod" to "abilitiesFollowEvolutions",
            "abilitiesMod" to "banTrappingAbilities",
        )
        for ((master, dep) in pairs) {
            val m = idx(master); val d = idx(dep)
            assertTrue(m >= 0, "$master missing from the order")
            assertTrue(d >= 0, "$dep missing from the order")
            assertTrue(m < d, "$master (at $m) must precede $dep (at $d)")
        }
    }
}
