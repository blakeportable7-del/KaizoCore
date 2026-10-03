package com.ironmonone.app

import com.ironmonone.tracker.EnemyInfo
import com.ironmonone.tracker.HealTotals
import com.ironmonone.tracker.PokemonDecoder
import com.ironmonone.tracker.TrackedMon
import com.ironmonone.tracker.TrackerState
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The GBA tracker's swap in a double battle (Blake, 2026-10-03: "on doubles how do you cycle through your party pokemon
 * in the tracker?"). The PC tracker's toggle (Battle.togglePokemonViewed, Battle.lua:215-238) walks your left, the
 * opponent's left, your right and the opponent's right; the app's showed battlers 0 and 1 only, so your right-hand
 * Pokemon and the opponent's second could not be seen at all.
 */
class DoublesViewTest {
    private fun own(species: Int, level: Int, hp: Int = 50) = TrackedMon(
        mon = PokemonDecoder.Mon(pid = species.toLong(), level = level, nickname = "", species = species, heldItem = 0, friendship = 70,
            moves = listOf(33, 0, 0, 0), pp = listOf(35, 0, 0, 0), ivs = List(6) { 0 }, evs = List(6) { 0 }, ppUps = List(4) { 0 },
            abilitySlot = 0, nature = 0, shiny = false, status = 0, curHp = hp, maxHp = 50, atk = 5, def = 5, spe = 5, spAtk = 5, spDef = 5),
        speciesName = "#$species", moveNames = emptyList(), base = null,
    )

    private fun foe(species: Int, level: Int, hp: Int = 30) = EnemyInfo(
        species = species, speciesName = "#$species", level = level, curHp = hp, maxHp = 30, type1 = 0, type2 = 0,
        base = null, movesSeen = emptyList(),
    )

    // Your Pikachu on the left and Charmander on the right, Bulbasaur on the bench; the opponent's Pidgey and Rattata.
    private val pika = own(25, 21)
    private val bulba = own(1, 22)
    private val char = own(4, 23)
    private val pidgey = foe(16, 24)
    private val rattata = foe(19, 25)

    private fun doubles(field: List<Int> = listOf(0, 1), allyHidden: Boolean = false, enemyRight: EnemyInfo? = rattata) = TrackerState(
        partyCount = 3, party = listOf(pika, bulba, char), inBattle = true, isWildBattle = false,
        enemy = pidgey, enemyRight = enemyRight, enemyOnField = field, doubles = true,
        ownOnField = 0, ownRightOnField = 2, allyHidden = allyHidden,
        healPercent = 40, healCount = 1, healHp = 20, ownRightHeals = HealTotals(25, 20, 1),
    )

    private fun singles(field: List<Int> = listOf(0)) = TrackerState(
        partyCount = 3, party = listOf(pika, bulba, char), inBattle = true, isWildBattle = false,
        enemy = pidgey, enemyOnField = field,
    )

    private val overworld = TrackerState(partyCount = 3, party = listOf(pika, bulba, char), inBattle = false, isWildBattle = false)

    /** A view that has seen [s]'s battle begin, with "Auto swap to enemy" [auto]. */
    private fun viewIn(s: TrackerState, auto: Boolean = false) = GbaViewState().also { it.onRead(overworld, auto); it.onRead(s, auto) }

    /** The battler on the viewed card after each of [steps] swaps. */
    private fun walk(v: GbaViewState, s: TrackerState, steps: Int, stacked: Boolean = false): List<Int> =
        List(steps) { v.swap(s, stacked); v.viewedBattler(s) }

    @Test
    fun `in a double battle the swap walks your left, the opponent's left, your right and the opponent's right`() {
        val s = doubles()
        val v = viewIn(s)
        assertEquals(0, v.viewedBattler(s), "a battle opens on yours with auto swap off")
        assertEquals(listOf(1, 2, 3, 0, 1, 2, 3, 0), walk(v, s, 8))
    }

