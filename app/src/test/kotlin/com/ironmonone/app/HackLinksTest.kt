package com.ironmonone.app

import com.ironmonone.core.RomKind
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
}
