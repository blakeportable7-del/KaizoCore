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
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Both new-run buttons through RunStart and PrepStore.installRun: a run made
 * ahead and a run made there and then get the same rotate, seed, attempt and
 * cleared notes, a chosen seed is never swapped for a staged one, and a run
 * half put in place never lets an old save state through.
 */
class RunStartTest {
    private val filesDir = Files.createTempDirectory("runstart").toFile()
    private val store = PrepStore(filesDir)
    private val kind = RomKind.EMERALD_U
    private val prepared = File(filesDir, "prep/prepared/emerald-u.gba").apply { writeBytes(ByteArray(256) { 7 }) }
    private val settings = File(filesDir, "prep/settings/RSE Kaizo.rnqs").apply { writeBytes(byteArrayOf(1, 2, 3)) }
    private val app = "test build"
    private val prep = File(filesDir, "prep")

    private fun stub(tag: Int): (File, Long) -> Unit = { dest, seed ->
        dest.writeBytes(ByteArray(64) { tag.toByte() })
        Randomizers.logFor(dest).writeText("log $tag seed $seed")
    }

    /** A run in play: its ROM and log, its notes, an attempt counted, a seed saved. */
    private fun oldRun() {
        val cur = store.currentRunFor(kind)
        cur.parentFile.mkdirs(); cur.writeBytes(ByteArray(64) { 1 }); Randomizers.logFor(cur).writeText("old log")
        store.saveLastRun(kind.id, settings.name); store.saveLastSeed(0x0dL); store.bumpAttempt(kind.id)
        for (n in listOf("marks.txt", "notes.txt", "routes.txt")) File(prep, n).writeText("25:1,0,0,0,0,0")
    }

    private fun recipe(prePass: File? = null) = NextRun.recipe(kind, prepared, settings, null, prePass, app)

    @Test
    fun `a run made ahead is taken, with the same bookkeeping a fresh one gets`() {
        oldRun()
        val seedBefore = store.lastSeed(); val runBefore = store.loadLastRun(); val attemptBefore = store.attempt(kind.id)
        store.nextRun.make(recipe(), 0x42L, stub(2))
        // Making the next run touches nothing the run in play is read by (the run clock and
        // history read lastseed, lastrun and the attempt when a game ends): only NEW RUN does.
        assertEquals(seedBefore, store.lastSeed()); assertEquals(runBefore, store.loadLastRun())
        assertEquals(attemptBefore, store.attempt(kind.id))
        assertContentEquals(ByteArray(64) { 1 }, store.currentRunFor(kind).readBytes())
        val started = RunStart.start(store, kind, prepared, settings, seed = null, app = app, prePass = null, secondPass = null,
            take = { store.nextRun.claim(it) }, stop = { fail("no seed was chosen") }, randomize = { _, _ -> fail("a run made ahead was waiting") })
        assertTrue(started.wasStaged)
        assertEquals(0x42L, started.seed)
        assertContentEquals(ByteArray(64) { 2 }, store.currentRunFor(kind).readBytes())
        assertEquals("log 2 seed 66", Randomizers.logFor(store.currentRunFor(kind)).readText())
        assertContentEquals(ByteArray(64) { 1 }, store.previousRunFor(kind).readBytes(), "the old run rotates to previous")
        assertEquals("old log", Randomizers.logFor(store.previousRunFor(kind)).readText())
        assertEquals("%016x".format(0x42L), store.lastSeed())
        assertEquals(kind.id to settings.name, store.loadLastRun())
        assertEquals(2, store.attempt(kind.id))
        assertFalse(File(prep, "marks.txt").exists() || File(prep, "notes.txt").exists(), "the old run's notes go")
        assertEquals("${kind.id}/%016x".format(0x42L), store.runIdentity())

        // The same through a fresh randomize: nothing differs but where the ROM came from.
        val fresh = RunStart.start(store, kind, prepared, settings, seed = null, app = app, prePass = null, secondPass = null,
            take = { store.nextRun.claim(it) }, stop = { fail("no seed was chosen") }, randomize = stub(3))
        assertFalse(fresh.wasStaged)
        assertContentEquals(ByteArray(64) { 3 }, store.currentRunFor(kind).readBytes())
        assertContentEquals(ByteArray(64) { 2 }, store.previousRunFor(kind).readBytes())
        assertEquals("%016x".format(fresh.seed), store.lastSeed())
        assertEquals(3, store.attempt(kind.id))
    }

