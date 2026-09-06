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

#ifndef LIBRETRODROID_LIBRETRODROID_H
#define LIBRETRODROID_LIBRETRODROID_H

#include <jni.h>

#include <EGL/egl.h>

#include <string>
#include <vector>
#include <unordered_set>
#include <mutex>
#include <atomic>
#include <memory>
#include <optional>

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
#include "vfs/vfsfile.h"
#include "renderers/es3/framebufferrenderer.h"
#include "renderers/es2/imagerendereres2.h"
#include "renderers/es3/imagerendereres3.h"
#include "utils/rect.h"

namespace libretrodroid {

class LibretroDroid {
public:
    static LibretroDroid& getInstance()
    {
        static LibretroDroid instance;
        return instance;
    }
    LibretroDroid(LibretroDroid const&) = delete;
    void operator=(LibretroDroid const&) = delete;

    void setViewport(Rect viewportRect);

private:
    LibretroDroid() {}

public:
    void setCheat(unsigned index, bool enabled, const std::string& code);
    void resetCheat();

    std::pair<int8_t*, size_t> serializeState();
    bool unserializeState(int8_t *data, size_t size);

    std::pair<int8_t *, size_t> serializeSRAM();
    jboolean unserializeSRAM(int8_t *data, size_t size);

    void onSurfaceCreated();
    void onSurfaceChanged(unsigned int width, unsigned int height);

    void create(
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
    );
    void resume();
    void step();
    void pause();
    void destroy();

    void reset();

    void loadGameFromPath(const std::string &gamePath);
    void loadGameFromBytes(const int8_t *data, size_t size);
    void loadGameFromVirtualFiles(std::vector<VFSFile> virtualFiles);

    /**
     * IronMON One patch: read emulated memory for the tracker.
     *
     * Tries the SET_MEMORY_MAPS descriptors first (mGBA publishes those), then
     * falls back to retro_get_memory_data(RETRO_MEMORY_SYSTEM_RAM), which is what
     * melonDS exposes. Both consoles place system RAM at 0x02000000, so the
     * fallback treats the address as an offset from there. Returns bytes read; 0
     * means "no map here", never zeroed memory.
     */
    size_t readMemory(uint64_t address, size_t length, unsigned char* output);

    /**
     * IronMON One patch: true only between a successful game load and teardown.
     *
     * Querying core memory before the game is loaded crashes INSIDE the core -
     * melonDS's GetMemorySize dereferences a CoreState that does not exist yet
     * (SIGSEGV at 0x18, caught 2026-08-30). A null check on the Core wrapper is
     * not enough, because the wrapper and its function pointers are valid long
     * before the core is ready to answer.
     */
    std::atomic<bool> gameLoaded { false };

    /**
     * Frames the core has actually run. melonDS builds its CoreState lazily on
     * the first retro_run, so "the game is loaded" is NOT enough to ask it for
     * memory: GetMemorySize still dereferences a state that does not exist and
     * takes the process down. Memory access waits for real frames instead.
     */
    std::atomic<uint32_t> framesRun { 0 };

    /** Write counterpart of [readMemory], same descriptor-then-SYSTEM_RAM order. */
    size_t writeMemory(uint64_t address, size_t length, const unsigned char* input);

    void onKeyEvent(unsigned int port, int action, int keyCode);
    void onMotionEvent(unsigned int port, unsigned int source, float xAxis, float yAxis);
    void onTouchEvent(float xAxis, float yAxis);

    void refreshAspectRatio();
    float getAspectRatio();

    bool requiresVideoRefresh() const;
    void clearRequiresVideoRefresh();

    std::vector<Variable> getVariables();
    void updateVariable(const Variable& variable);

    std::vector<std::vector<struct Controller>> getControllers();
    void setControllerType(unsigned int port, unsigned int type);

    int availableDisks();
    int currentDisk();
    void changeDisk(unsigned int index);

    void setRumbleEnabled(bool enabled);
    bool isRumbleEnabled() const;
    void handleRumbleUpdates(const std::function<void(int, float, float)> &handler);

    void setFrameSpeed(unsigned int speed);
    // KaizoCore patch: slow motion. The core runs once every `divisor`
    // vsyncs; every vsync still renders, so the picture never freezes.
    void setSlowMotion(unsigned int divisor);
    // KaizoCore patch: raw core memory for rcheevos' region mapper.
    void* coreMemoryData(unsigned id);
    size_t coreMemorySize(unsigned id);

    void setAudioEnabled(bool enabled);

    void setShaderConfig(ShaderManager::Config shaderConfig);

    void resetGlobalVariables();

    // Handle callbacks
    void handleVideoRefresh(const void *data, unsigned width, unsigned height, size_t pitch);
    size_t handleAudioCallback(const int16_t* data, size_t frames);
    int16_t handleSetInputState(unsigned port, unsigned device, unsigned index, unsigned id);
    uintptr_t handleGetCurrentFrameBuffer();

private:
    void updateAudioSampleRateMultiplier();
    float findDefaultAspectRatio(const retro_system_av_info &system_av_info);
    void afterGameLoad();

protected:
    static void callback_hw_video_refresh(const void *data, unsigned width, unsigned height, size_t pitch);
    static size_t callback_set_audio_sample_batch(const int16_t* data, size_t frames);
    static void callback_audio_sample(int16_t left, int16_t right);
    static int16_t callback_set_input_state(unsigned port, unsigned device, unsigned index, unsigned id);
    static uintptr_t callback_get_current_framebuffer();
    static void callback_retro_set_input_poll();

private:
    unsigned int frameSpeed = 1;
    unsigned int slowDivisor = 1;
    unsigned int slowTick = 0;
    bool audioEnabled = true;
    bool preferLowLatencyAudio = false;
    bool rumbleEnabled = false;

    ShaderManager::Config fragmentShaderConfig = ShaderManager::Config {
        ShaderManager::Type::SHADER_DEFAULT, { }
    };

    Rect viewportRect = Rect(0.0F, 0.0F, 1.0F, 1.0F);
    float screenRefreshRate = 60.0;
    int openglESVersion = 2;
    bool skipDuplicateFrames = false;
    bool immersiveModeEnabled = false;
    ImmersiveMode::Config immersiveModeConfig {};

    float defaultAspectRatio = 1.0;
    bool dirtyVideo = false;

    std::mutex coreLock;

    std::unique_ptr<Core> core;
    std::unique_ptr<Audio> audio;
    std::unique_ptr<Video> video;
    std::unique_ptr<FPSSync> fpsSync;
    std::unique_ptr<Input> input;
    std::unique_ptr<Rumble> rumble;
};

} //namespace libretrodroid

#endif //LIBRETRODROID_LIBRETRODROID_H
