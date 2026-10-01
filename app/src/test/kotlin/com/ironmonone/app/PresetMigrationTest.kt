package com.ironmonone.app

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * An install that already has the old DPPt Kaizo and RSE Kaizo gets the
 * settings page's strings on the next launch; an edited copy under the same
 * name keeps its bytes. The old files are the exact copies the app shipped
 * (src/test/resources/presets-before), and seeding runs through the same
 * function PrepStore.seedBundledPresets calls.
 */
class PresetMigrationTest {
    private val presets = File("src/main/assets/presets")
    private val before = File("src/test/resources/presets-before")
    private val names = presets.list { _, n -> n.endsWith(".rnqs") }!!.sorted()
    private fun bundled(n: String): ByteArray? = File(presets, n).takeIf { it.isFile }?.readBytes()
    private val dir = Files.createTempDirectory("settings").toFile()

    @Test
    fun `the copies the app shipped are the ones listed, and the current presets are not`() {
        for ((name, old) in PresetMigration.REPLACED) {
            assertTrue(PresetMigration.sha256(File(before, name).readBytes()) in old, name)
            assertFalse(PresetMigration.sha256(bundled(name)!!) in old, "$name: the bundled copy is the new one")
        }
    }

    @Test
    fun `an old bundled copy is replaced, an edited copy and a current one are left alone`() {
        for (n in PresetMigration.REPLACED.keys) File(before, n).copyTo(File(dir, n))
        val edited = File(before, "RSE Kaizo.rnqs").readBytes().also { it[it.size - 1] = (it[it.size - 1] + 1).toByte() }
        File(dir, "RSE Kaizo (mine).rnqs").writeBytes(edited)
        val added = PresetMigration.seed(dir, names, ::bundled)
        assertEquals(names.size - 2, added, "every other preset is added")
        for (n in PresetMigration.REPLACED.keys) assertContentEquals(bundled(n), File(dir, n).readBytes(), "$n brought up to date")
        assertContentEquals(edited, File(dir, "RSE Kaizo (mine).rnqs").readBytes())
        // A second launch changes nothing.
        val stamp = names.associateWith { File(dir, it).readBytes().toList() }
        assertEquals(0, PresetMigration.seed(dir, names, ::bundled))
        assertEquals(emptyList(), PresetMigration.migrate(dir, ::bundled))
        assertEquals(stamp, names.associateWith { File(dir, it).readBytes().toList() })
    }

    @Test
    fun `a player's own file under the old name is never overwritten`() {
        val mine = File(before, "DPPt Kaizo.rnqs").readBytes().copyOf().also { it[20] = (it[20] + 1).toByte() }
        File(dir, "DPPt Kaizo.rnqs").writeBytes(mine)
        PresetMigration.seed(dir, names, ::bundled)
        assertContentEquals(mine, File(dir, "DPPt Kaizo.rnqs").readBytes())
    }
}
