package com.ironmonone.app

import androidx.compose.ui.graphics.Color
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Preset tracker themes (2026-09-29): the lists are the two reference trackers' own, applying one shows what
 * AutoTheme.apply shows, it becomes the player's theme and an auto theme gives it back whole, and the player's
 * own presets save, list and remove as the PC tracker's do.
 */
class ThemePresetsTest {
    private val dir: File = Files.createTempDirectory("themepresets").toFile()
    private val refs = File(System.getProperty("user.home"), "ironmon-ref")

    @AfterTest fun cleanup() {
        AutoTheme.resume(); AutoTheme.release()
        ThemeStore.detach(); ThemePresets.detach()
        ThemeStore.show(ThemeStore.FACTORY)
        dir.deleteRecursively()
    }

    private fun c(hex: String) = Color(0xFF000000L or hex.toLong(16))
    private fun pc(name: String) = ThemePresets.builtIns(false).first { it.name == name }
    private fun ds(name: String) = ThemePresets.builtIns(true).first { it.name == name }

    // ---- The lists are the references' ----------------------------------------------------------------

    @Test
    fun `the PC presets are the reference's names, codes and order`() {
        val constants = File(refs, "Ironmon-Tracker/ironmon_tracker/Constants.lua")
        val themeLua = File(refs, "Ironmon-Tracker/ironmon_tracker/Theme.lua")
        if (!constants.isFile || !themeLua.isFile) { println("skipped: no PC tracker checkout under $refs"); return }
        val text = constants.readText()
        val block = text.substringAfter("Constants.PreloadedThemes = {").substringBefore("\n}")
        val codes = Regex("\\[\"([^\"]+)\"\\]\\s*=\\s*\"([^\"]+)\"").findAll(block).associate { it.groupValues[1] to it.groupValues[2] }
        val order = text.substringAfter("PRELOADED_THEMES = {").substringBefore("}")
        val names = Regex("\"([^\"]+)\"").findAll(order).map { it.groupValues[1] }.toList()
        assertEquals(14, names.size, "the reference lists fourteen: $names")
        assertEquals(names.map { it to codes.getValue(it) }, ThemePresets.PC_CODES)
        assertEquals(listOf("Default") + names, ThemePresets.builtIns(false).map { it.name })
        // The Default is Theme.resetPresets' "Default Theme": the one non-empty code in that function.
        val reset = themeLua.readText().substringAfter("function Theme.resetPresets()").substringBefore("\nend")
        val defaults = Regex("code = \"([^\"]*)\"").findAll(reset).map { it.groupValues[1] }.filter { it.isNotEmpty() }.toList()
        assertEquals(listOf(ThemePresets.DEFAULT_CODE), defaults)
    }

    @Test
    fun `the DS presets are the colortheme files the DS tracker ships, by file name`() {
        val folder = File(refs, "NDS-Ironmon-Tracker/ironmon_tracker/themes")
        if (!folder.isDirectory) { println("skipped: no DS tracker checkout under $refs"); return }
        // Its Load Theme dialog lists them by name without regard to case.
        val files = folder.listFiles { f -> f.extension == "colortheme" }!!.sortedBy { it.nameWithoutExtension.lowercase() }
        assertEquals(15, files.size)
        val expected = files.map { it.nameWithoutExtension to it.readText().trim().replace(Regex("\\s+"), " ") }
        assertEquals(expected, ThemePresets.DS_CODES)
        assertEquals(listOf("Default") + expected.map { it.first }, ThemePresets.builtIns(true).map { it.name })
    }

    @Test
    fun `every built-in parses, and a console's names are its own and unique`() {
        val gba = ThemePresets.builtIns(false)
        val nds = ThemePresets.builtIns(true)
        assertEquals(15, gba.size, "Default and the PC tracker's fourteen")
        assertEquals(16, nds.size, "Default and the DS tracker's fifteen")
        assertEquals(gba.size, gba.map { it.name.lowercase() }.toSet().size)
        assertEquals(nds.size, nds.map { it.name.lowercase() }.toSet().size)
        assertTrue((gba + nds).all { it.builtIn })
    }

    // ---- Applying one shows what AutoTheme.apply shows ---------------------------------------------------

