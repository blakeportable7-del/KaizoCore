package com.ironmonone.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.swordfish.libretrodroid.GLRetroView
import com.swordfish.libretrodroid.LibretroDroid

/**
 * What the game view reports when its core refuses the game, said in Play's status line (rc33 audit P1). The view
 * emitted these on getGLRetroErrors and nothing listened, so a game that would not load was a black screen with no
 * word. Kept out of PlayScreen, which sits at ART's verifier limit: Play calls this in one line.
 */
@Composable
internal fun CoreLoadErrors(retro: GLRetroView?, onError: (String) -> Unit) {
    LaunchedEffect(retro) {
        val view = retro ?: return@LaunchedEffect
        view.getGLRetroErrors().collect { code -> onError(CoreLoadErrorCopy.forCode(code)) }
    }
}

internal object CoreLoadErrorCopy {
    fun forCode(code: Int): String = when (code) {
        LibretroDroid.ERROR_LOAD_LIBRARY -> "The emulator could not start on this phone. Reinstall KaizoCore, then try again."
        LibretroDroid.ERROR_LOAD_GAME -> "This game would not load. The file may be damaged, or not the game it says it is."
        LibretroDroid.ERROR_GL_NOT_COMPATIBLE -> "This phone's graphics cannot run this game's emulator."
        else -> "The emulator stopped. Go back to Home and open the game again."
    }
}
