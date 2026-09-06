package com.ironmonone.app.gen3

import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ironmonone.app.R

/**
 * Gen 3 skin (docs/DESIGN_GEN3_UI.md): usability first, then the costume.
 *
 * The Emerald text box is drawn procedurally - a dark frame, a light bevel, paper
 * fill - because that IS the real frame's geometry; no ripped assets needed. Pixel
 * font is Press Start 2P (OFL, license bundled in assets), used ONLY for headers and
 * labels at readable sizes; body text stays the system face, which is the design
 * doc's readability rule, not a compromise.
 */
object Gen3 {
    // Palette per the design doc, sampled from Emerald's UI family.
    val FrameDark = Color(0xFF303030)
    val FrameBevel = Color(0xFFA8A8A0)
    val Paper = Color(0xFFF8F8F8)
    val Ink = Color(0xFF404040)
    val InkShadow = Color(0xFFB8B8B0)
    val Emerald = Color(0xFF40A058)
    val EmeraldDeep = Color(0xFF2E7842)
    val HpGreen = Color(0xFF58D080)
    val HpAmber = Color(0xFFF8B050)
    val HpRed = Color(0xFFF05838)
    val ExpBlue = Color(0xFF40C8F8)
    val Sky = Color(0xFFA9D8F8)
    val MenuBlue = Color(0xFF3050A8)   // the save-menu blue family

    // The PC tracker's default theme, so the shell and the tracker agree.
    val PcPage = Color(0xFF000000)
    val PcGround = Color(0xFF222222)
    val PcBorder = Color(0xFFAAAAAA)
    val PcText = Color(0xFFFFFFFF)
    val PcGold = Color(0xFFFFFF00)

    val PixelFont = FontFamily(Font(R.font.press_start_2p))

    /**
     * The app shell runs the PC tracker's own palette, not the Emerald green it
     * was first skinned in: black page, grey hairlines, yellow accent. The
     * tracker panel is a faithful copy of the PC tool, and a green shell around
     * it read as two different programs.
     *
     * MenuBlue is deliberately not the secondary any more - it was tinting
     * status lines like "Loaded slot 1." blue, the one colour the PC tracker's
     * theme has no slot for.
     */
    val Scheme = lightColorScheme(
        primary = PcBorder,
        onPrimary = PcPage,
        secondary = PcGold,
        onSecondary = PcPage,
        background = PcPage,
        onBackground = PcText,
        surface = Paper,
        onSurface = Ink,
        surfaceVariant = Paper,
        onSurfaceVariant = Ink,
        error = HpRed,
        onError = Paper,
        outline = FrameDark,
    )
}

/**
 * The Emerald text box: 2dp dark outer frame, 2dp light bevel, paper inside.
 * Every card and dialog in the app is one of these.
 */
@Composable
fun Gen3Box(
    modifier: Modifier = Modifier,
    paper: Color = Gen3.Paper,
    content: @Composable () -> Unit,
) {
    Box(
        modifier
            .border(2.dp, Gen3.FrameDark)
            .padding(2.dp)
            .border(2.dp, Gen3.FrameBevel)
            .padding(2.dp)
            .background(paper)
            .padding(10.dp),
    ) { content() }
}

/**
 * A Gen 3 menu button: a small text box with the selector triangle. Pressed state
 * nudges nothing fancy - the triangle IS the affordance, like the game.
 */
@Composable
fun Gen3Button(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    accent: Boolean = false,
    onClick: () -> Unit,
) {
    // Press feel: a 1px inset shift and a darkened face, no Material ripple.
    // A ripple is the wrong idiom on a hard-edged plastic button, and the app
    // had no press feedback at all - taps looked ignored until the screen
    // changed.
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    val face = when {
        accent -> Gen3.PcGround
        pressed -> Gen3.InkShadow
        else -> Gen3.Paper
    }
    Box(
        modifier
            .offset(y = if (pressed) 1.dp else 0.dp)
            .border(2.dp, if (enabled) Gen3.FrameDark else Gen3.FrameBevel)
            .padding(2.dp)
            .border(2.dp, Gen3.FrameBevel)
            .padding(2.dp)
            .background(face)
            .clickable(
                enabled = enabled,
                interactionSource = interaction,
                // No ripple: this is a moulded button, not a Material surface.
                indication = null,
            ) {
                haptics.performHapticFeedback(
                    androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress,
                )
                onClick()
            }
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "▶ ",
                fontFamily = Gen3.PixelFont,
                fontSize = 9.sp,
                color = if (accent) Gen3.PcGold else Gen3.Ink,
            )
            Text(
                text,
                fontFamily = Gen3.PixelFont,
                fontSize = 10.sp,
                // A button label that wraps stops looking like a button:
                // "CANCEL" was breaking across two lines as "CANCE / L".
                maxLines = 1,
                softWrap = false,
                color = when {
                    !enabled -> Gen3.InkShadow
                    accent -> Gen3.PcGold
                    else -> Gen3.Ink
                },
            )
        }
    }
}

/** Section header in the pixel face - the "eyebrow" of a Gen 3 menu page. */
@Composable
fun Gen3Header(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        modifier = modifier.padding(vertical = 4.dp),
        fontFamily = Gen3.PixelFont,
        fontSize = 11.sp,
        fontWeight = FontWeight.Normal,
        color = Gen3.Paper,
    )
}
