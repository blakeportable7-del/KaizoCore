package com.ironmonone.app

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The move-category glyphs, checked against the reference's own source.
 *
 * These were invented once already - a filled dot and a ring, coloured by the
 * move's type - where the reference draws two specific 7x7 pixel images in
 * plain white. Transcribed pixel art silently rots, so it is diffed against
 * Constants.lua rather than trusted.
 */
class CategoryIconTest {

    private val TAB = "" + 9.toChar()
    private val OPEN = "" + '{'
    private val CLOSE = "" + 10.toChar() + 9.toChar() + "},"


    private val constants = File(
        System.getProperty("user.home"),
        "ironmon-ref/Ironmon-Tracker/ironmon_tracker/Constants.lua",
    )

    /** Pull a `NAME = { {..}, {..} }` pixel image out of Constants.lua. */
    private fun glyph(name: String): List<List<Int>> {
        val text = constants.readText()
        val start = text.indexOf("\t$name = {")
        assertTrue(start >= 0, "$name not found in ${constants.absolutePath}")
        val end = text.indexOf("\n\t},", start)
        return Regex("[{]([01,]+)[}]").findAll(text.substring(start, end))
            .map { m -> m.groupValues[1].split(",").filter { it.isNotBlank() }.map(String::toInt) }
            .toList()
    }

    @Test
    fun `the physical and special icons are the reference's, pixel for pixel`() {
        if (!constants.exists()) {
            println("SKIP: reference tracker not at ${constants.absolutePath}")
            return
        }
        assertEquals(glyph("PHYSICAL"), PcCategoryGlyphs.PHYSICAL.map { it.toList() })
        assertEquals(glyph("SPECIAL"), PcCategoryGlyphs.SPECIAL.map { it.toList() })
        // Both are 7x7, as the reference's own comment says.
        assertEquals(7, PcCategoryGlyphs.PHYSICAL.size)
        assertTrue(PcCategoryGlyphs.PHYSICAL.all { it.size == 7 })
    }
}
