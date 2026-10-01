package com.ironmonone.app

/**
 * The badge rows Gold, Silver and Crystal draw, as (set, eight bits): all
 * sixteen badges, always, Johto's then Kanto's. The Gen 2 reference's
 * Program.updateBadgesObtained walks index 1..16 whatever has been earned
 * (Program.lua:1100-1112), Kanto from wKantoBadges, which GbcTracker puts in
 * bits 8-15; the Kanto art is GSC_K, Johto's GSC (PcAssets.badgePath). Only
 * the Johto row was drawn until 2026-09-29.
 *
 * Its own file so a unit test can call it: PcTracker.kt's file class loads an
 * Android Typeface when it is first touched.
 */
internal fun gscBadgeRows(badges: Int): List<Pair<String, Int>> =
    listOf("GSC_J" to (badges and 0xFF), "GSC_K" to ((badges shr 8) and 0xFF))
