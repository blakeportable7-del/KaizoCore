package com.ironmonone.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import java.io.File

/**
 * How a theme code from a reference tracker becomes a [WholeTheme] (2026-09-29).
 *
 * [gen3] is the one reading of a Gen 3 theme code in the app: [AutoTheme.apply] shows an auto theme
 * through it and the presets below are shown through it, so the same code gives the same theme
 * either way. It puts the 11 colours where Theme.lua's order says (Default text, Lower box text,
 * Positive, Negative, Intermediate, Header, Upper border, Upper background, Lower border, Lower
 * background, Main background) and reads flag 1 as MOVE_TYPES_ENABLED, "0" meaning move names in the
 * lower text with a bar in the move's type colour. Flag 2, text shadows, is not drawn here.
 */
object ThemeCodes {
    private fun isHex6(t: String) = t.length == 6 && t.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }

    /** The theme for the eleven colours [c] in Theme.lua's order. */
    private fun ofEleven(c: List<Color>, moveTypeBar: Boolean) = WholeTheme(
        page = c[10], ground = c[7], border = c[6], text = c[0],
        positive = c[2], negative = c[3], gold = c[4], dim = c[1],
        headerX = c[5], headerGroundX = c[10], lowerTextX = c[1],
        lowerBorderX = c[8], lowerGroundX = c[9], moveTypeBar = moveTypeBar,
    )

    /** A Gen 3 theme code, or null when it has fewer than eleven colours or one that is not six hex digits. */
    fun gen3(code: String): WholeTheme? {
        val parts = code.trim().split(Regex("\\s+"))
        if (parts.size < 11) return null
        val colours = parts.take(11).map { h ->
            h.takeIf { isHex6(it) }?.toLong(16)?.let { Color(0xFF000000L or it) } ?: return null
        }
        return ofEleven(colours, moveTypeBar = parts.getOrNull(11) == "0")
    }

    /**
     * A DS tracker .colortheme file as ThemeFactory.readThemeString (ThemeFactory.lua:212-257) reads it
     * into the settings, which is what its Load Theme button does. Tokens of one digit are flags in
     * COLOR_SETTINGS_KEYS_ORDERED order, the others are colours. All fifteen shipped files hold thirteen
     * colours, the legacy shape (isLegacyNDSString): the first is used for both the top and the bottom box
     * text, and the ninth to the eleventh keys follow. The last three are the physical, special and gear icon
     * colours, which this app draws in the box text colours (AutoTheme.applyDs), so they are not kept. Flag 1
     * is "Color move names by type", the same switch as the Gen 3 flag 1. The file's other flags are not drawn
     * here. Null for anything but thirteen six-digit colours: no shipped file is another shape, and reading
     * a fourteen colour string would also need the alternate positive and negative rule.
     */
    fun ds(text: String): WholeTheme? {
        val tokens = Regex("[0-9a-fA-F]+").findAll(text).map { it.value }.toList()
        val colours = tokens.filter { it.length > 1 }
        if (colours.size != 13 || !colours.all { isHex6(it) }) return null
        val rgb = colours.map { Color(0xFF000000L or it.toLong(16)) }
        val flags = tokens.filter { it.length == 1 }.map { it != "0" }
        // Thirteen colours: the first one twice, then the second to the tenth.
        val eleven = listOf(rgb[0], rgb[0]) + rgb.subList(1, 10)
        return ofEleven(eleven, moveTypeBar = flags.getOrNull(0) == false)
    }
}

