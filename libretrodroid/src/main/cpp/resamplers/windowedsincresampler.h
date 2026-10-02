/*
 * LOCAL MODIFICATION (KaizoCore, 2026-10-01): a resampler that filters what the phone cannot play.
 *
 * Blake, 2026-10-01: "the audio from the app is a bit crackling ... it sounds like it has some static to it".
 * mGBA sends 65,536 Hz and the phone plays 48,000. CubicResampler never filtered before decimating, so everything the
 * core makes between 24 and 32.8 kHz folded back into the audible band, and on the GBA that is a lot: in LeafGreen's
 * intro, 17 dB below the whole signal (tools/audio/pcm_dump.cpp, the core's own output captured on the emulator).
 * Against a long band-limited resample of that music, CubicResampler's error was 20 dB below the music at 12 to
 * 20 kHz and 25 dB below it under 12 kHz. That is the static.
 *
 * This one interpolates with a Kaiser-windowed sinc whose cutoff sits at 92% of the lower Nyquist of the two rates,
 * so what the phone cannot play is removed, not folded. The kernel is a table of 512 phases, linearly interpolated
 * between neighbours: 38 taps at 65,536 to 48,000, 30 when a core is upsampled (melonDS, Gambatte). On the same music
 * its error is 102 dB below it under 12 kHz and 46 dB below it at 12 to 20 kHz. Like CubicResampler it reads input at
 * the exact fractional rate asked for and keeps its place across callbacks; tools/audio/README.md has the numbers.
 */

#ifndef LIBRETRODROID_WINDOWEDSINCRESAMPLER_H
#define LIBRETRODROID_WINDOWEDSINCRESAMPLER_H

#include <cstdint>
#include <functional>
#include <vector>

namespace libretrodroid {

class WindowedSincResampler {
public:
    /**
     * Writes [sinkFrames] interleaved stereo frames to [sink], stepping [step] input frames per output frame. [pull]
     * fills its buffer with up to the number of interleaved frames asked for and returns how many it wrote; a short
     * read (an underrun) is padded by repeating the last frame.
     */
    void render(const std::function<int32_t(int16_t *, int32_t)> &pull,
                int16_t *sink, int32_t sinkFrames, double step);

private:
    void design(double step);

    static constexpr int PHASES = 512;
    // Zero crossings on each side, the cutoff as a share of the lower Nyquist, the Kaiser window's shape.
    static constexpr double ZEROS = 12.0;
    static constexpr double ALPHA = 0.92;
    static constexpr double BETA = 9.0;
    // Sound plays at 1x only (PlayScreen.applyAudio), where the step is about 1.37 at most; a turbo step is silent
    // and is not worth a longer kernel.
    static constexpr double MAX_DESIGN_STEP = 2.0;

    std::vector<float> table;  // PHASES + 1 rows of [taps] weights, each row summing to 1
    int32_t taps = 0;
    int32_t half = 0;          // the output at position p reads frames floor(p) - half + 1 .. floor(p) + half
    double designedFor = 0.0;

    std::vector<float> pending; // input frames not yet passed, interleaved L R
    double position = 0.0;      // where the next output frame is read, in frames of [pending]
    std::vector<int16_t> scratch;
};

} //namespace libretrodroid

#endif //LIBRETRODROID_WINDOWEDSINCRESAMPLER_H