    /** What AutoThemes.lua's loader puts where, written out from Theme.lua's order, not taken from the code under test. */
    private fun assertPaletteIsTheCode(code: String, label: String) {
        val parts = code.split(' ')
        val k = parts.take(11).map(::c)
        assertEquals(k[0], Pc.Text, "$label default text")
        assertEquals(k[1], Pc.LowerTextX, "$label lower text"); assertEquals(k[1], Pc.Dim, "$label bottom box text")
        assertEquals(k[2], Pc.Positive, "$label positive")
        assertEquals(k[3], Pc.Negative, "$label negative")
        assertEquals(k[4], Pc.Gold, "$label intermediate")
        assertEquals(k[5], Pc.HeaderX, "$label header")
        assertEquals(k[6], Pc.Border, "$label upper border")
        assertEquals(k[7], Pc.Ground, "$label upper background")
        assertEquals(k[8], Pc.LowerBorderX, "$label lower border")
        assertEquals(k[9], Pc.LowerGroundX, "$label lower background")
        assertEquals(k[10], Pc.Page, "$label main background"); assertEquals(k[10], Pc.HeaderGroundX, "$label header ground")
        assertEquals(parts[11] == "0", Pc.moveTypeBar, "$label move-type bar is flag 1 at zero")
        assertNull(Pc.AltPositiveX, label); assertNull(Pc.AltNegativeX, label); assertNull(Pc.categoryIconsX, label)
    }

    @Test
    fun `a PC preset puts every colour and override where AutoTheme_apply puts them`() {
        for ((name, code) in listOf("Default" to ThemePresets.DEFAULT_CODE) + ThemePresets.PC_CODES) {
            ThemeStore.show(ThemeStore.FACTORY)
            ThemePresets.apply(pc(name))
            assertPaletteIsTheCode(code, name)
            val viaPreset = ThemeStore.snapshot()
            // And the same code through the auto theme's own entry point.
            ThemeStore.show(ThemeStore.FACTORY)
            assertTrue(AutoTheme.apply(code), name)
            assertEquals(viaPreset, ThemeStore.snapshot(), name)
            AutoTheme.release()
        }
    }

    @Test
    fun `a preset replaces the whole theme, nothing of the last one stays`() {
        ThemePresets.apply(pc("Fire Red"))
        assertTrue(Pc.moveTypeBar)
        // A DS theme's leftovers: an alternate pair and the category icons.
        Pc.AltPositiveX = Color(0xFF0343B0); Pc.AltNegativeX = Color(0xFFB40002); Pc.categoryIconsX = false
        ThemePresets.apply(pc("Neon Lights"))
        assertEquals(pc("Neon Lights").theme, ThemeStore.snapshot())
        assertFalse(Pc.moveTypeBar, "Neon Lights colours the move names by type: flag 1 is 1")
        assertNull(Pc.AltPositiveX); assertNull(Pc.AltNegativeX); assertNull(Pc.categoryIconsX)
    }

    @Test
    fun `a DS preset shows what the DS tracker's Load Theme shows for its file`() {
        for ((name, text) in ThemePresets.DS_CODES) {
            val tokens = Regex("[0-9a-fA-F]+").findAll(text).map { it.value }.toList()
            val colours = tokens.filter { it.length > 1 }.map(::c)
            assertEquals(13, colours.size, name)
            // isLegacyNDSString: the first colour is both boxes' text, then keys 3 to 11 in THEME_COLOR_KEYS_ORDERED order.
            val expected = WholeTheme(
                page = colours[9], ground = colours[6], border = colours[5], text = colours[0],
                positive = colours[1], negative = colours[2], gold = colours[3], dim = colours[0],
                headerX = colours[4], headerGroundX = colours[9], lowerTextX = colours[0],
                lowerBorderX = colours[7], lowerGroundX = colours[8],
                moveTypeBar = tokens.first { it.length == 1 } == "0",
            )
            ThemeStore.show(ThemeStore.FACTORY)
            ThemePresets.apply(ds(name))
            assertEquals(expected, ThemeStore.snapshot(), name)
        }
    }

    @Test
    fun `the DS reading agrees with the PC tracker on the themes both ship`() {
        // Same colours in both trackers' files, so the two readings must land on the same theme.
        assertEquals(pc("Fire Red").theme, ds("FireRed").theme)
        assertEquals(pc("Leaf Green").theme, ds("LeafGreen").theme)
        assertEquals(pc("Neon Lights").theme, ds("Neon").theme)
    }

    @Test
    fun `a code that is not a theme changes nothing`() {
        ThemePresets.apply(pc("Team Rocket"))
        val before = ThemeStore.snapshot()
        assertFalse(AutoTheme.apply("FFFFFF FFFFFF"), "too few colours")
        assertFalse(AutoTheme.apply("FFFFFF FFFFFF 00FF00 FF0000 FFFF00 FFFFFF AAAAAA 222222 AAAAAA 222222 -12345 1 1"), "a sign is not a digit")
        assertNull(AutoTheme.current)
        assertEquals(before, ThemeStore.snapshot())
        assertNull(ThemeCodes.gen3("")); assertNull(ThemeCodes.ds("FFFFFF 000000 1"))
    }

