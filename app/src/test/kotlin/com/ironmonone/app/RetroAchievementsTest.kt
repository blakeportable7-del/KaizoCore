package com.ironmonone.app

import com.ironmonone.core.Platform
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RetroAchievementsTest {

    @Test
    fun `summary and achievement JSON from the native side parse, escapes included`() {
        val s = RetroAchievements.parseSummary(
            """{"loggedIn":true,"user":"Blake \"P\"","score":1234,"scoreSoftcore":5,"hardcore":false,"gameLoaded":true,"game":"Pokémon FireRed","gameId":515,"unlocked":3,"total":97,"pointsUnlocked":12,"pointsTotal":800,"unsupported":0}""")
        assertTrue(s.loggedIn); assertEquals("Blake \"P\"", s.user); assertEquals(1234, s.score)
        assertTrue(s.gameLoaded); assertEquals(3, s.unlocked); assertEquals(97, s.total); assertEquals(800, s.pointsTotal)
        assertFalse(RetroAchievements.hardcoreOn(s))
        val empty = RetroAchievements.parseSummary("""{"loggedIn":false,"hardcore":false,"gameLoaded":false}""")
        assertFalse(empty.loggedIn); assertEquals("", empty.game)
        val list = RetroAchievements.parseAchievements(
            """[{"id":1,"title":"First","description":"Do a thing","points":5,"unlocked":true,"state":2,"bucket":"Unlocked","progress":100.0,"badge":"x"},{"id":2,"title":"Second, harder","description":"","points":10,"unlocked":false,"state":1,"bucket":"Locked","progress":40.5,"badge":""}]""")
        assertEquals(2, list.size); assertEquals("Second, harder", list[1].title); assertEquals(40.5f, list[1].progress); assertTrue(list[0].unlocked)
    }

    @Test
    fun `the store keeps user and token, never a password, and hardcore is a marker`() {
        val dir = Files.createTempDirectory("ra").toFile()
        val st = RetroAchievements.Store(File(dir, "ra.txt"))
        assertNull(st.load())
        st.save("blake", "tok123")
        assertEquals("blake" to "tok123", st.load())
        assertFalse(File(dir, "ra.txt").readText().contains("password"))
        assertFalse(st.hardcore); st.hardcore = true; assertTrue(st.hardcore); st.hardcore = false; assertFalse(st.hardcore)
        st.clear(); assertNull(st.load())
    }

    /**
     * rc32 audit P2 #54: signing out left the hardcore flag behind, and the reopen at the moment a game was left kept
     * reading it, so a signed-out player's games all opened at the title, with no switch in sight to clear it.
     */
    @Test
    fun `signing out takes hardcore with it, and a flag with no session does not count`() {
        val dir = Files.createTempDirectory("ra").toFile()
        val st = RetroAchievements.Store(File(dir, "session.txt"))
        st.save("blake", "tok"); st.hardcore = true
        assertTrue(st.hardcoreSignedIn())
        st.clear()
        assertFalse(st.hardcore)
        assertFalse(st.hardcoreSignedIn())
        // A flag an older build left after a sign-out.
        File(dir, "ra-hardcore").writeText("1")
        assertTrue(st.hardcore); assertFalse(st.hardcoreSignedIn())
        val play = File("src/main/kotlin/com/ironmonone/app/PlayScreen.kt").readText()
        assertTrue("loadsAllowed = !(raStore.hardcoreSignedIn() && !session.isRun)" in play)
    }

    /** rc32 audit P3 #59: a randomized Nuzlocke read "IronMON run: achievements are not loaded here." */
    @Test
    fun `achievements are not called an IronMON run's in a Nuzlocke`() {
        val dialog = File("src/main/kotlin/com/ironmonone/app/RetroAchievementsDialog.kt").readText().replace("\r\n", "\n")
        val strings = dialog.lines().filterNot { it.trimStart().startsWith("//") || it.trimStart().startsWith("*") }
            .flatMap { line -> Regex("\"((?:[^\"\\\\]|\\\\.)*)\"").findAll(line).map { it.groupValues[1] } }
        assertTrue(strings.size > 10)
        for (s in strings) {
            assertFalse("IronMON run:" in s, s)
            assertFalse(Regex("\\bROM\\b").containsMatchIn(s), s)
        }
        assertTrue(RetroAchievementsCopy.RUN.startsWith("A run does not load achievements"))
        assertTrue("RetroAchievementsCopy.RUN" in dialog)
    }

    @Test
    fun `console ids are rcheevos' own`() {
        assertEquals(5, RetroAchievements.consoleId(Platform.GBA)); assertEquals(6, RetroAchievements.consoleId(Platform.GBC)); assertEquals(18, RetroAchievements.consoleId(Platform.NDS))
    }

    @Test
    fun `a server reply reaches the client with no view at all`() {
        // rc33 audit P1 #36: replies went through view.post, and a view that had left the screen never ran them, so the
        // client stayed "logging in" until the app was killed.
        val got = java.util.concurrent.atomic.AtomicReference<Triple<Int, String, Int>>()
        val done = java.util.concurrent.CountDownLatch(1)
        val l = RetroAchievements.listener(onEvent = { _, _, _, _, _, _ -> }, post = { it.run() }, respond = { id, body, status -> got.set(Triple(id, body, status)); done.countDown() })
        l.onServerCall(7, "http://127.0.0.1:1/", "", "")
        kotlin.test.assertTrue(done.await(20, java.util.concurrent.TimeUnit.SECONDS), "the reply came back")
        kotlin.test.assertEquals(Triple(7, "", 0), got.get(), "a refused connection is still answered, as status 0")
        val play = java.io.File("src/main/kotlin/com/ironmonone/app/PlayScreen.kt").readText()
        kotlin.test.assertFalse("view.post(r)" in play, "no reply goes through the view")
    }

    @Test
    fun `only the server refusing the token signs the player out`() {
        // rc33 audit P1 #37: any failure deleted the saved session, offline at launch included (-32).
        for (code in listOf(-33, -34, -35)) kotlin.test.assertTrue(RetroAchievements.tokenRefused(code), "$code")
        for (code in listOf(0, -25, -26, -27, -28, -31, -32)) kotlin.test.assertFalse(RetroAchievements.tokenRefused(code), "$code")
        val play = java.io.File("src/main/kotlin/com/ironmonone/app/PlayScreen.kt").readText()
        kotlin.test.assertTrue("if (RetroAchievements.tokenRefused(result)) raStore.clear()" in play)
        kotlin.test.assertTrue("else if (result != RetroAchievements.SIGN_IN_IN_FLIGHT) {" in play, "a sign-in already under way is not an error")
    }

    @Test
    fun `hardcore's reset restarts the game and the client together`() {
        // rc33 audit P1 #38: rcheevos waits for a reset after hardcore goes on mid-game; nothing answered it.
        val ra = java.io.File("src/main/kotlin/com/ironmonone/app/RetroAchievements.kt").readText()
        kotlin.test.assertTrue("view?.queueEvent { runCatching { LibretroDroid.reset(); LibretroDroid.cheevosReset() } }" in ra, "the core first, between frames")
        val play = java.io.File("src/main/kotlin/com/ironmonone/app/PlayScreen.kt").readText()
        kotlin.test.assertTrue("RetroAchievements.EV_RESET -> { RetroAchievements.restartGame(view)" in play)
        kotlin.test.assertTrue("onRestart = { RetroAchievements.restartGame(retro) }" in play, "File > Restart too")
    }
}
