package com.ironmonone.app.engine

import com.ironmonone.core.RomKind
import java.io.File
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The phone's half of the phone/PC byte pin (KaizoCore PC, PLAN section 6 row 1): fixed seeds on both pools, made the way
 * the app makes a run (Randomizers.randomize on the comfort build, the preset file, the app's assets), must give the
 * ROM and log SHA-1s in engine-hns/pinned-seeds.tsv. The desktop module's KaizoCorePcPinTest makes the same seeds the way
 * KaizoCore PC makes them (assets from inside the jar, the build from the official 2.0.6 or the comfort build) and must
 * give the same lines. So a seed makes the same game on a phone and on a PC, and a run code moves between them.
 *
 * A change to the randomizer that changes its output on purpose changes these lines too: run this test with
 * IRONMON_PIN_PRINT=1, check the change is the one you meant, and paste the printed lines into the file.
 */
class HnsPinnedSeedsTest {
    private val assets = HnsEngine.folderAssets(File("src/main/assets"))
    private val presets = File("src/main/assets/presets")

    /**
     * [romSha1] and [logSha1] are KaizoCore PC's, [appRomSha1] and [appLogSha1] the app's (patch 0061: two builds of one
     * game whose ROM tables lie at other offsets, so the same run's ROM bytes and the offsets its log names differ).
     */
    data class Pin(val preset: String, val pool: HnsEngine.Pool, val seed: Long, val romSha1: String, val logSha1: String,
                   val appRomSha1: String = "", val appLogSha1: String = "") {
        fun line() = listOf(preset, pool.name, seed.toString(), romSha1, logSha1, appRomSha1, appLogSha1).joinToString("\t")
    }

    companion object {
        val pinsFile = File("../engine-hns/pinned-seeds.tsv")

        fun pins(file: File): List<Pin> = file.readLines(Charsets.UTF_8).map { it.trimEnd('\r') }
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .map { l -> l.split('\t').let { Pin(it[0], HnsEngine.Pool.valueOf(it[1]), it[2].toLong(), it[3], it[4], it.getOrElse(5) { "" }, it.getOrElse(6) { "" }) } }

        fun sha1(b: ByteArray): String = MessageDigest.getInstance("SHA-1").digest(b).joinToString("") { "%02X".format(it) }
    }

    @Test
    fun `the phone makes the pinned bytes for each pinned seed on both pools`() {
        val rom = HnsEngineTest.romFile ?: return println("HnsPinnedSeedsTest skipped: no .vendor/hns/hns-kaizo.gba")
        val pins = pins(pinsFile)
        assertTrue(pins.map { it.pool }.toSet() == HnsEngine.Pool.entries.toSet(), "both pools are pinned")
        val before = HnsEngine.assetText
        HnsEngine.assetText = assets
        val dir = File(System.getProperty("java.io.tmpdir"), "hns-pin-${System.nanoTime()}").apply { mkdirs() }
        val made = ArrayList<String>()
        try {
            for (p in pins) {
                val dest = File(dir, "run.gba")
                val out = Randomizers.randomize(RomKind.HEARTSOUL_KAIZO_206, rom, File(presets, "${p.preset}.rnqs"), dest, p.seed, pool = p.pool)
                // The phone plays the app's build: its own ROM bytes and log, the same run as the PC's.
                val got = p.copy(appRomSha1 = sha1(dest.readBytes()), appLogSha1 = sha1(out.logText.toByteArray(Charsets.UTF_8)))
                // The log the phone keeps beside the ROM is the same text.
                assertEquals(out.logText, Randomizers.logFor(dest).readText(Charsets.UTF_8))
                made += got.line()
            }
        } finally {
            HnsEngine.assetText = before
            dir.deleteRecursively()
        }
        if (System.getenv("IRONMON_PIN_PRINT") == "1") made.forEach(::println)
        assertEquals(pins.map { it.line() }, made, "the phone's bytes moved from engine-hns/pinned-seeds.tsv")
    }
}
