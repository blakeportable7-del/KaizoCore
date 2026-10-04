package com.ironmonone.app

import com.ironmonone.app.WalkingPals.Dex
import com.ironmonone.app.WalkingPals.Look
import com.ironmonone.app.WalkingPals.Pack
import com.ironmonone.app.WalkingPals.Pal
import com.ironmonone.tracker.EnemyInfo
import com.ironmonone.tracker.EnemyPartyMon
import com.ironmonone.tracker.PokemonDecoder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The forms the original games draw (Blake, 2026-10-03: "And forms sprites"): Unown's letter by each generation's own
 * formula, Deoxys in its game's form, Castform Normal on the overworld, and the sheets each finds.
 */
class PalFormsTest {
    private val F = PalForms
    private val ix = ShippedPals.index

    /** RC35-NOTICED rows 42 and 43: a DS Pokemon walks, and shows on the game over screen, in its own form. */
    @Test
    fun `a DS form with a walking sheet of its own walks in it`() {
        fun pal(species: Int, form: Int, shiny: Boolean = false) = ix.find(species, Dex.NATIONAL, F.ds(species, form, shiny))
        assertEquals(Pal(Pack.NATIONAL, "479-wash"), pal(479, 2), "Wash Rotom")
        assertEquals(Pal(Pack.NATIONAL, "479-mow"), pal(479, 5), "Mow Rotom")
        assertEquals(Pal(Pack.NATIONAL, "487-origin"), pal(487, 1), "Giratina Origin")
        assertEquals(Pal(Pack.NATIONAL, "492-sky"), pal(492, 1), "Shaymin Sky")
        assertEquals(Pal(Pack.NATIONAL, "386-speed"), pal(386, 3), "Deoxys Speed")
        assertEquals(Pal(Pack.NATIONAL, "413-trash"), pal(413, 2), "Trash Wormadam")
        assertEquals(Pal(Pack.NATIONAL, "646-black"), pal(646, 2), "Black Kyurem")
        assertEquals(Pal(Pack.NATIONAL, "201-b"), pal(201, 1), "Unown B")
        assertEquals(Pal(Pack.NATIONAL, "201-question"), pal(201, 27), "Unown ?")
        assertEquals(Look(shiny = true), F.ds(479, 0, true), "the first form is the species")
        assertEquals(Look(), F.ds(422, 1, false), "East Sea Shellos has no sheet: the species")
        assertEquals(Look(), F.ds(479, 9, false), "a form past the list: the species")
        // Both DS cards and the game over screen pass the form; none of them decodes a picture during composition.
        val panel = java.io.File("src/main/kotlin/com/ironmonone/app/NdsTrackerPanel.kt").readText()
        assertTrue("iconLook = PalForms.ds(m.species, m.form, m.shiny)," in panel && "iconLook = PalForms.ds(e.mon.species, e.mon.form, e.mon.shiny)," in panel)
        val host = java.io.File("src/main/kotlin/com/ironmonone/app/GameOverHost.kt").readText()
        assertTrue("it.mon.shiny, form = it.mon.form)" in host && "rememberDsPicture(m.species, m.form, m.shiny)" in host)
        val log = java.io.File("src/main/kotlin/com/ironmonone/app/DsLogViewer.kt").readText()
        assertTrue("withContext(Dispatchers.IO) { DsPictures.load(ctx, id, 0, false) }" in log)
        for (src in listOf(panel, host, log)) assertTrue("PcAssets.dsSprite(" !in src && "RomFormSprites.sprite(" !in src)
    }

    /** pokeemerald's GET_UNOWN_LETTER: bits 0-1 of each personality byte, the top byte's highest. */
    @Test
    fun `Unown's Gen 3 letter is two bits from each byte of its personality`() {
        assertEquals(0, F.unownLetterGen3(0), "A")
        assertEquals(1, F.unownLetterGen3(0x00000001), "B")
        assertEquals(4, F.unownLetterGen3(0x00000100), "E: the second byte counts four")
        assertEquals(16, F.unownLetterGen3(0x00010000), "Q: the third, sixteen")
        assertEquals(64 % 28, F.unownLetterGen3(0x01000000), "the top byte, 64, past the 28 letters")
        assertEquals(26, F.unownLetterGen3(0x00010202), "!")
        assertEquals(27, F.unownLetterGen3(0x00010203), "?")
        assertEquals(255 % 28, F.unownLetterGen3(0xFFFFFFFFL))
        assertEquals(0, F.unownLetterGen3(0xFCFCFCFCL), "the other bits do not count")
    }

