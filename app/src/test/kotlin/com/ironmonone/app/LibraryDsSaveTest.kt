package com.ironmonone.app

import com.ironmonone.core.Platform
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A DS game's in-game save follows its file NAME (2026-09-30, UX audit P1): melonDS writes it as <ROM name>.sav in
 * the saves folder, so renaming the file, or adding the same game again under another name, left the save behind
 * and the game came up with none. The library moves it with the game. Game Boy and GBA saves are keyed by CRC and
 * were never at risk; that is checked here too, so the claim is not just made.
 */
class LibraryDsSaveTest {

    private val filesDir: File = Files.createTempDirectory("dssave").toFile()
    private val saves = File(filesDir, "saves").apply { mkdirs() }

    /** The library the app keeps: filesDir/prep/library, beside filesDir/saves. */
    private val lib = PrepStore(filesDir).library

    private fun hg(salt: Int = 1) = LibraryRoms.ds("POKEMON HG", "IPKE", salt)
    private fun save(stem: String, vararg bytes: Byte) = File(saves, "$stem.sav").apply { writeBytes(bytes) }
    private fun beforeLoad(stem: String, vararg bytes: Byte) = File(saves, "$stem.sav.before-load").apply { writeBytes(bytes) }

    @Test
    fun `the saves folder is found where the app keeps it, and nowhere else`() {
        assertEquals(File(filesDir, "saves"), savesDirFor(File(filesDir, "prep/library")))
        assertNull(savesDirFor(Files.createTempDirectory("bare").toFile()), "a library on a bare folder has no saves to keep")
        assertNull(savesDirFor(File(filesDir, "library")))
    }

    @Test
    fun `the DS core is handed the library file, so its save is named after that file`() {
        val e = lib.import("heartgold-u.nds", hg())
        val session = assertNotNull(GameSession.forLibrary(e))
        assertEquals(e.file, session.file, "PlayScreen gives the core this path")
        assertEquals(File(saves, "heartgold-u.sav"), SaveGuard.dsSaveFile(saves, session.file))
        // Renamed, the file has another name, and that is the name the core would look for.
        val r = lib.rename(e, "My game")
        assertEquals(File(saves, "My game.sav"), SaveGuard.dsSaveFile(saves, assertNotNull(GameSession.forLibrary(r)).file))
    }

    @Test
    fun `renaming a DS game moves its in-game save and the copy beside it`() {
        val e = lib.import("heartgold-u.nds", hg())
        val s = save("heartgold-u", 1, 2, 3)
        val b = beforeLoad("heartgold-u", 9, 9)
        val r = lib.rename(e, "My HeartGold")
        assertEquals("My HeartGold.nds", r.name)
        assertContentEquals(byteArrayOf(1, 2, 3), File(saves, "My HeartGold.sav").readBytes(), "the save is where the core looks for the renamed game")
        assertContentEquals(byteArrayOf(9, 9), File(saves, "My HeartGold.sav.before-load").readBytes())
        assertFalse(s.exists(), "and no longer under the old name")
        assertFalse(b.exists())
        assertEquals(File(saves, "My HeartGold.sav"), SaveGuard.dsSaveFile(saves, r.file))
    }

    @Test
    fun `only the renamed game's save moves`() {
        val e = lib.import("heartgold-u.nds", hg())
        val other = lib.import("soulsilver-u.nds", LibraryRoms.ds("POKEMON SS", "IPGE"))
        save("heartgold-u", 1)
        save("soulsilver-u", 2)
        lib.rename(e, "Renamed")
        assertContentEquals(byteArrayOf(2), File(saves, "soulsilver-u.sav").readBytes(), "the other DS game's save stays")
        assertTrue(other.file.exists())
        // A game with no save yet renames without a fuss and leaves no save behind.
        val fresh = lib.import("platinum.nds", LibraryRoms.ds("POKEMON PL", "CPUE"))
        assertEquals("Fresh.nds", lib.rename(fresh, "Fresh").name)
        assertFalse(File(saves, "Fresh.sav").exists())
    }

