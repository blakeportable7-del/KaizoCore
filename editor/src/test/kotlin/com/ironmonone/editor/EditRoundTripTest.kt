package com.ironmonone.editor

import com.dabomstew.pkrandomzx.Settings
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Editing a bundled preset, saving it, and reading it back.
 *
 * The reported case is "I edited it to change it so all TMs can be learned":
 * open a preset, flip TM/HM compatibility to FULL, save, and expect that file
 * to randomize and play. This drives that path through the SAME reflective
 * Option the editor screen uses, against the real bundled .rnqs and the real
 * randomizer Settings, and asserts the edit survives the write/read round
 * trip instead of silently reverting.
 */
class EditRoundTripTest {

    private val preset = File(
        "C:/Users/bepor/IronMonOne/app/src/main/assets/presets/FRLG Kaizo.rnqs"
    )

    private fun load(f: File): Settings =
        FileInputStream(f).use { Settings.read(it) }

    private fun tmCompatOption(): Option.Choice {
        val opts = SettingsReflector.options(Settings::class.java)
        val c = opts.filterIsInstance<Option.Choice>()
            .firstOrNull {
                it.id.contains("tmsHms", ignoreCase = true) &&
                    it.id.contains("compat", ignoreCase = true)
            }
        assertNotNull(
            c,
            "editor exposes no TM/HM compatibility choice; tm-ish ids: " +
                opts.map { it.id }.filter { it.contains("tm", true) },
        )
        return c
    }

    @Test
    fun `the bundled FRLG preset opens with the vanilla engine`() {
        assertTrue(preset.exists(), "bundled preset missing: $preset")
        assertNotNull(load(preset))
    }

    @Test
    fun `TM-HM compatibility offers FULL and it survives a save`() {
        val opt = tmCompatOption()
        assertTrue(
            opt.values.contains("FULL"),
            "FULL not offered; values are ${opt.values}",
        )

        val s = load(preset)
        val before = opt.get(s)
        opt.set(s, "FULL")
        assertEquals("FULL", opt.get(s), "setting FULL did not stick in memory")

        val tmp = Files.createTempFile("edited", ".rnqs").toFile()
        FileOutputStream(tmp).use { s.write(it) }
        assertTrue(tmp.length() > 0, "saved preset is empty")

        val reread = load(tmp)
        assertEquals(
            "FULL", opt.get(reread),
            "TM/HM compatibility did not survive the save (was $before)",
        )
        tmp.delete()
    }

    @Test
    fun `an edited preset keeps the rest of the ruleset intact`() {
        val opt = tmCompatOption()
        val original = load(preset)
        val edited = load(preset)
        opt.set(edited, "FULL")

        val tmp = Files.createTempFile("edited2", ".rnqs").toFile()
        FileOutputStream(tmp).use { edited.write(it) }
        val reread = load(tmp)

        // Every OTHER mode enum must be untouched: changing TM compatibility
        // must not quietly reset the ruleset around it.
        val choices = SettingsReflector.options(Settings::class.java)
            .filterIsInstance<Option.Choice>()
            .filter { it.id != opt.id }
        var checked = 0
        for (c in choices) {
            assertEquals(
                c.get(original), c.get(reread),
                "${c.id} changed after editing TM compatibility",
            )
            checked++
        }
        assertTrue(checked >= 20, "expected the ~22 mode enums, checked $checked")
        tmp.delete()
    }
}
