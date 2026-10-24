package com.ironmonone.pc

import com.ironmonone.app.stream.twitch.ChatLine
import com.ironmonone.app.stream.twitch.MemoryTokenStore
import com.ironmonone.app.stream.twitch.Twitch
import com.ironmonone.app.stream.twitch.TwitchLink
import com.ironmonone.app.stream.twitch.TwitchTokens
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Twitch chat on the PC (PLAN row 9), with no Twitch at all: `!fav` on stubbed chat lines, the badges read off a
 * stubbed EventSub notification, the shared commands answered from the PC's snapshot, and the whole link against local
 * fakes of id.twitch.tv, Helix and EventSub (the app's own fakes). No sign-in is made anywhere.
 */
class PcChatTest {
    private val me = "141981764"
    private val closers = ArrayList<AutoCloseable>()
    @AfterTest fun closeAll() { closers.forEach { runCatching { it.close() } } }

    private fun line(text: String, chatter: String = "99", badges: List<String> = emptyList()) =
        ChatLine("id-$text", text, me, chatter, "Viewer$chatter", null, badges)

    @Test
    fun `fav is for moderators and the broadcaster, slot and name passed on with who asked, one a second for each chatter`() {
        val set = ArrayList<Triple<Int, String, String>>()
        var now = 1_000_000L
        val fav = FavCommand({ slot, name, by -> set += Triple(slot, name, by); "Favorite #$slot changed from (none) to $name." }, clock = { now })
        assertNull(fav.handle(line("!fav 2 gengar")), "a viewer gets nothing")
        assertNull(fav.handle(line("!pokemon", badges = listOf("moderator"))), "not a fav line")
        assertEquals("Favorite #2 changed from (none) to gengar.", fav.handle(line("!fav 2 gengar", badges = listOf("moderator"))))
        assertNull(fav.handle(line("!fav 3 mew", badges = listOf("moderator"))), "the same mod within a second: quiet")
        assertEquals("Favorite #4 changed from (none) to mew.", fav.handle(line("!fav 4 mew", chatter = "55", badges = listOf("moderator"))),
            "another mod in the same second is not dropped")
        now += 1001
        assertEquals("Favorite #3 changed from (none) to mr mime.", fav.handle(line("!FAV 3 mr mime", badges = listOf("broadcaster"))))
        now += 1001
        assertEquals("Favorite #1 changed from (none) to pikachu.", fav.handle(line("!fav 1 pikachu", chatter = me)), "the channel's own line counts as the broadcaster's")
        now += 1001
        assertEquals("Favorite #9 changed from (none) to Charizard-X.", fav.handle(line("!favorite 9 Charizard-X", badges = listOf("vip", "moderator"))))
        assertEquals(listOf(Triple(2, "gengar", "Viewer99"), Triple(4, "mew", "Viewer55"), Triple(3, "mr mime", "Viewer99"),
            Triple(1, "pikachu", "Viewer$me"), Triple(9, "Charizard-X", "Viewer99")), set)
    }

    @Test
    fun `favorites is for anyone once every ten seconds, and a bare fav gives a mod the help with this run's slots`() {
        var now = 1_000_000L
        val fav = FavCommand({ _, _, _ -> "set" }, list = { "Favorites > #1 Gengar, #2 (none), #3 (none)" }, shown = { 3 }, clock = { now })
        assertEquals("Favorites > #1 Gengar, #2 (none), #3 (none)", fav.handle(line("!favorites")))
        assertNull(fav.handle(line("!favs", chatter = "12")), "within ten seconds: quiet")
        now += 10_001
        assertEquals("Favorites > #1 Gengar, #2 (none), #3 (none)", fav.handle(line("!FAVS", chatter = "12")))
        assertNull(fav.handle(line("!fav")), "a viewer's bare !fav gets nothing")
        assertEquals("!fav <slot> <pokemon> (mods) > Sets favorite 1 to 3 to that Pokemon. !favorites lists them.",
            fav.handle(line("!fav", badges = listOf("moderator"))))
        assertNull(fav.handle(line("!favorites 3")), "not a line of ours")
    }

    @Test
    fun `a mod's fav in chat is answered in chat and a viewer's is not, and the shared commands read the PC's snapshot`() {
        val messages = CopyOnWriteArrayList<SeenRequest>()
        val http = FakeHttp { r ->
            when {
                r.path == "/oauth2/validate" -> 200 to """{"client_id":"x","login":"streamer","scopes":["user:read:chat","user:write:chat"],"user_id":"$me","expires_in":5000}"""
                r.path == "/helix/eventsub/subscriptions" -> 202 to """{"data":[{"id":"sub","status":"enabled"}],"total":1,"total_cost":0,"max_total_cost":10}"""
                r.path == "/helix/chat/messages" -> { messages += r; 200 to """{"data":[{"message_id":"reply-${messages.size}","is_sent":true}]}""" }
                else -> 404 to "{}"
            }
        }.also { closers += it }
        val es = FakeEventSub().also { closers += it }
        val answered = ArrayList<Pair<Int, String>>()
        val snapshot = com.ironmonone.pcui.PcSnapshot.json(com.ironmonone.pcui.Samples.party, 740, "Heart & Soul 2.0.6 (KaizoCore)", null)
        val fav = FavCommand({ slot, name, _ -> answered += slot to name; "Favorite #$slot changed from (none) to ${name.replaceFirstChar { it.uppercase() }}." })
        val link = TwitchLink(
            store = MemoryTokenStore(TwitchTokens("acc-1", "ref-1", System.currentTimeMillis() + 3_600_000, me, "streamer")),
            settings = com.ironmonone.app.stream.twitch.ChatSettings(), facts = object : com.ironmonone.app.stream.twitch.ChatFacts {
                override val version = "test"
                override fun gacha(name: String) = com.ironmonone.app.stream.twitch.GachaLookup.NotHere
                override fun enabled(c: com.ironmonone.app.stream.twitch.ChatCommand) = true
                override val extraHelp = listOf("!favorites" to FavCommand.LIST_HELP, "!fav" to "<slot> <pokemon> (mods)")
            },
            state = { snapshot }, endpoints = Twitch.Endpoints(id = http.base, api = http.base, eventSub = es.url),
            pace = { it / 100 }, sendOn = java.util.concurrent.Executor { it.run() }, extra = { fav.handle(it) },
        ).also { closers += AutoCloseable { it.shutdown() } }
        link.start()
        val c = es.next()
        c.welcome("sess-1")
        waitFor(what = "listening") { link.status.value.phase == TwitchLink.Phase.LISTENING }
        c.chat("q1", "!fav 2 gengar", me, chatter = "7", badges = listOf("moderator"))
        waitFor(what = "the mod's reply") { messages.size == 1 }
        assertEquals("Favorite #2 changed from (none) to Gengar.", messages[0].json["message"])
        assertEquals("q1", messages[0].json["reply_parent_message_id"])
        assertEquals(listOf(2 to "gengar"), answered)
        c.chat("q2", "!fav 3 mew", me, chatter = "8")
        c.chat("q3", "!attempts", me, chatter = "8")
        waitFor(what = "the attempts reply") { messages.size == 2 }
        assertEquals("Attempts > 740 | Game: Heart & Soul 2.0.6 (KaizoCore)", messages[1].json["message"])
        assertEquals(listOf(2 to "gengar"), answered, "the viewer's !fav set nothing")
        c.chat("q4", "!pokemon", me, chatter = "8")
        waitFor(what = "the pokemon reply") { messages.size == 3 }
        val p = messages[2].json["message"] as String
        assertEquals("Golisopod Lv.6 (Bug/Steel) > HP: 22/29 | Ability: Shield Dust | Item: Choice Band | Stats: HP 29, Atk 29, Def 29, SpA 26, SpD 9, Spe 13 | BST: 530", p)
        c.chat("q5", "!moves", me, chatter = "8")
        waitFor(what = "the moves reply") { messages.size == 4 }
        val mv = messages[3].json["message"] as String
        assertTrue(mv.startsWith("Golisopod moves > Thunder Shock") && "Growl" in mv && "Tail Whip" in mv, mv)
        c.chat("q6", "!help", me, chatter = "8")
        waitFor(what = "the help reply") { messages.size == 5 }
        val help = messages[4].json["message"] as String
        assertTrue(help.startsWith("Tracker Commands > ") && help.endsWith(", !favorites, !fav"), help)
    }

    @Test
    fun `the Windows sign-in store seals and opens the tokens for this user, and drops a file it cannot open`() {
        if (!Dpapi.available) return println("DpapiTokenStoreTest skipped: not Windows")
        val dir = TestFiles.tempDir("dpapi")
        try {
            val f = java.io.File(dir, DpapiTokenStore.FILE)
            val store = DpapiTokenStore(f)
            assertNull(store.load())
            val t = TwitchTokens("acc-1", "ref-1", 1_700_000_000_000L, me, "streamer", "Streamer", listOf("user:read:chat", "user:write:chat"))
            store.save(t)
            assertTrue(f.isFile && f.length() > 100)
            assertTrue(!String(f.readBytes(), Charsets.ISO_8859_1).contains("acc-1"), "the file does not hold the token in the clear")
            val back = store.load()!!
            assertEquals(listOf("acc-1", "ref-1", me, "streamer"), listOf(back.access, back.refresh, back.userId, back.login))
            f.writeBytes(ByteArray(40) { 7 })
            assertNull(store.load())
            assertTrue(!f.exists(), "a file that will not open is dropped")
            store.clear()
        } finally { dir.deleteRecursively() }
    }
}
