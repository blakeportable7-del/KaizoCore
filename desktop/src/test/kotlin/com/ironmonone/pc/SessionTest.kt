package com.ironmonone.pc

import com.ironmonone.app.engine.HnsEngine
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The session's file protocol with kaizocore-hns.lua, driven the way the script drives it (lua.req written to a temp name
 * and renamed, jar.resp read back by seq, ram.bin with its "KCRAM <gen>" header): hello for an unknown game, New Run and
 * its files named as besteon's, the run's own folder, the RAM reaching the tracker, PreviousAttempt, the "quit" file,
 * and a restarted KaizoCore PC finding an earlier run's folder by its hash. BizHawk itself is checked by the harnesses
 * in KaizoCore-pc-port/bizhawk/test.
 */
class SessionTest {
    private var seq = 100

    private fun parse(t: String) = t.lines().mapNotNull { l -> l.indexOf('=').takeIf { it > 0 }?.let { l.substring(0, it) to l.substring(it + 1) } }.toMap()

    private fun waitFor(ms: Long, what: String, cond: () -> Boolean) {
        val end = System.currentTimeMillis() + ms
        while (!cond()) {
            if (System.currentTimeMillis() > end) throw AssertionError("timed out waiting for $what")
            Thread.sleep(20)
        }
    }

    private fun request(dir: File, cmd: String, vararg fields: Pair<String, String>): Map<String, String> {
        seq++
        val resp = File(dir, "jar.resp")
        resp.delete()
        val tmp = File(dir, "lua.req.tmp")
        tmp.writeText((listOf("seq=$seq", "cmd=$cmd") + fields.map { "${it.first}=${it.second}" }).joinToString("\n", postfix = "\n"))
        assertTrue(tmp.renameTo(File(dir, "lua.req")))
        var r: Map<String, String> = emptyMap()
        waitFor(40_000, "the answer to $cmd") { resp.isFile && runCatching { parse(resp.readText()) }.getOrNull()?.also { r = it }?.get("seq") == "$seq" }
        return r
    }

    private fun session(build: ByteArray, root: File, events: SessionEvents = SessionEvents.NONE): Session {
        val settings = PcHns.settingsFile(PcHns.DEFAULT_PRESET)
        return Session(build, settings, PcHns.options(settings), HnsEngine.Pool.NATDEX, File(root, "runs"), File(root, "runs"),
            File(root, "session"), "A, B, Start", ahead = 1, keepLogs = 50, events = events)
    }

