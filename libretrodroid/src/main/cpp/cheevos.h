/*
 * KaizoCore patch: RetroAchievements through rcheevos' rc_client.
 *
 * The client lives here in native code so it can read the core's memory
 * every frame without a JNI hop. Everything that needs the outside world
 * (HTTP) or the player (events) is queued and drained from the JNI step,
 * which is the one place that already calls back into Kotlin each frame.
 */
#ifndef LIBRETRODROID_CHEEVOS_H
#define LIBRETRODROID_CHEEVOS_H

#include <string>
#include <vector>
#include <deque>
#include <mutex>
#include <map>
#include <cstdint>

extern "C" {
#include "rcheevos/include/rc_client.h"
#include "rcheevos/src/rc_libretro.h"
}

namespace libretrodroid {

struct CheevosServerCall {
    int id;
    std::string url;
    std::string postData;
    std::string contentType;
};

struct CheevosEvent {
    int type;          // rc_client event type, or the app-level codes below
    std::string title;
    std::string description;
    int points;
    std::string badgeUrl;
    int result;        // for login/load callbacks: rc_error code
};

// App-level event codes, outside rc_client's range.
constexpr int CHEEVOS_EVENT_LOGIN_DONE = 100;
constexpr int CHEEVOS_EVENT_GAME_LOADED = 101;
constexpr int CHEEVOS_EVENT_LOG = 102;

class Cheevos {
public:
    static Cheevos& getInstance();

    void create();
    void destroy();
    bool isCreated() const { return client != nullptr; }

    void loginWithPassword(const std::string& user, const std::string& password);
    void loginWithToken(const std::string& user, const std::string& token);
    void logout();
    void setHardcore(bool enabled);
    bool isHardcore() const;
    void loadGame(const std::string& path, unsigned consoleId);
    void unloadGame();
    bool isGameLoaded() const;
    void reset();

    /** Called from LibretroDroid::step once per frame while the core runs. */
    void doFrame();
    /** Called when the core is paused, so pending network work still moves. */
    void idle();

    /** The HTTP worker on the Kotlin side answers a queued call with this. */
    void serverResponse(int id, const std::string& body, int httpStatus);

    /** Drain queues (JNI step). */
    bool popServerCall(CheevosServerCall& out);
    bool popEvent(CheevosEvent& out);

    /** One-line JSON summaries for the UI. */
    std::string summaryJson();
    std::string achievementsJson();

    /** Memory regions from the core's memory map; rebuilt on game load. */
    void initMemory(unsigned consoleId);

private:
    Cheevos() = default;

    static uint32_t readMemoryThunk(uint32_t address, uint8_t* buffer, uint32_t num_bytes, rc_client_t* client);
    static void serverCallThunk(const rc_api_request_t* request, rc_client_server_callback_t callback, void* callback_data, rc_client_t* client);
    static void eventHandlerThunk(const rc_client_event_t* event, rc_client_t* client);
    static void loginCallback(int result, const char* error_message, rc_client_t* client, void* userdata);
    static void loadCallback(int result, const char* error_message, rc_client_t* client, void* userdata);
    static void logThunk(const char* message, const rc_client_t* client);

    void pushEvent(CheevosEvent e);

    rc_client_t* client = nullptr;
    rc_libretro_memory_regions_t regions{};
    bool regionsReady = false;
    std::mutex queueLock;
    std::deque<CheevosServerCall> serverCalls;
    std::deque<CheevosEvent> events;
    struct Pending { rc_client_server_callback_t callback; void* data; };
    std::map<int, Pending> pending;
    int nextId = 1;
    std::string lastLoadError;
};

} // namespace libretrodroid

#endif
