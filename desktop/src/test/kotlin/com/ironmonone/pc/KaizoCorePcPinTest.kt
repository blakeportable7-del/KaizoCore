package com.ironmonone.pc

import com.ironmonone.app.engine.HnsEngine
import java.io.File
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The PC's half of the phone/PC byte pin (PLAN section 6 row 1). KaizoCore PC makes each pinned seed the way it makes
 * a run: the build from the player's file (the comfort build as it is, or the official 2.0.6 with the comfort patch from
 * inside the jar), the preset and every table from inside the jar. Each must give the line in engine-hns/pinned-seeds.tsv,
 * the same lines the app's HnsPinnedSeedsTest holds the phone to. The same run made from the app's own asset folder must
 * be the same bytes too, so the jar's copy of the assets is the app's.
 */
class KaizoCorePcPinTest {
    private fun sha1(b: ByteArray) = MessageDigest.getInstance("SHA-1").digest(b).joinToString("") { "%02X".format(it) }

    private val pinsFile = File(TestFiles.repo, "engine-hns/pinned-seeds.tsv")

    private fun pins(): List<List<String>> = pinsFile.readLines(Charsets.UTF_8).map { it.trimEnd('\r') }
        .filter { it.isNotBlank() && !it.startsWith("#") }.map { it.split('\t') }

    @Test
    fun `KaizoCore PC makes the pinned bytes for each pinned seed on both pools`() {
        val comfort = TestFiles.comfortBuild ?: return println("KaizoCorePcPinTest skipped: no .vendor/hns/hns-kaizo.gba")
        val build = PcHns.build(comfort.readBytes())
        assertEquals(0xF4E28E3DL, PcHns.crc(build))
        val pins = pins()
        assertTrue(pins.map { it[1] }.toSet() == setOf("NATDEX", "VANILLA"), "both pools are pinned")
        val appAssets = HnsEngine.folderAssets(File(TestFiles.repo, "app/src/main/assets"))
        for (p in pins) {
            val (preset, pool, seed) = Triple(p[0], HnsEngine.Pool.valueOf(p[1]), p[2].toLong())
            val r = PcHns.randomize(build, PcHns.options(PcHns.settingsFile(preset)), seed, pool)
            assertEquals(p.take(5).joinToString("\t"), listOf(preset, pool.name, seed.toString(), sha1(r.rom), sha1(r.logText.toByteArray(Charsets.UTF_8))).joinToString("\t"),
                "KaizoCore PC's bytes moved from engine-hns/pinned-seeds.tsv")
            if (p === pins.first()) {
                // The app's own build (no easter eggs, patch 0061), asset folder and preset file make the same run on its
                // own ROM: its tables lie at other offsets, so its bytes and the offsets its log names are its own, pinned
                // beside the PC's; the log with the offsets taken out is the PC's word for word.
                val appRom = TestFiles.appBuild?.readBytes() ?: continue
                val phone = HnsEngine.randomize(appRom, HnsEngine.readSettings(File(TestFiles.repo, "app/src/main/assets/presets/$preset.rnqs")), seed, pool, appAssets)
                assertEquals(listOf(p[5], p[6]), listOf(sha1(phone.rom), sha1(phone.logText.toByteArray(Charsets.UTF_8))), "the app's bytes")
                val offsets = Regex("@[0-9A-F]{5,8}| at [0-9A-F]{7,8}:")
                assertEquals(phone.logText.replace(offsets, ""), r.logText.replace(offsets, ""), "the same run")
            }
        }
    }

    @Test
    fun `the official 2_0_6 becomes the comfort build inside KaizoCore PC`() {
        val official = TestFiles.official206 ?: return println("skipped: no .vendor/roms/hns-206.gba")
        val bytes = official.readBytes()
        assertEquals(PcHns.OFFICIAL_206, PcHns.crc(bytes))
        assertEquals(0xF4E28E3DL, PcHns.crc(PcHns.build(bytes)))
        // The player's file is untouched: build works on a copy in memory.
        assertEquals(PcHns.OFFICIAL_206, PcHns.crc(official.readBytes()))
    }

    @Test
    fun `a game that is not Heart and Soul is refused with the reason`() {
        val e = runCatching { PcHns.build(ByteArray(4096) { it.toByte() }) }.exceptionOrNull()
        assertTrue(e is IllegalArgumentException && "not Heart & Soul 2.0.6" in e.message.orEmpty(), "$e")
    }

    @Test
    fun `the jar carries the comfort patch and the presets, and no game or team patch`() {
        assertTrue(PcHns.resource(PcHns.COMFORT_PATCH).size > 1000)
        assertTrue(PcHns.settingsFile(PcHns.DEFAULT_PRESET).isFile)
        val res = File(PcHns::class.java.getResource("/assets/hns/layout-kaizo.json")!!.toURI()).parentFile.parentFile
        val shipped = res.walkTopDown().filter { it.isFile }.map { it.name.lowercase() }.toList()
        assertTrue(shipped.none { it.endsWith(".gba") || it.endsWith(".ups") || it.endsWith(".xdelta") || it.endsWith(".sav") }, "$shipped")
        assertEquals(listOf("hns-kaizo-heartsoul-206.bps"), shipped.filter { it.endsWith(".bps") || it.endsWith(".ips") })
    }
}
