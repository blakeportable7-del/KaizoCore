package com.ironmonone.app

import com.ironmonone.core.Platform
import com.ironmonone.core.RomKind
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CheatStoreTest {

    @Test
    fun `cheats round-trip per game, multi-line codes included`() {
        val s = CheatStore(Files.createTempDirectory("cheats").toFile())
        val list = listOf(
            CheatStore.Cheat("Walk through walls", "C518E2CE 7FF6ACD5\n6F0D8D95 6AF2F7A2", true),
            CheatStore.Cheat("Rare candies", "82025BD0 0044", false),
        )
        s.save("lib-deadbeef", list)
        assertEquals(list, s.load("lib-deadbeef"))
        assertTrue(s.load("lib-other").isEmpty(), "per game")
        s.save("lib-deadbeef", emptyList())
        assertTrue(s.load("lib-deadbeef").isEmpty())
    }

    @Test
    fun `never in a run or a Nuzlocke, and on in plain play`() {
        val run = GameSession.forRun(File("/x/current.gba"), RomKind.FIRERED_U_V11)
        assertFalse(CheatStore.allowed(run, nuzlocke = false))
        val plainTracked = GameSession(File("/x/fr.gba"), Platform.GBA, RomKind.FIRERED_U_V11, "fr", "lib-2", isRun = false)
        assertTrue(CheatStore.allowed(plainTracked, nuzlocke = false), "a plain game from the library, tracker and all")
        assertFalse(CheatStore.allowed(plainTracked, nuzlocke = true), "the same game with a Nuzlocke on it")
        val hack = GameSession(File("/x/hack.gba"), Platform.GBA, null, "hack", "lib-1", isRun = false)
        assertTrue(CheatStore.allowed(hack, nuzlocke = false))
    }

    @Test
    fun `codes are cleaned and joined the way the cores read them`() {
        assertEquals("C518E2CE 7FF6ACD5+6F0D8D95 6AF2F7A2",
            CheatStore.normalise(" c518e2ce 7ff6acd5 \n\n6f0d8d95 6af2f7a2\n", Platform.GBA))
        assertEquals("010138CD", CheatStore.normalise("010138cd", Platform.GBC))
        assertEquals("ABC-DEF-123", CheatStore.normalise("abc-def-123", Platform.GBC), "Game Genie keeps its dashes")
        assertNull(CheatStore.normalise("  \n ", Platform.NDS))
        assertNull(CheatStore.normalise("hello", Platform.GBA), "not a code, not the single hex digit E")
        assertNull(CheatStore.normalise("c518e2ce 7ff6acd5\nmaster code", Platform.GBA), "one bad line refuses the code")
    }

    /**
     * Crash reports, Samsung, 2026-11-14: melonDS's retro_cheat_set copies every word of a code into a buffer of 128
     * with no bound and runs it there and then. A DS code is whole Action Replay lines or nothing.
     */
    @Test
    fun `a DS code is whole Action Replay lines, no more than melonDS holds`() {
        assertEquals("02000000 00000001+1209D2E0 00000063",
            CheatStore.normalise("02000000 00000001\n1209d2e0 00000063", Platform.NDS))
        assertEquals("02000000 00000001", CheatStore.normalise("0200000000000001", Platform.NDS), "16 digits run together")
        assertNull(CheatStore.normalise("02000000", Platform.NDS), "half a line")
        assertNull(CheatStore.normalise("02000000 00000001\n1209D2E0", Platform.NDS), "an odd word out")
        assertNull(CheatStore.normalise("0200 00000001", Platform.NDS), "a short word")
        val most = (1..CheatStore.DS_MAX_LINES).joinToString("\n") { "02000000 %08X".format(it) }
        assertEquals(CheatStore.DS_MAX_LINES, CheatStore.normalise(most, Platform.NDS)!!.split('+').size)
        assertNull(CheatStore.normalise(most + "\n02000000 00000000", Platform.NDS), "one line past melonDS's buffer")
    }

    @Test
    fun `the tracker on a second display never takes the game's buttons, and cheats never hold the screen`() {
        val second = java.io.File("src/main/kotlin/com/ironmonone/app/SecondScreen.kt").readText()
        assertTrue("FLAG_NOT_FOCUSABLE" in second && "setCancelable(false)" in second)
        val view = java.io.File("../libretrodroid/src/main/java/com/swordfish/libretrodroid/GLRetroView.kt").readText().replace("\r\n", "\n")
        val setCheats = view.substringAfter("fun setCheats(").substringBefore("private val cheatTicket")
        assertFalse("runOnEmulationThread" in setCheats, "applying cheats is not waited for")
        assertTrue("queueEvent" in setCheats)
    }
}
