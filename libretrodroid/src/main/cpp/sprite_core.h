/*
 * KaizoCore addition: "Play as your Pokemon" (Sprite Is Me), the per-frame logic.
 *
 * This file has no Android, libretro or libc in it, on purpose: it is compiled
 * for the phone by sprite_overlay.cpp AND, unchanged, for WebAssembly by the
 * unit tests (app/src/test/.../SpriteCoreWasmTest.kt runs it under Node), which
 * is how logic that only ever runs inside the emulator's video callback gets
 * tested at all. Keep it that way: no #include but <stdint.h>, no new/delete,
 * no STL, no exceptions.
 *
 * WHAT IT DOES, once per emulated frame, from inside the video-refresh callback
 * (so exactly between two frames, on the emulator's thread):
 *
 *   1. Read the player's object out of the game's RAM (Gen 3 only: FireRed,
 *      LeafGreen, Ruby, Sapphire, Emerald). It acts only while the game's main
 *      callback (gMain.callback2) is the overworld's, and only for a sprite that
 *      is in use and not invisible. The box comes from the game's own
 *      UpdateOamCoords maths, so it follows ledge jumps, the surf bob, the 32x32
 *      bike and scripted camera moves instead of assuming the screen centre.
 *   2. Hide the trainer: set the OBJ-disable bits (attr0 bits 8-9 = 0b10) on
 *      every entry of gMain.oamBuffer whose tile number lies inside the player's
 *      tile block. That takes the subsprite halves and the water reflection (the
 *      reflection sprite copies oam.tileNum from the player's) and nothing else.
 *   3. Draw the replacement into the frame, at the box computed at the PREVIOUS
 *      refresh, faded like the game's own sprites.
 *
 * WHY THE WRITE LANDS ON THE NEXT PICTURE, AND WHY THE DRAW IS ONE FRAME LATE
 *
 * The game's frame N is: the VBlank handler (LoadOam copies gMain.oamBuffer to
 * OAM, TransferPlttBuffer copies gPlttBufferFaded to palette RAM), then the main
 * loop pass (input, callback2 -> OverworldBasic -> AnimateSprites, CameraUpdate,
 * BuildOamBuffer, UpdatePaletteFade), which builds the buffer for the NEXT
 * VBlank, then WaitForVBlank. mGBA ends its retro_run the instant VBlank starts,
 * before the handler has run, and calls the video callback then. So at the
 * callback of refresh k:
 *
 *   - the picture being handed over was drawn from OAM = the buffer built by
 *     pass k-1 (copied at the start of frame k), and palette RAM as of that copy;
 *   - gMain.oamBuffer and the sprite structs hold pass k's result, which nobody
 *     has seen yet and which the VBlank handler will copy right after this call.
 *
 * So a change made to oamBuffer now shows up in picture k+1, and the box this
 * refresh reads describes picture k+1 too. The replacement drawn into picture k
 * must therefore use the box read at refresh k-1. Hide-at-k and draw-at-k+1
 * always come from the same snapshot, which is why the trainer and the
 * replacement are never both on screen, and never both missing. The game
 * rebuilds oamBuffer from scratch every pass, so the write lives for exactly one
 * picture: nothing reaches a save or a save state, and switching the feature off
 * needs no clean-up.
 *
 * Fade: palette RAM as shown in picture k is compared with gPlttBufferUnfaded as
 * read at refresh k-1 (the same pass, so the same palette), and the game's own
 * BlendPalette formula is fitted to the difference (blend colour and 0-16
 * coefficient). The sprite is then run through that same formula, so warps,
 * battle transitions and white flashes take the replacement with them. If palette
 * RAM cannot be read, gPlttBufferFaded as read at refresh k-1 stands in for it
 * (it is what the game copies to palette RAM at the start of frame k).
 *
 * If ANY reading is out of range the step does nothing: no draw and, above all,
 * no write. Memory is never written on a guess.
 */
#ifndef LIBRETRODROID_SPRITE_CORE_H
#define LIBRETRODROID_SPRITE_CORE_H

#include <stdint.h>

