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
 * The user's whole theme (2026-09-29): the file format that holds more than the eight colours and still reads a file
 * from before, reset, import and export around it, and release() giving the whole theme back.
 */
class ThemeWholeTest {
    private val dir: File = Files.createTempDirectory("themewhole").toFile()

    @AfterTest fun cleanup() {
        AutoTheme.resume(); AutoTheme.release()
        ThemeStore.detach()
        ThemeStore.show(ThemeStore.FACTORY)
        dir.deleteRecursively()
    }

    private val eight = "FF111111,FF222222,FF333333,FF444444,FF555555,FF666666,FF777777,FF888888"

    /** A theme with every extra set, so a format that drops one shows. */
    private fun full(icons: Boolean? = true) = WholeTheme(
        page = Color(0xFF010101), ground = Color(0xFF020202), border = Color(0xFF030303), text = Color(0xFF040404),
        positive = Color(0xFF050505), negative = Color(0xFF060606), gold = Color(0xFF070707), dim = Color(0xFF080808),
        headerX = Color(0xFF111111), headerGroundX = Color(0xFF121212), lowerTextX = Color(0xFF131313),
        lowerBorderX = Color(0xFF141414), lowerGroundX = Color(0x80151515),
        altPositiveX = Color(0xFF161616), altNegativeX = Color(0xFF171717), moveTypeBar = true, categoryIconsX = icons,
    )

    private fun file(text: String): File = File(dir, "prep/theme.txt").apply { parentFile!!.mkdirs(); writeText(text) }

    // ---- A file from before presets ------------------------------------------------------------------------

    @Test
    fun `an old eight value theme file loads to the same colours and no extras`() {
        ThemeStore.load(file(eight))
        assertEquals(
            listOf(0xFF111111, 0xFF222222, 0xFF333333, 0xFF444444, 0xFF555555, 0xFF666666, 0xFF777777, 0xFF888888).map { Color(it) },
            ThemeStore.KEYS.map { it.get() },
        )
        assertEquals(WholeTheme.ofKeys(ThemeStore.KEYS.map { it.get() }), ThemeStore.snapshot())
        assertTrue(ThemeStore.snapshot().plain)
        assertFalse(Pc.moveTypeBar)
    }

    @Test
    fun `an old file with six digit colours or a trailing newline still loads`() {
        ThemeStore.load(file("111111,222222,333333,444444,555555,666666,777777,888888\n"))
        assertEquals(Color(0xFF111111), Pc.Page); assertEquals(Color(0xFF888888), Pc.Dim)
    }

    @Test
    fun `a plain theme is written exactly as it always was, so its export does not change`() {
        val f = File(dir, "prep/theme.txt")
        ThemeStore.load(f)
        assertTrue(ThemeStore.import(eight))
        DiskWriter.drain()   // written on the writer's thread (rc32 audit P3 #69)
        assertEquals(eight, f.readText())
        assertEquals(eight, ThemeStore.export())
        ThemeStore.reset()
        DiskWriter.drain()
        assertEquals(ThemeStore.KEYS.joinToString(",") { ThemeStore.hex(it.default) }, f.readText())
    }

    @Test
    fun `a file damaged after the eight colours still gives the player the eight`() {
        ThemeStore.load(file("$eight|garbage"))
        assertEquals(Color(0xFF111111), Pc.Page)
        assertTrue(ThemeStore.snapshot().plain)
        ThemeStore.show(ThemeStore.FACTORY)
        ThemeStore.load(file("$eight|-,-,-,-,-,-,-,2,-"))   // seven and a bad flag
        assertEquals(Color(0xFF888888), Pc.Dim)
        assertTrue(ThemeStore.snapshot().plain)
    }

    // ---- The whole theme ---------------------------------------------------------------------------------

    @Test
    fun `the whole theme round trips through the file, every extra of it`() {
        val f = File(dir, "prep/theme.txt")
        for (w in listOf(full(true), full(false), full(null), full().copy(altPositiveX = null, altNegativeX = null, headerX = null))) {
            ThemeStore.detach(); ThemeStore.show(ThemeStore.FACTORY)
            ThemeStore.load(f)
            ThemeStore.adopt(w)
            ThemeStore.show(ThemeStore.FACTORY)
            assertNotEquals(w, ThemeStore.snapshot())
            ThemeStore.load(f)
            assertEquals(w, ThemeStore.snapshot())
        }
    }

    @Test
    fun `the code is the eight, a bar, and nine tokens, and reads back to the same theme`() {
        val w = full()
        val code = ThemeStore.encode(w)
        assertTrue(code.startsWith("FF010101,FF020202,FF030303,FF040404,FF050505,FF060606,FF070707,FF080808|"), code)
        assertEquals("FF111111,FF121212,FF131313,FF141414,80151515,FF161616,FF171717,1,1", code.substringAfter('|'))
        assertEquals(w, ThemeStore.decode(code))
        assertEquals("-,-,-,-,-,-,-,1,-", ThemeStore.encode(WholeTheme.ofKeys(ThemeStore.FACTORY.keys).copy(moveTypeBar = true)).substringAfter('|'))
        assertEquals(eight, ThemeStore.encode(WholeTheme.ofKeys(ThemeStore.decode(eight)!!.keys)))
    }

