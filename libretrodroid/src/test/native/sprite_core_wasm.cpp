/*
 * The WebAssembly build of sprite_core.h that the unit tests drive.
 *
 * The real thing runs inside the emulator's video callback, where nothing can
 * be tested; this wraps the SAME header (no copy, no #ifdef) around fake game
 * memory so SpriteCoreWasmTest can run scenarios through it under Node:
 *
 *   clang++ --target=wasm32 -ffreestanding -nostdlib ... -> ld.lld -flavor wasm
 *
 * Nothing here is shipped. The exports are plain C, numbers and pointers into the
 * module's own memory, so sprite_core_runner.js needs no glue.
 */
#include <stdint.h>

#include "../../main/cpp/sprite_core.h"

using namespace spriteisme;

// A freestanding module still gets memcpy and memset calls from the compiler.
extern "C" {
void* memcpy(void* d, const void* s, unsigned long n) {
    uint8_t* dd = (uint8_t*)d;
    const uint8_t* ss = (const uint8_t*)s;
    for (unsigned long i = 0; i < n; i++) dd[i] = ss[i];
    return d;
}
void* memset(void* d, int v, unsigned long n) {
    uint8_t* dd = (uint8_t*)d;
    for (unsigned long i = 0; i < n; i++) dd[i] = (uint8_t)v;
    return d;
}
void* memmove(void* d, const void* s, unsigned long n) {
    uint8_t* dd = (uint8_t*)d;
    const uint8_t* ss = (const uint8_t*)s;
    if (dd < ss) { for (unsigned long i = 0; i < n; i++) dd[i] = ss[i]; }
    else { for (unsigned long i = n; i > 0; i--) dd[i - 1] = ss[i - 1]; }
    return d;
}
}

static uint8_t g_iwram[0x8000];
static uint8_t g_ewram[0x40000];
static uint8_t g_pltt[0x400];
static uint8_t g_frame[240 * 160 * 4 + 4096];
static uint8_t g_sprite[kMaxSpriteDim * kMaxSpriteDim * 4];
static uint8_t g_scratch[4096];

struct HarnessMem {
    int writes = 0;
    int reads = 0;
    bool plttReadable = true;
    bool ewramReadable = true;

    bool region(uint32_t addr, uint32_t len, uint8_t** out) {
        if (addr >= 0x02000000u && addr + len <= 0x02000000u + sizeof(g_ewram) && addr + len >= addr) {
            if (!ewramReadable) return false;
            *out = g_ewram + (addr - 0x02000000u);
            return true;
        }
        if (addr >= 0x03000000u && addr + len <= 0x03000000u + sizeof(g_iwram) && addr + len >= addr) {
            *out = g_iwram + (addr - 0x03000000u);
            return true;
        }
        if (addr >= 0x05000000u && addr + len <= 0x05000000u + sizeof(g_pltt) && addr + len >= addr) {
            if (!plttReadable) return false;
            *out = g_pltt + (addr - 0x05000000u);
            return true;
        }
        return false;
    }
    bool read(uint32_t addr, void* out, uint32_t len) {
        uint8_t* p;
        if (!region(addr, len, &p)) return false;
        reads++;
        for (uint32_t i = 0; i < len; i++) ((uint8_t*)out)[i] = p[i];
        return true;
    }
    bool write(uint32_t addr, const void* in, uint32_t len) {
        uint8_t* p;
        if (!region(addr, len, &p)) return false;
        writes++;
        for (uint32_t i = 0; i < len; i++) p[i] = ((const uint8_t*)in)[i];
        return true;
    }
};

static HarnessMem g_mem;
static Overlay g_overlay;
static SpriteView g_view;
static int32_t g_lastDrew = 0;

