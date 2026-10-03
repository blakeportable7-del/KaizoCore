package com.ironmonone.app

import com.ironmonone.core.Platform
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** A save file with a game in it, told apart from one that only exists (emulator QA, 2026-09-29). */
class SaveCheckTest {
    private fun gen3Save(sections: Int = 14): ByteArray = ByteArray(128 * 1024) { 0xFF.toByte() }.also { b ->
        for (s in 0 until sections) {
            val at = s * 4096 + 0xFF8
            b[at] = 0x25; b[at + 1] = 0x20; b[at + 2] = 0x01; b[at + 3] = 0x08
        }
    }

    @Test
    fun `a Gen 3 save is a game in progress only when its sections carry the game's signature`() {
        assertTrue(SaveCheck.hasProgress(gen3Save(), Platform.GBA))
        assertTrue(SaveCheck.hasProgress(gen3Save(sections = 1), Platform.GBA), "one written section is enough")
        assertFalse(SaveCheck.hasProgress(ByteArray(128 * 1024) { 0xFF.toByte() }, Platform.GBA), "erased flash")
        assertFalse(SaveCheck.hasProgress(ByteArray(128 * 1024), Platform.GBA), "all zero")
        assertFalse(SaveCheck.hasProgress(ByteArray(128 * 1024) { (it * 7).toByte() }, Platform.GBA), "noise with no signature")
        assertFalse(SaveCheck.hasProgress(ByteArray(100), Platform.GBA), "too short to hold a section")
    }

    @Test
    fun `any other save counts when it is not blank`() {
        assertFalse(SaveCheck.hasProgress(ByteArray(32 * 1024), Platform.GBC))
        assertFalse(SaveCheck.hasProgress(ByteArray(32 * 1024) { 0xFF.toByte() }, Platform.GBC))
        assertTrue(SaveCheck.hasProgress(ByteArray(32 * 1024).also { it[500] = 3 }, Platform.GBC))
    }

    @Test
    fun `no file, no progress, and a file is read from disk`() {
        val dir = Files.createTempDirectory("savecheck").toFile()
        try {
            assertFalse(SaveCheck.hasProgress(File(dir, "missing.srm"), Platform.GBA))
            val f = File(dir, "game.srm").apply { writeBytes(gen3Save()) }
            assertTrue(SaveCheck.hasProgress(f, Platform.GBA))
        } finally { dir.deleteRecursively() }
    }

    @Test
    fun `the Nuzlocke screen warns only about a real save, not about an auto-save or a blank file`() {
        val screen = File("src/main/kotlin/com/ironmonone/app/NuzlockeScreen.kt").readText()
        // A DS game's save is melonDS's own .sav, not the .srm (rc32 audit P2 #37, NuzlockeStartsTest).
        assertTrue("SaveCheck.hasProgress(NuzlockeStarts.inGameSave(filesDir, s, store.sramFile(s)), s.platform)" in screen)
        assertFalse("store.slotFile(s, 0).isFile" in screen, "the auto-save is written whenever a game is left, title screen included")
    }
}