    // ---- It is the player's own theme: saved, back at launch, put back whole by an auto theme --------------

    @Test
    fun `a preset is saved, comes back at the next launch and survives an auto theme`() {
        val f = File(dir, "prep/theme.txt")
        ThemeStore.load(f)
        val fire = pc("Fire Red")
        ThemePresets.apply(fire)
        DiskWriter.drain()   // written on the writer's thread (rc32 audit P3 #69)
        assertTrue(f.isFile, "picking a preset saves the theme")
        // The next launch.
        ThemeStore.show(ThemeStore.FACTORY)
        ThemeStore.load(f)
        assertEquals(fire.theme, ThemeStore.snapshot())
        // A Gen 3 auto theme takes over (Bulbasaur), then lets go.
        AutoTheme.onGba(listOf(1 to false), on = true)
        assertNotEquals(fire.theme, ThemeStore.snapshot())
        AutoTheme.onGba(listOf(1 to false), on = false)
        assertEquals(fire.theme, ThemeStore.snapshot(), "release() gives the whole preset back, lower box and move-type bar included")
        // With no auto theme showing, the release each tracker update makes must leave a preset alone.
        AutoTheme.onGba(listOf(1 to false), on = false)
        AutoTheme.release()
        assertEquals(fire.theme, ThemeStore.snapshot())
    }

    @Test
    fun `a DS auto theme gives a preset with a DS theme's extras back whole`() {
        val mine = ThemePresets.builtIns(true).first { it.name == "Chalkboard" }.theme.copy(
            altPositiveX = Color(0xFFC8DDFF), altNegativeX = Color(0xFFFDCDCD), categoryIconsX = false,
        )
        ThemeStore.adopt(mine)
        AutoTheme.onDs(AutoTheme.DsPokemon(4, 7, 0), on = true)
        assertNotEquals(mine, ThemeStore.snapshot())
        AutoTheme.onDs(AutoTheme.DsPokemon(4, 7, 0), on = false)
        assertEquals(mine, ThemeStore.snapshot())
    }

    @Test
    fun `the colour editor holds an auto theme off and picks a preset on the player's own theme`() {
        ThemePresets.apply(pc("Item Bag"))
        AutoTheme.onGba(listOf(1 to false), on = true)
        AutoTheme.suspend()
        assertNull(AutoTheme.current)
        assertEquals(pc("Item Bag").theme, ThemeStore.snapshot(), "the editor opens on the player's theme")
        ThemePresets.apply(pc("GameCube"))
        AutoTheme.resume()
        AutoTheme.onGba(listOf(1 to false), on = true)
        AutoTheme.onGba(listOf(1 to false), on = false)
        assertEquals(pc("GameCube").theme, ThemeStore.snapshot(), "what the auto theme gives back is the preset picked in the editor")
    }

    // ---- Editing after a preset ----------------------------------------------------------------------------

    @Test
    fun `an edit after a preset works, follows the two colours tied to its key, and is saved`() {
        val f = File(dir, "prep/theme.txt")
        ThemeStore.load(f)
        val fire = pc("Fire Red")
        ThemePresets.apply(fire)
        val page = ThemeStore.KEYS[0]; val bottom = ThemeStore.KEYS[7]; val topBg = ThemeStore.KEYS[1]
        ThemeStore.edit(page, Color(0xFF123456))
        assertEquals(Color(0xFF123456), Pc.Page)
        assertEquals(Color(0xFF123456), Pc.HeaderGroundX, "the header sits on the main background")
        ThemeStore.edit(bottom, Color(0xFF654321))
        assertEquals(Color(0xFF654321), Pc.Dim)
        assertEquals(Color(0xFF654321), Pc.LowerTextX, "the moves table takes the bottom box text colour")
        ThemeStore.edit(topBg, Color(0x80102030))
        assertEquals(Color(0x80102030), Pc.Ground)
        assertEquals(fire.theme.lowerGroundX, Pc.LowerGroundX, "the top box's background is not the lower box's")
        assertEquals(fire.theme.headerX, Pc.HeaderX)
        assertTrue(Pc.moveTypeBar)
        val edited = ThemeStore.snapshot()
        assertNotEquals(fire.theme, edited, "an edited preset is no longer that preset")
        // Saved: the next launch shows the edit and the preset around it.
        ThemeStore.show(ThemeStore.FACTORY)
        ThemeStore.load(f)
        assertEquals(edited, ThemeStore.snapshot())
    }

