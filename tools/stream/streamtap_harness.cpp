// KaizoCore stream kit (2026-09-29): exercises libretrodroid/src/main/cpp/streamtap.cpp off the
// phone, with real threads, because the JVM tests cannot reach native code and a device is not
// always at hand.
//
// What it proves, in order:
//   1. every pixel format converts to the RGB a reference formula gives, pitch padding included;
//   2. the frame counter, the "nothing newer" answer, the "buffer too small" answer, bad input;
//   3. the sound ring keeps order across its wrap, drains in whole stereo frames, flushes, and drops
//      NEW samples (counted) rather than wait when it is full;
//   4. under load: a writer thread standing in for the emulation thread and a reader standing in for
//      the stream's pump run flat out against each other. Every frame the reader copies is one whole
//      frame (never two mixed), the counters only rise, every sound batch arrives whole or is counted
//      as dropped, and the writer's slowest call is reported (it never waits on the reader).
//
// Build with the NDK's clang, statically, so the binary runs on plain Linux (WSL) as well:
//   NDK=$ANDROID_SDK/ndk/26.1.10909125/toolchains/llvm/prebuilt/windows-x86_64/bin
//   $NDK/x86_64-linux-android21-clang++ -std=c++17 -O2 -static -Wall -Wextra \
//       -I libretrodroid/src/main/cpp tools/stream/streamtap_harness.cpp \
//       libretrodroid/src/main/cpp/streamtap.cpp -o streamtap_harness
//   ./streamtap_harness            (exit code 0 and "ALL OK" when it passes)

#include <atomic>
#include <chrono>
#include <cmath>
#include <cstdint>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <thread>
#include <vector>

#include "streamtap.h"

using libretrodroid::StreamTap;

