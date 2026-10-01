/*
 * KaizoCore addition: "Play as your Pokemon", the emulator side. See sprite_overlay.h;
 * the logic itself is sprite_core.h, which the unit tests run under WebAssembly.
 */
#include <jni.h>

#include <cstdio>
#include <cstring>
#include <exception>
#include <vector>

#include "sprite_overlay.h"
#include "environment.h"
#include "libretrodroid.h"
#include "log.h"

namespace libretrodroid {

namespace {

/**
 * The game's memory as the core lays it out, resolved from the core's descriptors on every
 * refresh (a copy of about ten of them), so nothing here can outlive a game that was
 * unloaded. EWRAM falls back to the core's SYSTEM_RAM when no descriptor covers it.
 *
 * Reads and writes go straight to the core's own buffers. That is safe here, and no lock
 * is taken, because the caller is the core's video callback: inside retro_run, on the
 * emulator's thread, with the core lock already held by step().
 */
class CoreMem {
public:
    CoreMem() : descriptors_(Environment::getInstance().getMemoryDescriptors()) {}

    bool read(uint32_t addr, void* out, uint32_t len) {
        const uint8_t* p = find(addr, len, false);
        if (p == nullptr) return false;
        std::memcpy(out, p, len);
        return true;
    }

    bool write(uint32_t addr, const void* in, uint32_t len) {
        uint8_t* p = find(addr, len, true);
        if (p == nullptr) return false;
        std::memcpy(p, in, len);
        return true;
    }

private:
    uint8_t* find(uint32_t addr, uint32_t len, bool forWrite) {
        for (auto& d : descriptors_) {
            if (d.ptr == nullptr) continue;
            if (forWrite && (d.flags & RETRO_MEMDESC_CONST) != 0) continue;
            uint64_t start = d.start;
            uint64_t size = d.len;
            if (addr >= start && static_cast<uint64_t>(addr) + len <= start + size) {
                return static_cast<uint8_t*>(d.ptr) + d.offset + (addr - start);
            }
        }
        if (addr >= 0x02000000u && static_cast<uint64_t>(addr) + len <= 0x02040000u) {
            if (!triedSystemRam_) {
                triedSystemRam_ = true;
                auto& ld = LibretroDroid::getInstance();
                systemRam_ = static_cast<uint8_t*>(ld.coreMemoryData(RETRO_MEMORY_SYSTEM_RAM));
                systemRamSize_ = ld.coreMemorySize(RETRO_MEMORY_SYSTEM_RAM);
            }
            if (systemRam_ != nullptr && (addr - 0x02000000u) + len <= systemRamSize_) {
                return systemRam_ + (addr - 0x02000000u);
            }
        }
        return nullptr;
    }

