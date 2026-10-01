package com.ironmonone.app

import com.ironmonone.app.engine.Randomizers
import com.ironmonone.core.RomKind
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The next run made ahead (NextRun): taken only by the run it was made for,
 * never half-written, never once anything it was made from has changed.
 * The engine is a stub that writes what Randomizers.randomize leaves beside
 * a ROM; the real randomize is exercised on the emulator.
 */
class NextRunTest {
    private val root = Files.createTempDirectory("nextrun").toFile()
    private val stage = NextRun(File(root, "next"))
    private val recipe = NextRun.Recipe(
        kind = "emerald-u", ext = "gba", engine = "zx-4.6.1", app = "1.0 38 100",
        rom = "/data/prep/library/emerald-u.gba", romSize = 16777216, romTime = 1000, romCrc = "1f1c08fb",
        settings = "RSE Kaizo.rnqs", settingsSha = "ab12", secondPass = "", prePass = "",
    )

    /** What Randomizers.randomize leaves: the ROM, its log beside it, and a DS sidecar when asked. */
    private fun engine(bytes: Int = 64, sidecar: Boolean = false): (File, Long) -> Unit = { dest, seed ->
        dest.writeBytes(ByteArray(bytes) { (seed + it).toByte() })
        Randomizers.logFor(dest).writeText("log of $seed")
        if (sidecar) Randomizers.sidecarFor(dest).writeText("1\tBULBASAUR")
    }

    private fun files(d: File = stage.dir) = d.listFiles()?.map { it.name }?.sorted() ?: emptyList()

    @Test
    fun `a stage is taken by the recipe it was made for, with its seed and its files`() {
        stage.make(recipe, 0x1234L, engine(sidecar = true))
        val s = assertNotNull(stage.ready(recipe))
        assertEquals(0x1234L, s.seed)
        assertEquals(64L, s.rom.length())
        assertEquals("log of 4660", s.log?.readText())
        assertNotNull(s.sidecar)
    }

    @Test
    fun `any difference in what it was made from throws the stage away`() {
        val changed = listOf(
            recipe.copy(kind = "firered-u-v11"), recipe.copy(ext = "nds"), recipe.copy(engine = "natdex-1.2.1"),
            recipe.copy(app = "1.0 39 200"), recipe.copy(rom = "/data/prep/prepared/emerald-u.gba"),
            recipe.copy(romSize = 1), recipe.copy(romTime = 2000), recipe.copy(romCrc = "00000000"),
            recipe.copy(settings = "RSE Standard.rnqs"), recipe.copy(settingsSha = "cd34"),
            recipe.copy(secondPass = "RBY PART 2.rnqs ef56"), recipe.copy(prePass = "RSE PRE-PASS.rnqs 0a0b"),
        )
        assertEquals(12, changed.toSet().size, "one change per field")
        for (other in changed) {
            stage.make(recipe, 7L, engine())
            assertNull(stage.ready(other), "taken for $other")
            assertTrue(files().isEmpty(), "a stage for another recipe is deleted, not kept: ${files()}")
            stage.make(recipe, 7L, engine())
            assertNull(stage.claim(other), "claimed for $other")
            assertTrue(files().isEmpty())
        }
    }

    @Test
    fun `the recipe follows the settings file's content, the prepared ROM and the app build`() {
        val dir = File(root, "in").apply { mkdirs() }
        val rom = File(dir, "emerald-u.gba").apply { writeBytes(ByteArray(128)) }
        val settings = File(dir, "RSE Kaizo.rnqs").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val a = NextRun.recipe(RomKind.EMERALD_U, rom, settings, null, null, "build 1")
        assertEquals(a, NextRun.recipe(RomKind.EMERALD_U, rom, settings, null, null, "build 1"))
        settings.writeBytes(byteArrayOf(1, 2, 4))   // edited in the editor, saved under the same name
        val b = NextRun.recipe(RomKind.EMERALD_U, rom, settings, null, null, "build 1")
        assertNotEquals(a, b)
        assertNotEquals(b, NextRun.recipe(RomKind.EMERALD_U, rom, settings, null, null, "build 2"))
        rom.setLastModified(rom.lastModified() - 60_000)   // prepared again
        assertNotEquals(b, NextRun.recipe(RomKind.EMERALD_U, rom, settings, null, null, "build 1"))
        val part2 = File(dir, Randomizers.GEN1_SECOND_PASS).apply { writeText("part 2") }
        val c = NextRun.recipe(RomKind.YELLOW_U, rom, settings, part2, null, "build 1")
        part2.writeText("part 2, edited")
        assertNotEquals(c, NextRun.recipe(RomKind.YELLOW_U, rom, settings, part2, null, "build 1"))
        // PART 2 switched off, and the 60% levels' pre-pass switched on or edited.
        assertNotEquals(c, NextRun.recipe(RomKind.YELLOW_U, rom, settings, null, null, "build 1"))
        val pre = File(dir, "RSE PRE-PASS.rnqs").apply { writeText("pre") }
        val d = NextRun.recipe(RomKind.EMERALD_U, rom, settings, null, pre, "build 1")
        assertNotEquals(NextRun.recipe(RomKind.EMERALD_U, rom, settings, null, null, "build 1"), d)
        pre.writeText("pre, edited")
        assertNotEquals(d, NextRun.recipe(RomKind.EMERALD_U, rom, settings, null, pre, "build 1"))
    }

