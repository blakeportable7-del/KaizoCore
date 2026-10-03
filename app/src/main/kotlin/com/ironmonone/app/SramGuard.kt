package com.ironmonone.app

import com.swordfish.libretrodroid.GLRetroView
import java.io.File

/**
 * An in-game save the core refused to load (rc32 audit P3 #100): too big for the game's save RAM, or a core with none.
 * The answer used to be yes whatever happened, and the app's next flush wrote the core's blank save over the player's.
 * Now a copy is kept beside it, under a name no save reader opens, before that flush can run, and the player is told.
 */
internal object SramGuard {
    fun check(view: GLRetroView?, srm: File) {
        if (view == null || !view.sramLoadRefused) return
        keep(srm)
    }

    /** True when a copy was made. */
    internal fun keep(srm: File): Boolean {
        if (!srm.isFile) return false
        val copy = File(srm.parentFile, srm.name + ".refused")
        val kept = runCatching { SafeWrite.bytes(copy, srm.readBytes()) }.getOrDefault(false)
        SaveTrouble.report(SaveTrouble.BATTERY,
            if (kept) "This game's save could not be loaded. A copy was kept, so nothing was lost."
            else "This game's save could not be loaded.")
        return kept
    }
}
