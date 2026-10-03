// Runs a libretro core headless with no input and writes the sound it makes, untouched, to a file:
// the input KaizoCore's resampler gets. Usage: pcm_dump <core.so> <rom> <frames> <out.raw> [lowpass]
// Core options: PCM_OPTS="key=value,key=value" in the environment, e.g.
// PCM_OPTS="melonds_audio_bitrate=16-bit,melonds_audio_interpolation=Cubic" (2026-10-02, the DS static).
#include <dlfcn.h>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <map>
#include <string>
#include <vector>
#include "libretro.h"

static std::vector<int16_t> pcm;
static const char *lowpass = nullptr;
static std::map<std::string, std::string> opts;

static void readOpts() {
    const char *e = std::getenv("PCM_OPTS");
    if (!e) return;
    std::string all(e);
    size_t at = 0;
    while (at < all.size()) {
        size_t comma = all.find(',', at);
        std::string kv = all.substr(at, comma == std::string::npos ? std::string::npos : comma - at);
        size_t eq = kv.find('=');
        if (eq != std::string::npos) opts[kv.substr(0, eq)] = kv.substr(eq + 1);
        if (comma == std::string::npos) break;
        at = comma + 1;
    }
}

static void log_cb(enum retro_log_level, const char *fmt, ...) {}
static bool rumble(unsigned, enum retro_rumble_effect, uint16_t) { return true; }

static bool env(unsigned cmd, void *data) {
    switch (cmd) {
        // melonDS reaches for these (2026-10-02): without them it crashed right after loading.
        case RETRO_ENVIRONMENT_GET_LOG_INTERFACE:
            ((retro_log_callback *) data)->log = log_cb;
            return true;
        case RETRO_ENVIRONMENT_GET_RUMBLE_INTERFACE:
            ((retro_rumble_interface *) data)->set_rumble_state = rumble;
            return true;
        case RETRO_ENVIRONMENT_GET_LANGUAGE:
            *(unsigned *) data = 0;
            return true;
        case RETRO_ENVIRONMENT_GET_SYSTEM_DIRECTORY:
        case RETRO_ENVIRONMENT_GET_SAVE_DIRECTORY:
            *(const char **) data = "/data/local/tmp/pcm";
            return true;
        case RETRO_ENVIRONMENT_SET_PIXEL_FORMAT:
            return true;
        case RETRO_ENVIRONMENT_GET_VARIABLE: {
            auto *v = (retro_variable *) data;
            if (lowpass && std::strcmp(v->key, "mgba_audio_low_pass_filter") == 0) { v->value = "enabled"; return true; }
            if (lowpass && std::strcmp(v->key, "mgba_audio_low_pass_range") == 0) { v->value = lowpass; return true; }
            auto o = opts.find(v->key);
            if (o != opts.end()) { v->value = o->second.c_str(); return true; }
            v->value = nullptr;
            return false;
        }
        case RETRO_ENVIRONMENT_GET_CAN_DUPE:
            *(bool *) data = true;
            return true;
        default:
            return false;
    }
}

static void video(const void *, unsigned, unsigned, size_t) {}
static void sample(int16_t l, int16_t r) { pcm.push_back(l); pcm.push_back(r); }
static size_t batch(const int16_t *d, size_t frames) { pcm.insert(pcm.end(), d, d + frames * 2); return frames; }
static void poll() {}
static int16_t input(unsigned, unsigned, unsigned, unsigned) { return 0; }

int main(int argc, char **argv) {
    if (argc < 5) { std::printf("usage: pcm_dump core.so rom frames out.raw [lowpass%%]\n"); return 2; }
    if (argc > 5) lowpass = argv[5];
    readOpts();
    void *h = dlopen(argv[1], RTLD_NOW);
    if (!h) { std::printf("dlopen: %s\n", dlerror()); return 1; }
#define SYM(n) auto p_##n = (decltype(&n)) dlsym(h, #n); if (!p_##n) { std::printf("missing %s\n", #n); return 1; }
    SYM(retro_set_environment) SYM(retro_set_video_refresh) SYM(retro_set_audio_sample)
    SYM(retro_set_audio_sample_batch) SYM(retro_set_input_poll) SYM(retro_set_input_state)
    SYM(retro_init) SYM(retro_load_game) SYM(retro_get_system_av_info) SYM(retro_run)
    SYM(retro_unload_game) SYM(retro_deinit)
    p_retro_set_environment(env);
    p_retro_set_video_refresh(video);
    p_retro_set_audio_sample(sample);
    p_retro_set_audio_sample_batch(batch);
    p_retro_set_input_poll(poll);
    p_retro_set_input_state(input);
    p_retro_init();
    retro_game_info game{argv[2], nullptr, 0, nullptr};
    if (!p_retro_load_game(&game)) { std::printf("load failed\n"); return 1; }
    retro_system_av_info av{};
    p_retro_get_system_av_info(&av);
    const int frames = std::atoi(argv[3]);
    for (int i = 0; i < frames; i++) p_retro_run();
    FILE *f = std::fopen(argv[4], "wb");
    std::fwrite(pcm.data(), sizeof(int16_t), pcm.size(), f);
    std::fclose(f);
    std::printf("rate %.3f fps %.4f frames %zu\n", av.timing.sample_rate, av.timing.fps, pcm.size() / 2);
    p_retro_unload_game();
    p_retro_deinit();
    return 0;
}
