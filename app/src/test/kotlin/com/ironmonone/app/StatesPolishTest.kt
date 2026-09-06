package com.ironmonone.app

import android.view.KeyEvent
import com.ironmonone.core.RomKind
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StatesPolishTest {

    private fun run(filesDir: File) = GameSession.forRun(File(filesDir, "prep/runs/current.gba"), RomKind.EMERALD_U)

    @Test
    fun `the auto-save has its own file and never collides with a numbered slot`() {
        val d = Files.createTempDirectory("f").toFile()
        val auto = StateSlots.auto(d, run(d))
        assertTrue(auto.isAuto)
        assertEquals(File(d, "saves/autosave.bin"), auto.file)
        assertTrue(StateSlots.list(d, run(d)).none { it.file == auto.file })
        assertEquals("Auto-save", auto.title())
    }

    @Test
    fun `lock is a marker, backup keeps the overwritten state and restores once`() {
        val d = Files.createTempDirectory("f").toFile()
        val s = StateSlots.list(d, run(d))[1]
        s.file.parentFile.mkdirs()
        assertFalse(s.locked); s.setLocked(true); assertTrue(s.locked); s.setLocked(false); assertFalse(s.locked)

        s.file.writeBytes(byteArrayOf(1, 1, 1)); s.thumb.writeBytes(byteArrayOf(9))
        s.keepBackup()
        s.file.writeBytes(byteArrayOf(2, 2))
        assertTrue(s.hasBackup)
        assertEquals(listOf<Byte>(1, 1, 1), s.backup.readBytes().toList())
        assertTrue(s.restoreBackup())
        assertEquals(listOf<Byte>(1, 1, 1), s.file.readBytes().toList(), "the old state is back")
        assertEquals(listOf<Byte>(2, 2), s.backup.readBytes().toList(), "and the one it replaced is the backup now")
        assertEquals(listOf<Byte>(9), s.thumb.readBytes().toList(), "thumbnail travels with it")
    }

    @Test
    fun `an action binding takes a key away from a button and survives a reload`() {
        val f = File(Files.createTempDirectory("k").toFile(), "keys.txt")
        val kb = KeyBindings(f)
        assertEquals(KeyEvent.KEYCODE_X, kb.keyFor(KeyBindings.Button.A))
        kb.bindAction(KeyBindings.Action.QUICK_SAVE, KeyEvent.KEYCODE_X)
        assertEquals(0, kb.keyFor(KeyBindings.Button.A).let { if (it == KeyEvent.KEYCODE_X) -1 else 0 }, "A no longer holds X")
        assertEquals(KeyBindings.Action.QUICK_SAVE, KeyBindings.activeActions[KeyEvent.KEYCODE_X])
        val again = KeyBindings(f)
        assertEquals(KeyEvent.KEYCODE_X, again.keyForAction(KeyBindings.Action.QUICK_SAVE))
        again.bind(KeyBindings.Button.A, KeyEvent.KEYCODE_X)
        assertNull(again.keyForAction(KeyBindings.Action.QUICK_SAVE), "binding a button takes the key back")
        assertNull(KeyBindings.activeActions[KeyEvent.KEYCODE_X])
        again.resetToDefaults()
        assertTrue(KeyBindings.activeActions.isEmpty())
    }
}