    /** pokecrystal's GetUnownLetter: the middle two bits of each DV, Attack's highest, divided by 10. */
    @Test
    fun `Unown's Gen 2 letter is the middle bits of its DVs, and a shiny Unown is an I or a V`() {
        assertEquals(0, F.unownLetterGen2(0, 0, 0, 0), "A")
        assertEquals(25, F.unownLetterGen2(15, 15, 15, 15), "Z: 255 / 10")
        assertEquals(0, F.unownLetterGen2(9, 9, 9, 9), "the outer bits do not count: 9 is 1001")
        assertEquals(20, F.unownLetterGen2(6, 2, 0, 0), "U: Attack 6 gives 192, Defense 2 gives 16, and 208 over 10 is 20")
        assertEquals(0, F.unownLetterGen2(0, 0, 4, 2), "8 and 1 make 9, still A")
        // The famous one: a shiny's Defense, Speed and Special DVs are 10, its Attack has bit 1 set, so only I and V.
        val shinyLetters = (0..15).filter { (it and 2) != 0 }.map { F.unownLetterGen2(it, 10, 10, 10) }.toSet()
        assertEquals(setOf(8, 21), shinyLetters, "I and V")
        // By the DV word as the tracker reads it, and shiny by the game's own check.
        val v = F.gen2(PalForms.UNOWN, 0xEAAA)
        assertEquals(Look("201-v", shiny = true), v)
        assertEquals(Look("201-i", shiny = true), F.gen2(PalForms.UNOWN, 0x2AAA))
        assertEquals(Look(null, shiny = false), F.gen2(PalForms.UNOWN, 0x0000), "Unown A, not shiny")
        assertEquals(Look(null, shiny = true), F.gen2(25, 0xEAAA), "a shiny Pikachu has no form")
        assertEquals(Look(), F.gen2(PalForms.UNOWN, -1), "no DVs read")
    }

    @Test
    fun `every letter is Sprite Collab's form, drawn with its shiny`() {
        assertNull(F.unownForm(0), "A is the species' own sheet")
        assertEquals("201-b", F.unownForm(1)); assertEquals("201-z", F.unownForm(25))
        assertEquals("201-exclamation", F.unownForm(26)); assertEquals("201-question", F.unownForm(27))
        for (letter in 1..27) {
            val key = F.unownForm(letter)!!
            assertTrue(key in ShippedPals.national, "$key ships")
            assertTrue(ShippedPals.shinies.getValue(Pack.NATIONAL).has(key), "$key's shiny ships")
        }
    }

    @Test
    fun `Unown walks as its letter, in a retail game and a Nat Dex build alike`() {
        val b = F.gen3(PalForms.UNOWN, Dex.GEN3, 0x00000001, shiny = false, routeVersion = "firered")
        assertEquals(Look("201-b"), b)
        assertEquals(Pal(Pack.NATIONAL, "201-b"), ix.find(PalForms.UNOWN, Dex.GEN3, b))
        assertEquals(Pal(Pack.NATIONAL, "201-question", shiny = true), ix.find(PalForms.UNOWN, Dex.NAT_DEX, F.gen3(PalForms.UNOWN, Dex.NAT_DEX, 0x00010203, true, "emerald")))
        // A is Ironmon-Tracker's own Unown.
        assertEquals(Pal(Pack.GEN3, "201"), ix.find(PalForms.UNOWN, Dex.GEN3, F.gen3(PalForms.UNOWN, Dex.GEN3, 0, false, "ruby")))
        // A DS game's species are national numbers, and its forms are not read here.
        assertNull(F.gen3(PalForms.UNOWN, Dex.NATIONAL, 1, false, "").form)
        // A form with no sheet is the species' own: never another Pokemon's, never nothing.
        assertEquals(Pal(Pack.GEN3, "25"), ix.find(25, Dex.GEN3, Look("25-not-drawn")))
    }

    /** pret: pokeruby draws Normal, pokefirered Attack (FireRed) and Defense (LeafGreen), pokeemerald Speed. */
    @Test
    fun `Deoxys walks in its game's form`() {
        fun deoxys(version: String, dex: Dex = Dex.GEN3) = ix.find(PalForms.DEOXYS_GEN3, dex, F.gen3(PalForms.DEOXYS_GEN3, dex, 0, false, version))
        assertEquals(Pal(Pack.NATIONAL, "386-attack"), deoxys("firered"))
        assertEquals(Pal(Pack.NATIONAL, "386-defense"), deoxys("leafgreen"))
        assertEquals(Pal(Pack.NATIONAL, "386-speed"), deoxys("emerald"))
        assertEquals(Pal(Pack.GEN3, "410"), deoxys("ruby"))
        assertEquals(Pal(Pack.GEN3, "410"), deoxys("sapphire"))
        assertEquals(Pal(Pack.GEN3, "410"), deoxys(""), "a game it cannot tell: Normal")
        // A Nat. Dex build has Deoxys-A, -D and -S as Pokemon of their own, so its Deoxys is the Normal one.
        assertEquals(Pal(Pack.GEN3, "410"), deoxys("firered", Dex.NAT_DEX))
        assertEquals(Pal(Pack.NATIONAL, "386-attack"), ix.find(1165, Dex.NAT_DEX), "Deoxys-A")
        // Shiny, in its form.
        assertEquals(Pal(Pack.NATIONAL, "386-speed", shiny = true), ix.find(PalForms.DEOXYS_GEN3, Dex.GEN3, F.gen3(PalForms.DEOXYS_GEN3, Dex.GEN3, 0, true, "emerald")))
    }

