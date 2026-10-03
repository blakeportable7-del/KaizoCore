package com.ironmonone.app

import com.ironmonone.tracker.Gen3Pictures
import com.ironmonone.tracker.PokemonDecoder
import com.ironmonone.tracker.TrackedMon
import com.ironmonone.tracker.TrackerState
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Where the GBA tracker's pictures from the game (TrackedMon.picture, EnemyInfo.picture: shiny, Unown's letter, Deoxys's
 * form; Gen3PicturesRomTest proves them on the dumps) reach the screen: the party and opponent cards, Team View and the
 * game-over team, each falling back to the species' own picture as before. None of it is in PlayScreen, which sits at
 * the ART verifier's limit.
 */
class GbaRomPicturesWiringTest {
    private fun src(name: String) = File("src/main/kotlin/com/ironmonone/app/$name").readText().replace("\r\n", "\n")

    @Test
    fun `the cards draw the game's own picture first and the species' picture when there is none`() {
        val panel = src("TrackerPanel.kt")
        assertTrue("            sprite = romPicture(p.picture) ?: spriteFor(m.species),\n" in panel, "your Pokemon's card")
        assertTrue("                else romPicture(e.picture) ?: spriteFor(e.species),\n" in panel, "the opponent's card")
        assertTrue("PcSprite(romPicture(p.picture) ?: spriteFor(box.iconSpecies))" in src("TeamView.kt"), "Team View")
        val over = src("GameOverHost.kt")
        assertTrue("else romPicture(m.picture) ?: spriteFor(m.species) }," in over, "the game-over team")
        assertFalse("romPicture" in src("PlayScreen.kt"))
        assertTrue("fun romPicture(p: com.ironmonone.tracker.Gen3Pictures.Picture?): ImageBitmap? = remember(p) {" in src("PcTracker.kt"))
    }

    private fun own(species: Int, picture: Gen3Pictures.Picture?) = TrackedMon(
        mon = PokemonDecoder.Mon(pid = 1L, level = 5, nickname = "", species = species, heldItem = 0, friendship = 70,
            moves = listOf(33, 0, 0, 0), pp = listOf(35, 0, 0, 0), ivs = List(6) { 0 }, evs = List(6) { 0 }, ppUps = List(4) { 0 },
            abilitySlot = 0, nature = 0, shiny = picture != null, status = 0, curHp = 20, maxHp = 20, atk = 5, def = 5, spe = 5, spAtk = 5, spDef = 5),
        speciesName = "#$species", moveNames = emptyList(), base = null, picture = picture,
    )

    @Test
    fun `the game-over team keeps each Pokemon's picture from the game`() {
        val shiny = Gen3Pictures.Picture(64, 64, IntArray(64 * 64) { if (it == 0) 0xFF00F800.toInt() else 0 })
        val state = TrackerState(partyCount = 2, party = listOf(own(6, shiny), own(25, null)), inBattle = false, isWildBattle = false)
        val team = gameOverTeam(null, state)
        assertEquals(shiny, team[0].picture)
        assertTrue(team[0].shiny)
        assertNull(team[1].picture)
        // A picture is equal by its pixels, so a read that finds the same one again is no change to the screen.
        assertEquals(shiny, Gen3Pictures.Picture(64, 64, shiny.argb.copyOf()))
    }
}
