/*
 * LOCAL MODIFICATION (KaizoCore): a resampler that keeps its place.
 *
 * Audio::onAudioReady used to round each callback's input to a whole number of
 * frames (130, then 131, at mGBA's 65238 -> 48000 Hz with 96-frame callbacks)
 * and stretch exactly that onto the callback's output. The stretch changed on
 * every callback, so the playback rate wobbled hundreds of times a second and
 * the high notes smeared. Measured with tools/audio/resampler_harness.cpp on
 * the emulator: 41 dB at 440 Hz falling to 10 dB at 15 kHz. A cubic curve fed
 * the same rounded blocks scored within 0.6 dB of linear (checked at 32768 Hz),
 * which is how the rounding, not the curve, was found to be the fault. This
 * class: 87 dB falling to 26 dB.
 * Heard at 1x only, because turbo is muted (Blake, 2026-09-27: "the music on
 * 1x speed was bad quality").
 *
 * This one reads input at the exact, fractional rate the caller asks for,
 * carries its position and the frames it still needs across callbacks, and
 * interpolates with a 4-point cubic (Catmull-Rom). A callback boundary is just
 * another sample.
 */

#ifndef LIBRETRODROID_CUBICRESAMPLER_H
#define LIBRETRODROID_CUBICRESAMPLER_H

#include <cstdint>
#include <functional>
#include <vector>

namespace libretrodroid {

class CubicResampler {
public:
    /**
     * Writes [sinkFrames] interleaved stereo frames to [sink], stepping [step]
     * input frames per output frame. [pull] fills its buffer with up to the
     * number of interleaved frames asked for and returns how many it wrote; a
     * short read (an underrun) is padded by repeating the last frame.
     */
    void render(const std::function<int32_t(int16_t *, int32_t)> &pull,
                int16_t *sink, int32_t sinkFrames, double step);

private:
    // Input frames not yet passed, interleaved L R. Frame 0 is one frame
    // before [position], kept for the cubic's left neighbour.
    std::vector<float> pending = std::vector<float>(2 * 2, 0.0f);
    // Where the next output frame is read, in frames of [pending]. Always >= 1.
    double position = 1.0;
    std::vector<int16_t> scratch;
};

} //namespace libretrodroid

#endif //LIBRETRODROID_CUBICRESAMPLER_H
