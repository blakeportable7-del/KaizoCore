package com.ironmonone.app.stream

import java.net.URI
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The OBS scene collection. What OBS demands of it is in ObsScene's own comment,
 * with the OBS source files each rule came from; these tests hold the file to
 * those rules and to this phone's address and key.
 */
class ObsSceneTest {

    private val base = "http://192.168.1.50:8642"
    private val token = "abcd1234"

    @Suppress("UNCHECKED_CAST")
    private fun parse(top: Boolean = false, b: String = base, k: String = token): Map<String, Any?> =
        MiniJson(ObsScene.json(b, k, top)).parse() as Map<String, Any?>

    @Suppress("UNCHECKED_CAST")
    private fun sources(scene: Map<String, Any?>) = scene["sources"] as List<Map<String, Any?>>

    @Suppress("UNCHECKED_CAST")
    private fun settings(src: Map<String, Any?>) = src["settings"] as Map<String, Any?>

    private fun browsers(scene: Map<String, Any?>) = sources(scene).filter { it["id"] == "browser_source" }
    private fun byName(scene: Map<String, Any?>, name: String) = sources(scene).single { it["name"] == name }

    @Test
    fun `it is strict JSON with what OBS's importer checks for`() {
        val scene = parse()
        // StudioImporter::Check: "sources", "name" and "current_scene" must all be there.
        assertNotNull(scene["sources"]); assertNotNull(scene["name"]); assertNotNull(scene["current_scene"])
        assertEquals("KaizoCore stream", scene["name"])
        assertEquals("KaizoCore", scene["current_scene"])
        assertEquals("KaizoCore", scene["current_program_scene"])
        assertEquals(listOf(mapOf("name" to "KaizoCore")), scene["scene_order"])
        // OBS reads these arrays and objects, and a missing one is a different case from an empty one.
        for (k in listOf("groups", "transitions", "quick_transitions", "saved_projectors")) assertEquals(emptyList<Any?>(), scene[k], k)
        assertEquals(emptyMap<String, Any?>(), scene["modules"])
        // A file with no "version" is a legacy, absolute-pixel one, which every OBS version loads.
        assertFalse(scene.containsKey("version")); assertFalse(scene.containsKey("resolution"))
    }

    /**
     * The game, the tracker, the attempt counter, the game over card and the timer (2026-10-05), then the nine favorites
     * (StreamFavorites, 2026-10-03), then the run history (StreamHistory, 2026-10-05).
     */
    private val favoriteNames = (1..StreamFavorites.SLOTS).map { "KaizoCore favorite $it" }
    private val mainNames = listOf("KaizoCore game", "KaizoCore tracker", "KaizoCore attempts", "KaizoCore game over", "KaizoCore timer")
    /** The run history for viewers (StreamHistory, 2026-10-05): the last source, hidden as the favorites are. */
    private val historyName = "KaizoCore history"
    /** Hidden when the scene is imported, and shut down while hidden: the timer, the favorites and the history. */
    private val hiddenNames = listOf("KaizoCore timer") + favoriteNames + historyName
    private val allNames = mainNames + favoriteNames + historyName
    private val count = allNames.size

    @Test
    fun `one scene, six browser sources and one for each favorite, with unique names`() {
        val scene = parse()
        val all = sources(scene)
        assertEquals(15, count, "game, tracker, attempts, game over, timer, nine favorites, history")
        assertEquals(listOf("scene") + List(count) { "browser_source" }, all.map { it["id"] })
        assertEquals(all.size, all.map { it["name"] }.toSet().size, "names are how items find their sources")
        assertEquals(allNames, browsers(scene).map { it["name"] })
        for (s in all) assertEquals(s["id"], s["versioned_id"])
    }

    @Test
    fun `every url in the file is this phone's address with its key`() {
        val scene = parse()
        val urls = browsers(scene).map { settings(it)["url"] as String }
        assertEquals(count, urls.size)
        for (u in urls) {
            val uri = URI(u)
            assertEquals("http", uri.scheme, u)
            assertEquals("192.168.1.50", uri.host, u)
            assertEquals(8642, uri.port, u)
            assertTrue(uri.rawQuery.split('&').contains("k=$token"), "the key is in $u")
        }
        assertEquals(
            setOf("/game", "/tracker", "/attempts.html", "/gameover", "/timer", "/history") + (1..StreamFavorites.SLOTS).map { "/favorite/$it" },
            urls.map { URI(it).path }.toSet(),
            "the game, the tracker, the attempt counter, the game over card, the timer, each favorite's page and the run history",
        )
        // No other string in the file is a URL.
        assertEquals(count, Regex("http://").findAll(ObsScene.json(base, token)).count())
    }

    @Test
    fun `the game source carries its sound into OBS and the others do not`() {
        val scene = parse()
        assertEquals(true, settings(byName(scene, "KaizoCore game"))["reroute_audio"], "Control audio via OBS")
        assertEquals(false, settings(byName(scene, "KaizoCore tracker"))["reroute_audio"])
        assertEquals(false, settings(byName(scene, "KaizoCore attempts"))["reroute_audio"])
    }

