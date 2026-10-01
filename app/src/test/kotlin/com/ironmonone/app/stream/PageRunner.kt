package com.ironmonone.app.stream

import com.ironmonone.tracker.BaseStats
import com.ironmonone.tracker.GameOver
import com.ironmonone.tracker.MoveRow
import com.ironmonone.tracker.PokemonDecoder
import com.ironmonone.tracker.TrackedMon
import com.ironmonone.tracker.TrackerState
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Runs one stream page's own script under node, against a fake browser (pages_runner.js, beside this file), for the
 * tests that must see what a page DOES and not only what its source says: whether the tracker prints an attempt,
 * whether the game page writes words over a live picture, whether the attempt counter has a demo. The page's HTML is
 * written out and its script cut from it, so the page under test is the one the phone serves.
 *
 * Needs node on the PATH. Without it [available] is false, and the tests that use this say so and skip themselves
 * (Assume), as the tests that need the NDK do. Added 2026-09-30 for the UX audit's stream fixes.
 */
internal object PageRunner {
    private val runner = File("src/test/kotlin/com/ironmonone/app/stream/pages_runner.js")
    private val scratch = File("build/stream-page-runs")

    val available: Boolean by lazy {
        runCatching {
            val p = ProcessBuilder("node", "--version").redirectErrorStream(true).start()
            p.inputStream.readBytes()
            p.waitFor() == 0
        }.getOrDefault(false)
    }

    /** What a page showed after it loaded ([initial]) and after each step. Values are what pages_runner.js prints. */
    class Result(val initial: Seen, val steps: List<Seen>)

    class Seen(private val values: Map<String, Any?>) {
        @Suppress("UNCHECKED_CAST")
        private fun at(path: String): Any? = path.split('.').fold(values as Any?) { m, key -> (m as Map<String, Any?>)[key] }

        fun str(path: String): String = at(path) as String
        fun long(path: String): Long = at(path) as Long
    }

    /**
     * [page] is "tracker", "attempts" or "game"; [html] is that page as served; [search] is the query the page is opened
     * with; [steps] and [extra] are the runner's job (see the top of pages_runner.js).
     */
    @Suppress("UNCHECKED_CAST")
    fun run(page: String, html: String, search: String = "?k=abcd", steps: List<Map<String, Any?>> = emptyList(), extra: Map<String, Any?> = emptyMap()): Result {
        check(runner.isFile) { "${runner.path} should exist (the working directory is app/)" }
        scratch.mkdirs()
        val file = File.createTempFile("page-", ".html", scratch).apply { writeText(html); deleteOnExit() }
        val job = File.createTempFile("job-", ".json", scratch).apply {
            writeText(Json.write(linkedMapOf<String, Any?>("page" to page, "file" to file.absolutePath, "search" to search, "steps" to steps) + extra))
            deleteOnExit()
        }
        val process = ProcessBuilder("node", runner.absolutePath, job.absolutePath).redirectError(ProcessBuilder.Redirect.INHERIT).start()
        val out = process.inputStream.bufferedReader().readText()
        check(process.waitFor(30, TimeUnit.SECONDS) && process.exitValue() == 0) { "pages_runner.js failed for the $page page (its message is in the test output)" }
        val answer = MiniJson(out).parse() as Map<String, Any?>
        return Result(Seen(answer["initial"] as Map<String, Any?>), (answer["steps"] as List<Map<String, Any?>>).map { Seen(it) })
    }
}

/**
 * Snapshots as the phone builds them (StreamSnapshot.build, the same call the Play screen makes), so a page test is tied
 * to the field names the phone really sends, "run" and "attempt" among them.
 */
internal object PageSnapshots {
    private fun mon(species: Int, level: Int) = PokemonDecoder.Mon(
        pid = 1, level = level, nickname = "", species = species, heldItem = 0, friendship = 0,
        moves = listOf(1, 2), pp = listOf(10, 20), ivs = List(6) { 0 }, evs = List(6) { 0 },
        ppUps = List(4) { 0 }, abilitySlot = 0, nature = 0, shiny = false, status = 0,
        curHp = 61, maxHp = 74, atk = 55, def = 40, spe = 52, spAtk = 61, spDef = 38,
    )

    private val base = BaseStats(70, 94, 50, 66, 94, 50, type1 = 6, type2 = 2, ability1 = 68, ability2 = 0)

    /** A tracked game with one Pokemon in the party. [run] says whether the Play screen is playing a run; [outcome] ends it. */
    fun of(run: Boolean, outcome: GameOver? = null, attempt: Int = 812): Map<String, Any?> {
        val own = TrackedMon(mon(414, 23), "Mothim", listOf("Gust"), base, abilityName = "Swarm",
            itemName = "Oran Berry", moveRows = listOf(MoveRow(16, "Gust", 31, 35, 40, 100, 2, "PHYSICAL")),
            movesLearned = 6, movesTotal = 11, nextMoveLevel = 26)
        val state = TrackerState(1, listOf(own), inBattle = false, isWildBattle = false, badges = 0b101, gameOver = outcome)
        // The page's run-over view follows the latched end (StreamHub.ended), which the Play screen sets from the popup.
        val ended = outcome?.let { if (it == GameOver.WON) com.ironmonone.tracker.RunOutcome.WON else com.ironmonone.tracker.RunOutcome.LOST }
        val play = StreamSnapshot.Run(if (run) "Kaizo run" else "Library game", "GBA", attempt, true, if (run) "abc" else null, run, ended = ended)
        return StreamSnapshot.build(play, state, null, StreamSnapshot.Notes())
    }
}
