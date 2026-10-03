package com.ironmonone.app

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Play's words. rc32 audit P3 #52 and #53: status lines used a spaced hyphen as a dash and said ROM in prose, and the
 * engine's message for a Nat. Dex mode on a standard game did both ("vanilla" too). P3 #63: a refused auto-save was
 * called "Slot 0", a name the app shows nowhere.
 */
class PlayCopyTest {
    private fun src(name: String) = File("src/main/kotlin/com/ironmonone/app/$name").readText().replace("\r\n", "\n")

    /** The string literals on lines that are code, not comments. */
    private fun literals(code: String): List<String> = code.lines()
        .filterNot { it.trimStart().startsWith("//") || it.trimStart().startsWith("*") }
        .flatMap { line -> Regex("\"((?:[^\"\\\\]|\\\\.)*)\"").findAll(line).map { it.groupValues[1] } }

    @Test
    fun `no line Play shows uses a spaced hyphen as a dash or says ROM`() {
        val shown = literals(src("PlayScreen.kt"))
        assertTrue(shown.size > 100, "the sweep reads the file's strings")
        for (s in shown) {
            assertFalse(Regex("\\w - \\w").containsMatchIn(s), "a spaced hyphen: $s")
            assertFalse(Regex("\\bROM\\b").containsMatchIn(s), "ROM in prose: $s")
        }
        val play = src("PlayScreen.kt")
        assertTrue("status = \"Could not save: the game is not running.\"" in play)
        assertTrue("status = \"Sprites read from your game: \$n.\"" in play)
    }

    @Test
    fun `the engine's mode message reads like the run screen's`() {
        val zx = src("engine/ZxEngine.kt")
        for (s in literals(zx)) {
            assertFalse(Regex("\\w - \\w").containsMatchIn(s), "a spaced hyphen: $s")
            assertFalse("vanilla" in s.lowercase(), "vanilla: $s")
        }
        assertTrue("is a mode for the Nat. Dex version of this game. Pick a standard " in zx)
    }

    @Test
    fun `a refused load names the slot as the app does, the auto-save never as Slot 0`() {
        val other = "emerald-u/00000000000000bb"; val here = "emerald-u/00000000000000aa"
        assertEquals("The auto-save is from a different run, so it was not loaded.", StateSlots.loadRefusal(StateSlots.AUTO, other, here))
        assertEquals("The auto-save is from an older version, so it was not loaded.", StateSlots.loadRefusal(StateSlots.AUTO, null, here))
        assertEquals("The auto-save is empty.", StateSlots.emptyLine(StateSlots.AUTO))
        assertEquals("Slot 3 is from a different run, so it was not loaded.", StateSlots.loadRefusal(3, other, here))
        assertNull(StateSlots.loadRefusal(3, here, here))
        val play = src("PlayScreen.kt")
        assertFalse("\"Slot \$which" in play, "every refusal goes through StateSlots")
        assertFalse("slot \$slot." in play && "Could not read slot" in play)
    }

}
