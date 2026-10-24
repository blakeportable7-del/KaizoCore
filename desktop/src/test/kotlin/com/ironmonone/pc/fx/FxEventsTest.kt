package com.ironmonone.pc.fx

import com.ironmonone.pcui.Samples
import com.ironmonone.tracker.TrackerState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** What FxEvents finds between two reads: only real changes of the same Pokemon, and a badge by the bit that came on. */
class FxEventsTest {
    private fun battle(): TrackerState = Samples.party.copy(inBattle = true, badges = 0b1)

    private fun lead(st: TrackerState, f: (com.ironmonone.tracker.TrackedMon) -> com.ironmonone.tracker.TrackedMon) =
        st.copy(party = listOf(f(st.party[0])) + st.party.drop(1))

    @Test
    fun `a burn, a raised attack, a hit and a badge are each one event`() {
        val a = battle()
        val b = lead(a) { it.copy(statusCondition = "BRN", statStages = it.statStages + ("ATK" to 8), mon = it.mon.copy(curHp = it.mon.curHp - 5)) }
            .copy(badges = 0b11)
        val e = FxEvents.between(a, b)
        assertTrue(FxEvent.Status(FxEvent.Side.OWN, "BRN", "") in e, "$e")
        assertTrue(e.any { it is FxEvent.Stage && it.stat == "ATK" && it.delta == 2 && it.stage == 2 }, "$e")
        assertTrue(e.any { it is FxEvent.Damage && it.amount == 5 && it.side == FxEvent.Side.OWN }, "$e")
        assertTrue(FxEvent.Badge(1, 2, a.badgeSet) in e, "$e")
    }

    @Test
    fun `an ability plays when the game shows it, on the side whose Pokemon it is`() {
        val a = battle()
        val sp = a.party[0].mon.species
        val shown = a.copy(abilitiesRevealed = listOf(sp to "Static", 9999 to "Levitate"))
        assertEquals(listOf(FxEvent.Ability(FxEvent.Side.OWN, "Static")), FxEvents.between(a, shown).filterIsInstance<FxEvent.Ability>(),
            "a Pokemon on neither side plays nothing")
        assertTrue(FxEvents.between(a, shown.copy(inBattle = false)).none { it is FxEvent.Ability }, "only in battle")
        val enemy = Samples.enemy.copy(inBattle = true)
        val foe = enemy.copy(abilitiesRevealed = listOf(enemy.enemy!!.species to "Volt Absorb"))
        assertTrue(FxEvent.Ability(FxEvent.Side.FOE, "Volt Absorb") in FxEvents.between(enemy, foe))
    }

    @Test
    fun `outside battle a level is a candy, effort is a vitamin, and a new move id is a learned move`() {
        val a = Samples.party.copy(inBattle = false, enemy = null)
        val m = a.party[0].mon
        val evs = m.evs.ifEmpty { List(6) { 0 } }
        val b = lead(a) { it.copy(mon = m.copy(level = m.level + 1, evs = evs.mapIndexed { i, v -> if (i == 1) v + 10 else v },
            moves = listOf(999) + m.moves.drop(1)), moveNames = listOf("Flamethrower") + it.moveNames.drop(1)) }
        val e = FxEvents.between(a, b)
        assertTrue(FxEvent.Candy(1) in e, "$e")
        assertTrue(FxEvent.Effort("ATK", 10) in e, "the second effort value is Attack: $e")
        assertTrue(FxEvent.MoveLearned(0, "Flamethrower", levelUp = true) in e, "$e")
        // The same moves in another order are not a new move.
        val swapped = lead(a) { it.copy(mon = m.copy(moves = m.moves.reversed())) }
        assertTrue(FxEvents.between(a, swapped).none { it is FxEvent.MoveLearned }, "a reorder")
        // In battle a level is not a candy.
        val inBattle = battle()
        assertTrue(FxEvents.between(inBattle, lead(inBattle) { it.copy(mon = it.mon.copy(level = it.mon.level + 1)) }).none { it is FxEvent.Candy })
    }

    @Test
    fun `the two switches in Settings stop their animations and only theirs`() {
        val opts = com.ironmonone.app.TrackerOptions
        val keep = opts.battleAnimations to opts.progressAnimations
        try {
            fun layer(e: FxEvent): Boolean = FxLayer(java.awt.Font(java.awt.Font.DIALOG, 0, 8)).also { it.play(listOf(e), FxEvent.Side.OWN, 0) }.active
            val burn = FxEvent.Status(FxEvent.Side.OWN, "BRN", ""); val level = FxEvent.LevelUp(5, 6)
            opts.battleAnimations = true; opts.progressAnimations = true
            assertTrue(layer(burn) && layer(level), "both on: both play")
            opts.battleAnimations = false
            assertTrue(!layer(burn) && layer(level), "battle off: the burn does not play, the level-up does")
            opts.battleAnimations = true; opts.progressAnimations = false
            assertTrue(layer(burn) && !layer(level), "level-up and item animations off: only the level-up stops")
        } finally { opts.battleAnimations = keep.first; opts.progressAnimations = keep.second }
    }

    @Test
    fun `nothing changed is nothing, and a new lead is not damage`() {
        val a = battle()
        assertEquals(emptyList(), FxEvents.between(a, a))
        val other = lead(a) { it.copy(mon = it.mon.copy(species = it.mon.species + 1, curHp = 1)) }
        assertTrue(FxEvents.between(a, other).none { it is FxEvent.Damage || it is FxEvent.Faint }, "a switched-in Pokemon took no damage")
    }
}
