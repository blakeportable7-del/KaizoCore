package com.ironmonone.app.stream.twitch

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** The Device Code Grant Flow and the refresh, against a real local HTTP server speaking Twitch's answers. */
class TwitchAuthTest {

    private val deviceAnswer = """{"device_code":"dev-123","expires_in":1800,"interval":5,"user_code":"ABCDEFGH","verification_uri":"https://www.twitch.tv/activate?public=true&device-code=ABCDEFGH"}"""
    private fun tokenAnswer(access: String = "acc-1", refresh: String = "ref-1") =
        """{"access_token":"$access","expires_in":14124,"refresh_token":"$refresh","scope":["user:read:chat","user:write:chat"],"token_type":"bearer"}"""

    @Test
    fun `pending, slow down, then success, the interval is honoured and grows by five seconds`() {
        val polls = ArrayDeque(listOf(
            400 to """{"status":400,"message":"authorization_pending"}""",
            400 to """{"status":400,"message":"slow_down"}""",
            400 to """{"status":400,"message":"authorization_pending"}""",
            200 to tokenAnswer()))
        FakeHttp { r -> if (r.path == "/oauth2/device") 200 to deviceAnswer else polls.removeFirst() }.use { http ->
            val slept = ArrayList<Long>()
            val auth = TwitchAuth(UrlHttp, Twitch.Endpoints(id = http.base), clock = { 0L }, sleep = { slept += it })
            val code = auth.start()
            assertEquals("ABCDEFGH", code.userCode)
            assertTrue(code.verificationUri.startsWith("https://www.twitch.tv/activate"))
            val start = http.seen[0].form
            assertEquals(Twitch.CLIENT_ID, start["client_id"])
            assertEquals("user:read:chat user:write:chat", start["scopes"])

            val done = assertIs<TwitchAuth.Poll.Done>(auth.await(code) { false })
            assertEquals("acc-1", done.tokens.access); assertEquals("ref-1", done.tokens.refresh)
            assertEquals(listOf(5000L, 5000L, 10_000L, 10_000L), slept, "5 s, then 5 more for good after slow_down")
            val poll = http.seen[1].form
            assertEquals("urn:ietf:params:oauth:grant-type:device_code", poll["grant_type"])
            assertEquals("dev-123", poll["device_code"]); assertEquals(Twitch.CLIENT_ID, poll["client_id"])
            assertFalse(http.seen.any { "client_secret" in it.body }, "a public client has no secret to send")
        }
    }

    @Test
    fun `an expired code says so, from Twitch or from the clock`() {
        FakeHttp { r -> if (r.path == "/oauth2/device") 200 to deviceAnswer else 400 to """{"status":400,"message":"invalid device code"}""" }.use { http ->
            val auth = TwitchAuth(UrlHttp, Twitch.Endpoints(id = http.base), clock = { 0L }, sleep = {})
            assertEquals(TwitchAuth.Poll.Expired, auth.await(auth.start()) { false })
        }
        var now = 0L
        FakeHttp { r -> if (r.path == "/oauth2/device") 200 to deviceAnswer else 400 to """{"status":400,"message":"authorization_pending"}""" }.use { http ->
            val auth = TwitchAuth(UrlHttp, Twitch.Endpoints(id = http.base), clock = { now }, sleep = { now += it })
            assertEquals(TwitchAuth.Poll.Expired, auth.await(auth.start()) { false }, "1800 s of pending runs out on its own")
            assertTrue(http.seen.size in 300..400, "polled about every 5 s for 30 minutes, ${http.seen.size} requests")
        }
    }

    @Test
    fun `a cancelled sign-in stops polling, and a start Twitch refuses reads as words for the player`() {
        FakeHttp { r -> if (r.path == "/oauth2/device") 200 to deviceAnswer else 400 to """{"message":"authorization_pending"}""" }.use { http ->
            var n = 0
            val auth = TwitchAuth(UrlHttp, Twitch.Endpoints(id = http.base), clock = { 0L }, sleep = { n++ })
            assertEquals(TwitchAuth.Poll.Cancelled, auth.await(auth.start()) { n >= 3 })
        }
        FakeHttp { 500 to "oops" }.use { http ->
            val e = assertFailsWith<TwitchTrouble> { TwitchAuth(UrlHttp, Twitch.Endpoints(id = http.base)).start() }
            assertTrue("Try again" in e.message!!)
        }
    }

