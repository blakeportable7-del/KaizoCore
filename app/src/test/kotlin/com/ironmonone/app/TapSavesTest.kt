package com.ironmonone.app

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Saves made from a tap in Play that threw on a full phone and closed the app mid-game (rc32 audit P2 #16), a cheat
 * code's line ends (P3 #23), and a new sheet set that deleted the old one before it was written (P3 #67).
 */
class TapSavesTest {
    private val dir: File = Files.createTempDirectory("tapsaves").toFile()

    @AfterTest fun cleanup() { dir.deleteRecursively() }

    // ---- A tap's save on a full phone (rc32 audit P2 #16) ---------------------------------------------------------

    @Test
    fun `a cheat, a slot lock or the hardcore switch that cannot be saved says so instead of closing the app`() {
        val cheats = CheatStore(File(dir, "cheats"))
        val list = listOf(CheatStore.Cheat("Rare candies", "82025BD0 0044", true))
        assertTrue(cheats.save("lib-1", list))
        File(dir, "cheats/lib-1.tsv.tmp").mkdirs()
        assertFalse(cheats.save("lib-1", list + CheatStore.Cheat("More", "82025BD2 0001", true)), "refused, not thrown")
        assertEquals(list, cheats.load("lib-1"), "the list on disk is as it was")

        val slot = StateSlots.Slot(1, File(dir, "saves/state1.bin"), File(dir, "saves/state1.id"), File(dir, "saves/state1.png"))
        slot.file.parentFile.mkdirs()
        slot.lockFile.mkdirs()   // a folder where the lock goes: the write cannot happen
        assertFalse(slot.setLocked(true), "refused, not thrown")

        val ra = RetroAchievements.Store(File(dir, "ra/ra-user.txt"))
        File(dir, "ra/ra-hardcore").mkdirs()
        ra.hardcore = true   // must not throw
    }

    // ---- Line ends in a pasted cheat (rc32 audit P3 #23) -----------------------------------------------------------

    @Test
    fun `a code pasted with Windows line ends and a name with a line break come back whole`() {
        val s = CheatStore(File(dir, "cheats"))
        s.save("lib-2", listOf(
            CheatStore.Cheat("Two\nlines", "AAAA BBBB\r\nCCCC DDDD", true),
            CheatStore.Cheat("x", "1111 2222\r3333 4444", true),
        ))
        val back = s.load("lib-2")
        assertEquals(2, back.size, "a name with a line break lost the whole cheat")
        assertEquals("Two lines", back[0].name)
        assertEquals("AAAA BBBB\nCCCC DDDD", back[0].code)
        assertEquals("1111 2222\n3333 4444", back[1].code, "a code with a carriage return kept its first line only")
    }

    // ---- A new sheet set (rc32 audit P3 #67) ---------------------------------------------------------------------------

    @Test
    fun `a sheet set that cannot be written leaves the one in use`() {
        SpriteIsMeSettings.load(File(dir, "prep/sprite-is-me.txt"))
        try {
            assertEquals(2, SpriteIsMeStore.saveSheets(dir, mapOf(WalkingPals.Anim.IDLE to png(96, 320), WalkingPals.Anim.WALK to png(128, 320))))
            val refuse: (File, ByteArray) -> Boolean = { _, _ -> false }
            assertEquals(SpriteIsMeStore.NOT_SAVED, SpriteIsMeStore.saveSheets(dir, mapOf(WalkingPals.Anim.SLEEP to png(64, 40)), refuse))
            assertEquals(setOf(WalkingPals.Anim.IDLE, WalkingPals.Anim.WALK), SpriteIsMeStore.sheetSizes(dir).keys, "the set in use was deleted first")
            assertEquals(SpriteIsMeSettings.Own.SHEET, SpriteIsMeSettings.own)
            assertFalse(File(SpriteIsMeStore.dir(dir), "sheet.new").exists())
            // A swap a kill cut between its renames: the old set is the set again.
            File(SpriteIsMeStore.dir(dir), "sheet").renameTo(File(SpriteIsMeStore.dir(dir), "sheet.old"))
            assertEquals(setOf(WalkingPals.Anim.IDLE, WalkingPals.Anim.WALK), SpriteIsMeStore.sheetSizes(dir).keys)
        } finally { SpriteIsMeSettings.reset() }
    }

    /** A PNG header of [w] by [h], as SheetSet.pngSize reads one. */
    private fun png(w: Int, h: Int): ByteArray {
        val b = ByteArray(33)
        byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 13, 10, 26, 10).copyInto(b)
        byteArrayOf(0, 0, 0, 13, 'I'.code.toByte(), 'H'.code.toByte(), 'D'.code.toByte(), 'R'.code.toByte()).copyInto(b, 8)
        fun be(v: Int, at: Int) { b[at] = (v ushr 24).toByte(); b[at + 1] = (v ushr 16).toByte(); b[at + 2] = (v ushr 8).toByte(); b[at + 3] = v.toByte() }
        be(w, 16); be(h, 20)
        b[24] = 8; b[25] = 6
        return b
    }
}