extern "C" {

// ---- buffers the runner reads and writes
__attribute__((used)) uint8_t* h_buf(int kind) {
    switch (kind) {
        case 0: return g_iwram;
        case 1: return g_ewram;
        case 2: return g_pltt;
        case 3: return g_frame;
        case 4: return g_sprite;
        default: return g_scratch;
    }
}
__attribute__((used)) int h_size(int kind) {
    switch (kind) {
        case 0: return (int)sizeof(g_iwram);
        case 1: return (int)sizeof(g_ewram);
        case 2: return (int)sizeof(g_pltt);
        case 3: return (int)sizeof(g_frame);
        case 4: return (int)sizeof(g_sprite);
        default: return (int)sizeof(g_scratch);
    }
}

__attribute__((used)) void h_reset() {
    for (unsigned i = 0; i < sizeof(g_iwram); i++) g_iwram[i] = 0;
    for (unsigned i = 0; i < sizeof(g_ewram); i++) g_ewram[i] = 0;
    for (unsigned i = 0; i < sizeof(g_pltt); i++) g_pltt[i] = 0;
    for (unsigned i = 0; i < sizeof(g_frame); i++) g_frame[i] = 0;
    for (unsigned i = 0; i < sizeof(g_sprite); i++) g_sprite[i] = 0;
    g_mem = HarnessMem();
    g_overlay = Overlay();
    g_view = SpriteView{g_sprite, 0, 0, 0, 0};
    g_lastDrew = 0;
}

__attribute__((used)) void h_configure(uint32_t mainAddr, uint32_t oamOff, uint32_t cb2, uint32_t cb2Basic, uint32_t avatar,
                                      uint32_t sprites, uint32_t offX, uint32_t offY, uint32_t unfaded, uint32_t faded,
                                      uint32_t objEvents) {
    Config c{};
    c.mainAddr = mainAddr;
    c.oamBufferOffset = oamOff;
    c.cb2Overworld = cb2;
    c.cb2OverworldBasic = cb2Basic;
    c.playerAvatar = avatar;
    c.sprites = sprites;
    c.coordOffsetX = offX;
    c.coordOffsetY = offY;
    c.plttUnfaded = unfaded;
    c.plttFaded = faded;
    c.objectEvents = objEvents;
    g_overlay.configure(c);
}

/** Whether the native side would accept this table (the check SpriteOverlay::configure makes before it configures). */
__attribute__((used)) int h_plausible(uint32_t mainAddr, uint32_t oamOff, uint32_t cb2, uint32_t cb2Basic, uint32_t avatar,
                                      uint32_t sprites, uint32_t offX, uint32_t offY, uint32_t unfaded, uint32_t faded,
                                      uint32_t objEvents) {
    Config c{};
    c.mainAddr = mainAddr;
    c.oamBufferOffset = oamOff;
    c.cb2Overworld = cb2;
    c.cb2OverworldBasic = cb2Basic;
    c.playerAvatar = avatar;
    c.sprites = sprites;
    c.coordOffsetX = offX;
    c.coordOffsetY = offY;
    c.plttUnfaded = unfaded;
    c.plttFaded = faded;
    c.objectEvents = objEvents;
    return configPlausible(c) ? 1 : 0;
}

__attribute__((used)) void h_sprite(int w, int h, int ox, int oy) { g_view = SpriteView{g_sprite, w, h, ox, oy}; }
__attribute__((used)) void h_plttReadable(int on) { g_mem.plttReadable = on != 0; }
__attribute__((used)) void h_ewramReadable(int on) { g_mem.ewramReadable = on != 0; }

/** One refresh: wanted = on and a picture chosen; haveSprite = keep the pixels; noPicture = a duplicate frame. Returns 1 if drew. */
__attribute__((used)) int h_step(int wanted, int haveSprite, int format, int width, int height, int pitch, int noPicture) {
    if (!frameUsable(width, height, (uint32_t)pitch, format)) {
        // The phone's glue: not a GBA picture, leave everything alone and forget.
        g_overlay.reset();
        return 0;
    }
    bool drew = applyFrame(g_overlay, g_mem, wanted != 0, haveSprite ? &g_view : nullptr, noPicture == 0, [&]() {
        return FrameView{g_frame, width, height, (uint32_t)pitch, format};
    });
    g_lastDrew = drew ? 1 : 0;
    return g_lastDrew;
}

__attribute__((used)) void h_resetOverlay() { g_overlay.reset(); }
__attribute__((used)) int h_pending() { return g_overlay.pending() ? 1 : 0; }
__attribute__((used)) uint32_t h_frames() { return g_overlay.snapshot().frames; }
__attribute__((used)) int h_flags() {
    const Snapshot& s = g_overlay.snapshot();
    return (s.valid ? 1 : 0) | (s.moving ? 2 : 0) | (s.facing << 2);
}
/** The packed snapshot the frontend reads, low and high 32 bits (a number the runner can print). */
__attribute__((used)) uint32_t h_snapshotLo(int wanted) { return (uint32_t)packSnapshot(g_overlay.snapshot(), wanted != 0); }
__attribute__((used)) uint32_t h_snapshotHi(int wanted) { return (uint32_t)(packSnapshot(g_overlay.snapshot(), wanted != 0) >> 32); }
__attribute__((used)) int h_reason() { return g_overlay.debug().reason; }
__attribute__((used)) uint32_t h_debug(int i) {
    const DebugInfo& d = g_overlay.debug();
    switch (i) {
        case 0: return d.callback2;
        case 1: return d.spriteId;
        case 2: return (uint32_t)d.boxX;
        case 3: return (uint32_t)d.boxY;
        case 4: return d.hidden;
        case 5: return (uint32_t)d.fadeCoeff;
        case 6: return d.fadeColor;
        case 7: return d.tileBase;
        case 8: return d.tiles;
        default: return 0;
    }
}
__attribute__((used)) int h_writes() { return g_mem.writes; }
__attribute__((used)) int h_reads() { return g_mem.reads; }
__attribute__((used)) void h_clearCounters() { g_mem.writes = 0; g_mem.reads = 0; }

// ---- the pure pieces, for vector tests
__attribute__((used)) uint32_t h_blend(uint32_t c, int coeff, uint32_t color) { return gameBlend((uint16_t)c, coeff, (uint16_t)color); }
__attribute__((used)) int h_estimate(const uint16_t* unfaded, const uint16_t* shown, int n, int* coeff, uint32_t* color) {
    Fade f{};
    bool ok = estimateFade(unfaded, shown, (uint32_t)n, &f);
    *coeff = f.coeff;
    *color = f.color;
    return ok ? 1 : 0;
}
__attribute__((used)) uint32_t h_tiles(int shape, int size) { return tileCount((uint32_t)shape, (uint32_t)size); }
__attribute__((used)) uint32_t h_hide(uint8_t* oam, uint32_t base, uint32_t count) { return hideTileRange(oam, base, count, nullptr); }
__attribute__((used)) uint32_t h_pack(int format, int r5, int g5, int b5) { return packPixel(format, r5, g5, b5); }
__attribute__((used)) void h_box(int x, int y, int x2, int y2, int vecY, int coordEnabled, int offX, int offY, int* out) {
    computeBox(x, y, x2, y2, vecY, coordEnabled != 0, offX, offY, &out[0], &out[1]);
}
__attribute__((used)) int h_floorDiv16(int v) { return floorDiv16(v); }

/** Draw the sprite buffer into the frame buffer at (dx, dy) with a given fade: the compositor on its own. */
__attribute__((used)) void h_draw(int format, int width, int height, int pitch, int dx, int dy, int coeff, uint32_t color) {
    FrameView f{g_frame, width, height, (uint32_t)pitch, format};
    Fade fade{coeff, (uint16_t)color};
    drawSprite(f, g_view, dx, dy, fade);
}

}  // extern "C"
