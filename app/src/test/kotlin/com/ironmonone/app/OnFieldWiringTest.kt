package com.ironmonone.app

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * rc33 audit P1: in a battle the own card, Calc Atk, the enemy's matchups, the lethal sword and the stream used party
 * slot 1, which after a switch (or a lead fainted at the start) is not the Pokemon on the field. The trackers name it
 * (TrackerState.onField); these are the places that must read it.
 */
class OnFieldWiringTest {
    private fun src(p: String) = File(p).readText()

    @Test
    fun `every battle view reads the Pokemon on the field`() {
        // Since rc34 through GbaViewState, which is TrackerState.onField unless a double battle's right-hand one is viewed.
        val panel = src("src/main/kotlin/com/ironmonone/app/TrackerPanel.kt")
        val view = src("src/main/kotlin/com/ironmonone/app/DoublesView.kt")
        assertTrue("fun own(state: TrackerState?): TrackedMon? = state?.let { if (ownBattler(it) == 2) it.ownRight else it.onField }" in view)
        assertTrue("listOfNotNull(view.own(state)).take(" in panel, "the own card")
        assertTrue("enemyMoveContext(enemy, view.foeTarget(state)," in panel, "the enemy's matchups")
        assertTrue("LastAttack.lethal(state.lastAttackDamage, view.own(state)?.mon?.curHp)" in panel, "the lethal sword")
        assertTrue("val own = gbaView.own(s) ?: return null" in src("src/main/kotlin/com/ironmonone/app/CalcAtkScreen.kt"), "Calc Atk")
        // The stream: the Pokemon on the field by default, the one the phone shows in a double battle (rc34).
        val stream = src("src/main/kotlin/com/ironmonone/app/stream/StreamSnapshot.kt")
        assertTrue("target: TrackedMon? = s.onField," in stream && "enemyMoveContext(e, target," in stream, "the stream")
        assertFalse("enemyMoveContext(enemy, state.party.firstOrNull()" in panel)
    }
}
