package com.ironmonone.app

import kotlinx.coroutines.launch
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.foundation.focusable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ironmonone.app.gen3.Gen3
import com.ironmonone.app.gen3.Gen3Box

/**
 * Shell components. Everything that is not the tracker panel is built from
 * these, so a screen cannot quietly grow its own dialect.
 *
 * The tracker panel is NOT in scope here - it is a clone of the PC tracker and
 * takes its metrics from that reference, not from this file.
 */

/** The phases the randomizer actually has. See [ProgressPanel]. */
enum class RunPhase(val label: String) {
    ROTATING("Preparing files"),
    RANDOMIZING("Randomizing on this phone"),
    FINISHING("Saving run"),
    /** The Prepare screen: patching a supplied ROM into a stored base. */
    PATCHING("Patching ROM"),
    /** The Run tab adding settings files, and exporting the current run. */
    IMPORTING("Adding files"),
    EXPORTING("Exporting the run"),
}

/**
 * A framed progress panel for the app's long operations.
 *
 * ## Why this is indeterminate, and why the phases are what they are
 *
 * Randomizing takes 25-45s and previously showed a bare progress bar whose
 * only explanation sat at the bottom of a scrolling page, off-screen at the
 * moment it mattered. The screen looked frozen.
 *
 * The engine exposes NO progress callback - `randomize()` is a single blocking
 * call - so there is no honest percentage to show, and inventing one would be
 * a lie that gets less true the longer it runs. What IS real: the caller runs
 * three discrete steps (rotate files, randomize, save), so those are reported
 * as phases, and the elapsed second count is measured rather than guessed.
 *
 * If the engine ever grows a progress callback, this is where it lands.
 */
@Composable
fun ProgressPanel(phase: RunPhase, modifier: Modifier = Modifier) {
    var seconds by remember(phase) { mutableIntStateOf(0) }
    LaunchedEffect(phase) {
        seconds = 0
        while (true) {
            kotlinx.coroutines.delay(1000)
            seconds += 1
        }
    }
    Gen3Box(modifier.fillMaxWidth()) {
        Column {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    phase.label,
                    style = MaterialTheme.typography.titleSmall,
                    color = Shell.inkOnPaper,
                )
                Text(
                    "${seconds}s",
                    style = MaterialTheme.typography.bodySmall,
                    color = Shell.hintOnPaper,
                )
            }
            Spacer(Modifier.height(8.dp))
            IndeterminateBar()
            if (phase == RunPhase.RANDOMIZING) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Twenty to forty seconds is normal. Leaving this screen is fine; " +
                        "closing the app is not.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Shell.hintOnPaper,
                )
            }
        }
    }
}

/**
 * A hard-edged sweeping bar, in the shell's language rather than Material's.
 *
 * Motion only - it carries no information, so a device that refuses to animate
 * loses nothing but the sweep. The panel's phase label and second count are
 * plain text and never depend on an animation running.
 */
@Composable
private fun IndeterminateBar() {
    val t = rememberInfiniteTransition(label = "bar")
    val x by t.animateFloat(
        initialValue = -0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "sweep",
    )
    // The sweeping segment must be its OWN node. Putting the background on the
    // node whose size the layout block expands painted the full width solid -
    // a "progress bar" that was always 100%, which is worse than none.
    androidx.compose.foundation.layout.BoxWithConstraints(
        Modifier.fillMaxWidth().height(6.dp)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(3.dp))
            .background(Shell.frame),
    ) {
        val track = maxWidth
        Box(
            Modifier
                .offset(x = track * x)
                .width(track * 0.35f)
                .height(6.dp)
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(3.dp))
                .background(Shell.accent),
        )
    }
}

/**
 * The outcome of an action, shown where the action was taken.
 *
 * The randomizer's "New run ready (seed ...)" line used to render at the very
 * bottom of a scrolling column - below the fold exactly when it mattered, so
 * the primary action of the app appeared to do nothing. This is anchored by
 * its caller instead, next to the button that caused it.
 */
