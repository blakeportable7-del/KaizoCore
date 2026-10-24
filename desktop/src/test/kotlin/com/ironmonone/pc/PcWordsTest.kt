package com.ironmonone.pc

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The update notice, the settings files, and the house rules for every word KaizoCore PC shows. */
class PcWordsTest {
    private val latest = """
        {
          "app": "KaizoCore",
          "versionCode": 52,
          "version": "1.0.0-rc38",
          "published": "2026-10-07",
          "apk": "https://willowcreek.group/kaizocore/KaizoCore-1.0.0-rc38.apk?v=5f9ad7f4",
          "page": "https://willowcreek.group/kaizocore"
        }
    """.trimIndent()

    @Test
    fun `the update notice names a newer release only`() {
        assertNull(Updates.notice(latest, 52), "the same release")
        assertNull(Updates.notice(latest, 53), "a newer jar than the site")
        val n = Updates.notice(latest, 51)!!
        assertEquals("1.0.0-rc38", n.version)
        assertEquals("https://willowcreek.group/kaizocore", n.page)
        assertEquals("KaizoCore 1.0.0-rc38 is out. Get the new KaizoCore PC from https://willowcreek.group/kaizocore", n.message)
        assertNull(Updates.notice(latest, 0), "a jar built outside a release (no version) says nothing")
        assertNull(Updates.notice("not json", 10))
    }

    @Test
    fun `a pc entry in latest_json wins over the APK's`() {
        val withPc = latest.replace("\"page\"", "\"pc\": { \"versionCode\": 50, \"version\": \"1.0.0-rc36\", \"page\": \"https://willowcreek.group/kaizocore#pc\" },\n  \"page\"")
        assertNull(Updates.notice(withPc, 51), "the PC release is older than this jar")
        assertEquals("https://willowcreek.group/kaizocore#pc", Updates.notice(withPc, 49)!!.page)
        assertNull(Updates.notice(latest.replace("https://willowcreek", "http://willowcreek"), 1), "only an https page is offered")
    }

    @Test
    fun `kaizocore-pc_ini and besteon's Settings_ini are read`() {
        val d = TestFiles.tempDir("ini")
        try {
            File(d, "kaizocore-pc.ini").writeText("# a note\nbuild=\nsettings=RSE NatDex v1.2 Survival\npool = vanilla\ntracker=\"C:\\T\"\n")
            assertEquals(mapOf("settings" to "RSE NatDex v1.2 Survival", "pool" to "vanilla", "tracker" to "C:\\T"), iniSettings(d))
            File(d, "Settings.ini").writeText("[config]\nX=1\n[controls]\nLoad_next_seed=L, R, Start\n[overrides]\nROMs_and_Logs=C:\\Roms\nAttempt_Counts=\n")
            val b = BesteonSettings(d)
            assertEquals("L, R, Start", b.combo)
            assertEquals(File("C:\\Roms"), b.romsDir)
            assertEquals(d, b.attemptsDir)
        } finally {
            d.deleteRecursively()
        }
    }

    /** Every line the streamer can read: the window, the setup, the notice, the README, the BizHawk script's messages. */
    private fun allWords(): List<String> {
        val pkg = File(TestFiles.repo, "desktop/package")
        val lua = File(pkg, "kaizocore-hns.lua").readText().lines()
            .filter { "say(" in it || "console.log(" in it }.flatMap { Regex("\"([^\"]*)\"").findAll(it).map { m -> m.groupValues[1] }.toList() }
        return PcSetup.allCopy + listOf(
            StatusWindow.WAITING, StatusWindow.MAKING, StatusWindow.STARTING,
            StatusWindow.statusLine(0, null, false, null), StatusWindow.statusLine(1, null, true, null),
            StatusWindow.statusLine(2, "x", true, "run.gba"), Session.ALREADY_RUNNING, StatusWindow.NO_GAME,
            Updates.Notice("1.0.0-rc39", 53, Updates.PAGE).message,
            File(pkg, "README.txt").readText(), File(pkg, "kaizocore-pc.ini").readText(), File(pkg, "KaizoCore PC.bat").readText(),
            // The chat's lines (row 9): the window's Twitch line while signed out, and what !fav answers.
            PcChat(TestFiles.tempDir("words").also { it.deleteOnExit() }, { null }, "1.0", {}, com.ironmonone.app.stream.twitch.MemoryTokenStore()).statusLine(),
            FavCommand({ _, _, _ -> "" }).help(), FavCommand.LIST_HELP, PcChat.STARTING, "The slot must be 1 to 9.",
            "No Pokemon called \"x\" in this run.", "Favorite #1 changed from (none) to Gengar.",
            "Which one? Charmander, Charmeleon, Charizard. Say !fav 5 and the full name.",
            "favorites: not a Pokemon in this run, kept the box as it was: zzzz",
        ) + lua
    }

    @Test
    fun `no em dash and no mention of AI in anything the streamer reads`() {
        val words = allWords()
        assertTrue(words.size > 20)
        for (w in words) {
            assertTrue('\u2014' !in w && '\u2013' !in w, "a dash in: ${w.take(80)}")
            assertTrue(!Regex("\\bAI\\b").containsMatchIn(w) && "artificial intelligence" !in w.lowercase(), "AI in: ${w.take(80)}")
        }
    }
}
