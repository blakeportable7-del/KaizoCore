package com.ironmonone.app

import androidx.compose.ui.graphics.Color
import com.ironmonone.tracker.nds.Gen4
import com.ironmonone.tracker.nds.NdsAutoThemes
import com.ironmonone.tracker.nds.NdsTrackedMon
import com.ironmonone.tracker.nds.NdsTrackerState
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Auto Pokemon Themes (Fellshadow's AutoThemes.lua, v1.2), 2026-09-28: the table, the
 * lead rules, and that the user's own theme always comes back untouched. DS games
 * (2026-09-29): the DS tracker's PokemonThemeManager rules and its 14-colour themes.
 */
class AutoThemeTest {
    private fun hex(c: Color) = ThemeStore.hex(c).substring(2)

    @AfterTest fun reset() { AutoTheme.resume(); AutoTheme.release(); ThemeStore.KEYS.forEach { it.set(it.default) } }

    @Test
    fun `the table is the extension's 386 species, every code well formed`() {
        var rows = 0
        for (sp in 1..411) {
            val code = AutoTheme.gbaTheme(sp) ?: continue
            rows++
            val parts = code.split(' ')
            assertTrue(parts.size == 13 && parts.take(11).all { it.length == 6 && it.toLongOrNull(16) != null }, "species $sp: $code")
        }
        assertEquals(386, rows)
        // Gen 3 internal ids: 252-276 are the unused slots between Celebi and Treecko.
        assertTrue((252..276).none { AutoTheme.gbaTheme(it) != null })
        assertTrue(AutoTheme.gbaTheme(277) != null, "Treecko")
    }

    @Test
    fun `a lead with a theme shows it, in the reference's colour order`() {
        AutoTheme.onGba(listOf(1 to false), on = true)
        // Bulbasaur: ... Lower background 2DC3C1, Main background BE434C, flag 1 = 0.
        assertEquals("BE434C", hex(Pc.Page))
        assertEquals("2DC3C1", Pc.LowerGroundX?.let { hex(it) })
        assertEquals("FBFD85", hex(Pc.Gold))
        assertTrue(Pc.moveTypeBar)
        assertEquals(AutoTheme.gbaTheme(1), AutoTheme.current)
    }

    @Test
    fun `an egg in slot 1 gives way to the next Pokemon`() {
        AutoTheme.onGba(listOf(1 to true, 7 to false), on = true)
        assertEquals(AutoTheme.gbaTheme(7), AutoTheme.current)
    }

    @Test
    fun `a lead with no theme, or the option off, gives the user's theme back exactly`() {
        ThemeStore.KEYS.first { it.name == "Main background color" }.set(Color(0xFF123456))
        AutoTheme.onGba(listOf(1 to false), on = true)
        AutoTheme.onGba(listOf(412 to false), on = true)   // a Nat. Dex species: no theme
        assertEquals("123456", hex(Pc.Page))
        assertNull(Pc.LowerGroundX); assertNull(AutoTheme.current)
        AutoTheme.onGba(listOf(1 to false), on = true)
        AutoTheme.onGba(listOf(1 to false), on = false)
        assertEquals("123456", hex(Pc.Page))
        assertTrue(!Pc.moveTypeBar)
    }

    @Test
    fun `an empty party keeps what is showing, and the colour editor always edits the user's theme`() {
        AutoTheme.onGba(listOf(4 to false), on = true)
        val showing = AutoTheme.current
        AutoTheme.onGba(emptyList(), on = true)
        assertEquals(showing, AutoTheme.current)
        AutoTheme.suspend()
        assertNull(AutoTheme.current)
        assertEquals("000000", hex(Pc.Page))            // the default user theme
        AutoTheme.onGba(listOf(4 to false), on = true)   // ignored while the editor is open
        assertNull(AutoTheme.current)
        assertEquals(ThemeStore.KEYS.joinToString(",") { ThemeStore.hex(it.default) }, ThemeStore.export())
    }

    /** rc32 audit P3 #18: Play sends an update only when the team changes, so the auto theme stayed off after the editor closed. */
    @Test
    fun `closing the colour editor puts the lead's theme back at once`() {
        AutoTheme.onGba(listOf(4 to false), on = true)
        val code = AutoTheme.current
        AutoTheme.suspend()
        assertNull(AutoTheme.current)
        AutoTheme.resume()
        assertEquals(code, AutoTheme.current, "Charmander's theme, with no new update")
        // The same on a DS game.
        AutoTheme.onDs(AutoTheme.DsPokemon(4, 7, 0), on = true)
        val ds = AutoTheme.current
        AutoTheme.suspend()
        AutoTheme.resume()
        assertEquals(ds, AutoTheme.current)
        assertEquals("7D563A", hex(Pc.Page))
    }

    @Test
    fun `an option switched off while the editor was open stays off when it closes, and a second resume does nothing`() {
        AutoTheme.onGba(listOf(4 to false), on = true)
        AutoTheme.suspend()
        AutoTheme.onGba(listOf(4 to false), on = false)   // Play's update while the editor is open
        AutoTheme.resume()
        assertNull(AutoTheme.current)
        assertEquals("000000", hex(Pc.Page))
        AutoTheme.onGba(listOf(4 to false), on = true)
        val showing = AutoTheme.current
        AutoTheme.resume()   // the editor was not open: nothing is applied again
        assertEquals(showing, AutoTheme.current)
    }

    /**
     * rc32 audit P2 #93: the editor's hex fields were read in its first draw, before it held the auto theme off, so they
     * showed the auto colours, and Done on one saved it over the user's own theme.
     */
    @Test
    fun `the colour editor opens on the user's own colours while an auto theme shows`() {
        ThemeStore.KEYS.first { it.name == "Main background color" }.set(Color(0xFF123456))
        AutoTheme.onGba(listOf(1 to false), on = true)
        assertEquals("BE434C", hex(Pc.Page), "Bulbasaur's theme shows")
        val opening = ThemeEditor.openingHex()
        assertEquals("FF123456", opening["Main background color"], "the user's, not Bulbasaur's")
        assertEquals(ThemeStore.KEYS.map { it.name }, opening.keys.toList())
        AutoTheme.suspend()
        assertEquals(ThemeStore.KEYS.associate { it.name to ThemeStore.hex(it.get()) }, opening, "exactly what the editor shows once the auto theme is held off")
        // With no auto theme showing, it is what shows.
        AutoTheme.resume(); AutoTheme.onGba(listOf(1 to false), on = false)
        assertEquals(ThemeStore.KEYS.associate { it.name to ThemeStore.hex(it.get()) }, ThemeEditor.openingHex())
        val src = File("src/main/kotlin/com/ironmonone/app/Theme.kt").readText()
        assertTrue("var edits by remember { mutableStateOf(ThemeEditor.openingHex()) }" in src)
    }

    private fun dsMon(pid: Long, species: Int, curHp: Int, form: Int = 0) = NdsTrackedMon(
        mon = Gen4.decodeParty(Gen4.encodeParty(pid, species, 20, curHp, 50, listOf(33, 0, 0, 0), form = form))!!,
        speciesName = "-", info = null, abilityName = "-", itemName = "-", moves = emptyList(),
    )

    @Test
    fun `a DS Pokemon's theme lands on the palette as the DS tracker formats it`() {
        // Squirtle (Gen 4): white text over black. Its own positive D3E3FE gives way to the light
        // pair, and the lower box gets the dark pair as its alternates.
        AutoTheme.onDs(AutoTheme.DsPokemon(4, 7, 0), on = true)
        assertEquals(listOf("C8DDFF", "FDCDCD", "0343B0", "B40002"), listOf(Pc.Positive, Pc.Negative, Pc.AltPositive, Pc.AltNegative).map(::hex))
        assertEquals(listOf("FFFFFF", "000000", "000000"), listOf(Pc.Text, Pc.LowerText, Pc.Dim).map(::hex))
        assertEquals(listOf("7D563A", "7D563A", "FADCB2", "DDB568", "548DCB", "3167A1", "FFFCAA", "FFFFFF"),
            listOf(Pc.Page, Pc.HeaderGroundX!!, Pc.LowerGroundX!!, Pc.LowerBorder, Pc.Ground, Pc.Border, Pc.Gold, Pc.Header).map(::hex))
        assertTrue(Pc.moveTypeBar)
        assertEquals(true, Pc.categoryIconsX)
        assertEquals(NdsAutoThemes.code(4, 7), AutoTheme.current)
        // Pidgey: one text colour for both boxes, so no alternates; the lower box uses the pair above.
        AutoTheme.onDs(AutoTheme.DsPokemon(4, 16, 0), on = true)
        assertNull(Pc.AltPositiveX)
        assertEquals(hex(Pc.Positive), hex(Pc.AltPositive))
    }

    @Test
    fun `a DS form with a theme of its own shows it`() {
        AutoTheme.onDs(AutoTheme.DsPokemon(4, 413, 2), on = true)
        assertEquals(NdsAutoThemes.code(4, 413, 2), AutoTheme.current)
        assertNotEquals(NdsAutoThemes.code(4, 413, 0), AutoTheme.current)
    }

    @Test
    fun `a DS Pokemon with no theme, or none at all, keeps what shows, and the option off gives the user's theme back`() {
        ThemeStore.KEYS.first { it.name == "Main background color" }.set(Color(0xFF123456))
        AutoTheme.onDs(AutoTheme.DsPokemon(4, 7, 0), on = true)
        val showing = AutoTheme.current
        AutoTheme.onDs(AutoTheme.DsPokemon(4, 600, 0), on = true)   // no species 600 in Gen 4
        AutoTheme.onDs(null, on = true)
        assertEquals(showing, AutoTheme.current)
        assertEquals("7D563A", hex(Pc.Page))
        AutoTheme.onDs(AutoTheme.DsPokemon(4, 7, 0), on = false)
        assertEquals("123456", hex(Pc.Page))
        assertNull(AutoTheme.current); assertNull(Pc.AltPositiveX); assertNull(Pc.categoryIconsX)
        assertTrue(!Pc.moveTypeBar)
    }

    @Test
    fun `the colour editor holds DS themes off too`() {
        AutoTheme.suspend()
        AutoTheme.onDs(AutoTheme.DsPokemon(4, 7, 0), on = true)
        assertNull(AutoTheme.current)
        assertEquals("000000", hex(Pc.Page))
    }

    @Test
    fun `the DS theme follows the Pokemon the heals follow, with its form and the game's generation`() {
        val fainted = dsMon(0x11, 7, curHp = 0)
        val trash = dsMon(0x22, 413, curHp = 30, form = 2)
        val s = NdsTrackerState(2, listOf(fainted, trash), located = true, healsPid = 0x22, badgeSet = "DPPT")
        assertEquals(AutoTheme.DsPokemon(4, 413, 2), AutoTheme.dsPokemon(s))
        assertEquals(AutoTheme.DsPokemon(5, 413, 2), AutoTheme.dsPokemon(s.copy(badgeSet = "BW2")))
        assertNull(AutoTheme.dsPokemon(NdsTrackerState(0, emptyList(), located = false)))
    }

    @Test
    fun `DS games are wired to the DS rules, and the lower box draws the alternates`() {
        val play = File("src/main/kotlin/com/ironmonone/app/PlayScreen.kt").readText()
        assertTrue("val autoThemeDs = ndsState?.let(AutoTheme::dsPokemon)" in play)
        assertTrue("AutoTheme.onDs(autoThemeDs, TrackerOptions.autoPokemonThemes)" in play)
        assertTrue("showAutoThemes = ndsState == null" !in play, "the gear hides the toggle on DS again")
        val pc = File("src/main/kotlin/com/ironmonone/app/PcTracker.kt").readText()
        assertEquals(3, pc.split("if (r.stab) Pc.AltPositive else Pc.LowerText").size, "STAB power in the alternate, both layouts")
        assertTrue("Pc.categoryIconsX ?: TrackerOptions.showCategoryIcons" in pc)
        val decor = File("src/main/kotlin/com/ironmonone/app/MoveDecor.kt").readText()
        assertTrue("val color = if (up) Pc.AltPositive else Pc.AltNegative" in decor)
        assertTrue("""PixText("X", PcRef.FONT, Pc.AltNegative, modifier)""" in decor)
    }

    @Test
    fun `the play screen applies it on every tracker update`() {
        // Wiring proof: fails if PlayScreen stops calling AutoTheme or stops releasing it.
        val src = File("src/main/kotlin/com/ironmonone/app/PlayScreen.kt").readText()
        assertTrue("AutoTheme.onGba(" in src, "PlayScreen no longer applies auto themes")
        assertTrue("AutoTheme.release()" in src, "PlayScreen no longer releases the auto theme on exit")
    }
}
