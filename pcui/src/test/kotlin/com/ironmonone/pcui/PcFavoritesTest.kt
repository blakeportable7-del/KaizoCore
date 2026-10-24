package com.ironmonone.pcui

import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The favorites on the PC (PLAN row 8): the app's matching, the run's pool, the start screen. */
class PcFavoritesTest {
    private val files = File(System.getProperty("java.io.tmpdir"), "pcui-fav-${System.nanoTime()}")

    @Test
    fun `names are matched as the drop-down matches them, held to the run's pool`() {
        val char = PcFavorites.suggest("char", natDex = true)
        assertEquals(listOf("Charmander", "Charmeleon", "Charizard", "Charjabug", "Charcadet"), char.take(5), "$char")
        assertEquals(6, char.size)
        // Any generation is offered; a name typed whole, with no other name left, offers nothing (it is in the box).
        assertEquals("Helioptile", PcFavorites.suggest("helio", natDex = true).firstOrNull())
        assertEquals(emptyList(), PcFavorites.suggest("Helioptile", natDex = true))
        assertEquals("Gengar", PcFavorites.resolve("gengar", natDex = true))
        assertEquals("Mr. Mime", PcFavorites.resolve("mr mime", natDex = true))
        assertEquals("Charmander", PcFavorites.resolve("char", natDex = true), "the drop-down's first offer")
        assertNull(PcFavorites.resolve("zzzzzz", natDex = true))
        // A Vanilla pool run is held to Gens 1 to 3: no Gen 4 favorite, and three boxes shown of the nine kept.
        assertEquals("Lucario", PcFavorites.resolve("lucario", natDex = true))
        assertNull(PcFavorites.resolve("lucario", natDex = false))
        assertEquals(3, PcFavorites.scope(natDex = false).shown)
        assertEquals(9, PcFavorites.scope(natDex = true).shown)
        val nine = PcFavorites.slots("Gengar,Charizard,,Lucario,,,,,")
        assertEquals(9, nine.size)
        assertEquals(listOf("Gengar", "Charizard", "Lucario"), PcFavorites.icons(nine, natDex = true).map { it.name })
        assertEquals(listOf("Gengar", "Charizard"), PcFavorites.icons(nine, natDex = false).map { it.name }, "a Vanilla run counts the first three")
        assertTrue(PcFavorites.icons(nine, natDex = true).all { (it.species ?: 0) > 0 }, "each has the game's picture")
        val mega = PcFavorites.icons(PcFavorites.slots("Meganium-M"), natDex = true).single()
        assertEquals("Meganium-M", mega.name)
        assertEquals(1242, mega.pack, "the Nat. Dex id is kept when Heart & Soul has no row for the form")
    }

    /** A starter ball holding [name], in the game's own (Heart & Soul) numbering. */
    private fun ball(at: String, name: String) =
        com.ironmonone.tracker.GbaTracker.BallOption(at, com.ironmonone.app.HnsNumbers.fromPack(com.ironmonone.app.Favorites.idOf(name)!!)!!, name)

    @Test
    fun `a favorite in a starter ball is named only when the mode lets you take it, as the phone's FavoriteBall`() {
        assertEquals("kaizo", PcFavorites.mode(File("RSE NatDex v1.2 Kaizo.rnqs")))
        assertEquals("superkaizo", PcFavorites.mode(File("HnS Super Kaizo.rnqs")))
        assertNull(PcFavorites.mode(File("My build.rnqs")))
        val balls = listOf(ball("LEFT", "Gengar"), ball("MIDDLE", "Pikachu"), ball("RIGHT", "Mewtwo"))
        val bst = mapOf("Gengar" to 500, "Pikachu" to 320, "Mewtwo" to 680).mapKeys { (k, _) -> balls.first { it.name == k }.species }
        val slots = PcFavorites.slots("Gengar,Mewtwo,,,,,,,")
        // Kaizo, Nat. Dex rules: Gengar (500) may be taken; Mewtwo (680) may not, so its ball is never named; Pikachu is
        // no favorite, and what that ball holds is never said.
        assertEquals(listOf(0 to "Gengar"), PcFavorites.favoriteBalls(slots, true, "kaizo", balls) { bst[it] })
        // Journey (any starter anyway) and a build no mode names: no Favorites Clause, nothing marked.
        assertEquals(emptyList(), PcFavorites.favoriteBalls(slots, true, PcFavorites.JOURNEY, balls) { bst[it] })
        assertEquals(emptyList(), PcFavorites.favoriteBalls(slots, true, null, balls) { bst[it] })
        // No favorite in any ball: nothing marked, the random pick stands.
        assertEquals(emptyList(), PcFavorites.favoriteBalls(PcFavorites.slots("Lucario"), true, "kaizo", balls) { bst[it] })
        // A Vanilla pool run counts the first three favorites only.
        assertEquals(emptyList(), PcFavorites.favoriteBalls(PcFavorites.slots(",,,Gengar"), false, "kaizo", balls) { bst[it] })
    }

