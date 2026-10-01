package com.ironmonone.app

import com.ironmonone.tracker.GameMap
import com.ironmonone.tracker.GbaTracker
import com.ironmonone.tracker.MemoryReader
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The staged screens show what a real game could (Blake, 2026-10-01, on the staged lab: "Why is the repel logo on this?"). */
class DemoStagesTest {
    @Test
    fun `the staged lab has no repel running, the staged walk does`() {
        val t = GbaTracker(MemoryReader { _, n -> ByteArray(n) }, GameMap.EMERALD_U)
        val lab = Demo.gba(t, "gba-lab")
        assertTrue(lab.inLab && lab.partyCount == 0)
        assertFalse(lab.repelVisible, "a new game has no repel to use before its first Pokemon")
        assertTrue(Demo.gba(t, "gba-walk").repelVisible)
    }
}