    @Test
    fun `a GBA game's rename touches no save, because its save is keyed by CRC`() {
        val e = lib.import("firered.gba", LibraryRoms.gba("POKEMON FIRE", "BPRD"))
        assertEquals(Platform.GBA, e.platform)
        val s = save("firered", 5)
        val sram = SessionPaths.sram(filesDir, assertNotNull(GameSession.forLibrary(e)), null)
        val r = lib.rename(e, "Renamed")
        assertContentEquals(byteArrayOf(5), s.readBytes(), "a .sav that only shares the name is not this game's to move")
        assertFalse(File(saves, "Renamed.sav").exists())
        assertEquals(sram, SessionPaths.sram(filesDir, assertNotNull(GameSession.forLibrary(r)), null), "the battery save's path does not name the file")
    }

    @Test
    fun `a save already under the new name is set aside, not overwritten`() {
        val e = lib.import("heartgold-u.nds", hg())
        save("heartgold-u", 1, 1)
        save("Taken", 7, 7, 7)
        val r = lib.rename(e, "Taken")
        assertEquals("Taken.nds", r.name)
        assertContentEquals(byteArrayOf(1, 1), File(saves, "Taken.sav").readBytes(), "the game keeps its own save")
        assertContentEquals(byteArrayOf(7, 7, 7), File(saves, "Taken.sav.replaced").readBytes(), "and the other is still on the phone")
    }

    @Test
    fun `a rename that does not happen moves nothing`() {
        val e = lib.import("heartgold-u.nds", hg())
        val s = save("heartgold-u", 4)
        assertEquals(e, lib.rename(e, "   "))
        assertContentEquals(byteArrayOf(4), s.readBytes())
    }

    // ---------------------------------------------------------------- the same game again, under another name

    private fun keep(name: String, bytes: ByteArray): LibraryStore.Entry {
        val e = lib.import(name, bytes)
        assertNull(lib.refuse(name, e), "the game stays")
        lib.adoptDsSave(e)
        return e
    }

    @Test
    fun `a game deleted and added again under another name gets its in-game save back`() {
        val e = keep("heartgold-u.nds", hg())
        save("heartgold-u", 1, 2, 3)
        beforeLoad("heartgold-u", 8)
        lib.delete(e)
        // Kept, with the game's states, not under the name the next game added there would take (rc33 audit P1 #20).
        assertFalse(File(saves, "heartgold-u.sav").exists(), "not left under the file name")
        assertContentEquals(byteArrayOf(1, 2, 3), File(saves, "lib/lib-%08x/ds-save.sav".format(e.crc)).readBytes(), "deleting a game keeps its save")
        val again = keep("Pokemon HeartGold (USA).nds", hg())
        assertContentEquals(byteArrayOf(1, 2, 3), File(saves, "Pokemon HeartGold (USA).sav").readBytes())
        assertContentEquals(byteArrayOf(8), File(saves, "Pokemon HeartGold (USA).sav.before-load").readBytes())
        assertFalse(File(saves, "heartgold-u.sav").exists())
        // And renamed once more, the marker went with it, so the next re-add finds it as well.
        lib.rename(again, "Third name")
        lib.delete(lib.list().single())
        keep("Fourth name.nds", hg())
        assertContentEquals(byteArrayOf(1, 2, 3), File(saves, "Fourth name.sav").readBytes())
    }

    @Test
    fun `a game added before the marker existed is covered once the library has been read`() {
        val e = lib.import("heartgold-u.nds", hg())   // no adoptDsSave: how every game was added until now
        lib.list()                                     // the first read writes the marker
        save("heartgold-u", 6)
        lib.delete(e)
        keep("Renamed on the phone.nds", hg())
        assertContentEquals(byteArrayOf(6), File(saves, "Renamed on the phone.sav").readBytes())
    }

    @Test
    fun `the marker is kept beside the game's save states and goes into a backup with them`() {
        val e = keep("heartgold-u.nds", hg())
        val session = assertNotNull(GameSession.forLibrary(e))
        val marker = File(SessionPaths.slot(filesDir, session, 1).parentFile, "ds-save-name.txt")
        assertTrue(marker.isFile, "under saves/lib/<the game's id>/, next to its states")
        assertEquals("heartgold-u", marker.readText())
        assertTrue(Backup.admits(marker.relativeTo(filesDir).path.replace('\\', '/')), "a restore on a new phone brings it back")
    }

