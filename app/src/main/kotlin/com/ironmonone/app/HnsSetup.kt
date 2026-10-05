package com.ironmonone.app

import com.ironmonone.core.PatchFormat
import com.ironmonone.core.RomKind
import com.ironmonone.patch.GbaHeader
import com.ironmonone.patch.OutputMismatch
import com.ironmonone.patch.PatchException
import com.ironmonone.patch.Patcher
import com.ironmonone.patch.SourceMismatch
import com.ironmonone.patch.WrongSourceRom
import com.ironmonone.patch.WrongSourceSize
import java.io.File
import java.io.RandomAccessFile

/**
 * The Pokemon Heart & Soul button on Home (Blake, 2026-10-05: "Pokemon Heart and Soul will get its own button on the
 * main menu, then you can click it, upload your game, find patch, patch, it gets added to your library and then you
 * can choose your mode from there"), without the screen (HeartSoulScreen draws it), so every step is tested on the JVM.
 *
 * 1. The player's own game: Emerald (USA), CRC 1F1C08FB, or a copy already patched to Heart & Soul 2.0.6 (01713508), or
 *    the KaizoCore build itself (C993EB6E). Anything else is refused, saying what it is.
 * 2. The patch, which the player downloads in their own browser from the team's GitHub release (the .ups) or Hackdex
 *    (an .xdelta). KaizoCore never downloads it and never ships it. Not needed when the game is already 2.0.6.
 * 3. Patch: their patch, which must give 01713508 exactly ("this is not the 2.0.6 patch" otherwise), added to the
 *    Library as Pokemon Heart & Soul 2.0.6, a normal game with no tracker (Blake: "the user should be able to play a
 *    normal heart and soul if they patch their emerald copy with the official patch"); then KaizoCore's own comfort
 *    patch on top (bundled, ours), which must give C993EB6E, added as Pokemon Heart & Soul (KaizoCore), the build Kaizo
 *    IronMON and Nuzlocke play.
 * 4. The screen then sends the player to choose a mode.
 *
 * Every file this works on is a copy it owns; the player's files and the Library's are never changed.
 */
internal object HnsSetup {
    const val GITHUB_URL = "https://github.com/PokemonHnS-Development/pokehns-expansion/releases/tag/Release-v2.0.6"
    const val HACKDEX_URL = "https://www.hackdex.app/hack/pokemon-heart-and-soul"
    const val REPO_URL = "https://github.com/PokemonHnS-Development/pokehns-expansion"

    /** KaizoCore's comfort patch, ours (tools/hns/build.sh): official 2.0.6 in, the KaizoCore build out. */
    const val COMFORT_PATCH = "hns-kaizo-heartsoul-206.bps"

    /** The Library's names for the two games, as Blake named them. */
    const val OFFICIAL_FILE = "Pokémon Heart & Soul 2.0.6.gba"
    const val KAIZO_FILE = "Pokémon Heart & Soul (KaizoCore).gba"

    /** The checksums the flow knows. A test hands its own, for files small enough to make. */
    data class Crcs(val emerald: Long, val official: Long, val kaizo: Long) {
        companion object {
            val REAL = Crcs(RomKind.EMERALD_U.expectedCrc, RomKind.HEARTSOUL_206.expectedCrc, RomKind.HEARTSOUL_KAIZO_206.expectedCrc)
        }
    }

    /** What the picked game is, and so how far it still has to go. */
    enum class Start { EMERALD, OFFICIAL, KAIZO }

    /** The game picked in step 1: a copy the flow owns, the name it was picked under, its checksum and what it is. */
    data class Game(val file: File, val name: String, val crc: Long, val start: Start)

    /** The patch picked in step 2: a copy the flow owns, its name and format. */
    data class Patch(val file: File, val name: String, val format: PatchFormat)

    /** A refusal, said the way the player can act on it. */
    class Problem(message: String) : Exception(message)

    // ------------------------------------------------------------------ the words

    const val TITLE = "Pokémon Heart & Soul"
    const val INTRO = "Heart & Soul is a fan-made remake of HeartGold and SoulSilver's Johto and Kanto, built on Emerald. " +
        "Turn your own Emerald into it here, then play it as it is, in Kaizo IronMON or as a Nuzlocke."
    /** About's credit (AboutScreen): whose game it is, whose engine, and what KaizoCore adds and leaves out. */
    const val ABOUT_CREDIT = "Pokémon Heart & Soul is by Lil Dill and the Heart & Soul team, on RHH's pokeemerald-expansion. " +
        "KaizoCore includes neither their patch nor any game: you patch your own Emerald with the patch from their page. " +
        "The comfort patch KaizoCore adds on top is its own, made from their open source."

