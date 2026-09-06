package com.ironmonone.app.engine

import com.ironmonone.core.Engine
import com.ironmonone.core.Generation
import com.ironmonone.core.RomKind
import java.io.File

/**
 * The one place a ROM is handed to a randomizer.
 *
 * RunScreen and PlayScreen's NEW RUN each carried their own copy of
 * "if Nat. Dex then the fork else ZX". Two copies of the rule that must never
 * be wrong is one too many; the engine is a property of the [RomKind] now and
 * both call sites come through here.
 *
 * Gen 1 is randomized TWICE. The official settings page: "Gen 1 does not have
 * a fluctuating exp curve option. As such, two separate randomizer settings
 * files and randomizations are required ... to prevent your Pokémon from
 * evolving into legendary Pokémon. Apply PART 1 first, then PART 2." The
 * chosen preset is PART 1; PART 2 is one bundled file, the page's own string,
 * run over PART 1's output with a seed derived from the run's. [twoPass] is
 * that, with the engine call handed in so the orchestration is testable
 * without a cartridge.
 */
object Randomizers {
    /** The Gen 1 second pass, bundled with the presets and hidden from the picker. */
    const val GEN1_SECOND_PASS = "RBY PART 2.rnqs"

    fun randomize(
        kind: RomKind,
        sourceRom: File,
        settingsFile: File,
        dest: File,
        seed: Long,
        /** The PART 2 file for a Gen 1 ROM (PrepStore.secondPassSettings); ignored otherwise. */
        secondPass: File? = null,
    ): NatDexEngine.Outcome {
        val engine: (File, File, File, Long) -> NatDexEngine.Outcome = when (kind.engine) {
            Engine.NATDEX -> { src, st, d, sd -> NatDexEngine.randomize(src, st, d, sd) }
            Engine.ZX -> { src, st, d, sd -> ZxEngine.randomize(src, st, d, sd, kind.generation) }
        }
        return if (kind.generation == Generation.GB1) twoPass(sourceRom, settingsFile, secondPass, dest, seed, engine)
        else engine(sourceRom, settingsFile, dest, seed)
    }

    /** PART 2's seed: derived from the run's so one seed reproduces both passes. */
    fun secondSeed(seed: Long): Long = seed xor 0x5041525432L   // "PART2"

    internal fun twoPass(
        sourceRom: File, partOne: File, partTwo: File?, dest: File, seed: Long,
        engine: (File, File, File, Long) -> NatDexEngine.Outcome,
    ): NatDexEngine.Outcome {
        if (partTwo == null || !partTwo.isFile) throw NatDexEngine.EngineException(
            "Gen 1 is randomized in two passes and the second one, \"$GEN1_SECOND_PASS\", is missing from the settings folder."
        )
        val mid = File(dest.parentFile, dest.name + ".pass1.tmp")
        try {
            val first = engine(sourceRom, partOne, mid, seed)
            if (!mid.isFile || mid.length() == 0L) throw NatDexEngine.EngineException("PART 1 wrote no ROM.")
            val second = engine(mid, partTwo, dest, secondSeed(seed))
            return NatDexEngine.Outcome(
                seed,
                "== PART 1: ${partOne.name} ==\n" + first.logText + "\n== PART 2: ${partTwo.name} (seed %016x) ==\n".format(secondSeed(seed)) + second.logText,
            )
        } finally {
            mid.delete()
        }
    }
}
