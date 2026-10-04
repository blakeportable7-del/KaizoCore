package com.ironmonone.app

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * rc32 audit P3 #4: the APK carried GPL-3.0, GPL-2.0, MPL-2.0, Apache-2.0 and MIT code and art, and one licence text,
 * the font's. Every licence NOTICE names ships whole now and About opens them (Licences).
 */
class LicencesTest {
    private val assets = File("src/main/assets")

    /** Each licence's text, as its own heading or first words say it. */
    private val heads = mapOf(
        "GPL-3.0" to listOf("GNU GENERAL PUBLIC LICENSE", "Version 3, 29 June 2007"),
        "GPL-2.0" to listOf("GNU GENERAL PUBLIC LICENSE", "Version 2, June 1991"),
        "MPL-2.0" to listOf("Mozilla Public License Version 2.0"),
        "Apache-2.0" to listOf("Apache License", "Version 2.0, January 2004"),
        "BSD-3-Clause-inih" to listOf("The \"inih\" library is distributed under the New BSD license", "Copyright (c) 2009, Ben Hoyt"),
        "PublicDomain-mGBA" to listOf("MurmurHash3 was written by Austin Appleby, and is placed in the public", "Igor Pavlov : Public domain"),
        "OFL-1.1" to listOf("SIL OPEN FONT LICENSE"),
        "CC-BY-NC-4.0" to listOf("Attribution-NonCommercial 4.0 International"),
        "DarkusShadow-Free-Use" to listOf("DarkusShadow's overworld sprites: free use with credit", "Give Credits If Used"),
    )

    @Test
    fun `every licence text the page names ships, and is that licence`() {
        for (t in Licences.texts) {
            val f = File(assets, t.asset)
            assertTrue(f.isFile && f.length() > 300, "${t.asset} ships")
            val text = f.readText().replace("\r\n", "\n")
            val expected = heads[t.id] ?: if (t.id.startsWith("MIT-")) listOf("Permission is hereby granted, free of charge", "Copyright") else error("no heading for ${t.id}")
            for (h in expected) assertTrue(h in text.take(if (t.id.startsWith("MIT-")) 4000 else 600), "${t.id}: $h")
        }
        // Each MIT project carries its own copyright line.
        assertTrue("Copyright (c) 2018 RetroAchievements.org" in File(assets, "licenses/MIT-rcheevos.txt").readText())
        assertTrue("Copyright 2022-2023 Besteon" in File(assets, "licenses/MIT-Ironmon-Tracker.txt").readText())
        assertTrue("Copyright (c) 2023 UTDZac" in File(assets, "licenses/MIT-CalcAtk.txt").readText())
        assertTrue("Copyright (c) 2023 Fellshadow" in File(assets, "licenses/MIT-AutoPokemonThemes.txt").readText())
        assertTrue("Copyright (c) 2026 UTDZac (Zeke)" in File(assets, "licenses/MIT-FavoritesAsSources.txt").readText())
        assertTrue("Copyright (c) 2011-2026 Guangcong Luo and other contributors" in File(assets, "licenses/MIT-PokemonShowdown.txt").readText())
        assertEquals(5, Regex("The RetroArch team").findAll(File(assets, "licenses/MIT-libretro-common.txt").readText()).count(), "each compiled file's statement")
    }

    @Test
    fun `every part is under a licence the page has, and every licence NOTICE lists is covered`() {
        val ids = Licences.texts.map { it.id }.toSet()
        for (p in Licences.parts) assertTrue(p.licence in ids, "${p.name}: ${p.licence}")
        for (t in Licences.texts) assertTrue(Licences.parts.any { it.licence == t.id }, "${t.id} covers nothing")
        // The licences NOTICE's LICENSED COMPONENTS name, and the libraries it lists, each have their text here.
        val notice = File("../NOTICE").readText().replace("\r\n", "\n")
        val listed = notice.substringAfter("LICENSED COMPONENTS").substringBefore("BUNDLED IN THE APK")
        for ((word, id) in listOf("MPL 2.0" to "MPL-2.0", "GPL-3.0" to "GPL-3.0", "GPL-2.0" to "GPL-2.0", "Apache-2.0" to "Apache-2.0",
            "SIL Open Font License 1.1" to "OFL-1.1", "CC BY-NC 4.0" to "CC-BY-NC-4.0", "- MIT" to "MIT-rcheevos")) {
            assertTrue(word in listed, "NOTICE lists $word")
            assertTrue(id in ids, "and the page has $id")
        }
        for (name in listOf("mGBA", "melonDS", "Gambatte", "LibretroDroid", "rcheevos", "CameraX", "Oboe", "Press Start 2P", "PMD Sprite Collab",
            "Universal Pokémon Randomizer ZX", "NDS IronMON Tracker", "Calc Atk", "Sprite Is Me", "Death Quotes", "Auto Pokémon Themes", "inih",
            "Favorites As Sources", "Showdown"))
            assertTrue(Licences.parts.any { name in it.name }, "the page names $name")
        // The GPL and MPL parts say where their source is.
        for (p in Licences.parts.filter { it.licence == "GPL-3.0" || it.licence == "GPL-2.0" || it.licence == "MPL-2.0" })
            assertTrue(p.source != null, "${p.name}: where is its source")
        assertTrue("LICENSE TEXTS IN THE APK" in notice, "NOTICE says they ship")
        assertTrue("CameraX 1.4.2 - Apache-2.0" in notice && "camera-camera2:1.4.2" in File("build.gradle.kts").readText(), "NOTICE names the CameraX that is built")
    }

    @Test
    fun `About opens the page, and its words keep the copy rules`() {
        val about = File("src/main/kotlin/com/ironmonone/app/AboutScreen.kt").readText()
        assertTrue("Gen3Button(\"Licenses\", Modifier.fillMaxWidth()) { licencesOpen = true }" in about)
        assertTrue("if (licencesOpen) LicencesDialog { licencesOpen = false }" in about)
        val page = File("src/main/kotlin/com/ironmonone/app/Licences.kt").readText()
        assertTrue("withContext(Dispatchers.IO) { Licences.read(context, t) }" in page, "the text is read off the main thread")
        val ours = listOf(Licences.INTRO, Licences.SOURCE) + Licences.parts.flatMap { listOf(it.name, it.what) } + Licences.texts.map { it.name }
        for (s in ours) {
            assertFalse(s.contains(0x2014.toChar()) || s.contains(0x2013.toChar()) || " - " in s, s)
            assertFalse(Regex("\\bAI\\b").containsMatchIn(s), s)
        }
        // A licence written in markdown shows its words, not its marks.
        assertEquals("Attribution\nSection 1 Definitions.\na. Adapted Material means", Licences.shown("# Attribution\r\n### Section 1 Definitions.\na. **Adapted Material** means"))
    }
}
