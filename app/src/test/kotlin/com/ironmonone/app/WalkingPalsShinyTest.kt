package com.ironmonone.app

import com.ironmonone.app.WalkingPals.Anim
import com.ironmonone.app.WalkingPals.Dex
import com.ironmonone.app.WalkingPals.Look
import com.ironmonone.app.WalkingPals.Pack
import com.ironmonone.app.WalkingPals.Pal
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The shinies (Blake, 2026-10-03: "If they are shiny you should be able to play as shiny"): the one lookup that finds a
 * shiny or, with none, the plain sheets; what the phone draws for each (WalkingPals.bitmap's choice, [WalkingPals.Index.art]);
 * and the tables as shipped. The converter proves each color map against Sprite Collab's own shiny sheet
 * (tools/trainer-data/convert_walking_pals_shiny_test.py); this holds the app to what it shipped.
 */
class WalkingPalsShinyTest {
    private val ix = ShippedPals.index

    private fun opaque(p: ArtPixels) = p.argb.map { (it ushr 24) == 0xFF }

    /** The shiny lookup: take it out of Index.look and this goes red. */
    @Test
    fun `a shiny finds its shiny, and a Pokemon with no shiny its plain sheets`() {
        assertEquals(Pal(Pack.GEN3, "25", shiny = true), ix.find(25, Dex.GEN3, Look(shiny = true)), "Pikachu, a recolor")
        assertEquals(Pal(Pack.GEN3, "1", shiny = true), ix.find(1, Dex.GEN3, Look(shiny = true)), "Bulbasaur, sheets of its own")
        assertEquals(Pal(Pack.NATIONAL, "387", shiny = true), ix.find(412, Dex.NAT_DEX, Look(shiny = true)), "Turtwig on a Nat. Dex build")
        assertEquals(Pal(Pack.GEN3, "25"), ix.find(25, Dex.GEN3, Look()), "not shiny: the plain sheets")
        assertEquals(Pal(Pack.GEN3, "25"), ix.find(25, Dex.GEN3), "the lookup as it was")
        // Sprite Collab has no shiny Karrablast: the plain one, silently.
        assertEquals(Pal(Pack.NATIONAL, "588"), ix.find(588, Dex.NATIONAL, Look(shiny = true)))
        assertNull(ix.find(539, Dex.NAT_DEX, Look(shiny = true)), "no sheet at all is still none")
        // The sheets a shiny animates by: its own rows where it has sheets of its own.
        val megaX = Pal(Pack.NATIONAL, "6-mega-x")
        assertEquals(56, ix.sheets(megaX)!!.getValue(Anim.IDLE).h)
        assertEquals(48, ix.sheets(megaX.copy(shiny = true))!!.getValue(Anim.IDLE).h, "Mega Charizard X's shiny is drawn in 48 by 48 frames")
    }

    @Test
    fun `a shiny is drawn in its own colors over the plain sheet's pixels, each animation as it ships`() {
        val pikachu = ix.find(25, Dex.GEN3, Look(shiny = true))!!
        for (a in Anim.entries) {
            val art = ix.art(pikachu, a)
            assertEquals("walkingpals/${a.key}/25.png", art.path, "a recolor reads the plain sheet")
            assertNotNull(art.colors, "and recolors it")
            val plain = ShippedPals.drawn(Pal(Pack.GEN3, "25"), a)
            val shiny = ShippedPals.drawn(pikachu, a)
            assertEquals(plain.w to plain.h, shiny.w to shiny.h)
            assertEquals(opaque(plain), opaque(shiny), "the same pixels clear and opaque")
            assertTrue(plain.argb.indices.any { plain.argb[it] != shiny.argb[it] }, "${a.key}: in other colors")
        }
        // A shiny that is not a recolor is a sheet of its own.
        val bulbasaur = Pal(Pack.GEN3, "1", shiny = true)
        assertEquals(WalkingPals.Art("walkingpals/shiny/idle/1.png"), ix.art(bulbasaur, Anim.IDLE))
        assertTrue(ShippedPals.drawn(bulbasaur, Anim.IDLE).argb.contentEquals(ShippedPals.drawn(Pal(Pack.GEN3, "1"), Anim.IDLE).argb).not())
        // Both in one Pokemon: Castform's idle and walk are recolors, its sleep a sheet of its own.
        val castform = Pal(Pack.GEN3, "385", shiny = true)
        assertNotNull(ix.art(castform, Anim.WALK).colors)
        assertEquals("walkingpals/shiny/sleep/385.png", ix.art(castform, Anim.SLEEP).path)
        // An animation Sprite Collab has no shiny for is the plain one: Arbok's faint.
        val arbok = ix.find(24, Dex.GEN3, Look(shiny = true))!!
        assertTrue(arbok.shiny)
        assertNotNull(ix.art(arbok, Anim.IDLE).colors)
        assertEquals(WalkingPals.Art("walkingpals/faint/24.png"), ix.art(arbok, Anim.FAINT))
        // Not shiny: always the plain file, as it is.
        assertEquals(WalkingPals.Art("walkingpals/idle/25.png"), ix.art(Pal(Pack.GEN3, "25"), Anim.IDLE))
    }

    /** A map that missed a color would leave a plain pixel in the shiny: every opaque color of each sheet is in its map. */
    @Test
    fun `every color map covers every color of its plain sheet, and every sheet of its own is on disk`() {
        var maps = 0
        var sheets = 0
        for (pack in Pack.entries) {
            val shinies = ShippedPals.shinies.getValue(pack)
            val plainTable = if (pack == Pack.GEN3) ShippedPals.gen3 else ShippedPals.national
            for ((key, anims) in shinies.colors) for ((a, colors) in anims) {
                val plain = assertNotNull(plainTable[key]?.get(a), "$key ${a.key}: a map for a sheet that is not there")
                val px = ShippedPals.pixels(ShippedPals.file(Pal(pack, key), a))
                assertTrue(px.w >= plain.w && px.h >= plain.h)
                val missing = px.argb.filter { (it ushr 24) == 0xFF }.map { it and 0xFFFFFF }.toSet().filter { java.util.Arrays.binarySearch(colors.from, it) < 0 }
                assertEquals(emptyList(), missing.map { "%06x".format(it) }, "$key ${a.key} (${pack.dir})")
                assertTrue(px.argb.all { (it ushr 24) == 0 || (it ushr 24) == 0xFF }, "$key ${a.key}: a recolor has no half-clear pixels")
                maps++
            }
            for ((key, anims) in shinies.sheets) for ((a, row) in anims) {
                val plain = assertNotNull(plainTable[key]?.get(a), "$key ${a.key}: a shiny sheet for a sheet that is not there")
                val f = ShippedPals.asset(Pal(pack, key).shinyPath(a))
                assertTrue(f.isFile, "no file $f")
                val px = ShippedPals.pixels(f)
                assertTrue(px.w >= row.w * row.durations.size && px.h >= row.h, "$key ${a.key}: frames past the sheet")
                assertEquals(px.h / row.h, ShippedPals.pixels(ShippedPals.file(Pal(pack, key), a)).h / plain.h, "$key ${a.key}: as many facings as the plain one")
                sheets++
            }
        }
        // As converted at d2ceb96254fb: 840 + 1528 maps and 372 + 863 sheets of their own.
        assertTrue(maps >= 2300, "only $maps color maps")
        assertTrue(sheets >= 1200, "only $sheets sheets of their own")
    }

    /**
     * Play as your Pokemon draws a shiny's sheets of its own as it draws the plain ones (WalkingPalsTest's every frame
     * fits): the emulator side takes 128 x 128 at most, and each frame cut to what shows fits whole.
     */
    @Test
    fun `every frame of a shiny sheet of its own fits the overworld once cut to what shows`() {
        var big = 0
        for (pack in Pack.entries) for ((key, anims) in ShippedPals.shinies.getValue(pack).sheets) for ((anim, sheet) in anims) {
            if (sheet.w <= SpriteArt.MAX_FRAME && sheet.h <= SpriteArt.MAX_FRAME) continue
            big++
            val img = ShippedPals.pixels(ShippedPals.asset(Pal(pack, key).shinyPath(anim)))
            for (row in 0 until img.h / sheet.h) for (col in 0 until img.w / sheet.w) {
                val frame = SpriteArt.crop(img, col * sheet.w, row * sheet.h, sheet.w, sheet.h)
                val placed = SpriteArt.placeFrame(frame, sheet.x, sheet.y)
                val what = "$key ${anim.key} row $row frame $col"
                assertTrue(placed.pixels.w <= SpriteArt.MAX_FRAME && placed.pixels.h <= SpriteArt.MAX_FRAME, what)
                assertEquals(frame.argb.count { (it ushr 24) != 0 }, placed.pixels.argb.count { (it ushr 24) != 0 }, "$what lost a pixel")
            }
        }
        assertTrue(big > 0, "no shiny sheet has a big frame any more: this test checks nothing")
    }

    @Test
    fun `nearly every Pokemon with a walking sprite has its shiny`() {
        val gen3 = ShippedPals.gen3.keys.filter { it.toInt() !in 252..276 }
        val withShiny = gen3.count { ShippedPals.shinies.getValue(Pack.GEN3).has(it) }
        assertEquals(gen3.size, withShiny, "every Gen 1 to 3 Pokemon")
        val nat = ShippedPals.national.keys
        val natShiny = nat.count { ShippedPals.shinies.getValue(Pack.NATIONAL).has(it) }
        assertTrue(natShiny >= nat.size - 20, "$natShiny of ${nat.size}")
    }

    @Test
    fun `a recolor swaps opaque colors only, and leaves a color the map lacks`() {
        val colors = WalkingPals.ShinyColors(intArrayOf(0x000000, 0x102030, 0xF8F8F8), intArrayOf(0x000000, 0xA0B0C0, 0x101010))
        val px = intArrayOf(0xFF102030.toInt(), 0x00102030, 0x80102030.toInt(), 0xFFF8F8F8.toInt(), 0xFF123456.toInt(), 0xFF000000.toInt())
        colors.apply(px)
        assertContentEquals(intArrayOf(0xFFA0B0C0.toInt(), 0x00102030, 0x80102030.toInt(), 0xFF101010.toInt(), 0xFF123456.toInt(), 0xFF000000.toInt()), px)
    }

    @Test
    fun `a color map is read whole or not at all`() {
        val t = WalkingPals.parseShinyColors(sequenceOf(
            "# key\tanimations\tcolors",
            "25\tidle,walk\t000000=000000 f8d030=f8b800",
            "26\tidle\t000000=000000 f8d030",
            "27\tidle\t000000=000000 000000=101010",
            "28\tsleep,swim\t102030=405060",
            "../x\tidle\t000000=000000",
            "29\tidle\tGGGGGG=000000",
        ))
        assertEquals(setOf("25", "28"), t.keys)
        assertEquals(setOf(Anim.IDLE, Anim.WALK), t.getValue("25").keys)
        assertContentEquals(intArrayOf(0x000000, 0xF8D030), t.getValue("25").getValue(Anim.IDLE).from)
        assertContentEquals(intArrayOf(0x000000, 0xF8B800), t.getValue("25").getValue(Anim.WALK).to)
        assertEquals(setOf(Anim.SLEEP), t.getValue("28").keys, "an animation it does not know is left out")
    }
}
