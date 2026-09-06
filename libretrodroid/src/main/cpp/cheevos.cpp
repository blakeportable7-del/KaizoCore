/*
 * KaizoCore patch: RetroAchievements through rcheevos' rc_client. See cheevos.h.
 */
#include "cheevos.h"
#include "libretrodroid.h"
#include "environment.h"
#include "log.h"

#include <sstream>

extern "C" {
#include "rcheevos/include/rc_consoles.h"
#include "rcheevos/include/rc_error.h"
}

namespace libretrodroid {

Cheevos& Cheevos::getInstance() {
    static Cheevos instance;
    return instance;
}

static std::string jsonEscape(const char* s) {
    std::string out;
    if (s == nullptr) return out;
    for (const char* p = s; *p; ++p) {
        switch (*p) {
            case '"': out += "\\\""; break;
            case '\\': out += "\\\\"; break;
            case '\n': out += "\\n"; break;
            case '\r': out += "\\r"; break;
            case '\t': out += "\\t"; break;
            default: if ((unsigned char)*p < 0x20) out += ' '; else out += *p;
        }
    }
    return out;
}

void Cheevos::create() {
    if (client != nullptr) return;
    client = rc_client_create(&readMemoryThunk, &serverCallThunk);
    rc_client_set_event_handler(client, &eventHandlerThunk);
    rc_client_enable_logging(client, RC_CLIENT_LOG_LEVEL_INFO, &logThunk);
    // Softcore by default: the app decides, and hardcore is opt-in.
    rc_client_set_hardcore_enabled(client, 0);
    LOGI("Cheevos: client created");
}

void Cheevos::destroy() {
    if (client == nullptr) return;
    rc_client_destroy(client);
    client = nullptr;
    if (regionsReady) { rc_libretro_memory_destroy(&regions); regionsReady = false; }
    std::lock_guard<std::mutex> lock(queueLock);
    serverCalls.clear(); events.clear(); pending.clear();
}

void Cheevos::loginWithPassword(const std::string& user, const std::string& password) {
    if (client == nullptr) create();
    rc_client_begin_login_with_password(client, user.c_str(), password.c_str(), &loginCallback, nullptr);
}

void Cheevos::loginWithToken(const std::string& user, const std::string& token) {
    if (client == nullptr) create();
    rc_client_begin_login_with_token(client, user.c_str(), token.c_str(), &loginCallback, nullptr);
}

void Cheevos::logout() {
    if (client == nullptr) return;
    rc_client_logout(client);
}

void Cheevos::setHardcore(bool enabled) {
    if (client == nullptr) create();
    rc_client_set_hardcore_enabled(client, enabled ? 1 : 0);
}

bool Cheevos::isHardcore() const {
    return client != nullptr && rc_client_get_hardcore_enabled(client) != 0;
}

void Cheevos::loadGame(const std::string& path, unsigned consoleId) {
    if (client == nullptr) create();
    lastLoadError.clear();
    rc_client_begin_identify_and_load_game(client, consoleId, path.c_str(), nullptr, 0, &loadCallback, nullptr);
}

void Cheevos::unloadGame() {
    if (client == nullptr) return;
    rc_client_unload_game(client);
    if (regionsReady) { rc_libretro_memory_destroy(&regions); regionsReady = false; }
}

bool Cheevos::isGameLoaded() const {
    return client != nullptr && rc_client_is_game_loaded(client) != 0;
}

void Cheevos::reset() {
    if (client != nullptr) rc_client_reset(client);
}

void Cheevos::doFrame() {
    if (client != nullptr) rc_client_do_frame(client);
}

void Cheevos::idle() {
    if (client != nullptr) rc_client_idle(client);
}

// ---------------------------------------------------------------- memory

static void coreMemoryInfo(uint32_t id, rc_libretro_core_memory_info_t* info) {
    auto& ld = LibretroDroid::getInstance();
    info->data = (uint8_t*) ld.coreMemoryData(id);
    info->size = ld.coreMemorySize(id);
}

void Cheevos::initMemory(unsigned consoleId) {
    if (regionsReady) { rc_libretro_memory_destroy(&regions); regionsReady = false; }
    struct retro_memory_map mmap{};
    auto descriptors = Environment::getInstance().getMemoryDescriptors();
    mmap.descriptors = descriptors.empty() ? nullptr : descriptors.data();
    mmap.num_descriptors = (unsigned) descriptors.size();
    if (rc_libretro_memory_init(&regions, &mmap, &coreMemoryInfo, consoleId)) {
        regionsReady = true;
        LOGI("Cheevos: memory regions ready, %u bytes", (unsigned) regions.total_size);
    } else {
        LOGE("Cheevos: could not map the core's memory for console %u", consoleId);
    }
}

uint32_t Cheevos::readMemoryThunk(uint32_t address, uint8_t* buffer, uint32_t num_bytes, rc_client_t* client) {
    auto& self = getInstance();
    if (!self.regionsReady) return 0;
    return rc_libretro_memory_read(&self.regions, address, buffer, num_bytes);
}

// ---------------------------------------------------------------- network

void Cheevos::serverCallThunk(const rc_api_request_t* request, rc_client_server_callback_t callback, void* callback_data, rc_client_t* client) {
    auto& self = getInstance();
    std::lock_guard<std::mutex> lock(self.queueLock);
    int id = self.nextId++;
    self.pending[id] = Pending{callback, callback_data};
    self.serverCalls.push_back(CheevosServerCall{
        id, request->url ? request->url : "",
        request->post_data ? request->post_data : "",
        request->content_type ? request->content_type : ""
    });
}

void Cheevos::serverResponse(int id, const std::string& body, int httpStatus) {
    Pending p{};
    {
        std::lock_guard<std::mutex> lock(queueLock);
        auto it = pending.find(id);
        if (it == pending.end()) return;
        p = it->second;
        pending.erase(it);
    }
    rc_api_server_response_t response{};
    response.body = body.c_str();
    response.body_length = body.size();
    response.http_status_code = httpStatus;
    if (p.callback) p.callback(&response, p.data);
}

bool Cheevos::popServerCall(CheevosServerCall& out) {
    std::lock_guard<std::mutex> lock(queueLock);
    if (serverCalls.empty()) return false;
    out = serverCalls.front(); serverCalls.pop_front();
    return true;
}

// ----------------------------------------------------------------- events

void Cheevos::pushEvent(CheevosEvent e) {
    std::lock_guard<std::mutex> lock(queueLock);
    events.push_back(std::move(e));
}

bool Cheevos::popEvent(CheevosEvent& out) {
    std::lock_guard<std::mutex> lock(queueLock);
    if (events.empty()) return false;
    out = events.front(); events.pop_front();
    return true;
}

void Cheevos::eventHandlerThunk(const rc_client_event_t* event, rc_client_t* client) {
    auto& self = getInstance();
    CheevosEvent e{};
    e.type = (int) event->type;
    if (event->achievement) {
        e.title = event->achievement->title ? event->achievement->title : "";
        e.description = event->achievement->description ? event->achievement->description : "";
        e.points = (int) event->achievement->points;
        e.badgeUrl = event->achievement->badge_url ? event->achievement->badge_url : "";
    }
    if (event->server_error) {
        e.title = event->server_error->api ? event->server_error->api : "";
        e.description = event->server_error->error_message ? event->server_error->error_message : "";
        e.result = event->server_error->result;
    }
    self.pushEvent(e);
}

void Cheevos::loginCallback(int result, const char* error_message, rc_client_t* client, void* userdata) {
    auto& self = getInstance();
    CheevosEvent e{};
    e.type = CHEEVOS_EVENT_LOGIN_DONE;
    e.result = result;
    e.description = error_message ? error_message : "";
    const rc_client_user_t* u = rc_client_get_user_info(client);
    if (result == RC_OK && u != nullptr) {
        e.title = u->display_name ? u->display_name : "";
        e.badgeUrl = u->token ? u->token : "";   // the session token, for silent re-login
        e.points = (int) u->score;
    }
    self.pushEvent(e);
}

void Cheevos::loadCallback(int result, const char* error_message, rc_client_t* client, void* userdata) {
    auto& self = getInstance();
    CheevosEvent e{};
    e.type = CHEEVOS_EVENT_GAME_LOADED;
    e.result = result;
    e.description = error_message ? error_message : "";
    const rc_client_game_t* g = rc_client_get_game_info(client);
    if (result == RC_OK && g != nullptr) {
        e.title = g->title ? g->title : "";
        e.points = (int) g->id;
        e.badgeUrl = g->badge_url ? g->badge_url : "";
        self.initMemory(g->console_id);
    }
    self.pushEvent(e);
}

void Cheevos::logThunk(const char* message, const rc_client_t* client) {
    LOGI("rcheevos: %s", message ? message : "");
}

// ------------------------------------------------------------------- JSON

std::string Cheevos::summaryJson() {
    std::ostringstream o;
    o << "{";
    const rc_client_user_t* u = client ? rc_client_get_user_info(client) : nullptr;
    o << "\"loggedIn\":" << (u ? "true" : "false");
    if (u) o << ",\"user\":\"" << jsonEscape(u->display_name) << "\",\"score\":" << u->score << ",\"scoreSoftcore\":" << u->score_softcore;
    o << ",\"hardcore\":" << (isHardcore() ? "true" : "false");
    const rc_client_game_t* g = (client && rc_client_is_game_loaded(client)) ? rc_client_get_game_info(client) : nullptr;
    o << ",\"gameLoaded\":" << (g ? "true" : "false");
    if (g) {
        rc_client_user_game_summary_t s{};
        rc_client_get_user_game_summary(client, &s);
        o << ",\"game\":\"" << jsonEscape(g->title) << "\",\"gameId\":" << g->id
          << ",\"unlocked\":" << s.num_unlocked_achievements << ",\"total\":" << s.num_core_achievements
          << ",\"pointsUnlocked\":" << s.points_unlocked << ",\"pointsTotal\":" << s.points_core
          << ",\"unsupported\":" << s.num_unsupported_achievements;
    }
    if (!lastLoadError.empty()) o << ",\"loadError\":\"" << jsonEscape(lastLoadError.c_str()) << "\"";
    o << "}";
    return o.str();
}

std::string Cheevos::achievementsJson() {
    std::ostringstream o;
    o << "[";
    if (client && rc_client_is_game_loaded(client)) {
        rc_client_achievement_list_t* list = rc_client_create_achievement_list(client,
            RC_CLIENT_ACHIEVEMENT_CATEGORY_CORE, RC_CLIENT_ACHIEVEMENT_LIST_GROUPING_LOCK_STATE);
        bool first = true;
        for (uint32_t b = 0; list && b < list->num_buckets; b++) {
            const rc_client_achievement_bucket_t& bucket = list->buckets[b];
            for (uint32_t i = 0; i < bucket.num_achievements; i++) {
                const rc_client_achievement_t* a = bucket.achievements[i];
                if (!first) o << ","; first = false;
                o << "{\"id\":" << a->id << ",\"title\":\"" << jsonEscape(a->title) << "\",\"description\":\"" << jsonEscape(a->description)
                  << "\",\"points\":" << a->points << ",\"unlocked\":" << (a->unlocked ? "true" : "false")
                  << ",\"state\":" << (int) a->state << ",\"bucket\":\"" << jsonEscape(bucket.label) << "\""
                  << ",\"progress\":" << a->measured_percent << ",\"badge\":\"" << jsonEscape(a->badge_url) << "\"}";
            }
        }
        if (list) rc_client_destroy_achievement_list(list);
    }
    o << "]";
    return o.str();
}

} // namespace libretrodroid
