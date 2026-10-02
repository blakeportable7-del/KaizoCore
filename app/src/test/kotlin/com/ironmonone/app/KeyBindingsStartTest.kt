package com.ironmonone.app

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * rc33 audit P1: saved key bindings and controller actions were published only when More > Controls built its
 * KeyBindings, so after a restart a remapped pad played on the defaults until that screen was opened. The app now
 * builds one as it starts, from the same file Controls writes.
 */
class KeyBindingsStartTest {
    @Test
    fun `reading the file publishes the player's own bindings`() {
        val f = File(Files.createTempDirectory("keys").toFile(), KeyBindings.FILE)
        f.parentFile.mkdirs()
        val button = KeyBindings.Button.entries.first()
        f.writeText("${button.name}=4242\n")
        KeyBindings(f)
        assertEquals(button.coreKey, KeyBindings.active[4242])
    }

    @Test
    fun `the app reads the bindings as it starts, from the file Controls writes`() {
        fun src(name: String) = File("src/main/kotlin/com/ironmonone/app/$name").readText().replace("\r\n", "\n")
        assertTrue("KeyBindings(java.io.File(filesDir, KeyBindings.FILE))" in src("MainActivity.kt"))
        assertTrue("KeyBindings(File(context.filesDir, KeyBindings.FILE))" in src("ControlsScreen.kt"))
    }
}
