package com.ironmonone.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.ironmonone.tracker.EnemyInfo
import com.ironmonone.tracker.HealTotals
import com.ironmonone.tracker.TrackedMon
import com.ironmonone.tracker.TrackerState
import com.ironmonone.tracker.nds.NdsTrackerState

/**
 * Which Pokemon a Game Boy Advance battle shows: Battle.isViewingOwn and Battle.isViewingLeft (Battle.lua:8-9), both
 * true when a battle begins and ends. In a single battle only [own] ever changes. "Left" is the game's word for a
 * side's first battler as that trainer stands: your battler 0 and the opponent's battler 1 (Battle.IndexMap,
 * Combatants.LeftOwn and LeftOther). Where each stands on your screen is [BattleSideWords]'.
 */
internal data class BattleView(val own: Boolean = true, val left: Boolean = true) {
    /** Battle.getViewedIndex (Battle.lua:243-254): 0 your left, 1 the opponent's left, 2 your right, 3 the opponent's right. */
    val battler: Int get() = (if (own) 0 else 1) + (if (left) 0 else 2)

    /** Battle.getViewedPokemon(true) (Battle.lua:258-261): your right-hand battler only while it is the one viewed. */
    val ownBattler: Int get() = if (left || !own) 0 else 2

    /** Battle.getViewedPokemon(false) (Battle.lua:262-263): the opponent's right-hand battler only while it is viewed. */
    val foeBattler: Int get() = if (left || own) 1 else 3

    /**
     * Battle.togglePokemonViewed (Battle.lua:215-238): yours and the opponent's in turn, and in a double battle the side
     * changes each time the view comes back to yours, so the swap walks your left, the opponent's left, your right and
     * the opponent's right. In a fight that hides your partner's Pokemon ([hideAlly]) it goes from the opponent's left
     * straight to the opponent's right.
     */
    fun toggled(doubles: Boolean, hideAlly: Boolean): BattleView {
        var own = !own
        var left = left
        if (own && doubles) {
            left = !left
            if (!left && hideAlly) own = !own
        }
        return BattleView(own, left)
    }
}

/**
 * Battle.updateViewSlots (Battle.lua:300-316): which side's party slot on the field now holds a different Pokemon
 * from the one it held, 0 the left (battler 1) or 1 the right (battler 3, a double battle's); the left first when
 * both changed, as the reference checks it first. Null when neither did. A slot not known before is the battle's
 * start, which the battle's own swap covers.
 */
internal fun opponentSentOutSide(before: List<Int>, now: List<Int>): Int? =
    now.indices.firstOrNull { i -> before.getOrNull(i).let { it != null && it != now[i] } }

/** [opponentSentOutSide] on either side: the opponent sent a new Pokemon out. */
internal fun opponentSentOut(before: List<Int>, now: List<Int>): Boolean = opponentSentOutSide(before, now) != null

/**
 * The GBA battle view the panels share (Battle.lua's view state), as [dsView] is the DS one: one instance, so a
 * rotation, the floating window and the side screens (Calc Atk, Heals in Bag) all see the same Pokemon, and the
 * opponent's notebook follows the one on screen. The play screen clears it on leaving ([clear]).
 *
 * Out of a battle it is always your lead, and the swap does nothing, as in the reference.
 */
internal class GbaViewState {
    var view by mutableStateOf(BattleView())
        private set
    private var inBattle = false
    private var enemySlots: List<Int> = emptyList()

    /** The play screen closed: nothing viewed carries into the next game. */
    fun clear() { view = BattleView(); inBattle = false; enemySlots = emptyList() }

    /**
     * Each read. A battle's start opens it on your left battler, or, with "Auto swap to enemy" on ([autoSwap]), on the
     * opponent's (Battle.beginNewBattle, then the battle's data start, Battle.lua:172-175 and 779-780); its end brings
     * your lead back (:859-860). During the battle a new Pokemon sent out turns the view to it, on its side
     * (Battle.changeOpposingPokemonView, :945-952), with auto swap on.
     */
    fun onRead(state: TrackerState, autoSwap: Boolean) {
        if (state.inBattle != inBattle) {
            inBattle = state.inBattle
            enemySlots = state.enemyOnField
            view = BattleView(own = !(state.inBattle && autoSwap))
            return
        }
        if (!state.inBattle) return
        val side = opponentSentOutSide(enemySlots, state.enemyOnField)
        if (autoSwap && side != null) view = BattleView(own = false, left = side == 0)
        enemySlots = state.enemyOnField
        // A single battle has no right-hand side, and its swap never changes sides: a view an earlier battle left there
        // (the panel away while one battle ended and the next began) comes back to the left.
        if (!state.doubles && !view.left) view = view.copy(left = true)
    }

