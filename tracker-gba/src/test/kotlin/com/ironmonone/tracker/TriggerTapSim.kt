package com.ironmonone.tracker

/**
 * libretrodroid's cpp/triggertap.cpp in Kotlin, for the tests: the same arming bounds (every address in the GBA's work
 * RAM), the same once-per-arrival catch, the same copy, the same fixed ring that keeps one copy of a repeated catch
 * and counts what does not fit, the same packing. The emulator calls the C++ after every emulated frame; a test calls
 * [frame] after each frame it plays. A change to either goes into both.
 */
class TriggerTapSim(private val memory: MemoryReader) : TriggerTap {
    private var armed = false
    private var watch = 0L
    private var targets = LongArray(0)
    private var addresses = LongArray(0)
    private var lengths = IntArray(0)
    private var contextLength = 0
    private var haveLast = false
    private var last = 0L
    private var frameNo = 0L
    private val ring = ArrayList<Triple<Long, Long, ByteArray>>()

    /** What the tap did since arming: frames looked at, arrivals caught, catches dropped for room. */
    var framesSeen = 0L; private set
    var caught = 0; private set
    var droppedTotal = 0; private set
    private var dropped = 0
    /** The most catches the ring held at once. */
    var mostHeld = 0; private set

    private fun inRam(address: Long, length: Long) =
        length > 0 && address >= RAM_START && length <= RAM_SIZE && address - RAM_START <= RAM_SIZE - length

    override fun arm(watch: Long, targets: LongArray, rangeAddresses: LongArray, rangeLengths: IntArray): Boolean {
        armed = false; ring.clear(); dropped = 0; frameNo = 0; haveLast = false
        if (rangeAddresses.size != rangeLengths.size) return false
        if (targets.isEmpty() || targets.size > MAX_TARGETS || rangeAddresses.size > MAX_RANGES || !inRam(watch, 4)) return false
        if (targets.any { it < 0 || it > 0xFFFFFFFFL }) return false
        for (i in rangeAddresses.indices) if (rangeLengths[i] !in 1..MAX_RANGE_LENGTH || !inRam(rangeAddresses[i], rangeLengths[i].toLong())) return false
        if (rangeLengths.sum() > MAX_CONTEXT) return false
        this.watch = watch
        this.targets = targets.sortedArray()
        this.addresses = rangeAddresses.copyOf()
        this.lengths = rangeLengths.copyOf()
        contextLength = rangeLengths.sum()
        armed = true
        return true
    }

    override fun disarm() { armed = false; ring.clear(); dropped = 0; haveLast = false }

    /** After every emulated frame (LibretroDroid::step and stepBot, through tapFrame). */
    fun frame() {
        if (!armed) return
        frameNo++; framesSeen++
        val w = memory.read(watch, 4)
        if (w.size != 4) return
        val v = w.u32(0)
        if (haveLast && v == last) return
        haveLast = true; last = v
        if (targets.binarySearch(v) < 0) return
        val ctx = ByteArray(contextLength)
        var at = 0
        for (i in addresses.indices) {
            val b = memory.read(addresses[i], lengths[i])
            if (b.size == lengths[i]) b.copyInto(ctx, at)
            at += lengths[i]
        }
        caught++
        if (ring.any { it.second == v && it.third.contentEquals(ctx) }) return
        if (ring.size >= CAPACITY) { dropped++; droppedTotal++; return }
        ring += Triple(frameNo, v, ctx)
        mostHeld = maxOf(mostHeld, ring.size)
    }

    override fun drain(): ByteArray {
        if (!armed) return ByteArray(0)
        val out = java.io.ByteArrayOutputStream()
        fun u32(v: Long) { for (i in 0 until 4) out.write(((v ushr (8 * i)) and 0xFF).toInt()) }
        u32(ring.size.toLong()); u32(contextLength.toLong()); u32(dropped.toLong())
        for ((f, v, ctx) in ring) { u32(f); u32(v); out.write(ctx) }
        ring.clear(); dropped = 0
        return out.toByteArray()
    }

    companion object {
        // triggertap.h
        const val RAM_START = 0x02000000L
        const val RAM_SIZE = 0x40000L
        const val MAX_TARGETS = 512
        const val MAX_RANGES = 32
        const val MAX_RANGE_LENGTH = 64
        const val MAX_CONTEXT = 256
        const val CAPACITY = 64
    }
}