    @Test
    fun `the browser source settings are the ones the browser plugin reads`() {
        val scene = parse()
        for (b in browsers(scene)) {
            val s = settings(b)
            assertTrue(s["url"] is String); assertTrue(s["width"] is Long); assertTrue(s["height"] is Long)
            assertEquals(
                "body { background-color: rgba(0, 0, 0, 0); margin: 0px auto; overflow: hidden; }", s["css"],
                "OBS's own default CSS: the page stays see-through",
            )
            assertEquals(false, s["is_local_file"]); assertEquals(true, s["restart_when_active"])
            assertEquals(b["name"] in hiddenNames, s["shutdown"], "only the hidden sources shut down while out of sight")
            assertEquals(255L, b["mixers"], "all audio tracks, as OBS writes a source with sound")
        }
        val game = settings(byName(scene, "KaizoCore game"))
        assertEquals(60L, game["fps"]); assertEquals(true, game["fps_custom"])
        assertEquals(1500L, game["width"]); assertEquals(1080L, game["height"])
    }

    @Test
    fun `every source loads its page again when its scene comes up, so opening OBS first is not a dead end`() {
        // UX audit P0-16. obs-browser reads "restart_when_active" as the checkbox "Refresh browser when scene becomes
        // active" (default false) and "shutdown" as "Shutdown source when not visible". OBS is normally opened before
        // the phone is streaming, so each source's first load fails; without this box nothing ever loads it again, and
        // the streamer sees three error pages until they know to press Refresh cache of current page.
        val sources = browsers(parse())
        assertEquals(count, sources.size)
        for (b in sources) {
            assertEquals(true, settings(b)["restart_when_active"], "${b["name"]} reloads when its scene becomes active")
            // The favorites are hidden in the scene, so they are shut down while hidden and cost OBS nothing until shown.
            if (b["name"] in hiddenNames) assertEquals(true, settings(b)["shutdown"], "${b["name"]} sleeps while hidden")
            else assertEquals(false, settings(b)["shutdown"], "${b["name"]} is not torn down when it is out of sight")
        }
    }

    @Test
    fun `the game source asks for whole-number scaling and the other two say nothing about it`() {
        val scene = parse()
        val query = { s: Map<String, Any?>, name: String -> URI(settings(byName(s, name))["url"] as String).rawQuery.split('&') }
        assertEquals(listOf("k=$token", "int=1"), query(scene, "KaizoCore game"), "int=1 keeps every pixel the same size")
        assertEquals(listOf("k=$token"), query(scene, "KaizoCore tracker"))
        assertEquals(listOf("k=$token"), query(scene, "KaizoCore attempts"))
        assertEquals(listOf("k=$token"), query(scene, "KaizoCore game over"))
        assertEquals(listOf("k=$token"), query(scene, "KaizoCore timer"))
        assertEquals(listOf("k=$token", "bg=none"), query(scene, "KaizoCore history"), "see-through, for over a scene")
        // The top screen version keeps it and adds top=1 after it.
        assertEquals(listOf("k=$token", "int=1", "top=1"), query(parse(top = true), "KaizoCore game"))
    }

    @Test
    fun `the game page has the switch the scene asks for`() {
        // int=1 in the scene means something only because the game page reads it; the two names are pinned together.
        assertContains(StreamPages.game(), "q.get('int') === '1'")
        assertContains(StreamPages.game(), "Math.floor(s)")
    }