/**
 * Preset tracker themes, and the ones the player saves (2026-09-29).
 *
 * Built in, for Gen 3 and Game Boy games: "Default" and the PC tracker's fourteen, by its own names
 * and codes and in its own order. Constants.PreloadedThemes and Constants.OrderedLists.PRELOADED_THEMES in
 * ironmon_tracker/Constants.lua, and the Default Theme of Theme.resetPresets in Theme.lua (besteon/Ironmon-Tracker,
 * MIT, reference commit c450ecae). Theme.populateThemePresets is what puts them in the PC tracker's list.
 *
 * Built in, for DS games: "Default" and the fifteen .colortheme files the DS tracker ships in
 * ironmon_tracker/themes (Brian0255/NDS-Ironmon-Tracker, GPL-3.0, commit 910c255), which its Load Theme
 * button opens (ThemeFactory.createLoadThemeForm). A file's name is its preset's name, kept as it is
 * (beach, STONKS), and they are in the order its file dialog lists them, by name without regard to case.
 * The DS tracker has no list of preset names of its own, so that folder is the reference.
 *
 * "Default" is the PC tracker's Default Theme, which is not quite the palette a fresh install shows: its
 * bottom box text is white where [ThemeStore.FACTORY] has the quiet grey, and its header sits on the main
 * background. Reset colours is still the palette from before presets.
 *
 * Saved by the player: prep/theme-presets.txt, one name=code per line as the PC tracker's Save as new does it,
 * the code being the whole theme ([ThemeStore.encode]). A name may hold spaces or an equals sign, because a
 * line splits at its last equals sign and a code never has one. The built-ins cannot be replaced or removed.
 */
object ThemePresets {
    const val FILE = "prep/theme-presets.txt"
    const val MAX_NAME = 40

    class Preset(val name: String, val theme: WholeTheme, val builtIn: Boolean)

    /** Theme.resetPresets, "Default Theme". */
    const val DEFAULT_CODE = "FFFFFF FFFFFF 00FF00 FF0000 FFFF00 FFFFFF AAAAAA 222222 AAAAAA 222222 000000 1 1"

    /** Constants.PreloadedThemes, in Constants.OrderedLists.PRELOADED_THEMES order. */
    val PC_CODES: List<Pair<String, String>> = listOf(
        "Fire Red" to "FFFFFF FFFFFF 55CB6B 62C7FE FEFA69 FEFA69 FF1920 81000E FF1920 81000E 58050D 0 1",
        "Leaf Green" to "FFFFFF FFFFFF 62C7FE FE7573 FEFA69 FEFA69 55CB6B 006200 55CB6B 006200 053A04 0 1",
        "Beach Getaway" to "222222 222222 5463FF E78EA9 A581E6 444444 E78EA9 B9F8D3 E78EA9 FFFBE7 40DFEF 0 0",
        "Blue Da Ba Dee" to "FFFFFF FFFFFF 2EB5FF E04DBA FEFA69 55CB6B 198BFF 004881 198BFF 004881 072557 1 1",
        "Calico Cat" to "4A3432 4A3432 E07E3D 8A9298 E07E3D FCFCF0 8A9298 FCFCF0 E07E3D FBCA8C 0F0601 0 0",
        "Calico Cat v2" to "4A3432 4A3432 E07E3D 8A9298 E07E3D FCFCF0 FCFCF0 FCFCF0 FBCA8C FBCA8C E07E3D 0 0",
        "Cotton Candy" to "000000 000000 1A85FF D41159 9155D9 EEEEEE D35FB7 FFCBF3 1A85FF A0D3FF 5D3A9B 0 0",
        "GameCube" to "C8C8C8 C8C8C8 2ACA38 FE4A4A EBE31A CBCCC4 000000 342A54 000000 342A54 000000 1 1",
        "Item Bag" to "636363 636363 017BC4 DF2800 DE8C4A 636363 D7B452 FEFFCF D7B452 FEFFCF F6CF73 0 0",
        "Neon Lights" to "FFFFFF FFFFFF 38FF12 FF00E3 FFF100 FFFFFF 00F5FB 000000 001EFF 000000 000000 1 1",
        "Simple Monotone" to "222222 222222 01B910 FE5958 555555 FFFFFF 000000 FFFFFF 000000 FFFFFF 555555 0 0",
        "Team Rocket" to "EEF5FE EEF5FE 8F7DEB D6335E F4E7BA F4E7BA 8F7DEB 333333 D6335E 333333 333333 1 1",
        "USS Galactic" to "EEEEEE EEEEEE 00ADB5 DFBB9D B6C8EF 00ADB5 222831 393E46 222831 393E46 000000 1 1",
        "Cozy Fall Leaves" to "2C432C 2C432C FA8223 9C7456 307940 307940 7D5D1E 9ED4B0 7D5D1E 9ED4B0 9ED4B0 0 0",
    )

