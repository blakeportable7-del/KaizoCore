package com.ironmonone.core

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.test.assertFalse

/**
 * The guardrails, tested. These are the rules that stop the app corrupting a run:
 * never randomize a randomized ROM, never cross-wire an engine and a ROM, never load a
 * vanilla .rnqs onto a NatDex ROM (which crashes the intro).
 */
class GuardrailsTest {

    private val zxSettings = Settings(
        displayName = "RSE Kaizo.rnqs", gameTag = "RSE", ruleset = "Kaizo",
        natDex = false, payload = byteArrayOf(1),
    )
    private val natDexSettings = Settings(
        displayName = "RSE NatDex v1.2 Kaizo.rnqs", gameTag = "RSE", ruleset = "Kaizo",
        natDex = true, payload = byteArrayOf(2),
    )

    private class FakeEngine(
        override val id: String,
        override val displayName: String,
        private val kinds: Set<RomKind>,
        private val wantsNatDexSettings: Boolean,
    ) : RandomizerEngine {
        override fun accepts(kind: RomKind) = kind in kinds
        override fun accepts(settings: Settings) = settings.natDex == wantsNatDexSettings
        override fun randomize(source: RomFile, settings: Settings, seed: Long, destUri: String) =
            error("not exercised")
    }

    private val zx = FakeEngine("zx-4.6.1", "Universal Pokémon Randomizer ZX 4.6.1",
        setOf(RomKind.EMERALD_U, RomKind.FIRERED_U_V11), wantsNatDexSettings = false)
    private val natDex = FakeEngine("natdex-1.2.1", "Nat. Dex Randomizer 1.2.1",
        setOf(RomKind.EMERALD_NATDEX_121), wantsNatDexSettings = true)

    private fun profile(kind: RomKind, engineId: String) = Profile(
        id = "test", displayName = "test", generation = Generation.GBA3, romKind = kind,
        coreId = "mgba", trackerId = "gba-ironmon", engineId = engineId,
        sourceRom = RomFile("uri", kind, kind.expectedCrc, 33_554_432),
        recipe = null, outputRomUri = null, previousAttemptUri = null,
    )

    // ------------------------------------------------------------------ RomKind

    @Test
    fun `NatDex is an explicit flag, not inferred from the untouched header`() {
        // The BPS leaves the GBA header alone, so both report POKEMON EMER.
        assertEquals(RomKind.EMERALD_U.titleDetect, RomKind.EMERALD_NATDEX_121.titleDetect)
        assertFalse(RomKind.EMERALD_U.isNatDex)
        assertTrue(RomKind.EMERALD_NATDEX_121.isNatDex)
        assertTrue(RomKind.FIRERED_NATDEX_121.isNatDex)
    }

    @Test
    fun `NatDex cannot be stacked on an already-patched ROM`() {
        assertTrue(RomKind.EMERALD_U.natDexCapable)
        assertFalse(RomKind.EMERALD_NATDEX_121.natDexCapable)
    }

    // ------------------------------------------------------------------- Recipe

    @Test
    fun `a recipe cannot patch a randomized ROM`() {
        val patch = Patch("b-to-run", "B to Run", "B flees a wild battle", PatchFormat.IPS,
            setOf(RomKind.EMERALD_NATDEX_121), PatchStage.AFTER_NATDEX, "uri")
        assertFailsWith<IllegalArgumentException> {
            Recipe(listOf(
                RecipeStep.Clean(RomKind.EMERALD_U),
                RecipeStep.Randomize("natdex-1.2.1", natDexSettings),
                RecipeStep.Apply(patch),          // after randomize: forbidden
            ))
        }
    }

    @Test
    fun `a recipe randomizes exactly once`() {
        assertFailsWith<IllegalArgumentException> {
            Recipe(listOf(
                RecipeStep.Randomize("natdex-1.2.1", natDexSettings),
                RecipeStep.Randomize("natdex-1.2.1", natDexSettings),
            ))
        }
    }

    @Test
    fun `the prepared ROM is everything before the randomize step`() {
        val recipe = Recipe(listOf(
            RecipeStep.Clean(RomKind.EMERALD_U),
            RecipeStep.Randomize("natdex-1.2.1", natDexSettings),
        ))
        assertEquals(1, recipe.deterministicSteps.size)
        assertEquals("natdex-1.2.1", recipe.randomizeStep.engineId)
    }

    // ------------------------------------------------------------------ Profile

    @Test
    fun `a valid pairing reports no problems`() {
        val p = profile(RomKind.EMERALD_NATDEX_121, "natdex-1.2.1")
        assertEquals(emptyList(), p.validate(natDex, natDexSettings))
    }

    @Test
    fun `a vanilla settings file is refused on a NatDex ROM`() {
        val p = profile(RomKind.EMERALD_NATDEX_121, "natdex-1.2.1")
        val problems = p.validate(natDex, zxSettings)
        assertTrue(problems.isNotEmpty())
        assertContains(problems.first(), "crashes the intro")
    }

    @Test
    fun `a NatDex settings file is refused on a vanilla ROM`() {
        val p = profile(RomKind.EMERALD_U, "zx-4.6.1")
        val problems = p.validate(zx, natDexSettings)
        assertTrue(problems.any { "vanilla ROM" in it })
    }

    @Test
    fun `the wrong engine for the profile is refused`() {
        val p = profile(RomKind.EMERALD_NATDEX_121, "natdex-1.2.1")
        val problems = p.validate(zx, natDexSettings)
        assertTrue(problems.any { "runs natdex-1.2.1" in it })
        assertTrue(problems.any { "cannot randomize" in it })
    }

    @Test
    fun `New Run is refused until every piece is valid`() {
        val p = profile(RomKind.EMERALD_NATDEX_121, "natdex-1.2.1")
        assertFalse(p.canNewRun(natDex, natDexSettings), "no recipe yet")

        val ready = p.copy(recipe = Recipe(listOf(
            RecipeStep.Clean(RomKind.EMERALD_U),
            RecipeStep.Randomize("natdex-1.2.1", natDexSettings),
        )))
        assertTrue(ready.canNewRun(natDex, natDexSettings))
        assertFalse(ready.canNewRun(natDex, zxSettings), "vanilla settings must still block")
    }
}
