package com.ironmonone.app.stream

import java.net.URLEncoder

/**
 * The OBS scene collection the setup page hands out (/obs-scene.json): one scene,
 * "KaizoCore", with the game, the tracker and the attempt counter as browser
 * sources, every URL pointing at this phone with its key.
 *
 * The format is OBS Studio's own scene collection file, the one under
 * %APPDATA%\obs-studio\basic\scenes, and it imports through Scene Collection,
 * Import Scene Collection. Read from OBS's source, not from memory:
 *
 *  - The importer (frontend/importers/studio.cpp, StudioImporter::Check) accepts a
 *    file only if it has "sources", "name" and "current_scene". Its default name for
 *    the new collection is "name"; it adds a number if that name is taken.
 *  - A source is {"id", "versioned_id", "name", "settings", ...} (libobs/obs.c,
 *    obs_load_source_type). Anything left out takes libobs's default: volume 1.0,
 *    balance 0.5, enabled, unmuted. The values written here are OBS's own defaults
 *    so a later save does not change them.
 *  - The scene's "items" (libobs/obs-scene.c, scene_load_item) find their source by
 *    "name" (the uuid is optional), and place it by "pos", "scale" and "bounds".
 *  - A file with no "version" key is a legacy one, whose coordinates are absolute
 *    pixels. OBS 32 and later load it in "absolute" mode and older versions never
 *    had another, so it works everywhere; that is why "pos" is pixels and the
 *    "*_rel" keys are absent.
 *  - The browser source (obs-browser, obs-browser-plugin.cpp) reads "url", "width",
 *    "height", "css", "fps", "fps_custom", "shutdown", "restart_when_active" and
 *    "reroute_audio", the last being the checkbox "Control audio via OBS", which is
 *    what puts a browser source's sound in the Audio Mixer.
 *
 * Layout at 1920 x 1080: the game takes the left 1500 pixels at full height (the
 * page centres its picture in whatever size it is given, on a see-through
 * background), the tracker's column is the right 420, and the attempt counter sits
 * under the tracker. The scene says nothing about runs or modes: it works for any game.
 * Added 2026-09-29 for the stream kit.
 *
 * 2026-09-30 (UX audit P0-16): "restart_when_active" is true on all three sources. It is the checkbox
 * "Refresh browser when scene becomes active" (obs-browser-plugin.cpp, default false). OBS is normally opened
 * before the phone is streaming, so a source's first page load fails, and the error page it leaves behind is
 * never retried; with the box ticked, switching to another scene and back loads the page again. "shutdown"
 * (the checkbox "Shutdown source when not visible") stays false. The game's address also ends in int=1, so
 * the picture is scaled by whole numbers and every pixel is the same size.
 */
object ObsScene {
    const val COLLECTION = "KaizoCore stream"
    const val SCENE = "KaizoCore"
    const val GAME = "KaizoCore game"
    const val TRACKER = "KaizoCore tracker"
    const val ATTEMPTS = "KaizoCore attempts"

    const val CANVAS_W = 1920
    const val CANVAS_H = 1080
    const val TRACKER_W = 420
    const val TRACKER_H = 950
    const val ATTEMPTS_H = 120
    const val GAME_W = CANVAS_W - TRACKER_W

    /**
     * What the two downloads on the setup page are called. The server sends them under these names and the
     * page tells the streamer the same ones, so the file to pick in OBS's Browse box is never a guess.
     */
    const val FILE = "KaizoCore-stream.json"
    const val FILE_TOP = "KaizoCore-stream-top-screen.json"

    /** Added to the game's address: whole-number scaling (the game page's int=1). Before top=1, which the top-screen scene adds. */
    const val GAME_OPTIONS = "&int=1"

    /** OBS Studio 30.2.3, the version number its own files carry as "prev_ver" (libobs LIBOBS_API_VER). */
    private const val PREV_VER = 503447555

    /** OBS's own default for a browser source's CSS: the page's background stays see-through. */
    private const val BROWSER_CSS = "body { background-color: rgba(0, 0, 0, 0); margin: 0px auto; overflow: hidden; }"

    /** The three page addresses in the scene, for the tests and the setup page. */
    fun urls(base: String, token: String, topOnly: Boolean = false): Map<String, String> {
        val k = "k=" + URLEncoder.encode(token, "UTF-8")
        return linkedMapOf(
            GAME to "$base/game?$k$GAME_OPTIONS" + if (topOnly) "&top=1" else "",
            TRACKER to "$base/tracker?$k",
            ATTEMPTS to "$base/attempts.html?$k",
        )
    }

