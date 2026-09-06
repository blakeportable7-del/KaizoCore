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

    @Test
    fun `console ids are rcheevos' own`() {
        assertEquals(5, RetroAchievements.consoleId(Platform.GBA)); assertEquals(6, RetroAchievements.consoleId(Platform.GBC)); assertEquals(18, RetroAchievements.consoleId(Platform.NDS))
    }
}
