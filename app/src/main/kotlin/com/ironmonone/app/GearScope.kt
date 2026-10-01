package com.ironmonone.app

import java.io.File

/**
 * What Tracker Setup is open over: the game and the mode, so each row shows only where it does something (Blake,
 * 2026-10-01: "the setup menu should be variable depending on the mode, and the game", and "if you are playing emerald,
 * and the set up menu mentions fire red maps, that's a problem"). Worked out here from PrepStore's session, as
 * PlayRules' game-over card does, because PlayScreen is at the verifier's limit and passes only [gameBoy] and [ds].
 */
internal data class GearScope(
    val gameBoy: Boolean,
    val ds: Boolean,
    /** RomKind.family of the game in Play: RBY, GSC, FRLG, RSE, DPPt, HGSS, BW, B2W2. */
    val family: String?,
    val natDex: Boolean,
    val rules: PlayRules.Kind,
    /** A randomized run in Play (a Kaizo IronMON run, or a randomized Nuzlocke). */
    val isRun: Boolean,
) {
    /** A Gen 3 game, Nat. Dex builds included: the rows only the Gen 3 tracker reads. */
    val gen3: Boolean get() = !gameBoy && !ds

    /** FireRed or LeafGreen, a Nat. Dex FireRed included: the dungeon maps' games. */
    val frlg: Boolean get() = family == "FRLG"

    /** A Kaizo IronMON run: the run-over rule, the game over lines, past runs and the like act only there. */
    val ironmon: Boolean get() = rules == PlayRules.Kind.IRONMON

    /** A game with no run and no Nuzlocke: no rules to read. */
    val plain: Boolean get() = rules == PlayRules.Kind.PLAIN

    /** A randomized game: a run, or a Nat. Dex build, which the tracker reads as randomized. Clean library games are not. */
    val randomized: Boolean get() = isRun || natDex

    companion object {
        fun of(filesDir: File, gameBoy: Boolean, ds: Boolean): GearScope {
            val session = runCatching { PrepStore(filesDir).session() }.getOrNull()
            val kind = session?.kind
            return GearScope(
                gameBoy, ds, kind?.family, kind?.isNatDex == true,
                session?.let { s -> runCatching { PlayRules.kind(s, filesDir) }.getOrNull() } ?: PlayRules.Kind.PLAIN,
                session?.isRun == true,
            )
        }
    }
}
