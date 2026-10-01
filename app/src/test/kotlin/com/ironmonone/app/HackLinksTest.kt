package com.ironmonone.app

import com.ironmonone.core.PatchFormat
import com.ironmonone.core.RomKind
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The ROM hack list: real games, creators' own pages only, and honest fit warnings. */
class HackLinksTest {

    /** Forums, code hosts and creator pages. A ROM mirror (pokeharbor, pokemoncoders...) must never appear. */
    private val hosts = setOf("www.pokecommunity.com", "github.com", "projectpokemon.org", "gbatemp.net", "ko-fi.com", "x.com")

    @Test
    fun `every link names a real game and a creator page`() {
        val known = RomKind.all.map { it.id }.toSet()
        for (l in HackLinks.all) {
            assertTrue(l.ids.all { it in known }, "${l.name}: ${l.ids} is not a RomKind id")
            assertTrue(l.url.startsWith("https://"), l.url)
            val host = java.net.URI(l.url).host
            assertTrue(host in hosts, "${l.name}: $host is not on the creator-page list")
            assertTrue('—' !in l.blurb && '—' !in l.name, "${l.name}: no em dashes")
        }
    }

    @Test
    fun `every game offered has hacks and every hack has a game`() {
        assertTrue(HackLinks.families.isNotEmpty())
        for (f in HackLinks.families) assertTrue(HackLinks.forFamily(f.key).isNotEmpty(), f.key)
        val covered = HackLinks.families.flatMap { HackLinks.forFamily(it.key) }.toSet()
        assertEquals(HackLinks.all.toSet(), covered, "a hack whose game has no chip can never be seen")
    }

    @Test
    fun `a FireRed 1_0 hack warns on FireRed 1_1 and not on 1_0`() {
        val rr = HackLinks.all.first { it.name == "Radical Red" }
        assertNotNull(HackLinks.mismatch(rr, RomKind.FIRERED_U_V11))
        assertNull(HackLinks.mismatch(rr, RomKind.FIRERED_U_V10))
        // A hack for another game says nothing about this one.
        assertNull(HackLinks.mismatch(rr, RomKind.EMERALD_U))
    }

    @Test
    fun `an already patched copy is told to patch the original`() {
        val kaizo = HackLinks.all.first { it.name == "Emerald Kaizo" }
        val msg = HackLinks.mismatch(kaizo, RomKind.EMERALD_SMARTAI)
        assertNotNull(msg)
        assertTrue("original" in msg, msg)
        assertEquals("emerald", HackLinks.familyOf("emerald-natdex-121"))
    }

    @Test
    fun `only the pages that carry game files are flagged`() {
        assertEquals(listOf("Polished Crystal"), HackLinks.all.filter { it.fullRomPage }.map { it.name })
    }

    // ------------------------------------------------ why none of the player's patches fits (2026-09-30, UX audit P1)

    private fun game(kind: RomKind, crc: Long = kind.expectedCrc, name: String = kind.id + ".gba") =
        LibraryStore.Entry(File(name), name, crc, kind, kind.platform, "a game")

    private fun patch(name: String, madeFor: RomKind) =
        LibraryStore.PatchEntry(File(name), name, PatchFormat.BPS, madeFor.expectedCrc, null, madeFor.displayName)

    @Test
    fun `a FireRed 1_1 with a Radical Red patch for 1_0 is told why, with the hack's base next to its own`() {
        val why = HackLinks.noFitReason(game(RomKind.FIRERED_U_V11), listOf(patch("Radical Red 4.1.bps", RomKind.FIRERED_U_V10)))
        assertEquals("Radical Red needs FireRed 1.0 (US). Your FireRed is 1.1, so it will not fit. Add a FireRed 1.0 file to use it.", why)
        // And the other way round: FireRed 1.0 with a patch for 1.1.
        assertEquals(
            "Faster FireRed needs FireRed 1.1 (US). Your FireRed is 1.0, so it will not fit. Add a FireRed 1.1 file to use it.",
            HackLinks.noFitReason(game(RomKind.FIRERED_U_V10), listOf(patch("Faster FireRed 1.3.2.ips", RomKind.FIRERED_U_V11))),
        )
    }

