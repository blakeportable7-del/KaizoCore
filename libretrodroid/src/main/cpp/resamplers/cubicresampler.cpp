/*
 * LOCAL MODIFICATION (KaizoCore). See cubicresampler.h for why.
 */

#include <algorithm>
#include <cmath>
#include "cubicresampler.h"

namespace libretrodroid {

namespace {

// Catmull-Rom through p1..p2, t in [0, 1).
inline float cubic(float p0, float p1, float p2, float p3, float t) {
    return p1 + 0.5f * t * (p2 - p0 + t * (2.0f * p0 - 5.0f * p1 + 4.0f * p2 - p3 + t * (3.0f * (p1 - p2) + p3 - p0)));
}

inline int16_t clamp16(float v) {
    return static_cast<int16_t>(std::max(-32768.0f, std::min(32767.0f, std::round(v))));
}

} // namespace

void CubicResampler::render(const std::function<int32_t(int16_t *, int32_t)> &pull,
                            int16_t *sink, int32_t sinkFrames, double step) {
    if (sinkFrames <= 0) return;
    if (!(step > 0.0)) step = 1.0;

    // The last output frame reads at position + (sinkFrames - 1) * step and
    // needs two frames after its floor. Pull whatever is missing, in one read.
    const double lastPosition = position + (sinkFrames - 1) * step;
    const auto framesNeeded = static_cast<int32_t>(std::floor(lastPosition)) + 3;
    const auto have = static_cast<int32_t>(pending.size() / 2);
    if (framesNeeded > have) {
        const int32_t missing = framesNeeded - have;
        scratch.resize(static_cast<size_t>(missing) * 2);
        const int32_t got = std::max(0, std::min(missing, pull(scratch.data(), missing)));
        pending.reserve(static_cast<size_t>(framesNeeded) * 2);
        for (int32_t i = 0; i < got * 2; i++) pending.push_back(static_cast<float>(scratch[i]));
        // Underrun: hold the last frame instead of dropping to silence.
        while (static_cast<int32_t>(pending.size() / 2) < framesNeeded) {
            const float l = pending[pending.size() - 2], r = pending[pending.size() - 1];
            pending.push_back(l);
            pending.push_back(r);
        }
    }

    for (int32_t k = 0; k < sinkFrames; k++) {
        const double at = position + k * step;
        const auto i = static_cast<int32_t>(at);
        const float t = static_cast<float>(at - i);
        for (int channel = 0; channel < 2; channel++) {
            *sink++ = clamp16(cubic(pending[(i - 1) * 2 + channel], pending[i * 2 + channel],
                                    pending[(i + 1) * 2 + channel], pending[(i + 2) * 2 + channel], t));
        }
    }

    // Drop the frames the next call no longer needs, keeping one before the
    // next read position for the cubic's left neighbour.
    position += sinkFrames * step;
    const auto frames = static_cast<int32_t>(pending.size() / 2);
    const int32_t drop = std::min(static_cast<int32_t>(std::floor(position)) - 1, frames);
    if (drop > 0) {
        pending.erase(pending.begin(), pending.begin() + static_cast<size_t>(drop) * 2);
        position -= drop;
    }
}

} //namespace libretrodroid
