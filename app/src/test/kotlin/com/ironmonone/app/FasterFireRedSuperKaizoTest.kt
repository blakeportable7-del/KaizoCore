package com.ironmonone.app

import com.ironmonone.app.engine.ZxEngine
import com.ironmonone.core.Generation
import com.ironmonone.core.RomKind
import com.ironmonone.patch.Patcher
import com.ironmonone.patch.RomIdentity
import com.ironmonone.tracker.GameMap
import com.ironmonone.tracker.MemoryReader
import java.io.File
import java.security.MessageDigest
import java.util.zip.CRC32
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Faster FireRed Super Kaizo 1.0.2 (DrMaple), a player's suggestion (2026-11-06), bundled with DrMaple's permission
 * given to Blake on the KaizoCore Discord (NOTICE): both release IPS files, unmodified, as fastersk- and
 * fasterskopen-firered-u-v11.ips, offered on Library, Patched versions, for FireRed 1.1, with the link to his page kept.
 *
 * What runs everywhere: the bundled files are the release's by SHA-256, the builds take the FRLG settings files and
 * count as smart AI for Super Kaizo, and the link stays. With IRONMON_ROMS (firered-u-v11.gba): the dump is FireRed 1.1
 * by checksum, each file builds its pinned CRC, the result is identified as its build and tracks on FireRed 1.1's map
 * (every table the tracker reads is vanilla v1.1's except the trainers' AI flags), and ZX randomizes it with FRLG
 * Super Kaizo.
 */
class FasterFireRedSuperKaizoTest {

    /** Each build, its bundled file, and the release file's SHA-256 (NOTICE). */
    private val builds = listOf(
        Triple(RomKind.FIRERED_V11_FASTER_SK, "fastersk-firered-u-v11.ips", "9777aaaf3f7f894ca74597ee837cc48f0c6c7d8ca0ca7c295c602ef70261b714"),
        Triple(RomKind.FIRERED_V11_FASTER_SK_NB, "fasterskopen-firered-u-v11.ips", "ff6f51c67183836eb3cdf4787c80f84b8c526458cc63f4740b5e96dc7b389257"),
    )

    private fun sha256(f: File) = MessageDigest.getInstance("SHA-256").digest(f.readBytes()).joinToString("") { "%02x".format(it) }

    @Test
    fun `both builds are bundled as released, offered for FireRed 1_1, and count as smart AI for Super Kaizo`() {
        val options = PrepOptions.forKind(RomKind.FIRERED_U_V11)
        for ((k, asset, sha) in builds) {
            assertEquals(sha, sha256(File("src/main/assets/patches/$asset")), "$asset is the release file, unmodified")
            assertEquals(asset, options.single { it.out?.id == k.id }.asset)
            assertEquals("FRLG", k.family)
            assertEquals(RomKind.FIRERED_U_V11.id, k.baseId)
            assertTrue(k in RomKind.allPatched)
            assertNull(RulesetCatalog.superKaizoWarning(k, "superkaizo"), k.id)
            assertEquals(listOf("STANDARD"), PrepOptions.forKind(k).map { it.id }, "already patched: stored as is")
        }
        assertEquals("Faster FireRed Super Kaizo 1.0.2", options.single { it.out == RomKind.FIRERED_V11_FASTER_SK }.label)
        val link = HackLinks.all.single { it.name == "Faster FireRed Super Kaizo" }
        assertEquals("DrMaple" to "https://github.com/DrMaple/Faster-FireRed-Super-Kaizo/releases", link.author to link.url)
        assertEquals(setOf(RomKind.FIRERED_U_V11.id), link.ids)
        assertTrue("KaizoCore already includes it" in link.blurb)
    }

    private fun reader(rom: ByteArray) = MemoryReader { address, length ->
        val o = address - 0x08000000L
        if (o < 0 || o + length > rom.size) ByteArray(0) else rom.copyOfRange(o.toInt(), (o + length).toInt())
    }

    @Test
    fun `the bundled files build the pinned kinds from FireRed 1_1, track on its map, and randomize`() {
        val rom = Dumps.rom("firered-u-v11.gba") ?: return println("FasterFireRedSuperKaizoTest skipped: set IRONMON_ROMS (firered-u-v11.gba)")
        val vanilla = rom.readBytes()
        assertEquals(RomKind.FIRERED_U_V11.expectedCrc, CRC32().also { it.update(vanilla) }.value, "the base is FireRed 1.1 by checksum")
        val m = GameMap.FIRERED_U_V11
        for ((k, asset, _) in builds) {
            val built = File.createTempFile(k.id, ".gba")
            val run = File.createTempFile(k.id + "-run", ".gba")
            try {
                val crc = Patcher.applyFiles(File("src/main/assets/patches/$asset"), rom, built)
                assertEquals("%08x".format(k.expectedCrc), "%08x".format(crc), k.id)
                val id = RomIdentity.identify(built)
                assertEquals(k.id, id.kind?.id, "identified as ${id.summary}")
                val patched = built.readBytes()
                assertEquals(m.name, GameMap.resolve(reader(patched)).name, "${k.id} tracks on FireRed 1.1's map")

                fun same(addr: Long, len: Int) = (addr - 0x08000000L).toInt().let { o -> (0 until len).count { vanilla[o + it] == patched[o + it] } }
                for ((what, r) in listOf(
                    "base stats" to (m.baseStats to 412 * 28), "level-up learnsets" to (m.levelUpLearnsets to 412 * 4),
                    "trainer class names" to (m.gTrainerClassNames to 107 * 13), "experience tables" to (m.expTables to 6 * 101 * 4),
                    "moves" to (m.battleMoves to 355 * 12), "species names" to (m.speciesNames to 412 * 11),
                    "move names" to (m.moveNames to 355 * 13), "ability names" to (m.abilityNames to 78 * 13),
                )) assertEquals(r.second, same(r.first, r.second), "${k.id}: $what moved")
                // The trainer table: only each trainer's AI flags (bytes 28-31 of the 40-byte record) change.
                val t = (m.gTrainers - 0x08000000L).toInt()
                var ai = 0
                for (i in 0 until 743) for (b in 0 until 40) {
                    if (vanilla[t + i * 40 + b] != patched[t + i * 40 + b]) { assertTrue(b in 28..31, "${k.id}: trainer $i byte $b changed"); ai++ }
                }
                assertTrue(ai > 600, "${k.id}: $ai AI flag bytes changed; the smart AI is missing")
                // The battle code the tracker compares gBattleMainFunc against is where v1.1 has it.
                for (f in listOf(m.introDrawPartySummary, m.introOpponentSendsOut, m.handleTurnAction, m.returnToOverworld)) {
                    assertEquals(32, same(f and 1L.inv(), 32), "${k.id}: code at %08x changed".format(f))
                }

                ZxEngine.randomize(built, File("src/main/assets/presets/FRLG Super Kaizo.rnqs"), run, 20261106L, Generation.GBA3)
                val after = run.readBytes()
                assertEquals(patched.size, after.size)
                assertTrue(patched.indices.count { patched[it] != after[it] } > 10_000, "nothing was randomized")
            } finally {
                built.delete(); run.delete(); File(run.parentFile, run.nameWithoutExtension + ".species.tsv").delete()
            }
        }
    }
}
