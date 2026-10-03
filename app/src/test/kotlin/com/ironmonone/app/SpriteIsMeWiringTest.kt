package com.ironmonone.app

import com.ironmonone.tracker.Overworld
import com.ironmonone.tracker.OverworldAddresses
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Where "Play as your Pokemon" is joined to the rest of the app and to the emulator, and that each join is the size it
 * is meant to be: PlayScreen at the ART verifier's method-size limit knows one line of it; the video callback makes one
 * call; the credits are where the licence wants them; a backup carries what the player made.
 */
class SpriteIsMeWiringTest {
    private fun src(name: String) = File("src/main/kotlin/com/ironmonone/app/$name").readText().replace("\r\n", "\n")

    @Test
    fun `who you play as is one of three, drawn round, and a second tap goes back to the lead`() {
        // Blake, 2026-09-30: "you can select multiple playas buttons at a time and you can't uncheck always use".
        val ui = src("SpriteIsMeUi.kt")
        assertTrue("if (s.who == SpriteIsMeSettings.Who.ALWAYS) s.who = SpriteIsMeSettings.Who.LEAD" in ui)
        assertTrue("s.who = if (s.who == SpriteIsMeSettings.Who.OWN) SpriteIsMeSettings.Who.LEAD else SpriteIsMeSettings.Who.OWN" in ui)
        val toggle = src("TrackerGearDialog.kt").substringAfter("internal fun GearToggle(").substringBefore("\n}\n")
        assertTrue("if (radio) androidx.compose.foundation.shape.CircleShape else androidx.compose.ui.graphics.RectangleShape" in toggle)
        // And the line about which Pokemon have sprites sits right under the switch, whether it is on or off.
        val controls = ui.substringAfter("Check(pix, SpriteIsMeCopy.TITLE, s.on)")
        assertTrue(controls.indexOf("Note(pix, SpriteIsMeCopy.GENS)") in 1 until controls.indexOf("if (!s.on) return"))
    }

    /**
     * Blake, 2026-09-30: "play as Gen 4+ Pokemon". Most have a walking sprite now, so the line under the switch says so,
     * and what happens with one of the few that has none; it no longer says Gen 1-3 only.
     */
    @Test
    fun `the line under the switch says what is true now`() {
        val g = SpriteIsMeCopy.GENS
        assertFalse("Gen 1-3" in g || "only" in g, g)
        assertTrue(g.startsWith("Most Pokemon have a walking sprite."), g)
        assertTrue("you stay the trainer" in g, g)
        assertTrue(g in SpriteIsMeCopy.all, "held to the copy rules with the rest")
    }

    @Test
    fun `the picker lists every Pokemon with a sprite, through the one lookup`() {
        val picker = src("SpriteIsMeUi.kt").substringAfter("private fun SpeciesPickerDialog(").substringBefore("\n}\n")
        assertTrue("val ix = WalkingPals.ready(ctx)" in picker, picker.take(400))
        assertTrue("remember(ix) { SpriteIsMeLogic.choices(Favorites.namesInOrder) { id, dex -> ix?.find(id, dex) } }" in picker, picker.take(400))
        assertFalse("1..411" in picker, "no Gen 1-3 cap")
        // The lead says how its game numbers species (SpriteLead.dexOf, MaxDex's own way included), and the engine asks
        // the art by that numbering.
        assertTrue("return Reading.Found(lead(mon, dexOf(map), map))" in src("SpriteIsMeLogic.kt").substringAfter("fun read("))
        // Drawn as the lead's look says, a picked Pokemon as its shiny when the switch says so (Blake, 2026-10-03).
        assertTrue("SpriteIsMeLogic.choose(s.who, s.always, s.own, ownReady, leadNow, art::pal, s.alwaysShiny, art::look)" in src("SpriteIsMe.kt"))
    }

