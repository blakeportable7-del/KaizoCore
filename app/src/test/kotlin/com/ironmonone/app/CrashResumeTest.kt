package com.ironmonone.app

import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * The resume after the app closed mid-game (CrashResume): the run comes back
 * from its own auto slot and nothing else, once, on record, and a resume
 * that killed the app is not repeated.
 */
class CrashResumeTest {
    private val filesDir = Files.createTempDirectory("resume").toFile()
    private val marker = File(filesDir, "prep/playing.txt")
    private val run = GameSession.forRun(File(filesDir, "prep/runs/current.gba"), com.ironmonone.core.RomKind.EMERALD_U)
    private val slot = StateSlots.auto(filesDir, run)
    private val events = RunEvents(File(filesDir, "prep/integrity.txt"))
    private val stamp = "emerald-u/00000000000000aa"

    private fun saved(bytes: ByteArray = byteArrayOf(1, 2, 3), stampedFor: String = stamp) {
        slot.file.parentFile.mkdirs(); slot.file.writeBytes(bytes); slot.stamp.writeText(stampedFor)
    }

    private fun atCoreUp(loadsAllowed: Boolean = true, load: suspend (ByteArray) -> Boolean): String? = runBlocking {
        CrashResume.atCoreUp(marker, run, slot, stamp, loadsAllowed, events, why = { "the app crashed" }, load = load)
    }

    @Test
    fun `a game the process died with comes back from its auto slot, once, and on record`() {
        saved(byteArrayOf(7, 7))
        CrashResume.playing(marker, run.id)
        var loaded: ByteArray? = null
        val said = atCoreUp { loaded = it; true }
        assertContentEquals(byteArrayOf(7, 7), loaded)
        assertTrue(said!!.startsWith("Back where you were"))
        assertEquals(CrashResume.Left(run.id, resuming = false), CrashResume.parse(marker), "marked open again, not resuming")
        val e = events.entries().single()
        assertEquals(RunEvents.Kind.RESUME, e.kind); assertEquals("auto", e.slot)
        assertTrue("the app crashed" in e.detail)
        // The same process bringing a core up again (NEW RUN) is not a launch.
        assertEquals("Auto-save from ${slot.savedLabel()}: File > States > Resume.", atCoreUp { fail("resumed twice") })
    }

    @Test
    fun `a state stamped for another randomization is never loaded`() {
        saved(stampedFor = "emerald-u/00000000000000bb")
        CrashResume.playing(marker, run.id)
        assertNull(atCoreUp { fail("another run's state would load under this run's tables") })
        assertTrue(events.entries().isEmpty())
        assertEquals(CrashResume.Left(run.id, false), CrashResume.parse(marker))
    }

    @Test
    fun `a resume that killed the app is not tried again`() {
        saved()
        CrashResume.resuming(marker, run.id)
        val said = atCoreUp { fail("a state that crashes the core on load would crash every launch") }
        assertTrue(said!!.startsWith("The auto-save did not load"))
        assertEquals(CrashResume.Left(run.id, false), CrashResume.parse(marker))
    }

    @Test
    fun `no resume after a normal close, for another game, or in hardcore`() {
        saved()
        // Closed the normal way: no marker.
        assertEquals("Auto-save from ${slot.savedLabel()}: File > States > Resume.", atCoreUp { fail("no marker, no resume") })
        // The marker named a library game.
        val other = File(filesDir, "prep/playing2.txt").apply { writeText("playing lib-1f1c08fb") }
        assertEquals(CrashResume.Plan.NONE, CrashResume.plan(CrashResume.leftover(other), run.id, usable = true, loadsAllowed = true))
        // RetroAchievements hardcore refuses every load.
        assertEquals(CrashResume.Plan.NONE, CrashResume.plan(CrashResume.Left(run.id, false), run.id, usable = true, loadsAllowed = false))
        assertEquals(CrashResume.Plan.RESUME, CrashResume.plan(CrashResume.Left(run.id, false), run.id, usable = true, loadsAllowed = true))
    }

