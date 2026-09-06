package com.ironmonone.core

/**
 * Screen arrangement a core presents. The play screen composes from this and never
 * assumes one screen. DS slots in here without touching the UI layer.
 */
sealed interface ScreenLayout {
    data class SingleScreen(val width: Int, val height: Int) : ScreenLayout
    data class DualScreen(
        val topWidth: Int, val topHeight: Int,
        val bottomWidth: Int, val bottomHeight: Int,
        val bottomIsTouch: Boolean,
    ) : ScreenLayout

    companion object {
        val GBA = SingleScreen(240, 160)
    }
}

/**
 * Address spaces named by role, not by GBA memory map, so the tracker's port to a
 * second console is a mapping change and not a rewrite.
 */
enum class MemSpace { MAIN_RAM, INTERNAL_RAM, ROM, SAVE }

/**
 * Superset of buttons across consoles. A core declares what it actually has.
 * New Run's A+B+Start combo goes through here, never through raw GBA key bits.
 */
enum class Button { A, B, X, Y, L, R, START, SELECT, UP, DOWN, LEFT, RIGHT }

/**
 * How audio behaves while the gameplay clock is not 1x.
 *
 * The brief's ideal (game at 4x, music at 1x pitch AND 1x tempo) is not implementable:
 * GBA games mix BGM and SFX in game code into shared DirectSound buffers, driven by the
 * emulated CPU. There is no hardware split to exploit. This is the honest ladder.
 */
sealed interface AudioPolicy {
    /** Default. Mute during turbo, restore instantly at 1x. Cannot chipmunk. */
    data object BgmLock : AudioPolicy

    /**
     * Time-stretched: pitch stays 1x, tempo follows the multiplier. Music sounds fast,
     * never squeaky. Degrades above [maxMultiplier], where we fall back to [BgmLock].
     */
    data class PitchLockedStretch(val maxMultiplier: Float = 3.0f) : AudioPolicy

    /** Raw resampling, pitch follows FPS. Debug only. Never a user-facing default. */
    data object FollowFps : AudioPolicy
}

/** Thrown when a core is handed a ROM kind it does not support. */
class UnsupportedRomException(message: String) : Exception(message)

/**
 * The emulator contract. Everything above [core-api] talks to this and nothing else.
 * Naming a class GbaActivity and calling it directly is the failure mode brief 15.5
 * exists to prevent.
 */
interface EmulatorCore {
    val id: String
    val layout: ScreenLayout
    val supportedKinds: Set<RomKind>
    val supportedButtons: Set<Button>

    fun load(rom: RomFile)
    fun unload()

    /** 0.5f, 1f, 2f, 3f, 4f. */
    fun setSpeed(multiplier: Float)
    fun setAudioPolicy(policy: AudioPolicy)

    fun saveState(slot: Int)
    fun loadState(slot: Int)
    fun softReset()

    fun injectInput(buttons: Set<Button>)

    /** The tracker's only window into the game. */
    fun readMemory(space: MemSpace, address: Long, length: Int): ByteArray

    fun screenshot(): ByteArray

    fun supports(kind: RomKind): Boolean = kind in supportedKinds
}
