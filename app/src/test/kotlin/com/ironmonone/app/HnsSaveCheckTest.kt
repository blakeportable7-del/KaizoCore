package com.ironmonone.app

import com.ironmonone.core.RomKind
import com.ironmonone.tracker.GbaTracker
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Heart & Soul as a full KaizoCore game (docs/NEW-GAME-CHECKLIST.md, 2026-10-05): the new seed's save check reads Heart & Soul's party count.
 */
class HnsSaveCheckTest {
    private val hns = RomKind.HEARTSOUL_KAIZO_206
    private val books = File("src/main/assets/rulesets")



    @Test
    fun `a new seed reads whether Heart and Soul's save holds a team`() {
        val layout = File("src/main/assets/hns/layout-kaizo.json").readText()
        val sb1 = layout.substringAfter("\"SaveBlock1\":").substringAfter("\"playerPartyCount\":")
        assertEquals(SaveCheck.HNS_PARTY_COUNT, Regex("\"offset\":\\s*(\\d+)").find(sb1)!!.groupValues[1].toInt())
        fun save(count: Int): File {
            val b = ByteArray(14 * 4096 * 2)
            for (s in 0 until 14) {
                val at = s * 4096
                fun w16(i: Int, v: Int) { b[i] = v.toByte(); b[i + 1] = (v shr 8).toByte() }
                fun w32(i: Int, v: Long) { for (k in 0 until 4) b[i + k] = (v shr (8 * k)).toByte() }
                w16(at + 0xFF4, s); w32(at + 0xFF8, 0x08012025L); w32(at + 0xFFC, 7L)
            }
            b[4096 + SaveCheck.HNS_PARTY_COUNT] = count.toByte()
            return File.createTempFile("hns", ".srm").apply { writeBytes(b); deleteOnExit() }
        }
        assertEquals(RunSaves.Plan.HOLDS_TEAM, RunSaves.plan(save(1), hns))
        assertEquals(RunSaves.Plan.NO_TEAM, RunSaves.plan(save(0), hns))
        // Read at Emerald's offset, the same bytes said nothing useful.
        assertEquals(0, SaveCheck.gen3PartyCount(save(1).readBytes(), frlg = false))
    }
}
