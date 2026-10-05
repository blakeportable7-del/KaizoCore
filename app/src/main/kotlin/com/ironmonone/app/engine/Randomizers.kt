package com.ironmonone.app.engine

import com.ironmonone.app.RunSetupProblem
import com.ironmonone.core.Engine
import com.ironmonone.core.Generation
import com.ironmonone.core.Platform
import com.ironmonone.core.RomKind
import java.io.File
import java.util.Locale

/**
 * The one place a ROM is handed to a randomizer.
 *
 * RunScreen and PlayScreen's NEW RUN each carried their own copy of
 * "if Nat. Dex then the fork else ZX". Two copies of the rule that must never
 * be wrong is one too many; the engine is a property of the [RomKind] now and
 * both call sites come through here.
 *
 * Gen 1 has no fluctuating curve, and the official settings page gives two
 * ways to keep a Pokemon from evolving into a legendary: "Use the
 * Pseudo-Fluctuating Growth patch for your game ROM + the settings string from
 * PART 1", or "Use two separate setting strings and apply them in the correct
 * order: PART 1 first, then PART 2". The chosen preset is PART 1; PART 2 is
 * one bundled file, the page's own string, run over PART 1's output with a
 * seed derived from the run's ([twoPass]). PART 2 sets every curve to Slow, so
 * on a patched build it would undo the patch: a patched build takes PART 1
 * only and a vanilla one both, by default, and the player can switch PART 2
 * either way (ExtraPasses). [gen1] is that, with the engine call handed in so
 * the orchestration is testable without a cartridge. Either way it puts the
 * Repel in the Viridian Mart that the Gen 1 reference tracker writes at
 * startup (ViridianRepel).
 *
 * Emerald and Gold/Silver/Crystal Kaizo and Survival (and Emerald Super
 * Kaizo) run at 60% levels, past the randomizer's 50% cap. The settings
 * page's workaround: randomize once with a string that raises trainer and
 * wild levels 6% and nothing else, then randomize that output with the mode.
 * [withPrePass] is that. Whether a run takes it is the player's choice, on
 * by default only for an official preset of those modes (ExtraPasses).
 */
object Randomizers {
    /** The Gen 1 second pass, bundled with the presets and hidden from the picker. */
    const val GEN1_SECOND_PASS = "RBY PART 2.rnqs"

    /**
     * One randomize at a time, whoever asks. Both engines keep their random
     * source in statics (RandomSource), so two at once would each draw from
     * the other's seed and neither ROM would match its seed. A run made ahead
     * in the background (NextRunJob) and a NEW RUN made there and then used
     * to be able to meet here; now the second waits for the first.
     */
    private val engineLock = Any()

    fun randomize(
        kind: RomKind,
        sourceRom: File,
        settingsFile: File,
        dest: File,
        seed: Long,
        /** Gen 1's PART 2 when this run takes it (ExtraPasses.secondPassFor), else null; ignored for other games. */
        secondPass: File? = null,
        /** The 60% levels pre-pass to run first (ExtraPasses.prePassFor), or null for none. Not for Gen 1. */
        prePass: File? = null,
        /** Heart & Soul's pool (Vanilla Gen 1-3 or Nat. Dex Gen 1-9); null takes the one chosen now ([hnsPoolNow]). Ignored for other games. */
        pool: HnsEngine.Pool? = null,
    ): NatDexEngine.Outcome = synchronized(engineLock) {
        val engine: (File, File, File, Long) -> NatDexEngine.Outcome = whole(kind, when (kind.engine) {
            Engine.NATDEX -> { src, st, d, sd -> NatDexEngine.randomize(src, st, d, sd) }
            Engine.MAXDEX -> { src, st, d, sd -> MaxDexEngine.randomize(src, st, d, sd) }
            Engine.ZX -> { src, st, d, sd -> ZxEngine.randomize(src, st, d, sd, kind.generation) }
            Engine.HNS -> { src, st, d, sd -> HnsEngine.randomize(src, st, d, sd, pool ?: hnsPoolNow(), HnsEngine.appAssets()) }
        })
        val outcome = inEngineLocale {
            when {
                kind.generation == Generation.GB1 -> gen1(sourceRom, settingsFile, secondPass, dest, seed, engine)
                prePass != null -> withPrePass(sourceRom, prePass, settingsFile, dest, seed, engine)
                else -> engine(sourceRom, settingsFile, dest, seed)
            }
        }
        // The randomizer's own log, kept beside the ROM it describes so the
        // game-over screen's "Inspect the log" has something to open. Both
        // callers used to discard it. A failed write is not a failed run.
        runCatching { logFor(dest).writeText(outcome.logText) }
        outcome
    }