    @Test
    fun `a seed the player chose is never swapped for a staged run`() {
        oldRun()
        store.nextRun.make(recipe(), 0x42L, stub(2))
        var seen = 0L
        var stopped = false
        val started = RunStart.start(store, kind, prepared, settings, seed = 0x777L, app = app, prePass = null, secondPass = null,
            take = { fail("a chosen seed must not ask for a staged run") }, stop = { stopped = true },
            randomize = { d, s -> seen = s; stub(5)(d, s) })
        assertEquals(0x777L, started.seed); assertEquals(0x777L, seen)
        assertTrue(stopped, "a stage being made is stopped, not waited for")
        assertFalse(started.wasStaged)
        assertContentEquals(ByteArray(64) { 5 }, store.currentRunFor(kind).readBytes())
        assertEquals("%016x".format(0x777L), store.lastSeed())
    }

    @Test
    fun `a stage made from settings since edited is refused and the run is randomized fresh`() {
        oldRun()
        store.nextRun.make(recipe(), 0x42L, stub(2))
        settings.writeBytes(byteArrayOf(9, 9))   // edited under the same name
        val started = RunStart.start(store, kind, prepared, settings, seed = null, app = app, prePass = null, secondPass = null,
            take = { store.nextRun.claim(it) }, stop = { fail("no seed was chosen") }, randomize = stub(6))
        assertFalse(started.wasStaged)
        assertNotEquals(0x42L, started.seed)
        assertContentEquals(ByteArray(64) { 6 }, store.currentRunFor(kind).readBytes())
        assertTrue(store.nextRun.dir.listFiles().orEmpty().isEmpty(), "nothing left staged")
    }

    @Test
    fun `a failed randomize leaves the run in play as it was`() {
        oldRun()
        assertFailsWith<IllegalStateException> {
            RunStart.start(store, kind, prepared, settings, seed = null, app = app, prePass = null, secondPass = null,
                take = { store.nextRun.claim(it) }, stop = { fail("no seed was chosen") }, randomize = { d, _ -> d.writeBytes(ByteArray(3)); error("engine") })
        }
        assertContentEquals(ByteArray(64) { 1 }, store.currentRunFor(kind).readBytes(), "never written over in place")
        assertEquals("%016x".format(0x0dL), store.lastSeed())
        assertEquals(1, store.attempt(kind.id))
        assertTrue(File(prep, "marks.txt").exists())
    }

    @Test
    fun `a run half put in place matches no save state of the run before`() {
        oldRun()
        val stamp = store.runIdentity()
        assertEquals("${kind.id}/%016x".format(0x0dL), stamp)
        // A claimed stage whose ROM has gone: the install fails partway, as a
        // crash between the ROM moving in and the new seed being saved would.
        store.nextRun.make(recipe(), 0x42L, stub(2))
        val staged = store.nextRun.claim(recipe())!!
        staged.rom.delete()
        assertFailsWith<Exception> { store.installRun(kind, settings.name, staged) }
        assertNotEquals(stamp, store.runIdentity(), "the old run's states would load on whatever is in place now")
        assertTrue(store.runIdentity().endsWith("/?"))
    }