    @Test
    fun `every card follows the Pokemon viewed`() {
        val s = doubles()
        val v = viewIn(s)
        assertEquals(25, v.own(s)?.mon?.species)
        assertEquals(HealTotals(40, 20, 1), v.heals(s))
        v.swap(s, stacked = false)                      // the opponent's left
        assertEquals(16, v.foe(s)?.species)
        assertEquals(25, v.own(s)?.mon?.species, "getViewedPokemon(true) is your left while an opponent is viewed")
        v.swap(s, stacked = false)                      // your right
        assertEquals(4, v.own(s)?.mon?.species)
        assertEquals(HealTotals(25, 20, 1), v.heals(s), "its heals are a share of its own max HP")
        assertEquals(16, v.foe(s)?.species, "its moves are matched against the opponent's left")
        assertEquals(16, v.ownTarget(s)?.species)
        v.swap(s, stacked = false)                      // the opponent's right
        assertEquals(19, v.foe(s)?.species)
        assertEquals(25, v.foeTarget(s)?.mon?.species)
        assertEquals(HealTotals(40, 20, 1), v.heals(s))
        // Battle Details' line is the viewed battler's (Battle.getViewedIndex).
        assertEquals("d", BattleSummary.line(listOf("a", "b", "c", "d"), v.viewedBattler(s)))
    }

    /**
     * The proof the right-hand battlers are reachable: a swap that never turned to them (the bug) fails here. Broken
     * once on purpose while writing it (BattleView.toggled without its side change) and seen to fail.
     */
    @Test
    fun `the right-hand battlers are shown, one card at a time and both at once`() {
        val s = doubles()
        val v = viewIn(s)
        val mine = HashSet<Int>(); val theirs = HashSet<Int>()
        repeat(4) {
            v.swap(s, stacked = false)
            if (v.view.own) mine += v.own(s)!!.mon.species else theirs += v.foe(s)!!.species
        }
        assertEquals(setOf(25, 4), mine, "your right-hand Charmander")
        assertEquals(setOf(16, 19), theirs, "the opponent's right-hand Rattata")
        // Both cards on screen: each step changes one of them, and a step that would change neither is passed over.
        val both = viewIn(s)
        val pairs = List(3) { both.swap(s, stacked = true); both.own(s)!!.mon.species to both.foe(s)!!.species }
        assertEquals(listOf(4 to 16, 25 to 19, 25 to 16), pairs)
    }

    @Test
    fun `a single battle swaps yours and the opponent's as it always has`() {
        val s = singles()
        val v = viewIn(s)
        assertTrue(v.offersFoe(s, stacked = false), "SEE FOE")
        assertEquals(listOf(1, 0, 1, 0), walk(v, s, 4))
        assertTrue(v.view.left, "never changes sides")
        assertNull(v.sideWords(s, stacked = false), "no side words")
        assertNull(v.swapSpoken(s, stacked = false))
        v.swap(s, stacked = false)
        assertFalse(v.offersFoe(s, stacked = false), "SEE MINE")
        assertFalse(v.canSwap(s, stacked = true), "both on screen: nothing to swap")
        assertFalse(v.canSwap(singles().copy(enemy = null), stacked = false), "no opponent read yet")
        assertFalse(v.canSwap(overworld, stacked = false), "outside a battle the toggle does nothing")
        assertEquals(BattleView(own = false, left = true), BattleView().toggled(doubles = false, hideAlly = false))
        assertEquals(BattleView(), BattleView(own = false).toggled(doubles = false, hideAlly = true))
    }

    @Test
    fun `a fight that hides your partner never shows it`() {
        val s = doubles(allyHidden = true)
        val v = viewIn(s)
        assertEquals(listOf(1, 3, 0, 1, 3, 0), walk(v, s, 6))
        // Battle.togglePokemonViewed's own rule: from the opponent's left straight to its right.
        assertEquals(BattleView(own = false, left = false), BattleView(own = false, left = true).toggled(doubles = true, hideAlly = true))
        val both = viewIn(s)
        assertEquals(listOf(25 to 19, 25 to 16), List(2) { both.swap(s, stacked = true); both.own(s)!!.mon.species to both.foe(s)!!.species })
        assertEquals(25, v.foeTarget(s.copy(party = listOf(own(25, 21, hp = 0), bulba, char)))?.mon?.species, "never the target either")
    }

