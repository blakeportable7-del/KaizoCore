package com.ironmonone.pc

import com.ironmonone.app.engine.HnsEngine
import java.io.File
import java.io.FileInputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * jar.lock (2026-10-09): the script knows KaizoCore PC is alive because a read of jar.lock is refused, not from a time in
 * jar.txt rewritten every second (each new jar.txt cost BizHawk's thread a Defender scan, about 20 ms). While a session
 * runs the lock is held and a second KaizoCore PC for the folder does not start; jar.txt is not rewritten while nothing in
 * it changes; after quit the lock is gone.
 */
class JarLockTest {
    private fun waitFor(ms: Long, what: String, cond: () -> Boolean) {
        val end = System.currentTimeMillis() + ms
        while (!cond()) {
            if (System.currentTimeMillis() > end) throw AssertionError("timed out waiting for $what")
            Thread.sleep(20)
        }
    }

    private fun readOneByte(f: File): Boolean = runCatching { FileInputStream(f).use { it.read() } }.isSuccess

    @Test
    fun `a running session holds jar lock, keeps jar txt still, and lets go on quit`() {
        val comfort = TestFiles.comfortBuild ?: return println("JarLockTest skipped: no hns-kaizo.gba")
        val build = PcHns.build(comfort.readBytes())
        val root = TestFiles.tempDir("jarlock")
        val dir = File(root, "session")
        val settings = PcHns.settingsFile(PcHns.DEFAULT_PRESET)
        val s = Session(build, settings, PcHns.options(settings), HnsEngine.Pool.NATDEX, File(root, "runs"), File(root, "runs"), dir,
            "A, B, Start", ahead = 1, keepLogs = 10)
        val t = Thread({ s.run() }, "kc-lock").apply { start() }
        val lock = File(dir, Session.JAR_LOCK)
        val jarTxt = File(dir, "jar.txt")
        try {
            waitFor(60_000, "jar.txt") { jarTxt.isFile }
            assertTrue(lock.isFile, "jar.lock is there")
            assertFalse(readOneByte(lock), "a read of jar.lock is refused while KaizoCore PC runs")
            assertTrue(jarTxt.readText().lines().contains("lock=1"))
            // The run made ahead changes ready=; once it is made, nothing in jar.txt changes, so the file stays as it is.
            waitFor(60_000, "the run made ahead") { s.readyRuns >= 1 }
            Thread.sleep(600)
            val before = jarTxt.lastModified() to jarTxt.readText()
            Thread.sleep(2_500)
            assertEquals(before, jarTxt.lastModified() to jarTxt.readText(), "jar.txt is not rewritten while nothing in it changes")

            val second = Session(build, settings, PcHns.options(settings), HnsEngine.Pool.NATDEX, File(root, "runs2"), File(root, "runs2"), dir,
                "A, B, Start", ahead = 1, keepLogs = 10)
            assertFalse(second.run(), "a second KaizoCore PC for the folder does not start")
            assertFalse(readOneByte(lock), "and the first still holds the lock")
        } finally {
            File(dir, "quit").writeText("1")
            t.join(15_000)
        }
        assertFalse(t.isAlive, "session stopped")
        assertTrue(!lock.isFile || readOneByte(lock), "after quit the lock is gone")
        root.deleteRecursively()
    }
}
