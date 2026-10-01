package com.ironmonone.tracker.nds

/**
 * The DS tracker's Auto Pokemon Themes (PokemonThemeManager.lua): one theme string
 * per species and per alternate form, PokemonData.POKEMON[i].theme, as
 * nds/autothemes.tsv holds it (gen, species, form, theme;
 * tools/trainer-data/convert_autothemes.py ran the reference's own PokemonData and
 * GameConfigurator to make it). Program.checkForAlternateForm's resolution
 * (Program.lua:456-489) is done ahead in the table: a form has a row only when it
 * shows a theme of its own (Wormadam's cloaks, Rotom's appliances, Shaymin Sky,
 * Castform, Darmanitan Zen, Meloetta Pirouette), and every other form, the cosmetic
 * ones included, shows its species' theme. That covers Unfezant, Frillish and
 * Jellicent too: the reference turns their female bit into form 1, and that form is
 * cosmetic.
 */
object NdsAutoThemes {
    private val table: Map<String, String> by lazy {
        val out = HashMap<String, String>()
        NdsAutoThemes::class.java.getResourceAsStream("/nds/autothemes.tsv")?.bufferedReader(Charsets.UTF_8)?.useLines { lines ->
            lines.filter { it.isNotBlank() && !it.startsWith("#") }.forEach { l ->
                val r = l.split('\t')
                if (r.size >= 4) out["${r[0]}/${r[1]}/${r[2]}"] = r[3].trim()
            }
        }
        out
    }

    /** The theme string of [species] in [form] in a Gen [gen] game, or null when the reference has none. */
    fun code(gen: Int, species: Int, form: Int = 0): String? = table["$gen/$species/$form"] ?: table["$gen/$species/0"]

    /** A theme as the reference shows it, each colour 0xRRGGBB. */
    data class Theme(
        val topText: Long,
        val bottomText: Long,
        val positive: Long,
        val negative: Long,
        val intermediate: Long,
        val moveHeader: Long,
        val topBorder: Long,
        val topBackground: Long,
        val bottomBorder: Long,
        val bottomBackground: Long,
        val mainBackground: Long,
        /**
         * "Alternate positive text color" and "Alternate negative text color": what the
         * bottom box draws STAB power and effectiveness marks in (MainScreen.lua:505,
         * DrawingUtils.lua:434-460). Null when the theme has none, and then those use
         * [positive] and [negative] (DrawingUtils.lua:336-341).
         */
        val altPositive: Long?,
        val altNegative: Long?,
    )

    // Graphics.THEME_COLORS (Graphics.lua:168-175).
    private const val WHITE = 0xFFFFFFFFL
    private const val BLACK = 0xFF000000L
    private const val DARK_POSITIVE = 0x0343B0L
    private const val DARK_NEGATIVE = 0xB40002L
    private const val LIGHT_POSITIVE = 0xC8DDFFL
    private const val LIGHT_NEGATIVE = 0xFDCDCDL

    /**
     * ThemeFactory.readThemeString (ThemeFactory.lua:212-257) and then
     * formatPokemonTheme (PokemonThemeManager.lua:10-42) over a theme string: its
     * fourteen colours in THEME_COLOR_KEYS_ORDERED (the flags after them are set by
     * formatPokemonTheme, not read). Null unless there are exactly fourteen.
     *
     * - Positive and negative become the light pair under top-box text that is
     *   white, the dark pair under black; any other top text keeps the theme's own.
     * - The alternate pair exists when the two text colours are white and black,
     *   either way round: the dark pair under white top text, the light pair under
     *   black (checkForVaryingTextColor, addAlternatePositiveNegative).
     * - The physical, special and gear icon colours (the last three) are replaced
     *   by the bottom, bottom and top text colours, so they are not kept here.
     *
     * The comparisons use each colour as the reference parses it, "0xFF" and the
     * digits. Two strings in PokemonData carry a seventh digit (Scrafty's and
     * Cubchoo's top text, "FFFFFFF" and "0000000"), so neither compares as white or
     * black there; the colour shown is the last six digits.
     */
    fun read(code: String): Theme? {
        val tokens = Regex("[0-9a-fA-F]+").findAll(code).map { it.value }.filter { it.length > 1 }.toList()
        if (tokens.size != 14 || tokens.any { it.length > 8 }) return null
        val raw = tokens.map { ("FF$it").toLong(16) }
        val rgb = raw.map { it and 0xFFFFFFL }
        val top = raw[0]
        val bottom = raw[1]
        val (positive, negative) = when (top) {
            WHITE -> LIGHT_POSITIVE to LIGHT_NEGATIVE
            BLACK -> DARK_POSITIVE to DARK_NEGATIVE
            else -> rgb[2] to rgb[3]
        }
        val alternate = if (Math.abs(top - bottom) != 0xFFFFFFL) null
            else if (Math.abs(WHITE - top) < 0xFFL) DARK_POSITIVE to DARK_NEGATIVE
            else LIGHT_POSITIVE to LIGHT_NEGATIVE
        return Theme(
            topText = rgb[0], bottomText = rgb[1], positive = positive, negative = negative,
            intermediate = rgb[4], moveHeader = rgb[5], topBorder = rgb[6], topBackground = rgb[7],
            bottomBorder = rgb[8], bottomBackground = rgb[9], mainBackground = rgb[10],
            altPositive = alternate?.first, altNegative = alternate?.second,
        )
    }
}