    fun json(base: String, token: String, topOnly: Boolean = false): String = Json.write(build(base, token, topOnly))

    /** [base] is the phone's origin, like http://192.168.1.50:8642, without a trailing slash. */
    fun build(base: String, token: String, topOnly: Boolean = false): Map<String, Any?> {
        val url = urls(base, token, topOnly)
        val game = browser(GAME, url.getValue(GAME), GAME_W, CANVAS_H, audio = true, fps = 60)
        val tracker = browser(TRACKER, url.getValue(TRACKER), TRACKER_W, TRACKER_H)
        val attempts = browser(ATTEMPTS, url.getValue(ATTEMPTS), TRACKER_W, ATTEMPTS_H)

        val items = listOf(
            item(1, GAME, 0.0, 0.0),
            item(2, TRACKER, GAME_W.toDouble(), 0.0),
            item(3, ATTEMPTS, GAME_W.toDouble(), (CANVAS_H - ATTEMPTS_H).toDouble()),
        )
        val scene = source(
            id = "scene", name = SCENE, mixers = 0,
            settings = linkedMapOf("id_counter" to items.size, "custom_size" to false, "items" to items),
        )

        return linkedMapOf(
            "name" to COLLECTION,
            "current_scene" to SCENE,
            "current_program_scene" to SCENE,
            "scene_order" to listOf(mapOf("name" to SCENE)),
            "sources" to listOf(scene, game, tracker, attempts),
            "groups" to emptyList<Any?>(),
            "current_transition" to "Fade",
            "transition_duration" to 300,
            "transitions" to emptyList<Any?>(),
            "quick_transitions" to emptyList<Any?>(),
            "saved_projectors" to emptyList<Any?>(),
            "preview_locked" to false,
            "scaling_enabled" to false,
            "scaling_level" to 0,
            "scaling_off_x" to 0.0,
            "scaling_off_y" to 0.0,
            "modules" to emptyMap<String, Any?>(),
        )
    }

    private fun browser(name: String, url: String, width: Int, height: Int, audio: Boolean = false, fps: Int? = null): Map<String, Any?> {
        val settings = linkedMapOf<String, Any?>(
            "url" to url,
            "width" to width,
            "height" to height,
            "css" to BROWSER_CSS,
            "reroute_audio" to audio,
            "is_local_file" to false,
            "shutdown" to false,
            // Reload the page whenever its scene comes up: the cure for an error box left by OBS opening first.
            "restart_when_active" to true,
        )
        if (fps != null) {
            settings["fps"] = fps
            settings["fps_custom"] = true
        }
        return source(id = "browser_source", name = name, mixers = 255, settings = settings)
    }

    /** A source with the fields OBS writes for one, at OBS's defaults. */
    private fun source(id: String, name: String, mixers: Int, settings: Map<String, Any?>): Map<String, Any?> = linkedMapOf(
        "prev_ver" to PREV_VER,
        "name" to name,
        "id" to id,
        "versioned_id" to id,
        "settings" to settings,
        "mixers" to mixers,
        "sync" to 0,
        "flags" to 0,
        "volume" to 1.0,
        "balance" to 0.5,
        "enabled" to true,
        "muted" to false,
        "push-to-mute" to false,
        "push-to-mute-delay" to 0,
        "push-to-talk" to false,
        "push-to-talk-delay" to 0,
        "hotkeys" to emptyMap<String, Any?>(),
        "deinterlace_mode" to 0,
        "deinterlace_field_order" to 0,
        "monitoring_type" to 0,
        "private_settings" to emptyMap<String, Any?>(),
    )

    /** One scene item: the source's name, and where it sits in pixels. `align` 5 is top-left. */
    private fun item(id: Int, name: String, x: Double, y: Double): Map<String, Any?> = linkedMapOf(
        "name" to name,
        "visible" to true,
        "locked" to false,
        "rot" to 0.0,
        "align" to 5,
        "bounds_type" to 0,
        "bounds_align" to 0,
        "bounds_crop" to false,
        "crop_left" to 0,
        "crop_top" to 0,
        "crop_right" to 0,
        "crop_bottom" to 0,
        "id" to id,
        "group_item_backup" to false,
        "pos" to xy(x, y),
        "scale" to xy(1.0, 1.0),
        "bounds" to xy(0.0, 0.0),
        "scale_filter" to "disable",
        "blend_method" to "default",
        "blend_type" to "normal",
        "private_settings" to emptyMap<String, Any?>(),
    )

    private fun xy(x: Double, y: Double): Map<String, Any?> = linkedMapOf("x" to x, "y" to y)
}
