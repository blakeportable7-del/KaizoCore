package com.ironmonone.app.stream.twitch

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executor
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The whole of Stream Connect against local fakes of id.twitch.tv, the Helix API and EventSub: a chat line in, a reply
 * out; the lines it must not answer; Twitch dropping the connection; Twitch ending the sign-in; signing in and out.
 */
class TwitchLinkTest {
    private val me = "141981764"
    private val closers = ArrayList<AutoCloseable>()
    @AfterTest fun closeAll() { closers.forEach { runCatching { it.close() } } }

    private val state = """{"app":"KaizoCore","title":"FireRed","platform":"GBA","attempt":812,"tracked":true,"run":true,"seed":"s",""" +
        """"party":[{"species":414,"name":"Mothim","level":23,"types":["Bug","Flying"],"stats":{"hp":74,"atk":55,"def":40,"spa":61,"spd":38,"spe":52},"ability":"Swarm","bst":424,"moves":[]}],"badges":1}"""

    /** Helix and id.twitch.tv in one server. [chatCode] answers Send Chat Message; [refreshCode] answers a refresh. */
    private inner class FakeTwitch(var subscribeCode: Int = 202, var chatCode: Int = 200, var refreshCode: Int = 200) {
        val subscriptions = CopyOnWriteArrayList<SeenRequest>()
        val messages = CopyOnWriteArrayList<SeenRequest>()
        var refreshes = 0
        val http = FakeHttp { r ->
            when {
                r.path == "/oauth2/validate" -> 200 to """{"client_id":"x","login":"streamer","scopes":["user:read:chat","user:write:chat"],"user_id":"$me","expires_in":5000}"""
                r.path == "/oauth2/token" -> { refreshes++; refreshCode to (if (refreshCode == 200) """{"access_token":"acc-${refreshes + 1}","refresh_token":"ref-${refreshes + 1}","expires_in":14000,"scope":["user:read:chat","user:write:chat"],"token_type":"bearer"}""" else """{"status":400,"message":"Invalid refresh token"}""") }
                r.path == "/oauth2/revoke" -> 200 to ""
                r.path == "/helix/eventsub/subscriptions" -> { subscriptions += r; subscribeCode to """{"data":[{"id":"sub","status":"enabled"}],"total":1,"total_cost":0,"max_total_cost":10}""" }
                r.path == "/helix/chat/messages" -> { messages += r; chatCode to """{"data":[{"message_id":"reply-${messages.size}","is_sent":true}]}""" }
                else -> 404 to "{}"
            }
        }.also { closers += it }
    }

    private fun link(tw: FakeTwitch, es: FakeEventSub, store: TwitchTokenStore, settings: ChatSettings = ChatSettings(), clock: () -> Long = System::currentTimeMillis) =
        TwitchLink(store, settings, NoFacts, { state }, UrlHttp,
            Twitch.Endpoints(id = tw.http.base, api = tw.http.base, eventSub = es.url),
            clock = clock, pace = { it / 100 }, sendOn = Executor { it.run() }).also { closers += AutoCloseable { it.shutdown() } }

    private fun signedIn(expiresAt: Long = System.currentTimeMillis() + 3_600_000) = MemoryTokenStore(TwitchTokens("acc-1", "ref-1", expiresAt, me, "streamer"))

    @Test
    fun `a command in chat gets a reply in chat, as the streamer, threaded under the question`() {
        val tw = FakeTwitch(); val es = FakeEventSub().also { closers += it }
        val l = link(tw, es, signedIn())
        l.start()
        val c = es.next()
        c.welcome("sess-1")
        waitFor(what = "listening") { l.status.value.phase == TwitchLink.Phase.LISTENING }
        assertTrue(l.wantsSnapshot.value, "the stream snapshot is built while chat is answered")
        val sub = tw.subscriptions.single()
        assertEquals("Bearer acc-1", sub.headers["authorization"]); assertEquals(Twitch.CLIENT_ID, sub.headers["client-id"])
        assertEquals(mapOf("broadcaster_user_id" to me, "user_id" to me), sub.json["condition"])
        assertEquals(mapOf("method" to "websocket", "session_id" to "sess-1"), sub.json["transport"])
        assertEquals("channel.chat.message", sub.json["type"]); assertEquals("1", sub.json["version"])

        c.chat("q1", "!attempts", broadcaster = me)
        waitFor(what = "a reply") { tw.messages.size == 1 }
        val m = tw.messages.single().json
        assertEquals("Attempts > 812 | Game: FireRed", m["message"])
        assertEquals(me, m["broadcaster_id"]); assertEquals(me, m["sender_id"]); assertEquals("q1", m["reply_parent_message_id"])
        assertFalse(m.containsKey("for_source_only"), "a user token may not send it (Twitch answers 400)")
    }

