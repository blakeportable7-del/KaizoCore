package com.ironmonone.app

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Every bundled preset has rules text for its game and mode, and every rules
 * file names its sources. Reads the source tree, not the APK, so a missing
 * file fails here rather than as an empty RULES box on a phone.
 */
class RulesAssetsTest {
    private val assets = File("src/main/assets")

    @Test
    fun `every preset's game and mode has a rules file that cites its sources and carries no em dash`() {
        val presets = File(assets, "presets").listFiles { f -> f.extension == "rnqs" }!!
        assertTrue(presets.size > 30, "presets: ${presets.size}")
        val missing = mutableListOf<String>()
        for (p in presets) {
            val info = RnqsInfo.parse(p.name)
            val tag = info.gameTag ?: continue; val mode = info.ruleset ?: continue
            if (info.secondPass) continue
            val f = File(assets, "rulesets/$tag/$mode.md")
            if (!f.isFile) { missing += "$tag/$mode (for ${p.name})"; continue }
            val text = f.readText()
            assertTrue("## Sources" in text && "gist.github.com" in text, "${f.name}: sources missing")
            assertTrue("—" !in text, "${f.name}: em dash")
            assertTrue(text.lineSequence().any { it.startsWith("## ${expectedHeading(mode)}") }, "${f.name}: no section for its own mode")
        }
        assertTrue(missing.isEmpty(), "no rules for: $missing")
    }

    private fun expectedHeading(mode: String) = when (mode) {
        "standard" -> "Standard IronMON"; "ultimate" -> "Ultimate IronMON"; "kaizo" -> "Kaizo IronMON"
        "superkaizo" -> "Super Kaizo IronMON"; "survival" -> "Survival IronMON"; "kaizodoubles" -> "Kaizo Doubles"
        else -> mode
    }
}
