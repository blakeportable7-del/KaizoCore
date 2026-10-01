/*
 * KaizoCore addition: "Play as your Pokemon", the emulator side.
 *
 * The frontend picks a sprite and hands it here as pixels (SpriteOverlay.java); on
 * every video refresh this reads the Gen 3 game's own memory (sprite_core.h holds all
 * of that logic), hides the trainer in the game's OAM shadow buffer and draws the
 * sprite into the picture. Drawing in the game's own 240x160 pixels, before the
 * picture reaches the renderer, is what makes it pixel-exact at every scale, shader,
 * rotation and landscape layout, and makes it part of the frame the Stream kit and
 * OBS capture.
 */
#ifndef LIBRETRODROID_SPRITE_OVERLAY_H
#define LIBRETRODROID_SPRITE_OVERLAY_H

#include <atomic>
#include <cstddef>
#include <cstdint>
#include <mutex>
#include <vector>

#include "sprite_core.h"

namespace libretrodroid {

// Off costs one relaxed load of this and nothing else: no lock, no memory read, no write.
enum SpriteOverlayMode : int { kSpriteOverlayIdle = 0, kSpriteOverlayOn = 1, kSpriteOverlayWindingDown = 2 };
inline std::atomic<int> gSpriteOverlayMode{kSpriteOverlayIdle};

class SpriteOverlay {
public:
    static SpriteOverlay& get();

    /**
     * The one call LibretroDroid::handleVideoRefresh makes, at its very top. [data] and
     * [pitch] are the core's picture; when there is something to draw they come back
     * pointing at a scratch copy with the sprite drawn in, otherwise untouched. The
     * core's own buffer is never written (it keeps last frame's pixels for a skipped
     * frame, and mGBA's frame blending reads them).
     */
    static void apply(const void*& data, unsigned width, unsigned height, size_t& pitch) {
        if (gSpriteOverlayMode.load(std::memory_order_relaxed) == kSpriteOverlayIdle) return;
        get().applyActive(data, width, height, pitch);
    }

    // ---- from the frontend's threads (JNI)
    bool configure(const int64_t* words, int count);
    void setEnabled(bool on);
    bool setSprite(const int32_t* argb, int w, int h, int ox, int oy);
    void clearSprite();
    int64_t snapshot();
    void debugLine(char* out, size_t size);
    void reset();

private:
    SpriteOverlay() = default;
    void applyActive(const void*& data, unsigned width, unsigned height, size_t& pitch);
    void forget(int mode);

    std::mutex lock_;
    spriteisme::Overlay core_;

    // The picture. Pixels are kept after clearSprite() until the next setSprite(), so the
    // step that follows a clear can still draw the last frame it hid the trainer for.
    std::vector<uint8_t> pixels_;
    int w_ = 0, h_ = 0, ox_ = 0, oy_ = 0;
    bool pixelsValid_ = false;
    bool wantSprite_ = false;

    std::vector<uint8_t> scratch_;
    std::atomic<int64_t> snapshot_{0};
    bool loggedFirst_ = false;
    int lastReason_ = -1;
};

}  // namespace libretrodroid

#endif  // LIBRETRODROID_SPRITE_OVERLAY_H
