package com.ironmonone.app

import com.ironmonone.core.RomKind
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Run codes on the Run tab (RunCodes): the code is made from the run's own recipe, and a pasted
 * code is built only when this phone has the game set up, the same settings (by content, not by
 * name) and passes that fit; the build is compared with the sharer's checksum.
 */
class RunCodesTest {
    private val dir: File = Files.createTempDirectory("runcodes").toFile()
    private val settingsBytes = "RSE Kaizo settings bytes".toByteArray()
    private val settings = File(dir, "RSE Kaizo.rnqs").apply { writeBytes(settingsBytes) }
    private val emeraldBase = File(dir, "emerald-u.gba").apply { writeBytes(ByteArray(16)) }
    private val redBase = File(dir, "red-u.gbc").apply { writeBytes(ByteArray(16)) }
    private val prepared = listOf(RomKind.EMERALD_U to emeraldBase, RomKind.RED_U to redBase)
    private val sha = RunCode.sha256(settings)

    private fun recipe(prePass: String = "", secondPass: String = "") = NextRun.Recipe(
        kind = "emerald-u", ext = "gba", engine = "zx", app = "test", rom = emeraldBase.path, romSize = 16L, romTime = 0L,
        romCrc = "00000000", settings = "RSE Kaizo.rnqs", settingsSha = sha, secondPass = secondPass, prePass = prePass,
    )

    @Test fun `the code is the run's recipe, whatever the switches say now`() {
        val code = RunCodes.shareCodeFor(recipe(prePass = "RSE PRE-PASS.rnqs abc"), 0x5261db990e333467L, 0xa1b2c3d4L)
        assertEquals("emerald-u", code.game)
        assertEquals(sha.take(8).toLong(16), code.settingsHash)
        assertTrue(code.prePass); assertTrue(!code.part2)
        assertEquals(0xa1b2c3d4L, code.romCrc)
        val text = RunCodes.shareText(code, "Pokémon Emerald (U)", 12)
        assertEquals(code.text(), text.lines().first(), "the code on a line of its own")
        assertTrue("my attempt 12" in text)
    }

    @Test fun `a code builds when the game and the same settings are here, under any name`() {
        val renamed = File(dir, "my copy.rnqs").apply { writeBytes(settingsBytes) }
        val other = File(dir, "RSE Standard.rnqs").apply { writeBytes("something else".toByteArray()) }
        val code = RunCodes.shareCodeFor(recipe(prePass = "x y"), 42L, 7L)
        val plan = RunCodes.plan("from a friend: " + code.text() + ".", prepared, listOf(other, renamed)) { RunCode.sha256(it) }
        val ready = assertIs<RunCodes.Plan.Ready>(plan)
        assertEquals(renamed, ready.settings, "matched by content")
        assertEquals(emeraldBase, ready.prepared)
        assertEquals(42L, ready.code.seed); assertTrue(ready.code.prePass)
    }

    @Test fun `and says why when it cannot`() {
        fun why(text: String, prep: List<Pair<RomKind, File>> = prepared, s: List<File> = listOf(settings)) =
            assertIs<RunCodes.Plan.Refused>(RunCodes.plan(text, prep, s) { RunCode.sha256(it) }).why
        val good = RunCodes.shareCodeFor(recipe(), 1L, 2L)
        assertTrue("starts with KC1" in why("seed 5261db990e333467"))
        assertTrue("newer KaizoCore" in why(good.copy(passes = 4).text()))
        assertTrue("does not know" in why(good.copy(game = "sapphire-jp").text()))
        // A game as it is comes in under My games; a patched build is made on Patched versions (2026-09-30, UX audit P0-10).
        assertEquals(
            "Add Pokémon Emerald (U) first (Library, My games), then paste the code again.",
            why(good.text(), prep = listOf(RomKind.RED_U to redBase)),
        )
        assertEquals(
            "Make ${RomKind.EMERALD_NATDEX_121.displayName} first (Library, Patched versions), then paste the code again.",
            why(good.copy(game = RomKind.EMERALD_NATDEX_121.id).text(), prep = listOf(RomKind.RED_U to redBase)),
        )
        assertEquals(
            "Make ${RomKind.EMERALD_SMARTAI.displayName} first (Library, Patched versions), then paste the code again.",
            why(good.copy(game = RomKind.EMERALD_SMARTAI.id).text(), prep = listOf(RomKind.RED_U to redBase)),
        )
        assertTrue(listOf("Set up a game", "All files").none { it in why(good.text(), prep = emptyList()) }, "the old page names are gone")
        assertTrue("You do not have the settings" in why(good.text(), s = emptyList()))
        assertTrue("PART 2" in why(good.copy(passes = RunCode.PART_2).text()), "PART 2 is Red, Blue and Yellow's")
        assertTrue("60% levels" in why(good.copy(game = "red-u", passes = RunCode.PRE_PASS).text()), "Red takes no pre-pass")
    }

    @Test fun `the build is compared with the sharer's checksum`() {
        val code = RunCodes.shareCodeFor(recipe(), 1L, 0xa1b2c3d4L)
        assertTrue(RunCodes.verdict(code, 0xa1b2c3d4L).startsWith("Same game as theirs"))
        assertTrue(RunCodes.verdict(code, 0x0badf00dL).startsWith("Built, but it is not the same game"))
        assertTrue("cannot be compared" in RunCodes.verdict(code.copy(romCrc = 0L), 5L))
    }
}