    @Test
    fun `an edit leaves an extra alone once it no longer holds its key's colour`() {
        ThemeStore.adopt(pc("Fire Red").theme.copy(headerGroundX = Color(0xFF445566)))
        ThemeStore.edit(ThemeStore.KEYS[0], Color(0xFF123456))
        assertEquals(Color(0xFF445566), Pc.HeaderGroundX)
    }

    // ---- The player's own presets ------------------------------------------------------------------------

    private fun mine(seed: Int) = pc("Fire Red").theme.copy(page = Color(0xFF000000L or seed.toLong()))

    @Test
    fun `a saved preset lists after the built-ins, is written as name=code, and is there at the next launch`() {
        val f = File(dir, "prep/theme-presets.txt")
        ThemePresets.load(f)
        assertTrue(ThemePresets.yours.isEmpty())
        assertEquals(ThemePresets.SaveResult.SAVED, ThemePresets.saveYours("Mine", mine(0x101010)))
        assertEquals(ThemePresets.SaveResult.SAVED, ThemePresets.saveYours("Second", mine(0x202020)))
        assertEquals(listOf("Mine", "Second"), ThemePresets.yours.map { it.name })
        assertTrue(ThemePresets.yours.none { it.builtIn })
        assertEquals("Mine=" + ThemeStore.encode(mine(0x101010)) + "\n" + "Second=" + ThemeStore.encode(mine(0x202020)) + "\n", f.readText())
        // The next launch.
        ThemePresets.detach(); assertTrue(ThemePresets.yours.isEmpty())
        ThemePresets.load(f)
        assertEquals(listOf("Mine", "Second"), ThemePresets.yours.map { it.name })
        assertEquals(mine(0x101010), ThemePresets.yours[0].theme)
        assertEquals(mine(0x202020), ThemePresets.yours[1].theme)
    }

    @Test
    fun `the theme showing is what a save keeps by default, extras and all`() {
        ThemePresets.load(File(dir, "prep/theme-presets.txt"))
        ThemePresets.apply(pc("Cozy Fall Leaves"))
        assertEquals(ThemePresets.SaveResult.SAVED, ThemePresets.saveYours("Leaves, mine"))
        assertEquals(pc("Cozy Fall Leaves").theme, ThemePresets.yours.single().theme)
        // The tile of the saved one and of the built-in it came from are both "in use", and a saved one applies as it was.
        ThemePresets.apply(pc("Neon Lights"))
        ThemePresets.apply(ThemePresets.yours.single())
        assertEquals(pc("Cozy Fall Leaves").theme, ThemeStore.snapshot())
    }

    @Test
    fun `saving under a name in use replaces it where it stands, whatever the case`() {
        val f = File(dir, "prep/theme-presets.txt")
        ThemePresets.load(f)
        ThemePresets.saveYours("Mine", mine(0x101010))
        ThemePresets.saveYours("Other", mine(0x202020))
        assertEquals(ThemePresets.SaveResult.REPLACED, ThemePresets.saveYours("mINE", mine(0x303030)))
        assertEquals(listOf("mINE", "Other"), ThemePresets.yours.map { it.name })
        assertEquals(mine(0x303030), ThemePresets.yours[0].theme)
        ThemePresets.detach(); ThemePresets.load(f)
        assertEquals(listOf("mINE", "Other"), ThemePresets.yours.map { it.name })
    }

    @Test
    fun `a saved preset can be removed, and the file follows`() {
        val f = File(dir, "prep/theme-presets.txt")
        ThemePresets.load(f)
        ThemePresets.saveYours("Mine", mine(0x101010))
        ThemePresets.saveYours("Other", mine(0x202020))
        assertTrue(ThemePresets.removeYours("mine"))
        assertEquals(listOf("Other"), ThemePresets.yours.map { it.name })
        assertEquals("Other=" + ThemeStore.encode(mine(0x202020)) + "\n", f.readText())
        assertFalse(ThemePresets.removeYours("Mine"), "already gone")
        assertTrue(ThemePresets.removeYours("Other"))
        assertEquals("", f.readText())
    }

