package com.ironmonone.app

import androidx.compose.ui.graphics.Color
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The floating window's see-through mode (Blake, 2026-10-04, for rc35.1). The option, the fade and the touch rule run
 * for real; where the fade is applied is held to the source.
 */
class FloatingSeeThroughTest {
    private val dir = Files.createTempDirectory("see").toFile()
    private val src = File("src/main/kotlin/com/ironmonone/app")
    private fun read(name: String) = File(src, name).readText().replace("\r\n", "\n")
    private fun code(text: String) = text.replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "").replace(Regex("//[^\\n]*"), "")

    @AfterTest fun cleanup() {
        dir.deleteRecursively()
        TrackerOptions.floatingSolid = FloatingSeeThrough.SOLID
        TrackerOptions.floatingLocked = false
    }

    @Test
    fun `the default is solid, and the slider runs from 30 to 100 in tens`() {
        assertEquals(100, TrackerOptions.floatingSolid)
        assertEquals(1f, FloatingSeeThrough.fade(100))
        assertFalse(FloatingSeeThrough.seeThrough(100))
        assertEquals(30, FloatingSeeThrough.clamp(0)); assertEquals(30, FloatingSeeThrough.clamp(-5))
        assertEquals(100, FloatingSeeThrough.clamp(250)); assertEquals(60, FloatingSeeThrough.clamp(61))
        assertEquals(0.3f, FloatingSeeThrough.fade(30), 1e-6f)
        assertTrue(FloatingSeeThrough.seeThrough(90))
    }

    @Test
    fun `a value saves and loads, and an rc35 file with no line reads as solid`() {
        val f = File(dir, "tracker-options.txt")
        f.writeText("showBallPicker=true\nlandscapeTracker=FLOATING\nfloatingLocked=true\n")   // as rc35 wrote it
        TrackerOptions.floatingSolid = 40
        TrackerOptions.load(f)
        assertEquals(100, TrackerOptions.floatingSolid, "no saved value: solid")
        TrackerOptions.floatingSolid = 60
        TrackerOptions.save()
        DiskWriter.drain()
        assertTrue("floatingSolid=60\n" in f.readText())
        TrackerOptions.floatingSolid = 100
        TrackerOptions.load(f)
        assertEquals(60, TrackerOptions.floatingSolid, "a saved value comes back")
        f.writeText("floatingSolid=nonsense\n")
        TrackerOptions.load(f)
        assertEquals(100, TrackerOptions.floatingSolid, "an unreadable value is the default")
        f.writeText("floatingSolid=5\n")
        TrackerOptions.load(f)
        assertEquals(30, TrackerOptions.floatingSolid, "below the least is the least")
        TrackerOptions.load(File(dir, "no-such-file.txt"))
        assertEquals(100, TrackerOptions.floatingSolid, "no file: solid")
    }

    @Test
    fun `the fade takes alpha off a fill and leaves a solid window alone`() {
        val c = Color(0xFF222222)
        assertEquals(c, faded(c, 1f))
        assertEquals(0.6f, faded(c, 0.6f).alpha, 1e-3f)
        assertEquals(0.15f, faded(c.copy(alpha = 0.5f), 0.3f).alpha, 1e-3f, "an already see-through fill fades further")
    }

    @Test
    fun `only the floating window fades, and it fades fills, never words or marks`() {
        val all = src.listFiles { f -> f.extension == "kt" }!!
        val providers = all.filter { "LocalWindowFade provides" in code(it.readText()) }.map { it.name }
        assertEquals(listOf("FloatingTracker.kt", "TrackerHud.kt"), providers.sorted(), "docked, portrait and the second display never set it")
        val solidReaders = all.filter { "floatingSolid" in code(it.readText()) }.map { it.name }.sorted()
        assertEquals(listOf("FloatingTracker.kt", "TrackerGearDialog.kt", "TrackerHud.kt", "TrackerOptions.kt"), solidReaders)
        val ft = code(read("FloatingTracker.kt"))
        assertTrue("graphicsLayer { alpha = fade }.hostBackdrop()" in ft, "the backdrop fades in a layer of its own")
        assertTrue("background(windowFill(Pc.Ground))" in ft, "the title bar's fill fades")
        assertFalse(Regex("""graphicsLayer\s*\{\s*alpha\s*=\s*fade\s*}\s*\)?\s*\{""").containsMatchIn(ft), "no layer fades the content")
        assertTrue(".border(1.dp, Pc.Border)" in ft, "the border stays solid")
        // Every box fill in the panels goes through the fade, none round it.
        for (f in all) if (f.name != "FloatingSeeThrough.kt") {
            assertFalse("TrackerBackground.boxFill(" in code(f.readText()), "${f.name} fills a box without the window's fade")
        }
        // The words take the outline wherever the panels draw them.
        assertTrue("shadow = LocalTrackerTextShadow.current" in code(read("PcTracker.kt")))
    }

    @Test
    fun `locked and see-through, empty space passes taps through, buttons never do, and unlocked never does`() {
        // Locked and see-through: empty space passes, a button does not.
        assertTrue(FloatingSeeThrough.passesThrough(locked = true, percent = 60, interactive = false))
        assertFalse(FloatingSeeThrough.passesThrough(locked = true, percent = 60, interactive = true))
        assertTrue(FloatingSeeThrough.passesThrough(locked = true, percent = 30, interactive = false))
        // Solid: as rc35, nothing passes.
        assertFalse(FloatingSeeThrough.passesThrough(locked = true, percent = 100, interactive = false))
        // Unlocked: never, at any see-through.
        for (p in listOf(30, 60, 90, 100)) for (i in listOf(true, false)) {
            assertFalse(FloatingSeeThrough.passesThrough(locked = false, percent = p, interactive = i), "unlocked at $p")
        }
        // The column stops taking swipes exactly when taps pass.
        assertFalse(FloatingSeeThrough.swipeScrolls(locked = true, percent = 60))
        assertTrue(FloatingSeeThrough.swipeScrolls(locked = true, percent = 100))
        assertTrue(FloatingSeeThrough.swipeScrolls(locked = false, percent = 30))
        // And the window uses that rule, with no touch handler of its own over the content.
        val ft = code(read("FloatingTracker.kt"))
        assertTrue("swipe = FloatingSeeThrough.swipeScrolls(locked, solid)" in ft)
        val column = code(read("TrackerScroll.kt")).substringAfter("private fun SwipeColumn").substringBefore("private fun MoreBelow")
        assertFalse(Regex("verticalScroll|pointerInput|scrollable|draggable|clickable|MoreBelow").containsMatchIn(column),
            "the pass-through column takes no touch and draws no arrow")
        assertTrue("WindowSwipe.target = t" in column, "a swipe moves it, through the activity")
    }
}
