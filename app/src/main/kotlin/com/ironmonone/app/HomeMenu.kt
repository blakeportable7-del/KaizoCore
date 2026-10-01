package com.ironmonone.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ironmonone.app.gen3.Gen3Box
import com.ironmonone.app.gen3.Gen3Button
import com.ironmonone.app.gen3.Gen3Header
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The screens of the welcome and the main menu (2026-09-29). What they say is HomeCopy and
 * HomeMode, where each button leads is AppNav, and none of that is decided here: this file draws.
 */

/** A mode button never gets shorter than this. Its text grows past it with the font size and is never cut. */
private val MODE_BUTTON_MIN_HEIGHT = 72.dp

/**
 * Home, the first screen (2026-09-29): the game in progress at the top when there is one, the
 * four modes, and small links to Library, More and Your stats. Buttons are stacked in one column rather than
 * set in a row or a grid: at 360dp wide and a 1.3 font scale two columns would cut the words,
 * and here every line simply wraps.
 */
@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun HomeScreen(
    modifier: Modifier = Modifier,
    onContinue: () -> Unit,
    onMode: (HomeMode) -> Unit,
    onLibrary: () -> Unit,
    onMore: () -> Unit,
    onStats: () -> Unit,
) {
    val context = LocalContext.current
    val store = remember { PrepStore(context) }
    // Read off the main thread, since finding a Library game can identify a file that has no
    // sidecar yet (2026-09-29). Nothing is drawn until it has answered (a few milliseconds), so the
    // buttons never slide down when the Continue card arrives above them and take a tap meant for another.
    var loaded by remember { mutableStateOf(false) }
    var resume by remember { mutableStateOf<ContinueInfo?>(null) }
    LaunchedEffect(Unit) {
        resume = withContext(Dispatchers.IO) { runCatching { ContinueCard.read(store, NuzlockeStore(context.applicationContext.filesDir)) }.getOrNull() }
        loaded = true
    }
    Box(modifier.fillMaxSize().background(Shell.night)) {
        if (loaded) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // A column no wider than a phone's, so a tablet does not stretch the buttons across the room.
                Column(Modifier.widthIn(max = 560.dp).fillMaxWidth()) {
                    // The menu's own title, the welcome's words (Blake, 2026-09-29: "need a Welcome to Kaizo Core
                    // on the top of the menu"). A heading, so a screen reader can jump to it.
                    Text(
                        HomeCopy.WELCOME_TITLE,
                        Modifier.padding(top = 4.dp, bottom = 16.dp).semantics { heading() },
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = Shell.textOnNight,
                    )
                    resume?.let {
                        ContinueBlock(it, onContinue)
                        Spacer(Modifier.height(20.dp))
                    }
                    Gen3Header(HomeCopy.PICK_A_MODE)
                    Spacer(Modifier.height(6.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        HomeMode.entries.forEach { m -> ModeButton(m) { onMode(m) } }
                    }
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        HomeLink(HomeCopy.LIBRARY_LINK, HomeCopy.LIBRARY_LINK_SPOKEN, onLibrary)
                        HomeLink(HomeCopy.MORE_LINK, HomeCopy.MORE_LINK_SPOKEN, onMore)
                        HomeLink(HomeCopy.STATS_LINK, HomeCopy.STATS_LINK_SPOKEN, onStats)
                    }
                }
            }
        }
    }
}

/** The game the player was in, and one tap back into it, on the shell's accent button (2026-09-29). */
@Composable
private fun ContinueBlock(info: ContinueInfo, onContinue: () -> Unit) {
    Gen3Box(Modifier.fillMaxWidth()) {
        Column {
            Gen3Header(HomeCopy.LEFT_OFF)
            Text(
                info.game,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = Shell.inkOnPaper,
            )
            Text(info.detail, style = MaterialTheme.typography.bodyMedium, color = Shell.hintOnPaper)
            Spacer(Modifier.height(12.dp))
            Gen3Button(
                HomeCopy.CONTINUE,
                Modifier.fillMaxWidth().semantics { contentDescription = HomeCopy.continueSpoken(info.game) },
                accent = true,
                onClick = onContinue,
            )
        }
    }
}

/**
 * One of the four modes: a card that is a button, with the mode's name and its one plain line
 * (2026-09-29). Gen3Button is a single line that ends in an ellipsis, right for a label and wrong
 * for a sentence, so this is a Gen3Box made tappable. It is a button to a screen reader, at least
 * 48dp tall and here well over that, and no text in it has a line limit: at a large font it
 * grows taller instead of cutting a word.
 */
