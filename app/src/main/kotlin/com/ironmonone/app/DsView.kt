package com.ironmonone.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ironmonone.tracker.nds.NdsTrackedMon
import com.ironmonone.tracker.nds.NdsTrackerState

/**
 * The DS tracker's one-Pokemon view (Program.lua: selectedPlayer, locked and
 * lockedPokemonCopy). The reference swaps between your Pokemon and the opponent
 * with Start (CHANGE_VIEW) and locks the opponent with Select (LOCK_ENEMY); the
 * tracker's controls here are touch (Blake, 2026-09-29), so the swap is the battle
 * banner's and the lock is the enemy card's lock icon, and no game button is bound.
 * One instance serves the portrait and the landscape panels, so a rotation keeps
 * the view and the lock; a new run clears both ([forAttempt]).
 *
 * In a double or triple battle the view also names a slot on each side
 * (BattleHandlerBase's player and enemy slotIndex), and the swap walks them the
 * reference's way: each of yours in turn, then each of the opponent's, then yours
 * again ([next]). L and R (LEFT/RIGHT_EFFECTIVENESS), which pick the opponent your
 * own moves are matched against, have no touch control: that stays its first.
 */
internal class DsViewState {
    /** selectedPlayer is ENEMY. */
    var viewingEnemy by mutableStateOf(false)
        private set

    /** lockedPokemonCopy: the opponent as it was when locked; null while nothing is. */
    var locked by mutableStateOf<NdsTrackedMon?>(null)
        private set

    /** BattleHandlerBase's battleData player slotIndex, from 0: which of your Pokemon on the field the view shows. */
    var playerSlot by mutableStateOf(0)
        private set

    /** The same for the opponent's side. */
    var enemySlot by mutableStateOf(0)
        private set

    private var attempt = Int.MIN_VALUE
    private var inBattle = false

    /** The run the view belongs to: a new one starts on your Pokemon with nothing locked. */
    fun forAttempt(a: Int) {
        if (a != attempt) { attempt = a; viewingEnemy = false; locked = null; playerSlot = 0; enemySlot = 0 }
    }

    /** The play screen closed: nothing viewed or locked carries into the next game, DS or not. */
    fun clear() { attempt = Int.MIN_VALUE; viewingEnemy = false; locked = null; playerSlot = 0; enemySlot = 0; inBattle = false }

    /**
     * The opponent the enemy view shows (Program.getPokemonToDraw, lua:537-555): the
     * locked one, else the one on the field in a battle, in its slot
     * (BattleHandlerBase.getActivePokemonInBattle, lua:423-431). While yours is the one
     * viewed, that is the opponent's first slot, which your moves are matched against.
     */
    fun shownEnemy(state: NdsTrackerState): NdsTrackedMon? =
        locked ?: state.takeIf { it.inBattle }?.let { s -> s.enemyBattlers.getOrNull(if (viewingEnemy) enemySlot else 0) ?: s.enemy }

    /**
     * Your Pokemon the view shows: your slot's on the field in a fetched battle
     * (getActivePokemonInBattle for the player), else Program's playerPokemon.
     */
    fun shownPlayer(state: NdsTrackerState): NdsTrackedMon? =
        state.takeIf { it.inBattle }?.playerBattlers?.getOrNull(playerSlot) ?: state.playerPokemon

    /**
     * Program.readMemory (lua:650-652): outside a battle, with nothing locked, the view
     * is yours. A battle starts and ends with each side on its first slot
     * (BattleHandlerBase._baseSetUpBattleVariables, lua:330-345).
     */
    fun onRead(state: NdsTrackerState) {
        if (state.inBattle != inBattle) { inBattle = state.inBattle; playerSlot = 0; enemySlot = 0 }
        if (!state.inBattle && locked == null) viewingEnemy = false
    }

    /** Where the view stands: which side, and the slot on each. */
    private data class Pos(val enemy: Boolean, val player: Int, val foe: Int)

    private val here: Pos get() = Pos(viewingEnemy, playerSlot, enemySlot)

    /** How many slots each side has to step through: one outside a fetched battle, where only a locked opponent is left. */
    private fun players(state: NdsTrackerState): Int = if (state.inBattle) state.playerBattlers.size.coerceAtLeast(1) else 1

    private fun foes(state: NdsTrackerState): Int = if (state.inBattle && locked == null) state.enemyBattlers.size.coerceAtLeast(1) else 1