    @Test
    fun `the ball picker marks the random ball with the die to roll again, or the favorite's ball with no die`() {
        try {
            var rolls = 0
            PcPanel.Renderer(files, PcPanel.Input(null, 0)).use { r ->
                r.onReroll = { rolls++ }
                val white = 0xFFFFFFFF.toInt()
                // The down arrow's stem over a ball: x ball x + 1 + 4, y 5 + ball y - 13 + 2 (the start screen's boxes start at x 0).
                fun arrowOver(img: java.awt.image.BufferedImage, i: Int) = listOf(22 to 12, 45 to 20, 68 to 12)[i].let { (x, y) -> img.getRGB(x, y) == white }
                val random = PcPanel.Input(null, 12, ballPicker = PcBallPick(1))
                val img = ImageIO.read(r.render(random).inputStream())
                assertEquals(listOf(false, true, false), (0..2).map { arrowOver(img, it) })
                r.click(88, 46); r.render(random)
                assertEquals(1, rolls, "the die rolls again")
                val fav = PcPanel.Input(null, 12, ballPicker = PcBallPick(1, listOf(2 to "Gengar")))
                val img2 = ImageIO.read(r.render(fav).inputStream())
                assertEquals(listOf(false, false, true), (0..2).map { arrowOver(img2, it) }, "the favorite's ball, not the random one")
                r.click(88, 46); r.render(fav)
                assertEquals(1, rolls, "no die beside a favorite")
            }
        } finally { files.deleteRecursively() }
    }

    @Test
    fun `all nine favorites scroll through the box, and the header opens the editor`() {
        try {
            val favs = PcFavorites.icons(PcFavorites.slots("Gengar,Charizard,Scyther,Pikachu,Lucario,Meganium-M,Bulbasaur,Squirtle,Charmander"), natDex = true)
            assertEquals(9, favs.size)
            assertEquals(1242, favs[5].pack)
            var edits = 0
            PcPanel.Renderer(files, PcPanel.Input(null, 0)).use { r ->
                assertNotNull(PcPictures.picture(null, 1242), "Meganium-M draws Meganium's walking sheet")
                assertNotNull(PcPictures.picture(null, 94), "Gengar draws its own sheet")
                r.onEditFavorites = { edits++ }
                val input = PcPanel.Input(null, 740, title = "Heart & Soul 2.0.6 (KaizoCore)", favorites = favs)
                val img = ImageIO.read(r.render(input).inputStream())
                assertEquals(150 to 160, img.width to img.height)
                // The pictures are not in the panel: BizHawk's script slides them across the box (PanelOut's favbanner.txt).
                r.click(20, 58); r.render(input)
                assertEquals(1, edits, "the header opens the editor")
                r.render(PcPanel.Input(Samples.party, 3)); r.click(20, 58); r.render(PcPanel.Input(Samples.party, 3))
                assertEquals(1, edits)
            }
        } finally { files.deleteRecursively() }
    }

    @Test
    fun `the lab card shows the info lines and Show resistances opens the chart`() {
        try {
            val facts = PcPanel.StarterFacts(1, "AVALUGG-H", listOf("Ice"), "514", "180", "505.0 kg", "-", listOf(16, 32), listOf("Fire" to 2.0, "Fighting" to 2.0, "Rock" to 2.0, "Steel" to 2.0), "")
            PcPanel.Renderer(files, PcPanel.Input(null, 2, starter = facts)).use { r ->
                val img = ImageIO.read(r.render(r.input).inputStream())
                assertEquals(150 to 160, img.width to img.height)
                r.click(110, 96)
                r.render(r.input)
                assertEquals(listOf(15), r.chart.types, "Show resistances opens the chart for the card's types")
            }
        } finally { files.deleteRecursively() }
    }

    @Test
    fun `the lab card's History opens the move history for that Pokemon, and its back arrow goes back to the card`() {
        try {
            val facts = PcPanel.StarterFacts(1, "AVALUGG-H", listOf("Ice"), "514", "180", "505.0 kg", "-", listOf(16, 32), emptyList(), "")
            PcPanel.Renderer(files, PcPanel.Input(null, 2, starter = facts)).use { r ->
                r.render(r.input)
                r.click(120, 67)
                r.render(r.input)
                assertEquals(PcScreens.Screen.MoveHistory(1, "AVALUGG-H", listOf(16, 32)), r.screens.top, "History opens MoveHistoryScreen")
                r.click(134, 146)
                r.render(r.input)
                assertEquals(null, r.screens.top, "the back arrow returns to the card")
            }
        } finally { files.deleteRecursively() }
    }
}