    @Test
    fun `another game added under a deleted game's name never takes its save, and the game gets it back`() {
        // rc33 audit P1 #20: SoulSilver added as hg.nds booted HeartGold's save, and wrote over it.
        val a = keep("hg.nds", hg())
        save("hg", 1)
        lib.delete(a)
        keep("hg.nds", LibraryRoms.ds("POKEMON SS", "IPGE"))
        assertFalse(File(saves, "hg.sav").exists(), "SoulSilver starts with no save")
        keep("HG new.nds", hg())
        assertContentEquals(byteArrayOf(1), File(saves, "HG new.sav").readBytes(), "HeartGold has its own back, under its new name")
    }

    @Test
    fun `a save a game left under its name before saves were parked waits for that game, not the next one`() {
        // Phones that deleted a DS game before this fix still have its save under the file name, with its marker.
        val plat = LibraryRoms.ds("POKEMON PL", "CPUE")
        val a = keep("Pokemon.nds", plat)
        a.file.delete(); File(a.file.path + ".meta").delete()   // removed the old way: the save stays behind
        save("Pokemon", 7)
        keep("Pokemon.nds", hg())
        assertFalse(File(saves, "Pokemon.sav").exists(), "HeartGold does not boot Platinum's save")
        keep("Platinum again.nds", plat)
        assertContentEquals(byteArrayOf(7), File(saves, "Platinum again.sav").readBytes(), "Platinum gets it back")
    }

    @Test
    fun `a save already under the new name is never overwritten by an add`() {
        val a = keep("hg.nds", hg())
        save("hg", 1)
        lib.delete(a)
        save("X", 2, 2)
        val e = keep("X.nds", hg())
        assertContentEquals(byteArrayOf(2, 2), File(saves, "X.sav").readBytes())
        assertContentEquals(byteArrayOf(1), File(saves, "lib/lib-%08x/ds-save.sav".format(e.crc)).readBytes(), "the old one stays parked")
    }

    @Test
    fun `a game added for the first time, or under the name it had, moves nothing`() {
        val e = keep("hg.nds", hg())
        val s = save("hg", 3)
        lib.adoptDsSave(e)                 // the same name again
        assertContentEquals(byteArrayOf(3), s.readBytes())
        lib.delete(e)
        keep("hg.nds", hg())               // deleted and added back under the same name
        assertContentEquals(byteArrayOf(3), File(saves, "hg.sav").readBytes())
    }

    @Test
    fun `only a DS game has a marker`() {
        val gba = lib.import("x.gba", LibraryRoms.gba("POKEMON FIRE", "BPRD"))
        val ds = lib.import("y.nds", hg())
        lib.list()
        val root = File(saves, "lib")
        assertTrue(File(root, "lib-%08x/ds-save-name.txt".format(ds.crc)).isFile)
        assertFalse(File(root, "lib-%08x".format(gba.crc)).exists(), "a GBA game needs none: its save is keyed by CRC")
    }

    @Test
    fun `a library with no saves folder to keep does nothing and breaks nothing`() {
        val bare = LibraryStore(Files.createTempDirectory("bare").toFile())
        val e = bare.import("hg.nds", hg())
        bare.adoptDsSave(e)
        assertEquals("Renamed.nds", bare.rename(e, "Renamed").name)
        assertEquals(1, bare.list().size)
    }

    @Test
    fun `no library game is named like the run, whose DS save is current_sav`() {
        val e = lib.import("current.nds", hg())
        assertEquals("current (2).nds", e.name, "it would share saves/current.sav with the DS run")
        val other = lib.import("Current.nds", LibraryRoms.ds("POKEMON SS", "IPGE"))
        assertFalse(other.file.nameWithoutExtension.equals("current", ignoreCase = true), "in any case: ${other.name}")
    }
}
