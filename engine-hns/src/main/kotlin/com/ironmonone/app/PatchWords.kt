package com.ironmonone.app

// In engine-hns so HnsSetup (HnsSetupCore.kt) says a failed patch the same way in the app and in KaizoCore PC.

/**
 * What a failed patch says to the player. Plain words only: the patcher's own
 * messages carry checksums and file paths (audit, 2026-09-27).
 */
fun patchFailure(t: Throwable): String = when (t) {
    // An xdelta on the wrong copy of its game is SourceMismatch; it read "The patch file is damaged" (rc32 audit P2 #115).
    is com.ironmonone.patch.WrongSourceRom, is com.ironmonone.patch.WrongSourceSize, is com.ironmonone.patch.SourceMismatch ->
        "This patch was made for a different version of the game. Nothing was changed."
    is com.ironmonone.patch.OutputMismatch ->
        "The patch applied, but the result came out wrong, so it was not kept. The game is probably a different version."
    is com.ironmonone.patch.PatchException -> t.message ?: "The patch did not apply."
    is OutOfMemoryError -> "This phone ran out of memory patching a game this size. Close other apps and try again."
    is java.io.IOException -> if ((t.message ?: "").contains("ENOSPC") || (t.message ?: "").contains("No space"))
        "Not enough free space on this phone to save the patched game." else "Could not save the patched game. Try again."
    else -> "The patch did not apply. It was probably made for a different version of this game."
}