    std::vector<retro_memory_descriptor> descriptors_;
    bool triedSystemRam_ = false;
    uint8_t* systemRam_ = nullptr;
    size_t systemRamSize_ = 0;
};

}  // namespace

SpriteOverlay& SpriteOverlay::get() {
    static SpriteOverlay instance;
    return instance;
}

bool SpriteOverlay::configure(const int64_t* words, int count) {
    if (words == nullptr || count != static_cast<int>(spriteisme::kConfigWords)) return false;
    spriteisme::Config c{};
    uint32_t v[spriteisme::kConfigWords];
    for (uint32_t i = 0; i < spriteisme::kConfigWords; i++) {
        if (words[i] < 0 || words[i] > 0xFFFFFFFFLL) return false;
        v[i] = static_cast<uint32_t>(words[i]);
    }
    c.mainAddr = v[0];
    c.oamBufferOffset = v[1];
    c.cb2Overworld = v[2];
    c.cb2OverworldBasic = v[3];
    c.playerAvatar = v[4];
    c.sprites = v[5];
    c.coordOffsetX = v[6];
    c.coordOffsetY = v[7];
    c.plttUnfaded = v[8];
    c.plttFaded = v[9];
    c.objectEvents = v[10];

    // Refuse a table that cannot be a Gen 3 game's, so a wrong one fails here and not as a stray write.
    if (!spriteisme::configPlausible(c)) {
        LOGW("Sprite Is Me: config refused");
        return false;
    }
    std::lock_guard<std::mutex> g(lock_);
    core_.configure(c);
    snapshot_.store(0, std::memory_order_relaxed);
    loggedFirst_ = false;
    lastReason_ = -1;
    return true;
}

void SpriteOverlay::setEnabled(bool on) {
    if (on) {
        gSpriteOverlayMode.store(kSpriteOverlayOn, std::memory_order_relaxed);
    } else {
        int expected = kSpriteOverlayOn;
        // Wind down rather than stop: the frame after the last hide still needs its replacement.
        gSpriteOverlayMode.compare_exchange_strong(expected, kSpriteOverlayWindingDown, std::memory_order_relaxed);
    }
}

bool SpriteOverlay::setSprite(const int32_t* argb, int w, int h, int ox, int oy) {
    if (argb == nullptr || w < 1 || h < 1 || w > spriteisme::kMaxSpriteDim || h > spriteisme::kMaxSpriteDim) return false;
    std::lock_guard<std::mutex> g(lock_);
    pixels_.resize(static_cast<size_t>(w) * static_cast<size_t>(h) * 4u);
    for (int i = 0; i < w * h; i++) {
        uint32_t p = static_cast<uint32_t>(argb[i]);
        pixels_[i * 4 + 0] = static_cast<uint8_t>(p >> 16);   // R
        pixels_[i * 4 + 1] = static_cast<uint8_t>(p >> 8);    // G
        pixels_[i * 4 + 2] = static_cast<uint8_t>(p);         // B
        pixels_[i * 4 + 3] = static_cast<uint8_t>(p >> 24);   // A
    }
    w_ = w; h_ = h; ox_ = ox; oy_ = oy;
    pixelsValid_ = true;
    wantSprite_ = true;
    return true;
}

void SpriteOverlay::clearSprite() {
    std::lock_guard<std::mutex> g(lock_);
    wantSprite_ = false;
}

void SpriteOverlay::reset() {
    std::lock_guard<std::mutex> g(lock_);
    core_.reset();
    wantSprite_ = false;
    snapshot_.store(0, std::memory_order_relaxed);
}

int64_t SpriteOverlay::snapshot() {
    return snapshot_.load(std::memory_order_relaxed);
}

void SpriteOverlay::debugLine(char* out, size_t size) {
    std::lock_guard<std::mutex> g(lock_);
    const spriteisme::DebugInfo& d = core_.debug();
    std::snprintf(out, size,
                  "mode=%d reason=%d cb2=%08X sprite=%u box=(%d,%d) tiles=%u+%u hidden=%u fade=%d/%04X frames=%u pending=%d",
                  gSpriteOverlayMode.load(std::memory_order_relaxed), d.reason, d.callback2, d.spriteId, d.boxX, d.boxY,
                  d.tileBase, d.tiles, d.hidden, d.fadeCoeff, d.fadeColor, core_.snapshot().frames, core_.pending() ? 1 : 0);
}

void SpriteOverlay::forget(int mode) {
    core_.reset();
    snapshot_.store(0, std::memory_order_relaxed);
    if (mode == kSpriteOverlayWindingDown) gSpriteOverlayMode.store(kSpriteOverlayIdle, std::memory_order_relaxed);
}

void SpriteOverlay::applyActive(const void*& data, unsigned width, unsigned height, size_t& pitch) {
    std::lock_guard<std::mutex> guard(lock_);
    int mode = gSpriteOverlayMode.load(std::memory_order_relaxed);
    if (mode == kSpriteOverlayIdle) return;
    try {
        Environment& env = Environment::getInstance();
        // Only a software 240x160 frame is a GBA picture this may touch. A frame of any other
        // size, or a hardware one, is left entirely alone, and what was pending is forgotten.
        if (env.isUseHwAcceleration() || width != static_cast<unsigned>(spriteisme::kScreenW)
            || height != static_cast<unsigned>(spriteisme::kScreenH)) {
            forget(mode);
            return;
        }
        int32_t format = env.getPixelFormat();
        bool haveBuffer = data != nullptr && data != reinterpret_cast<const void*>(static_cast<intptr_t>(-1));
        if (haveBuffer && !spriteisme::frameUsable(static_cast<int32_t>(width), static_cast<int32_t>(height),
                                                   static_cast<uint32_t>(pitch), format)) {
            forget(mode);
            return;
        }

        bool wanted = mode == kSpriteOverlayOn && wantSprite_ && pixelsValid_;
        if (!wanted && !core_.pending()) {
            // Switched on with no picture chosen: nothing to read, hide or draw. The game moves on.
            snapshot_.store(static_cast<int64_t>(core_.snapshot().frames), std::memory_order_relaxed);
            if (mode == kSpriteOverlayWindingDown) gSpriteOverlayMode.store(kSpriteOverlayIdle, std::memory_order_relaxed);
            return;
        }

        CoreMem mem;
        spriteisme::SpriteView view{pixels_.data(), w_, h_, ox_, oy_};
        const unsigned char* source = static_cast<const unsigned char*>(data);
        size_t bytes = pitch * height;
        bool drew = spriteisme::applyFrame(core_, mem, wanted, pixelsValid_ ? &view : nullptr, haveBuffer, [&]() {
            if (scratch_.size() < bytes) scratch_.resize(bytes);
            std::memcpy(scratch_.data(), source, bytes);
            return spriteisme::FrameView{scratch_.data(), static_cast<int32_t>(width), static_cast<int32_t>(height),
                                         static_cast<uint32_t>(pitch), format};
        });
        if (drew) data = scratch_.data();

        snapshot_.store(static_cast<int64_t>(spriteisme::packSnapshot(core_.snapshot(), wanted)), std::memory_order_relaxed);

        // One line in the log each time the reason changes, so a phone that shows nothing says why.
        int reason = core_.debug().reason;
        if (reason != lastReason_) {
            lastReason_ = reason;
            char line[256];
            const spriteisme::DebugInfo& d = core_.debug();
            std::snprintf(line, sizeof(line), "reason=%d cb2=%08X sprite=%u box=(%d,%d) tiles=%u+%u", d.reason, d.callback2,
                          d.spriteId, d.boxX, d.boxY, d.tileBase, d.tiles);
            LOGI("Sprite Is Me: %s", line);
        }

        if (mode == kSpriteOverlayWindingDown && !core_.pending()) {
            gSpriteOverlayMode.store(kSpriteOverlayIdle, std::memory_order_relaxed);
        }
    } catch (const std::exception& e) {
        LOGE("Sprite Is Me stopped: %s", e.what());
        gSpriteOverlayMode.store(kSpriteOverlayIdle, std::memory_order_relaxed);
    } catch (...) {
        LOGE("Sprite Is Me stopped");
        gSpriteOverlayMode.store(kSpriteOverlayIdle, std::memory_order_relaxed);
    }
}

}  // namespace libretrodroid

