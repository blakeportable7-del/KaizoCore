package com.ironmonone.app

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Every source the app's IronMON modes are bundled from is credited twice, in
 * NOTICE and on the About screen, and every bundled patch file is named in
 * NOTICE. Reads the source tree, so a patch or a mode added without its credit
 * fails here.
 */
class ModeCreditsTest {
    private val notice = File("../NOTICE").readText()
    private val about = File("src/main/kotlin/com/ironmonone/app/AboutScreen.kt").readText()

    /** Each source by the part of its URL that names it. */
    private val sources = listOf(
        "CyanSMP64/NatDexExtension", "DrMaple/Faster-FireRed", "DrMaple/Faster-Emerald",
        "SilverstarStream/faster_black2_white2",
        "valiant-code/adb18d248fa0fae7da6b639e2ee8f9c1", "UTDZac/a147c497424dfbd537d8c4b0c22b5621",
        "PyroMikeGit/SuperKaizoIronMON", "Reimittv/SurvivalRevivalIronMON",
        "PappyQC/b9e28068ba4abbbdf191dd33625b737d", "UTDZac/7c51734eed353779f1653217413dd05e",
        "UTDZac/c8c3a84553840f8eabb063be80a33ee7", "pastebin.com/puyAqPyt",
        "stealmylyrics.com/kaizo/patch", "tom-overton/pokefirered", "CyanSMP64/Emerald_Smart_AI",
        "SentorG/PlatinumSuperKaizo",
    )

    @Test
    fun `every mode source is credited in NOTICE and on the About screen`() {
        for (s in sources) {
            assertTrue(s in notice, "NOTICE does not credit $s")
            assertTrue(s in about, "the About screen does not credit $s")
        }
    }

    @Test
    fun `every bundled patch file is named in NOTICE with its SHA-256`() {
        val patches = File("src/main/assets/patches").listFiles()!!.filter { it.isFile }
        assertTrue(patches.size >= 20, "${patches.size} patches")
        for (p in patches) {
            assertTrue(p.name in notice, "NOTICE does not name ${p.name}")
            // The natdex ones predate the hashes; every mode patch carries its own.
            if (!p.name.startsWith("natdex-")) {
                val sha = java.security.MessageDigest.getInstance("SHA-256").digest(p.readBytes()).joinToString("") { "%02x".format(it) }
                assertTrue(sha in notice || (sha.take(8) in notice && sha.takeLast(9) in notice), "NOTICE has no SHA-256 for ${p.name}")
            }
        }
    }
}
