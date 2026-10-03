package com.ironmonone.app

import com.ironmonone.core.RomKind
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
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
        "SentorG/PlatinumSuperKaizo", "PyroMikeGit/IronMONHGSS", "Tripc423/Maxdex", "rh-hideout/pokeemerald-expansion",
    )

    @Test
    fun `every mode source is credited in NOTICE and on the About screen`() {
        for (s in sources) {
            assertTrue(s in notice, "NOTICE does not credit $s")
            assertTrue(s in about, "the About screen does not credit $s")
        }
    }

    /** IronMON HGSS (PyroMikeGit) is built on SilverstarStream and Foulton's intro skip, and both places say so (2026-10-02). */
    @Test
    fun `IronMON HGSS is credited with the intro skip it is built on`() {
        for ((where, text) in listOf("NOTICE" to notice, "About" to about))
            assertTrue("IronMON HGSS" in text && "PyroMikeGit" in text && "intro skip patch by SilverstarStream and Foulton" in text, where)
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

    /**
     * MaxDex (Tripc423/Maxdex) ships its settings file and, since rc34 (Blake, 2026-10-03: "yes"), its patch: NOTICE pins
     * both by SHA-256, the patch is bundled once, under the name PrepOptions gives it, and NOTICE and the About screen
     * name everyone the MaxDex wiki's Credits page names.
     */
    @Test
    fun `MaxDex's settings file and its bundled patch are pinned, and its wiki's credits are in both places`() {
        val preset = File("src/main/assets/presets/FRLG MaxDex Kaizo.rnqs")
        val sha = java.security.MessageDigest.getInstance("SHA-256").digest(preset.readBytes()).joinToString("") { "%02x".format(it) }
        assertTrue("$sha  FRLG MaxDex Kaizo.rnqs" in notice, "NOTICE pins the bundled MaxDex settings file")
        assertTrue("65d70b582f14b493863d2c31d1f668301dc370bf46ece9f40ecaf28e94b15057  maxdex-firered-u-v11.bps (MaxDex.bps)" in notice,
            "and the patch, under its name in the app and Trip's")
        // Bundled once: one BPS in the assets carries its two checksums (FireRed USA 1.1 to MaxDex 1.0), the one the option names.
        val trips = File("src/main/assets").walkTopDown().filter { it.isFile && it.extension.lowercase() == "bps" }.filter { f ->
            val tail = java.io.RandomAccessFile(f, "r").use { r -> r.seek(r.length() - 12); ByteArray(8).also { r.readFully(it) } }
            fun u32(o: Int) = (0..3).fold(0L) { a, i -> a or ((tail[o + i].toLong() and 0xFF) shl (8 * i)) }
            u32(0) == 0x84EE4776L && u32(4) == 0x28C12926L
        }.map { it.relativeTo(File("src/main/assets")).invariantSeparatorsPath }.toList()
        assertEquals(listOf("patches/maxdex-firered-u-v11.bps"), trips)
        val option = PrepOptions.forKind(RomKind.FIRERED_U_V11).single { it.mode == PrepOptions.Mode.MAXDEX }
        assertEquals("patches/" + option.asset, trips.single())
        for (who in listOf("Tripc423", "CyanSixFour", "rh-hideout", "Annadrol", "Ehmtae", "Micro Mouse", "Frankie", "Philanthropist",
            "ColeB", "Johnnyonthemove", "Himmy", "ValyaLoona", "Demon_Quingar", "Pokemon Tabletop Game", "xxYungSmurfxx", "Yitious")) {
            assertTrue(who in notice, "NOTICE names $who")
            assertTrue(who in about, "the About screen names $who")
        }
    }
}
