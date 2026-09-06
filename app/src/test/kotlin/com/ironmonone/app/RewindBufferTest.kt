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

    @Test
    fun `policy and the tracked gate`() {
        assertEquals(60 to 500L, RewindBuffer.policy(Platform.GBA))
        assertEquals(6 to 2000L, RewindBuffer.policy(Platform.NDS))
        assertFalse(RewindBuffer.allowed(GameSession.forRun(File("/x/c.gba"), RomKind.FIRERED_U_V11)))
        assertTrue(RewindBuffer.allowed(GameSession(File("/x/h.gba"), Platform.GBA, null, "h", "lib-1", isRun = false)))
    }
}
