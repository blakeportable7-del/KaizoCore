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
 * Singles only: the tracker reads one opponent, so the doubles steps through the
 * slots (and L and R, LEFT/RIGHT_EFFECTIVENESS) have nothing to step through yet.
 */
internal class DsViewState {
    /** selectedPlayer is ENEMY. */
    var viewingEnemy by mutableStateOf(false)
        private set

    /** lockedPokemonCopy: the opponent as it was when locked; null while nothing is. */
    var locked by mutableStateOf<NdsTrackedMon?>(null)
        private set

    private var attempt = Int.MIN_VALUE

    /** The run the view belongs to: a new one starts on your Pokemon with nothing locked. */
    fun forAttempt(a: Int) {
        if (a != attempt) { attempt = a; viewingEnemy = false; locked = null }
    }

    /** The play screen closed: nothing viewed or locked carries into the next game, DS or not. */
    fun clear() { attempt = Int.MIN_VALUE; viewingEnemy = false; locked = null }

    /**
     * The opponent the enemy view shows (Program.getPokemonToDraw, lua:537-555): the
     * locked one, else the one on the field in a battle.
     */
    fun shownEnemy(state: NdsTrackerState): NdsTrackedMon? = locked ?: state.enemy?.takeIf { state.inBattle }

    /** Program.readMemory (lua:650-652): outside a battle, with nothing locked, the view is yours. */
    fun onRead(state: NdsTrackerState) {
        if (!state.inBattle && locked == null) viewingEnemy = false
    }

    /**
     * switchPokemonView in a single battle (lua:808-831): your Pokemon and the
     * opponent in turn. Refused in a battle while BattleHandlerBase.isAllowedToSwap
     * is false, the pause after each new opponent ([allowed]); after the battle only
     * a locked opponent is left to see (readMemory keeps it in view while locked).
     */
    fun canSwap(state: NdsTrackerState, allowed: Boolean): Boolean =
        shownEnemy(state) != null && (allowed || !state.inBattle)

    fun swap(state: NdsTrackerState, allowed: Boolean) {
        if (canSwap(state, allowed)) viewingEnemy = !viewingEnemy
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
        fill = Pc.Ground, buttons = true,
        label = { PixText("ENEMY LOCKED", PcRef.FONT, Pc.Text) },
    ) {
        PcButton(PcBannerCopy.see(viewingOwn), spoken = PcBannerCopy.seeSpoken(viewingOwn), onClick = onSwap)
    }
}
