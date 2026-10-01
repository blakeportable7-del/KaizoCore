package com.ironmonone.app

import android.content.ContextWrapper
import com.ironmonone.core.RomKind
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * What adding files says (2026-09-30, UX audit P0-11): a .7z or .rar is told to be unpacked, a game says whether the
 * tracker reads it and where to start, a game it cannot read says what it is, and a pick of several says how many went in.
 */
class LibraryImportTest {

    private val playsWithout = "It plays here without a tracker."

    private fun tmp(): File = Files.createTempDirectory("import").toFile()

    /** An importer on a store in a bare folder; importFile and importOne never touch the context, so a bare wrapper stands in for it. */
    private fun importer(forHacks: Boolean = false): LibraryImport {
        val filesDir = tmp()
        return LibraryImport(ContextWrapper(null), PrepStore(filesDir), FileProgress(), forHacks)
    }

    private fun file(dir: File, name: String, bytes: ByteArray) = File(dir, name).apply { writeBytes(bytes) }

    // ---------------------------------------------------------------- archives

    @Test
    fun `a 7z or a rar is known by its first bytes or its name`() {
        assertEquals(".7z", LibraryImport.archiveKind("a.bin", LibraryRoms.sevenZip()), "renamed, still a 7z by its first bytes")
        assertEquals(".rar", LibraryImport.archiveKind("a.bin", LibraryRoms.rar()))
        assertEquals(".7z", LibraryImport.archiveKind("Pokemon.7Z", ByteArray(8)), "by its name when its bytes do not say")
        assertEquals(".rar", LibraryImport.archiveKind("Pokemon.rar", ByteArray(0)))
        // A zip, a game and a patch are not those.
        assertNull(LibraryImport.archiveKind("a.zip", byteArrayOf('P'.code.toByte(), 'K'.code.toByte(), 3, 4)))
        assertNull(LibraryImport.archiveKind("a.gba", LibraryRoms.gba("POKEMON FIRE", "BPRE").copyOf(8)))
        assertNull(LibraryImport.archiveKind("a.bps", ByteArray(8)))
    }

    @Test
    fun `a 7z or a rar is told to be unpacked first, not called damaged`() {
        assertEquals(
            "That is a .7z file. KaizoCore opens .zip only. Unpack it on your phone first, then add the game inside.",
            LibraryImport.archiveLine(".7z"),
        )
        assertEquals(
            "That is a .rar file. KaizoCore opens .zip only. Unpack it on your phone first, then add the game inside.",
            LibraryImport.archiveLine(".rar"),
        )
        // The Patched versions page picks a game file rather than adding one.
        assertEquals(
            "That is a .7z file. KaizoCore opens .zip only. Unpack it on your phone first, then choose the game inside.",
            LibraryImport.archiveLine(".7z", verb = "choose"),
        )
        // Through the importer: skipped, said so, nothing kept, and never "damaged".
        val dir = tmp()
        val imp = importer()
        imp.begin(1)
        val line = imp.importFile("Pokemon HeartGold.7z", file(dir, "import-1", LibraryRoms.sevenZip()))
        assertEquals("That is a .7z file. KaizoCore opens .zip only. Unpack it on your phone first, then add the game inside.", line)
        assertFalse("damaged" in line)
        assertEquals(line, imp.finish(listOf(line)), "one file that was skipped needs no count")
    }

    // ---------------------------------------------------------------- what an added game says

    @Test
    fun `a game the tracker reads says so and where to start`() {
        val e = LibraryStore.Entry(File("e.gba"), "e.gba", RomKind.EMERALD_U.expectedCrc, RomKind.EMERALD_U, RomKind.EMERALD_U.platform, "s")
        assertEquals(
            "Added Pokémon Emerald (U). The tracker reads it. Start a run from Kaizo IronMON on Home, or tap Play to just play.",
            LibraryImport.addedLine(e),
        )
        // In a pick of several the tail is said once, not on every line.
        assertEquals("Added Pokémon Emerald (U). The tracker reads it.", LibraryImport.addedLine(e, several = true))
        // On the ROM Hacks screen there is no Play button on a game, and it turns up in step 1.
        assertEquals("Added Pokémon Emerald (U). The tracker reads it. It is in the list under step 1.", LibraryImport.addedLine(e, forHacks = true))
    }

    @Test
    fun `a game the tracker cannot read says that first, then what it is`() {
        val lib = LibraryStore(tmp())
        val german = lib.import("Pokemon FireRed (Germany).gba", LibraryRoms.gba("POKEMON FIRE", "BPRD"))
        assertEquals(
            "Added, but the tracker cannot read it. This is the German FireRed. The tracker reads the US English FireRed, v1.0 or v1.1. $playsWithout",
            LibraryImport.addedLine(german),
        )
        val revision = lib.import("fr11.gba", LibraryRoms.gba("POKEMON FIRE", "BPRE", version = 9))
        assertEquals(
            "Added, but the tracker cannot read it. This is FireRed, but not v1.0 or v1.1. It may be trimmed, changed or another revision. $playsWithout",
            LibraryImport.addedLine(revision),
        )
        val black = lib.import("black.nds", LibraryRoms.ds("POKEMON B", "IRBO"))
        assertEquals(
            "Added, but the tracker cannot read it. This is Pokémon Black (U), a copy KaizoCore has not checked yet. $playsWithout",
            LibraryImport.addedLine(black),
        )
        // In a pick of several the file's name is in front, because the sentence does not carry it.
        assertEquals(
            "Pokemon FireRed (Germany): Added, but the tracker cannot read it. This is the German FireRed. The tracker reads the US English FireRed, v1.0 or v1.1. $playsWithout",
            LibraryImport.addedLine(german, several = true),
        )
    }

