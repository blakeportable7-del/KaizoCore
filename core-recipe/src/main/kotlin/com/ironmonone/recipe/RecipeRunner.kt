package com.ironmonone.recipe

import com.ironmonone.core.Recipe
import com.ironmonone.core.RecipeStep
import com.ironmonone.core.RomKind
import com.ironmonone.core.Settings
import com.ironmonone.core.StepResult
import com.ironmonone.patch.Crc32
import com.ironmonone.patch.Patcher

/** Somewhere to keep cached stage outputs. Android backs this with app-private files. */
interface BlobStore {
    fun get(key: String): ByteArray?
    fun put(key: String, data: ByteArray)
    fun remove(key: String)
    fun keys(): Set<String>
}

class MemoryBlobStore : BlobStore {
    private val map = LinkedHashMap<String, ByteArray>()
    override fun get(key: String) = map[key]
    override fun put(key: String, data: ByteArray) { map[key] = data }
    override fun remove(key: String) { map.remove(key) }
    override fun keys(): Set<String> = map.keys.toSet()
}

/** Loads a patch's bytes by the uri recorded on the Patch. */
fun interface PatchLoader {
    fun load(uri: String): ByteArray
}

/**
 * Byte-level randomization. Narrower than [com.ironmonone.core.RandomizerEngine] on
 * purpose: the runner has no business knowing about files, so it stays testable without
 * one and the engine adapters own all the I/O.
 */
fun interface RandomizeStep {
    fun randomize(source: ByteArray, settings: Settings, seed: Long): ByteArray
}

class RecipeException(message: String) : Exception(message)

/** Everything up to but not including randomization. Deterministic, therefore cacheable. */
class Prepared(
    val bytes: ByteArray,
    val crc: Long,
    val steps: List<StepResult>,
) {
    val cacheHits: Int get() = steps.count { it.fromCache }
}

class RunResult(
    val output: ByteArray,
    val outputCrc: Long,
    val steps: List<StepResult>,
    val previousAttempt: ByteArray?,
)

/**
 * Runs a [Recipe].
 *
 * The whole design turns on one fact: stages 1 to 3 are deterministic and only the
 * randomize stage takes a seed. So the prepared ROM is computed once and cached, and
 * New Run re-runs nothing but randomization.
 *
 * Cache keys carry the CRC of the stage's INPUT, so a change anywhere upstream changes
 * every key downstream and those stages miss automatically. There is no separate
 * invalidation logic to get wrong.
 *
 * This also makes the brief's forbidden mistake unrepresentable: randomization always
 * consumes [Prepared], never the previous output, so a second New Run cannot randomize
 * an already-randomized ROM.
 */
class RecipeRunner(
    private val cache: BlobStore,
    private val patches: PatchLoader,
    private val engines: Map<String, RandomizeStep>,
) {

    fun prepare(recipe: Recipe, source: ByteArray): Prepared {
        var current = source
        var crc = Crc32.of(source)
        val results = mutableListOf<StepResult>()

        for (step in recipe.deterministicSteps) {
            val inputCrc = crc
            when (step) {
                is RecipeStep.Clean -> {
                    verifyClean(step.kind, source.size, inputCrc)
                    results += StepResult(step, inputCrc, inputCrc, uriFor(step, inputCrc), false)
                }

                is RecipeStep.Apply -> {
                    val key = keyFor(step, inputCrc)
                    val hit = cache.get(key)
                    current = hit ?: run {
                        val patch = patches.load(step.patch.uri)
                        Patcher.apply(patch, current, step.patch.displayName)
                            .also { cache.put(key, it) }
                    }
                    crc = Crc32.of(current)
                    results += StepResult(step, inputCrc, crc, key, hit != null)
                }

                is RecipeStep.Randomize ->
                    throw RecipeException("randomize cannot appear before the final step")
            }
        }
        return Prepared(current, crc, results)
    }

    /**
     * Prepares (from cache after the first time) and randomizes with a fresh [seed].
     * [previous] is the ROM being replaced; it is handed back so the caller can keep it
     * as `*_PreviousAttempt.gba`.
     */
    fun newRun(
        recipe: Recipe,
        source: ByteArray,
        seed: Long,
        previous: ByteArray? = null,
    ): RunResult {
        val prepared = prepare(recipe, source)
        val step = recipe.randomizeStep
        val engine = engines[step.engineId]
            ?: throw RecipeException(
                "No randomizer is registered as \"${step.engineId}\". " +
                    "This profile cannot run until one is."
            )

        val output = engine.randomize(prepared.bytes, step.settings, seed)
        val outputCrc = Crc32.of(output)

        return RunResult(
            output = output,
            outputCrc = outputCrc,
            steps = prepared.steps + StepResult(step, prepared.crc, outputCrc, "auto", false),
            previousAttempt = previous,
        )
    }

    private fun verifyClean(kind: RomKind, size: Int, crc: Long) {
        if (kind.expectedCrc != RomKind.CRC_UNKNOWN && crc != kind.expectedCrc) {
            throw RecipeException(
                "This recipe starts from ${kind.displayName}, but the ROM you gave it has " +
                    "checksum %08x instead of %08x.".format(crc, kind.expectedCrc)
            )
        }
    }

    private fun keyFor(step: RecipeStep.Apply, inputCrc: Long) =
        "patch:${step.patch.id}@%08x".format(inputCrc)

    private fun uriFor(step: RecipeStep.Clean, crc: Long) =
        "clean:${step.kind.id}@%08x".format(crc)
}
