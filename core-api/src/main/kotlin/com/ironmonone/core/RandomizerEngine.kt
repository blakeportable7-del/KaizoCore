package com.ironmonone.core

/**
 * A settings payload. Either a loaded .rnqs file or a settings string.
 *
 * [gameTag] and [natDex] are parsed from the file name and contents so the profile can
 * refuse a mismatch BEFORE the engine runs. Matching is on game tag + NatDex + ruleset,
 * never on a bare name like "RSE Kaizo.rnqs" (brief section 5).
 */
data class Settings(
    val displayName: String,
    val gameTag: String,      // "FRLG" | "RSE" | "DPPt"
    val ruleset: String,      // "Kaizo" | "Standard" | ...
    val natDex: Boolean,
    val payload: ByteArray,
    val isSettingsString: Boolean = false,
) {
    override fun equals(other: Any?) = this === other ||
        (other is Settings && displayName == other.displayName &&
            payload.contentEquals(other.payload))

    override fun hashCode() = 31 * displayName.hashCode() + payload.contentHashCode()
}

/**
 * Raised instead of letting a mismatched engine/ROM/settings combination run.
 *
 * On PC this surfaced as:
 *   java.lang.UnsupportedOperationException: The settings file is too old to update
 * We detect before spawn and say it in plain English (brief section 13).
 */
class IncompatibleEngineException(message: String) : Exception(message)

/**
 * A randomizer, compiled into the APK from source. Never a JAR loaded at runtime:
 * Android runs ART on DEX bytecode, has no JVM and no `java` binary, and the desktop
 * builds reference Swing/AWT classes that do not exist in Android's class library.
 * The JARs in the user's drop folder are version pins, not executables.
 */
interface RandomizerEngine {
    val id: String            // "zx-4.6.1" | "natdex-1.2.1"
    val displayName: String   // shown in the editor header so the user sees what runs

    fun accepts(kind: RomKind): Boolean
    fun accepts(settings: Settings): Boolean

    /** Deterministic for a given (source, settings, seed). */
    fun randomize(source: RomFile, settings: Settings, seed: Long, destUri: String): RomFile
}
