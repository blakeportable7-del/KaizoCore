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

#include <jni.h>

#include <EGL/egl.h>

#include <string>
#include <utility>
#include <vector>
#include <unordered_set>

#include "libretrodroid.h"
#include "cheevos.h"
#include "utils/libretrodroidexception.h"
#include "log.h"
#include "core.h"
#include "audio.h"
#include "video.h"
#include "renderers/renderer.h"
#include "fpssync.h"
#include "input.h"
#include "rumble.h"
#include "shadermanager.h"
#include "utils/javautils.h"
#include "environment.h"
#include "renderers/es3/framebufferrenderer.h"
#include "renderers/es2/imagerendereres2.h"
#include "renderers/es3/imagerendereres3.h"
#include "utils/utils.h"
#include "utils/rect.h"
#include "errorcodes.h"
#include "vfs/vfs.h"

namespace libretrodroid {

uintptr_t LibretroDroid::callback_get_current_framebuffer() {
    return LibretroDroid::getInstance().handleGetCurrentFrameBuffer();
}

void LibretroDroid::callback_hw_video_refresh(
    const void *data,
    unsigned width,
    unsigned height,
    size_t pitch
) {
    LOGD("hw video refresh callback called %i %i", width, height);
    LibretroDroid::getInstance().handleVideoRefresh(data, width, height, pitch);
}

void LibretroDroid::callback_audio_sample(int16_t left, int16_t right) {
    LOGE("callback audio sample (left, right) has been called");
}

size_t LibretroDroid::callback_set_audio_sample_batch(const int16_t *data, size_t frames) {
    return LibretroDroid::getInstance().handleAudioCallback(data, frames);
}

void LibretroDroid::callback_retro_set_input_poll() {
    // Do nothing in here...
}

int16_t LibretroDroid::callback_set_input_state(
    unsigned int port,
    unsigned int device,
    unsigned int index,
    unsigned int id
) {
    return LibretroDroid::getInstance().handleSetInputState(port, device, index, id);
}

void LibretroDroid::updateAudioSampleRateMultiplier() {
    if (audio) {
        audio->setPlaybackSpeed(frameSpeed);
    }
}

// TODO... Do we really need this?
void LibretroDroid::resetGlobalVariables() {
    core = nullptr;
    audio = nullptr;
    video = nullptr;
    fpsSync = nullptr;
    input = nullptr;
    rumble = nullptr;
}

int LibretroDroid::availableDisks() {
    return Environment::getInstance().getRetroDiskControlCallback() != nullptr
           ? Environment::getInstance().getRetroDiskControlCallback()->get_num_images()
           : 0;
}

int LibretroDroid::currentDisk() {
    return Environment::getInstance().getRetroDiskControlCallback() != nullptr
           ? Environment::getInstance().getRetroDiskControlCallback()->get_image_index()
           : 0;
}

void LibretroDroid::changeDisk(unsigned int index) {
    if (Environment::getInstance().getRetroDiskControlCallback() == nullptr) {
        LOGE("Cannot swap disk. This platform does not support it.");
        return;
    }

    if (index < 0 || index >= Environment::getInstance().getRetroDiskControlCallback()->get_num_images()) {
        LOGE("Requested image index is not valid.");
        return;
    }

    if (Environment::getInstance().getRetroDiskControlCallback()->get_image_index() != index) {
        Environment::getInstance().getRetroDiskControlCallback()->set_eject_state(true);
        Environment::getInstance().getRetroDiskControlCallback()->set_image_index((unsigned) index);
        Environment::getInstance().getRetroDiskControlCallback()->set_eject_state(false);
    }
}

void LibretroDroid::updateVariable(const Variable& variable) {
    Environment::getInstance().updateVariable(variable.key, variable.value);
}

std::vector<Variable> LibretroDroid::getVariables() {
    return Environment::getInstance().getVariables();
}

std::vector<std::vector<struct Controller>> LibretroDroid::getControllers() {
    return Environment::getInstance().getControllers();
}

void LibretroDroid::setControllerType(unsigned int port, unsigned int type) {
    core->retro_set_controller_port_device(port, type);
}

bool LibretroDroid::unserializeState(int8_t *data, size_t size) {
    std::lock_guard<std::mutex> lock(coreLock);
    if (!gameLoaded) return false;
    // KaizoCore patch: every serialize path can be reached after destroy()
    // (the app flushes SRAM on dispose, which on Back-to-exit runs AFTER the
    // lifecycle destroyed the core). A null core answers empty, never faults.
    if (core == nullptr) return false;

    return core->retro_unserialize(data, size);
}

JNIEXPORT jboolean JNICALL LibretroDroid::unserializeSRAM(int8_t* data, size_t size) {
    std::lock_guard<std::mutex> lock(coreLock);
    if (core == nullptr || !gameLoaded) return false;

    size_t sramSize = core->retro_get_memory_size(RETRO_MEMORY_SAVE_RAM);
    void *sramState = core->retro_get_memory_data(RETRO_MEMORY_SAVE_RAM);

    if (sramState == nullptr) {
        LOGE("Cannot load SRAM: nullptr in retro_get_memory_data");
        return false;
    }

    if (size > sramSize) {
        LOGE("Cannot load SRAM: size mismatch");
        return false;
    }

    memcpy(sramState, data, size);

    return true;
}

// IronMON One patch: see the header. Descriptors first, then SYSTEM_RAM.
size_t LibretroDroid::readMemory(uint64_t address, size_t length, unsigned char* output) {
    size_t read = Environment::getInstance().readMemoryRegion(address, length, output);
    if (read > 0) return read;

    std::lock_guard<std::mutex> lock(coreLock);
    // Not merely "is there a core": the core must have a game loaded, or its
    // own memory accessors dereference state it has not built yet.
    // Wait for the core to have actually run: a loaded game is not the same as
    // an initialised core (see framesRun in the header).
    if (core == nullptr || !gameLoaded || framesRun < 120) return 0;

    // melonDS publishes no memory-map descriptors, only SYSTEM_RAM. Both the DS
    // (4MB main RAM) and the GBA (256KB EWRAM) map it at 0x02000000.
    const uint64_t systemRamBase = 0x02000000;
    size_t ramSize = core->retro_get_memory_size(RETRO_MEMORY_SYSTEM_RAM);
    auto* ram = static_cast<unsigned char*>(
        core->retro_get_memory_data(RETRO_MEMORY_SYSTEM_RAM));
    if (ram == nullptr || ramSize == 0) return 0;
    if (address < systemRamBase) return 0;

    uint64_t offset = address - systemRamBase;
    if (offset >= ramSize) return 0;
    size_t available = std::min(length, static_cast<size_t>(ramSize - offset));
    memcpy(output, ram + offset, available);
    return available;
}

// IronMON One patch: write counterpart of readMemory. Same fallback, because a
// core that publishes no descriptors (melonDS) would otherwise accept writes
// that silently go nowhere.
size_t LibretroDroid::writeMemory(uint64_t address, size_t length, const unsigned char* input) {
    size_t written = Environment::getInstance().writeMemoryRegion(address, length, input);
    if (written > 0) return written;

    std::lock_guard<std::mutex> lock(coreLock);
    if (core == nullptr || !gameLoaded || framesRun < 120) return 0;

    const uint64_t systemRamBase = 0x02000000;
    size_t ramSize = core->retro_get_memory_size(RETRO_MEMORY_SYSTEM_RAM);
    auto* ram = static_cast<unsigned char*>(
        core->retro_get_memory_data(RETRO_MEMORY_SYSTEM_RAM));
    if (ram == nullptr || ramSize == 0) return 0;
    if (address < systemRamBase) return 0;

    uint64_t offset = address - systemRamBase;
    if (offset >= ramSize) return 0;
    size_t available = std::min(length, static_cast<size_t>(ramSize - offset));
    memcpy(ram + offset, input, available);
    return available;
}

std::pair<int8_t*, size_t> LibretroDroid::serializeSRAM() {
    std::lock_guard<std::mutex> lock(coreLock);
    if (core == nullptr || !gameLoaded) return { new int8_t[0], 0 };

    size_t size = core->retro_get_memory_size(RETRO_MEMORY_SAVE_RAM);
    auto* data = new int8_t[size];
    memcpy(data, (int8_t*) core->retro_get_memory_data(RETRO_MEMORY_SAVE_RAM), size);

    return std::pair(data, size);
}

void LibretroDroid::onSurfaceChanged(unsigned int width, unsigned int height) {
    LOGD("Performing libretrodroid onSurfaceChanged");
    video->updateScreenSize(width, height);
}

void LibretroDroid::onSurfaceCreated() {
    LOGD("Performing libretrodroid onSurfaceCreated");

    struct retro_system_av_info system_av_info {};
    core->retro_get_system_av_info(&system_av_info);

    video = nullptr;

    Video::RenderingOptions renderingOptions {
        Environment::getInstance().isUseHwAcceleration(),
        system_av_info.geometry.base_width,
        system_av_info.geometry.base_height,
        Environment::getInstance().isUseDepth(),
        Environment::getInstance().isUseStencil(),
        openglESVersion,
        Environment::getInstance().getPixelFormat()
    };

    auto newVideo = new Video(
        renderingOptions,
        fragmentShaderConfig,
        Environment::getInstance().isBottomLeftOrigin(),
        Environment::getInstance().getScreenRotation(),
        skipDuplicateFrames,
        immersiveModeEnabled,
        viewportRect,
        immersiveModeConfig
    );

    video = std::unique_ptr<Video>(newVideo);

    if (Environment::getInstance().getHwContextReset() != nullptr) {
        Environment::getInstance().getHwContextReset()();
    }
}

void LibretroDroid::onMotionEvent(
    unsigned int port,
    unsigned int source,
    float xAxis,
    float yAxis
) {
    LOGD("Received motion event: %d %.2f, %.2f", source, xAxis, yAxis);
    if (input) {
        input->onMotionEvent(port, source, xAxis, yAxis);
    }
}

void LibretroDroid::onTouchEvent(float xAxis, float yAxis) {
    LOGD("Received touch event: %.2f, %.2f", xAxis, yAxis);
    if (input && video) {
        auto [x, y] = video->getLayout().getRelativePosition(xAxis, yAxis);
        input->onMotionEvent(0, Input::MOTION_SOURCE_POINTER, x, y);
    }
}

void LibretroDroid::onKeyEvent(unsigned int port, int action, int keyCode) {
    // IronMON One debug: LOGI so it shows without VERBOSE_LOGGING.
    LOGI("IM1 key: action=%d keyCode=%d input=%s", action, keyCode,
         input ? "alive" : "NULL");
    if (input) {
        input->onKeyEvent(port, action, keyCode);
    }
}

void LibretroDroid::create(
    unsigned int GLESVersion,
    const std::string& soFilePath,
    const std::string& systemDir,
    const std::string& savesDir,
    std::vector<Variable> variables,
    const ShaderManager::Config& shaderConfig,
    float refreshRate,
    bool lowLatencyAudio,
    bool enableVirtualFileSystem,
    bool enableMicrophone,
    bool duplicateFrames,
    std::optional<ImmersiveMode::Config> immersiveModeConfig,
    const std::string& language
) {
    LOGD("Performing libretrodroid create");

    resetGlobalVariables();

    Environment::getInstance().initialize(systemDir, savesDir, &callback_get_current_framebuffer);
    Environment::getInstance().setLanguage(language);
    Environment::getInstance().setEnableVirtualFileSystem(enableVirtualFileSystem);
    Environment::getInstance().setEnableMicrophone(enableMicrophone);

    openglESVersion = GLESVersion;
    screenRefreshRate = refreshRate;
    skipDuplicateFrames = duplicateFrames;
    immersiveModeEnabled = GLESVersion >= 3 && immersiveModeConfig.has_value();
    this->immersiveModeConfig = immersiveModeConfig.value_or(ImmersiveMode::Config{});
    audioEnabled = true;
    frameSpeed = 1;

    core = std::make_unique<Core>(soFilePath);

    core->retro_set_video_refresh(&callback_hw_video_refresh);
    core->retro_set_environment(&Environment::callback_environment);
    core->retro_set_audio_sample(&callback_audio_sample);
    core->retro_set_audio_sample_batch(&callback_set_audio_sample_batch);
    core->retro_set_input_poll(&callback_retro_set_input_poll);
    core->retro_set_input_state(&callback_set_input_state);

    std::for_each(variables.begin(), variables.end(), [&](const Variable& v) {
        updateVariable(v);
    });

    core->retro_init();

    preferLowLatencyAudio = lowLatencyAudio;

    // HW accelerated cores are only supported on opengles 3.
    if (Environment::getInstance().isUseHwAcceleration() && openglESVersion < 3) {
        throw LibretroDroidError("OpenGL ES 3 is required for this Core", ERROR_GL_NOT_COMPATIBLE);
    }

    fragmentShaderConfig = shaderConfig;

    rumble = std::make_unique<Rumble>();
}

void LibretroDroid::loadGameFromPath(const std::string& gamePath) {
    LOGD("Performing libretrodroid loadGameFromPath");
    struct retro_system_info system_info {};
    core->retro_get_system_info(&system_info);

    struct retro_game_info game_info {};
    game_info.path = Utils::cloneToCString(gamePath);
    game_info.meta = nullptr;

    if (system_info.need_fullpath) {
        game_info.data = nullptr;
        game_info.size = 0;
    } else {
        struct Utils::ReadResult file = Utils::readFileAsBytes(gamePath);
        game_info.data = file.data;
        game_info.size = file.size;
    }

    bool result = core->retro_load_game(&game_info);
    if (!result) {
        LOGE("Cannot load game. Leaving.");
        throw std::runtime_error("Cannot load game");
    }

    afterGameLoad();
    gameLoaded = true;
}

void LibretroDroid::loadGameFromBytes(const int8_t *data, size_t size) {
    LOGD("Performing libretrodroid loadGameFromBytes");

    struct retro_system_info system_info {};
    core->retro_get_system_info(&system_info);

    struct retro_game_info game_info {};
    game_info.path = nullptr;
    game_info.meta = nullptr;

    if (system_info.need_fullpath) {
        game_info.data = nullptr;
        game_info.size = 0;
    } else {
        game_info.data = data;
        game_info.size = size;
    }

    bool result = core->retro_load_game(&game_info);
    if (!result) {
        LOGE("Cannot load game. Leaving.");
        throw std::runtime_error("Cannot load game");
    }

    afterGameLoad();
    gameLoaded = true;
}

void LibretroDroid::loadGameFromVirtualFiles(std::vector<VFSFile> virtualFiles) {
    LOGD("Performing libretrodroid loadGameFromVirtualFiles");
    struct retro_system_info system_info {};
    core->retro_get_system_info(&system_info);

    if (virtualFiles.empty()) {
        LOGE("Calling loadGameFromVirtualFiles without any file.");
        throw std::runtime_error("Calling loadGameFromVirtualFiles without any file.");
    }

    std::string firstFilePath = virtualFiles[0].getFileName();
    int firstFileFD = virtualFiles[0].getFD();

    bool loadUsingVFS = system_info.need_fullpath || virtualFiles.size() > 1;

    struct retro_game_info game_info {};
    game_info.path = Utils::cloneToCString(firstFilePath);
    game_info.meta = nullptr;

    if (loadUsingVFS) {
        VFS::getInstance().initialize(std::move(virtualFiles));
    }

    if (loadUsingVFS) {
        game_info.data = nullptr;
        game_info.size = 0;
    } else {
        struct Utils::ReadResult file = Utils::readFileAsBytes(firstFileFD);
        game_info.data = file.data;
        game_info.size = file.size;
    }

    bool result = core->retro_load_game(&game_info);
    if (!result) {
        LOGE("Cannot load game. Leaving.");
        throw std::runtime_error("Cannot load game");
    }

    afterGameLoad();
}

void LibretroDroid::destroy() {
    std::lock_guard<std::mutex> lock(coreLock);
    gameLoaded = false;
    framesRun = 0;

    // IronMON One patch: destroy must be idempotent. The app tears the view down
    // explicitly (leaving the Play tab, and the sequenced NEW RUN reboot) AND the
    // lifecycle observer fires its own, so this runs twice; the second pass hit
    // retro_unload_game on an already-null core (SIGSEGV at 0x80).
    if (core == nullptr) {
        LOGD("libretrodroid destroy called with no core; already torn down");
        return;
    }

    LOGD("Performing libretrodroid destroy");

    if (Environment::getInstance().getHwContextDestroy() != nullptr) {
        Environment::getInstance().getHwContextDestroy()();
    }

    core->retro_unload_game();
    core->retro_deinit();

    video = nullptr;
    core = nullptr;
    rumble = nullptr;
    fpsSync = nullptr;
    audio = nullptr;

    Environment::getInstance().deinitialize();
    VFS::getInstance().deinitialize();
}

void LibretroDroid::resume() {
    LOGI("IM1 resume(): constructing Input");

    input = std::make_unique<Input>();

    // KaizoCore patch: resume/pause can arrive after destroy() nulled these
    // (a stale lifecycle observer, see GLRetroView). Guard, do not crash.
    if (fpsSync) fpsSync->reset();
    if (audio) audio->start();
    refreshAspectRatio();
}

void LibretroDroid::pause() {
    LOGD("Performing libretrodroid pause");
    if (audio) audio->stop();

    input = nullptr;
}

void LibretroDroid::step() {
    std::lock_guard<std::mutex> lock(coreLock);

    LOGD("Stepping into retro_run()");

    // Counted here so memory access can wait for a core that has really started.
    if (framesRun < 1000) framesRun++;

    unsigned frames = 1;
    if (fpsSync) {
        unsigned requestedFrames = fpsSync->advanceFrames();

        // If the application runs too slow it's better to just skip those frames.
        frames = std::min(requestedFrames, 2u);
    }

    // KaizoCore patch: slow motion runs the core on one vsync in `slowDivisor`.
    bool runCore = true;
    if (slowDivisor > 1) {
        slowTick = (slowTick + 1) % slowDivisor;
        runCore = slowTick == 0;
    }
    if (runCore) {
        for (size_t i = 0; i < frames * frameSpeed; i++)
            core->retro_run();
        // KaizoCore patch: achievements read memory once per emulated frame.
        if (gameLoaded) Cheevos::getInstance().doFrame();
    }

    if (video && !video->rendersInVideoCallback()) {
        video->renderFrame();
    }

    if (fpsSync) {
        fpsSync->wait();
    }

    if (rumble && rumbleEnabled) {
        rumble->fetchFromEnvironment();
    }

    // Some games override the core geometry at runtime. These fields get updated in retro_run().
    if (video && Environment::getInstance().isGameGeometryUpdated()) {
        Environment::getInstance().clearGameGeometryUpdated();

        video->updateRendererSize(
            Environment::getInstance().getGameGeometryWidth(),
            Environment::getInstance().getGameGeometryHeight()
        );

        dirtyVideo = true;
    }

    if (video && Environment::getInstance().isScreenRotationUpdated()) {
        Environment::getInstance().clearScreenRotationUpdated();

        video->updateRotation(Environment::getInstance().getScreenRotation());
    }
}

float LibretroDroid::getAspectRatio() {
    float gameAspectRatio = Environment::getInstance().retrieveGameSpecificAspectRatio();
    return gameAspectRatio > 0 ? gameAspectRatio : defaultAspectRatio;
}

void LibretroDroid::refreshAspectRatio() {
    if (video) video->updateAspectRatio(getAspectRatio());
}

void LibretroDroid::setRumbleEnabled(bool enabled) {
    rumbleEnabled = enabled;
}

bool LibretroDroid::isRumbleEnabled() const {
    return rumbleEnabled;
}

void* LibretroDroid::coreMemoryData(unsigned id) {
    return (core != nullptr && gameLoaded) ? core->retro_get_memory_data(id) : nullptr;
}

size_t LibretroDroid::coreMemorySize(unsigned id) {
    return (core != nullptr && gameLoaded) ? core->retro_get_memory_size(id) : 0;
}

void LibretroDroid::setSlowMotion(unsigned int divisor) {
    slowDivisor = divisor < 1 ? 1 : divisor;
    slowTick = 0;
}

void LibretroDroid::setFrameSpeed(unsigned int speed) {
    frameSpeed = speed;
    updateAudioSampleRateMultiplier();
}

void LibretroDroid::setAudioEnabled(bool enabled) {
    audioEnabled = enabled;
}

void LibretroDroid::setShaderConfig(ShaderManager::Config shaderConfig) {
    fragmentShaderConfig = std::move(shaderConfig);
    if (video) {
        video->updateShaderType(fragmentShaderConfig);
    }
}

void LibretroDroid::handleVideoRefresh(
    const void *data,
    unsigned int width,
    unsigned int height,
    size_t pitch
) {
    if (video) {
        video->onNewFrame(data, width, height, pitch);

        if (video->rendersInVideoCallback()) {
            video->renderFrame();
        }
    }
}

size_t LibretroDroid::handleAudioCallback(const int16_t *data, size_t frames) {
    if (audio && audioEnabled) {
        audio->write(data, frames);
    }
    return frames;
}

int16_t LibretroDroid::handleSetInputState(
    unsigned int port,
    unsigned int device,
    unsigned int index,
    unsigned int id
) {
    if (input) {
        return input->getInputState(port, device, index, id);
    }
    return 0;
}

uintptr_t LibretroDroid::handleGetCurrentFrameBuffer() {
    if (video) {
        return video->getCurrentFramebuffer();
    }
    return 0;
}

void LibretroDroid::reset() {
    std::lock_guard<std::mutex> lock(coreLock);

    core->retro_reset();
}

std::pair<int8_t*, size_t> LibretroDroid::serializeState() {
    std::lock_guard<std::mutex> lock(coreLock);
    // KaizoCore patch: a core whose game failed to load has no state to give;
    // melonDS DS faults (0x18) inside retro_serialize_size in that condition,
    // and the rewind recorder asks every two seconds.
    if (core == nullptr || !gameLoaded) return { new int8_t[0], 0 };

    size_t size = core->retro_serialize_size();
    auto data = new int8_t[size];

    core->retro_serialize(data, size);

    return std::pair(data, size);
}

void LibretroDroid::resetCheat() {
    std::lock_guard<std::mutex> lock(coreLock);
    if (core == nullptr) return;

    core->retro_cheat_reset();
}

void LibretroDroid::setCheat(unsigned index, bool enabled, const std::string& code) {
    std::lock_guard<std::mutex> lock(coreLock);
    if (core == nullptr) return;

    core->retro_cheat_set(index, enabled, Utils::cloneToCString(code));
}

bool LibretroDroid::requiresVideoRefresh() const {
    return dirtyVideo;
}

void LibretroDroid::clearRequiresVideoRefresh() {
    dirtyVideo = false;
}

void LibretroDroid::afterGameLoad() {
    struct retro_system_av_info system_av_info {};
    core->retro_get_system_av_info(&system_av_info);

    fpsSync = std::make_unique<FPSSync>(system_av_info.timing.fps, screenRefreshRate);

    double inputSampleRate = system_av_info.timing.sample_rate * fpsSync->getTimeStretchFactor();

    audio = std::make_unique<Audio>(
        (int32_t) std::lround(inputSampleRate),
        system_av_info.timing.fps,
        preferLowLatencyAudio
    );

    updateAudioSampleRateMultiplier();

    defaultAspectRatio = findDefaultAspectRatio(system_av_info);
}

float LibretroDroid::findDefaultAspectRatio(const retro_system_av_info& system_av_info) {
    float result = system_av_info.geometry.aspect_ratio;
    if (result < 0) {
        result =
            (float) system_av_info.geometry.base_width / (float) system_av_info.geometry.base_height;
    }
    return result;
}

void LibretroDroid::handleRumbleUpdates(const std::function<void(int, float, float)> &handler) {
    if (rumble && rumbleEnabled) {
        rumble->handleRumbleUpdates(handler);
    }
}

void LibretroDroid::setViewport(Rect viewportRect) {
    this->viewportRect = viewportRect;

    if (video != nullptr) {
        video->updateViewportSize(viewportRect);
    }
}

} //namespace libretrodroid
