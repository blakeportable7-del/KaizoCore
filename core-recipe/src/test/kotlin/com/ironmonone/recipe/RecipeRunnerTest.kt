package com.ironmonone.recipe

import com.ironmonone.core.Patch
import com.ironmonone.core.PatchFormat
import com.ironmonone.core.PatchStage
import com.ironmonone.core.Recipe
import com.ironmonone.core.RecipeStep
import com.ironmonone.core.RomKind
import com.ironmonone.core.Settings
import com.ironmonone.patch.Crc32
import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RecipeRunnerTest {

    // A stand-in for a clean dump. CRC_UNKNOWN so the Clean step accepts any bytes.
    private val kind = RomKind(
        id = "test-rom", displayName = "Test ROM", family = "RSE",
        generation = com.ironmonone.core.Generation.GBA3,
        fileExtension = "gba", expectedCrc = RomKind.CRC_UNKNOWN,
        titleDetect = "TEST", natDexCapable = true,
    )

    private val settings = Settings("RSE NatDex v1.2 Kaizo.rnqs", "RSE", "Kaizo", true, byteArrayOf(1))

    private fun patch(id: String, offset: Int, bytes: ByteArray) = Patch(
        id = id, displayName = id, oneLineEffect = "test", format = PatchFormat.IPS,
        appliesTo = setOf(kind), stage = PatchStage.AFTER_NATDEX, uri = "uri:$id",
    ) to ips(offset, bytes)

    /** Counts calls so the tests can prove work was skipped, not merely that output matched. */
    private class CountingRandomizer : RandomizeStep {
        var calls = 0
        override fun randomize(source: ByteArray, settings: Settings, seed: Long): ByteArray {
            calls++
            // Deterministic in (source, seed) the way a real seeded randomizer is.
            return source.copyOf().also { out ->
                for (i in out.indices) out[i] = (out[i] + seed.toByte()).toByte()
            }
        }
    }

    private fun runner(
        patchBytes: Map<String, ByteArray>,
        randomizer: RandomizeStep,
        cache: BlobStore = MemoryBlobStore(),
    ) = RecipeRunner(
        cache = cache,
        patches = { uri -> patchBytes[uri] ?: error("no such patch $uri") },
        engines = mapOf("natdex-1.2.1" to randomizer),
    )

    // ------------------------------------------------------------------- basics

    @Test
    fun `runs clean then patch then randomize`() {
        val (p, bytes) = patch("b-to-run", 4, byteArrayOf(9, 9))
        val source = ByteArray(32) { it.toByte() }
        val r = runner(mapOf(p.uri to bytes), CountingRandomizer())

        val recipe = Recipe(listOf(
            RecipeStep.Clean(kind),
            RecipeStep.Apply(p),
            RecipeStep.Randomize("natdex-1.2.1", settings),
        ))
        val result = r.newRun(recipe, source, seed = 1)

        assertEquals(3, result.steps.size)
        // patch wrote 9,9 at offset 4; the randomizer then added the seed to every byte
        assertEquals(10, result.output[4].toInt())
        assertEquals(10, result.output[5].toInt())
    }

    // ------------------------------------------------- the reason this class exists

    @Test
    fun `New Run re-runs only the randomize step`() {
        val (p, bytes) = patch("natdex", 0, byteArrayOf(1))
        val source = ByteArray(64) { it.toByte() }
        val cache = MemoryBlobStore()
        val randomizer = CountingRandomizer()
        val r = runner(mapOf(p.uri to bytes), randomizer, cache)

        val recipe = Recipe(listOf(
            RecipeStep.Clean(kind),
            RecipeStep.Apply(p),
            RecipeStep.Randomize("natdex-1.2.1", settings),
        ))

        val first = r.newRun(recipe, source, seed = 1)
        assertFalse(first.steps.first { it.step is RecipeStep.Apply }.fromCache,
            "first run must actually apply the patch")

        val second = r.newRun(recipe, source, seed = 2)
        assertTrue(second.steps.first { it.step is RecipeStep.Apply }.fromCache,
            "New Run must take the prepared ROM from cache, not re-patch it")

        assertEquals(2, randomizer.calls, "randomize runs once per New Run")
        assertFalse(first.output.contentEquals(second.output), "a new seed must change the ROM")
    }

    @Test
    fun `randomization always consumes the prepared ROM, never the previous output`() {
        val (p, bytes) = patch("natdex", 0, byteArrayOf(1))
        val source = ByteArray(64) { it.toByte() }
        val r = runner(mapOf(p.uri to bytes), CountingRandomizer())
        val recipe = Recipe(listOf(
            RecipeStep.Clean(kind),
            RecipeStep.Apply(p),
            RecipeStep.Randomize("natdex-1.2.1", settings),
        ))

        // Same seed twice must give identical output. If the second run had fed on the
        // first run's output, it would drift — which is the mistake the brief forbids.
        val a = r.newRun(recipe, source, seed = 7)
        val b = r.newRun(recipe, source, seed = 7)
        assertTrue(a.output.contentEquals(b.output),
            "re-running the same seed must be idempotent")
    }

    @Test
    fun `changing an upstream toggle invalidates everything downstream`() {
        val (p1, b1) = patch("natdex", 0, byteArrayOf(1))
        val (p2, b2) = patch("b-to-run", 8, byteArrayOf(2))
        val source = ByteArray(64) { it.toByte() }
        val cache = MemoryBlobStore()
        val r = runner(mapOf(p1.uri to b1, p2.uri to b2), CountingRandomizer(), cache)

        val withoutQol = Recipe(listOf(
            RecipeStep.Clean(kind), RecipeStep.Apply(p1),
            RecipeStep.Randomize("natdex-1.2.1", settings),
        ))
        r.newRun(withoutQol, source, seed = 1)

        // User switches on B-to-Run. The new stage sits downstream of the same NatDex
        // output, so NatDex still hits cache and only the new stage runs.
        val withQol = Recipe(listOf(
            RecipeStep.Clean(kind), RecipeStep.Apply(p1), RecipeStep.Apply(p2),
            RecipeStep.Randomize("natdex-1.2.1", settings),
        ))
        val result = r.newRun(withQol, source, seed = 1)

        val applied = result.steps.filter { it.step is RecipeStep.Apply }
        assertTrue(applied[0].fromCache, "the unchanged upstream stage should still hit")
        assertFalse(applied[1].fromCache, "the newly added stage must actually run")
    }

    @Test
    fun `a different source ROM misses the cache entirely`() {
        val (p, bytes) = patch("natdex", 0, byteArrayOf(1))
        val cache = MemoryBlobStore()
        val r = runner(mapOf(p.uri to bytes), CountingRandomizer(), cache)
        val recipe = Recipe(listOf(
            RecipeStep.Clean(kind), RecipeStep.Apply(p),
            RecipeStep.Randomize("natdex-1.2.1", settings),
        ))

        r.newRun(recipe, ByteArray(64) { it.toByte() }, seed = 1)
        val other = r.newRun(recipe, ByteArray(64) { (it + 1).toByte() }, seed = 1)
        assertFalse(other.steps.first { it.step is RecipeStep.Apply }.fromCache,
            "a different input CRC must produce a different cache key")
    }

    // ------------------------------------------------------------------ failures

    @Test
    fun `the previous attempt is handed back for backup`() {
        val (p, bytes) = patch("natdex", 0, byteArrayOf(1))
        val r = runner(mapOf(p.uri to bytes), CountingRandomizer())
        val recipe = Recipe(listOf(
            RecipeStep.Clean(kind), RecipeStep.Apply(p),
            RecipeStep.Randomize("natdex-1.2.1", settings),
        ))
        val old = byteArrayOf(1, 2, 3)
        assertTrue(r.newRun(recipe, ByteArray(64), seed = 1, previous = old).previousAttempt
            .contentEquals(old))
    }

    @Test
    fun `an unregistered engine fails in plain English`() {
        val r = RecipeRunner(MemoryBlobStore(), { error("unused") }, emptyMap())
        val recipe = Recipe(listOf(
            RecipeStep.Clean(kind), RecipeStep.Randomize("natdex-1.2.1", settings),
        ))
        val e = assertFailsWith<RecipeException> { r.newRun(recipe, ByteArray(16), seed = 1) }
        assertContains(e.message!!, "No randomizer is registered")
    }

    @Test
    fun `a source ROM that is not what the recipe starts from is refused`() {
        val strict = kind.copy(expectedCrc = 0xDEADBEEFL)
        val r = runner(emptyMap(), CountingRandomizer())
        val recipe = Recipe(listOf(
            RecipeStep.Clean(strict), RecipeStep.Randomize("natdex-1.2.1", settings),
        ))
        val e = assertFailsWith<RecipeException> { r.newRun(recipe, ByteArray(16), seed = 1) }
        assertContains(e.message!!, "instead of")
    }

    // ------------------------------------------------------------------ fixtures

    /** A minimal real IPS patch, applied by the real core-patch applier. */
    private fun ips(offset: Int, data: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        "PATCH".forEach { out.write(it.code) }
        out.write(offset shr 16); out.write(offset shr 8); out.write(offset)
        out.write(data.size shr 8); out.write(data.size)
        data.forEach { out.write(it.toInt()) }
        "EOF".forEach { out.write(it.code) }
        return out.toByteArray()
    }
}
