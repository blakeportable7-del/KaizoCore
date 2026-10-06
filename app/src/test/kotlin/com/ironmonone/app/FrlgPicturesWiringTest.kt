package com.ironmonone.app

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * How the FireRed and LeafGreen pictures reach the screen (2026-09-29). The viewer and the mark are
 * Compose and load Android classes, so, as in GbPanelWiringTest, the wiring is checked in the source; the
 * table and the maths behind it are FrlgPicturesTest and PanZoomTest.
 */
class FrlgPicturesWiringTest {
    private fun src(name: String) = File("src/main/kotlin/com/ironmonone/app/$name").readText().replace("\r\n", "\n")

    private val ui get() = src("FrlgPicturesUi.kt")

    /** Only a FireRed or LeafGreen game, and only a place with pictures, gets the mark: every caller asks placeFor or lookupFor. */
    @Test
    fun `the mark is asked for through placeFor and lookupFor, which answer null for any other game`() {
        val panel = src("TrackerPanel.kt")
        assertTrue("mapMark = FrlgPictures.placeFor(state.badgeSet, state.mapId)?.let { place ->" in panel, "the wild battle card")
        assertTrue("picturesFor = FrlgPictures.lookupFor(state?.badgeSet)," in panel, "the route info screen")
        val side = src("SideScreens.kt")
        assertTrue("pictures = FrlgPictures.placeFor(st.badgeSet, mapId)," in side, "Trainers on Route")
        assertTrue("routePictures = FrlgPictures.placeFor(st?.badgeSet, st?.mapId)," in side, "Trainer Info")
        // Nobody reaches around the check to the whole table, and the DS and Game Boy code knows nothing of it.
        val main = File("src/main/kotlin").walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        for (f in main) {
            val text = f.readText()
            if (f.name != "FrlgPictures.kt") assertFalse("FrlgPictures.places" in text, "${f.name} reads the whole table")
            // Demo.kt stages a FireRed place with pictures (--es demo gba-frlg) so the mark and viewer can be checked on
            // a phone; it names the feature in a comment only.
            if (f.name !in setOf("FrlgPictures.kt", "FrlgPicturesUi.kt", "TrackerPanel.kt", "RouteInfoScreen.kt", "TrainerScreens.kt", "SideScreens.kt", "PanZoom.kt", "Demo.kt", "TrackerOptions.kt", "TrackerGearDialog.kt"))
                assertFalse("Frlg" in text || "PanZoom" in text, "${f.name} mentions the pictures")
        }
    }

    /**
     * The tracker shows the place's name only in a wild battle, on the route info screen, on Trainers on Route and on
     * Trainer Info. SETUP's TRAINERS ON ROUTE button opens Trainers on Route for the map the player is on, whatever
     * it is called and whether or not anyone is fighting, so it is the way to the pictures of any place, including
     * the ones the tracker has no name for (the two Underground Path tunnels) or no battle happens in.
     */
    @Test
    fun `SETUP reaches the mark for any place through Trainers on Route`() {
        assertTrue("\"TRAINERS ON ROUTE\"" in src("TrackerGearDialog.kt"))
        // Tracker Setup and the FILE bar's TRACKER sheet share the link; Tracker Setup closes itself first (FileBar.kt).
        assertTrue("onTrainers = if (trackerRef?.hasTrainerData == true) { { side.trainersDialog = true } } else null," in src("PlayScreen.kt"))
        assertTrue("onTrainers = close(links.onTrainers)" in src("FileBar.kt"))
        val side = src("SideScreens.kt")
        assertTrue("if (gba != null && mapId != null) {" in side, "any map the tracker can read")
        assertTrue("st.routeName ?: \"This map\"" in side, "a place with no name still opens it")
    }

    @Test
    fun `PlayScreen is not touched`() {
        val play = src("PlayScreen.kt")
        assertFalse("Frlg" in play || "PanZoom" in play, "the viewer's state lives in FrlgPicturesUi, not in PlayScreen")
        assertFalse("FrlgMapMark" in play)
    }

    /** A wild battle's card is laid out as before unless there is a mark; with one, the name wraps instead of running under it. */
    @Test
    fun `the wild battle card is unchanged without a mark`() {
        val panel = src("TrackerPanel.kt")
        assertTrue("val wildMark = mapMark.takeIf { isWild }" in panel, "only a wild battle has the mark")
        assertTrue("if (isWild && wildMark != null) Row(verticalAlignment = Alignment.CenterVertically) {" in panel)
        assertTrue(Regex("Pc\\.Text, Modifier\\.weight\\(1f, fill = false\\), wrap = true\\)\\s+wildMark\\(\\)").containsMatchIn(panel), "the mark follows the name")
        assertTrue("\n                    else if (isWild) PixText(routeName ?: \"\", PcRef.FONT, Pc.Text)\n" in panel, "without a mark the name is drawn as before")
    }

    @Test
    fun `the route info screen puts the mark after the name of the map it shows`() {
        val s = src("RouteInfoScreen.kt")
        // The name in sp since rc34 (rc32 audit P2 #19), and SEARCH, which looks up a route, says so.
        val name = s.indexOf("DialogText(src.name.ifBlank { \"---\" }, 14, Pc.Text, Modifier.weight(1f), heading = true)")
        val mark = s.indexOf("picturesFor?.let { FrlgMapMark(it(src.mapId)) }")
        val search = s.indexOf("PcTap(\"SEARCH\", 8, Pc.Gold, \"Look up a route\")")
        assertTrue(name in 0 until mark && mark < search, "name, then the mark, then SEARCH")
    }

