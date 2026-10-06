package com.ironmonone.app.stream

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * OBS reacting by itself (ObsLink, Blake's streamer list item 2): the debounce that keeps a flickering battle flag from
 * thrashing scenes, the backoff, and the link against FakeObs: scenes switched, the replay saved, OBS gone and back,
 * and never a wait or a throw on the game's side.
 */
class ObsLinkTest {

    private val scenes = ObsSettings(enabled = true, host = "127.0.0.1", overworldScene = "Game", battleScene = "Battle",
        gameOverScene = "Game over", saveReplay = true)

    // ------------------------------------------------------------------ the debounce, on a clock of the test's own

    @Test
    fun `a battle flag that flickers for less than the settle time never moves the scene`() {
        val d = ObsDirector(settleMs = 1000)
        assertEquals("Game", d.scene(scenes, 0)); d.applied("Game")
        // The flag blinks on and off every 200 ms for two seconds, as a battle's first frames can make it.
        var t = 0L
        repeat(10) { d.battle(it % 2 == 0, t); assertNull(d.scene(scenes, t), "no switch at $t ms"); t += 200 }
        d.battle(false, t)
        assertNull(d.scene(scenes, t + 5000), "settled back where it was: nothing to send")
    }

    @Test
    fun `a battle that holds switches once, after the settle time, and back once when it ends`() {
        val d = ObsDirector(settleMs = 1000)
        d.applied("Game")
        d.battle(true, 10_000)
        assertNull(d.scene(scenes, 10_500))
        assertEquals(11_000L, d.wakeAt(), "the link wakes when the flag will have settled")
        assertEquals("Battle", d.scene(scenes, 11_000)); d.applied("Battle")
        assertNull(d.scene(scenes, 11_100), "once")
        d.battle(false, 20_000)
        assertNull(d.scene(scenes, 20_900))
        assertEquals("Game", d.scene(scenes, 21_000)); d.applied("Game")
        assertNull(d.wakeAt())
    }

    @Test
    fun `a loss goes to the game over scene at once, over the battle it was lost in, and asks for one replay`() {
        val d = ObsDirector(settleMs = 1000)
        d.battle(true, 0); assertEquals("Battle", d.scene(scenes, 1000)); d.applied("Battle")
        d.ended(ended = true, lost = true, now = 1100)
        assertEquals("Game over", d.scene(scenes, 1100), "no settle time for a latched end")
        assertEquals(1100L, d.replayAsked)
        d.applied("Game over"); d.replaySaved()
        // The latch reads again and again while the popup is up: still one replay.
        d.ended(ended = true, lost = true, now = 1200)
        assertNull(d.replayAsked)
        // The battle flag dropping on the whiteout does not leave the game over scene.
        d.battle(false, 1300)
        assertNull(d.scene(scenes, 9000))
        // Retry the battle opens the run again: back to the battle.
        d.ended(ended = false, lost = false, now = 9100)
        d.battle(true, 9100)
        assertEquals("Battle", d.scene(scenes, 10_100))
    }

    @Test
    fun `a win saves the replay and keeps the game over scene for losses`() {
        val d = ObsDirector(settleMs = 0)
        d.applied("Game")
        d.ended(ended = true, lost = false, now = 5)
        assertEquals(5L, d.replayAsked)
        assertNull(d.scene(scenes, 6), "a win is not a game over")
    }

    @Test
    fun `a scene left as None is never switched to`() {
        val d = ObsDirector(settleMs = 0)
        val noBattle = scenes.copy(battleScene = "", gameOverScene = "")
        d.applied("Game")
        d.battle(true, 0)
        assertNull(d.scene(noBattle, 1))
        d.ended(true, true, 2)
        assertNull(d.scene(noBattle, 3), "after a loss with no game over scene, nothing moves")
    }

    @Test
    fun `the game stopping mid-battle goes back to the game scene at once, and a battle when it is back settles again`() {
        val d = ObsDirector(settleMs = 1000)
        d.applied("Game")
        d.battle(true, 0); assertEquals("Battle", d.scene(scenes, 1000)); d.applied("Battle")
        // Play left: the battle flag never goes false (its effect is gone), so only stopped() can end it.
        d.stopped(5000)
        assertEquals("Game", d.scene(scenes, 5000), "no settle time for a stopped game")
        d.applied("Game")
        assertNull(d.wakeAt(), "nothing waiting to settle")
        // Back in Play, still in the battle: it has to hold again before OBS moves.
        d.battle(true, 9000)
        assertNull(d.scene(scenes, 9500))
        assertEquals("Battle", d.scene(scenes, 10_000))
    }

    @Test
    fun `the game stopping on the game over scene goes back to the game scene, and the same end coming back asks no second replay`() {
        val d = ObsDirector(settleMs = 1000)
        d.ended(ended = true, lost = true, now = 0)
        assertEquals("Game over", d.scene(scenes, 0)); d.applied("Game over"); d.replaySaved()
        d.stopped(100)
        assertEquals("Game", d.scene(scenes, 100)); d.applied("Game")
        // Play opened again with the popup still up: the same latched end is fed again.
        d.battle(false, 200)
        d.ended(ended = true, lost = true, now = 200)
        assertNull(d.replayAsked, "the same end, not a new one")
        assertEquals("Game over", d.scene(scenes, 200), "the popup is up again, so is its scene")
    }

    @Test
    fun `the backoff doubles from one second to a ceiling of thirty`() {
        assertEquals(listOf(1000L, 2000, 4000, 8000, 16000, 30000, 30000, 30000), (0..7).map { obsBackoffMs(it) })
        assertEquals(1000L, obsBackoffMs(-3))
    }

    @Test
    fun `settings round-trip, and a typed address is cleaned`() {
        val s = scenes.copy(password = "p=w\nx", port = 4460)
        // The password is never in the saved form: it is sealed on its own (ObsLink.PASSWORD_FILE).
        assertFalse("p=w" in s.encode(), s.encode())
        assertEquals(s.copy(password = ""), ObsSettings.decode(s.encode()))
        // A file from before still reads, password and all, for ObsLink.load to move it.
        assertEquals("old one", ObsSettings.decode("enabled=1\nhost=pc\npassword=old one\n").password)
        assertEquals("192.168.1.20" to 4456, ObsSettings.cleanHost(" ws://192.168.1.20:4456/ "))
        assertEquals("my-pc.local" to null, ObsSettings.cleanHost("my-pc.local"))
        assertEquals("fe80::1" to 4455, ObsSettings.cleanHost("[fe80::1]:4455"))
        assertTrue(scenes.usable)
        assertFalse(scenes.copy(overworldScene = "").usable, "a battle scene with no game scene to come back to")
        assertTrue(ObsSettings(enabled = true, host = "pc", saveReplay = true).usable, "the replay save alone")
        assertFalse(scenes.copy(host = " ").usable)
        assertContains(ObsLink.whatIsMissing(scenes.copy(overworldScene = "")), "game scene")
    }

    // ------------------------------------------------------------------ the link, against FakeObs

    private fun link() = ObsLink(backoff = longArrayOf(50, 100, 200), settleMs = 100, keepAliveMs = 300, connectMs = 500, ioMs = 1000)

    @Test
    fun `a battle switches OBS to the battle scene and its end back to the game scene`() {
        FakeObs().use { obs ->
            val link = link()
            try {
                link.apply(scenes.copy(port = obs.port))
                assertTrue(waitFor { link.status.value.state == ObsLink.State.CONNECTED }, link.status.value.toString())
                link.battle(true)
                assertTrue(waitFor { obs.scene == "Battle" }, obs.requests.toString())
                link.battle(false)
                assertTrue(waitFor { obs.scene == "Game" }, obs.requests.toString())
                assertEquals(listOf("SetCurrentProgramScene:Battle", "SetCurrentProgramScene:Game"), obs.requests.filter { it.startsWith("Set") })
            } finally { link.stop() }
        }
    }

    @Test
    fun `leaving Play mid-battle puts OBS back on the game scene`() {
        FakeObs().use { obs ->
            val link = link()
            try {
                link.apply(scenes.copy(port = obs.port))
                assertTrue(waitFor { link.status.value.state == ObsLink.State.CONNECTED }, link.status.value.toString())
                link.battle(true)
                assertTrue(waitFor { obs.scene == "Battle" }, obs.requests.toString())
                // No battle(false) ever comes: StreamFeed's effects stop with Play. Its dispose says the game stopped.
                link.gameStopped()
                assertTrue(waitFor { obs.scene == "Game" }, obs.requests.toString())
                assertEquals(listOf("SetCurrentProgramScene:Battle", "SetCurrentProgramScene:Game"), obs.requests.filter { it.startsWith("Set") })
            } finally { link.stop() }
        }
    }

    @Test
    fun `Play tells the link when the game stops, from StreamFeed and not from PlayScreen`() {
        val feed = java.io.File("src/main/kotlin/com/ironmonone/app/stream/StreamFeed.kt").readText()
        assertContains(feed, "DisposableEffect(session.id) { onDispose { ObsLink.app.gameStopped() } }")
        val play = java.io.File("src/main/kotlin/com/ironmonone/app/PlayScreen.kt").readText()
        assertFalse("ObsLink" in play, "PlayScreen is at ART's verifier limit and stays as it was")
    }

    @Test
    fun `OBS on a scene of the streamer's own is left there`() {
        FakeObs().use { obs ->
            obs.scene = "Be right back"
            val link = link()
            try {
                link.apply(scenes.copy(port = obs.port))
                assertTrue(waitFor { link.status.value.state == ObsLink.State.CONNECTED })
                link.battle(true)
                assertTrue(waitFor { obs.requests.contains("GetCurrentProgramScene") })
                Thread.sleep(400)
                assertEquals("Be right back", obs.scene)
                assertTrue(obs.requests.none { it.startsWith("SetCurrentProgramScene") }, obs.requests.toString())
            } finally { link.stop() }
        }
    }

    @Test
    fun `a lost run goes to the game over scene and saves the replay once`() {
        FakeObs().use { obs ->
            val link = link()
            try {
                link.apply(scenes.copy(port = obs.port))
                assertTrue(waitFor { link.status.value.state == ObsLink.State.CONNECTED })
                link.battle(true)
                link.runEnded(ended = true, lost = true)
                assertTrue(waitFor { obs.scene == "Game over" && obs.requests.contains("SaveReplayBuffer") }, obs.requests.toString())
                repeat(5) { link.runEnded(ended = true, lost = true) }
                Thread.sleep(300)
                assertEquals(1, obs.requests.count { it == "SaveReplayBuffer" })
            } finally { link.stop() }
        }
    }

    @Test
    fun `a replay buffer that is off is said plainly, and the link stays up`() {
        FakeObs().use { obs ->
            obs.replayActive = false
            val link = link()
            try {
                link.apply(scenes.copy(port = obs.port))
                assertTrue(waitFor { link.status.value.state == ObsLink.State.CONNECTED })
                link.runEnded(ended = true, lost = true)
                assertTrue(waitFor { link.status.value.note.contains("replay buffer was not running") }, link.status.value.toString())
                assertEquals(ObsLink.State.CONNECTED, link.status.value.state)
                assertTrue(waitFor { obs.scene == "Game over" })
            } finally { link.stop() }
        }
    }

    @Test
    fun `with OBS closed the game's calls return at once and never throw, and the link waits, then finds OBS when it opens`() {
        val port = freePort()
        val link = link()
        try {
            link.apply(scenes.copy(port = port))
            assertTrue(waitFor { link.status.value.state == ObsLink.State.WAITING }, link.status.value.toString())
            assertContains(link.status.value.line, "Trying again in")
            // The game's side: a thousand calls in well under a frame's worth of time each, OBS or no OBS.
            val t0 = System.nanoTime()
            repeat(1000) { link.battle(it % 3 == 0); link.runEnded(ended = false, lost = false) }
            assertTrue((System.nanoTime() - t0) / 1_000_000 < 500, "the calls never wait on the network")
            link.battle(true)
            FakeObs(port = port).use { obs ->
                assertTrue(waitFor { link.status.value.state == ObsLink.State.CONNECTED }, link.status.value.toString())
                assertTrue(waitFor { obs.scene == "Battle" }, "what changed while OBS was away goes out once it is back: ${obs.requests}")
            }
        } finally { link.stop() }
    }

    @Test
    fun `OBS closing mid-stream is waited out, and the next battle still switches`() {
        val port = freePort()
        val link = link()
        try {
            FakeObs(port = port).use { obs ->
                link.apply(scenes.copy(port = port))
                assertTrue(waitFor { link.status.value.state == ObsLink.State.CONNECTED })
            }
            // OBS is gone: the keepalive finds out, and the link goes to waiting rather than anywhere worse.
            assertTrue(waitFor { link.status.value.state == ObsLink.State.WAITING }, link.status.value.toString())
            FakeObs(port = port).use { obs ->
                assertTrue(waitFor { link.status.value.state == ObsLink.State.CONNECTED })
                link.battle(true)
                assertTrue(waitFor { obs.scene == "Battle" }, obs.requests.toString())
            }
        } finally { link.stop() }
    }

    @Test
    fun `a wrong password stops the link until the settings change, with no hammering`() {
        FakeObs(password = "right").use { obs ->
            val link = link()
            try {
                link.apply(scenes.copy(port = obs.port, password = "wrong"))
                assertTrue(waitFor { link.status.value.state == ObsLink.State.STOPPED }, link.status.value.toString())
                assertContains(link.status.value.line, "password")
                Thread.sleep(600)
                assertEquals(1, obs.connections.get(), "one try, not one every backoff step")
                link.apply(scenes.copy(port = obs.port, password = "right"))
                assertTrue(waitFor { link.status.value.state == ObsLink.State.CONNECTED })
            } finally { link.stop() }
        }
    }

    @Test
    fun `turned off, the link never touches the network`() {
        FakeObs().use { obs ->
            val link = link()
            link.apply(scenes.copy(port = obs.port, enabled = false))
            link.battle(true); link.runEnded(true, true)
            Thread.sleep(300)
            assertEquals(0, obs.connections.get())
            assertEquals(ObsLink.State.OFF, link.status.value.state)
        }
    }

    @Test
    fun `the test button reads the scenes in OBS's own order and the replay buffer's state, or says why not`() {
        FakeObs().use { obs ->
            val p = ObsLink.probe(scenes.copy(port = obs.port))
            assertTrue(p.ok, p.line)
            assertEquals(obs.scenes, p.scenes, "top of OBS's list first, as the Scenes box shows them")
            assertEquals(true, p.replayRunning)
        }
        val gone = ObsLink.probe(scenes.copy(port = freePort()), connectMs = 500)
        assertFalse(gone.ok)
        assertContains(gone.line, "did not answer")
    }

    /** A link whose password box is [box] (the app's is sealed with the Keystore, which a JVM test does not have). */
    private fun link(box: com.ironmonone.app.SealedFile) =
        ObsLink(backoff = longArrayOf(50, 100, 200), settleMs = 100, keepAliveMs = 300, connectMs = 500, ioMs = 1000, passwordAt = { box })

    private fun sealedText(box: com.ironmonone.app.MemorySealedFile) = box.bytes?.toString(Charsets.UTF_8)

    @Test
    fun `the link's settings are saved where the backup leaves them out, and the password only sealed`() {
        val dir = java.nio.file.Files.createTempDirectory("obslink").toFile()
        try {
            val box = com.ironmonone.app.MemorySealedFile()
            val link = link(box)
            link.load(dir)
            link.update(scenes.copy(enabled = false, password = "secret"))
            val f = java.io.File(dir, "prep/obs-link.txt")
            assertTrue(f.isFile)
            assertFalse("secret" in f.readText(), "no password in the plain file: ${f.readText()}")
            assertFalse("password" in f.readText())
            assertEquals("secret", sealedText(box))
            val again = link(box)
            again.load(dir)
            assertEquals("Battle", again.settings().battleScene)
            assertEquals("secret", again.settings().password, "the password comes back from the sealed file")
            assertFalse(com.ironmonone.app.Backup.admits("prep/obs-link.txt"), "the address stays on this phone")
            assertFalse(com.ironmonone.app.Backup.admits(ObsLink.PASSWORD_FILE), "the sealed password stays on this phone")
            // No password: the sealed file goes.
            again.update(again.settings().copy(password = ""))
            assertNull(box.bytes)
        } finally { dir.deleteRecursively() }
    }

    @Test
    fun `a plain-text password from before is sealed once and taken out of the file`() {
        val dir = java.nio.file.Files.createTempDirectory("obslink").toFile()
        try {
            val f = java.io.File(dir, "prep/obs-link.txt").apply { parentFile.mkdirs() }
            f.writeText("enabled=0\nhost=192.168.1.20\nport=4455\npassword=hunter22\nbattle=Battle\noverworld=Game\ngameover=\nreplay=0\n")
            val box = com.ironmonone.app.MemorySealedFile()
            val link = link(box)
            link.load(dir)
            assertEquals("hunter22", link.settings().password, "the password still works")
            assertEquals("Battle", link.settings().battleScene)
            assertEquals("hunter22", sealedText(box))
            assertFalse("hunter22" in f.readText(), "the plain copy is gone: ${f.readText()}")
            assertEquals("192.168.1.20", ObsSettings.decode(f.readText()).host, "and nothing else in the file changed")
            // Once: the next start reads it from the sealed file, the plain file has none to move.
            box.bytes = "moved".toByteArray()
            val again = link(box)
            again.load(dir)
            assertEquals("moved", again.settings().password)
        } finally { dir.deleteRecursively() }
    }

    @Test
    fun `a password that cannot be sealed stays where it was and still works`() {
        val dir = java.nio.file.Files.createTempDirectory("obslink").toFile()
        try {
            val f = java.io.File(dir, "prep/obs-link.txt").apply { parentFile.mkdirs() }
            val before = "enabled=0\nhost=pc\nport=4455\npassword=hunter22\nbattle=\noverworld=Game\ngameover=\nreplay=0\n"
            f.writeText(before)
            val broken = object : com.ironmonone.app.SealedFile {
                override fun read(): ByteArray? = null
                override fun write(bytes: ByteArray) { throw java.io.IOException("no key store") }
                override fun clear() { }
            }
            val link = link(broken)
            link.load(dir)
            assertEquals("hunter22", link.settings().password)
            assertEquals(before, f.readText(), "left for the next start to try again, not lost")
        } finally { dir.deleteRecursively() }
    }
}
