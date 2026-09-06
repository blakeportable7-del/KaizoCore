package com.ironmonone.app.engine

import com.dabomstew.pkrandomzx.FileFunctions
import com.dabomstew.pkrandomzx.RandomSource
import com.dabomstew.pkrandomzx.Randomizer
import com.dabomstew.pkrandomzx.Settings
import com.dabomstew.pkrandomzx.romhandlers.Gen3RomHandler
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.PrintStream
import java.util.ResourceBundle

/**
 * Universal Pokémon Randomizer ZX 4.6.1 — the VANILLA engine, for standard-dex runs.
 *
 * Lives beside the Nat. Dex fork in one APK because the vendored engine-zx source was
 * package-renamed to com.dabomstew.pkrandomzx (114 files, byte-safe rewrite, resource
 * paths included). Same invocation sequence as NatDexEngine.
 */
object ZxEngine {

    const val ID = "zx-4.6.1"
    const val DISPLAY_NAME = "Universal Pokémon Randomizer ZX 4.6.1"

    fun randomize(
        sourceRom: File,
        settingsFile: File,
        dest: File,
        seed: Long,
        /** Which ROM handler to use. Null falls back to the file extension. */
        generation: com.ironmonone.core.Generation? = null,
    ): NatDexEngine.Outcome {
        val settings = try {
            FileInputStream(settingsFile).use { Settings.read(it) }
        } catch (e: UnsupportedOperationException) {
            val msg = e.message ?: ""
            throw NatDexEngine.EngineException(
                when {
                    "newer version" in msg ->
                        "\"${settingsFile.name}\" is a Nat. Dex settings file. The vanilla " +
                            "engine cannot read it - use a standard settings file."
                    else -> "Could not read \"${settingsFile.name}\": $msg"
                }, e
            )
        }
        settings.customNames = FileFunctions.getCustomNames()

        // The handler follows the ROM's GENERATION. ZX 4.6.1 carries Gen 1-7;
        // this used to pick Gen 4 for any .nds, which would have handed a
        // Black 2 cartridge to the Gen 4 handler. The extension is only the
        // fallback for a caller with no RomKind.
        val isNds = sourceRom.extension.equals("nds", ignoreCase = true)
        val ext = sourceRom.extension.lowercase()
        val gen = generation ?: when {
            isNds -> com.ironmonone.core.Generation.NDS4
            ext == "gbc" -> com.ironmonone.core.Generation.GBC2
            ext == "gb" -> com.ironmonone.core.Generation.GB1
            else -> com.ironmonone.core.Generation.GBA3
        }
        val factory: com.dabomstew.pkrandomzx.romhandlers.RomHandler.Factory = when (gen) {
            com.ironmonone.core.Generation.NDS5 -> com.dabomstew.pkrandomzx.romhandlers.Gen5RomHandler.Factory()
            com.ironmonone.core.Generation.NDS4 -> com.dabomstew.pkrandomzx.romhandlers.Gen4RomHandler.Factory()
            com.ironmonone.core.Generation.GBC2 -> com.dabomstew.pkrandomzx.romhandlers.Gen2RomHandler.Factory()
            com.ironmonone.core.Generation.GB1 -> com.dabomstew.pkrandomzx.romhandlers.Gen1RomHandler.Factory()
            com.ironmonone.core.Generation.GBA3 -> Gen3RomHandler.Factory()
        }
        if (!factory.isLoadable(sourceRom.absolutePath)) {
            throw NatDexEngine.EngineException(
                if (isNds)
                    "\"${sourceRom.name}\" is not a DS Pokémon ROM this engine can open."
                else
                    "\"${sourceRom.name}\" is not a GBA Pokémon ROM this engine can open."
            )
        }
        val handler = factory.create(RandomSource.instance())
        handler.loadRom(sourceRom.absolutePath)
        settings.tweakForRom(handler)

        val logBuffer = ByteArrayOutputStream()
        val log = PrintStream(logBuffer, false, "UTF-8")
        val bundle = ResourceBundle.getBundle("com/dabomstew/pkrandomzx/newgui/Bundle")

        try {
            Randomizer(settings, handler, bundle, false)
                .randomize(dest.absolutePath, log, seed)
        } catch (e: Exception) {
            throw NatDexEngine.EngineException("Randomization failed: ${e.message}", e)
        } finally {
            log.close()
        }

        if (!dest.exists() || dest.length() == 0L) {
            throw NatDexEngine.EngineException("The engine finished but wrote no output.")
        }

        // DS species data lives in compressed archives inside the ROM, which the
        // tracker cannot read out of emulator memory the way it does on GBA. The
        // handler standing here already holds the RANDOMIZED data, so dump what
        // the tracker needs beside the ROM instead. Written for DS only; GBA
        // reads its own ROM live and needs no sidecar.
        if (isNds) {
            runCatching { writeTrackerSidecar(handler, dest) }
        }

        return NatDexEngine.Outcome(seed, String(logBuffer.toByteArray(), Charsets.UTF_8))
    }

