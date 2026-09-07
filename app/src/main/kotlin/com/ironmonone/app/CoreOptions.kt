package com.ironmonone.app

import com.ironmonone.core.Platform
import java.io.File
import java.util.Properties

/**
 * The emulator settings page's catalogue: the core options worth a row, per
 * console, with the values each core accepts. Keys and values come from the
 * cores' own option tables (dumped from the bundled binaries, 2026-09-05)
 * and the libretro docs for mGBA, Gambatte and melonDS DS; a value the core
 * does not know makes it log "defaulting" and carry on, never crash.
 *
 * Only CHANGED values are stored (prep/coreopts/<platform>.properties), so
 * a core update that renames a default costs nothing.
 *
 * [restart] marks options the core reads at load only; the page says so and
 * the change lands on the next boot of that game.
 */
object CoreOptions {

    data class Option(
        val key: String,
        val label: String,
        val values: List<String>,
        val default: String,
        val group: String,
        val restart: Boolean = false,
        /** Shown under the row. Short, plain. */
        val hint: String? = null,
    ) {
        init { require(default in values) { "$key: default $default not in values" } }
        fun next(current: String): String {
            val i = values.indexOf(current)
            return values[(if (i < 0) 0 else i + 1) % values.size]
        }
    }

    /** The video filter is LibretroDroid's, not a core option; it rides on the same page. */
    const val FILTER_KEY = "kaizo_filter"
    /** Phone-side switches that never reach the core as variables. */
    const val RUMBLE_KEY = "kaizo_rumble"
    const val SENSORS_KEY = "kaizo_sensors"
    val APP_KEYS = setOf(FILTER_KEY, RUMBLE_KEY, SENSORS_KEY)
    private val RUMBLE_ROW = Option(RUMBLE_KEY, "Rumble on the phone", listOf("on", "off"), "on", "Hardware",
        hint = "The motor plays the game's rumble.")
    private val SENSORS_ROW = Option(SENSORS_KEY, "Tilt, gyro and light from the phone", listOf("on", "off"), "on", "Hardware",
        hint = "Only read while a game asks for them.")
    val FILTERS = listOf("Default", "Sharp", "LCD", "CRT", "Upscale")
    const val LINK_GROUP = "Link cable (Wi-Fi)"
    /** DSi rows: drawn as one section with a switch, not as cycling rows. */
    const val DSI_GROUP = "DSi"
    /** The twelve address digits: stored like any option, drawn as one field. */
    const val LINK_IP_GROUP = "LinkIp"

    private fun range(from: Int, to: Int, step: Int = 1) = (from..to step step).map { it.toString() }

    val GBA: List<Option> = listOf(
        Option(FILTER_KEY, "Video filter", FILTERS, "Default", "Video", hint = "LCD and CRT mimic a screen; Upscale smooths pixel art."),
        Option("mgba_color_correction", "Colour correction", listOf("OFF", "GBA", "GBC", "Auto"), "OFF", "Video",
            hint = "GBA reproduces the original screen's washed colours."),
        Option("mgba_interframe_blending", "Frame blending", listOf("OFF", "mix", "mix_smart", "lcd_ghosting", "lcd_ghosting_fast"), "OFF", "Video",
            hint = "Blends frames like the real LCD; fixes flicker some games rely on."),
        Option("mgba_frameskip", "Frameskip", listOf("disabled", "auto", "auto_threshold", "fixed_interval"), "disabled", "Performance"),
        Option("mgba_frameskip_interval", "Frameskip interval", range(0, 10), "0", "Performance"),
        Option("mgba_idle_optimization", "Idle loop removal", listOf("Remove Known", "Detect and Remove", "Don't Remove"), "Remove Known", "Performance", restart = true),
        Option("mgba_audio_low_pass_filter", "Audio low-pass filter", listOf("disabled", "enabled"), "disabled", "Audio"),
        Option("mgba_audio_low_pass_range", "Low-pass range (%)", range(5, 95, 5), "60", "Audio"),
        Option("mgba_use_bios", "Use BIOS file if present", listOf("ON", "OFF"), "ON", "System", restart = true,
            hint = "Needs gba_bios.bin imported below. Without it the built-in HLE BIOS runs."),
        Option("mgba_skip_bios", "Skip BIOS intro", listOf("OFF", "ON"), "OFF", "System", restart = true),
        Option("mgba_solar_sensor_level", "Solar sensor level", range(0, 10) + "sensor", "0", "Hardware",
            hint = "For Boktai. 'sensor' reads the phone's light sensor."),
        Option("mgba_force_gbp", "Game Boy Player rumble", listOf("OFF", "ON"), "OFF", "Hardware", restart = true),
        Option("mgba_allow_opposing_directions", "Allow up+down / left+right", listOf("no", "yes"), "no", "Hardware"),
        RUMBLE_ROW, SENSORS_ROW,
    )

