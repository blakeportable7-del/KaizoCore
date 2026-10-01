package com.ironmonone.app

import com.ironmonone.app.WalkingPals.Dex
import com.ironmonone.app.WalkingPals.Pack
import com.ironmonone.app.WalkingPals.Pal
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The Walking Pals tables as shipped, the one lookup over both sets, and the frame and facing rules SpriteData and Input use. */
class WalkingPalsTest {
    private val table = ShippedPals.gen3
    private val ix = ShippedPals.index

    @Test
    fun `the shipped table covers Gen 1 to 3 with every sheet on disk`() {
        assertTrue(table.size >= 380, "only ${table.size} species")
        assertTrue((1..251).all { it.toString() in table }, "a Gen 1 or 2 species is missing")
        // Gengar (94): SpriteData.WalkingPals[94].
        val g = assertNotNull(table["94"])
        val idle = assertNotNull(g[WalkingPals.Anim.IDLE])
        assertEquals(listOf(40, 4, 3, 3, 3, 3, 3, 4), idle.durations.toList())
        assertEquals(32 to 40, idle.w to idle.h)
        for ((id, anims) in table) for (a in anims.keys) {
            assertTrue(File("src/main/assets/walkingpals/${a.key}/$id.png").isFile, "no sheet ${a.key}/$id")
        }
    }

    /** Phase 1 wrote the second set keyed by text: a national number, or "<national>-<form>". */
    @Test
    fun `keys are read as text, so the second set's forms load with its numbers`() {
        val t = WalkingPals.parse(sequenceOf(
            "# key\tanimation\tw\th\tx\ty\tdurations",
            "6-mega-x\tidle\t48\t56\t-7\t-1\t15,15,15,15",
            "741-pom-pom\twalk\t32\t40\t0\t4\t8,10",
            "387\tidle\t32\t40\t0\t4\t40,2",
            "../idle/25\tidle\t32\t32\t0\t0\t1",
            "6-Mega-X\tidle\t32\t32\t0\t0\t1",
        ))
        assertEquals(setOf("6-mega-x", "741-pom-pom", "387"), t.keys, "a key the tables never write is not read")
        val x = assertNotNull(t["6-mega-x"]?.get(WalkingPals.Anim.IDLE))
        assertEquals(listOf(48, 56, -7, -1), listOf(x.w, x.h, x.x, x.y))
        assertEquals(listOf(15, 15, 15, 15), x.durations.toList())
        // As shipped: every key of the second set, forms and all, with its sheets on disk.
        val nat = ShippedPals.national
        assertTrue(nat.size >= 700, "only ${nat.size} keys")
        assertTrue(nat.keys.count { '-' in it } >= 150, "the forms did not load")
        assertEquals(48 to 56, nat.getValue("6-mega-x").getValue(WalkingPals.Anim.IDLE).let { it.w to it.h })
        for ((key, anims) in nat) for (a in anims.keys) {
            assertTrue(ShippedPals.file(Pal(Pack.NATIONAL, key), a).isFile, "no sheet ${a.key}/$key")
        }
    }

    @Test
    fun `one lookup takes each caller's numbering, at each seam`() {
        // A retail Gen 3 game: its own ids, 1 to 411, and nothing past them (412 is its egg).
        assertEquals(Pal(Pack.GEN3, "25"), ix.find(25, Dex.GEN3))
        assertEquals(Pal(Pack.GEN3, "411"), ix.find(411, Dex.GEN3), "Chimecho")
        assertNull(ix.find(412, Dex.GEN3))
        // A Nat. Dex build: 1 to 411 as Gen 3's, then the expansion's ids through natdex-map.tsv.
        assertEquals(Pal(Pack.GEN3, "277"), ix.find(277, Dex.NAT_DEX), "Treecko")
        assertEquals(Pal(Pack.NATIONAL, "387"), ix.find(412, Dex.NAT_DEX), "Turtwig")
        assertEquals(Pal(Pack.NATIONAL, "1025"), ix.find(1050, Dex.NAT_DEX), "Pecharunt")
        // National numbers: 1 to 251 straight, Hoenn through Gen 3's own order, 387 on the second set.
        assertEquals(Pal(Pack.GEN3, "251"), ix.find(251, Dex.NATIONAL), "Celebi")
        assertEquals(Pal(Pack.GEN3, "277"), ix.find(252, Dex.NATIONAL), "Treecko")
        assertEquals(Pal(Pack.GEN3, "392"), ix.find(280, Dex.NATIONAL), "Ralts")
        assertEquals(Pal(Pack.GEN3, "410"), ix.find(386, Dex.NATIONAL), "Deoxys")
        assertEquals(Pal(Pack.GEN3, "411"), ix.find(358, Dex.NATIONAL), "Chimecho")
        assertEquals(Pal(Pack.NATIONAL, "387"), ix.find(387, Dex.NATIONAL), "Turtwig")
    }

    @Test
    fun `a Nat Dex form finds its own sheet, or the base one where the map says it looks the same`() {
        assertEquals(Pal(Pack.NATIONAL, "6-mega-x"), ix.find(1052, Dex.NAT_DEX), "Charizard-X")
        assertEquals(Pal(Pack.NATIONAL, "19-alola"), ix.find(1101, Dex.NAT_DEX), "Rattata-A")
        assertEquals(Pal(Pack.NATIONAL, "479-wash"), ix.find(1174, Dex.NAT_DEX), "Rotom-Wash")
        assertEquals(Pal(Pack.GEN3, "25"), ix.find(1157, Dex.NAT_DEX), "Partner Pikachu is drawn as Pikachu")
    }