    /** "show Gen 4+ animations on the trackers": the GBA and Game Boy panel's icon finds either set by the game's numbering. */
    @Test
    fun `the tracker's animated icon is found by the game's numbering, past 411 too`() {
        val head = src("PcTracker.kt").substringAfter("fun PcHeadBlock(").substringBefore("PcSprite(sprite)")
        assertTrue("WalkingPals.ready(iconCtx).let { ix -> remember(ix, iconSpecies, iconDex, iconLook) { if (iconSpecies > 0) ix?.find(iconSpecies, iconDex, iconLook) else null } }" in head, head.takeLast(600))
        assertFalse("in 1..411" in head, "no Gen 1-3 cap on the animated icon")
        val panel = src("TrackerPanel.kt")
        // MaxDex in Play numbers its own way past 1235 (MaxDexPlayTest), told by the session; every game goes through trackerDex.
        assertEquals(2, Regex("iconDex = WalkingPals\\.trackerDex\\(generation, speciesTotal, maxDex\\)").findAll(panel).count(), "your card and the enemy's")
        assertEquals(2, Regex("iconDex = iconDex,").findAll(panel).count())
    }
    private fun cpp(name: String) = File("../libretrodroid/src/main/cpp/$name").readText().replace("\r\n", "\n")
    private fun notice() = File("../NOTICE").readText().replace("\r\n", "\n")

    @AfterTest fun tearDown() { SpriteIsMeSettings.reset() }

    // ------------------------------------------------------------------ the app

    @Test
    fun `PlayScreen knows one line of it, and it is the call that hosts the engine`() {
        val play = src("PlayScreen.kt")
        val lines = play.lines().filter { "SpriteIsMe" in it }
        assertEquals(1, lines.size, "PlayScreen is at the ART verifier's size limit: one line, not more: $lines")
        assertTrue(lines.single().trim().startsWith("SpriteIsMeHost(retro, platform)"), lines.single())
        // Nothing of the feature's logic is in the Play screen: no overlay, no settings, no art.
        assertFalse("SpriteOverlay" in play || "SpriteIsMeSettings" in play || "SpriteArt" in play || "SheetSet" in play)
        // The host sits inside the composable, after the retro view state it reads.
        assertTrue(play.indexOf("SpriteIsMeHost(") > play.indexOf("var retro by remember"))
    }

    @Test
    fun `the section is in the tracker gear and in the emulator settings, both reachable without a run`() {
        val gear = src("TrackerGearDialog.kt")
        assertEquals(1, Regex("SpriteIsMeGearSection\\(\\)").findAll(gear).count())
        val settings = src("EmulatorSettingsDialog.kt")
        assertTrue("SpriteIsMeSettingsSection()" in settings)
        assertTrue("platform == Platform.GBA" in settings.substringBefore("SpriteIsMeSettingsSection()").takeLast(120), "for Game Boy Advance games only")
        val ui = src("SpriteIsMeUi.kt")
        assertTrue("fun SpriteIsMeGearSection() = SpriteIsMeControls(pix = true)" in ui)
        assertTrue("fun SpriteIsMeSettingsSection() = SpriteIsMeControls(pix = false)" in ui)
        // The controls never ask what kind of game is running: no session, no run, no verified kind.
        for (f in listOf("SpriteIsMeUi.kt", "SpriteIsMe.kt", "SpriteIsMeLogic.kt", "SpriteIsMeSettings.kt", "SpriteIsMeStore.kt", "SpriteIsMeArt.kt")) {
            val text = src(f)
            assertFalse(Regex("\\.isRun\\b|session\\.|RomKind|\\.kind\\b|tracked\\b").containsMatchIn(text), "$f gates on the mode")
        }
    }

    @Test
    fun `the gear says so in one line when the game is not one it works on`() {
        // Not a GBA game, and a GBA game it does not know, each have their line, and the controls stop there.
        val ui = src("SpriteIsMeUi.kt")
        assertTrue("SpriteIsMeSupport.State.NotGba -> SpriteIsMeCopy.NOT_GBA" in ui)
        assertTrue("is SpriteIsMeSupport.State.Unsupported -> support.why" in ui)
        val host = src("SpriteIsMe.kt")
        assertTrue("SpriteIsMeSupport.State.NotGba" in host && "SpriteIsMeCopy.NAT_DEX" in host && "SpriteIsMeCopy.NOT_KNOWN" in host)
        assertTrue("if (!gba)" in host, "a Game Boy or DS game is told")
        // Nat. Dex is not refused up front: its addresses are read out of the game (tracker-gba's OverworldScan) and it only
        // gets the note when that finds nothing. The words the tracker module gives are the words the app shows.
        assertTrue("Overworld.resolve(map, reader)" in host, "the runner asks resolve, which reads Nat. Dex out of its own code")
        assertFalse("Overworld.forMap(" in host, "forMap alone would refuse Nat. Dex without looking")
        val natDex = com.ironmonone.tracker.GameMap.FIRERED_U_V10.copy(name = "Nat. Dex", expandedSpeciesIds = true)
        assertEquals(SpriteIsMeCopy.NAT_DEX, Overworld.whyNot(natDex))
        assertEquals(SpriteIsMeCopy.NOT_KNOWN, Overworld.whyNot(com.ironmonone.tracker.GameMap.FIRERED_U_V10.copy(name = "Some hack")))
    }

