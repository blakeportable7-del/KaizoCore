package com.ironmonone.app

import com.ironmonone.core.RomKind
import com.ironmonone.tracker.GbaTracker
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Heart & Soul as a full KaizoCore game (docs/NEW-GAME-CHECKLIST.md, 2026-10-05): the log viewer's sixteen gyms, gym TMs and rematches.
 */
class HnsLogViewerTest {
    private val hns = RomKind.HEARTSOUL_KAIZO_206
    private val books = File("src/main/assets/rulesets")



    @Test
    fun `the log knows Heart and Soul's sixteen gyms, their TMs and its rematches`() {
        val layout = File("src/main/assets/hns/layout-kaizo.json").readText()
        fun trainer(n: String) = Regex("\"TRAINER_$n\":\\s*(\\d+)").find(layout)!!.groupValues[1].toInt()
        val gyms = LogTrainerRules.HNS_GYMS
        assertEquals(1, gyms[trainer("FALKNER_1_HNS")]); assertEquals(8, gyms[trainer("CLAIR_1_HNS")])
        assertEquals(9, gyms[trainer("BROCK_HNS")]); assertEquals(16, gyms[trainer("BLUE_HNS")])
        assertEquals((1..16).toSet(), gyms.values.toSet(), "every badge has its leader")
        // The rematches after the Elite Four, and nothing else, are left out.
        val postgame = Regex("\"TRAINER_\\w+_POSTOBC_HNS\":\\s*(\\d+)").findAll(layout).map { it.groupValues[1].toInt() }.toSet()
        assertEquals(postgame + 0, LogTrainerRules.ranges(LogTrainerRules.HNS_EXCLUDED))
        // The gym TMs are the build's machines for the moves each leader gives.
        val machines = Regex("\\{\"kind\":\\s*\"TM\",\\s*\"num\":\\s*(\\d+),\\s*\"move\":\\s*\\d+,\\s*\"moveName\":\\s*\"MOVE_(\\w+)\"").findAll(layout)
            .associate { it.groupValues[2] to it.groupValues[1].toInt() }
        val moves = listOf("ROOST", "U_TURN", "ATTRACT", "SHADOW_BALL", "FOCUS_PUNCH", "IRON_TAIL", "HAIL", "DRAGON_PULSE",
            "ROCK_SLIDE", "WATER_PULSE", "SHOCK_WAVE", "GIGA_DRAIN", "SKILL_SWAP", "POISON_JAB", "OVERHEAT", "TRICK_ROOM")
        assertEquals(moves.map { machines.getValue(it) }, LogTms.HNS_GYM_TMS)
        // Badges one to sixteen in HeartGold and SoulSilver's art.
        assertEquals("badges/HGSS_badge1.png", PcAssets.badgePath(PcAssets.HNS_BADGES, 1, true))
        assertEquals("badges/HGSS_K_badge1_OFF.png", PcAssets.badgePath(PcAssets.HNS_BADGES, 9, false))
        assertEquals("badges/HGSS_K_badge8.png", PcAssets.badgePath(PcAssets.HNS_BADGES, 16, true))
        for (i in 1..16) assertTrue(File("src/main/assets/" + PcAssets.badgePath(PcAssets.HNS_BADGES, i, true)).isFile, "badge $i")
    }
}
