package com.ironmonone.app

import com.ironmonone.core.Platform
import com.ironmonone.core.RomKind
import com.ironmonone.patch.RomIdentity.Verdict
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A file the tracker cannot read is told which case it is and goes on the shelf that fits (2026-09-30, UX audit
 * P0-11): another language or revision on "Other versions", not on "ROM hacks", which stays for games that were
 * changed. And a card says "Tracker works" or "No tracker" (the audit's Words table), never "verified" or "tracked".
 */
class LibraryShelvesTest {

    private fun store() = LibraryStore(Files.createTempDirectory("shelves").toFile())

    private val playsWithout = "It plays here without a tracker."

    private fun entry(kind: RomKind, crc: Long = kind.expectedCrc, verdict: Verdict? = Verdict.EXACT) =
        LibraryStore.Entry(File(kind.id + ".bin"), kind.id + ".bin", crc, kind, kind.platform, kind.displayName, verdict = verdict)

    @Test
    fun `a German FireRed goes on Other versions and says what does work`() {
        val lib = store()
        val e = lib.import("Pokemon FireRed (Germany).gba", LibraryRoms.gba("POKEMON FIRE", "BPRD"))
        assertEquals(LibraryStore.Category.OTHER_VERSIONS, e.category)
        assertEquals(Platform.GBA, e.platform, "it still plays")
        assertNull(e.kind)
        assertFalse(e.tracked)
        assertEquals(Verdict.OTHER_LANGUAGE, e.verdict)
        val said = "This is the German FireRed. The tracker reads the US English FireRed, v1.0 or v1.1. $playsWithout"
        assertEquals(said, e.summary)
        assertEquals(said, e.subtitle, "the card says it in full")
        // The shelf is still that after the sidecar is read back.
        assertEquals(LibraryStore.Category.OTHER_VERSIONS, lib.list().single().category)
    }

    @Test
    fun `another revision of the right language goes on Other versions and says which`() {
        val e = store().import("fr11.gba", LibraryRoms.gba("POKEMON FIRE", "BPRE", version = 1))
        assertEquals(LibraryStore.Category.OTHER_VERSIONS, e.category)
        assertEquals(
            "This is FireRed v1.1 (the file name may say Rev 1), but this file is not an exact copy. It may be trimmed or changed. $playsWithout",
            e.subtitle,
        )
    }

    @Test
    fun `a game bigger than any release is a ROM hack`() {
        val e = store().import("Radical Red.gba", LibraryRoms.gba("POKEMON FIRE", "BPRE", size = 16 * 1024 * 1024 + 1))
        assertEquals(LibraryStore.Category.HACK, e.category)
        assertEquals("A changed Pokémon FireRed (a ROM hack). It plays without the tracker.", e.subtitle)
        assertEquals(Platform.GBA, e.platform)
    }

    @Test
    fun `DS games sort the same way, by the game code and the checksum`() {
        val lib = store()
        val german = lib.import("hg-de.nds", LibraryRoms.ds("POKEMON HG", "IPKD"))
        assertEquals(LibraryStore.Category.OTHER_VERSIONS, german.category)
        assertNull(german.kind, "a German cartridge is not the US game because its title is")
        assertEquals(Platform.NDS, german.platform)
        val other = lib.import("hg-copy.nds", LibraryRoms.ds("POKEMON HG", "IPKE"))
        assertEquals(LibraryStore.Category.OTHER_VERSIONS, other.category, "the US game with another checksum is not a ROM hack either")
        assertEquals(RomKind.HEARTGOLD_U, other.kind)
        val black = lib.import("black.nds", LibraryRoms.ds("POKEMON B", "IRBO"))
        assertEquals(LibraryStore.Category.CLEAN, black.category, "a game whose copy is not pinned yet stays where it was")
        val mk = lib.import("mk.nds", LibraryRoms.ds("MARIO KART", "AMCE"))
        assertEquals(LibraryStore.Category.OTHER, mk.category)
        assertEquals("A DS game (MARIO KART), but not one the tracker reads. It still plays, without a tracker.", mk.subtitle)
        assertEquals(Platform.NDS, mk.platform)
    }

    @Test
    fun `a damaged DS file does not play and says what to do`() {
        val bad = LibraryRoms.ds("POKEMON HG", "IPKE").also { it[0x15E] = (it[0x15E].toInt() xor 0x55).toByte() }
        val e = store().import("bad.nds", bad)
        assertNull(e.platform)
        assertEquals("This DS file looks damaged (POKEMON HG), so it will not play. Copy the file to the phone again, then add it again.", e.subtitle)
    }

    @Test
    fun `a Game Boy game of another language or checksum is not a ROM hack either`() {
        val lib = store()
        val red = lib.import("red-copy.gb", LibraryRoms.gb("POKEMON RED", cgb = 0))
        assertEquals(LibraryStore.Category.OTHER_VERSIONS, red.category)
        assertEquals(Platform.GBC, red.platform)
        val gold = lib.import("gold-de.gbc", LibraryRoms.gb("POKEMON_GLDAAUD", cgb = 0x80))
        assertEquals(LibraryStore.Category.OTHER_VERSIONS, gold.category)
        assertEquals(
            "This is the German Pokémon Gold. The tracker reads the US English Pokémon Gold. $playsWithout",
            gold.subtitle,
        )
        assertEquals(LibraryStore.Category.OTHER, lib.import("tetris.gb", LibraryRoms.gb("TETRIS", cgb = 0)).category)
    }

    @Test
    fun `the shelves are named, in the order they are drawn, and the new one says what it holds`() {
        assertEquals(
            listOf("Clean ROMs", "Patched", "Other versions", "ROM hacks", "Other games"),
            LibraryStore.Category.entries.map { it.title },
        )
        assertEquals("Other versions", LibraryStore.Category.OTHER_VERSIONS.title)
        assertEquals(
            "Real Pokémon games the tracker does not read, such as another language or revision. They play without the tracker.",
            LibraryStore.Category.OTHER_VERSIONS.blurb,
        )
        assertEquals("ROM hacks", LibraryStore.Category.HACK.title, "ROM hacks stays, for games that were changed")
        for (c in LibraryStore.Category.entries) assertTrue('—' !in c.blurb && '–' !in c.blurb, c.blurb)
    }

    @Test
    fun `a build the tracker reads says Tracker works, and the card adds nothing to it`() {
        val emerald = entry(RomKind.EMERALD_U)
        assertTrue(emerald.verified && emerald.tracked)
        assertEquals(LibraryStore.Category.CLEAN, emerald.category)
        assertEquals("Pokémon Emerald (U) · Tracker works", emerald.subtitle)
        val natDex = entry(RomKind.EMERALD_NATDEX_121)
        assertEquals(LibraryStore.Category.PATCHED, natDex.category)
        assertEquals("Pokémon Emerald + Nat. Dex 1.2.1 · Tracker works", natDex.subtitle)
        // The Emerald that a patch made here and that is not a known build has no tracker, and says so.
        val made = LibraryStore.Entry(File("x.gba"), "x.gba", 7L, null, Platform.GBA, "s", baseName = "emerald-u.gba", patchName = "Kaizo.bps")
        assertEquals("Kaizo on emerald-u · No tracker", made.subtitle)
        assertEquals(LibraryStore.Category.HACK, made.category)
    }

    @Test
    fun `tracked is the same test the game session makes`() {
        for (k in RomKind.all) {
            for (crc in listOf(k.expectedCrc, 0L, 1L)) {
                val e = entry(k, crc = crc)
                assertEquals(GameSession.trackerKind(k, crc) != null, e.tracked, "${k.id} $crc")
            }
        }
    }

    @Test
    fun `no card line uses a word the audit retired`() {
        val lib = store()
        val entries = listOf(
            lib.import("a.gba", LibraryRoms.gba("POKEMON FIRE", "BPRD")), lib.import("b.gba", LibraryRoms.gba("POKEMON FIRE", "BPRE", version = 1)),
            lib.import("c.nds", LibraryRoms.ds("POKEMON B", "IRBO")), lib.import("d.nds", LibraryRoms.ds("MARIO KART", "AMCE")),
            lib.import("e.nds", LibraryRoms.ds("POKEMON HG", "IPKD")), lib.import("f.gbc", LibraryRoms.gb("POKEMON RED", cgb = 0)),
            entry(RomKind.EMERALD_U), entry(RomKind.EMERALD_NATDEX_121), entry(RomKind.HEARTGOLD_SUPERKAIZO),
            LibraryStore.Entry(File("g.gba"), "g.gba", 7L, null, Platform.GBA, "s", baseName = "b.gba", patchName = "p.bps"),
        )
        for (e in entries) {
            assertFalse(Regex("(?i)\\bverified\\b|\\btracked\\b|plays untracked|clean dump|\\bdump\\b|vanilla").containsMatchIn(e.subtitle), e.subtitle)
            assertTrue("Tracker works" in e.subtitle || "No tracker" in e.subtitle || "without a tracker" in e.subtitle || "without the tracker" in e.subtitle, "says whether the tracker works: ${e.subtitle}")
        }
    }

    // ---------------------------------------------------------------- refusing

    @Test
    fun `a file that is refused says why in words that fit`() {
        val lib = store()
        val bad = lib.import("bad.nds", LibraryRoms.ds("POKEMON HG", "IPKE").also { it[0x15E] = (it[0x15E].toInt() xor 0x55).toByte() })
        assertEquals(
            "This DS file looks damaged (POKEMON HG), so it will not play. Copy the file to the phone again, then add it again.",
            lib.refuse("bad.nds", bad),
        )
        assertFalse(bad.file.exists(), "it is not kept")
        val photo = lib.import("IMG_2041.jpg", ByteArray(5000) { (it * 31).toByte() })
        assertEquals(
            "IMG_2041.jpg is not a Game Boy, Game Boy Advance or DS game file, or the copy is damaged. Nothing was added.",
            lib.refuse("IMG_2041.jpg", photo),
        )
        // A game the tracker cannot read is kept: it plays.
        val german = lib.import("de.gba", LibraryRoms.gba("POKEMON FIRE", "BPRD"))
        assertNull(lib.refuse("de.gba", german))
        assertTrue(german.file.exists())
    }

    @Test
    fun `a file with no header that plays only by its extension does not say it is not a game beside a Play button`() {
        val e = store().import("homebrew.gba", ByteArray(0x2000) { 1 })
        assertEquals(Platform.GBA, e.platform)
        assertEquals("A GBA file with no readable header. It may still play, without a tracker.", e.subtitle)
        assertEquals(LibraryStore.Category.OTHER, e.category)
    }

    @Test
    fun `a rename suggestion for another copy of a game is the game's name, not a hack's`() {
        val lib = store()
        val other = lib.import("x.nds", LibraryRoms.ds("POKEMON HG", "IPKE"))
        val s = lib.suggestions(other)
        assertTrue("Pokémon HeartGold" in s, s.toString())
        assertTrue(s.none { "hack" in it }, "it is not a hack: $s")
    }

    // ---------------------------------------------------------------- the sidecar

    @Test
    fun `the verdict survives the sidecar`() {
        val dir = Files.createTempDirectory("sidecar").toFile()
        val lib = LibraryStore(dir)
        val e = lib.import("de.gba", LibraryRoms.gba("POKEMON FIRE", "BPRD"))
        assertEquals("v3", File(dir, "de.gba.meta").readLines().first())
        val back = LibraryStore(dir).list().single()
        assertEquals(e.verdict, back.verdict)
        assertEquals(e.summary, back.summary)
        assertEquals(e.category, back.category)
    }

    @Test
    fun `a v2 sidecar is read again from the header without hashing the file`() {
        val dir = Files.createTempDirectory("v2").toFile()
        val lib = LibraryStore(dir)
        lib.import("de.gba", LibraryRoms.gba("POKEMON FIRE", "BPRD"))
        // What the build before this wrote: version, checksum, kind, console, base, patch and the old sentence.
        // The checksum is not the file's own, so a file that was hashed again would show another one.
        val told = 0x1234ABCDL
        File(dir, "de.gba.meta").writeText(listOf("v2", "%08x".format(told), "-", "GBA", "base.gba", "patch.ips", "FireRed, but not a revision this app knows.").joinToString("\n"))
        val again = lib.list().single()
        assertEquals(told, again.crc, "the checksum the sidecar held was trusted: nothing was hashed again")
        assertEquals(Verdict.OTHER_LANGUAGE, again.verdict, "the verdict is read from the header")
        assertEquals("This is the German FireRed. The tracker reads the US English FireRed, v1.0 or v1.1. $playsWithout", again.summary)
        assertEquals("base.gba", again.baseName)
        assertEquals("patch.ips", again.patchName)
        assertEquals("v3", File(dir, "de.gba.meta").readLines().first(), "and it is written as v3 from then on")
    }

    @Test
    fun `a sidecar that cannot be read is made again, from the file`() {
        val dir = Files.createTempDirectory("v0").toFile()
        val lib = LibraryStore(dir)
        val e = lib.import("de.gba", LibraryRoms.gba("POKEMON FIRE", "BPRD"))
        File(dir, "de.gba.meta").writeText("not a sidecar")
        val again = lib.list().single()
        assertEquals(e.crc, again.crc, "hashed again, from the file")
        assertEquals(Verdict.OTHER_LANGUAGE, again.verdict)
        // A v3 sidecar cut short is not trusted either.
        File(dir, "de.gba.meta").writeText(listOf("v3", "00000001", "-", "GBA", "-", "-").joinToString("\n"))
        assertEquals(e.crc, lib.list().single().crc)
    }

    // ---------------------------------------------------------------- the empty page

    @Test
    fun `the empty page names every game the tracker reads and only those`() {
        val pinned = RomKind.allV1.filter { it.expectedCrc != RomKind.CRC_UNKNOWN }
            .map { it.displayName.substringAfter(' ').substringBefore(" (") }.toSet()
        assertEquals(pinned, LibraryStore.trackedGames().toSet(), "a game added to RomKind must be added to the list, and one with no pinned copy must not be on it")
        assertFalse("Black" in LibraryStore.trackedGames(), "Black has no pinned copy yet, so the tracker does not read it")
        assertEquals(LibraryStore.trackedGames().size, LibraryStore.trackedGames().toSet().size)
    }

    @Test
    fun `the empty page says what Add files really takes`() {
        assertEquals(
            "Add files takes games (.gba, .gbc, .gb, .nds), patches (.bps, .ips, .ups, .xdelta) and .zip files holding either. " +
                "The tracker reads the US English Red, Blue, Yellow, Gold, Silver, Crystal, Ruby, Sapphire, Emerald, FireRed, LeafGreen, " +
                "Diamond, Pearl, Platinum, HeartGold, SoulSilver, White, Black 2 and White 2. Any other game still plays, without a tracker.",
            emptyLibraryLine(),
        )
        assertEquals("Red, Blue and Yellow", listWithAnd(listOf("Red", "Blue", "Yellow")))
        assertEquals("Red and Blue", listWithAnd(listOf("Red", "Blue")))
        assertEquals("Red", listWithAnd(listOf("Red")))
    }

    /** The claim is checked against what the code takes: a zip gives up exactly these, and a header is what makes a game one. */
    @Test
    fun `and every file type it names is one the import takes`() {
        val zip = java.io.ByteArrayOutputStream().also { out ->
            java.util.zip.ZipOutputStream(out).use { z ->
                for (n in listOf("a.gba", "b.gbc", "c.gb", "d.nds", "e.bps", "f.ips", "g.ups", "h.xdelta", "readme.txt")) {
                    z.putNextEntry(java.util.zip.ZipEntry(n)); z.write(byteArrayOf(1, 2, 3)); z.closeEntry()
                }
            }
        }.toByteArray()
        assertEquals(listOf("a.gba", "b.gbc", "c.gb", "d.nds", "e.bps", "f.ips", "g.ups", "h.xdelta"), ZipImport.extract(zip).map { it.first })
        for (p in listOf("e.bps", "f.ips", "g.ups", "h.xdelta")) assertTrue(LibraryStore.looksLikePatch(p), p)
        // A Game Boy game is a .gb, and it is a game by its header, whatever the file is called.
        assertEquals(Platform.GBC, store().import("Pokemon Red.gb", LibraryRoms.gb("POKEMON RED", cgb = 0)).platform)
        // The empty page names each of these, and nothing else as a game file.
        val line = emptyLibraryLine()
        for (ext in listOf(".gba", ".gbc", ".gb", ".nds", ".bps", ".ips", ".ups", ".xdelta", ".zip")) assertTrue(ext in line, ext)
        for (ext in listOf(".7z", ".rar", ".rom", ".bin")) assertFalse(ext in line, ext)
        assertNotNull(line)
    }
}
