package com.ironmonone.app

import com.ironmonone.core.RomKind
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Black 2 and White 2's Challenge Mode switch (Blake, 2026-10-23: official, not custom): off by default, per game and
 * settings file; on, the run is made from a copy of the file under the same name with the randomizer's own
 * FORCE_CHALLENGE_MODE tweak set, the recipe moves (a run made ahead the other way is not taken), and the record says it.
 */
class ChallengeModeTest {
    private val filesDir = Files.createTempDirectory("challenge").toFile()
    private val store = PrepStore(filesDir)
    private val kind = RomKind.BLACK2_U
    private val settings = File(filesDir, "prep/settings/B2W2 Kaizo.rnqs").apply {
        parentFile.mkdirs(); writeBytes(File("src/main/assets/presets/B2W2 Kaizo.rnqs").readBytes())
    }
    private val choices = ExtraPasses.Choices(File(filesDir, "prep/extra-passes.txt"))
    private val tweak = com.dabomstew.pkrandomzx.MiscTweak.FORCE_CHALLENGE_MODE.value

    private fun tweaks(f: File) = java.io.FileInputStream(f).use { com.dabomstew.pkrandomzx.Settings.read(it) }.currentMiscTweaks

    @Test
    fun `off by default, and only Black 2 and White 2 take it`() {
        assertTrue(ExtraPasses.takesChallenge(RomKind.BLACK2_U) && ExtraPasses.takesChallenge(RomKind.WHITE2_U))
        assertFalse(ExtraPasses.takesChallenge(RomKind.BLACK_U) || ExtraPasses.takesChallenge(RomKind.EMERALD_U))
        assertFalse(ExtraPasses.challengeOn(kind, settings.name, choices))
        assertSame(settings, ExtraPasses.forRun(store, kind, settings))
        assertEquals(0, tweaks(settings) and tweak, "the official preset has no Challenge Mode of its own")
    }

    @Test
    fun `on, the run is made from a copy under the same name with the tweak, and the record says so`() {
        choices.set(kind.id, settings.name, ExtraPasses.CHALLENGE, true)
        val made = ExtraPasses.forRun(store, kind, settings)
        assertNotEquals(settings, made)
        assertEquals(settings.name, made.name, "the run's name, attempt count and rules stay the file's")
        assertTrue(ExtraPasses.isChallengeCopy(made))
        assertEquals(tweak, tweaks(made) and tweak)
        assertEquals(tweaks(settings) or tweak, tweaks(made), "nothing else changed")
        // A run made ahead with the switch the other way is not taken.
        val prepared = File(filesDir, "prep/prepared/black2-u.nds").apply { parentFile.mkdirs(); writeBytes(ByteArray(64)) }
        assertNotEquals(NextRun.recipe(kind, prepared, settings, null, null, "app"), NextRun.recipe(kind, prepared, made, null, null, "app"))
        // Official all the same: the file is the bundled one, and the record notes the mode.
        val bundled = mapOf(settings.name to settings.readBytes())
        assertEquals("Challenge Mode", ExtraPasses.variant(kind, settings.name, settings.readBytes(), bundled, false, false, challenge = true))
        assertEquals(null, ExtraPasses.variant(kind, settings.name, settings.readBytes(), bundled, false, false))
        // Switched off again, the file itself.
        choices.set(kind.id, settings.name, ExtraPasses.CHALLENGE, false)
        assertSame(settings, ExtraPasses.forRun(store, kind, settings))
    }
}
