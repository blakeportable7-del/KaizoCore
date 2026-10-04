package com.ironmonone.app

import com.ironmonone.app.WalkingPals.Anim
import com.ironmonone.app.WalkingPals.Dex
import com.ironmonone.app.WalkingPals.Look
import com.ironmonone.app.WalkingPals.Pack
import com.ironmonone.app.WalkingPals.Pal
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Blake, 2026-10-04: "i want iron boulder, find the missing sprites", then "use the ones i gave you if they fill in sprite
 * collabs gap". DarkusShadow's overworld sprites ship as a third set, walkingpals-darkus/, written by
 * convert_walking_pals_nat.py (DARKUSSHADOW, tested by convert_walking_pals_darkus_test.py), and only where Sprite Collab
 * has no sheet of its own.
 */
class WalkingPalsDarkusTest {
    private val ix = ShippedPals.index

    @Test
    fun `Iron Boulder and Iron Crown walk on DarkusShadow's sheets, in eight facings, idle and walk`() {
        for ((id, key) in listOf(1047 to "1022", 1048 to "1023", 1027 to "1002", 1257 to "687-mega")) {
            val pal = Pal(Pack.DARKUS, key)
            assertEquals(pal, ix.find(id, Dex.NAT_DEX), "Nat. Dex $id")
            val sheets = assertNotNull(ix.sheets(pal), "$key is in walkingpals-darkus.tsv")
            assertEquals(setOf(Anim.IDLE, Anim.WALK), sheets.keys, "$key: idle and walk; sleep and faint fall back to idle")
            val idle = sheets.getValue(Anim.IDLE)
            val walk = sheets.getValue(Anim.WALK)
            assertEquals(listOf(32, 32), idle.durations.toList(), "$key idle: his two grounded poses")
            assertEquals(listOf(8, 8, 8, 8), walk.durations.toList(), "$key walk: his four frames")
            assertEquals(idle.x to idle.y, walk.x to walk.y, "$key: one frame grid, one anchor")
            for ((anim, s) in sheets) {
                val img = ShippedPals.pixels(ShippedPals.file(pal, anim))
                assertEquals(s.w * s.durations.size to s.h * 8, img.w to img.h, "$key ${anim.key}: every frame, eight facings")
            }
        }
        // His shinies are not public at full size: a shiny one walks in plain colors, never on another set's colors.
        val shiny = ix.find(1047, Dex.NAT_DEX, Look(shiny = true))
        assertEquals(Pal(Pack.DARKUS, "1022"), shiny)
        assertEquals(WalkingPals.Art("walkingpals-darkus/walk/1022.png"), ix.art(assertNotNull(shiny), Anim.WALK))
        // MaxDex finds them by the same Nat. Dex rows.
        assertEquals(Pal(Pack.DARKUS, "1022"), ix.find(1047, Dex.MAX_DEX))
    }

    @Test
    fun `Sprite Collab's sheet always wins, and a form with neither walks as its base`() {
        val rows = File("src/main/assets/walkingpals-nat/natdex-map.tsv").readLines(Charsets.UTF_8)
            .filter { it.isNotBlank() && !it.startsWith("#") }.map { it.split('\t') }
        val darkus = rows.filter { it[4] == Pack.DARKUS.dir }
        assertTrue(darkus.size >= 19, "only ${darkus.size} rows")
        for (c in darkus) {
            val own = c[2] + (if (c[3].isEmpty()) "" else "-" + c[3])
            assertFalse(own in ShippedPals.national, "${c[0]} ${c[1]}: Sprite Collab has $own, so it must win")
            assertTrue(c[5] in ShippedPals.darkus, "${c[0]} ${c[1]}: ${c[5]} is shipped")
        }
        // Sheets Blake pasted that Sprite Collab already draws stay Sprite Collab's.
        for ((id, key) in listOf(1013 to "988", 1030 to "1005", 1045 to "1020", 1042 to "1017", 1234 to "1024-terastal", 1152 to "713-hisui", 1041 to "1016")) {
            assertEquals(Pal(Pack.NATIONAL, key), ix.find(id, Dex.NAT_DEX), "Nat. Dex $id")
        }
        assertEquals(Pal(Pack.DARKUS, "931"), ix.find(1237, Dex.NAT_DEX), "White Squawkabilly walks as Squawkabilly")
    }

    @Test
    fun `every DarkusShadow sheet is credited, and NOTICE, About and Licenses name him`() {
        val credits = File("src/main/assets/walkingpals-darkus/credits.tsv").readLines(Charsets.UTF_8)
            .filter { it.isNotBlank() && !it.startsWith("#") }.map { it.split('\t') }.associateBy { it[0] }
        for (key in ShippedPals.darkus.keys) {
            val c = assertNotNull(credits[key], "$key has a credits row")
            assertTrue("DarkusShadow" in c[3], "$key: ${c[3]}")
            assertTrue(c[2].startsWith("https://www.deviantart.com/darkusshadow/art/"), "$key: its post, ${c[2]}")
        }
        assertTrue("TyranitarDark" in credits.getValue("986")[3], "Brute Bonnet's original sprite")
        assertTrue("princess-phoenix" in credits.getValue("687-mega")[3], "Mega Malamar's original sprite")
        val notice = File("../NOTICE").readText()
        val about = File("src/main/kotlin/com/ironmonone/app/AboutScreen.kt").readText()
        val licences = File("src/main/kotlin/com/ironmonone/app/Licences.kt").readText()
        for (who in listOf("DarkusShadow", "TyranitarDark", "princess-phoenix")) {
            assertTrue(who in notice, "NOTICE names $who")
            assertTrue(who in about, "About names $who")
        }
        assertTrue("CreditLink(\"https://www.deviantart.com/darkusshadow\")" in about, "About links his gallery")
        assertTrue("DarkusShadow-Free-Use" in licences, "Licenses lists his terms")
        assertTrue(File("src/main/assets/licenses/DarkusShadow-Free-Use.txt").isFile, "and ships them")
    }
}
