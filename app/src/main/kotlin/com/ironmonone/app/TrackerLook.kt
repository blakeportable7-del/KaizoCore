package com.ironmonone.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign

/**
 * KaizoCore's own tracker look (Blake, 2026-10-02: "modernize the look of the tracker. KaizoCore should have its own
 * distinct tracker look. Still keep the color scheme themes", and "Lots of wasted space in our current tracker").
 *
 * The panel used to be a scaled copy of the PC tracker: square boxes ruled in the border colour at full strength,
 * pixel-art type badges and flat bands with blank space round them. It keeps its proportions (PcCanvas still lays it
 * out) and every colour still comes from the theme (Pc), but it is drawn as KaizoCore's: rounded cards whose outline
 * and inner rules are the border colour softened, type pills, an HP bar, names a weight heavier, and pill buttons.
 * Everything here is read from Pc at draw time, so a theme preset, a custom theme or Auto Pokemon Themes repaints it.
 */
object TrackerLook {
    /** Card corners, in reference pixels. */
    const val RADIUS = 5

    /** A card's outline: the theme's border, softened. */
    val outline: Color get() = Pc.Border.copy(alpha = 0.55f)

    /** The rules inside a card: quieter still. */
    val divider: Color get() = Pc.Border.copy(alpha = 0.28f)

    /** A raised surface inside a card (a button, the moves header): the theme's text colour, faintly. */
    val inset: Color get() = Pc.Text.copy(alpha = 0.10f)

    /** The HP colour the reference uses for the number, as a fill: low red, half gold, otherwise the positive colour. */
    fun hpColor(cur: Int, max: Int): Color = when {
        max <= 0 -> Pc.Dim
        cur * 5 <= max -> Pc.Negative
        cur * 2 <= max -> Pc.Gold
        else -> Pc.Positive
    }

    /** Black or white, whichever reads on [fill]. */
    fun onFill(fill: Color): Color = if (fill.luminance() > 0.42f) Color(0xFF15161A) else Color.White

    /**
     * A move's name: in its type's colour, on every theme (Blake, 2026-10-02: "I like how the moves texts have
     * colors based on the move's typing"). A theme whose lower box would swallow a type's colour, yellow on a light
     * box, gets that colour moved toward the box's own text colour until it reads.
     */
    fun moveName(type: Color): Color = readable(type, Pc.LowerGroundX ?: Pc.Ground, Pc.LowerText)

    /** [color], or the first step from it toward [text] that reads on [ground] at [min] to 1; [text] at worst. */
    internal fun readable(color: Color, ground: Color, text: Color, min: Float = 3f): Color {
        if (contrast(color, ground) >= min) return color
        for (step in 1..5) {
            val c = lerp(color, text, step / 5f)
            if (contrast(c, ground) >= min) return c
        }
        return text
    }

    /** The WCAG contrast ratio of two colours, 1 to 21. */
    internal fun contrast(a: Color, b: Color): Float {
        val la = a.luminance()
        val lb = b.luminance()
        return (maxOf(la, lb) + 0.05f) / (minOf(la, lb) + 0.05f)
    }
}

/**
 * A type as a pill in its own colour, in the 30 by 12 the badge row is laid out for. Long names step the size down
 * so all eighteen fit: "FIGHTING", "ELECTRIC" and "PSYCHIC" whole.
 */
@Composable
internal fun TrackerTypePill(label: String, color: Color) {
    val text = label.uppercase()
    val size = when {
        text.length <= 5 -> PcRef.FONT - 2
        text.length <= 7 -> PcRef.FONT - 3
        else -> PcRef.FONT - 4
    }
    Box(
        Modifier.width(30.rp).height(11.rp).clip(RoundedCornerShape(6.rp)).background(color),
        contentAlignment = Alignment.Center,
    ) { PixText(text, size, TrackerLook.onFill(color), align = TextAlign.Center, weight = FontWeight.Medium) }
}

/**
 * A status condition as a pill, where the PC tracker drew its 16 by 8 pixel image over the icon's top right. Each
 * has the colour the games give it, as a type pill has its type's, and a screen reader hears the word.
 */
internal object TrackerStatus {
    fun color(code: String): Color = when (code.uppercase()) {
        "BRN" -> Color(0xFFE2683C)
        "FRZ" -> Color(0xFF6CC6E8)
        "PAR" -> Color(0xFFE8C53A)
        "PSN", "TOX" -> Color(0xFFA35BC9)
        "FNT" -> Color(0xFFC23B3B)
        else -> Color(0xFF9C9C9C)   // SLP, and anything unforeseen
    }

    fun spoken(code: String): String = when (code.uppercase()) {
        "BRN" -> "Burned"
        "FRZ" -> "Frozen"
        "PAR" -> "Paralyzed"
        "PSN" -> "Poisoned"
        "TOX" -> "Badly poisoned"
        "SLP" -> "Asleep"
        "FNT" -> "Fainted"
        else -> code
    }
}

@Composable
internal fun TrackerStatusPill(code: String, modifier: Modifier = Modifier) {
    val fill = TrackerStatus.color(code)
    Box(
        modifier.width(18.rp).height(9.rp).clip(RoundedCornerShape(5.rp)).background(fill)
            .semantics { contentDescription = TrackerStatus.spoken(code) },
        contentAlignment = Alignment.Center,
    ) { PixText(code.uppercase(), PcRef.FONT - 3, TrackerLook.onFill(fill), align = TextAlign.Center, weight = FontWeight.Medium) }
}

/**
 * The battle's weather as a pill under the battle banner's label (Blake, 2026-10-02, showing the Gen 3 tracker's
 * "Weather: Sunlight": "that's helpful to the player to know if there's any current weather effects in play").
 * TrackerState.weather's four values, named as the PC tracker's Battle Details names them; null for any other.
 */
internal object TrackerWeather {
    fun name(code: String): String? = when (code.uppercase()) {
        "SUN" -> "Sunlight"
        "RAIN" -> "Rain"
        "SANDSTORM" -> "Sandstorm"
        "HAIL" -> "Hail"
        else -> null
    }

    fun color(code: String): Color = when (code.uppercase()) {
        "SUN" -> Color(0xFFF0A030)
        "RAIN" -> Color(0xFF4C8DE8)
        "SANDSTORM" -> Color(0xFFC9A45C)
        else -> Color(0xFF9ED8EA)   // HAIL
    }
}

@Composable
internal fun TrackerWeatherPill(code: String) {
    val name = TrackerWeather.name(code) ?: return
    val fill = TrackerWeather.color(code)
    Box(
        Modifier.height(9.rp).clip(RoundedCornerShape(5.rp)).background(fill)
            .semantics { contentDescription = "Weather: $name" }
            .padding(horizontal = 3.rp),
        contentAlignment = Alignment.Center,
    ) { PixText(name.uppercase(), PcRef.FONT - 3, TrackerLook.onFill(fill), weight = FontWeight.Medium) }
}

/** HP as a bar under the number, rounded, in [TrackerLook.hpColor] on a faint track. */
@Composable
internal fun TrackerHpBar(cur: Int, max: Int, widthRp: Int = 56) {
    val fraction = if (max > 0) (cur.toFloat() / max).coerceIn(0f, 1f) else 0f
    Box(Modifier.width(widthRp.rp).height(3.rp).clip(RoundedCornerShape(2.rp)).background(TrackerLook.inset)) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(fraction).clip(RoundedCornerShape(2.rp)).background(TrackerLook.hpColor(cur, max)))
    }
}