    /**
     * Runs [block] with the JVM's default locale set to Locale.ROOT, and puts the phone's back after (rc32 audit P2
     * #118). Both engines case-convert and format with the default locale: on a Turkish phone "FIRE" lower-cased to
     * "fıre" and a HeartGold starter's text lost a letter, so a seed made other ROM bytes than on any other phone and a
     * run code no longer matched; on a phone with Arabic or Persian digits the log's numbers were written in them.
     * The default is the whole process's, so the rest of the app formats in ROOT for the seconds a randomize takes;
     * the app's own text names its locale where it matters. Every caller holds [engineLock], so two never meet here.
     */
    internal fun <T> inEngineLocale(block: () -> T): T {
        val default = Locale.getDefault()
        val display = Locale.getDefault(Locale.Category.DISPLAY)
        val format = Locale.getDefault(Locale.Category.FORMAT)
        Locale.setDefault(Locale.ROOT)
        try {
            return block()
        } finally {
            Locale.setDefault(default)
            Locale.setDefault(Locale.Category.DISPLAY, display)
            Locale.setDefault(Locale.Category.FORMAT, format)
        }
    }

    /**
     * [engine], refusing a Game Boy or GBA ROM that is not whole (rc32 audit P2 #119). Those handlers write back the
     * array they loaded, so a whole build is the source's size; a shorter one is a write cut off, which the vendored
     * engines swallow (AbstractGBRomHandler.saveRomFile returns false and Randomizer.randomize carries on), and the
     * app took any file that was not empty. The cut file is deleted. A DS handler throws on a failed write itself, and
     * its output need not be the source's size.
     */
    internal fun whole(
        kind: RomKind, engine: (File, File, File, Long) -> NatDexEngine.Outcome,
        /** The free bytes beside the output; the test's to replace. */
        free: (File) -> Long = { it.usableSpace },
    ): (File, File, File, Long) -> NatDexEngine.Outcome =
        if (kind.platform == Platform.NDS) engine else { src, st, d, sd ->
            val out = engine(src, st, d, sd)
            val want = src.length()
            if (d.length() != want) {
                // Read before the cut file goes: a volume with less room than the missing part is why it stopped.
                val full = free(d.absoluteFile.parentFile) < want - d.length()
                d.delete()
                if (full) throw RunSetupProblem(NO_ROOM)
                throw NatDexEngine.EngineException("The randomizer stopped partway through. Try again, or pick another settings file.")
            }
            out
        }

    /** What a new game that did not fit says, on Play and on the Run tab alike. */
    const val NO_ROOM = "The phone ran out of space while the new game was being made. Free some space and try again."

    /** The same, said before anything is made (RunStart). */
    const val NO_ROOM_BEFORE = "Not enough free space on this phone to make a new game. Free some space and try again."

    /** Where [randomize] keeps the log for the ROM it wrote: `<rom>.log`, the same name the PC randomizer uses. */
    fun logFor(dest: File): File = File(dest.parentFile, dest.name + ".log")

    /**
     * The DS tracker's species sidecar the engine writes beside the ROM it
     * made (ZxEngine), `<rom without extension>.species.tsv`, which the Play
     * screen reads beside the ROM it plays.
     */
    fun sidecarFor(dest: File): File = File(dest.parentFile, dest.nameWithoutExtension + ".species.tsv")

    /**
     * The engine that randomizes [kind], by its version id: part of what a run made ahead was made with (NextRun). For
     * Heart & Soul the pool is part of it ("hns-1.0 natdex"): a run made ahead with one pool is never taken for the other,
     * and the recipe of the run in play says which pool it has ([hnsPoolOf]).
     */
    fun engineId(kind: RomKind, pool: HnsEngine.Pool? = null): String = when (kind.engine) {
        Engine.NATDEX -> NatDexEngine.ID
        Engine.MAXDEX -> MaxDexEngine.ID
        Engine.ZX -> ZxEngine.ID
        Engine.HNS -> HnsEngine.ID + " " + (pool ?: hnsPoolNow()).name.lowercase(Locale.ROOT)
    }

    /** The engine that randomizes [kind], by the name a player reads (the Kaizo IronMON screen's settings line). */
    fun engineName(kind: RomKind): String = when (kind.engine) {
        Engine.NATDEX -> NatDexEngine.DISPLAY_NAME
        Engine.MAXDEX -> MaxDexEngine.DISPLAY_NAME
        Engine.ZX -> ZxEngine.DISPLAY_NAME
        Engine.HNS -> HnsEngine.DISPLAY_NAME
    }