    /**
     * rc32 audit P2 #87: a hack built from the decompilations keeps its base game's header and moves its code. It got the
     * base game's table, the native side never found its overworld, and the section showed its switch and never changed
     * the character, with no word. Its own code is read now (Overworld.resolve), and when that fails it is told so.
     */
    @Test
    fun `a game whose overworld moved is told so, in the tracker's own words`() {
        val moved = com.ironmonone.tracker.GameMap.EMERALD_U
        assertEquals(SpriteIsMeCopy.NO_OVERWORLD, SpriteIsMeRunner.refusal(moved))
        assertEquals(SpriteIsMeCopy.NO_OVERWORLD, Overworld.whyNot(moved))
        val natDex = com.ironmonone.tracker.GameMap.FIRERED_U_V10.copy(name = "Nat. Dex", expandedSpeciesIds = true)
        assertEquals(SpriteIsMeCopy.NAT_DEX, SpriteIsMeRunner.refusal(natDex))
        val unknown = com.ironmonone.tracker.GameMap.FIRERED_U_V10.copy(name = "Some hack")
        assertEquals(SpriteIsMeCopy.NOT_KNOWN, SpriteIsMeRunner.refusal(unknown))
        assertTrue("SpriteIsMeSupport.State.Unsupported(refusal(map))" in src("SpriteIsMe.kt"))
        assertTrue(SpriteIsMeCopy.NO_OVERWORLD in SpriteIsMeCopy.all)
        // MaxDex is read out of its own code since 2026-10-03; when that finds nothing it is named, not called Nat. Dex.
        val maxDex = com.ironmonone.tracker.GameMap.MAXDEX_FR_10
        assertEquals(SpriteIsMeCopy.MAX_DEX, SpriteIsMeRunner.refusal(maxDex))
        assertEquals(SpriteIsMeCopy.MAX_DEX, Overworld.whyNot(maxDex))
        assertTrue(SpriteIsMeCopy.MAX_DEX in SpriteIsMeCopy.all)
    }

    /**
     * rc32 audit P2 #88, P3 #66, P2 #106: a tick runs on the main thread once a display frame. It decoded the player's
     * sheets whole the first time each showed, opened the four sheet files on every frame, and decoded every Pokemon met.
     */
    @Test
    fun `the art is decoded off the main thread, and a tick only crops what is ready`() {
        val art = src("SpriteIsMeArt.kt").substringAfter("class AndroidSpriteArt(").substringBefore("\n}\n")
        for (fn in listOf("override fun pal(", "override fun look(", "override fun palSheets(", "override fun palFrame(", "override fun ownReady(",
            "override fun ownSheets(", "override fun ownFrame(", "override fun ownPicture(", "private fun palFor(", "override fun readiness(")) {
            val body = art.substringAfter(fn).substringBefore("\n\n")
            assertTrue(body.length < art.length / 2, fn)
            assertFalse("BitmapFactory" in body || "SpriteIsMeStore" in body || "WalkingPals.bitmap" in body || "readBytes" in body, "$fn reads in a tick: $body")
        }
        val prepare = art.substringAfter("suspend fun prepare()")
        assertTrue("BitmapFactory.decodeFile" in prepare && "WalkingPals.bitmap(" in prepare, "the decoding is in prepare")
        // Each decode, when it is done, tells the ticks (readiness): a run resumed with the switch on stayed the trainer
        // because nothing did (2026-10-03).
        for (fn in listOf("private fun prepareOwn(", "private fun preparePal(")) {
            assertTrue("decoded.incrementAndGet()" in art.substringAfter(fn).substringBefore("\n    }\n"), "$fn says when it is done")
        }
        assertTrue("override fun readiness(): Int = decoded.get() * 2 + if (WalkingPals.ready(ctx) != null) 1 else 0" in art)
        assertTrue("val (sx, sy) = sheet.cell(row, index, b.width, b.height) ?: return null" in art, "the cut SpriteIsMeEverySheetTest checks every sheet with")
        assertTrue("launch(Dispatchers.Default) { art.prepare() }" in src("SpriteIsMe.kt"), "which the runner starts off the main thread")
        assertTrue("SpriteIsMeSupport.ownNote?.takeIf { s.who == SpriteIsMeSettings.Who.OWN" in src("SpriteIsMeUi.kt"), "art it cannot draw is said")
    }