namespace spriteisme {

// ---------------------------------------------------------------- constants

constexpr int32_t kScreenW = 240;
constexpr int32_t kScreenH = 160;

// The five Gen 3 games share these layouts; OverworldAddressTest proves each one
// from the ROMs' own code (UpdateOamCoords, LoadOam, GetPlayerFacingDirection...).
constexpr uint32_t kSpriteBytes = 0x44;        // sizeof(struct Sprite)
constexpr uint32_t kMaxSprites = 64;           // MAX_SPRITES
constexpr uint32_t kObjectEventBytes = 0x24;   // sizeof(struct ObjectEvent)
constexpr uint32_t kObjectEventCount = 16;     // OBJECT_EVENTS_COUNT
constexpr uint32_t kOamEntries = 128;          // gMain.oamBuffer[128], 8 bytes each
constexpr uint32_t kOamBytes = 1024;
constexpr int32_t kBoxW = 32;                  // the box the Walking Pals art is drawn against:
constexpr int32_t kBoxH = 32;                  // centred on the sprite, resting on its feet line
constexpr int32_t kMaxSpriteDim = 128;
constexpr uint32_t kFadeColors = 15;           // palette entries 1..15; entry 0 is transparent

constexpr uint32_t kPlttRamObj = 0x05000200u;  // OBJ palettes in palette RAM
constexpr uint32_t kObjPlttBufferOffset = 0x200u;  // gPlttBuffer*[0x100] is the first OBJ palette

// Pixel formats, numbered as libretro numbers them.
enum PixelFormat : int32_t { kFormat0RGB1555 = 0, kFormatXRGB8888 = 1, kFormatRGB565 = 2 };

// Why a step did nothing (the last one is kept for the debug line).
enum Reason : int32_t {
    kOk = 0,
    kOff = 1,            // switched off, or no picture chosen yet
    kNoConfig = 2,
    kNoMemory = 3,       // a read failed
    kNotOverworld = 4,   // gMain.callback2 is not the overworld's
    kBadSpriteId = 5,    // gPlayerAvatar.spriteId out of range
    kSpriteGone = 6,     // sprite not in use, or invisible
    kBadSprite = 7,      // centre-to-corner vector, shape or size out of range
    kFarOffScreen = 8,   // the box is nowhere near the screen
    kBadFrame = 9,       // not a 240x160 software frame
};

// Everything the phone side must tell the core about one game. Addresses are
// Gen 3 addresses (0x02 EWRAM, 0x03 IWRAM); function pointers keep their Thumb bit.
struct Config {
    uint32_t mainAddr;         // gMain
    uint32_t oamBufferOffset;  // gMain.oamBuffer: 0x38, or 0x3C in Ruby and Sapphire
    uint32_t cb2Overworld;     // CB2_Overworld | 1
    uint32_t cb2OverworldBasic;// CB2_OverworldBasic | 1
    uint32_t playerAvatar;     // gPlayerAvatar
    uint32_t sprites;          // gSprites
    uint32_t coordOffsetX;     // gSpriteCoordOffsetX (s16)
    uint32_t coordOffsetY;     // gSpriteCoordOffsetY (s16)
    uint32_t plttUnfaded;      // gPlttBufferUnfaded
    uint32_t plttFaded;        // gPlttBufferFaded
    uint32_t objectEvents;     // gObjectEvents
};

constexpr uint32_t kConfigWords = 11;

/**
 * True when [c] can be a Gen 3 game's table: every address where that address can be in a Game Boy Advance, with room for
 * what is read there. A wrong table is refused here instead of becoming a stray write. SpriteOverlay::configure (the JNI
 * side) asks this before it accepts a table, and the WebAssembly build tests it with every table the app has.
 */
inline bool configPlausible(const Config& c) {
    auto in = [](uint32_t v, uint32_t lo, uint32_t hi) { return v >= lo && v <= hi; };
    return in(c.mainAddr, 0x03000000u, 0x03007000u)
        && in(c.oamBufferOffset, 0x30u, 0x40u)
        && in(c.cb2Overworld, 0x08000001u, 0x09FFFFFFu) && (c.cb2Overworld & 1u)
        && in(c.cb2OverworldBasic, 0x08000001u, 0x09FFFFFFu) && (c.cb2OverworldBasic & 1u)
        && in(c.playerAvatar, 0x02000000u, 0x0203FFE0u)
        && in(c.sprites, 0x02000000u, 0x0203FFFFu - kMaxSprites * kSpriteBytes)
        && (in(c.coordOffsetX, 0x02000000u, 0x0203FFFCu) || in(c.coordOffsetX, 0x03000000u, 0x03007FFCu))
        && (in(c.coordOffsetY, 0x02000000u, 0x0203FFFCu) || in(c.coordOffsetY, 0x03000000u, 0x03007FFCu))
        && in(c.plttUnfaded, 0x02000000u, 0x0203FBFFu)
        && in(c.plttFaded, 0x02000000u, 0x0203FBFFu)
        && (in(c.objectEvents, 0x02000000u, 0x0203FDBFu) || in(c.objectEvents, 0x03000000u, 0x03007DBFu));
}

// -------------------------------------------------------------- pure helpers

inline uint16_t le16(const uint8_t* p) { return (uint16_t)(p[0] | (p[1] << 8)); }
inline int16_t leS16(const uint8_t* p) { return (int16_t)le16(p); }
inline uint32_t le32(const uint8_t* p) {
    return (uint32_t)p[0] | ((uint32_t)p[1] << 8) | ((uint32_t)p[2] << 16) | ((uint32_t)p[3] << 24);
}

/** x >> 4 rounding toward minus infinity, the game's `>> 4` on a negative int. */
inline int32_t floorDiv16(int32_t v) { return v >= 0 ? (v >> 4) : -((-v + 15) >> 4); }

/** Tiles in a sprite of this OAM shape (attr0 bits 14-15) and size (attr1 bits 14-15); 0 = not a real shape. */
inline uint32_t tileCount(uint32_t shape, uint32_t size) {
    static const uint8_t kTiles[4][4] = {
        {1, 4, 16, 64},   // square:     8x8 16x16 32x32 64x64
        {2, 4, 8, 32},    // horizontal: 16x8 32x8 32x16 64x32
        {2, 4, 8, 32},    // vertical:   8x16 8x32 16x32 32x64
        {0, 0, 0, 0},     // prohibited
    };
    return kTiles[shape & 3][size & 3];
}

/** One 15-bit colour through the game's BlendPalette: c + (((blend - c) * coeff) >> 4), per channel. */
inline uint16_t gameBlend(uint16_t c, int32_t coeff, uint16_t blend) {
    uint32_t out = 0;
    for (int ch = 0; ch < 3; ch++) {
        int32_t v = (c >> (5 * ch)) & 31;
        int32_t b = (blend >> (5 * ch)) & 31;
        v = v + floorDiv16((b - v) * coeff);
        if (v < 0) v = 0;
        if (v > 31) v = 31;
        out |= (uint32_t)v << (5 * ch);
    }
    return (uint16_t)out;
}

struct Fade {
    int32_t coeff;    // 0 = no fade
    uint16_t color;   // 15-bit blend colour
};

/**
 * Fit the game's fade to what happened to a palette: [unfaded] is the palette the
 * game holds, [shown] what came out. Tries every coefficient 1-16 and, per channel,
 * every blend value 0-31, keeps the least squared error. Returns false (and no fade)
 * when nothing fits well, so a palette that changed for another reason is not
 * mistaken for a fade. Identical palettes are coefficient 0, at no cost.
 */
inline bool estimateFade(const uint16_t* unfaded, const uint16_t* shown, uint32_t n, Fade* out) {
    out->coeff = 0;
    out->color = 0;
    bool same = true;
    for (uint32_t i = 0; i < n; i++)
        if (unfaded[i] != shown[i]) { same = false; break; }
    if (same || n == 0) return true;

    int32_t best = 0x7FFFFFFF;
    int32_t bestCoeff = 0;
    uint16_t bestColor = 0;
    for (int32_t coeff = 1; coeff <= 16; coeff++) {
        int32_t total = 0;
        uint32_t color = 0;
        for (int ch = 0; ch < 3; ch++) {
            int32_t bestErr = 0x7FFFFFFF;
            int32_t bestC = 0;
            for (int32_t c = 0; c < 32; c++) {
                int32_t err = 0;
                for (uint32_t i = 0; i < n; i++) {
                    int32_t u = (unfaded[i] >> (5 * ch)) & 31;
                    int32_t d = (shown[i] >> (5 * ch)) & 31;
                    int32_t p = u + floorDiv16((c - u) * coeff);
                    int32_t e = d - p;
                    err += e * e;
                }
                if (err < bestErr) { bestErr = err; bestC = c; }
            }
            total += bestErr;
            color |= (uint32_t)bestC << (5 * ch);
        }
        if (total < best) { best = total; bestCoeff = coeff; bestColor = (uint16_t)color; }
    }
    // Mean squared error per channel sample of 8 or less: a clean fade fits at 0, a gamma
    // shift or a rounding disagreement a little above it; a different palette does not fit.
    if (best > (int32_t)(n * 3 * 8)) return false;
    out->coeff = bestCoeff;
    out->color = bestColor;
    return true;
}

inline int32_t expand5(int32_t v) { return (v << 3) | (v >> 2); }

/** 5-bit channels to the frame's pixel format. */
inline uint32_t packPixel(int32_t format, int32_t r5, int32_t g5, int32_t b5) {
    switch (format) {
        case kFormatXRGB8888: return ((uint32_t)expand5(r5) << 16) | ((uint32_t)expand5(g5) << 8) | (uint32_t)expand5(b5);
        case kFormat0RGB1555: return ((uint32_t)r5 << 10) | ((uint32_t)g5 << 5) | (uint32_t)b5;
        default: return ((uint32_t)r5 << 11) | ((uint32_t)((g5 << 1) | (g5 >> 4)) << 5) | (uint32_t)b5;   // RGB565
    }
}

/** A frame pixel to 8-bit channels. */
inline void unpackPixel(int32_t format, uint32_t px, int32_t* r8, int32_t* g8, int32_t* b8) {
    switch (format) {
        case kFormatXRGB8888:
            *r8 = (px >> 16) & 255; *g8 = (px >> 8) & 255; *b8 = px & 255;
            break;
        case kFormat0RGB1555:
            *r8 = expand5((px >> 10) & 31); *g8 = expand5((px >> 5) & 31); *b8 = expand5(px & 31);
            break;
        default: {
            int32_t r5 = (px >> 11) & 31, g6 = (px >> 5) & 63, b5 = px & 31;
            *r8 = expand5(r5); *g8 = (g6 << 2) | (g6 >> 4); *b8 = expand5(b5);
        }
    }
}

/** 8-bit channels back into the frame's format, truncating (as a converter would). */
inline uint32_t packPixel8(int32_t format, int32_t r8, int32_t g8, int32_t b8) {
    switch (format) {
        case kFormatXRGB8888: return ((uint32_t)r8 << 16) | ((uint32_t)g8 << 8) | (uint32_t)b8;
        case kFormat0RGB1555: return ((uint32_t)(r8 >> 3) << 10) | ((uint32_t)(g8 >> 3) << 5) | (uint32_t)(b8 >> 3);
        default: return ((uint32_t)(r8 >> 3) << 11) | ((uint32_t)(g8 >> 2) << 5) | (uint32_t)(b8 >> 3);
    }
}

// ------------------------------------------------------------ the picture data

/** The replacement, RGBA bytes (not premultiplied), drawn at the box corner plus (ox, oy). */
struct SpriteView {
    const uint8_t* rgba;
    int32_t w, h;
    int32_t ox, oy;
};

/** The game's picture: 240x160, the given format, rows [pitch] bytes apart. */
struct FrameView {
    uint8_t* px;
    int32_t width, height;
    uint32_t pitch;
    int32_t format;
};

inline uint32_t bytesPerPixel(int32_t format) { return format == kFormatXRGB8888 ? 4u : 2u; }

inline uint32_t readPixel(const FrameView& f, int32_t x, int32_t y) {
    const uint8_t* p = f.px + (uint32_t)y * f.pitch + (uint32_t)x * bytesPerPixel(f.format);
    return bytesPerPixel(f.format) == 4 ? le32(p) : le16(p);
}

inline void writePixel(const FrameView& f, int32_t x, int32_t y, uint32_t v) {
    uint8_t* p = f.px + (uint32_t)y * f.pitch + (uint32_t)x * bytesPerPixel(f.format);
    p[0] = (uint8_t)v;
    p[1] = (uint8_t)(v >> 8);
    if (bytesPerPixel(f.format) == 4) { p[2] = (uint8_t)(v >> 16); p[3] = 0; }
}

/**
 * Draw [s] with its top-left at (dx, dy), clipped to the frame. Each colour is cut to
 * the GBA's 15 bits and run through the game's own fade [fade], so it looks like a
 * sprite the game drew; a partly transparent pixel is mixed with what is there.
 */
inline void drawSprite(const FrameView& f, const SpriteView& s, int32_t dx, int32_t dy, const Fade& fade) {
    uint8_t lut[3][32];
    for (int ch = 0; ch < 3; ch++) {
        int32_t blend = (fade.color >> (5 * ch)) & 31;
        for (int32_t q = 0; q < 32; q++) {
            int32_t v = q + floorDiv16((blend - q) * fade.coeff);
            lut[ch][q] = (uint8_t)(v < 0 ? 0 : (v > 31 ? 31 : v));
        }
    }
    for (int32_t y = 0; y < s.h; y++) {
        int32_t py = dy + y;
        if (py < 0 || py >= f.height) continue;
        for (int32_t x = 0; x < s.w; x++) {
            int32_t px = dx + x;
            if (px < 0 || px >= f.width) continue;
            const uint8_t* c = s.rgba + ((uint32_t)y * (uint32_t)s.w + (uint32_t)x) * 4u;
            int32_t a = c[3];
            if (a == 0) continue;
            int32_t r5 = lut[0][c[0] >> 3], g5 = lut[1][c[1] >> 3], b5 = lut[2][c[2] >> 3];
            if (a == 255) {
                writePixel(f, px, py, packPixel(f.format, r5, g5, b5));
            } else {
                int32_t dr, dg, db;
                unpackPixel(f.format, readPixel(f, px, py), &dr, &dg, &db);
                int32_t sr = expand5(r5), sg = expand5(g5), sb = expand5(b5);
                int32_t r = (sr * a + dr * (255 - a) + 127) / 255;
                int32_t g = (sg * a + dg * (255 - a) + 127) / 255;
                int32_t b = (sb * a + db * (255 - a) + 127) / 255;
                writePixel(f, px, py, packPixel8(f.format, r, g, b));
            }
        }
    }
}

// ------------------------------------------------------------------ the OAM hide

/**
 * Set the OBJ-disable bits on every live entry of the 128-entry OAM copy [oam] (1024
 * bytes, little-endian) whose tile number is in [base, base + count). Only attr0's
 * bits 8-9 are touched, and they are set to 0b10 whatever they were: the reflection
 * sprite is an AFFINE entry (bit 8 set), and OR-ing bit 9 alone would make it a
 * double-size affine sprite instead of hiding it. Entries already disabled, and the
 * game's dummy entry (y = 160, everything else zero), are left byte for byte alone.
 * Returns how many entries changed; when [changed] is not null it is set to a bit per
 * entry (bit i of word i / 32).
 */
inline uint32_t hideTileRange(uint8_t* oam, uint32_t base, uint32_t count, uint32_t* changed) {
    uint32_t n = 0;
    if (changed) for (uint32_t i = 0; i < kOamEntries / 32; i++) changed[i] = 0;
    for (uint32_t i = 0; i < kOamEntries; i++) {
        uint8_t* e = oam + i * 8u;
        uint32_t attr0 = le16(e);
        uint32_t tile = le16(e + 4) & 0x3FFu;
        if (((attr0 >> 8) & 3u) == 2u) continue;                 // already disabled
        if (attr0 == 0x00A0u && le16(e + 2) == 0 && le16(e + 4) == 0) continue;   // gDummyOamData
        if (tile < base || tile >= base + count) continue;
        attr0 = (attr0 & ~0x0300u) | 0x0200u;
        e[0] = (uint8_t)attr0;
        e[1] = (uint8_t)(attr0 >> 8);
        if (changed) changed[i / 32] |= 1u << (i % 32);
        n++;
    }
    return n;
}

// ----------------------------------------------------------------- the player

struct Player {
    bool valid;
    int32_t boxX, boxY;       // top-left of the 32x32 box, in screen pixels
    int32_t facing;           // DIR_SOUTH 1, NORTH 2, WEST 3, EAST 4, 0 = unknown
    bool moving;
    uint32_t tileBase, tiles; // the player sprite's tile block
    uint32_t palSlot;         // its OBJ palette
    uint16_t unfaded[kFadeColors];
    uint16_t faded[kFadeColors];
    bool havePalette;
};

/** What the box is, from the sprite's own fields: the game's UpdateOamCoords, then the feet line. */
inline void computeBox(int32_t x, int32_t y, int32_t x2, int32_t y2, int32_t vecY,
                       bool coordOffsetEnabled, int32_t offX, int32_t offY, int32_t* boxX, int32_t* boxY) {
    if (!coordOffsetEnabled) { offX = 0; offY = 0; }
    int32_t cx = x + x2 + offX;
    int32_t feet = y + y2 + offY - vecY;   // vecY is negative: the sprite's bottom edge
    *boxX = cx - kBoxW / 2;
    *boxY = feet - kBoxH;
}

/** What a step decided: draw [fade]-faded at (x, y) if [draw]. */
struct Plan {
    bool draw;
    int32_t x, y;
    Fade fade;
};

struct Snapshot {
    uint32_t frames;    // steps taken while switched on, since configure
    bool valid;         // the trainer was replaced this step
    bool moving;
    int32_t facing;
};

/**
 * The snapshot the frontend reads back, in one 64-bit value: bits 0-31 the frames run since the switch went on,
 * bit 32 the trainer was replaced this frame, bit 33 stepping, bits 34-36 facing (1 down, 2 up, 3 left, 4 right,
 * 0 unknown), bit 37 [wanted] (switched on with a picture chosen). SpriteSnapshot.kt unpacks it.
 */
inline uint64_t packSnapshot(const Snapshot& s, bool wanted) {
    return (uint64_t)s.frames | ((uint64_t)(s.valid ? 1 : 0) << 32) | ((uint64_t)(s.moving ? 1 : 0) << 33) |
           ((uint64_t)(s.facing & 7) << 34) | ((uint64_t)(wanted ? 1 : 0) << 37);
}

struct DebugInfo {
    int32_t reason;
    uint32_t callback2;
    uint32_t spriteId;
    int32_t boxX, boxY;
    uint32_t hidden;    // entries hidden this step
    int32_t fadeCoeff;
    uint32_t fadeColor;
    uint32_t tileBase, tiles;
};

class Overlay {
public:
    Config cfg{};
    bool configured = false;

