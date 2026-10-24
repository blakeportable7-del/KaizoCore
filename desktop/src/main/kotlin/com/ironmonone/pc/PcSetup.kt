package com.ironmonone.pc

import com.ironmonone.app.HnsSetup
import com.ironmonone.patch.GbaHeader
import java.io.File
import java.io.RandomAccessFile
import java.util.zip.CRC32

/**
 * Finding the player's Heart & Soul in KaizoCore PC's folder, and making it the first time, with the phone's checks
 * (HnsSetup, engine-hns): the KaizoCore build or the official 2.0.6 is used as it is (the comfort patch goes on in
 * memory at every start); an Emerald (USA) with the team's patch beside it is patched once into
 * [OFFICIAL_FILE] in the same folder, checked to be 2.0.6 exactly. The player's own files are never
 * changed, and KaizoCore PC never downloads or ships the team's patch or any game.
 */
object PcSetup {
    /** What the folder gives: the file to build from, or why there is none yet. */
    sealed class Found {
        class Build(val file: File, val start: HnsSetup.Start) : Found()
        class Missing(val why: String) : Found()
    }

    const val NOTHING_YET = "Put your own Pokémon Emerald (USA) .gba and the Heart & Soul team's 2.0.6 patch " +
        "(pokemonHnS_v2.0.6.ups from their GitHub page) in this folder, then press Try again. A Heart & Soul 2.0.6 .gba " +
        "you already made works on its own."
    const val EMERALD_ONLY = "Found your Pokémon Emerald (USA). Now download pokemonHnS_v2.0.6.ups from the Heart & Soul " +
        "team's GitHub page (${HnsSetup.GITHUB_URL}), put it in this folder and press Try again."
    /** The 2.0.6 made from the player's Emerald, kept beside the jar. Plain letters, so every tool reads the name. */
    const val OFFICIAL_FILE = "Pokemon Heart & Soul 2.0.6.gba"
    const val MADE = "Made Pokémon Heart & Soul 2.0.6 from your Emerald and the team's patch. Your own files were not changed."

    private val GAME_EXT = setOf("gba")
    private val PATCH_EXT = setOf("ups", "xdelta", "bps", "ips")

    /** A file's CRC-32, read 1 MB at a time. */
    fun crcOf(f: File): Long = HnsSetup.crcOf(f)

    private fun header(f: File): GbaHeader? = runCatching {
        RandomAccessFile(f, "r").use { r -> ByteArray(0xC0).also { r.readFully(it) } }.let { GbaHeader.parse(it) }
    }.getOrNull()

    /**
     * Looks in [dir] for a game KaizoCore PC can build from, and makes the official 2.0.6 from an Emerald and the team's
     * patch when that is what is there ([work] is a folder of its own for the patching). [say] hears each step.
     */
    fun find(dir: File, work: File, say: (String) -> Unit = {}, crcs: HnsSetup.Crcs = HnsSetup.Crcs.PC): Found {
        val games = dir.listFiles { f -> f.isFile && f.extension.lowercase() in GAME_EXT }?.sortedBy { it.name }.orEmpty()
        val problems = ArrayList<String>()
        var emerald: File? = null
        val usable = ArrayList<Pair<File, HnsSetup.Start>>()
        for (g in games) {
            // A run New Run made is not a starting point; its name says so, and reading 32 MB of each would be slow.
            if (" AutoRandomized" in g.name || " PreviousAttempt" in g.name) continue
            val start = try { HnsSetup.startOf(crcOf(g), header(g), crcs) } catch (e: HnsSetup.Problem) {
                problems += "${g.name}: ${e.message}"
                continue
            }
            if (start == HnsSetup.Start.EMERALD) { if (emerald == null) emerald = g } else usable += g to start
        }
        // The KaizoCore build first, then the official 2.0.6: either builds a run with no patching.
        usable.sortedByDescending { it.second.ordinal }.firstOrNull()?.let { return Found.Build(it.first, it.second) }
        val em = emerald ?: return Found.Missing((listOf(NOTHING_YET) + problems.take(3)).joinToString("\n"))
        val patches = dir.listFiles { f -> f.isFile && f.extension.lowercase() in PATCH_EXT }?.sortedBy { it.name }.orEmpty()
        if (patches.isEmpty()) return Found.Missing(EMERALD_ONLY)
        var patchProblem: String? = null
        for (p in patches) {
            val format = try { HnsSetup.patchFormatOf(p, crcs) } catch (e: HnsSetup.Problem) {
                patchProblem = patchProblem ?: "${p.name}: ${e.message}"
                continue
            }
            return try {
                Found.Build(make(em, p, format, dir, work, say, crcs), HnsSetup.Start.OFFICIAL)
            } catch (e: HnsSetup.Problem) {
                Found.Missing("${p.name}: ${e.message}")
            }
        }
        return Found.Missing(patchProblem ?: EMERALD_ONLY)
    }

    /**
     * Patches a copy of [emerald] with [patch] into the official 2.0.6 (HnsSetup.make checks it is 2.0.6 exactly, then
     * puts KaizoCore's comfort patch on top as a check), keeps the 2.0.6 in [dir] and drops the rest.
     */
    private fun make(emerald: File, patch: File, format: com.ironmonone.core.PatchFormat, dir: File, work: File,
                     say: (String) -> Unit, crcs: HnsSetup.Crcs): File {
        work.mkdirs()
        val copy = File(work, "emerald-${System.nanoTime()}.gba")
        val comfort = File(work, "comfort-${System.nanoTime()}.bps")
        try {
            emerald.copyTo(copy)
            comfort.writeBytes(PcHns.resource(PcHns.COMFORT_PATCH))
            val made = HnsSetup.make(
                HnsSetup.Game(copy, emerald.name, crcs.emerald, HnsSetup.Start.EMERALD),
                HnsSetup.Patch(patch, patch.name, format), comfort, work, crcs, onStep = say,
            )
            made.kaizo.delete()
            val official = made.official ?: throw HnsSetup.Problem(HnsSetup.COMFORT_FAILED)
            val keep = File(dir, OFFICIAL_FILE)
            if (keep.exists()) { official.delete(); return keep }
            official.copyTo(keep)
            official.delete()
            say(MADE)
            return keep
        } finally {
            copy.delete()
            comfort.delete()
            work.listFiles()?.forEach { it.delete() }
            work.delete()
        }
    }

    /** Every line KaizoCore PC's setup can show, for the copy-rule test. */
    val allCopy = listOf(NOTHING_YET, EMERALD_ONLY, MADE)

    /** The CRC-32 of [b]. */
    fun crc(b: ByteArray): Long = CRC32().also { it.update(b) }.value
}