    const val CREDIT = "Heart & Soul is made by Lil Dill and the Heart & Soul team, on RHH's pokeemerald-expansion. " +
        "KaizoCore does not include their patch or any game: you bring both, and the patch comes from their own pages."

    const val STEP1 = "Add your game"
    const val STEP1_LINE = "Pick your own Pokémon Emerald (USA) file. A copy you already patched to Heart & Soul 2.0.6 works too."
    const val STEP1_LIBRARY = "Or use one from your library:"
    const val STEP2 = "Get the patch"
    const val STEP2_LINE = "Open the Heart & Soul team's GitHub page and download pokemonHnS_v2.0.6.ups, then pick it here. " +
        "Hackdex is their other official page: its .xdelta file works too."
    const val STEP2_NOT_NEEDED = "Your game is already Heart & Soul 2.0.6, so there is no patch to get."
    const val STEP3 = "Patch"
    const val STEP3_LINE = "Makes Pokémon Heart & Soul 2.0.6, to play as it is, and Pokémon Heart & Soul (KaizoCore), " +
        "the same game with KaizoCore's tracker and comforts for Kaizo IronMON and Nuzlocke. Both go in your library."
    const val STEP4 = "Choose how to play"
    const val STEP4_LINE = "Kaizo IronMON and Nuzlocke use Heart & Soul (KaizoCore). Pick it there, and pick Vanilla (Gen 1-3) or Nat. Dex (Gen 1-9) Pokémon."

    const val EMERALD_OK = "Pokémon Emerald (USA). Next, get the patch."
    const val OFFICIAL_OK = "Pokémon Heart & Soul 2.0.6, already patched. No patch needed: tap Patch it."
    const val KAIZO_OK = "This is already Pokémon Heart & Soul (KaizoCore). Tap Patch it to add it to your library."
    const val PATCH_OK = "The Heart & Soul 2.0.6 patch. Next, tap Patch it."
    const val PATCH_UNCHECKED = "A patch that does not say what it makes. It is checked when it is applied."

    const val WRONG_GAME = "This file is not Pokémon Emerald (USA). Heart & Soul is made from the US Emerald, and only from it."
    const val OTHER_EMERALD = "This is Emerald, but not the US copy the patch is made for. Another language or revision, " +
        "or a changed copy, will not take the patch."
    const val OTHER_HNS = "This is a Heart & Soul file, but not version 2.0.6. Pick your Emerald (USA) and the 2.0.6 patch instead."
    const val NOT_A_PATCH = "This file is not a patch. Pick pokemonHnS_v2.0.6.ups from GitHub, or the .xdelta file from Hackdex."
    const val PATCH_OTHER_GAME = "This patch is not made for Emerald (USA), so it is not the Heart & Soul patch. Nothing was changed."
    const val NOT_206 = "This is not the 2.0.6 patch. Download version 2.0.6 from the Heart & Soul page and pick that one."
    const val DAMAGED_PATCH = "This patch file is damaged. Download it again and pick the new copy."
    const val COMFORT_FAILED = "KaizoCore's own Heart & Soul patch did not apply. Reinstall KaizoCore and try again."
    const val PICK_GAME_FIRST = "Add your game first."
    const val PICK_PATCH_FIRST = "Pick the patch first."

    /** "This is not the 2.0.6 patch", with the checksum it did make. */
    fun not206(made: Long): String = "$NOT_206 (It made a game with checksum %08X, and 2.0.6 is 01713508.)".format(made)

    // ------------------------------------------------------------------ the checks

    /** What a game file with [crc] and [header] is, or the [Problem] that says why it will not do. */
    fun startOf(crc: Long, header: GbaHeader?, crcs: Crcs = Crcs.REAL): Start = when (crc) {
        crcs.emerald -> Start.EMERALD
        crcs.official -> Start.OFFICIAL
        crcs.kaizo -> Start.KAIZO
        else -> throw Problem(
            when {
                header?.title?.startsWith("POKEMON HNS") == true -> OTHER_HNS
                header?.game == "Emerald" -> OTHER_EMERALD
                else -> WRONG_GAME
            },
        )
    }

    /** What the line under step 1 says for a game that will do. */
    fun gameLine(start: Start): String = when (start) {
        Start.EMERALD -> EMERALD_OK
        Start.OFFICIAL -> OFFICIAL_OK
        Start.KAIZO -> KAIZO_OK
    }