@Composable
fun StatusBanner(text: String, isError: Boolean, modifier: Modifier = Modifier) {
    Gen3Box(modifier.fillMaxWidth()) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isError) Shell.dangerOnPaper else Shell.inkOnPaper,
        )
    }
}

// ---------------------------------------------------------------------------
// Selection, rows, dialogs and status: the shell's replacements for the stock
// Material widgets that were showing up on RUN, KEYS and LIBRARY.
// ---------------------------------------------------------------------------

/**
 * A selector in the shell's language: a hard square that fills in, not a
 * Material circle with a ripple.
 *
 * Deliberately NOT clickable itself - the whole row is the target, which is
 * both easier to hit and the behaviour the rows already had.
 */
@Composable
fun ShellRadio(selected: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(20.dp)
            .clip(androidx.compose.foundation.shape.CircleShape)
            .border(2.dp, if (selected) Shell.accent else Shell.hintOnPaper, androidx.compose.foundation.shape.CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Box(Modifier.size(10.dp).clip(androidx.compose.foundation.shape.CircleShape).background(Shell.accent))
        }
    }
}

/**
 * One row of a settings list on paper: label left, value right, whole row
 * tappable and at least [Shell.touchTarget] tall.
 */
@Composable
fun ShellListRow(
    label: String,
    value: String,
    onClick: (() -> Unit)? = null,
    valueColor: Color = Shell.inkOnPaper,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .heightIn(min = Shell.touchTarget)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = Shell.inkOnPaper)
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
            color = valueColor,
        )
    }
}

/** A hairline on paper. */
@Composable
fun ShellDivider(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(Shell.hairline))
}

/**
 * The shell's busy indicator: the same sweeping bar the progress panel uses,
 * so a spinner never appears in an app that has no other circles in it.
 */
@Composable
fun ShellBusy(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(vertical = 12.dp)) { IndeterminateBar() }
}

/**
 * A modal in shell language - a framed paper panel, not a Material AlertDialog
 * with rounded corners and a tonal scrim.
 */
@Composable
fun ShellDialog(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Gen3Box(Modifier.fillMaxWidth(), paper = Shell.paper) {
            // Scrolls: at a large font a dialog's buttons fell off the bottom
            // of the screen with no way to reach them (audit, 2026-09-27).
            Column(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(4.dp)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                    color = Shell.inkOnPaper,
                )
                Spacer(Modifier.height(12.dp))
                content()
            }
        }
    }
}

/**
 * An empty state: a headline and an explanation, on paper.
 *
 * These used to be loose grey text on the black page, measured at 2.03:1 -
 * which reads as a disabled control rather than as the app telling you
 * something. The copy was always good; it just looked switched off.
 */
@Composable
fun EmptyState(headline: String, detail: String, modifier: Modifier = Modifier) {
    Gen3Box(modifier.fillMaxWidth()) {
        Column {
            Text(
                headline,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                color = Shell.inkOnPaper,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                detail,
                style = MaterialTheme.typography.bodyMedium,
                color = Shell.hintOnPaper,
            )
        }
    }
}

/**
 * A full-screen scene behind a tab's content.
 *
 * ## The rule that makes this safe
 *
 * Screen-filling artwork is the fastest way to wreck a contrast sweep, so this
 * component owns the guarantee rather than trusting each screen: the scene is
 * always drawn under a SCRIM, and the app's readable text sits on opaque paper
 * cards which the scrim cannot affect at all.
 *
 * That ordering is why this is possible now and would not have been before
 * M2 - de-Materializing moved essentially every string in the shell onto a
 * card. Exactly one place still paints text straight onto the page.
 *
 * The art is pixel art, so it is upscaled with NO filtering: a 256x160 scene
 * blown up to a phone should look like big honest pixels, not a blurred
 * photograph. [scrim] is measured, not eyeballed - see the contrast check in
 * the plan's verification protocol.
 */