    /**
     * One CHANGE_VIEW (switchPokemonView, lua:808-831, with updatePlayerSlotIndex and updateEnemySlotIndex,
     * BattleHandlerBase.lua:272-302): your next slot, or after your last the opponent's first; the opponent's next, or
     * after its last your first. Both sides go back to their first slot on each change of side. A locked opponent is a
     * side of one, back to yours at once.
     */
    private fun step(p: Pos, state: NdsTrackerState): Pos = when {
        !p.enemy -> if (p.player >= players(state) - 1) Pos(true, 0, 0) else p.copy(player = p.player + 1)
        locked != null -> Pos(false, p.player, p.foe)
        else -> if (p.foe >= foes(state) - 1) Pos(false, 0, 0) else p.copy(foe = p.foe + 1)
    }

    /** The card each [Pos] puts up: your slot, and the opponent's (its first while yours is viewed). */
    private fun cards(p: Pos): Pair<Int, Int> = p.player to (if (p.enemy) p.foe else 0)

    /** A slot read nothing (a battler the tracker could not match): the swap passes it by. */
    private fun shows(p: Pos, state: NdsTrackerState): Boolean =
        if (p.enemy) locked != null || !state.inBattle || state.enemyBattlers.getOrNull(p.foe) != null || (p.foe == 0 && state.enemy != null)
        else !state.inBattle || state.playerBattlers.getOrNull(p.player) != null || p.player == 0

    /**
     * Where the swap goes. [stacked]: both cards are on screen (landscape). The step onto the opponent's first slot puts
     * your first and its first up together, which is where the walk starts and ends, so it is passed over there, as is
     * any step that changes neither card.
     */
    private fun next(state: NdsTrackerState, stacked: Boolean): Pos {
        var p = here
        repeat(players(state) + foes(state) + 1) {
            p = step(p, state)
            if (stacked && p.enemy && p.foe == 0) return@repeat
            val moved = if (stacked) cards(p) != cards(here) else p != here
            if (shows(p, state) && moved) return p
        }
        return here
    }

    /**
     * switchPokemonView: refused in a battle while BattleHandlerBase.isAllowedToSwap is
     * false, the pause after each new opponent ([allowed]); after the battle only a
     * locked opponent is left to see (readMemory keeps it in view while locked).
     */
    fun canSwap(state: NdsTrackerState, allowed: Boolean, stacked: Boolean = false): Boolean =
        shownEnemy(state) != null && (allowed || !state.inBattle) && next(state, stacked) != here

    fun swap(state: NdsTrackerState, allowed: Boolean, stacked: Boolean = false) {
        if (!canSwap(state, allowed, stacked)) return
        val p = next(state, stacked)
        viewingEnemy = p.enemy; playerSlot = p.player; enemySlot = p.foe
    }

    /** The swap button's word: SEE FOE when its next step changes the opponent shown, SEE MINE when it changes yours. */
    fun offersFoe(state: NdsTrackerState, stacked: Boolean): Boolean {
        val n = next(state, stacked)
        return if (stacked) cards(n).second != cards(here).second else n.enemy
    }

    /** Your slot [i] (from 0), in words: "MINE" and where it stands; in a multi battle yours is on the left, your partner's on the right. */
    private fun mine(state: NdsTrackerState, i: Int): SideSpot {
        val n = state.playerBattlers.size
        val place = when {
            state.rotation -> null
            state.enemyAllies > 0 -> "LEFT"
            else -> BattleSideWords.place(false, i, n)
        }
        return SideSpot("MINE", place, i, n)
    }

    /**
     * The opponent's slot [i], in words. In a multi battle your partner's come first (NdsTrackerState.enemyAllies) and
     * stand on your side, on the right of yours; the opponents' stand as in a double battle.
     */
    private fun theirs(state: NdsTrackerState, i: Int): SideSpot {
        val allies = state.enemyAllies
        if (i < allies) return SideSpot("PARTNER", if (allies == 1) "RIGHT" else null, i, allies)
        val n = state.enemyBattlers.size - allies
        return SideSpot("FOE", if (state.rotation) null else BattleSideWords.place(true, i - allies, n), i - allies, n)
    }

    /**
     * The banner's words in a battle with more than one Pokemon a side: the one card (swap) or both ([stacked]). Null
     * in a single battle, or with an opponent locked, which has no slot.
     */
    fun sideWords(state: NdsTrackerState, stacked: Boolean): BannerSide? {
        val (mine, theirs) = shownSpots(state) ?: return null
        return if (stacked) BattleSideWords.pair(mine, theirs) else BattleSideWords.one(if (viewingEnemy) theirs else mine)
    }

