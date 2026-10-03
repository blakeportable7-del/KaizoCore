package com.ironmonone.app

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * What the Play screen does differently on MaxDex 1.0. Its ids are Nat. Dex 1.2.1's to 1235 and part ways after, where
 * the 45 Legends Z-A megas sit: the icons there are MaxDex's own, the Walking Pals there are found by name (MaxDex's own
 * numbering, WalkingPals.Dex.MAX_DEX, since 2026-10-03), and Freeze-Dry hits Water hard, as its tracker extension has
 * it. The screen's own wiring is read from its source, the way the other Play screen tests read it.
 */
class MaxDexPlayTest {
    private val src = File("src/main/kotlin/com/ironmonone/app")
    private fun read(name: String) = File(src, name).readText().replace("\r\n", "\n")

    @Test
    fun `the Play screen's icons follow the game, MaxDex's own past 411`() {
        // PlayScreen's one change: the sprite loader names the game's set (GbaTracker.nameSet).
        assertTrue("PcAssets.gbaSprite(context, sp, trackerRef?.nameSet) else null" in read("PlayScreen.kt"))
        val pc = read("PcTracker.kt")
        assertTrue("if (nameSet == \"maxdex\" && species in 412..1280) load(context, \"gbasprites-maxdex/\$species.png\") else gbaSprite(context, species)" in pc)
        for (id in listOf(412, 1236, 1280)) assertTrue(File("src/main/assets/gbasprites-maxdex/$id.png").isFile, "$id")
    }

    @Test
    fun `Walking Pals are on for MaxDex, by its own numbering, and the other dexes are as they were`() {
        val ix = ShippedPals.index
        val pal = { pack: WalkingPals.Pack, key: String -> WalkingPals.Pal(pack, key) }
        // The ids MaxDex shares with Nat. Dex walk as there; past them, each Z-A Mega walks as the Nat. Dex one of its name.
        for (id in listOf(1, 25, 277, 412, 1032, 1050, 1235)) assertEquals(ix.find(id, WalkingPals.Dex.NAT_DEX), ix.find(id, WalkingPals.Dex.MAX_DEX), "$id")
        assertEquals(pal(WalkingPals.Pack.GEN3, "149"), ix.find(1236, WalkingPals.Dex.MAX_DEX), "Dragonite-M, not Nat. Dex's 1236")
        assertEquals(pal(WalkingPals.Pack.NATIONAL, "998"), ix.find(1280, WalkingPals.Dex.MAX_DEX), "Baxcalibur-M")
        assertNull(ix.find(1281, WalkingPals.Dex.MAX_DEX), "past MaxDex's last Pokemon")
        // A Nat. Dex build still finds Bulbasaur and Turtwig.
        assertNotNull(ix.find(1, WalkingPals.Dex.NAT_DEX))
        assertNotNull(ix.find(412, WalkingPals.Dex.NAT_DEX))
        // Both of the panel's cards number their icons the game's way, MaxDex in Play included.
        val panel = read("TrackerPanel.kt")
        assertEquals(2, Regex("""iconDex = WalkingPals\.trackerDex\(generation, speciesTotal, maxDex\)""").findAll(panel).count())
        assertTrue("maxDex: Boolean = maxDexInPlay(attempt)," in panel)
        assertEquals(2, Regex("""natDex = natDex, maxDex = maxDex\)""").findAll(panel).count(), "both move contexts carry it")
        // Prepare no longer says they are not on MaxDex.
        assertTrue(MaxDexInfo.lines.none { "Walking Pals" in it || "Play as your" in it }, MaxDexInfo.lines.last())
    }

    @Test
    fun `Play as your Pokemon is on for MaxDex, its lead counted its own way`() {
        // The lead in a MaxDex game says MaxDex's numbering, and its overworld is read out of its own code
        // (tracker-gba's OverworldScanTest has the real build).
        assertEquals(WalkingPals.Dex.MAX_DEX, SpriteLead.dexOf(com.ironmonone.tracker.GameMap.MAXDEX_FR_10))
        assertEquals(SpriteIsMeCopy.MAX_DEX, SpriteIsMeRunner.refusal(com.ironmonone.tracker.GameMap.MAXDEX_FR_10), "only when that finds nothing")
    }

    @Test
    fun `Freeze-Dry is super effective on Water on MaxDex, and only there`() {
        val ice = 15; val water = 11; val grass = 12
        val r = com.ironmonone.tracker.MoveRules
        assertEquals(2.0, r.effectiveness(578, ice, "SPE", listOf(water), natDex = true, maxDex = true))
        assertEquals(0.5, r.effectiveness(578, ice, "SPE", listOf(water), natDex = true, maxDex = false), "another build's move 578")
        assertEquals(1.0, r.effectiveness(578, ice, "SPE", listOf(water, ice), natDex = true, maxDex = true), "Water/Ice: 2 times a half")
        assertEquals(4.0, r.effectiveness(578, ice, "SPE", listOf(water, grass), natDex = true, maxDex = true))
        assertEquals(0.5, r.effectiveness(58, ice, "SPE", listOf(water), natDex = true, maxDex = true), "Ice Beam is still resisted")
        // Wired where the move rows and Calc Atk work it out.
        assertEquals(2, Regex("""natDex = ctx\.natDex, maxDex = ctx\.maxDex\)""").findAll(read("MoveDecor.kt")).count())
        assertTrue("maxDex = t.nameSet == \"maxdex\"," in read("CalcAtkScreen.kt"))
    }

    @Test
    fun `the log viewer finds MaxDex's shortened log names`() {
        // Through LogNames since rc34, which shows them as the tracker names them too (LogNamesTest).
        assertTrue("LogNames.of(tracker)," in read("LogViewer.kt"))
        assertTrue("for ((name, id) in t.logSpeciesIds()) add(name, id)" in read("LogNames.kt"))
    }
}
