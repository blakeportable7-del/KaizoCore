package com.ironmonone.app.engine

import com.dabomstew.pkrandom122.FileFunctions
import com.dabomstew.pkrandom122.RandomSource
import com.dabomstew.pkrandom122.Randomizer
import com.dabomstew.pkrandom122.Settings
import com.dabomstew.pkrandom122.romhandlers.Gen3RomHandler
import com.ironmonone.core.RomKind
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.PrintStream
import java.util.ResourceBundle

/**
 * The Nat. Dex 1.2.2 randomizer (engine-natdex122, CyanSMP64's fork at release v1.2.2, renamed
 * com.dabomstew.pkrandom122), invoked as NatDexEngine invokes 1.2.1's: the CliRandomizer call sequence, Gen 3 only,
 * explicit seed. Side by side since 2026-11-20 (Blake: "Don't hurt the players who are on the old system"): a 1.2.1
 * game keeps NatDexEngine, and only a 1.2.2 game comes here.
 *
 * Its ROM detection takes any Nat. Dex Emerald or FireRed and would write 1.2.2's offsets into a 1.2.1 game, so this
 * refuses any source whose checksum is not one of the two 1.2.2 games before the engine sees it, as MaxDexEngine does.
 */
object NatDex122Engine {

    const val ID = "natdex-1.2.2"
    const val DISPLAY_NAME = "Nat. Dex Randomizer 1.2.2 (4.6.1-END122)"

    /** Why [sourceRom] cannot be randomized here, or null when it is Nat. Dex 1.2.2's Emerald or FireRed. */
    fun refusal(sourceRom: File): String? {
        if (!sourceRom.isFile) return "\"${sourceRom.name}\" is not there."
        val crc = java.util.zip.CRC32()
        sourceRom.inputStream().buffered(1 shl 20).use { i ->
            val buf = ByteArray(1 shl 20)
            while (true) { val n = i.read(buf); if (n < 0) break; crc.update(buf, 0, n) }
        }
        return if (RomKind.natDexMade.any { it.expectedCrc == crc.value }) null
        else "\"${sourceRom.name}\" is not Nat. Dex 1.2.2, so the Nat. Dex 1.2.2 randomizer will not touch it."
    }

    /**
     * Randomizes [sourceRom] into [dest] using [settingsFile] and [seed]. Deterministic for a given (source, settings,
     * seed), New Run's contract (engine-natdex122/test/FullRandomizeTest).
     */
    fun randomize(sourceRom: File, settingsFile: File, dest: File, seed: Long): NatDexEngine.Outcome {
        refusal(sourceRom)?.let { throw NatDexEngine.EngineException(it) }
        val settings = try {
            FileInputStream(settingsFile).use { Settings.read(it) }
        } catch (e: UnsupportedOperationException) {
            val msg = e.message ?: ""
            throw NatDexEngine.EngineException(
                when {
                    "too old" in msg ->
                        "\"${settingsFile.name}\" is a mode for the standard version of this game. Pick a Nat. Dex mode."
                    "newer version" in msg ->
                        "\"${settingsFile.name}\" was made for a newer randomizer than this app ships."
                    else -> "Could not read \"${settingsFile.name}\": $msg"
                }, e
            )
        }
        settings.customNames = FileFunctions.getCustomNames()

        val factory = Gen3RomHandler.Factory()
        if (!factory.isLoadable(sourceRom.absolutePath)) {
            throw NatDexEngine.EngineException("\"${sourceRom.name}\" is not a GBA Pokémon game the Nat. Dex randomizer can open.")
        }
        val handler = factory.create(RandomSource.instance())
        handler.loadRom(sourceRom.absolutePath)
        // Required side effect: aligns the settings with what this ROM supports.
        settings.tweakForRom(handler)

        val logBuffer = ByteArrayOutputStream()
        val log = PrintStream(logBuffer, false, "UTF-8")
        val bundle = ResourceBundle.getBundle("com/dabomstew/pkrandom122/newgui/Bundle")
        try {
            Randomizer(settings, handler, bundle, false).randomize(dest.absolutePath, log, seed)
        } catch (e: Exception) {
            throw NatDexEngine.EngineException("Randomization failed: ${e.message}", e)
        } finally {
            log.close()
        }
        if (!dest.exists() || dest.length() == 0L) throw NatDexEngine.EngineException("The engine finished but wrote no output.")
        return NatDexEngine.Outcome(seed, String(logBuffer.toByteArray(), Charsets.UTF_8))
    }
}