    @Test
    fun `flipping the 60 percent levels or PART 2 between staging and NEW RUN throws the stage away`() {
        val pre = File(filesDir, "prep/settings/" + ExtraPasses.RSE_PRE_PASS).apply { writeText("trainer and wild +6%") }
        // Staged with the 60% levels on; RUN's switch is off by NEW RUN.
        oldRun()
        store.nextRun.make(recipe(prePass = pre), 0x42L, stub(2))
        var passUsed: File? = pre
        val off = RunStart.start(store, kind, prepared, settings, seed = null, app = app, prePass = null, secondPass = null,
            take = { store.nextRun.claim(it) }, stop = { fail("no seed was chosen") },
            randomize = { d, s -> passUsed = null; stub(3)(d, s) })
        assertFalse(off.wasStaged, "a ROM built with the pre-pass was handed to a run without it")
        assertEquals(null, passUsed)
        assertContentEquals(ByteArray(64) { 3 }, store.currentRunFor(kind).readBytes())
        // And the other way: staged without it, switched on by NEW RUN.
        store.nextRun.make(recipe(prePass = null), 0x43L, stub(4))
        val on = RunStart.start(store, kind, prepared, settings, seed = null, app = app, prePass = pre, secondPass = null,
            take = { store.nextRun.claim(it) }, stop = { fail("no seed was chosen") }, randomize = stub(5))
        assertFalse(on.wasStaged)
        assertContentEquals(ByteArray(64) { 5 }, store.currentRunFor(kind).readBytes())

        // Gen 1's PART 2, the same both ways.
        val red = RomKind.RED_U
        val redRom = File(filesDir, "prep/prepared/red-u.gb").apply { writeBytes(ByteArray(32) { 9 }) }
        val redSettings = File(filesDir, "prep/settings/RBY Kaizo.rnqs").apply { writeBytes(byteArrayOf(4, 5)) }
        val part2 = store.secondPassSettings(red)!!.apply { parentFile.mkdirs(); writeText("every curve Slow") }
        fun redRecipe(p2: File?) = NextRun.recipe(red, redRom, redSettings, p2, null, app)
        store.nextRun.make(redRecipe(part2), 0x44L, stub(6))
        val noPart2 = RunStart.start(store, red, redRom, redSettings, seed = null, app = app, prePass = null, secondPass = null,
            take = { store.nextRun.claim(it) }, stop = { fail("no seed was chosen") }, randomize = stub(7))
        assertFalse(noPart2.wasStaged, "a ROM with every curve Slow was handed to a run without PART 2")
        store.nextRun.make(redRecipe(null), 0x45L, stub(8))
        val withPart2 = RunStart.start(store, red, redRom, redSettings, seed = null, app = app, prePass = null, secondPass = part2,
            take = { store.nextRun.claim(it) }, stop = { fail("no seed was chosen") }, randomize = stub(9))
        assertFalse(withPart2.wasStaged)
        // Unchanged switches still take the stage.
        store.nextRun.make(redRecipe(part2), 0x46L, stub(10))
        assertTrue(RunStart.start(store, red, redRom, redSettings, seed = null, app = app, prePass = null, secondPass = part2,
            take = { store.nextRun.claim(it) }, stop = { fail("no seed was chosen") }, randomize = { _, _ -> fail("a matching stage was waiting") }).wasStaged)
    }

    @Test
    fun `the run in play keeps the recipe and seed it was made with, and a new run replaces them`() {
        oldRun()
        assertEquals(null, NextRun.currentRecipe(store), "no recipe before a run was installed through NextRun")
        val pre = File(filesDir, "prep/settings/" + ExtraPasses.RSE_PRE_PASS).apply { writeText("trainer and wild +6%") }
        store.nextRun.make(recipe(prePass = pre), 0x42L, stub(2))
        RunStart.start(store, kind, prepared, settings, seed = null, app = app, prePass = pre, secondPass = null,
            take = { store.nextRun.claim(it) }, stop = { fail("no seed was chosen") }, randomize = { _, _ -> fail("staged") })
        val (r1, s1) = NextRun.currentRecipe(store)!!
        assertEquals(0x42L, s1)
        assertEquals(recipe(prePass = pre), r1, "what the run was made with, pre-pass included")
        assertTrue(r1.prePass.startsWith(ExtraPasses.RSE_PRE_PASS))

        // RUN's switch flipped afterwards changes nothing about the run in play.
        val (again, _) = NextRun.currentRecipe(store)!!
        assertEquals(r1, again)

        // A new run made there and then replaces it with its own.
        val fresh = RunStart.start(store, kind, prepared, settings, seed = 0x99L, app = app, prePass = null, secondPass = null,
            take = { fail("chosen seed") }, stop = {}, randomize = stub(3))
        val (r2, s2) = NextRun.currentRecipe(store)!!
        assertEquals(fresh.seed, s2); assertEquals(0x99L, s2)
        assertEquals("", r2.prePass)

        // A recipe that no longer names the seed on disk (a NEW RUN cut off halfway) is not the run's.
        store.saveLastSeed(0x1234L)
        assertEquals(null, NextRun.currentRecipe(store))
    }
}
