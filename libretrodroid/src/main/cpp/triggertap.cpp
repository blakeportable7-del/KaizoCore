/*
 *     KaizoCore patch (2026-10-04) to LibretroDroid. GPL-3.0, as the rest of this module. See triggertap.h.
 */
#include "triggertap.h"

#include <algorithm>
#include <cstring>

namespace libretrodroid {

TriggerTap& TriggerTap::getInstance() {
    static TriggerTap instance;
    return instance;
}

static bool inRam(uint64_t address, uint64_t length) {
    return length > 0 && address >= TriggerTap::RAM_START && length <= TriggerTap::RAM_SIZE &&
           address - TriggerTap::RAM_START <= TriggerTap::RAM_SIZE - length;
}

uint32_t TriggerTap::arm(uint64_t watch, std::vector<uint32_t> values, const std::vector<std::pair<uint64_t, uint32_t>>& copies) {
    armed = false;
    count = 0;
    dropped = 0;
    frame = 0;
    haveLast = false;
    if (values.empty() || values.size() > MAX_TARGETS || copies.size() > MAX_RANGES || !inRam(watch, 4)) return 0;
    size_t total = 0;
    size_t needed = (size_t) (watch - RAM_START) + 4;
    std::vector<std::pair<uint32_t, uint32_t>> offsets;
    offsets.reserve(copies.size());
    for (auto& r : copies) {
        if (r.second == 0 || r.second > MAX_RANGE_LENGTH || !inRam(r.first, r.second)) return 0;
        total += r.second;
        const auto offset = (uint32_t) (r.first - RAM_START);
        needed = std::max(needed, (size_t) offset + r.second);
        offsets.emplace_back(offset, r.second);
    }
    if (total > MAX_CONTEXT) return 0;
    std::sort(values.begin(), values.end());
    watchOffset = (uint32_t) (watch - RAM_START);
    targets = std::move(values);
    ranges = std::move(offsets);
    contextLength = total;
    ramNeeded = needed;
    token = token == UINT32_MAX ? 1 : token + 1;
    armed = true;
    return token;
}

void TriggerTap::disarm(uint32_t which) {
    if (which != 0 && which != token) return;   // a newer arming stands
    armed = false;
    count = 0;
    dropped = 0;
    haveLast = false;
}

void TriggerTap::doFrame(const unsigned char* ram, size_t ramSize) {
    if (!armed) return;
    frame++;
    // The core's buffer as it is now: every offset was checked against the GBA's RAM when armed, and must fit this.
    if (ram == nullptr || ramSize < ramNeeded) return;
    const unsigned char* w = ram + watchOffset;
    const uint32_t value = (uint32_t) w[0] | ((uint32_t) w[1] << 8) | ((uint32_t) w[2] << 16) | ((uint32_t) w[3] << 24);
    // Once per arrival: the pointer stands on a message's address for many frames, and each stay is one reveal.
    if (haveLast && value == last) return;
    haveLast = true;
    last = value;
    if (!std::binary_search(targets.begin(), targets.end(), value)) return;
    // Rare from here on: the pointer arrived on an ability script. The copy goes on the stack (no allocation).
    Hit hit;
    hit.frame = frame;
    hit.value = value;
    size_t at = 0;
    for (auto& r : ranges) {
        std::memcpy(hit.context.data() + at, ram + r.first, r.second);
        at += r.second;
    }
    // The same message on the same battlers again changes nothing the tracker would find; keep the room.
    for (size_t i = 0; i < count; i++) {
        if (ring[i].value == value && std::memcmp(ring[i].context.data(), hit.context.data(), contextLength) == 0) return;
    }
    // Full (64 different catches since the tracker last drained): newer ones are counted, never written past the ring.
    if (count >= CAPACITY) {
        dropped++;
        return;
    }
    ring[count] = hit;
    count++;
}

static void putU32(std::vector<unsigned char>& out, uint32_t v) {
    out.push_back((unsigned char) (v & 0xFF));
    out.push_back((unsigned char) ((v >> 8) & 0xFF));
    out.push_back((unsigned char) ((v >> 16) & 0xFF));
    out.push_back((unsigned char) ((v >> 24) & 0xFF));
}

std::vector<unsigned char> TriggerTap::drain() {
    std::vector<unsigned char> out;
    if (!armed) return out;
    out.reserve(12 + count * (8 + contextLength));
    putU32(out, (uint32_t) count);
    putU32(out, (uint32_t) contextLength);
    putU32(out, dropped);
    for (size_t i = 0; i < count; i++) {
        putU32(out, ring[i].frame);
        putU32(out, ring[i].value);
        out.insert(out.end(), ring[i].context.begin(), ring[i].context.begin() + (long) contextLength);
    }
    count = 0;
    dropped = 0;
    return out;
}

}