    /**
     * The test bot's keys went to the core alone, never to the walking sprites' idle clock and facing (SpriteMotion), so
     * in every run the bot played every sprite fell asleep 55 seconds in and never walked (2026-10-03). They go through
     * one helper now that feeds both, as the pad and a controller do.
     */
    @Test
    fun `every key the test bot sends reaches the walking sprites too, as the pad's do`() {
        val bot = File("src/debug/kotlin/com/ironmonone/app/bot/BotPort.kt").readText().replace("\r\n", "\n")
        val helper = bot.substringAfter("private fun key(action: Int, code: Int) {").substringBefore("\n    }\n")
        assertTrue("LibretroDroid.onKeyEvent(0, action, code)" in helper && "SpriteMotion.key(action, code)" in helper, helper)
        assertEquals(1, Regex("LibretroDroid\\.onKeyEvent\\(").findAll(bot).count(), "no key goes to the core past the helper")
        assertTrue(Regex("\\bkey\\(KeyEvent\\.ACTION_DOWN, code\\)").findAll(bot).count() >= 2 && Regex("\\bkey\\(KeyEvent\\.ACTION_UP, code\\)").findAll(bot).count() >= 3)
    }

    // ------------------------------------------------------------------ backup

    @Test
    fun `a backup carries the settings and the art, and only those`() {
        for (p in listOf("prep/sprite-is-me.txt", "prep/spriteisme/picture.png", "prep/spriteisme/sheet/idle.png", "prep/spriteisme/sheet/faint.png")) assertTrue(Backup.admits(p), p)
        assertFalse(Backup.admits("prep/spriteisme/../saves.txt"))
        assertFalse(Backup.admits("prep/spriteisme/picture.png.tmp"))
        assertFalse(Backup.admits("prep/sprite-is-me.txt.tmp"))
        assertFalse(Backup.admits("prep/spriteism/picture.png"))
    }