    @Test
    fun `a source carries the fields OBS writes, at OBS's defaults`() {
        val scene = parse()
        for (s in sources(scene)) {
            assertEquals(1.0, s["volume"]); assertEquals(0.5, s["balance"])
            assertEquals(true, s["enabled"]); assertEquals(false, s["muted"])
            assertEquals(0L, s["sync"]); assertEquals(0L, s["flags"])
            assertTrue((s["prev_ver"] as Long) > 0)
            assertEquals(emptyMap<String, Any?>(), s["private_settings"])
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun items(scene: Map<String, Any?>) = settings(byName(scene, "KaizoCore"))["items"] as List<Map<String, Any?>>

    @Test
    fun `the scene's items name sources that exist, with unique ids under id_counter`() {
        val scene = parse()
        val names = sources(scene).map { it["name"] }.toSet()
        val items = items(scene)
        assertEquals(count, items.size)
        for (i in items) assertTrue(i["name"] in names, "item ${i["name"]} names a source in the file")
        assertEquals(allNames, items.map { it["name"] }, "items stack in the order the sources are listed")
        // Ids run 1, 2, 3 ... in that order, and id_counter is the last one given, so OBS's next item gets a new id.
        assertEquals((1L..count.toLong()).toList(), items.map { it["id"] })
        // The game, the tracker, the attempts and the game over card are shown; the timer, the favorites and the history
        // are there to show, hidden until the streamer clicks an eye.
        assertEquals(allNames.map { it !in hiddenNames }, items.map { it["visible"] })
        val counter = settings(byName(scene, "KaizoCore"))["id_counter"] as Long
        assertTrue(items.all { (it["id"] as Long) <= counter }, "a new item must not reuse an id")
        assertEquals(count.toLong(), counter, "the highest id given, as OBS keeps it")
    }

    @Test
    fun `the layout fits 1920 by 1080 with nothing overlapping`() {
        val scene = parse()
        class Box(val name: String, val x: Double, val y: Double, val w: Double, val h: Double)
        // What shows when the scene is imported; the hidden favorites are held to the canvas and to each other below.
        val all = items(scene)
        val favorites = all.filter { it["visible"] == false && it["name"] in favoriteNames }
        assertEquals(favoriteNames, favorites.map { it["name"] })
        assertEquals(hiddenNames, all.filter { it["visible"] == false }.map { it["name"] })
        for ((n, f) in favorites.withIndex()) {
            @Suppress("UNCHECKED_CAST") val pos = f["pos"] as Map<String, Double>
            val size = settings(byName(scene, f["name"] as String))["width"] as Long
            assertEquals(n * 128.0, pos["x"], "in a row along the top, one beside the next")
            assertEquals(0.0, pos["y"])
            assertTrue(pos["x"]!! + size <= 1920, "${f["name"]} is inside the canvas")
        }
        val boxes = all.filter { it["visible"] == true }.map { i ->
            @Suppress("UNCHECKED_CAST") val pos = i["pos"] as Map<String, Double>
            @Suppress("UNCHECKED_CAST") val scale = i["scale"] as Map<String, Double>
            val s = settings(byName(scene, i["name"] as String))
            assertEquals(1.0, scale["x"]); assertEquals(1.0, scale["y"])
            assertEquals(5L, i["align"], "top left")
            assertEquals(0L, i["bounds_type"], "no bounds box: the source's own size is the size")
            Box(i["name"] as String, pos["x"]!!, pos["y"]!!, (s["width"] as Long).toDouble(), (s["height"] as Long).toDouble())
        }
        for (b in boxes) {
            assertTrue(b.x >= 0 && b.y >= 0 && b.x + b.w <= 1920 && b.y + b.h <= 1080, "${b.name} is inside the canvas")
        }
        // The game over card is over the game on purpose (it is see-through until a run ends); nothing else overlaps.
        val overGame = "KaizoCore game over"
        for (a in boxes) for (b in boxes) if (a !== b && overGame != a.name && overGame != b.name) {
            val overlap = a.x < b.x + b.w && b.x < a.x + a.w && a.y < b.y + b.h && b.y < a.y + a.h
            assertFalse(overlap, "${a.name} and ${b.name} overlap")
        }
        // The game gets the big left area, in the order they stack: game at the bottom, the card above it.
        assertEquals(listOf("KaizoCore game", "KaizoCore tracker", "KaizoCore attempts", overGame), boxes.map { it.name })
        assertEquals(0.0, boxes[0].x); assertEquals(1500.0, boxes[0].w); assertEquals(1080.0, boxes[0].h)
        val card = boxes[3]
        assertTrue(card.x + card.w <= boxes[0].x + boxes[0].w && card.y + card.h <= boxes[0].h, "the card is inside the game's area")
        assertEquals(boxes[0].w - card.x - card.w, card.x, "centred across it")
        assertEquals(1080.0 - card.y - card.h, card.y, "and down it")
    }

    @Test
    fun `the top screen version changes only the game address`() {
        val normal = parse()
        val top = parse(top = true)
        val gameOf = { s: Map<String, Any?> -> settings(byName(s, "KaizoCore game"))["url"] as String }
        assertEquals(gameOf(normal) + "&top=1", gameOf(top))
        for (n in allNames.drop(1)) {
            assertEquals(settings(byName(normal, n))["url"], settings(byName(top, n))["url"], n)
        }
        assertEquals(1, Regex("top=1").findAll(ObsScene.json(base, token, topOnly = true)).count())
        assertEquals(0, Regex("top=1").findAll(ObsScene.json(base, token)).count())
    }

    @Test
    fun `a key with odd characters is encoded and the file is still valid`() {
        val scene = parse(k = "a b&c=d\"e")
        for (b in browsers(scene)) {
            val q = URI(settings(b)["url"] as String).rawQuery
            assertTrue(q.startsWith("k=a+b%26c%3Dd%22e"), q)
        }
    }

    @Test
    fun `it never says a run or a mode is needed, and has no em dash`() {
        val text = ObsScene.json(base, token)
        assertFalse(text.contains(0x2014.toChar()))
        for (word in listOf("IronMON", "Nuzlocke", "hack")) {
            assertFalse(text.contains(word, ignoreCase = true), "the scene works for any game, so it says nothing about '$word'")
        }
        assertFalse(Regex("\\brun\\b", RegexOption.IGNORE_CASE).containsMatchIn(text), "nor about a run")
    }
}
