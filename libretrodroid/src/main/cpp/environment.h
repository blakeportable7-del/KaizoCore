/*
 *     Copyright (C) 2020  Filippo Scognamiglio
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

#ifndef LIBRETRODROID_ENVIRONMENT_H
#define LIBRETRODROID_ENVIRONMENT_H

#define MODULE_NAME_CORE "Libretro Core"

#include <vector>
#include <mutex>
#include <string>
#include <cstring>
#include <cmath>
#include <EGL/egl.h>
#include <unordered_map>
#include <array>

#include "../../libretro-common/include/libretro.h"
#include "log.h"
#include "rumblestate.h"

class Environment {
public:
    static Environment& getInstance()
    {
        static Environment instance;
        return instance;
    }
    Environment(Environment const&) = delete;
    void operator=(Environment const&) = delete;

    static void callback_retro_log(enum retro_log_level level, const char *fmt, ...);

    // KaizoCore patch: the libretro sensor interface. The core asks for
    // accelerometer / gyroscope / illuminance; the app feeds values in.
    static bool callback_set_sensor_state(unsigned port, enum retro_sensor_action action, unsigned rate);
    static float callback_sensor_get_input(unsigned port, unsigned id);
    void setSensorValues(float ax, float ay, float az, float gx, float gy, float gz, float lux);
    unsigned getSensorMask() const { return sensorMask; }

    static bool callback_set_rumble_state(
        unsigned port,
        enum retro_rumble_effect effect,
        uint16_t strength
    );

    static bool callback_environment(unsigned cmd, void *data);

    void setEnableVirtualFileSystem(bool value);
    void setEnableMicrophone(bool value);

private:
    Environment() {}

public:
    void initialize(
        const std::string &requiredSystemDirectory,
        const std::string &requiredSavesDirectory,
        retro_hw_get_current_framebuffer_t required_callback_get_current_framebuffer
    );

    void deinitialize();

    void updateVariable(const std::string &key, const std::string &value);

    void setLanguage(const std::string &androidLanguage);

    float retrieveGameSpecificAspectRatio();

    bool handle_callback_set_rumble_state(
        unsigned port,
        enum retro_rumble_effect effect,
        uint16_t strength
    );

    bool handle_callback_environment(unsigned cmd, void *data);

    retro_hw_context_reset_t getHwContextReset() const;
    retro_hw_context_reset_t getHwContextDestroy() const;

    struct retro_disk_control_callback* getRetroDiskControlCallback() const;

    int getPixelFormat() const;
    bool isUseHwAcceleration() const;
    bool isUseDepth() const;
    bool isUseStencil() const;
    bool isBottomLeftOrigin() const;

    float getScreenRotation() const;
    bool isScreenRotationUpdated() const;
    void clearScreenRotationUpdated();

    unsigned int getGameGeometryWidth() const;
    unsigned int getGameGeometryHeight() const;
    float getGameGeometryAspectRatio() const;
    bool isGameGeometryUpdated() const;
    void clearGameGeometryUpdated();

    std::array<libretrodroid::RumbleState, 4> & getLastRumbleStates();

    const std::vector<struct Variable> getVariables() const;

    const std::vector<std::vector<struct Controller>> &getControllers() const;

    // IronMON One patch: capture RETRO_ENVIRONMENT_SET_MEMORY_MAPS so a tracker can
    // read emulated memory (the mGBA core publishes WRAM through it; the older
    // retro_get_memory_data API exposes only save RAM).
    void setMemoryMaps(const struct retro_memory_map* maps);
    // KaizoCore patch: a copy of the descriptors for rcheevos' memory mapper.
    std::vector<struct retro_memory_descriptor> getMemoryDescriptors() {
        std::lock_guard<std::mutex> lock(memoryDescriptorsLock);
        return memoryDescriptors;
    }
    size_t readMemoryRegion(uint64_t address, size_t length, unsigned char* output);
    size_t writeMemoryRegion(uint64_t address, size_t length, const unsigned char* input);

private:
    bool environment_handle_set_variables(const struct retro_variable* received);
    bool environment_handle_get_variable(struct retro_variable* requested);
    bool environment_handle_set_controller_info(const struct retro_controller_info* received);
    bool environment_handle_set_hw_render(struct retro_hw_render_callback* hw_render_callback);
    bool environment_handle_get_vfs_interface(struct retro_vfs_interface_info* vfs_interface_info);
    bool environment_handle_get_microphone_interface(struct retro_microphone_interface* microphone_interface);

private:
    retro_hw_context_reset_t hw_context_reset = nullptr;
    retro_hw_context_reset_t hw_context_destroy = nullptr;
    struct retro_disk_control_callback *retro_disk_control_callback = nullptr;

    std::string savesDirectory;
    std::string systemDirectory;
    retro_hw_get_current_framebuffer_t callback_get_current_framebuffer = nullptr;
    unsigned language = RETRO_LANGUAGE_ENGLISH;
    bool useVirtualFileSystem = false;
    bool enableMicrophone = false;

    int pixelFormat = RETRO_PIXEL_FORMAT_RGB565;
    bool useHWAcceleration = false;
    bool useDepth = false;
    bool useStencil = false;
    bool bottomLeftOrigin = false;

    float screenRotation = 0;
    bool screenRotationUpdated = false;

    bool gameGeometryUpdated = false;
    unsigned gameGeometryWidth = 0;
    unsigned gameGeometryHeight = 0;
    float gameGeometryAspectRatio = -1.0f;

    std::array<libretrodroid::RumbleState, 4> rumbleStates;
    float sensorValues[7] = {0, 0, 0, 0, 0, 0, 0};
    unsigned sensorMask = 0;   // bit0 accelerometer, bit1 gyroscope, bit2 illuminance

    std::unordered_map<std::string, struct Variable> variables;
    bool dirtyVariables = false;

    std::vector<std::vector<struct Controller>> controllers;

    // IronMON One patch: copies of the core's memory descriptors. The ptr fields point
    // into core-owned memory and stay valid while the game is loaded.
    std::vector<struct retro_memory_descriptor> memoryDescriptors;
    // IronMON One patch: the core publishes these from the emulation thread
    // while the tracker reads them from its own. Iterating the vector while it
    // is being reassigned is a use-after-free, so both sides take this lock.
    std::mutex memoryDescriptorsLock;
};

struct Variable {
public:
    std::string key;
    std::string value;
    std::string description;
};

struct Controller {
public:
    unsigned id;
    std::string description;
};

#endif //LIBRETRODROID_ENVIRONMENT_H