    private val GB_PALETTES = listOf("GB - DMG", "GB - Pocket", "GB - Light",
        "GBC - Blue", "GBC - Brown", "GBC - Dark Blue", "GBC - Dark Brown", "GBC - Dark Green", "GBC - Grayscale",
        "GBC - Green", "GBC - Inverted", "GBC - Orange", "GBC - Pastel Mix", "GBC - Red", "GBC - Yellow",
        "SGB - 1A", "SGB - 1B", "SGB - 1C", "SGB - 1D", "SGB - 1E", "SGB - 1F", "SGB - 1G", "SGB - 1H",
        "SGB - 2A", "SGB - 2B", "SGB - 2C", "SGB - 2D", "SGB - 2E", "SGB - 2F", "SGB - 2G", "SGB - 2H",
        "SGB - 3A", "SGB - 3B", "SGB - 3C", "SGB - 3D", "SGB - 3E", "SGB - 3F", "SGB - 3G", "SGB - 3H",
        "SGB - 4A", "SGB - 4B", "SGB - 4C", "SGB - 4D", "SGB - 4E", "SGB - 4F", "SGB - 4G", "SGB - 4H")

    val GBC: List<Option> = listOf(
        Option(FILTER_KEY, "Video filter", FILTERS, "Default", "Video"),
        Option("gambatte_gb_colorization", "Game Boy colourisation", listOf("disabled", "auto", "GBC", "SGB", "internal", "custom"), "disabled", "Video",
            hint = "For original Game Boy games: auto uses the GBC/SGB palette the game shipped with; internal uses the palette below."),
        Option("gambatte_gb_internal_palette", "Internal palette", GB_PALETTES, "GB - DMG", "Video"),
        Option("gambatte_gbc_color_correction", "GBC colour correction", listOf("GBC only", "always", "disabled"), "GBC only", "Video"),
        Option("gambatte_gbc_color_correction_mode", "Correction mode", listOf("accurate", "fast"), "accurate", "Video"),
        Option("gambatte_dark_filter_level", "Dark filter (%)", range(0, 50, 5), "0", "Video"),
        Option("gambatte_mix_frames", "Frame blending", listOf("disabled", "mix", "lcd_ghosting", "lcd_ghosting_fast"), "disabled", "Video"),
        Option("gambatte_audio_resampler", "Audio resampler", listOf("sinc", "cc"), "sinc", "Audio", restart = true),
        Option("gambatte_gb_hwmode", "Hardware mode", listOf("Auto", "GB", "GBC", "GBA"), "Auto", "System", restart = true,
            hint = "GBA runs GBC games the way a Game Boy Advance did."),
        Option("gambatte_gb_bootloader", "Boot logo", listOf("enabled", "disabled"), "enabled", "System", restart = true),
        Option("gambatte_rumble_level", "Rumble strength", range(0, 10), "10", "Hardware"),
        Option("gambatte_up_down_allowed", "Allow up+down / left+right", listOf("disabled", "enabled"), "disabled", "Hardware"),
        RUMBLE_ROW, SENSORS_ROW,
        // Game Link over Wi-Fi. Gambatte's own network link: one phone is the
        // server, the other joins it by IP; both run the same game on the same
        // Wi-Fi. The server address goes to the core as twelve digit options
        // (LinkIp), which the page shows as one address field.
        Option("gambatte_show_gb_link_settings", "Link settings active", listOf("enabled", "disabled"), "enabled", LINK_GROUP,
            hint = "Leave enabled; the core reads the link rows only while this is on."),
        Option("gambatte_gb_link_mode", "Link mode", listOf("Not Connected", "Network Server", "Network Client"), "Not Connected", LINK_GROUP,
            hint = "Network Server: this phone hosts. Network Client: this phone joins the server address below."),
        Option("gambatte_gb_link_network_port", "Port", range(56400, 56420), "56400", LINK_GROUP),
    ) + (1..12).map { Option(LinkIp.key(it), "Server address digit $it", range(0, 9), "0", LINK_IP_GROUP) }

