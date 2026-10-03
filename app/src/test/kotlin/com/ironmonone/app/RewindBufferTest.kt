package com.ironmonone.app

import com.ironmonone.core.Platform
import com.ironmonone.core.RomKind
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RewindBufferTest {
    @Test
    fun `keeps the newest N, pops newest first, ignores empty states`() {
        val b = RewindBuffer(3)
        b.push(byteArrayOf()); assertEquals(0, b.size)
        (1..5).forEach { b.push(byteArrayOf(it.toByte())) }
        assertEquals(3, b.size); assertEquals(3L, b.bytes)
        assertEquals(5, b.pop()!![0].toInt()); assertEquals(4, b.pop()!![0].toInt()); assertEquals(3, b.pop()!![0].toInt())
        assertNull(b.pop())
    }

    /**
     * rc32 audit P2 #52: a library DS game recorded a state every two seconds on the main thread, six of them kept on
     * the Java heap beside the restore points.
     */
    @Test
    fun `a byte cap keeps the newest, and the recorder takes its states off the main thread`() {
        val b = RewindBuffer(6, maxBytes = 24L * 1024 * 1024)
        (1..6).forEach { i -> b.push(ByteArray(10 * 1024 * 1024) { i.toByte() }) }
        assertTrue(b.bytes <= 24L * 1024 * 1024, "${b.bytes}")
        assertEquals(2, b.size)
        assertEquals(6, b.pop()!![0].toInt()); assertEquals(5, b.pop()!![0].toInt()); assertNull(b.pop())
        val one = RewindBuffer(6, maxBytes = 1)
        one.push(ByteArray(5)); assertEquals(1, one.size, "never fewer than the newest")
        assertTrue(RewindBuffer.forPlatform(Platform.NDS).maxBytes < RewindBuffer.forPlatform(Platform.GBA).maxBytes)
        val play = File("src/main/kotlin/com/ironmonone/app/PlayScreen.kt").readText().replace("\r\n", "\n")
        assertFalse("r.serializeState(useEmulationThread = false)" in play, "no state taken on the main thread for rewind")
        assertTrue("rewind.record(r, RewindBuffer.policy(platform).second) {" in play)
        assertTrue("battleStartState = AutoSave.battleStart(retro)" in play, "nor at a battle's start")
        val rec = File("src/main/kotlin/com/ironmonone/app/RewindBuffer.kt").readText()
        assertTrue("withContext(kotlinx.coroutines.Dispatchers.IO) { AutoSave.snapshot(view, quiet = true) }" in rec)
    }

    @Test
    fun `policy and the tracked gate`() {
        assertEquals(60 to 500L, RewindBuffer.policy(Platform.GBA))
        assertEquals(6 to 2000L, RewindBuffer.policy(Platform.NDS))
        assertFalse(RewindBuffer.allowed(GameSession.forRun(File("/x/c.gba"), RomKind.FIRERED_U_V11), nuzlocke = false), "a Kaizo IronMON run")
        assertTrue(RewindBuffer.allowed(GameSession(File("/x/h.gba"), Platform.GBA, null, "h", "lib-1", isRun = false), nuzlocke = false))
        val plainTracked = GameSession(File("/x/fr.gba"), Platform.GBA, RomKind.FIRERED_U_V11, "fr", "lib-2", isRun = false)
        assertTrue(RewindBuffer.allowed(plainTracked, nuzlocke = false), "plain play, tracker and all (2026-09-30)")
        assertFalse(RewindBuffer.allowed(plainTracked, nuzlocke = true), "a Nuzlocke")
    }
}