    @Test
    fun `a stage cut off before it finished is never taken, and is deleted`() {
        // The folder as it stands at the moment of a kill mid-randomize: part of
        // the ROM written, and no meta yet, because the meta is written last.
        val killed = File(root, "killed")
        stage.make(recipe, 9L, { dest, _ ->
            dest.writeBytes(ByteArray(32))
            Randomizers.logFor(dest).writeText("partial")
            stage.dir.copyRecursively(killed)
            dest.appendBytes(ByteArray(32))
        })
        assertFalse(File(killed, "next.meta").exists(), "no meta before the files are done")
        assertNull(NextRun(killed).ready(recipe))
        assertTrue(files(killed).isEmpty(), "what the kill left is deleted: ${files(killed)}")

        // A kill while the meta itself was being written leaves its .tmp, never a meta.
        assertNotNull(stage.ready(recipe))
        assertTrue(File(stage.dir, "next.meta").renameTo(File(stage.dir, "next.meta.tmp")))
        assertNull(stage.ready(recipe))
        assertTrue(files().isEmpty(), files().toString())
    }

    @Test
    fun `a stage whose files are not the size its meta says is refused`() {
        stage.make(recipe, 5L, engine())
        File(stage.dir, "next.gba").appendBytes(byteArrayOf(1))
        assertNull(stage.ready(recipe))
        assertTrue(files().isEmpty())

        stage.make(recipe, 5L, engine())
        Randomizers.logFor(File(stage.dir, "next.gba")).writeText("the log of some other run")
        assertNull(stage.ready(recipe), "the log is checked too")
        assertTrue(files().isEmpty())
    }

    @Test
    fun `a randomize that fails leaves nothing behind, and one that is not asked for makes nothing`() {
        assertFailsWith<IllegalStateException> {
            stage.make(recipe, 1L, { dest, _ -> dest.writeBytes(ByteArray(8)); error("engine stopped") })
        }
        assertTrue(files().isEmpty())
        assertFailsWith<java.io.IOException> { stage.make(recipe, 1L, { _, _ -> }) }
        assertTrue(files().isEmpty())
        stage.make(recipe, 2L, engine())
        assertNull(stage.make(recipe, 3L, engine(), proceed = { false }))
        assertEquals(2L, stage.ready(recipe)?.seed, "a stage not asked for does not replace the one there")
    }

    @Test
    fun `a claimed run is out of reach of the next stage, and installs with its own log and sidecar only`() {
        val runs = File(root, "runs").apply { mkdirs() }
        val cur = File(runs, "current.nds")
        cur.writeText("old rom"); Randomizers.logFor(cur).writeText("old log"); Randomizers.sidecarFor(cur).writeText("old sidecar")
        val ds = recipe.copy(ext = "nds")
        stage.make(ds, 3L, engine(bytes = 100, sidecar = true))
        val want = File(stage.dir, "next.nds").readBytes()
        val s = assertNotNull(stage.claim(ds))
        assertNull(stage.claim(ds), "a stage is taken once")
        // Making or clearing the next stage now must not touch the claimed one.
        stage.make(recipe, 4L, engine())
        assertNull(stage.ready(ds))
        stage.install(s, cur)
        assertContentEquals(want, cur.readBytes())
        assertEquals("log of 3", Randomizers.logFor(cur).readText())
        assertEquals("1\tBULBASAUR", Randomizers.sidecarFor(cur).readText())
        assertFalse(File(stage.dir, "taken").exists())

        // A run with no log or sidecar takes the old ones away with it.
        stage.make(ds, 8L, { dest, _ -> dest.writeBytes(ByteArray(10)) })
        stage.install(assertNotNull(stage.claim(ds)), cur)
        assertEquals(10L, cur.length())
        assertFalse(Randomizers.logFor(cur).exists(), "the old run's log would describe the wrong seed")
        assertFalse(Randomizers.sidecarFor(cur).exists(), "the old run's species would be read for the new one")
    }
}