    /** Vanilla counterparts of the Nat. Dex helpers, so the editor can accept a
     *  pasted settings string from either engine rather than only the fork. */
    fun settingsString(settingsFile: File): String =
        FileInputStream(settingsFile).use { Settings.read(it) }.toString()

    /**
     * A settings string the way the desktop GUI reads one (NewRandomizerGUI
     * ~1345): the first three characters are the randomizer VERSION, the rest
     * is base64. An older string is run through SettingsUpdater first; a newer
     * one is refused. `Settings.fromString` alone does none of that, and every
     * real string - the official page's included - carries the prefix, so
     * passing it straight through failed as "Malformed input" every time.
     */
    fun parseSettingsString(s: String): com.dabomstew.pkrandomzx.Settings {
        val t = s.trim()
        require(t.length > 3 && t.take(3).all { it.isDigit() }) {
            "A settings string starts with its three-digit version, e.g. 321..."
        }
        val version = t.take(3).toInt()
        val current = com.dabomstew.pkrandomzx.Version.VERSION
        require(version <= current) {
            "That settings string is from a newer randomizer ($version) than this app carries ($current)."
        }
        val body = if (version < current)
            com.dabomstew.pkrandomzx.SettingsUpdater().update(version, t.substring(3))
        else t.substring(3)
        return Settings.fromString(body)
    }

    fun validateSettingsString(s: String): String? = try {
        parseSettingsString(s); null
    } catch (e: Exception) {
        "That settings string is not valid: ${e.message}"
    }

    fun writeSettingsString(s: String, dest: File) {
        val parsed = parseSettingsString(s)
        java.io.FileOutputStream(dest).use { parsed.write(it) }
    }

    /**
     * One line per species: id, name, type1, type2, BST, ability1, ability2.
     * Post-randomization values, straight from the handler that wrote the ROM,
     * so the tracker shows what the player will actually face.
     */
    private fun writeTrackerSidecar(
        handler: com.dabomstew.pkrandomzx.romhandlers.RomHandler,
        dest: File,
    ) {
        val sidecar = File(dest.parentFile, dest.nameWithoutExtension + ".species.tsv")
        sidecar.bufferedWriter().use { w ->
            handler.pokemon.forEach { p ->
                p ?: return@forEach
                fun ability(i: Int) =
                    if (i <= 0) "" else runCatching { handler.abilityName(i) }.getOrDefault("")
                val bst = p.hp + p.attack + p.defense + p.speed + p.spatk + p.spdef
                w.write(
                    listOf(
                        p.number,
                        p.name,
                        p.primaryType?.name ?: "",
                        p.secondaryType?.name ?: "",
                        bst,
                        ability(p.ability1),
                        ability(p.ability2),
                    ).joinToString("\t")
                )
                w.newLine()
            }
        }
    }
}
