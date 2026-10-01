package com.ironmonone.app

import com.ironmonone.core.Platform
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Loading a state never changes the in-game save (2026-09-30). The DS half is here; the Game Boy half is native. */
class SaveGuardTest {
    private val dir = Files.createTempDirectory("saveguard").toFile()
    private val save = File(dir, "heartgold-u.sav")
    private val newer = ByteArray(512) { 7 }
    private val older = ByteArray(512) { 3 }
    private var now = 0L

    @AfterTest fun cleanUp() { dir.deleteRecursively() }

    /** A watch whose worker runs on the test's own thread, with a clock that moves as it sleeps. */
    private fun watch(onSleep: (Long) -> Unit = {}): SaveGuard.DsWatch = SaveGuard.DsWatch(
        save, clock = { now }, sleep = { ms -> now += ms; onSleep(now) }, startWorker = { it.run() })

    @Test
    fun `melonDS writing the older save from the state is undone, and a copy of the newer one is kept`() {
        save.writeBytes(newer)
        // melonDS's flush lands 600 ms after the load.
        val w = watch { t -> if (t == 600L) save.writeBytes(older) }
        w.beforeStateLoad(); w.afterStateLoad(true)
        assertContentEquals(newer, save.readBytes(), "the in-game save is the one from before the load")
        assertContentEquals(newer, w.backup.readBytes())
    }

    @Test
    fun `a write after the window is the player's own save and stays`() {
        save.writeBytes(newer)
        val w = watch()
        w.beforeStateLoad(); w.afterStateLoad(true)   // nothing changed within the window: the watch ends
        now += SaveGuard.WINDOW_MS + 1
        save.writeBytes(older)
        assertContentEquals(older, save.readBytes())
    }

    @Test
    fun `only the first change is undone, so an in-game save made later is not`() {
        save.writeBytes(newer)
        val third = ByteArray(512) { 9 }
        val w = watch { t -> if (t == 300L) save.writeBytes(older) }
        w.beforeStateLoad(); w.afterStateLoad(true)
        assertContentEquals(newer, save.readBytes())
        save.writeBytes(third)   // the watch has ended; nothing puts it back
        assertContentEquals(third, save.readBytes())
    }

    @Test
    fun `a failed load, or a game with no save yet, changes nothing`() {
        val w = watch()
        w.beforeStateLoad(); w.afterStateLoad(true)
        assertTrue(!save.exists() && !w.backup.exists(), "no save, nothing to keep")
        save.writeBytes(newer)
        val w2 = watch { t -> if (t == 200L) save.writeBytes(older) }
        w2.beforeStateLoad(); w2.afterStateLoad(false)
        assertTrue(!w2.backup.exists(), "a load the core refused is not watched")
    }

    @Test
    fun `rewind's loads in a row keep the save from before the first one`() {
        save.writeBytes(newer)
        var started = 0
        val w = SaveGuard.DsWatch(save, clock = { now }, sleep = { now += it }, startWorker = { started++ })
        w.beforeStateLoad(); w.afterStateLoad(true)
        save.writeBytes(older)                      // the first load's flush, while the watch has not run yet
        w.beforeStateLoad(); w.afterStateLoad(true) // a second load inside the window
        assertEquals(1, started, "one worker at a time")
        assertContentEquals(newer, w.backup.readBytes(), "still the save from before the first load")
        w.watch()
        assertContentEquals(newer, save.readBytes())
    }

    @Test
    fun `only DS games are watched, and the file is the ROM's name with sav`() {
        val rom = File(dir, "heartgold-u.nds")
        assertNotNull(SaveGuard.listener(Platform.NDS, dir, rom))
        assertNull(SaveGuard.listener(Platform.GBA, dir, rom), "mGBA never rolls the save back")
        assertNull(SaveGuard.listener(Platform.GBC, dir, rom), "gambatte is kept in native code")
        assertEquals(File(dir, "heartgold-u.sav"), SaveGuard.dsSaveFile(dir, rom))
        assertEquals(File(dir, "current.sav"), SaveGuard.dsSaveFile(dir, File(dir, "current.nds")))
    }

    @Test
    fun `every state load goes through the listener, and the Game Boy save is put back in native code`() {
        val view = File("../libretrodroid/src/main/java/com/swordfish/libretrodroid/GLRetroView.kt").readText().replace("\r\n", "\n")
        val load = view.substringAfter("fun unserializeState(").substringBefore("\n    }\n")
        assertTrue("listener?.beforeStateLoad()" in load && "listener?.afterStateLoad(loaded)" in load)
        val native = File("../libretrodroid/src/main/cpp/libretrodroid.cpp").readText().replace("\r\n", "\n")
        val body = native.substringAfter("bool LibretroDroid::unserializeState(").substringBefore("\n}\n")
        assertTrue("keptSram.assign(sramBefore, sramBefore + sramSize)" in body, "the save before the load is copied")
        assertTrue("memcpy(sramAfter, keptSram.data(), keptSram.size())" in body, "and put back after it")
        val play = File("src/main/kotlin/com/ironmonone/app/PlayScreen.kt").readText()
        assertTrue("view.stateLoadListener = SaveGuard.listener(platform, File(ctx.filesDir, \"saves\"), rom)" in play)
    }
}