    /** Whether [start] still needs the team's patch. */
    fun needsPatch(start: Start?): Boolean = start == Start.EMERALD

    /** Whether Patch it can run: a game, and the patch when the game needs one. */
    fun canPatch(game: Game?, patch: Patch?): Boolean = game != null && (!needsPatch(game.start) || patch != null)

    /** Why Patch it cannot run yet, or null. */
    fun waitingFor(game: Game?, patch: Patch?): String? = when {
        game == null -> PICK_GAME_FIRST
        needsPatch(game.start) && patch == null -> PICK_PATCH_FIRST
        else -> null
    }

    /**
     * The format of the patch in [file], refused when it is not a patch at all or, for the formats that carry their
     * checksums (UPS, BPS), when it is not for Emerald (USA) or does not make 2.0.6. An xdelta or IPS says neither, and
     * is checked by what it makes ([make]).
     */
    fun patchFormatOf(file: File, crcs: Crcs = Crcs.REAL): PatchFormat {
        val format = Patcher.detect(Patcher.head(file, 16)) ?: throw Problem(NOT_A_PATCH)
        if (format == PatchFormat.UPS || format == PatchFormat.BPS) {
            val (source, target) = footer(file) ?: throw Problem(NOT_A_PATCH)
            // A patch from 2.0.6 (KaizoCore's own comfort patch, picked by mistake) is not the 2.0.6 patch; one from any
            // other game is not for Emerald (USA), whatever it makes.
            if (source != crcs.emerald) throw Problem(if (source == crcs.official) NOT_206 else PATCH_OTHER_GAME)
            if (target != crcs.official) throw Problem(NOT_206)
        }
        return format
    }

    /** What the line under step 2 says for a patch that passed [patchFormatOf]. */
    fun patchLine(format: PatchFormat): String = if (format == PatchFormat.UPS || format == PatchFormat.BPS) PATCH_OK else PATCH_UNCHECKED

    /** A UPS or BPS file's source and target checksums: its last 12 bytes are source, target, patch, little-endian. */
    private fun footer(file: File): Pair<Long, Long>? {
        if (file.length() < 12 + 4) return null
        val b = ByteArray(12)
        RandomAccessFile(file, "r").use { it.seek(file.length() - 12); it.readFully(b) }
        fun u32(o: Int) = (b[o].toLong() and 0xFF) or ((b[o + 1].toLong() and 0xFF) shl 8) or
            ((b[o + 2].toLong() and 0xFF) shl 16) or ((b[o + 3].toLong() and 0xFF) shl 24)
        return u32(0) to u32(4)
    }

    // ------------------------------------------------------------------ patching

    /** What [make] made: the official 2.0.6 (null when the player started from the KaizoCore build) and the KaizoCore build. */
    class Made(val official: File?, val kaizo: File)

    /**
     * Patches [game] up to the KaizoCore build, in [work] (the flow's own folder): the team's [patch] when the game is
     * Emerald, checked to give 2.0.6, then [comfort], checked to give the KaizoCore build. [game]'s file is used up:
     * it becomes the official 2.0.6, or the KaizoCore build. A failure deletes what was made and throws a [Problem].
     */
    fun make(
        game: Game, patch: Patch?, comfort: File?, work: File, crcs: Crcs = Crcs.REAL,
        onStep: (String) -> Unit = {}, onProgress: ((Long, Long) -> Unit)? = null,
    ): Made {
        work.mkdirs()
        if (game.start == Start.KAIZO) return Made(null, game.file)
        // Checked first, so nothing is made for a patch that is not there.
        val c = comfort?.takeIf { it.isFile } ?: throw Problem(COMFORT_FAILED)
        val official: File = if (game.start == Start.OFFICIAL) game.file else {
            val p = patch ?: throw Problem(PICK_PATCH_FIRST)
            onStep("Patching your Emerald into Heart & Soul 2.0.6")
            val out = File(work, "hns-official-${System.nanoTime()}.gba")
            val crc = try {
                Patcher.applyFiles(p.file, game.file, out, "Emerald (USA)", onProgress)
            } catch (t: Throwable) {
                out.delete()
                throw problemOf(t)
            }
            if (crc != crcs.official) { out.delete(); throw Problem(not206(crc)) }
            out
        }
        onStep("Adding KaizoCore's tracker and comforts")
        val kaizo = File(work, "hns-kaizo-${System.nanoTime()}.gba")
        val crc = try {
            Patcher.applyFiles(c, official, kaizo, "Heart & Soul 2.0.6", onProgress)
        } catch (t: Throwable) {
            kaizo.delete()
            if (official != game.file) official.delete()
            throw if (t is OutOfMemoryError || t is java.io.IOException) Problem(patchFailure(t)) else Problem(COMFORT_FAILED)
        }
        if (crc != crcs.kaizo) {
            kaizo.delete()
            if (official != game.file) official.delete()
            throw Problem(COMFORT_FAILED)
        }
        // The picked Emerald copy was the flow's own and is not needed once both are made; a failure above kept it for a retry.
        if (official != game.file) game.file.delete()
        return Made(official, kaizo)
    }