    @Test
    fun `Trainers on Route and Trainer Info put the mark after the place`() {
        val s = src("TrainerScreens.kt")
        val head = s.indexOf("DialogText(routeName.uppercase(), 16, Pc.Text, Modifier.weight(1f), heading = true)")
        assertTrue(head > 0 && s.indexOf("FrlgMapMark(pictures)") > head, "after the header's name")
        val route = s.indexOf("DialogText(routeName ?: \"???\", 13, Pc.Text, Modifier.weight(1f, fill = false))")
        assertTrue(route > 0 && s.indexOf("FrlgMapMark(routePictures)") > route, "after the Route line's name")
        assertTrue("if (routePictures == null) row(\"Route\", routeName ?: \"???\")" in s, "the Route line is as before without pictures")
    }

    /** One picture at a time, read on the IO dispatcher, and nothing keeps the ones already seen. */
    @Test
    fun `pictures are decoded one at a time off the main thread`() {
        val text = ui
        assertEquals(1, Regex("BitmapFactory\\.decodeStream\\(").findAll(text).count(), "one decode call")
        val io = text.indexOf("withContext(Dispatchers.IO)")
        val decode = text.indexOf("BitmapFactory.decodeStream(")
        assertTrue(io in 0 until decode && decode - io < 500, "the decode is inside withContext(Dispatchers.IO)")
        assertTrue("private val oneAtATime = Mutex()" in text && "oneAtATime.withLock {" in text, "a lock lets one decode run at a time")
        assertTrue("ensureActive()" in text, "a picture already left behind is not decoded")
        // The viewer asks for only the picture on screen, and starts over for the next.
        assertTrue("var shot by remember(path) { mutableStateOf<Shot?>(null) }" in text)
        assertTrue("LaunchedEffect(path) {" in text && "shot = Shot(FrlgPictureDecoder.load(context, path))" in text)
        assertFalse("forEach { FrlgPictureDecoder" in text || "map { FrlgPictureDecoder" in text, "never all of them")
    }

    @Test
    fun `pixel art is drawn with nearest neighbour filtering when enlarged`() {
        assertTrue("filterQuality = if (PanZoom.nearestPixel(s)) FilterQuality.None else FilterQuality.Medium," in ui)
        assertTrue("detectTransformGestures" in ui && "detectTapGestures(onDoubleTap" in ui, "pinch, drag and double tap")
    }

    @Test
    fun `the mark and every button are 48dp targets and the mark is spoken`() {
        assertTrue(Regex("sizeIn\\(minWidth = 48\\.dp, minHeight = 48\\.dp\\)").findAll(ui).count() >= 2, "the mark and ViewerButton")
        // On the tracker card the mark keeps the PC tracker's card shape: text height, no 48dp box (review 2026-09-29).
        assertTrue("FrlgMapMark(place, compact = true)" in src("TrackerPanel.kt"), "the card's mark is the compact one")
        assertTrue(".clickable(onClickLabel = spoken, role = Role.Button) { open = true }" in ui)
        assertTrue(".semantics { contentDescription = spoken }" in ui)
        assertTrue("val spoken = place.spoken()" in ui)
        assertTrue(".clickable(enabled = enabled, onClickLabel = spoken, role = Role.Button) { onClick() }" in ui, "buttons are announced as buttons")
    }

    @Test
    fun `the viewer has the buttons and words the brief asks for, and no hidden item tab`() {
        assertFalse("PictureTab" in ui || "HIDDEN" in ui || "hiddenAsset" in ui, "the hidden item pictures went on 2026-09-30")
        assertTrue("PixText(place.name, 12, Pc.Gold, Modifier.weight(1f))" in ui, "the place is named at the top")
        assertTrue("if (count > 1) Row(" in ui, "Previous, Next and the counter only for a place with more than one map")
        assertTrue("\"\${at + 1} of \$count\"" in ui, "the counter reads 2 of 5")
        for (label in listOf("\"Previous\"", "\"Next\"", "\"Close\"")) assertTrue("ViewerButton($label" in ui, label)
        assertTrue("Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false))" in ui, "full screen, Back closes")
    }

    /** The copy rules: no em dash, no mention of how the work is made. */
    @Test
    fun `the new user-facing text follows the copy rules`() {
        for (name in listOf("FrlgPictures.kt", "FrlgPicturesUi.kt", "PanZoom.kt")) {
            val text = src(name)
            assertFalse('\u2014' in text || '\u2013' in text, "$name has a dash that is not a hyphen")
            assertFalse(Regex("\\bAI\\b").containsMatchIn(text), "$name mentions AI")
        }
        val about = src("AboutScreen.kt")
        val credit = about.indexOf("The FireRed and LeafGreen dungeon maps are by Bill Greenwald (doctrDNA)")
        assertTrue(credit > about.indexOf("\"Credits\"") && credit < about.indexOf("This is not an official IronMON or Nat. Dex release"), "in the Credits section")
    }

    /** The files a place points at are the app's assets: nothing else spells the folder out. */
    @Test
    fun `only FrlgPictures knows where the files are`() {
        for (f in File("src/main/kotlin").walkTopDown().filter { it.isFile && it.extension == "kt" }) {
            if (f.name == "FrlgPictures.kt") continue
            assertFalse("frlg/" in f.readText(), "${f.name} spells out the pictures' folder")
        }
    }
}
