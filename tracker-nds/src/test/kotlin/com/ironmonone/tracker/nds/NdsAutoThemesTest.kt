package com.ironmonone.tracker.nds

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The DS tracker's Auto Pokemon Themes: its table, its forms, and how PokemonThemeManager formats a theme. */
class NdsAutoThemesTest {
    private fun hex(v: Long?) = v?.let { String.format("%06X", it) }

    @Test
    fun `every species of both generations has a theme, and every theme reads`() {
        for ((gen, last) in listOf(4 to 493, 5 to 649)) {
            for (sp in 1..last) {
                val code = assertNotNull(NdsAutoThemes.code(gen, sp), "gen $gen species $sp")
                assertNotNull(NdsAutoThemes.read(code), "gen $gen species $sp: $code")
            }
            assertNull(NdsAutoThemes.code(gen, last + 1), "no species past the generation's last")
        }
        assertNull(NdsAutoThemes.code(4, 0))
    }

    @Test
    fun `a form with a theme of its own shows it, any other form its species' theme`() {
        // Wormadam's Sandy and Trash cloaks, Rotom's five appliances (Program.checkForAlternateForm, not cosmetic).
        val plant = NdsAutoThemes.code(4, 413, 0)
        assertNotEquals(plant, NdsAutoThemes.code(4, 413, 1))
        assertNotEquals(NdsAutoThemes.code(4, 413, 1), NdsAutoThemes.code(4, 413, 2))
        assertEquals(5, (1..5).map { NdsAutoThemes.code(4, 479, it) }.count { it != NdsAutoThemes.code(4, 479, 0) })
        // Castform's forms are alternate forms in Gen 5 only; Gen 4 shows the species' theme.
        assertNotEquals(NdsAutoThemes.code(5, 351, 0), NdsAutoThemes.code(5, 351, 1))
        assertEquals(NdsAutoThemes.code(4, 351, 0), NdsAutoThemes.code(4, 351, 1))
        // Cosmetic forms (Shellos East, Unfezant's female form) and forms whose entry repeats the
        // species' theme (Deoxys) show the species' theme.
        assertEquals(NdsAutoThemes.code(5, 422, 0), NdsAutoThemes.code(5, 422, 1))
        assertEquals(NdsAutoThemes.code(5, 521, 0), NdsAutoThemes.code(5, 521, 1))
        assertEquals(NdsAutoThemes.code(4, 386, 0), NdsAutoThemes.code(4, 386, 2))
    }

    @Test
    fun `the form is the top five bits of block B's form byte`() {
        // A Trash Cloak Wormadam is female as well: bit 1 must not move the form.
        val trash = Gen4.decodeParty(Gen4.encodeParty(0x1357L, 413, 20, 50, 50, listOf(33, 0, 0, 0), female = true, form = 2))!!
        assertEquals(2, trash.form)
        assertTrue(trash.isFemale)
        val g5 = Gen4.decodeParty(Gen4.encodeParty(0x1357L, 479, 20, 50, 50, listOf(33, 0, 0, 0), gen5 = true, form = 4), gen5 = true)!!
        assertEquals(4, g5.form)
        assertEquals(0, Gen4.decodeParty(Gen4.encodeParty(0x1357L, 7, 20, 50, 50, listOf(33, 0, 0, 0), female = true))!!.form)
    }

    @Test
    fun `white top text over black bottom text gives the light pair on top and the dark pair below`() {
        // Squirtle: FFFFFF 000000 D3E3FE FECCCC FFFCAA FFFFFF 3167A1 548DCB DDB568 FADCB2 7D563A ...
        val t = assertNotNull(NdsAutoThemes.read(NdsAutoThemes.code(4, 7)!!))
        assertEquals(listOf("FFFFFF", "000000", "C8DDFF", "FDCDCD"), listOf(t.topText, t.bottomText, t.positive, t.negative).map(::hex),
            "formatPokemonTheme puts the light pair under white text, over the theme's own D3E3FE and FECCCC")
        assertEquals("0343B0", hex(t.altPositive))
        assertEquals("B40002", hex(t.altNegative))
        assertEquals(listOf("FFFCAA", "FFFFFF", "3167A1", "548DCB", "DDB568", "FADCB2", "7D563A"),
            listOf(t.intermediate, t.moveHeader, t.topBorder, t.topBackground, t.bottomBorder, t.bottomBackground, t.mainBackground).map(::hex))
    }

    @Test
    fun `black over white takes the light alternates, one text colour for both boxes takes none`() {
        val voltorb = NdsAutoThemes.read(NdsAutoThemes.code(4, 100)!!)!!   // 000000 over FFFFFF
        assertEquals("0343B0", hex(voltorb.positive))
        assertEquals("C8DDFF", hex(voltorb.altPositive))
        assertEquals("FDCDCD", hex(voltorb.altNegative))
        val pidgey = NdsAutoThemes.read(NdsAutoThemes.code(4, 16)!!)!!    // 000000 over 000000
        assertEquals("0343B0", hex(pidgey.positive))
        assertNull(pidgey.altPositive)
        val bulbasaur = NdsAutoThemes.read(NdsAutoThemes.code(4, 1)!!)!!  // FFFFFF over FFFFFF
        assertEquals("C8DDFF", hex(bulbasaur.positive))
        assertNull(bulbasaur.altNegative)
    }

    @Test
    fun `top text neither white nor black keeps the theme's own pair, the seventh digit included`() {
        val own = NdsAutoThemes.read("123456 000000 AABBCC DDEEFF 111111 222222 333333 444444 555555 666666 777777 888888 999999 AAAAAA 0 1 1 1 0 1")!!
        assertEquals("AABBCC", hex(own.positive))
        assertEquals("DDEEFF", hex(own.negative))
        assertNull(own.altPositive)
        // Scrafty's "FFFFFFF" and Cubchoo's "0000000": not white or black to the reference, drawn
        // as the last six digits; both carry the pair their text colour would have given anyway.
        val scrafty = NdsAutoThemes.read(NdsAutoThemes.code(5, 560)!!)!!
        assertEquals(listOf("FFFFFF", "C8DDFF", "FDCDCD"), listOf(scrafty.topText, scrafty.positive, scrafty.negative).map(::hex))
        assertNull(scrafty.altPositive)
        val cubchoo = NdsAutoThemes.read(NdsAutoThemes.code(5, 613)!!)!!
        assertEquals(listOf("000000", "0343B0", "B40002"), listOf(cubchoo.topText, cubchoo.positive, cubchoo.negative).map(::hex))
        assertNull(cubchoo.altNegative)
    }

    @Test
    fun `a string without fourteen colours is not a theme`() {
        assertNull(NdsAutoThemes.read("FFFFFF 000000 C8DDFF FDCDCD FFE670 FFFFFF 9D2E27 D63D38 C25C1A E87823 313131 0 1"))
        assertNull(NdsAutoThemes.read(""))
    }
}