@Composable
private fun ModeButton(mode: HomeMode, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val haptics = LocalHapticFeedback.current
    Gen3Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = MODE_BUTTON_MIN_HEIGHT)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClickLabel = mode.title) {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            }
            .semantics { contentDescription = mode.spoken },
        paper = if (pressed) Shell.raised else Shell.paper,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    mode.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = Shell.inkOnPaper,
                )
                Spacer(Modifier.height(2.dp))
                Text(mode.line, style = MaterialTheme.typography.bodyMedium, color = Shell.hintOnPaper)
            }
            Spacer(Modifier.width(8.dp))
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Shell.hintOnPaper)
        }
    }
}

/** A small underlined link on the page, with a 48dp target (2026-09-29): the bar has the same two places, this is the quick way from the menu. */
@Composable
private fun HomeLink(text: String, spoken: String, onClick: () -> Unit) {
    Box(
        Modifier
            .heightIn(min = Shell.touchTarget)
            .widthIn(min = Shell.touchTarget)
            .clip(RoundedCornerShape(Shell.controlRadius))
            .clickable(role = Role.Button, onClickLabel = spoken, onClick = onClick)
            .semantics { contentDescription = spoken }
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = Shell.accentOnNight,
            textDecoration = Shell.linkDecoration,
        )
    }
}

/**
 * A screen reached from a Home button (Kaizo IronMON, Nuzlocke, ROM Hacks), under a top bar with
 * a back control to Home. The system Back does the same (AppNav.back). Run and Hacks were tabs
 * until 2026-09-29 and are drawn unchanged inside [content].
 */
@Composable
internal fun ModeScreen(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().background(Shell.night)) {
        Row(
            Modifier.fillMaxWidth().background(Shell.paper).padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .heightIn(min = Shell.touchTarget)
                    .widthIn(min = Shell.touchTarget)
                    .clip(RoundedCornerShape(Shell.controlRadius))
                    .clickable(role = Role.Button, onClickLabel = HomeCopy.BACK_HOME, onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = HomeCopy.BACK_HOME,
                    tint = Shell.inkOnPaper,
                )
            }
            Text(
                title,
                modifier = Modifier.padding(start = 6.dp),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = Shell.inkOnPaper,
            )
        }
        Box(Modifier.weight(1f)) { content() }
    }
}

/**
 * The welcome, on the first launch only (2026-09-29): what KaizoCore is in two lines, that it holds
 * no games, and two ways on. It scrolls, so at a large font or in landscape both buttons stay
 * reachable, and it draws its own status and navigation bar padding because it replaces the
 * whole screen.
 */
@Composable
internal fun WelcomeScreen(onAddGames: () -> Unit, onLookAround: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(Shell.night).statusBarsPadding().navigationBarsPadding()
            .verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Column(Modifier.widthIn(max = 480.dp).align(Alignment.CenterHorizontally)) {
            Text(
                HomeCopy.WELCOME_TITLE,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Shell.textOnNight,
            )
            Spacer(Modifier.height(12.dp))
            Text(HomeCopy.WELCOME_WHAT, style = MaterialTheme.typography.bodyLarge, color = Shell.textOnNight)
            Spacer(Modifier.height(6.dp))
            Text(HomeCopy.WELCOME_DOES, style = MaterialTheme.typography.bodyLarge, color = Shell.textOnNight)
            Spacer(Modifier.height(16.dp))
            Gen3Box(Modifier.fillMaxWidth()) {
                Text(HomeCopy.WELCOME_NO_GAMES, style = MaterialTheme.typography.bodyMedium, color = Shell.inkOnPaper)
            }
            // A beta, and how to tell Blake what is wrong, with the crash report switch from More beside it. The
            // welcome shows once, so this is the first launch only (Blake, 2026-09-30).
            Spacer(Modifier.height(12.dp))
            Gen3Box(Modifier.fillMaxWidth()) {
                Column {
                    Text(HomeCopy.BETA_TITLE, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium, color = Shell.inkOnPaper)
                    Spacer(Modifier.height(6.dp))
                    Text(HomeCopy.BETA_ASK, style = MaterialTheme.typography.bodyMedium, color = Shell.inkOnPaper)
                }
            }
            Spacer(Modifier.height(12.dp))
            CrashReportsCard()
            Spacer(Modifier.height(20.dp))
            Gen3Button(HomeCopy.ADD_GAMES, Modifier.fillMaxWidth(), accent = true) { onAddGames() }
            Spacer(Modifier.height(10.dp))
            Gen3Button(HomeCopy.LOOK_AROUND, Modifier.fillMaxWidth()) { onLookAround() }
        }
    }
}