    @Test
    fun `New Run, the run's folder, the RAM and a restart, through the session's files`() {
        val comfort = TestFiles.comfortBuild ?: return println("SessionTest skipped: no .vendor/hns/hns-kaizo.gba")
        val build = PcHns.build(comfort.readBytes())
        val root = TestFiles.tempDir("session")
        val dir = File(root, "session")
        val runs = File(root, "runs")
        val prefix = "HnS ${PcHns.DEFAULT_PRESET}"
        var changes = 0
        try {
            // Before 2026-10-07 the favorites lived per seed and the options and theme per preset beside the runs: the
            // first start keeps them as the one standing copy each in the KaizoCore PC folder (here [root]).
            File(runs, "$prefix seeds/attempt 00000 seed 7").mkdirs()
            File(runs, "$prefix seeds/attempt 00000 seed 7/favorites.txt").writeText("Lucario,,,,,,,,")
            File(runs, "$prefix tracker options.txt").writeText("showBallPicker=true\n")
            val s = session(build, root, object : SessionEvents { override fun changed() { changes++ } })
            val t = Thread { s.run() }.apply { start() }
            waitFor(60_000, "a run made ahead") { File(dir, "jar.txt").isFile && parse(File(dir, "jar.txt").readText())["ready"] == "1" }
            val favorites = File(root, Session.FAVORITES)
            assertEquals("Lucario,,,,,,,,", favorites.readText(), "the seed folder's favorites kept as the one list")
            assertTrue(File(root, Session.OPTIONS).isFile, "the preset's options kept as the one copy")
            assertEquals(listOf("Lucario", "", "", "", "", "", "", "", ""), s.favoritesNow())
            // Set before any run (no game loaded): the editor's Save and !fav both work.
            File(dir, "input.tmp").writeText("seq=1\ncmd=favorites\nslots=gengar,lucario\n")
            assertTrue(File(dir, "input.tmp").renameTo(File(dir, "input.req")))
            waitFor(5_000, "the favorites set before any run") { favorites.readText() == "Gengar,Lucario,,,,,,," }
            assertEquals("Favorite #3 changed from (none) to Scyther.", s.setFavorite(3, "scyther", "SomeMod"))
            waitFor(5_000, "!fav before any run") { favorites.readText() == "Gengar,Lucario,Scyther,,,,,," }
            val jar = parse(File(dir, "jar.txt").readText())
            assertEquals("A, B, Start", jar["combo"])
            assertEquals("0", jar["attempts"])
            assertEquals(1, s.readyRuns)

            val unknown = request(dir, "hello", "hash" to "0123456789ABCDEF0123456789ABCDEF01234567", "path" to "", "quiet" to "1")
            assertEquals("0", unknown["ok"])
            assertEquals("KaizoCore PC cannot find this game's file. Load it with New Run.", unknown["why"])

            // The window's New Run (first start, no game open): refused until BizHawk's script says it runs (lua.txt
            // alive, about once a second, with or without a game), then handed to the script as jar.cmd newrun.
            assertTrue(s.requestNewRun().startsWith("BizHawk's script is not running"))
            File(dir, "lua.txt").writeText("battlestate=0\ngame=none\nalive=${System.currentTimeMillis() / 1000}\n")
            waitFor(3_000, "the script seen running") { s.scriptRunning }
            assertEquals("none", s.scriptGame)
            assertEquals("Opening the next run in BizHawk.", s.requestNewRun())
            waitFor(3_000, "jar.cmd newrun") { File(dir, "jar.cmd").isFile && parse(File(dir, "jar.cmd").readText())["cmd"] == "newrun" }
            assertTrue((parse(File(dir, "jar.cmd").readText())["at"]?.toLong() ?: 0) >= System.currentTimeMillis() / 1000 - 5)
            File(dir, "jar.cmd").delete()

            val first = request(dir, "newrun")
            assertEquals("1", first["ok"], "$first")
            assertEquals("1", first["attempt"])
            val auto = File(runs, "$prefix AutoRandomized.gba")
            assertEquals(auto.absolutePath, first["path"])
            assertTrue(auto.isFile && File(auto.path + ".log").isFile)
            assertEquals(PcHns.sha1(auto.readBytes()), first["sha1"])
            assertEquals("1", File(runs, "$prefix Attempts.txt").readText().trim())
            val seedDir = File(runs, "$prefix seeds/attempt 00001 seed ${first["seed"]}")
            val runTxt = parse(File(seedDir, "run.txt").readText())
            assertEquals(first["sha1"], runTxt["sha1"])
            assertEquals("F4E28E3D", runTxt["build"])
            assertTrue(File(seedDir, "$prefix attempt 00001.gba").isFile)
            waitFor(5_000, "the tracker for attempt 1") { s.tracking == auto.name }
            assertEquals(1, s.attempt)

            // The script's hello for the game it just opened: tracked, same gen.
            val hello = request(dir, "hello", "hash" to first["sha1"]!!, "path" to auto.path, "quiet" to "1")
            assertEquals(listOf("1", first["gen"]), listOf(hello["ok"], hello["gen"]))

            // A RAM snapshot for that game reaches the tracker (an empty party: the game has not been played).
            val ram = File(dir, "ram.tmp")
            ram.outputStream().use { o -> o.write("KCRAM ${first["gen"]}\n".toByteArray()); o.write(ByteArray(0x40000 + 0x8000)) }
            assertTrue(ram.renameTo(File(dir, "ram.bin")))
            waitFor(5_000, "tracker.txt") { File(dir, "tracker.txt").isFile }
            assertTrue("party: none" in File(dir, "tracker.txt").readText())
            assertFalse(File(dir, "ram.bin").exists(), "the snapshot was taken")

            // The panel (row 4): named in panel.txt, a 150x160 PNG beside it, drawn again when what it shows changed.
            val panelTxt = parse(File(dir, "panel.txt").readText())
            val png = File(dir, panelTxt.getValue("file"))
            val img = javax.imageio.ImageIO.read(png)
            assertEquals(150 to 160, img.width to img.height)
            // A note from the script's note window (row 5): kept in attempt 1's folder.
            File(dir, "input.tmp").writeText("seq=1\ncmd=note\nspecies=245\ntext=hello note\\nsecond line\n")
            assertTrue(File(dir, "input.tmp").renameTo(File(dir, "input.req")))
            waitFor(5_000, "the note taken") { !File(dir, "input.req").exists() }

            // Favorites (row 8): the editor's Save settles each box as the drop-down would and keeps them as the one
            // list (never in the run's folder); the drop-down's names come by request; !fav sets one box.
            assertTrue(File(runs, "$prefix seeds/attempt 00001 seed ${first["seed"]}").isDirectory)
            assertFalse(File(seedDir, "favorites.txt").exists(), "a seed folder holds no favorites")
            File(dir, "input.tmp").writeText("seq=2\ncmd=favorites\nslots=gengar,char,mr mime,zzzz\n")
            assertTrue(File(dir, "input.tmp").renameTo(File(dir, "input.req")))
            // "zzzz" names no Pokemon: box 4 keeps what it held (nothing), never the typo.
            waitFor(5_000, "the favorites taken") { favorites.readText() == "Gengar,Charmander,Mr. Mime,,,,,," }
            assertFalse(File(seedDir, "favorites.txt").exists())
            assertTrue(request(dir, "suggest", "text" to "char", "quiet" to "1")["names"]!!.startsWith("Charmander|Charmeleon|Charizard|Charjabug|Charcadet|"))
            assertEquals("Favorite #4 changed from (none) to Pikachu.", s.setFavorite(4, "pikachu"))
            assertEquals("The slot must be 1 to 9.", s.setFavorite(10, "pikachu"))
            assertEquals("No Pokemon called \"qqq\" in this run.", s.setFavorite(5, "qqq"))
            assertEquals("Which one? Charmander, Charmeleon, Charizard. Say !fav 5 and the full name.", s.setFavorite(5, "char"),
                "chat is held to one Pokemon, not the drop-down's first")
            waitFor(5_000, "the favorite written on the session's thread") { favorites.readText() == "Gengar,Charmander,Mr. Mime,Pikachu,,,,," }
            assertEquals("Favorite #1 changed from Gengar to Gengar.", s.setFavorite(1, "gengr"), "one letter off is still the one Pokemon, its Mega form no reason to ask")
            assertEquals("Favorites > #1 Gengar, #2 Charmander, #3 Mr. Mime, #4 Pikachu, #5 (none), #6 (none), #7 (none), #8 (none), #9 (none)", s.favoritesLine())
            val history = File(root, Session.FAVORITES_HISTORY).readLines()
            assertTrue(history.any { "\tSomeMod\t#3 (none) -> Scyther" in it }, history.joinToString("\n"))
            assertTrue(history.any { "\tthe editor\t#2 Lucario -> Charmander; #3 Scyther -> Mr. Mime" in it }, history.joinToString("\n"))
            assertTrue(history.last().endsWith("\tchat\t#4 (none) -> Pikachu"), history.joinToString("\n"))
            // The chat commands' snapshot: the attempt and the game, no party yet.
            assertTrue(s.snapshotJson().contains("\"attempt\":1") && s.snapshotJson().contains("\"party\":[]"), s.snapshotJson())

            waitFor(60_000, "the second run made ahead") { s.readyRuns == 1 }
            val second = request(dir, "newrun")
            assertEquals("2", second["attempt"])
            // New Run keeps the one list: unchanged, and still none in the new run's folder.
            val seedDir2 = File(runs, "$prefix seeds/attempt 00002 seed ${second["seed"]}")
            assertFalse(File(seedDir2, "favorites.txt").exists())
            assertEquals("Gengar,Charmander,Mr. Mime,Pikachu,,,,,", favorites.readText())
            assertEquals("Pikachu", s.favoritesNow()[3])
            val prev = File(runs, "$prefix PreviousAttempt.gba")
            assertEquals(first["sha1"], PcHns.sha1(prev.readBytes()), "attempt 1 is kept as PreviousAttempt")
            assertTrue(changes >= 3, "the window heard $changes changes")

            File(dir, "quit").writeText("")
            t.join(15_000)
            assertFalse(t.isAlive)
            assertEquals(listOf("jar.log"), dir.list()!!.toList(), "the session folder is left with its log only")
            assertTrue(seedDir.listFiles()!!.any { f -> f.isFile && "hello note" in f.readText(Charsets.ISO_8859_1) }, "the note is in the run's folder: ${seedDir.list()!!.toList()}")
            assertFalse(File(runs, ".kaizocore-next").exists(), "the runs made ahead are gone")

            // KaizoCore PC started again: attempt 1's game, opened from its own folder, is that run.
            val again = session(build, root)
            val t2 = Thread { again.run() }.apply { start() }
            waitFor(60_000, "the restarted session") { File(dir, "jar.txt").isFile }
            val back = request(dir, "hello", "hash" to first["sha1"]!!, "path" to "", "quiet" to "1")
            assertEquals("1", back["ok"], "$back")
            assertNotNull(again.tracking)
            assertTrue(File(dir, "jar.log").readText().contains("run folder \"attempt 00001 seed ${first["seed"]}\""))
            assertEquals("hello note second line", RunData(seedDir).note(245), "attempt 1's note, back after the restart")
            again.stop()
            t2.join(15_000)
            assertFalse(t2.isAlive)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `a second KaizoCore PC for the same folder does not start`() {
        val root = TestFiles.tempDir("twice")
        try {
            val dir = File(root, "session").apply { mkdirs() }
            File(dir, "jar.txt").writeText("alive=${System.currentTimeMillis() / 1000}\n")
            val s = Session(ByteArray(0), PcHns.settingsFile(PcHns.DEFAULT_PRESET), PcHns.options(PcHns.settingsFile(PcHns.DEFAULT_PRESET)),
                HnsEngine.Pool.NATDEX, File(root, "runs"), File(root, "runs"), dir, "A, B, Start", 1)
            assertFalse(s.run())
        } finally {
            root.deleteRecursively()
        }
    }
}
