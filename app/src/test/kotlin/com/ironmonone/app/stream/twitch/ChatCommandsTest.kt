package com.ironmonone.app.stream.twitch

import com.ironmonone.app.TrackerOptions
import com.ironmonone.app.stream.Json
import com.ironmonone.app.stream.StreamSnapshot
import com.ironmonone.tracker.BaseStats
import com.ironmonone.tracker.EnemyInfo
import com.ironmonone.tracker.MoveRow
import com.ironmonone.tracker.PokemonDecoder
import com.ironmonone.tracker.RandomizedFlags
import com.ironmonone.tracker.TrackedMon
import com.ironmonone.tracker.TrackerState
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The commands, their limits, and the rule that matters most: chat learns nothing the tracker hides. The answers are
 * built from real snapshots (StreamSnapshot.build), the same JSON the stream page gets.
 */
class ChatCommandsTest {

    private fun mon(species: Int, level: Int) = PokemonDecoder.Mon(
        pid = 1, level = level, nickname = "", species = species, heldItem = 0, friendship = 0,
        moves = listOf(16), pp = listOf(31), ivs = List(6) { 0 }, evs = List(6) { 0 },
        ppUps = List(4) { 0 }, abilitySlot = 0, nature = 0, shiny = false, status = 0,
        curHp = 61, maxHp = 74, atk = 55, def = 40, spe = 52, spAtk = 61, spDef = 38,
    )

    private val base = BaseStats(70, 94, 50, 66, 94, 50, type1 = 6, type2 = 2, ability1 = 68, ability2 = 0)

    /** Your Mothim against a wild Sandile whose real stats (Atk 72, ability Intimidate) the tracker never shows. */
    private fun battle(): TrackerState {
        val own = TrackedMon(mon(414, 23), "Mothim", listOf("Gust"), base, abilityName = "Swarm", itemName = "Oran Berry",
            moveRows = listOf(MoveRow(16, "Gust", 31, 35, 40, 100, 2, "PHYSICAL")), movesLearned = 6, movesTotal = 11, nextMoveLevel = 26)
        val second = TrackedMon(mon(25, 9).copy(curHp = 20, maxHp = 30), "Pikachu", listOf("Thundershock"), base)
        val enemy = EnemyInfo(551, "Sandile", 21, 33, 60, 4, 17, BaseStats(50, 72, 35, 65, 35, 35, 4, 17, 22, 153),
            movesSeen = listOf("Bite"), abilityGuess = "Intimidate / Moxie")
        val rand = RandomizedFlags(true, true, false, false, false, false, false, false, false, false)
        return TrackerState(1, listOf(own, second), inBattle = true, isWildBattle = true, enemy = enemy, randomized = rand,
            gameDataRandomized = true, badges = 0b1011, healPercent = 62, healCount = 3)
    }

    private val notes = StreamSnapshot.Notes(marksOf = { if (it == 551) intArrayOf(0, 1, 0, 0, 2, 3) else IntArray(6) },
        movesSeenOf = { if (it == 551) listOf("Sand Tomb") else emptyList() }, noteOf = { if (it == 551) "scary" else "" })

    private fun snapshot(attempt: Int, run: Boolean = true, generation: Int = 3): String = Json.write(StreamSnapshot.build(
        StreamSnapshot.Run("Emerald", "GBA", attempt, true, seed = if (run) "1234567890abcdef" else null, isRun = run,
            generation = generation, ended = null, nuzlocke = false),
        battle(), null, notes))

    private fun ask(text: String, state: String, facts: ChatFacts = NoFacts) = ChatAnswers.answer(ChatAsk.parse(text)!!, state, facts)

    private class Gacha(val lookup: GachaLookup) : ChatFacts {
        override fun gacha(name: String) = lookup
        override val version = "rc36"
        override fun enabled(c: ChatCommand) = c != ChatCommand.HEALS
    }

    private val card = GachaFacts("Mothim", "Swarm", 4, 60, 7000, 23, listOf(74, 55, 40, 61, 38, 52), listOf("Gust", "---", "", "Tackle"), shiny = false)