    @Test
    fun `a code that is not a theme is refused`() {
        for (bad in listOf("", "nonsense", "FF111111", "$eight,FF999999", "$eight|", "$eight|-,-,-,-,-,-,-,1", "$eight|-,-,-,-,-,-,-,1,-,-",
            "$eight|zz,-,-,-,-,-,-,1,-", "$eight|-,-,-,-,-,-,-,2,-", "$eight|-,-,-,-,-,-,-,1,x", "|-,-,-,-,-,-,-,1,-")) {
            assertNull(ThemeStore.decode(bad), bad)
        }
    }

    @Test
    fun `the eight colours keep the editor's order in the whole theme`() {
        // A different colour in each key: the theme's own order has to be the editor's.
        ThemeStore.KEYS.forEachIndexed { i, k -> k.set(Color(0xFF000000L or (0x101010L * (i + 1)))) }
        val w = ThemeStore.snapshot()
        assertEquals(ThemeStore.KEYS.map { it.get() }, w.keys)
        assertEquals(ThemeStore.KEYS.map { it.get() }, ThemeStore.decode(ThemeStore.encode(w))!!.keys)
        assertEquals(listOf("Main background color", "Top box background color", "Top box border color", "Top box text color"),
            ThemeStore.KEYS.take(4).map { it.name })
        assertEquals(w.page, ThemeStore.KEYS[0].get()); assertEquals(w.dim, ThemeStore.KEYS[7].get())
    }

    @Test
    fun `a colour is six or eight hex digits and nothing else`() {
        assertEquals(Color(0xFF123456), ThemeStore.parse("#123456"))
        assertEquals(Color(0x80ABCDEF), ThemeStore.parse("80ABCDEF"))
        for (bad in listOf("-12345", "+12345", "12345G", "1234567", "12345", "", "-1234567", "0x1234"))
            assertNull(ThemeStore.parse(bad), bad)
    }

    @Test
    fun `import takes the eight, or a whole theme, and export gives what import takes`() {
        assertTrue(ThemeStore.import(eight))
        assertEquals(eight, ThemeStore.export())
        assertTrue(ThemeStore.import(ThemeStore.encode(full())))
        assertEquals(full(), ThemeStore.snapshot())
        val shared = ThemeStore.export()
        ThemeStore.show(ThemeStore.FACTORY)
        assertTrue(ThemeStore.import(shared))
        assertEquals(full(), ThemeStore.snapshot())
        // Importing the eight replaces the extras too: a string of eight holds no extras.
        assertTrue(ThemeStore.import(eight))
        assertTrue(ThemeStore.snapshot().plain)
        assertFalse(ThemeStore.import("nonsense"))
        assertEquals(WholeTheme.ofKeys(ThemeStore.decode(eight)!!.keys), ThemeStore.snapshot(), "a refused string changes nothing")
    }

    @Test
    fun `Reset colours puts the factory theme back, extras and all`() {
        val f = File(dir, "prep/theme.txt")
        ThemeStore.load(f)
        ThemeStore.adopt(full())
        assertFalse(ThemeStore.snapshot().plain)
        ThemeStore.reset()
        assertEquals(ThemeStore.FACTORY, ThemeStore.snapshot())
        assertTrue(ThemeStore.snapshot().plain)
        assertNull(Pc.LowerGroundX); assertNull(Pc.HeaderGroundX); assertFalse(Pc.moveTypeBar)
        ThemeStore.show(full()); ThemeStore.load(f)
        assertEquals(ThemeStore.FACTORY, ThemeStore.snapshot(), "and what was saved is the factory theme")
    }

    // ---- Auto themes give the whole theme back ------------------------------------------------------------

    @Test
    fun `release() puts every colour and override of the user's theme back`() {
        ThemeStore.show(full())
        AutoTheme.apply(ThemePresets.PC_CODES[2].second)   // Beach Getaway
        assertNotEquals(full(), ThemeStore.snapshot())
        AutoTheme.release()
        assertEquals(full(), ThemeStore.snapshot())
        assertNull(AutoTheme.current)
    }

    @Test
    fun `an auto theme changing to another keeps the first user theme, not the one before it`() {
        ThemeStore.show(full())
        AutoTheme.apply(ThemePresets.PC_CODES[0].second)
        AutoTheme.apply(ThemePresets.PC_CODES[1].second)
        AutoTheme.release()
        assertEquals(full(), ThemeStore.snapshot())
    }

    @Test
    fun `with no auto theme showing, release() touches nothing`() {
        ThemeStore.show(full())
        AutoTheme.release()
        AutoTheme.onGba(listOf(4 to false), on = false)
        assertEquals(full(), ThemeStore.snapshot())
    }

    @Test
    fun `a plain user theme still comes back plain, as it did`() {
        ThemeStore.show(WholeTheme.ofKeys(ThemeStore.decode(eight)!!.keys))
        AutoTheme.onGba(listOf(1 to false), on = true)
        assertNotNull(Pc.LowerGroundX)
        AutoTheme.onGba(listOf(1 to false), on = false)
        assertNull(Pc.LowerGroundX); assertNull(Pc.HeaderX); assertFalse(Pc.moveTypeBar)
        assertEquals(Color(0xFF111111), Pc.Page)
    }

    private fun assertNotNull(v: Any?) = assertTrue(v != null)
}
