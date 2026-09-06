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
    fun `never on a tracked game`() {
        val tracked = GameSession.forRun(File("/x/current.gba"), RomKind.FIRERED_U_V11)
        assertFalse(CheatStore.allowed(tracked))
        val hack = GameSession(File("/x/hack.gba"), Platform.GBA, null, "hack", "lib-1", isRun = false)
        assertTrue(CheatStore.allowed(hack))
    }

    @Test
    fun `codes are cleaned and joined the way the cores read them`() {
        assertEquals("C518E2CE 7FF6ACD5+6F0D8D95 6AF2F7A2",
            CheatStore.normalise(" c518e2ce 7ff6acd5 \n\n6f0d8d95 6af2f7a2\n", Platform.GBA))
        assertEquals("010138CD", CheatStore.normalise("010138cd", Platform.GBC))
        assertEquals("ABC-DEF-123", CheatStore.normalise("abc-def-123", Platform.GBC), "Game Genie keeps its dashes")
        assertNull(CheatStore.normalise("  \n ", Platform.NDS))
    }
}