    @Test
    fun `lines that are not ours to answer get nothing`() {
        val tw = FakeTwitch(); val es = FakeEventSub().also { closers += it }
        val settings = ChatSettings().apply { setEnabled(ChatCommand.HEALS, false) }
        val l = link(tw, es, signedIn(), settings)
        l.start()
        val c = es.next(); c.welcome("s")
        waitFor { l.status.value.phase == TwitchLink.Phase.LISTENING }
        c.chat("x1", "hello chat", broadcaster = me)                      // not a command
        c.chat("x2", "!heals", broadcaster = me)                          // switched off
        c.chat("x3", "!progress", broadcaster = me, source = "555")       // Shared Chat: another channel's line
        c.chat("reply-1", "!about", broadcaster = me)                     // our own earlier reply id (none sent yet: answered)
        waitFor(what = "the about reply") { tw.messages.size == 1 }
        c.chat("reply-1b", "!about", broadcaster = me)                    // inside !about's cooldown
        c.chat("x5", "!progress", broadcaster = "someone-else")           // another broadcaster's event
        Thread.sleep(300)
        assertEquals(listOf("KaizoCore > Version: test | Game: FireRed | Attempts: 812"), tw.messages.map { it.json["message"] })
        // Our reply comes back as a chat event from the streamer, under the id Twitch gave it: never answered again.
        c.chat("reply-1", "!progress", broadcaster = me, meta = "m-echo")  // a new delivery, so EventSub's dedupe is not what stops it
        Thread.sleep(300)
        assertEquals(1, tw.messages.size)
    }

    @Test
    fun `a dropped connection comes back by itself, and a 401 on send refreshes and resends once`() {
        val tw = FakeTwitch(); val es = FakeEventSub().also { closers += it }
        val store = signedIn()
        val l = link(tw, es, store)
        l.start()
        es.next().apply { welcome("s1") }.also { waitFor { l.status.value.phase == TwitchLink.Phase.LISTENING } }.drop()
        val again = es.next(10)  // backoff of about a second, paced down
        again.welcome("s2")
        waitFor(what = "listening again") { l.status.value.phase == TwitchLink.Phase.LISTENING && tw.subscriptions.size == 2 }
        assertEquals("s2", (tw.subscriptions[1].json["transport"] as Map<*, *>)["session_id"], "a new session subscribes again")

        tw.chatCode = 401
        again.chat("q", "!progress", broadcaster = me)
        waitFor(what = "two sends") { tw.messages.size == 2 }
        assertEquals(1, tw.refreshes)
        assertEquals("Bearer acc-2", tw.messages[1].headers["authorization"], "resent with the refreshed token")
        assertEquals("ref-2", store.tokens?.refresh, "and the new refresh token is the saved one")
    }

    @Test
    fun `tokens about to run out are refreshed before connecting`() {
        val tw = FakeTwitch(); val es = FakeEventSub().also { closers += it }
        val store = signedIn(expiresAt = System.currentTimeMillis() + 60_000)
        val l = link(tw, es, store)
        l.start()
        es.next().welcome("s")
        waitFor { l.status.value.phase == TwitchLink.Phase.LISTENING }
        assertEquals(1, tw.refreshes)
        assertEquals("Bearer acc-2", tw.subscriptions.single().headers["authorization"])
        assertEquals(listOf("ref-2"), store.saved)
    }