    @Test
    fun `an imported sprite and the switch come back after a restore`() {
        val a = Files.createTempDirectory("simbk-a").toFile(); val b = Files.createTempDirectory("simbk-b").toFile()
        try {
            SpriteIsMeSettings.load(File(a, SpriteIsMeSettings.FILE))
            SpriteIsMeSettings.on = true; SpriteIsMeSettings.always = 25
            val png = ByteArray(40).also { byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte()).copyInto(it); it[19] = 8; it[23] = 8 }
            assertTrue(SpriteIsMeStore.savePicture(a, png))
            val zip = java.io.ByteArrayOutputStream().also { Backup.write(a, it) }.toByteArray()
            assertTrue(Backup.read(b, zip.inputStream()) > 0)
            assertContentEquals(png, SpriteIsMeStore.pictureFile(b).readBytes())
            SpriteIsMeSettings.load(File(b, SpriteIsMeSettings.FILE))
            assertTrue(SpriteIsMeSettings.on); assertEquals(25, SpriteIsMeSettings.always)
            assertEquals(SpriteIsMeSettings.Own.PICTURE, SpriteIsMeSettings.own)
        } finally { a.deleteRecursively(); b.deleteRecursively() }
    }

    // ------------------------------------------------------------------ credits

    @Test
    fun `UTDZac and Sprite Is Me are credited in NOTICE and on the About screen, next to Walking Pals`() {
        val n = notice()
        assertTrue("UTDZac / SpriteIsMe-IronmonExtension - MIT    https://github.com/UTDZac/SpriteIsMe-IronmonExtension" in n)
        assertTrue("(FRLG_Invisible_Trainer_-_Maple_Compatible.ips) is NOT shipped or applied" in n, "the extension's IPS is named, and refused")
        val about = src("AboutScreen.kt")
        val walking = about.indexOf("Walking Pals icon set")
        val credit = about.indexOf("Play as your Pok\\u00e9mon follows Sprite Is Me")
        assertTrue(walking in 0 until credit && credit - walking < 900, "the credit sits right after the Walking Pals one")
        assertTrue("UTDZac" in about.substring(credit, credit + 200))
        assertTrue("MIT license" in about.substring(credit, credit + 300).replace("\" +\n                \"", ""))
        assertTrue("CreditLink(\"https://github.com/UTDZac/SpriteIsMe-IronmonExtension\") { openOrSay(it) }" in about)
        assertFalse('\u2014' in about.substring(credit, credit + 400))
        // The licence NOTICE states is the repository's own, where a checkout is at hand.
        val lic = File(System.getProperty("user.home"), "ironmon-ref/SpriteIsMe-IronmonExtension/LICENSE").takeIf { it.isFile }
        if (lic != null) assertTrue("MIT License" in lic.readText().take(100) && "UTDZac" in lic.readText().take(100))
    }

    @Test
    fun `the invisible trainer patch is not in the repository`() {
        val roots = listOf(File("src"), File("../libretrodroid/src"), File("../tracker-gba/src"), File("../tools"), File("../site"))
        val hits = roots.filter { it.isDirectory }.flatMap { r -> r.walkTopDown().filter { it.isFile && (it.name.contains("Invisible_Trainer", true) || it.extension.equals("ips", true) && it.name.contains("trainer", true)) }.toList() }
        assertTrue(hits.isEmpty(), "the extension's IPS must not ship: $hits")
    }

    // ------------------------------------------------------------------ the emulator

    @Test
    fun `the video callback makes one call, first, and hands what it returns to the tap and the renderer`() {
        val c = cpp("libretrodroid.cpp")
        val body = c.substringAfter("void LibretroDroid::handleVideoRefresh(").substringBefore("\n}\n")
        val statements = body.substringAfter("{\n").lines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("//") }
        assertTrue(statements.first().startsWith("SpriteOverlay::apply(data, width, height, pitch);"), "the first statement of handleVideoRefresh: ${statements.first()}")
        assertEquals(1, Regex("SpriteOverlay::apply\\(").findAll(c).count(), "one call, in that one place")
        assertTrue("video->onNewFrame(data, width, height, pitch);" in body, "the renderer gets the possibly swapped pointer and pitch")
        assertTrue("#include \"sprite_overlay.h\"" in c)
        assertTrue("sprite_overlay.cpp" in cpp("CMakeLists.txt"))
    }

    @Test
    fun `off costs one relaxed atomic load`() {
        val h = cpp("sprite_overlay.h")
        val apply = h.substringAfter("static void apply(").substringBefore("\n    }\n")
        assertTrue(apply.trim().lines().let { l -> l.drop(1).first().trim() } == "if (gSpriteOverlayMode.load(std::memory_order_relaxed) == kSpriteOverlayIdle) return;", apply)
        assertTrue("inline std::atomic<int> gSpriteOverlayMode{kSpriteOverlayIdle};" in h)
        // Nothing before that line takes a lock, reads memory or touches the environment.
        assertFalse("lock" in apply.substringBefore("kSpriteOverlayIdle") || "Environment" in apply.substringBefore("kSpriteOverlayIdle"))
    }

    @Test
    fun `every native method of the Java class has its JNI function, and the config words agree in all three languages`() {
        val java = File("../libretrodroid/src/main/java/com/swordfish/libretrodroid/SpriteOverlay.java").readText().replace("\r\n", "\n")
        val natives = Regex("public static native \\w+(?:\\[\\])? (\\w+)\\(").findAll(java).map { it.groupValues[1] }.toList()
        assertEquals(setOf("configure", "setEnabled", "setSprite", "clearSprite", "reset", "snapshot", "debug"), natives.toSet())
        val jni = cpp("sprite_overlay.cpp")
        for (n in natives) assertTrue("Java_com_swordfish_libretrodroid_SpriteOverlay_$n(" in jni, "no JNI function for $n")
        // The words: Java's CFG_* indices, C++'s reading of them, and Kotlin's table.
        val cfg = Regex("public static final int (CFG_\\w+) = (\\d+);").findAll(java).associate { it.groupValues[1] to it.groupValues[2].toInt() }
        assertEquals(11, cfg.size)
        assertEquals((0..10).toList(), cfg.values.sorted())
        assertEquals(cfg.size, Regex("CONFIG_WORDS = (\\d+)").find(java)!!.groupValues[1].toInt())
        assertTrue("constexpr uint32_t kConfigWords = ${cfg.size};" in cpp("sprite_core.h"))
        val readOrder = Regex("c\\.(\\w+) = v\\[(\\d+)\\];").findAll(jni).associate { it.groupValues[2].toInt() to it.groupValues[1] }
        val javaOrder = mapOf("CFG_MAIN" to "mainAddr", "CFG_OAM_BUFFER_OFFSET" to "oamBufferOffset", "CFG_CB2_OVERWORLD" to "cb2Overworld", "CFG_CB2_BASIC" to "cb2OverworldBasic",
            "CFG_PLAYER_AVATAR" to "playerAvatar", "CFG_SPRITES" to "sprites", "CFG_COORD_OFFSET_X" to "coordOffsetX", "CFG_COORD_OFFSET_Y" to "coordOffsetY",
            "CFG_PLTT_UNFADED" to "plttUnfaded", "CFG_PLTT_FADED" to "plttFaded", "CFG_OBJECT_EVENTS" to "objectEvents")
        for ((name, field) in javaOrder) assertEquals(field, readOrder[cfg.getValue(name)], "$name is word ${cfg[name]}")
        // Kotlin: a table with a different number in every field lands in the words where Java says.
        val probe = OverworldAddresses("probe", main = 101, oamBufferOffset = 102, cb2Overworld = 103, cb2OverworldBasic = 104, playerAvatar = 105, sprites = 106,
            coordOffsetX = 107, coordOffsetY = 108, plttUnfaded = 109, plttFaded = 110, objectEvents = 111)
        val words = probe.toConfig()
        val kotlinOrder = mapOf("CFG_MAIN" to 101L, "CFG_OAM_BUFFER_OFFSET" to 102L, "CFG_CB2_OVERWORLD" to 103L, "CFG_CB2_BASIC" to 104L, "CFG_PLAYER_AVATAR" to 105L,
            "CFG_SPRITES" to 106L, "CFG_COORD_OFFSET_X" to 107L, "CFG_COORD_OFFSET_Y" to 108L, "CFG_PLTT_UNFADED" to 109L, "CFG_PLTT_FADED" to 110L, "CFG_OBJECT_EVENTS" to 111L)
        for ((name, v) in kotlinOrder) assertEquals(v, words[cfg.getValue(name)], name)
        // The wasm harness configures in the same order too.
        val harness = File("../libretrodroid/src/test/native/sprite_core_wasm.cpp").readText()
        assertTrue("h_configure(uint32_t mainAddr, uint32_t oamOff, uint32_t cb2, uint32_t cb2Basic, uint32_t avatar," in harness.replace("\r\n", "\n"))
        // Five distinct tables for six games: LeafGreen v1.0 IS FireRed v1.0 to the overworld (pokeleafgreen.sym equals pokefirered.sym here).
        assertEquals(5, Overworld.ALL.map { it.toConfig().toList() }.toSet().size)
        assertEquals(Overworld.FIRERED_U_V10.toConfig().toList(), Overworld.LEAFGREEN_U.toConfig().toList())
    }

    @Test
    fun `the native code takes nothing from the app but a table and pixels, and the core has no libc in it`() {
        val core = cpp("sprite_core.h")
        val includes = Regex("#include [<\"]([^>\"]+)[>\"]").findAll(core).map { it.groupValues[1] }.toList()
        assertEquals(listOf("stdint.h"), includes, "sprite_core.h must stay freestanding, or the WebAssembly build of it stops working")
        assertFalse(Regex("\\bnew\\b|\\bdelete\\b|std::|malloc|printf|throw\\b").containsMatchIn(core.lines().filterNot { it.trim().startsWith("*") || it.trim().startsWith("//") || it.trim().startsWith("/*") }.joinToString("\n")))
    }
}