    /**
     * Whether [battler] can be shown: your left one and the opponent's whenever they are read; the right-hand ones only
     * in a double battle, once read, and your partner's never in a fight that hides it.
     */
    fun shows(state: TrackerState, battler: Int): Boolean = when (battler) {
        0 -> state.onField != null
        1 -> state.enemy != null
        2 -> state.doubles && !state.allyHidden && state.ownRight != null
        else -> state.doubles && state.enemyRight != null
    }

    /** Your battler [v] puts on the own card: 0 or 2. */
    private fun ownIndex(state: TrackerState, v: BattleView): Int = if (v.ownBattler == 2 && shows(state, 2)) 2 else 0

    /** The opponent's battler [v] puts on its card: 1 or 3. */
    private fun foeIndex(state: TrackerState, v: BattleView): Int = if (v.foeBattler == 3 && shows(state, 3)) 3 else 1

    /**
     * The view the swap goes to: the reference's next one, past any battler there is nothing of to show. [stacked]: both
     * cards are on screen (landscape), so a step that changes neither card (your left and the opponent's left show the
     * same pair) is passed over too.
     */
    fun next(state: TrackerState, stacked: Boolean): BattleView {
        var v = view
        repeat(4) {
            v = v.toggled(state.doubles, state.allyHidden)
            val moved = if (stacked) ownIndex(state, v) != ownIndex(state, view) || foeIndex(state, v) != foeIndex(state, view)
                else v != view
            if (shows(state, v.battler) && moved) return v
        }
        return view
    }

    /**
     * The swap has somewhere to go: only in a battle, as the reference's toggle does nothing outside one, and only once an
     * opponent is read.
     */
    fun canSwap(state: TrackerState, stacked: Boolean): Boolean = state.inBattle && state.enemy != null && next(state, stacked) != view

    fun swap(state: TrackerState, stacked: Boolean) {
        if (canSwap(state, stacked)) view = next(state, stacked)
    }

    /**
     * The swap button's word: SEE FOE when its next step shows another opponent, SEE MINE when it shows another of yours.
     * True for SEE FOE, as [PcBannerCopy.see] takes it.
     */
    fun offersFoe(state: TrackerState, stacked: Boolean): Boolean {
        val n = next(state, stacked)
        return if (stacked) foeIndex(state, n) != foeIndex(state, view) else !n.own
    }

    /** The swap button's spoken label in a double battle, naming where its next Pokemon stands; null in a single battle. */
    fun swapSpoken(state: TrackerState, stacked: Boolean): String? {
        if (!state.doubles) return null
        val n = next(state, stacked)
        val foe = offersFoe(state, stacked)
        return PcBannerCopy.seeSpoken(foe, BattleSideWords.gba(if (foe) foeIndex(state, n) else ownIndex(state, n)))
    }

    /** Your battler the panels show, 0 or 2: the own card, its stages and heals, Calc Atk and the opponent's matchups. */
    fun ownBattler(state: TrackerState): Int = if (state.inBattle) ownIndex(state, view) else 0

    /** The opponent's battler the panels show, 1 or 3. */
    fun foeBattler(state: TrackerState): Int = foeIndex(state, view)

    /** Battle.getViewedIndex (Battle.lua:243-254): the battler on the viewed card, 0 to 3; your lead's 0 outside a battle. */
    fun viewedBattler(state: TrackerState): Int = if (!state.inBattle || view.own) ownBattler(state) else foeBattler(state)

    /** Your Pokemon the panels show (Battle.getViewedPokemon(true)); your lead outside a battle. */
    fun own(state: TrackerState?): TrackedMon? = state?.let { if (ownBattler(it) == 2) it.ownRight else it.onField }

    /** The opponent's Pokemon the panels show (Battle.getViewedPokemon(false)); null outside a battle. */
    fun foe(state: TrackerState?): EnemyInfo? = state?.takeIf { it.inBattle }?.let { if (foeBattler(it) == 3) it.enemyRight else it.enemy }

    /** The heals strip for [own]: a share of its own max HP (Program.recalcLeadPokemonHealingInfo). */
    fun heals(state: TrackerState): HealTotals =
        if (ownBattler(state) == 2) state.ownRightHeals else HealTotals(state.healPercent, state.healHp, state.healCount)

    /**
     * The opponent your own card's moves are matched against: the one shown, or its partner once it has fainted
     * (Battle.getDoublesCursorTargetInfo, Battle.lua:1147-1176, without the game's own target cursor).
     */
    fun ownTarget(state: TrackerState): EnemyInfo? {
        val f = foe(state) ?: return null
        val other = if (foeBattler(state) == 1) state.enemyRight?.takeIf { shows(state, 3) } else state.enemy
        return if (f.curHp <= 0 && other != null) other else f
    }

