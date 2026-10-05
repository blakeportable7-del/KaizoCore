package com.ironmonone.tracker

/**
 * The battle scripts the move tracking watches, per game (GameSettings.BattleScript_*, from the reference's
 * GameAddresses; the Nat. Dex expansion publishes its own): Focus Punch's set-up, a snatched move, and the eleven
 * "is confused", "is in love", "is frozen", "thawed out" and "woke up" pauses during which the move a Pokemon chose may
 * still not happen (Battle.moveDelayed, Battle.lua:1130-1142).
 */
data class MoveScripts(
    val focusPunchSetUp: Long,
    val snatchedMove: Long,
    val isConfused: Long,
    val isConfused2: Long,
    val isConfusedNoMore: Long,
    val wokeUp: Long,
    val isInLove: Long,
    val isInLove2: Long,
    val isFrozen: Long,
    val isFrozen2: Long,
    val isFrozen3: Long,
    val unfroze: Long,
    val unfroze2: Long,
    /**
     * The same pauses as whole scripts, for a build whose script labels are known but not the reference's offsets into
     * them (Heart & Soul: from the ELF, each label to the next symbol). The pointer anywhere inside one counts.
     */
    val delayedRanges: List<LongRange> = emptyList(),
) {
    /** Battle.moveDelayed's scripts. */
    val delayed: Set<Long> = setOf(isConfused, isConfused2, isConfusedNoMore, wokeUp, isInLove, isInLove2,
        isFrozen, isFrozen2, isFrozen3, unfroze, unfroze2) - 0L

    /** Whether the battle script at [script] is one of those pauses. */
    fun isDelayed(script: Long): Boolean = script in delayed || delayedRanges.any { script in it }

    companion object {
        val EMERALD = MoveScripts(0x082DB20F, 0x082DB1B6, 0x082DB2C0, 0x082DB2C9, 0x082DB303, 0x082DB22E,
            0x082DB32A, 0x082DB333, 0x082DB26D, 0x082DB270, 0x082DB272, 0x082DB27C, 0x082DB281)
        val FIRERED_V10 = MoveScripts(0x081D9025, 0x081D8FCC, 0x081D90D6, 0x081D90DF, 0x081D9119, 0x081D9044,
            0x081D9140, 0x081D9149, 0x081D9083, 0x081D9086, 0x081D9088, 0x081D9092, 0x081D9097)
        val FIRERED_V11 = MoveScripts(0x081D9095, 0x081D903C, 0x081D9146, 0x081D914F, 0x081D9189, 0x081D90B4,
            0x081D91B0, 0x081D91B9, 0x081D90F3, 0x081D90F6, 0x081D90F8, 0x081D9102, 0x081D9107)
        val LEAFGREEN_V10 = MoveScripts(0x081D9001, 0x081D8FA8, 0x081D90B2, 0x081D90BB, 0x081D90F5, 0x081D9020,
            0x081D911C, 0x081D9125, 0x081D905F, 0x081D9062, 0x081D9064, 0x081D906E, 0x081D9073)
        val RUBY_V10 = MoveScripts(0x081D94EA, 0x081D9491, 0x081D9598, 0x081D95A1, 0x081D95D7, 0x081D9509,
            0x081D95FE, 0x081D9607, 0x081D9548, 0x081D954B, 0x081D954D, 0x081D9557, 0x081D955C)
        val SAPPHIRE_V10 = MoveScripts(0x081D947A, 0x081D9421, 0x081D9528, 0x081D9531, 0x081D9567, 0x081D9499,
            0x081D958E, 0x081D9597, 0x081D94D8, 0x081D94DB, 0x081D94DD, 0x081D94E7, 0x081D94EC)
        /** MaxDex 1.0: MaxDexExtension.lua 1.0's FireRed BattleScript_* lines (each a symbol plus its offset), summed. */
        val MAXDEX_FR_10 = MoveScripts(0x081DFA3A, 0x081DF9E1, 0x081DFB18, 0x081DFB21, 0x081DFB5B, 0x081DFA59,
            0x081DFB82, 0x081DFB8B, 0x081DFA98, 0x081DFA9B, 0x081DFA9D, 0x081DFAA7, 0x081DFAAC)

        /**
         * NatDexExtension.lua:17749-17761: each script is a published pointer plus an offset into it. [ptr] reads the
         * ROM's u32 at a slot; a slot that does not point into the ROM gives 0, which matches no script.
         */
        fun natDex(ptr: (Long) -> Long): MoveScripts {
            fun at(slot: Long, off: Int): Long = ptr(slot).takeIf { it in 0x08000000L..0x09FFFFFFL }?.plus(off) ?: 0L
            return MoveScripts(
                focusPunchSetUp = at(0x0800032C, 0x10), snatchedMove = at(0x08000328, 0xA),
                isConfused = at(0x0800033C, 0x3), isConfused2 = at(0x0800033C, 0xC), isConfusedNoMore = at(0x08000340, 0x3),
                wokeUp = at(0x08000330, 0xE), isInLove = at(0x08000344, 0x3), isInLove2 = at(0x08000344, 0xC),
                isFrozen = at(0x08000334, 0x3), isFrozen2 = at(0x08000334, 0x6), isFrozen3 = at(0x08000334, 0x8),
                unfroze = at(0x08000338, 0x5), unfroze2 = at(0x08000338, 0xA),
            )
        }
    }
}

