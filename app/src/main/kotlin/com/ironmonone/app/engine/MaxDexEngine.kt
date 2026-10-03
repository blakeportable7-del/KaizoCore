package com.ironmonone.app.engine

import com.dabomstew.pkrandommd.FileFunctions
import com.dabomstew.pkrandommd.RandomSource
import com.dabomstew.pkrandommd.Randomizer
import com.dabomstew.pkrandommd.Settings
import com.dabomstew.pkrandommd.romhandlers.Gen3RomHandler
import com.ironmonone.core.RomKind
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.PrintStream
import java.util.ResourceBundle

/**
 * The MaxDex randomizer (engine-maxdex: CyanSixFour's Nat. Dex randomizer 1.1.3 with Trip's MaxDex
 * changes, ported from his MaxDex-Randomizer.jar), invoked the way NatDexEngine invokes its fork: the
 * CliRandomizer call sequence, Gen 3 only, explicit seed.
 *
 * Trip's ROM detection accepts any FireRed 1.1 of the right size and would write MaxDex's addresses into
 * it, so this refuses any source whose checksum is not the pinned MaxDex build's before the engine sees it.
 */
object MaxDexEngine {

    const val ID = "maxdex-1.0"
    const val DISPLAY_NAME = "MaxDex Randomizer 1.0 (Nat. Dex 1.1.3, 4.6.0-END112)"

    /** Why [sourceRom] cannot be randomized here, or null when it is MaxDex 1.0. */
    fun refusal(sourceRom: File): String? {
        if (!sourceRom.isFile) return "\"${sourceRom.name}\" is not there."
        val crc = java.util.zip.CRC32()
        sourceRom.inputStream().buffered(1 shl 20).use { i ->
            val buf = ByteArray(1 shl 20)
            while (true) { val n = i.read(buf); if (n < 0) break; crc.update(buf, 0, n) }
        }
        return if (crc.value == RomKind.FIRERED_MAXDEX_10.expectedCrc) null
        else "\"${sourceRom.name}\" is not ${RomKind.FIRERED_MAXDEX_10.displayName}, so the MaxDex randomizer will not touch it."
    }

    /**
     * Randomizes [sourceRom] into [dest] using [settingsFile] and [seed]. Deterministic for a given
     * (source, settings, seed), New Run's contract, as the golden runs in engine-maxdex/PINNED.txt show.
     */
    fun randomize(sourceRom: File, settingsFile: File, dest: File, seed: Long): NatDexEngine.Outcome {
        refusal(sourceRom)?.let { throw NatDexEngine.EngineException(it) }
        val settings = try {
            FileInputStream(settingsFile).use { Settings.read(it) }
        } catch (e: UnsupportedOperationException) {
            val msg = e.message ?: ""
            throw NatDexEngine.EngineException(
                when {
                    "newer version" in msg ->
                        "\"${settingsFile.name}\" is not a MaxDex settings file. MaxDex runs on its own, FRLG MaxDex Kaizo."
                    "too old" in msg ->
                        "\"${settingsFile.name}\" is a settings file for an older randomizer. MaxDex runs on its own, FRLG MaxDex Kaizo."
                    else -> "Could not read \"${settingsFile.name}\": $msg"
                }, e
            )
        }
        settings.customNames = FileFunctions.getCustomNames()

        val factory = Gen3RomHandler.Factory()
        if (!factory.isLoadable(sourceRom.absolutePath)) {
            throw NatDexEngine.EngineException("\"${sourceRom.name}\" is not a GBA Pokémon ROM this engine can open.")
        }
        val handler = factory.create(RandomSource.instance())
        handler.loadRom(sourceRom.absolutePath)

        // Required side effect: aligns the settings with what this ROM supports.
        settings.tweakForRom(handler)

        val logBuffer = ByteArrayOutputStream()
        val log = PrintStream(logBuffer, false, "UTF-8")
        val bundle = ResourceBundle.getBundle("com/dabomstew/pkrandommd/newgui/Bundle")
        try {
            Randomizer(settings, handler, bundle, false).randomize(dest.absolutePath, log, seed)
        } catch (e: Exception) {
            throw NatDexEngine.EngineException("Randomization failed: ${e.message}", e)
        } finally {
            log.close()
        }

        if (!dest.exists() || dest.length() == 0L) {
            throw NatDexEngine.EngineException("The engine finished but wrote no output.")
        }
        return NatDexEngine.Outcome(seed, String(logBuffer.toByteArray(), Charsets.UTF_8))
    }

    /**
     * Whether this engine reads [f]. It also brings an older settings file up to 904, so a file the Nat. Dex fork
     * or ZX reads may pass too: RnqsInfo asks those two first.
     */
    fun readsSettings(f: File): Boolean = runCatching { FileInputStream(f).use { Settings.read(it) } }.isSuccess
}