    @Test
    fun `Castform walks in its Normal form`() {
        for (id in PalForms.CASTFORM_WEATHER) assertEquals(PalForms.CASTFORM_GEN3, F.overworld(id, Dex.NAT_DEX), "Nat. Dex $id")
        assertEquals(PalForms.CASTFORM_GEN3, F.overworld(PalForms.CASTFORM_GEN3, Dex.GEN3))
        assertEquals(1162, F.overworld(1162, Dex.NATIONAL), "a national number 1162 is not Castform")
        assertEquals(25, F.overworld(25, Dex.NAT_DEX))
        assertEquals(Pal(Pack.GEN3, "385"), ix.find(F.overworld(1163, Dex.NAT_DEX), Dex.NAT_DEX))
        assertNull(F.gen3(PalForms.CASTFORM_GEN3, Dex.GEN3, 0x12345678, false, "emerald").form)
    }

    private fun mon(species: Int, pid: Long, shiny: Boolean, ivs: List<Int> = List(6) { 0 }) = PokemonDecoder.Mon(
        pid = pid, level = 5, nickname = "", species = species, heldItem = 0, friendship = 0, moves = listOf(1, 0, 0, 0), pp = listOf(35, 0, 0, 0),
        ivs = ivs, evs = List(6) { 0 }, ppUps = List(4) { 0 }, abilitySlot = 0, nature = 0, shiny = shiny, status = 0, curHp = 20, maxHp = 20,
        atk = 10, def = 10, spe = 10, spAtk = 10, spDef = 10,
    )

    private fun enemy(species: Int, pid: Long = 0, dvs: Int = -1) =
        EnemyInfo(species, "?", 5, 20, 20, 0, 0, null, emptyList(), pid = pid, dvs = dvs)

    @Test
    fun `the tracker card draws each Pokemon as its game does`() {
        // Your own, by generation.
        assertEquals(Look("201-b", shiny = true), F.ofMon(mon(PalForms.UNOWN, 1, true), 3, Dex.GEN3, "firered"))
        assertEquals(Look("386-defense"), F.ofMon(mon(PalForms.DEOXYS_GEN3, 0, false), 3, Dex.GEN3, "leafgreen"))
        // Gen 2: the tracker keeps the DVs as HP, Attack, Defense, Speed, Special (GbcTracker.partyMon).
        assertEquals(Look("201-v", shiny = true), F.ofMon(mon(PalForms.UNOWN, 0, true, listOf(0, 14, 10, 10, 10, 10)), 2, Dex.NATIONAL, ""))
        assertEquals(Look(), F.ofMon(mon(25, 0, false), 1, Dex.NATIONAL, ""), "Gen 1: no shinies, no forms")
        // The opponent: shiny when its party slot, found by personality, is.
        val party = listOf(EnemyPartyMon(0, 25, 5, true, pid = 99, shiny = true), EnemyPartyMon(1, 25, 5, true, pid = 7, shiny = false))
        assertEquals(Look(shiny = true), F.ofEnemy(enemy(25, pid = 99), party, 3, Dex.GEN3, "emerald"))
        assertEquals(Look(shiny = false), F.ofEnemy(enemy(25, pid = 7), party, 3, Dex.GEN3, "emerald"))
        assertEquals(Look(shiny = false), F.ofEnemy(enemy(25, pid = 0), party.map { it.copy(pid = 0) }, 3, Dex.GEN3, "emerald"), "no personality read: not shiny")
        assertEquals(Look("201-e"), F.ofEnemy(enemy(PalForms.UNOWN, pid = 0x100), emptyList(), 3, Dex.NAT_DEX, "firered"))
        // An opposing Deoxys walks in its Normal form in every game, as the battle draws it (rc34 known issue).
        for (game in listOf("firered", "leafgreen", "emerald")) {
            assertEquals(Look(), F.ofEnemy(enemy(PalForms.DEOXYS_GEN3), emptyList(), 3, Dex.GEN3, game), "an opposing Deoxys in $game")
        }
        // Gen 2 by its DVs (EnemyInfo.dvs, from the battle struct).
        assertEquals(Look("201-i", shiny = true), F.ofEnemy(enemy(PalForms.UNOWN, dvs = 0x2AAA), emptyList(), 2, Dex.NATIONAL, ""))
        assertEquals(Look(), F.ofEnemy(enemy(PalForms.UNOWN), emptyList(), 2, Dex.NATIONAL, ""), "no DVs read")
    }
}