    /**
     * Where your Pokemon and the opponent's that the panels show stand, in a battle with more than one a side (the stream's
     * words too). Null in a single battle, or with an opponent locked, which has no slot.
     */
    fun shownSpots(state: NdsTrackerState): Pair<SideSpot, SideSpot>? {
        if (!state.inBattle || locked != null || (state.playerBattlers.size < 2 && state.enemyBattlers.size < 2)) return null
        val (mineAt, foeAt) = cards(here)
        return mine(state, mineAt) to theirs(state, foeAt)
    }

    /** Everything that changes what the view shows, for a watcher that is not composed with the panel (the stream). */
    val key: List<Any?> get() = listOf(viewingEnemy, playerSlot, enemySlot, locked?.mon?.pid)

    /** The swap button's spoken label, naming where its next Pokemon stands; null in a single battle. */
    fun swapSpoken(state: NdsTrackerState, stacked: Boolean): String? {
        if (sideWords(state, stacked) == null) return null
        val n = next(state, stacked)
        val foe = offersFoe(state, stacked)
        return PcBannerCopy.seeSpoken(foe, if (foe) theirs(state, cards(n).second) else mine(state, cards(n).first))
    }

    private var paused = false

    /**
     * The pause after each new opponent (BattleHandlerBase._logNewEnemy's delay):
     * [ready] is false while it runs. Its end, _onDelayFinished (lua:119-124), calls
     * Program.switchToEnemy (lua:797-806): the opponent comes into view when
     * AUTO_SWAP_TO_ENEMY is on ([autoSwap]). Only the end of a pause swaps, so turning
     * the phone mid-battle does not.
     */
    fun onPause(ready: Boolean, state: NdsTrackerState, autoSwap: Boolean) {
        if (ready && paused && autoSwap && state.inBattle && state.enemy != null) viewingEnemy = true
        paused = !ready
    }

    /**
     * onLockButtonPress (lua:833-855): with ENABLE_ENEMY_LOCKING on ([enabled]), the
     * opponent on the field is copied and locked (lockEnemy needs a battle and an
     * opponent); pressed again, it is let go (unlockEnemy), whatever the setting.
     */
    fun toggleLock(state: NdsTrackerState, enabled: Boolean) {
        if (locked != null) { unlock(); onRead(state); return }
        if (enabled && state.inBattle && state.enemy != null) locked = state.enemy
    }

    /**
     * unlockEnemy (lua:841-846), which BattleOptionsScreen.onToggleClick also calls
     * when the setting goes off (lua:27-33). In a battle the view stays on the
     * opponent, now the one on the field; after one, [onRead] brings yours back.
     */
    fun unlock() {
        locked = null
    }
}

/** The one view the DS panels share. */
internal val dsView = DsViewState()

/** IconDrawer's LOCKED and UNLOCKED (IconDrawer.lua:1022-1055), 7 by 10, in the top box's text colour. */
private val DS_LOCKED = listOf(
    "0000000", "0011000", "0100100", "0100100", "1111110",
    "1111110", "1111110", "1111110", "1111110", "1111110",
)
private val DS_UNLOCKED = listOf(
    "0000000", "0011000", "0100100", "0000100", "1111110",
    "1000010", "1000010", "1000010", "1000010", "1111110",
)

/**
 * MainScreen's lock icon (setEnemySpecificControls, lua:569-577): on the opponent's
 * card with "Enable enemy locking" on, LOCKED or UNLOCKED. The reference only shows
 * it; here it is also what locks and unlocks.
 */
@Composable
internal fun DsLockIcon(locked: Boolean, onTap: () -> Unit) {
    PcPixelImage(if (locked) DS_LOCKED else DS_UNLOCKED, Pc.Text,
        Modifier.clickable { onTap() }.padding(horizontal = 3.dp, vertical = 2.dp))
}

/**
 * After a battle, with an opponent locked: the swap between your Pokemon and the
 * locked one, where the battle banner carries it in a battle (Start in the
 * reference, which readMemory lets stay on the opponent while it is locked).
 */
@Composable
internal fun DsLockedBanner(viewingOwn: Boolean, onSwap: () -> Unit) {
    // The same strip and touch band as the battle banner (PcBannerBand, 2026-09-30, UX audit P0-14): SEE FOE and
    // SEE MINE have a 44dp touch box round the drawn button. This strip stays solid, as it always was.
    PcBannerBand(
        fill = windowFill(Pc.Ground), buttons = true,
        label = { PixText("ENEMY LOCKED", PcRef.FONT, Pc.Text) },
    ) {
        PcButton(PcBannerCopy.see(viewingOwn), spoken = PcBannerCopy.seeSpoken(viewingOwn), onClick = onSwap)
    }
}
