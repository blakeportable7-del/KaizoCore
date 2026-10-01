package com.ironmonone.app

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The test bot's port (app/src/debug, bot/BotPort.kt) reads and writes the game's memory and presses buttons. It
 * must never reach a players' build: nothing in the main source set may name it, the main manifest must not
 * declare its provider, and the emulator's step hook list must start empty so a players' build pays nothing.
 */
class BotPortStaysInDebugTest {
    private val app = generateSequence(File("").absoluteFile) { it.parentFile }
        .map { if (File(it, "src/main/kotlin").isDirectory) it else File(it, "app") }
        .first { File(it, "src/main/kotlin").isDirectory }

    @Test
    fun mainCodeNeverNamesTheBot() {
        val hits = File(app, "src/main").walkTopDown()
            .filter { it.isFile && (it.extension == "kt" || it.extension == "xml") }
            .filter { f -> f.readText().let { "com.ironmonone.app.bot" in it || "BotPort" in it } }
            .map { it.relativeTo(app).path }
            .toList()
        assertEquals(emptyList(), hits)
    }

    @Test
    fun theProviderIsDeclaredInTheDebugManifestOnly() {
        assertFalse("botport" in File(app, "src/main/AndroidManifest.xml").readText())
        assertTrue(".bot.BotPortStarter" in File(app, "src/debug/AndroidManifest.xml").readText())
    }

    @Test
    fun theBotListensOnlyOnThisPhone() {
        val port = File(app, "src/debug/kotlin/com/ironmonone/app/bot/BotPort.kt").readText()
        // Loopback only on a phone; the emulator's own virtual network is the one exception.
        assertTrue("getByName(if (isEmulator()) \"0.0.0.0\" else \"127.0.0.1\")" in port)
        assertEquals(1, Regex("""0\.0\.0\.0""").findAll(port.substringAfter("server.bind(")).count())
        assertTrue("android.os.Build.HARDWARE == \"ranchu\"" in port)
        val starter = File(app, "src/debug/kotlin/com/ironmonone/app/bot/BotPortStarter.kt").readText()
        assertTrue("\"enabled\"" in starter, "the port starts only when the PC armed it")
    }

    @Test
    fun onlyTheDebugBotAddsAStepHook() {
        // Read as source: loading GLRetroView in a JVM test would run Android stubs.
        val view = File(app.parentFile, "libretrodroid/src/main/java/com/swordfish/libretrodroid/GLRetroView.kt").readText()
        assertTrue("val stepHooks = java.util.concurrent.CopyOnWriteArrayList<StepHook>()" in view)
        val adders = listOf(File(app, "src/main"), File(app.parentFile, "libretrodroid/src/main")).flatMap { root ->
            root.walkTopDown().filter { it.isFile && it.extension in setOf("kt", "java") && "stepHooks.add" in it.readText() }.toList()
        }
        assertEquals(emptyList(), adders.map { it.name })
        // Nor does anything in a players' build stop the render loop's own frames.
        val holders = listOf(File(app, "src/main"), File(app.parentFile, "libretrodroid/src/main")).flatMap { root ->
            root.walkTopDown().filter { it.isFile && it.extension in setOf("kt", "java") && "holdSteps = true" in it.readText() }.toList()
        }
        assertEquals(emptyList(), holders.map { it.name })
        assertTrue("var holdSteps = false" in view)
    }
}