    void configure(const Config& c) {
        cfg = c;
        configured = true;
        reset();
    }

    void reset() {
        prev_.valid = false;
        frames_ = 0;
        snap_ = Snapshot{0, false, false, 0};
        dbg_ = DebugInfo{};
    }

    const Snapshot& snapshot() const { return snap_; }
    const DebugInfo& debug() const { return dbg_; }
    bool pending() const { return prev_.valid; }

    /**
     * One step, at one video refresh. [active] is "switched on and a picture chosen".
     * Always reads and (when it may) hides; returns what to draw into THIS frame,
     * which is decided by the previous step's snapshot. When [active] is false but
     * the previous step hid the trainer, the frame still gets its replacement once,
     * so switching off does not blink.
     */
    template <class Mem>
    Plan step(Mem& mem, bool active) {
        Plan plan{};
        plan.draw = false;

        // 1. This picture: the replacement, at the previous refresh's box.
        if (prev_.valid) {
            plan.draw = true;
            plan.x = prev_.boxX;
            plan.y = prev_.boxY;
            plan.fade = fadeFor(mem, prev_);
        }

        // 2. The next picture: read the player, hide the trainer.
        Player now{};
        now.valid = false;
        dbg_.reason = kOff;
        dbg_.hidden = 0;
        if (active) {
            frames_++;
            if (!configured) dbg_.reason = kNoConfig;
            else readPlayer(mem, &now);
            if (now.valid) hide(mem, now);
        }
        snap_.frames = frames_;
        snap_.valid = now.valid;
        snap_.moving = now.valid && now.moving;
        snap_.facing = now.valid ? now.facing : 0;
        dbg_.boxX = now.boxX;
        dbg_.boxY = now.boxY;
        dbg_.tileBase = now.tileBase;
        dbg_.tiles = now.tiles;
        dbg_.fadeCoeff = plan.draw ? plan.fade.coeff : 0;
        dbg_.fadeColor = plan.draw ? plan.fade.color : 0;

        prev_ = now;
        return plan;
    }

private:
    Player prev_{};
    uint32_t frames_ = 0;
    Snapshot snap_{};
    DebugInfo dbg_{};