    /** The DS tracker's themes folder, the text of each .colortheme file as it is. */
    val DS_CODES: List<Pair<String, String>> = listOf(
        "AlolanExeggcutor" to "222222 1288FD E78EA9 887F61 F5FFF1 6AB56C AAEA89 887D5E FCFBAE C5B79E 000000 5496FE DBDBDB 0 1 1 0",
        "beach" to "222222 5463FF E78EA9 EF8D32 444444 E78EA9 B9F8D3 E78EA9 FFFBE7 40DFEF FE650C 4458E0 C09397 0 1 0 0",
        "Bulbasaur" to "EEEEEE 5FD969 E42A21 8ADD56 27938E 5ADBBD 27938E 5ADBBD 27938E 5ADBBD FFC631 7DB6FF DBDBDB 0 1 0 0",
        "Chalkboard" to "A6AEC5 2D9FFE D82F27 D2E070 FFFFFF AAAAAA 47424C AAAAAA 47424C 37445B F37D7E 5496FE DBDBDB 0 0 1 0",
        "ChillBlue" to "FFFFFF 62FE69 E3627D FFFF00 FFFFFF 294DA5 000000 294DA5 000000 191F2D FFC631 7DB6FF DBDBDB 1 1 0 0",
        "CottonCandy" to "000000 1A85FF D41159 D06CE3 C0C0C0 D35FB7 FFCBF3 1A85FF A0D3FF 5D3A9B FF0D3E 5E45FD 985081 0",
        "FireRed" to "FFFFFF 55CB6B 62C7FE FEFA69 FEFA69 FF1920 81000E FF1920 81000E 58050D FFCA00 76C9FF 985081 0 1 0 0",
        "LeafGreen" to "FFFFFF 62C7FE FE7573 FEFA69 FEFA69 55CB6B 006200 55CB6B 006200 053A04 FFCA00 76C9FF 985081 0 1 0 0",
        "LightTheme" to "000000 1191FE FF517D 000000 FFFFFF 000000 FFFFFF 000000 FFFFFF 000000 000000 000000 939393 0 1 0 0",
        "Neon" to "FFFFFF 38FF12 FF00E3 FFF100 FFFFFF 00F5FB 000000 001EFF 000000 000000 FEA510 7DB6FF DBDBDB 1 1 0 0",
        "Pinky" to "000000 4870F3 CC3600 FFFEFF FFFFFF CE1B88 E886AD CE1B88 E886AD 000000 FFC631 7DB6FF DBDBDB 0 0",
        "RedBlueAndGreen" to "FFFFFF 57FF00 FFBE40 FFF999 FFFFFF EDB6C1 A36C90 A2ECFF 00ABE7 008470 FEBF06 014EB2 FFFFFF 0 1 0 0",
        "Spaceship" to "EEEEEE 00ADB5 DFBB9D 7F8487 00ADB5 222831 393E46 222831 393E46 000000 4CC4E2 9895FF DBDBDB 1 1 0 0",
        "STONKS" to "FFFFFF 66FE79 FF7676 FFFB1E F5FFF1 EFE3FB 1491FF D5D1DB A798A7 000000 FFC100 52A3FF DBDBDB 0 1 1 0",
        "VeryBlue" to "FFFFFF 2EB5FF E04DBA FEFA69 55CB6B 198BFF 004881 198BFF 004881 1E345B FEBB0D 7AA1F1 B4DAFA 1",
    )

    private fun built(name: String, theme: WholeTheme?) = Preset(name, checkNotNull(theme) { "built-in theme $name does not parse" }, true)

