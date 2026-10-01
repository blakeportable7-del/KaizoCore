package com.ironmonone.app

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The Game Boy trackers (Gen1Tracker, GbcTracker) follow the Gen 1 and Gen 2
 * IronMON trackers, so NOTICE and the About screen name them and their
 * licence, as they name besteon's. Until 2026-09-29 neither did (parity
 * audit, gen1-0315).
 */
class CreditsTest {
    private val trackers = listOf(
        Triple("Sannji (mollo010)", "https://github.com/mollo010/Ironmon-gen-tracker", "Ironmon-gen-tracker"),
        Triple("seadogstingray", "https://github.com/seadogstingray/Ironmon-gen-2-tracker", "Ironmon-gen-2-tracker"),
    )

    @Test
    fun `NOTICE and the About screen credit both Game Boy trackers, under the licence their repositories state`() {
        val notice = File("../NOTICE").readText().replace("\r\n", "\n")
        val about = File("src/main/kotlin/com/ironmonone/app/AboutScreen.kt").readText().replace("\r\n", "\n")
        for ((who, url, repo) in trackers) {
            assertTrue("$repo - MIT   $url" in notice, "NOTICE names $repo, its licence and its link")
            assertTrue(who in notice && who in about, "$who in NOTICE and About")
            assertTrue("\"$url\"" in about, "About links $url")
        }
        assertTrue("CreditLink(GEN1_TRACKER_URL)" in about && "CreditLink(GEN2_TRACKER_URL)" in about)
        assertTrue("used under \" +\n                \"the MIT licence." in about, "About states the licence")
        // Both LICENSE.txt files are besteon's MIT text, carried over by the forks.
        assertEquals(2, Regex("\"Copyright 2022-2023 Besteon\"").findAll(notice).count())
    }

    /**
     * The rc30 credits check (Blake, 2026-09-29: every author named): whoever's work ships is
     * named on the About screen with a link and in NOTICE with the licence. The DS tracker, Calc
     * Atk, Auto Pokemon Themes, the randomizer and its Nat. Dex fork, the cores' front end, the
     * controls, rcheevos, the font and the sprites were in one of the two or neither.
     */
    @Test
    fun `every shipped author is named in About and NOTICE`() {
        val notice = File("../NOTICE").readText().replace("\r\n", "\n")
        val about = File("src/main/kotlin/com/ironmonone/app/AboutScreen.kt").readText().replace("\r\n", "\n")
        val credits = listOf(
            // who (as About names them), About's link, NOTICE's line with the licence
            Triple("Brian0255", "https://github.com/Brian0255/NDS-Ironmon-Tracker", "NDS-Ironmon-Tracker (Brian0255) - GPL-3.0"),
            Triple("UTDZac", "https://github.com/UTDZac/CalcAtk-IronmonExtension", "UTDZac / CalcAtk-IronmonExtension - MIT"),
            Triple("Fellshadow", "https://github.com/Fellshadow/Ironmon-Tracker-AutoPokemonThemes", "Fellshadow / Ironmon-Tracker-AutoPokemonThemes - MIT"),
            Triple("Ajarmar", "https://github.com/Ajarmar/universal-pokemon-randomizer-zx", "Universal Pokemon Randomizer ZX — GPL-3.0"),
            Triple("CyanSixFour", "https://github.com/CyanSMP64/universal-pokemon-randomizer-zx", "CyanSMP64 randomizer fork — GPL-3.0"),
            Triple("Swordfish90", "https://github.com/Swordfish90/LibretroDroid", "LibretroDroid 0.13.2 — GPL-3.0"),
            Triple("RadialGamePad", "https://github.com/Swordfish90/RadialGamePad", "Swordfish90/RadialGamePad — GPL-3.0"),
            Triple("rcheevos", "https://github.com/RetroAchievements/rcheevos", "rcheevos - MIT"),
            Triple("CodeMan38", "", "Press Start 2P - SIL Open Font License 1.1"),
            Triple("PMD Sprite", "https://sprites.pmdcollab.org", "PMD Sprite Collab - CC BY-NC 4.0"),
            Triple("Gambatte", "", "Gambatte libretro core - GPL-2.0"),
        )
        for ((who, link, line) in credits) {
            assertTrue(who in about, "About names $who")
            if (link.isNotEmpty()) assertTrue("\"$link\"" in about, "About links $link")
            assertTrue(line in notice, "NOTICE has: $line")
        }
        assertTrue("Dabomstew" in about, "the original randomizer's author")
    }

    /** Where the reference checkouts are on this machine, the licences NOTICE states are the repositories' own. */
    @Test
    fun `the licences NOTICE states are the ones in the repositories`() {
        val refs = File(System.getProperty("user.home"), "ironmon-ref")
        for ((repo, file, head) in listOf(
            Triple("CalcAtk-IronmonExtension", "LICENSE", "MIT License"),
            Triple("Ironmon-Tracker-AutoPokemonThemes", "LICENSE", "MIT License"),
            Triple("NDS-Ironmon-Tracker", "LICENSE.md", "GNU GENERAL PUBLIC LICENSE"),
        )) {
            val f = File(refs, "$repo/$file").takeIf { it.isFile } ?: continue
            assertTrue(head in f.readText().take(200), "$repo: $head")
        }
        assertTrue(File("../libretrodroid/src/main/cpp/rcheevos/LICENSE").readText().startsWith("MIT License"), "rcheevos")
    }

