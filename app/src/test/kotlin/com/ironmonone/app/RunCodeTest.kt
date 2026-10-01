package com.ironmonone.app

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Run codes (RunCode): a run shared as text, rebuilt from the friend's own dump. */
class RunCodeTest {
    private val code = RunCode("emerald-u", 0x3f2a9c1eL, 0x5261db990e333467L, RunCode.PRE_PASS, 0xa1b2c3d4L, "RSE Kaizo.rnqs")

    @Test fun `a code reads back as itself`() {
        val text = code.text()
        assertEquals("KC1-emerald-u-3f2a9c1e-5261db990e333467-1-a1b2c3d4-RSE_Kaizo", text)
        assertEquals(code.copy(settingsName = "RSE_Kaizo"), RunCode.parse(text))
    }

    @Test fun `what chat apps do to a pasted code is tolerated`() {
        val back = RunCode.parse("try this one: kc1-EMERALD-U-3F2A9C1E-5261DB990E333467-1-A1B2C3D4-RSE_Kaizo.  good luck")
        assertEquals(code.copy(settingsName = "RSE_Kaizo"), back)
        // Game ids with several parts and seeds with the top bit set.
        val nd = RunCode("firered-natdex-121", 1L, -1L, 0, 0L, "FRLG NatDex v1.2 Kaizo.rnqs")
        assertEquals(nd.copy(settingsName = "FRLG_NatDex_v1.2_Kaizo"), RunCode.parse(nd.text()))
    }

    @Test fun `the passes the run was made with travel in the code`() {
        assertEquals(0, RunCode.passesOf(prePass = false, part2 = false))
        val red = RunCode("red-u", 2L, 3L, RunCode.passesOf(prePass = false, part2 = true), 4L, "RBY Kaizo.rnqs")
        val back = RunCode.parse(red.text())!!
        assertTrue(back.part2); assertFalse(back.prePass); assertFalse(back.unknownPasses)
        val crystal = RunCode.parse(RunCode("crystal-u", 2L, 3L, RunCode.PRE_PASS, 4L, "GSC Kaizo.rnqs").text())!!
        assertTrue(crystal.prePass); assertFalse(crystal.part2)
        assertTrue(RunCode.parse("KC1-emerald-u-3f2a9c1e-5261db990e333467-4-a1b2c3d4-x")!!.unknownPasses, "a pass from a newer app")
    }

    @Test fun `anything else is not a code`() {
        assertNull(RunCode.parse("KC1-emerald-u-3f2a9c1e-5261db990e33346-1-a1b2c3d4-x"), "a 15-digit seed")
        assertNull(RunCode.parse("KC1-emerald-u-3f2a9c1e-5261db990e333467-a1b2c3d4-x"), "no passes digit")
        assertNull(RunCode.parse("KC2-emerald-u-3f2a9c1e-5261db990e333467-1-a1b2c3d4-x"))
        assertNull(RunCode.parse("seed 5261db990e333467"))
    }

    @Test fun `file checksums are CRC32`() {
        val f = File(Files.createTempDirectory("runcode").toFile(), "x.bin")
        f.writeBytes("123456789".toByteArray())
        assertEquals(0xCBF43926L, RunCode.crc32(f), "the CRC32 check value")
    }
}
