package com.ironmonone.app

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.swordfish.libretrodroid.GLRetroView
import com.swordfish.libretrodroid.LibretroDroid

/**
 * What the game view reports when its core refuses the game (rc33 audit P1). The view emitted these on
 * getGLRetroErrors and nothing listened, so a game that would not load was a black screen with no word. Kept out of
 * PlayScreen, which sits at ART's verifier limit: Play calls this in one line.
 *
 * The line stays on screen in a panel over the game, with the way out (rc32 audit P2 #53): it was a toast, gone after
 * three seconds, and then the screen was black again with nothing saying why. Any error stops the view for good.
 */
@Composable
internal fun CoreLoadErrors(retro: GLRetroView?, isRun: Boolean, modifier: Modifier) {
    var failed by remember(retro) { mutableStateOf<String?>(null) }
    LaunchedEffect(retro) {
        val view = retro ?: return@LaunchedEffect
        view.getGLRetroErrors().collect { code -> failed = CoreLoadErrorCopy.forCode(code) }
    }
    val line = failed ?: return
    val nav = LocalShellNav.current
    EmptyState(
        CoreLoadErrorCopy.HEADLINE, line, modifier.padding(16.dp).widthIn(max = 520.dp),
        actionLabel = if (isRun) PlayNothingCopy.BACK_TO_KAIZO else PlayNothingCopy.PICK_A_GAME,
        onAction = if (isRun) nav?.openKaizo else nav?.openMyGames,
    )
}

internal object CoreLoadErrorCopy {
    const val HEADLINE = "The game is not running"
    fun forCode(code: Int): String = when (code) {
        LibretroDroid.ERROR_LOAD_LIBRARY -> "The emulator could not start on this phone. Reinstall KaizoCore, then try again."
        LibretroDroid.ERROR_LOAD_GAME -> "This game would not load. The file may be damaged, or not the game it says it is."
        LibretroDroid.ERROR_GL_NOT_COMPATIBLE -> "This phone's graphics cannot run this game's emulator."
        else -> "The emulator stopped. Go back to Home and open the game again."
    }
}
