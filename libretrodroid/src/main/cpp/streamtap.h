/*
 * KaizoCore patch (2026-09-29): the stream kit's taps.
 *
 * The picture and the sound the core produces, copied out so the stream server
 * (Kotlin, app/.../stream/) can send them to OBS over Wi-Fi. Nothing here talks
 * to the network, and nothing here is upstream LibretroDroid.
 *
 * Two rules, in this order:
 *
 *  1. Playing costs nothing while nobody watches. Every path in libretrodroid.cpp
 *     asks StreamTap::enabled() first, which is one relaxed atomic load. The
 *     Kotlin side turns it on when the first viewer connects and off when the last
 *     one leaves (setStreamCapture in LibretroDroid.java).
 *
 *  2. The emulation thread never waits. The frame goes through a lock-free
 *     triple buffer and the sound through a lock-free single-producer ring. The
 *     reader (the server's pump thread, over JNI) can be slow, stalled or gone:
 *     the emulation thread copies its frame, swaps an index and moves on. A full
 *     ring drops the NEW samples rather than block.
 *
 * Frames are copied in the core's own pixel format and size, with the row
 * padding (pitch) removed, and converted to tightly packed RGB only when the
 * reader asks, on the reader's thread. They are taken BEFORE the renderer sees
 * them: ImageRendererES3 rewrites 0RGB1555 frames in place and ImageRendererES2
 * swaps red and blue of XRGB8888 in place, so a copy taken afterwards would not
 * be in the format Environment reports.
 *
 * Cores: mGBA, Gambatte and melonDS all render in software and reach
 * LibretroDroid::handleVideoRefresh with a pixel pointer, so all three are
 * covered. A core that renders through OpenGL passes RETRO_HW_FRAME_BUFFER_VALID
 * instead of pixels; the caller skips those, because reading the framebuffer back
 * would stall the GPU on the emulation thread. None of the shipped cores does it.
 */

#ifndef LIBRETRODROID_STREAMTAP_H
#define LIBRETRODROID_STREAMTAP_H

#include <atomic>
#include <cstddef>
#include <cstdint>
#include <memory>
#include <mutex>

namespace libretrodroid {

class StreamTap {
public:
    // The values of enum retro_pixel_format. libretrodroid.cpp static_asserts
    // them against libretro.h, so this header does not need to include it.
    static constexpr int FORMAT_0RGB1555 = 0;
    static constexpr int FORMAT_XRGB8888 = 1;
    static constexpr int FORMAT_RGB565 = 2;

    // int16 samples the sound ring holds (two per stereo frame): one second at
    // mGBA's 65536 Hz, two at melonDS's 32768 Hz. The reader drains every few
    // milliseconds, so this is headroom for a stall, not working room.
    static constexpr size_t RING_SAMPLES = 1u << 17;

    static StreamTap& getInstance();

    // Any thread. The emulation and audio paths test this before doing anything.
    static bool enabled() { return captureOn.load(std::memory_order_relaxed); }

    // Any thread (Kotlin calls it over JNI). Turning it on discards sound that a
    // previous viewer left behind.
    static void setEnabled(bool on);

    // The rate the samples arrive at in real time, in Hz: the core's rate scaled
    // for the fast-forward speed. Any thread. Static, like the flag, so setting it
    // never builds the tap (and its sound ring) for a player who does not stream.
    static void setAudioRate(int hz) { audioRateHz.store(hz, std::memory_order_relaxed); }

    // Any thread: the reader discards what is buffered before its next drain.
    static void flushAudio() { flushRequested.store(true, std::memory_order_release); }

    // ---- Emulation thread only (the one inside retro_run). ------------------

    // Copies one core frame. `pixelFormat` is the value of enum retro_pixel_format
    // the core negotiated. Drops the frame when it cannot be stored.
    void onFrame(const void* data, unsigned width, unsigned height, size_t pitch, int pixelFormat);

    // Copies interleaved stereo samples (`frames` of them, two int16 each).
    void onAudio(const int16_t* samples, size_t frames);

    // ---- Reader side (one at a time; several callers just take turns). ------

    // Writes the newest frame as tightly packed RGB (width * height * 3 bytes) to
    // `dst` when it is newer than `afterCounter`.
    //   > 0  the frame's counter; `dst`, `*width` and `*height` are filled
    //     0  nothing newer than `afterCounter`
    //    -1  `capacity` is too small; `*width` and `*height` say what is needed
    int64_t copyFrame(uint8_t* dst, size_t capacity, int64_t afterCounter, int* width, int* height);

    // Moves up to `maxSamples` buffered samples (an even number: whole stereo
    // frames) into `dst`. Returns how many; `*rateHz` gets the current rate.
    size_t drainAudio(int16_t* dst, size_t maxSamples, int* rateHz);

    // Samples dropped because the ring was full, since the process started.
    uint64_t droppedSamples() const { return dropped.load(std::memory_order_relaxed); }

private:
    StreamTap();

    struct Slot {
        std::unique_ptr<uint8_t[]> data;
        size_t capacity = 0;
        unsigned width = 0;
        unsigned height = 0;
        int format = FORMAT_RGB565;
        int64_t counter = 0;
    };

    static constexpr uint32_t FRESH = 4u;   // set in `middle` while it holds an unread frame
    static constexpr uint32_t INDEX = 3u;

    static std::atomic<bool> captureOn;
    static std::atomic<bool> flushRequested;
    static std::atomic<int> audioRateHz;

    // Triple buffer. Three slots are always split three ways: the writer owns
    // `back`, the reader owns `front`, and `middle` (with the FRESH bit) hands
    // frames from one to the other. Each side swaps its own slot for `middle`
    // with one atomic exchange, so neither ever waits for the other.
    Slot slots[3];
    std::atomic<uint32_t> middle { 1u };
    uint32_t back = 0;                      // emulation thread only
    uint32_t front = 2;                     // reader only, under readerLock
    int64_t nextCounter = 1;                // emulation thread only
    std::mutex readerLock;                  // readers take turns; the writer never touches it

    // Sound: single producer (head), single consumer (tail). Indices only grow
    // and wrap with the 32-bit unsigned arithmetic, which is exact because the
    // ring size divides 2^32.
    std::unique_ptr<int16_t[]> ring;
    std::atomic<uint32_t> head { 0 };
    std::atomic<uint32_t> tail { 0 };
    std::atomic<uint64_t> dropped { 0 };

    uint8_t lut5[32];
    uint8_t lut6[64];
};

} // namespace libretrodroid

#endif //LIBRETRODROID_STREAMTAP_H
