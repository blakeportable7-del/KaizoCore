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

#include <cstring>

#include "log.h"

#include "audio.h"
#include <cmath>
#include <memory>

namespace libretrodroid {

Audio::Audio(int32_t sampleRate, double refreshRate, bool preferLowLatencyAudio) {
    LOGI("Audio initialization has been called with input sample rate %d", sampleRate);

    contentRefreshRate = refreshRate;
    inputSampleRate = sampleRate;
    audioLatencySettings = findBestLatencySettings(preferLowLatencyAudio);
    initializeStream();
}

Audio::~Audio() {
    // LOCAL MODIFICATION (KaizoCore): stop and close the stream before any
    // member is destroyed.
    //
    // Members go in reverse order of declaration, and latencyTuner is declared
    // after stream, so it was freed while the stream's callback thread could
    // still be inside onAudioReady. The null check there cannot close that
    // window: the member is checked, then freed on this thread, then tune()
    // runs on null. Seen twice on the emulator on 2026-09-29 tearing a DS core
    // down, once on NEW RUN and once leaving the Play tab:
    //   signal 11 (SIGSEGV), fault addr 0x8, AudioTrack thread
    //   oboe::LatencyTuner::tune()+9
    //   libretrodroid::Audio::onAudioReady(...)+437
    // stop() waits for the stream to stop and close() for its callback thread
    // to end, so nothing the callback reads is gone while it can still run.
    if (stream != nullptr) {
        stream->stop();
        stream->close();
    }
}

bool Audio::initializeStream() {
    LOGI("Using low latency stream: %d", audioLatencySettings->useLowLatencyStream);

    int32_t audioBufferSize = computeAudioBufferSize();

    oboe::AudioStreamBuilder builder;
    builder.setChannelCount(2);
    builder.setDirection(oboe::Direction::Output);
    builder.setFormat(oboe::AudioFormat::I16);
    builder.setDataCallback(this);
    builder.setErrorCallback(this);

    if (audioLatencySettings->useLowLatencyStream) {
        builder.setPerformanceMode(oboe::PerformanceMode::LowLatency);
    } else {
        builder.setFramesPerCallback(audioBufferSize / 10);
    }

    oboe::Result result = builder.openManagedStream(stream);
    if (result == oboe::Result::OK) {
        baseConversionFactor.store((double) inputSampleRate / stream->getSampleRate());
        fifoBuffer = std::make_unique<oboe::FifoBuffer>(2, audioBufferSize);
        temporaryAudioBuffer = std::unique_ptr<int16_t[]>(new int16_t[audioBufferSize]);
        latencyTuner = std::make_unique<oboe::LatencyTuner>(*stream);
        return true;
    } else {
        LOGE("Failed to create stream. Error: %s", oboe::convertToText(result));
        stream = nullptr;
        latencyTuner = nullptr;
        return false;
    }
}

std::unique_ptr<Audio::AudioLatencySettings> Audio::findBestLatencySettings(bool preferLowLatencyAudio) {
    if (oboe::AudioStreamBuilder::isAAudioRecommended() && preferLowLatencyAudio) {
        return std::make_unique<AudioLatencySettings>(LOW_LATENCY_SETTINGS);
    } else {
        return std::make_unique<AudioLatencySettings>(DEFAULT_LATENCY_SETTINGS);
    }
}

int32_t Audio::computeAudioBufferSize() {
    double maxLatency = computeMaximumLatency();
    LOGI("Average audio latency set to: %f ms", maxLatency * 0.5);
    double sampleRateDivisor = 500.0 / maxLatency;
    return roundToEven(inputSampleRate / sampleRateDivisor);
}

double Audio::computeMaximumLatency() const {
    double maxLatency = (audioLatencySettings->bufferSizeInVideoFrames / contentRefreshRate) * 1000;
    return std::max(maxLatency, 32.0);
}

void Audio::start() {
    std::lock_guard<std::mutex> lock(streamLock);
    startRequested = true;
    if (stream != nullptr)
        stream->requestStart();
}

void Audio::stop() {
    std::lock_guard<std::mutex> lock(streamLock);
    startRequested = false;
    if (stream != nullptr)
        stream->requestStop();
}

void Audio::serviceRebuild() {
    if (!rebuildPending.exchange(false)) return;
    std::lock_guard<std::mutex> lock(streamLock);
    LOGI("Reopening the sound stream after the device went away");
    if (initializeStream() && startRequested && stream != nullptr) {
        stream->requestStart();
    }
}

void Audio::setInputSampleRate(int32_t rate) {
    std::lock_guard<std::mutex> lock(streamLock);
    if (rate <= 0) return;
    inputSampleRate = rate;
    if (stream != nullptr) baseConversionFactor.store((double) inputSampleRate / stream->getSampleRate());
}

void Audio::write(const int16_t *data, size_t frames) {
    // LOCAL MODIFICATION (IronMON One): same defect class as onAudioReady.
    //
    // This runs on the EMULATOR thread while teardown runs on another, so
    // fifoBuffer can be destroyed underneath it. Dropping a buffer during
    // teardown is inaudible; dereferencing null is a lost run.
    if (fifoBuffer == nullptr) {
        return;
    }
    lastWriteNs.store(std::chrono::duration_cast<std::chrono::nanoseconds>(
        std::chrono::steady_clock::now().time_since_epoch()).count());
    fifoBuffer->write(data, frames * 2);
}

void Audio::setPlaybackSpeed(const double newPlaybackSpeed) {
    playbackSpeed = newPlaybackSpeed;
}

oboe::DataCallbackResult Audio::onAudioReady(oboe::AudioStream *oboeStream, void *audioData, int32_t numFrames) {
    // LOCAL MODIFICATION (IronMON One): guard the callback's three members.
    //
    // fifoBuffer, temporaryAudioBuffer and latencyTuner are populated ONLY when
    // openManagedStream() succeeds, and are destroyed at teardown while a
    // callback can still be in flight on the AudioTrack thread. All three were
    // dereferenced unconditionally, so either window is a null dereference in
    // native code - which kills the process outright, with no Java exception
    // to catch and nothing in the app's own logs.
    //
    // Seen as a hard crash on NEW RUN, which destroys the core and builds a
    // new one:
    //   signal 11 (SIGSEGV), fault addr 0x8
    //   oboe::LatencyTuner::tune()+9
    //   libretrodroid::Audio::onAudioReady(...)+345
    // fault addr 0x8 with rdi=0 is `this` being null inside tune().
    //
    // Emit silence for this callback instead. A silent buffer during teardown
    // is inaudible; the alternative is losing the run.
    if (fifoBuffer == nullptr || temporaryAudioBuffer == nullptr ||
        latencyTuner == nullptr) {
        std::memset(audioData, 0, (size_t) numFrames * 2 * sizeof(int16_t));
        return oboe::DataCallbackResult::Continue;
    }

    double dynamicBufferFactor = computeDynamicBufferConversionFactor(0.001 * numFrames);
    double finalConversionFactor = baseConversionFactor.load() * dynamicBufferFactor * playbackSpeed;

    // LOCAL MODIFICATION (KaizoCore): read input at the exact fractional rate.
    // This used to round each callback's input to whole frames and stretch that
    // onto the callback, so the rate wobbled every callback and the music at 1x
    // sounded grainy. See resamplers/cubicresampler.h for the measurements, and
    // resamplers/windowedsincresampler.h for the static that came after it.
    // The FIFO counts int16 samples, two to a stereo frame; the core always
    // writes whole frames, so a read of an even count stays aligned.
    auto outputArray = reinterpret_cast<int16_t *>(audioData);
    resampler.render(
        [this](int16_t *dst, int32_t frames) -> int32_t {
            const int32_t samples = fifoBuffer->readNow(dst, frames * 2);
            return samples > 0 ? samples / 2 : 0;
        },
        outputArray, numFrames, finalConversionFactor);

    latencyTuner->tune();

    return oboe::DataCallbackResult::Continue;
}

// To prevent audio buffer overruns or underruns we set up a PI controller. The idea is to run the
// audio slower when the buffer is empty and faster when it's full.
double Audio::computeDynamicBufferConversionFactor(double dt) {
    double framesCapacityInBuffer = fifoBuffer->getBufferCapacityInFrames();
    double framesAvailableInBuffer = fifoBuffer->getFullFramesAvailable();

    // Error is represented by normalized distance to half buffer utilization. Range [-1.0, 1.0]
    double errorMeasure = (framesCapacityInBuffer - 2.0f * framesAvailableInBuffer) / framesCapacityInBuffer;

    // LOCAL MODIFICATION (KaizoCore, rc32 audit P2 #120). The integral is held while the core writes no sound (muted,
    // fast forward, slow motion, rewind): it grew without end on the empty buffer, and back at 1x the game played flat
    // and dropped sound for about as long as it had been silent. It is also kept within the range its output can use.
    const int64_t nowNs = std::chrono::duration_cast<std::chrono::nanoseconds>(
        std::chrono::steady_clock::now().time_since_epoch()).count();
    const bool coreWriting = nowNs - lastWriteNs.load() < 100000000LL;
    if (coreWriting) {
        errorIntegral = std::clamp(errorIntegral + errorMeasure * dt, -maxi / ki, maxi / ki);
    }

    // Wikipedia states that human ear resolution is around 3.6 Hz within the octave of 1000–2000 Hz.
    // This changes continuously, so we should try to keep it a very low value.
    double proportionalAdjustment = std::clamp(kp * errorMeasure, -maxp, maxp);

    // Ki is a lot lower, so it's safe if it exceeds the ear threshold. Hopefully convergence will
    // be slow enough to be not perceptible. We need to battle test this value.
    double integralAdjustment = std::clamp(ki * errorIntegral, -maxi, maxi);

    double finalAdjustment = proportionalAdjustment + integralAdjustment;

    LOGD("Audio speed adjustments (p: %f) (i: %f)", proportionalAdjustment, integralAdjustment);

    return 1.0 - (finalAdjustment);
}

int32_t Audio::roundToEven(int32_t x) {
    return (x / 2) * 2;
}

void Audio::onErrorAfterClose(oboe::AudioStream* oldStream, oboe::Result result) {
    AudioStreamErrorCallback::onErrorAfterClose(oldStream, result);
    LOGI("Stream error in oboe::onErrorAfterClose %s", oboe::convertToText(result));

    if (result != oboe::Result::ErrorDisconnected)
        return;

    // LOCAL MODIFICATION (KaizoCore, rc32 audit P3 #92): only flagged here. Rebuilt on Oboe's own thread, the new
    // stream and buffer replaced the old ones under a write in progress on the emulation thread.
    rebuildPending = true;
}

} //namespace libretrodroid