    // ------------------------------------------------------------------ parsing and limits

    @Test
    fun `commands are read from the start of a line, with aliases and arguments`() {
        assertEquals(ChatAsk(ChatCommand.POKEMON, ""), ChatAsk.parse("!pokemon"))
        assertEquals(ChatAsk(ChatCommand.POKEMON, "pikachu"), ChatAsk.parse("  !MON   pikachu "))
        assertEquals(ChatAsk(ChatCommand.HELP, "moves"), ChatAsk.parse("!commands moves"))
        assertEquals(ChatCommand.GACHAMON, ChatAsk.parse("!gachamon")?.command)
        assertNull(ChatAsk.parse("what is !pokemon"), "only at the start of the line")
        assertNull(ChatAsk.parse("!pokemonx"))
        assertNull(ChatAsk.parse("!seed"), "not a command")
        assertNull(ChatAsk.parse("!log"), "the log is never a command here")
        assertEquals(ChatAsk.MAX_ARGS, ChatAsk.parse("!pokemon " + "x".repeat(100))!!.args.length)
    }

    @Test
    fun `each command waits out its cooldown, and all of them together stay under the rate limit`() {
        var now = 0L
        val gate = ChatGate({ now }, cooldownMs = 10_000, maxPerWindow = 3, windowMs = 30_000)
        assertTrue(gate.admit(ChatCommand.POKEMON))
        assertFalse(gate.admit(ChatCommand.POKEMON), "the same command inside its cooldown")
        now = 9_999; assertFalse(gate.admit(ChatCommand.POKEMON))
        now = 10_000; assertTrue(gate.admit(ChatCommand.POKEMON))
        assertTrue(gate.admit(ChatCommand.MOVES))
        assertFalse(gate.admit(ChatCommand.ATTEMPTS), "three answers in 30 s is the most")
        now = 30_000; assertTrue(gate.admit(ChatCommand.ATTEMPTS), "the first answer left the window")
        // The app's own numbers: far under Twitch's 20 (and the broadcaster's 100) per 30 seconds.
        val app = ChatGate()
        assertTrue(app.maxPerWindow <= 10 && app.windowMs >= 30_000 && app.cooldownMs >= 5_000)
    }

    @Test
    fun `an answer fits one chat line and never reads as a command`() {
        assertEquals(500, ChatAnswers.fit("x".repeat(900)).length)
        assertTrue(ChatAnswers.fit("x".repeat(900)).endsWith("..."))
        assertEquals("Command: !pokemon [name]", ChatAnswers.fit("!pokemon [name]"))
        assertFalse(ask("!help pokemon", "{}").startsWith("!"))
        assertFalse(ask("!pokemon", "{}").contains('\n'))
    }

    // ------------------------------------------------------------------ the answers

    @Test
    fun `pokemon, moves, attempts, progress and heals answer from the snapshot`() {
        val s = snapshot(812)
        assertEquals("Mothim Lv.23 (Bug/Flying) > HP: 61/74 | Ability: Swarm | Item: Oran Berry | " +
            "Stats: HP 74, Atk 55, Def 40, SpA 61, SpD 38, Spe 52 | BST: 424", ask("!pokemon", s))
        assertContains(ask("!mon pikachu", s), "Pikachu Lv.9")
        assertEquals("Pokemon > Only your own party can be looked up.", ask("!pokemon sandile", s), "the opponent is not looked up")
        val moves = ask("!moves", s)
        assertTrue(moves.startsWith("Mothim moves > Gust ["), moves)
        assertContains(moves, "Power 40, Acc 100, PP 31/35")
        assertContains(moves, "Next move at Lv.26")
        assertEquals("Attempts > 812 | Game: Emerald", ask("!attempts", s))
        assertEquals("Progress > Gym badges: 3/8", ask("!progress", s))
        assertEquals("Heals > 62% HP in the bag | 3 healing items", ask("!heals", s))
        assertEquals("KaizoCore > Version: test | Game: Emerald | Attempts: 812", ask("!about", s))
        assertEquals("Pokemon > Nothing is being played right now.", ask("!pokemon", "{}"))
    }