    /** Your Pokemon the opponent's moves are matched against: the one shown, or your other one once it has fainted. */
    fun foeTarget(state: TrackerState): TrackedMon? {
        val o = own(state) ?: return null
        val other = if (ownBattler(state) == 0) state.ownRight?.takeIf { shows(state, 2) } else state.onField
        return if (o.mon.curHp <= 0 && other != null) other else o
    }

    /**
     * The banner's words for which Pokemon the cards show, in a double battle only: the one card (swap) or both
     * ([stacked]). Null in a single battle, where there is only the one of each.
     */
    fun sideWords(state: TrackerState, stacked: Boolean): BannerSide? {
        val (own, foe) = shownSpots(state) ?: return null
        return if (stacked) BattleSideWords.pair(own, foe) else BattleSideWords.one(if (view.own) own else foe)
    }

    /** Where your Pokemon and the opponent's that the panels show stand, in a double battle only (the stream's words too). */
    fun shownSpots(state: TrackerState): Pair<SideSpot, SideSpot>? =
        if (!state.inBattle || !state.doubles) null else BattleSideWords.gba(ownBattler(state)) to BattleSideWords.gba(foeBattler(state))
}

/** The one GBA battle view the panels share. */
internal val gbaView = GbaViewState()

/** One Pokemon on the field, for the banner's words: whose it is ("MINE", "FOE", "PARTNER") and its slot of [count] on that side. */
internal data class SideSpot(val who: String, val place: String?, val index: Int, val count: Int) {
    /** Where it stands, or its number where the slots stand in no row (a rotation battle): "LEFT", "2 OF 3"; empty for a side of one. */
    val at: String get() = place ?: if (count > 1) "${index + 1} OF $count" else ""

    /** "MINE LEFT", "FOE 2 OF 3", or "MINE" alone. */
    val short: String get() = if (at.isEmpty()) who else "$who $at"
}

/** The banner's line of words, and a shorter one for a narrow pane. Public, as the banner is. */
data class BannerSide(val full: String, val short: String)

/**
 * Where a Pokemon stands on the game's screen, in words, for the battle banner. The game names a side's first battler
 * its left as that trainer faces the field, so the opponent's first one (battler 1, Battle.lua's LeftOther) stands on
 * the right of your screen: the reference's own note says the perspective is reversed for the enemy
 * (TrackerAPI.getPokemonTypes, TrackerAPI.lua:69), and CFRU's Scary Face places battler 1 on your right-hand one's
 * side of the screen (battle_anims.c:3726-3741). The DS games keep the same sides: the DS tracker's L button moves the
 * matchups to the opponent's next slot (BattleHandlerBase.lua:63-70, LEFT_EFFECTIVENESS = "L").
 */
internal object BattleSideWords {
    /**
     * Slot [i] (from 0) of [n] on one side, where it stands on your screen: yours from the left, the opponent's from the
     * right. Null for a single battle, which needs no words.
     */
    fun place(foe: Boolean, i: Int, n: Int): String? {
        val names = when (n) { 2 -> listOf("LEFT", "RIGHT"); 3 -> listOf("LEFT", "MIDDLE", "RIGHT"); else -> return null }
        return names.getOrNull(if (foe) n - 1 - i else i)
    }

    /** A GBA battler: your 0 on the left and 2 on the right, the opponent's 1 on the right and 3 on the left. */
    fun gba(battler: Int): SideSpot {
        val foe = battler % 2 == 1
        return SideSpot(if (foe) "FOE" else "MINE", place(foe, battler / 2, 2), battler / 2, 2)
    }

    /** One card: "MINE ON THE LEFT" ("MINE LEFT" where that does not fit), "FOE IN THE MIDDLE", "FOE 2 OF 3" in a rotation battle. */
    fun one(s: SideSpot): BannerSide =
        BannerSide(if (s.place != null) "${s.who} ${if (s.place == MIDDLE) "IN" else "ON"} THE ${s.place}" else s.short, s.short)

    const val MIDDLE = "MIDDLE"

    /** Both cards (landscape): "MINE LEFT, FOE RIGHT". */
    fun pair(own: SideSpot, foe: SideSpot): BannerSide = "${own.short}, ${foe.short}".let { BannerSide(it, it) }
}

/**
 * The species of the opponent the panels show, whose notebook (marks, note, encounters, moves seen) the play screen
 * hands them: the DS view's opponent (a locked one first), else the GBA view's, else [fallback], the one on the field.
 */
internal fun viewedFoeSpecies(nds: NdsTrackerState?, gba: TrackerState?, fallback: Int): Int =
    nds?.let { dsView.shownEnemy(it)?.mon?.species } ?: dsView.locked?.mon?.species ?: gbaView.foe(gba)?.species ?: fallback
