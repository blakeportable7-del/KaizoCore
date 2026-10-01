package com.ironmonone.app

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The choices behind "Play as your Pokemon": what is saved, that a damaged file cannot put anything odd on screen, that
 * a fresh install is off, and that every line the section shows keeps the copy rules.
 */
class SpriteIsMeSettingsTest {
    private val S = SpriteIsMeSettings
    private val dir = Files.createTempDirectory("simset").toFile()

    @AfterTest fun tearDown() { dir.deleteRecursively(); S.reset() }

    private val file get() = File(dir, "prep/sprite-is-me.txt")

    @Test
    fun `a fresh install is off, plays as the lead, and has nothing chosen`() {
        S.load(file)
        assertFalse(S.on)
        assertEquals(SpriteIsMeSettings.Who.LEAD, S.who)
        assertEquals(0, S.always)
        assertEquals(SpriteIsMeSettings.Own.NONE, S.own)
        assertEquals(0, S.sheetWidth); assertEquals(0, S.sheetHeight)
        assertEquals("", S.idleLengths + S.walkLengths + S.sleepLengths + S.faintLengths)
        assertEquals("on=false\nwho=lead\nalways=0\nown=none\nsheetWidth=0\nsheetHeight=0\nidle=\nwalk=\nsleep=\nfaint=\n", S.text())
    }

    @Test
    fun `every setting survives a save and a load`() {
        S.load(file)
        S.on = true; S.who = SpriteIsMeSettings.Who.ALWAYS; S.always = 94
        S.own = SpriteIsMeSettings.Own.SHEET; S.sheetWidth = 48; S.sheetHeight = 40
        S.idleLengths = "40,6,6"; S.walkLengths = "8, 10"; S.sleepLengths = "30"; S.faintLengths = "12"
        S.save()
        assertEquals(S.text(), file.readText())
        S.reset()
        assertFalse(S.on)
        S.load(file)
        assertTrue(S.on)
        assertEquals(SpriteIsMeSettings.Who.ALWAYS, S.who); assertEquals(94, S.always)
        assertEquals(SpriteIsMeSettings.Own.SHEET, S.own); assertEquals(48, S.sheetWidth); assertEquals(40, S.sheetHeight)
        assertEquals("40,6,6", S.idleLengths); assertEquals("8, 10", S.walkLengths); assertEquals("30", S.sleepLengths); assertEquals("12", S.faintLengths)
        val spec = S.spec
        assertEquals(SheetSet.Spec(48, 40, "40,6,6", "8, 10", "30", "12"), spec)
        assertEquals("8, 10", spec.lengthsText(WalkingPals.Anim.WALK))
    }

    @Test
    fun `it is the phone's setting, in its own file, and the run and the tracker options never see it`() {
        S.load(file)
        S.on = true; S.save()
        assertEquals("prep/sprite-is-me.txt", SpriteIsMeSettings.FILE)
        assertTrue(file.isFile)
        // The tracker's own options file has no key for it: nothing per run.
        assertFalse(TrackerOptions.text().contains("SpriteIsMe", ignoreCase = true) || TrackerOptions.text().contains("spriteIsMe"))
    }

    @Test
    fun `a damaged file cannot put anything odd on screen`() {
        file.parentFile.mkdirs()
        file.writeText("on=maybe\nwho=teleport\nalways=99999\nfallback=-4\nown=hologram\nsheetWidth=500\nsheetHeight=abc\nidle=ab40,\u0007 6<b>\nwalk=" + "9".repeat(500) + "\nnonsense\n=x\nsleep=\n")
        S.load(file)
        assertFalse(S.on, "anything but true is off")
        assertEquals(SpriteIsMeSettings.Who.LEAD, S.who)
        assertEquals(0, S.always)
        assertEquals(SpriteIsMeSettings.Own.NONE, S.own)
        assertEquals(0, S.sheetWidth); assertEquals(0, S.sheetHeight)
        assertEquals("40, 6", S.idleLengths, "digits, commas and spaces only")
        assertTrue(S.walkLengths.length <= 80)
        assertEquals("", S.sleepLengths)
    }

    @Test
    fun `a missing or empty file leaves the defaults, and reloading forgets what was in memory`() {
        S.on = true; S.always = 25
        S.load(File(dir, "nothing-here.txt"))
        assertFalse(S.on); assertEquals(0, S.always)
        file.parentFile.mkdirs(); file.writeText("")
        S.load(file)
        assertFalse(S.on)
    }

    @Test
    fun `ensureLoaded reads the file once per place and never overwrites a running switch`() {
        file.parentFile.mkdirs(); file.writeText("on=true\nalways=25\n")
        S.ensureLoaded(dir)
        assertTrue(S.on); assertEquals(25, S.always)
        S.on = false                      // the player turns it off; asking again must not bring the old value back
        S.ensureLoaded(dir)
        assertFalse(S.on)
    }

    @Test
    fun `frame lengths keep digits, commas and spaces`() {
        assertEquals("40,6 6", S.lengths("40,6 6"))
        assertEquals("40,6", S.lengths("4a0,6\n"))
        assertEquals("", S.lengths("fast"))
        assertEquals(80, S.lengths("1".repeat(200)).length)
    }

    // ------------------------------------------------------------------ the words

    @Test
    fun `the words the section is specified in are the ones it shows`() {
        assertEquals("Play as your Pokemon", SpriteIsMeCopy.TITLE)
        assertEquals("Your lead Pokemon", SpriteIsMeCopy.LEAD)
        assertEquals("Always use", SpriteIsMeCopy.ALWAYS)
        assertEquals("Your own sprite", SpriteIsMeCopy.OWN)
        assertEquals("Choose a picture", SpriteIsMeCopy.CHOOSE_PICTURE)
        assertEquals("Remove", SpriteIsMeCopy.REMOVE)
    }

    @Test
    fun `every line follows the copy rules`() {
        assertTrue(SpriteIsMeCopy.all.size >= 30)
        for (line in SpriteIsMeCopy.all) {
            assertTrue(line.isNotBlank(), "a blank line")
            assertFalse('\u2014' in line || '\u2013' in line || " -- " in line, "no dashes as punctuation: $line")
            assertTrue(line.all { it.code in 32..126 }, "plain characters only: $line")
            assertFalse(Regex("\\bAI\\b|artificial|claude|generated|machine|automat|robot|bot\\b", RegexOption.IGNORE_CASE).containsMatchIn(line), "nothing about how it was made: $line")
            assertFalse(line.endsWith(" ") || line.startsWith(" "), line)
            assertFalse("!" in line, "dry, not excited: $line")
        }
        // The one-line notes for games it cannot work on are plain sentences.
        for (note in listOf(SpriteIsMeCopy.NOT_GBA, SpriteIsMeCopy.NOT_KNOWN, SpriteIsMeCopy.NAT_DEX)) assertTrue(note.endsWith("."), note)
        assertEquals("Found 1 sheet.", SpriteIsMeCopy.sheetsFound(1)); assertEquals("Found 4 sheets.", SpriteIsMeCopy.sheetsFound(4))
    }
}
