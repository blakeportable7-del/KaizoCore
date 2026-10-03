package com.ironmonone.app

import com.ironmonone.patch.RomIdentity.Verdict
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Library's pages as they are drawn (2026-09-30, UX audit P0-10, P0-11, P0-13 and the ROM Hacks item): a Compose
 * screen cannot run on the JVM, so this reads the source for exactly the lines a phone would otherwise be needed to
 * see, as HomeWiringTest does. What the words say is run for real in LibraryShelvesTest, LibraryImportTest,
 * PrepOptionsTest, PrepPlanTest and HackLinksTest.
 */
class LibraryWiringTest {
    private val src = File("src/main/kotlin/com/ironmonone/app")
    private fun read(name: String) = File(src, name).readText().replace("\r\n", "\n")
    private val prepare = read("PrepareScreen.kt")
    private val library = read("RomLibraryScreen.kt")
    private val hacks = read("HacksScreen.kt")

    // ---------------------------------------------------------------- Patched versions

    @Test
    fun `the Patched versions page says why it exists, and most games never need it`() {
        assertTrue("\"Most games are ready as soon as you add them under My games. Use this page only to make a patched version, \" +" in prepare)
        assertTrue("\"such as Faster FireRed, Nat. Dex or Super Kaizo, from a game you already added.\"" in prepare)
        assertFalse("Makes a game ready for Kaizo IronMON" in prepare, "the old first line is gone")
    }

    @Test
    fun `it opens on Standard, and a new file opens on it again`() {
        assertFalse("options.first()" in prepare, "the selection is PrepOptions.default, not whatever comes first")
        assertEquals(2, Regex("PrepOptions\\.default\\(").findAll(prepare).count(), "what the list shows selected and what Prepare runs")
        // A choice made for the last file must not carry over to the next one.
        val onPick = prepare.substringAfter("romFile?.delete(); romName = n; romFile = f; romId = id").substringBefore(".onFailure")
        assertTrue("chosenOption = null" in onPick, "reset on every pick")
    }

    @Test
    fun `the main button says why it is off`() {
        assertTrue("Gen3Button(\"PREPARE\", enabled = !busy && exact, accent = true)" in prepare)
        assertTrue("\"Choose a game file first.\"" in prepare)
        assertTrue("Gen3Button(if (romName == null) \"CHOOSE A GAME FILE\" else \"CHOOSE ANOTHER\"" in prepare, "the reason names a button that exists")
        assertFalse("CHOOSE ROM" in prepare)
        // Only an exact copy of a known game is stored, so "Ready for Kaizo IronMON" is never said about a file it cannot list.
        assertTrue("if (!id.exact) {" in prepare)
        assertFalse("!id.recognised" in prepare || "recognised == true" in prepare)
    }

    @Test
    fun `a file this page cannot set up is told what to do, with the button for it`() {
        assertEquals(null, notSetUpHereLine(Verdict.EXACT))
        assertEquals(null, notSetUpHereLine(Verdict.DAMAGED), "a damaged file's sentence already says what to do")
        assertEquals("Choose a Game Boy, Game Boy Advance or DS game file, or a .zip holding one.", notSetUpHereLine(Verdict.NOT_A_GAME))
        for (v in listOf(Verdict.UNCHECKED, Verdict.OTHER_LANGUAGE, Verdict.OTHER_VERSION, Verdict.CHANGED, Verdict.OTHER_GAME)) {
            assertEquals(
                "This page only works on an exact copy of a game the tracker reads. To play this one, add it under My games. To make a ROM hack, use ROM Hacks on Home.",
                notSetUpHereLine(v), "$v",
            )
            assertTrue(playsWithoutSetup(v), "$v plays, so it gets the button to My games")
        }
        for (v in listOf(Verdict.EXACT, Verdict.DAMAGED, Verdict.NOT_A_GAME)) assertFalse(playsWithoutSetup(v), "$v")
        assertTrue("Gen3Button(\"OPEN MY GAMES\", enabled = !busy) { onMyGames() }" in prepare)
    }

    @Test
    fun `a 7z or a rar is told to be unpacked here too`() {
        assertTrue("LibraryImport.archiveKind(name, head)?.let { throw ArchiveNotOpened(it) }" in prepare)
        assertTrue("t is ArchiveNotOpened -> LibraryImport.archiveLine(t.kind, verb = \"choose\")" in prepare)
    }

    @Test
    fun `no page of Library names the old pages`() {
        for ((name, text) in listOf("PrepareScreen.kt" to prepare, "RomLibraryScreen.kt" to library, "HacksScreen.kt" to hacks, "RunCodes.kt" to read("RunCodes.kt"))) {
            for (old in listOf("Library, All files", "in All files", "Set up a game", "the ROMs tab", "Library tab")) assertFalse(old in text, "$name still says \"$old\"")
        }
    }

    // ---------------------------------------------------------------- My games

    @Test
    fun `My games draws every shelf, and the card says only what the entry says`() {
        assertTrue("for (cat in LibraryStore.Category.entries)" in library, "the new shelf is drawn with the rest")
        assertTrue("Text(entry.subtitle, style = MaterialTheme.typography.bodySmall, color = accent)" in library)
        for (old in listOf("\" · tracked\"", "plays untracked", "\" · verified\"")) assertFalse(old in library, "the card still adds $old")
        assertTrue("val tracked = entry.tracked" in library)
    }

