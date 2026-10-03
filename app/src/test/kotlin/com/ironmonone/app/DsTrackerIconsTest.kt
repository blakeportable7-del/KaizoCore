package com.ironmonone.app

import com.ironmonone.app.WalkingPals.Dex
import com.ironmonone.app.WalkingPals.Pack
import com.ironmonone.app.WalkingPals.Pal
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The DS tracker's cards animate from the Walking Pals sets by national number (2026-10-01; Blake: "if we get the
 * animation sprites for all pokemon, we can include the gen 4 and more animations on the trackers"), behind the same
 * two Tracker Setup switches as the GBA panel, and keep the still picture with the switch off, with no sheet, or for
 * an egg. Never another Pokemon's sheet: a DS game counts in national numbers, and Gen 3's own ids are not those.
 */
class DsTrackerIconsTest {
    private fun src(name: String) = File("src/main/kotlin/com/ironmonone/app/$name").readText().replace("\r\n", "\n")

    @Test
    fun `both DS cards hand the head block their national number, an egg none`() {
        val panel = src("NdsTrackerPanel.kt")
        val party = panel.substringAfter("private fun NdsPartyCard(").substringBefore("private fun NdsEnemyCard(")
        val enemy = panel.substringAfter("private fun NdsEnemyCard(").substringBefore("\n}\n")
        assertTrue("iconSpecies = if (m.isEgg) 0 else m.species,\n            iconDex = WalkingPals.Dex.NATIONAL," in party, "your card")
        assertTrue("iconSpecies = if (e.mon.isEgg) 0 else e.mon.species,\n            iconDex = WalkingPals.Dex.NATIONAL," in enemy, "the opponent's")
        // The still sprite (the player's ROM, else gen4sprites) is still handed over: the head block draws it whenever it
        // does not animate.
        assertTrue("sprite = sprite," in party && "sprite = sprite," in enemy)
        // No walking in battle, on DS as on GBA.
        assertTrue("androidx.compose.runtime.SideEffect { SpriteMotion.inBattle = state.inBattle }" in panel)
    }

    @Test
    fun `the head block animates only with the switch on and a sheet found, and walks only with walking on`() {
        val pc = src("PcTracker.kt")
        val head = pc.substringAfter("fun PcHeadBlock(").substringBefore("PcSprite(sprite)")
        assertTrue("WalkingPals.ready(iconCtx).let { ix -> remember(ix, iconSpecies, iconDex, iconLook) { if (iconSpecies > 0) ix?.find(iconSpecies, iconDex, iconLook) else null } }" in head)
        assertTrue("val animated = pal != null && TrackerOptions.animatedSprites &&" in head)
        assertTrue(pc.substringAfter("WalkingPalsIcon(pal, status, 1.rp, PcRef.ICON.rp)").trimStart().startsWith("if (!animated) PcSprite(sprite)"))
        assertTrue("val walk = TrackerOptions.spritesWalk && !SpriteMotion.inBattle && SpriteMotion.walking()" in src("WalkingPals.kt"))
    }

    @Test
    fun `Tracker Setup offers both switches on DS games, since the DS panel reads them now`() {
        val gear = src("TrackerGearDialog.kt")
        for (label in listOf("Animated Pok\\u00e9mon (Walking Pals)", "Allow sprites to walk")) {
            val line = gear.lines().single { "GearToggle(\"$label\"" in it }.trim()
            assertFalse("!ds" in line || "ds)" in line.substringBefore("GearToggle("), line)
        }
    }

    /** "never draw another Pokemon's sheet": every species a DS game has finds its own sheet, or none and the still picture. */
    @Test
    fun `every DS species finds its own sheet or none`() {
        val ix = ShippedPals.index
        var drawn = 0
        for (n in 1..649) {
            val pal = ix.find(n, Dex.NATIONAL) ?: continue
            drawn++
            val drawnAs = when (pal.pack) {
                Pack.GEN3 -> Favorites.nationalOf(pal.key.toInt())
                Pack.NATIONAL -> pal.key.toIntOrNull()
            }
            assertEquals(n, drawnAs, "national $n would draw $pal")
        }
        // Every Gen 4 Pokemon has one; Gen 5 all but the few nobody has drawn yet (natdex-map.tsv names them).
        assertTrue((387..493).all { ix.find(it, Dex.NATIONAL) != null }, "a Gen 4 Pokemon has no sheet")
        assertTrue(drawn >= 630, "only $drawn of 649")
        assertEquals(Pal(Pack.NATIONAL, "387"), ix.find(387, Dex.NATIONAL), "Turtwig, not Gen 3's 387 (Illumise)")
        assertEquals(Pal(Pack.GEN3, "392"), ix.find(280, Dex.NATIONAL), "Ralts, by Gen 3's own order")
        assertEquals(Pal(Pack.NATIONAL, "649"), ix.find(649, Dex.NATIONAL), "Genesect")
        assertNull(ix.find(564, Dex.NATIONAL), "Tirtouga has none: its still sprite stays")
    }
}