    /** Where the reference checkouts are on this machine, NOTICE quotes what their LICENSE.txt says. */
    @Test
    fun `the quoted copyright line is the one in each repository's LICENSE`() {
        val refs = File(System.getProperty("user.home"), "ironmon-ref")
        for ((_, _, repo) in trackers) {
            val license = File(refs, "$repo/LICENSE.txt").takeIf { it.isFile } ?: continue
            val text = license.readText()
            assertTrue(text.startsWith("Copyright 2022-2023 Besteon"), repo)
            assertTrue("Permission is hereby granted, free of charge" in text, "$repo is the MIT text")
        }
    }

    /**
     * The game over lines the player can add (2026-09-30) follow UTDZac's Death Quotes extension. It ships no code here
     * (the idea is written again in Kotlin), but it is credited in NOTICE and on the About screen all the same, under
     * the MIT licence its own repository states.
     */
    @Test
    fun `NOTICE and the About screen credit UTDZac for the Death Quotes idea, under its MIT licence`() {
        val notice = File("../NOTICE").readText().replace("\r\n", "\n")
        val about = File("src/main/kotlin/com/ironmonone/app/AboutScreen.kt").readText().replace("\r\n", "\n")
        val repo = "https://github.com/UTDZac/DeathQuotes-IronmonExtension"
        assertTrue("UTDZac / DeathQuotes-IronmonExtension - MIT   $repo" in notice, "NOTICE names the extension, its licence and its link")
        assertTrue("\"Copyright (c) 2023 UTDZac\"" in notice, "NOTICE quotes the copyright line the repository carries")
        assertTrue("\"$repo\"" in about && "CreditLink(\"$repo\")" in about, "About links it")
        assertTrue("Death Quotes extension by UTDZac, used under the \" +\n                \"MIT licence." in about, "About states the licence")
        assertTrue(about.indexOf("Death Quotes") in about.indexOf("\"Credits\"") until about.indexOf("This is not an official IronMON"), "in the Credits section")
        // Where the checkout is on this machine, what NOTICE states is the repository's own.
        val license = File(System.getProperty("user.home"), "ironmon-ref/DeathQuotes-IronmonExtension/LICENSE").takeIf { it.isFile } ?: return
        val text = license.readText()
        assertTrue(text.startsWith("MIT License"), "the licence is MIT")
        assertTrue("Copyright (c) 2023 UTDZac" in text)
        // The extension keeps no lines of its own, which is why NOTICE says the built-in ones are the tracker's.
        val lua = File(license.parentFile, "DeathQuotes.lua").takeIf { it.isFile } ?: return
        assertTrue("self.DefaultQuotes = {}" in lua.readText() && "Resources.GameOverScreenQuotes" in lua.readText())
    }

    /**
     * The FireRed and LeafGreen map and hidden item pictures (2026-09-29) are Bill Greenwald's, from IronMon Emu,
     * used with his permission for the pictures only. NOTICE and the About screen credit him, and the folder's
     * SOURCE.txt says where every file came from.
     */
    @Test
    fun `NOTICE and the About screen credit Bill Greenwald for the FireRed and LeafGreen pictures`() {
        val notice = File("../NOTICE").readText().replace("\r\n", "\n")
        val about = File("src/main/kotlin/com/ironmonone/app/AboutScreen.kt").readText().replace("\r\n", "\n")
        val source = File("src/main/assets/frlg/SOURCE.txt").readText()
        assertTrue(
            "FireRed and LeafGreen dungeon maps: Bill Greenwald (doctrDNA), from IronMon Emu " +
                "(github.com/billgreenwald/ironmon_emu), used with permission." in notice,
            "NOTICE carries the credit line",
        )
        // The permission covers the pictures and nothing else, and NOTICE says so.
        val flat = notice.replace(Regex("\\s+"), " ")
        assertTrue("It does NOT cover his Kotlin code, and none of it is used" in flat, "the scope of the permission")
        assertTrue("app/src/main/assets/frlg/SOURCE.txt" in flat)
        assertFalse("[ ] 6. ironmon_emu" in notice, "condition 6 is no longer an unchecked box that says nothing of theirs is used")
        // The About screen, in its own words, with a link.
        assertTrue(
            "\"The FireRed and LeafGreen dungeon maps are by Bill Greenwald (doctrDNA), \" + " +
                "\"from IronMon Emu, used with permission.\"" in about.replace(Regex("\\s+"), " "),
            "About names him and says with permission",
        )
        assertTrue("\"https://github.com/billgreenwald/ironmon_emu\"" in about, "About links IronMon Emu")
        assertTrue("CreditLink(IRONMON_EMU_URL)" in about)
        assertTrue(about.indexOf("Bill Greenwald") in about.indexOf("\"Credits\"") until about.indexOf("This is not an official IronMON"), "in the Credits section")
        // The folder's own record of where the files came from.
        assertTrue("https://github.com/billgreenwald/ironmon_emu" in source && "abf9453ba031a2ff58ab9a11059cb8678aec15e1" in source)
        assertTrue("Bill Greenwald (doctrDNA)" in source)
    }
}