/**
 * Battle.lua's move tracking (updateTrackedInfo, lines 376-445), one poll at a time: which opposing battler used which
 * move. It replaced reading gBattleResults' last opponent move on its own, which is the move the AI CHOSE, written
 * before the game checks whether the Pokemon can act, and kept after the Pokemon that chose it has gone: the tracker
 * revealed moves never used (asleep, paralysed, flinching, confused, loafing), handed a fainted foe's last move to the
 * next one, and filed a doubles partner's moves under the left foe (rc33 audit P1 #72). A move is recorded only:
 *  - in a battler's own move action (gCurrentTurnActionNumber below gBattlersCount, its gActionsByTurnOrder entry
 *    B_ACTION_USE_MOVE, no confirmation pending in gBattleCommunication), once the battle's first action is under way;
 *  - one poll after that action began, once, and only if the game did not mark the Pokemon unable to use it
 *    (gHitMarker HITMARKER_UNABLE_TO_USE_MOVE);
 *  - never while a "confused", "in love", "frozen" or "woke up" pause is showing (Battle.moveDelayed);
 *  - for an opposing battler, and only a move its Pokemon knew as the battle began, not one Mimic gave it (Battle.lua:432).
 * Focus Punch's set-up names it before it becomes the last move used (Battle.lua:452-470), and a snatched move belongs to
 * the battler that snatched it (Battle.lua:409-426). Struggle is never tracked (Tracker.TrackMove).
 */
class EnemyMoveWatch {
    /** One poll's reads. */
    data class Frame(
        /** gBattlersCount: 2, or 4 in a double battle. */
        val battlers: Int,
        /** gBattlerAttacker. */
        val attacker: Int,
        /** gBattleScripting.battler, already taken modulo [battlers] (Battle.readBattleValues). */
        val scriptingBattler: Int,
        /** gCurrentTurnActionNumber. */
        val actionNumber: Int,
        /** gActionsByTurnOrder[gCurrentTurnActionNumber]; 0 is B_ACTION_USE_MOVE. */
        val action: Int,
        /** gBattleCommunication's confirmed count (offsetBattleCommConfirmedCount, +4). */
        val confirmedCount: Int,
        /** gHitMarker. */
        val hitMarker: Long,
        /** gBattlescriptCurrInstr. */
        val script: Long,
        /** gBattleResults' last move used by the player's side (+0x22) and by the opposing side (+0x24). */
        val lastMovePlayer: Int,
        val lastMoveOpponent: Int,
    )

    private var firstActionTaken = false
    private var prevAction = 4
    private var recordNext = false

    /** Battle.lua:758-759: a new battle. */
    @Synchronized
    fun reset() { firstActionTaken = false; prevAction = 4; recordNext = false }

    /**
     * One poll: the opposing battler and the move it used, or null. [knew] gives a battler's four moves as its Pokemon
     * has them in its party; [maxMove] is the game's last move id (MoveData.getTotal).
     */
    @Synchronized
    fun tick(f: Frame, scripts: MoveScripts?, maxMove: Int, knew: (battler: Int) -> List<Int>): Pair<Int, Int>? {
        fun lastBy(battler: Int) = if (battler % 2 == 0) f.lastMovePlayer else f.lastMoveOpponent
        val last = lastBy(f.attacker)
        // "Handles this value not being cleared from the previous battle" (Battle.lua:381-387).
        if (f.actionNumber <= 1 && (last != 0 || f.action != 0)) firstActionTaken = true
        if (scripts != null && scripts.isDelayed(f.script)) return null
        if (scripts != null && scripts.focusPunchSetUp != 0L && f.script == scripts.focusPunchSetUp)
            return if (f.attacker % 2 == 1 && FOCUS_PUNCH in knew(f.attacker)) f.attacker to FOCUS_PUNCH else null
        if (f.actionNumber >= f.battlers || !firstActionTaken || f.confirmedCount != 0 || f.action != 0) return null
        if (last !in 1..maxMove) return null
        if (prevAction != f.actionNumber) { recordNext = true; prevAction = f.actionNumber; return null }
        if (!recordNext) return null
        // Only one chance to record per action.
        recordNext = false
        if (f.hitMarker and UNABLE_TO_USE_MOVE != 0L) return null
        val snatched = scripts != null && scripts.snatchedMove != 0L && f.script == scripts.snatchedMove
        val battler = if (snatched) f.scriptingBattler else f.attacker
        val move = lastBy(battler)
        // Only the opposing side's moves are tracked: your own could be TMs or moves kept from earlier levels.
        if (battler % 2 == 0 || move == STRUGGLE || move !in 1..maxMove) return null
        return if (move in knew(battler)) battler to move else null
    }

    companion object {
        const val FOCUS_PUNCH = 264
        const val STRUGGLE = 165
        /** HITMARKER_UNABLE_TO_USE_MOVE (Program.Addresses.hitmarkerFlag80000). */
        const val UNABLE_TO_USE_MOVE = 0x80000L
    }
}
