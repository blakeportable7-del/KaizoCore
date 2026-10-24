package com.ironmonone.pcui

import androidx.compose.ui.graphics.toArgb
import com.ironmonone.tracker.GbaTracker
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** besteon's screens opened from the live card (PcScreens), drawn from a stand-in for the session's tracker. */
class PcScreensTest {
    private val files = File(System.getProperty("java.io.tmpdir"), "pcui-screens-${System.nanoTime()}")

    private val bag = listOf(
        GbaTracker.BagRow(26, "SUPER POTION", 3, "HP", true, 50050),
        GbaTracker.BagRow(17, "POTION", 5, "HP", false, 50020),
        GbaTracker.BagRow(23, "FULL HEAL", 2, "Status", true, 40002),
        GbaTracker.BagRow(14, "ANTIDOTE", 1, "Status", false, 40001),
        GbaTracker.BagRow(34, "ETHER", 1, "PP", false, 30000),
        GbaTracker.BagRow(4, "POKE BALL", 7, "Balls", false, 0),
    )

    private val source = object : PcScreens.Source {
        override fun info(species: Int) = PcPanel.StarterFacts(species, "GOLISOPOD", listOf("Bug", "Water"), "530", "186", "108.0 kg", "-", listOf(5, 9, 13), emptyList(), "")
        override fun learnset(species: Int) = listOf(1 to 33, 5 to 45, 9 to 39, 13 to 55)
        override fun moveName(id: Int) = mapOf(33 to "Tackle", 45 to "Growl", 39 to "Tail Whip", 55 to "Water Gun")[id] ?: "#$id"
        override fun moveRow(id: Int): com.ironmonone.tracker.MoveRow? = null
        override fun moveText(id: Int) = "A move."
        override fun heals() = bag
        override fun catchRates(hpAdjust: Int): GbaTracker.CatchRates? = null
        override fun battleDetails(): GbaTracker.BattleDetails? = null
        override fun routeName(mapId: Int) = "ROUTE 29"
        override fun routeAreas(mapId: Int) = emptyMap<String, List<GbaTracker.RouteMon>>()
        override fun routeSeen(mapId: Int) = emptySet<Int>()
        override val runTables = true
        override fun trainer(id: Int): GbaTracker.TrainerInfo? = null
        override fun trainersOnRoute(mapId: Int) = emptyList<Int>()
        override fun itemName(id: Int) = "#$id"
        override fun sprite(species: Int) = null
    }

    private fun save(r: PcPanel.Renderer, input: PcPanel.Input, name: String) {
        File("build/screens").mkdirs()
        File("build/screens/$name.bmp").writeBytes(r.render(input))
        ImageIO.write(ImageIO.read(File("build/screens/$name.bmp")), "png", File("build/screens/$name.png"))
    }

    @Test
    fun `the note link on a Pokemon's info card opens that species' note, and the wheel turns pages`() {
        try {
            PcPanel.Renderer(files, PcPanel.Input(Samples.party, 3), scale = 3f).use { r ->
                r.screens.source = source
                val input = PcPanel.Input(Samples.party, 3)
                var asked = 0
                r.screens.onNoteFor = { asked = it }
                r.screens.push(PcScreens.Screen.PokemonInfo(768))
                r.render(input)
                // "(Leave a note)" at (6, 140), 115 by 13 (Blake, 2026-10-11: it did nothing and closed every card).
                r.click(30, 146)
                assertEquals(768, asked)
                // A card with no pages: the wheel leaves it alone.
                assertEquals(false, r.screens.wheel(down = true))
                assertEquals(true, r.hoverAt(134f, 146f), "the back arrow is clickable")
            }
        } finally { files.deleteRecursively() }
    }

    @Test
    fun `level-up moves never name a move the run has not shown`() {
        // Blake, 2026-10-11: "all the moves in green, you should not be able to see them". Learnset 1, 5, 9, 13; level 5.
        try {
            PcPanel.Renderer(files, PcPanel.Input(Samples.party, 3), scale = 3f).use { r ->
                r.screens.source = source
                val input = PcPanel.Input(Samples.party, 3)
                fun opens(row: Int): Boolean {
                    r.render(input); r.click(60, 30 + row * 11 + 4)
                    val opened = r.screens.top is PcScreens.Screen.MoveInfo
                    if (opened) r.screens.back()
                    return opened
                }
                r.screens.push(PcScreens.Screen.MovesLearned(768, 5, own = true))
                assertEquals(true, opens(0), "your own Pokemon's level 1 move")
                assertEquals(true, opens(1), "your own Pokemon's level 5 move")
                assertEquals(false, opens(2), "your own Pokemon's level 9 move is not named")
                r.screens.close()
                r.screens.push(PcScreens.Screen.MovesLearned(768, 5))
                assertEquals(false, opens(0), "a foe's move not seen is not named")
                r.screens.close()
                r.screens.push(PcScreens.Screen.MovesLearned(768, null))
                assertEquals(false, opens(3), "an info card names nothing")
            }
        } finally { files.deleteRecursively() }
    }