@Composable
fun ScreenBackground(
    @Suppress("UNUSED_PARAMETER") asset: String?,
    modifier: Modifier = Modifier,
    @Suppress("UNUSED_PARAMETER") scrim: Float = 0.62f,
    content: @Composable () -> Unit,
) {
    // The modern look paints the plain page; the pixel-art scenes were the
    // retro skin's and are no longer drawn (2026-09-27).
    Box(modifier.fillMaxSize().background(Shell.night)) { content() }
}

/** A rounded check box. Row-level click, like ShellRadio. */
@Composable
fun ShellCheck(checked: Boolean, modifier: Modifier = Modifier) {
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp)
    Box(
        modifier.size(20.dp).clip(shape)
            .then(if (checked) Modifier.background(Shell.accent) else Modifier.border(2.dp, Shell.hintOnPaper, shape)),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            Text("\u2713", fontSize = 13.sp, color = Shell.onAccent, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
        }
    }
}

/**
 * A small segmented choice, for enums with only a couple of values.
 *
 * The editor rendered EVERY enum as a stacked radio list, so a two-value
 * choice ate ~230px of a phone screen and a long one ate the whole screen.
 * Short lists go inline; longer ones get a dialog (see the editor).
 */
@Composable
@OptIn(ExperimentalLayoutApi::class)
fun ShellSegmented(
    values: List<String>,
    selected: String,
    label: (String) -> String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // FlowRow, not Row. A Row silently CLIPS chips past the right edge: they
    // are not drawn and cannot be tapped.
    //
    // The caller only picks this control when it thinks the labels fit, but it
    // decides that by COUNTING CHARACTERS, which is not a width. At the
    // system font scale of 1.3 that estimate is wrong: Unchanged/Shuffle/
    // Random measures 24 characters, passes the check, and overflows. Verified
    // on a device - the chips wrap to a second line here, so under a Row
    // "Random" was clipped and unselectable for anyone using large text.
    // Chips are a radio group to TalkBack and 48dp tall (audit, 2026-09-27):
    // at 36dp with plain clicks they were small and never said "selected".
    FlowRow(
        modifier.selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        values.forEach { v ->
            val on = v == selected
            Box(
                Modifier
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(50))
                    .background(if (on) Shell.inkOnPaper else Shell.raised)
                    .selectable(selected = on, role = androidx.compose.ui.semantics.Role.RadioButton) { onSelect(v) }
                    .heightIn(min = Shell.touchTarget)
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label(v),
                    fontSize = 13.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                    color = if (on) Shell.night else Shell.inkOnPaper,
                )
            }
        }
    }
}

/**
 * A number setting: type it, or hold - / + to run it.
 *
 * It used to be a -/+ pair that moved one step per tap, so 26 to 100 was 74
 * taps (Blake, 2026-09-27: "If I want 100% it requires a hundred taps"). Now
 * the number is a field with the number keyboard, committed on Done or when
 * the field loses focus and clamped to [range], and a held key repeats after
 * 0.4 s, speeding up the longer it is held.
 */
