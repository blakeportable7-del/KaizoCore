package com.ironmonone.tracker

/**
 * The emulator's per-frame watch on the battle-script pointer (libretrodroid's cpp/triggertap.h, KaizoCore patch
 * 2026-10-04), as the tracker sees it.
 *
 * The reference tracker runs inside the emulator's frame loop: Battle.update runs every frame and checks the ability
 * scripts every 30 emulated frames (Battle.lua:116-139, Program.lua:16-17 and :634-635), whatever the speed. The app's
 * tracker looked on a wall clock, every 31 ms between full reads at best: about 15 emulated frames at 8x and 30 at 16x,
 * and more around each full read, so an ability message that held the pointer for fewer frames than that was never seen
 * (Blake, 2026-10-04: abilities missed at 8x, caught at 4x). The tap looks after every emulated frame instead, and when
 * the pointer arrives on an ability script's address it keeps a copy of the memory the check reads. The tracker drains
 * the copies on its own polls and runs the reference's check (Battle.checkAbilitiesToTrack, Battle.lua:616-726) on each
 * one as that frame left it, so how fast the game runs no longer matters.
 *
 * Null on a tracker without one (the tests, a core without it): the tracker then looks on its own polls, as before.
 */
interface TriggerTap {
    /**
     * Watch the u32 at [watch]; each time it changes to one of [targets], copy each range ([rangeAddresses],
     * [rangeLengths]). Replaces any earlier arming. False when the emulator refused it.
     */
    fun arm(watch: Long, targets: LongArray, rangeAddresses: LongArray, rangeLengths: IntArray): Boolean

    /** What was caught since the last drain, packed as [TapHits.parse] reads it; empty when there is nothing or the tap is off. */
    fun drain(): ByteArray

    fun disarm()
}

/** One arrival of the watched pointer on a target: the frame it happened on, the value, and the ranges as that frame left them. */
class TapHit(val frame: Long, val value: Long, val context: ByteArray)

/**
 * What the tracker arms the tap with: the word to watch, the values to catch, and the memory to copy (each range's
 * address and length, in order). [view] serves a caught frame back to the tracker's own reads.
 */
class TapPlan(val watch: Long, val targets: LongArray, val addresses: LongArray, val lengths: IntArray) {
    companion object {
        /** The GBA's work RAM, the only memory the tap watches or copies (cpp/triggertap.h RAM_START, RAM_SIZE). */
        const val RAM_START = 0x02000000L
        const val RAM_END = 0x02040000L
        fun inRam(address: Long, length: Int): Boolean = length > 0 && address >= RAM_START && address + length <= RAM_END
    }

    private val offsets = IntArray(lengths.size).also { o -> var at = 0; for (i in lengths.indices) { o[i] = at; at += lengths[i] } }
    val contextLength: Int = lengths.sum()

    /**
     * Memory as [hit]'s frame left it: a read inside one of the copied ranges is served from the copy, the watched word
     * is the caught value, and everything else (the parties, the species data, the ROM) comes from [live], which a
     * battle does not change between the frame and the drain.
     */
    fun view(live: MemoryReader, hit: TapHit): MemoryReader = MemoryReader { address, length ->
        if (address == watch && length in 1..4) {
            ByteArray(length) { ((hit.value ushr (8 * it)) and 0xFF).toByte() }
        } else {
            var served: ByteArray? = null
            for (i in addresses.indices) {
                val start = addresses[i]
                if (address >= start && address + length <= start + lengths[i] && hit.context.size >= offsets[i] + lengths[i]) {
                    val from = offsets[i] + (address - start).toInt()
                    served = hit.context.copyOfRange(from, from + length)
                    break
                }
            }
            served ?: live.read(address, length)
        }
    }
}

object TapHits {
    /**
     * The tap's drain: little-endian u32s, the hit count, the context length per hit and the hits dropped for room; then
     * per hit its frame number, the watched value and [contextLength] bytes. Nothing (or a malformed block) reads as no hits.
     */
    fun parse(bytes: ByteArray, contextLength: Int): List<TapHit> {
        if (bytes.size < 12) return emptyList()
        val count = bytes.u32(0).toInt()
        val ctx = bytes.u32(4).toInt()
        if (ctx != contextLength || count < 0 || count > 4096) return emptyList()
        val per = 8 + ctx
        if (bytes.size < 12 + count.toLong() * per) return emptyList()
        return List(count) { i ->
            val at = 12 + i * per
            TapHit(bytes.u32(at), bytes.u32(at + 4), bytes.copyOfRange(at + 8, at + 8 + ctx))
        }
    }
}
