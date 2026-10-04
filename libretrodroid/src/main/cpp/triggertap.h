/*
 *     KaizoCore patch (2026-10-04) to LibretroDroid. GPL-3.0, as the rest of this module.
 *
 *     The ability trigger tap: one 32-bit word of the GBA's work RAM watched after EVERY emulated frame.
 *
 *     The Gen 3 tracker knows an enemy's ability is revealed when the game's battle-script pointer
 *     (gBattlescriptCurrInstr) stands on one of the ability scripts' addresses (the reference tracker's
 *     Battle.checkAbilitiesToTrack). The reference runs inside the emulator's frame loop and looks every
 *     30 emulated frames, whatever the speed. The app's tracker looked on a wall clock, every 31 ms at
 *     best: 15 emulated frames at 8x, 30 at 16x, more around each full read, so an ability message that
 *     held the pointer for fewer frames than that was never seen (Blake, 2026-10-04: missed at 8x, caught
 *     at 4x). This runs after every retro_run(), fast forward included, as Cheevos::doFrame does: when the
 *     watched word CHANGES to one of the armed values it copies the armed ranges (the battler numbers and
 *     battle structs the check reads) into a fixed ring, and the tracker drains the ring on its own polls
 *     and runs its check against each copy.
 *
 *     Cost and safety, frame by frame: nothing at all while disarmed (one bool test). Armed, it asks the
 *     core for its system RAM's pointer and size (two calls the core answers from a field), tests the
 *     armed offsets against that size, loads one u32 and compares it with the last frame's. No allocation,
 *     no lock of its own and no JNI: every member is guarded by LibretroDroid's coreLock, which step() and
 *     stepBot() already hold around the frames, and the JNI side takes for arm, disarm and drain. Every
 *     address is checked to be inside the GBA's work RAM when armed and inside the core's own buffer on
 *     every frame, so a wrong address reads nothing rather than stray memory. Only the GBA tracker arms it;
 *     a game's teardown (LibretroDroid::destroy) disarms it.
 *
 *     tracker-gba's TriggerTapSim.kt (in its tests) is this algorithm in Kotlin, which the tracker's tests
 *     drive frame by frame; a change here goes there too.
 */
#ifndef LIBRETRODROID_TRIGGERTAP_H
#define LIBRETRODROID_TRIGGERTAP_H

#include <array>
#include <cstddef>
#include <cstdint>
#include <utility>
#include <vector>

namespace libretrodroid {

class TriggerTap {
public:
    /** The GBA's work RAM, where every watched or copied address must lie (the core's RETRO_MEMORY_SYSTEM_RAM). */
    static constexpr uint64_t RAM_START = 0x02000000;
    static constexpr uint64_t RAM_SIZE = 0x40000;
    static constexpr size_t MAX_TARGETS = 512;
    static constexpr size_t MAX_RANGES = 32;
    static constexpr size_t MAX_RANGE_LENGTH = 64;
    static constexpr size_t MAX_CONTEXT = 256;
    /** Distinct catches kept between two drains; past that, newer ones are counted as dropped. */
    static constexpr size_t CAPACITY = 64;

    static TriggerTap& getInstance();

    // All of these: the caller holds LibretroDroid's coreLock.

    /**
     * Watch the little-endian u32 at [watch]; on every frame its value changes to one of [targets], copy each of
     * [ranges] (address, length). Replaces any earlier arming and forgets what was caught. Returns this arming's
     * token (never 0), or 0 when the request is out of bounds (and the tap is then off).
     */
    uint32_t arm(uint64_t watch, std::vector<uint32_t> targets, const std::vector<std::pair<uint64_t, uint32_t>>& ranges);

    /** Off, and what was caught forgotten, when [token] is the current arming's or 0 (a teardown). */
    void disarm(uint32_t token);

    /** After every emulated frame. [ram] and [ramSize] are the core's RETRO_MEMORY_SYSTEM_RAM, as it gives them now. */
    void doFrame(const unsigned char* ram, size_t ramSize);

    /** Whether doFrame has anything to do; step() asks before it asks the core for its RAM. */
    bool isArmed() const { return armed; }

    /**
     * What was caught since the last drain, then forgotten. Little-endian u32s: the hit count, the context length per
     * hit, the catches dropped for room; then per hit its frame number, the watched value and the copied ranges in the
     * order they were armed. Empty when the tap is off.
     */
    std::vector<unsigned char> drain();

private:
    TriggerTap() = default;

    struct Hit {
        uint32_t frame;
        uint32_t value;
        std::array<unsigned char, MAX_CONTEXT> context;
    };

    bool armed = false;
    uint32_t token = 0;
    uint32_t watchOffset = 0;
    std::vector<uint32_t> targets;
    std::vector<std::pair<uint32_t, uint32_t>> ranges;   // offset into RAM, length
    size_t contextLength = 0;
    /** The highest byte any armed read touches, plus one: the core's RAM must reach it. */
    size_t ramNeeded = 0;
    bool haveLast = false;
    uint32_t last = 0;
    uint32_t frame = 0;
    uint32_t dropped = 0;
    std::array<Hit, CAPACITY> ring {};
    size_t count = 0;
};

}

#endif //LIBRETRODROID_TRIGGERTAP_H
