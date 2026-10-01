package com.swordfish.libretrodroid;

/**
 * KaizoCore addition: "Play as your Pokemon" (Sprite Is Me), the frontend's handle on the
 * emulator side (sprite_overlay.cpp, with its logic in sprite_core.h).
 *
 * The frontend chooses the picture and pushes it here as pixels only when the frame it
 * wants changes; everything that has to happen between two frames of the game, reading its
 * memory, hiding the trainer and drawing, happens inside the video callback, natively.
 * With the switch off nothing here is called per frame and the native side costs one
 * relaxed atomic load.
 *
 * Only Gen 3 games are understood (FireRed, LeafGreen, Ruby, Sapphire, Emerald). What the
 * game's addresses are is the frontend's business (tracker-gba's Overworld.kt); this only
 * checks that a table could be a Gen 3 game's.
 */
public final class SpriteOverlay {

    static {
        System.loadLibrary("libretrodroid");
    }

    private SpriteOverlay() {}

    /** Words in the table [configure] takes, in this order. */
    public static final int CONFIG_WORDS = 11;
    public static final int CFG_MAIN = 0;             // gMain
    public static final int CFG_OAM_BUFFER_OFFSET = 1;// gMain.oamBuffer: 0x38, or 0x3C in Ruby and Sapphire
    public static final int CFG_CB2_OVERWORLD = 2;    // CB2_Overworld, Thumb bit set
    public static final int CFG_CB2_BASIC = 3;        // CB2_OverworldBasic, Thumb bit set
    public static final int CFG_PLAYER_AVATAR = 4;    // gPlayerAvatar
    public static final int CFG_SPRITES = 5;          // gSprites
    public static final int CFG_COORD_OFFSET_X = 6;   // gSpriteCoordOffsetX
    public static final int CFG_COORD_OFFSET_Y = 7;   // gSpriteCoordOffsetY
    public static final int CFG_PLTT_UNFADED = 8;     // gPlttBufferUnfaded
    public static final int CFG_PLTT_FADED = 9;       // gPlttBufferFaded
    public static final int CFG_OBJECT_EVENTS = 10;   // gObjectEvents

    /** Give the native side the game's addresses. False when the table cannot be a Gen 3 game's. Resets its state. */
    public static native boolean configure(long[] words);

    /** On or off. Off finishes the frame it is in (the trainer stays replaced for it) and then stops touching anything. */
    public static native void setEnabled(boolean on);

    /**
     * The sprite to draw, ARGB pixels (not premultiplied), [w] x [h] up to 128 x 128, its top-left placed at
     * the player's 32x32 box corner plus ([ox], [oy]). Call it only when the frame changes.
     */
    public static native boolean setSprite(int[] argb, int w, int h, int ox, int oy);

    /** Nothing to draw: the trainer is left alone. */
    public static native void clearSprite();

    /** Forget everything about the game that was running. */
    public static native void reset();

    /** The last frame's result packed in one long (layout below). No lock, safe to call every frame. */
    public static native long snapshot();

    /** A one-line description of the native state, for logs. */
    public static native String debug();

    // The snapshot's layout (sprite_core.h packSnapshot): bits 0-31 the emulated frames run since the switch went on
    // (the clock the sprite's animation counts in), bit 32 the trainer was replaced this frame (the game is in the
    // overworld with the player on screen), bit 33 the player is stepping or walking into something (the game's own
    // state), bits 34-36 the direction the player faces (1 down, 2 up, 3 left, 4 right, 0 unknown), bit 37 the switch
    // is on and a picture is chosen. The app decodes it in SpriteSnapshot (Kotlin), which the JVM tests can load.
}