    @Test
    fun `a hack and a game of another kind say what they are and that they play`() {
        val lib = LibraryStore(tmp())
        val hack = lib.import("Radical Red.gba", LibraryRoms.gba("POKEMON FIRE", "BPRE", size = 16 * 1024 * 1024 + 1))
        assertEquals("Added Radical Red. A changed Pokémon FireRed (a ROM hack). It plays without the tracker.", LibraryImport.addedLine(hack))
        val mk = lib.import("Mario Kart DS.nds", LibraryRoms.ds("MARIO KART", "AMCE"))
        assertEquals(
            "Added Mario Kart DS. A DS game (MARIO KART), but not one the tracker reads. It still plays, without a tracker.",
            LibraryImport.addedLine(mk),
        )
    }

    // ---------------------------------------------------------------- the count

    @Test
    fun `a pick of several says how many went in`() {
        assertEquals("Added 4 of 5. 1 was skipped, see below.", LibraryImport.countLine(4, 1))
        assertEquals("Added 2 of 4. 2 were skipped, see below.", LibraryImport.countLine(2, 2))
        assertEquals("Added all 3.", LibraryImport.countLine(3, 0))
        assertEquals("Nothing was added. All 3 were skipped, see below.", LibraryImport.countLine(0, 3))
        assertNull(LibraryImport.countLine(1, 0), "one file needs no count")
        assertNull(LibraryImport.countLine(0, 1))
        assertNull(LibraryImport.countLine(0, 0))
    }

    /** The whole path a pick takes, file by file, as importUris does it: begin, each file, finish. */
    @Test
    fun `the count and the lines come out of a pick of five as the audit asked`() {
        val dir = tmp()
        val imp = importer()
        imp.begin(5)
        val lines = listOf(
            imp.importFile("Pokemon Emerald (U).gba", file(dir, "import-1", LibraryRoms.gba("POKEMON EMER", "BPEE", salt = 1))),
            imp.importFile("Pokemon FireRed (Germany).gba", file(dir, "import-2", LibraryRoms.gba("POKEMON FIRE", "BPRD"))),
            imp.importFile("Pokemon HeartGold.7z", file(dir, "import-3", LibraryRoms.sevenZip())),
            imp.importFile("Mario Kart DS.nds", file(dir, "import-4", LibraryRoms.ds("MARIO KART", "AMCE"))),
            imp.importFile("IMG_2041.jpg", file(dir, "import-5", ByteArray(5000) { (it * 31).toByte() })),
        )
        val said = assertNotNull(imp.finish(lines)).lines()
        // The Emerald is a synthetic file, so it is not the known build. It, the German FireRed and the DS game are added; the 7z and the photo are not.
        assertEquals("Added 3 of 5. 2 were skipped, see below.", said.first())
        assertTrue(said.any { it.startsWith("Pokemon HeartGold.7z: That is a .7z file.") }, "the file the line is about is named: $said")
        assertTrue(said.any { it.startsWith("IMG_2041.jpg is not a Game Boy, Game Boy Advance or DS game file") }, said.toString())
        assertTrue(said.any { it.startsWith("Pokemon FireRed (Germany): Added, but the tracker cannot read it.") }, said.toString())
        assertEquals(1 + 5, said.size, "the count, then one line for each file")
    }

    @Test
    fun `a single file is said in full, with no count and no name in front`() {
        val dir = tmp()
        val imp = importer()
        imp.begin(1)
        val line = imp.importFile("Pokemon FireRed (Germany).gba", file(dir, "import-1", LibraryRoms.gba("POKEMON FIRE", "BPRD")))
        assertEquals(
            "Added, but the tracker cannot read it. This is the German FireRed. The tracker reads the US English FireRed, v1.0 or v1.1. $playsWithout",
            imp.finish(listOf(line)),
        )
    }

    @Test
    fun `the same game twice is skipped and counted as skipped`() {
        val dir = tmp()
        val imp = importer()
        imp.begin(2)
        val a = imp.importFile("de.gba", file(dir, "import-1", LibraryRoms.gba("POKEMON FIRE", "BPRD")))
        val b = imp.importFile("de copy.gba", file(dir, "import-2", LibraryRoms.gba("POKEMON FIRE", "BPRD")))
        val said = assertNotNull(imp.finish(listOf(a, b))).lines()
        assertEquals("Added 1 of 2. 1 was skipped, see below.", said.first())
        assertTrue(said.any { "already in your library" in it }, said.toString())
    }

    @Test
    fun `every line follows the copy rules`() {
        val lib = LibraryStore(tmp())
        val lines = listOf(
            LibraryImport.archiveLine(".7z"), LibraryImport.archiveLine(".rar", "choose"),
            LibraryImport.addedLine(lib.import("a.gba", LibraryRoms.gba("POKEMON FIRE", "BPRD"))),
            LibraryImport.addedLine(lib.import("b.gba", LibraryRoms.gba("POKEMON FIRE", "BPRE", version = 1)), several = true),
            LibraryImport.addedLine(lib.import("c.nds", LibraryRoms.ds("MARIO KART", "AMCE"))),
            LibraryImport.countLine(4, 1)!!, LibraryImport.countLine(3, 0)!!, LibraryImport.countLine(0, 3)!!,
        )
        for (s in lines) {
            assertTrue('—' !in s && '–' !in s, "no dash: $s")
            assertFalse(Regex("\\bAI\\b").containsMatchIn(s), s)
            assertFalse(Regex("(?i)\\bcrc\\b|checksum|\\bdump\\b|verified").containsMatchIn(s), s)
        }
    }
}
