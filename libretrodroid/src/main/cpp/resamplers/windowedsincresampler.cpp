/*
 * LOCAL MODIFICATION (KaizoCore). See windowedsincresampler.h for why.
 */

#include <algorithm>
#include <cmath>
#include "windowedsincresampler.h"

namespace libretrodroid {

namespace {

// The modified Bessel function of the first kind, order 0, for the Kaiser window.
double besselI0(double x) {
    double sum = 1.0, term = 1.0;
    const double q = x * x / 4.0;
    for (int k = 1; k < 64; k++) {
        term *= q / (static_cast<double>(k) * k);
        sum += term;
        if (term < sum * 1e-12) break;
    }
    return sum;
}

inline int16_t clamp16(float v) {
    return static_cast<int16_t>(std::max(-32768.0f, std::min(32767.0f, std::round(v))));
}

} // namespace

void WindowedSincResampler::design(double step) {
    // Cutoff in cycles per input frame: the lower of the two Nyquists, times ALPHA.
    const double fc = 0.5 * ALPHA / std::max(1.0, step);
    const double width = ZEROS / (2.0 * fc);   // half the kernel, in input frames
    const auto newHalf = static_cast<int32_t>(std::ceil(width)) + 1;
    const int32_t newTaps = newHalf * 2;
    const double norm = besselI0(BETA);

    table.assign(static_cast<size_t>(PHASES + 1) * newTaps, 0.0f);
    for (int32_t p = 0; p <= PHASES; p++) {
        float *row = &table[static_cast<size_t>(p) * newTaps];
        double sum = 0.0;
        for (int32_t k = 0; k < newTaps; k++) {
            // Tap k reads frame floor(position) - half + 1 + k, at distance d before the read position.
            const double d = static_cast<double>(p) / PHASES + newHalf - 1 - k;
            const double r = d / width;
            if (r <= -1.0 || r >= 1.0) continue;
            const double x = 2.0 * fc * d;
            const double sinc = std::abs(x) < 1e-12 ? 1.0 : std::sin(M_PI * x) / (M_PI * x);
            const double w = 2.0 * fc * sinc * besselI0(BETA * std::sqrt(1.0 - r * r)) / norm;
            row[k] = static_cast<float>(w);
            sum += w;
        }
        for (int32_t k = 0; k < newTaps; k++) row[k] = static_cast<float>(row[k] / sum);
    }

    // A longer kernel reads further back: give the read position the frames it now needs before it.
    const int32_t needLeft = newHalf - 1;
    const auto floorPos = static_cast<int32_t>(std::floor(position));
    if (floorPos < needLeft) {
        const int32_t add = needLeft - floorPos;
        pending.insert(pending.begin(), static_cast<size_t>(add) * 2, 0.0f);
        position += add;
    }
    taps = newTaps;
    half = newHalf;
    designedFor = step;
}

void WindowedSincResampler::render(const std::function<int32_t(int16_t *, int32_t)> &pull,
                                   int16_t *sink, int32_t sinkFrames, double step) {
    if (sinkFrames <= 0) return;
    if (!(step > 0.0)) step = 1.0;

    // Designed once for the core's rate; the speed controller's small corrections (a few per cent) keep the kernel.
    const double want = std::min(std::max(step, 1.0), MAX_DESIGN_STEP);
    if (table.empty() || std::abs(want - designedFor) > 0.05 * designedFor) design(want);

    // The last output frame reads up to floor(lastPosition) + half. Pull whatever is missing, in one read.
    const double lastPosition = position + (sinkFrames - 1) * step;
    const auto framesNeeded = static_cast<int32_t>(std::floor(lastPosition)) + half + 1;
    const auto have = static_cast<int32_t>(pending.size() / 2);
    if (framesNeeded > have) {
        const int32_t missing = framesNeeded - have;
        scratch.resize(static_cast<size_t>(missing) * 2);
        const int32_t got = std::max(0, std::min(missing, pull(scratch.data(), missing)));
        pending.reserve(static_cast<size_t>(framesNeeded) * 2);
        for (int32_t i = 0; i < got * 2; i++) pending.push_back(static_cast<float>(scratch[i]));
        // Underrun: hold the last frame; with no frame at all, silence.
        if (pending.size() < 2) pending.assign(2, 0.0f);
        while (static_cast<int32_t>(pending.size() / 2) < framesNeeded) {
            const float l = pending[pending.size() - 2], r = pending[pending.size() - 1];
            pending.push_back(l);
            pending.push_back(r);
        }
    }

    for (int32_t k = 0; k < sinkFrames; k++) {
        const double at = position + k * step;
        const auto i = static_cast<int32_t>(at);
        const double ph = (at - i) * PHASES;
        const auto p0 = std::min(static_cast<int32_t>(ph), PHASES - 1);
        const auto a = static_cast<float>(ph - p0);
        const float *r0 = &table[static_cast<size_t>(p0) * taps];
        const float *r1 = r0 + taps;
        const float *x = &pending[static_cast<size_t>(i - half + 1) * 2];
        float left = 0.0f, right = 0.0f;
        for (int32_t j = 0; j < taps; j++) {
            const float w = r0[j] + a * (r1[j] - r0[j]);
            left += w * x[j * 2];
            right += w * x[j * 2 + 1];
        }
        *sink++ = clamp16(left);
        *sink++ = clamp16(right);
    }

    // Drop the frames the next call no longer needs, keeping half - 1 before the next read position for the kernel's
    // left side. Never all of them: an emptied buffer is what an underrun hold would then read outside.
    position += sinkFrames * step;
    const auto frames = static_cast<int32_t>(pending.size() / 2);
    const int32_t drop = std::min(static_cast<int32_t>(std::floor(position)) - (half - 1), frames - 1);
    if (drop > 0) {
        pending.erase(pending.begin(), pending.begin() + static_cast<size_t>(drop) * 2);
        position -= drop;
    }
}

} //namespace libretrodroid
