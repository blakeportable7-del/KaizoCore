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

        /**
         * The number scale, when this option is one: five or more whole numbers,
         * evenly spaced, ascending. The page draws these as a stepper instead of a
         * forward-only cycle (2026-09-27, audit: the low-pass range was 19 taps
         * round). Values that are not numbers ride along as [extras].
         */
        val numbers: List<Int>? = values.mapNotNull { it.toIntOrNull() }.takeIf { ns ->
            ns.size >= 5 && ns[1] > ns[0] && ns.zipWithNext { a, b -> b - a }.distinct().size == 1
        }

        /** Non-number values on a number option (Boktai's "sensor"). */
        val extras: List<String> get() = if (numbers == null) emptyList() else values.filter { it.toIntOrNull() == null }

        /**
         * The allowed value for a stepper result [n]. A one-step move from [from]
         * goes to the next allowed value in that direction, so + on a scale in
         * fives moves 60 to 65 rather than snapping 61 back to 60; anything else
         * (a typed number) goes to the nearest allowed value.
         */
        fun snap(n: Int, from: Int?): String {
            val ns = numbers ?: return default
            val pick = when {
                from != null && n == from + 1 -> ns.firstOrNull { it > from } ?: ns.last()
                from != null && n == from - 1 -> ns.lastOrNull { it < from } ?: ns.first()
                else -> ns.minBy { kotlin.math.abs(it - n) }
            }
            return pick.toString()
        }
    }

    /**
     * How a stored value reads on the page. The stored value is the core's and
     * never changes; this is only its label (2026-09-27, audit: rows showed
     * "mix_smart" and "lcd_ghosting_fast" raw, in monospace).
     */
    fun display(value: String): String {
        LABELS[value]?.let { return it }
        when (value.lowercase()) {
            "on", "yes", "enabled" -> return "On"
            "off", "no", "disabled" -> return "Off"
        }
        // "GB - DMG", "SGB - 1A": the palette names, without the separator.
        val words = value.replace(" - ", " ").replace('_', ' ').split(' ').filter { it.isNotEmpty() }
        return words.mapIndexed { i, w ->
            val titleCase = w.length > 1 && w[0].isUpperCase() && w.drop(1).all { !it.isLetter() || it.isLowerCase() }
            when {
                i == 0 -> w.replaceFirstChar { it.uppercase() }
                titleCase && w !in KEEP_CASE -> w.lowercase()
                else -> w
            }
        }.joinToString(" ")
    }

    private val KEEP_CASE = setOf("Game", "Boy", "Pocket", "Light")
    private val LABELS = mapOf(
        "mix" to "Mix",
        "mix_smart" to "Mix (smart)",
        "lcd_ghosting" to "LCD ghosting",
        "lcd_ghosting_fast" to "LCD ghosting (fast)",
        "auto_threshold" to "Auto (threshold)",
        "fixed_interval" to "Fixed interval",
        "sinc" to "Sinc (best)",
        "cc" to "Cosine (lighter)",
        "sensor" to "Phone light sensor",
        "internal" to "Internal palette",
    )

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
        Option("mgba_color_correction", "Color correction", listOf("OFF", "GBA", "GBC", "Auto"), "OFF", "Video",
            hint = "GBA reproduces the original screen's washed colors."),
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
            hint = "For Boktai. Phone light sensor reads the real light around you."),
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
        Option("gambatte_gb_colorization", "Game Boy colorization", listOf("disabled", "auto", "GBC", "SGB", "internal"), "disabled", "Video",
            hint = "For original Game Boy games: Auto uses the GBC/SGB palette the game shipped with; Internal palette uses the one below."),
        // "custom" is left out (2026-09-27, audit): it reads a palette file the app has no way to import.
        Option("gambatte_gb_internal_palette", "Internal palette", GB_PALETTES, "GB - DMG", "Video"),
        Option("gambatte_gbc_color_correction", "GBC color correction", listOf("GBC only", "always", "disabled"), "GBC only", "Video"),
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
            hint = "Leave it on; the core reads the link rows only while this is on."),
        Option("gambatte_gb_link_mode", "Link mode", listOf("Not Connected", "Network Server", "Network Client"), "Not Connected", LINK_GROUP,
            hint = "Network server: this phone hosts. Network client: this phone joins the server address below."),
        Option("gambatte_gb_link_network_port", "Port", range(56400, 56420), "56400", LINK_GROUP),
    ) + (1..12).map { Option(LinkIp.key(it), "Server address digit $it", range(0, 9), "0", LINK_IP_GROUP) }

    /**
     * The classic libretro melonDS core (libretro/melonDS), since 2026-09-08.
     * The app shipped melonDS DS (JesseTG) until then; its system RAM did not
     * carry the running game (a 4 MB dump held the cartridge header and the
     * ROM's static data but no trainer name and no Pokemon, in a battle),
     * so no DS tracker read ever worked. The classic core's RAM has the game
     * where the PC tracker's addresses say. Its option names and values are
     * its own (src/libretro/libretro_core_options.h); they are not the melonDS
     * DS names, and a stale value from the old core is simply unknown to it.
     */
    val NDS: List<Option> = listOf(
        Option(FILTER_KEY, "Video filter", FILTERS, "Default", "Video"),
        Option("melonds_threaded_renderer", "Threaded software renderer", listOf("enabled", "disabled"), "enabled", "Performance", restart = true),
        Option("melonds_jit_enable", "JIT recompiler", listOf("enabled", "disabled"), "enabled", "Performance", restart = true,
            hint = "Off is slower but is the fallback if a game misbehaves."),
        Option("melonds_hybrid_ratio", "Hybrid big screen ratio", listOf("2", "3"), "2", "Video"),
        Option("melonds_hybrid_small_screen", "Hybrid small screen", listOf("Bottom", "Top", "Duplicate"), "Bottom", "Video"),
        Option("melonds_touch_mode", "Touch", listOf("Touch", "Mouse", "Joystick", "disabled"), "Touch", "Hardware",
            hint = "Touch: the stylus is your finger on the bottom screen. Leave it."),
        Option("melonds_mic_input", "Microphone input", listOf("Blow Noise", "White Noise", "Microphone Input", "None"), "Blow Noise", "Hardware",
            hint = "Blow Noise answers any mic prompt with a breath."),
        // The DS's own sound is 10-bit with no interpolation, and that is what melonDS sends unless told otherwise
        // ("Automatic" is 10-bit on a DS): every sample a multiple of 32, a grain on every fade and note tail, and the
        // instruments' steps as hash at 12 to 16 kHz, 28 dB under the music (Black 2's title, read off the stream tap,
        // 2026-10-02). Blake heard it as crackling, as he had the GBA's static. 16-bit and cubic are the core's own
        // clean settings; the DS's own sound is a tap away. The core reads the bit depth only as the game boots
        // (switched mid-game the samples stayed 16-bit; after a restart they were 10-bit), the interpolation at once.
        Option("melonds_audio_bitrate", "Audio bit depth", listOf("16-bit", "10-bit", "Automatic"), "16-bit", "Audio", restart = true,
            hint = "16-bit is clean. 10-bit is the DS's own sound, grain and all."),
        Option("melonds_audio_interpolation", "Audio interpolation", listOf("Cubic", "Cosine", "Linear", "None"), "Cubic", "Audio",
            hint = "Cubic smooths the instruments. None is how the DS played them."),
        RUMBLE_ROW, SENSORS_ROW,
        Option("melonds_boot_directly", "Boot straight into the game", listOf("enabled", "disabled"), "enabled", "System", restart = true,
            hint = "Off shows the DS menu first; needs real firmware."),
        Option("melonds_use_fw_settings", "Use firmware settings", listOf("disabled", "enabled"), "disabled", "System", restart = true,
            hint = "On reads your name, color and language from an imported firmware.bin."),
        Option("melonds_language", "Language", listOf("English", "Japanese", "French", "German", "Italian", "Spanish"), "English", "System", restart = true),
        Option("melonds_randomize_mac_address", "Random MAC address", listOf("disabled", "enabled"), "disabled", "System", restart = true),
        // In the DSi group, so it is not drawn as a row: the DSi section switches it,
        // and only once all seven files are present. As a plain row it skipped that
        // check and a game could fail to boot (2026-09-27, audit).
        Option("melonds_console_mode", "Console", listOf("DS", "DSi"), "DS", DSI_GROUP, restart = true),
        Option("melonds_dsi_sdcard", "DSi virtual SD card", listOf("disabled", "enabled"), "disabled", DSI_GROUP, restart = true),
    )

    fun forPlatform(p: Platform): List<Option> = when (p) {
        Platform.GBA -> GBA; Platform.GBC -> GBC; Platform.NDS -> NDS
    }

    /**
     * The options a boot of [platform] is given, from [values]: every core option, none of the app's own keys. With DSi
     * mode off the DSi pair goes with its plain-DS values instead of being left out: the core kept a key left out at its
     * last value, so after Reset all a game that had been a DSi kept booting as one (rc32 audit P2 #121).
     */
    fun coreVariables(platform: Platform, values: Map<String, String>): List<Pair<String, String>> {
        val out = LinkedHashMap(values.filterKeys { it !in APP_KEYS })
        if (!DsiMode.isOn(values)) {
            for (o in forPlatform(platform)) if (o.group == DSI_GROUP) out[o.key] = DsiMode.OFF[o.key] ?: o.default
        }
        return out.map { (k, v) -> k to v }
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