    /** A patch failure in the words of this flow. Out of memory and a full phone keep their own (patchFailure). */
    private fun problemOf(t: Throwable): Throwable = when (t) {
        is Problem -> t
        is WrongSourceRom, is WrongSourceSize, is SourceMismatch -> Problem(PATCH_OTHER_GAME)
        is OutputMismatch -> Problem(NOT_206)
        is com.ironmonone.patch.CorruptPatch -> Problem(DAMAGED_PATCH)
        is PatchException -> Problem(NOT_A_PATCH)
        is OutOfMemoryError, is java.io.IOException -> Problem(patchFailure(t))
        else -> Problem(NOT_A_PATCH)
    }

    /** The two Library entries, and whether each was already there. */
    class Added(val official: LibraryStore.Entry?, val kaizo: LibraryStore.Entry, val officialWasThere: Boolean, val kaizoWasThere: Boolean)

    /**
     * Puts what [make] made into [library] under [OFFICIAL_FILE] and [KAIZO_FILE]. A game already there (the same
     * checksum, under any name) is kept as it is and the new copy dropped, so doing this twice adds nothing twice.
     */
    fun addToLibrary(library: LibraryStore, made: Made, gameName: String?, patchName: String?): Added {
        val have = library.list()
        fun keep(file: File, name: String, baseName: String?, patch: String?): Pair<LibraryStore.Entry, Boolean> {
            val crc = crcOf(file)
            have.firstOrNull { it.crc == crc }?.let { file.delete(); return it to true }
            return library.importFile(name, file, baseName = baseName, patchName = patch) to false
        }
        val official = made.official?.let { keep(it, OFFICIAL_FILE, gameName, patchName) }
        val kaizo = keep(made.kaizo, KAIZO_FILE, null, null)
        return Added(official?.first, kaizo.first, official?.second == true, kaizo.second)
    }

    /** What the screen says once both are in the Library. */
    fun doneLine(added: Added): String {
        val names = listOfNotNull(added.official?.let { stripKnownExt(it.name) }, stripKnownExt(added.kaizo.name))
        val there = (added.official == null || added.officialWasThere) && added.kaizoWasThere
        return if (there) "Already in your library: ${names.joinToString(" and ")}. Choose how to play below."
        else "Done. ${names.joinToString(" and ")} ${if (names.size > 1) "are" else "is"} in your library. Choose how to play below."
    }

    private fun crcOf(f: File): Long {
        val crc = java.util.zip.CRC32()
        f.inputStream().buffered(1 shl 20).use { input -> val b = ByteArray(1 shl 20); while (true) { val n = input.read(b); if (n < 0) break; crc.update(b, 0, n) } }
        return crc.value
    }

    /** The Library games step 1 offers: any copy of the three starting points, Emerald first. */
    fun libraryGames(entries: List<LibraryStore.Entry>, crcs: Crcs = Crcs.REAL): List<Pair<LibraryStore.Entry, Start>> =
        entries.mapNotNull { e ->
            when (e.crc) {
                crcs.emerald -> e to Start.EMERALD
                crcs.official -> e to Start.OFFICIAL
                crcs.kaizo -> e to Start.KAIZO
                else -> null
            }
        }.sortedBy { it.second.ordinal }

    /** Every line the screen shows, for the copy-rule test. */
    val allCopy: List<String> = listOf(
        TITLE, INTRO, CREDIT, STEP1, STEP1_LINE, STEP1_LIBRARY, STEP2, STEP2_LINE, STEP2_NOT_NEEDED, STEP3, STEP3_LINE, STEP4, STEP4_LINE,
        EMERALD_OK, OFFICIAL_OK, KAIZO_OK, PATCH_OK, PATCH_UNCHECKED, WRONG_GAME, OTHER_EMERALD, OTHER_HNS, NOT_A_PATCH,
        PATCH_OTHER_GAME, NOT_206, DAMAGED_PATCH, COMFORT_FAILED, PICK_GAME_FIRST, PICK_PATCH_FIRST, not206(0x12345678),
    )
}