    @Test
    fun `auto swap turns to the side that sent a Pokemon out`() {
        val s = doubles()
        val v = viewIn(s, auto = true)
        assertEquals(1, v.viewedBattler(s), "the battle opens on the opponent's left")
        v.swap(s, stacked = false); v.swap(s, stacked = false)
        assertEquals(3, v.viewedBattler(s))
        v.swap(s, stacked = false)
        assertEquals(0, v.viewedBattler(s))
        v.onRead(doubles(field = listOf(0, 3)), autoSwap = true)      // its right-hand one fainted, slot 4 came out
        assertEquals(3, v.viewedBattler(s))
        v.onRead(doubles(field = listOf(2, 3)), autoSwap = true)      // then on its left
        assertEquals(1, v.viewedBattler(s))
        v.swap(s, stacked = false)
        v.onRead(doubles(field = listOf(4, 5)), autoSwap = true)
        assertEquals(1, v.viewedBattler(s), "both at once: the left, which the reference checks first")
        val off = viewIn(s, auto = false)
        off.onRead(doubles(field = listOf(0, 3)), autoSwap = false)
        assertEquals(0, off.viewedBattler(s), "auto swap off: the view stays")
        // The battle's end brings your lead back; the next one starts afresh.
        v.onRead(overworld, autoSwap = true)
        assertEquals(BattleView(), v.view)
        assertEquals(1, opponentSentOutSide(listOf(0, 1), listOf(0, 3)))
        assertEquals(0, opponentSentOutSide(listOf(0, 1), listOf(2, 3)))
        assertNull(opponentSentOutSide(emptyList(), listOf(0, 1)), "the battle's start, which its own swap covers")
        assertNull(opponentSentOutSide(listOf(0, 1), listOf(0)), "a slot unread for a poll")
    }

    @Test
    fun `a view an earlier double battle left on the right comes back to the left in a single battle`() {
        val v = viewIn(doubles())
        repeat(3) { v.swap(doubles(), stacked = false) }
        assertEquals(3, v.viewedBattler(doubles()))
        v.onRead(singles(field = listOf(0, 1)), autoSwap = false)
        assertTrue(v.view.left)
        assertTrue(v.canSwap(singles(), stacked = false))
        v.clear()
        assertEquals(BattleView(), v.view)
    }

    @Test
    fun `a slot that read nothing is passed over`() {
        val s = doubles(enemyRight = null)
        val v = viewIn(s)
        assertEquals(listOf(1, 2, 0, 1), walk(v, s, 4))
        assertEquals(16, v.foe(s)?.species)
    }

    @Test
    fun `a card's moves are matched against the partner once the one shown has fainted`() {
        val s = doubles()
        val v = viewIn(s)
        assertEquals(19, v.ownTarget(s.copy(enemy = foe(16, 24, hp = 0)))?.species)
        assertEquals(4, v.foeTarget(s.copy(party = listOf(own(25, 21, hp = 0), bulba, char)))?.mon?.species)
        assertEquals(16, v.ownTarget(singles().copy(enemy = foe(16, 24, hp = 0)))?.species, "a single battle has no partner")
    }

    @Test
    fun `the banner says which one is on screen, by where it stands`() {
        val s = doubles()
        val v = viewIn(s)
        assertEquals(BannerSide("MINE ON THE LEFT", "MINE LEFT"), v.sideWords(s, stacked = false))
        assertEquals("Show the opponent's Pokémon on the right", v.swapSpoken(s, stacked = false))
        v.swap(s, stacked = false)
        // The game names the opponent's first its left as it faces you: it stands on the right of your screen.
        assertEquals(BannerSide("FOE ON THE RIGHT", "FOE RIGHT"), v.sideWords(s, stacked = false))
        assertFalse(v.offersFoe(s, stacked = false), "next is yours: SEE MINE")
        v.swap(s, stacked = false)
        assertEquals("MINE ON THE RIGHT", v.sideWords(s, stacked = false)?.full)
        v.swap(s, stacked = false)
        assertEquals("FOE ON THE LEFT", v.sideWords(s, stacked = false)?.full)
        assertEquals("MINE LEFT, FOE LEFT", v.sideWords(s, stacked = true)?.full)
        assertNull(v.sideWords(singles(), stacked = false))
        // The DS games' slots stand the same way, a triple battle's in three places.
        assertEquals(listOf("LEFT", "RIGHT"), (0..1).map { BattleSideWords.place(foe = false, it, 2) })
        assertEquals(listOf("RIGHT", "LEFT"), (0..1).map { BattleSideWords.place(foe = true, it, 2) })
        assertEquals(listOf("RIGHT", "MIDDLE", "LEFT"), (0..2).map { BattleSideWords.place(foe = true, it, 3) })
        assertNull(BattleSideWords.place(foe = false, 0, 1))
        assertEquals(BannerSide("FOE 2 OF 3", "FOE 2 OF 3"), BattleSideWords.one(SideSpot("FOE", null, 1, 3)))
    }

