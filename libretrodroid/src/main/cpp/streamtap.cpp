/*
 * KaizoCore patch (2026-09-29): the stream kit's taps. See streamtap.h.
 */

#include "streamtap.h"

#include <algorithm>
#include <cstring>
#include <new>

namespace libretrodroid {

static_assert((StreamTap::RING_SAMPLES & (StreamTap::RING_SAMPLES - 1)) == 0,
              "the sound ring's size must be a power of two: its indices wrap on it");

namespace {
// The largest frame the tap will copy: 2048 x 2048 in XRGB8888. Anything past
// it is a core (or a caller) gone wrong, and a slot that size would be a
// megabytes-long allocation on the emulation thread.
constexpr size_t MAX_FRAME_BYTES = 16u << 20;
}

std::atomic<bool> StreamTap::captureOn { false };
std::atomic<bool> StreamTap::flushRequested { false };
std::atomic<int> StreamTap::audioRateHz { 32768 };

StreamTap& StreamTap::getInstance() {
    static StreamTap instance;
    return instance;
}

StreamTap::StreamTap() : ring(new int16_t[RING_SAMPLES]) {
    // The same rounding OpenGL applies when it turns a 5 or 6 bit channel into
    // the 8 bit one on screen, so the stream's colours are the phone's colours.
    for (int i = 0; i < 32; i++) lut5[i] = (uint8_t) ((i * 255 + 15) / 31);
    for (int i = 0; i < 64; i++) lut6[i] = (uint8_t) ((i * 255 + 31) / 63);
}

void StreamTap::setEnabled(bool on) {
    // Flush first, then open the gate: the first drain after this never sees
    // sound a previous viewer left behind.
    if (on) flushAudio();
    captureOn.store(on, std::memory_order_release);
}

void StreamTap::onFrame(const void* data, unsigned width, unsigned height, size_t pitch, int pixelFormat) {
    if (data == nullptr || width == 0 || height == 0) return;

    const size_t bytesPerPixel = pixelFormat == FORMAT_XRGB8888 ? 4 : 2;
    const size_t rowBytes = (size_t) width * bytesPerPixel;
    if (pitch < rowBytes) return;                       // not a picture we can read
    const size_t need = rowBytes * height;
    if (need > MAX_FRAME_BYTES) return;

    Slot& slot = slots[back];
    if (slot.capacity < need) {
        // Grows only when a frame is bigger than any this slot has held (the
        // first frame, or a DS layout change), never in steady state.
        slot.data.reset(new (std::nothrow) uint8_t[need]);
        slot.capacity = slot.data ? need : 0;
        if (!slot.data) return;
    }

    const auto* src = static_cast<const uint8_t*>(data);
    uint8_t* dst = slot.data.get();
    if (pitch == rowBytes) {
        std::memcpy(dst, src, need);
    } else {
        for (unsigned y = 0; y < height; y++) {
            std::memcpy(dst + (size_t) y * rowBytes, src + (size_t) y * pitch, rowBytes);
        }
    }
    slot.width = width;
    slot.height = height;
    slot.format = pixelFormat;
    slot.counter = nextCounter++;

    // Publish: hand the finished slot over and take back whichever one the
    // reader did not want. One exchange, no lock, no waiting.
    back = middle.exchange(back | FRESH, std::memory_order_acq_rel) & INDEX;
}

void StreamTap::onAudio(const int16_t* samples, size_t frames) {
    if (samples == nullptr || frames == 0) return;

    const size_t n = frames * 2;
    if (n > RING_SAMPLES / 2) return;                   // a batch this large is not sound from a core

    const uint32_t h = head.load(std::memory_order_relaxed);
    const uint32_t t = tail.load(std::memory_order_acquire);
    if (n > RING_SAMPLES - (size_t) (h - t)) {
        // The reader is a second behind. Dropping the new samples is the only
        // move that cannot make the emulation thread wait.
        dropped.fetch_add(n, std::memory_order_relaxed);
        return;
    }

    const size_t pos = h & (RING_SAMPLES - 1);
    const size_t first = std::min(n, RING_SAMPLES - pos);
    std::memcpy(ring.get() + pos, samples, first * sizeof(int16_t));
    if (first < n) std::memcpy(ring.get(), samples + first, (n - first) * sizeof(int16_t));
    head.store(h + (uint32_t) n, std::memory_order_release);
}

int64_t StreamTap::copyFrame(uint8_t* dst, size_t capacity, int64_t afterCounter, int* width, int* height) {
    std::lock_guard<std::mutex> guard(readerLock);

    // Take the newest published frame, if there is one, in exchange for the
    // slot this side was holding. The writer never touches `front`.
    if (middle.load(std::memory_order_acquire) & FRESH) {
        front = middle.exchange(front, std::memory_order_acq_rel) & INDEX;
    }

    const Slot& slot = slots[front];
    if (slot.counter == 0 || slot.counter <= afterCounter) return 0;

    const size_t need = (size_t) slot.width * slot.height * 3;
    if (width != nullptr) *width = (int) slot.width;
    if (height != nullptr) *height = (int) slot.height;
    if (dst == nullptr || capacity < need) return -1;

    const uint8_t* src = slot.data.get();
    const size_t pixels = (size_t) slot.width * slot.height;
    if (slot.format == FORMAT_XRGB8888) {
        // Little-endian 0x00RRGGBB: the bytes in memory run B, G, R, unused.
        for (size_t i = 0; i < pixels; i++, src += 4, dst += 3) {
            dst[0] = src[2];
            dst[1] = src[1];
            dst[2] = src[0];
        }
    } else if (slot.format == FORMAT_0RGB1555) {
        for (size_t i = 0; i < pixels; i++, src += 2, dst += 3) {
            const unsigned v = (unsigned) src[0] | ((unsigned) src[1] << 8);
            dst[0] = lut5[(v >> 10) & 31];
            dst[1] = lut5[(v >> 5) & 31];
            dst[2] = lut5[v & 31];
        }
    } else {
        // RGB565, which is also what the renderers assume for anything else.
        for (size_t i = 0; i < pixels; i++, src += 2, dst += 3) {
            const unsigned v = (unsigned) src[0] | ((unsigned) src[1] << 8);
            dst[0] = lut5[(v >> 11) & 31];
            dst[1] = lut6[(v >> 5) & 63];
            dst[2] = lut5[v & 31];
        }
    }
    return slot.counter;
}

size_t StreamTap::drainAudio(int16_t* dst, size_t maxSamples, int* rateHz) {
    std::lock_guard<std::mutex> guard(readerLock);

    if (rateHz != nullptr) *rateHz = audioRateHz.load(std::memory_order_relaxed);

    const uint32_t h = head.load(std::memory_order_acquire);
    uint32_t t = tail.load(std::memory_order_relaxed);
    if (flushRequested.exchange(false, std::memory_order_acq_rel)) t = h;

    size_t n = std::min<size_t>((size_t) (h - t), maxSamples) & ~(size_t) 1;   // whole stereo frames
    if (dst == nullptr) n = 0;
    if (n > 0) {
        const size_t pos = t & (RING_SAMPLES - 1);
        const size_t first = std::min(n, RING_SAMPLES - pos);
        std::memcpy(dst, ring.get() + pos, first * sizeof(int16_t));
        if (first < n) std::memcpy(dst + first, ring.get(), (n - first) * sizeof(int16_t));
    }
    tail.store(t + (uint32_t) n, std::memory_order_release);
    return n;
}

} // namespace libretrodroid