    @Test
    fun `attempts are only told in a Kaizo IronMON run`() {
        val playAny = snapshot(812, run = false)
        assertEquals("Attempts > Not in a Kaizo IronMON run right now.", ask("!attempts", playAny))
        assertFalse(ask("!about", playAny).contains("812"), "another game's attempt number would be the last run's")
    }

    @Test
    fun `help lists only the commands that are switched on`() {
        val f = Gacha(GachaLookup.NoCard)
        val list = ask("!help", "{}", f)
        assertTrue(list.startsWith("Tracker Commands > !pokemon, !moves"), list)
        assertFalse("!heals" in list)
        assertEquals("Command: > !moves ${ChatCommand.MOVES.help}", ask("!help moves", "{}", f))
        assertEquals("Command: > That one is turned off.", ask("!help !heals", "{}", f))
    }

    @Test
    fun `gachamon prints the card the way the PC tracker does`() {
        val s = snapshot(812)
        assertEquals("GachaMon > Mothim - Swarm | 4 Stars (60 Points) | 7000 BP | Lv.23 Stats: 74/55/40/61/38/52 | Gust, Tackle",
            ask("!gachamon", s, Gacha(GachaLookup.Card(card))))
        assertEquals("GachaMon > Please wait until the GachaMon card pack is opened.", ask("!gachamon", s, Gacha(GachaLookup.PackOpening)))
        assertEquals("GachaMon > No card for the lead this run yet.", ask("!gachamon", s, Gacha(GachaLookup.NoCard)))
    }

    // ------------------------------------------------------------------ what is hidden stays hidden

    /** Every reply to every command, with and without a name, for one snapshot. */
    private fun everyReply(s: String, facts: ChatFacts): List<String> =
        ChatCommand.entries.flatMap { c -> listOf("", " mothim", " sandile", " pikachu").map { ask(c.word + it, s, facts) } }

    @Test
    fun `the opponent's real data, the seed and the player's notes never reach chat`() {
        val s = snapshot(812)
        for (r in everyReply(s, Gacha(GachaLookup.Card(card)))) {
            for (secret in listOf("Sandile", "Intimidate", "Moxie", "Sand Tomb", "Bite", "scary", "1234567890abcdef", "72"))
                assertFalse(secret in r, "'$secret' leaked in: $r")
        }
    }

    @Test
    fun `hide stats until summary shown hides the same things in chat, GachaMon card included`() {
        val saved = TrackerOptions.hideStatsUntilSummary
        val attempt = 515151
        try {
            TrackerOptions.hideStatsUntilSummary = true
            val s = snapshot(attempt)
            assertEquals("Mothim Lv.23 (Bug/Flying) > BST: 424 | Stats, ability and moves: Shows once a summary has been opened this attempt.",
                ask("!pokemon", s))
            // The card holds the real stats, ability and moves; the guard in ChatAnswers.gacha is what keeps it quiet.
            val replies = everyReply(s, Gacha(GachaLookup.Card(card)))
            for (r in replies) {
                for (secret in listOf("Swarm", "Oran Berry", "61/74", "74", "55", "Gust", "Tackle", "62%"))
                    assertFalse(secret in r, "'$secret' shown before the summary, in: $r")
            }
            assertEquals("GachaMon > Shows once a summary has been opened this attempt.", ask("!gachamon", s, Gacha(GachaLookup.Card(card))))

            com.ironmonone.app.SummaryChecks.mark(attempt)
            val after = snapshot(attempt)
            assertContains(ask("!pokemon", after), "Ability: Swarm")
            assertContains(ask("!gachamon", after, Gacha(GachaLookup.Card(card))), "Stats: 74/55/40/61/38/52")
        } finally {
            TrackerOptions.hideStatsUntilSummary = saved
            com.ironmonone.app.SummaryChecks.forgetAll()
        }
    }
}