    /**
     * Heart & Soul's pool as the player has it chosen now (HnsPool, on the Kaizo IronMON and Nuzlocke screens).
     * MainActivity sets it; without it (a test, a tool) it is Nat. Dex.
     */
    @Volatile var hnsPoolChosen: (() -> HnsEngine.Pool)? = null

    fun hnsPoolNow(): HnsEngine.Pool = runCatching { hnsPoolChosen?.invoke() }.getOrNull() ?: HnsEngine.Pool.NATDEX

    /** The pool an [engineId] names, or null for an id that is not Heart & Soul's. */
    fun hnsPoolOf(engineId: String?): HnsEngine.Pool? {
        if (engineId == null || !engineId.startsWith(HnsEngine.ID + " ")) return null
        val p = engineId.substringAfter(' ').trim().uppercase(Locale.ROOT)
        return HnsEngine.Pool.entries.firstOrNull { it.name == p }
    }

    /** PART 2's seed: derived from the run's so one seed reproduces both passes. */
    fun secondSeed(seed: Long): Long = seed xor 0x5041525432L   // "PART2"

    /** A Gen 1 run: PART 1, then PART 2 when the run takes it, and the Viridian Repel either way. */
    internal fun gen1(
        sourceRom: File, partOne: File, partTwo: File?, dest: File, seed: Long,
        engine: (File, File, File, Long) -> NatDexEngine.Outcome,
    ): NatDexEngine.Outcome {
        if (partTwo != null) return twoPass(sourceRom, partOne, partTwo, dest, seed, engine)
        val outcome = engine(sourceRom, partOne, dest, seed)
        // Main.lua:279-285 of both Game Boy reference trackers: Repel for the Mart's second item.
        ViridianRepel.apply(dest)
        return outcome
    }

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
            // Main.lua:279-285 of both Game Boy reference trackers: Repel for the Mart's second item.
            ViridianRepel.apply(dest)
            return NatDexEngine.Outcome(
                seed,
                "== PART 1: ${partOne.name} ==\n" + first.logText + "\n== PART 2: ${partTwo.name} (seed %016x) ==\n".format(secondSeed(seed)) + second.logText,
            )
        } finally {
            mid.delete()
        }
    }

    /** The pre-pass's seed: derived from the run's, so one seed reproduces both passes. */
    fun preSeed(seed: Long): Long = seed xor 0x5052455041L   // "PREPA"

    /**
     * The 60% levels: [prePass] (trainer and wild +6%, nothing else) over the
     * prepared ROM, then [preset] over that, with the run's own seed, so the
     * seed the player sees is the mode's. The log is the preset's, first line
     * untouched (RandomizerLog finds the seed and settings string in its
     * header, and every section from its first occurrence); the pre-pass
     * follows at the end: a "== PRE-PASS ==" line, its seed and its settings
     * string, which a Premade Seed needs to build this run again (rc32 audit
     * P2 #69: they were not kept), and one line that says what it did. Its own
     * log would repeat the vanilla trainers at +6%, which the log viewer must
     * never mistake for the run's.
     */
    internal fun withPrePass(
        sourceRom: File, prePass: File, preset: File, dest: File, seed: Long,
        engine: (File, File, File, Long) -> NatDexEngine.Outcome,
    ): NatDexEngine.Outcome {
        if (!prePass.isFile) throw NatDexEngine.EngineException(
            "The 60% levels are run with \"${prePass.name}\" first, and it is missing from the settings folder."
        )
        val mid = File(dest.parentFile, dest.name + ".prepass.tmp")
        try {
            val pre = engine(sourceRom, prePass, mid, preSeed(seed))
            if (!mid.isFile || mid.length() == 0L) throw NatDexEngine.EngineException("The 60% levels pre-pass wrote no ROM.")
            val main = engine(mid, preset, dest, seed)
            val bom = Char(0xFEFF).toString()
            val preSettings = pre.logText.lineSequence().map { it.trimEnd('\r').removePrefix(bom) }.firstOrNull { it.startsWith("Settings String:") }
            return NatDexEngine.Outcome(
                seed,
                main.logText.trimEnd() + "\n------------------------------------------------------------------\n" +
                    "== PRE-PASS: ${prePass.name} (seed ${"%016x".format(preSeed(seed))}) ==\n" +
                    "Random Seed: ${preSeed(seed)}\n" + (preSettings?.let { it + "\n" } ?: "") +
                    "60% levels: \"${prePass.name}\" raised trainer and wild levels 6% first (seed ${"%016x".format(preSeed(seed))}), " +
                    "then \"${preset.name}\" ran over its output.\n",
            )
        } finally {
            mid.delete()
        }
    }
}
