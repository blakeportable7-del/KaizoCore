package com.ironmonone.app

import com.ironmonone.tracker.Overworld
import java.io.DataInputStream
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Blake, 2026-10-03, after Baxcalibur held one pose through a walk on MaxDex: "Fix it for all sprites". Every Walking
 * Pals sheet the app ships is walked through Play as your Pokemon's own frame picker, SpriteIsMeEngine as the phone
 * runs it, with the cut AndroidSpriteArt makes (WalkingPals.Sheet.cell) taken on each sheet's real size: walking and
 * then standing, facing down, up, left and right. A sheet fails when it would hold one pose: one picture through a walk,
 * or the same picture in two directions. The pickers are the same in every game Play as your Pokemon knows (vanilla
 * FireRed, LeafGreen, Ruby, Sapphire and Emerald, Nat. Dex, MaxDex); only the numbering that finds a sheet differs.
 *
 * Twice: once with the keys reaching the phone's idle clock, and once with them going past it, as the test bot's did
 * when the walk was recorded (every sprite slept 55 seconds in, whatever the game did).
 */
class SpriteIsMeEverySheetTest {
    /** Every shipped sheet set a Pokemon is drawn with: Gen 3's own ids but its unused slots, and the second set's keys. */
    private val pals: List<WalkingPals.Pal> =
        ShippedPals.gen3.keys.filter { it.toInt() !in 252..276 }.sortedBy { it.toInt() }.map { WalkingPals.Pal(WalkingPals.Pack.GEN3, it) } +
            ShippedPals.national.keys.sorted().map { WalkingPals.Pal(WalkingPals.Pack.NATIONAL, it) }

    /** A PNG's width and height, out of its header. */
    private fun size(f: File): Pair<Int, Int> = DataInputStream(f.inputStream().buffered()).use { it.skipBytes(16); it.readInt() to it.readInt() }

    /** The sheets as the phone has them, each frame one pixel naming the cell it is cut from, by the phone's own rule. */
    private class SheetArt(
        private val pal: WalkingPals.Pal,
        private val sheets: Map<WalkingPals.Anim, WalkingPals.Sheet>,
        private val sizes: Map<WalkingPals.Anim, Pair<Int, Int>>,
    ) : SpriteIsMeArt {
        override fun pal(id: Int, dex: WalkingPals.Dex) = pal
        override fun palSheets(pal: WalkingPals.Pal) = sheets
        override fun palFrame(pal: WalkingPals.Pal, anim: WalkingPals.Anim, sheet: WalkingPals.Sheet, row: Int, index: Int): ArtPixels? {
            val (w, h) = sizes[anim] ?: return null
            val (x, y) = sheet.cell(row, index, w, h) ?: return null
            return ArtPixels(1, 1, intArrayOf((anim.ordinal shl 28) or (y shl 14) or x))
        }
        override fun ownReady() = false
        override fun ownSheets(): OwnSheets? = null
        override fun ownFrame(anim: WalkingPals.Anim, sheet: WalkingPals.Sheet, row: Int, index: Int): ArtPixels? = null
        override fun ownPicture(): SpriteArt.Fitted? = null
        override fun readiness() = 0
    }

    /** The emulator side: the snapshot the test sets, and what is on screen (the cell and where it is drawn). */
    private class Screen : SpriteOverlayPort {
        var snap = 0L
        var shown: Triple<Int, Int, Int>? = null
        override fun configure(words: LongArray) = true
        override fun setEnabled(on: Boolean) {}
        override fun setSprite(argb: IntArray, w: Int, h: Int, ox: Int, oy: Int): Boolean { shown = Triple(argb[0], ox, oy); return true }
        override fun clearSprite() { shown = null }
        override fun reset() {}
        override fun snapshot() = snap
    }

    private val directions = mapOf(1 to "down", 2 to "up", 3 to "left", 4 to "right")

    @BeforeTest fun setUp() {
        SpriteIsMeSettings.load(java.nio.file.Files.createTempFile("sim-every", ".txt").toFile().also { it.delete() })
        SpriteIsMeSettings.on = true
    }

    @AfterTest fun tearDown() { SpriteIsMeSettings.reset() }

    /** Why [pal] would hold one pose, or null when it walks and turns. */
    private fun holdsOnePose(pal: WalkingPals.Pal, keysReachTheClock: Boolean): String? {
        val sheets = ShippedPals.index.sheets(pal) ?: return "it has no sheets"
        val sizes = sheets.keys.associateWith { size(ShippedPals.file(pal, it)) }
        val screen = Screen()
        val engine = SpriteIsMeEngine(screen, SheetArt(pal, sheets, sizes), afk = { !keysReachTheClock })
        engine.configure(Overworld.FIRERED_U_V10)
        engine.lead = SpriteIsMeLogic.Lead(1, 30, false)
        var frames = 0L
        fun play(ticks: Int, moving: Boolean, facing: Int): List<Triple<Int, Int, Int>> = (1..ticks).mapNotNull {
            frames++
            screen.snap = frames or (1L shl 32) or ((if (moving) 1L else 0L) shl 33) or (facing.toLong() shl 34) or (1L shl 37)
            engine.tick()
            screen.shown
        }
        val walks = directions.keys.associateWith { play(48, moving = true, facing = it) }
        val stands = directions.keys.associateWith { play(3, moving = false, facing = it).lastOrNull() }
        for ((d, seen) in walks) {
            if (seen.isEmpty()) return "nothing drawn walking ${directions[d]}"
            if (seen.toSet().size < 2) return "one picture through a walk ${directions[d]}"
        }
        if (walks.values.map { it.first() }.toSet().size < 4) return "the same picture walking in two directions"
        if (stands.values.any { it == null }) return "nothing drawn standing"
        if (stands.values.toSet().size < 4) return "the same picture standing in two directions"
        return null
    }

    @Test
    fun `every shipped sheet walks and turns, in all four directions`() {
        assertTrue(pals.size >= 1100, "only ${pals.size} sheets")
        val failures = ArrayList<String>()
        for (keys in listOf(true, false)) for (pal in pals) {
            holdsOnePose(pal, keys)?.let { failures += "${pal.pack.dir}/${pal.key}${if (keys) "" else " (keys past the idle clock)"}: $it" }
        }
        println("SpriteIsMeEverySheetTest: ${pals.size} sheets walked and turned, ${pals.count { it.pack == WalkingPals.Pack.GEN3 }} of Gen 1-3 " +
            "and ${pals.count { it.pack == WalkingPals.Pack.NATIONAL }} of the second set, each twice")
        assertEquals(emptyList(), failures.take(40), "${failures.size} hold one pose")
    }
}
