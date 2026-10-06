package com.ironmonone.app.stream

import com.ironmonone.app.DeathCard
import com.ironmonone.app.GachaMonEntry
import com.ironmonone.app.GachaMonNotes
import com.ironmonone.app.GameOverMon
import com.ironmonone.app.RunRecord
import com.ironmonone.tracker.RunOutcome
import com.ironmonone.tracker.gachamon.GachaMonCard
import com.ironmonone.tracker.gachamon.SixStats
import org.junit.Assume
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The game over card source (/gameover, streamer list item 1, 2026-10-05). Its data is the game-over popup's: the
 * latch's outcome, the death card RunHistoryHook filed, the popup's line and the run's GachaMon card. These tests hold
 * what the data says, that the hub carries it and drops it on a new run, and what the page does with it.
 */
class StreamGameOverTest {

    @AfterTest fun clean() { StreamHub.newRun(); StreamHub.timerRun = null }

    private val seed = "5261db990e333467"

    private fun record(outcome: RunRecord.Outcome = RunRecord.Outcome.LOST, trainer: String = "Hiker Marcos", killer: RunRecord.Mon? = RunRecord.Mon(551, "Sandile", 21)) =
        RunRecord(812, seed, "Kaizo.rnqs", 0L, 0L, 3723, outcome, 3, RunRecord.Mon(414, "Mothim", 23), killer, trainer, "Route 3")

    private fun card(r: RunRecord = record(), best: RunRecord? = null) = DeathCard("Pokemon Emerald", r, best, earlier = false)

    private fun gacha() = GachaMonEntry(
        GachaMonCard(version = 2, personality = 4242, pokemonId = 280, level = 21, abilityId = 0, ratingScore = 60, battlePower = 412,
            seedNumber = 812, type1 = 4, type2 = 17, stats = SixStats(50, 72, 35, 35, 35, 65), moveIds = listOf(44, 0, 0, 0),
            gameVersion = 2, nature = 0, year = 26, month = 10, day = 5),
        GachaMonNotes(name = "Sandile", ability = "Moxie", moves = listOf("Bite", "Sand Tomb", "Leer"), trainer = "Hiker Marcos"),
    )

    private fun ending(card: DeathCard? = card(), outcome: RunOutcome = RunOutcome.LOST, team: List<GameOverMon> = emptyList(), bits: Int = 0,
                       g: GachaMonEntry? = null, from: String? = null, counted: Boolean = true) =
        StreamSnapshot.Ending(outcome, "Pokemon Emerald", 812, seed, counted, card, "Devastating!", team, bits, g, from)

    // ------------------------------------------------------------------ the data

    @Test
    fun `no ending is no card`() {
        assertNull(StreamSnapshot.gameOver(null))
        assertEquals("null", Json.write(StreamSnapshot.gameOver(null)))
    }

    @Test
    fun `a loss says what ended it as the popup does, with the attempt, badges, time and the popup's line`() {
        val best = record().copy(attempt = 640, seed = "00000000000000aa", badges = 5)
        val m = StreamSnapshot.gameOver(ending(card(best = best)))!!
        assertEquals("LOST", m["outcome"]); assertEquals(false, m["won"])
        assertEquals(812, m["attempt"])
        // DeathCard.headline: the popup's own first line.
        assertEquals("LOST TO", m["label"])
        assertEquals("Lv.21 Sandile (Hiker Marcos)", m["cause"])
        assertEquals(mapOf("species" to 551, "name" to "Sandile", "level" to 21), m["killer"])
        assertEquals("Hiker Marcos", m["trainer"]); assertEquals("Route 3", m["location"])
        assertEquals(mapOf("species" to 414, "name" to "Mothim", "level" to 23), m["fallen"])
        assertEquals(3, m["badges"]); assertEquals(3723, m["seconds"])
        assertEquals(false, m["newBest"]); assertEquals("Best: 5 badges, attempt 640", m["best"])
        assertEquals("No state loads or retries", m["integrity"])
        assertEquals("Devastating!", m["quote"])
        assertEquals("Pokemon Emerald|$seed|812|LOST", m["key"])
        assertNull(m["gachamon"])
    }

