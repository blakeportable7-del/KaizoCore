package com.ironmonone.app

import com.ironmonone.core.RomKind
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * rc33 audit P1: melonDS names its save after the ROM and every DS run's ROM is current.nds, so every DS game shared
 * saves/current.sav: a HeartGold run opened on the last Platinum run's save. RunSaves.dsSwap keeps one save per game.
 */
class DsRunSaveTest {
    private val dir = Files.createTempDirectory("dssave").toFile()
    private val save = File(dir, "saves/current.sav")
    private fun write(f: File, text: String) { f.parentFile.mkdirs(); f.writeText(text) }

    @Test
    fun `each DS game keeps its own save across runs of other games`() {
        val plat = RomKind.PLATINUM_U; val hg = RomKind.HEARTGOLD_U
        write(save, "platinum save")
        // A Platinum run is replaced by a HeartGold one: no rc33 owner yet, so the run being replaced owns it.
        RunSaves.dsSwap(dir, hg, previous = plat)
        assertFalse(save.exists(), "HeartGold starts with no save, not Platinum's")
        assertEquals("platinum save", RunSaves.dsParked(dir, plat.id).readText())
        write(save, "heartgold save")
        // Back to Platinum.
        RunSaves.dsSwap(dir, plat, previous = hg)
        assertEquals("platinum save", save.readText())
        assertEquals("heartgold save", RunSaves.dsParked(dir, hg.id).readText())
        assertFalse(RunSaves.dsParked(dir, plat.id).exists())
        // A new seed of the same game keeps the save where it is.
        RunSaves.dsSwap(dir, plat, previous = plat)
        assertEquals("platinum save", save.readText())
        assertEquals(plat.id, RunSaves.dsOwner(save).readText())
    }

    @Test
    fun `a save of unknown game is kept apart, and other consoles are left alone`() {
        write(save, "whose?")
        RunSaves.dsSwap(dir, RomKind.HEARTGOLD_U, previous = RomKind.EMERALD_U)
        assertFalse(save.exists())
        val kept = File(dir, "saves/ds").listFiles()!!.single()
        assertTrue(kept.name.startsWith("unknown-") && kept.readText() == "whose?")
        write(save, "hg")
        RunSaves.dsSwap(dir, RomKind.EMERALD_U, previous = RomKind.HEARTGOLD_U)
        assertEquals("hg", save.readText(), "a GBA run does not touch the DS save")
    }

    @Test
    fun `the install swaps before lastrun names the new run`() {
        val store = File("src/main/kotlin/com/ironmonone/app/PrepStore.kt").readText().replace("\r\n", "\n")
        val swap = store.indexOf("RunSaves.dsSwap(filesDir, kind, RomKind.byId(loadLastRun()?.first))")
        assertTrue(swap > 0)
        assertTrue(swap < store.indexOf("saveLastRun(kind.id, settingsName, custom", swap))
        assertTrue(swap < store.indexOf("nextRun.install(staged, currentRunFor(kind))", swap))
    }
}
