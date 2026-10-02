// Feeds sound through the audio path the way Audio::onAudioReady drives it: fixed-size output callbacks, the core's
// rate in, 48,000 Hz out.
//   old:   round each callback's input to whole frames, stretch onto the callback (LinearResampler)
//   cubic: CubicResampler::render at the exact fractional rate (2026-09-27)
//   sinc:  WindowedSincResampler::render, which also filters what 48 kHz cannot hold (2026-10-01, Audio's now)
// With no argument: pure tones, scored by least-squares fitting the known tone, then tones above 24 kHz, which must
// come out as nothing (whatever does come out is an alias folded into the audible band).
// With a file (a core's raw output, from pcm_dump.cpp): writes cubic.raw and sinc.raw beside it for comparison.
#include <cmath>
#include <cstdio>
#include <cstdlib>
#include <string>
#include <vector>
#include "linearresampler.h"
#include "cubicresampler.h"
#include "windowedsincresampler.h"
using namespace libretrodroid;

static std::vector<int16_t> tone(double freq, double rate, int seconds) {
    std::vector<int16_t> in(size_t(rate * seconds + 4000) * 2);
    for (size_t i = 0; i < in.size() / 2; i++) {
        int16_t v = int16_t(std::lround(12000.0 * std::sin(2 * M_PI * freq * i / rate)));
        in[i * 2] = v; in[i * 2 + 1] = v;
    }
    return in;
}

static double score(const std::vector<int16_t> &out, double freq, double outRate) {
    size_t start = 4000, N = out.size() / 2 - start;
    double A[3][4] = {};
    for (size_t k = 0; k < N; k++) {
        double t = 2 * M_PI * freq * (k + start) / outRate, b[3] = {std::sin(t), std::cos(t), 1.0}, y = out[(k + start) * 2];
        for (int i = 0; i < 3; i++) { for (int j = 0; j < 3; j++) A[i][j] += b[i] * b[j]; A[i][3] += b[i] * y; }
    }
    for (int i = 0; i < 3; i++) for (int j = i + 1; j < 3; j++) { double f = A[j][i] / A[i][i]; for (int k = i; k < 4; k++) A[j][k] -= f * A[i][k]; }
    double x[3]; for (int i = 2; i >= 0; i--) { double s = A[i][3]; for (int k = i + 1; k < 3; k++) s -= A[i][k] * x[k]; x[i] = s / A[i][i]; }
    double sig = 0, noise = 0;
    for (size_t k = 0; k < N; k++) {
        double t = 2 * M_PI * freq * (k + start) / outRate, fit = x[0] * std::sin(t) + x[1] * std::cos(t) + x[2], y = out[(k + start) * 2];
        sig += fit * fit; noise += (y - fit) * (y - fit);
    }
    return 10 * std::log10(sig / noise);
}

// Level of [out] against the 12000-amplitude tone that went in, in dB.
static double level(const std::vector<int16_t> &out) {
    double s = 0; size_t start = 4000, N = out.size() / 2 - start;
    for (size_t k = 0; k < N; k++) s += double(out[(k + start) * 2]) * out[(k + start) * 2];
    return 10 * std::log10(s / N / (12000.0 * 12000.0 / 2) + 1e-30);
}

template <class R>
static std::vector<int16_t> run(R &r, const std::vector<int16_t> &in, int callback, double factor, size_t outFrames) {
    std::vector<int16_t> out, blk(callback * 2); size_t rd = 0;
    auto pull = [&](int16_t *dst, int32_t frames) -> int32_t {
        int32_t n = int32_t(std::min<size_t>(frames, in.size() / 2 - rd));
        std::copy(&in[rd * 2], &in[(rd + n) * 2], dst); rd += n; return n;
    };
    while (out.size() / 2 < outFrames) { r.render(pull, blk.data(), callback, factor); out.insert(out.end(), blk.begin(), blk.end()); }
    return out;
}

int main(int argc, char **argv) {
    const double outRate = 48000;
    if (argc > 1) {
        const double inRate = argc > 2 ? std::atof(argv[2]) : 65536;
        FILE *f = std::fopen(argv[1], "rb"); if (!f) { std::printf("cannot read %s\n", argv[1]); return 1; }
        std::vector<int16_t> in; int16_t buf[4096]; size_t n;
        while ((n = std::fread(buf, sizeof(int16_t), 4096, f)) > 0) in.insert(in.end(), buf, buf + n);
        std::fclose(f);
        const size_t outFrames = size_t((in.size() / 2 - 200) / (inRate / outRate));
        CubicResampler cub; WindowedSincResampler sinc;
        auto a = run(cub, in, 96, inRate / outRate, outFrames), b = run(sinc, in, 96, inRate / outRate, outFrames);
        std::string base(argv[1]);
        FILE *fa = std::fopen((base + ".cubic.raw").c_str(), "wb"); std::fwrite(a.data(), 2, a.size(), fa); std::fclose(fa);
        FILE *fb = std::fopen((base + ".sinc.raw").c_str(), "wb"); std::fwrite(b.data(), 2, b.size(), fb); std::fclose(fb);
        std::printf("wrote %zu frames each at %.0f Hz from %.0f Hz\n", outFrames, outRate, inRate);
        return 0;
    }
    const double inRate = 65536, factor = inRate / outRate; const int seconds = 2;
    const size_t outFrames = size_t(outRate * (seconds - 0.2));
    for (int callback : {96, 192, 480}) for (double f : {440.0, 1760.0, 5000.0, 9000.0, 15000.0}) {
        auto in = tone(f, inRate, seconds);
        LinearResampler lin; std::vector<int16_t> a, blk(callback * 2); double carry = 0; size_t read = 0;
        while (a.size() / 2 < outFrames) {
            carry += callback * factor; int n = int(std::lround(carry)); carry -= n;
            lin.resample(&in[read * 2], n, blk.data(), callback); read += n; a.insert(a.end(), blk.begin(), blk.end());
        }
        CubicResampler cub; WindowedSincResampler sinc;
        auto b = run(cub, in, callback, factor, outFrames), c = run(sinc, in, callback, factor, outFrames);
        std::printf("callback %3d  %5.0f Hz   old %6.1f dB   cubic %6.1f dB   sinc %6.1f dB\n", callback, f,
                    score(a, f, outRate), score(b, f, outRate), score(c, f, outRate));
    }
    for (double f : {25000.0, 28000.0, 31000.0}) {
        auto in = tone(f, inRate, seconds);
        CubicResampler cub; WindowedSincResampler sinc;
        auto b = run(cub, in, 96, factor, outFrames), c = run(sinc, in, 96, factor, outFrames);
        std::printf("%5.0f Hz in (above 24 kHz): folds to %5.0f Hz at   cubic %6.1f dB   sinc %6.1f dB\n",
                    f, outRate - f, level(b), level(c));
    }
    // Upsampling, as from melonDS and Gambatte (32,768 Hz).
    for (double f : {440.0, 5000.0, 12000.0}) {
        auto in = tone(f, 32768, seconds);
        CubicResampler cub; WindowedSincResampler sinc;
        auto b = run(cub, in, 96, 32768 / outRate, outFrames), c = run(sinc, in, 96, 32768 / outRate, outFrames);
        std::printf("32768 Hz in  %5.0f Hz   cubic %6.1f dB   sinc %6.1f dB\n", f, score(b, f, outRate), score(c, f, outRate));
    }
}