    @Test
    fun `Twitch ending the sign-in asks for a new one and forgets the tokens`() {
        val tw = FakeTwitch(); val es = FakeEventSub().also { closers += it }
        val store = signedIn()
        val l = link(tw, es, store)
        l.start()
        val c = es.next(); c.welcome("s")
        waitFor { l.status.value.phase == TwitchLink.Phase.LISTENING }
        c.revoke("authorization_revoked")
        waitFor(what = "signed out") { l.status.value.phase == TwitchLink.Phase.SIGNED_OUT }
        assertNull(store.tokens)
        assertTrue("Connect again" in l.status.value.note)
        assertFalse(l.wantsSnapshot.value)

        // A refresh token Twitch refuses (used, or 30 days unused) does the same.
        val tw2 = FakeTwitch(refreshCode = 400); val es2 = FakeEventSub().also { closers += it }
        val store2 = signedIn(expiresAt = 0)
        val l2 = link(tw2, es2, store2)
        l2.start()
        waitFor(what = "signed out after the refusal") { l2.status.value.phase == TwitchLink.Phase.SIGNED_OUT }
        assertNull(store2.tokens)
        assertTrue(es2.accepted.isEmpty(), "nothing connected with a token Twitch refused")
    }

    @Test
    fun `sign in shows the code, then answers chat, and sign out revokes and forgets`() {
        val tw = FakeTwitch(); val es = FakeEventSub().also { closers += it }
        var polls = 0
        val id = FakeHttp { r ->
            when (r.path) {
                "/oauth2/device" -> 200 to """{"device_code":"d","expires_in":1800,"interval":5,"user_code":"WDJBMJHT","verification_uri":"https://www.twitch.tv/activate?public=true&device-code=WDJBMJHT"}"""
                "/oauth2/token" -> if (++polls < 3) 400 to """{"status":400,"message":"authorization_pending"}"""
                    else 200 to """{"access_token":"acc-9","refresh_token":"ref-9","expires_in":14000,"scope":["user:read:chat","user:write:chat"],"token_type":"bearer"}"""
                "/oauth2/validate" -> 200 to """{"client_id":"x","login":"streamer","scopes":["user:read:chat","user:write:chat"],"user_id":"$me","expires_in":5000}"""
                "/oauth2/revoke" -> 200 to ""
                else -> 404 to "{}"
            }
        }.also { closers += it }
        val store = MemoryTokenStore()
        val l = TwitchLink(store, ChatSettings(), NoFacts, { state }, UrlHttp,
            Twitch.Endpoints(id = id.base, api = tw.http.base, eventSub = es.url),
            pace = { it / 100 }, sendOn = Executor { it.run() }).also { closers += AutoCloseable { it.shutdown() } }
        l.start()
        assertEquals(TwitchLink.Phase.SIGNED_OUT, l.status.value.phase)
        l.signIn()
        waitFor(what = "the code") { l.status.value.phase == TwitchLink.Phase.WAITING_FOR_CODE }
        assertEquals("WDJBMJHT", l.status.value.code)
        assertTrue(l.status.value.link.startsWith("https://www.twitch.tv/activate"))
        es.next(10).welcome("s")
        waitFor(what = "listening") { l.status.value.phase == TwitchLink.Phase.LISTENING }
        assertEquals("streamer", l.status.value.name)
        assertEquals("ref-9", store.tokens?.refresh)

        l.signOut()
        assertEquals(TwitchLink.Phase.SIGNED_OUT, l.status.value.phase)
        assertNull(store.tokens)
        waitFor(what = "the revoke") { id.paths().contains("/oauth2/revoke") }
        assertEquals("acc-9", id.seen.last { it.path == "/oauth2/revoke" }.form["token"])
    }

    @Test
    fun `nothing an answer does can throw into its caller`() {
        val tw = FakeTwitch(); val es = FakeEventSub().also { closers += it }
        val l = TwitchLink(signedIn(), ChatSettings(), NoFacts, { error("the game's state blew up") }, UrlHttp,
            Twitch.Endpoints(id = tw.http.base, api = tw.http.base, eventSub = es.url),
            sendOn = Executor { throw IllegalStateException("rejected") }).also { closers += AutoCloseable { it.shutdown() } }
        l.start(); l.shutdown()
        l.onChat(ChatLine("z", "!pokemon", me, "1", "Viewer", null))  // no exception: the state read and the send are both guarded
    }
}
