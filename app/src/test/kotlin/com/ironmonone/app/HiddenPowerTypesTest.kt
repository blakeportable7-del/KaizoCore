package com.ironmonone.app

import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Hidden Power's type as InfoScreen's arrows set it (MoveData.HiddenPowerTypeList). */
class HiddenPowerTypesTest {
    private val dir = Files.createTempDirectory("hp").toFile()
    private val pid = 0x2A6B41C7L
    @AfterTest fun cleanup() { dir.deleteRecursively() }

    @Test
    fun `the arrows walk Fighting to Dark and wrap through unknown, never Normal`() {
        HiddenPowerTypes.load(java.io.File(dir, "hp.txt"))
        assertNull(HiddenPowerTypes.of(pid))
        HiddenPowerTypes.next(pid); assertEquals(1, HiddenPowerTypes.of(pid))    // Fighting
        repeat(15) { HiddenPowerTypes.next(pid) }; assertEquals(17, HiddenPowerTypes.of(pid)) // Dark
        HiddenPowerTypes.next(pid); assertNull(HiddenPowerTypes.of(pid))         // back to unknown
        HiddenPowerTypes.prev(pid); assertEquals(17, HiddenPowerTypes.of(pid))   // unknown <- Dark
        assertEquals(false, 0 in HiddenPowerTypes.CYCLE, "Normal is not a Hidden Power type")
        assertEquals(17, HiddenPowerTypes.CYCLE.size)
    }

    @Test
    fun `a pick survives a restart`() {
        val f = java.io.File(dir, "hp.txt")
        HiddenPowerTypes.load(f)
        HiddenPowerTypes.next(pid); HiddenPowerTypes.next(pid)                   // Flying
        HiddenPowerTypes.load(f)
        assertEquals(2, HiddenPowerTypes.of(pid))
    }
}
