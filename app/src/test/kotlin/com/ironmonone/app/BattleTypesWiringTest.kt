package com.ironmonone.app

import com.ironmonone.tracker.BaseStats
import com.ironmonone.tracker.EnemyInfo
import com.ironmonone.tracker.MoveRow
import com.ironmonone.tracker.PokemonDecoder
import com.ironmonone.tracker.TrackedMon
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The move rows' marks follow the battlers' battle types on both cards (Blake, 2026-10-06: "the effectiveness arrows on
 * the moves should adapt, right?"). The trackers read a type change (Conversion, Color Change, Protean, Soak, a third
 * type, Roost) into TrackedMon.battleTypes and EnemyInfo.battleTypes on the next read; these contexts draw from them.
 */
class BattleTypesWiringTest {
    private fun mon(species: Int) = PokemonDecoder.Mon(
        pid = 1, level = 30, nickname = "", species = species, heldItem = 0, friendship = 0,
        moves = listOf(85), pp = listOf(15), ivs = List(6) { 0 }, evs = List(6) { 0 },
        ppUps = List(4) { 0 }, abilitySlot = 0, nature = 0, shiny = false, status = 0,
        curHp = 61, maxHp = 74, atk = 55, def = 40, spe = 52, spAtk = 61, spDef = 38,
    )

    /** A Water Pokemon (type 11) of yours with Thunderbolt (Electric, 13). */
    private val ownBase = BaseStats(70, 94, 50, 66, 94, 50, type1 = 11, type2 = 11, ability1 = 16, ability2 = 0)
    private val thunderbolt = MoveRow(85, "THUNDERBOLT", 15, 15, 95, 100, 13, "SPE")
    private val own = TrackedMon(mon(130), "GYARADOS", listOf("THUNDERBOLT"), ownBase, moveRows = listOf(thunderbolt))
    /** A Water/Water opponent that has used Thunderbolt too. */
    private val foe = EnemyInfo(130, "GYARADOS", 30, 50, 50, 11, 11, ownBase, movesSeen = listOf("THUNDERBOLT"), moveRows = listOf(thunderbolt))

    private fun ownEffect(o: TrackedMon, e: EnemyInfo) = with(MoveDecorAccess) { thunderbolt.shown(ownMoveContext(o, e, null, null)) }.effect
    private fun foeEffect(o: TrackedMon, e: EnemyInfo) = with(MoveDecorAccess) { thunderbolt.shown(enemyMoveContext(e, o, null, null)) }.effect

    @Test
    fun `a type change on either side moves the arrows on both cards`() {
        assertEquals(2.0, ownEffect(own, foe)); assertEquals(2.0, foeEffect(own, foe))
        // The opponent's Color Change made it Grass (its battle struct, read into battleTypes): your Thunderbolt is resisted.
        val grassFoe = foe.copy(battleTypes = listOf(12, 12))
        assertEquals(0.5, ownEffect(own, grassFoe))
        // Your Conversion made you Ground: its Thunderbolt does nothing to you.
        val groundOwn = own.copy(battleTypes = listOf(4, 4))
        assertEquals(0.0, foeEffect(groundOwn, foe))
        fun stab(o: TrackedMon) = with(MoveDecorAccess) { thunderbolt.shown(ownMoveContext(o, foe, null, null)) }.stab
        assertEquals(false, stab(own))
        // Conversion to Electric (or Protean on Thunderbolt): now same-type, drawn green.
        assertEquals(true, stab(own.copy(battleTypes = listOf(13, 13))))
        // A third type (Forest's Curse adds Grass to Water/Flying): Electric 2x on Water and Flying, 1/2 on Grass.
        assertEquals(2.0, ownEffect(own, foe.copy(type1 = 11, type2 = 2, battleTypes = listOf(11, 2, 12))))
        // Roost's lost Flying (9, the Mystery type) is neutral.
        assertNull(ownEffect(own, foe.copy(type1 = 2, type2 = 2, battleTypes = listOf(9, 9))))
    }
}