    val NDS: List<Option> = listOf(
        Option(FILTER_KEY, "Video filter", FILTERS, "Default", "Video"),
        Option("melonds_render_mode", "Renderer", listOf("software", "opengl"), "software", "Video", restart = true,
            hint = "OpenGL is the path to upscaling, if this core build has it."),
        Option("melonds_opengl_filtering", "OpenGL filtering", listOf("nearest", "linear"), "nearest", "Video"),
        Option("melonds_threaded_renderer", "Threaded software renderer", listOf("enabled", "disabled"), "enabled", "Performance", restart = true),
        Option("melonds_hybrid_ratio", "Hybrid big screen ratio", listOf("2", "3"), "2", "Video"),
        // melonDS DS parses exactly two values here (config/parse.hpp, ParseHybridSideScreenDisplay): "one" shows only the
        // other screen small, "both" shows both screens small. Anything else makes the core fall back to both, which is
        // how the top screen came to be drawn twice beside the tracker (2026-09-06).
        Option("melonds_hybrid_small_screen", "Hybrid small screen", listOf("one", "both"), "one", "Video"),
        Option("melonds_audio_interpolation", "Audio interpolation", listOf("disabled", "linear", "cosine", "cubic", "gaussian"), "disabled", "Audio"),
        Option("melonds_audio_bitdepth", "Audio bit depth", listOf("auto", "10bit", "16bit"), "auto", "Audio"),
        Option("melonds_mic_input", "Microphone input", listOf("silence", "blow", "noise"), "silence", "Hardware",
            hint = "blow answers any mic prompt with a breath; noise with static. The phone's own mic is not used."),
        Option("melonds_solar_sensor_host_sensor", "Solar sensor from the phone", listOf("disabled", "enabled"), "disabled", "Hardware"),
        Option("melonds_slot2_device", "Slot-2 device", listOf("auto", "rumble-pak", "expansion-pak"), "auto", "Hardware", restart = true,
            hint = "rumble-pak for games that use it."),
        RUMBLE_ROW, SENSORS_ROW,
        Option("melonds_console_mode", "Console", listOf("ds", "dsi"), "ds", "System", restart = true,
            hint = "DSi needs your own NAND and DSi firmware imported below."),
        Option("melonds_boot_mode", "Boot", listOf("direct", "native"), "direct", "System", restart = true,
            hint = "native shows the DS menu first; needs real firmware."),
        Option("melonds_sysfile_mode", "System files", listOf("builtin", "native"), "builtin", "System", restart = true,
            hint = "native uses bios7.bin, bios9.bin and firmware.bin imported below."),
        Option("melonds_firmware_language", "Firmware language", listOf("auto", "en", "ja", "fr", "de", "it", "es"), "auto", "System", restart = true),
        Option("melonds_start_time_mode", "Clock", listOf("sync", "relative", "absolute"), "sync", "System", restart = true,
            hint = "sync follows the phone's clock."),
        // DSi: the file-path options the core discovers by scanning the system
        // folder. Our import names are the only values; sent to the core only
        // while console mode is dsi (PlayScreen.coreVariables), so a plain DS
        // never hears about files it does not have.
        Option("melonds_dsi_nand_path", "DSi NAND", listOf("dsi_nand.bin"), "dsi_nand.bin", DSI_GROUP, restart = true),
        Option("melonds_firmware_dsi_path", "DSi firmware", listOf("dsi_firmware.bin"), "dsi_firmware.bin", DSI_GROUP, restart = true),
        Option("melonds_firmware_nds_path", "DS firmware", listOf("firmware.bin"), "firmware.bin", DSI_GROUP, restart = true),
        Option("melonds_dsi_sdcard", "DSi virtual SD card", listOf("disabled", "enabled"), "disabled", DSI_GROUP, restart = true),
    )

