package com.ironmonone.app

import com.ironmonone.core.RomKind
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The favorites as Pokemon icons on the no-party card (Blake, 2026-10-02, on the DS card: "favorite sprites will go
 * here"), as the PC trackers draw them: the Gen 1 to 3 trackers' StartupScreen favorites, the NDS tracker's favorites
 * frame beside its ball picker. They were a gold line of names. Compose cannot run here, so what the cards draw is held
 * to the source; which picture each favorite gets runs for real.
 */
class FavoriteIconsTest {
    private fun src(name: String) = File("src/main/kotlin/com/ironmonone/app/$name").readText().replace("\r\n", "\n")

    @Test
    fun `a DS game draws its favorites by national number, the Game Boy and GBA cards by the tracker's own id`() {
        // Platinum: Treecko is the table's 277 and the DS sprites' 252; Snivy is not in a Gen 4 game.
        assertEquals(
            listOf(FavoriteIcon("Turtwig", 387), FavoriteIcon("Treecko", 252), FavoriteIcon("Snivy", null), FavoriteIcon("Gengar", 94)),
            FavoriteIcons.of(listOf("Turtwig", "treecko", "Snivy", "Gengar"), RomKind.PLATINUM_U),
        )
        assertEquals(listOf(FavoriteIcon("Snivy", 495), FavoriteIcon("Sylveon", null)), FavoriteIcons.of(listOf("Snivy", "Sylveon"), RomKind.BLACK2_U))
        // Emerald: the tracker's id, which is what spriteFor and the bundled pack take; Turtwig is not in it.
        assertEquals(listOf(FavoriteIcon("Treecko", 277), FavoriteIcon("Turtwig", null)), FavoriteIcons.of(listOf(" Treecko ", "Turtwig"), RomKind.EMERALD_U))
        // Red: its 151; a Gold and Silver Pokemon shows its name.
        assertEquals(listOf(FavoriteIcon("Mewtwo", 150), FavoriteIcon("Chikorita", null)), FavoriteIcons.of(listOf("mewtwo", "Chikorita"), RomKind.RED_U))
        // A Nat. Dex run: the whole table, forms included, by the Nat. Dex pack's ids.
        assertEquals(listOf(FavoriteIcon("Charizard-X", 1052), FavoriteIcon("Sylveon", 725)), FavoriteIcons.of(listOf("Charizard-X", "Sylveon"), RomKind.EMERALD_NATDEX_121))
        // A name that is no Pokemon keeps the spelling typed; blank slots are not favorites.
        assertEquals(listOf(FavoriteIcon("Missingno", null)), FavoriteIcons.of(listOf("", "Missingno", "  "), RomKind.EMERALD_U))
    }

    @Test
    fun `nine Nat Dex favorites are nine icons, in the order typed`() {
        val nine = listOf("Bulbasaur", "Gengar", "Mr. Mime", "Mewtwo", "Chikorita", "Treecko", "Turtwig", "Snivy", "Sylveon")
        val icons = FavoriteIcons.of(nine, RomKind.EMERALD_NATDEX_121)
        assertEquals(nine, icons.map { it.name })
        assertTrue(icons.all { it.species != null }, "$icons")
    }

    @Test
    fun `the cards draw the icons, wrapped, each named for a screen reader, the name where there is no picture`() {
        val icons = src("FavoriteIcons.kt")
        assertTrue("FlowRow(" in icons, "the row wraps")
        assertTrue("Image(bmp, contentDescription = f.name" in icons, "each icon is its Pokemon's name to TalkBack")
        assertTrue("PixText(f.name, PcRef.FONT, Pc.Text)" in icons, "no picture: the name")
        // The DS card's pictures come the way its Pokemon cards' do: the player's ROM first (PcAssets.dsSprite).
        assertTrue("PcAssets.dsSprite(c, n, false)" in icons)
        // The random ball row and the favorites share one wrapping row, so they sit side by side where there is room.
        val ds = icons.substringAfter("internal fun DsBallAndFavorites(")
        assertTrue(ds.indexOf("randomBall?.let { RandomBallRow(it, hgss) }") in 0 until ds.indexOf("FavoriteIconRow(icons,"), "the favorites follow the balls")
        // Neither card prints the old line of names any more.
        for (f in listOf("TrackerPanel.kt", "NdsTrackerPanel.kt")) assertFalse("favoriteLine?.list" in src(f), "$f still prints the names line")
        val panel = src("TrackerPanel.kt")
        val noParty = panel.indexOf("state.partyCount == 0 -> PcCard {")
        assertTrue(panel.indexOf("FavoriteIconRow(it, { sp -> spriteFor(sp) })") in noParty until panel.indexOf("state.gameOver != null && ironmonOver"))
        val nds = src("NdsTrackerPanel.kt")
        val card = nds.indexOf("!state.located && !state.inBattle -> PcCard {")
        assertTrue(card > 0 && nds.indexOf("DsBallAndFavorites(randomBall,") in card until nds.indexOf("ironmonOver -> {", card), "on the no-Pokemon card")
    }
}