static int failures = 0;
#define CHECK(cond, ...)                                                       \
    do {                                                                       \
        if (!(cond)) {                                                         \
            failures++;                                                        \
            std::printf("FAIL %s:%d  %s  ", __FILE__, __LINE__, #cond);        \
            std::printf(__VA_ARGS__);                                          \
            std::printf("\n");                                                 \
        }                                                                      \
    } while (0)

// ----------------------------------------------------------------------------------------------
// 1 and 2: formats, counters, bad input

static uint8_t ref5(unsigned v) { return (uint8_t) std::lround(v * 255.0 / 31.0); }
static uint8_t ref6(unsigned v) { return (uint8_t) std::lround(v * 255.0 / 63.0); }

static void testFormats() {
    StreamTap& tap = StreamTap::getInstance();
    StreamTap::setEnabled(true);
    int w = 0, h = 0;

    // XRGB8888: little-endian 0x00RRGGBB, so the bytes in memory are B G R X. Pitch has 8 spare bytes.
    {
        const unsigned W = 5, H = 3, pitch = W * 4 + 8;
        std::vector<uint8_t> src(pitch * H, 0xEE);
        std::vector<uint8_t> expect;
        for (unsigned y = 0; y < H; y++)
            for (unsigned x = 0; x < W; x++) {
                uint8_t r = (uint8_t) (x * 40 + y), g = (uint8_t) (y * 70 + x), b = (uint8_t) (255 - x * 9 - y);
                uint8_t* p = &src[y * pitch + x * 4];
                p[0] = b; p[1] = g; p[2] = r; p[3] = 0x7F;
                expect.push_back(r); expect.push_back(g); expect.push_back(b);
            }
        tap.onFrame(src.data(), W, H, pitch, StreamTap::FORMAT_XRGB8888);
        std::vector<uint8_t> out(W * H * 3, 0);
        int64_t c = tap.copyFrame(out.data(), out.size(), 0, &w, &h);
        CHECK(c > 0 && w == (int) W && h == (int) H, "XRGB8888 frame c=%lld %dx%d", (long long) c, w, h);
        CHECK(out == expect, "XRGB8888 pixels");
    }

    // RGB565, with padding: all 65536 values in one frame, checked against the reference rounding.
    {
        const unsigned W = 256, H = 256, pitch = W * 2 + 6;
        std::vector<uint8_t> src(pitch * H, 0xAA);
        for (unsigned v = 0; v < 65536; v++) {
            unsigned y = v / W, x = v % W;
            src[y * pitch + x * 2] = (uint8_t) (v & 0xFF);
            src[y * pitch + x * 2 + 1] = (uint8_t) (v >> 8);
        }
        tap.onFrame(src.data(), W, H, pitch, StreamTap::FORMAT_RGB565);
        std::vector<uint8_t> out(W * H * 3);
        int64_t c = tap.copyFrame(out.data(), out.size(), 0, &w, &h);
        CHECK(c > 0 && w == 256 && h == 256, "RGB565 frame");
        size_t bad = 0;
        for (unsigned v = 0; v < 65536; v++) {
            const uint8_t* o = &out[v * 3];
            if (o[0] != ref5((v >> 11) & 31) || o[1] != ref6((v >> 5) & 63) || o[2] != ref5(v & 31)) bad++;
        }
        CHECK(bad == 0, "RGB565: %zu of 65536 values differ from the reference", bad);
    }

    // 0RGB1555: every value.
    {
        const unsigned W = 256, H = 128, pitch = W * 2;
        std::vector<uint8_t> src(pitch * H);
        for (unsigned v = 0; v < 32768; v++) {
            unsigned y = v / W, x = v % W;
            src[y * pitch + x * 2] = (uint8_t) (v & 0xFF);
            src[y * pitch + x * 2 + 1] = (uint8_t) (v >> 8);
        }
        tap.onFrame(src.data(), W, H, pitch, StreamTap::FORMAT_0RGB1555);
        std::vector<uint8_t> out(W * H * 3);
        int64_t c = tap.copyFrame(out.data(), out.size(), 0, &w, &h);
        CHECK(c > 0, "0RGB1555 frame");
        size_t bad = 0;
        for (unsigned v = 0; v < 32768; v++) {
            const uint8_t* o = &out[v * 3];
            if (o[0] != ref5((v >> 10) & 31) || o[1] != ref5((v >> 5) & 31) || o[2] != ref5(v & 31)) bad++;
        }
        CHECK(bad == 0, "0RGB1555: %zu of 32768 values differ from the reference", bad);
    }

    // Extremes: white and black must be exactly 255 and 0 in every format.
    {
        uint16_t white565 = 0xFFFF, black = 0;
        uint32_t whiteX = 0x00FFFFFF;
        uint8_t out[3];
        tap.onFrame(&white565, 1, 1, 2, StreamTap::FORMAT_RGB565);
        tap.copyFrame(out, 3, 0, &w, &h);
        CHECK(out[0] == 255 && out[1] == 255 && out[2] == 255, "white 565");
        tap.onFrame(&whiteX, 1, 1, 4, StreamTap::FORMAT_XRGB8888);
        tap.copyFrame(out, 3, 0, &w, &h);
        CHECK(out[0] == 255 && out[1] == 255 && out[2] == 255, "white 8888");
        uint16_t white1555 = 0x7FFF;
        tap.onFrame(&white1555, 1, 1, 2, StreamTap::FORMAT_0RGB1555);
        tap.copyFrame(out, 3, 0, &w, &h);
        CHECK(out[0] == 255 && out[1] == 255 && out[2] == 255, "white 1555");
        tap.onFrame(&black, 1, 1, 2, StreamTap::FORMAT_RGB565);
        tap.copyFrame(out, 3, 0, &w, &h);
        CHECK(out[0] == 0 && out[1] == 0 && out[2] == 0, "black");
    }
    StreamTap::setEnabled(false);
}

static void testCountersAndBadInput() {
    StreamTap& tap = StreamTap::getInstance();
    StreamTap::setEnabled(true);
    int w = 0, h = 0;
    uint16_t px[4 * 2] = {};
    uint8_t out[4 * 2 * 3];

    tap.onFrame(px, 4, 2, 8, StreamTap::FORMAT_RGB565);
    int64_t c1 = tap.copyFrame(out, sizeof out, 0, &w, &h);
    CHECK(c1 > 0, "a frame");
    CHECK(tap.copyFrame(out, sizeof out, c1, &w, &h) == 0, "nothing newer than what was read");
    CHECK(tap.copyFrame(out, sizeof out, c1 + 100, &w, &h) == 0, "nothing newer than a counter from the future");

    tap.onFrame(px, 4, 2, 8, StreamTap::FORMAT_RGB565);
    tap.onFrame(px, 4, 2, 8, StreamTap::FORMAT_RGB565);
    int64_t c2 = tap.copyFrame(out, sizeof out, c1, &w, &h);
    CHECK(c2 == c1 + 2, "two frames later the newest counter (%lld vs %lld)", (long long) c2, (long long) c1);

    // A buffer that is too small reports what is needed and consumes nothing.
    tap.onFrame(px, 4, 2, 8, StreamTap::FORMAT_RGB565);
    w = h = 0;
    int64_t small = tap.copyFrame(out, 5, c2, &w, &h);
    CHECK(small == -1 && w == 4 && h == 2, "too small: %lld %dx%d", (long long) small, w, h);
    CHECK(tap.copyFrame(out, sizeof out, c2, &w, &h) > c2, "the frame was still there for a big enough buffer");

    // Bad input is ignored: no pixels, no size, pitch under a row.
    int64_t before = tap.copyFrame(out, sizeof out, 0, &w, &h);
    tap.onFrame(nullptr, 4, 2, 8, StreamTap::FORMAT_RGB565);
    tap.onFrame(px, 0, 2, 8, StreamTap::FORMAT_RGB565);
    tap.onFrame(px, 4, 0, 8, StreamTap::FORMAT_RGB565);
    tap.onFrame(px, 4, 2, 7, StreamTap::FORMAT_RGB565);
    CHECK(tap.copyFrame(out, sizeof out, before, &w, &h) == 0, "bad frames leave no trace");
    std::vector<uint8_t> huge(64);
    tap.onFrame(huge.data(), 8192, 8192, 16384, StreamTap::FORMAT_RGB565);
    CHECK(tap.copyFrame(out, sizeof out, before, &w, &h) == 0, "an absurd size is refused");
    CHECK(tap.copyFrame(nullptr, 0, before, &w, &h) == 0, "a null destination with nothing new");

    // A size change is followed.
    std::vector<uint8_t> wide(16 * 3 * 2, 1);
    tap.onFrame(wide.data(), 16, 3, 32, StreamTap::FORMAT_RGB565);
    std::vector<uint8_t> big(16 * 3 * 3);
    int64_t cw = tap.copyFrame(big.data(), big.size(), before, &w, &h);
    CHECK(cw > 0 && w == 16 && h == 3, "resized frame %dx%d", w, h);
    StreamTap::setEnabled(false);
}

// ----------------------------------------------------------------------------------------------
// 3: sound

static void testSound() {
    StreamTap& tap = StreamTap::getInstance();
    StreamTap::setEnabled(true);          // flushes
    int rate = 0;
    std::vector<int16_t> buf(StreamTap::RING_SAMPLES);

    CHECK(tap.drainAudio(buf.data(), buf.size(), &rate) == 0, "empty after enabling");
    StreamTap::setAudioRate(48000);
    std::vector<int16_t> in(2000);
    for (size_t i = 0; i < in.size(); i++) in[i] = (int16_t) i;
    tap.onAudio(in.data(), in.size() / 2);
    size_t n = tap.drainAudio(buf.data(), 7, &rate);        // odd cap: must give whole frames
    CHECK(n == 6 && rate == 48000, "odd cap gives %zu samples at %d", n, rate);
    n += tap.drainAudio(buf.data() + n, 1000, &rate);
    n += tap.drainAudio(buf.data() + n, 5000, &rate);
    CHECK(n == in.size(), "all 2000 samples came out, got %zu", n);
    CHECK(std::memcmp(buf.data(), in.data(), n * 2) == 0, "in order");
    CHECK(tap.drainAudio(buf.data(), 100, &rate) == 0, "then empty");

    // Wrap the ring many times with batches of odd sizes; the sequence must never break.
    size_t sent = 0, got = 0;
    int16_t nextOut = 0;
    bool ordered = true;
    for (int round = 0; round < 3000; round++) {
        size_t frames = 1 + (round * 37) % 900;
        std::vector<int16_t> batch(frames * 2);
        for (auto& s : batch) s = (int16_t) (sent++);
        tap.onAudio(batch.data(), frames);
        size_t want = 1 + (round * 53) % 2500;
        size_t r = tap.drainAudio(buf.data(), want, &rate);
        for (size_t i = 0; i < r; i++) if (buf[i] != nextOut++) ordered = false;
        got += r;
    }
    got += [&] { size_t r = tap.drainAudio(buf.data(), buf.size(), &rate); for (size_t i = 0; i < r; i++) if (buf[i] != nextOut++) ordered = false; return r; }();
    CHECK(ordered && got == sent, "sequence across the wrap: got %zu of %zu, ordered=%d", got, sent, (int) ordered);

    // Full ring: new samples are dropped and counted, and the call returns at once.
    StreamTap::flushAudio();
    tap.drainAudio(buf.data(), buf.size(), &rate);
    std::vector<int16_t> chunk(4096, 5);
    uint64_t droppedBefore = tap.droppedSamples();
    auto t0 = std::chrono::steady_clock::now();
    for (int i = 0; i < 100; i++) tap.onAudio(chunk.data(), chunk.size() / 2);      // 409600 samples into a 131072 ring
    double ms = std::chrono::duration<double, std::milli>(std::chrono::steady_clock::now() - t0).count();
    uint64_t dropped = tap.droppedSamples() - droppedBefore;
    size_t held = tap.drainAudio(buf.data(), buf.size(), &rate);
    CHECK(held + dropped == 100 * chunk.size(), "kept %zu + dropped %llu != %zu", held, (unsigned long long) dropped, 100 * chunk.size());
    CHECK(held <= StreamTap::RING_SAMPLES && held > 0, "the ring held %zu", held);
    CHECK(ms < 50.0, "a full ring must not slow the writer (took %.2f ms)", ms);

    // A flush discards what is buffered.
    tap.onAudio(chunk.data(), 100);
    StreamTap::flushAudio();
    CHECK(tap.drainAudio(buf.data(), buf.size(), &rate) == 0, "flush empties the ring");
    tap.onAudio(chunk.data(), 100);
    CHECK(tap.drainAudio(buf.data(), buf.size(), &rate) == 200, "and sound after it is kept");

    // Bad input.
    tap.onAudio(nullptr, 10);
    tap.onAudio(chunk.data(), 0);
    CHECK(tap.drainAudio(buf.data(), buf.size(), &rate) == 0, "bad audio leaves nothing");
    CHECK(tap.drainAudio(nullptr, 100, &rate) == 0, "no destination, no samples");
    StreamTap::setEnabled(false);
}

// ----------------------------------------------------------------------------------------------
// 4: two threads, flat out

// A frame whose every byte is a function of its stamp, so a torn frame (rows from two frames) shows.
static inline uint8_t pixelByte(uint32_t stamp, unsigned x, unsigned y, unsigned c) {
    return (uint8_t) (stamp * 31u + x * 7u + y * 13u + c * 101u);
}

static void testUnderLoad() {
    StreamTap& tap = StreamTap::getInstance();
    StreamTap::setEnabled(true);
    StreamTap::setAudioRate(32768);

    const unsigned W = 96, H = 64, PITCH = W * 4 + 16;
    const uint32_t FRAMES = 60000;
    const uint64_t droppedAtStart = tap.droppedSamples();
    {
        // setEnabled(true) asked for a flush; the first drain carries it out. Let that happen before
        // the writer starts, so the sound accounting below is exact and not short by that flush.
        std::vector<int16_t> scratch(256);
        int r0 = 0;
        tap.drainAudio(scratch.data(), scratch.size(), &r0);
    }
    std::atomic<bool> done { false };
    std::atomic<double> worstWriteMs { 0.0 };
    uint64_t soundSent = 0;         // stereo FRAMES sent; frame f carries f in its two samples (left low, right high)

    std::thread writer([&] {
        std::vector<uint8_t> frame(PITCH * H);
        std::vector<int16_t> batch(2 * 400);
        double worst = 0;
        for (uint32_t stamp = 1; stamp <= FRAMES; stamp++) {
            for (unsigned y = 0; y < H; y++) {
                uint8_t* row = &frame[y * PITCH];
                for (unsigned x = 0; x < W; x++) {
                    // XRGB8888 in memory: B G R X. The stamp rides in the colours.
                    row[x * 4 + 0] = pixelByte(stamp, x, y, 2);
                    row[x * 4 + 1] = pixelByte(stamp, x, y, 1);
                    row[x * 4 + 2] = pixelByte(stamp, x, y, 0);
                    row[x * 4 + 3] = 0;
                }
            }
            auto t0 = std::chrono::steady_clock::now();
            tap.onFrame(frame.data(), W, H, PITCH, StreamTap::FORMAT_XRGB8888);
            // sound: batches of 1..400 stereo frames; every sample is its running index.
            size_t frames = 1 + (stamp * 29u) % 400;
            for (size_t i = 0; i < frames; i++) {
                uint32_t f = (uint32_t) (soundSent + i);
                batch[i * 2] = (int16_t) (f & 0xFFFFu);
                batch[i * 2 + 1] = (int16_t) (f >> 16);
            }
            tap.onAudio(batch.data(), frames);
            soundSent += frames;
            double ms = std::chrono::duration<double, std::milli>(std::chrono::steady_clock::now() - t0).count();
            if (ms > worst) worst = ms;
        }
        worstWriteMs = worst;
        done = true;
    });

    uint64_t framesSeen = 0, torn = 0, backwards = 0, mismatched = 0, soundGot = 0, soundBadOrder = 0, gaps = 0, skipped = 0;
    int64_t last = 0;
    std::vector<uint8_t> rgb(W * H * 3);
    std::vector<int16_t> snd(4096);
    {
        // The tap still holds the last frame of the tests above (a different size). Start after it.
        int w0 = 0, h0 = 0;
        int64_t held = tap.copyFrame(rgb.data(), rgb.size(), 0, &w0, &h0);
        if (held > 0) last = held;
    }
    uint32_t expect = 0;            // the stereo frame index the next frame should carry when nothing was dropped
    std::thread reader([&] {
        int w, h, rate;
        while (true) {
            bool finishing = done.load();
            int64_t c = tap.copyFrame(rgb.data(), rgb.size(), last, &w, &h);
            if (c > 0) {
                framesSeen++;
                if (c <= last) backwards++;
                last = c;
                if (w != (int) W || h != (int) H) mismatched++;
                // Every pixel must be one function of ONE stamp: recover it from pixel (0,0) and check all.
                uint32_t stamp = 0;
                for (uint32_t s = 0; s < 256; s++) if (pixelByte(s, 0, 0, 0) == rgb[0]) { stamp = s; break; }
                // pixel (0,0) only fixes the stamp mod 256 (31 is odd, so it is a bijection); use frame counter parity to be sure
                bool ok = true;
                for (unsigned y = 0; y < H && ok; y++)
                    for (unsigned x = 0; x < W; x++) {
                        const uint8_t* p = &rgb[(y * W + x) * 3];
                        if (p[0] != pixelByte(stamp, x, y, 0) || p[1] != pixelByte(stamp, x, y, 1) || p[2] != pixelByte(stamp, x, y, 2)) { ok = false; break; }
                    }
                if (!ok) torn++;
            }
            size_t n = tap.drainAudio(snd.data(), snd.size(), &rate);
            for (size_t i = 0; i + 1 < n; i += 2) {
                uint32_t f = (uint32_t) (uint16_t) snd[i] | ((uint32_t) (uint16_t) snd[i + 1] << 16);
                if (f != expect) {
                    // A gap is legal only where the ring overflowed and dropped whole batches; going back is never legal.
                    if (f < expect) soundBadOrder++;
                    else { gaps++; skipped += f - expect; }
                    expect = f;
                }
                expect++;
            }
            soundGot += n;
            if (finishing && n == 0 && c <= 0) break;
        }
    });

    writer.join();
    reader.join();
    uint64_t dropped = tap.droppedSamples() - droppedAtStart;
    std::printf("under load: %llu frames of %u written, %llu seen (newest-wins skips the rest), "
                "sound %llu of %llu samples arrived, %llu dropped by the ring, %llu gap(s), writer's slowest call %.3f ms\n",
                (unsigned long long) framesSeen, FRAMES, (unsigned long long) framesSeen, (unsigned long long) soundGot,
                (unsigned long long) soundSent * 2, (unsigned long long) dropped, (unsigned long long) gaps, worstWriteMs.load());
    CHECK(torn == 0, "%llu torn frames", (unsigned long long) torn);
    CHECK(backwards == 0, "%llu counters went backwards", (unsigned long long) backwards);
    CHECK(mismatched == 0, "%llu frames of the wrong size", (unsigned long long) mismatched);
    CHECK(framesSeen > 100, "the reader saw only %llu frames", (unsigned long long) framesSeen);
    CHECK(soundBadOrder == 0, "%llu sound samples arrived out of order", (unsigned long long) soundBadOrder);
    CHECK(soundGot + dropped == soundSent * 2, "sound accounting: %llu samples arrived + %llu dropped != %llu sent",
          (unsigned long long) soundGot, (unsigned long long) dropped, (unsigned long long) soundSent * 2);
    CHECK(skipped * 2 <= dropped, "%llu frames are missing from the middle of the sound but only %llu samples were counted as dropped",
          (unsigned long long) skipped, (unsigned long long) dropped);
    CHECK(gaps == 0 || dropped > 0, "a gap in the sound with nothing dropped");
    // The writer never waits on the reader. 250 ms is generous for a scheduler hiccup on a shared box.
    CHECK(worstWriteMs.load() < 250.0, "the writer's slowest call took %.3f ms", worstWriteMs.load());
    StreamTap::setEnabled(false);
}

int main() {
    testFormats();
    testCountersAndBadInput();
    testSound();
    testUnderLoad();
    if (failures == 0) std::printf("ALL OK\n");
    else std::printf("%d FAILURE(S)\n", failures);
    return failures == 0 ? 0 : 1;
}