    fun forPlatform(p: Platform): List<Option> = when (p) {
        Platform.GBA -> GBA; Platform.GBC -> GBC; Platform.NDS -> NDS
    }

    /** System files a console can take, by file name, with what they enable. */
    fun systemFiles(p: Platform): List<Pair<String, String>> = when (p) {
        Platform.GBA -> listOf("gba_bios.bin" to "Official GBA BIOS (16 KB). Enables the real boot and fixes a few games.")
        Platform.GBC -> emptyList()
        Platform.NDS -> listOf(
            "bios7.bin" to "DS ARM7 BIOS (16 KB)",
            "bios9.bin" to "DS ARM9 BIOS (4 KB)",
            "firmware.bin" to "DS firmware (256 KB): native boot, your DS profile",
            "dsi_bios7.bin" to "DSi ARM7 BIOS (64 KB)",
            "dsi_bios9.bin" to "DSi ARM9 BIOS (64 KB)",
            "dsi_firmware.bin" to "DSi firmware (128 KB)",
            "dsi_nand.bin" to "DSi NAND (240 MB): DSi mode",
        )
    }
}

/** Changed values per console, on disk. */
class CoreOptionStore(private val dir: File) {
    init { dir.mkdirs() }

    private fun file(p: Platform) = File(dir, p.name.lowercase() + ".properties")

    fun load(p: Platform): Map<String, String> {
        val props = runCatching { Properties().apply { file(p).inputStream().use { load(it) } } }.getOrNull() ?: return emptyMap()
        val known = CoreOptions.forPlatform(p).associateBy { it.key }
        return props.entries.mapNotNull { (k, v) ->
            val o = known[k.toString()] ?: return@mapNotNull null
            val s = v.toString(); if (s in o.values && s != o.default) k.toString() to s else null
        }.toMap()
    }

    fun set(p: Platform, key: String, value: String) {
        val current = load(p).toMutableMap()
        val o = CoreOptions.forPlatform(p).firstOrNull { it.key == key } ?: return
        if (value == o.default) current.remove(key) else current[key] = value
        save(p, current)
    }

    fun reset(p: Platform) { file(p).delete() }

    private fun save(p: Platform, values: Map<String, String>) {
        if (values.isEmpty()) { file(p).delete(); return }
        val props = Properties(); values.forEach { (k, v) -> props.setProperty(k, v) }
        runCatching {
            val f = file(p); val tmp = File(dir, f.name + ".tmp")
            tmp.outputStream().use { props.store(it, null) }
            if (!tmp.renameTo(f)) { f.delete(); tmp.renameTo(f) }
        }
    }

    /** Every option with its effective value: stored, else the default. */
    fun effective(p: Platform): Map<String, String> {
        val stored = load(p)
        // A stored value the option no longer lists is stale (a renamed value in a
        // newer build); the core would reject it and fall back to its own default.
        return CoreOptions.forPlatform(p).associate { o -> o.key to (stored[o.key]?.takeIf { it in o.values } ?: o.default) }
    }
}
