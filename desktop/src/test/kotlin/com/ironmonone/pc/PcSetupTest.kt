package com.ironmonone.pc

import com.ironmonone.app.HnsSetup
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** KaizoCore PC finding and making the player's Heart & Soul in its folder (PcSetup), on copies in a temp folder. */
class PcSetupTest {
    private fun withDir(name: String, block: (File) -> Unit) {
        val d = TestFiles.tempDir(name)
        try { block(d) } finally { d.deleteRecursively() }
    }

    private fun work(d: File) = File(d, ".kaizocore-setup")

    @Test
    fun `an empty folder says what to bring`() = withDir("empty") { d ->
        val f = PcSetup.find(d, work(d))
        assertTrue(f is PcSetup.Found.Missing && f.why.startsWith(PcSetup.NOTHING_YET), "$f")
    }

    @Test
    fun `a game that is not Emerald or Heart and Soul is named with the reason`() = withDir("junk") { d ->
        File(d, "something.gba").writeBytes(ByteArray(0x200) { 7 })
        val f = PcSetup.find(d, work(d)) as PcSetup.Found.Missing
        assertTrue("something.gba: ${HnsSetup.WRONG_GAME}" in f.why, f.why)
    }

    @Test
    fun `Emerald alone asks for the team's patch, and Emerald with it makes 2_0_6 once`() = withDir("emerald") { d ->
        val emerald = TestFiles.emerald ?: return@withDir println("skipped: no .vendor/roms/emerald-u.gba")
        val ups = TestFiles.teamPatch ?: return@withDir println("skipped: no .vendor/hns/pokemonHnS_v2.0.6.ups")
        val myEmerald = File(d, "Pokemon - Emerald Version (USA, Europe).gba").also { emerald.copyTo(it) }
        val emeraldCrc = PcSetup.crcOf(myEmerald)
        assertEquals(PcSetup.EMERALD_ONLY, (PcSetup.find(d, work(d)) as PcSetup.Found.Missing).why)

        ups.copyTo(File(d, "pokemonHnS_v2.0.6.ups"))
        val steps = ArrayList<String>()
        val made = PcSetup.find(d, work(d), { steps += it }) as PcSetup.Found.Build
        assertEquals(PcSetup.OFFICIAL_FILE, made.file.name)
        assertEquals(HnsSetup.Start.OFFICIAL, made.start)
        assertEquals(PcHns.OFFICIAL_206, PcSetup.crcOf(made.file))
        assertEquals(0xF4E28E3DL, PcHns.crc(PcHns.build(made.file.readBytes())))
        assertTrue(PcSetup.MADE in steps, "$steps")
        // The player's Emerald is unchanged and the work folder is gone.
        assertEquals(emeraldCrc, PcSetup.crcOf(myEmerald))
        assertFalse(work(d).exists())
        // The next start finds the 2.0.6 and patches nothing.
        val again = PcSetup.find(d, work(d)) as PcSetup.Found.Build
        assertEquals(made.file, again.file)
        assertEquals(listOf(PcSetup.OFFICIAL_FILE, myEmerald.name, "pokemonHnS_v2.0.6.ups").sorted(), d.list()!!.sorted())
    }

    @Test
    fun `KaizoCore's own comfort patch is not taken for the team's`() = withDir("ourpatch") { d ->
        val emerald = TestFiles.emerald ?: return@withDir println("skipped: no .vendor/roms/emerald-u.gba")
        emerald.copyTo(File(d, "emerald.gba"))
        File(d, "hns-kaizo-heartsoul-206.bps").writeBytes(PcHns.resource(PcHns.COMFORT_PATCH))
        val f = PcSetup.find(d, work(d)) as PcSetup.Found.Missing
        assertTrue(HnsSetup.NOT_206 in f.why, f.why)
    }

    @Test
    fun `the comfort build or the 2_0_6 is used as it is`() = withDir("built") { d ->
        val official = TestFiles.official206 ?: return@withDir println("skipped: no .vendor/roms/hns-206.gba")
        official.copyTo(File(d, "my hns.gba"))
        val f = PcSetup.find(d, work(d)) as PcSetup.Found.Build
        assertEquals("my hns.gba", f.file.name)
        assertEquals(HnsSetup.Start.OFFICIAL, f.start)
        val comfort = TestFiles.comfortBuild ?: return@withDir
        comfort.copyTo(File(d, "zz comfort.gba"))
        assertEquals(HnsSetup.Start.KAIZO, (PcSetup.find(d, work(d)) as PcSetup.Found.Build).start, "the comfort build wins")
    }
}