    @Test
    fun `the empty state waits for the library to be read, and its words come from what the code takes`() {
        assertTrue("if (loaded && roms.isEmpty() && patches.isEmpty() && !busy)" in library)
        assertTrue("EmptyState(\"Nothing here yet.\", emptyLibraryLine())" in library)
        assertTrue("else if (!loaded) ShellBusy()" in library, "a slow first read shows the busy bar, not an empty page")
        assertTrue("loaded = true" in library)
    }

    /**
     * rc32 audit P2 #74: PATCH, APPLY and the rows of their windows started a job while one ran. Two runs of one
     * built-in patch wrote one file and both failed, and the first to end turned busy off under the other.
     */
    @Test
    fun `no patch job starts while another runs, and each writes a file of its own`() {
        assertTrue("\"PATCH\", enabled = playable && !busy, onClick = onPatch)" in library, "the card's PATCH")
        assertTrue("accent = romCount > 0, enabled = !busy, onClick = onApply)" in library, "the patch card's APPLY")
        for (fn in listOf("fun runBuiltIn(", "fun runPatch(")) {
            val body = code(library.substringAfter(fn).substringBefore("busy = true"))
            assertTrue("if (busy) return" in body, "$fn returns while a job runs, before it sets busy")
        }
        val dir = java.nio.file.Files.createTempDirectory("prep").toFile()
        val a = PrepRun.patchedTemp(dir, com.ironmonone.core.RomKind.PLATINUM_U)
        val b = PrepRun.patchedTemp(dir, com.ironmonone.core.RomKind.PLATINUM_U)
        assertTrue(a != b && a.isFile && b.isFile, "two runs of one patch, two files: $a $b")
        assertTrue(a.name.endsWith("." + com.ironmonone.core.RomKind.PLATINUM_U.fileExtension))
        assertFalse("\"prep-patched-\${outKind.id}.\${outKind.fileExtension}\"" in read("PrepRun.kt"), "the fixed name is gone")
    }

    /** rc32 audit P2 #73: the result of adding many files sat in the fixed card at the top and pushed the list off the screen. */
    @Test
    fun `the result of the last action is the list's first item`() {
        val header = library.substringAfter("Column(modifier.fillMaxSize().padding(10.dp)) {").substringBefore("LazyColumn(")
        assertFalse("status?.let" in header, "not in the card at the top")
        assertTrue("LazyColumn(Modifier.weight(1f), state = listState," in library, "the list takes the height that is left")
        val list = library.substringAfter("LazyColumn(Modifier.weight(1f)").substringBefore("for (cat in LibraryStore.Category.entries)")
        assertTrue("item(key = \"status\")" in list, "the status comes before the shelves")
        assertTrue("LaunchedEffect(status) { if (status != null) listState.animateScrollToItem(0) }" in library, "a new result is scrolled into view")
    }

    private fun code(text: String) = text.replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "").replace(Regex("//[^\\n]*"), "")

    @Test
    fun `a game of another version can be the game an IPS is for`() {
        val dialog = library.substringAfter("Which game is \$name for?")
        assertTrue("LibraryStore.Category.OTHER_VERSIONS" in dialog.substringBefore("candidates.forEach"))
    }

    // ---------------------------------------------------------------- ROM Hacks

    @Test
    fun `the Find a hack card starts from the game picked, and says to pick one when none is`() {
        assertTrue("Pick a game in step 1 to see hacks made for it." in hacks)
        assertTrue("FindHacks(game, startOpen = patches.isEmpty())" in hacks)
        assertFalse("HackLinks.families.first().key" in hacks, "no default to FireRed's list")
        assertTrue("var family by remember { mutableStateOf<String?>(pickedFamily) }" in hacks)
        assertTrue("selected = family ?: \"\"" in hacks, "no chip is selected until one is chosen")
    }

    @Test
    fun `when nothing fits, the reason names the hack's base and the player's game`() {
        assertTrue("HackLinks.noFitReason(game, patches.toList())" in hacks)
        assertTrue("None of your patches is made for" in hacks, "and the old line is what is said when there is no reason to give")
    }

    @Test
    fun `ROM Hacks adds games the way My games does, and its cards say only what the entry says`() {
        assertTrue("LibraryImport(context, store, progress, forHacks = true)" in hacks)
        assertFalse("the tracker works on it" in hacks || "plays without the tracker" in hacks)
        assertTrue("e.subtitle," in hacks.substringAfter("private fun HackCard"))
    }

    // ---------------------------------------------------------------- the files' copy

    @Test
    fun `the files this work touched carry no dash, no emoji and no old tab name in a string`() {
        val files = listOf(
            "MainActivity.kt", "HomeNav.kt", "PrepareScreen.kt", "PrepOptions.kt", "PrepPlan.kt", "RomLibraryScreen.kt",
            "LibraryStore.kt", "LibraryImport.kt", "RunCodes.kt", "HacksScreen.kt", "HackLinks.kt",
        )
        for (name in files) {
            val text = read(name)
            assertFalse('—' in text || '–' in text, "$name has an em or en dash")
            // Outside the basic plane an emoji is a surrogate pair; inside it, the pictograph blocks.
            assertTrue(text.none { it.isSurrogate() || it.code in 0x2600..0x27BF }, "$name has an emoji or a pictograph")
        }
        val core = File("../core-patch/src/main/kotlin/com/ironmonone/patch/RomIdentity.kt").readText()
        assertFalse('—' in core || '–' in core, "RomIdentity.kt has an em or en dash")
        assertTrue(core.none { it.isSurrogate() || it.code in 0x2600..0x27BF }, "RomIdentity.kt has an emoji or a pictograph")
    }
}