    @Test
    fun `a click on the heals box opens the bag, its Status tab lists the status items, and the back arrow returns to the card`() {
        try {
            PcPanel.Renderer(files, PcPanel.Input(Samples.party, 3), scale = 3f).use { r ->
                r.screens.source = source
                val input = PcPanel.Input(Samples.party, 3)
                save(r, input, "card")
                r.click(30, 62)
                save(r, input, "heals-all")
                assertEquals(PcScreens.Screen.HealsInBag, r.screens.top, "the heals box opens Heals in Bag")
                assertEquals("All", r.screens.tab, "it opens on All, as besteon's does")
                // The tabs at y 18: All, HP, PP, Status, Battle; Status is the fourth.
                val statusX = run {
                    var x = 7
                    for (t in PcScreens.HEAL_TABS) { if (t == "Status") break; x += com.ironmonone.app.referenceWidth(t) + 6 }
                    x + 3
                }
                r.click(statusX, 21)
                save(r, input, "heals-status")
                assertEquals("Status", r.screens.tab)
                r.click(134, 146)
                r.render(input)
                assertNull(r.screens.top, "the back arrow returns to the card")
            }
        } finally { files.deleteRecursively() }
    }

    @Test
    fun `the card's HP and level lines, held item, badges and bag rows each open their screen`() {
        val t = Samples.party.party[0]
        val st = Samples.party.copy(party = listOf(t.copy(mon = t.mon.copy(heldItem = 220))))
        try {
            PcPanel.Renderer(files, PcPanel.Input(st, 3), scale = 3f).use { r ->
                r.screens.source = source
                val input = PcPanel.Input(st, 3)
                r.render(input)
                r.click(60, 18); r.render(input)
                assertEquals(PcScreens.Screen.PokemonInfo(793), r.screens.top, "the HP line opens the Pokemon's info")
                r.screens.close(); r.render(input)
                r.click(60, 28); r.render(input)
                assertEquals(PcScreens.Screen.PokemonInfo(793), r.screens.top, "the level line too")
                r.screens.close(); r.render(input)
                r.click(50, 39); r.render(input)
                assertEquals(PcScreens.Screen.ItemInfo(220), r.screens.top, "the held item opens its description")
                save(r, input, "item-info")
                r.screens.close(); r.render(input)
                r.click(14, 145); r.render(input)
                assertTrue(r.screens.top is PcScreens.Screen.Note, "a badge opens its note: ${r.screens.top}")
                r.screens.close(); r.render(input)
                r.click(120, 84); r.render(input)
                assertTrue(r.screens.top is PcScreens.Screen.MovesLearned, "Pow in the moves header opens the moves: ${r.screens.top}")
                r.screens.close(); r.render(input)
                r.click(30, 62); r.render(input)
                r.click(40, 34); r.render(input)
                assertEquals(PcScreens.Screen.ItemInfo(26), r.screens.top, "a bag row opens that item")
            }
        } finally { files.deleteRecursively() }
    }

    @Test
    fun `a move type's screen names what it hits twice, half and not at all`() {
        val fire = PcScreens.typeLines(10, natDex = true)
        assertTrue("Super effective (2x): Bug, Steel, Grass, Ice" in fire, fire)
        assertTrue("Not very effective (1/2x): Rock, Fire, Water, Dragon" in fire, fire)
        assertTrue("No effect" !in fire, fire)
    }

    @Test
    fun `the nature's plus is blue, its minus red, and both are the same width`() {
        try {
            PcPanel.Renderer(files, PcPanel.Input(Samples.party, 3), scale = 3f).use { r ->
                val input = PcPanel.Input(Samples.party, 3)
                save(r, input, "nature-marks")
                val img = ImageIO.read(File("build/screens/nature-marks.png"))
                val k = img.width / 150
                // Adamant: + on ATK (row 1), the minus on SPA (row 3). The mark sits at x 118 to 125 of row y 7 + 10 * i.
                fun xs(row: Int, c: androidx.compose.ui.graphics.Color): List<Int> {
                    val sy = 7 + 10 * row
                    val out = mutableListOf<Int>()
                    for (y in (sy - 2) * k until (sy + 8) * k) for (x in 117 * k until 126 * k) if (img.getRGB(x, y) == c.toArgb()) out += x
                    return out
                }
                val plus = xs(1, NatureMarks.UP); val minus = xs(3, NatureMarks.DOWN)
                assertTrue(plus.isNotEmpty(), "ATK's + is blue")
                assertTrue(minus.isNotEmpty(), "SPA's minus is red")
                assertTrue(xs(1, NatureMarks.DOWN).isEmpty() && xs(3, NatureMarks.UP).isEmpty(), "no colour crosses over")
                assertEquals(plus.max() - plus.min(), minus.max() - minus.min(), "the plus and the minus are equally wide")
                assertEquals(5 * k - 1, plus.max() - plus.min())
            }
        } finally { files.deleteRecursively() }
    }

    @Test
    fun `the moves header opens the level-up moves, and a move opens its info`() {
        try {
            PcPanel.Renderer(files, PcPanel.Input(Samples.party, 3), scale = 3f).use { r ->
                r.screens.source = source
                val input = PcPanel.Input(Samples.party, 3)
                r.render(input)
                r.click(20, 84)
                save(r, input, "moves-learned")
                val top = r.screens.top
                assertTrue(top is PcScreens.Screen.MovesLearned, "$top")
                // The third row, Tail Whip at 9, is above this level 6 Pokemon: not named, and a click opens nothing.
                r.click(40, 56)
                r.render(input)
                assertTrue(r.screens.top is PcScreens.Screen.MovesLearned, "a move to come opens nothing: ${r.screens.top}")
                // The second row, Growl at 5, is learned: it opens its info.
                r.click(40, 45)
                r.render(input)
                assertEquals(PcScreens.Screen.MoveInfo(45), r.screens.top, "the second row is Growl")
                r.click(134, 146); r.click(134, 146); r.render(input)
                assertNull(r.screens.top)
            }
        } finally { files.deleteRecursively() }
    }
}