    @Test
    fun `a new best says so and a win names no killer`() {
        val best = record().copy(attempt = 640, seed = "00000000000000aa", badges = 1)
        assertEquals(true, StreamSnapshot.gameOver(ending(card(best = best)))!!["newBest"])
        val won = record(RunRecord.Outcome.WON, trainer = "", killer = null).copy(badges = 8)
        val m = StreamSnapshot.gameOver(ending(card(won), RunOutcome.WON))!!
        assertEquals(true, m["won"]); assertEquals("WON", m["outcome"])
        assertNull(m["killer"]); assertNull(m["label"]); assertNull(m["cause"])
        assertEquals(8, m["badges"])
    }

    @Test
    fun `with no death card it still says the attempt, the badges and who fell`() {
        val team = listOf(GameOverMon(414, "Mothim", 23, fainted = false), GameOverMon(280, "Ralts", 19, fainted = true))
        val m = StreamSnapshot.gameOver(ending(card = null, team = team, bits = 0b1011))!!
        assertEquals(3, m["badges"], "a badge a bit")
        assertEquals(mapOf("species" to 280, "name" to "Ralts", "level" to 19), m["fallen"])
        assertNull(m["seconds"]); assertNull(m["cause"]); assertNull(m["best"])
        assertNull(StreamSnapshot.gameOver(ending(counted = false))!!["attempt"], "an attempt only where one is counted")
    }

    @Test
    fun `the run's GachaMon card goes out as its facts`() {
        val g = StreamSnapshot.gameOver(ending(g = gacha(), from = "prize"))!!["gachamon"] as Map<*, *>
        assertEquals("prize", g["from"]); assertEquals("Sandile", g["name"]); assertEquals(21, g["level"])
        assertEquals(280, g["species"]); assertEquals(412, g["power"]); assertEquals("Moxie", g["ability"])
        assertEquals(listOf("Bite", "Sand Tomb", "Leer"), g["moves"], "the empty slot is left out")
        assertEquals(gacha().stars, g["stars"]); assertEquals(false, g["shiny"]); assertEquals("Hiker Marcos", g["trainer"])
    }

    // ------------------------------------------------------------------ the hub: it appears, and a new run clears it

    @Test
    fun `the hub serves the card while the run is over and a new run clears it`() {
        assertEquals("null", StreamHub.gameOver)
        StreamHub.ended = RunOutcome.LOST
        StreamHub.gameOver = Json.write(StreamSnapshot.gameOver(ending()))
        assertContains(StreamHub.gameOver, "\"cause\":\"Lv.21 Sandile (Hiker Marcos)\"")
        // PrepStore.installRun calls this as the next run goes in, Play open or not.
        StreamHub.newRun()
        assertEquals("null", StreamHub.gameOver)
        assertNull(StreamHub.ended)
    }

    @Test
    fun `a new run going in clears the card, through the store`() {
        // The call is in the store's install itself, after the run's notes are cleared: no other path reaches the hub.
        val src = File("src/main/kotlin/com/ironmonone/app/PrepStore.kt").readText()
        val install = src.substring(src.indexOf("fun installRun("), src.indexOf("private fun dropStaleRuns("))
        assertTrue(install.indexOf("StreamHub.newRun()") > install.indexOf("clearRunNotes()"), "installRun clears the stream's game over card")
    }

    @Test
    fun `the feed builds the card from the latch and only from this run's death card`() {
        val feed = File("src/main/kotlin/com/ironmonone/app/stream/StreamFeed.kt").readText()
        // The latch's ended, never a new read of the game.
        assertContains(feed, "StreamExtras(on, session, platform, run, gba, nds, latch, ended)")
        assertContains(feed, "StreamHub.gameOver = if (ended == null) \"null\" else")
        assertContains(feed, "card?.takeIf { it.record.attempt == run.attempt && it.record.seed == run.seed }")
    }

    // ------------------------------------------------------------------ the page

    private val ids = listOf("card", "title", "attempt", "label", "cause", "where", "quote", "stats", "best", "gacha", "gname", "gstars", "gmoves")