@Composable
fun ShellStepper(
    value: Int,
    range: IntRange,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val current by androidx.compose.runtime.rememberUpdatedState(value)
    val change by androidx.compose.runtime.rememberUpdatedState(onChange)
    var text by remember { mutableStateOf(value.toString()) }
    var focused by remember { mutableStateOf(false) }
    fun step(d: Int) {
        val next = (current + d).coerceIn(range)
        // The field shows the stepped value too. While it had focus it kept the
        // old typed text, and losing focus committed that, undoing the step
        // (audit, 2026-09-27).
        text = next.toString()
        if (next != current) change(next)
    }
    // Follow the value from outside (a step, a revert) unless the player is typing.
    androidx.compose.runtime.LaunchedEffect(value) { if (!focused) text = value.toString() }
    fun commit() {
        val typed = text.trim().toIntOrNull()
        val next = (typed ?: current).coerceIn(range)
        text = next.toString()
        if (next != current) change(next)
    }
    val focus = androidx.compose.ui.platform.LocalFocusManager.current
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        StepKey("\u2212", "Less") { step(-1) }
        androidx.compose.foundation.text.BasicTextField(
            value = text,
            onValueChange = { t -> if (t.length <= maxOf(4, range.last.toString().length, range.first.toString().length) && t.all { it.isDigit() || it == '-' }) text = t },
            singleLine = true,
            textStyle = MaterialTheme.typography.titleMedium.copy(
                color = Shell.inkOnPaper, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            ),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(Shell.accent),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                // The number pad has no minus sign on many phones, so a range below zero gets the full keyboard.
                keyboardType = if (range.first < 0) androidx.compose.ui.text.input.KeyboardType.Text
                    else androidx.compose.ui.text.input.KeyboardType.Number,
                imeAction = androidx.compose.ui.text.input.ImeAction.Done,
            ),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { commit(); focus.clearFocus() }),
            modifier = Modifier
                .padding(horizontal = 6.dp)
                .width(64.dp)
                .heightIn(min = Shell.touchTarget)
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(Shell.controlRadius))
                .background(Shell.night)
                .border(1.dp, if (focused) Shell.accent else Shell.hairline,
                    androidx.compose.foundation.shape.RoundedCornerShape(Shell.controlRadius))
                .onFocusChanged { f ->
                    if (focused && !f.isFocused) commit()
                    focused = f.isFocused
                }
                .semantics { contentDescription = "Value, from ${range.first} to ${range.last}" },
            decorationBox = { inner -> Box(Modifier.padding(vertical = 12.dp), contentAlignment = Alignment.Center) { inner() } },
        )
        StepKey("+", "More") { step(1) }
    }
}

/** A - or + key. A tap is one step; holding repeats, faster the longer it is held. */
@Composable
private fun StepKey(glyph: String, spoken: String, onStep: () -> Unit) {
    val fire by androidx.compose.runtime.rememberUpdatedState(onStep)
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    var pressed by remember { mutableStateOf(false) }
    var keyFocus by remember { mutableStateOf(false) }
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(Shell.controlRadius)
    Box(
        Modifier.size(Shell.touchTarget)
            .clip(shape)
            .background(if (pressed) Shell.raisedPressed else Shell.raised)
            .border(if (keyFocus) 2.dp else 0.dp, if (keyFocus) Shell.accent else Color.Transparent, shape)
            // TalkBack, Switch Access and a keyboard press it through these; the
            // pointerInput below only ever heard fingers (audit, 2026-09-27).
            .semantics {
                contentDescription = spoken; role = androidx.compose.ui.semantics.Role.Button
                onClick { fire(); true }
            }
            .onKeyEvent { e ->
                val press = e.key == Key.Enter || e.key == Key.NumPadEnter || e.key == Key.DirectionCenter || e.key == Key.Spacebar
                if (press && e.type == KeyEventType.KeyUp) fire()
                press
            }
            .onFocusChanged { keyFocus = it.isFocused }
            .focusable()
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    pressed = true
                    haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                    fire()
                    val repeat = scope.launch {
                        kotlinx.coroutines.delay(400)
                        var wait = 120L
                        var n = 0
                        while (true) {
                            fire()
                            kotlinx.coroutines.delay(wait)
                            n++
                            // Every 6 steps a little faster, down to 25 ms: 0 to 100 in about 3 s.
                            if (n % 6 == 0 && wait > 25) wait = (wait * 0.7).toLong().coerceAtLeast(25)
                        }
                    }
                    tryAwaitRelease()
                    repeat.cancel()
                    pressed = false
                })
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(glyph, fontSize = 20.sp, color = Shell.inkOnPaper)
    }
}