    @Test
    fun `the built-ins cannot be removed or written over`() {
        ThemePresets.load(File(dir, "prep/theme-presets.txt"))
        ThemePresets.saveYours("Mine", mine(0x101010))
        for (name in listOf("Default", "default", "Fire Red", "FIRE RED", "Calico Cat v2", "STONKS", "beach", "AlolanExeggcutor")) {
            assertFalse(ThemePresets.removeYours(name), "remove $name")
            assertEquals(ThemePresets.SaveResult.RESERVED, ThemePresets.saveYours(name, mine(0x111111)), "save $name")
        }
        assertEquals(listOf("Mine"), ThemePresets.yours.map { it.name })
        assertEquals(15, ThemePresets.builtIns(false).size); assertEquals(16, ThemePresets.builtIns(true).size)
        assertEquals(pc("Fire Red").theme, ThemeCodes.gen3(ThemePresets.PC_CODES[0].second), "and the built-in is what it was")
    }

    @Test
    fun `a name with spaces or an equals sign survives the file`() {
        val f = File(dir, "prep/theme-presets.txt")
        ThemePresets.load(f)
        val names = listOf("My theme", "a=b=c", "x=", "=lead", "plain")
        names.forEachIndexed { i, n -> assertEquals(ThemePresets.SaveResult.SAVED, ThemePresets.saveYours(n, mine(0x100000 + i)), n) }
        ThemePresets.detach(); ThemePresets.load(f)
        assertEquals(names, ThemePresets.yours.map { it.name })
        names.indices.forEach { i -> assertEquals(mine(0x100000 + i), ThemePresets.yours[i].theme, names[i]) }
    }

    @Test
    fun `a name is cleaned, and a blank one is refused`() {
        ThemePresets.load(File(dir, "prep/theme-presets.txt"))
        assertEquals("two lines here", ThemePresets.cleanName("  two\nlines\t  here "))
        assertEquals(ThemePresets.MAX_NAME, ThemePresets.cleanName("x".repeat(90)).length)
        assertEquals(ThemePresets.SaveResult.EMPTY, ThemePresets.saveYours("   ", mine(1)))
        assertEquals(ThemePresets.SaveResult.EMPTY, ThemePresets.saveYours("\n\t", mine(1)))
        assertEquals(ThemePresets.SaveResult.SAVED, ThemePresets.saveYours("a\nb", mine(1)))
        assertEquals("a b", ThemePresets.yours.single().name)
        assertEquals(1, File(dir, "prep/theme-presets.txt").readLines().size, "a newline in a name never splits a line")
    }

    @Test
    fun `a write that fails leaves the list as it was`() {
        val f = File(dir, "prep/theme-presets.txt")
        ThemePresets.load(f)
        ThemePresets.saveYours("Mine", mine(0x101010))
        val blocked = File(dir, "prep/theme-presets.txt.tmp").apply { mkdirs() }   // a directory where the temp file goes
        assertEquals(ThemePresets.SaveResult.FAILED, ThemePresets.saveYours("Other", mine(0x202020)))
        assertFalse(ThemePresets.removeYours("Mine"))
        assertEquals(listOf("Mine"), ThemePresets.yours.map { it.name })
        assertEquals("Mine=" + ThemeStore.encode(mine(0x101010)) + "\n", f.readText())
        blocked.delete()
    }

    @Test
    fun `a file that was edited by hand loads what is sound and skips the rest`() {
        val good = ThemeStore.encode(mine(0x123456))
        val other = ThemeStore.encode(mine(0x654321))
        val f = File(dir, "prep/theme-presets.txt")
        f.parentFile!!.mkdirs()
        f.writeText(
            listOf(
                "no equals sign here", "=$good", "Bad code=zzz", "Fire Red=$good", "  spaced name  =$good",
                "Dup=$good", "Other=$good", "dup=$other", "",
            ).joinToString("\n")
        )
        ThemePresets.load(f)
        assertEquals(listOf("spaced name", "dup", "Other"), ThemePresets.yours.map { it.name }, "the built-in's name and the junk are skipped")
        assertEquals(mine(0x654321), ThemePresets.yours[1].theme, "a name twice keeps its first place, its last spelling and its last code")
        ThemePresets.load(File(dir, "prep/missing.txt"))
        assertTrue(ThemePresets.yours.isEmpty())
    }

    @Test
    fun `the tiles mark the one in use by its whole theme`() {
        ThemePresets.apply(pc("Team Rocket"))
        val now = ThemeStore.snapshot()
        assertEquals(listOf("Team Rocket"), ThemePresets.builtIns(false).filter { it.theme == now }.map { it.name })
        ThemeStore.edit(ThemeStore.KEYS[3], Color(0xFF010203))
        assertTrue(ThemePresets.builtIns(false).none { it.theme == ThemeStore.snapshot() }, "an edited preset is custom")
    }
}
