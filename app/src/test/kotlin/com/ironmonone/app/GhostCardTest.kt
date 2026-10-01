package com.ironmonone.app

import com.ironmonone.tracker.BaseStats
import com.ironmonone.tracker.EnemyInfo
import com.ironmonone.tracker.TrackerState
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The GBA panel in a Pokemon Tower ghost battle (Battle.isGhost): the
 * reference's stand-in on the enemy card, no effectiveness on any move
 * (DataHelper.lua:337), nothing recorded (Battle.lua:505), and the ghost's
 * note (Tracker.lua:490). The tracker side is GhostBattleTest.
 */
class GhostCardTest {
    @AfterTest fun defaults() { TrackerOptions.revealInfoIfRandomized = true }

    private val gastly = EnemyInfo(
        species = 92, speciesName = "GASTLY", level = 18, curHp = 30, maxHp = 30, type1 = 7, type2 = 3,
        base = BaseStats(30, 35, 30, 80, 100, 35, 7, 3, 26, 0), movesSeen = listOf("LICK"),
    )
    private val ghost = EnemyInfo(
        species = 413, speciesName = "Ghost", level = 18, curHp = 30, maxHp = 30, type1 = 9, type2 = 9,
        base = null, movesSeen = emptyList(), abilityGuess = "---", isGhost = true,
    )

    private fun battle(enemy: EnemyInfo) = TrackerState(
        partyCount = 1, party = emptyList(), inBattle = true, isWildBattle = true, enemy = enemy,
        isGhostBattle = enemy.isGhost,
    )

    @Test
    fun `the stand-in draws one unknown type icon and a blank BST`() {
        assertEquals(listOf("Unknown" to 9), GhostCard.types(ghost))
        assertEquals("---", GhostCard.bst(ghost))
        // An ordinary opponent is untouched.
        assertEquals(listOf("Ghost" to 7, "Poison" to 3), GhostCard.types(gastly))
        assertEquals("310", GhostCard.bst(gastly))
    }

    @Test
    fun `a ghost battle records nothing and shows the ghost's note`() {
        assertFalse(GhostCard.recordsEncounter(battle(ghost)))
        assertTrue(GhostCard.recordsEncounter(battle(gastly)))
        assertTrue(GhostCard.recordsEncounter(null))
        assertEquals("Spoooky!", GhostCard.note(ghost, "a note saved under the ghost's id"))
        assertEquals("slow", GhostCard.note(gastly, "slow"))
    }

    @Test
    fun `a ghost hides effectiveness on both cards`() {
        assertTrue(InfoRules.hideEffectiveness(null, ghost = true, own = true))
        assertTrue(InfoRules.hideEffectiveness(null, ghost = true, own = false))
        // Outside a ghost battle, the "Reveal info if randomized" rule is unchanged.
        assertFalse(InfoRules.hideEffectiveness(null, ghost = false, own = true))
        assertFalse(InfoRules.hideEffectiveness(null, ghost = false, own = false))
        TrackerOptions.revealInfoIfRandomized = false
        assertTrue(InfoRules.hideEffectiveness(null, ghost = false, own = true))
        assertFalse(InfoRules.hideEffectiveness(null, ghost = false, own = false))
    }

    /** The composables cannot run in a unit test; this proves they use the rules above. */
    @Test
    fun `the panel and the play screen are wired to the ghost rules`() {
        val src = File("src/main/kotlin/com/ironmonone/app")
        val panel = File(src, "TrackerPanel.kt").readText()
        assertTrue("GhostCard.types(e)" in panel, "enemy card type icons")
        assertTrue("GhostCard.bst(e)" in panel, "enemy card BST")
        assertTrue("GhostCard.SPRITE" in panel, "enemy card sprite")
        assertEquals(2, Regex("""InfoRules\.hideEffectiveness\(state\.randomized, state\.isGhostBattle""").findAll(panel).count(),
            "both move tables hide effectiveness in a ghost battle")
        val play = File(src, "PlayScreen.kt").readText()
        assertTrue("GhostCard.recordsEncounter(trackerState)" in play, "encounters, last level and route sightings")
        assertTrue("isWildBattle && records &&" in play, "route sightings")
        assertTrue("GhostCard.note(trackerState?.enemy" in play, "the carousel note")
    }
}
