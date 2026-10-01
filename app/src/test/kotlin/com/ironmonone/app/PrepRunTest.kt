package com.ironmonone.app

import com.ironmonone.core.RomKind
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Patched versions made from the games already in the library (Blake, 2026-09-30: PATCH on his FireRed 1.1 said no
 * patch fits while the app carries Nat. Dex for it, Patched versions only took a file from the phone, and "this needs
 * applied to all games").
 */
class PrepRunTest {
    private fun ids(k: RomKind) = PrepRun.builtIns(k).map { it.id }

    @Test
    fun `every game is offered the patched versions KaizoCore can make of it, and never the game as it is`() {
        assertEquals(listOf("NATDEX", RomKind.FIRERED_V11_FASTER.id), ids(RomKind.FIRERED_U_V11).take(2))
        assertTrue("NATDEX" in ids(RomKind.EMERALD_U))
        for (k in RomKind.all) {
            assertFalse("STANDARD" in ids(k), k.id)
            assertEquals(PrepOptions.forKind(k).filter { it.mode != PrepOptions.Mode.STANDARD }.map { it.id }, ids(k), k.id)
        }
        // A build that is already patched has nothing more to make.
        assertEquals(emptyList(), ids(RomKind.FIRERED_NATDEX_121))
    }

    @Test
    fun `Nat Dex is offered on exactly the two games the Nat Dex Extension has patches for`() {
        val natDex = RomKind.all.filter { k -> PrepRun.builtIns(k).any { it.mode == PrepOptions.Mode.NATDEX } }.map { it.id }
        assertEquals(setOf(RomKind.FIRERED_U_V11.id, RomKind.EMERALD_U.id), natDex.toSet())
    }

    @Test
    fun `the Nat Dex version says what it adds, for each game`() {
        val fr = NatDexInfo.lines(RomKind.FIRERED_U_V11).joinToString(" ")
        val em = NatDexInfo.lines(RomKind.EMERALD_U).joinToString(" ")
        for (text in listOf(fr, em)) {
            assertTrue("faster battle engine" in text.lowercase(), text)
            assertTrue("hidden items sparkle" in text.lowercase(), text)
            assertTrue("every generation" in text, text)
        }
        assertTrue("Faster FireRed" in fr); assertFalse("Faster Emerald" in fr)
        assertTrue("Faster Emerald" in em); assertFalse("Faster FireRed" in em)
        assertEquals(NatDexInfo.SHORT, PrepOptions.describe(PrepRun.builtIns(RomKind.FIRERED_U_V11).first { it.mode == PrepOptions.Mode.NATDEX }))
        val all = (NatDexInfo.lines(RomKind.FIRERED_U_V11) + NatDexInfo.lines(RomKind.EMERALD_U) +
            listOf(NatDexInfo.TITLE, NatDexInfo.WHAT, NatDexInfo.CREDIT, NatDexInfo.SHORT)).joinToString(" ")
        assertFalse('—' in all, "no em dashes")
    }

    private fun src(name: String) = File("src/main/kotlin/com/ironmonone/app/$name").readText().replace("\r\n", "\n")

    @Test
    fun `the library's own file is never handed to the run, which moves or deletes what it is given`() {
        val lib = src("RomLibraryScreen.kt")
        val runBuiltIn = lib.substringAfter("fun runBuiltIn(").substringBefore("fun runPatch(")
        assertTrue("PrepRun.copyFromLibrary(context, base, progress)" in runBuiltIn, "a copy is made first")
        assertTrue("PrepRun.run(context, store, tmp," in runBuiltIn, "and the copy is what is run")
        assertFalse("base.file" in runBuiltIn, "the library file itself never reaches the run")
        // PATCH lists the built-ins, and the games that have a Nat. Dex version get the button.
        assertTrue("val builtIns = PrepRun.builtIns(e)" in lib)
        assertTrue("Gen3Button(\"NAT. DEX\"" in lib)
        // Patched versions offers the library's games, through a copy too.
        val prep = src("PrepareScreen.kt")
        assertTrue("PrepRun.builtIns(it).isNotEmpty()" in prep)
        assertTrue("PrepRun.copyFromLibrary(context, e, progress)" in prep)
        // Kaizo IronMON offers the Nat. Dex version under the game, made from a copy of the game picked there.
        val run = src("RunScreen.kt")
        val notice = run.substringAfter("NatDexInfo.buildOf(rom)").substringBefore("// Build your own")
        assertTrue("NatDexNotice(busy)" in notice)
        assertTrue("base.copyTo(tmp, overwrite = true)" in notice && "PrepRun.run(context, store, tmp, rom," in notice)
        assertFalse("PrepRun.run(context, store, base" in notice)
    }

    @Test
    fun `the Nat Dex build of each game is the one its patch makes`() {
        assertEquals(RomKind.FIRERED_NATDEX_121, NatDexInfo.buildOf(RomKind.FIRERED_U_V11))
        assertEquals(RomKind.EMERALD_NATDEX_121, NatDexInfo.buildOf(RomKind.EMERALD_U))
        for (k in RomKind.all) if (NatDexInfo.buildOf(k) != null) assertTrue(PrepRun.builtIns(k).any { it.mode == PrepOptions.Mode.NATDEX }, k.id)
        assertEquals(null, NatDexInfo.buildOf(RomKind.LEAFGREEN_U))
    }
}
