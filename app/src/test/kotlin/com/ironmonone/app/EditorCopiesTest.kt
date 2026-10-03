package com.ironmonone.app

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * RC35-NOTICED N #12, the rest of rc32 audit P2 #32: the settings editor kept its unsaved edits in a remembered settings
 * object, and a process death while the app was in the background reopened the editor on the file without them. The
 * edited copy is kept in the saved state as its settings string now, and read back by its engine.
 */
class EditorCopiesTest {
    private val presets = File("src/main/assets/presets")

    @Test
    fun `an unsaved edit outlives the process, and the file stays the baseline`() {
        for (name in listOf("FRLG Kaizo.rnqs", "FRLG NatDex v1.2 Kaizo.rnqs")) {
            val file = File(presets, name)
            val loaded = assertNotNull(EditorCopies.load(file), name)
            val (working, _, cls) = loaded
            val before = GameBuild.get(cls, working, "startersMod")
            val edit = if (before == "COMPLETELY_RANDOM") "UNCHANGED" else "COMPLETELY_RANDOM"
            GameBuild.set(cls, working, "startersMod", edit)
            val saved = assertNotNull(EditorCopies.save(loaded), name)
            val back = assertNotNull(EditorCopies.restore(file, saved), name)
            assertEquals(cls, back.third, "$name: the same engine")
            assertEquals(edit, GameBuild.get(cls, back.first, "startersMod"), "$name: the edit is back")
            assertEquals(before, GameBuild.get(cls, back.second, "startersMod"), "$name: the baseline is the file as it is")
        }
    }

    @Test
    fun `a saved copy that does not fit the file is the file as it is`() {
        val zx = File(presets, "FRLG Kaizo.rnqs")
        val natDex = File(presets, "FRLG NatDex v1.2 Kaizo.rnqs")
        val fromNatDex = assertNotNull(EditorCopies.save(EditorCopies.load(natDex)))
        val fresh = assertNotNull(EditorCopies.load(zx))
        val back = assertNotNull(EditorCopies.restore(zx, fromNatDex), "another engine's copy")
        assertEquals(fresh.third, back.third)
        assertEquals(fresh.first.toString(), back.first.toString())
        val junk = assertNotNull(EditorCopies.restore(zx, fresh.third.name + "\nnot a settings string"))
        assertEquals(fresh.first.toString(), junk.first.toString(), "a string that will not read")
        val src = File("src/main/kotlin/com/ironmonone/app/EditorScreen.kt").readText()
        assertTrue("rememberSaveable(file, stateSaver = EditorCopies.saver(file)) { mutableStateOf(EditorCopies.load(file)) }" in src)
        assertTrue("var savedString by rememberSaveable { mutableStateOf<String?>(null) }" in src, "what was last saved, for Leave without saving")
    }
}
