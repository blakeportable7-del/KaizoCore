package com.ironmonone.app

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * MainScreen.updateBadgeLayout for HeartGold and SoulSilver. SHOW_BOTH_BADGES is on by default
 * (MiscConstants.lua:82), so both rows show from the first step, Kanto's all unlit; our panel
 * waited for the first Kanto badge. With it off there is one row, and it moves from Johto to
 * Kanto once the League is beaten (leagueBeaten >= 3, Program.lua:583-586; MainScreen.lua:1285-1295);
 * the primary set only orders two rows.
 */
class HgssBadgeRowsTest {
    private val threeJohto = 0b111
    private val allJohtoOneKanto = 0xFF or (0b1 shl 8)

    @Test
    fun `both rows from the start, Johto first unless Kanto is the primary set`() {
        assertEquals(listOf(threeJohto to "HGSS_J", 0 to "HGSS_K"),
            hgssBadgeRows(threeJohto, showBoth = true, kantoFirst = false, leagueBeaten = false))
        assertEquals(listOf(0 to "HGSS_K", threeJohto to "HGSS_J"),
            hgssBadgeRows(threeJohto, showBoth = true, kantoFirst = true, leagueBeaten = false))
        assertEquals(listOf(0xFF to "HGSS_J", 1 to "HGSS_K"),
            hgssBadgeRows(allJohtoOneKanto, showBoth = true, kantoFirst = false, leagueBeaten = true), "the League does not move two rows")
    }

    @Test
    fun `one row is Johto until the League is beaten, then Kanto, whatever the primary set`() {
        assertEquals(listOf(0xFF to "HGSS_J"), hgssBadgeRows(allJohtoOneKanto, showBoth = false, kantoFirst = false, leagueBeaten = false))
        assertEquals(listOf(0xFF to "HGSS_J"), hgssBadgeRows(allJohtoOneKanto, showBoth = false, kantoFirst = true, leagueBeaten = false))
        assertEquals(listOf(1 to "HGSS_K"), hgssBadgeRows(allJohtoOneKanto, showBoth = false, kantoFirst = false, leagueBeaten = true))
        assertEquals(listOf(0 to "HGSS_K"), hgssBadgeRows(0xFF, showBoth = false, kantoFirst = false, leagueBeaten = true), "Kanto, unlit")
    }
}
