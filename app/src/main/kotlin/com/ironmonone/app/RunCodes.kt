package com.ironmonone.app

import com.ironmonone.core.RomKind
import java.io.File

/**
 * Run codes on the Run tab (roadmap item 7, Blake, 2026-09-29): share the run in play as a code,
 * or build the run a code names from this phone's own dump and settings. The rules here are
 * plain functions so they can be tested; RunCodeUi.kt draws them.
 */
object RunCodes {

    /**
     * The code for the run in play, from the recipe it was made with (NextRun.currentRecipe),
     * never from the Run tab's switches as they are now. [romCrc] is the CRC32 of the run's
     * randomized ROM.
     */
    fun shareCodeFor(recipe: NextRun.Recipe, seed: Long, romCrc: Long): RunCode = RunCode(
        game = recipe.kind,
        settingsHash = RunCode.settingsHashOf(recipe.settingsSha),
        seed = seed,
        passes = RunCode.passesOf(prePass = recipe.prePass.isNotEmpty(), part2 = recipe.secondPass.isNotEmpty()),
        romCrc = romCrc,
        settingsName = recipe.settings,
    )

    /** The text Share sends: the code on a line of its own, then what it is. */
    fun shareText(code: RunCode, gameTitle: String, attempt: Int): String =
        code.text() + "\n" +
            "A KaizoCore run: $gameTitle, ${code.settingsName.removeSuffix(".rnqs")}, my attempt $attempt. " +
            "Paste the code in KaizoCore, Kaizo IronMON, Run codes, to play the same game."

    /** What joining a code needs from this phone, or why it cannot be built here. */
    sealed class Plan {
        data class Ready(
            val code: RunCode,
            val kind: RomKind,
            val prepared: File,
            val settings: File,
        ) : Plan()

        data class Refused(val why: String) : Plan()
    }

    /**
     * Whether the run [text] names can be built on this phone: the game set up, the settings file
     * there (matched by its content, not its name), and passes that fit the game. [sha] is the
     * settings files' SHA-256 in hex.
     */
    fun plan(
        text: String,
        prepared: List<Pair<RomKind, File>>,
        settings: List<File>,
        sha: (File) -> String,
    ): Plan {
        val code = RunCode.parse(text) ?: return Plan.Refused("That is not a run code. A run code starts with KC1.")
        if (code.unknownPasses) return Plan.Refused("This code comes from a newer KaizoCore. Update the app, then paste it again.")
        val kind = RomKind.byId(code.game) ?: return Plan.Refused("This code is for a game this app does not know (${code.game}).")
        val base = prepared.firstOrNull { it.first.id == kind.id }
            // A patched build is made on Patched versions; a game as it is comes in under My games (2026-09-30, UX audit P0-10).
            ?: return Plan.Refused(
                if (kind.isNatDex || kind.patchTag != null) "Make ${kind.displayName} first (Library, Patched versions), then paste the code again."
                else "Add ${kind.displayName} first (Library, My games), then paste the code again.",
            )
        val file = settings.firstOrNull { runCatching { RunCode.settingsHashOf(sha(it)) }.getOrNull() == code.settingsHash }
            ?: return Plan.Refused(
                "You do not have the settings this run was made with (${code.settingsName}). " +
                    "Ask for the settings file, add it with Import under Randomizer settings below, then paste the code again.",
            )
        if (code.prePass && ExtraPasses.prePassName(kind) == null)
            return Plan.Refused("This code asks for the 60% levels, which ${kind.displayName} does not take here.")
        if (code.part2 && !ExtraPasses.takesPart2(kind))
            return Plan.Refused("This code asks for PART 2, which only Red, Blue and Yellow take.")
        return Plan.Ready(code, kind, base.second, file)
    }

    /** What to say once the run is built: whether it is the same game as the sharer's. */
    fun verdict(code: RunCode, builtCrc: Long): String = when {
        code.romCrc == 0L -> "Built from the code. The code carries no checksum, so it cannot be compared."
        code.romCrc == builtCrc -> "Same game as theirs: the checksum matches."
        else -> "Built, but it is not the same game as theirs (their checksum %08x, yours %08x). ".format(code.romCrc, builtCrc) +
            "Their app version or their dump differs from yours."
    }
}