    private fun data(key: String = "k1", won: Boolean = false, g: Boolean = true): Map<String, Any?> =
        MiniJson(Json.write(StreamSnapshot.gameOver(ending(if (won) card(record(RunRecord.Outcome.WON, "", null)) else card(),
            if (won) RunOutcome.WON else RunOutcome.LOST, g = if (g) gacha() else null, from = "prize"))!! + ("key" to key))).parse() as Map<String, Any?>

    private fun run(steps: List<Map<String, Any?>>, search: String = "?k=abcd"): PageRunner.Result {
        Assume.assumeTrue("node is not on the PATH, so the pages' own scripts are not run", PageRunner.available)
        return PageRunner.run("gameover", StreamOverlays.gameOver(), search, steps, mapOf("ids" to ids))
    }

    private fun event(d: Map<String, Any?>?) = mapOf<String, Any?>("event" to mapOf("type" to "gameover", "data" to d))

    @Test
    fun `the page is see-through while the run goes on, and the card comes in when it ends`() {
        val r = run(listOf(event(null), event(data()), event(null)))
        assertEquals("", r.initial.str("card.cls"), "nothing on screen before any data")
        assertEquals(1L, r.initial.long("sources"), "it listens to the phone")
        assertFalse(r.steps[0].str("card.cls").contains("on"), "a run going on shows nothing")
        val shown = r.steps[1]
        assertEquals("lost on", shown.str("card.cls"))
        assertEquals("RUN OVER", shown.str("title.text"))
        assertContains(shown.str("attempt.html"), "<b>812</b>")
        assertEquals("LOST TO", shown.str("label.text"))
        assertEquals("Lv.21 Sandile (Hiker Marcos)", shown.str("cause.text"))
        assertEquals("Route 3", shown.str("where.text"), "a trainer's line names no place, so the place is under it")
        assertEquals("\"Devastating!\"", shown.str("quote.text"))
        assertContains(shown.str("stats.html"), "<b>3</b>"); assertContains(shown.str("stats.html"), "<b>1:02:03</b>")
        assertContains(shown.str("stats.html"), "Lv.23 Mothim")
        assertEquals("on kc-rule", shown.str("gacha.cls"))
        assertContains(shown.str("gname.text"), "Sandile")
        assertContains(shown.str("gmoves.text"), "Sand Tomb")
        assertEquals(gacha().stars, Regex("class=\"star\"").findAll(shown.str("gstars.html")).count())
        // A new run (or Retry) sends null: the card goes out.
        assertEquals("lost off", r.steps[2].str("card.cls"))
    }

    @Test
    fun `the same ending again refreshes the card without bringing it in again, and a win looks like one`() {
        val r = run(listOf(event(data(g = false)), event(data(g = true)), event(data(key = "k2", won = true, g = false))))
        assertEquals("", r.steps[0].str("gacha.cls"), "no GachaMon card yet")
        assertEquals("lost on", r.steps[1].str("card.cls"))
        assertEquals("on kc-rule", r.steps[1].str("gacha.cls"), "the prize card made a moment later is added")
        assertEquals("won on", r.steps[2].str("card.cls"))
        assertEquals("RUN WON", r.steps[2].str("title.text"))
        assertEquals("BEAT THE GAME", r.steps[2].str("label.text"))
    }

    @Test
    fun `with hold it goes by itself and stays down for that run`() {
        val r = run(listOf(event(data()), mapOf("advance" to 19_000), mapOf("advance" to 2_000), event(data()), event(data(key = "k2"))), "?k=abcd&hold=20")
        assertEquals("lost on", r.steps[1].str("card.cls"))
        assertEquals("lost off", r.steps[2].str("card.cls"), "gone after 20 seconds")
        assertEquals("lost off", r.steps[3].str("card.cls"), "the same run's card does not come back on a reconnect")
        assertEquals("lost on", r.steps[4].str("card.cls"), "the next run's does")
    }

    @Test
    fun `the demo shows a card with no phone`() {
        val r = run(emptyList(), "?k=abcd&demo=1")
        assertEquals("lost on", r.initial.str("card.cls"))
        assertEquals(0L, r.initial.long("sources"), "a demo asks the phone for nothing")
        assertNotEquals("", r.initial.str("cause.text"))
    }
}