    @Test
    fun `the hack is named by its own page when the file name says so, by the longest name, else by the file`() {
        fun say(file: String, base: RomKind, mine: RomKind) = HackLinks.noFitReason(game(mine), listOf(patch(file, base)))!!
        // "Blaze Black 2 Redux" holds "Blaze Black" too: the longer is the hack.
        assertTrue(say("Blaze Black 2 Redux v1.xdelta", RomKind.BLACK2_U, RomKind.BLACK2_U.copy(expectedCrc = 1L)).startsWith("Blaze Black 2 Redux needs Black 2 (US)."))
        // A name that is on no page is the file's own, without its extension.
        assertTrue(say("my great hack.bps", RomKind.FIRERED_U_V10, RomKind.FIRERED_U_V11).startsWith("my great hack needs FireRed 1.0 (US)."))
    }

    @Test
    fun `a patch for the game picked was made from is told to be used on the original, and a trimmed copy is told it is not exact`() {
        val natDex = game(RomKind.EMERALD_NATDEX_121)
        assertEquals(
            "Emerald Kaizo needs Emerald (US). The game you picked is already patched, so it will not fit. Pick your original Emerald instead.",
            HackLinks.noFitReason(natDex, listOf(patch("Emerald Kaizo 5.bps", RomKind.EMERALD_U))),
        )
        // The same game by its header, with another checksum: an Emerald, not an exact copy of one.
        val trimmed = game(RomKind.EMERALD_U, crc = 1L)
        assertEquals(
            "Emerald Kaizo needs Emerald (US). The file you picked is not an exact copy of it, so it will not fit. Add an Emerald file to use it.",
            HackLinks.noFitReason(trimmed, listOf(patch("Emerald Kaizo 5.bps", RomKind.EMERALD_U))),
        )
    }

    @Test
    fun `nothing is said about a family the player has no patch for, or a game the app does not know`() {
        assertNull(HackLinks.noFitReason(game(RomKind.FIRERED_U_V11), listOf(patch("Crystal Kaizo.bps", RomKind.CRYSTAL_U))))
        assertNull(HackLinks.noFitReason(game(RomKind.FIRERED_U_V11), emptyList()))
        // No kind at all (another language, an unknown game): the caller says "none is made for it".
        val unknown = LibraryStore.Entry(File("x.gba"), "x.gba", 5L, null, com.ironmonone.core.Platform.GBA, "a game")
        assertNull(HackLinks.noFitReason(unknown, listOf(patch("Radical Red.bps", RomKind.FIRERED_U_V10))))
        // A patch whose game is no game this app knows (an IPS declared for a file of the player's own).
        val strange = LibraryStore.PatchEntry(File("a.ips"), "a.ips", PatchFormat.IPS, 12345L, null, "some file")
        assertNull(HackLinks.noFitReason(game(RomKind.FIRERED_U_V11), listOf(strange)))
    }

    @Test
    fun `the reasons follow the copy rules`() {
        val reasons = listOf(
            HackLinks.noFitReason(game(RomKind.FIRERED_U_V11), listOf(patch("Radical Red.bps", RomKind.FIRERED_U_V10))),
            HackLinks.noFitReason(game(RomKind.EMERALD_NATDEX_121), listOf(patch("Kaizo.bps", RomKind.EMERALD_U))),
            HackLinks.noFitReason(game(RomKind.EMERALD_U, crc = 1L), listOf(patch("Kaizo.bps", RomKind.EMERALD_U))),
        )
        for (r in reasons) {
            assertNotNull(r)
            assertTrue('—' !in r && '–' !in r && r.endsWith("."), r)
            assertTrue(Regex("(?i)\\bcrc\\b|checksum|[0-9a-f]{8}").containsMatchIn(r).not(), "no checksum talk: $r")
        }
    }
}
