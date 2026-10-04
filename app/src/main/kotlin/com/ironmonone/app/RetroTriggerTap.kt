package com.ironmonone.app

import com.swordfish.libretrodroid.GLRetroView

/**
 * The Gen 3 tracker's trigger tap (com.ironmonone.tracker.TriggerTap) on the running core: libretrodroid's per-frame
 * watch on the battle-script pointer (cpp/triggertap.h). The core runs it after every emulated frame, fast forward
 * included, so an ability message is seen at 8x and 16x as at 1x (Blake, 2026-10-04: missed at 8x, caught at 4x).
 */
class RetroTriggerTap(private val retro: GLRetroView) : com.ironmonone.tracker.TriggerTap {
    /** This arming's token: a disarm lets go of this arming only, never of a newer tracker's. */
    @Volatile private var token = 0L

    override fun arm(watch: Long, targets: LongArray, rangeAddresses: LongArray, rangeLengths: IntArray): Boolean {
        token = retro.armTriggerTap(watch, targets, rangeAddresses, rangeLengths)
        return token != 0L
    }

    override fun drain(): ByteArray = retro.drainTriggerTap()

    override fun disarm() { token.takeIf { it != 0L }?.let { retro.disarmTriggerTap(it) }; token = 0L }

    companion object {
        /** [tracker] with the tap of [retro] armed for its game. */
        fun attach(tracker: com.ironmonone.tracker.GbaTracker, retro: GLRetroView): com.ironmonone.tracker.GbaTracker =
            tracker.also { it.triggerTap = RetroTriggerTap(retro) }
    }
}
