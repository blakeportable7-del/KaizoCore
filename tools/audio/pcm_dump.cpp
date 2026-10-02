// Runs a libretro core headless with no input and writes the sound it makes, untouched, to a file:
// the input KaizoCore's resampler gets. Usage: pcm_dump <core.so> <rom> <frames> <out.raw> [lowpass]
#include <dlfcn.h>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <vector>
#include "libretro.h"

static std::vector<int16_t> pcm;
static const char *lowpass = nullptr;

static bool env(unsigned cmd, void *data) {
    switch (cmd) {
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