    template <class Mem>
    Fade fadeFor(Mem& mem, const Player& p) {
        Fade none{0, 0};
        if (!p.havePalette) return none;
        uint16_t shown[kFadeColors];
        bool haveShown = false;
        uint8_t buf[kFadeColors * 2];
        // Palette RAM as it was in this picture, else what the game copies there at the start of the frame.
        if (mem.read(kPlttRamObj + p.palSlot * 32u + 2u, buf, kFadeColors * 2u)) {
            for (uint32_t i = 0; i < kFadeColors; i++) shown[i] = le16(buf + i * 2u);
            haveShown = true;
        }
        if (!haveShown) for (uint32_t i = 0; i < kFadeColors; i++) shown[i] = p.faded[i];
        Fade f{0, 0};
        if (!estimateFade(p.unfaded, shown, kFadeColors, &f)) return none;
        return f;
    }

    template <class Mem>
    void readPlayer(Mem& mem, Player* p) {
        uint8_t w[4];
        if (!mem.read(cfg.mainAddr + 4u, w, 4)) { dbg_.reason = kNoMemory; return; }
        uint32_t cb2 = le32(w);
        dbg_.callback2 = cb2;
        if (cb2 != cfg.cb2Overworld && cb2 != cfg.cb2OverworldBasic) { dbg_.reason = kNotOverworld; return; }

        uint8_t av[8];
        if (!mem.read(cfg.playerAvatar, av, 8)) { dbg_.reason = kNoMemory; return; }
        uint32_t spriteId = av[4];
        uint32_t objectEventId = av[5];
        dbg_.spriteId = spriteId;
        if (spriteId >= kMaxSprites) { dbg_.reason = kBadSpriteId; return; }

        uint8_t sp[kSpriteBytes];
        if (!mem.read(cfg.sprites + spriteId * kSpriteBytes, sp, kSpriteBytes)) { dbg_.reason = kNoMemory; return; }
        uint32_t flags = sp[0x3E];
        bool inUse = (flags & 1u) != 0;
        bool coordOffsetEnabled = (flags & 2u) != 0;
        bool invisible = (flags & 4u) != 0;
        if (!inUse || invisible) { dbg_.reason = kSpriteGone; return; }

        uint32_t attr0 = le16(sp), attr1 = le16(sp + 2), attr2 = le16(sp + 4);
        uint32_t tiles = tileCount(attr0 >> 14, attr1 >> 14);
        int32_t vecX = (int8_t)sp[0x28], vecY = (int8_t)sp[0x29];
        if (tiles == 0 || vecX > 0 || vecX < -64 || vecY >= 0 || vecY < -64) { dbg_.reason = kBadSprite; return; }

        int32_t offX = 0, offY = 0;
        if (coordOffsetEnabled) {
            uint8_t o[2];
            if (!mem.read(cfg.coordOffsetX, o, 2)) { dbg_.reason = kNoMemory; return; }
            offX = leS16(o);
            if (!mem.read(cfg.coordOffsetY, o, 2)) { dbg_.reason = kNoMemory; return; }
            offY = leS16(o);
        }
        int32_t boxX, boxY;
        computeBox(leS16(sp + 0x20), leS16(sp + 0x22), leS16(sp + 0x24), leS16(sp + 0x26), vecY,
                   coordOffsetEnabled, offX, offY, &boxX, &boxY);
        if (boxX < -128 || boxX > kScreenW + 128 || boxY < -128 || boxY > kScreenH + 128) { dbg_.reason = kFarOffScreen; return; }

        p->boxX = boxX;
        p->boxY = boxY;
        p->tileBase = attr2 & 0x3FFu;
        p->tiles = tiles;
        p->palSlot = (attr2 >> 12) & 15u;

        // Facing and stepping come from the game itself, not from the D-pad.
        p->facing = 0;
        p->moving = av[2] == 2 || av[3] == 1;   // runningState == MOVING, or tileTransitionState == T_TILE_TRANSITION
        if (objectEventId < kObjectEventCount) {
            uint8_t oe[1];
            if (mem.read(cfg.objectEvents + objectEventId * kObjectEventBytes + 0x18u, oe, 1)) {
                uint32_t dir = oe[0] & 15u;
                if (dir >= 1 && dir <= 4) p->facing = (int32_t)dir;
            }
        }

        // The palette, for the fade: as the game holds it now, and as it is about to copy it.
        p->havePalette = false;
        uint8_t un[kFadeColors * 2], fa[kFadeColors * 2];
        uint32_t off = kObjPlttBufferOffset + p->palSlot * 32u + 2u;
        if (mem.read(cfg.plttUnfaded + off, un, kFadeColors * 2u) && mem.read(cfg.plttFaded + off, fa, kFadeColors * 2u)) {
            for (uint32_t i = 0; i < kFadeColors; i++) {
                p->unfaded[i] = le16(un + i * 2u);
                p->faded[i] = le16(fa + i * 2u);
            }
            p->havePalette = true;
        }

        p->valid = true;
        dbg_.reason = kOk;
    }