    private val pc: List<Preset> by lazy {
        listOf(built("Default", ThemeCodes.gen3(DEFAULT_CODE))) + PC_CODES.map { (n, c) -> built(n, ThemeCodes.gen3(c)) }
    }
    private val ds: List<Preset> by lazy {
        listOf(built("Default", ThemeCodes.gen3(DEFAULT_CODE))) + DS_CODES.map { (n, c) -> built(n, ThemeCodes.ds(c)) }
    }

    /** The built-in presets of a Gen 3 or Game Boy game, or of a DS game when [ds]. */
    fun builtIns(ds: Boolean): List<Preset> = if (ds) this.ds else pc

    /** What the player saved, in the order it was saved. Read it in a composable and the list follows a save. */
    var yours by mutableStateOf<List<Preset>>(emptyList())
        private set

    private var file: File? = null

    /** A name of a built-in on either console, so a saved name never hides one of them. */
    fun isBuiltInName(name: String): Boolean =
        (pc + ds).any { it.name.equals(name, ignoreCase = true) }

    /** A name as it is kept: control characters as spaces, runs of space as one, trimmed, at most [MAX_NAME] long. */
    fun cleanName(raw: String): String =
        raw.map { if (it.isISOControl()) ' ' else it }.joinToString("")
            .trim().replace(Regex("\\s+"), " ").take(MAX_NAME).trim()

    fun apply(p: Preset) { ThemeStore.adopt(p.theme) }

    enum class SaveResult { SAVED, REPLACED, EMPTY, RESERVED, FAILED }

    /**
     * Keeps [theme] (the theme showing, by default) under [rawName]. A name already saved is replaced
     * where it stands, as the PC tracker does once it has been told yes. The built-ins' names are refused,
     * as its reserved names are, and a write that fails leaves the list as it was.
     */
    fun saveYours(rawName: String, theme: WholeTheme = ThemeStore.snapshot()): SaveResult {
        val name = cleanName(rawName)
        if (name.isEmpty()) return SaveResult.EMPTY
        if (isBuiltInName(name)) return SaveResult.RESERVED
        val at = yours.indexOfFirst { it.name.equals(name, ignoreCase = true) }
        val kept = Preset(name, theme, false)
        val next = if (at >= 0) yours.toMutableList().also { it[at] = kept } else yours + kept
        if (!write(next)) return SaveResult.FAILED
        yours = next
        return if (at >= 0) SaveResult.REPLACED else SaveResult.SAVED
    }

    /** Takes a saved preset out of the list. False for a built-in, for a name that is not there, or a failed write. */
    fun removeYours(name: String): Boolean {
        if (isBuiltInName(name)) return false
        val at = yours.indexOfFirst { it.name.equals(name, ignoreCase = true) }
        if (at < 0) return false
        val next = yours.toMutableList().also { it.removeAt(at) }
        if (!write(next)) return false
        yours = next
        return true
    }

    /** The file text for [list]. */
    fun format(list: List<Preset>): String =
        list.joinToString("") { it.name + "=" + ThemeStore.encode(it.theme) + "\n" }

    /**
     * The presets a file holds. A line splits at its last equals sign; a line with no name, no equals
     * sign or a code that is not a theme is skipped, and so is a name that would hide a built-in. A name
     * that turns up twice keeps its first place and its last code.
     */
    fun parse(text: String): List<Preset> {
        val out = LinkedHashMap<String, Preset>()
        for (line in text.lines()) {
            val eq = line.lastIndexOf('=')
            if (eq <= 0) continue
            val name = cleanName(line.substring(0, eq))
            val theme = ThemeStore.decode(line.substring(eq + 1)) ?: continue
            if (name.isEmpty() || isBuiltInName(name)) continue
            out[name.lowercase()] = Preset(name, theme, false)
        }
        return out.values.toList()
    }

    private fun write(list: List<Preset>): Boolean = file?.let { SafeWrite.text(it, format(list)) } ?: true

    fun load(f: File) {
        file = f
        yours = if (f.isFile) runCatching { parse(f.readText()) }.getOrDefault(emptyList()) else emptyList()
    }

    /** Tests only: forget the file and the list. */
    internal fun detach() { file = null; yours = emptyList() }
}
