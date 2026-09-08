package com.ironmonone.app

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

class PadSkinTest {
    @Test
    fun `parses loosely, defaults to outline, round-trips through its file`() {
        assertEquals(PadSkin.MODERN, PadSkin.parse(" modern "))
        assertEquals(PadSkin.OUTLINE, PadSkin.parse("nope"))
        assertEquals(PadSkin.OUTLINE, PadSkin.parse(null))
        assertEquals(PadSkin.MODERN, PadSkin.CLASSIC.next()); assertEquals(PadSkin.OUTLINE, PadSkin.MODERN.next()); assertEquals(PadSkin.CLASSIC, PadSkin.OUTLINE.next())
        val f = File(Files.createTempDirectory("skin").toFile(), "skin.txt")
        assertEquals(PadSkin.OUTLINE, PadSkin.load(f))
        PadSkin.save(f, PadSkin.MODERN)
        assertEquals(PadSkin.MODERN, PadSkin.load(f))
    }
}
