package com.ironmonone.app.engine

import com.dabomstew.pkrandom.FileFunctions
import com.dabomstew.pkrandom.RandomSource
import com.dabomstew.pkrandom.Randomizer
import com.dabomstew.pkrandom.Settings
import com.dabomstew.pkrandom.romhandlers.Gen3RomHandler
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.PrintStream
import java.util.ResourceBundle

/**
 * The Nat. Dex randomizer (CyanSMP64 fork @1.2.1), invoked the way its own
 * CliRandomizer does it — same call sequence, Gen3 only, explicit seed.
 *
 * File-path based because the engine is: callers hand real files from PrepStore.
 */
object NatDexEngine {

    const val ID = "natdex-1.2.1"
    const val DISPLAY_NAME = "Nat. Dex Randomizer 1.2.1 (4.6.1-END121)"

    class EngineException(message: String, cause: Throwable? = null) : Exception(message, cause)

    data class Outcome(val seed: Long, val logText: String)

    /**
     * Randomizes [sourceRom] into [dest] using [settingsFile] and [seed].
     * Deterministic for a given (source, settings, seed) — New Run's contract.
     */
    fun randomize(sourceRom: File, settingsFile: File, dest: File, seed: Long): Outcome {
        val settings = try {
            FileInputStream(settingsFile).use { Settings.read(it) }
        } catch (e: UnsupportedOperationException) {
            // Version-direction errors, captured from real runs on 2026-08-30.
            val msg = e.message ?: ""
            throw EngineException(
                when {
                    "too old" in msg ->
                        "\"${settingsFile.name}\" is a vanilla-era settings file. " +
                            "This engine needs a Nat. Dex settings file."
                    "newer version" in msg ->
                        "\"${settingsFile.name}\" was made for a newer randomizer than " +
                            "this app ships."
                    else -> "Could not read \"${settingsFile.name}\": $msg"
                }, e
            )
        }
        settings.customNames = FileFunctions.getCustomNames()

        val factory = Gen3RomHandler.Factory()
        if (!factory.isLoadable(sourceRom.absolutePath)) {
            throw EngineException(
                "\"${sourceRom.name}\" is not a GBA Pokémon ROM this engine can open."
            )
        }
        val handler = factory.create(RandomSource.instance())
        handler.loadRom(sourceRom.absolutePath)

        // Required side effect: aligns the settings with what this ROM supports.
        settings.tweakForRom(handler)

        val logBuffer = ByteArrayOutputStream()
        val log = PrintStream(logBuffer, false, "UTF-8")
        val bundle = ResourceBundle.getBundle("com/dabomstew/pkrandom/newgui/Bundle")

        try {
            Randomizer(settings, handler, bundle, false)
                .randomize(dest.absolutePath, log, seed)
        } catch (e: Exception) {
            throw EngineException("Randomization failed: ${e.message}", e)
        } finally {
            log.close()
        }

        if (!dest.exists() || dest.length() == 0L) {
            throw EngineException("The engine finished but wrote no output.")
        }
        return Outcome(seed, String(logBuffer.toByteArray(), Charsets.UTF_8))
    }

    /** The settings string for an rnqs file — Blake can copy, edit against the gist, paste. */
    fun settingsString(settingsFile: File): String =
        FileInputStream(settingsFile).use { Settings.read(it) }.toString()

    /** Validates a pasted settings string; returns a readable error or null if fine. */
    /**
     * Same contract as ZxEngine.parseSettingsString, against the Nat. Dex
     * fork's own VERSION (908 for 1.2.1) and its own SettingsUpdater. The two
     * forks number their versions differently, which is one more reason a
     * string is only ever handed to the engine that owns the ROM.
     */
    fun parseSettingsString(s: String): Settings {
        val t = s.trim()
        require(t.length > 3 && t.take(3).all { it.isDigit() }) {
            "A settings string starts with its three-digit version, e.g. 908..."
        }
        val version = t.take(3).toInt()
        val current = com.dabomstew.pkrandom.Version.VERSION
        require(version <= current) {
            "That settings string is from a newer randomizer ($version) than this app carries ($current)."
        }
        val body = if (version < current)
            com.dabomstew.pkrandom.SettingsUpdater().update(version, t.substring(3))
        else t.substring(3)
        return Settings.fromString(body)
    }

    fun validateSettingsString(s: String): String? = try {
        parseSettingsString(s); null
    } catch (e: Exception) {
        "That settings string is not valid: ${e.message}"
    }

    /** Persist a pasted settings string as an rnqs-equivalent file the engine can read. */
    fun writeSettingsString(s: String, dest: File) {
        val parsed = parseSettingsString(s)
        java.io.FileOutputStream(dest).use { parsed.write(it) }
    }
}