    @Test
    fun `no sheet is null, never another Pokemon's`() {
        assertNull(ix.find(539, Dex.NAT_DEX), "Simisear: nobody has drawn it yet")
        assertNull(ix.find(514, Dex.NATIONAL), "Simisear by its national number")
        assertNull(ix.find(1053, Dex.NAT_DEX), "Charizard-Y's slot is empty")
        assertNull(ix.find(260, Dex.GEN3), "one of Gen 3's unused slots")
        assertNull(ix.find(1284, Dex.NAT_DEX), "a Nat. Dex build's egg")
        assertNull(ix.find(1285, Dex.NAT_DEX), "the Nat. Dex ghost stand-in")
        assertNull(ix.find(413, Dex.GEN3), "the retail ghost stand-in")
        assertNull(ix.find(1026, Dex.NATIONAL), "past the National Dex")
        for (d in Dex.entries) { assertNull(ix.find(0, d)); assertNull(ix.find(-5, d)) }
    }

    @Test
    fun `every sheet the Nat Dex map names is in its set's table and on disk`() {
        val map = ShippedPals.natDex
        assertEquals((412..1283).toSet(), map.keys, "the map covers the expansion's ids exactly")
        val named = map.values.filterNotNull()
        assertTrue(named.size >= 700, "only ${named.size} with a sheet")
        for (pal in named) {
            val sheets = assertNotNull(ix.sheets(pal), "$pal is not in its table")
            assertTrue(WalkingPals.Anim.IDLE in sheets, "$pal has no idle sheet")
            for (a in sheets.keys) assertTrue(ShippedPals.file(pal, a).isFile, "no file for $pal ${a.key}")
        }
    }

    /**
     * Play as your Pokemon hands the emulator side one frame at most 128 x 128 (sprite_core.h's kMaxSpriteDim). A few
     * second-set sheets have bigger frames: each frame of those, cut to what shows (SpriteArt.placeFrame), fits whole,
     * not a shown pixel lost, so every Pokemon the picker lists can be drawn.
     */
    @Test
    fun `every frame of every sheet fits the overworld once cut to what shows`() {
        var big = 0
        for ((pack, set) in listOf(Pack.GEN3 to ShippedPals.gen3, Pack.NATIONAL to ShippedPals.national)) {
            for ((key, anims) in set) for ((anim, sheet) in anims) {
                if (sheet.w <= SpriteArt.MAX_FRAME && sheet.h <= SpriteArt.MAX_FRAME) continue
                big++
                val img = ShippedPals.pixels(ShippedPals.file(Pal(pack, key), anim))
                for (row in 0 until img.h / sheet.h) for (col in 0 until img.w / sheet.w) {
                    val frame = SpriteArt.crop(img, col * sheet.w, row * sheet.h, sheet.w, sheet.h)
                    val placed = SpriteArt.placeFrame(frame, sheet.x, sheet.y)
                    val what = "$key ${anim.key} row $row frame $col"
                    assertTrue(placed.pixels.w <= SpriteArt.MAX_FRAME && placed.pixels.h <= SpriteArt.MAX_FRAME, what)
                    assertEquals(frame.argb.count { (it ushr 24) != 0 }, placed.pixels.argb.count { (it ushr 24) != 0 }, "$what lost a pixel")
                }
            }
        }
        assertTrue(big > 0, "no sheet has a big frame any more: this test checks nothing")
    }

    @Test
    fun `the tracker panel numbers its species by the game`() {
        assertEquals(Dex.NATIONAL, WalkingPals.trackerDex(generation = 1, speciesTotal = 151))
        assertEquals(Dex.NATIONAL, WalkingPals.trackerDex(generation = 2, speciesTotal = 251))
        assertEquals(Dex.GEN3, WalkingPals.trackerDex(generation = 3, speciesTotal = 411))
        assertEquals(Dex.NAT_DEX, WalkingPals.trackerDex(generation = 3, speciesTotal = 1283))
    }

    @Test
    fun `frames follow their durations, and faint stops on its last`() {
        val s = WalkingPals.Sheet(32, 40, 0, 0, intArrayOf(40, 6, 6))
        assertEquals(0, s.frameAt(0, loop = true))
        assertEquals(0, s.frameAt(39, loop = true))
        assertEquals(1, s.frameAt(40, loop = true))
        assertEquals(2, s.frameAt(51, loop = true))
        assertEquals(0, s.frameAt(52, loop = true))
        assertEquals(2, s.frameAt(500, loop = false))
    }

    @Test
    fun `facing follows Input getSpriteFacingDirection`() {
        assertEquals(0, WalkingPals.facingRow(up = false, down = false, left = false, right = false))
        assertEquals(0, WalkingPals.facingRow(up = false, down = true, left = false, right = false))
        assertEquals(1, WalkingPals.facingRow(up = false, down = true, left = false, right = true))
        assertEquals(2, WalkingPals.facingRow(up = false, down = false, left = false, right = true))
        assertEquals(4, WalkingPals.facingRow(up = true, down = false, left = false, right = false))
        assertEquals(6, WalkingPals.facingRow(up = false, down = false, left = true, right = false))
        assertEquals(5, WalkingPals.facingRow(up = true, down = false, left = true, right = false))
    }
}