    @Test
    fun `each refresh uses the newest refresh token and saves the one it gets back`() {
        var issued = 1
        FakeHttp { r ->
            if (r.path == "/oauth2/token" && r.form["grant_type"] == "refresh_token") {
                if (r.form["refresh_token"] != "ref-$issued") 400 to """{"status":400,"message":"Invalid refresh token"}"""
                else { issued++; 200 to tokenAnswer("acc-$issued", "ref-$issued") }
            } else 404 to "{}"
        }.use { http ->
            val store = MemoryTokenStore(TwitchTokens("acc-1", "ref-1", 0, "141981764", "streamer"))
            val quiet = ChatSettings().apply { setAnswering(false) }  // signed in and paused: no chat loop racing the test
            val link = TwitchLink(store, quiet, NoFacts, { "{}" }, UrlHttp, Twitch.Endpoints(id = http.base, api = http.base))
            link.start()
            assertEquals(TwitchLink.Phase.PAUSED, link.status.value.phase)
            val first = assertIs<TwitchAuth.Refresh.Ok>(link.refreshNow(store.tokens!!))
            assertEquals("ref-2", first.tokens.refresh)
            assertEquals("streamer", first.tokens.login, "who it is survives a refresh")
            val second = assertIs<TwitchAuth.Refresh.Ok>(link.refreshNow(first.tokens))
            assertEquals("ref-3", second.tokens.refresh)
            assertEquals(listOf("ref-2", "ref-3"), store.saved, "every new refresh token is saved at once")
            assertEquals(listOf("ref-1", "ref-2"), http.seen.map { it.form["refresh_token"] }, "a used refresh token is never sent again")
            assertTrue(http.seen.all { it.form["client_id"] == Twitch.CLIENT_ID && "client_secret" !in it.form })
        }
    }

    @Test
    fun `a refused refresh token asks for a new sign-in, a dropped one does not`() {
        FakeHttp { 400 to """{"status":400,"message":"Invalid refresh token"}""" }.use { http ->
            val auth = TwitchAuth(UrlHttp, Twitch.Endpoints(id = http.base))
            assertEquals(TwitchAuth.Refresh.Rejected, auth.refresh(TwitchTokens("a", "r", 0)))
        }
        val dead = FakeHttp { 200 to "{}" }.also { it.close() }
        assertEquals(TwitchAuth.Refresh.Unreachable, TwitchAuth(UrlHttp, Twitch.Endpoints(id = dead.base)).refresh(TwitchTokens("a", "r", 0)))
    }

    @Test
    fun `validate reads who the token is for, and revoke sends the client id and token`() {
        FakeHttp { r ->
            when (r.path) {
                "/oauth2/validate" -> if (r.headers["authorization"] == "OAuth acc-1") 200 to """{"client_id":"x","login":"streamer","scopes":["user:read:chat","user:write:chat"],"user_id":"141981764","expires_in":5520838}"""
                    else 401 to """{"status":401,"message":"invalid access token"}"""
                "/oauth2/revoke" -> 200 to ""
                else -> 404 to "{}"
            }
        }.use { http ->
            val auth = TwitchAuth(UrlHttp, Twitch.Endpoints(id = http.base))
            val ok = assertIs<TwitchAuth.Validate.Ok>(auth.validate("acc-1"))
            assertEquals("141981764", ok.userId); assertEquals("streamer", ok.login)
            assertEquals(TwitchAuth.Validate.Invalid, auth.validate("acc-old"))
            assertTrue(auth.revoke("acc-1"))
            assertEquals(mapOf("client_id" to Twitch.CLIENT_ID, "token" to "acc-1"), http.seen.last().form)
        }
    }
}

/** Facts for a test that asks nothing of GachaMon. */
object NoFacts : ChatFacts {
    override fun gacha(name: String) = GachaLookup.NotHere
    override val version = "test"
    override fun enabled(c: ChatCommand) = true
}
