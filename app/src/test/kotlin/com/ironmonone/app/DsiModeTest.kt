package com.ironmonone.app

import com.ironmonone.core.Platform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DsiModeTest {
    @Test
    fun `ready only with every file, and the switch values are real catalogue values`() {
        assertFalse(DsiMode.ready { false })
        assertEquals(listOf("dsi_nand.bin"), DsiMode.missing { it != "dsi_nand.bin" })
        assertTrue(DsiMode.ready { true })
        val known = CoreOptions.forPlatform(Platform.NDS).associateBy { it.key }
        for ((k, v) in DsiMode.ON + DsiMode.OFF) {
            val o = known[k] ?: error("$k is not a DS option")
            assertTrue(v in o.values, "$k=$v")
        }
        assertTrue(DsiMode.isOn(DsiMode.ON)); assertFalse(DsiMode.isOn(DsiMode.OFF)); assertFalse(DsiMode.isOn(emptyMap()))
        assertTrue(CoreOptions.systemFiles(Platform.NDS).map { it.first }.containsAll(DsiMode.FILES.map { it.first }))
    }
}