// ------------------------------------------------------------------- JNI

extern "C" {

using libretrodroid::SpriteOverlay;

JNIEXPORT jboolean JNICALL Java_com_swordfish_libretrodroid_SpriteOverlay_configure(JNIEnv* env, jclass, jlongArray words) {
    if (words == nullptr) return JNI_FALSE;
    jsize n = env->GetArrayLength(words);
    std::vector<int64_t> v(static_cast<size_t>(n));
    env->GetLongArrayRegion(words, 0, n, reinterpret_cast<jlong*>(v.data()));
    return SpriteOverlay::get().configure(v.data(), static_cast<int>(n)) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_SpriteOverlay_setEnabled(JNIEnv*, jclass, jboolean on) {
    SpriteOverlay::get().setEnabled(on == JNI_TRUE);
}

JNIEXPORT jboolean JNICALL Java_com_swordfish_libretrodroid_SpriteOverlay_setSprite(
    JNIEnv* env, jclass, jintArray argb, jint w, jint h, jint ox, jint oy) {
    if (argb == nullptr || w < 1 || h < 1 || w > 128 || h > 128) return JNI_FALSE;
    jsize n = env->GetArrayLength(argb);
    if (n < w * h) return JNI_FALSE;
    std::vector<int32_t> v(static_cast<size_t>(w) * static_cast<size_t>(h));
    env->GetIntArrayRegion(argb, 0, w * h, reinterpret_cast<jint*>(v.data()));
    return SpriteOverlay::get().setSprite(v.data(), w, h, ox, oy) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_SpriteOverlay_clearSprite(JNIEnv*, jclass) {
    SpriteOverlay::get().clearSprite();
}

JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_SpriteOverlay_reset(JNIEnv*, jclass) {
    SpriteOverlay::get().reset();
}

JNIEXPORT jlong JNICALL Java_com_swordfish_libretrodroid_SpriteOverlay_snapshot(JNIEnv*, jclass) {
    return static_cast<jlong>(SpriteOverlay::get().snapshot());
}

JNIEXPORT jstring JNICALL Java_com_swordfish_libretrodroid_SpriteOverlay_debug(JNIEnv* env, jclass) {
    char line[320];
    SpriteOverlay::get().debugLine(line, sizeof(line));
    return env->NewStringUTF(line);
}

}  // extern "C"