    @Test
    fun `the info screen's level reads the right-hand pair too`() {
        // TrackerAPI.getActiveBattlePokemon (TrackerAPI.lua:46-58): your left, the opponent's left, then the right-hand pair.
        assertEquals(23, InfoScreenLines.viewedLevel(4, doubles()))
        assertEquals(25, InfoScreenLines.viewedLevel(19, doubles()))
        assertEquals(0, InfoScreenLines.viewedLevel(19, singles()))
    }

    private fun src(name: String) = File("src/main/kotlin/com/ironmonone/app/$name").readText().replace("\r\n", "\n")

    @Test
    fun `the panel, Calc Atk, Heals in Bag and the notebook all read the one view`() {
        val panel = src("TrackerPanel.kt")
        assertTrue("view.onRead(state, TrackerOptions.autoSwapToEnemy(gameBoy = generation < 3))" in panel)
        assertTrue("onSwapView = if (view.canSwap(state, stackBoth)) { { view.swap(state, stackBoth) } } else null," in panel)
        assertTrue("side = view.sideWords(state, stackBoth)," in panel, "the banner's words")
        assertTrue("val enemy = view.foe(state)?.takeIf { stackBoth || !viewingOwn }" in panel, "the opponent's card")
        assertTrue("listOfNotNull(view.own(state)).take(" in panel, "your card")
        assertTrue("val ownHeals = view.heals(state)" in panel, "its heals")
        assertTrue("moveCtx = ownMoveContext(p, view.ownTarget(state), state.weather, onWeight)" in panel, "your matchups")
        assertTrue("moveCtx = enemyMoveContext(enemy, view.foeTarget(state), state.weather, onWeight)" in panel, "the opponent's matchups")
        assertTrue("LastAttack.lethal(state.lastAttackDamage, view.own(state)?.mon?.curHp)" in panel, "the lethal mark")
        assertTrue("BattleSummary.line(state.battleSummaries, view.viewedBattler(state))" in panel, "Battle Details' line")
        assertTrue("ownLead = species == gbaView.own(state)?.mon?.species" in panel, "the info screen's types")
        assertFalse("remember(state.inBattle)" in panel, "the view is no longer the panel's own: a rotation keeps it")
        val calc = src("CalcAtkScreen.kt")
        assertTrue("val own = gbaView.own(s) ?: return null" in calc && "val enemy = gbaView.foe(s) ?: return null" in calc, "Calc Atk")
        assertTrue("gba?.healsInBag(gbaView.own(trackerState))" in src("SideScreens.kt"), "Heals in Bag")
        val play = src("PlayScreen.kt")
        assertTrue("val notebookSpecies = viewedFoeSpecies(ndsState, trackerState, enemySpecies)" in play, "the notebook")
        for (line in listOf("movesSeenRunWide = gbaView.foe(trackerState)", "revealedEnemyAbility = gbaView.foe(trackerState)",
            "revealedEnemyAbility2 = gbaView.foe(trackerState)", "gbaView.foe(trackerState)?.let { statMarks.cycle(it.species, i) }")) {
            assertEquals(2, Regex(Regex.escape(line)).findAll(play).count(), "portrait and landscape: $line")
        }
        assertTrue("onDispose { AutoTheme.release(); dsView.clear(); gbaView.clear() }" in play, "nothing viewed reaches the next game")
    }
}
