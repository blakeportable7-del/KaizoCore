// Feeds a pure tone through the audio path the way Audio::onAudioReady drives it,
// then scores signal-to-noise by least-squares fitting the known tone.
//   old: round each callback's input to whole frames, stretch onto the callback (LinearResampler)
//   new: CubicResampler::render at the exact fractional rate
#include <cmath>
#include <cstdio>
#include <vector>
#include "linearresampler.h"
#include "cubicresampler.h"
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

int main() {
    const double inRate = 65238, outRate = 48000, factor = inRate / outRate; const int seconds = 2;
    const size_t outFrames = size_t(outRate * (seconds - 0.2));
    for (int callback : {96, 192, 480}) for (double f : {440.0, 1760.0, 5000.0, 9000.0, 15000.0}) {
        auto in = tone(f, inRate, seconds);
        // old
        LinearResampler lin; std::vector<int16_t> a, blk(callback * 2); double carry = 0; size_t read = 0;
        while (a.size() / 2 < outFrames) {
            carry += callback * factor; int n = int(std::lround(carry)); carry -= n;
            lin.resample(&in[read * 2], n, blk.data(), callback); read += n; a.insert(a.end(), blk.begin(), blk.end());
        }
        // new
        CubicResampler cub; std::vector<int16_t> b; size_t rd = 0;
        auto pull = [&](int16_t *dst, int32_t frames) -> int32_t {
            int32_t n = int32_t(std::min<size_t>(frames, in.size() / 2 - rd));
            std::copy(&in[rd * 2], &in[(rd + n) * 2], dst); rd += n; return n;
        };
        while (b.size() / 2 < outFrames) { cub.render(pull, blk.data(), callback, factor); b.insert(b.end(), blk.begin(), blk.end()); }
        printf("callback %3d  %5.0f Hz   old %6.1f dB   new %6.1f dB\n", callback, f, score(a, f, outRate), score(b, f, outRate));
    }
}