    template <class Mem>
    void hide(Mem& mem, const Player& p) {
        uint8_t oam[kOamBytes];
        uint32_t base = cfg.mainAddr + cfg.oamBufferOffset;
        if (!mem.read(base, oam, kOamBytes)) return;
        uint32_t changed[kOamEntries / 32];
        uint32_t n = hideTileRange(oam, p.tileBase, p.tiles, changed);
        dbg_.hidden = n;
        if (n == 0) return;
        for (uint32_t i = 0; i < kOamEntries; i++) {
            if (!(changed[i / 32] & (1u << (i % 32)))) continue;
            mem.write(base + i * 8u, oam + i * 8u, 2);   // attr0 only
        }
    }
};

/**
 * True for a picture this can draw into: a 240x160 frame in a known format whose rows
 * are at least a row wide. mGBA hands over exactly that; anything else (a hardware
 * frame, another console's frame size, a bad pitch) is left entirely alone.
 */
inline bool frameUsable(int32_t width, int32_t height, uint32_t pitch, int32_t format) {
    if (width != kScreenW || height != kScreenH) return false;
    if (format != kFormat0RGB1555 && format != kFormatXRGB8888 && format != kFormatRGB565) return false;
    return pitch >= (uint32_t)width * bytesPerPixel(format);
}

/**
 * The whole per-refresh job, shared by the phone and the tests.
 *
 * [wanted] is "switched on and a picture is chosen". [sprite] is the picture's pixels, kept
 * (and passed) for one refresh after [wanted] turns false so the last replacement is still
 * drawn once; it may be null. [canDraw] is false when the core sent no picture this time
 * (a duplicate frame): the game still moved on, so the step still runs, but nothing is drawn.
 * [prepare] gives the frame to draw into, and is called only when there is something to
 * draw: the phone copies the core's buffer to a scratch one there, the core's own buffer is
 * never written. Returns true when the sprite was drawn.
 */
template <class Mem, class Prepare>
bool applyFrame(Overlay& o, Mem& mem, bool wanted, const SpriteView* sprite, bool canDraw, Prepare&& prepare) {
    Plan plan = o.step(mem, wanted);
    if (!plan.draw || !canDraw || sprite == nullptr || sprite->rgba == nullptr) return false;
    FrameView frame = prepare();
    if (frame.px == nullptr) return false;
    drawSprite(frame, *sprite, plan.x + sprite->ox, plan.y + sprite->oy, plan.fade);
    return true;
}

}  // namespace spriteisme

#endif  // LIBRETRODROID_SPRITE_CORE_H
