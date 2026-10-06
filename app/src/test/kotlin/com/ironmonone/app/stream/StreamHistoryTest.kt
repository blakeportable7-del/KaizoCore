package com.ironmonone.app.stream

import com.ironmonone.app.RunRecord
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The run history page for viewers (StreamHistory, Blake's streamer list item 4): what ends runs most, the best run,
 * one game and mode at a time, only the run facts, behind the token like every other route.
 */
class StreamHistoryTest {

    private val kaizo = "FRLG Kaizo.rnqs"

    private fun run(
        attempt: Int, outcome: RunRecord.Outcome = RunRecord.Outcome.LOST, badges: Int = 0, killer: String? = null, level: Int = 10,
        trainer: String = "", location: String = "", ruleset: String = kaizo, seed: String = "00000000deadbeef",
    ) = RunRecord(attempt = attempt, seed = seed, ruleset = ruleset, started = 1000L * attempt, ended = 1000L * attempt + 500,
        playSeconds = 60 * attempt, outcome = outcome, badges = badges, lead = RunRecord.Mon(1, "Bulbasaur", 5),
        killer = killer?.let { RunRecord.Mon(74, it, level) }, trainer = trainer, location = location, restores = 3)

    private val runs = listOf(
        run(1, killer = "Geodude", trainer = "Leader Brock", location = "Pewter Gym"),
        run(2, killer = "Rattata", location = "Route 1"),
        run(3, killer = "Geodude", trainer = "Leader Brock", location = "Pewter Gym", badges = 0),
        run(4, badges = 3, killer = "Gengar", trainer = "Rival Gary", location = "Pokemon Tower"),
        run(5, outcome = RunRecord.Outcome.ENDED, badges = 1, location = "Viridian City"),
        run(6, badges = 3, killer = "Rattata", trainer = "Youngster Joey", location = "Route 3"),
        run(7, killer = "Geodude", location = "Mt. Moon"),
        // Another settings file on the same game: not this mode's history.
        run(8, outcome = RunRecord.Outcome.WON, badges = 8, ruleset = "FRLG Survival.rnqs"),
    )

    @Test
    fun `the most common Pokemon and trainers count only lost runs, most first, ties by name`() {
        val mine = runs.filter { it.ruleset == kaizo }
        assertEquals(listOf("Geodude" to 3, "Rattata" to 2, "Gengar" to 1), StreamHistory.commonPokemon(mine))
        assertEquals(listOf("Leader Brock" to 2, "Rival Gary" to 1, "Youngster Joey" to 1), StreamHistory.commonTrainers(mine))
        assertEquals(listOf("Geodude" to 3), StreamHistory.commonPokemon(mine, limit = 1))
        // A win and a run ended early name nobody, even with a Pokemon on the record.
        val notLosses = listOf(run(1, outcome = RunRecord.Outcome.WON, killer = "Mew", trainer = "Champion Gary"),
            run(2, outcome = RunRecord.Outcome.ENDED, killer = "Mew"))
        assertTrue(StreamHistory.commonPokemon(notLosses).isEmpty())
        assertTrue(StreamHistory.commonTrainers(notLosses).isEmpty())
        // A blank name is no one.
        assertTrue(StreamHistory.commonTrainers(listOf(run(1, killer = "Zubat", trainer = "  "))).isEmpty())
    }

    @Test
    fun `the facts are this game and mode only, newest first, with the best run`() {
        val f = StreamHistory.facts("FireRed", "FRLG Kaizo", runs, kaizo)
        assertEquals(7, f["runs"]); assertEquals(0, f["wins"])
        @Suppress("UNCHECKED_CAST") val attempts = f["attempts"] as List<Map<String, Any?>>
        assertEquals(listOf(7, 6, 5, 4, 3, 2, 1), attempts.map { it["attempt"] })
        // Two runs reached 3 badges: the first to get there is the best.
        @Suppress("UNCHECKED_CAST") val best = f["best"] as Map<String, Any?>
        assertEquals(4, best["attempt"]); assertEquals(3, best["badges"])
        assertEquals(mapOf("name" to "Gengar", "level" to 10), best["lostTo"])
        assertEquals("Rival Gary", best["trainer"]); assertEquals("Pokemon Tower", best["area"])
        // The other settings file's win is not this mode's best.
        val survival = StreamHistory.facts("FireRed", "FRLG Survival", runs, "FRLG Survival.rnqs")
        assertEquals(1, survival["runs"]); assertEquals(1, survival["wins"])
        @Suppress("UNCHECKED_CAST") val survivalBest = survival["best"] as Map<String, Any?>
        assertEquals("won", survivalBest["outcome"])
        // A run ended early says so, and names no one.
        val five = attempts.single { it["attempt"] == 5 }
        assertEquals("ended", five["outcome"]); assertNull(five["lostTo"]); assertEquals("Viridian City", five["area"])
    }

    @Test
    fun `a win beats any number of badges, and a phone with no game has an empty history`() {
        val withWin = runs.map { it.copy(ruleset = kaizo) }
        @Suppress("UNCHECKED_CAST") val best = StreamHistory.facts("FireRed", "x", withWin, kaizo)["best"] as Map<String, Any?>
        assertEquals(8, best["attempt"])
        val none = StreamHistory.facts(null, null, emptyList(), null)
        assertNull(none["game"]); assertEquals(0, none["runs"]); assertNull(none["best"])
        assertContains(StreamHistory.page(none), "No game yet")
    }

    @Test
    fun `only the run facts go out, no seed, no settings file, no integrity counts`() {
        val json = StreamHistory.json(StreamHistory.facts("FireRed", "FRLG Kaizo", runs, kaizo))
        @Suppress("UNCHECKED_CAST") val m = MiniJson(json).parse() as Map<String, Any?>
        assertEquals(setOf("app", "game", "mode", "runs", "wins", "best", "commonPokemon", "commonTrainers", "attempts"), m.keys)
        @Suppress("UNCHECKED_CAST") val row = (m["attempts"] as List<Map<String, Any?>>).first()
        assertEquals(setOf("attempt", "outcome", "badges", "area", "lostTo", "trainer", "playSeconds", "ended"), row.keys)
        for (secret in listOf("deadbeef", ".rnqs", "Bulbasaur", "restores", "seed", "/", "\\\\")) {
            assertFalse(json.contains(secret), "the history leaks $secret")
        }
    }

    @Test
    fun `the page reads as a page, escapes every name, and goes see-through with bg=none`() {
        val odd = listOf(run(1, killer = "<script>alert(1)</script>", trainer = "Rival \"Gary\" & co", location = "Route <1>"))
        val page = StreamHistory.page(StreamHistory.facts("Fire<Red>", "Kaizo & co", odd, kaizo))
        assertFalse(page.contains("<script>alert"), "a name is text, never markup")
        assertContains(page, "&lt;script&gt;alert(1)&lt;/script&gt;")
        assertContains(page, "Rival &quot;Gary&quot; &amp; co")
        assertContains(page, "Fire&lt;Red&gt;")
        assertContains(page, "background:#0b0b0b", message = "a normal page has a background")
        assertContains(StreamHistory.page(StreamHistory.facts("FireRed", "K", odd, kaizo), mapOf("bg" to "none")), "background:transparent")
        val full = StreamHistory.page(StreamHistory.facts("FireRed", "FRLG Kaizo", runs, kaizo))
        for (w in listOf("RUN HISTORY", "BEST RUN", "WHAT ENDS RUNS", "LATEST RUNS", "Geodude", "Leader Brock", "Lost to Lv.10 Gengar (Rival Gary), Pokemon Tower", "Ended early, Viridian City")) {
            assertContains(full, w)
        }
        // rows=2 draws two of the latest; the default draws them all here (seven, under twelve).
        val two = StreamHistory.page(StreamHistory.facts("FireRed", "K", runs, kaizo), mapOf("rows" to "2"))
        assertEquals(2, Regex("<td class=\"att\">#").findAll(two).count())
        assertEquals(7, Regex("<td class=\"att\">#").findAll(full).count())
    }

    @Test
    fun `the page follows the house rules`() {
        for (body in listOf(StreamHistory.page(StreamHistory.facts("FireRed", "FRLG Kaizo", runs, kaizo)), StreamHistory.page(StreamHistory.facts(null, null, emptyList(), null)))) {
            assertFalse(body.contains(0x2014.toChar()) || body.contains(0x2013.toChar()), "no dash")
            assertFalse(Regex("\\bAI\\b|artificial intelligence|machine learning|donat|\\bpay\\b|\\bmoney\\b", RegexOption.IGNORE_CASE).containsMatchIn(body))
        }
    }

    // ------------------------------------------------------------------ the routes

    private fun get(port: Int, path: String): Pair<Int, String> {
        val c = URL("http://127.0.0.1:$port$path").openConnection() as HttpURLConnection
        val code = c.responseCode
        return code to (if (code < 400) c.inputStream else c.errorStream).bufferedReader().readText()
    }

    @Test
    fun `history and its JSON need the token, like every route`() {
        val facts = StreamHistory.facts("FireRed", "FRLG Kaizo", runs, kaizo)
        val s = StreamServer("abcd", { "" }, { "{}" }, { 1L }, { "" }, { null }, history = { facts })
        val port = s.start(0)
        try {
            for (path in listOf("/history", "/history.json", "/history.html")) {
                assertEquals(403, get(port, path).first, "$path with no key")
                assertEquals(403, get(port, "$path?k=nope").first, "$path with a wrong key")
            }
            val (code, html) = get(port, "/history?k=abcd&bg=none&rows=3")
            assertEquals(200, code)
            assertContains(html, "background:transparent")
            assertEquals(3, Regex("<td class=\"att\">#").findAll(html).count())
            val (jcode, json) = get(port, "/history.json?k=abcd")
            assertEquals(200, jcode)
            assertEquals(StreamHistory.json(facts), json)
        } finally { s.stop() }
    }

    @Test
    fun `a history that cannot be read is an empty page, not a broken server`() {
        val s = StreamServer("abcd", { "" }, { "{}" }, { 1L }, { "" }, { null }, history = { error("disk gone") })
        val port = s.start(0)
        try {
            assertEquals(200, get(port, "/history?k=abcd").first)
            assertContains(get(port, "/history.json?k=abcd").second, "\"runs\":0")
            assertEquals(200, get(port, "/state.json?k=abcd").first, "the server carries on")
        } finally { s.stop() }
    }

    @Test
    fun `the app's own run history files are what it reads, for the game and mode last randomized`() {
        val dir = java.nio.file.Files.createTempDirectory("history").toFile()
        try {
            val prep = File(dir, "prep").apply { mkdirs() }
            File(prep, "lastrun.txt").writeText("firered-u-v11\n$kaizo\n")
            File(prep, "runhistory-firered-u-v11.tsv").writeText(runs.joinToString("") { it.encode() + "\n" })
            val f = StreamHistory.source(dir)()
            assertEquals(com.ironmonone.core.RomKind.byId("firered-u-v11")!!.displayName, f["game"])
            assertEquals(7, f["runs"])
            assertFalse((f["mode"] as String).endsWith(".rnqs"))
            // No game randomized yet: an empty history, not an error.
            val empty = java.nio.file.Files.createTempDirectory("history0").toFile()
            try { assertNull(StreamHistory.source(empty)()["game"]) } finally { empty.deleteRecursively() }
        } finally { dir.deleteRecursively() }
    }

    // ------------------------------------------------------------------ where it is offered

    @Test
    fun `the OBS scene carries it hidden over the tracker's column, see-through, asleep while hidden`() {
        val base = "http://192.168.1.50:8642"
        @Suppress("UNCHECKED_CAST") val scene = MiniJson(ObsScene.json(base, "abcd")).parse() as Map<String, Any?>
        @Suppress("UNCHECKED_CAST") val src = (scene["sources"] as List<Map<String, Any?>>).single { it["name"] == ObsScene.HISTORY }
        @Suppress("UNCHECKED_CAST") val settings = src["settings"] as Map<String, Any?>
        val uri = URI(settings["url"] as String)
        assertEquals("/history", uri.path)
        assertEquals(listOf("k=abcd", "bg=none"), uri.rawQuery.split('&'))
        assertEquals(true, settings["shutdown"])
        @Suppress("UNCHECKED_CAST") val items = ((scene["sources"] as List<Map<String, Any?>>).single { it["name"] == ObsScene.SCENE }["settings"] as Map<String, Any?>)["items"] as List<Map<String, Any?>>
        val item = items.single { it["name"] == ObsScene.HISTORY }
        assertEquals(false, item["visible"])
        @Suppress("UNCHECKED_CAST") val pos = item["pos"] as Map<String, Double>
        assertEquals(ObsScene.GAME_W.toDouble(), pos["x"])
        assertTrue(pos["y"]!! + (settings["height"] as Long) <= ObsScene.CANVAS_H)
    }

    @Test
    fun `the setup page links it with the key and the size`() {
        val page = StreamPages.setup("http://192.168.1.50:8642", "abcd")
        assertContains(page, "http://192.168.1.50:8642/history?k=abcd&amp;bg=none")
        assertContains(page, "Run history")
        assertContains(page, "${ObsScene.TRACKER_W} x ${ObsScene.HISTORY_H}")
    }
}
