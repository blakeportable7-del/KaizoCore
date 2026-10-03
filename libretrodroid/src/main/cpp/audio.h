/*
 *     Copyright (C) 2019  Filippo Scognamiglio
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

#ifndef LIBRETRODROID_AUDIO_H
#define LIBRETRODROID_AUDIO_H

#include <array>
#include <atomic>
#include <chrono>
#include <mutex>
#include <unistd.h>
#include <oboe/Oboe.h>
#include <oboe/FifoBuffer.h>

#include "resamplers/windowedsincresampler.h"

namespace libretrodroid {

class Audio: public oboe::AudioStreamDataCallback, oboe::AudioStreamErrorCallback {
private:
    struct AudioLatencySettings {
        unsigned bufferSizeInVideoFrames;
        bool useLowLatencyStream;
    };

    const AudioLatencySettings DEFAULT_LATENCY_SETTINGS { 8, false };
    const AudioLatencySettings LOW_LATENCY_SETTINGS { 4, true };

public:
    Audio(int32_t sampleRate, double refreshRate, bool preferLowLatencyAudio);
    // LOCAL MODIFICATION (KaizoCore): closes the stream first, see audio.cpp.
    ~Audio() override;

    void start();
    void stop();

    oboe::DataCallbackResult onAudioReady(
        oboe::AudioStream *oboeStream,
        void *audioData,
        int32_t numFrames
    ) override;

    void onErrorAfterClose(oboe::AudioStream *oldStream, oboe::Result result) override;

public:
    void write(const int16_t *data, size_t frames);
    void setPlaybackSpeed(const double newPlaybackSpeed);
    // LOCAL MODIFICATION (KaizoCore, rc32 audit P3 #92): reopens a stream the device took away. Emulation thread only,
    // under the core lock (LibretroDroid::step), where no write can run on the stream it replaces.
    void serviceRebuild();
    // LOCAL MODIFICATION (KaizoCore, rc32 audit P2 #123): the core's sound now arrives at this rate.
    void setInputSampleRate(int32_t rate);

private:
    static int32_t roundToEven(int32_t x);
    double computeDynamicBufferConversionFactor(double dt);
    int32_t computeAudioBufferSize();
    bool initializeStream();
    std::unique_ptr<Audio::AudioLatencySettings> findBestLatencySettings(bool preferLowLatencyAudio);
    double computeMaximumLatency() const;

private:
    const double kp = 0.006;
    const double ki = 0.00002;
    const double maxp = 0.003;
    const double maxi = 0.02;

    // LOCAL MODIFICATION (KaizoCore): see resamplers/windowedsincresampler.h (and cubicresampler.h before it).
    WindowedSincResampler resampler;
    std::unique_ptr<oboe::FifoBuffer> fifoBuffer = nullptr;
    std::unique_ptr<int16_t[]> temporaryAudioBuffer = nullptr;

    oboe::ManagedStream stream = nullptr;
    std::unique_ptr<oboe::LatencyTuner> latencyTuner = nullptr;

    std::atomic<bool> startRequested { false };
    // LOCAL MODIFICATION (KaizoCore, rc32 audit P3 #92): set on Oboe's error thread, served on the emulation thread.
    std::atomic<bool> rebuildPending { false };
    // Held while the stream is replaced, started or stopped (the main thread starts and stops it).
    std::mutex streamLock;
    // LOCAL MODIFICATION (KaizoCore, rc32 audit P2 #120): when the core last wrote sound, in steady-clock nanoseconds.
    std::atomic<int64_t> lastWriteNs { 0 };
    int32_t inputSampleRate;
    double contentRefreshRate = 60.0;

    std::atomic<double> baseConversionFactor { 1.0 };

    double framesToSubmit = 0.0;
    double errorIntegral = 0.0;

    double playbackSpeed = 1.0;

    std::unique_ptr<AudioLatencySettings> audioLatencySettings;
};

} // namespace libretrodroid

#endif //LIBRETRODROID_AUDIO_H