    @Test
    fun `leaving the game clears the marker, an activity Android destroys to bring back keeps it`() {
        CrashResume.playing(marker, run.id)
        CrashResume.left(marker, finishing = false, destroyed = false)   // another tab
        assertNull(CrashResume.parse(marker))
        CrashResume.playing(marker, run.id)
        CrashResume.left(marker, finishing = true, destroyed = true)     // the app finishing
        assertNull(CrashResume.parse(marker))
        // Destroyed without finishing: Android recreates the activity in this
        // same process, whose first core-up has already read the marker.
        saved(byteArrayOf(4))
        CrashResume.playing(marker, run.id)
        CrashResume.leftover(marker)
        CrashResume.left(marker, finishing = false, destroyed = true)
        var loaded: ByteArray? = null
        assertTrue(atCoreUp { loaded = it; true }!!.startsWith("Back where you were"))
        assertContentEquals(byteArrayOf(4), loaded)
    }

    @Test
    fun `a core that refuses the state leaves the slot and says so`() {
        saved()
        CrashResume.playing(marker, run.id)
        assertEquals("Could not load the auto-save. It is still in File > States.", atCoreUp { false })
        assertTrue(events.entries().isEmpty(), "nothing was loaded, so nothing is on record")
        assertTrue(slot.exists)
    }

    // ------------------------------------------------------------------ coming back the normal way (2026-09-30, UX audit P0-4)

    @Test
    fun `a game left the normal way opens where it was left, with nothing on the run's record`() {
        saved(byteArrayOf(4, 4, 4))
        slot.leftMark.writeText("1790000000000")   // AutoSave writes it with the snapshot taken as the game is left
        var loaded: ByteArray? = null
        val said = atCoreUp { loaded = it; true }
        assertContentEquals(byteArrayOf(4, 4, 4), loaded)
        assertEquals(CrashResume.RETURNED, said)
        assertTrue(events.entries().isEmpty(), "nothing was rewound, so nothing counts against the run")
        assertEquals(CrashResume.Left(run.id, resuming = false), CrashResume.parse(marker), "marked open, not resuming")
        // IronMON rules check R14: opened there once, the mark goes, so the same moment is not opened again by itself.
        assertFalse(slot.leftMark.exists(), "the left mark is used once")
        CrashResume.closed(marker)
        assertEquals("Auto-save from ${slot.savedLabel()}: File > States > Resume.", atCoreUp { fail("an older moment, opened again without a word") })
    }

    @Test
    fun `an auto-save that is not the moment the game was left is only offered, as before`() {
        saved()
        assertEquals("Auto-save from ${slot.savedLabel()}: File > States > Resume.", atCoreUp { fail("a periodic state is up to three minutes back") })
    }

    @Test
    fun `coming back never loads another run's state, never in hardcore, and never twice after it killed the app`() {
        saved(stampedFor = "emerald-u/00000000000000bb")
        slot.leftMark.writeText("1790000000000")
        assertNull(atCoreUp { fail("another randomization's state") })
        saved()
        assertEquals("Auto-save from ${slot.savedLabel()}: File > States > Resume.", atCoreUp(loadsAllowed = false) { fail("loads are off") })
        // The load that killed the app left the marker at "resuming": the next launch leaves the slot alone.
        CrashResume.resuming(marker, run.id)
        val fresh = File(filesDir, "prep/playing2.txt").also { marker.copyTo(it) }
        val said = runBlocking {
            CrashResume.atCoreUp(fresh, run, slot, stamp, true, events, why = { "the app crashed" }, load = { fail("tried again") })
        }
        assertTrue(said!!.startsWith("The auto-save did not load"))
    }

    @Test
    fun `a load the core refuses says so and leaves the game at the title screen`() {
        saved()
        slot.leftMark.writeText("1790000000000")
        assertEquals("Could not load the auto-save. It is still in File > States.", atCoreUp { false })
        assertTrue(events.entries().isEmpty())
    }
}
