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
        val panel = src("src/main/kotlin/com/ironmonone/app/TrackerPanel.kt")
        assertTrue("listOfNotNull(state.onField).take(" in panel, "the own card")
        assertTrue("enemyMoveContext(enemy, state.onField," in panel, "the enemy's matchups")
        assertTrue("LastAttack.lethal(state.lastAttackDamage, state.onField?.mon?.curHp)" in panel, "the lethal sword")
        assertTrue("val own = s.onField ?: return null" in src("src/main/kotlin/com/ironmonone/app/CalcAtkScreen.kt"), "Calc Atk")
        assertTrue("enemyMoveContext(e, s.onField," in src("src/main/kotlin/com/ironmonone/app/stream/StreamSnapshot.kt"), "the stream")
        assertFalse("enemyMoveContext(enemy, state.party.firstOrNull()" in panel)
    }
}
